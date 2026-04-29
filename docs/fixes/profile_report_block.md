# Profile Report + Block (User Profile Detail)

**Date:** 2026-04-28

## What was fixed
Report user / Block user cards were hidden for male users on `UserProfileDetailActivity`, even though the layout, dialogs, ViewModels, and endpoints were all wired up. Exposed them and unblocked the block-status check so the label flips correctly.

## Changes
- `app/src/main/res/layout/activity_user_profile_detail.xml`
  - Moved `cv_report_block_section` (Report user + Block user cards) from below the action buttons to right after `cv_online_notify_section`, so it stays near the top and visible without scrolling.
- `app/src/main/java/com/gmwapp/hima/activities/UserProfileDetailActivity.kt`
  - `populateUserData()`: replaced female-only visibility check with `shouldShowReportBlockSection()` so any gender viewing another user's profile sees Report/Block.
  - `checkBlockStatus()`: dropped the female-only guard so male users get the correct `Block user` / `Unblock user` label after `check_block_status`.
  - Added `shouldShowReportBlockSection()` helper -> returns false only when `userId <= 0`, current user id is missing, or `userId == currentUserId` (self-profile).

## API connectivity (unchanged)
All endpoints were already wired through existing ViewModels; no new networking code:

| Action | Endpoint | Caller |
|---|---|---|
| Load reasons | `POST report_reasons` | `loadReportReasons()` -> `reportUserViewModel.getReportReasons()` |
| Submit report | `POST report_user` | `submitReport()` -> `reportUserViewModel.reportUser()` |
| Block | `POST blocked_user` (blocked=1) | `blockUser()` -> `blockUserViewModel.blockUser()` |
| Check block | `POST check_block_status` | `checkBlockStatus()` (onResume) -> `blockUserViewModel.checkBlockStatus()` |
| Unblock | `POST unblock_user` (+ `blocked_user` blocked=0 VM fallback) | `unblockUser()` -> `blockUserViewModel.unblockUser()` |

Observers in `setupObservers()` toast on success/error, update `isUserBlocked`, and call `updateBlockButtonUI()` to flip the label.

## Untouched
- Existing dialogs: `dialog_report_user.xml`, `dialog_block_user_confirmation.xml`, `dialog_unblock_user_confirmation.xml`.
- Notify-online card (`cv_online_notify_section`) visibility logic — still male-only.
- `loadReportReasons()` — already gender-agnostic.

## Verify
- Open creator profile as male user: Notify, Report user, and Block user are visible.
- Tap Report -> reasons load -> submit; toast on success.
- Tap Block -> confirmation -> success toast; label changes to `Unblock user`.
- Reopen the same profile: `check_block_status` runs and label stays `Unblock user`.
- Tap Unblock -> confirmation -> success; label returns to `Block user`.
- Open own profile via this activity (if any route exists): Report/Block stays hidden.
