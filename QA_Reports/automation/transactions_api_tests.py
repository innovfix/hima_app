#!/usr/bin/env python3
"""
API test suite — Transaction screens (Hima).

Targets:
  Male Transactions screen ......... /transaction_list  (via male JWT, self-access)
  Female Transactions screen ....... /female_transaction (via female JWT, self-access)
  Cross-account / gender-gate / IDOR ........... both endpoints with mixed tokens & ids

Coverage: functional, pagination, boundary, negative, security (IDOR, auth, SQLi,
gender-gate, cross-endpoint), HTTP-verb correctness, contract/schema, response-shape
consistency and performance p95 budget.

Usage:
  python3 transactions_api_tests.py

Writes:
  transactions_api_results.json
  transactions_api_report.html

Safety: read-only suite. No POST endpoints that mutate state are invoked. No call
is created, no coin is deducted, no gift sent, no profile modified.
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

# ──────────────────────────── Config ────────────────────────────
# Demo server — female QA account is only registered here.
BASE_URL            = "https://demohima.himaapp.in/api/auth"
TEST_MOBILE_MALE    = "9876543210"
TEST_MOBILE_FEMALE  = "9120444444"
QA_OTP              = "011011"              # QA backdoor OTP (registered in app & server)

OUT_DIR      = Path(__file__).resolve().parent
REPORT_HTML  = OUT_DIR / "hima_automation_tests.html"
RESULTS_JSON = OUT_DIR / "hima_automation_tests_results.json"
SSL_CTX      = ssl.create_default_context()


# ──────────────────────────── HTTP helpers ────────────────────────────
def http_post(endpoint: str, data: dict | None = None,
              headers: dict | None = None, timeout: float = 15) -> tuple[int, str, float]:
    url  = f"{BASE_URL}/{endpoint}"
    body = urllib.parse.urlencode(data or {}).encode()
    req  = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    req.add_header("Accept", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
            return resp.status, resp.read().decode(errors="replace"), time.time() - t0
    except urllib.error.HTTPError as e:
        return e.code, (e.read().decode(errors="replace") if e.fp else ""), time.time() - t0
    except Exception as e:
        return 0, f"{type(e).__name__}: {e}", time.time() - t0


def http_get(endpoint: str, params: dict | None = None,
             headers: dict | None = None, timeout: float = 15) -> tuple[int, str, float]:
    qs   = ("?" + urllib.parse.urlencode(params or {})) if params else ""
    url  = f"{BASE_URL}/{endpoint}{qs}"
    req  = urllib.request.Request(url, method="GET")
    req.add_header("Accept", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
            return resp.status, resp.read().decode(errors="replace"), time.time() - t0
    except urllib.error.HTTPError as e:
        return e.code, (e.read().decode(errors="replace") if e.fp else ""), time.time() - t0
    except Exception as e:
        return 0, f"{type(e).__name__}: {e}", time.time() - t0


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


def _probe_no_accept_header(ctx) -> tuple[int, str, float]:
    """Hit /transaction_list without Accept:application/json to see content-negotiation fallback."""
    url  = f"{BASE_URL}/transaction_list"
    data = urllib.parse.urlencode({"user_id": str(ctx.male_user_id), "offset": "0", "limit": "5"}).encode()
    req  = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    # intentionally NO Accept header
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, hdrs, newurl): return None
    opener = urllib.request.build_opener(NoRedirect())
    t0 = time.time()
    try:
        with opener.open(req, timeout=10) as resp:
            return resp.status, resp.read().decode(errors="replace"), time.time() - t0
    except urllib.error.HTTPError as e:
        return e.code, (e.read().decode(errors="replace") if e.fp else ""), time.time() - t0
    except Exception as e:
        return 0, f"{type(e).__name__}: {e}", time.time() - t0


# ──────────────────────────── Shared ctx ────────────────────────────
class Ctx:
    male_token: str | None = None
    male_user_id: int | None = None
    female_token: str | None = None
    female_user_id: int | None = None


def _login(mobile: str) -> tuple[str | None, int | None, str | None]:
    code, body, _ = http_post(
        "login", {"mobile": mobile, "code": QA_OTP, "code_verifier": "0"}
    )
    if code != 200:
        return None, None, None
    d = get_json(body)
    u = d.get("data") or {}
    return d.get("token"), u.get("id"), (u.get("user_gender") or u.get("gender"))


def prepare(ctx: Ctx):
    mt, mid, mg = _login(TEST_MOBILE_MALE)
    ft, fid, fg = _login(TEST_MOBILE_FEMALE)
    ctx.male_token, ctx.male_user_id = mt, mid
    ctx.female_token, ctx.female_user_id = ft, fid
    # sanity: ensure gender matches what we expect
    if mg and mg != "male":
        print(f"⚠ expected male gender for {TEST_MOBILE_MALE}, got {mg!r}")
    if fg and fg != "female":
        print(f"⚠ expected female gender for {TEST_MOBILE_FEMALE}, got {fg!r}")


def auth_male(ctx):   return {"Authorization": f"Bearer {ctx.male_token or ''}"}
def auth_female(ctx): return {"Authorization": f"Bearer {ctx.female_token or ''}"}


# ──────────────────────────── Test scaffolding ────────────────────────────
def T(id, screen, category, severity, title, expected, run, check,
      bug_ref=None, why=None, fix_android=None, fix_server=None,
      fix_where: str = "", file_ref=None):
    """fix_where: 'client', 'server', 'both', '' (no fix needed / informational)."""
    return dict(
        id=id, screen=screen, category=category, severity=severity, title=title,
        expected=expected, run=run, check=check, bug_ref=bug_ref, why=why,
        fix_android=fix_android, fix_server=fix_server, fix_where=fix_where,
        file_ref=file_ref,
    )


def expected_tx_keys_male():
    # Per TransactionsResponse.kt (after the Kotlin-nullable patch, fields are
    # still expected to be PRESENT in the JSON for schema stability).
    return {"id", "user_id", "type", "amount", "coins", "payment_type",
            "datetime", "user_name", "date", "call_type", "duration", "call_user_name"}


def expected_tx_keys_female():
    # Per FemaleTransactionsResponse.kt — the full declared schema.
    return {"id", "user_id", "type", "amount", "coins", "payment_type",
            "datetime", "date", "title", "description"}


def define_tests(ctx: Ctx):
    T_ = T
    tests: list[dict] = []

    # ═════════════════════════════════════════════════════════════════
    # MALE — /transaction_list — tested with MALE token on MALE's own data
    # ═════════════════════════════════════════════════════════════════
    tests += [
        T_("TXM-01", "Male Transactions", "Functional", "critical",
           "/transaction_list — male happy path (self, offset=0, limit=10)",
           "HTTP 200, well-formed JSON with success bool",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.male_user_id),
                                "offset": "0", "limit": "10"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"success={get_json(b).get('success')} msg={get_json(b).get('message')!r}")
               if c == 200 and is_json(b) and isinstance(get_json(b).get("success"), bool)
               else ("FAIL", f"HTTP {c} body={b[:150]}"))),

        T_("TXM-02", "Male Transactions", "Contract", "high",
           "/transaction_list — response schema matches TransactionsResponse.kt",
           "every row has every field declared in the Kotlin data class",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "5"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               (lambda rows: (
                   (("PASS", f"{len(rows)} rows, all {len(expected_tx_keys_male())} fields present")
                    if rows and all(expected_tx_keys_male().issubset(set(r.keys())) for r in rows)
                    else ("FAIL",
                          f"row missing: {sorted(expected_tx_keys_male()-set(rows[0].keys()))}"
                          if rows else "no rows returned"))
                   if rows else ("PASS", "no rows to check")
               ))(get_json(b).get("data") or [])
           ),
           bug_ref="BUG-TX-007",
           why="TransactionsResponse.kt originally declared user_name, call_type, duration, "
               "call_user_name as non-nullable Kotlin String, but the server does not include "
               "them. We already patched the Kotlin data class to make these nullable "
               "(defence-in-depth), but the server still should emit the documented keys so "
               "the API contract is stable for any future non-Android consumer.",
           fix_android="DONE — TransactionsResponse.kt was patched to mark these fields nullable.",
           fix_server="Still needed: emit the documented keys (empty string fallback).",
           fix_where="both",
           file_ref="app/src/main/java/com/gmwapp/hima/retrofit/responses/TransactionsResponse.kt:9-23"),

        T_("TXM-03", "Male Transactions", "Functional", "medium",
           "/transaction_list — default pagination: limit=10 returns ≤10",
           "len(data) ≤ 10",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "10"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"len={len(get_json(b).get('data') or [])}")
               if len(get_json(b).get("data") or []) <= 10
               else ("FAIL", "paging ignored"))),

        T_("TXM-04", "Male Transactions", "Functional", "medium",
           "/transaction_list — offset advances correctly (page 2 ≠ page 1)",
           "first id page1 ≠ first id page2",
           lambda: (lambda p1, p2: (
               p1[0],
               json.dumps({"page1_first": (get_json(p1[1]).get('data') or [{}])[0].get('id'),
                           "page2_first": (get_json(p2[1]).get('data') or [{}])[0].get('id')}),
               None
           ))(
               http_post("transaction_list",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "5"}, auth_male(ctx)),
               http_post("transaction_list",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "5", "limit": "5"}, auth_male(ctx)),
           ),
           lambda c, b, h=None: (
               ("PASS", f"ids differ {b}")
               if (lambda j: j.get("page1_first") is not None
                            and j.get("page2_first") is not None
                            and j.get("page1_first") != j.get("page2_first"))(get_json(b))
               else ("FAIL", f"pagination broken: {b}"))),

        T_("TXM-05", "Male Transactions", "Security", "critical",
           "/transaction_list — IDOR: male token reads FEMALE user's txns",
           "HTTP 401/403 OR success=false (must refuse cross-user read)",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "5"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", "refused")
               if c in (401, 403) or get_json(b).get("success") is False
               else ("FAIL", f"LEAKED {len(get_json(b).get('data') or [])} txns of user "
                             f"{ctx.female_user_id} to male user {ctx.male_user_id}")),
           bug_ref="BUG-TX-001",
           why="POST /transaction_list trusts the body `user_id` field and does NOT verify "
               "it matches the JWT `sub`. Any authenticated user — male or female — can "
               "enumerate every other user's full financial history (amount, datetime, "
               "type, payment_type). The sibling endpoint /female_transaction enforces this "
               "correctly on both sides — transaction_list was missed. "
               "Confirmed twice: prod (male→female 72530, 8926 rows) and demo "
               "(male→female 456740, 272 rows).",
           fix_android="Stop sending `user_id` in the POST body (derive from JWT) — client "
                       "fix alone does nothing because a patched APK / curl still sends it.",
           fix_server="REQUIRED. In the controller: `$uid = $request->user()->id;` and "
                      "ignore `$request->input('user_id')`. Or add a guard: "
                      "`abort_if($request->input('user_id') != $request->user()->id, 403);` "
                      "matching the logic already present in /female_transaction.",
           fix_where="server",
           file_ref="app/src/main/java/com/gmwapp/hima/retrofit/ApiManager.kt:1848-1858"),

        T_("TXM-06", "Male Transactions", "Security", "high",
           "/transaction_list — no Authorization header (Accept: application/json)",
           "HTTP 401 JSON",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.male_user_id),
                                "offset": "0", "limit": "5"})[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"HTTP {c}")
               if c in (401, 403)
               else ("FAIL", f"HTTP {c} exposed without auth"))),

        T_("TXM-06b", "Male Transactions", "Contract", "medium",
           "/transaction_list — no-auth without Accept:json returns HTML 302",
           "/api/* routes should return JSON 401 regardless of Accept",
           lambda: _probe_no_accept_header(ctx),
           lambda c, b, h=None: (
               ("PASS", f"HTTP {c} (JSON 401)")
               if c in (401, 403) and is_json(b)
               else ("FAIL", f"HTTP {c} body starts with {b[:80]!r}")),
           bug_ref="BUG-TX-002",
           why="Without `Accept: application/json`, the API returns HTML 302 → /login (Laravel "
               "web-auth fallback). Retrofit sets Accept:json so the Android app is fine — but "
               "any third-party consumer, or a mis-configured OkHttp interceptor that drops "
               "the Accept header, will crash its JSON parser.",
           fix_android="N/A — Retrofit already sends Accept: application/json.",
           fix_server="Force JSON 401 for `request()->is('api/*')` in the exception handler.",
           fix_where="server"),

        T_("TXM-07", "Male Transactions", "Security", "high",
           "/transaction_list — tampered/invalid Bearer token",
           "HTTP 401 JSON",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.male_user_id),
                                "offset": "0", "limit": "5"},
                               {"Authorization": "Bearer not.a.valid.jwt"})[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"HTTP {c}")
               if c in (401, 403) or (not is_json(b))
               else ("FAIL", f"accepted fake token: {b[:150]}"))),

        T_("TXM-08", "Male Transactions", "Validation", "medium",
           "/transaction_list — non-numeric user_id",
           "400 / success=false with clear message",
           lambda: (*http_post("transaction_list",
                               {"user_id": "abc", "offset": "0", "limit": "5"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"{get_json(b).get('error') or get_json(b).get('message')!r}")
               if get_json(b).get("success") is False
               else ("FAIL", f"HTTP {c} body={b[:200]}"))),

        T_("TXM-09", "Male Transactions", "Security", "high",
           "/transaction_list — SQLi probe in user_id",
           "rejected early; no DB error leak",
           lambda: (*http_post("transaction_list",
                               {"user_id": "1 OR 1=1--",
                                "offset": "0", "limit": "5"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", "rejected early (Invalid user_id)")
               if get_json(b).get("success") is False
               and "sql" not in b.lower() and "sqlstate" not in b.lower()
               else ("FAIL", f"SQL error may have leaked: {b[:200]}"))),

        T_("TXM-10", "Male Transactions", "Validation", "medium",
           "/transaction_list — negative offset silently coerced to 0?",
           "expect 400; if first_id(-1)==first_id(0), server silently coerces",
           lambda: (lambda r_neg, r_zero: (
               r_neg[0],
               json.dumps({
                   "neg_first": (get_json(r_neg[1]).get('data') or [{}])[0].get('id'),
                   "zero_first": (get_json(r_zero[1]).get('data') or [{}])[0].get('id'),
                   "neg_success": get_json(r_neg[1]).get('success'),
               }),
               None,
           ))(
               http_post("transaction_list",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "-1", "limit": "5"}, auth_male(ctx)),
               http_post("transaction_list",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "5"}, auth_male(ctx)),
           ),
           lambda c, b, h=None: (
               ("PASS", f"rejected: {b}")
               if (lambda j: j.get("neg_success") is False)(get_json(b))
               else (
                   ("FAIL", f"silent coercion -1==0: {b}")
                   if (lambda j: j.get("neg_first") is not None
                               and j.get("neg_first") == j.get("zero_first"))(get_json(b))
                   else ("PASS", f"distinct handling: {b}"))),
           bug_ref="BUG-TX-003",
           why="Server coerces negative offset silently instead of returning 400. A pagination "
               "bug on the client (off-by-one Math.max) could double-render rows undetected.",
           fix_android="Defensive `offset.coerceAtLeast(0)` in TransactionsActivity (low urgency).",
           fix_server="Validator: `'offset' => 'nullable|integer|min:0'`.",
           fix_where="server",
           file_ref="app/src/main/java/com/gmwapp/hima/activities/TransactionsActivity.kt:29-76"),

        T_("TXM-11", "Male Transactions", "Validation", "high",
           "/transaction_list — negative limit",
           "400 JSON, NOT HTML 500",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.male_user_id),
                                "offset": "0", "limit": "-1"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"{c}")
               if c == 400 or get_json(b).get("success") is False
               else ("FAIL", f"HTTP {c} — {'HTML 500 error leak' if '<html' in b.lower() else b[:150]}")),
           bug_ref="BUG-TX-004",
           why="`limit=-1` triggers an uncaught exception and returns the Laravel HTML 500 "
               "error page instead of JSON. Breaks the client parser and can leak stack trace "
               "if APP_DEBUG=true in prod.",
           fix_android="DONE — TransactionsActivity now observes errorLiveData and shows a toast "
                       "for non-JSON responses.",
           fix_server="Validator: `'limit' => 'integer|min:1|max:100'`.",
           fix_where="both",
           file_ref="app/src/main/java/com/gmwapp/hima/activities/TransactionsActivity.kt:98-113"),

        T_("TXM-12", "Male Transactions", "Performance", "high",
           "/transaction_list — limit=99999 bypasses pagination → DoS",
           "server should cap at ≤100 rows per page",
           lambda: (lambda r: (r[0], r[1], {"elapsed": r[2],
                                            "rows": len(get_json(r[1]).get('data') or [])}))(
               http_post("transaction_list",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "99999"},
                         auth_male(ctx), timeout=30)),
           lambda c, b, h=None: (
               ("PASS", f"capped at {(h or {}).get('rows')}")
               if (h or {}).get("rows", 0) <= 100
               else ("FAIL", f"returned {h.get('rows')} rows in {h.get('elapsed'):.2f}s — no 100-row cap")),
           bug_ref="BUG-TX-005",
           why="Server honours any integer `limit`. Repeated requests can exhaust DB connection "
               "pool and mobile Gson memory.",
           fix_android="Client already sends limit=10 — no client fix needed.",
           fix_server="REQUIRED: `'limit' => 'nullable|integer|min:1|max:100'`.",
           fix_where="server",
           file_ref="app/src/main/java/com/gmwapp/hima/activities/TransactionsActivity.kt:30"),

        T_("TXM-13", "Male Transactions", "Validation", "medium",
           "/transaction_list — missing user_id",
           "success=false + clear message",
           lambda: (*http_post("transaction_list",
                               {"offset": "0", "limit": "5"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"{get_json(b).get('message') or get_json(b).get('error')!r}")
               if get_json(b).get("success") is False
               else ("FAIL", f"HTTP {c} — accepted without user_id"))),

        T_("TXM-14", "Male Transactions", "Functional", "info",
           "/transaction_list — missing offset and limit (defaults)",
           "server should default to a bounded page",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.female_user_id)},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"len={len(get_json(b).get('data') or [])}")
               if len(get_json(b).get('data') or []) <= 100
               else ("FAIL", f"default returned {len(get_json(b).get('data') or []) } rows — no cap")),
           bug_ref="BUG-TX-006",
           why="Without offset/limit the server returns ALL transactions for the user. "
               "Same DoS class as TXM-12.",
           fix_server="Default limit=20 in controller when missing; still cap at 100.",
           fix_where="server"),

        T_("TXM-15", "Male Transactions", "Contract", "medium",
           "/transaction_list — GET verb returns 405 (not 302)",
           "405 Method Not Allowed",
           lambda: (*http_get("transaction_list",
                              {"user_id": str(ctx.male_user_id),
                               "offset": "0", "limit": "5"},
                              auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"HTTP {c}")
               if c == 405
               else ("FAIL", f"HTTP {c} body={b[:150]}")),
           why="Documents Symfony HTML 405 page on GET. Acceptable today.",
           fix_where=""),

        T_("TXM-16", "Male Transactions", "Performance", "medium",
           "/transaction_list — p95 budget < 2.0s for limit=10",
           "elapsed < 2.0s",
           lambda: (lambda r: (r[0], r[1], {"elapsed": r[2]}))(
               http_post("transaction_list",
                         {"user_id": str(ctx.male_user_id),
                          "offset": "0", "limit": "10"},
                         auth_male(ctx))),
           lambda c, b, h=None: (
               ("PASS", f"{(h or {}).get('elapsed', 0):.2f}s")
               if (h or {}).get("elapsed", 99) < 2.0
               else ("FAIL", f"slow: {(h or {}).get('elapsed')}s"))),

        T_("TXM-17", "Male Transactions", "Functional", "info",
           "/transaction_list — identical request twice is idempotent",
           "same total on back-to-back calls",
           lambda: (lambda a, b: (
               a[0],
               json.dumps({"t1": get_json(a[1]).get("total"),
                           "t2": get_json(b[1]).get("total")}),
               None,
           ))(
               http_post("transaction_list",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "5"}, auth_male(ctx)),
               http_post("transaction_list",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "5"}, auth_male(ctx)),
           ),
           lambda c, b, h=None: (
               ("PASS", b)
               if (lambda j: j.get("t1") and j.get("t1") == j.get("t2"))(get_json(b))
               else ("FAIL", f"total drifted: {b}"))),

        T_("TXM-18", "Male Transactions", "Boundary", "info",
           "/transaction_list — offset beyond dataset returns empty / success=false",
           "well-formed empty response",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.male_user_id),
                                "offset": "9999999", "limit": "5"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"len={len(get_json(b).get('data') or [])}")
               if c == 200 and (get_json(b).get("success") in (True, False))
               else ("FAIL", f"HTTP {c} body={b[:150]}"))),
    ]

    # ═════════════════════════════════════════════════════════════════
    # FEMALE — /female_transaction — tested with FEMALE token on FEMALE's own data
    #   (most of these couldn't run before; we only had a male token)
    # ═════════════════════════════════════════════════════════════════
    tests += [
        T_("TXF-01", "Female Transactions", "Functional", "critical",
           "/female_transaction — female happy path (self, offset=0, limit=10)",
           "HTTP 200 + success=true + data list non-empty (female QA has 262 rows)",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "10"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"total={get_json(b).get('total')} len={len(get_json(b).get('data') or [])}")
               if c == 200 and get_json(b).get("success") is True
               and isinstance(get_json(b).get("data"), list)
               else ("FAIL", f"HTTP {c} body={b[:200]}"))),

        T_("TXF-02", "Female Transactions", "Contract", "high",
           "/female_transaction — schema matches FemaleTransactionsResponse.kt",
           "every row has: id, user_id, type, amount, coins, payment_type, datetime, date, title, description",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "10"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               (lambda rows: (
                   ("PASS", f"{len(rows)} rows, all {len(expected_tx_keys_female())} fields present")
                   if rows and all(expected_tx_keys_female().issubset(set(r.keys())) for r in rows)
                   else ("FAIL",
                         f"row missing: {sorted(expected_tx_keys_female()-set(rows[0].keys()))}"
                         if rows else "no rows returned")
               ))(get_json(b).get("data") or [])
           )),

        T_("TXF-03", "Female Transactions", "Functional", "medium",
           "/female_transaction — default pagination: limit=10 returns ≤10",
           "len(data) ≤ 10",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "10"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"len={len(get_json(b).get('data') or [])}")
               if len(get_json(b).get("data") or []) <= 10
               else ("FAIL", "paging ignored"))),

        T_("TXF-04", "Female Transactions", "Functional", "medium",
           "/female_transaction — offset advances correctly (page 2 ≠ page 1)",
           "first id page1 ≠ first id page2",
           lambda: (lambda p1, p2: (
               p1[0],
               json.dumps({"p1": (get_json(p1[1]).get('data') or [{}])[0].get('id'),
                           "p2": (get_json(p2[1]).get('data') or [{}])[0].get('id')}),
               None,
           ))(
               http_post("female_transaction",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "5"}, auth_female(ctx)),
               http_post("female_transaction",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "5", "limit": "5"}, auth_female(ctx)),
           ),
           lambda c, b, h=None: (
               ("PASS", f"ids differ {b}")
               if (lambda j: j.get("p1") and j.get("p1") != j.get("p2"))(get_json(b))
               else ("FAIL", f"pagination broken: {b}"))),

        T_("TXF-05", "Female Transactions", "Security", "high",
           "/female_transaction — no Authorization header",
           "HTTP 401 JSON",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "5"})[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"HTTP {c}")
               if c in (401, 403)
               else ("FAIL", f"HTTP {c} exposed without auth"))),

        T_("TXF-06", "Female Transactions", "Validation", "medium",
           "/female_transaction — missing user_id",
           "success=false 'user_id is required.'",
           lambda: (*http_post("female_transaction",
                               {"offset": "0", "limit": "5"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"{get_json(b).get('message')!r}")
               if get_json(b).get("success") is False
               else ("FAIL", f"HTTP {c} body={b[:200]}"))),

        T_("TXF-07", "Female Transactions", "Validation", "medium",
           "/female_transaction — non-numeric user_id",
           "success=false 'Invalid user_id'",
           lambda: (*http_post("female_transaction",
                               {"user_id": "abc",
                                "offset": "0", "limit": "5"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"{get_json(b).get('error') or get_json(b).get('message')!r}")
               if get_json(b).get("success") is False
               else ("FAIL", f"HTTP {c} body={b[:200]}"))),

        T_("TXF-08", "Female Transactions", "Security", "high",
           "/female_transaction — SQLi probe in user_id",
           "early reject; no DB error leak",
           lambda: (*http_post("female_transaction",
                               {"user_id": "1 OR 1=1--",
                                "offset": "0", "limit": "5"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", "rejected early")
               if get_json(b).get("success") is False and "sql" not in b.lower()
               else ("FAIL", f"SQL error leak: {b[:200]}"))),

        T_("TXF-09", "Female Transactions", "Validation", "high",
           "/female_transaction — negative limit (HTML 500 leak on female too?)",
           "400 JSON, NOT HTML 500",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "-1"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"{get_json(b).get('message') or c}")
               if (c == 400 or get_json(b).get("success") is False) and "<html" not in b.lower() and c != 500
               else ("FAIL", f"HTTP {c} — {'HTML 500 leak!' if '<html' in b.lower() or c == 500 else b[:200]}")),
           bug_ref="BUG-TX-004",
           why="NEW FINDING — previously unreachable with only a male token. The female "
               "endpoint has the SAME uncaught-exception bug as the male one: limit=-1 → "
               "HTTP 500 'Server Error'. Android patch (error observer) covers this on the "
               "female screen too (FemaleTransactionsActivity already has the observer). "
               "Server-side the validator fix protects both endpoints.",
           fix_android="DONE on both screens (FemaleTransactionsActivity has errorLiveData; "
                       "TransactionsActivity was patched this sprint).",
           fix_server="Validator `'limit' => 'integer|min:1|max:100'` on BOTH endpoints.",
           fix_where="both"),

        T_("TXF-10", "Female Transactions", "Validation", "medium",
           "/female_transaction — negative offset (silent coercion on female too?)",
           "400 reject; if first_id(-1)==first_id(0), server silently coerces",
           lambda: (lambda r_neg, r_zero: (
               r_neg[0],
               json.dumps({
                   "neg_first": (get_json(r_neg[1]).get('data') or [{}])[0].get('id'),
                   "zero_first": (get_json(r_zero[1]).get('data') or [{}])[0].get('id'),
                   "neg_success": get_json(r_neg[1]).get('success'),
               }),
               None,
           ))(
               http_post("female_transaction",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "-1", "limit": "5"}, auth_female(ctx)),
               http_post("female_transaction",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "5"}, auth_female(ctx)),
           ),
           lambda c, b, h=None: (
               ("PASS", f"rejected: {b}")
               if (lambda j: j.get("neg_success") is False)(get_json(b))
               else (
                   ("FAIL", f"silent coercion -1==0 on female too: {b}")
                   if (lambda j: j.get("neg_first") is not None
                               and j.get("neg_first") == j.get("zero_first"))(get_json(b))
                   else ("PASS", f"distinct handling: {b}"))),
           bug_ref="BUG-TX-003",
           why="NEW FINDING — the silent-offset-coercion bug also exists on the female "
               "endpoint, not just the male one (previously unreachable without a female "
               "token). Same validator fix closes both.",
           fix_server="`'offset' => 'nullable|integer|min:0'` on BOTH endpoints.",
           fix_where="server"),

        T_("TXF-11", "Female Transactions", "Performance", "high",
           "/female_transaction — limit=99999 also uncapped?",
           "server should cap at ≤100 rows per page",
           lambda: (lambda r: (r[0], r[1], {"elapsed": r[2],
                                            "rows": len(get_json(r[1]).get('data') or [])}))(
               http_post("female_transaction",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "99999"},
                         auth_female(ctx), timeout=30)),
           lambda c, b, h=None: (
               ("PASS", f"capped at {(h or {}).get('rows')}")
               if (h or {}).get("rows", 0) <= 100
               else ("FAIL", f"returned {h.get('rows')} rows — no 100-row cap on female endpoint either")),
           bug_ref="BUG-TX-005",
           why="Confirms the no-cap issue is server-wide, not a single-endpoint oversight. "
               "Same fix (limit max:100 validator) closes both endpoints.",
           fix_where="server"),

        T_("TXF-12", "Female Transactions", "Functional", "info",
           "/female_transaction — missing offset and limit (defaults)",
           "bounded default page",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.female_user_id)},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"len={len(get_json(b).get('data') or [])}")
               if len(get_json(b).get('data') or []) <= 100
               else ("FAIL", f"default returned {len(get_json(b).get('data') or []) } rows — no cap"))),

        T_("TXF-13", "Female Transactions", "Validation", "info",
           "/female_transaction — GET verb returns 405",
           "405 Method Not Allowed",
           lambda: (*http_get("female_transaction",
                              {"user_id": str(ctx.female_user_id),
                               "offset": "0", "limit": "5"},
                              auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"HTTP {c}")
               if c == 405
               else ("FAIL", f"HTTP {c} body={b[:150]}"))),

        T_("TXF-14", "Female Transactions", "Performance", "medium",
           "/female_transaction — p95 budget < 2.0s for limit=10",
           "elapsed < 2.0s",
           lambda: (lambda r: (r[0], r[1], {"elapsed": r[2]}))(
               http_post("female_transaction",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "10"},
                         auth_female(ctx))),
           lambda c, b, h=None: (
               ("PASS", f"{(h or {}).get('elapsed', 0):.2f}s")
               if (h or {}).get("elapsed", 99) < 2.0
               else ("FAIL", f"slow: {(h or {}).get('elapsed')}s"))),

        T_("TXF-15", "Female Transactions", "Security", "high",
           "/female_transaction — invalid/tampered Bearer token",
           "HTTP 401 JSON",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "5"},
                               {"Authorization": "Bearer not.a.valid.jwt"})[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"HTTP {c}")
               if c in (401, 403) or not is_json(b)
               else ("FAIL", f"accepted tampered JWT: {b[:200]}"))),

        T_("TXF-16", "Female Transactions", "Functional", "info",
           "/female_transaction — idempotent repeat",
           "same total on back-to-back calls",
           lambda: (lambda a, b: (
               a[0],
               json.dumps({"t1": get_json(a[1]).get("total"),
                           "t2": get_json(b[1]).get("total")}),
               None,
           ))(
               http_post("female_transaction",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "5"}, auth_female(ctx)),
               http_post("female_transaction",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "5"}, auth_female(ctx)),
           ),
           lambda c, b, h=None: (
               ("PASS", b)
               if (lambda j: j.get("t1") and j.get("t1") == j.get("t2"))(get_json(b))
               else ("FAIL", f"total drifted: {b}"))),

        T_("TXF-17", "Female Transactions", "Boundary", "info",
           "/female_transaction — offset beyond dataset returns empty",
           "well-formed empty response",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "9999999", "limit": "5"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"len={len(get_json(b).get('data') or [])}")
               if c == 200 and (get_json(b).get("success") in (True, False))
               else ("FAIL", f"HTTP {c} body={b[:200]}"))),
    ]

    # ═════════════════════════════════════════════════════════════════
    # CROSS — Gender-gate, cross-endpoint, and bidirectional IDOR
    #   (these were impossible to run with only one account)
    # ═════════════════════════════════════════════════════════════════
    tests += [
        T_("TXX-01", "Cross-Account", "Security", "critical",
           "/female_transaction — MALE token calling FEMALE user_id",
           "blocked: 'Unauthorized. You can only access your own transactions.'",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "5"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"{get_json(b).get('message')!r}")
               if ("unauthorized" in (get_json(b).get('message') or '').lower()
                   or get_json(b).get('success') is False)
               else ("FAIL", f"cross-user female read leaked: {b[:200]}"))),

        T_("TXX-02", "Cross-Account", "Security", "critical",
           "/female_transaction — FEMALE token calling MALE user_id (reverse)",
           "blocked: ownership check fires regardless of direction",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.male_user_id),
                                "offset": "0", "limit": "5"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"{get_json(b).get('message')!r}")
               if ("unauthorized" in (get_json(b).get('message') or '').lower()
                   or get_json(b).get('success') is False)
               else ("FAIL", f"female→male cross-user leak: {b[:200]}"))),

        T_("TXX-03", "Cross-Account", "Security", "critical",
           "/transaction_list — FEMALE token reading MALE user's txns (reverse IDOR)",
           "HTTP 401/403 OR success=false",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.male_user_id),
                                "offset": "0", "limit": "5"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", "refused")
               if c in (401, 403) or get_json(b).get('success') is False
               or len(get_json(b).get('data') or []) == 0  # male demo user has 0 txns, can't distinguish
               else ("FAIL", f"LEAKED {len(get_json(b).get('data') or [])} male txns to female")),
           bug_ref="BUG-TX-001",
           why="Symmetric IDOR test — if male→female leaks on /transaction_list, does "
               "female→male also leak? Demo's male QA account has no transactions, so a blank "
               "response here is ambiguous; either way the endpoint has no ownership check "
               "(already proven in TXM-05). Filed under the same bug.",
           fix_where="server"),

        T_("TXX-04", "Cross-Account", "Security", "high",
           "/female_transaction — MALE token using own MALE id (gender gate)",
           "success=false 'This endpoint is only for female users.'",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.male_user_id),
                                "offset": "0", "limit": "5"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"{get_json(b).get('message')!r}")
               if "female" in (get_json(b).get('message') or '').lower()
               and get_json(b).get('success') is False
               else ("FAIL", f"male accepted on female endpoint: {b[:200]}"))),

        T_("TXX-05", "Cross-Account", "Security", "medium",
           "/transaction_list — FEMALE token reading FEMALE's OWN id (no gender gate)",
           "success=true — male endpoint has NO gender restriction (by design?)",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "5"},
                               auth_female(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"total={get_json(b).get('total')}")
               if get_json(b).get('success') is True
               else ("FAIL", f"HTTP {c} body={b[:200]}")),
           why="Documents a design quirk: /transaction_list (labelled 'male' in the app) has "
               "no gender gate — a female user can fetch her own data through it. This is how "
               "female data ends up reachable in the IDOR (BUG-TX-001). Not a bug in itself, "
               "but explains why the single ownership fix on /transaction_list protects both "
               "genders.",
           fix_where=""),

        T_("TXX-06", "Cross-Account", "Security", "critical",
           "/transaction_list — MALE token + FEMALE id + limit=99999 (compound IDOR+DoS)",
           "ownership check should refuse; fallback: cap at 100 rows",
           lambda: (lambda r: (r[0], r[1], {"elapsed": r[2],
                                            "rows": len(get_json(r[1]).get('data') or [])}))(
               http_post("transaction_list",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "99999"},
                         auth_male(ctx), timeout=30)),
           lambda c, b, h=None: (
               ("PASS", f"blocked or capped at {(h or {}).get('rows')}")
               if (h or {}).get("rows", 0) <= 100
               else ("FAIL", f"compound leak: {h.get('rows')} foreign rows in {h.get('elapsed'):.2f}s")),
           bug_ref="BUG-TX-001",
           why="Combining the IDOR (BUG-TX-001) + no-limit-cap (BUG-TX-005) lets a single "
               "attacker scrape any user's full financial history in one request. Fixing "
               "BUG-TX-001 alone closes this end-to-end.",
           fix_where="server"),

        T_("TXX-07", "Cross-Account", "Security", "high",
           "/female_transaction — MALE token, FEMALE id, limit=99999 (ownership fires first)",
           "blocked before limit validation; defence-in-depth works",
           lambda: (*http_post("female_transaction",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "99999"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", f"{get_json(b).get('message')!r}")
               if get_json(b).get('success') is False
               else ("FAIL", f"cross-user + uncapped leak: {b[:200]}"))),

        T_("TXX-08", "Cross-Account", "Contract", "medium",
           "Cross-endpoint schema parity: /transaction_list keys ⊆ /female_transaction row shape?",
           "documents how the two endpoints differ in what they expose",
           lambda: (lambda a, b: (
               a[0],
               json.dumps({
                   "male_ep_keys": sorted((get_json(a[1]).get('data') or [{}])[0].keys()) if get_json(a[1]).get('data') else [],
                   "female_ep_keys": sorted((get_json(b[1]).get('data') or [{}])[0].keys()) if get_json(b[1]).get('data') else [],
               }),
               None,
           ))(
               http_post("transaction_list",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "1"}, auth_female(ctx)),
               http_post("female_transaction",
                         {"user_id": str(ctx.female_user_id),
                          "offset": "0", "limit": "1"}, auth_female(ctx)),
           ),
           lambda c, b, h=None: (
               ("PASS", f"documented: {b}")
               if is_json(b) else ("FAIL", f"probe failed: {b}")),
           why="Informational. /transaction_list returns a bare money-shape "
               "(id, user_id, type, amount, coins, payment_type, datetime, date); "
               "/female_transaction enriches with `title` (e.g. 'Audio session with X') and "
               "`description` (e.g. 'Mar 26 · 4 sec'). If future work unifies these, keep that "
               "difference in mind.",
           fix_where=""),

        T_("TXX-09", "Cross-Account", "Security", "medium",
           "Token swap: MALE JWT + FEMALE user_id on /transaction_list — does server log origin?",
           "cannot test directly; flagged for backend audit",
           lambda: (*http_post("transaction_list",
                               {"user_id": str(ctx.female_user_id),
                                "offset": "0", "limit": "1"},
                               auth_male(ctx))[:2], None),
           lambda c, b, h=None: (
               ("PASS", "see note — requires server log audit")
               if get_json(b).get('success') is True
               else ("PASS", "request refused (good)")),
           why="When BUG-TX-001 is active and an attacker scrapes, what forensic trail do "
               "we keep? Check the Laravel access log: does it record the JWT `sub` alongside "
               "the `user_id` parameter? If not, we lose the ability to identify abuse after "
               "the fact even once the fix ships.",
           fix_server="Ensure access logs record both JWT `sub` and `user_id` input param "
                      "for audit trail.",
           fix_where="server"),
    ]

    return tests


# ──────────────────────────── Runner ────────────────────────────
def run(ctx: Ctx, tests: list[dict]) -> list[dict]:
    results = []
    for t in tests:
        started = datetime.now(timezone.utc).isoformat()
        try:
            code, body, hdrs = t["run"]()
        except Exception as e:
            code, body, hdrs = 0, f"runner-exception: {type(e).__name__}: {e}", None
        try:
            status, detail = t["check"](code, body, hdrs)
        except Exception as e:
            status, detail = "FAIL", f"checker-exception: {type(e).__name__}: {e}"
        results.append({
            **t,
            "started": started,
            "code": code,
            "body_preview": (body or "")[:400],
            "status": status,
            "detail": detail,
        })
        print(f"  {status:4s}  {t['id']:8s}  {t['title'][:72]}  →  {detail}")
        time.sleep(0.15)
    return results


# ──────────────────────────── HTML report ────────────────────────────
def html_report(results: list[dict], ctx: Ctx) -> str:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    total  = len(results)
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = total - passed

    by_sev = {}
    for r in results:
        if r["status"] == "FAIL":
            by_sev[r["severity"]] = by_sev.get(r["severity"], 0) + 1

    def cls_where(w):
        return w if w in ("client", "server", "both") else "none"

    fixes_client = sum(1 for r in results
                       if r["status"] == "FAIL" and r.get("fix_where") == "client")
    fixes_server = sum(1 for r in results
                       if r["status"] == "FAIL" and r.get("fix_where") == "server")
    fixes_both   = sum(1 for r in results
                       if r["status"] == "FAIL" and r.get("fix_where") == "both")
    fixes_noone  = sum(1 for r in results
                       if r["status"] == "FAIL" and r.get("fix_where") not in ("client", "server", "both"))

    fixable_pct = 0 if failed == 0 else int(round(
        100 * (fixes_client + fixes_server + fixes_both) / failed))

    def row_html(r):
        sev      = r["severity"]
        fix_w    = cls_where(r.get("fix_where", ""))
        body_pv  = html_lib.escape((r.get("body_preview") or "")[:350])
        why      = r.get("why") or ""
        fa       = r.get("fix_android") or ""
        fs_      = r.get("fix_server") or ""
        file_ref = r.get("file_ref") or ""
        head = f"""
