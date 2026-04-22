#!/usr/bin/env python3
"""
Automated API test suite — Hima login / signup flow.

Runs ~25 test cases against the dev API and writes:
  - latest_results.json  — machine-readable results
  - latest_report.html   — human-readable execution report

Usage:
    python3 run_tests.py

Dependencies: Python 3.9+ standard library only (urllib, json, ssl).
Environment:  https://demohima.himaapp.in/api/auth  (dev)
"""

from __future__ import annotations

import html as html_lib
import json
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

# ──────────────────────────── Config ────────────────────────────
BASE_URL    = "https://demohima.himaapp.in/api/auth"
TEST_MOBILE = "9876543210"            # reused to avoid DB pollution
OUT_DIR     = Path(__file__).resolve().parent
REPORT_HTML = OUT_DIR / "latest_report.html"
RESULTS_JSON = OUT_DIR / "latest_results.json"
SSL_CTX     = ssl.create_default_context()

# ──────────────────────────── HTTP helpers ────────────────────────────
def http_post(endpoint: str, data: dict | None = None,
              headers: dict | None = None, timeout: float = 15) -> tuple[int, str]:
    url  = f"{BASE_URL}/{endpoint}"
    body = urllib.parse.urlencode(data or {}).encode()
    req  = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    req.add_header("Accept", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
            return resp.status, resp.read().decode(errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, (e.read().decode(errors="replace") if e.fp else "")

def http_options(endpoint: str, origin: str) -> tuple[int, dict]:
    url = f"{BASE_URL}/{endpoint}"
    req = urllib.request.Request(url, method="OPTIONS")
    req.add_header("Origin", origin)
    req.add_header("Access-Control-Request-Method", "POST")
    try:
        with urllib.request.urlopen(req, timeout=10, context=SSL_CTX) as resp:
            return resp.status, dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers) if e.headers else {}

def http_get(endpoint: str) -> tuple[int, str]:
    req = urllib.request.Request(f"{BASE_URL}/{endpoint}", method="GET")
    try:
        with urllib.request.urlopen(req, timeout=10, context=SSL_CTX) as resp:
            return resp.status, resp.read().decode(errors="replace")[:300]
    except urllib.error.HTTPError as e:
        return e.code, ""

def is_json(body: str) -> bool:
    try:
        json.loads(body); return True
    except Exception:
        return False

def get_json(body: str) -> dict:
    try:
        v = json.loads(body); return v if isinstance(v, dict) else {}
    except Exception:
        return {}

# ──────────────────────────── Shared context ────────────────────────────
class Ctx:
    token: str | None = None
    my_user_id: int | None = None
    neighbor_user_id: int | None = None

def prepare(ctx: Ctx):
    """Obtain a valid JWT for the test mobile (functional prerequisite)."""
    code, body = http_post("login", {"mobile": TEST_MOBILE, "code": "0", "code_verifier": "0"})
    if code != 200:
        return
    data = get_json(body)
    ctx.token = data.get("token")
    ctx.my_user_id = (data.get("data") or {}).get("id")
    if isinstance(ctx.my_user_id, int):
        ctx.neighbor_user_id = ctx.my_user_id - 1

# ──────────────────────────── Assertions ────────────────────────────
def expect_status(want: int | list[int]) -> Callable:
    want_list = want if isinstance(want, list) else [want]
    def check(code, body, hdrs=None):
        if code in want_list:
            return "PASS", f"HTTP {code} ∈ {want_list}"
        return "FAIL", f"expected HTTP {want_list}, got {code}"
    return check

def expect_success_true(code, body, hdrs=None):
    if code != 200:          return "FAIL", f"HTTP {code}"
    j = get_json(body)
    if j.get("success") is True: return "PASS", "success=true as expected"
    return "FAIL", f"success={j.get('success')} · msg={j.get('message')!r}"

def expect_success_false(code, body, hdrs=None):
    if code != 200:          return "FAIL", f"HTTP {code}"
    j = get_json(body)
    if j.get("success") is False: return "PASS", f"success=false ({j.get('message')!r})"
    return "FAIL", f"success={j.get('success')}"

# ──────────────────────────── Test definitions ────────────────────────────
# Each test: dict(id, category, title, expected, run, check, bug_ref, severity)
# `run` returns (status_code, body_str, extra_headers_dict_or_None)

def T(id, category, severity, title, expected, run, check, bug_ref=None,
      why=None, fix_android=None, fix_server=None, file_ref=None):
    """Define a test case. On failure, the HTML report will show:
        why          — why this failure matters (root cause)
        fix_android  — what you (Android) can change; None if nothing
        fix_server   — what the backend team must change; None if nothing
        file_ref     — specific Android file[:line] to edit, if applicable
    """
    return dict(id=id, category=category, severity=severity, title=title,
                expected=expected, run=run, check=check, bug_ref=bug_ref,
                why=why, fix_android=fix_android, fix_server=fix_server, file_ref=file_ref)

def define_tests(ctx: Ctx):
    tests: list[dict] = []

    # ───── Functional (happy path) ─────
    tests += [
        T("F-01", "Functional", "info",
          "first_install returns HTTP 200",
          "HTTP 200",
          lambda: (*http_post("first_install", {}), None),
          expect_status(200)),

        T("F-02", "Functional", "info",
          "appsettings_list (user_id=1) returns 200 + success",
          "HTTP 200 and success=true",
          lambda: (*http_post("appsettings_list", {"user_id": "1"}), None),
          expect_success_true),

        T("F-03", "Functional", "info",
          "send_otp with valid 10-digit mobile returns success",
          "HTTP 200 and success=true (1 real SMS will be sent)",
          lambda: (*http_post("send_otp",
                              {"mobile": TEST_MOBILE, "country_code": "91", "otp": "123456"}), None),
          expect_success_true),

        T("F-04", "Functional", "info",
          "login with valid mobile returns JWT",
          "HTTP 200, success=true, token present",
          lambda: (*http_post("login",
                              {"mobile": TEST_MOBILE, "code": "0", "code_verifier": "0"}), None),
          lambda c, b, h=None: (("PASS", "JWT returned")
                                if c == 200 and get_json(b).get("token")
                                else ("FAIL", f"HTTP {c} · token={bool(get_json(b).get('token'))}"))),

        T("F-05", "Functional", "info",
          "userdetails with valid token returns own profile",
          "HTTP 200, success=true, data.id == my_user_id",
          lambda: (*http_post("userdetails",
                              {"user_id": str(ctx.my_user_id or 0)},
                              {"Authorization": f"Bearer {ctx.token or ''}"}), None),
          lambda c, b, h=None: (("PASS", f"returned id={get_json(b).get('data', {}).get('id')}")
                                if c == 200 and get_json(b).get("success") is True
                                else ("FAIL", f"HTTP {c} · body={b[:120]}"))),

        T("F-06", "Functional", "info",
          "avatar_list gender=male returns a non-empty list",
          "HTTP 200, success=true, total >= 1",
          lambda: (*http_post("avatar_list", {"gender": "male"},
                              {"Authorization": f"Bearer {ctx.token or ''}"}), None),
          lambda c, b, h=None: (("PASS", f"total={get_json(b).get('total')}")
                                if c == 200 and (get_json(b).get("total") or 0) >= 1
                                else ("FAIL", f"HTTP {c}"))),

        T("F-07", "Functional", "info",
          "avatar_list gender=XYZ rejected",
          "HTTP 200, success=false",
          lambda: (*http_post("avatar_list", {"gender": "XYZ"},
                              {"Authorization": f"Bearer {ctx.token or ''}"}), None),
          expect_success_false),

        T("F-08", "Functional", "info",
          "GET /login returns 405 Method Not Allowed",
          "HTTP 405",
          lambda: (*http_get("login"), None),
          expect_status(405)),

        T("F-09", "Functional", "info",
          "/register rejects duplicate mobile (already-registered)",
          "success=false with duplicate-mobile message",
          lambda: (*http_post("register",
                              {"mobile": TEST_MOBILE, "language": "Tamil",
                               "avatar_id": "21", "gender": "male", "referred_by": ""}), None),
          lambda c, b, h=None: (("PASS", get_json(b).get("message", ""))
                                if get_json(b).get("success") is False
                                and "register" in get_json(b).get("message", "").lower()
                                else ("FAIL", f"body={b[:150]}"))),
    ]

    # ───── Validation (input handling) ─────
    tests += [
        T("V-01", "Validation", "medium",
          "send_otp with no body should return 400 JSON, not 500 HTML",
          "HTTP 400 + JSON",
          lambda: (*http_post("send_otp", {}), None),
          lambda c, b, h=None: (("PASS", f"HTTP {c} JSON")
                                if c == 400 and is_json(b)
                                else ("FAIL", f"HTTP {c} · JSON={is_json(b)} (BUG-005 expected FAIL)")),
          bug_ref="BUG-005",
          why="The server has no request-validator on /send_otp. Missing / empty fields fall through to a Laravel uncaught exception, which returns HTTP 500 with an HTML error page. Client can't parse it, and the framework identity leaks to anyone probing the API.",
          fix_android="Nothing to change in the client beyond robust parsing — ensure the Retrofit callback does not crash on non-JSON responses (already fine today). If the server keeps returning odd codes, add a generic 'Something went wrong, please try again' toast as a fallback.",
          fix_server="Add a Laravel FormRequest validator on /send_otp that returns HTTP 400 with JSON like `{\"success\":false,\"mobile\":[\"...\"]}` — mirror what /login already does correctly."),

        T("V-02", "Validation", "medium",
          "send_otp with empty mobile should return 400 JSON",
          "HTTP 400 + JSON",
          lambda: (*http_post("send_otp",
                              {"mobile": "", "country_code": "91", "otp": "123456"}), None),
          lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                if c == 400 and is_json(b)
                                else ("FAIL", f"HTTP {c} · JSON={is_json(b)} (BUG-005)")),
          bug_ref="BUG-005",
          why="Empty mobile should trigger a 400 'mobile is required' validation error. Server doesn't short-circuit, so the request flows into business logic and either crashes or silently succeeds.",
          fix_android="No change needed — the app's own regex `^[6-9]\\d{9}$` already blocks empty mobile before any API call. This test matters for API-level safety when someone hits the endpoint with curl.",
          fix_server="Add `'mobile' => 'required|digits:10|regex:/^[6-9]/'` to the FormRequest validator and return 400 on failure."),

        T("V-03", "Validation", "medium",
          "send_otp with country_code='xyz' should be rejected",
          "success=false / 400",
          lambda: (*http_post("send_otp",
                              {"mobile": TEST_MOBILE, "country_code": "xyz", "otp": "123456"}), None),
          lambda c, b, h=None: (("PASS", "rejected")
                                if get_json(b).get("success") is False
                                else ("FAIL", f"accepted xyz country_code (BUG-009)")),
          bug_ref="BUG-009",
          why="PHP silently casts the string 'xyz' to integer 0 during loose comparison, so the required-field branch never fires. The request is accepted with bogus country code, which corrupts SMS routing data.",
          fix_android="Nothing to do — the Android app already sends `country_code: Int`, so from your client this never happens. Test exists to document that curl abuse would succeed.",
          fix_server="Use Laravel's `integer` rule with an explicit allow-list of supported dialing codes: `'country_code' => ['required','integer','in:91,1,44,...']`."),

        T("V-04", "Validation", "info",
          "login with mobile='abc' returns 400",
          "HTTP 400",
          lambda: (*http_post("login", {"mobile": "abc", "code": "0", "code_verifier": "0"}), None),
          expect_status(400)),

        T("V-05", "Validation", "info",
          "login with 9-digit mobile returns 400",
          "HTTP 400",
          lambda: (*http_post("login", {"mobile": "987654321", "code": "0", "code_verifier": "0"}), None),
          expect_status(400)),
    ]

    # ───── Security critical ─────
    tests += [
        T("S-01", "Security", "critical",
          "/login without prior OTP should reject",
          "HTTP 401 / 422 (no JWT issued)",
          lambda: (*http_post("login",
                              {"mobile": TEST_MOBILE, "code": "0", "code_verifier": "0"}), None),
          lambda c, b, h=None: (("PASS", "rejected without OTP")
                                if c in (401, 422) or not get_json(b).get("token")
                                else ("FAIL", "JWT issued without OTP validation (BUG-001)")),
          bug_ref="BUG-001",
          why="Server's /login endpoint issues a JWT based only on the mobile number — it never validates the OTP. OTP comparison is done entirely on the Android client (VerifyOTPActivity.kt:143). An attacker with only a phone number can log into any account via curl, bypassing your app entirely.",
          fix_android="Delete the `|| enteredOTP == \"011011\"` branch at VerifyOTPActivity.kt:143 (backdoor OTP). Stop generating the OTP client-side and stop sending it in `send_otp`. This only closes the patched-APK path — the server-side bug stays open.",
          fix_server="REQUIRED to truly fix: generate the OTP server-side (6-digit, stored hashed with ≤5-minute TTL + attempt counter), have the client submit the OTP via /login, and validate it. Reject if expired, already-used, or attempt-cap exceeded.",
          file_ref="app/src/main/java/com/gmwapp/hima/activities/VerifyOTPActivity.kt:143"),

        T("S-02", "Security", "critical",
          "/login with garbage code and code_verifier should reject",
          "HTTP 401 / 422",
          lambda: (*http_post("login",
                              {"mobile": TEST_MOBILE,
                               "code": "RANDOMGARBAGE", "code_verifier": "NOTAVERIFIER"}), None),
          lambda c, b, h=None: (("PASS", "rejected")
                                if c in (401, 422) or not get_json(b).get("token")
                                else ("FAIL", "garbage accepted (BUG-001)")),
          bug_ref="BUG-001",
          why="Proves the manual-OTP `/login` path is unauthenticated: even invalid Truecaller-style tokens return a valid JWT. The server never checks code/code_verifier unless the Truecaller SDK path is in use.",
          fix_android="Same as S-01 — remove the backdoor, stop sending `code=\"0\", code_verifier=\"0\"` as placeholders. Coordinate with server change.",
          fix_server="Validate `code`/`code_verifier` strictly on the Truecaller path (PKCE check). For manual-OTP path, replace them with a real OTP field and validate it. Reject unknown formats.",
          file_ref="app/src/main/java/com/gmwapp/hima/viewmodels/LoginViewModel.kt:36-61"),

        T("S-03", "Security", "critical",
          "IDOR: /userdetails with someone else's user_id must not return their data",
          "403 OR own data (JWT sub wins)",
          lambda: (*http_post("userdetails",
                              {"user_id": str(ctx.neighbor_user_id or 1)},
                              {"Authorization": f"Bearer {ctx.token or ''}"}), None),
          lambda c, b, h=None: (("PASS", "not leaking neighbour")
                                if c in (401, 403)
                                or get_json(b).get("success") is False
                                or get_json(b).get("data", {}).get("id") == ctx.my_user_id
                                else ("FAIL", f"leaked id={get_json(b).get('data',{}).get('id')} mobile={get_json(b).get('data',{}).get('mobile')} (BUG-002)")),
          bug_ref="BUG-002",
          why="Server trusts `user_id` from the request body instead of deriving it from the JWT `sub` claim. Any authenticated user can dump every other user's profile (name, mobile, referral code) by iterating sequential user IDs — mass PII leak under the DPDP Act.",
          fix_android="Nothing effective. You could stop sending `user_id` from the app, but attackers don't use your app — they send it with curl. This is a pure server bug.",
          fix_server="REQUIRED: in the /userdetails controller, replace any use of `$request->user_id` with `$request->user()->id` (the JWT sub). Then audit every other endpoint in the controller layer that accepts `user_id` as a request parameter — there are ~60 in ApiManager.kt."),

        T("S-04", "Security", "critical",
          "CORS preflight with evil origin must not reflect Allow-Origin",
          "No ACAO for unknown origin, or no credentials",
          lambda: (lambda r: (r[0], json.dumps(dict(r[1])), dict(r[1])))(http_options("login", "https://evil.com")),
          lambda c, b, h=None: (("FAIL",
                                 f"reflected + creds: ACAO={get_json(b).get('Access-Control-Allow-Origin')} "
                                 f"ACAC={get_json(b).get('Access-Control-Allow-Credentials')} (BUG-003)")
                                if get_json(b).get("Access-Control-Allow-Origin", "").startswith("https://evil")
                                   and str(get_json(b).get("Access-Control-Allow-Credentials")).lower() == "true"
                                else ("PASS", "origin not reflected")),
          bug_ref="BUG-003",
          why="Server reflects ANY request Origin back in `Access-Control-Allow-Origin` AND sets `Access-Control-Allow-Credentials: true`. Combined with BUG-001, a malicious website can silently issue /login requests from a victim's browser session and exfiltrate their JWT.",
          fix_android="N/A — CORS is a browser-only mechanism. Mobile apps don't participate in CORS preflight at all.",
          fix_server="REQUIRED: in Laravel's CORS config (`config/cors.php`), set `'allowed_origins' => ['https://himaapp.in']` (or whatever legitimate web origins you have). Never reflect. Either drop `supports_credentials` or ensure it's only true for whitelisted origins."),

        T("S-05", "Security", "critical",
          "Rate limit: 5 rapid send_otp calls should trigger 429 on at least one",
          "At least one HTTP 429 in the burst",
          lambda: (lambda codes: (200,
                                  json.dumps({"codes": codes,
                                              "any_429": any(c == 429 for c in codes)}),
                                  None))(
                  [http_post("send_otp",
                             {"mobile": TEST_MOBILE, "country_code": "91", "otp": "000000"})[0]
                   for _ in range(5)]),
          lambda c, b, h=None: (("PASS", "rate-limit observed")
                                if get_json(b).get("any_429")
                                else ("FAIL", f"codes={get_json(b).get('codes')} — no rate-limit (BUG-004)")),
          bug_ref="BUG-004",
          why="Server has no throttle middleware on /send_otp. Every burst request sends a real SMS and costs the business money. In earlier manual tests, 20 SMS were sent to the same number in under 2 seconds. Also opens a brute-force window once BUG-001 is fixed.",
          fix_android="Optional UX polish only: add a 30-second disable on the 'Send OTP' button after each tap. This is cosmetic for security — attackers never see your UI.",
          fix_server="REQUIRED: Laravel `throttle` middleware. Suggested quotas — per mobile: 1/30s, 5/hr, 10/day. Per IP: 20/hr. Per /login: 5 wrong OTPs → 15-min cooldown per mobile."),

        T("S-06", "Security", "high",
          "/register without prior OTP should require OTP proof",
          "HTTP 401/422 or success=false with 'OTP'-related message",
          lambda: (*http_post("register",
                              {"mobile": "9000099999", "language": "Tamil",
                               "avatar_id": "21", "gender": "male", "referred_by": ""}), None),
          lambda c, b, h=None: (("PASS", "blocked")
                                if c in (401, 422)
                                or (get_json(b).get("success") is False
                                    and "otp" in get_json(b).get("message","").lower())
                                else ("FAIL", f"registered without OTP proof (BUG-007): {get_json(b).get('message')}")),
          bug_ref="BUG-007",
          why="`/register` creates a brand-new user account with JWT and referral code without any proof that the mobile number belongs to the requester. Mass spam-signup and account-pollution vector — anyone can register thousands of accounts from one IP.",
          fix_android="Once server supports it, pass the server-issued OTP proof token (from the verify step) into the /register payload. No-op until server changes.",
          fix_server="REQUIRED: after BUG-001 fix, /register must require a single-use, server-signed OTP proof token (e.g. short-lived JWT with `purpose=signup_for_mobile_X`). Reject without it."),
    ]

    # ───── Auth ─────
    tests += [
        T("A-01", "Auth", "high",
          "userdetails without Authorization header should be rejected",
          "HTTP 401 / 403 / 302 redirect",
          lambda: (*http_post("userdetails", {"user_id": str(ctx.my_user_id or 0)}), None),
          expect_status([401, 403, 302])),

        T("A-02", "Auth", "high",
          "userdetails with fake token 'abc' should be rejected",
          "HTTP 401 / 403 / 302 redirect",
          lambda: (*http_post("userdetails", {"user_id": str(ctx.my_user_id or 0)},
                              {"Authorization": "Bearer abc"}), None),
          expect_status([401, 403, 302])),
    ]

    return tests

