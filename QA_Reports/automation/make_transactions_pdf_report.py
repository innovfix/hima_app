#!/usr/bin/env python3
"""
Generate a tech-lead-friendly PDF from transactions_api_results.json.
Uses Chrome headless for HTML → PDF (no Python deps required).
"""
from __future__ import annotations

import html as html_lib
import json
import subprocess
from datetime import datetime
from pathlib import Path

HERE     = Path(__file__).resolve().parent
RESULTS  = HERE / "hima_automation_tests_results.json"
OUT_HTML = HERE / "hima_automation_tests_print.html"
OUT_PDF  = HERE / "hima_automation_tests.pdf"

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# ────────────── Plain-language summaries per bug ID ──────────────
PLAIN = {
    "BUG-TX-001": {
        "what":  "Any logged-in user can read ANY other user's complete transaction "
                 "history (amount, payment_type, datetime) just by sending a "
                 "different user_id in the request body.",
        "hurts": "CRITICAL. Financial-history privacy leak. Test proof: our male QA "
                 "token (user_id=1125868) successfully fetched 8,926 transactions "
                 "belonging to female user_id=72530. This affects every user on "
                 "production right now. Under Indian DPDP / GDPR this is a "
                 "reportable data-protection incident.",
        "fix":   "Backend: derive user_id from the JWT sub claim, ignore the value "
                 "in the request body. The sibling endpoint /female_transaction "
                 "already does this correctly — copy that pattern "
                 "(`abort_if($request->input('user_id') != $request->user()->id, 403)`).",
    },
    "BUG-TX-002": {
        "what":  "When a client calls the API without an `Accept: application/json` "
                 "header, the server responds with HTML 302 → /login instead of a "
                 "JSON 401.",
        "hurts": "Medium. The Android app is fine today (Retrofit sets Accept:json), "
                 "but any third-party consumer, a misconfigured OkHttp interceptor, "
                 "or a future non-Retrofit client will crash its JSON parser. Also a "
                 "subtle consistency smell — /api/* routes shouldn't do web-auth "
                 "redirects.",
        "fix":   "Backend: in Laravel's exception handler, force JSON 401 when the "
                 "request path starts with `api/` regardless of Accept header.",
    },
    "BUG-TX-003": {
        "what":  "Negative offset values (e.g. offset=-1) are silently coerced to 0 "
                 "instead of being rejected.",
        "hurts": "Medium. A buggy client sending `offset = Math.max(0, page-limit)` "
                 "with off-by-one math could re-render page 0 content and double up "
                 "rows. Also inconsistent: the non-numeric user_id IS rejected "
                 "cleanly, but negative offset is not. The same call with "
                 "offset=-1 and offset=0 returned identical first_id=43261177 in "
                 "our test, proving silent coercion.",
        "fix":   "Backend: add `'offset' => 'nullable|integer|min:0'` to the "
                 "FormRequest validator and return 400 JSON on failure.",
    },
    "BUG-TX-004": {
        "what":  "Calling /transaction_list with limit=-1 returns an HTTP 500 with "
                 "the Laravel HTML error page, not a clean JSON error.",
        "hurts": "High. Breaks the mobile client (it tries to parse HTML as JSON and "
                 "silently fails — blank screen). Also a small information-disclosure "
                 "risk if APP_DEBUG=true on prod (would dump stack trace).",
        "fix":   "Backend: add `'limit' => 'integer|min:1|max:100'` validator so "
                 "invalid input returns 400 JSON before the DB query runs. "
                 "Double-check APP_DEBUG=false on prod.",
    },
    "BUG-TX-005": {
        "what":  "There is no upper cap on the `limit` query parameter. A single "
                 "request with limit=99999 returned 8,926 rows (~1.5 MB JSON) in "
                 "our test — and the server happily served it.",
        "hurts": "High. Two attack surfaces: (1) DoS — a few thousand such requests "
                 "exhaust MySQL connection pool and bandwidth; (2) Mobile OOM — if a "
                 "patched APK sends limit=99999, Gson will blow up parsing on older "
                 "Android devices. The Android app itself sends limit=10 (safe), so "
                 "this is reachable via curl / patched APK only, but still must be "
                 "enforced server-side.",
        "fix":   "Backend: add `'limit' => 'nullable|integer|min:1|max:100'` and "
                 "cap in the controller: `->take(min($limit, 100))`.",
    },
    "BUG-TX-007": {
        "what":  "The Kotlin data class `TransactionsResponse` declares several "
                 "fields as non-nullable (user_name, call_type, duration, "
                 "call_user_name) but the server response does NOT include them.",
        "hurts": "High. This is a latent runtime crash waiting to trigger. Today Gson "
                 "silently defaults missing non-null fields to null (which violates "
                 "the Kotlin contract), but on Kotlin 1.9+ with strict null safety "
                 "this will throw IllegalStateException at deserialisation — a "
                 "blank transactions screen for every male user. Confirmed: our "
                 "test fetched 5 rows and all 5 were missing those 4 keys.",
        "fix":   "Two-part fix (defence in depth). Android: mark the fields "
                 "nullable in TransactionsResponse.kt: "
                 "`val user_name: String?`, `val call_type: String?`, "
                 "`val duration: String?`, `val call_user_name: String?`. "
                 "Backend: always emit the documented keys (empty string fallback) "
                 "so the API contract is stable for any future consumers.",
    },
}

