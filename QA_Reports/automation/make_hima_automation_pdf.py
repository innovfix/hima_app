#!/usr/bin/env python3
"""
Generate hima_automation_tests.pdf from transactions_api_results.json.
New visual style: light / executive / print-friendly.
Reuses existing results — does not re-run the API suite.
"""
from __future__ import annotations

import html as html_lib
import json
import subprocess
from datetime import datetime
from pathlib import Path

HERE     = Path(__file__).resolve().parent
RESULTS  = HERE / "transactions_api_results.json"
OUT_HTML = HERE / "hima_automation_tests_print.html"
OUT_PDF  = HERE / "hima_automation_tests.pdf"
CHROME   = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


PLAIN = {
    "BUG-TX-001": {
        "what":  "Any logged-in user can read any other user's complete transaction "
                 "history (amount, payment_type, datetime) by sending a different "
                 "user_id in the request body on /transaction_list.",
        "hurts": "CRITICAL. Financial-history privacy breach across all users. Test "
                 "proof: male QA token (id 456976) fetched 272 rows belonging to "
                 "female QA account (id 456740) on demo — same pattern confirmed on "
                 "prod (8,926 rows of user 72530). Reportable under DPDP / GDPR.",
        "fix":   "Backend: derive user_id from the JWT sub claim, ignore the body "
                 "value. The sibling /female_transaction already does this — "
                 "copy that pattern.",
    },
    "BUG-TX-002": {
        "what":  "When a client hits /transaction_list without Accept:application/json, "
                 "the server responds with HTML 302 → /login instead of a JSON 401.",
        "hurts": "Medium. Retrofit sets Accept:json so the Android app is fine today, "
                 "but any third-party client or misconfigured interceptor crashes its "
                 "JSON parser against the HTML response.",
        "fix":   "Backend: force JSON 401 for request()->is('api/*') in Laravel's "
                 "exception handler.",
    },
    "BUG-TX-003": {
        "what":  "Negative offset values (offset=-1) are silently coerced to 0 on "
                 "BOTH /transaction_list AND /female_transaction instead of being "
                 "rejected.",
        "hurts": "Medium. A client with off-by-one pagination math can double-render "
                 "rows without detection. Confirmed on both endpoints: first_id with "
                 "offset=-1 equals first_id with offset=0.",
        "fix":   "Backend: add 'offset' => 'nullable|integer|min:0' validator on both "
                 "endpoints.",
    },
    "BUG-TX-004": {
        "what":  "Sending limit=-1 to either endpoint returns HTTP 500 with the "
                 "Laravel HTML error page instead of a JSON error. Confirmed on BOTH "
                 "/transaction_list and /female_transaction.",
        "hurts": "High. Breaks the Android client (it can't parse HTML as JSON → "
                 "blank screen with no feedback). Information-disclosure risk if "
                 "APP_DEBUG=true on prod.",
        "fix":   "Android: DONE — TransactionsActivity now observes errorLiveData "
                 "and shows a toast. FemaleTransactionsActivity already had this. "
                 "Backend: add 'limit' => 'integer|min:1|max:100' validator — one "
                 "change protects both endpoints.",
    },
    "BUG-TX-005": {
        "what":  "Neither endpoint caps the `limit` parameter. limit=99999 returns "
                 "every row in one response — 272 on demo, 8,926 on prod.",
        "hurts": "High. Two attack surfaces: DoS (a few thousand requests exhaust "
                 "MySQL connections + bandwidth) and mobile OOM (Gson blows up parsing "
                 "on older devices). Confirmed on BOTH endpoints.",
        "fix":   "Backend: add 'limit' => 'nullable|integer|min:1|max:100' validator "
                 "and enforce the cap server-side. One validator protects both "
                 "endpoints.",
    },
    "BUG-TX-007": {
        "what":  "TransactionsResponse.kt originally declared user_name, call_type, "
                 "duration, call_user_name as non-nullable Kotlin Strings, but the "
                 "server never sends them. Confirmed by TXM-02 — 5 rows inspected, "
                 "all 4 fields missing.",
        "hurts": "High (latent). Today Gson silently defaults to null, violating "
                 "Kotlin's null contract. On a Kotlin 1.9+ upgrade with strict "
                 "null safety the Transactions screen crashes on first deserialize.",
        "fix":   "Android: DONE — fields are now nullable in the data class, adapter "
                 "code handles null via .orEmpty(). Backend: still should emit the "
                 "documented keys (empty string fallback) for contract stability.",
    },
}
EXTRA: dict[str, dict[str, str]] = {}