# ──────────────────────────── Runner ────────────────────────────
def run_all(tests: list[dict]) -> list[dict]:
    results = []
    for t in tests:
        t0 = time.perf_counter()
        try:
            status, body, hdrs = t["run"]()
            verdict, detail = t["check"](status, body, hdrs)
            err = None
        except Exception as e:
            status, body, hdrs = -1, "", None
            verdict, detail = "ERROR", f"{type(e).__name__}: {e}"
            err = str(e)
        ms = int((time.perf_counter() - t0) * 1000)
        results.append({
            "id": t["id"], "category": t["category"], "severity": t["severity"],
            "title": t["title"], "expected": t["expected"],
            "http_code": status, "body_preview": (body or "")[:1500],
            "verdict": verdict, "detail": detail,
            "bug_ref": t.get("bug_ref"), "duration_ms": ms,
            "error": err,
            "why":         t.get("why"),
            "fix_android": t.get("fix_android"),
            "fix_server":  t.get("fix_server"),
            "file_ref":    t.get("file_ref"),
        })
        print(f"  [{verdict:5}] {t['id']} · {t['title']} · {detail} ({ms} ms)")
    return results

# ──────────────────────────── HTML renderer ────────────────────────────
def esc(s: Any) -> str:
    return html_lib.escape("" if s is None else str(s))

