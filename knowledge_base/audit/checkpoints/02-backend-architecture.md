# Checkpoint 02 — Backend Architecture

Status: complete

Scope: live Laravel routes/controllers/services/models/middleware, authentication, scheduler/cron ownership, external integrations, Socket.IO server, node roles, and Android-to-live API contract shape. Git is supporting context; live `/var/www/himaapp` is production truth.

No production, database, service, repository, Git, or external state will be modified.

## Runtime and ownership baseline

- Production runs Laravel `10.48.29`. The active application is `/var/www/himaapp`; production files are hand-patched and can drift from Git.
- The public API is unusually concentrated in `App\\Http\\Controllers\\API\\AuthController.php`: the live file is 24,617 lines and owns 167 route declarations. The API route files contain roughly 320 declarations in total; the admin/web surface contains roughly 346.
- The `routes/api.php` authenticated prefix group itself only carries the `api` middleware. Authentication for most `AuthController` actions is imposed by that controller's constructor through `auth:api`, with a very large `except` list. Consequently, endpoint security cannot be inferred from route grouping alone; each action must be checked against the constructor exception list.
- The global API rate limit is configured at 60,000 requests per minute, so it offers little practical abuse resistance. Autopay initiation has a dedicated limit of 3 attempts per 10 minutes per user/IP. MCP uses token-specific dynamic limits.
- `AppServiceProvider` forces HTTPS in production, appropriate for the load-balanced deployment.
- The live queue driver is `sync` on all three nodes, and no HIMA `queue:work`/Horizon process is running. (A Horizon process on app-1 belongs to `/var/www/mmp`, not HIMA.) HIMA's external API calls, notification sends, payout work, and much of its monitoring therefore execute inline in the initiating HTTP request or scheduler process.
- There is no application `app/Jobs` layer in the inspected tree. Background behavior is implemented primarily through Laravel schedules, direct cron scripts, `runInBackground()` schedule wrappers, Android workers, and the separate Socket.IO process.
- The backend mixes Eloquent models, query builder calls, raw SQL, direct HTTP clients, Firebase/OneSignal SDKs, and shell/process execution inside controllers and services. Transaction boundaries are feature-specific rather than enforced by a shared domain layer.
- Core state centers on `users`, `user_calls`, `transactions`, `withdrawals`, orders/payment tables, `fcm_tokens`, chat tables, subscriptions/claims, tickets, reports, and configuration tables. `AuthController` directly owns logic across nearly all of these domains.

## Authentication and middleware shape

- Mobile API authentication uses the `api` guard backed by `tymon/jwt-auth`; the user provider is `App\\Models\\Users`. Tokens are configured for a 90-day TTL, with blacklist support enabled and a default refresh window much shorter than the access-token TTL. Since the registered refresh handler is missing, clients cannot use that advertised refresh route.
- Admin web authentication uses a separate session guard and `Admin` provider. The main admin route group applies `verified` plus mandatory `twofactor`; the live Laravel `EnsureEmailIsVerified` middleware explicitly blocks a missing user, so this nonstandard outer group does still act as an unauthenticated gate. Additional middleware restricts role 3 to named read-only routes and role 4 to moderation routes. Explicit `role_level` gates protect selected superadmin/admin/support actions.
- MCP routes use a separate hashed bearer-token model, per-token roles/scopes/rate limits, expiry/revocation checks, no-store responses, and audit logging. This is architecturally separate from the Android JWT and admin session guards.
- API middleware tracks authenticated user IPs after responses and throttles the DB upsert with Redis. During 21:00–01:59 server time it skips writes to protect peak DB capacity.
- Web requests enable a full Laravel query log in `PerformanceLogger` to compute request metrics. This applies to every web request and can materially increase per-request memory on query-heavy admin pages; the risk is lower on API routes because the middleware is only in the web group.
- Report-response caching keys on path/query plus the full Authorization header hash input. It reduces repeated heavy analytics scans but creates one cache entry per distinct bearer token and makes correct controller-level authorization essential.

## Major integrations

- Calling/realtime: Agora token generation plus the separate Socket.IO/MySQL/Redis service.
- Push and lifecycle notifications: Firebase Admin/FCM and OneSignal, with several direct HTTP implementations as well as wrapper services.
- Pay-ins/subscriptions: PhonePe, Cashfree pay-in, Cashfree subscriptions/autopay, Razorpay payment links, and HDFC PG; legacy payment packages remain installed from the original Laravel application scaffold.
- Payouts/identity: Cashfree Payout and PaySprint validation/token generation.
- AI/ops: OpenRouter-based revenue, server, voice, ticket, onboarding, and notification generation; a Claude daily-intelligence service; Slack webhooks in monitoring/reporting paths.
- Attribution/marketing: install referrer, tracking/deep links, Google/MMP-related cron tooling, and notification conversion/report endpoints.
- Admin/MCP: a large web admin surface, role-restricted MCP operational API, analytics/reporting, moderation, support, creator verification, and finance operations.

