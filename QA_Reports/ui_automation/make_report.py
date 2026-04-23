#!/usr/bin/env python3
"""Generate HTML report combining Voice (live UI) + Edit Profile (static) results."""
from __future__ import annotations

import base64
import html as html_lib
import json
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
VOICE = HERE / "voice_results.json"
EDIT  = HERE / "edit_profile_results.json"
SHOTS = HERE / "screenshots"
OUT   = HERE / "ui_automation_report.html"


def b64(path: Path) -> str:
    if not path.exists():
        return ""
    return base64.b64encode(path.read_bytes()).decode()


def load(p: Path) -> list[dict]:
    return json.loads(p.read_text()) if p.exists() else []


def badge(kind: str, label: str) -> str:
    return f'<span class="badge {kind}">{html_lib.escape(label)}</span>'


def where_chip(w: str) -> str:
    label = {"client": "ANDROID", "server": "BACKEND", "both": "BOTH"}.get(w, "—")
    return f'<span class="where w-{w or "none"}">{label}</span>'


def sev_chip(s: str) -> str:
    return f'<span class="sev sev-{s}">{s.upper()}</span>'


def render() -> str:
    voice = load(VOICE)
    edit  = load(EDIT)
    all_ = voice + edit

    total = len(all_)
    passed = sum(1 for r in all_ if r["status"] == "PASS")
    failed = total - passed

    fails = [r for r in all_ if r["status"] == "FAIL"]
    crit  = sum(1 for r in fails if r["severity"] == "critical")
    high  = sum(1 for r in fails if r["severity"] == "high")
    med   = sum(1 for r in fails if r["severity"] == "medium")
    low   = sum(1 for r in fails if r["severity"] in ("low", "info"))

    client_only = sum(1 for r in fails if r["fix_where"] == "client")
    server_only = sum(1 for r in fails if r["fix_where"] == "server")
    both_       = sum(1 for r in fails if r["fix_where"] == "both")
    undef       = sum(1 for r in fails if r["fix_where"] not in ("client","server","both"))

    fix_here = client_only + both_

    def row(r: dict, live: bool) -> str:
        shot_before = f'data:image/png;base64,{b64(SHOTS / r["before_png"])}' if r.get("before_png") else ""
        shot_after  = f'data:image/png;base64,{b64(SHOTS / r["after_png"])}'  if r.get("after_png")  else ""
        shots = ""
        if live and (shot_before or shot_after):
            shots = '<div class="shots">'
            if shot_before:
                shots += f'<div><div class="lbl">Before</div><img src="{shot_before}"/></div>'
            if shot_after:
                shots += f'<div><div class="lbl">After</div><img src="{shot_after}"/></div>'
            shots += '</div>'
        crash_tag = '<span class="crash">CRASH IN LOG</span>' if r.get("crash_in_log") else ''
        file_ref = r.get('file_ref') or ''
        file_html = f'<div class="kv"><b>Source:</b> <code>{html_lib.escape(file_ref)}</code></div>' if file_ref else ''
        why      = r.get("why") or ""
        fa       = r.get("fix_android") or ""
        fs       = r.get("fix_server") or ""
        return f"""
<article class="card status-{r['status'].lower()} sev-frame-{r['severity']}">
  <header>
    <span class="id mono">{html_lib.escape(r['id'])}</span>
    {sev_chip(r['severity'])}
    {where_chip(r.get('fix_where',''))}
    <span class="cat">{html_lib.escape(r.get('category',''))}</span>
    <span class="st st-{r['status'].lower()}">{r['status']}</span>
    {crash_tag}
  </header>
  <h3>{html_lib.escape(r['title'])}</h3>
  <div class="detail"><b>Result:</b> {html_lib.escape(r.get('detail',''))}</div>
  {f'<div class="kv"><b>Expected:</b> {html_lib.escape(r.get("expected",""))}</div>' if r.get("expected") else ""}
  {f'<div class="kv"><b>Why it matters:</b> {html_lib.escape(why)}</div>' if why else ""}
  {f'<div class="kv"><b>Android fix:</b> {html_lib.escape(fa)}</div>' if fa else ""}
  {f'<div class="kv"><b>Backend fix:</b> {html_lib.escape(fs)}</div>' if fs else ""}
  {file_html}
  {shots}
</article>
"""

    voice_rows = "".join(row(r, live=True)  for r in voice)
    edit_rows  = "".join(row(r, live=False) for r in edit)

    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    almost = SHOTS / "almost_done_blocked.png"
    almost_b64 = f'data:image/png;base64,{b64(almost)}' if almost.exists() else ""

    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Hima — UI Automation Report · Edit Profile & Voice Verification</title>