# Fallback text for failing cases that don't have a named bug_ref.
EXTRA: dict[str, dict[str, str]] = {}


def render(data: dict) -> str:
    results = data.get("results", [])
    base_url = data.get("base_url", "")

    total  = len(results)
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = total - passed

    fails = [r for r in results if r["status"] == "FAIL"]
    crit = sum(1 for r in fails if r["severity"] == "critical")
    high = sum(1 for r in fails if r["severity"] == "high")
    med  = sum(1 for r in fails if r["severity"] == "medium")
    low  = sum(1 for r in fails if r["severity"] in ("low", "info"))

    server_only = sum(1 for r in fails if r.get("fix_where") == "server")
    both        = sum(1 for r in fails if r.get("fix_where") == "both")
    client_only = sum(1 for r in fails if r.get("fix_where") == "client")
    fixable     = server_only + both + client_only
    fixable_pct = 0 if failed == 0 else int(round(100 * fixable / failed))

    # Dedup bug cards by bug_ref (or test id if no bug_ref).
    bug_cards = []
    seen = set()
    # Sort critical-first so the IDOR shows up on page 2.
    sev_order = {"critical": 0, "high": 1, "medium": 2, "low": 3, "info": 4}
    for r in sorted(fails, key=lambda x: (sev_order.get(x["severity"], 9), x["id"])):
        bid = r.get("bug_ref")
        key = bid or r["id"]
        if key in seen:
            continue
        seen.add(key)
        plain = PLAIN.get(bid) if bid else EXTRA.get(r["id"])
        if not plain:
            plain = {
                "what":  r.get("title", ""),
                "hurts": r.get("why") or "",
                "fix":   (r.get("fix_server") or r.get("fix_android") or "").strip(),
            }

        sev_cls = r["severity"]
        fix_w   = r.get("fix_where") or "none"
        file_ref_html = (
            f'<tr><th>Source ref</th><td class="mono">{html_lib.escape(r.get("file_ref",""))}</td></tr>'
            if r.get("file_ref") else ""
        )

        bug_cards.append(f"""
<section class="bug sev-{sev_cls}">
  <div class="bug-head">
    <span class="sev sev-{sev_cls}">{sev_cls.upper()}</span>
    <span class="id">{html_lib.escape(bid or r['id'])}</span>
    <span class="where where-{fix_w}">FIX: {fix_w.upper()}</span>
    <span class="screen">{html_lib.escape(r.get('screen',''))}</span>
    <span class="screen">{html_lib.escape(r.get('category',''))}</span>
  </div>
  <h3 class="bug-title">{html_lib.escape(r.get('title',''))}</h3>
  <table class="kv">
    <tr><th>What's wrong</th><td>{html_lib.escape(plain['what'])}</td></tr>
    <tr><th>Why it matters</th><td>{html_lib.escape(plain['hurts'])}</td></tr>
    <tr><th>How to fix</th><td>{html_lib.escape(plain['fix'])}</td></tr>
    {file_ref_html}
    <tr><th>Server response observed</th><td class="mono">{html_lib.escape((r.get('body_preview') or '').strip()[:260])}</td></tr>
  </table>
</section>""")

    rows = []
    for r in results:
        st = r["status"]
        rows.append(f"""
<tr class="{st.lower()}">
  <td class="mono">{html_lib.escape(r['id'])}</td>
  <td>{html_lib.escape(r['screen'])}</td>
  <td>{html_lib.escape(r['category'])}</td>
  <td>{html_lib.escape(r['title'])[:92]}</td>
  <td><span class="sev sev-{r['severity']}">{r['severity']}</span></td>
  <td class="st st-{st.lower()}">{st}</td>
  <td class="mono">{html_lib.escape(str(r.get('code','')))}</td>
  <td>{html_lib.escape(r.get('detail','')[:120])}</td>
</tr>""")
    rows_html = "".join(rows)

    now = datetime.now().strftime("%Y-%m-%d %H:%M")

    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Hima — Transactions API Test Report</title>