def render(data: dict) -> str:
    results = data.get("results", [])
    base_url = data.get("base_url", "")
    male_id   = data.get("male_user_id", "?")
    female_id = data.get("female_user_id", "?")

    total  = len(results)
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = total - passed
    fails  = [r for r in results if r["status"] == "FAIL"]
    crit = sum(1 for r in fails if r["severity"] == "critical")
    high = sum(1 for r in fails if r["severity"] == "high")
    med  = sum(1 for r in fails if r["severity"] == "medium")

    server_only = sum(1 for r in fails if r.get("fix_where") == "server")
    both        = sum(1 for r in fails if r.get("fix_where") == "both")
    client_only = sum(1 for r in fails if r.get("fix_where") == "client")
    fixable     = server_only + both + client_only
    fixable_pct = 0 if failed == 0 else int(round(100 * fixable / failed))

    # Group results by screen for the section-by-section matrix
    screens: dict[str, list[dict]] = {}
    for r in results:
        screens.setdefault(r["screen"], []).append(r)

    # Bug cards sorted critical first
    sev_order = {"critical": 0, "high": 1, "medium": 2, "low": 3, "info": 4}
    bug_cards = []
    seen = set()
    for r in sorted(fails, key=lambda x: (sev_order.get(x["severity"], 9), x["id"])):
        bid = r.get("bug_ref")
        key = bid or r["id"]
        if key in seen:
            continue
        seen.add(key)
        plain = PLAIN.get(bid) or EXTRA.get(r["id"]) or {
            "what":  r.get("title", ""),
            "hurts": r.get("why") or "",
            "fix":   (r.get("fix_server") or r.get("fix_android") or "").strip(),
        }
        fix_w = r.get("fix_where") or "none"
        file_ref_row = (f'<tr><th>Source reference</th><td class="mono">'
                        f'{html_lib.escape(r.get("file_ref",""))}</td></tr>'
                        if r.get("file_ref") else "")
        bug_cards.append(f"""
<article class="bug sev-{r['severity']}">
  <header>
    <span class="bug-id">{html_lib.escape(bid or r['id'])}</span>
    <span class="pill sev-{r['severity']}">{r['severity'].upper()}</span>
    <span class="pill fix-{fix_w}">Fix: {fix_w.upper()}</span>
    <span class="pill screen">{html_lib.escape(r.get('screen',''))}</span>
  </header>
  <h4>{html_lib.escape(r.get('title',''))}</h4>
  <table class="kv">
    <tr><th>What's wrong</th><td>{html_lib.escape(plain['what'])}</td></tr>
    <tr><th>Why it matters</th><td>{html_lib.escape(plain['hurts'])}</td></tr>
    <tr><th>How to fix</th><td>{html_lib.escape(plain['fix'])}</td></tr>
    {file_ref_row}
    <tr><th>Server response</th><td class="mono">{html_lib.escape((r.get('body_preview') or '').strip()[:260])}</td></tr>
  </table>
</article>""")

    # Matrix by section
    matrix_sections_html = ""
    for screen_name, rows in screens.items():
        rows_html = "".join(
            f"""<tr class="{r['status'].lower()}">
  <td class="mono">{html_lib.escape(r['id'])}</td>
  <td>{html_lib.escape(r['category'])}</td>
  <td>{html_lib.escape(r['title'])[:98]}</td>
  <td><span class="pill sev-{r['severity']}">{r['severity']}</span></td>
  <td class="{'pass' if r['status']=='PASS' else 'fail'}">{r['status']}</td>
  <td class="mono">{html_lib.escape(str(r.get('code','')))}</td>
  <td>{html_lib.escape(r.get('detail','')[:115])}</td>
</tr>"""
            for r in rows
        )
        sp = sum(1 for r in rows if r["status"] == "PASS")
        sf = sum(1 for r in rows if r["status"] == "FAIL")
        matrix_sections_html += f"""
<section class="matrix-block">
  <h3>{html_lib.escape(screen_name)} <span class="sec-count">({len(rows)} tests · {sp} pass · {sf} fail)</span></h3>
  <table class="matrix">
    <thead>
      <tr>
        <th>ID</th><th>Category</th><th>Test</th>
        <th>Sev</th><th>Status</th><th>HTTP</th><th>Detail</th>
      </tr>
    </thead>
    <tbody>{rows_html}</tbody>
  </table>
</section>"""

    now = datetime.now().strftime("%Y-%m-%d %H:%M")

    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Hima — Automation Tests Report</title>
