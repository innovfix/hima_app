# Checkpoint 03 — Authentication, Onboarding, Profile, and Configuration

Status: complete

Strong review: 2026-07-15 IST. The critical OTP/login/register/JWT/logout/FCM route and controller wiring was rechecked read-only against the Android source and `/var/www/himaapp` on app-1, app-2, and app-3. The targeted FCM follow-up also traced both registration workers, logout invalidation, login cancellation, WorkManager account guards, and the live token-reassignment implementation. The conclusions below remain valid; no authentication or provider request was sent.

Final batch-review correction: production `voice_verify_mode` changed to `enforce` at 2026-07-15 15:40:01 IST. Current inline scoring is active on app-1/app-2, while app-3 still lacks `VoiceVerificationService.php`; fresh caught app-3 failures were present through 15:40:14 IST. No voice upload or provider request was initiated by the audit.

Scope: end-to-end Android and live-Laravel behavior for OTP/login, JWT/session lifecycle, registration, referrals, install attribution, AI onboarding, gender/language/profile data, female voice verification, startup routing, settings/appsettings, feature configuration, and account deletion/logout.

Production, databases, services, repositories, Git, and external communications remain read-only. No OTP, login, registration, notification, or third-party request will be initiated during static analysis.

## Entry and routing overview

- Cold start begins in `SplashScreenActivity`, which consults cached `UserData`/JWT and refreshes a cached female profile before deciding among Main, Almost Done, Voice Identification, or New Login. The previously documented no-network early return can strand startup before routing.
- `NewLoginActivity` is the current launcher-facing login surface. It supports Truecaller OAuth plus a manual mobile/SMS path, while `LoginActivity` remains manifest-registered legacy residue. Existing users are routed directly by gender/status; unregistered numbers enter gender/avatar then gender-specific details/language, after which `register` creates the account and returns a JWT.
- New male users call authenticated `language_config` after registration. `enabled_feature=ai_onboarding` routes to Get Name then AI Onboarding; `autopay` or `none` routes directly to Main. On API failure the client infers autopay for a static North-Indian-language set and otherwise chooses `none`; it deliberately never infers AI onboarding.
- New female users collect age/interests/description before language/registration, then route by status: verified (`2`) to Main, pending (`1`) to Almost Done, otherwise to Voice Identification.

## Confirmed critical authentication bypass

The manual OTP flow is not server-authenticated:

- In the current path, `NewLoginActivity` generates the six-digit OTP locally with a time-seeded non-cryptographic random generator, sends that chosen value to the backend, and verifies both manual entry and SMS Retriever autofill against its in-memory value. In the legacy path, `LoginActivity` generates the OTP and passes it to `VerifyOTPActivity`, which performs the local comparison.
- Android sends that chosen OTP to public `POST /api/send_otp`; Laravel merely forwards the caller-supplied value to the SMS provider. It does not generate, hash, persist, expire, bind, or attempt-limit an OTP.
- Both the current `NewLoginActivity` manual-verification branch and the legacy `VerifyOTPActivity` accept a hard-coded review/QA bypass code (`011011`) embedded in the APK.
- On local success Android calls `POST /api/login` with the mobile number and placeholder `code=0`, `code_verifier=0` in the fallback branch, or the Truecaller `code`/`code_verifier` pair in the stronger branch.
- The live Laravel login handler validates only the mobile-number format in this fallback branch, looks up that user, and immediately issues a 90-day JWT. It does not require any OTP, SMS-session proof, device proof, password, or other challenge. `register()` also returns a JWT after creating the account, so both first registration and later login mint the same long-lived token shape.

Therefore anyone able to call the public login endpoint can obtain a valid JWT for any registered mobile number without receiving an OTP. The hard-coded app bypass is not the root cause; even a modified/raw client can skip the OTP UI entirely. This is a critical account-takeover vulnerability.

The Truecaller branch itself exchanges authorization code + PKCE verifier with Truecaller and requires a verified returned phone number before issuing a JWT, but the unauthenticated mobile fallback makes that stronger branch optional.

## Registration and OTP abuse surface

