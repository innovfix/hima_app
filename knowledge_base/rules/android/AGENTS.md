# Hima Project Instructions

## HIMA operating and audit rules

These rules apply to all HIMA Android, backend, admin, production, database, Git, and infrastructure work performed from this workspace.

- Treat both repositories as one system: Android is `/Users/apple/Desktop/hima_autopay`; the Laravel backend/admin repository is `/Users/apple/Desktop/hima_admin_panel`.
- Use the `innovfix123` Git remote for owner-requested HIMA Git operations, not `innovfix-ai`. Do not push unless the owner explicitly asks.
- Before every requested Git push, review the complete diff and relevant surrounding code, check cross-app/backend compatibility, and run risk-proportionate validation. Never push known-defective or unreviewed code.
- Do not infer behavior from route names, command names, comments, docs, branch names, commit messages, or file presence. Read the implementation and prove actual callers, conditions/flags, payload and data flow, controller/service/model/table behavior, runtime scheduling, and failure handling before concluding what a feature does or whether it is used.
- Production is read-only by default. Read/inspect/query safely, but obtain explicit per-action owner approval before any production code/config/file change, database write, migration, deploy, service restart/reload, process action, or other mutation.
- Obtain explicit per-action approval before any outward-facing action, including notifications, Slack/email/messages, AI/provider test calls that incur spend or transmit data, payouts/refunds, and GitHub posts/pushes/PRs.
- For `/var/www/himaapp`, production files are the source of runtime truth and may drift from Git. Inspect the live file directly. Always inspect the live admin sidebar by route name because `menu.blade.php` is drift-prone. Never deploy production with `git pull`.
- A production HIMA app patch must be applied to app-1, app-2, and app-3, verified on all three, and followed by PHP-FPM 8.2 and 8.4 reloads only after explicit approval. Socket.IO production traffic is consolidated on app-3:3003.
- Apply the database performance gate before any app/DB change or potentially heavy production query. Inspect relevant indexes and use EXPLAIN/range-safe aggregate evidence first; `transactions` and `user_calls` are very large. Reconcile Laravel-stored IST timestamps with MySQL UTC functions.
- Query production ground truth directly through approved read-only/admin query paths; do not treat analytics labels or cached dashboards as authoritative connectivity/revenue truth.
- Never read, copy, display, or transmit secrets from `.env`, `*_DB_ACCESS_HANDOVER.md`, signing keys, private keys, tokens, passwords, or gateway credentials. Keep user-level production data out of audit notes; use redacted aggregates and metadata.
- Demo backend is `/var/www/demo-hima` only when the task names demo. `demolivedb.himaapp.in` is off-limits unless the owner explicitly names and approves it.
- Preserve unrelated and user-owned working-tree changes. The backend can contain live-vs-Git patches and local work in progress; inspect status and isolate the requested change.
- The resumable read-only system audit and diagnostic knowledge base lives at `/Users/apple/.codex/visualizations/2026/07/14/019f5ef8-18a0-7510-a6ac-da6ba390d578/hima-system-audit`. Update confirmed audit findings there without placing audit files in either product repository.

### Mandatory HIMA knowledge-base workflow

- For every HIMA bug report, question, investigation, code change, review, or operational task opened in this workspace, automatically consult the saved audit knowledge base. The owner does not need to ask or remind Codex to use it.
- Start with `HIMA_DIAGNOSTIC_SYSTEM_MAP.md`, then open `HIMA_AUDIT_MASTER.md` and only the checkpoint files relevant to the affected feature or runtime path. Do not load unrelated checkpoints merely for completeness.
- Use the audit as a diagnostic index and prior evidence, not as a substitute for current verification. Reinspect the implicated Android and backend implementations and, when relevant, the read-only live production files, flags, logs, schema, indexes, and safe aggregate data before confirming a cause or proposing a fix.
- If current code or live evidence differs from the saved audit, treat current verified runtime evidence as authoritative and update the appropriate audit Markdown files with the newly confirmed finding, date, and scope.
- Apply this workflow automatically in new Codex chats/tasks started from the HIMA workspace, even when the owner's message only describes the bug or requested change.

### Mandatory audit maintenance after every HIMA change

- Every HIMA Android, backend/admin, API, Socket.IO, job/cron, configuration, schema, infrastructure, or production change must finish with a targeted re-audit of the affected end-to-end path. This applies to features, fixes, refactors, performance work, operational patches, and configuration-only changes.
- Re-read the actual changed implementation and its callers/downstream behavior after validation. When production is affected, verify the approved deployed result read-only on all relevant nodes and current non-secret runtime state; never infer the audit update from the diff, commit message, route name, or intended behavior alone.
- Update the owning audit checkpoint with the verified post-change behavior, date, scope, failure handling, compatibility/performance implications, and any remaining uncertainty. Update `HIMA_DIAGNOSTIC_SYSTEM_MAP.md` and `HIMA_AUDIT_MASTER.md` only when the change alters system behavior, diagnostic causes, risk, topology, active flags, or completion state; do not add noisy duplicate entries for behavior that remains unchanged.
- A HIMA code/configuration change is not complete until this targeted audit maintenance is finished. Audit documentation remains separate from product repositories and must be saved only in the established HIMA audit knowledge base.

### Mandatory performance, capacity, and regression gate

