# Checkpoint 04 — Calls, Agora, Billing, and Recovery

Status: complete

Targeted ring-heartbeat review: 2026-07-15 IST

Scope: end-to-end male/female audio and video calls from discovery/matching and preflight through FCM/socket signaling, accept/reject/cancel, Agora token/channel join, connected-state persistence, heartbeats/liveness, per-minute coin and creator-income calculations, duration bonuses/referrals, call-end settlement, ratings/favorites, and orphan/drop/retry recovery.

Production, databases, services, repositories, Git, and external communications remain read-only. No call, notification, socket emission, service action, or database write will be initiated during analysis.

## Current call surface

The Android client implements separate male/female and audio/video call activities, plus random-call matching, FCM receivers, foreground calling services, Android Telecom integration, heartbeat/liveness helpers, and several WorkManager recovery paths. The principal backend call endpoints are concentrated in the live `AuthController`, including:

- `calls_status_update`
- `call_female_user`
- `call_male_user`
- `call_reject_count`
- `random_user`
- `update_connected_call`
- `individual_update_connected_call`
- `every_min_update_connected_call`
- `female_call_attend`
- `get_remaining_time`
- `get_female_talk_duration`
- `notify_call_end`
- `check_call_alive`
- `pending_incoming_call`
- `cron_detect_orphan_calls`
- `checkCallAvailability`
- `call_status`, `call_drop_status`, and `get_call_earnings`

This is not one call state machine. It is a family of overlapping flows whose invariants are split among the client, Laravel, FCM, Socket.IO, scheduled cleanup, and retry workers. The ringing-stage table and diagnostic matrix below normalize the effective transition owners.

## Call creation and matching — confirmed findings

### Critical authorization failures

Several call endpoints require a valid JWT but then authorize the operation using caller-supplied user IDs rather than the JWT subject:

- `calls_status_update` can change another account's online/offline availability and can initiate creator-online notification work.
- `call_female_user` accepts caller and receiver identities from the request and does not bind the caller to the token owner.
- `call_male_user` accepts the female and male identities from the request and does not bind the initiator to the token owner.
- `random_user` performs matching and creates a call for the body `user_id`, not necessarily the authenticated account.
- `call_reject_count` lets any authenticated account manipulate a specified male/female pair's rejection counter. The aggregate fatigue strike has an ownership check, but the pair-level 60-minute suppression does not.

These are authenticated IDORs, not merely missing validation. They can alter call availability, produce call records, affect matching eligibility, suppress legitimate matches, and enter notification/signaling paths for other users. Read-only audit rules mean no live proof request was sent; the conclusion follows directly from route middleware, controller authentication exceptions/constructor behavior, request fields, and absent subject comparisons.

### Asymmetric concurrency protection

The three creation flows protect participants differently:

- `call_male_user` locks both participant rows in a stable order, then rechecks ringing collisions and duplicate calls inside the transaction. This is the strongest of the three paths.
- `call_female_user` locks only the caller. It does not lock the female receiver before its decisive availability/ringing checks. Two different callers can therefore pass the same receiver checks concurrently and create two incoming rings for one creator.
- `random_user` similarly locks the caller but not the selected creator and does not repeat every selection/ringing check under a receiver lock. The same creator can be selected by concurrent callers.

The existing “one active call” guard is deliberately log-only in relevant paths. A caller can therefore create a new call while an older call is still open or billable. This is not isolated to UI behavior: it feeds directly into settlement races described below.

### Matching and notification behavior

- Direct and random call creation increment the creator's `missed_calls` at creation time, before a call has actually been missed. Any failure after record creation can inflate that metric.
- Backend creation code can send a provider notification, while Android also invokes the public `send-fcm-notification` endpoint after a successful create response. The two-stage OneSignal/FCM presentation and channel-upgrade logic can produce duplicate surfaces; local call-ID/sender guards suppress some, but not all, delayed or process-restarted duplicates.
- The random matcher considers user status, active started calls, recent call/connection history, unified blocks, language, night mode, and a weighted score. It also contains hard-coded account-specific override branches that can bypass ordinary selection fairness. Identifiers are intentionally not copied into this checkpoint.
- `call_female_user` returns a `date_time` value built from a likely nonexistent model property; coercing that null through Carbon can make the response appear to start “now” rather than reflect the persisted call timestamp.

## Primary call settlement — confirmed findings

### Critical financial IDOR

`update_connected_call` requires a JWT but accepts `user_id`, `call_id`, `started_time`, and `ended_time` from the client. It verifies that the call's caller equals the submitted `user_id`; it does **not** verify that this user is the JWT subject.

Consequently, any authenticated account that knows an open call ID and its caller ID can attempt to settle that call with client-selected times. Settlement is not a cosmetic update: it can end the call, deduct the caller's coins, credit creator income, write transaction records, update talk duration and metrics, and trigger call/referral bonuses. This is a critical authenticated financial IDOR.

### Client-controlled duration and charging

The endpoint reconstructs duration from client-supplied `H:i:s` values using the server's current date and an overnight adjustment. A clamp can replace the supplied start with the call record's creation timestamp if the submitted start looks more than 60 seconds late, with a six-hour cap. Since call creation precedes connection, that fallback can include ringing time as paid time.

Observed charging rules in this path:

- Calls below 10 seconds are uncharged.
- At 10 seconds and above, minutes are rounded up with `ceil(duration / 60)`.
- Audio costs 10 coins per rounded minute; video costs 60.
- A 10–59 second video call charges the full 60 coins but credits the creator at half the normal first-minute income.
- Time-of-day creator rates vary by hour. The loop's minute overflow correction subtracts 60 only once, so calls longer than 120 minutes can be assigned to the wrong hourly rate slot.

Comments and unused variables still describe a historical “ignore the first 60 seconds” rule, but the executable calculation does not implement it. This makes maintenance especially error-prone because comments, variable names, and billing reality disagree.

### Non-atomic settlement and lost-money states

The endpoint has a short transaction that locks the call row and claims settlement by writing `ended_time`/`update_current_endedtime`. Nearly all consequential work occurs afterward, outside that transaction:

- affordable-spend calculation and caller coin deduction
- transaction ledger creation
- creator balance/income credit
- call bonus evaluation
- final call-field updates
- cache/version bumps
- talk-duration accumulation
- referral rewards
- metrics and availability cleanup

This creates two independent severe failure classes:

1. **Claim-before-money crash:** if the process dies after marking the call ended but before some or all financial work, a retry returns “already updated.” The call is permanently marked settled while balances, ledger rows, bonuses, or metrics can be missing or internally inconsistent.
2. **Parallel-call lost update:** two different call IDs for the same caller can both claim their own call, read the same old coin balance, calculate spend, and save absolute balances. Both creators and ledgers may be credited while the caller's final balance reflects only one deduction. The creation paths' log-only one-active-call rule makes this reachable through ordinary concurrency, not only malicious input.

The referral reward logic also follows check-then-credit-then-insert patterns outside the main call claim. Live schema has no logical reward uniqueness, and aggregate evidence below confirms duplicate awards already exist.

### `individual_update_connected_call`

Android's durable post-call worker normally selects this endpoint for individual calls. It repeats the critical authorization pattern: the JWT only proves that *some* user is logged in, while the submitted `user_id` is used to load the payer and is only compared with the call's stored caller. It also repeats client-controlled start/end times, the short call-row claim, and all balance/ledger/metrics work after that claim.

This path adds several production patches that do not remove the core settlement hazards:

- A non-switch call whose persisted `started_time` is empty is atomically closed with zero billing as a presumed phantom. A watch/enforce configuration can separately record sustained client-reported durations, but the current guard still treats absence of the attend write as proof that billing must be zero.
- Switch calls bypass that phantom guard because their attend path historically leaves `started_time` empty.
- An optional income cap compares rounded creator-income minutes with paid minutes plus a grace. Its default is fail-open/off, and the selected mode is runtime configuration.
- A duplicate-parallel-leg detector is explicitly log-only. It does not stop the second settlement.
- Caller affordability caps the debit, but creator income is intentionally calculated from the entire rounded duration, subject only to the optional cap. A caller who runs out of coins can therefore generate creator income for unpaid minutes.

The source calls the bonus duration “server-authoritative,” but it is the same duration derived from client-submitted timestamps (with the creation-time clamp). The label is therefore inaccurate.

### `every_min_update_connected_call` — live routed legacy financial primitive

This endpoint is still registered under the API route group, although current Android source exposes no Retrofit method or call site for it. It is therefore latent/reachable API surface rather than a current first-party client flow.

It is more dangerous than the ordinary settlement paths:

- It repeats the body-user authorization flaw.
- It has no call-row claim, “already settled” check, transaction, or idempotency key.
- Repeating the same request repeats caller debit and creator credit.
- It subtracts without a zero floor or affordability cap, so caller coins can become negative.
- It creates creator-income transactions but no matching caller-deduction transaction.
- It uses legacy creator rates of ₹2/audio-minute and ₹10/video-minute, inconsistent with the main time-dependent ₹1–₹1.40 and ₹6–₹8.40 rates.
- It accepts client timestamps and derives an increment from the call's existing `ended_time`; malformed/null state can also throw before a controlled response.
- It marks the creator available and increments `attended_calls` on every invocation.

No live request was made. Reachability is established by the current route registration and controller method. Current Android absence lowers accidental invocation likelihood but does not lower the endpoint's direct financial impact if called by an authenticated client.

## Client settlement delivery and retry behavior

`CallEndUpdater` consolidates many activity teardown paths into unique WorkManager work keyed by call ID, plus an in-process set. This reduces duplicate enqueues from `onUserOffline`, channel leave, switch callbacks, force-end FCM, and similar overlapping lifecycle exits.

However, `CallUpdateWorker` treats any HTTP 2xx response as success without parsing the JSON `success` field. The backend returns HTTP 200 for many business failures, including invalid/missing fields and “already updated.” More importantly, an application-level settlement failure returned as HTTP 200 is permanently considered successful by WorkManager. Exceptions and non-2xx responses return `Result.failure()`, not `Result.retry()`, so transient timeouts/server errors are also not retried by this worker. The server's claim-before-money structure means neither client dedupe nor retries can repair a partial settlement once the call row was claimed.

### Ring heartbeat helper is currently unmatched

Current Android source includes a `CallAliveChecker.sendRingHeartbeat()` client helper that posts to `call_ring_heartbeat` while the caller is still on the connecting screen. This is executable and wired in current source rather than a dormant helper: both male and female connecting activities invoke it on their 2.5-second liveness-poll tick, and each invocation creates a new thread and `OkHttpClient` for a fire-and-forget request with no authorization header. Installation of this exact source in the current production user build was not independently verified, so production-client adoption remains unproven.

Strong-model review on 2026-07-15 rechecked the Android call sites and searched the live routes, controllers, and Laravel route-cache files on app-1, app-2, and app-3 directly. None of the three production nodes has a matching `call_ring_heartbeat` route, controller method, or cached route. When a build containing this Android path targets current production, the requests cannot update server ring state; they only add repeated client thread/network work and failed HTTP traffic while an outgoing call is connecting. This does not change settlement behavior today, but it is a current-source/live-backend contract and efficiency defect to account for in ring-cancel diagnosis.

## Attend, remaining-time, and terminal-state writes

### `female_call_attend` is an authenticated state IDOR

Despite its name, this endpoint does not require the authenticated account to be the creator who accepted the call—or even either participant. It loads the payer from body `user_id`, verifies that the call stores that payer, and then accepts a client-selected `started_time`.

Any valid token with a known call ID/payer ID can therefore:

- mark a never-answered call as connected;
- clear a pre-existing `ended_time`, `end_reason`, and `ended_by_user_id`;
- make the receiver busy and turn off the relevant availability toggle;
- establish the timestamp that later phantom-call and settlement logic treats as evidence of attendance.

The check-then-save is not enclosed in a participant-locked transaction. The receiver-busy check and update are also not atomic. This endpoint can race a decline/timeout or a second call. Its returned `date_time` is again built from a likely nonexistent model property rather than the persisted `datetime` column.

### `get_remaining_time` leaks state but also powers liveness

