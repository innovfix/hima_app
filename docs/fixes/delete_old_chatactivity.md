# Delete Old ChatActivity

**Date:** 2026-04-28

## What was fixed
The legacy `ChatActivity` was still shipped alongside the new `ChatActivityInHouse`. Notifications posted by the old version (and a stray adapter button) opened the old screen after upgrade. Deleted `ChatActivity` entirely, rerouted the leftover call sites, and added a one-time stale-notification reset on version bump so old PendingIntents that pointed at the deleted class don't crash or open a dead screen.

## Changes
- **Deleted** `app/src/main/java/com/gmwapp/hima/activities/ChatActivity.kt` (legacy ~1700-line class).
- `app/src/main/AndroidManifest.xml`
  - Removed `<activity android:name=".activities.ChatActivity" .../>` block.
- `app/src/main/java/com/gmwapp/hima/adapters/RecentCallsAdapter.kt`
  - Both `ivChatCircle` listeners (lines 136 and 184) now target `ChatActivityInHouse::class.java`. `USER_ID` / `USER_NAME` / `USER_IMAGE` extras kept — `ChatActivityInHouse.kt:871, 910, 913` already reads those keys.
- `app/src/main/java/com/gmwapp/hima/fragments/FriendsTabFragment.kt:1031`
  - Cosmetic comment update: `ChatActivity` -> `ChatActivityInHouse`.
- `app/src/main/java/com/gmwapp/hima/BaseApplication.kt onCreate()`
  - Right after `mInstance = this`, added a version-bump check: on first launch after a `BuildConfig.VERSION_CODE` change, calls `NotificationManagerCompat.cancelAll()` once and writes the new code to a separate `app_version_prefs` SharedPreferences. Subsequent launches no-op.

## Untouched
- `ChatActivityInHouse` (the new chat UI) — unchanged.
- `ChatNotifications.kt`, `CallNotifications.kt`, `BaseApplication.kt` OneSignal click handler — already targeted `ChatActivityInHouse`.
- `ChatNotificationStore` and per-peer notification ids — unchanged.
- Recent/missed badge code — unchanged.
- `RecentCallsAdapter.openChatActivity()` private method name — already routed to `ChatActivityInHouse`; only the method name still mentions "Chat" without "InHouse".
- Markdown docs (`CHAT_DESIGN_SUMMARY.md`, `CHAT_QUICK_START.md`, `FIRESTORE_CHAT_INTEGRATION.md`) — out of scope per plan.

## Why the version-bump cancel
`NotificationManagerCompat.from(this).cancelAll()` only clears notifications posted by **this** package. It wipes leftover chat notifications whose PendingIntent pointed at the deleted `ChatActivity`, so users upgrading from a previous build with active notifications don't tap into a dead activity. It does not touch in-app `ChatNotificationStore` history.

`app_version_prefs` is a dedicated SharedPreferences file so this code does not race with `AppPrefs` initialization later in `onCreate`.

## Verify
- Cold-start the new build first time -> logcat: `AppUpgrade: version bump -1 -> <code>; cancelling stale system notifications`. System tray is empty.
- Restart again without upgrading -> log line does **not** repeat (`lastVersion == currentVersion`).
- Recent tab chat icon on any call row -> opens `ChatActivityInHouse` with the correct `USER_ID`.
- Receive a new chat push -> tap heads-up -> opens `ChatActivityInHouse` (no flicker, no old screen).
- `./gradlew :app:compileProductionDebugKotlin` should succeed; no compile errors referencing `ChatActivity::class`.
- Optional manual upgrade test: install previous version, send self a chat (notification posts), sideload new build over it -> first launch: stale notification gone; new notifications still work.
