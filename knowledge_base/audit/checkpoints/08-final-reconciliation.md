# Checkpoint 08 — Final Live, Git, Runtime, Schema, and State Reconciliation

Status: complete

Scope: deterministic executable-source manifests, exact live-node drift, local working-tree truth, primary/replica schema identity, Redis/cache identity, recent hand-applied schema versus migration files, final non-secret feature flags, route-dispatch reconciliation, and remaining boundaries that cannot be proven without outward or mutating actions.

No production, database, service, Git, provider, notification, payment, or other outward mutation was performed.

## Reconciliation method

- Git status/branches/remotes were used only to identify worktree context, never to infer deployed behavior.
- `tools/tree_manifest.php` recursively hashes active Laravel source by path/content while excluding backup/recovery artifacts. Category and per-file manifests were computed independently in Git and on all three production nodes.
- `tools/route_method_inventory.php` strips route comments and uses Composer reflection on live nodes to distinguish real missing actions from commented routes, inherited methods, or orphan classes.
- `tools/runtime_schema_fingerprint.php` connects through each node's configured Laravel database connections and hashes information-schema table, column, and index metadata. It reports topology/read-only state but no credentials or row data.
- `tools/runtime_cache_fingerprint.php` fingerprints configured Redis host aliases and the live Redis server `run_id`, database, and prefix without reading cache values.
- Exact source diffs and selected non-secret feature flags were then inspected to interpret each hash difference. A differing hash was never treated as a behavioral difference without reading the code.

## Repository working-tree truth

### Android

- Current branch is `app_july_14`; remotes include `innovfix123` and `origin` and no `innovfix-ai` remote.
- Tracked Android product source has no current modification/staged diff. The repository contains many untracked mockups, reports, media, generated outputs, scripts, and temporary artifacts. They are user-owned and were not interpreted as compiled app features.
- `AGENTS.md` is untracked but now contains the owner-requested durable HIMA operating rules: use both repositories, prove actual code/callers rather than names/commits, live production truth, read-only/approval boundaries, all-three-node patching, performance gate, secrets handling, `innovfix123`, and full review before push.

### Backend/admin

- Current branch is `hima_sprint_july_13_26`; remotes include `innovfix123`, `origin`, and `democlone` and no `innovfix-ai` remote.
- The local backend has user-owned uncommitted work: tracked edits to `Kernel`, `UsersController`, `Users`, `services.php`, the admin menu, and web routes, plus untracked NudeNet lab/service/migrations/tests, block-reason migration work, and local user-edit persistence for the current block metadata (`blocked_reason`, `blocked_at`, `blocked_by`). The audit preserved it and made no product-repository edit.
- Strong-model review on 2026-07-15 confirmed that the block-reason textarea and client-side required toggle already existed in the checked-in edit view; the local WIP adds controller persistence/model fields rather than a new screen. On a transition from unblocked to blocked, it stores the submitted reason and stamps the current admin/time; it preserves the original stamp when an already-blocked row is re-saved, and clears all three fields on unblock. This is still local WIP rather than deployed production behavior.
- This implementation is current-block metadata, not a durable audit trail: server-side validation does not require a reason, direct requests can save it as null, an already-blocked legacy row with null stamps is not backfilled, the reason can be overwritten without history, and unblock deletes the reason/admin/time. The broad `users` resource also has no route-specific role-level guard; existing role-0/1/2 admin/support access behavior therefore needs an explicit product decision before release.
- The local NudeNet code is a feature-gated superadmin accuracy/labeling lab, not live moderation. Its production controller, services, migration, routes, menu entry, scheduler, and tables are absent.

## Deterministic live source result

After backup/recovery artifacts were excluded, app-1 and app-2 have identical active hashes and file counts for:

- 139 controllers;
- 24 middleware files;
- 29 services;
- 111 models;
- 46 console/scheduler files;
- all five route files;
- all 33 config files;
- all 273 view files; and
- all 276 deployed migration files.

This proves app-1/app-2 executable-source equality for the audited tree at the reconciliation snapshot. It does not make either node equal to Git.

### app-3 active drift

app-3 has the same active route, middleware, and migration hashes as app-1/app-2 but differs in the following executable/support files:

- `HomeController` lacks the main-dashboard `cad_workspace` session reset.
- The admin header lacks the Main Panel/Creators workspace switch. Together these make Creators Dashboard navigation/session behavior node-dependent.
- `UsersVerificationController` is older: it has no 1,000-row cap, auto-verify toggle, or Gemini `getAccuracy`; its corresponding view is also older.
- `VoiceVerificationService.php` is absent. Inline verification on app-3 is caught and leaves uploads pending. Production `voice_verify_mode` changed to `enforce` at 2026-07-15 15:40:01 IST, making this an active node-dependent onboarding path rather than a latent drift; direct accuracy/toggle routes are also broken there and the unbounded verification list remains active.
- `VoiceGeminiScore` uses the general model accessor rather than the voice model and always enforces the prompt-match gate. app-1/app-2 support the live prompt-gate flag. Manual/backfill results can therefore differ by node.
- `config/database.php` alone adds default-connection read/write splitting. Runtime proved app-3 writes to primary and can read from the read-only replica; app-1/app-2 default `mysql` reads primary. A later load-balanced request can therefore observe replica lag only when served by app-3.
- `Withdrawals` on app-3 includes Cashfree service-charge/tax in `$fillable`; app-1/app-2 do not. Current provider reconciliation assigns these properties directly before save, so this fillable-only drift does not change that path today, but future mass assignment can differ.
- app-3's user-call view always displays `ended_time`; app-1/app-2 suppress it when `started_time` is empty/zero. The same row can therefore look completed on one node and blank-ended on another.
- app-3's `Kernel` content difference is only the absence of explanatory inline-verification comments; no schedule expression differs in that hunk.

### Wallet/admin live parity

- A targeted 2026-07-15 live recheck of the wallet/admin mutation surface found `app/Http/Controllers/UsersController.php`, `resources/views/users/edit.blade.php`, `resources/views/femalereports/index.blade.php`, and `routes/web.php` byte-identical on app-1, app-2, and app-3. The active manual-wallet helpers therefore run from the same source on all three nodes, even though the mutation semantics remain non-transactional.
- Strong review expanded the all-three-node checksum to the relevant downstream consumers: `AuthController.php`, `SegmentService.php`, `AudienceSegments.php`, `NotifyInactiveNewUsers.php`, and `TransactionsExport.php` are also uniform across the three live nodes. Direct source inspection confirmed that the user-edit view exposes wallet fields, `users.update` overwrites them without ledger evidence, and admin `addCoins` creates an amount-less ordinary `add_coins`/`Credit` row that those offer, segment, notification, and invoice paths interpret as recharge evidence. This is live uniform behavior, not a Git-only inference.

## Git versus production

- Production contains live-only Maya/WATI controller/commands plus a live-only creator-update notification command. These are not present in the current local backend checkout and were already audited as production-only code.
- Local and live `AuthController`, `OpenRouterService`, API routes, and Diagnosis view differ because production has hand patches not represented by the current Git branch. Local `UsersController`, `Users`, `Kernel`, service config, menu, and web routes also differ because of the current user-owned work in progress.
- The live admin menu is uniform across all three nodes at this snapshot but differs from Git. This confirms the standing rule to match live menu items by route name and never overwrite the file from Git wholesale.
- App-1 contains several zeroed/broken/pre-revert controller recovery files and other nodes contain different recovery copies. They are not referenced route classes and were excluded from executable manifests, but they are filesystem clutter and should not be mistaken for alternate active implementations.

## Database topology and schema identity

- The primary is MySQL 8.0.46 with `read_only=0`; the replica is the same version with `read_only=1` and `super_read_only=1`.
- Primary and replica fingerprints are identical: 176 base tables, 1,983 columns, and 760 index-statistics entries, with matching table/column/index hashes.
- All nodes' explicit `mysql_replica` connection reaches the read-only replica. app-1/app-2 default `mysql` reaches primary. app-3 default `mysql` has a primary write PDO and a replica read PDO with sticky read-after-write behavior.
- The `hima_readonly` connection on every node reaches the writable primary under a write-capable account. Its name is not a safety control.

### Migration files do not describe deployment state

- Eight recent migration files exist in Git but not under live `database/migrations`. Production nevertheless has `call_heartbeats`, `male_call_fatigue`, `transactions.call_id`, both app-setting fields, both Cashfree withdrawal fee fields, and the three user block-reason/audit columns. These were hand-applied or otherwise deployed outside the live migration-file set.
- The two NudeNet tables do not exist. NudeNet files/routes are also absent, confirming the local lab is not deployed.
- The new user `blocked_reason`/`blocked_at`/`blocked_by` columns already exist while deployed `UsersController`/`Users` do not use them. The local uncommitted code now persists current-block metadata from the pre-existing user-edit fields and clears it on unblock, subject to the validation/history/access gaps above. This is a partial schema-first rollout, not an active production admin-block-reason feature.

## Redis/cache correction

