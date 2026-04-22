# Login / Signup Flow — API Test Report

| Field | Value |
|---|---|
| Tester | Perumal (QA) |
| Date | 2026-04-21 |
| App | Hima (Android) |
| Branch under test | `login_flow_test_21_04_2026` |
| Environment tested | **Dev** — `https://demohima.himaapp.in/api/auth/` |
| Prod base (not exercised) | `https://himaapp.in/api/auth/` |
| Test type | Black-box API testing + client-code review |
| Tool | `curl` + Retrofit source review |
| Total cases executed | **52** |

---

## 1. Executive Summary

The auth flow is **functionally working** (happy path passes), but the design has **three critical security defects** that make the login/signup flow effectively unsafe for production. They should be treated as release blockers.

### Verdict: **FAIL — do not ship to prod until S1 defects are fixed.**

| Severity | Count | Status |
|---|---|---|
| S1 (Critical / Blocker) | **4** | Open |
| S2 (High) | **4** | Open |
| S3 (Medium) | **5** | Open |
| S4 (Low / Cosmetic) | **3** | Open |
| Passed | **8** | — |

---

## 2. Scope

Flow covered end-to-end:

1. `first_install` — install tracking ping
2. `appsettings_list` — app version/config
3. `send_otp` — request OTP SMS
4. `login` — verify + authenticate (also used post-OTP)
5. `register` — create new user (male / female variants)
6. `avatar_list` — list avatars by gender
7. `userdetails` — fetch user profile
8. `delete_users` — account deletion
9. Truecaller OAuth path (reviewed in code only — cannot exercise without SDK)