- Public `POST /api/register` accepts mobile, profile, gender, language, and optional referral data, creates the user, and returns a 90-day JWT. It has no server-side proof that the caller owns the mobile number and no registration ticket derived from OTP/Truecaller. An arbitrary unused valid-format mobile can therefore be pre-registered by another caller.
- `send_otp` has no dedicated rate limit beyond the effectively permissive 60,000/minute global API limit. Because the caller supplies the target mobile and OTP, it can be abused as an SMS/cost primitive.
- The live SMS provider credential is hard-coded in `AuthController` source instead of secret configuration. Its value was deliberately not copied into this audit.
- Android logs OTP values, SMS contents, Truecaller authorization material, API URLs/responses, cached referral values, and login response objects. A login response contains the JWT and user profile, so release logging/device log capture can expose credentials and PII.

No login, OTP, registration, Truecaller, or SMS-provider request was made to confirm these code-level findings.

## Confirmed authenticated cross-account profile/voice access

Several authenticated profile endpoints verify that *a* JWT is present but then act on a caller-supplied `user_id` rather than binding the operation to the JWT subject:

- `POST /api/auth/userdetails` looks up the requested account. It conditionally hides only the mobile number and PAN for a non-owner, while still returning sensitive fields including balance/coins, bank and branch details, account number, IFSC, account-holder name, UPI ID, referral data, voice path, status, and profile data. This is an authenticated IDOR with financial and personal-data exposure.
- `POST /api/auth/update_profile` saves name, avatar, and interests to the requested `user_id` without checking it against the authenticated user. Any valid token can therefore modify another account and can consume that account's one-time name-change allowance. The response also includes the target mobile number.
- `POST /api/auth/update_voice` uploads an MP3 for the requested `user_id`, changes that account's voice/status to pending, and invokes the AI verification path without an ownership check. Any valid token can tamper with another user's verification workflow. Production node drift makes the outcome node-dependent: app-3 lacks `VoiceVerificationService`, and uploads landing there also participate in the asymmetric-media-replication problem documented in Checkpoint 02.
- `user_validations` similarly accepts an arbitrary user ID, although its current effect is validation/availability checking rather than a direct state mutation. `delete_users` currently exits with an instruction to contact support; the old deletion implementation is commented and is therefore not presently an exploitable IDOR.

This is not a framework-wide guarantee one way or the other: some later methods explicitly use the authenticated user's ID. Every feature must be checked individually. Android frequently refreshes `userdetails` and replaces its cached `UserData`; a wrong or manipulated requested ID could therefore poison the local profile cache while the bearer token still represents a different account.

## Registration behavior and first-login telemetry

- Registration initializes zero coins, applies the default coupon when configured, selects Cashfree subscription handling for Tamil/Malayalam and PhonePe for other languages, and accepts an optional same-gender referral code. It does not prove mobile ownership, as noted above.
- Generated display names and referral/reference codes use non-cryptographic shuffling. Their database uniqueness constraints and collision behavior still need explicit schema/runtime verification.
- Android's male path selects gender/avatar then language, registers, stores the returned user/JWT, fetches `language_config`, and routes to AI onboarding, autopay, or Main. The female path gathers age/interests/description before language/registration and then routes by verification status.
- Successful registration triggers multiple attribution/analytics paths (mobile-measurement partner, Adjust, Meta, Firebase, and backend analytics). None was invoked during this audit.
- Android logs signing-certificate/authentication details in code paths that are not consistently debug-gated, expanding the release-build credential/PII logging concern.

## Production identity collisions and ambiguous account selection

Live schema inspection confirms that `users.mobile`, `users.name`, and `users.refer_code` have ordinary non-unique indexes, not unique constraints. Aggregate-only production queries (no identifier values returned) found:

- 1,575 duplicate-mobile groups;
- 15,709 duplicate-name groups;
- 946 duplicate-referral-code groups.

The code does not safely tolerate those collisions:

- Registration performs an application-level “mobile exists” check, saves a new `Users` row, then performs a fresh `Users::where('mobile', $mobile)->first()` and mints the JWT for that independently selected row. Under a concurrent registration or an already duplicated mobile, the response can describe the newly inserted row while the JWT belongs to an older row. There is no transaction/unique constraint to close the race.
- Login likewise uses an unordered `first()` for a mobile, so duplicate-mobile owners have no deterministic row identity.
- Referral generation does not retry for uniqueness, and validation, registration attribution, and later reward code resolve `refer_code` with unordered `first()` calls. Existing duplicate codes can therefore validate as one person and credit whichever duplicate row MySQL returns first.
- Display-name availability is also check-then-save without a unique constraint. Concurrent changes can create duplicates even when both validation requests initially pass.

This is a confirmed production data-integrity and identity-routing problem. It compounds the no-proof registration/login vulnerability, but remains independently important for any future authentication repair and data migration.

## AI onboarding

- The active AI endpoints are JWT-protected and rate-limited to 10 requests/minute. `ai_onboarding_start` deliberately ignores the submitted `user_id` and uses the JWT subject; reply and completion verify that the session belongs to that subject. This part does not repeat the profile IDOR pattern.
- Start creates/reuses one unfinished session from the last ten minutes, stores the concern and conversation, and calls OpenRouter with recent chat context. Android provides language-specific local fallback text if start fails.
- After one user reply, completion selects up to three online verified creators in connected languages, writes an icebreaker into `chat_messages`, `chats`, `active_chats`, and an anti-spam log, then marks the session and user complete. This is an actual outbound message action when the endpoint is called; the audit did not call it.
- Completion is not protected by a database transaction, row lock, or atomic “claim” update. Two requests can both observe `messages_sent=false` and send duplicate messages/increment unread counts before either marks completion. Per-creator exceptions are swallowed, so partial delivery is committed and the session is still marked complete.
- If Android loses or receives an error from the completion request, its input bar and both action buttons are already hidden. The generic error observer removes the overlay but exposes no retry/skip control, stranding the user on that screen. A start failure can skip to Main, but a completion failure cannot.
- `get_user_concern` is documented as allowing a creator to read a new user's concern, but it ignores the requested peer and reads the authenticated creator's own onboarding session. It is currently unused by this Android tree, so it returns generic/self data rather than serving the documented feature.
- Live `language_configs` on 2026-07-14 has no `ai_onboarding` language: Hindi and Punjabi are `autopay`; the other nine rows are `none`. These defects are dormant in the normal new-user route today but activate immediately if an admin changes a language to `ai_onboarding`.
- The feature flag is enforced only by the current Android navigation. The three AI endpoints themselves do not check `language_configs`, gender, or a server-side rollout flag, so an older client or any authenticated caller can still start the flow. Production proves residual execution after the current flag was removed: sessions continued through 2026-07-13 at roughly two to nine per day. The live ledger contains 22,569 sessions, 15,086 marked sent, 8,015 garbage-classified inputs, 58,446 creator-log rows, and 15,058 users marked complete.
- Start sends OpenRouter the user's name, concern, language, and as many as ten recent `ch_messages` snippets involving that user, including the other participant's text. Reply sends the user's emotional narrative, the generated opener, and recent chat snippets. This is a real third-party disclosure boundary; the code does not obtain separate consent from the other chat participant.
- There is no message-length validation on `user_message`. The OpenRouter reply call classifies garbage and stores a generated response, but current Android deliberately never displays that AI response; it waits about 900 ms and invokes completion. The second provider call therefore spends provider capacity and processes sensitive text even though its visible reply is discarded.
- Completion may generate one AI icebreaker from the user's story and insert the same text for three creators. It writes directly to `chat_messages`, `chats`, `active_chats`, and `ai_onboarding_creator_log`; it does not traverse the normal chat permission/autopay/friend/content-filter, Socket.IO, or push-notification path. Recipients see DB/unread state without the normal real-time/push behavior.
- Android's delivery animation does not use the message returned or persisted by the backend. It builds a separate concern/language template locally, so the text shown as “sent” can differ from what all three creators actually received.
- The recent-session lookup has no ordering, unique constraint, transaction, or claim lock. Completion catches per-creator failures, then still marks the session sent and the user complete; a partial result is terminal and retry does not repair missing recipients. Every audited onboarding/helper method in live `AuthController` and `OpenRouterService` matched Git byte-for-byte on all three nodes even though those whole files contain unrelated live drift.