Many integrations are synchronous because the queue driver is `sync`. When diagnosing latency or partial failures, separate database time from gateway/AI/push HTTP time and remember that caller timeouts do not necessarily imply the backend stopped processing.

## Production-only Maya/WATI subsystem

Production has two source files and API routes not present in the backend Git checkout:

- `MayaWebhookController.php`: public GET verification and secret-query-gated POST webhook for WATI inbound WhatsApp events. It records conversation history, builds creator context from users/calls/tickets, asks OpenRouter for a response, and sends a WATI session message. It fails closed when a configured webhook secret does not match, but would fail open at the secret check if the secret were absent; the independent feature toggle remains another gate.
- `MayaOutreach.php`: CLI command to seed inactive creators and send personalized WATI win-back templates, with OpenRouter-generated copy, follow-up cadence, re-churn monitoring, database state, active-hour limits, and optional whitelists.
- Root cron on app-1 runs `maya:outreach send --limit=25` every 30 minutes.

A deliberately redacted live configuration query returned only state, never credential or phone values:

- `maya_outreach_enabled`: on
- `maya_autoreply_enabled`: on
- outreach whitelist: missing
- auto-reply whitelist: missing
- webhook secret: configured

Therefore this production-only subsystem is actively capable of outbound WhatsApp messaging across the eligible pool rather than a whitelist. Its source is absent from Git, so a repo-based deploy or recovery would silently lose active behavior. It also makes external messaging depend on database-stored code configuration and app-1 root cron ownership. No command, webhook, OpenRouter request, WATI request, or database mutation was initiated during the audit.

## Confirmed live routing defect

Running the read-only command `php artisan route:list` against the live app fails with a `ReflectionException`: two public routes named around `default-coupon-analytics` in `routes/web.php` reference `App\\Http\\Controllers\\DefaultCouponAnalyticsController`, but that controller does not exist. The repository contains `CouponAnalyticsController.php`, not the referenced class.

Impact known so far:

- Laravel cannot enumerate its own complete route table, which obstructs operational inspection and may affect route-cache generation or any tooling that reflects controller actions.
- The two affected routes are outside an authenticated route group in the inspected file.
- No production change or route probe was performed. The intended controller/action still needs contract-level confirmation before proposing a fix.

Two additional live route-contract defects are confirmed: `/api/auth/logout` and `/api/auth/refresh` are registered against `AuthController`, but the live controller contains neither method. The current Android client appears to perform logout locally rather than call these routes, so this may be dormant for the app, but any API consumer using them will fail at controller dispatch.

## Confirmed critical unauthenticated Laravel actions

The live `AuthController` constructor exempts several state-changing methods from `auth:api`. Exact live source inspection, without issuing requests, confirms the following reachable route/action combinations:

- `POST /api/razorpay_add_coins` calls the auth-exempt `razorpay_add_coins`. It accepts caller-supplied `user_id` and `coins_id`, increments both `users.coins` and `users.total_coins`, and creates a credit transaction. It performs no JWT identity check, payment/order lookup, gateway signature verification, idempotency check, or transaction/row lock. This is a direct arbitrary coin-credit path and is critical.
- `POST /api/auth/add_coins` calls auth-exempt `add_coins`. It requires a pending order matching caller-supplied user/coin/order IDs, but then trusts caller-supplied `status=1` to mark the order successful and credit coins. There is no gateway verification or authenticated user binding. Knowledge or discovery of a pending order is the only material barrier in the inspected code.
- `POST /api/auth/send-fcm-notification` calls auth-exempt `sendNotification`. It accepts caller-supplied sender, receiver, call type, channel, and message, then sends a call FCM. Some message values also change call lifecycle markers. This permits notification/call-state impersonation and provides a potentially abusive outbound messaging primitive.
- `POST /api/internal/send-message-notification` and its aliases call auth-exempt `send_message_notification_internal`. The caller supplies sender, receiver, and message; the method looks up those users and sends a OneSignal message notification while impersonating the supplied sender. Blocking checks reduce recipient misuse but do not authenticate the caller.
- `GET /api/cron_socket_health` calls auth-exempt `cron_socket_health`. It checks `localhost:3001`, while the deployed socket service is on 3003. When that wrong-port check fails, the handler executes PM2 list/restart/start/save shell commands and writes logs. Thus an unauthenticated HTTP request can initiate service-control commands, and the health check is predisposed to see the real service as down. Active nginx routes socket traffic to app-3, while this Laravel action can be load-balanced onto app-1 or app-2 as well.
- Auth-exempt cron actions such as `cron_update_withdrawals` are public HTTP handlers. The withdrawal action polls all pending payouts, changes payout status, refunds balances on partner failures/reversals, and sends paid notifications. Its scheduler event has an overlap lock, but the method itself has no internal authorization or equivalent mutex; direct requests bypass the scheduler mutex and can multiply expensive/financial processing.