def render_html(results: list[dict], env: dict) -> str:
    total  = len(results)
    passed = sum(1 for r in results if r["verdict"] == "PASS")
    failed = sum(1 for r in results if r["verdict"] == "FAIL")
    errors = sum(1 for r in results if r["verdict"] == "ERROR")
    rate   = (passed / total * 100) if total else 0

    # Group by category, preserving insertion order
    groups: dict[str, list[dict]] = {}
    for r in results:
        groups.setdefault(r["category"], []).append(r)

    def row(r):
        klass = {"PASS":"pass","FAIL":"fail","ERROR":"err"}[r["verdict"]]
        sev_badge = f'<span class="sev sev-{esc(r["severity"])}">{esc(r["severity"])}</span>' if r["severity"] != "info" else ""
        bug = f'<span class="bug">{esc(r["bug_ref"])}</span>' if r["bug_ref"] else ""

        # Guidance panel — only rendered for FAIL / ERROR, since PASS needs no fix
        guidance = ""
        if r["verdict"] in ("FAIL", "ERROR"):
            parts = []
            if r.get("why"):
                parts.append(
                    f'<div class="box why"><div class="box-h">🔎 What went wrong &amp; why it matters</div>'
                    f'<div>{esc(r["why"])}</div></div>'
                )
            if r.get("fix_android"):
                extra = (f'<div class="file-ref">📄 File: <code>{esc(r["file_ref"])}</code></div>'
                         if r.get("file_ref") else "")
                parts.append(
                    f'<div class="box fix-a"><div class="box-h">🟢 What YOU (Android) can fix</div>'
                    f'<div>{esc(r["fix_android"])}</div>{extra}</div>'
                )
            if r.get("fix_server"):
                parts.append(
                    f'<div class="box fix-s"><div class="box-h">🔴 What the BACKEND team must fix</div>'
                    f'<div>{esc(r["fix_server"])}</div></div>'
                )
            if parts:
                guidance = f'<div class="guidance">{"".join(parts)}</div>'

        return f"""
        <details class="case case-{klass}" {"open" if r["verdict"] != "PASS" else ""}>
          <summary>
            <span class="verdict v-{klass}">{esc(r["verdict"])}</span>
            <code class="tc">{esc(r["id"])}</code>
            <span class="title">{esc(r["title"])}</span>
            {sev_badge}
            {bug}
            <span class="meta">HTTP {esc(r["http_code"])} · {esc(r["duration_ms"])} ms</span>
          </summary>
          <div class="case-body">
            <div class="kv"><span class="k">Expected</span><span>{esc(r["expected"])}</span></div>
            <div class="kv"><span class="k">Actual</span><span>{esc(r["detail"])}</span></div>
            {guidance}
            <div class="kv"><span class="k">Response</span>
              <pre>{esc(r["body_preview"]) or "<em>(empty)</em>"}</pre>
            </div>
          </div>
        </details>"""

    sections = []
    for cat, rs in groups.items():
        p = sum(1 for r in rs if r["verdict"] == "PASS")
        f = sum(1 for r in rs if r["verdict"] == "FAIL")
        e = sum(1 for r in rs if r["verdict"] == "ERROR")
        sections.append(f"""
        <section>
          <h2>{esc(cat)} <span class="cat-count">{p} pass · {f} fail · {e} err · {len(rs)} total</span></h2>
          {"".join(row(r) for r in rs)}
        </section>""")

    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>Hima Login API — Automated Test Report</title>