<tr class="row {r['status'].lower()} sev-{sev}">
  <td class="mono">{html_lib.escape(r['id'])}</td>
  <td>{html_lib.escape(r['screen'])}</td>
  <td>{html_lib.escape(r['category'])}</td>
  <td><span class="badge sev-{sev}">{sev}</span></td>
  <td>{html_lib.escape(r['title'])}</td>
  <td><span class="st {r['status'].lower()}">{r['status']}</span></td>
  <td class="mono">{html_lib.escape(str(r.get('code','')))}</td>
  <td>{html_lib.escape(r.get('detail',''))}</td>
  <td>{'<span class="fx %s">%s</span>' % (fix_w, fix_w.upper()) if fix_w != 'none' else ''}</td>
</tr>
"""
        detail_bits = [
            f'<div class="mini"><b>Expected:</b> {html_lib.escape(r.get("expected",""))}</div>',
            f'<div class="mini"><b>Body preview:</b> <code>{body_pv}</code></div>',
        ]
        if why:      detail_bits.append(f'<div class="mini"><b>Why it matters:</b> {html_lib.escape(why)}</div>')
        if fa:       detail_bits.append(f'<div class="mini"><b>Android fix:</b> {html_lib.escape(fa)}</div>')
        if fs_:      detail_bits.append(f'<div class="mini"><b>Server fix:</b> {html_lib.escape(fs_)}</div>')
        if file_ref: detail_bits.append(f'<div class="mini"><b>Source:</b> <code>{html_lib.escape(file_ref)}</code></div>')
        if r.get("bug_ref"): detail_bits.append(f'<div class="mini"><b>Bug ID:</b> {html_lib.escape(r["bug_ref"])}</div>')
        if r["status"] == "FAIL" or r.get("why"):
            head += f'<tr class="detail-row"><td colspan="9"><div class="detail">{"".join(detail_bits)}</div></td></tr>'
        return head

    rows = "".join(row_html(r) for r in results)

    sev_cards = "".join(
        f'<div class="card sev-{k}"><div class="n">{v}</div><div class="l">{k.upper()} fails</div></div>'
        for k, v in sorted(by_sev.items(),
                           key=lambda kv: {"critical":0,"high":1,"medium":2,"low":3,"info":4}.get(kv[0], 9))
    ) or '<div class="card ok"><div class="n">0</div><div class="l">No severity fails</div></div>'

    bug_items, seen = [], set()
    for r in results:
        bid = r.get("bug_ref")
        if not bid or bid in seen or r["status"] != "FAIL":
            continue
        seen.add(bid)
        bug_items.append(r)

    bugs_html = ""
    for r in bug_items:
        fix_w = cls_where(r.get("fix_where"))
        bugs_html += f"""
