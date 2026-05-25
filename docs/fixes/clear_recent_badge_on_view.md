# Clear Recent Badge On View

**Date:** 2026-04-28

## What was fixed
The bottom-nav Recent badge (e.g. `38`) was the unseen missed-call count from `missed_call_count?seen=0`. Backend was only told `seen=1` when the user tapped the **Missed** chip — most users never did, so the badge kept growing. Now opening the Recent tab itself marks missed calls as seen and the fresh count goes straight to the badge.

## Changes
- `app/src/main/java/com/gmwapp/hima/activities/MainActivity.kt`
  - Added `fun setRecentMissedCount(count: Int)` next to `refreshRecentMissedCountBadge()`. Sets `recentMissedCount` (clamped to 0) and calls `updateRecentBadge()`. No second network round-trip.
- `app/src/main/java/com/gmwapp/hima/fragments/RecentFragment.kt`
  - `onResume()`: `loadMissedCallCount(seen = 0)` -> `loadMissedCallCount(seen = 1)`. Opening the Recent tab now marks unseen missed calls as seen on the backend.
  - `refresh()` (re-tap of Recent bottom-nav while on the tab): also calls `loadMissedCallCount(seen = 1)` so newly arrived misses clear too.
  - Observer of `missedCallCountLiveData`: removed the `currentSortType == "missed"` conditional refresh and instead always calls `MainActivity.setRecentMissedCount(freshCount)`. Chip count and badge stay in sync from a single response.
  - Missed-chip click handler unchanged — still fires `loadMissedCallCount(seen = 1)`.

## Untouched
- `MainActivity.loadRecentMissedCountBadge()` still uses `seen=0` (read-only) for the periodic Home-tab badge refresh — correct, it must not mutate state.
- Per-row "missed" indicator in `RecentCallsAdapter` — unchanged, list still shows past missed calls.
- Backend `seen=1` semantics — unchanged.
- Chat-tab unread badge in `loadChatUnreadCountBadge()` — unchanged.

## Resulting flow
1. Badge shows `38` on Home.
2. User opens Recent tab -> `onResume` fires `seen=1` -> backend marks as seen, returns `0` -> observer calls `setRecentMissedCount(0)` -> badge clears.
3. Re-tap Recent later -> `refresh()` fires `seen=1` -> any newly arrived misses clear too.
4. Misses arriving while user is on Home/Chat/Profile: `loadRecentMissedCountBadge()` (still `seen=0`) keeps the badge fresh until the user opens Recent.

## Verify
- Trigger missed calls so the badge shows e.g. `5`.
- Open Recent tab. Badge clears within a second of the `seen=1` response. Chip shows just `Missed`.
- Trigger 10 more missed calls -> badge shows `10` while on Home -> open Recent -> badge clears. No need to tap the Missed chip.
- Logcat (`missed_call_data`): look for `Calling missed_call_count API for userId=…, seen=1` followed by `parsed_count=0` then `ui_count=0`.
- Switch to Chat / Favourite / Profile and back to Recent -> `onResume` re-runs; badge stays at 0 unless new missed calls arrived.
- Re-tap Recent while already on it -> `refresh()` clears any newly arrived misses.
