# Checkpoint 01 — Android Architecture

Status: complete at architecture level; feature internals continue in later checkpoints

FCM/background-worker review: 2026-07-15 IST. Registration, logout invalidation, WorkManager naming/replacement, retry classification, and current-account race guards were rechecked against the Android source.

Scope: application startup, build variants, navigation, authentication/session state, dependency injection, networking, local persistence, background work, notifications, sockets, and ownership boundaries between Activities/Fragments, ViewModels, repositories, and API models.

No source or runtime state will be modified.

## Startup and global orchestration

- Launcher entry is `SplashScreenActivity`; application class is the Hilt-enabled `BaseApplication`.
- `BaseApplication` is a very large process-level orchestrator (over 3,100 lines) that owns mutable call/ring state, foreground tracking, notification routing, audio routing, install attribution, OneSignal/FCM/Meta/Adjust/Snap initialization, Firestore initialization, session teardown, network callbacks, and Socket.IO startup.
- Cold start explicitly clears stale in-memory call/Telecom/audio state, cancels system notifications after version changes, forces light theme, initializes preferences/caches, registers the self-managed phone account, enables Firestore offline persistence for database `himadatabase`, initializes attribution SDKs, reasserts OneSignal identity/subscription, and resends the FCM token.
- FCM token repair uses both an immediate raw-OkHttp background thread and a per-user unique `FcmTokenRegisterWorker` with exponential backoff. `MyFirebaseMessagingService.onNewToken` replaces that same per-user unique work, while login/Main also register through Retrofit. `REPLACE` coalesces pending worker instances for one user, but the direct thread and Retrofit paths remain independent duplicate attempts.
- Both FCM registration and logout invalidation workers persist the captured 90-day bearer token in WorkManager input. Logout schedules `FcmTokenInvalidationWorker` but neither `performGlobalSessionTeardown()` nor `BottomSheetLogout` cancels `fcm_token_register_<oldUserId>`. The registration worker also does not re-check the currently signed-in account before sending. A network-delayed registration can therefore run after logout, reattach the token to the old account, or—after another account logs in on the same device—delete that token's new mapping because the backend enforces one owner by reassignment. The invalidation worker has same-user re-login guards, but those guards do not protect against the separate stale registration work.
- `BaseApplication` tracks the current Activity and active call Activity count through process-wide lifecycle callbacks. Call delivery, busy detection, notification suppression, and ringtone behavior depend on this mutable process state.

## Splash and signed-in routing

- Splash enforces a 3-second minimum display and uses a first-winner navigation guard to prevent API/fallback/incoming-call races.
- For a cached user, it checks individual app-version policy and arms an 8-second fallback. Incoming-call routing takes priority and opens `FemaleCallAcceptActivity`.
- Cached male users route directly to `MainActivity`. Cached female users refresh the profile first, then route by status: complete (`status=2`) to Main, partial (`status=1`) to Almost Done, otherwise Voice Identification.
- A user with no cached session routes to `NewLoginActivity` while `first_install` tracking runs separately.

## Main shell and navigation

- `MainActivity` is another large orchestration surface: bottom navigation, profile/settings/offer/coins bootstrap, notification permission, update checks, install attribution, payment SDKs, free-coin/welcome/rating prompts, badges, and several raw payment-network paths.
- Navigation is fragment replacement rather than Navigation Component state restoration.
- Male shell: Home (`HomeFragment`), Recent, Friends/Favourites (`FriendsHubFragment`), Profile (`ProfileFragment`); Chat is hidden.
- Female shell: Home (`FemaleHomeFragment`), Chat (`CreatorChatFragment`), Recent, Profile (`ProfileFemaleFragment`); Favourite is hidden.
- Notification intents can route to Recent/Favourite/Chat and optionally request a Friends/Chat sub-tab.

## State and persistence

- There is no Room/SQLite/DataStore database layer in the app code.
- Primary session state is JSON `UserData`, bearer token, settings, selected payment/order data, referral data, and recent force-rejected call IDs in ordinary SharedPreferences (`DPreferences`, store `Hima`).
- Many features use separate SharedPreferences namespaces for notifications, active chat, pinned/deleted/cleared chats, subscription marketing, feature caches, UI state, referrer attribution, ratings, and permission prompts.
- Media rendering is decentralized through Glide rather than a central cache manager. `AttachmentViewPagerAdapter` explicitly uses `DiskCacheStrategy.ALL`; most avatar, gift, chat, call, and ticket image loads use Glide's defaults. There is no app-wide `clearMemory()` / `clearDiskCache()`, cache-version signature, or server-delete reconciliation hook.
- Chat image compression and audio recording create temporary files in `cacheDir`. Normal terminal upload, cancellation, and callback paths delete them, but a delayed retry abandoned by Activity teardown can leave a cache file for OS eviction. `SubmitTicketActivity` is less complete: every submit copies selected URIs to deterministic `cacheDir/ticket_image_<index>.jpg` files and never explicitly deletes those copies on success, failure, or Activity destruction.
- The retired `BottomSheetTrialOffer` has no executable UI caller, but its media cache is not dormant. Every signed-in `MainActivity.onResume()` calls `TrialOfferConfigCache.prefetch()`, which calls the JWT config API, serves cached config immediately, refreshes in the background, and downloads the active language video when its URL-hash file is absent. The downloader is process-single-flight, uses a `.part` file plus atomic rename/copy, creates a poster JPEG, and removes older entries after a successful replacement; the single-flight guard covers the file download, not the repeated config API call.
- Firestore is used for selected profile/friend surfaces with offline persistence enabled.
- Chat history also has a user-bound in-memory cache; process call guards use concurrent maps and atomic counters.
- Global teardown disconnects Socket.IO, clears active-chat/account-specific chat caches, removes OneSignal identity/tags, clears dynamic shortcuts, resets subscription/language state, and clears the main session preferences.

