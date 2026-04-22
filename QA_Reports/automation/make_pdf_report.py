#!/usr/bin/env python3
"""
Generate a tech-lead-friendly PDF from edit_profile_voice_results.json.
Uses Chrome headless for HTML → PDF.
"""
from __future__ import annotations

import html as html_lib
import json
import shutil
import subprocess
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
RESULTS = HERE / "edit_profile_voice_results.json"
OUT_HTML = HERE / "edit_profile_voice_report_print.html"
OUT_PDF  = HERE / "Hima_EditProfile_Voice_API_Report.pdf"

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# ────────────── Plain-language summaries per bug ID ──────────────
# Each entry: plain "what's wrong" + plain "why it hurts" + plain "fix".
PLAIN = {
    "BUG-EP-005": {
        "what": "Anyone logged in can validate a name as if they were a different user.",
        "hurts": "Lets an attacker probe which user IDs exist and what names are taken on other accounts.",
        "fix":  "Backend: take the user_id from the login token, ignore the one sent in the request body.",
    },
    "BUG-EP-008": {
        "what": "The avatar list API works without a login token at all.",
        "hurts": "Low severity, but any scraper can pull the whole avatar catalogue without signing in. Reduces attack surface to have one consistent auth policy.",
        "fix":  "Backend: put this route behind the same auth middleware as the rest.",
    },
    "BUG-EP-010": {
        "what": "A logged-in user can overwrite another user's profile (name, avatar, interests) by sending a different user_id.",
        "hurts": "CRITICAL. This is close to account takeover — attacker can wipe, impersonate or defame any user with a single API call.",
        "fix":  "Backend: derive user_id from the login token, not from the request body. Apply to every endpoint that takes user_id.",
    },
    "BUG-EP-012": {
        "what": "Sending an update-profile request without a 'name' field succeeds — profile keeps nothing in the name column.",
        "hurts": "Lets an attacker or buggy client blank out a user's display name. Users then show up as empty in chat lists.",
        "fix":  "Backend: add a required|min:4|max:10 validator on the name field and return a clean 400 JSON error.",
    },
    "BUG-EP-013": {
        "what": "The server accepts 30 interests, even though the app UI caps at 5.",
        "hurts": "Medium. Bloats profile data; users from patched APKs or curl can pollute recommendation features that read the interests column.",
        "fix":  "Backend: validate interests as an array with max 5, only from the known allow-list.",
    },
    "BUG-EP-015": {
        "what": "Update-profile is crashing with HTTP 500 'Server Error' (plain Laravel error page) instead of returning a clean JSON error.",
        "hurts": "Critical for UX: the app shows the generic 'please try again later' toast instead of the real reason (e.g. 'you can change your name only once'). Also leaks the framework identity to anyone probing the API.",
        "fix":  "Backend: wrap the 'name change only once' check in a proper validator / try-catch and return {success:false, message:'…'} JSON — never a 500 HTML.",
    },
    "BUG-VV-002": {
        "what": "The speech-text API (which returns the sentence the user must read aloud) works without a login token.",
        "hurts": "Anyone can scrape the entire prompt catalogue. That makes it easy to bot / automate the voice-verification step.",
        "fix":  "Backend: route must require auth.",
    },
    "BUG-VV-006": {
        "what": "A logged-in user can replace someone else's voice recording by passing a different user_id. Test confirmed we actually overwrote another user's voice.",
        "hurts": "CRITICAL. Voice verification is the gating step for female creators. This lets someone sabotage a rival creator (replace their voice with gibberish → they get flagged / de-verified).",
        "fix":  "Backend: derive user_id from the JWT only. Same pattern as BUG-EP-010.",
    },
    "BUG-VV-008": {
        "what": "The server accepts a 10 MB upload on /update_voice — no size limit observed.",
        "hurts": "Medium. Storage-DoS risk — an attacker can upload gigabytes of junk over time and inflate S3 bills.",
        "fix":  "Client: cap recording duration. Backend: add `max:10240` (10 MB) validator + nginx body-size guard.",
    },
}

# Also add plain text for the two failing cases that have no bug_ref but still mattered.
EXTRA = {
    "EP-UP-01": {
        "what": "The happy-path profile update (valid name + 1 interest) returned HTTP 500.",
        "hurts": "Critical. Normal user actions are crashing the backend on some accounts.",
        "fix":  "Backend: investigate the 500 in /update_profile — see BUG-EP-015 fix.",
    },
    "EP-UP-05": {
        "what": "Missing 'name' field accepted silently by /update_profile.",
        "hurts": "See BUG-EP-012.",
        "fix":  "Same as BUG-EP-012.",
    },
    "EP-UP-09": {
        "what": "Changing the name a second time crashes with HTTP 500 instead of clean 'You can change your name only once.' message.",
        "hurts": "See BUG-EP-015.",
        "fix":  "Same as BUG-EP-015.",
    },
}