Both participants poll this endpoint roughly every 30 seconds. The submitted `user_id` is the payer, allowing the creator to retrieve the payer's current coin-based time budget, while the JWT subject is written into Redis as that side's heartbeat. This dual-purpose design means a simple subject-equality check would break legitimate creator polling; the correct invariant is “JWT subject must be either participant in the selected call.” That participant check is absent.

An unrelated authenticated user can currently retrieve another user's coin balance plus active-call elapsed/remaining time. Their Redis member is ignored by the orphan detector because it looks specifically for the call's two participant IDs, but the request still adds junk liveness members and leaks financial/presence information.

Only a time-of-day is stored for `started_time`. `get_remaining_time` parses it against today without an overnight correction. Shortly after midnight, a call that began before midnight can appear almost 24 hours old and immediately return zero remaining time. Parallel calls also each calculate against the same undebited current balance because coins are not deducted continuously.

### `call_status` is participant-bound but not actually race-idempotent

This newer endpoint correctly requires body `user_id` to match the JWT and requires that user to be a call participant. It records `ended`, `rejected`, `not_answered`, or `failed`, and conditionally stamps an unstarted call's `ended_time`.

However, “already recorded” is checked on a previously read model, then `end_reason` is updated without a `WHERE end_reason IS NULL` guard or row lock. Concurrent participant reports can both pass the check and overwrite each other. A real attend can also land between the reason write and the conditional end-time write: the end-time update then safely fails, but the newly connected call can retain a terminal `rejected`/`not_answered` reason because `female_call_attend` only clears the reason when it observed a prior `ended_time`. `ended_by_user_id` is accepted without verifying that the submitted ID is a participant.

`call_drop_status`, by contrast, binds the token to the reporting user and verifies both supplied participants match the call before upserting the per-user result.

## Signaling and Agora authorization

### Public FCM relay is also a public call-state mutation API

`send-fcm-notification` is explicitly excluded from `AuthController` authentication. It accepts arbitrary sender, receiver, call type, channel, and message fields, looks up the receiver's token, and sends a data push. Its success response also returns the receiver's raw FCM token.

This is more than notification spam:

- `accepted` / `rejected` messages drive caller activities into or out of Agora sessions.
- `callDeclined` / `rejected` can locate the latest unstarted call for the submitted pair and stamp it ended. Despite comments claiming a channel match, the lookup does not filter by the supplied channel.
- A non-terminal message can backfill an attacker-selected `channel_name` onto the latest fresh pair row whose channel is null, then deliver that channel to the recipient.
- The endpoint returns top-level `success: true` even when the Firebase wrapper explicitly reports delivery failure.

This creates an unauthenticated chain for call cancellation, false accept/reject signaling, channel corruption, FCM-token disclosure, and push delivery to arbitrary users. Because Android trusts these messages as signaling, an attacker can make an outgoing female call enter its locally known room by spoofing `accepted` with the expected male sender ID; the male path additionally checks channel equality, but channels are either predictable or exposed through the same weak signaling surface.

### Public arbitrary Agora publisher-token minting

The Agora token controller has no authentication or participant/call authorization. A caller chooses channel name, UID, role, and expiration, and receives a signed RTC token plus the Agora app ID. The role defaults to publisher; any role text other than exact `subscriber` also becomes publisher. Expiration is not bounded.

Channel secrecy cannot compensate:

- female-initiated calls use `channel_<sequential call_id>`;
- male-initiated calls use `<caller_id>_<millisecond timestamp>`;
- random calls add only a four-digit suffix;
- the public FCM relay carries and can overwrite channel values.

The Android call activities join as broadcaster with UID 0 and commonly request publisher tokens for UID 0. They also log the first portion of the returned token and log the full token when joining, so release logs can disclose an active credential. The combined public token minting plus predictable/corruptible channels permits unauthorized join/publish/eavesdrop attempts against active calls; no live Agora request was made during this audit.

### Recovery endpoints and disabled client paths

- `pending_incoming_call` is correctly bound to the JWT subject and returns only that recipient's fresh, unanswered ring.
- `check_call_alive` is public and returns more than the documented boolean: `ended_time` and `end_reason` are exposed for enumerable call IDs.
- Android's post-reconnect in-call `checkAndEndIfDead` immediately returns and is disabled. Active calls therefore rely on Agora `onUserOffline`, per-activity watchdogs, and server orphan handling.
- Android's direct peer-hangup FCM sender is also disabled, even though the receiving logic remains active for ringing and matching active call IDs.
- `CallAliveChecker.isRingingNow` expects a newer `ringable` field, but neither the repository nor any live node emits it. It falls back to broad `alive`, so started-but-never-ended orphan rows can be treated as ringable.
- Current Android source also contains a wired caller-side ring-heartbeat sender, but the audited backend route table does not expose a matching `call_ring_heartbeat` endpoint, so a build containing that helper has no live server receiver. Production-build adoption was not independently verified.

### Public orphan-cron trigger

`cron_detect_orphan_calls` is auth-exempt and uses a shared 55-second cache key as its only invocation throttle. An external caller can repeatedly win that lock just before the scheduler and starve the intended run. If runtime mode is `enforce`, invoking the public URL can cause the server to settle qualifying calls and send `callEnded` FCM pushes to both parties. The requester does not choose a specific call, but can trigger outward and financial actions that should be scheduler/internal-only.

The detector scans only calls represented in the recent Redis sorted set; a call with no heartbeat from either side is invisible. Enforcement requires at least one previously seen side to become older than 90 seconds and skips cross-midnight calls. It delegates to the same non-atomic settlement methods, checks only their HTTP status in logs, and inherits their claim-before-money failure modes.

## Live production verification — 2026-07-14 IST

Read-only SSH and information-schema checks established:

- All three production nodes have byte-identical `AuthController.php`, `ArogaController.php`, and `routes/api.php` for the checked hashes. The call controller has 24,617 lines on each node.
- Live routes include `every_min_update_connected_call`, public `send-fcm-notification`, public `cron_detect_orphan_calls`, and GET/POST `agora/token`.
- Live `ArogaController.php` is byte-identical to the audited repository controller, confirming unrestricted caller-chosen token parameters in production.
- `female_call_attend`, `get_remaining_time`, both main settlement functions, and `every_min_update_connected_call` begin at the same line locations as the audited repository version. The critical logic in those regions is therefore live, despite unrelated controller drift later in the file.
- None of the live nodes' controller responses contains the intended `ringable` field. Android's fallback-to-`alive` behavior is a live client/server mismatch.
- The complete live controller differs from the repository checksum, but app-1/app-2/app-3 share one checksum. Direct app-1 source inspection confirms the production `calls_list`, `missed_call_count`, favourite, talk-duration, ratings, and earnings-recap authorization/settlement logic described below. The live history function contains both 500-row caps, per-row block/reject queries, and the unbounded creator bonus grouping.

Current non-secret runtime flags:

- `orphan_detector_mode = enforce`
- `income_cap_mode = enforce`, grace 10 minutes
- `phantom_heal_mode = log`, threshold 60 seconds
- call-duration bonus master/audio/video flags are all off; configured daily cap is ₹2,000 and minimum app version is 1114.

Thus the unauthenticated orphan-cron trigger is currently capable of entering its settlement/FCM enforcement branch. Duration-bonus cap races are dormant while the master flag remains off, but the implementation remains unsafe if re-enabled.

Live table estimates at inspection time were approximately 41.0M `user_calls`, 73.4M `transactions`, 16.0M call-drop logs, and 28.9M FCM call logs. Index inspection confirms:

- `call_bonus_payouts.call_id` is unique as designed.
- `call_drop_status_logs(call_id,user_id)` is unique.
- `refer_bonus` has only its primary-key index—no uniqueness over user/referral/reward type.
- `transactions` has no unique call/type constraint and no call-ID index in the returned index set.
- `user_calls` has useful lookup indexes but no uniqueness/exclusion constraint preventing simultaneous active calls for one payer or receiver.

The small live `refer_bonus` table contained 23 duplicate logical reward groups / 23 extra rows: 21 duplicate `5_min_talk_time` awards and two duplicate `10_min_talk_time` awards. No user identifiers were retrieved. This is production evidence that the check-then-credit-then-insert design has already failed its intended idempotency, although the read-only aggregate alone cannot attribute every duplicate to a specific race or code version.

After EXPLAIN/index gating, an index-only aggregate snapshot of the preceding 30 minutes found 9,458 call rows: 1,743 still open, comprising 1,559 unstarted and 184 started. Of those, 1,406 unstarted rows were already older than two minutes, while 49 started/open rows were older than five minutes. Across the preceding two hours, 38 started rows remained open beyond ten minutes, 12 beyond 30 minutes, and four beyond 60 minutes. The four >60-minute rows were all `random`; the 10-minute group was split 20 random / 18 individual.

These are point-in-time state counts, not proof that every row is a ghost—some started rows can be legitimate long calls. They do prove that enforced orphan recovery does not guarantee timely closure of every started call, and that never-answered rows routinely remain open far beyond the 30-second ring recovery window. The all-random composition of the >60-minute sample is consistent with, but does not by itself prove, the app/orphan settlement-path divergence described below.

## Canonical first-party call sequence

The current Android happy path is:

1. An outgoing screen calls `call_female_user`, `call_male_user`, or `random_user`. The backend persists one canonical row with the male payer in `user_id` and female creator in `call_user_id`, even when the female initiated the call.
2. Android generates the channel. Male/random channels are generated before the relay; female-originated channels are derived from the returned call ID.
3. Android calls the public FCM relay with an `incoming call ...` message. That call also backfills `user_calls.channel_name` if it was null.
4. The recipient's FCM/Telecom/accept activity presents the ring. Accept/reject is relayed through the same public FCM endpoint. `pending_incoming_call` is a recovery path when the initial ring push is lost.
5. Each side prefetches or fetches a publisher Agora token. The incoming accept activity and outgoing connecting activity navigate into the gender/type-specific in-call activity.
6. Local `onJoinChannelSuccess` only means this device joined. When `onUserJoined` fires, both sides start their timers/liveness state; the female-side activity additionally calls `female_call_attend` using the male payer ID and call ID.
7. Both devices poll `get_remaining_time` with the male payer ID about every 30 seconds. The backend derives remaining time from current payer coins minus elapsed wall time and records each authenticated participant's liveness in Redis.
8. On local hangup, peer-offline, watchdog timeout, or selected FCM paths, either device can enqueue settlement. All four current call activities pass `isIndividual=true`, so their worker calls `individual_update_connected_call` even for a row whose `call_connection_type` is `random`.
9. The first server request to claim the call row determines the financial timestamps. Subsequent device requests get HTTP 200/`success:false` and their workers still mark the work successful.
10. Post-call Android may show rating, block-word, earnings/bonus, missed-call, or return-to-chat/main flows depending on role and local state.

There is no server observation of the Agora participant roster or media duration. “Connected” is inferred from the female client's attend call, and “duration” is supplied by whichever client wins settlement.

## Android state-machine failure modes

### Either device can become the financial clock

Both participants enqueue the same settlement endpoint with independently captured start/end times. Server call-ID claiming prevents duplicate settlement of one row, but it does not establish an authoritative duration: the fastest device/network wins. Normal differences in `onUserJoined`, local hangup timing, clock/timezone, and WorkManager scheduling can change rounded minutes and earnings. A modified client can choose the times directly.

Every in-call activity overwrites `startTime` when `onUserJoined` fires again after a reconnect; the male audio/video and female audio paths also reset local monotonic duration anchors. Short reconnects can therefore shorten the submitted duration. The server's >60-second clamp often replaces that with call creation time, which can overcorrect by adding ring time. The result is a discontinuous rule: a smaller reset can underbill, while a larger reset can jump back to pre-answer creation and overbill.

### Attend failures are ignored

Female-side activities send `female_call_attend` only after a remote Agora UID joins, but their callbacks do not inspect `success`, do not retry, and do not leave on failure. Since the backend encodes most failures as HTTP 200, a call can continue after insufficient-coins, receiver-busy, already-started, or other business rejection.

