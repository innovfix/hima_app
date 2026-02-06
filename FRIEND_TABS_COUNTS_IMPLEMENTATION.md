# Friend Tabs Counts API Integration

## Overview
Integrated the `friend_tabs_counts` API to display counts on each tab (Chat, Friends, My Requests, Received) in **both** activities:
- **FriendsListActivity** - Main friends screen
- **ChatListActivity** - Chat list screen

Tab counts **automatically refresh** when user accepts or rejects friend requests.

---

## API Details

**Endpoint:** `POST /api/auth/friend_tabs_counts`

**Request:**
```json
{
  "user_id": 123456
}
```

**Response:**
```json
{
  "success": true,
  "message": "Friend tabs counts retrieved successfully",
  "data": {
    "user_id": 123456,
    "chats_count": 15,
    "friends_count": 23,
    "my_requests_count": 3,
    "received_requests_count": 5
  }
}
```

---

## Files Modified/Created

### 1. FriendTabsCountsResponse.kt (NEW)
**Location:** `app/src/main/java/com/gmwapp/hima/retrofit/responses/FriendTabsCountsResponse.kt`

Response data classes:
- `FriendTabsCountsResponse` - Main response wrapper
- `FriendTabsCountsData` - Contains the 4 count fields

### 2. ApiManager.kt (MODIFIED)
**Location:** `app/src/main/java/com/gmwapp/hima/retrofit/ApiManager.kt`

**Changes:**
- Added import: `FriendTabsCountsResponse`
- Added API interface method: `getFriendTabsCounts()`
- Added wrapper method with network check

### 3. FriendsListActivity.kt (MODIFIED)
**Location:** `app/src/main/java/com/gmwapp/hima/activities/FriendsListActivity.kt`

**Changes:**
- Injected `ApiManager`
- Added imports for API callbacks and responses
- Added `loadTabCounts()` method - calls API on screen open
- Added `updateTabBadges()` method - updates tab text with counts
- Added **`refreshTabCounts()`** public method - called from fragments to refresh
- Called `loadTabCounts()` in `onCreate()`

### 4. ChatListActivity.kt (MODIFIED)
**Location:** `app/src/main/java/com/gmwapp/hima/activities/ChatListActivity.kt`

**Changes:**
- Added imports for API callbacks and responses
- Added `loadTabCounts()` method - calls API on screen open
- Added `updateTabBadges()` method - updates tab text with counts
- Added **`refreshTabCounts()`** public method - called from fragments to refresh
- Called `loadTabCounts()` in `onCreate()`

### 5. FriendsTabFragment.kt (MODIFIED)
**Location:** `app/src/main/java/com/gmwapp/hima/fragments/FriendsTabFragment.kt`

**Changes:**
- Added `refreshParentTabCounts()` private method
- Updated `sendFriendRequestLiveData` observer to call `refreshParentTabCounts()` after successful accept/reject
- Automatically refreshes parent activity's tab counts when request is processed

---

## How It Works

### Initial Load Flow:
1. **Screen opens** → `onCreate()` called
2. **Setup complete** → `loadTabCounts()` triggered
3. **Get user ID** → From BaseApplication preferences (or myUserId property)
4. **Call API** → `apiManager.getFriendTabsCounts(userId, callback)`
5. **Handle response** → Extract counts from response data
6. **Update UI** → Call `updateTabBadges()` with counts
7. **Tabs updated** → Display counts in parentheses

### Auto-Refresh Flow (Accept/Reject):
1. **User accepts/rejects** friend request
2. **API call succeeds** → Observer in FriendsTabFragment triggered
3. **Call `refreshParentTabCounts()`** → Detects parent activity type
4. **Call parent's `refreshTabCounts()`** → Triggers new API call
5. **Tab counts updated** → Shows new values immediately

### UI Display:
- **If count > 0:** Shows "Tab Name (count)" - e.g., "Chat (15)"
- **If count = 0:** Shows "Tab Name" - e.g., "Chat"

---

## Example Tab Display

**Before:**
```
[Chat] [Friends] [My Requests] [Received]
```

**After (with counts):**
```
[Chat (15)] [Friends (23)] [My Requests (3)] [Received (5)]
```

**After accepting a request:**
```
[Chat (15)] [Friends (24)] [My Requests (3)] [Received (4)]
```
*Friends count increased, Received count decreased*

---

## Features

✅ **Immediate loading** - API called when screen opens
✅ **Smart display** - Only shows count if > 0
✅ **Error handling** - Graceful handling of network errors
✅ **Clean UI** - Counts in parentheses
✅ **Logging** - All operations logged for debugging
✅ **Two activities** - Works in both FriendsListActivity and ChatListActivity
✅ **Auto-refresh** - Counts refresh automatically after accept/reject actions
✅ **Smart detection** - Fragment detects parent activity type automatically

---

## Refresh Triggers

Tab counts are automatically refreshed when:
1. ✅ **Screen opens** - Initial load
2. ✅ **Accept friend request** - Counts update immediately
3. ✅ **Reject friend request** - Counts update immediately
4. ✅ **Cancel sent request** - Counts update immediately

---

## Testing

### To Test Initial Load:
1. Open "My Friends" screen (FriendsListActivity)
   - Check tab labels for counts
2. Open "Chat List" screen (ChatListActivity)
   - Check tab labels for counts
3. Check logcat for: `📊 Loading tab counts...` → `✅ Counts loaded...`

### To Test Auto-Refresh:
1. Go to "Received" tab
2. Accept a friend request
3. Watch tab counts update:
   - "Received" count should decrease by 1
   - "Friends" count should increase by 1
4. Check logcat for: `🔄 Refreshing tab counts...`

### Expected:
- Tabs show counts immediately in both screens
- Counts update dynamically after accept/reject
- Works even if API fails (tabs show without counts)

---

## Implementation Status

✅ **Complete and Ready for Testing**

**Files Changed:**
- 1 new file created (FriendTabsCountsResponse.kt)
- 4 files modified:
  - ApiManager.kt
  - FriendsListActivity.kt
  - ChatListActivity.kt
  - FriendsTabFragment.kt (NEW - added auto-refresh)
- 0 linter errors

**Date:** February 5, 2026