def sev_color(sev: str) -> str:
    return {"critical": "#c0392b", "high": "#d35400",
            "medium": "#b7791f", "low": "#2563eb",
            "info": "#64748b"}.get(sev, "#64748b")


def render(results: list[dict]) -> str:
    total = len(results)
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = total - passed

    fails = [r for r in results if r["status"] == "FAIL"]
    crit = sum(1 for r in fails if r["severity"] == "critical")
    high = sum(1 for r in fails if r["severity"] == "high")
    med = sum(1 for r in fails if r["severity"] == "medium")
    low = sum(1 for r in fails if r["severity"] in ("low", "info"))

    server_only = sum(1 for r in fails if r.get("fix_where") == "server")
    both = sum(1 for r in fails if r.get("fix_where") == "both")
    client_only = sum(1 for r in fails if r.get("fix_where") == "client")

    bug_cards = []
    # First, dedup by bug_ref; include EXTRA for unnamed fails with useful context.
    seen_bug = set()
    for r in fails:
        bid = r.get("bug_ref")
        key = bid or r["id"]
        if key in seen_bug:
            continue
        seen_bug.add(key)
        plain = PLAIN.get(bid) if bid else EXTRA.get(r["id"])
        # Fallback if no plain summary
        if not plain:
            plain = {
                "what": r.get("title", ""),
                "hurts": r.get("why") or "",
                "fix":  (r.get("fix_server") or r.get("fix_android") or "").strip(),
            }

        bug_cards.append(f"""
<section class="bug">
  <div class="bug-head">
    <span class="sev sev-{r['severity']}">{r['severity'].upper()}</span>
    <span class="id">{html_lib.escape(bid or r['id'])}</span>
    <span class="where where-{r.get('fix_where') or 'none'}">FIX: {(r.get('fix_where') or 'N/A').upper()}</span>
    <span class="screen">{html_lib.escape(r.get('screen',''))}</span>
  </div>
  <h3 class="bug-title">{html_lib.escape(r.get('title',''))}</h3>
  <table class="kv">
    <tr><th>What's wrong</th><td>{html_lib.escape(plain['what'])}</td></tr>
    <tr><th>Why it matters</th><td>{html_lib.escape(plain['hurts'])}</td></tr>
    <tr><th>How to fix</th><td>{html_lib.escape(plain['fix'])}</td></tr>
    {f'<tr><th>Source ref</th><td class="mono">{html_lib.escape(r.get("file_ref"))}</td></tr>' if r.get('file_ref') else ''}
    <tr><th>Server response observed</th><td class="mono">{html_lib.escape((r.get('body_preview') or '').strip()[:220])}</td></tr>
  </table>
</section>""")

    rows = []
    for r in results:
        st = r["status"]
        rows.append(f"""
<tr class="{st.lower()}">
  <td class="mono">{html_lib.escape(r['id'])}</td>
  <td>{html_lib.escape(r['screen'])}</td>
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
<title>Hima — Edit Profile & Voice Verification API Test Report</title>
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
  .note {{ color: #555; font-size: 10pt; }}
  .cards {{
    display: flex; flex-wrap: wrap; gap: 8pt; margin: 6pt 0 12pt;
  }}
  .card {{
    flex: 1 1 110pt; min-width: 110pt; padding: 10pt; border: 1px solid #d1d5db;
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

  .bug {{
    border: 1px solid #d1d5db; border-left: 4px solid #64748b;
    border-radius: 6pt; padding: 9pt 11pt; margin: 10pt 0;
    page-break-inside: avoid; background: #fcfcfd;
  }}
  .bug.crit {{ border-left-color: #c0392b; }}
  .bug-head {{ display: flex; gap: 6pt; flex-wrap: wrap; align-items: center; margin-bottom: 4pt; }}
  .bug-title {{ font-size: 12pt; font-weight: 600; margin: 2pt 0 6pt; color: #111; }}

  .sev {{ display: inline-block; font-size: 8.5pt; font-weight: 700;
          padding: 1.5pt 6pt; border-radius: 3pt; color: #fff; text-transform: uppercase; }}
  .sev-critical {{ background: #c0392b; }}
  .sev-high {{ background: #d35400; }}
  .sev-medium {{ background: #b7791f; }}
  .sev-low {{ background: #2563eb; }}
  .sev-info {{ background: #64748b; }}

  .id {{ font-family: ui-monospace, Menlo, Consolas, monospace;
         font-size: 9.5pt; background: #eef1f4; padding: 1.5pt 6pt; border-radius: 3pt; }}
  .where {{ font-size: 8.5pt; font-weight: 700; padding: 1.5pt 6pt; border-radius: 3pt;
            letter-spacing: 0.3pt; }}
  .where-server {{ background: #fde2e2; color: #991b1b; }}
  .where-both {{ background: #fef3c7; color: #854d0e; }}
  .where-client {{ background: #dcfce7; color: #065f46; }}
  .where-none {{ background: #eef1f4; color: #6b7280; }}
  .screen {{ font-size: 9pt; color: #555; background: #eef1f4; padding: 1.5pt 6pt; border-radius: 3pt; }}

  table {{ width: 100%; border-collapse: collapse; font-size: 10pt; }}
  table.kv th {{ text-align: left; width: 25%; padding: 4pt 6pt; vertical-align: top;
                 color: #444; font-weight: 600; background: #f7f8fa; border-right: 1px solid #e5e7eb; }}
  table.kv td {{ padding: 4pt 8pt; vertical-align: top; }}
  table.kv tr + tr th, table.kv tr + tr td {{ border-top: 1px solid #e5e7eb; }}

  .matrix th, .matrix td {{ border: 1px solid #e5e7eb; padding: 4pt 6pt; font-size: 9.5pt; }}
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
  .fix-box.both .title {{ color: #92400e; }}
  .fix-box.server .title {{ color: #991b1b; }}
  .fix-box ul {{ margin: 4pt 0 0 14pt; padding: 0; font-size: 10pt; color: #374151; }}
  .fix-box li {{ margin: 2pt 0; }}

  .page-break {{ page-break-before: always; }}
</style>
</head>
<body>

<h1>Edit Profile &amp; Voice Verification — API Test Report</h1>
<div class="meta">
  <b>Project:</b> Hima (Android app) &nbsp;·&nbsp;
  <b>Environment:</b> https://demohima.himaapp.in/api/auth &nbsp;·&nbsp;
  <b>Tested by:</b> Perumal (QA) &nbsp;·&nbsp;
  <b>Date:</b> {now}
</div>

<h2>1. Executive summary</h2>
<p>
  I ran {total} API test cases covering the two screens' five endpoints —
  <code>/user_validations</code>, <code>/avatar_list</code>, <code>/update_profile</code>,
  <code>/speech_text</code>, <code>/update_voice</code> — across functional,
  validation, security, boundary and performance categories.
</p>
<div class="cards">
  <div class="card"><div class="n">{total}</div><div class="l">Total tests</div></div>
  <div class="card pass"><div class="n">{passed}</div><div class="l">Passed</div></div>
  <div class="card fail"><div class="n">{failed}</div><div class="l">Failed</div></div>
  <div class="card crit"><div class="n">{crit}</div><div class="l">Critical</div></div>
  <div class="card high"><div class="n">{high}</div><div class="l">High</div></div>
  <div class="card med"><div class="n">{med}</div><div class="l">Medium</div></div>
</div>

<h2>2. Who needs to fix what</h2>
<p class="note">
  Every failure was classified by where the fix actually closes the hole.
  A fix belongs to the <b>server</b> if a curl-wielding attacker would bypass any client-side guard.
</p>
<div class="fix-grid">
  <div class="fix-box client">
    <div class="title">Android only</div>
    <div class="big">{client_only}</div>
    <ul><li>No bug is purely client-fixable — the app is well-behaved.</li></ul>
  </div>
  <div class="fix-box both">
    <div class="title">Android + Backend</div>
    <div class="big">{both}</div>
    <ul><li>Voice upload size cap — client should limit recording duration; backend must enforce byte cap.</li></ul>
  </div>
  <div class="fix-box server">
    <div class="title">Backend only</div>
    <div class="big">{server_only}</div>
    <ul>
      <li>Fix IDOR on /update_profile, /update_voice, /user_validations — use JWT sub, not body user_id.</li>
      <li>Add auth middleware on /avatar_list, /speech_text.</li>
      <li>Replace HTTP 500 crashes with JSON error responses.</li>
      <li>Add FormRequest validators (name, interests, file size).</li>
    </ul>
  </div>
</div>
<p class="note"><b>Bottom line:</b> {server_only + both} of {failed} bugs need backend work. 0 can be closed by Android alone.</p>

<h2 class="page-break">3. Bugs — plain-language breakdown</h2>
{"".join(bug_cards)}

<h2 class="page-break">4. Full test matrix ({total} cases)</h2>
<table class="matrix">
  <thead>
    <tr>
      <th>ID</th><th>Screen</th><th>Test</th><th>Sev</th>
      <th>Status</th><th>HTTP</th><th>Detail</th>
    </tr>
  </thead>
  <tbody>
    {rows_html}
  </tbody>
</table>

<h2>5. Methodology</h2>
<ul class="note">
  <li>Tests authenticated with a real JWT obtained via <code>/login</code> for QA mobile 9876543210.</li>
  <li>IDOR tests use user_id − 1 to stay within valid DB range while probing cross-user access.</li>
  <li>Voice tests use synthesised silent MP3 / WAV fixtures — no real user voice recorded.</li>
  <li>Each request includes 200 ms spacing to avoid triggering throttle middleware.</li>
  <li>Profile mutated during the suite is restored to its original state at the end.</li>
</ul>

</body>
</html>
"""


def main():
    results = json.loads(RESULTS.read_text()).get("results", [])
    if not results:
        print("No results found. Run edit_profile_voice_api_tests.py first.")
        return 1
    OUT_HTML.write_text(render(results))
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