## Female voice submission and verification

- Android fetches a random sentence for the selected language, records an MP3 in app cache, enforces only a three-second minimum, previews it, and submits it with the cached user ID. Record-permission denial immediately requests permission again and has no permanent-denial/settings route, so “don't ask again” can trap the user in a denial loop.
- The backend validates only the filename extension as MP3; it does not use MIME/content validation or an upload-size/duration limit in this handler. It stores the upload, sets the target account pending, and—when enabled—synchronously calls an external AI scorer with a 45-second timeout. This is vulnerable to slow requests/resource pressure and, as documented above, the target ID is not bound to the JWT subject.
- AI routing is fail-safe on parse/file errors and requires sufficient transcript/audio quality before automatic action. A high-confidence female can be approved; a low female-confidence applicant is converted into an active male account rather than rejected; ambiguous cases remain pending and create an MCP review item. The service can also send a OneSignal “verified” push as part of approval.
- Latest reconciled non-secret configuration at 2026-07-15 15:40 IST: `voice_verify_mode=enforce`, auto-approve is on, auto-reject is off, kill switches are off, approve/reject thresholds are 92/85, the prompt gate is off, and ambiguous-reject diversion is on. New uploads handled by app-1/app-2 are synchronously scored and can auto-approve reliable female results; auto-reject/review/error cases remain pending for manual review. app-3 cannot resolve the service and leaves every upload pending after logging the caught failure. The batch command exists but is explicitly not scheduled.
- Production behavior is node-dependent if the mode is enabled: app-3 does not contain `VoiceVerificationService`, so its caught resolution failure leaves uploads pending, while app-1/app-2 can score them. App-3-only uploads also do not replicate back through the current lsyncd topology.
- The returned voice URL is built as `/storage/app/public/voices/...`, unlike the correct public symlink URL `/storage/voices/...` used elsewhere. Consumers using the immediate upload response can receive a broken media URL.
- Android does not model/display the returned verification detail. It routes both female status 1 and 2 to the same video/explainer screen; converted-male status routes to Main. A success with any unhandled gender/status combination leaves the user on the submission screen without feedback.

## Profile cache integrity and validation races

- `GetNameActivity` and `EditProfileActivity` fire a server availability request on every qualifying text change but do not cancel prior calls or correlate a response with the exact submitted string. An older response can arrive after the user has typed a different name and incorrectly mark the current text valid/invalid. Final backend validation prevents many bad saves, but the UI can enable the button on stale evidence.
- Get Name's local minimum is two characters while server validation requires 4–10 alphanumeric characters and rejects three consecutive digits. The mismatch generates avoidable requests and inconsistent hints.
- `update_profile` and `update_voice` return only partial `UserData` shapes. Android then replaces its full cached user object (or preserves only audio/video/DND fields). Missing fields become null/default, including bank/referral/payment data and security/business flags such as `blocked` and `withdrawal_blocked`. After an edit/upload, the blocked banner or agency-withdrawal UI can therefore be temporarily wrong until a full `userdetails` refresh; backend enforcement still must be checked feature by feature.
- `EditProfileActivity` mutates the returned avatar list by moving the current avatar to index zero. If the current avatar is absent, it can insert a null element into the UI list; selection/index calculations also alternate between the mutated LiveData list and adapter position, creating a wrong-avatar/crash edge case.

## Settings and language feature configuration

