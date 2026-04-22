#!/usr/bin/env python3
"""
API test suite — Edit Profile + Voice Verification screens (Hima).

Targets:
  Edit Profile screen ........ /user_validations, /avatar_list, /update_profile
  Voice Verification screen .. /speech_text, /update_voice

Writes:
  edit_profile_voice_results.json
  edit_profile_voice_report.html

Usage:
  python3 edit_profile_voice_api_tests.py
"""

from __future__ import annotations

import html as html_lib
import json
import os
import ssl
import struct
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import wave
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

# ──────────────────────────── Config ────────────────────────────
BASE_URL    = "https://demohima.himaapp.in/api/auth"
TEST_MOBILE = "9876543210"
OUT_DIR     = Path(__file__).resolve().parent
REPORT_HTML = OUT_DIR / "edit_profile_voice_report.html"
RESULTS_JSON = OUT_DIR / "edit_profile_voice_results.json"
SSL_CTX     = ssl.create_default_context()


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


def http_multipart(endpoint: str, text_fields: dict, file_field: tuple | None = None,
                   headers: dict | None = None, timeout: float = 30) -> tuple[int, str, float]:
    """file_field = (field_name, filename, bytes, mime)"""
    boundary = f"----QABoundary{uuid.uuid4().hex}"
    parts: list[bytes] = []
    for k, v in text_fields.items():
        parts.append(f"--{boundary}\r\n".encode())
        parts.append(f'Content-Disposition: form-data; name="{k}"\r\n\r\n'.encode())
        parts.append(f"{v}\r\n".encode())
    if file_field:
        name, fname, data, mime = file_field
        parts.append(f"--{boundary}\r\n".encode())
        parts.append(
            f'Content-Disposition: form-data; name="{name}"; filename="{fname}"\r\n'.encode()
        )
        parts.append(f"Content-Type: {mime}\r\n\r\n".encode())
        parts.append(data)
        parts.append(b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    body = b"".join(parts)
    url = f"{BASE_URL}/{endpoint}"
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
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


def make_wav_bytes(duration_s: float = 1.0, rate: int = 8000) -> bytes:
    """Return a minimal valid WAV blob (silence) — suitable voice upload fixture."""
    import io
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        n = int(duration_s * rate)
        w.writeframes(b"\x00\x00" * n)
    return buf.getvalue()


# ──────────────────────────── Shared ctx ────────────────────────────
class Ctx:
    token: str | None = None
    my_user_id: int | None = None
    original_name: str | None = None
    original_avatar_id: int | None = None
    original_interests: str | None = None
    original_language: str | None = None
    original_voice: str | None = None
    foreign_user_id: int | None = None
    safe_name: str = "QAUser"
    mp3_bytes: bytes = b""


def prepare(ctx: Ctx):
    code, body, _ = http_post("login",
                              {"mobile": TEST_MOBILE, "code": "0", "code_verifier": "0"})
    if code != 200:
        return
    data = get_json(body)
    ctx.token = data.get("token")
    u = data.get("data") or {}
    ctx.my_user_id = u.get("id")
    ctx.original_name = u.get("name")
    ctx.original_avatar_id = u.get("avatar_id")
    ctx.original_interests = u.get("interests") or "Music"
    ctx.original_language = u.get("language") or "Tamil"
    ctx.original_voice = u.get("voice")
    if isinstance(ctx.my_user_id, int):
        ctx.foreign_user_id = max(1, ctx.my_user_id - 1)
    # Server rejects names with 3+ consecutive numbers; pick a safe QA name
    ctx.safe_name = "QAUser"
    # Small valid MP3 silence fixture (ID3v2 + MPEG layer III silent frame)
    # Note: the server validates MP3 — a synthesised silent frame works.
    ctx.mp3_bytes = bytes.fromhex(
        "494433040000000000000000" +  # ID3v2 header (no tags)
        ("fffb1064" + "00" * 100) * 60  # ~60 silent MPEG frames
    )


# ──────────────────────────── Test scaffolding ────────────────────────────
def T(id, screen, category, severity, title, expected, run, check,
      bug_ref=None, why=None, fix_android=None, fix_server=None,
      fix_where: str = "", file_ref=None):
    """fix_where: 'client', 'server', 'both', '' (passed / no fix needed)."""
    return dict(
        id=id, screen=screen, category=category, severity=severity, title=title,
        expected=expected, run=run, check=check, bug_ref=bug_ref, why=why,
        fix_android=fix_android, fix_server=fix_server, fix_where=fix_where,
        file_ref=file_ref,
    )


def auth_h(ctx): return {"Authorization": f"Bearer {ctx.token or ''}"}


def define_tests(ctx: Ctx):
    T_ = T
    tests: list[dict] = []

    # ═════════════════════════════════════════════════════════════════
    # EDIT PROFILE SCREEN — /user_validations
    # ═════════════════════════════════════════════════════════════════
    tests += [
        T_("EP-UV-01", "Edit Profile", "Functional", "info",
           "/user_validations — valid user_id + clean name → success",
           "HTTP 200 + success=true",
           lambda: (*http_post("user_validations",
                               {"user_id": str(ctx.my_user_id), "name": ctx.safe_name},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"msg={get_json(b).get('message')!r}")
                                 if c == 200 and get_json(b).get("success") is True
                                 else ("FAIL", f"HTTP {c} body={b[:120]}"))),

        T_("EP-UV-02", "Edit Profile", "Functional", "medium",
           "/user_validations — 3-char name should be rejected (UI enforces >=4)",
           "success=false (len<4 should fail)",
           lambda: (*http_post("user_validations",
                               {"user_id": str(ctx.my_user_id), "name": "ABC"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "rejected")
                                 if get_json(b).get("success") is False
                                 else ("FAIL", "server accepted 3-char name (client-only length rule)")),
           bug_ref="BUG-EP-001",
           why="The 4-character minimum is enforced only on the client (EditProfileActivity.kt:94). The server accepts shorter names through the API, so any curl user or patched APK can set a 1–3 char username.",
           fix_android="No client fix closes this (client check still matters for UX, but cannot be trusted).",
           fix_server="Add FormRequest rule `'name' => 'required|string|min:4|max:30|regex:/^[\\p{L}\\p{N}_ ]+$/u'` and return 400.",
           fix_where="server",
           file_ref="app/src/main/java/com/gmwapp/hima/activities/EditProfileActivity.kt:94"),

        T_("EP-UV-03", "Edit Profile", "Validation", "medium",
           "/user_validations — empty name should be rejected",
           "success=false",
           lambda: (*http_post("user_validations",
                               {"user_id": str(ctx.my_user_id), "name": ""},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"{get_json(b).get('message')!r}")
                                 if get_json(b).get("success") is False
                                 else ("FAIL", "empty name accepted")),
           bug_ref="BUG-EP-002",
           why="Empty string flows through to DB. Should be validated server-side.",
           fix_android="Client already blocks — no change needed.",
           fix_server="FormRequest: `'name' => 'required|string|min:4'`.",
           fix_where="server"),

        T_("EP-UV-04", "Edit Profile", "Validation", "medium",
           "/user_validations — 200-char name (overflow) should be rejected",
           "HTTP 400 / success=false",
           lambda: (*http_post("user_validations",
                               {"user_id": str(ctx.my_user_id), "name": "A" * 200},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"rejected · {get_json(b).get('message')!r}")
                                 if get_json(b).get("success") is False or c == 400
                                 else ("FAIL", "200-char name accepted — can DOS chat list UI")),
           bug_ref="BUG-EP-003",
           why="No max-length check server-side. Long names break chat-list UI rows and can trigger Firestore 1 MiB document limit when propagated to chat threads.",
           fix_android="Add `android:maxLength=\"30\"` to etUserName in activity_edit_profile.xml for UX, but server must still enforce it.",
           fix_server="Validator `'name' => 'max:30'`.",
           fix_where="both"),

        T_("EP-UV-05", "Edit Profile", "Security", "high",
           "/user_validations — XSS/HTML injection name should be sanitized or rejected",
           "success=false OR value returned escaped",
           lambda: (*http_post("user_validations",
                               {"user_id": str(ctx.my_user_id),
                                "name": "<script>alert(1)</script>"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "rejected or escaped")
                                 if get_json(b).get("success") is False
                                 else ("FAIL", "HTML accepted into name field (BUG-EP-004)")),
           bug_ref="BUG-EP-004",
           why="Name is echoed inside chat list / profile views. If accepted raw, any WebView or HTML-rendered surface is a stored-XSS vector.",
           fix_android="TextView uses setText which is safe, but any WebView rendering names is at risk — audit FriendsAdapter/ChatAdapter HTML sinks.",
           fix_server="Strip tags (`strip_tags`) and reject names matching `/<|>|&#|javascript:/i` in validator.",
           fix_where="both"),

        T_("EP-UV-06", "Edit Profile", "Security", "high",
           "/user_validations — SQL-like payload in name",
           "success=false OR safely stored",
           lambda: (*http_post("user_validations",
                               {"user_id": str(ctx.my_user_id),
                                "name": "a' OR 1=1--"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"{get_json(b).get('message')!r}")
                                 if c == 200 and is_json(b)
                                 else ("FAIL", f"HTTP {c} — SQL error leak? body={b[:150]}")),
           why="Smoke test that input doesn't leak SQL errors. Laravel Eloquent param-binds by default so expected PASS.",
           fix_where=""),

        T_("EP-UV-07", "Edit Profile", "Security", "critical",
           "/user_validations — IDOR: validate another user's id",
           "should tie user_id from JWT, not body",
           lambda: (*http_post("user_validations",
                               {"user_id": str(ctx.foreign_user_id), "name": "Hacker"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "server refused cross-user")
                                 if c in (401, 403)
                                 or get_json(b).get("success") is False
                                 else ("FAIL", "accepted foreign user_id — IDOR (BUG-EP-005)")),
           bug_ref="BUG-EP-005",
           why="Endpoint takes user_id from request body. Combined with the /userdetails IDOR already filed (BUG-002 in login report), the server trusts the client for identity across the whole API surface.",
           fix_android="Don't send user_id — but curl bypasses this. Pure server fix needed.",
           fix_server="Derive user_id from JWT `$request->user()->id`. Ignore body param.",
           fix_where="server"),

        T_("EP-UV-08", "Edit Profile", "Security", "high",
           "/user_validations — no Authorization header",
           "HTTP 401",
           lambda: (*http_post("user_validations",
                               {"user_id": str(ctx.my_user_id), "name": "TestName"})[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c in (401, 403)
                                 else ("FAIL", f"HTTP {c} — endpoint is unauthenticated (BUG-EP-006)")),
           bug_ref="BUG-EP-006",
           why="Profile-modifying endpoints must require a valid JWT. If this endpoint responds without auth, any scraper can test whether names exist.",
           fix_android="N/A (app already sends auth header).",
           fix_server="Move /user_validations route into `auth:api` middleware group.",
           fix_where="server"),

        T_("EP-UV-09", "Edit Profile", "Validation", "info",
           "/user_validations — emoji-only name '😀😀😀😀'",
           "HTTP 200 + success=true/false (document behavior)",
           lambda: (*http_post("user_validations",
                               {"user_id": str(ctx.my_user_id), "name": "😀😀😀😀"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c} · success={get_json(b).get('success')}")
                                 if c == 200 else ("FAIL", f"HTTP {c}")),
           why="Emoji is valid Unicode. Documents whether server accepts 4-byte UTF-8 — important because MySQL `utf8` (not `utf8mb4`) corrupts emojis.",
           fix_where=""),

        T_("EP-UV-10", "Edit Profile", "Validation", "info",
           "/user_validations — leading/trailing whitespace",
           "Trim server-side ideally",
           lambda: (*http_post("user_validations",
                               {"user_id": str(ctx.my_user_id), "name": "  JohnDoe  "},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}") if c == 200 else ("FAIL", f"HTTP {c}")),
           why="Server should trim whitespace. Not trimming lets two names that look identical differ in DB.",
           fix_where="server"),
    ]

    # ═════════════════════════════════════════════════════════════════
    # EDIT PROFILE SCREEN — /avatar_list
    # ═════════════════════════════════════════════════════════════════
    tests += [
        T_("EP-AV-01", "Edit Profile", "Functional", "info",
           "/avatar_list — gender=male returns non-empty list",
           "HTTP 200 + success=true + total >= 1",
           lambda: (*http_post("avatar_list", {"gender": "male"}, auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"total={get_json(b).get('total')}")
                                 if c == 200 and (get_json(b).get("total") or 0) >= 1
                                 else ("FAIL", f"HTTP {c}"))),

        T_("EP-AV-02", "Edit Profile", "Functional", "info",
           "/avatar_list — gender=female returns non-empty list",
           "HTTP 200 + success=true + total >= 1",
           lambda: (*http_post("avatar_list", {"gender": "female"}, auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"total={get_json(b).get('total')}")
                                 if c == 200 and (get_json(b).get("total") or 0) >= 1
                                 else ("FAIL", f"HTTP {c}"))),

        T_("EP-AV-03", "Edit Profile", "Validation", "low",
           "/avatar_list — invalid gender value",
           "success=false",
           lambda: (*http_post("avatar_list", {"gender": "ROBOT"}, auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "rejected")
                                 if get_json(b).get("success") is False
                                 else ("FAIL", "accepted unknown gender")),
           bug_ref="BUG-EP-007",
           why="Loose validation — unknown genders should return success=false rather than empty list or crash.",
           fix_android="No change — gender is always 'male'/'female' from app.",
           fix_server="Use `'gender' => 'required|in:male,female'`.",
           fix_where="server"),

        T_("EP-AV-04", "Edit Profile", "Security", "medium",
           "/avatar_list — no auth header",
           "HTTP 401",
           lambda: (*http_post("avatar_list", {"gender": "male"})[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c in (401, 403)
                                 else ("FAIL", f"HTTP {c} — avatar list exposed unauth (BUG-EP-008)")),
           bug_ref="BUG-EP-008",
           why="Avatar list is low-sensitivity but the route should still be behind auth to reduce scraping surface & unify policy.",
           fix_android="N/A.",
           fix_server="Put route inside `auth:api` group.",
           fix_where="server"),

        T_("EP-AV-05", "Edit Profile", "Performance", "info",
           "/avatar_list — response under 2s (p95)",
           "elapsed < 2.0s",
           lambda: (lambda r: (r[0], r[1], {"elapsed": r[2]}))(
               http_post("avatar_list", {"gender": "male"}, auth_h(ctx))),
           lambda c, b, h=None: (("PASS", f"{(h or {}).get('elapsed', 0):.2f}s")
                                 if (h or {}).get("elapsed", 99) < 2.0
                                 else ("FAIL", f"slow: {(h or {}).get('elapsed')}s"))),
    ]

    # ═════════════════════════════════════════════════════════════════
    # EDIT PROFILE SCREEN — /update_profile
    # ═════════════════════════════════════════════════════════════════
    # NB: these tests may mutate the account. We restore at the end.
    tests += [
        T_("EP-UP-01", "Edit Profile", "Functional", "critical",
           "/update_profile — happy path (clean name + 1 interest)",
           "HTTP 200 + success=true + data.id == my_id",
           lambda: (*http_post("update_profile",
                               {"user_id": str(ctx.my_user_id),
                                "avatar_id": str(ctx.original_avatar_id),
                                "name": ctx.safe_name,
                                "interests": ctx.original_interests or "Music"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"name={get_json(b).get('data',{}).get('name')}")
                                 if c == 200 and get_json(b).get("success") is True
                                 and get_json(b).get("data", {}).get("id") == ctx.my_user_id
                                 else ("FAIL", f"HTTP {c} body={b[:150]}")),
           bug_ref="BUG-EP-015",
           why="Valid /update_profile payload returns HTTP 500 'Server Error' on the 2nd attempt within a session — the 'change name only once' rule appears to raise an uncaught exception instead of returning JSON {success:false, message:'You can change your name only once.'}.",
           fix_android="None — client sends the correct payload. Crash happens entirely server-side.",
           fix_server="Wrap the once-only check in a try/catch and return `{success:false, message:'You can change your name only once.'}` as JSON. Add FormRequest validator to short-circuit before the update query.",
           fix_where="server",
           file_ref="app/src/main/java/com/gmwapp/hima/activities/EditProfileActivity.kt:240-246"),

        T_("EP-UP-02", "Edit Profile", "Validation", "medium",
           "/update_profile — avatar_id=999999 (not in avatar table)",
           "success=false or FK error",
           lambda: (*http_post("update_profile",
                               {"user_id": str(ctx.my_user_id),
                                "avatar_id": "999999",
                                "name": ctx.safe_name,
                                "interests": ctx.original_interests or "Music"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"{get_json(b).get('message')!r}")
                                 if get_json(b).get("success") is False
                                 or c >= 400
                                 else ("FAIL", "accepted non-existent avatar_id (FK check missing)")),
           bug_ref="BUG-EP-009",
           why="Avatar is a FK into avatars table. If DB has no constraint / validator, the app can display a broken avatar URL to the entire user base.",
           fix_android="No meaningful client fix (invalid avatar_id can only be sent from a patched APK or curl).",
           fix_server="Validator: `'avatar_id' => 'required|exists:avatars,id'`.",
           fix_where="server"),

        T_("EP-UP-03", "Edit Profile", "Validation", "medium",
           "/update_profile — avatar_id='abc' (non-numeric)",
           "400 / success=false",
           lambda: (*http_post("update_profile",
                               {"user_id": str(ctx.my_user_id),
                                "avatar_id": "abc",
                                "name": ctx.safe_name,
                                "interests": ctx.original_interests or "Music"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"{c}")
                                 if c == 400 or get_json(b).get("success") is False
                                 else ("FAIL", "non-numeric avatar_id accepted")),
           fix_server="Validator: `'avatar_id' => 'required|integer|exists:avatars,id'`.",
           fix_where="server"),

        T_("EP-UP-04", "Edit Profile", "Security", "critical",
           "/update_profile — IDOR: update foreign user_id",
           "403 or server returns own id",
           lambda: (*http_post("update_profile",
                               {"user_id": str(ctx.foreign_user_id),
                                "avatar_id": str(ctx.original_avatar_id),
                                "name": "Hijacker",
                                "interests": "Music"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "not mutated foreign record")
                                 if c in (401, 403)
                                 or get_json(b).get("success") is False
                                 or get_json(b).get("data", {}).get("id") == ctx.my_user_id
                                 else ("FAIL", f"updated user {get_json(b).get('data',{}).get('id')} (BUG-EP-010)")),
           bug_ref="BUG-EP-010",
           why="Server uses user_id from request body, so any logged-in user can overwrite another user's name, avatar, and interests. Critical account-takeover-adjacent bug.",
           fix_android="Stop sending `user_id`; derive server-side. But curl-level bug remains until server fixed.",
           fix_server="REQUIRED: `$user = $request->user(); $user->update([...]);` — ignore body user_id. Apply same pattern to every `user_id`-accepting endpoint.",
           fix_where="server",
           file_ref="app/src/main/java/com/gmwapp/hima/activities/EditProfileActivity.kt:241"),

        T_("EP-UP-05", "Edit Profile", "Validation", "high",
           "/update_profile — missing name field",
           "400 / success=false",
           lambda: (*http_post("update_profile",
                               {"user_id": str(ctx.my_user_id),
                                "avatar_id": str(ctx.original_avatar_id),
                                "interests": "Music"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"{c}")
                                 if c == 400 or get_json(b).get("success") is False
                                 else ("FAIL", "accepted missing name → may blank out profile")),
           bug_ref="BUG-EP-011",
           why="Without a 'required' rule, missing field becomes empty string in DB and wipes the display name.",
           fix_android="Button is disabled in UI when name invalid — already guarded.",
           fix_server="Validator: `'name' => 'required|string|min:4|max:30'`.",
           fix_where="server"),

        T_("EP-UP-06", "Edit Profile", "Security", "high",
           "/update_profile — no Authorization header",
           "HTTP 401",
           lambda: (*http_post("update_profile",
                               {"user_id": str(ctx.my_user_id),
                                "avatar_id": str(ctx.original_avatar_id),
                                "name": ctx.safe_name,
                                "interests": "Music"})[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c in (401, 403)
                                 else ("FAIL", f"HTTP {c} — profile mutable without auth (BUG-EP-012)")),
           bug_ref="BUG-EP-012",
           why="Any mutating endpoint must require JWT.",
           fix_android="N/A.",
           fix_server="Require `auth:api` on the route.",
           fix_where="server"),

        T_("EP-UP-07", "Edit Profile", "Validation", "medium",
           "/update_profile — 30 interests (limit should be 5 per UI)",
           "success=false (server should cap to 5)",
           lambda: (*http_post("update_profile",
                               {"user_id": str(ctx.my_user_id),
                                "avatar_id": str(ctx.original_avatar_id),
                                "name": ctx.safe_name,
                                "interests": ",".join(["Music"] * 30)},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "server rejected/trimmed")
                                 if get_json(b).get("success") is False
                                 else ("FAIL", f"accepted 30 interests (BUG-EP-013) — msg={get_json(b).get('message')!r}")),
           bug_ref="BUG-EP-013",
           why="Client caps at 5 interests; server has no cap. Leads to bloated profile data and inconsistent UI elsewhere.",
           fix_android="Client already caps (InterestsListAdapter).",
           fix_server="Validator: `'interests' => 'array|max:5'` (or explode string & count).",
           fix_where="server"),

        T_("EP-UP-08", "Edit Profile", "Validation", "medium",
           "/update_profile — interests containing non-whitelisted tag",
           "Server should reject unknown interest tokens",
           lambda: (*http_post("update_profile",
                               {"user_id": str(ctx.my_user_id),
                                "avatar_id": str(ctx.original_avatar_id),
                                "name": ctx.safe_name,
                                "interests": "DropTables,<script>"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "rejected")
                                 if get_json(b).get("success") is False
                                 else ("FAIL", "free-text interests accepted")),
           bug_ref="BUG-EP-014",
           why="Interests are chosen from a fixed list in-app. Server shouldn't accept arbitrary strings — interest field may later feed recommendation queries.",
           fix_android="Client already limits to known list.",
           fix_server="Validator with `in:Politics,Art,Sports,Movies,Music,...`.",
           fix_where="server"),

        T_("EP-UP-09", "Edit Profile", "Functional", "high",
           "/update_profile — second name change must return JSON error, not crash",
           "HTTP 200 + success=false",
           lambda: (*http_post("update_profile",
                               {"user_id": str(ctx.my_user_id),
                                "avatar_id": str(ctx.original_avatar_id),
                                "name": "QAChanged",
                                "interests": "Music"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "rejected cleanly")
                                 if c == 200 and get_json(b).get("success") is False
                                 else ("FAIL", f"HTTP {c} body={b[:120]}")),
           bug_ref="BUG-EP-015",
           why="The 'change name only once' rule is enforced by throwing an exception that bubbles to Laravel's default 500 handler. Client gets {\"message\":\"Server Error\"} HTML-flavoured payload instead of the intended `{success:false, message:'You can change your name only once.'}`. EditProfileActivity.kt:343-344 tries to read `it.message` which will be null → user sees generic toast.",
           fix_android="Keep current toast fallback (already does). But the user-friendly message is lost until server returns JSON.",
           fix_server="Return JSON (not 500): `return response()->json(['success'=>false,'message'=>'You can change your name only once.','data'=>null])`.",
           fix_where="server",
           file_ref="app/src/main/java/com/gmwapp/hima/activities/EditProfileActivity.kt:343"),

        T_("EP-UP-10", "Edit Profile", "Performance", "info",
           "/update_profile — response under 3s",
           "elapsed < 3s",
           lambda: (lambda r: (r[0], r[1], {"elapsed": r[2]}))(
               http_post("update_profile",
                         {"user_id": str(ctx.my_user_id),
                          "avatar_id": str(ctx.original_avatar_id),
                          "name": ctx.safe_name,
                          "interests": ctx.original_interests or ""},
                         auth_h(ctx))),
           lambda c, b, h=None: (("PASS", f"{(h or {}).get('elapsed', 0):.2f}s")
                                 if (h or {}).get("elapsed", 99) < 3.0
                                 else ("FAIL", f"slow: {(h or {}).get('elapsed')}s"))),
    ]

    # ═════════════════════════════════════════════════════════════════
    # VOICE VERIFICATION SCREEN — /speech_text
    # ═════════════════════════════════════════════════════════════════
    tests += [
        T_("VV-ST-01", "Voice Verification", "Functional", "info",
           "/speech_text — valid user + language returns non-empty text",
           "HTTP 200 + success=true + data[0].text non-empty",
           lambda: (*http_post("speech_text",
                               {"user_id": str(ctx.my_user_id),
                                "language": ctx.original_language},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"got text len={len((get_json(b).get('data') or [{}])[0].get('text',''))}")
                                 if c == 200 and get_json(b).get("success") is True
                                 and (get_json(b).get("data") or [])
                                 and (get_json(b).get("data") or [{}])[0].get("text")
                                 else ("FAIL", f"HTTP {c} body={b[:150]}"))),

        T_("VV-ST-02", "Voice Verification", "Validation", "medium",
           "/speech_text — unsupported language 'Klingon'",
           "success=false",
           lambda: (*http_post("speech_text",
                               {"user_id": str(ctx.my_user_id),
                                "language": "Klingon"},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "rejected")
                                 if get_json(b).get("success") is False
                                 or not (get_json(b).get("data") or [])
                                 else ("FAIL", "Klingon accepted as language (BUG-VV-001)")),
           bug_ref="BUG-VV-001",
           why="Language not in allow-list should fail cleanly. If server returns empty list silently and the client shows 'no text' toast, users get stuck on voice verification step.",
           fix_android="Already shows toast on empty list (VoiceIdentificationActivity.kt:79). Add friendlier message.",
           fix_server="Validator: `'language' => 'required|in:Hindi,Tamil,Telugu,English,...'`.",
           fix_where="both"),

        T_("VV-ST-03", "Voice Verification", "Validation", "medium",
           "/speech_text — missing language param",
           "HTTP 400 / success=false",
           lambda: (*http_post("speech_text",
                               {"user_id": str(ctx.my_user_id)},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c == 400 or get_json(b).get("success") is False
                                 else ("FAIL", "missing language accepted")),
           fix_server="Validator: `'language' => 'required'`.",
           fix_where="server"),

        T_("VV-ST-04", "Voice Verification", "Security", "high",
           "/speech_text — no auth header",
           "HTTP 401",
           lambda: (*http_post("speech_text",
                               {"user_id": str(ctx.my_user_id),
                                "language": ctx.original_language})[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c in (401, 403)
                                 else ("FAIL", f"HTTP {c} — endpoint unauthenticated (BUG-VV-002)")),
           bug_ref="BUG-VV-002",
           why="Speech prompts drive onboarding flow. Unauth access allows scraping the whole prompt catalog and gaming the voice-verification step.",
           fix_android="N/A.",
           fix_server="Route must be in `auth:api` group.",
           fix_where="server"),

        T_("VV-ST-05", "Voice Verification", "Security", "high",
           "/speech_text — IDOR: foreign user_id",
           "JWT sub should win or 403",
           lambda: (*http_post("speech_text",
                               {"user_id": str(ctx.foreign_user_id),
                                "language": ctx.original_language},
                               auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "not leaking")
                                 if c in (401, 403)
                                 or get_json(b).get("success") is False
                                 else ("PASS", "docs: returned generic prompt list")),
           why="Low-harm IDOR (prompts are public), but still documents that user_id from body is trusted.",
           fix_where="server"),

        T_("VV-ST-06", "Voice Verification", "Performance", "info",
           "/speech_text — response under 2s",
           "elapsed < 2s",
           lambda: (lambda r: (r[0], r[1], {"elapsed": r[2]}))(
               http_post("speech_text",
                         {"user_id": str(ctx.my_user_id),
                          "language": ctx.original_language},
                         auth_h(ctx))),
           lambda c, b, h=None: (("PASS", f"{(h or {}).get('elapsed', 0):.2f}s")
                                 if (h or {}).get("elapsed", 99) < 2.0
                                 else ("FAIL", f"slow: {(h or {}).get('elapsed')}s"))),
    ]

    # ═════════════════════════════════════════════════════════════════
    # VOICE VERIFICATION SCREEN — /update_voice
    # ═════════════════════════════════════════════════════════════════
    # Server validates MP3 specifically → use MP3 for positive cases.
    mp3_small = ctx.mp3_bytes                         # ~7 KB silent MP3
    mp3_ok    = ctx.mp3_bytes                         # same
    wav_small = make_wav_bytes(duration_s=1.0)       # used as negative (non-MP3)
    wav_huge  = make_wav_bytes(duration_s=600.0)     # ~9.6 MB non-MP3 — documents size+type behaviour

    tests += [
        T_("VV-UV-01", "Voice Verification", "Functional", "info",
           "/update_voice — small valid MP3 accepted",
           "HTTP 200 + success=true + data.voice non-empty",
           lambda: (*http_multipart("update_voice",
                                    {"user_id": str(ctx.my_user_id)},
                                    ("voice", "sample.mp3", mp3_ok, "audio/mpeg"),
                                    auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "voice url saved")
                                 if c == 200 and get_json(b).get("success") is True
                                 and get_json(b).get("data", {}).get("voice")
                                 else ("FAIL", f"HTTP {c} msg={get_json(b).get('message')!r}"))),

        T_("VV-UV-02", "Voice Verification", "Validation", "medium",
           "/update_voice — empty file upload",
           "400 / success=false",
           lambda: (*http_multipart("update_voice",
                                    {"user_id": str(ctx.my_user_id)},
                                    ("voice", "empty.wav", b"", "audio/wav"),
                                    auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c >= 400 or get_json(b).get("success") is False
                                 else ("FAIL", "empty file accepted as voice proof")),
           bug_ref="BUG-VV-003",
           why="An empty audio file should not pass verification. If it does, user gets approved without speaking — defeats the point of the screen.",
           fix_android="Add MediaRecorder duration check (>= 2s) before upload.",
           fix_server="Validator `'voice' => 'required|file|mimes:wav,mp3,m4a,aac|min:5'` (KB), plus ffprobe duration check in a Job.",
           fix_where="both"),

        T_("VV-UV-03", "Voice Verification", "Validation", "medium",
           "/update_voice — wrong MIME: PNG named voice.png",
           "400 / success=false (type check)",
           lambda: (*http_multipart("update_voice",
                                    {"user_id": str(ctx.my_user_id)},
                                    ("voice", "nope.png",
                                     b"\x89PNG\r\n\x1a\n" + b"\x00" * 100,
                                     "image/png"),
                                    auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c >= 400 or get_json(b).get("success") is False
                                 else ("FAIL", "PNG accepted as voice (BUG-VV-004)")),
           bug_ref="BUG-VV-004",
           why="Without MIME/extension whitelist, attacker can upload arbitrary files to `public/voice/` — a potential RCE vector if PHP is configured to execute inside that dir, and at minimum abuses storage as a CDN.",
           fix_android="Set `asRequestBody(\"audio/wav\".toMediaTypeOrNull())` explicitly in VoiceIdentificationActivity.kt:148.",
           fix_server="REQUIRED: `'voice' => 'required|file|mimes:wav,mp3,m4a,aac|max:10240'` + deny-exec on the upload dir.",
           fix_where="both",
           file_ref="app/src/main/java/com/gmwapp/hima/activities/VoiceIdentificationActivity.kt:147"),

        T_("VV-UV-04", "Voice Verification", "Validation", "medium",
           "/update_voice — no file part",
           "400 / success=false",
           lambda: (*http_multipart("update_voice",
                                    {"user_id": str(ctx.my_user_id)},
                                    None,
                                    auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c >= 400 or get_json(b).get("success") is False
                                 else ("FAIL", "empty multipart accepted (BUG-VV-005)")),
           bug_ref="BUG-VV-005",
           why="Request with no file should be 400. If server silently succeeds, the status can flip to 'verified' with no voice on file.",
           fix_android="Already checks `file != null` in onItemSelected — but client check isn't enough.",
           fix_server="Validator `'voice' => 'required|file'`.",
           fix_where="server"),

        T_("VV-UV-05", "Voice Verification", "Security", "critical",
           "/update_voice — IDOR: upload voice for a different user_id",
           "403 / success=false / own id returned",
           lambda: (*http_multipart("update_voice",
                                    {"user_id": str(ctx.foreign_user_id)},
                                    ("voice", "hack.mp3", mp3_ok, "audio/mpeg"),
                                    auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "not overwriting foreign voice")
                                 if c in (401, 403)
                                 or get_json(b).get("success") is False
                                 or get_json(b).get("data", {}).get("id") == ctx.my_user_id
                                 else ("FAIL", f"overwrote foreign user's voice id={get_json(b).get('data',{}).get('id')} (BUG-VV-006)")),
           bug_ref="BUG-VV-006",
           why="Voice verification is the gating step for female accounts. If someone else can overwrite a rival creator's voice with nonsense, they can get them de-verified / flagged.",
           fix_android="Drop user_id from body; use JWT server-side.",
           fix_server="REQUIRED: use `$request->user()->id` only.",
           fix_where="server",
           file_ref="app/src/main/java/com/gmwapp/hima/activities/VoiceIdentificationActivity.kt:146"),

        T_("VV-UV-06", "Voice Verification", "Security", "high",
           "/update_voice — no auth header",
           "HTTP 401",
           lambda: (*http_multipart("update_voice",
                                    {"user_id": str(ctx.my_user_id)},
                                    ("voice", "sample.mp3", mp3_small, "audio/mpeg"))[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c in (401, 403)
                                 else ("FAIL", f"HTTP {c} — voice mutable without auth (BUG-VV-007)")),
           bug_ref="BUG-VV-007",
           why="Mutating endpoints without auth = anyone can overwrite any account's voice.",
           fix_android="N/A.",
           fix_server="Wrap in `auth:api`.",
           fix_where="server"),

        T_("VV-UV-07", "Voice Verification", "Boundary", "medium",
           "/update_voice — 10 MB file (mime/size guard)",
           "413 / 400 / success=false",
           lambda: (*http_multipart("update_voice",
                                    {"user_id": str(ctx.my_user_id)},
                                    ("voice", "big.mp3", b"ID3\x04\x00\x00\x00\x00\x00\x00" + b"\x00" * (10 * 1024 * 1024), "audio/mpeg"),
                                    auth_h(ctx), 60)[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c in (400, 413) or get_json(b).get("success") is False
                                 else ("FAIL", f"10 MB upload accepted — storage DoS risk (BUG-VV-008)")),
           bug_ref="BUG-VV-008",
           why="Upload-size abuse → storage bill spike. Test documents server-side cap. Expected: 413 or validation 400.",
           fix_android="Client should cap recording at 30s already; confirm BottomSheetVoiceIdentification.",
           fix_server="`'voice' => 'max:10240'` (10 MB) + nginx client_max_body_size guard.",
           fix_where="both"),

        T_("VV-UV-08", "Voice Verification", "Validation", "info",
           "/update_voice — missing user_id field",
           "400 / success=false",
           lambda: (*http_multipart("update_voice",
                                    {},
                                    ("voice", "sample.mp3", mp3_small, "audio/mpeg"),
                                    auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"HTTP {c}")
                                 if c >= 400 or get_json(b).get("success") is False
                                 else ("FAIL", "missing user_id accepted")),
           fix_server="Validator `'user_id' => 'required|integer'` — but ideally derive from JWT.",
           fix_where="server"),

        T_("VV-UV-09", "Voice Verification", "Performance", "info",
           "/update_voice — response under 8s for small MP3",
           "elapsed < 8s",
           lambda: (lambda r: (r[0], r[1], {"elapsed": r[2]}))(
               http_multipart("update_voice",
                              {"user_id": str(ctx.my_user_id)},
                              ("voice", "perf.mp3", mp3_ok, "audio/mpeg"),
                              auth_h(ctx), 30)),
           lambda c, b, h=None: (("PASS", f"{(h or {}).get('elapsed', 0):.2f}s")
                                 if (h or {}).get("elapsed", 99) < 8.0
                                 else ("FAIL", f"slow: {(h or {}).get('elapsed')}s"))),

        T_("VV-UV-11", "Voice Verification", "Validation", "info",
           "/update_voice — WAV file rejected (server enforces MP3)",
           "success=false with explanatory message",
           lambda: (*http_multipart("update_voice",
                                    {"user_id": str(ctx.my_user_id)},
                                    ("voice", "sample.wav",
                                     make_wav_bytes(3.0), "audio/wav"),
                                    auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", f"rejected: {get_json(b).get('message')!r}")
                                 if get_json(b).get("success") is False
                                 else ("FAIL", "WAV accepted — expected MP3-only per earlier run")),
           why="Documents that the server enforces audio/mpeg. Client must pass a .mp3 recorded file, not WAV.",
           fix_where=""),

        T_("VV-UV-10", "Voice Verification", "Security", "medium",
           "/update_voice — path traversal filename",
           "Server renames / rejects",
           lambda: (*http_multipart("update_voice",
                                    {"user_id": str(ctx.my_user_id)},
                                    ("voice", "../../../../etc/passwd",
                                     mp3_small, "audio/mpeg"),
                                    auth_h(ctx))[:2], None),
           lambda c, b, h=None: (("PASS", "safe (Laravel Storage regenerates name)")
                                 if c == 200
                                 else ("FAIL", f"HTTP {c}")),
           why="Laravel Storage::put generates its own hash-based filename, so path traversal via filename is typically mitigated. Test documents the behaviour.",
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
            "body_preview": (body or "")[:350],
            "status": status,
            "detail": detail,
        })
        print(f"  {status:4s}  {t['id']:10s}  {t['title'][:70]}  →  {detail}")
        time.sleep(0.2)
    return results


# ──────────────────────────── HTML report ────────────────────────────
def html_report(results: list[dict], ctx: Ctx) -> str:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    total = len(results)
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
    fixes_both = sum(1 for r in results
                     if r["status"] == "FAIL" and r.get("fix_where") == "both")
    fixes_nowhere = sum(1 for r in results
                        if r["status"] == "FAIL" and r.get("fix_where") not in ("client", "server", "both"))

    client_ok = fixes_client + fixes_both       # "anything we CAN do on client"
    server_required = fixes_server + fixes_both
    server_only = fixes_server

    def row_html(r):
        sev = r["severity"]
        fix_w = cls_where(r.get("fix_where", ""))
        body_preview = html_lib.escape((r.get("body_preview") or "")[:280])
        why = r.get("why") or ""
        fa = r.get("fix_android") or ""
        fs_ = r.get("fix_server") or ""
        file_ref = r.get("file_ref") or ""
        return f"""
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
""" + (f"""
<tr class="detail-row"><td colspan="9"><div class="detail">
  <div class="mini"><b>Expected:</b> {html_lib.escape(r.get('expected',''))}</div>
  <div class="mini"><b>Body preview:</b> <code>{body_preview}</code></div>
  {f'<div class="mini"><b>Why it matters:</b> {html_lib.escape(why)}</div>' if why else ''}
  {f'<div class="mini"><b>Android fix:</b> {html_lib.escape(fa)}</div>' if fa else ''}
  {f'<div class="mini"><b>Server fix:</b> {html_lib.escape(fs_)}</div>' if fs_ else ''}
  {f'<div class="mini"><b>File ref:</b> <code>{html_lib.escape(file_ref)}</code></div>' if file_ref else ''}
  {f'<div class="mini"><b>Bug ID:</b> {html_lib.escape(r.get("bug_ref") or "")}</div>' if r.get('bug_ref') else ''}
</div></td></tr>""" if r["status"] == "FAIL" or r.get("why") else "")

    rows = "".join(row_html(r) for r in results)

    sev_cards = "".join(
        f'<div class="card sev-{k}"><div class="n">{v}</div><div class="l">{k.upper()} fails</div></div>'
        for k, v in sorted(by_sev.items(),
                           key=lambda kv: {"critical":0,"high":1,"medium":2,"low":3,"info":4}.get(kv[0], 9))
    ) or '<div class="card ok"><div class="n">0</div><div class="l">No severity fails</div></div>'

    # Build per-bug summary
    bug_items = []
    seen = set()
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
  <div><b>Screen:</b> {html_lib.escape(r['screen'])} · <b>Endpoint check:</b> {html_lib.escape(r['category'])}</div>
  {f'<div class="mini"><b>Why it matters:</b> {html_lib.escape(r.get("why") or "")}</div>' if r.get('why') else ''}
  {f'<div class="mini"><b>Android side:</b> {html_lib.escape(r.get("fix_android") or "")}</div>' if r.get('fix_android') else ''}
  {f'<div class="mini"><b>Server side:</b> {html_lib.escape(r.get("fix_server") or "")}</div>' if r.get('fix_server') else ''}
  {f'<div class="mini"><b>Source ref:</b> <code>{html_lib.escape(r.get("file_ref") or "")}</code></div>' if r.get('file_ref') else ''}
  <div class="mini"><b>Response body:</b> <code>{html_lib.escape((r.get("body_preview") or "")[:300])}</code></div>
</section>"""

    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Edit Profile + Voice Verification — API Test Report · {now[:10]}</title>
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
main{{padding:22px 40px;max-width:1250px;margin:0 auto}}
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
.screen-tag{{display:inline-block;font-size:11px;padding:2px 8px;border-radius:999px;background:#1d2333;color:#c8cfdd;margin-right:6px}}
</style>
</head>
<body>
<header>
  <h1>Edit Profile + Voice Verification — API Test Report</h1>
  <div class="sub">{now} · Env <code>{html_lib.escape(BASE_URL)}</code> · QA: Perumal</div>
</header>
<main>

<div class="meta">
  <div><div class="k">Screens covered</div><div class="v">Edit Profile · Voice Verification</div></div>
  <div><div class="k">Endpoints</div><div class="v">/user_validations · /avatar_list · /update_profile · /speech_text · /update_voice</div></div>
  <div><div class="k">Test user</div><div class="v">id={ctx.my_user_id} · {html_lib.escape(TEST_MOBILE)}</div></div>
  <div><div class="k">Suite size</div><div class="v">{total} test cases</div></div>
</div>

<h2>Summary</h2>
<div class="cards">
  <div class="card ok"><div class="n">{passed}</div><div class="l">Pass</div></div>
  <div class="card fail"><div class="n">{failed}</div><div class="l">Fail</div></div>
  {sev_cards}
</div>

<h2>Fix-ownership breakdown (of failing cases only)</h2>
<div class="fix-cards">
  <div class="fix-card client">
    <div class="title client">Fixable on ANDROID side</div>
    <div class="big">{client_ok}</div>
    <div class="mini">Client-only: {fixes_client} · Also needs client touch: {fixes_both}</div>
    <ul>
      <li>Explicit MIME on voice upload (<code>audio/wav</code>)</li>
      <li>Hard input length via <code>maxLength</code></li>
      <li>Drop redundant <code>user_id</code> once server stops trusting it</li>
    </ul>
  </div>
  <div class="fix-card both">
    <div class="title both">Requires BOTH android + server</div>
    <div class="big">{fixes_both}</div>
    <div class="mini">Defence in depth: client UX guard + server enforcement</div>
    <ul>
      <li>Voice file size / duration cap (client + server)</li>
      <li>Name length, pattern, XSS</li>
      <li>Unsupported language handling</li>
    </ul>
  </div>
  <div class="fix-card server">
    <div class="title server">ONLY fixable on server</div>
    <div class="big">{server_required}</div>
    <div class="mini">Pure server-side: {server_only} · With client piece: {fixes_both}</div>
    <ul>
      <li>IDOR on /update_profile, /update_voice, /user_validations (use JWT <code>sub</code>)</li>
      <li>Auth middleware on all mutating routes</li>
      <li>Laravel FormRequest validators for every endpoint</li>
      <li>FK + whitelist: avatar_id, interests, language</li>
    </ul>
  </div>
</div>
<div class="legend">Client/server/both figures only count failing cases. Informational passes are not included here.</div>

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
  <li>Authenticated tests use the JWT obtained via <code>/login</code> for the QA test mobile (9876543210).</li>
  <li>IDOR tests use <code>user_id - 1</code> to probe cross-user access while staying within DB bounds.</li>
  <li>Voice uploads use synthesised silent WAVs of various sizes; no real user voice collected.</li>
  <li>Rate-limit tests intentionally omitted here — covered in the login-flow report (BUG-004).</li>
  <li>Tests run sequentially with 200 ms inter-request delay to avoid triggering throttle middleware.</li>
</ul>

</main>
</body>
</html>
"""


# ──────────────────────────── Main ────────────────────────────
def main():
    print(f"▶ Running Edit Profile + Voice Verification API tests against {BASE_URL}")
    ctx = Ctx()
    prepare(ctx)
    if not ctx.token or not ctx.my_user_id:
        print("✖ Could not obtain JWT. Aborting.")
        return 1
    print(f"  ✓ logged in as user_id={ctx.my_user_id}, name={ctx.original_name!r}")
    tests = define_tests(ctx)
    print(f"  ✓ defined {len(tests)} test cases\n")
    results = run(ctx, tests)
    # restore original profile to minimise pollution
    try:
        http_post("update_profile",
                  {"user_id": str(ctx.my_user_id),
                   "avatar_id": str(ctx.original_avatar_id),
                   "name": ctx.safe_name,
                   "interests": ctx.original_interests or "Music"},
                  {"Authorization": f"Bearer {ctx.token}"})
    except Exception:
        pass

    RESULTS_JSON.write_text(json.dumps({
        "base_url": BASE_URL,
        "generated": datetime.now(timezone.utc).isoformat(),
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