Source references: [ApiManager.kt](app/src/main/java/com/gmwapp/hima/retrofit/ApiManager.kt), [LoginViewModel.kt](app/src/main/java/com/gmwapp/hima/viewmodels/LoginViewModel.kt), [VerifyOTPActivity.kt](app/src/main/java/com/gmwapp/hima/activities/VerifyOTPActivity.kt#L138-L156), [NewLoginActivity.kt](app/src/main/java/com/gmwapp/hima/activities/NewLoginActivity.kt), [AppModule.kt](app/src/main/java/com/gmwapp/hima/dagger/AppModule.kt).

---

## 3. Critical Defects (S1 — block release)

### 🔴 BUG-001 — OTP verification is client-side only; `/login` accepts any code

**Severity:** S1 Critical · **Repro:** 100% · **Evidence:** TC-23, TC-24, TC-25, TC-26

The OTP comparison happens in the Android client at [VerifyOTPActivity.kt:143](app/src/main/java/com/gmwapp/hima/activities/VerifyOTPActivity.kt#L143):

```kotlin
if (enteredOTP == otp.toString() || enteredOTP == "011011") { login(mobileNumber) }
```

The client then calls `/login` with `code="0", code_verifier="0"` — the server **never re-validates the OTP**.

**Proof-of-exploit (no SMS ever triggered):**
```
POST /api/auth/login
mobile=9876543210&code=0&code_verifier=0
→ HTTP 200
  { "success":true, "registered":true, "token":"eyJ0eX...<valid JWT>",
    "data": { "id":456976, "mobile":"9876543210", ... } }
```

Even **random garbage** in `code`/`code_verifier` returns a valid token (TC-26: `code=RANDOMGARBAGE&code_verifier=NOTAVERIFIER` → HTTP 200 + JWT).

**Impact:** Complete account takeover of any user given only a phone number. The Truecaller path (which uses real OAuth code + PKCE verifier) is the *only* code path server-validated; the manual-OTP path is unauthenticated login in disguise.

**Hardcoded backdoor OTP `"011011"`** is present at [VerifyOTPActivity.kt:143](app/src/main/java/com/gmwapp/hima/activities/VerifyOTPActivity.kt#L143). Even if OTP were server-side, this backdoor still lets anyone into any account via a patched APK.

**Fix (server):**
- `send_otp` must generate the OTP server-side, store it with `{mobile, otp, expiry≤5min, attempt_count}`.
- `/login` manual path must accept the raw OTP from the client and compare to stored value; expire after use; cap attempts.
- Remove the `"011011"` backdoor from the client.

---

### 🔴 BUG-002 — IDOR on `/userdetails` leaks PII of every user

**Severity:** S1 Critical · **Repro:** 100% · **Evidence:** TC-34, TC-35

A valid JWT for user A can fetch user B's full profile by simply changing `user_id` in the body. The server does **not** check that `user_id` matches the JWT's `sub` claim.

```
POST /api/auth/userdetails   (Auth: Bearer <my-token, sub=456976>)
user_id=456975
→ HTTP 200
  { "data": { "id":456975, "name":"NisdB403", "mobile":"8464643464",
              "refer_code":"GZDB1289", ... } }

user_id=456974
→ HTTP 200 { "data": { "id":456974, "mobile":"6162326296", ... } }
```

**Impact:** Any logged-in user can enumerate all user IDs (they are sequential integers) and dump every user's mobile number, name, gender, referral code, and more → **mass PII breach under Indian DPDP Act**.

**Fix:** `/userdetails` (and any other user-scoped endpoint) must derive the user id from the JWT `sub`, not trust the request body. Remove `user_id` from the payload entirely.

---

### 🔴 BUG-003 — CORS reflects arbitrary origins with credentials

**Severity:** S1 Critical · **Repro:** 100% · **Evidence:** TC-51

```
OPTIONS /api/auth/login  (Origin: https://evil.com)
→ Access-Control-Allow-Origin: https://evil.com
  Access-Control-Allow-Credentials: true
```

The server reflects *any* Origin and allows credentials. Combined with BUG-001, a malicious website opened on the victim's phone (or any browser logged in via web session cookies) can silently call `/login` for arbitrary phone numbers and exfiltrate JWTs.

**Fix:** Hard-code allowed origins (app typically needs none — mobile doesn't honour CORS). Disable `Access-Control-Allow-Credentials` for the API origin or restrict `Allow-Origin` to an explicit whitelist.

---

### 🔴 BUG-004 — No rate limiting on `send_otp` or `/login`

**Severity:** S1 Critical · **Repro:** 100% · **Evidence:** Burst tests

20 consecutive `send_otp` calls to the same mobile in < 2 seconds — **all returned 200** (each triggers a real SMS to the target number). 10 consecutive `/login` calls — all 200.

**Impact:**
- **SMS bombing** — attacker can weaponise the endpoint to spam any Indian mobile number with SMS, costing the business the per-SMS fee and harassing victims.
- Once OTP moves server-side (BUG-001 fix), absence of rate-limiting means OTPs are brute-forceable (10^6 space, 60-sec TTL, no attempt cap).

**Fix:** Per-mobile + per-IP rate limit on `send_otp` (e.g., 1 / 30s, 5 / hour, 10 / day). Per-mobile attempt cap on `/login` (5 wrong OTPs → cooldown 15 min).

---

## 4. High Defects (S2)

### 🟠 BUG-005 — `send_otp` returns HTTP 500 (HTML) on any invalid input

**Evidence:** TC-03…TC-10

Every malformed `send_otp` (missing params, non-numeric mobile, etc.) returns an HTTP 500 with a Laravel "Server Error" HTML page instead of a JSON 400. This is:
- A leak of framework identity (fingerprintable).
- Confusing for clients — impossible to show a user-friendly error.
- Symptom of a missing `FormRequest` validator on the controller.

**Fix:** Add request validation mirroring what `/login` already does:
```json
{ "success": false, "mobile": ["The mobile must be 10 digits or required."] }
```

### 🟠 BUG-006 — OTP is supplied by the client (`send_otp` takes `otp` as a param)

**Evidence:** TC-11, TC-16, TC-18

The `send_otp` endpoint accepts the `otp` **from the client** and simply pushes it to SMS (TC-16 even echoed `<script>alert(1)</script>` as the "OTP"; TC-18 accepted `otp=-1`). Combined with BUG-001, the client knows the OTP before sending → trivially enters it without SMS. Even after BUG-001 is fixed, letting the client pick the OTP is unsound (low entropy, predictable).

**Fix:** Remove the `otp` field from the request. Server generates a cryptographically random 6-digit OTP.

### 🟠 BUG-007 — `/register` does not require OTP / any proof of mobile ownership

**Evidence:** TC-45

Calling `/register` with a never-seen mobile creates a new account immediately, including a referral code and a JWT — no OTP, no signal from `send_otp` required. Mass-signup / user-pollution possible.

**Fix:** After fixing BUG-001, `/register` must require a valid server-issued OTP token (single-use, ≤ 5 min TTL) that proves the mobile was just verified.

### 🟠 BUG-008 — JWT lifetime is 90 days with no revocation

**Evidence:** TC-47 (JWT decoded)

```
{ "iat": 1776761750, "exp": 1784537750, "sub": "456976", ... }
```

`exp − iat = 7,776,000s = 90 days`. No logout endpoint exists, no refresh-token rotation, and JWTs are stateless — a stolen token is a **90-day golden key**. Client clears local storage on logout but the token remains valid on any other device.

**Fix:** Short-lived access token (≤ 1 hour) + refresh token stored server-side with revocation list. Add `/logout` that blacklists the current `jti`.

---

## 5. Medium Defects (S3)

### 🟡 BUG-009 — `country_code=xyz` accepted on `send_otp` (TC-12)
Returns `success:true`. PHP silently casts the string to `0`, but only `country_code=0` triggers the "Country code is required" branch (TC-13). Any non-empty non-numeric string → success. Add strict integer validation.

### 🟡 BUG-010 — HTTP 200 on business-logic failures (TC-41, TC-42, TC-43, TC-44, TC-17, TC-13)
`/register`, `/avatar_list`, `/send_otp`, etc. return HTTP 200 with `success:false` for validation failures. Inconsistent with `/login` (which correctly returns 400). Client can't use HTTP status alone — breaks standard error handling, retry logic, and monitoring.

### 🟡 BUG-011 — `first_install` leaks client IP in the response (TC-01)
```json
{ "ip_address":"103.163.232.121", "attributed_to_link":false, ... }
```
No reason to echo the caller's IP back. Minor info disclosure, but unusual — should be removed.

### 🟡 BUG-012 — Referral code is not validated on `/register` (TC-45)
`referred_by=INVALIDXYZ` → account created successfully, referral silently ignored. Should either reject with a clear error or be validated + attributed. Currently creates confusion in referral accounting.

### 🟡 BUG-013 — No `Content-Length` / size cap on `/register` (TC-50)
10 KB `describe_yourself` accepted without trimming. Combined with no rate-limit → DoS / DB-bloat vector. Enforce max-length per field, ideally at the framework layer.

---

## 6. Low Defects (S4)

### 🔵 BUG-014 — Development build's Cashfree **test** credentials are hard-coded in source
File: [CashfreeApiService.kt](app/src/main/java/com/gmwapp/hima/verification/CashfreeApiService.kt)
```
x-client-id: CF10666599D157Q7U76C7C73BOAF4G
x-client-secret: cfsk_ma_test_40c029f442f352110cb1c3f85aebf06e_085bf549
```
These are test keys, but they should still move to BuildConfig / Gradle secrets rather than committing into Git history.

### 🔵 BUG-015 — Heavy `Log.d` of request/response in auth flow
Files: [LoginViewModel.kt:45,47](app/src/main/java/com/gmwapp/hima/viewmodels/LoginViewModel.kt#L45), [VerifyOTPActivity.kt:139-154](app/src/main/java/com/gmwapp/hima/activities/VerifyOTPActivity.kt#L139-L154).
`Log.d("VerifyOTP", "OTP entered: $enteredOTP")` — logs the entered OTP. On Android < 12 these are readable by any app with READ_LOGS. Gate all `Log.d` behind `BuildConfig.DEBUG`.

### 🔵 BUG-016 — `delete_users` error message is cryptic (TC-52)
Returns `"please mail your mobile number and describe your issue"` which appears to be a catch-all when the request can't be honoured. Replace with a specific message (e.g., "You can only delete your own account.").

---

## 7. Endpoint Coverage Matrix

| # | Endpoint | Method | Auth | Status | Notes |
|---|---|---|---|---|---|
| 1 | `/first_install` | POST | none | ✅ Works | BUG-011 IP leak |
| 2 | `/appsettings_list` | POST | none | ✅ Works | Returns minimum app version = 39 |
| 3 | `/send_otp` | POST | none | ⚠️ BUG-001/004/005/006/009 | Client picks OTP; no rate limit; 500 on bad input |
| 4 | `/login` | POST | none | 🔴 BUG-001/004/008 | No OTP validation server-side |
| 5 | `/register` | POST | none | ⚠️ BUG-007/010/012/013 | Creates user with no mobile-ownership proof |
| 6 | `/avatar_list` | POST | Bearer | ✅ Works | Good validation |
| 7 | `/userdetails` | POST | Bearer | 🔴 BUG-002 | IDOR — returns any user's data |
| 8 | `/delete_users` | POST | Bearer | ✅ Works | Server correctly rejects cross-user delete |

---

## 8. Client-Side Validation — Observations

Reviewed against [NewLoginActivity.kt](app/src/main/java/com/gmwapp/hima/activities/NewLoginActivity.kt), [FemaleAboutActivity.kt](app/src/main/java/com/gmwapp/hima/activities/FemaleAboutActivity.kt):

| Field | Client rule | Server rule | Gap |
|---|---|---|---|
| Mobile | regex `^[6-9]\d{9}$`, 10 digits | "must be 10 digits" (`/login`) or missing (`/send_otp`) | Server doesn't enforce 6-9 prefix |
| OTP | 6 digits, or `011011` bypass | *not enforced at all* | See BUG-001 |
| Age (female) | 18–99 | not tested (requires full flow) | Verify server enforces 18+ |
| Description (female) | ≥ 15 chars | none seen | Server should mirror |
| Interests | 1–4 required | not validated | Server accepts any string |
| Gender | male/female | validated on `/avatar_list` only | Inconsistent |

---

## 9. Passed Test Cases (happy path)

| # | Case | Result |
|---|---|---|
| TC-02 | `appsettings_list` returns version & socket URL | ✅ |
| TC-11 | `send_otp` valid 10-digit mobile → 200 `OTP sent successfully` | ✅ |
| TC-23 | `/login` returns JWT + user data for valid mobile | ✅ (but see BUG-001) |
| TC-29 | `/userdetails` with valid token returns own profile | ✅ |
| TC-38 | `/avatar_list gender=male` returns 9 avatars | ✅ |
| TC-39/40 | `/avatar_list` rejects invalid/missing gender | ✅ |
| TC-42 | `/register` rejects duplicate mobile | ✅ |
| TC-46 | `GET /login` correctly returns 405 Method Not Allowed | ✅ |

---

## 10. Recommendations (in fix order)

1. **Move OTP to the server.** Generate, store hashed with expiry, validate on `/login` and `/register`. Remove `otp` from `send_otp` request body; remove `"011011"` backdoor from client.
2. **Scope user-data endpoints by JWT sub.** Don't trust `user_id` in the body. Applies to `/userdetails` and every other `user_id`-taking endpoint (audit all 60+ endpoints in ApiManager.kt).
3. **Lock down CORS.** Whitelist `allowed_origins`. Disable `Allow-Credentials` unless strictly required.
4. **Rate-limit `send_otp` and `/login`.** Per mobile + per IP + global. Cap OTP attempts.
5. **Shorten JWT lifetime + add revocation.** Access ≤ 1 h, refresh with rotation, `/logout` blacklists `jti`.
6. **Convert 500 HTML errors to 4xx JSON.** Add `FormRequest` validators on every controller.
7. **Strip `Log.d` from release builds.** Wrap in `BuildConfig.DEBUG`.
8. **Fix HTTP status semantics.** 4xx for validation / business failures, not 200-with-false.

---

## 11. Re-test Plan

After devs deliver fixes, minimum re-test set:

1. **BUG-001 regression:** call `/login` with `code=0, code_verifier=0` and no prior `send_otp` → expect 401/422.
2. **BUG-001 regression:** call `/login` with wrong OTP → expect error + attempt counter increment.
3. **BUG-002 regression:** authenticate as user A, call `/userdetails user_id=<B>` → expect 403 or own-user response ignoring body id.
4. **BUG-003 regression:** `OPTIONS /login Origin: https://evil.com` → expect no `Allow-Origin` header for that origin.
5. **BUG-004 regression:** burst 10× `send_otp` in 10s → expect 429 after threshold.
6. **BUG-005 regression:** `send_otp` with missing fields → expect 400 + JSON (not 500 HTML).
7. **BUG-008 regression:** call `/logout` then reuse token → expect 401.
8. **Happy path:** full signup + login + profile fetch still works end-to-end.

---

## 12. Artifacts

- Raw curl transcripts: `/tmp/hima_api_test/report.txt` on tester machine.
- Two real user IDs observed during IDOR test (**please delete after fix verification**): 456974 (mobile 6162326296), 456975 (mobile 8464643464).
- Test accounts auto-created during run (ID 456976 mobile 9876543210, ID 456978 mobile 9000000003) — please purge from `demohima` DB.

---

*Report generated by QA. Questions → perumal@innovfix.in.*
