#!/usr/bin/env python3
"""
Static UI-automation for Edit Profile screen.

Couldn't drive on device this session (user stuck on AlmostDone review screen),
so this suite performs 12 programmatic checks against the layout XML + activity
Kotlin source to catch the same kind of issues a live Espresso test would.

All checks are deterministic and fast — run with `python3 run_edit_profile_static.py`.
"""
from __future__ import annotations

import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path("/Users/apple/Desktop/hima_app_fresh")
LAYOUT = ROOT / "app/src/main/res/layout/activity_edit_profile.xml"
KT = ROOT / "app/src/main/java/com/gmwapp/hima/activities/EditProfileActivity.kt"

OUT = Path(__file__).resolve().parent
RESULTS: list[dict] = []


def case(id_, title, severity, category, fix_where="", expected="",
         why="", fix_android="", fix_server="", file_ref="", bug_ref=""):
    def wrap(fn):
        try:
            status, detail = fn()
        except Exception as e:
            status, detail = "FAIL", f"exception: {type(e).__name__}: {e}"
        RESULTS.append({
            "id": id_, "screen": "Edit Profile", "title": title,
            "severity": severity, "category": category, "fix_where": fix_where,
            "expected": expected, "why": why,
            "fix_android": fix_android, "fix_server": fix_server,
            "file_ref": file_ref, "bug_ref": bug_ref,
            "status": status, "detail": detail, "elapsed_s": 0,
            "before_png": "", "after_png": "",
            "crash_in_log": False,
        })
        print(f"  {status:4s}  {id_:8s}  {title[:70]:70s} - {detail}")
        return fn
    return wrap


def raw_layout() -> str:
    return LAYOUT.read_text()


def all_elements() -> list[dict]:
    """Parse layout XML and return list of {tag, attrs} dicts.

    Treat as text — grab every opening tag + its attributes via regex.
    Simpler than wrestling with ET namespaces for this read-only analysis.
    """
    raw = raw_layout()
    # Match both <Tag ... /> and <Tag ... > — capture tag name + attrs chunk
    TAG_RE = re.compile(r"<([A-Za-z][\w.]*)\b([^>]*?)/?>", re.S)
    ATTR_RE = re.compile(r'([A-Za-z_][\w:.\-]*)\s*=\s*"([^"]*)"', re.S)
    out = []
    for m in TAG_RE.finditer(raw):
        if m.group(1) in ("?xml", "!--"):
            continue
        a = {k: v for k, v in ATTR_RE.findall(m.group(2))}
        # Strip android: prefix for convenience
        stripped = {}
        for k, v in a.items():
            stripped[k.split(":", 1)[-1]] = v
        out.append({"tag": m.group(1), "attrs": stripped, "raw_attrs": a})
    return out


def attrs(el: dict) -> dict:
    return el["attrs"]


def kt_source() -> str:
    return KT.read_text()


