# HIMA Shared Project Rules

These rules apply to all Codex work under both HIMA repositories:

- Android app: `/Users/apple/Desktop/hima_autopay`
- Laravel/admin/backend: `/Users/apple/Desktop/hima_admin_panel`

Repository-specific `AGENTS.md` files may add stricter documentation, testing, or implementation requirements. Preserve and follow them together with this file.

## Repository and Git rules

- Treat both repositories as one product when investigating bugs or changing behavior. Trace the Android caller, Laravel/API implementation, Socket.IO behavior, persistence, and live production drift when relevant.
- Never conclude what code does, whether a feature is active, or why behavior occurs from command names, filenames, route names, comments, documentation, branch names, or Git commits/messages alone. Treat all of them only as investigation leads.
- Before concluding that a path is used, trace the actual end-to-end code flow: Android/UI caller and conditions, Retrofit/socket contract, route and middleware, controller/service/job implementation, model/table effects, flags/configuration, retries/fallbacks, scheduler/process/runtime wiring, and live production source/state when relevant.
- Distinguish clearly between active production paths, legacy compatibility paths, fallbacks, dormant/flagged-off code, admin-only/manual tools, scheduled commands, and dead/unreferenced code. The existence of a command, controller method, route, cron definition, or commit is not proof that it executes.
- Validate important conclusions with multiple forms of evidence where possible: static call sites plus live-source/runtime/process/log/schema evidence. State uncertainty explicitly when execution cannot be confirmed without a prohibited mutation or outward action.
- The authorized GitHub owner/remote is `innovfix123`, not `innovfix-ai`. Expected repositories are `innovfix123/hima_app` and `innovfix123/hima-admin-panel`.
- Codex may create a branch from a named branch when the owner asks. Use the requested branch name; otherwise use the normal `codex/` prefix.
- Never commit, push, open a pull request, post to GitHub, or otherwise publish externally unless the owner explicitly asks for that action.
- Before every authorized push, perform a full review of the complete diff, run relevant builds/tests/static checks, check Android-backend compatibility, and review security, authorization, data integrity, performance, and regression risk. Do not push known-defective code unless the owner explicitly accepts the documented defect.
- Preserve unrelated/user-owned work in dirty worktrees. Never discard or rewrite it.

## Production safety and source of truth

- Production is read-only by default. Reading files, logs, runtime state, indexed query plans, and read-only database results is allowed when needed. Ask for explicit per-action approval before any production code/config edit, database write, deployment, service reload/restart, process change, or other mutation.
- Ask before any outward-facing action, including push notifications, email, Slack, payouts, gateway/payment actions, GitHub posts, or messages to people/services.
- For anything under `/var/www/himaapp`, verify against the live boxes rather than trusting Git. Production is hand-patched and can drift from the repository.
- `menu.blade.php` is especially drift-prone: inspect the live file and match items by route name. Never overwrite it from Git assumptions.
- Never deploy production with `git pull`.
- If an app patch is explicitly approved, patch and verify all three application nodes, then reload both PHP-FPM 8.2 and 8.4 only with explicit approval.
- Apply a performance gate before any DB/app change. `transactions` and `user_calls` are very large; inspect indexes and use `EXPLAIN` before proposing or running relevant queries. Avoid unindexed production scans.
- Laravel-written datetime columns are generally stored in IST, while MySQL `NOW()`/`UTC_TIMESTAMP()` may be UTC. Reconcile timezone explicitly before comparing timestamps.

## Production topology

- Load balancer: `himaapp.in` / `64.225.87.36`.
- app-1: `139.59.56.195` (private `10.122.0.2`); also hosts the production MySQL primary and Redis.
- app-2: `168.144.68.179` (private `10.122.0.3`); production replica may be read-only/super-read-only.
- app-3: private `10.122.0.9`, public `165.232.181.213`; reach private SSH through app-1. Socket.IO is consolidated on port 3003 here, although legacy listeners can exist elsewhere.
- Production app code: `/var/www/himaapp` on all three nodes.
- Production DB: `himaapp` on app-1. Use only the established read-only/admin query path appropriate to the task; never expose credentials.
- Demo: `root@165.22.221.210`, code `/var/www/demo-hima`, PHP 8.3, Socket.IO/PM2 on 3003. Treat demo as read-only unless an exact mutation is approved.
- `demolivedb.himaapp.in` is off-limits unless the owner explicitly names it for the task.
- For production analytics or connectivity/revenue ground truth, use direct read-only production evidence, not the HIMA Analytics MCP.

## Secrets and sensitive data

- Never open, copy, display, log, commit, or transmit `.env`, `*_DB_ACCESS_HANDOVER.md`, `key0.jks`, private keys, passwords, access tokens, gateway credentials, signing material, or secret configuration values.
- If source inspection reveals an embedded credential, record only that a credential exists and where; redact its value from notes and responses.
- Keep audit notes aggregate/redacted. Do not store user-level production PII, message content, FCM tokens, payment details, bank/UPI data, or raw provider payloads.

## Resumable system audit knowledge base

- The durable HIMA audit entry point is `/Users/apple/.codex/visualizations/2026/07/14/019f5ef8-18a0-7510-a6ac-da6ba390d578/hima-system-audit/HIMA_AUDIT_MASTER.md`.
- Before a broad HIMA diagnosis or audit continuation, read the master, `README.md`, and relevant completed checkpoints. Re-check live source before treating a production conclusion as current.
- Save new confirmed, redacted findings into the active Markdown checkpoint. Do not treat audit notes as authorization to mutate production, Git, databases, services, payment systems, or external communications.