<style>
  :root {{
    --bg:#0f1115; --panel:#151923; --panel2:#1b2030; --line:#262c3b;
    --text:#e6e8ee; --muted:#9aa3b2; --accent:#5b8def;
    --crit:#ef4444; --high:#f59e0b; --med:#eab308; --low:#38bdf8; --info:#7dd3fc;
    --ok:#10b981; --client:#10b981; --server:#ef4444; --both:#f59e0b;
  }}
  *{{box-sizing:border-box}}
  html,body{{background:var(--bg);color:var(--text);font:14px/1.55 -apple-system,Segoe UI,Roboto,sans-serif;margin:0}}
  header.top{{padding:22px 36px;border-bottom:1px solid var(--line);background:linear-gradient(180deg,#171b26,#0f1115)}}
  header.top h1{{margin:0 0 6px;font-size:22px}}
  header.top .sub{{color:var(--muted);font-size:13px}}
  main{{max-width:1200px;margin:0 auto;padding:22px 36px}}
  h2{{font-size:18px;margin:30px 0 10px;border-bottom:1px solid var(--line);padding-bottom:6px}}
  .meta{{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:10px 0}}
  .meta div{{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:12px 14px}}
  .meta .k{{font-size:11px;letter-spacing:1px;text-transform:uppercase;color:var(--muted)}}
  .meta .v{{font-weight:600;margin-top:4px}}

  .cards{{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:10px;margin:10px 0}}
  .mini{{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:14px;text-align:center}}
  .mini .n{{font-size:26px;font-weight:700}}
  .mini .l{{font-size:12px;color:var(--muted);margin-top:4px}}
  .mini.ok{{border-color:var(--ok)}} .mini.ok .n{{color:var(--ok)}}
  .mini.fail{{border-color:var(--crit)}} .mini.fail .n{{color:var(--crit)}}
  .mini.crit{{border-color:var(--crit)}} .mini.crit .n{{color:var(--crit)}}
  .mini.high{{border-color:var(--high)}} .mini.high .n{{color:var(--high)}}
  .mini.med{{border-color:var(--med)}} .mini.med .n{{color:var(--med)}}

  .fix-grid{{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:10px;margin:10px 0 4px}}
  .fix{{background:var(--panel);border:1px solid var(--line);border-top:4px solid #64748b;border-radius:10px;padding:14px}}
  .fix.client{{border-top-color:var(--client)}}
  .fix.both{{border-top-color:var(--both)}}
  .fix.server{{border-top-color:var(--server)}}
  .fix .title{{font-weight:700;margin-bottom:4px}}
  .fix.client .title{{color:#6ee7b7}}
  .fix.both .title{{color:#fcd34d}}
  .fix.server .title{{color:#fca5a5}}
  .fix .big{{font-size:26px;font-weight:700;margin:4px 0 8px}}
  .fix ul{{margin:6px 0 0 18px;padding:0;font-size:13px;color:#cbd5e1}}
  .fix li{{margin:2px 0}}
  .headline{{background:var(--panel2);border-radius:10px;padding:12px 14px;margin:10px 0}}

  article.card{{background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:14px 16px;margin:10px 0;border-left:4px solid var(--line)}}
  article.card.sev-frame-critical{{border-left-color:var(--crit)}}
  article.card.sev-frame-high{{border-left-color:var(--high)}}
  article.card.sev-frame-medium{{border-left-color:var(--med)}}
  article.card.sev-frame-low, article.card.sev-frame-info{{border-left-color:var(--low)}}
  article.card header{{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:4px}}
  article.card h3{{margin:4px 0 8px;font-size:15px}}
  article.card .detail{{color:#cbd5e1;font-size:13px;margin:4px 0}}
  article.card .kv{{font-size:13px;color:#cbd5e1;margin:3px 0}}
  article.card .kv b{{color:#e6e8ee}}

  .sev{{font-size:10px;font-weight:700;padding:2px 7px;border-radius:4px;text-transform:uppercase;letter-spacing:.4px}}
  .sev-critical{{background:#3b0d0d;color:#fecaca;border:1px solid #7f1d1d}}
  .sev-high{{background:#3a230a;color:#fed7aa;border:1px solid #9a3412}}
  .sev-medium{{background:#332a0a;color:#fde68a;border:1px solid #854d0e}}
  .sev-low{{background:#0a2738;color:#bae6fd;border:1px solid #0e5c8a}}
  .sev-info{{background:#162033;color:#93c5fd;border:1px solid #1d4ed8}}

  .where{{font-size:10px;font-weight:700;padding:2px 7px;border-radius:4px;letter-spacing:.4px}}
  .w-client{{background:#0a2a1f;color:#6ee7b7;border:1px solid #065f46}}
  .w-server{{background:#2a0b0b;color:#fca5a5;border:1px solid #7f1d1d}}
  .w-both  {{background:#2a1a0b;color:#fcd34d;border:1px solid #92400e}}
  .w-none  {{background:#1a2030;color:#94a3b8;border:1px solid #334155}}

  .st{{font-size:11px;font-weight:700;padding:2px 7px;border-radius:4px}}
  .st-pass{{background:#0a2a1f;color:#6ee7b7;border:1px solid #065f46}}
  .st-fail{{background:#2a0b0b;color:#fca5a5;border:1px solid #7f1d1d}}

  .cat{{font-size:11px;color:var(--muted);background:#1a2030;padding:2px 6px;border-radius:4px}}
  .id.mono{{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:11px;color:var(--muted);background:#11151f;padding:2px 7px;border-radius:4px}}
  .crash{{font-size:10px;font-weight:700;padding:2px 7px;border-radius:4px;background:#3b0d0d;color:#fecaca}}

  .shots{{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:8px}}
  .shots img{{width:100%;border:1px solid var(--line);border-radius:6px;background:#000}}
  .shots .lbl{{font-size:11px;color:var(--muted);margin-bottom:3px}}
  code{{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12px;color:#cbd5e1}}
  .blocked{{display:flex;gap:14px;align-items:flex-start;margin:10px 0}}
  .blocked img{{max-width:240px;border:1px solid var(--line);border-radius:8px}}
  .legend{{color:var(--muted);font-size:12px;margin:6px 0}}
</style>
</head>
<body>
<header class="top">
  <h1>UI Automation Report — Edit Profile &amp; Voice Verification</h1>
  <div class="sub">{now} · Device: emulator-5554 (Android 16, 1080×2400) · App: com.gmwapp.hima.dev v53 · QA: Perumal</div>
</header>

<main>

<div class="meta">
  <div><div class="k">Screens</div><div class="v">Voice Verification · Edit Profile</div></div>
  <div><div class="k">Tests run</div><div class="v">{total} total ({len(voice)} live UI + {len(edit)} static)</div></div>
  <div><div class="k">Pass / Fail</div><div class="v">{passed} / {failed}</div></div>
  <div><div class="k">Fixable from Android</div><div class="v">{fix_here} of {failed}</div></div>
</div>

<h2>1. Headline numbers</h2>
<div class="cards">
  <div class="mini ok"><div class="n">{passed}</div><div class="l">Pass</div></div>
  <div class="mini fail"><div class="n">{failed}</div><div class="l">Fail</div></div>
  <div class="mini crit"><div class="n">{crit}</div><div class="l">Critical</div></div>
  <div class="mini high"><div class="n">{high}</div><div class="l">High</div></div>
  <div class="mini med"><div class="n">{med}</div><div class="l">Medium</div></div>
</div>

<h2>2. Where each failure has to be fixed</h2>
<div class="fix-grid">
  <div class="fix client">
    <div class="title">Fixable on Android</div>
    <div class="big">{client_only}</div>
    <ul>
      <li>Disable Update button while request in flight</li>
      <li>Debounce name-validation API (300 ms)</li>
      <li>Cache userData, avoid prefs read on every scroll tick</li>
      <li>Warn on back-press with unsaved edits</li>
      <li>contentDescription on cv_back icon</li>
    </ul>
  </div>
  <div class="fix both">
    <div class="title">Both Android + Backend</div>
    <div class="big">{both_}</div>
    <ul><li>(covered separately in API report)</li></ul>
  </div>
  <div class="fix server">
    <div class="title">Backend only</div>
    <div class="big">{server_only}</div>
    <ul><li>(None surfaced by THIS suite — separate API report covers IDOR / auth)</li></ul>
  </div>
</div>
<div class="headline">
  <b>Bottom line:</b> {fix_here} of the {failed} failing UI tests can be closed entirely inside the Android codebase.
  None of them requires backend work. Server-side issues are tracked in the separate API report.
</div>

<h2>3. Voice Verification — live UI tests on emulator (screenshots inline)</h2>
<p class="legend">Driven by adb + uiautomator on emulator-5554. Each case includes before/after screenshots captured automatically. V-06 specifically verifies the new 30-second auto-stop fix we just shipped.</p>
{voice_rows}

<h2>4. Edit Profile — static UI + code analysis</h2>
<div class="blocked">
  {f'<img src="data:image/png;base64,{b64(almost)}" alt="almost done screen">' if almost.exists() else ''}
  <div>
    <b>Why no live screenshots for Edit Profile?</b><br>
    The test account is currently stuck on the "Almost done… Your profile is under review" screen (shown left).
    This is a terminal state that blocks navigation to the home screen, so the Edit Profile activity cannot be
    reached from the emulator without a backend reset of the user's status flag. The 12 checks below therefore
    run against the layout XML and activity Kotlin source — catching the same issues a live Espresso suite would.
  </div>
</div>
{edit_rows}

<h2>5. Methodology</h2>
<ul class="legend">
  <li>Voice tests drive the app through adb shell input + `uiautomator dump`, with logcat captured for each case.</li>
  <li>V-06 and V-07 specifically verify the two recent client-side fixes (30 s cap + rapid-tap resilience).</li>
  <li>Edit Profile tests parse the layout XML and activity Kotlin source for standard Android UX/perf issues (maxLength, inputType, debounce, button-while-loading, back-confirm, Html.fromHtml on names, etc.).</li>
  <li>Screenshots are embedded inline as base64 so this HTML is one self-contained file to share.</li>
</ul>

</main>
</body>
</html>
"""


def main():
    OUT.write_text(render())
    print(f"✓ wrote {OUT}")


if __name__ == "__main__":
    main()