## Networking and API ownership

- Hilt provides one Retrofit/OkHttp stack with a dynamic bearer header from `DPreferences` and global 401/redirect-to-login handling through EventBus.
- `ApiManager.kt` contains both the request facade and the Retrofit interface. The interface currently declares about 161 POST endpoints, 2 GET endpoints, and 4 multipart methods.
- Most features follow Activity/Fragment -> Hilt ViewModel -> repository -> `ApiManager` -> `ApiInterface` -> Laravel.
- Several critical paths bypass this stack with direct OkHttp: cold-start FCM registration, WorkManager call updates/status, token registration/invalidation, and multiple payment paths. These paths do not automatically share Retrofit's global unauthorized handling or identical timeout/error behavior.
- The legacy `activities/ApiService.kt` also defines direct payment endpoints, creating a second API definition surface.

## Realtime and background work

- Socket URL is derived from the flavor's app-root host; path is `/socket.io`.
- `SocketManager` is a singleton with user-aware reconnect protection, a 15-second connect watchdog, capped reconnect attempts, user-room join, and event `SharedFlow`s for messages, receipts, typing, reactions, deletes, chat updates, and creator availability.
- Event buffers hold 256 items and drop the oldest during overflow; correctness under a very large reconnect burst therefore depends on REST refresh/reconciliation.
- `CallStatusWorker` durably retries rejected-call outcomes with network constraints and exponential backoff.
- `CallUpdateWorker` sends connected-call timestamps but treats network exceptions and non-2xx responses as terminal failure rather than retry.
- FCM token register/invalidate workers handle offline recovery and include relogin-race guards for invalidation. The register worker carries the bearer token in its input until execution, so the app keeps another at-rest copy of the credential while the retry is pending.

## Notification and call entry points

- Firebase data messages enter through `MyFirebaseMessagingService`; the class handles token rotation, incoming/missed calls, concurrent-call detection, account/session clearing, verification messages, and notification-channel creation.
- OneSignal service-extension processing covers incoming/missed calls, friend requests, chat messages, list-refresh broadcasts, DND, and fallback display before the application UI is necessarily alive.
- Foreground OneSignal processing also exists in `BaseApplication`, so notification behavior is intentionally split by process/foreground state.
- Incoming-call presentation uses `FcmCallService`, `CallNotifications`, `CallActionReceiver`, call-accept Activities, and the self-managed Android Telecom `HimaConnectionService`.
- `CallingService` keeps active calls in a foreground service and creates return-to-call intents.
- This means a call can enter through Firebase, OneSignal, a notification action, launcher recovery, or Telecom. The call-phase audit must prove that every path converges on the same idempotent server status and local cleanup rules.

## Complexity concentration

- The largest files dominate behavior and regression risk: Chat Activity 5,611 lines; four gender/media call Activities roughly 3,900–4,300 lines each; API Manager 3,568; Base Application 3,147; Main Activity 2,902; FCM service 1,767; male/female Home 1,749/1,624; Wallet 1,639.
- UI, network orchestration, persistence, analytics, and business decisions frequently coexist in Activities/Fragments rather than being fully isolated in ViewModels/domain services.
- Repositories are generally thin `ApiManager` adapters; ViewModels mostly expose callback results through LiveData. Therefore many correctness decisions remain in Activities/Fragments and cannot be inferred from the repository layer alone.
- Network availability is checked through deprecated `activeNetworkInfo.isConnected`, which reports connectivity but not necessarily validated internet access.

## Early audit leads (not yet confirmed bugs)

- Splash returns immediately when there is no network before scheduling cached-session navigation or the fallback. This appears capable of leaving an offline user indefinitely on the splash screen.
- `AppModule` logs the full bearer token, headers, and request body for the FCM-notification endpoint outside the debug-only logging gate. This is a serious credential/log exposure risk in production builds.
- The bearer token and full user object are stored in ordinary, unencrypted SharedPreferences.
- At least one SDK credential intended to be secret is compiled into BuildConfig/app code. The value is deliberately not recorded in this audit.
- `CallUpdateWorker` does not retry transient connectivity or 5xx failures, weakening its value as a process-death/network durability mechanism.
- Process-wide call correctness depends on many mutable flags/counters across `BaseApplication`, FCM services, call Activities, Telecom objects, and WorkManager. Ordering/process-death race analysis is required in the call phase.
- Cold-start and Main initialization fan out to many independent network/SDK operations. This increases startup timing variance and produces multiple partially independent sources of session/profile/notification state.
- The monolithic API interface contains repeated logical endpoints and a very broad responsibility surface, raising contract-drift risk; exact Android-vs-live-route comparison is pending.
- Multiple independent notification entry points create duplicate-display, duplicate-status, or stale-process-state risk unless call/message IDs are consistently deduplicated across all paths.

## Architecture mental model

`Android component (Activity/Fragment/service/receiver) -> optional ViewModel -> thin repository -> ApiManager/Retrofit -> Laravel`, with important exceptions that use direct OkHttp or Socket.IO. `BaseApplication` and SharedPreferences provide process/account-wide coordination; backend reconciliation is essential because process death discards much of the call/chat state.