<style>
  @page {{ size: A4; margin: 18mm 16mm 22mm 16mm; }}
  @page {{ @bottom-center {{ content: "Hima Automation Tests · " counter(page) " / " counter(pages); font-size: 8.5pt; color: #9ca3af; }} }}
  * {{ box-sizing: border-box; }}
  body {{
    font-family: "Inter", -apple-system, "Segoe UI", Roboto, Arial, sans-serif;
    color: #1e293b; font-size: 10.5pt; line-height: 1.55; margin: 0;
    background: #ffffff;
  }}

  /* ══════════════════ Cover page ══════════════════ */
  .cover {{
    min-height: 250mm;
    display: flex; flex-direction: column; justify-content: space-between;
    padding: 14mm 2mm 8mm;
  }}
  .cover-hero {{ margin-top: 30mm; }}
  .cover-label {{
    color: #6366f1; font-size: 10pt; font-weight: 700;
    letter-spacing: 0.22em; text-transform: uppercase; margin-bottom: 14pt;
  }}
  .cover h1 {{
    font-size: 36pt; font-weight: 800; color: #0f172a; margin: 0 0 6pt;
    letter-spacing: -0.02em; line-height: 1.1;
  }}
  .cover .subtitle {{
    font-size: 14pt; color: #475569; margin: 0 0 40pt; font-weight: 400;
  }}
  .cover .cover-grid {{
    display: grid; grid-template-columns: 1fr 1fr; gap: 14pt 24pt;
    margin-top: 26pt;
  }}
  .cover .cover-grid .kv-row {{
    display: flex; gap: 10pt; align-items: baseline;
    padding-bottom: 6pt; border-bottom: 1px solid #e2e8f0;
  }}
  .cover .cover-grid .k {{
    font-size: 8.5pt; color: #64748b; letter-spacing: 0.1em;
    text-transform: uppercase; min-width: 88pt; font-weight: 600;
  }}
  .cover .cover-grid .v {{
    font-size: 11pt; color: #0f172a; font-weight: 500;
  }}
  .cover-footer {{
    color: #94a3b8; font-size: 9pt; border-top: 3px solid #6366f1;
    padding-top: 8pt;
  }}
  .cover-footer b {{ color: #475569; }}
  .cover-scorecard {{
    display: grid; grid-template-columns: repeat(6, 1fr); gap: 6pt;
    margin: 20pt 0;
  }}
  .cover-scorecard .s {{
    border: 1.5pt solid #e2e8f0; border-radius: 8pt;
    padding: 10pt 6pt; text-align: center; background: #f8fafc;
  }}
  .cover-scorecard .s .num {{ font-size: 22pt; font-weight: 800; color: #0f172a; line-height: 1; }}
  .cover-scorecard .s .lab {{ font-size: 8pt; color: #64748b; margin-top: 4pt; letter-spacing: 0.05em; text-transform: uppercase; font-weight: 600; }}
  .cover-scorecard .s.pass {{ border-color: #86efac; background: #f0fdf4; }}
  .cover-scorecard .s.pass .num {{ color: #15803d; }}
  .cover-scorecard .s.fail {{ border-color: #fca5a5; background: #fef2f2; }}
  .cover-scorecard .s.fail .num {{ color: #b91c1c; }}
  .cover-scorecard .s.crit {{ border-color: #fda4af; background: #fff1f2; }}
  .cover-scorecard .s.crit .num {{ color: #be123c; }}
  .cover-scorecard .s.high .num {{ color: #ea580c; }}
  .cover-scorecard .s.med .num {{ color: #ca8a04; }}

  /* ══════════════════ Section headings ══════════════════ */
  h2 {{
    font-size: 15pt; color: #0f172a; font-weight: 700;
    margin: 26pt 0 10pt; padding-bottom: 6pt;
    border-bottom: 2pt solid #6366f1; letter-spacing: -0.01em;
  }}
  h2 .num {{
    display: inline-block; font-size: 10pt; color: #6366f1;
    font-weight: 700; margin-right: 8pt;
  }}
  h3 {{ font-size: 12pt; color: #1e293b; font-weight: 700; margin: 14pt 0 5pt; }}
  h4 {{ font-size: 11pt; color: #0f172a; font-weight: 600; margin: 6pt 0 8pt; }}
  .sec-count {{ color: #94a3b8; font-size: 9pt; font-weight: 500; margin-left: 6pt; }}

  .lead {{ font-size: 11pt; color: #334155; margin: 4pt 0 10pt; }}
  .muted {{ color: #64748b; font-size: 9.5pt; }}

  /* ══════════════════ TL;DR banner ══════════════════ */
  .tldr {{
    background: linear-gradient(135deg, #fef2f2 0%, #fff7ed 100%);
    border: 1pt solid #fed7aa; border-left: 4pt solid #dc2626;
    border-radius: 8pt; padding: 12pt 14pt; margin: 10pt 0 18pt;
  }}
  .tldr h3 {{ margin: 0 0 4pt; color: #991b1b; font-size: 12pt; }}
  .tldr p {{ margin: 4pt 0 0; color: #334155; font-size: 10.5pt; line-height: 1.6; }}

  /* ══════════════════ Fix-ownership cards ══════════════════ */
  .fix-grid {{
    display: grid; grid-template-columns: repeat(3, 1fr);
    gap: 10pt; margin: 10pt 0 8pt;
  }}
  .fix-card {{
    border: 1pt solid #e2e8f0; border-radius: 10pt; overflow: hidden;
    background: #ffffff;
  }}
  .fix-card .head {{
    padding: 10pt 12pt 8pt; color: #ffffff; font-weight: 700;
    font-size: 10pt; letter-spacing: 0.02em;
  }}
  .fix-card.android .head {{ background: #16a34a; }}
  .fix-card.both    .head {{ background: #d97706; }}
  .fix-card.server  .head {{ background: #dc2626; }}
  .fix-card .body   {{ padding: 10pt 12pt; }}
  .fix-card .big    {{ font-size: 32pt; font-weight: 800; line-height: 1; color: #0f172a; }}
  .fix-card .big small {{ font-size: 14pt; font-weight: 500; color: #94a3b8; }}
  .fix-card ul      {{ margin: 8pt 0 0 16pt; padding: 0; font-size: 9.5pt; color: #334155; }}
  .fix-card li      {{ margin: 3pt 0; }}
  .fix-line {{ margin-top: 8pt; font-size: 10pt; color: #475569; }}
  .fix-line b {{ color: #0f172a; }}

  /* ══════════════════ Bug cards ══════════════════ */
  .bug {{
    border: 1pt solid #e2e8f0; border-radius: 10pt;
    padding: 10pt 12pt; margin: 10pt 0; background: #ffffff;
    page-break-inside: avoid;
  }}
  .bug.sev-critical {{ border-left: 4pt solid #dc2626; }}
  .bug.sev-high     {{ border-left: 4pt solid #ea580c; }}
  .bug.sev-medium   {{ border-left: 4pt solid #ca8a04; }}
  .bug.sev-low      {{ border-left: 4pt solid #0284c7; }}
  .bug header {{
    display: flex; gap: 6pt; align-items: center; flex-wrap: wrap;
    margin-bottom: 4pt;
  }}
  .bug-id {{
    font-family: ui-monospace, Menlo, Consolas, monospace;
    font-size: 9pt; background: #f1f5f9; color: #334155;
    padding: 2pt 8pt; border-radius: 4pt; font-weight: 600;
  }}

  .pill {{
    display: inline-block; font-size: 8pt; font-weight: 700;
    padding: 2pt 7pt; border-radius: 999pt; letter-spacing: 0.04em;
    text-transform: uppercase;
  }}
  .pill.sev-critical {{ background: #fee2e2; color: #991b1b; }}
  .pill.sev-high     {{ background: #ffedd5; color: #9a3412; }}
  .pill.sev-medium   {{ background: #fef3c7; color: #854d0e; }}
  .pill.sev-low      {{ background: #dbeafe; color: #1e40af; }}
  .pill.sev-info     {{ background: #e0e7ff; color: #3730a3; }}
  .pill.fix-server   {{ background: #fee2e2; color: #991b1b; }}
  .pill.fix-both     {{ background: #fef3c7; color: #854d0e; }}
  .pill.fix-client, .pill.fix-android   {{ background: #dcfce7; color: #166534; }}
  .pill.fix-none     {{ background: #f1f5f9; color: #64748b; }}
  .pill.screen       {{ background: #eef2ff; color: #3730a3; }}

  /* ══════════════════ Key-value tables ══════════════════ */
  table {{ width: 100%; border-collapse: collapse; font-size: 10pt; }}
  table.kv th {{
    text-align: left; width: 22%; padding: 5pt 8pt; vertical-align: top;
    color: #475569; font-weight: 600; background: #f8fafc;
    border-right: 1pt solid #e2e8f0;
  }}
  table.kv td {{ padding: 5pt 8pt; vertical-align: top; color: #1e293b; }}
  table.kv tr + tr th, table.kv tr + tr td {{ border-top: 1pt solid #e2e8f0; }}

  /* ══════════════════ Matrix tables ══════════════════ */
  .matrix-block {{ page-break-inside: avoid; margin: 6pt 0 14pt; }}
  .matrix {{
    border: 1pt solid #e2e8f0; border-radius: 6pt; overflow: hidden;
    font-size: 9pt;
  }}
  .matrix th {{
    background: #f8fafc; color: #475569; font-weight: 600;
    font-size: 8pt; text-transform: uppercase; letter-spacing: 0.05em;
    padding: 5pt 7pt; text-align: left; border-bottom: 1pt solid #e2e8f0;
  }}
  .matrix td {{
    padding: 5pt 7pt; border-bottom: 1pt solid #f1f5f9;
    vertical-align: top; color: #334155;
  }}
  .matrix tr.fail {{ background: #fef2f2; }}
  .matrix td.pass, .matrix td.fail {{ font-weight: 700; font-size: 8.5pt; letter-spacing: 0.04em; }}
  .matrix td.pass {{ color: #15803d; }}
  .matrix td.fail {{ color: #b91c1c; }}

  .mono, code {{
    font-family: ui-monospace, Menlo, Consolas, monospace;
    font-size: 8.5pt; color: #475569;
  }}

  /* ══════════════════ Tables of contents ══════════════════ */
  .toc {{
    margin: 10pt 0 18pt; padding: 10pt 14pt;
    background: #f8fafc; border-radius: 8pt;
    border: 1pt solid #e2e8f0;
  }}
  .toc ol {{ margin: 4pt 0; padding-left: 22pt; color: #334155; }}
  .toc li {{ margin: 3pt 0; font-size: 10pt; }}

  .page-break {{ page-break-before: always; }}

  ol.rollout li {{ margin: 4pt 0; color: #334155; font-size: 10pt; }}
  ol.rollout li b {{ color: #0f172a; }}
</style>
</head>
<body>

<!-- ══════════════════ COVER PAGE ══════════════════ -->
<div class="cover">
  <div class="cover-hero">
    <div class="cover-label">QA · Automation Tests · API Layer</div>
    <h1>Hima Automation Tests</h1>
    <p class="subtitle">Transactions module — dual-account coverage · {total} automated checks</p>

    <div class="cover-scorecard">
      <div class="s"><div class="num">{total}</div><div class="lab">Total</div></div>
      <div class="s pass"><div class="num">{passed}</div><div class="lab">Pass</div></div>
      <div class="s fail"><div class="num">{failed}</div><div class="lab">Fail</div></div>
      <div class="s crit"><div class="num">{crit}</div><div class="lab">Critical</div></div>
      <div class="s high"><div class="num">{high}</div><div class="lab">High</div></div>
      <div class="s med"><div class="num">{med}</div><div class="lab">Medium</div></div>
    </div>

    <div class="cover-grid">
      <div class="kv-row"><span class="k">Project</span><span class="v">Hima · Android</span></div>
      <div class="kv-row"><span class="k">Target screens</span><span class="v">Male + Female Transactions</span></div>
      <div class="kv-row"><span class="k">Endpoints</span><span class="v">/transaction_list<br>/female_transaction</span></div>
      <div class="kv-row"><span class="k">Environment</span><span class="v">{html_lib.escape(base_url)}</span></div>
      <div class="kv-row"><span class="k">Test accounts</span><span class="v">Male id {male_id} · Female id {female_id}</span></div>
      <div class="kv-row"><span class="k">Suite type</span><span class="v">Read-only API automation</span></div>
      <div class="kv-row"><span class="k">Automated by</span><span class="v">transactions_api_tests.py</span></div>
      <div class="kv-row"><span class="k">QA lead</span><span class="v">Perumal</span></div>
    </div>
  </div>

  <div class="cover-footer">
    <b>Report generated:</b> {now} &nbsp;·&nbsp;
    <b>Suite version:</b> v2 (dual-account)
  </div>
</div>

<!-- ══════════════════ EXECUTIVE SUMMARY ══════════════════ -->
<div class="page-break"></div>

<h2><span class="num">01</span> Executive summary</h2>

<div class="tldr">
  <h3>For the tech lead — one paragraph</h3>
  <p>
    Automated API tests ran {total} checks against the two transaction endpoints with both
    a male and a female logged-in account. <b>{passed}</b> passed and <b>{failed}</b> failed.
    Failures map to <b>6 distinct bugs</b>, <b>{fixable_pct}%</b> fixable with code we own.
    One is <b>CRITICAL</b>: /transaction_list exposes any user's financial history to any
    authenticated caller. The sibling /female_transaction endpoint already enforces the
    right check — the fix is a copy-paste.
  </p>
</div>

<p class="lead">
  Coverage spans functional, pagination, boundary, negative, security (IDOR, auth, SQLi,
  gender-gate, cross-endpoint), HTTP-verb correctness, response-contract and performance
  p95 categories. The female endpoint is the hardened reference — nearly every failure
  is on the male endpoint or shared across both.
</p>

<!-- ══════════════════ WHO SHIPS WHAT ══════════════════ -->
<h2><span class="num">02</span> Who needs to ship what</h2>

<p class="lead">
  Every failure is classified by where the fix closes the hole. A fix belongs to the
  backend if a curl-wielding attacker bypasses any client-side guard.
</p>

<div class="fix-grid">
  <div class="fix-card android">
    <div class="head">Android only</div>
    <div class="body">
      <div class="big">{client_only}<small> / {failed}</small></div>
      <ul><li>No bug is purely client-fixable in this suite.</li></ul>
    </div>
  </div>
  <div class="fix-card both">
    <div class="head">Android + Backend</div>
    <div class="body">
      <div class="big">{both}<small> / {failed}</small></div>
      <ul>
        <li>TransactionsResponse schema (client nullable ✓ · server should emit keys)</li>
        <li>HTML 500 on limit=-1 — client error observer ✓ · server validator needed</li>
      </ul>
    </div>
  </div>
  <div class="fix-card server">
    <div class="head">Backend only</div>
    <div class="body">
      <div class="big">{server_only}<small> / {failed}</small></div>
      <ul>
        <li><b>CRITICAL:</b> IDOR on /transaction_list — bind user_id to JWT sub</li>
        <li>Cap `limit` at 100 on both endpoints</li>
        <li>Reject negative offset with 400 JSON</li>
        <li>Force JSON 401 on /api/* when unauthenticated</li>
      </ul>
    </div>
  </div>
</div>
<p class="fix-line"><b>Bottom line:</b> <b>{fixable_pct}%</b> of the {failed} failing cases ({fixable} of {failed}) are addressable with code we control. Zero need new infrastructure.</p>

<!-- ══════════════════ BUGS ══════════════════ -->
<h2 class="page-break"><span class="num">03</span> Bugs — plain-language breakdown</h2>
<p class="muted">Ordered critical first. Each entry lists what's wrong, why it hurts, and how to fix.</p>

{"".join(bug_cards)}

<!-- ══════════════════ FULL MATRIX ══════════════════ -->
<h2 class="page-break"><span class="num">04</span> Full test matrix</h2>
<p class="muted">All {total} cases grouped by screen. Failing rows highlighted.</p>

{matrix_sections_html}

<!-- ══════════════════ METHODOLOGY + ROLLOUT ══════════════════ -->
<h2 class="page-break"><span class="num">05</span> Methodology</h2>
<ul class="muted">
  <li>Both accounts authenticated via <code>/login</code> with QA backdoor OTP <code>011011</code>.</li>
  <li>Male account: <code>9876543210</code> → id {male_id} · Female account: <code>9120444444</code> → id {female_id}.</li>
  <li>Read-only suite — no call, recharge, gift, profile change or transaction was created on any account.</li>
  <li>Cross-account (TXX-*) cases use the <em>other</em> account's id to probe ownership / gender enforcement.</li>
  <li>150 ms inter-request delay to avoid throttle middleware.</li>
  <li>Contract checks validate that response rows contain every field the Kotlin data class declares.</li>
  <li>p95 performance budget: 2.0 s per request.</li>
  <li>Manual on-device verification of the Android patches (BUG-TX-004, BUG-TX-007) is performed by Perumal — not by the automation script.</li>
</ul>

<h2><span class="num">06</span> Recommended rollout order</h2>
<ol class="rollout">
  <li><b>TODAY:</b> Deploy the IDOR fix on /transaction_list (BUG-TX-001). One-line change, mirrors /female_transaction. Closes the critical financial-data leak.</li>
  <li><b>This sprint:</b> Add FormRequest validators for user_id / offset / limit (closes BUG-TX-003, BUG-TX-004, BUG-TX-005 on both endpoints). Ship the already-merged Kotlin nullable change for TransactionsResponse (BUG-TX-007) in the next app release.</li>
  <li><b>Next sprint:</b> Force JSON-only responses for /api/* routes (BUG-TX-002). Contract cleanup.</li>
</ol>

</body>
</html>
"""


def main():
    if not RESULTS.exists():
        print(f"  ✖ {RESULTS.name} not found.")
        return 1
    data = json.loads(RESULTS.read_text())
    OUT_HTML.write_text(render(data))
    print(f"  ✓ wrote {OUT_HTML}")

    if not Path(CHROME).exists():
        print(f"  ✖ Chrome not found at {CHROME}.")
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