- app-1 uses a local Redis host string; app-2/app-3 use the primary's private address. All three report the same Redis `run_id`, version 6.0.16, cache database 1, and prefix hash. Laravel cache and scheduler mutexes are shared; there are not multiple cache partitions.
- Earlier cached-report and screen-notification notes that inferred split caches from host strings were corrected. Risks remain where code has no claim lock or uses non-atomic `Cache::has` followed by `Cache::put`: simultaneous actors can both pass before either writes even on one shared Redis server.

## Final non-secret state snapshot

- Voice: `voice_verify_mode=enforce` as of 2026-07-15 15:40:01 IST, kill switch off, MCP-wide disable off, auto-approve on, auto-reject **off**, thresholds 92/85, prompt gate off, ambiguous rejection diversion on. Inline AI can auto-approve on app-1/app-2; manual review remains the fallback, and app-3 leaves uploads pending because its service is absent.
- Ticket AI account context is on; Cashfree webhook verification is enforce; creator-online instant notifications and friend-request notifications are on.
- UPI withdrawal, app-rating prompt, and duration-bonus master/audio/video flags are off.
- Icebreaker master/audio/video flags are off.
- Language routing remains Hindi/Punjabi `autopay`; Assamese, Bengali, Gujarati, Kannada, Malayalam, Marathi, Odia, Tamil, and Telugu are `none`. AI onboarding is not selected by any current language row.
- The auto-reject flag changed during the audit from on to off and remains off, so the male-conversion action is disabled. The separate master mode changed from off to enforce during the final review, activating auto-approve/scoring on compatible nodes without activating male conversion.

## Route-dispatch final result

- app-1/app-2 have 22 active route-action entries whose target method/file is absent; app-3 has the same set plus two voice-verification actions. Checkpoint 07 classifies every one by real caller/UI/runtime evidence.
- Visible broken paths are Default Coupon Analytics, Payments invoice/export, and Female Duration. Active Socket.IO friend creation calls the missing internal friend-notification action.
- The remaining missing actions are stale aliases or legacy residue with working replacements/no caller: old test-mail names, withdrawal bulk-cancel alias, GET add-coins/balance forms, Admin Staff toggle, `/check`, home language, Creator Registered, `customerdetails`, test PhonePe checksum, JWT logout/refresh, creators-admin-list, and historical creator tags.

## Remaining evidence boundaries

- External provider dashboards/configuration were not mutated or test-called. Cashfree plan cadence, provider delivery truth, bank outcomes, and similar external state must still be checked at incident time through approved provider/read-only evidence.
- Retained logs prove recent use only within their retention window. “No recent hit” means unproven/dormant classification, not proof no old/external client exists.
- Aggregate schema hashes prove structure equality, not zero replication lag or row-by-row data equality. Node, connection, and timestamp must still be checked during a specific incident.
- Admin/MCP strong review on 2026-07-15 verified five relevant source files byte-for-byte across local, app-1, app-2, and app-3 and ran one bounded primary-schema metadata query. It confirmed that `McpAuthorizes::remainingBudgetToday()` reads nonexistent `mcp_tokens.daily_payout_count`, causing zero payout-count-remaining telemetry in MCP auth/budget surfaces. The transactional payout path separately enforces the shared count with `daily_refund_cap_count`, so no payout-cap bypass was found; the uncalled preflight helper would falsely deny ordinary sub-threshold support payouts if wired in later.
- Final strong-model batch review on 2026-07-15 rechecked Android callers, live route/controller implementations, rating/agency/config state, Socket.IO identity, and critical payment/autopay source. `AuthController`, API routes, agency controllers, payment/autopay controllers, and the drift-prone live admin menu were byte-identical across all three nodes; route-name inspection confirmed that User Reports, Creator Verification, and User Ratings remain linked while Creator Ratings/agency administration remain outside that menu result. app-3's Socket.IO source matched the inspected backend checkout. The only material state correction was the voice mode transition to enforce. No provider call, notification, authentication attempt, or production mutation was performed.
- Android now actively calls the caller-side `call_ring_heartbeat` helper from both outgoing connecting screens every 2.5 seconds. Strong-model review on 2026-07-15 confirmed that app-1, app-2, and app-3 expose no matching route or controller method. Treat it as an active client-side contract/efficiency defect—repeated thread/network work and failed HTTP traffic—not as an active server ring-liveness feature.
- Feature flags, live hand patches, and data volumes can change after this snapshot. Bug diagnosis should begin from this knowledge base, then re-check the small set of implicated live files/state instead of re-auditing the whole system.

## Reconciliation conclusion

All known feature areas, provider methods, route actions, major storage contracts, and production topology boundaries are now reconciled sufficiently to build the final diagnostic system map. The most important operational qualifier is node identity: app-1/app-2 source is uniform, while app-3 can differ in verification, admin navigation/rendering, and default read consistency. Git remains a development reference rather than production truth.