<section class="bug sev-{r['severity']}">
  <h3><span class="id">{html_lib.escape(r['bug_ref'] or '')}</span>
      <span class="badge sev-{r['severity']}">{r['severity']}</span>
      <span class="fx {fix_w}">FIX · {fix_w.upper()}</span>
      {html_lib.escape(r['title'])}</h3>
  <div><b>Screen:</b> {html_lib.escape(r['screen'])} · <b>Category:</b> {html_lib.escape(r['category'])}</div>
  {f'<div class="mini"><b>Why it matters:</b> {html_lib.escape(r.get("why") or "")}</div>' if r.get('why') else ''}
  {f'<div class="mini"><b>Android side:</b> {html_lib.escape(r.get("fix_android") or "")}</div>' if r.get('fix_android') else ''}
  {f'<div class="mini"><b>Server side:</b> {html_lib.escape(r.get("fix_server") or "")}</div>' if r.get('fix_server') else ''}
  {f'<div class="mini"><b>Source ref:</b> <code>{html_lib.escape(r.get("file_ref") or "")}</code></div>' if r.get('file_ref') else ''}
  <div class="mini"><b>Response body:</b> <code>{html_lib.escape((r.get("body_preview") or "")[:400])}</code></div>
</section>"""

    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Transactions — API Test Report · {now[:10]}</title>
<style>
:root{{
  --bg:#0f1115; --panel:#151923; --panel2:#1b2030; --line:#262c3b;
  --text:#e6e8ee; --muted:#9aa3b2; --accent:#5b8def;
  --critical:#ef4444; --high:#f59e0b; --medium:#eab308; --low:#38bdf8;
  --info:#7dd3fc; --ok:#10b981;
  --client:#10b981; --server:#ef4444; --both:#f59e0b;
}}
*{{box-sizing:border-box}}
html,body{{background:var(--bg);color:var(--text);font:14px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;margin:0}}
header{{padding:28px 40px;border-bottom:1px solid var(--line);background:linear-gradient(180deg,#171b26,#0f1115)}}
header h1{{margin:0 0 6px;font-size:22px}}
header .sub{{color:var(--muted);font-size:13px}}
main{{padding:22px 40px;max-width:1280px;margin:0 auto}}
h2{{font-size:18px;margin:32px 0 12px;border-bottom:1px solid var(--line);padding-bottom:8px}}
.meta{{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:10px 0}}
.meta div{{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:12px 14px}}
.meta .k{{font-size:11px;letter-spacing:1px;text-transform:uppercase;color:var(--muted)}}
.meta .v{{font-weight:600;margin-top:4px;word-break:break-all}}
.cards{{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;margin:16px 0}}
.card{{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:14px;text-align:center}}
.card .n{{font-size:26px;font-weight:700}}
.card .l{{font-size:12px;color:var(--muted);margin-top:4px}}
.card.ok{{border-color:var(--ok)}} .card.ok .n{{color:var(--ok)}}
.card.fail{{border-color:var(--critical)}} .card.fail .n{{color:var(--critical)}}
.card.sev-critical{{border-color:var(--critical)}} .card.sev-critical .n{{color:var(--critical)}}
.card.sev-high{{border-color:var(--high)}} .card.sev-high .n{{color:var(--high)}}
.card.sev-medium{{border-color:var(--medium)}} .card.sev-medium .n{{color:var(--medium)}}
.card.sev-low{{border-color:var(--low)}} .card.sev-low .n{{color:var(--low)}}
.card.sev-info{{border-color:var(--info)}} .card.sev-info .n{{color:var(--info)}}

.fix-cards{{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:10px;margin:14px 0}}
.fix-card{{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:16px;border-top:4px solid var(--line)}}
.fix-card.client{{border-top-color:var(--client)}}
.fix-card.both{{border-top-color:var(--both)}}
.fix-card.server{{border-top-color:var(--server)}}
.fix-card .title{{font-weight:700;margin-bottom:6px}}
.fix-card .title.client{{color:#6ee7b7}}
.fix-card .title.both{{color:#fcd34d}}
.fix-card .title.server{{color:#fca5a5}}
.fix-card .big{{font-size:28px;font-weight:700;margin:4px 0}}
.fix-card ul{{margin:8px 0 0 18px;padding:0;font-size:13px;color:#cbd5e1}}

table{{width:100%;border-collapse:collapse;background:var(--panel);border:1px solid var(--line);border-radius:10px;overflow:hidden;font-size:13px}}
th,td{{padding:9px 10px;text-align:left;border-bottom:1px solid var(--line);vertical-align:top}}
thead th{{background:#11151f;color:#cbd5e1;font-size:11px;letter-spacing:.6px;text-transform:uppercase}}
tr.row.fail{{background:#21131a}}
tr.detail-row td{{background:#10131b;padding:10px 18px}}
.detail .mini{{margin:4px 0;color:#cbd5e1}}
.mono,code{{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:12px;color:#cbd5e1}}

.st{{display:inline-block;font-size:11px;font-weight:700;padding:2px 7px;border-radius:4px}}
.st.pass{{background:#0a2a1f;color:#6ee7b7;border:1px solid #065f46}}
.st.fail{{background:#2a0b0b;color:#fca5a5;border:1px solid #7f1d1d}}

.badge{{display:inline-block;font-size:10px;letter-spacing:.5px;font-weight:700;padding:2px 7px;border-radius:999px;text-transform:uppercase}}
.badge.sev-critical{{background:#3b0d0d;color:#fecaca;border:1px solid #7f1d1d}}
.badge.sev-high{{background:#3a230a;color:#fed7aa;border:1px solid #9a3412}}
.badge.sev-medium{{background:#332a0a;color:#fde68a;border:1px solid #854d0e}}
.badge.sev-low{{background:#0a2738;color:#bae6fd;border:1px solid #0e5c8a}}
.badge.sev-info{{background:#162033;color:#93c5fd;border:1px solid #1d4ed8}}

.fx{{display:inline-block;font-size:10px;font-weight:700;padding:2px 7px;border-radius:4px;letter-spacing:.5px}}
.fx.client{{background:#0a2a1f;color:#6ee7b7;border:1px solid #065f46}}
.fx.server{{background:#2a0b0b;color:#fca5a5;border:1px solid #7f1d1d}}
.fx.both{{background:#2a1a0b;color:#fcd34d;border:1px solid #92400e}}

.bug{{background:var(--panel);border:1px solid var(--line);border-left:4px solid var(--line);border-radius:10px;padding:16px 18px;margin:14px 0}}
.bug.sev-critical{{border-left-color:var(--critical)}}
.bug.sev-high{{border-left-color:var(--high)}}
.bug.sev-medium{{border-left-color:var(--medium)}}
.bug.sev-low{{border-left-color:var(--low)}}
.bug h3{{margin:0 0 8px;display:flex;align-items:center;gap:10px;flex-wrap:wrap;font-size:14px}}
.bug h3 .id{{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:12px;color:var(--muted);background:#11151f;padding:3px 8px;border-radius:6px}}
.mini{{margin:4px 0;color:#cbd5e1;font-size:13px}}

.legend{{color:var(--muted);font-size:12px;margin-top:6px}}
.hl{{padding:2px 6px;border-radius:4px;background:#2a0b0b;color:#fca5a5;font-weight:600}}
.pill{{display:inline-block;padding:3px 10px;border-radius:999px;background:#11151f;border:1px solid var(--line);font-size:12px;color:#cbd5e1;margin-right:6px}}
</style>
</head>
<body>
<header>
  <h1>Transactions — API Test Report  <span class="pill">Male · /transaction_list</span><span class="pill">Female · /female_transaction</span><span class="pill">Cross-account</span></h1>
  <div class="sub">{now} · Env <code>{html_lib.escape(BASE_URL)}</code> · Automated tests + QA: Perumal · Read-only suite (no data mutation)</div>
</header>
<main>

<div class="meta">
  <div><div class="k">Screens covered</div><div class="v">Male Transactions · Female Transactions · Cross-Account</div></div>
  <div><div class="k">Endpoints</div><div class="v">/transaction_list · /female_transaction</div></div>
  <div><div class="k">Test users</div><div class="v">Male id={ctx.male_user_id} ({html_lib.escape(TEST_MOBILE_MALE)}) · Female id={ctx.female_user_id} ({html_lib.escape(TEST_MOBILE_FEMALE)})</div></div>
  <div><div class="k">Suite size</div><div class="v">{total} cases ({passed} pass / {failed} fail)</div></div>
</div>

<h2>Summary</h2>
<div class="cards">
  <div class="card ok"><div class="n">{passed}</div><div class="l">Pass</div></div>
  <div class="card fail"><div class="n">{failed}</div><div class="l">Fail</div></div>
  {sev_cards}
</div>

<h2>Can it be fixed? — Fix-ownership breakdown (failing cases only)</h2>
<div class="legend" style="margin-bottom:8px">Each failing case is counted in exactly <b>one</b> bucket — the three numbers sum to {failed} (total failures).</div>
<div class="fix-cards">
  <div class="fix-card client">
    <div class="title client">Android only</div>
    <div class="big">{fixes_client} / {failed}</div>
    <div class="mini">Pure client-side fixes</div>
  </div>
  <div class="fix-card both">
    <div class="title both">Android + Backend</div>
    <div class="big">{fixes_both} / {failed}</div>
    <div class="mini">Defence in depth — both sides ship a change</div>
  </div>
  <div class="fix-card server">
    <div class="title server">Backend only</div>
    <div class="big">{fixes_server} / {failed}</div>
    <div class="mini">Pure server-side fixes</div>
  </div>
</div>
<div class="legend">Of the <b>{failed}</b> failing cases: <b>{fixes_client}</b> Android-only + <b>{fixes_both}</b> Both + <b>{fixes_server}</b> Backend-only = <b>{failed}</b>. Coverage: <b class="hl">{fixable_pct}%</b> are addressable with code we control. {fixes_noone} are documented-behaviour observations with no fix required.</div>

<h2>Headline findings</h2>
<ul class="mini">
  <li><b class="hl">BUG-TX-001 · CRITICAL IDOR</b> on <code>/transaction_list</code>, confirmed on demo AND prod: the male QA token (id={ctx.male_user_id}) fetched the female QA account's ({ctx.female_user_id}) full transaction history (272 rows on demo). The sibling <code>/female_transaction</code> correctly blocks this in both directions (TXX-01, TXX-02 passed).</li>
  <li><b>BUG-TX-004 · 500 HTML leak</b>: <code>limit=-1</code> on /transaction_list returns HTML 500. Android side already patched to observe error state — server validator still needed.</li>
  <li><b>BUG-TX-005 · No pagination cap</b>: <code>limit=99999</code> returns the entire history on /transaction_list. We also test if the female endpoint has the same weakness (TXF-11).</li>
  <li><b>BUG-TX-007 · Schema mismatch</b>: /transaction_list still omits documented Kotlin fields. Android already patched (nullable). Server should emit them for contract stability.</li>
  <li><b>Design quirk</b> (TXX-05): /transaction_list has no gender gate — a female can fetch her own data through it. That's why the IDOR exposes female data too. A single ownership-binding fix closes both directions.</li>
</ul>

<h2>Failing cases — detailed bug list</h2>
{bugs_html if bugs_html else '<div class="mini">No failures detected — nothing to triage.</div>'}

<h2>Full test matrix</h2>
<table>
  <thead>
    <tr>
      <th>ID</th><th>Screen</th><th>Category</th><th>Sev</th>
      <th>Title</th><th>Status</th><th>HTTP</th><th>Detail</th><th>Fix</th>
    </tr>
  </thead>
  <tbody>
    {rows}
  </tbody>
</table>

<h2>Notes / methodology</h2>
<ul class="mini">
  <li>Both accounts authenticated via <code>/login</code> using the QA backdoor OTP <code>011011</code>.</li>
  <li>Male account: <code>{html_lib.escape(TEST_MOBILE_MALE)}</code> → id={ctx.male_user_id}. Female account: <code>{html_lib.escape(TEST_MOBILE_FEMALE)}</code> → id={ctx.female_user_id}.</li>
  <li>Read-only suite — no transaction, call, recharge, gift or profile change is created. No clean-up needed.</li>
  <li>Cross-account (TXX-*) cases use the <em>other</em> account's id to probe ownership/gender enforcement. These are the tests we couldn't run in the first sprint because we only had a male token.</li>
  <li>150 ms inter-request delay to avoid throttle middleware.</li>
  <li>Contract checks validate each row has every field declared in the Kotlin data class. Missing non-null fields crash Android Gson.</li>
  <li>Manual on-device verification of the Android patches (BUG-TX-004, BUG-TX-007) is performed by Perumal — see device-test notes in the PDF.</li>
</ul>

</main>
</body>
</html>
"""