For a non-switch row, later `individual_update_connected_call` sees empty persisted `started_time` and zero-bills it as a phantom—even if both users actually talked. For a switch row, the phantom guard is bypassed, so the same missing attend evidence can still be billed. Caller-supplied `call_switch=1` in creation paths therefore weakens the only server check distinguishing a real attendance from a phantom.

### Critical synthetic-switch payout path

`call_female_user` accepts `call_switch=1` from the client but does not require a parent call ID, an actually connected parent row, ownership by the JWT subject, or proof that the two users are currently together. The flag bypasses receiver availability, DND, active-call, ringing-call, and busy checks. It still requires the selected male payer to have the minimum 10/60 coins and applies the unified block check, but any authenticated account can choose the payer and creator because of the endpoint's existing body-ID authorization flaw.

The resulting row is especially dangerous during `individual_update_connected_call`:

- switch rows bypass the empty-`started_time` phantom guard;
- switch rows are excluded from the enforced creator-income cap;
- creator income is calculated from all client-reported rounded minutes, while payer debit remains limited to affordable minutes;
- settlement timestamps can span much of the current day because the creation-time clamp only moves a submitted start earlier when the submitted start is *later* than creation; it does not cap a caller-supplied start that is already much earlier.

This composes into a direct authenticated payout-minting primitive: create a fake switch row for an arbitrary funded male and chosen female, then settle a long client-defined duration without any Agora join or attend evidence. The payer loses at most the affordable balance; the creator can be credited for the much larger full duration. This path is critical even without the broader public Agora/FCM chain. No exploit request was sent.

### App settlement and orphan settlement disagree for random calls

All Android calls use `individual_update_connected_call`. The orphan detector instead chooses `individual_update_connected_call` only when `call_connection_type === 'individual'`; `random` rows are delegated to `update_connected_call`.

The two endpoints do not implement the same money rules:

- the individual path can pay creator income for all rounded minutes even after the payer's affordable minutes end, limited by the enforced paid-minutes-plus-10 grace cap;
- the primary path pays creator income only for affordable minutes and has no phantom-call guard/income-cap branch.

Therefore a random call's creator payout and phantom handling depend on whether an app worker or the enforced orphan cron claims the row first. This is a production nondeterminism, not merely dead duplicate code.

### Zero-time enforcement is client-owned

The repository backend emits no `callEndedNoCoins` push and no server absolute `ends_at_ms`/`server_now_ms`, despite Android comments and response fields for those mechanisms. The app falls back to duration strings and treats one zero response as transient; it ends only if zero persists for roughly 25 seconds across resync. A positive timer reaching zero ends immediately.

If both clients are suspended, killed, or unable to poll, coin exhaustion does not itself close the call server-side. Enforced orphan detection may eventually settle a stale-heartbeat call, but calls with no heartbeat from either side never enter its scan.

## Ringing-stage state normalization

HIMA does not have one authoritative accept/cancel/reject transition. It combines a public FCM relay that also edits the database, an authenticated `call_status` write, selected WorkManager retries, local in-memory observables, Telecom callbacks, and polling. The same user action follows different durability and validation rules depending on which Android surface handled it.

| Ringing event / UI path | Signaling | Backend terminal write | Durable retry | Confirmed divergence |
|---|---|---|---|---|
| Caller presses Back/Cancel | public `callDeclined` relay | inline `call_status(not_answered, caller)` | No | Offline/process loss can leave row open |
| Caller reaches 40-second timeout | public `callDeclined` relay | inline `call_status(not_answered, receiver)` | No | Records receiver as ending user despite no receiver action |
| Female full-screen Decline | public `rejected` relay | inline `call_status(rejected)` | Yes | Strongest decline path |
| Male full-screen Decline | public `rejected` relay | inline `call_status(rejected)` | No | Same UI action is less durable than female path |
| Notification action Decline, either gender | public `rejected` relay | inline `call_status(rejected)` | Yes | Male path also increments reject count |
| System Telecom Reject/Disconnect | public `rejected` relay only | no call-ID-bound `call_status` | No | Relies entirely on unauthenticated pair lookup |
| App/task force-close while female ring is live | public `rejected` relay | inline `call_status(rejected)` | Yes | Accept-activity and foreground-service paths dedupe with persisted marker |
| Concurrent incoming while already ringing | public `userBusy` relay | inline `call_status(not_answered)` | No | Failure is deferred to cleanup that may never see a no-heartbeat row |

`CallStatusWorker` improves selected reject paths, but it treats every HTTP 2xx as delivered without reading JSON `success`. The backend deliberately reports several forbidden/not-found/already-recorded outcomes as HTTP 200, so a business failure can still be permanently dropped.

### Public relay mutation is not channel-bound

For `callDeclined` and `rejected`, `sendNotification` accepts a channel but selects the newest unstarted/unended row for either orientation of the sender/receiver pair within five minutes. It does not include `channel_name` in that query. A terminal signal for one call can therefore stamp a newer parallel/synthetic row for the same pair. The `accepted` dead-call guard likewise inspects the newest pair row, not the signaled channel.

The relay checks for a receiver FCM token before performing this mutation. If the receiver has no token, it returns 404 and never stamps the row. The separate authenticated `call_status` may still succeed while online, but cancel/timeout/system paths with missing durability can leave an open row when both delivery mechanisms fail.

### Accept validation differs by surface and gender

- Female full-screen Accept waits for the public relay response and aborts only on its specific `call_already_ended` business error. It fails open after two seconds and launches for every other failure.
- Male full-screen Accept sends the relay and immediately launches; it does not inspect the dead-call response.
- Notification Answer for either gender immediately launches after only a local recent-ID/channel check. The male notification path enforces the local 10-coin gate.
- System Telecom Answer immediately launches after a joinable-channel check, but has neither the male coin gate nor the local recently-ended guard. It sends `accepted` fire-and-forget and ignores the response.
- The backend dead-call guard itself deliberately allows an eight-second grace after a death stamp. Thus even the female gate can accept a recently ended row during that window.

These differences explain surface-specific reports such as an incoming call working from the full-screen button but opening a dead/empty channel from the system or notification Answer action. They also allow a male with a stale local balance below the UI minimum to enter through Telecom even though the normal male accept surfaces reject him.

### Polling reduces some ghosts but live contract drift reintroduces them