- `settings_list` and `appsettings_list` are intentionally public through the controller exception list. The former supplies policy/support URLs, withdrawal minimum, gateway display choice, blocked words, call-income display rates, missed-call/icebreaker flags, and call-bonus display configuration. Android persists the first row in ordinary SharedPreferences and many screens fetch it independently with silent failure paths.
- Two JSON keys intentionally contain trailing whitespace (`auto_disable_info ` and a tab-suffixed blocked-words key), with matching `@SerializedName` annotations in Android. This works only while both sides preserve the accidental whitespace and is brittle for serializers/admin rewrites.
- Night income values are calculated as decimals (for example 1.20/1.30/1.40) and then cast to `int` in the response, truncating the intended premium display to 1; video rates similarly truncate. This block is display-only, not settlement truth.
- `appsettings_list` advertises a socket URL and rewrites localhost/`:3003` values to the demo domain, but the current Android tree does not consume this endpoint for socket configuration. Mandatory update behavior instead uses authenticated `individual_app_update`.
- `individual_app_update` trusts a body `user_id`, updates that row's `current_version`, and returns its update policy without binding it to the token owner. This is another authenticated IDOR, although the victim normally overwrites the version again on next launch.
- `LanguageFeatureCache` is a process-wide + SharedPreferences cache, refreshed on app start/Home and cleared by the shared logout teardown. Unknown state fails closed for most autopay surfaces, but a static seven-language fallback enables autopay before the first fetch; that fallback currently over-includes five live `none` languages (Bengali, Assamese, Gujarati, Odia, Marathi). On a cold cache/network failure, those users can see autopay routing/UI that the backend will reject. Live truth currently enables autopay only for Hindi and Punjabi.

## Install, campaign, and app-event attribution

- Splash invokes Google Play Install Referrer on every logged-out launch, not once per installation. The response (including raw referrer/UTM fields) is logged, stored in main SharedPreferences, and posted to public `install-referrer`; the saved payload is later associated with a newly logged-in male account through `user-install-referrer`.
- `install-referrer` inserts a raw response row on every request with no device/install idempotency. Its tracking-link increment only deduplicates by tracking-link + observed IP + current date, so repeated logged-out launches still accumulate raw rows and NAT/shared-IP installs can collapse together. The endpoint trusts caller-supplied JSON/UTM data and an optional user ID.
- `user-install-referrer` is public because it is in the controller auth exception list. It trusts any submitted existing user ID and writes the one-per-user row (the table does have a unique `user_id`). An unauthenticated caller can therefore preempt another user's legitimate attribution; subsequent Android retries receive HTTP 409 and retain/retry the saved payload on each Main launch.
- `first_install` is also public and runs on every Splash where no cached user exists. It takes the first value of `X-Forwarded-For` directly, finds the newest unconverted click for that IP in 24 hours, marks it converted, increments the tracking link, and inserts an install. It has no durable device/install key, transaction/row lock, or first-launch client marker. Repeated launches, spoofed/forwarded headers, shared carrier/NAT IPs, and concurrent requests can misattribute or overcount installs.
- Android saves the returned IP even when `first_install` reports no matching click. `SelectGenderActivity` then calls `tracking_info` with `user_id=0`, which can never pass server validation; after registration Main calls it again with the real ID.
- `tracking_info` is public and trusts both `saved_address` and `user_id`. It matches the newest unclaimed converted click for that address over seven days and increments registration counts. Its check/select/update/increment sequence has no transaction or lock, so concurrent calls can double-increment; a caller can also attribute any known existing user to a known/shared address.
- `log-app-event` is public, accepts arbitrary event name/user/platform/parameters, and writes analytics records. Special cases such as `voice_verified` and two-minute completion use application-level “already exists” checks but no authenticated subject or trusted server evidence. Anyone can inject/preempt these events, and concurrent requests can bypass the check unless the live schema has an unobserved matching unique constraint. Backend funnel analytics must therefore not be treated as security-grade ground truth.
- Android's attribution/event posts are fire-and-forget and generally not durably retried (apart from the saved install-referrer payload). Referrer parsing also splits on every `=`, so encoded values containing an equals sign can be silently omitted.

## Logout, push-token ownership, and account deletion