These findings are based on live source and route definitions. No endpoint was called and no coin, order, notification, call, process, or database state was touched. They should be treated as urgent remediation candidates, but remediation requires a compatibility plan because the Android app, socket server, payment gateways, and scheduler currently call some of these paths without a service credential.

## Live Socket.IO topology

- All three nodes still have a process listening on TCP 3003, but active nginx configuration on app-1 and app-2 proxies `/socket.io` to `10.122.0.9:3003`; app-3 proxies to `127.0.0.1:3003`. Therefore current user traffic is consolidated on app-3. The listeners on app-1/app-2 appear legacy or standby, but their operational purpose is not yet proven.
- App-3 PM2 reports `hima-socket-server` online.
- The live app-3 `index.js` and `redis-adapter.js` hashes match the local backend repository exactly.
- The server uses Socket.IO 4.6.1, `mysql2`, and a Redis adapter. It permits any CORS origin, accepts Engine.IO v3 clients, and allows payloads up to 100 MB. The database pool permits 50 connections with a queue of 100.
- Redis adapter startup fails soft to in-memory mode. Consolidated nginx routing reduces cross-node broadcast divergence today, but a future return to multi-node socket routing would require working shared Redis fan-out.
- Socket timestamps are manually written in IST. This must be reconciled with MySQL functions that return UTC and Laravel columns commonly written in IST during time-based diagnosis.

## Confirmed Socket.IO trust-boundary findings

Static review of the exact live socket source found no `io.use` authentication middleware, JWT validation, or equivalent handshake identity verification. Several paths trust caller-provided identifiers:

- `join_user` accepts a client-supplied `user_id` and assigns it to `socket.userId`.
- Message handling prefers the payload `from_user_id` before the socket identity, writes the chat row, and can initiate a notification.
- `send_reaction` similarly prefers a caller-supplied user ID.
- `delete_message` authorizes deletion using the body `from_user_id`, which the caller controls.
- REST endpoints for chat history, user chats, and presence appear to accept supplied user IDs without authentication.
- `/api/emit-creator-status` and `/api/emit-message-deleted` are defined before the general rate limiter and no authentication check was found.
- Laravel's `/api/internal/send-message-notification` action is included in `AuthController`'s authentication exception list.

These are confirmed code-level authorization weaknesses, not exploitation results. Likely impact includes user identity spoofing, unauthorized chat read/write/delete/reaction operations, presence disclosure, unauthenticated broadcasts, and notification abuse. No live request, payload, user impersonation, or external notification was attempted.

The socket service also logs message payloads and detailed responses extensively, creating a probable PII/log-retention exposure that should be assessed against actual production log storage and access controls.

## Confirmed media-storage asymmetry

Laravel's `public` disk is local ext4 at `/var/www/himaapp/storage/app/public` on each node, exposed through each node's `public/storage` symlink. Application features write voices, ticket screenshots, chat attachments, avatars, notification assets, reviews, gifts, and other media there.

Replication is asymmetric:

- App-1 lsyncd mirrors `storage/app/public` and `public/uploads` to app-2 and app-3 through `rsyncssh` with about a 3-second delay, `delete=false`, and the shared `id_ed25519_lsync` key.
- App-2 lsyncd mirrors the same directories back to app-1 with the same `delete=false` policy and delay.
- App-3 has no lsyncd config/process and is not part of the reverse mirror, so it can accumulate uploads that never flow back to app-1/app-2.
- All three underlying mounts are node-local `/dev/vda1` ext4, not a shared filesystem.
- Aggregate access-log mtimes confirmed app-3 is currently serving HTTP traffic through the load-balanced deployment.

Observed non-content aggregate counts under `storage/app/public` only; the separate mirrored `public/uploads` tree is outside these totals:

- App-1: 176,245 voices, 18,445 tickets, 79,009 chat media, 886 reviews, 6 gifts; total 274,800 files.
- App-2: 176,246 voices, 18,445 tickets, 79,009 chat media, 886 reviews, 6 gifts; total 274,801 files.
- App-3: 176,261 voices, 19,415 tickets, 91,797 chat media, 910 reviews, 6 gifts; total 288,598 files.

App-1 and app-2 are still near-identical, while app-3 is materially ahead and carries 16 more voices, 970 more ticket files, 12,788 more chat media files, and 24 more review files than app-1. That is consistent with uploads landing on app-3 and never flowing back. A later media request or backend existence check served by app-1/app-2 can miss such a file; with three load-balanced nodes this can appear intermittent. `delete=false` also means deletions do not converge. A delete on app-1/app-2 can be undone by the surviving peer's reverse mirror, app-3 keeps its independent copy, and bidirectional app-1/app-2 writes to the same path use last-arriving file semantics rather than a conflict-aware store.

The live app-3 code also lacks `VoiceVerificationService.php` while its shared `AuthController::update_voice` attempts to resolve that service. The call is caught and deliberately leaves the creator pending, so voice uploads on app-3 succeed but silently skip inline AI verification; uploads on app-1/app-2 can follow the AI path. Combined with the asymmetric media topology, voice onboarding behavior is node-dependent.

The two lsyncd processes themselves currently consume hundreds of MB of memory, making media replication state a relevant input to node-memory/performance diagnosis.

## Database connection topology and node drift

- App-2's MySQL is an active read replica: both IO and SQL threads were running, `read_only` and `super_read_only` were enabled, the last replication error was empty/zero, and observed lag was about one second.
- The explicit Laravel `mysql_replica` connection from all three app nodes reached the app-2 replica and saw it as read-only.
- App-1 and app-2's default `mysql` configuration sends ordinary reads and writes to the primary on app-1 unless code explicitly selects `mysql_replica`.
- App-3's live `config/database.php` alone adds Laravel read/write splitting to the default `mysql` connection. Runtime config lists both app-2 replica and app-1 primary as candidate read hosts, app-1 as the only write host, and `sticky=true`.

Consequences:

- The same unqualified `DB`/Eloquent read can hit only primary when served by app-1/app-2, but primary or a lagging replica when served by app-3.
- Read-after-write is protected within one app-3 request by stickiness, but a subsequent request can see replica lag.
- Load and query plans differ by the node selected by the load balancer, complicating intermittent stale-state and performance diagnosis.
- Features that intentionally use `mysql_replica`—notably dashboard/report code—can be stale on every node by normal replication lag. Ground-truth operational queries should continue to target the primary directly.

## Scheduler and cron ownership — verification in progress

The live Laravel `Console\\Kernel` defines many scheduled jobs. Root crontab on app-1 separately invokes operational jobs including orphan-call cleanup, peak-query/revenue sentinels, read/write-split watchdog, geo enrichment, MMP scheduling, and `maya:outreach send --limit=25`. `/etc/cron.d` additionally contains permissions, DAU, analytics, repeat-caller, and meta-metrics jobs.

The scheduler owner is now confirmed:

- The `www-data` crontab runs `/usr/bin/php artisan schedule:run` every minute from `/var/www/himaapp` on both app-1 and app-2.
- A process snapshot caught both runners active during the same minute.
- App-3 has no corresponding timer, service, process, Supervisor entry, or `www-data` cron entry in the inspected mechanisms.
- App-1 and app-2 both use Laravel's Redis cache store. Redacted runtime fingerprints initially differed because their configured Redis host strings differ, but a read-only Redis `INFO` server identity hash was identical. Their cache database and cache prefix fingerprints were also identical. Therefore `withoutOverlapping()` mutexes are shared across the two scheduler nodes.

Most active schedules use `withoutOverlapping()`, so the shared Redis lock normally elects one of app-1/app-2 for each named event. Important exceptions and inconsistencies:

- `report:daily` at 09:00 IST has no `withoutOverlapping()` or `onOneServer()`, so both nodes can execute it. Its failure-output email can consequently duplicate.
- `health:snapshot` is documented in code as intentionally running on both app-1 and app-2 to record hostname-specific rows, but it has the same named `withoutOverlapping(5)` lock on a shared Redis namespace. That guard likely permits only one node per interval, conflicting with the comment and potentially omitting half of the intended node snapshots.
- The install-referrer attribution backfill description and its entire `$schedule->command('attribution:backfill ...')` expression are on one physical line beginning with `//`. PHP therefore treats the command as a comment; the advertised every-minute backfill is not scheduled.
- Several schedules call public `https://himaapp.in/api/...` endpoints through the load balancer rather than invoking service code or commands locally. Scheduler execution can therefore land on any application node and depends on external LB/nginx/network health even though the initiating scheduler is local.