Caller connecting screens and incoming accept screens poll `check_call_alive` every 2.5 seconds and only react to a terminal response carrying `end_reason`; this avoids treating the endpoint's age heuristic as an explicit decline. The active-call reconnect check in `CallAliveChecker.checkAndEndIfDead` is disabled, leaving Agora/watchdogs as the in-call authority.

For delayed incoming pushes, Android asks for a live `ringable` field. Production does not return that field, so Android falls back to `alive`. A started-but-never-ended orphan can remain `alive=true` indefinitely and a delayed/redelivered push can ring it again—the exact condition the `ringable` code comment says it was designed to prevent.

### Incoming state matching is inconsistent

The male caller consumes `accepted/rejected` only when the returned channel matches its current channel. The female caller instead matches the sender ID. A stale or concurrent status from the same peer can therefore be consumed by the female connecting screen even when it belongs to another channel. Local recent-ended/accepted maps are process-local with short TTLs; they reduce duplicate surfaces but are not an authoritative cross-device state machine.

## Post-call history, unread counts, favourites, ratings, and recap

### Call history is a cross-account privacy IDOR

`calls_list` requires a valid JWT, but it never requires the body `user_id` to equal the authenticated subject. The caller also supplies the `gender` branch independently of the stored account gender. Any authenticated account can therefore request another account's recent, missed, talk-time, A-Z, or favourite view and receive counterpart IDs, names, avatars, profile text, availability/block state, timestamps, durations, creator income, and duration bonus amounts. The same authorization flaw applies to `missed_call_count`, all three favourite endpoints, and `get_female_talk_duration`.

The Android app normally sends its locally stored user ID, so ordinary UI use does not expose the flaw. It is nevertheless a backend authorization failure and must not be treated as client-protected.

### History pagination and sorting are capped to the newest 500 rows

For non-favourite history the backend runs a full `count()`, then loads at most the newest 500 matching calls. Search, A-Z sorting, talk-time filtering/sorting, and offset pagination happen in PHP after this cap. Consequences:

- page requests beyond the first 500 can report a larger `total` but can never return the older rows;
- search cannot find a matching person outside the newest 500 calls;
- A-Z is alphabetical only within that newest-500 window, not the user's full history;
- talk-time totals and day-filtered results can diverge from the count after post-load duration filtering;
- favourite mode first loads every favourite ID and every matching user, then paginates in PHP, so its cost grows with the entire favourite set.

The Android adapter preserves every non-favourite response row because the payload `id` is the peer ID rather than the call ID. That is correct for repeat calls, but concurrent or stale page-zero responses can be appended twice. Search/filter requests share a single `LiveData` with no request generation or cancellation check, so a slower response for an older query can arrive after a newer reset and populate the wrong screen state.

### History caching can preserve stale business data and still performs N+1 work

All non-missed list variants are cached for five minutes as model collections. A per-user cache version is bumped by known call-end/favourite paths, but mutations that do not bump that counter can leave history, profile text, or status-bearing models stale until expiry. Fresh status columns are reloaded, but most peer profile fields remain from the cached model.

The implementation's comments describe an N+1 fix, yet each returned row can still query `blocked_users`, and a female-viewer status calculation can query `call_reject_count` per peer despite a separate prefetched reject-block map. Creator responses also query and group all `call_duration_bonus` transaction rows for that creator without limiting to the at-most-500 call IDs being rendered. At production transaction scale this makes another account's ID sufficient to trigger expensive history work.

### Duration and labels inherit untrusted settlement timestamps

History duration is computed from `started_time` to `ended_time`, the client-supplied fields used by settlement. A midnight wrap is added when the end time is earlier. This avoids counting ring time in the display, but it does not make duration server-authoritative and it can still reflect clock skew, reconnect timestamp resets, or manipulated times. The displayed `started_time` label is actually based on the row's `datetime` (call creation) when present, so the UI label can show dial time while its duration represents answer-to-hangup time.

### Missed badge counts stale open rings, not the same set as the Missed tab

`missed_call_count` is another body-user IDOR. With `seen=1`, any authenticated account can advance another user's `missed_calls_seen_upto` and clear that user's badge. With `seen=0`, it counts only calls older than 50 seconds whose `started_time` and `ended_time` are both null.

The Missed history tab is broader: it also includes rows stamped `end_reason='not_answered'` even when `ended_time` is populated. Therefore a genuine caller-cancel/timeout can appear in the Missed tab but never contribute to the unread badge, while an abandoned open row can contribute until another path closes it. This is separate from the older `users.missed_calls` value incremented during call creation, so HIMA has multiple non-equivalent definitions of a missed call.

`missed_call_count` never reads `users.missed_calls`; it counts open rows newer than the user's `missed_calls_seen_upto` marker and older than 50 seconds. The auto-disable cron in Checkpoint 07 keys off `users.missed_calls` instead, so the badge and disable trigger can drift for the same creator. Connected-call settlement later resets or decrements `missed_calls`, but that is a separate repair path from the UI badge.

### Favourites are cross-account mutable and duplicates exist live

`add_favorite`, `remove_favorite`, and `check_favorite` only check that some JWT is valid; they trust body `user_id`. An authenticated account can read or alter any other account's favourites and invalidate that victim's list cache.

The add path checks for an existing pair before its transaction, then serializes manual `max(id)+1` generation but does not recheck the pair under the lock. The live `(user_id, favorite_id)` index is non-unique. A redacted aggregate query found 614 duplicate pairs and 742 excess rows, with as many as five rows for one pair. This confirms the race/integrity gap has occurred in production. Removal deletes every duplicate for the pair, while check returns only the first.

### Talk-duration aggregation is an unauthorized, potentially unbounded write path

`get_female_talk_duration` accepts any female ID from any authenticated account. If the stored total is zero, it loads all completed calls for that creator into PHP, calculates the total, and saves it. This combines cross-account information disclosure, an authenticated high-cost query, and an unauthorized database mutation. Later settlement increments are not atomic, so concurrent calls can lose increments; once the stored value is nonzero, the endpoint does not reconcile older errors.

### Ratings are not tied to a user or call

