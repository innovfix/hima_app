# Checkpoint 00 — Baseline

Status: baseline inventory complete; architecture mapping continues in Checkpoint 01

This checkpoint establishes exact branches, commits, code sizes, runtime stacks, live-node fingerprints, and the high-level component graph. No application, backend, production, database, service, or Git state is modified.

## Measured local scope

- Android: 522 Kotlin/Java files, approximately 102,957 lines; 1,515 Kotlin/Java/XML files under `app/src/main`.
- Android automated tests discovered: 2 files.
- Backend: 711 PHP files and approximately 123,003 lines across `app`, `routes`, and `database`.
- Backend composition: 138 controllers, 111 models, 29 services, and 282 migrations.
- Backend automated tests discovered: 10 files.
- Socket.IO: 4 JavaScript source files and approximately 2,315 lines.
- Git branch references: 105 Android and 153 backend.

## Initial live observation

- SSH connectivity verified read-only to all three named production nodes.
- `/var/www/himaapp` exists on all three nodes.
- PHP file counts across `app`, `routes`, and `database`: app-1 707, app-2 707, app-3 706.
- The differing app-3 count is evidence that live-node drift must be measured rather than assuming identical deploys.

## Source baselines

- Android branch: `Hima_new_UI`; commit `ea16d939b663f55d4bde1c8b6d46f9f9e1c7bb04`.
- Android working tree contains a large set of pre-existing untracked reports/mockups/scripts. They are user-owned and were not modified.
- Backend branch: `hima_sprint_july_13_26`; commit `5e201781e95d55d3e7660528135d3b7223ecc5ba`.
- Backend tracked working tree was clean at baseline.
- Android build baseline: compile/target SDK 35, min SDK 24, versionCode 1117, versionName 1114, with demo and production product flavors.

## High-level component inventory

- Android is primarily Activity/Fragment XML UI with Hilt, ViewModels, repositories, Retrofit/OkHttp, WorkManager, Firebase/Firestore/FCM, OneSignal, Socket.IO client, Agora RTC, CameraX, Adjust, and Snap integrations.
- Largest Android code areas by source-file count: Retrofit/responses 133, utilities 76, activities 74, ViewModels 46, repositories 45, adapters 38, Agora/calling 23, dialogs 19.
- Manifest surface includes extensive call-related foreground services, telecom/full-screen permissions, FCM/OneSignal receivers/services, deep links, payment activities, and more than 70 activities.
- Laravel exposes approximately 320 API route declarations and 346 web/admin route declarations.
- API middleware includes throttling, route binding, and authenticated IP tracking; web/admin middleware includes sessions, CSRF, no-store handling, and role restrictions.
- Scheduler surface includes per-minute call/order/withdrawal cleanup, ticket delivery, creator notifications, monitoring, matching-score recalculation, moderation unblocking, notification campaigns, and health/diagnosis snapshots.

## Live runtime baseline

- app-1 hostname `ubuntu-s-2vcpu-4gb-120gb-intel-blr1-01`; PHP CLI 8.4.20. PHP-FPM 8.2, PHP-FPM 8.4, nginx, MySQL, and Redis were active.
- app-2 hostname `hima-app-2`; PHP CLI 8.4.20. PHP-FPM 8.2, PHP-FPM 8.4, nginx, and MySQL were active; Redis was inactive.
- app-3 hostname `hima-app-3`; PHP CLI 8.4.22. PHP-FPM 8.2, PHP-FPM 8.4, and nginx were active; MySQL and Redis were inactive.
- PM2 showed `hima-socket-server` online on app-3 port 3003.
- Node processes were also listening on port 3003 on app-1 and app-2. Whether they are unused legacy processes or receive traffic must be decided from live nginx/LB configuration, not process presence alone.

## Live drift baseline

- Active PHP files on app-1 and app-2 were hash-identical after excluding backup artifacts.
- app-3 had active drift in ten paths relative to app-1/app-2: `VoiceGeminiScore.php`, `Console/Kernel.php`, `HomeController.php`, `UsersVerificationController.php`, `Withdrawals.php`, `config/database.php`, diagnosis dashboard, admin header, user-calls view, and users-verification view. `VoiceVerificationService.php` was absent on app-3.
- The active `menu.blade.php` hash matched local Git and all three production nodes at this baseline moment. It must still be checked live for every menu task.
- Local Git versus app-1/app-2 differed in eleven active paths. Production had `MayaOutreach.php` and `MayaWebhookController.php` that were absent locally. `AuthController.php`, `OpenRouterService.php`, and `routes/api.php` differed by content. Six migration files existed locally but were absent from live files; database state must be checked independently before interpreting this.
- Key local/live matches included `AutopayController.php`, `Console/Kernel.php` on app-1/app-2, `Http/Kernel.php`, and the active admin menu.

## Early audit leads (not yet confirmed bugs)

- app-3 runtime code is materially behind or divergent from app-1/app-2 in user verification, withdrawals, scheduler, admin UI, and voice verification surfaces.
- Socket processes still listen on all three nodes despite the stated app-3 consolidation; live proxy configuration will determine actual traffic routing.
- MySQL is active and publicly listening on app-2 even though app-1 is designated as the production database host; the role and data state of app-2 MySQL need read-only verification.
- Repository scheduler formatting around the attribution backfill line appears suspicious and must be compared with live app-1/app-2/app-3 before concluding whether the job is actually scheduled.

## Next actions

- Begin Checkpoint 01: Android architecture, startup/navigation, state ownership, network stack, persistence, and background-work map.
- Verify live nginx Socket.IO targets and scheduler differences during the backend/live-topology phases.