The earlier root-crontab search missed this because the application scheduler belongs to `www-data`, not root. This illustrates why scheduler diagnosis must include all relevant service users.

## Initial diagnostic implications

- For API authorization bugs, inspect the controller constructor exception list as well as route middleware; the visible route group is insufficient.
- For chat identity, deletion, reaction, presence, or unexpected notification bugs, first examine socket payload identity versus authenticated Laravel identity. The live socket currently has no dependable identity binding.
- For inconsistent realtime delivery, verify nginx target, PM2 status, Redis adapter state, and whether any traffic is reaching the legacy listeners on app-1/app-2.
- For missed recurring jobs, first identify the real scheduler owner; do not assume the `Console\\Kernel` is executing merely because schedules are defined.
- For route tooling/cache failures, reproduce the missing `DefaultCouponAnalyticsController` reference before investigating unrelated framework/cache causes.

## Backend feature ownership map

- Identity/onboarding/profile/config: primarily `AuthController`, `Users`, settings/config models, voice verification service, and OpenRouter onboarding paths.
- Calls and billing: `AuthController` call methods, `UserCalls`, `Transactions`, call status/reject/drop models, Agora, Redis liveness, FCM, and scheduled orphan cleanup.
- Chat/social: `AuthController` REST fallbacks and lists, friends/block services/models, `ChMessage`, the separate Socket.IO server, and OneSignal/FCM notification bridges.
- Wallet/pay-ins/payouts: `AuthController`, PhonePe/Cashfree/HDFC/Razorpay controllers, Cashfree subscription client/webhook, AutopayController, withdrawals, subscriptions, claims, orders, and transaction models.
- Creator operations: voice verification, tags, ratings, warnings, moderation, verification/admin controllers, matching score schedules, and creator notification services.
- Support/AI: tickets in `AuthController` plus admin/MCP ticket controllers, OpenRouter ticket generation, delayed replies, AI onboarding, and notification generation.
- Admin/analytics/ops: web controllers and Blade views, role-specific middleware, scheduled monitoring/report services, explicit replica reads, diagnosis snapshots, MCP controllers, and standalone operational scripts.
- Marketing/engagement: install attribution, links, reports, screen/smart notifications, conversion tracking, WhatsApp campaigns, and production-only Maya/WATI outreach.
- Other product surface: IPL voice rooms with their own controller/models and settlement logic.

## Architectural risk patterns to carry into feature audits

- Authenticated actions often accept a body `user_id`; every feature pass must verify it is bound to `auth('api')->id()` rather than trusted independently.
- JSON error semantics are inconsistent: many validation/business failures return HTTP 200, while others use 4xx/5xx. Android callers frequently must inspect `success` rather than rely on status codes.
- MySQL strict mode is disabled. Silent coercion/truncation and timezone interpretation must be considered when database values look impossible.
- All three production nodes run with configuration and route caching disabled. Hand patches take effect without cache rebuild, but every request reparses live configuration/routes and node drift is immediately user-visible.
- API CORS has an origin allowlist but also a regex that accepts every HTTP(S) origin; credential support is enabled. The regex effectively defeats the allowlist for browsers, although bearer-token theft is still required for protected Android APIs.
- The public `/api/health` endpoint checks the DB on every load-balancer probe and returns the caught DB exception message when unhealthy. This couples health traffic to the primary connection and can disclose backend failure detail.
- Production contains numerous legacy packages, backup source files, old payment surfaces, Chatify/Passport routes, installer/debug tooling routes, and duplicated route declarations. Runtime debug is off and package middleware blocks some tooling, but the attack/maintenance surface is materially larger than the active Android product.
- No single deployment artifact guarantees parity: code, cron, lsyncd, nginx, PHP-FPM, PM2, DB routing, and database configuration each have independent live state.

## Checkpoint conclusion

The backend is a load-balanced Laravel monolith with a separate Socket.IO process, shared primary/replica MySQL and Redis, local-but-partially-replicated media, synchronous integration calls, two scheduler nodes, and significant node/Git drift. The dominant diagnostic rule is to trace one feature across five boundaries: Android request/payload, load-balanced node, controller/service authorization and transaction logic, shared DB/Redis state, and any asynchronous push/socket/scheduler path. Detailed business rules now continue in the feature checkpoints; this architecture checkpoint is sufficient to resume without repeating the topology discovery.