`ratings` trusts caller-supplied rater and receiver IDs, does not bind the rater to the JWT, and does not require a `call_id` or proof that the pair completed a call. It also lacks an observed one-rating-per-call constraint and backend bounds for rating/content fields. The live table has only its primary key and ordinary call-user indexes; it contains roughly 1.42 million rows. Any authenticated account can therefore impersonate another rater and submit unlimited records, corrupting creator/user analytics and any administrative decisions based on them. The standard Android screen offers a 1-to-5 UI, but that client restriction is not an authorization or integrity control.

### Earnings recap can declare a partial settlement final

`get_call_earnings` correctly requires the JWT subject to own the creator side of the call. However, it considers any nonempty `ended_time` settled. The settlement endpoint writes that claim field before balances, transaction rows, bonuses, referrals, and metric updates, so a crash can make the recap stop polling and display zero or partial earnings as final.

For switch sessions it locates a nearby root call and includes rows in a two-hour party/time window rather than proving each row belongs to that root. Synthetic or duplicate legs can therefore change the recap. Its `balance_before` is reconstructed as current balance minus session earnings, which is not historical truth if gifts, other calls, withdrawals, or adjustments happened in between. Android polls at most five times about 1.3 seconds apart, trusts the `settled` flag, displays the values, and proceeds to rating; it cannot distinguish a complete settlement from the early-claim partial state.

## Duration bonus service

`CallBonusService` has a sound per-call idempotency concept: `call_bonus_payouts.call_id` is unique, and payout-row creation, creator credit, and its transaction row occur in one inner DB transaction. Duplicate call-ID insertion rolls that transaction back.

Two important limitations remain:

- Duration is not independently server-authoritative; it comes from the settlement endpoint's client timestamps.
- The per-creator daily-cap sum and remaining-cap calculation occur before the transaction without locking a creator/cap row. Two different calls settling concurrently can both observe the same remaining allowance and together exceed the configured daily cap. Per-call uniqueness does not serialize a creator's separate calls.

The service is also outside the parent call settlement transaction because no such encompassing transaction exists. Thus a bonus may commit while a later call-field/referral/metric operation fails, or the process may fail before invoking the bonus after the call has already been claimed.

## Call symptom-to-root-cause diagnostic matrix

This matrix is a diagnostic starting point, not proof for an individual incident. Confirm using the exact live call row, aggregate-safe FCM/call-status evidence, active feature flags, and the serving node. Reconcile IST application timestamps with UTC MySQL functions before ordering events.

| Reported symptom | Highest-probability causes from confirmed design | First read-only confirmation checks |
|---|---|---|
| Recipient never saw a ring | missing/stale FCM token; relay failed before channel/dead-row mutation; delayed push failed the age/liveness gate; recipient already marked busy/ringing; node read saw stale availability | call row creation/channel; redacted FCM log outcome; token presence/update age; recipient busy/status fields; serving node/read host |
| Ring continues after caller cancelled | caller `callDeclined` and inline `call_status` both failed; no durable cancel worker; poll sees no terminal reason; no-heartbeat row is invisible to orphan detector | `ended_time`, `end_reason`, `ended_by_user_id`; FCM relay log; call-status application log; Redis heartbeat membership |
| Caller stays on Connecting after recipient declined | rejected push dropped; male/system decline did not durably stamp; public relay selected another pair row; polling cannot see a reason | target row plus newest same-pair rows/channels; FCM log; `call_status` log; Android surface used for decline |
| Old call rings again after reopen/reconnect | live API lacks `ringable`, Android fell back to `alive`; started orphan remains alive; five-minute local ended marker expired or process state was lost | call row started/ended/reason; push delivery timestamp; live `check_call_alive` response contract; local app version |
| Answer opens black/empty Agora room | dead call accepted inside eight-second grace; male/notification/Telecom path bypassed full-screen gate; wrong/stale channel attached to newest pair row; caller already left; token/join failure | row channel and terminal timestamps; accept/cancel chronology; FCM channel; Agora join callbacks from both devices; answer surface |
| Call ends seconds after Answer | caller 40-second timeout crossed accept; stale `callDeclined`; one client joined but peer did not; attend failed/was ignored; reconnect watchdog fired | accept, timeout, Agora join/user-join, and status ordering; row started/end reason; attend response/log; client clocks |
| Wrong peer/channel appears | female connecting observer matched only sender ID; global status from a stale/concurrent call; channel backfill/terminal mutation chose newest pair row; predictable/reused channel | simultaneous same-pair rows, their channels and creation IDs; FCM payload channel; current screen direction/gender |
| Recipient gets two simultaneous rings/calls | creation paths did not lock receiver; no active-call uniqueness; stale status/busy read on app-3 replica | overlapping open rows for receiver; node and read host; lock path used (`call_female_user`, `random_user`, or male path) |
| Peer hangs up but screen remains active | in-call `callEnded` handling disabled; Agora `onUserOffline` delayed/lost; reconnect server check disabled; watchdog is fallback | Agora connection/offline callbacks; reconnect-watchdog timeline; row ended time; client network transition |
| Call ends early although coins remain | device-local remaining-time timer; stale read/clock disagreement; midnight time-only calculation; reconnect reset of start timestamp; one transient zero became sustained | both devices' timer inputs; payer balance/rate; started time near midnight; serving node; `get_remaining_time` sequence |
| Call continues after coins reach zero | no production server force-end contract; clients suspended/killed/offline; polling delayed; orphan scan lacks qualifying heartbeat | row still open; last Redis beats; client lifecycle/network; payer coins and expected cut-off |
| Wrong duration, coins, or creator income | winning client supplied timestamps; `onUserJoined` reset start after reconnect; ring time entered clamp; per-endpoint rounding/rate differences; random call settled by different methods | raw row times/type/switch flag; settlement endpoint winner; transaction rows; payer/creator before/after evidence; reconnect events |
| Coins deducted but creator not credited, or reverse | call claimed before side effects; crash/exception between independent writes; concurrent calls used same old payer balance; app treats partial HTTP 200 as done | row claim time; payer/creator balances; call-linked ledger/bonus/referral rows; exception logs around settlement |
| Duplicate creator/referral/bonus credit | distinct call rows raced; synthetic switch row; per-minute endpoint replay; referral lacks uniqueness; daily cap check raced across calls | duplicate logical ledger/referral groups; call IDs/types/switch roots; endpoint access logs; unique-index presence |
| Just-finished call missing from Recent | settlement worker failed without retry; 2xx business failure marked successful; deferred UI refresh beat settlement; five-minute cache not invalidated | row ended state; WorkManager result/log; response JSON vs HTTP status; cache version and request timing |
| Recent list duplicated or changes after typing | stale page/search response appended after reset; same page fetched twice; API ID is peer rather than call so adapter cannot dedupe; cache/model staleness | request offsets/query order; response arrival order; repeated peer rows vs distinct call rows; cache key version |
| Search/A-Z cannot find older call | backend caps newest 500 before search/sort/pagination | user's matching-call rank/count and API reported total; verify query plan before any large live query |
| Missed badge disagrees with Missed tab | badge requires both times null while tab includes stamped `not_answered`; another account marked seen; multiple legacy missed counters | row reason/time distribution since `missed_calls_seen_upto`; badge endpoint identity; creation counter semantics |
| Favourite appears twice or returns after removal | live duplicate pairs; pre-transaction existence race; stale list cache/response | aggregate pair count; cache version; request/response order |
| Rating/creator score looks manipulated | rating not JWT-bound, call-bound, bounded, or unique | rating distribution/duplicates by pair and time using indexed aggregate checks; absence of call linkage |
| Earnings popup shows zero/partial as final | `ended_time` early claim is treated as settled; later money step failed; switch window grouped unrelated/synthetic leg; reconstructed balance is not historical | row claim and ledger timestamps; all session leg IDs; bonus payout rows; intervening creator balance events |
| “User busy” persists incorrectly | stale process `isUserAvailable`; manipulated/raced reject-count cooldown; overlapping active row; stale replica read | local lifecycle path; `call_reject_count` pair/timestamp; open calls; serving node/read host |
| Same incident reproduces only sometimes | load-balanced node/read-replica staleness; device surface differences; FCM/Agora timing; process-local guards and WorkManager availability | node hostname, DB read host/replica lag, Android entry surface/version, process state, exact event timestamps |