- User-initiated logout performs broad local teardown: socket disconnect, OneSignal tag/external-ID logout, active-chat and notification caches, dynamic shortcuts, call-status reporting, subscription/language caches, and the main Hima SharedPreferences (including the bearer token) are cleared before routing to login.
- It captures the old 90-day JWT and schedules a network-constrained WorkManager job to delete the exact FCM token from that account. The server invalidation handler correctly derives the account from the JWT, matches the exact token, and disables female call availability only when that token was actually deleted; this avoids a stale logout removing a newly registered token.
- Both Android FCM workers persist a captured JWT in WorkManager input until their work finishes: logout invalidation stores the old credential, and token registration stores the active credential alongside the FCM token. These are additional at-rest copies beyond the already unencrypted main preferences.
- Logout never calls a working JWT logout/revocation endpoint. `/api/auth/logout` and `/api/auth/refresh` are registered but no corresponding controller methods exist. JWT blacklist support is configured, but unused here, so a copied token remains usable until its 90-day expiry even after the user logs out locally. FCM invalidation is not session invalidation, and the Android app persists the bearer token in shared preferences rather than any server-side revocation list.
- `send_fcm_token` has the opposite ownership flaw: it is authenticated but trusts a submitted `user_id`. Any valid JWT can bind a device token to another account and delete that same device token from other accounts. This can redirect/suppress call and message pushes and alter which shared-device user is reachable. Android reaches this endpoint from login/Main through Retrofit, from cold start through both a direct OkHttp thread and `FcmTokenRegisterWorker`, and from `MyFirebaseMessagingService.onNewToken` through the same worker. Per-user `REPLACE` coalesces pending worker instances but does not deduplicate the direct/Retrofit attempts.
- Logout does not cancel pending `fcm_token_register_<oldUserId>` work, and the registration worker does not verify that its captured user is still signed in. Because transient failures return `Result.retry()` with no app-defined attempt cap, delayed old-user work can outlive logout and reverse the exact-token invalidation. If another account has since claimed the same device token, the stale registration can also delete that new account's mapping before rebinding it to the old user. `invalidate_fcm_token` itself remains correctly JWT-bound and exact-token-only; the defect is the uncancelled opposite-direction worker and the ownership-weak registration endpoint.
- Account deletion is not implemented in the API: `delete_users` immediately returns an instruction to contact support and all deletion code is unreachable/commented. The current Android Delete Account button opens the ticket flow; it does not call the deletion endpoint, delete local state, or provide an automated deletion/status workflow. The older success observer remains dead code.
- A small local-cleanup defect exists in `BottomSheetLogout`: after the shared teardown correctly captures/clears the old user's watermarks, a redundant second cleanup calculates the user ID *after* preferences were cleared and therefore uses `0`. The first shared cleanup already handles the real ID, so this is currently harmless duplication rather than the primary privacy boundary.

### Legacy auth/profile route residue

- Deployed API routes for `customerdetails` and `creators_admin_list` point to methods absent on all three nodes, joining the already-recorded missing JWT `logout`/`refresh` actions. No current Android/backend caller or recent API request was found. Android logout performs local token/state and OneSignal cleanup, login targets `/dashboard`, and there is no current refresh-token flow; classify these routes as legacy/dead residue rather than active authentication behavior.
- The deployed `/check` web route is also missing its method, but it is not the current login target: `RouteServiceProvider::HOME` is `/dashboard`, and the only redirect to `/check` is commented historical code. `home/getlanguvage` is missing and role-allow-listed but has no executable UI/JavaScript caller.

## Phase conclusion

Authentication cannot presently establish mobile ownership because the public fallback login and registration endpoints issue tokens without server-side OTP proof. Even after that is repaired, production duplicate identifiers, missing token revocation, and multiple subject/body-ID mismatches would still prevent reliable account isolation. The safe backend pattern already exists in newer endpoints—derive identity from `auth('api')->id()` and lock/idempotently mutate server-owned rows—but it is applied inconsistently.

The highest-probability causes for auth/onboarding/profile/config bugs are now mapped: ambiguous duplicate-mobile selection; cached-user poisoning from an IDOR/partial response; stale async name validation; no-network startup routing; language-cache/live-flag disagreement; app-3 media/service drift; synchronous voice AI; AI-completion partial failure; and a locally logged-out but still-valid JWT.