<style>
:root{{--bg:#f8fafc;--panel:#fff;--line:#e2e8f0;--text:#0f172a;--muted:#64748b;
       --ok:#059669;--bad:#dc2626;--warn:#ea580c;--info:#0284c7;--brand:#1e40af}}
*{{box-sizing:border-box}}
html,body{{margin:0;background:var(--bg);color:var(--text);
 font:14px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Helvetica,Arial,sans-serif}}
header{{background:linear-gradient(135deg,#1e40af,#3b82f6);color:#fff;padding:28px 32px}}
header .eyebrow{{text-transform:uppercase;letter-spacing:3px;font-size:11px;opacity:.85}}
header h1{{margin:6px 0 2px;font-size:26px;font-weight:700}}
header .sub{{opacity:.9}}
main{{max-width:1150px;margin:0 auto;padding:24px 28px}}
.summary{{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;margin:10px 0 22px}}
.kpi{{background:var(--panel);border:1px solid var(--line);border-top:3px solid var(--line);
      border-radius:6px;padding:12px 14px;text-align:center}}
.kpi.total{{border-top-color:var(--brand)}}
.kpi.pass{{border-top-color:var(--ok)}}
.kpi.fail{{border-top-color:var(--bad)}}
.kpi.err{{border-top-color:var(--warn)}}
.kpi.rate{{border-top-color:var(--info)}}
.kpi .n{{font-size:26px;font-weight:700}}
.kpi .l{{color:var(--muted);font-size:11px;text-transform:uppercase;letter-spacing:1px;margin-top:2px}}
.kpi.pass .n{{color:var(--ok)}} .kpi.fail .n{{color:var(--bad)}}
.kpi.err .n{{color:var(--warn)}} .kpi.rate .n{{color:var(--info)}}
.env{{background:var(--panel);border:1px solid var(--line);border-radius:6px;padding:12px 14px;
     margin-bottom:18px;font-size:12.5px;color:var(--muted);display:flex;gap:18px;flex-wrap:wrap}}
.env b{{color:var(--text);font-weight:600}}
h2{{margin:22px 0 10px;font-size:16px;color:var(--brand);display:flex;align-items:center;
    justify-content:space-between;border-bottom:2px solid var(--brand);padding-bottom:6px}}
h2 .cat-count{{font-size:11px;color:var(--muted);font-weight:500;letter-spacing:.3px}}
.case{{background:var(--panel);border:1px solid var(--line);border-left:4px solid var(--line);
       border-radius:5px;margin:6px 0;padding:0}}
.case-pass{{border-left-color:var(--ok)}}
.case-fail{{border-left-color:var(--bad)}}
.case-err{{border-left-color:var(--warn)}}
.case summary{{padding:10px 14px;display:flex;gap:10px;align-items:center;cursor:pointer;
               list-style:none;flex-wrap:wrap}}
.case summary::-webkit-details-marker{{display:none}}
.verdict{{font-size:10px;font-weight:800;padding:3px 8px;border-radius:3px;letter-spacing:.5px;
         text-transform:uppercase;min-width:52px;text-align:center}}
.v-pass{{background:#dcfce7;color:#065f46}}
.v-fail{{background:#fee2e2;color:#991b1b}}
.v-err{{background:#ffedd5;color:#9a3412}}
.tc{{font:12px ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;background:#0f172a;color:#e2e8f0;
     padding:2px 7px;border-radius:3px}}
.case .title{{flex:1;font-weight:500}}
.sev{{font-size:9.5px;font-weight:700;padding:2px 7px;border-radius:9px;text-transform:uppercase;
      letter-spacing:.4px}}
.sev-critical{{background:#fee2e2;color:#991b1b;border:1px solid #fca5a5}}
.sev-high{{background:#ffedd5;color:#9a3412;border:1px solid #fdba74}}
.sev-medium{{background:#fef9c3;color:#854d0e;border:1px solid #fde047}}
.sev-low{{background:#e0f2fe;color:#075985;border:1px solid #7dd3fc}}
.bug{{background:#eef2ff;color:#3730a3;font:10.5px ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;
      padding:2px 7px;border-radius:3px;border:1px solid #c7d2fe}}
.meta{{color:var(--muted);font-size:11.5px;margin-left:auto;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}}
.case-body{{padding:4px 16px 14px;border-top:1px solid var(--line)}}
.kv{{display:grid;grid-template-columns:90px 1fr;gap:10px;margin:8px 0;font-size:12.5px}}
.kv .k{{color:var(--muted);text-transform:uppercase;font-size:10.5px;letter-spacing:1px;font-weight:600}}
pre{{background:#0f172a;color:#e2e8f0;padding:10px 12px;border-radius:4px;
    font:11.5px/1.45 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;
    overflow:auto;white-space:pre-wrap;word-break:break-all;margin:0;max-height:240px}}
.guidance{{display:grid;grid-template-columns:1fr;gap:8px;margin:10px 0 4px}}
.box{{border-radius:5px;padding:10px 12px;border:1px solid var(--line);font-size:12.5px;line-height:1.55}}
.box-h{{font-weight:700;font-size:11.5px;letter-spacing:.4px;margin-bottom:4px}}
.box.why{{background:#fff7ed;border-color:#fdba74;color:#7c2d12}}
.box.why .box-h{{color:#9a3412}}
.box.fix-a{{background:#ecfdf5;border-color:#86efac;color:#14532d}}
.box.fix-a .box-h{{color:#047857}}
.box.fix-s{{background:#fef2f2;border-color:#fca5a5;color:#7f1d1d}}
.box.fix-s .box-h{{color:#b91c1c}}
.file-ref{{margin-top:6px;font-size:11.5px;color:#166534}}
.file-ref code{{background:#0f172a;color:#bbf7d0;padding:2px 6px;border-radius:3px;font-size:11px}}
footer{{padding:18px 28px;color:var(--muted);font-size:11.5px;text-align:center;border-top:1px solid var(--line);margin-top:28px}}
.banner-fail{{background:#fef2f2;border:1px solid #fca5a5;color:#7f1d1d;padding:14px 16px;border-radius:5px;margin:10px 0 16px}}
.banner-pass{{background:#ecfdf5;border:1px solid #6ee7b7;color:#065f46;padding:14px 16px;border-radius:5px;margin:10px 0 16px}}
</style></head><body>

<header>
  <div class="eyebrow">QA Automation · API Regression Suite</div>
  <h1>Hima Login &amp; Signup Flow — Automated Test Report</h1>
  <div class="sub">{esc(env["run_at"])} · {esc(env["base_url"])}</div>
</header>

<main>
  <div class="summary">
    <div class="kpi total"><div class="n">{total}</div><div class="l">Total</div></div>
    <div class="kpi pass"><div class="n">{passed}</div><div class="l">Passed</div></div>
    <div class="kpi fail"><div class="n">{failed}</div><div class="l">Failed</div></div>
    <div class="kpi err"><div class="n">{errors}</div><div class="l">Errors</div></div>
    <div class="kpi rate"><div class="n">{rate:.0f}%</div><div class="l">Pass rate</div></div>
  </div>

  { ('<div class="banner-fail"><b>Suite status: FAIL</b> — ' + str(failed) +
     ' assertions failed (all failing cases are auto-expanded below). Each FAIL card shows three panels: '
     '<b style="color:#9a3412">🔎 What went wrong</b> (root cause), '
     '<b style="color:#047857">🟢 What YOU can fix</b> (Android-side action), and '
     '<b style="color:#b91c1c">🔴 What the backend must fix</b> (server-side action). '
     'Since you only have Android access, focus on the green boxes — the red boxes need to be escalated to the backend team.</div>')
     if failed or errors else
     '<div class="banner-pass"><b>Suite status: PASS</b> — all assertions green. No action required.</div>' }

  <div class="env">
    <div><b>Environment:</b> {esc(env["base_url"])}</div>
    <div><b>Test mobile:</b> {esc(env["test_mobile"])}</div>
    <div><b>Duration:</b> {esc(env["duration_s"])} s</div>
    <div><b>Python:</b> {esc(env["python"])}</div>
    <div><b>Executed:</b> {esc(env["run_at"])}</div>
  </div>

  {"".join(sections)}
</main>

<footer>
  Generated by <code>run_tests.py</code> · automated suite for the login/signup flow ·
  Reports directory: <code>QA_Reports/automation/</code>
</footer>

</body></html>
"""

# ──────────────────────────── Main ────────────────────────────
def main():
    import sys
    started = time.perf_counter()
    run_at  = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    print(f"▶︎ Hima login-flow API regression suite")
    print(f"  env: {BASE_URL}")
    print(f"  at:  {run_at}\n")

    ctx = Ctx()
    prepare(ctx)
    print(f"  prepared: my_user_id={ctx.my_user_id} · neighbor_user_id={ctx.neighbor_user_id} · token={'set' if ctx.token else 'MISSING'}\n")

    tests   = define_tests(ctx)
    results = run_all(tests)
    elapsed = round(time.perf_counter() - started, 2)

    env = {
        "run_at":      run_at,
        "base_url":    BASE_URL,
        "test_mobile": TEST_MOBILE,
        "duration_s":  elapsed,
        "python":      f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}",
    }

    RESULTS_JSON.write_text(json.dumps({"env": env, "results": results}, indent=2))
    REPORT_HTML.write_text(render_html(results, env))

    print(f"\n✔︎ {len(results)} tests run in {elapsed}s")
    print(f"   JSON : {RESULTS_JSON}")
    print(f"   HTML : {REPORT_HTML}")

if __name__ == "__main__":
    main()