For financial incidents, the minimum evidence bundle is: canonical call ID and every same-session switch leg; payer/creator IDs kept private; call row before/after fields; all call-linked transactions/bonus/referral records; endpoint/access/application errors; serving node; and client-supplied start/end values. Do not infer correctness from `ended_time` alone.

## Severity register

| Severity | Failure | Direct impact |
|---|---|---|
| Critical | Settlement authorizes body user instead of JWT subject | Unauthorized call ending and financial movement |
| Critical | Separate call claim and monetary side effects | Irrecoverable partial settlements after crash |
| Critical | Non-atomic balance updates across parallel calls | Creator over-credit / caller under-debit |
| Critical | Routed `every_min_update_connected_call` is repeatable and non-idempotent | Arbitrary repeated debit/credit and negative coin balance |
| Critical | Public FCM relay mutates call state and returns receiver token | Unauthenticated cancellation, signaling spoof, channel corruption, token disclosure |
| Critical | Public arbitrary Agora publisher-token generation | Unauthorized channel join/publish/eavesdrop risk |
| Critical | `female_call_attend` trusts payer body ID | Unauthorized transition from ringing/dead to connected/billable |
| Critical | Unowned synthetic switch row bypasses phantom/income caps | Direct creator payout minting against arbitrary funded male |
| High | Creation/status endpoints trust body identities | Calls, presence, matching, and notifications for other accounts |
| High | Receiver not locked in two creation paths | Multiple callers ring/bill against one creator concurrently |
| High | Client supplies settlement timestamps | Duration and charge manipulation; ring time can enter billing |
| High | Settlement worker does not retry transport/app failures correctly | Lost settlements and permanent partial state |
| High | Daily bonus cap is check-then-credit across separate calls | Concurrent payouts can exceed creator daily cap |
| High | Public orphan cron can trigger settlement/pushes or be starved | Unauthorized operational trigger and lingering ghost calls |
| High | `call_status` check/write is not atomic | Connected calls can retain terminal reason; concurrent reasons overwrite |
| High | `calls_list` trusts requested account identity | Cross-account call history, counterpart, income, and bonus disclosure |
| High | Ratings and favourites trust body user identity | Cross-account mutation and analytics poisoning |
| High | Earnings recap treats early settlement claim as completion | Partial/zero money shown as final; polling stops |
| High | Ring terminal durability varies by Android surface | Offline/system-callback paths leave open or wrongly classified calls |
| High | Terminal FCM mutation selects newest pair row, not channel | One call can end a different parallel/synthetic row |
| High | System/notification Accept bypasses full-screen dead-call validation | Surface-specific ghost/dead-channel joins |
| Medium | Missed-count identity and definition mismatch | Another account can clear badge; badge and tab disagree |
| Medium | Live `check_call_alive` lacks Android's expected `ringable` field | Delayed push can resurrect a started orphan ring |
| Medium | History is capped before search/sort/pagination | Older records become unreachable or misreported |
| Medium | History retains per-row queries and unbounded creator-bonus grouping | Authenticated query amplification at production scale |
| Medium | `get_remaining_time` lacks participant authorization | Coin/presence leak and Redis junk entries |
| Medium | Midnight handling uses time-only `started_time` | Active call can jump to zero remaining after midnight |
| Medium | Missed count increments at creation | Inflated creator missed-call metrics |
| Medium | Long-call hourly-rate calculation overflows once | Incorrect creator earnings on long calls |

These severities reflect the repository, direct live-source comparison, live schema/index inspection, and redacted aggregate evidence available on 2026-07-14. No recommendation here authorizes a production change.

## Checkpoint completion boundary

This checkpoint is complete for the current production snapshot. It covers creation/matching, ring transitions, Agora/signaling, attendance, time/heartbeat ownership, all routed settlement variants, bonuses/referrals, switch legs, recovery, call history, unread counts, favourites, ratings, earnings recap, live drift, schema constraints, and a diagnostic symptom matrix. Re-open it if live call code/flags/schema change or a bug report supplies evidence that contradicts these modeled paths.
