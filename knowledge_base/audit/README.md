# HIMA System Audit

Status: complete baseline, read-only, resumable and maintainable

Final strong-model batch review: 2026-07-15 15:40 IST. Completion is 100% of the defined baseline and targeted deep-audit scope; incident-time live/provider evidence boundaries still apply.

Maintenance rule: every future HIMA code, configuration, schema, infrastructure, or production change must end with a targeted post-change re-audit. Update the owning checkpoint from verified implementation/runtime evidence; update the master and diagnostic map only when behavior, risks, topology, flags, or diagnostic guidance materially change.

Master entry point: `HIMA_AUDIT_MASTER.md`

Fast bug-diagnosis entry point: `HIMA_DIAGNOSTIC_SYSTEM_MAP.md`

## Sources of truth

- Android repository: `/Users/apple/Desktop/hima_autopay`
- Backend repository: `/Users/apple/Desktop/hima_admin_panel`
- Git remote to use when explicitly authorized: `innovfix123`
- Production code: `/var/www/himaapp` on all three nodes
- Demo code: `/var/www/demo-hima` on `165.22.221.210`
- Production database: `himaapp` on app-1

## Production topology

- Load balancer: `himaapp.in` / `64.225.87.36`
- app-1: `139.59.56.195`, private `10.122.0.2`, also production DB
- app-2: `168.144.68.179`
- app-3: private `10.122.0.9` via app-1 jump host, public `165.232.181.213`; Socket.IO port 3003

## Hard safety rules

- Production is read-only unless the owner explicitly approves the exact mutation.
- Never treat Git as production truth; inspect live `/var/www/himaapp` files directly.
- Never deploy with `git pull`; production code is hand-patched on all three nodes.
- A production patch must cover all three nodes, be verified, then reload PHP-FPM 8.2 and 8.4 only with explicit approval.
- Check indexes/query plans before proposing changes involving large tables, especially `transactions` and `user_calls`.
- Normalize Laravel IST timestamps versus MySQL UTC functions before conclusions.
- Do not access `demolivedb.himaapp.in` unless explicitly named by the owner.
- Do not read, copy, display, or transmit `.env`, `*_DB_ACCESS_HANDOVER.md`, `key0.jks`, private keys, tokens, or passwords.
- Ask before production code/config/DB/service changes or any outward-facing action.
- Before any authorized Git push, review the full diff and run relevant verification. Stop rather than push known-defective code.

## Checkpoint index

- `checkpoints/00-baseline.md` — repository, runtime, and live topology baseline (complete)
- `checkpoints/01-android-architecture.md` — Android architecture and runtime flow (complete)
- `checkpoints/02-backend-architecture.md` — Laravel, scheduler, integrations, Socket.IO, and live route architecture (complete)
- `checkpoints/03-auth-onboarding-profile-config.md` — authentication, onboarding, identity, profile, voice, configuration, and attribution (complete)
- `checkpoints/04-calls-agora-billing.md` — discovery, call signaling, Agora lifecycle, billing, settlement, post-call behavior, and recovery (complete)
- `checkpoints/05-chat-socket-friends-blocking.md` — chat, Socket.IO, friends, favourites, blocking, and realtime recovery (complete)
- `checkpoints/06-wallet-payments-autopay-withdrawals.md` — wallet, payments, autopay, gifts, transaction history, withdrawals, reconciliation, admin/manual transitions, and money-state recovery (complete)
- `checkpoints/07-creator-moderation-tickets-admin-analytics-ai.md` — creator lifecycle, moderation, tickets/support, admin authorization, analytics, AI/provider workflows, exports/imports, and route dispatch (complete)
- `checkpoints/08-final-reconciliation.md` — live/Git/node source manifests, runtime DB/Redis and schema identity, state flags, and remaining evidence boundaries (complete)

## Audit helpers

- `tools/route_method_inventory.php /path/to/laravel-root` — reads active web/API route actions, strips commented code, and reports controller files/methods that do not exist. When the Laravel Composer runtime is available, it uses reflection so inherited/trait methods are handled correctly. It is read-only.
- `tools/tree_manifest.php /path/to/laravel-root [--summary|category]` — computes deterministic path/content manifests for active Laravel source while excluding backup/recovery artifacts.
- `tools/runtime_schema_fingerprint.php /path/to/laravel-root` — fingerprints configured database endpoints and table/column/index metadata without reading business rows.
- `tools/runtime_cache_fingerprint.php /path/to/laravel-root` — fingerprints configured Laravel Redis/cache identity without reading cache values.