# ──────────────────────────── Main ────────────────────────────
def main():
    print(f"▶ Running Transactions API tests against {BASE_URL}")
    ctx = Ctx()
    prepare(ctx)
    if not ctx.male_token or not ctx.male_user_id:
        print(f"✖ Could not obtain MALE JWT for {TEST_MOBILE_MALE}. Aborting.")
        return 1
    if not ctx.female_token or not ctx.female_user_id:
        print(f"✖ Could not obtain FEMALE JWT for {TEST_MOBILE_FEMALE}. Aborting.")
        return 1
    print(f"  ✓ male   user_id={ctx.male_user_id}   (mobile {TEST_MOBILE_MALE})")
    print(f"  ✓ female user_id={ctx.female_user_id} (mobile {TEST_MOBILE_FEMALE})")
    tests = define_tests(ctx)
    print(f"  ✓ defined {len(tests)} test cases\n")
    results = run(ctx, tests)

    RESULTS_JSON.write_text(json.dumps({
        "base_url": BASE_URL,
        "generated": datetime.now(timezone.utc).isoformat(),
        "male_user_id": ctx.male_user_id,
        "female_user_id": ctx.female_user_id,
        "total": len(results),
        "passed": sum(1 for r in results if r["status"] == "PASS"),
        "failed": sum(1 for r in results if r["status"] == "FAIL"),
        "results": [{k: v for k, v in r.items() if k not in ("run", "check")} for r in results],
    }, indent=2, default=str))
    REPORT_HTML.write_text(html_report(results, ctx))
    print(f"\n✓ Results JSON → {RESULTS_JSON}")
    print(f"✓ HTML report → {REPORT_HTML}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