- For every bug fix, feature, refactor, configuration change, query, API, background job, cron, socket event, admin page, or Android change, assess performance and breakage risk before implementation and verify it again after implementation. This gate is mandatory whenever the work can affect app responsiveness, API latency, database load, queue load, memory, CPU, disk, network traffic, or server capacity.
- Establish a relevant baseline before changing code whenever measurement is possible. Compare the same path after the change and record the environment, test data size, request or operation count, response-time result, and any material CPU, memory, query-count, rows-examined, queue, disk, or network impact.
- Inspect the complete caller and downstream path before changing it. Check Android-to-API compatibility, request/response contracts, authentication and permissions, retries and idempotency, database indexes and row volume, queue/cron concurrency, cache behavior, failure handling, timeouts, and rollback behavior. Do not optimize one layer by shifting unsafe work or load into another layer.
- Database work must use indexed, bounded access patterns. Before adding or changing a query, inspect the schema and relevant indexes and use `EXPLAIN` or equivalent safe evidence for non-trivial paths. Avoid table scans, unbounded joins or aggregates, N+1 queries, large `WHERE IN` lists, unnecessary locks, and repeated lookups on high-volume tables. Use pagination, limits, batching, primary-key/range access, and precomputation where appropriate.
- API and admin changes must measure or otherwise verify endpoint/query latency and query count using representative data. Expensive AI, image, notification, export, aggregation, cleanup, and third-party work must not block user-facing requests; place it in an idempotent bounded queue/job when appropriate, with retry limits, backoff, timeouts, observability, and overload protection.
- Android changes must not perform network, database, image-processing, AI, or other blocking work on the main thread. Check startup, navigation, scrolling, call connection, in-call behavior, battery, memory, data usage, lifecycle cleanup, background restrictions, and low-end-device behavior as relevant. Ensure listeners, handlers, executors, media objects, temporary files, and workers cannot leak or duplicate work.
- Server and infrastructure changes must be evaluated against realistic and peak concurrency, not only a single successful request. Check worker capacity, queue depth, process memory, CPU, connection pools, storage growth and retention, rate limits, job overlap, multi-node duplication, and failure/recovery behavior. Add feature flags, shadow mode, batching, backpressure, or staged rollout whenever risk is meaningful.
- A change is not complete merely because it functions. It must preserve existing behavior outside its intended scope, remain responsive under representative load, and include risk-proportionate regression and performance tests. If a reliable performance check cannot be run, state exactly what remains unverified and do not describe the change as production-ready or authorize rollout.
- Never deploy or enable a change that measurably slows a critical app/API path or creates unsafe server/database load without explicit owner acceptance of the measured tradeoff. Prefer an optimized design, and report before/after evidence and remaining capacity risks in the handoff.

## Mandatory documentation maintenance

These rules apply to every future Codex task in this repository.

### Hima Complete Documentation

- Treat `output/pdf/Hima_COMPLETE_Documentation.pdf` as the maintained Hima product documentation.
- Update this PDF whenever code adds a genuinely new feature or capability to the Android app, admin panel, backend, API, or a supporting system.
- A material extension of an existing module that creates a new user, admin, business, or operational capability also counts as a new feature.
- Do not update this PDF for ordinary bug fixes, crash fixes, hotfixes, UI-only improvements, wording or styling changes, refactoring, dependency upgrades, logging, cleanup, or performance work that does not introduce a new capability.
- For each new feature, document its purpose, affected users, user/admin flow, validations, permissions, edge cases, failure handling, dependencies, app/admin screens, APIs/routes, controllers, models/tables, socket events, jobs/crons, configuration, analytics, external services, operational controls, feature flags, monitoring, and rollout/rollback behaviour when relevant.
- Update the document date/version and identify the owning feature and release or branch.

### Hima QA Release Testing SOP

- Treat `output/pdf/Hima_QA_Release_Testing_SOP.pdf` as the maintained tester handover document.
- Every product code or configuration change intended for a release must be represented by tester-ready test cases before the implementation task is considered complete.
- This requirement includes new features, bug fixes, UI/UX changes, backend or API changes, admin-panel changes, payment changes, AI or notification changes, analytics changes, permissions, feature flags, dependencies, data migrations, infrastructure, performance, and security changes that can affect application behaviour.
- Use the existing checklist pattern and include a unique test-case ID, module/change reference, priority or MUST-PASS status, preconditions, build/environment, account role, test data, numbered steps, expected results, execution status, and evidence/defect/retest fields where applicable.
- Include happy-path, negative, validation, boundary, edge, permissions, failure/retry, regression, compatibility, and production-smoke coverage as relevant.
- For a bug fix, include the original reproduction case, verification of the fix, nearby regression coverage, and a permanent regression test.
- For a UI/UX change, cover the approved design, interaction states, navigation, keyboard/back behaviour, loading/empty/error states, text overflow, and relevant device sizes.
- For backend, admin, or configuration work, cover authorization, data correctness, failures/retries, backward compatibility, audit/logging, and the user-visible outcome.

### Completion and PDF quality gate

- Do not mark a feature implementation complete until the Complete Documentation update is included.
- Do not hand any release implementation to QA until every release change maps to at least one test case in the QA SOP.
- Pure analysis, investigation, documentation-only work, and code changes that are not intended for a release do not require QA test cases unless the user explicitly requests them.
- Preserve the existing PDF design and checklist style. Render and visually inspect all changed PDF pages before delivery, checking for clipping, overlap, broken tables, unreadable text, and incorrect pagination.
- Preserve the previous PDF unless the user explicitly requests replacement; provide the revised PDF from `output/pdf/`.