def run_all():
    kt = kt_source()
    els = all_elements()

    # E-01: Layout file parses and has a root element
    @case("E-01", "Layout file parses without errors",
          severity="info", category="Sanity", fix_where="")
    def _():
        if len(els) > 1:
            return "PASS", f"{len(els)} elements parsed"
        return "FAIL", "layout empty"

    # E-02: Name field has maxLength so very long names can't be typed
    @case("E-02", "et_user_name has android:maxLength set (client-side cap)",
          severity="medium", category="Validation/UX", fix_where="client",
          expected="et_user_name defines maxLength ≤ 30",
          why="Without maxLength, users can paste a 500-char string that flows to the server. Server does cap at 10 (per API test), but UX is bad — input keeps filling. Defence in depth.",
          fix_android="Add `android:maxLength=\"30\"` (or 10 to match server) to et_user_name in activity_edit_profile.xml.",
          fix_server="N/A — already validates.",
          file_ref="app/src/main/res/layout/activity_edit_profile.xml (et_user_name)",
          bug_ref="UI-EP-001")
    def _():
        et = [e for e in els if attrs(e).get("id", "") == "@+id/et_user_name"]
        if not et:
            return "FAIL", "et_user_name not found"
        a = attrs(et[0])
        if "maxLength" in a:
            return "PASS", f"maxLength={a['maxLength']}"
        return "FAIL", "no maxLength — long paste allowed"

    # E-03: Name field has inputType that hides suggestions (privacy/UX)
    @case("E-03", "et_user_name has inputType defined",
          severity="low", category="UX", fix_where="client",
          expected="inputType set — default should be textPersonName",
          why="Without inputType, system chose a default which may show autocorrect suggestions inappropriate for a chosen display name.",
          fix_android="Set `android:inputType=\"textPersonName|textNoSuggestions\"`.",
          file_ref="app/src/main/res/layout/activity_edit_profile.xml (et_user_name)")
    def _():
        et = [e for e in els if attrs(e).get("id", "") == "@+id/et_user_name"]
        a = attrs(et[0]) if et else {}
        if "inputType" in a:
            return "PASS", f"inputType={a['inputType']}"
        return "FAIL", "no inputType — default soft keyboard"

    # E-04: Update button has accessibility contentDescription
    @case("E-04", "Critical icons (back, avatar arrows) have contentDescription",
          severity="low", category="Accessibility", fix_where="client",
          expected="cvBack / ivAvatarLeft / ivAvatarRight have contentDescription",
          why="TalkBack users can't identify buttons without contentDescription. Affects Play Store accessibility score.",
          fix_android="Add `android:contentDescription=\"@string/...\"` to each clickable ImageView / ImageButton.",
          file_ref="app/src/main/res/layout/activity_edit_profile.xml")
    def _():
        want = ("@+id/cv_back", "@+id/iv_avatar_left", "@+id/iv_avatar_right")
        missing = []
        for e in els:
            a = attrs(e)
            cid = a.get("id", "")
            if cid in want and not a.get("contentDescription"):
                missing.append(cid)
        if missing:
            return "FAIL", f"missing contentDescription on: {', '.join(missing)}"
        return "PASS", "all critical icons have contentDescription"

    # E-05: btnUpdate is wired with loader → disabled-while-loading pattern
    @case("E-05", "Update button shows loader + disables while request is in flight",
          severity="medium", category="UX/Robustness", fix_where="client",
          expected="btn_update.isEnabled=false and pb_update_loader.visibility=VISIBLE during updateProfile call",
          why="Without disabling the button, a user double-tap fires /update_profile twice. Backend is the one vulnerable to that duplicate (double counted name-change → 500 bug).",
          fix_android="Already partially wired — `binding.pbUpdateLoader.visibility = View.VISIBLE` and `binding.btnUpdate.text = \"\"`. But `isEnabled` is not set to false during loading. Add `binding.btnUpdate.isEnabled = false` in the update click handler.",
          fix_server="Server should also be idempotent, but client must not double-fire.",
          file_ref="app/src/main/java/com/gmwapp/hima/activities/EditProfileActivity.kt:236-246",
          bug_ref="UI-EP-002")
    def _():
        # extract the btnUpdate click handler by counting braces from its setOnClickListener
        i = kt.find("btnUpdate.setOnClickListener")
        if i == -1:
            return "FAIL", "update click handler not located"
        block = kt[i:i + 1200]
        has_disable = "isEnabled = false" in block or "isClickable = false" in block
        has_loader  = "pbUpdateLoader.visibility = View.VISIBLE" in block
        if has_disable and has_loader:
            return "PASS", "disable + loader present"
        miss = []
        if not has_disable: miss.append("btnUpdate.isEnabled = false")
        if not has_loader:  miss.append("loader visibility")
        return "FAIL", f"missing in click handler: {', '.join(miss)}"

    # E-06: Text watcher fires user_validations on every keystroke — UI risk (debouncing)
    @case("E-06", "etUserName changes fire user_validations on EVERY keystroke (no debounce)",
          severity="medium", category="Performance", fix_where="client",
          expected="text watcher debounces (e.g. 300 ms) before calling profileViewModel.userValidation()",
          why="Each keystroke → network call. Typing 'Aravindan' fires 9 validations in under a second. Spams the server, drains battery, and visual flicker on the name hint UI. Visible today in EditProfileActivity.kt:92-108.",
          fix_android="Wrap the watcher in a Handler.postDelayed(..., 300) pattern or use a Flow/debounce.",
          fix_server="Rate-limiting at /user_validations would mitigate, but the right fix is client-side debounce.",
          file_ref="app/src/main/java/com/gmwapp/hima/activities/EditProfileActivity.kt:92-108",
          bug_ref="UI-EP-003")
    def _():
        i = kt.find("etUserName.addTextChangedListener")
        if i == -1:
            return "FAIL", "text watcher not found"
        block = kt[i:i + 1500]
        has_debounce = any(term in block for term in
                           ("postDelayed", "debounce", "removeCallbacks", "Handler("))
        if has_debounce:
            return "PASS", "debounce pattern detected"
        return "FAIL", "no debounce — one API call per keystroke"

    # E-07: Observers do not call getUserData() inside tight loops
    @case("E-07", "updateButton() reads prefs from disk every call (ANR risk)",
          severity="medium", category="Performance", fix_where="client",
          expected="cache userData in a property, don't hit getPrefs() on every scroll/tick",
          why="updateButton() is called on every avatar scroll tick. It calls `getPrefs().getUserData()` each time. SharedPreferences reads are cheap but doing it on every scroll frame is wasteful and can stutter the avatar list.",
          fix_android="Cache userData once in onCreate and reuse. Or at least read once at start of updateButton().",
          file_ref="app/src/main/java/com/gmwapp/hima/activities/EditProfileActivity.kt:364-396",
          bug_ref="UI-EP-004")
    def _():
        m = re.search(r"private fun updateButton\(\).*?\n    \}\n", kt, flags=re.S)
        if not m:
            return "FAIL", "updateButton() not found"
        n = m.group(0).count("getPrefs()")
        if n == 0:
            return "PASS", "no prefs read inside updateButton"
        return "FAIL", f"prefs.getUserData() called {n}× inside updateButton (runs on every scroll)"

    # E-08: onBackPressed/ cvBack confirms unsaved changes
    @case("E-08", "Back / close button warns about unsaved edits",
          severity="low", category="UX", fix_where="client",
          expected="pressing cvBack while dirty shows 'Discard changes?' dialog",
          why="User types a new name, taps back, loses all edits silently. Standard Android UX is to prompt.",
          fix_android="In cvBack click handler, check if (isDirty) { show AlertDialog 'Discard changes?' }. Toggle isDirty from text watcher / interest selection.",
          file_ref="app/src/main/java/com/gmwapp/hima/activities/EditProfileActivity.kt:83-85",
          bug_ref="UI-EP-005")
    def _():
        m = re.search(r"cvBack\.setOnClickListener.*?\}\s*\)", kt, flags=re.S)
        if not m:
            return "FAIL", "cvBack click handler not found"
        block = m.group(0)
        if "AlertDialog" in block or "discard" in block.lower() or "confirm" in block.lower():
            return "PASS", "discard-confirm dialog present"
        return "FAIL", "cvBack just finish()es — unsaved edits lost silently"

    # E-09: XSS/HTML in name — client should display as text only (no WebView)
    @case("E-09", "Name shown as plain TextView (no WebView/Html.fromHtml)",
          severity="medium", category="Security", fix_where="client",
          expected="etUserName is EditText and tvUserNameHint TextView — no HTML rendering",
          why="If any screen later renders the name via Html.fromHtml or WebView, it becomes a stored-XSS sink. API accepts HTML (api test EP-UV-05 was a PASS only because of length, not content filter).",
          fix_android="Grep codebase for Html.fromHtml on any name-bearing string; confirm none.",
          file_ref="app/src/main/res/layout/activity_edit_profile.xml")
    def _():
        # Scan for HTML rendering specifically on name-bearing variables
        grep = Path(ROOT / "app/src/main/java").rglob("*.kt")
        hits = []
        name_html_re = re.compile(r"Html\.fromHtml\([^)]*\.name\b")
        for p in grep:
            try:
                s = p.read_text(errors="ignore")
            except Exception:
                continue
            if name_html_re.search(s):
                hits.append(str(p.relative_to(ROOT)))
        if not hits:
            return "PASS", "no Html.fromHtml on name fields"
        return "FAIL", f"HTML rendering on name in: {hits[:3]}"

    # E-10: Interests adapter enforces 5-item cap via updateLimitReached
    @case("E-10", "Interests selection caps at 5 items (client UX)",
          severity="info", category="Validation/UX", fix_where="client",
          expected="interestsListAdapter.updateLimitReached(size == 5) toggles UI",
          why="Client caps at 5. Server does NOT — BUG-EP-013. Client rule stops the honest user but attacker bypasses it with curl. Verifies client side is still correct.",
          fix_android="Already in place — no change needed on client.",
          fix_server="Add server-side cap (see BUG-EP-013).",
          file_ref="app/src/main/java/com/gmwapp/hima/activities/EditProfileActivity.kt:192")
    def _():
        if "updateLimitReached(selectedInterests.size == 5)" in kt:
            return "PASS", "client cap wired at 5"
        return "FAIL", "client cap on interests missing"

    # E-11: update observer dismisses screen on success
    @case("E-11", "Success path updates local prefs and finish()es the screen",
          severity="info", category="Functional", fix_where="")
    def _():
        # find updateProfileLiveData.observe { ... } block by brace counting
        i = kt.find("updateProfileLiveData.observe")
        if i == -1:
            return "FAIL", "observer not found"
        tail = kt[i:i + 2500]
        has_prefs = "setUserData(it.data)" in tail
        has_finish = "finish()" in tail
        if has_prefs and has_finish:
            return "PASS", "prefs update + finish wired"
        return "FAIL", f"missing: prefs={has_prefs} finish={has_finish}"

    # E-12: Error observer falls back to generic toast (already solid)
    @case("E-12", "Error observer shows fallback toast (no crash path)",
          severity="info", category="Robustness", fix_where="")
    def _():
        i = kt.find("updateProfileErrorLiveData.observe")
        if i == -1:
            return "FAIL", "error observer not found"
        tail = kt[i:i + 800]
        if "showAppToast" in tail and "please_try_again_later" in tail:
            return "PASS", "generic failure toast wired"
        return "FAIL", "error observer missing toast fallback"

    (OUT / "edit_profile_results.json").write_text(json.dumps(RESULTS, indent=2))
    print(f"\n→ {len(RESULTS)} edit-profile static cases written")


if __name__ == "__main__":
    run_all()