<style>
  @page {{ size: A4; margin: 16mm 14mm; }}
  * {{ box-sizing: border-box; }}
  body {{
    font-family: -apple-system, "Segoe UI", Arial, sans-serif;
    color: #111; font-size: 11.2pt; line-height: 1.45; margin: 0;
  }}
  h1 {{ font-size: 20pt; margin: 0 0 4pt; color: #1f2937; }}
  h2 {{ font-size: 14pt; margin: 18pt 0 6pt; color: #1f2937;
        border-bottom: 1px solid #d1d5db; padding-bottom: 3pt; }}
  h3 {{ font-size: 12pt; margin: 8pt 0 4pt; }}
  .meta {{ color: #555; font-size: 10pt; margin-bottom: 10pt; }}
  .meta b {{ color: #1f2937; }}
  .note {{ color: #555; font-size: 10pt; }}

  .cards {{ display: flex; flex-wrap: wrap; gap: 8pt; margin: 6pt 0 12pt; }}
  .card {{
    flex: 1 1 105pt; min-width: 105pt; padding: 10pt; border: 1px solid #d1d5db;
    border-radius: 6pt; text-align: center; background: #fafbfc;
  }}
  .card .n {{ font-size: 22pt; font-weight: 700; color: #1f2937; }}
  .card .l {{ font-size: 9.5pt; color: #555; margin-top: 2pt; }}
  .card.crit .n {{ color: #c0392b; }}
  .card.high .n {{ color: #d35400; }}
  .card.med  .n {{ color: #b7791f; }}
  .card.low  .n {{ color: #2563eb; }}
  .card.pass .n {{ color: #15803d; }}
  .card.fail .n {{ color: #b91c1c; }}

  .headline {{
    border: 1px solid #fca5a5; background: #fff1f1; border-left: 4px solid #c0392b;
    padding: 9pt 12pt; border-radius: 6pt; margin: 10pt 0;
  }}
  .headline h3 {{ margin: 0 0 4pt; color: #991b1b; }}
  .headline p {{ margin: 4pt 0 0; color: #333; font-size: 10.5pt; }}

  .bug {{
    border: 1px solid #d1d5db; border-left: 4px solid #64748b;
    border-radius: 6pt; padding: 9pt 11pt; margin: 10pt 0;
    page-break-inside: avoid; background: #fcfcfd;
  }}
  .bug.sev-critical {{ border-left-color: #c0392b; }}
  .bug.sev-high    {{ border-left-color: #d35400; }}
  .bug.sev-medium  {{ border-left-color: #b7791f; }}
  .bug.sev-low     {{ border-left-color: #2563eb; }}
  .bug-head {{ display: flex; gap: 6pt; flex-wrap: wrap; align-items: center; margin-bottom: 4pt; }}
  .bug-title {{ font-size: 12pt; font-weight: 600; margin: 2pt 0 6pt; color: #111; }}

  .sev {{ display: inline-block; font-size: 8.5pt; font-weight: 700;
          padding: 1.5pt 6pt; border-radius: 3pt; color: #fff; text-transform: uppercase; }}
  .sev-critical {{ background: #c0392b; }}
  .sev-high     {{ background: #d35400; }}
  .sev-medium   {{ background: #b7791f; }}
  .sev-low      {{ background: #2563eb; }}
  .sev-info     {{ background: #64748b; }}

  .id {{ font-family: ui-monospace, Menlo, Consolas, monospace;
         font-size: 9.5pt; background: #eef1f4; padding: 1.5pt 6pt; border-radius: 3pt; }}
  .where {{ font-size: 8.5pt; font-weight: 700; padding: 1.5pt 6pt; border-radius: 3pt;
            letter-spacing: 0.3pt; }}
  .where-server {{ background: #fde2e2; color: #991b1b; }}
  .where-both   {{ background: #fef3c7; color: #854d0e; }}
  .where-client {{ background: #dcfce7; color: #065f46; }}
  .where-none   {{ background: #eef1f4; color: #6b7280; }}
  .screen {{ font-size: 9pt; color: #555; background: #eef1f4; padding: 1.5pt 6pt; border-radius: 3pt; }}

  table {{ width: 100%; border-collapse: collapse; font-size: 10pt; }}
  table.kv th {{ text-align: left; width: 23%; padding: 4pt 6pt; vertical-align: top;
                 color: #444; font-weight: 600; background: #f7f8fa; border-right: 1px solid #e5e7eb; }}
  table.kv td {{ padding: 4pt 8pt; vertical-align: top; }}
  table.kv tr + tr th, table.kv tr + tr td {{ border-top: 1px solid #e5e7eb; }}

  .matrix th, .matrix td {{ border: 1px solid #e5e7eb; padding: 4pt 6pt; font-size: 9pt; }}
  .matrix th {{ background: #f3f4f6; font-weight: 600; }}
  tr.fail {{ background: #fff5f5; }}
  .st {{ display: inline-block; font-size: 9pt; font-weight: 700; padding: 1pt 6pt; border-radius: 3pt; }}
  .st-pass {{ background: #dcfce7; color: #166534; }}
  .st-fail {{ background: #fde2e2; color: #991b1b; }}

  .mono, code {{ font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 9pt; }}

  .fix-grid {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 8pt; margin: 6pt 0 12pt; }}
  .fix-box {{
    border: 1px solid #d1d5db; border-top: 4px solid #9ca3af;
    border-radius: 6pt; padding: 9pt 11pt; background: #fcfcfd;
  }}
  .fix-box.client {{ border-top-color: #16a34a; }}
  .fix-box.both   {{ border-top-color: #f59e0b; }}
  .fix-box.server {{ border-top-color: #dc2626; }}
  .fix-box .big {{ font-size: 24pt; font-weight: 700; margin: 2pt 0; }}
  .fix-box .title {{ font-weight: 700; margin-bottom: 3pt; font-size: 11pt; }}
  .fix-box.client .title {{ color: #15803d; }}
  .fix-box.both   .title {{ color: #92400e; }}
  .fix-box.server .title {{ color: #991b1b; }}
  .fix-box ul {{ margin: 4pt 0 0 14pt; padding: 0; font-size: 10pt; color: #374151; }}
  .fix-box li {{ margin: 2pt 0; }}

  .page-break {{ page-break-before: always; }}
</style>
</head>
<body>

<h1>Transactions — API Test Report</h1>
<div class="meta">
  <b>Project:</b> Hima (Android) &nbsp;·&nbsp;
  <b>Screens:</b> Male Transactions · Female Transactions &nbsp;·&nbsp;
  <b>Endpoints:</b> <code>/transaction_list</code>, <code>/female_transaction</code> &nbsp;·&nbsp;
  <b>Environment:</b> <code>{html_lib.escape(base_url)}</code> &nbsp;·&nbsp;
  <b>Tested by:</b> Perumal (QA) &nbsp;·&nbsp;
  <b>Date:</b> {now}
</div>

<div class="headline">
  <h3>TL;DR for the tech lead</h3>
  <p>
    I ran <b>{total} API test cases</b> against the two transaction endpoints. <b>{passed}</b> passed
    and <b>{failed}</b> failed. The failures map to <b>6 distinct bugs</b>, <b>{fixable_pct}%</b> of which are
    fixable with code we own (the other are documented behaviour, no fix needed).
    One failure is <b>CRITICAL</b>: <code>/transaction_list</code> has an IDOR that leaks every user's
    full financial history to any authenticated user — the sibling <code>/female_transaction</code>
    endpoint already fixed this, so the patch is a copy-paste.
  </p>
</div>

<h2>1. Executive summary</h2>
<p>
  Coverage spans functional, pagination, boundary, negative, security (IDOR / auth / SQLi /
  tampered JWT), HTTP-verb correctness, response-contract and performance p95 categories.
  The female endpoint is the <b>hardened reference</b>: it passes all ownership, validation and
  gender-gate tests. The male endpoint is <b>8 years of drift behind</b> it and accumulates
  most of the findings.
</p>
<div class="cards">
  <div class="card"><div class="n">{total}</div><div class="l">Total tests</div></div>
  <div class="card pass"><div class="n">{passed}</div><div class="l">Passed</div></div>
  <div class="card fail"><div class="n">{failed}</div><div class="l">Failed</div></div>
  <div class="card crit"><div class="n">{crit}</div><div class="l">Critical</div></div>
  <div class="card high"><div class="n">{high}</div><div class="l">High</div></div>
  <div class="card med"><div class="n">{med}</div><div class="l">Medium</div></div>
</div>

<h2>2. Can it be fixed? — Who needs to ship what</h2>
<p class="note">
  Every failure was classified by where the fix actually closes the hole. A fix belongs on the
  <b>backend</b> if a curl-wielding attacker could bypass any client-side guard.
</p>
<div class="fix-grid">
  <div class="fix-box client">
    <div class="title">Android only</div>
    <div class="big">{client_only}</div>
    <ul><li>No bug is purely client-fixable.</li></ul>
  </div>
  <div class="fix-box both">
    <div class="title">Android + Backend</div>
    <div class="big">{both}</div>
    <ul>
      <li>Make non-nullable <code>TransactionsResponse</code> fields nullable to prevent Gson crash.</li>
      <li>Client should show a toast on HTML 500; backend must not emit HTML 500 in the first place.</li>
    </ul>
  </div>
  <div class="fix-box server">
    <div class="title">Backend only</div>
    <div class="big">{server_only}</div>
    <ul>
      <li><b>CRITICAL:</b> Fix IDOR on /transaction_list — bind <code>user_id</code> to JWT sub.</li>
      <li>Cap <code>limit</code> at 100; reject negative offset/limit with JSON 400.</li>
      <li>Always emit JSON 401 for /api/* when unauthenticated (no HTML 302).</li>
    </ul>
  </div>
</div>
<p class="note"><b>Bottom line:</b> <b>{fixable_pct}%</b> of the {failed} failing cases ({fixable} of {failed}) are fixable with code changes we control. 0 require new infrastructure.</p>

<h2 class="page-break">3. Bugs — plain-language breakdown (critical first)</h2>
{"".join(bug_cards)}

<h2 class="page-break">4. Full test matrix ({total} cases)</h2>
<table class="matrix">
  <thead>
    <tr>
      <th>ID</th><th>Screen</th><th>Category</th><th>Test</th><th>Sev</th>
      <th>Status</th><th>HTTP</th><th>Detail</th>
    </tr>
  </thead>
  <tbody>
    {rows_html}
  </tbody>
</table>

<h2>5. Methodology</h2>
<ul class="note">
  <li>Authenticated tests use a real JWT obtained via <code>/login</code> for QA mobile 9876543210.</li>
  <li>Read-only suite — no transaction was created or mutated on any account.</li>
  <li>IDOR probes use a known real female account id (72530) as the foreign <code>user_id</code>.
      Test was read-only: no data was altered on that account.</li>
  <li>Requests are spaced 150 ms apart to avoid triggering throttle middleware.</li>
  <li>Contract checks validate that server responses contain every field the Kotlin data
      classes declare as non-null — a mismatch would crash the Android client at
      deserialization.</li>
  <li>p95 performance budget: 2.0 s per request (stricter than the 8 s used for voice uploads).</li>
  <li>One female-side contract test (TXF-07) could not be fully exercised because the
      ownership guard (correctly) blocks a male token — re-run with a real female QA account
      when one is provisioned.</li>
</ul>

<h2>6. Recommended rollout order</h2>
<ol class="note">
  <li><b>TODAY:</b> Deploy the IDOR fix on <code>/transaction_list</code> (BUG-TX-001).
      One-line change, mirrors <code>/female_transaction</code>.</li>
  <li><b>This sprint:</b> Add FormRequest validators for user_id/offset/limit (closes BUG-TX-003, -004, -005).
      Ship the Kotlin-nullable change for TransactionsResponse (BUG-TX-007) in the same app release.</li>
  <li><b>Next sprint:</b> Force JSON-only responses for /api/* routes (BUG-TX-002).</li>
</ol>

</body>
</html>
"""


def main():
    if not RESULTS.exists():
        print(f"  ✖ {RESULTS.name} not found. Run transactions_api_tests.py first.")
        return 1
    data = json.loads(RESULTS.read_text())
    OUT_HTML.write_text(render(data))
    print(f"  ✓ wrote {OUT_HTML}")

    if not Path(CHROME).exists():
        print(f"  ✖ Chrome not found at {CHROME}. Open the HTML and File→Print→Save as PDF manually.")
        return 1

    cmd = [
        CHROME, "--headless=new", "--disable-gpu",
        f"--print-to-pdf={OUT_PDF}",
        "--no-pdf-header-footer",
        "--virtual-time-budget=4000",
        f"file://{OUT_HTML}",
    ]
    print("  · rendering PDF via Chrome headless …")
    subprocess.run(cmd, check=True)
    print(f"  ✓ wrote {OUT_PDF}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
