# Block/Unblock User Feature - Implementation Guide

**Date**: February 9, 2026  
**Status**: ✅ **IMPLEMENTED**

---

## Overview

Added dynamic block/unblock functionality to the User Profile Detail Activity. Female users can now:
1. Check if they've already blocked a user
2. Block or unblock users dynamically
3. See the correct button text ("Block user" or "Unblock user") based on current status

---

## How It Works

### 1. **When Female User Views a Profile**
   - App calls `check_block_status` API to check if the viewed user is already blocked
   - Based on the response, shows either:
     - "Block user" (if not blocked)
     - "Unblock user" (if already blocked)

### 2. **When Blocking a User**
   - User taps "Block user"
   - Confirmation dialog shown
   - Calls `blocked_user` API (existing endpoint)
   - On success, button text changes to "Unblock user"

### 3. **When Unblocking a User**
   - User taps "Unblock user"
   - Confirmation dialog shown
   - Calls `unblock_user` API (new endpoint)
   - On success, button text changes to "Block user"

---

## API Endpoints Implemented

### 1. Check Block Status API

**Endpoint**: `POST /api/auth/check_block_status`

**Purpose**: Check if a user has already blocked another user

**Parameters**:
- `user_id` (Int): The current user's ID
- `call_user_id` (Int): The ID of the user being checked

**Expected Response Format**:

#### Option 1 - With nested data:
```json
{
  "success": true,
  "message": "Block status retrieved successfully",
  "data": {
    "is_blocked": true,
    "blocked_status": 2,
    "block_id": 123,
    "user_id": 456717,
    "call_user_id": 456709,
    "created_at": "2026-02-09T12:00:00Z",
    "updated_at": "2026-02-09T12:00:00Z"
  }
}
```

#### Option 2 - Flat structure:
```json
{
  "success": true,
  "message": "Block status retrieved successfully",
  "is_blocked": true,
  "blocked_status": 2
}
```

#### Option 3 - Not blocked:
```json
{
  "success": true,
  "message": "User is not blocked",
  "is_blocked": false,
  "blocked_status": 0
}
```

**Field Definitions**:
- `is_blocked` (Boolean): `true` if blocked, `false` if not
- `blocked_status` (Int): `0` = unblocked, `2` = blocked
- `block_id` (Int, optional): The ID of the block record
- `user_id` (Int): The user who did the blocking
- `call_user_id` (Int): The user who was blocked

---

### 2. Unblock User API

**Endpoint**: `POST /api/auth/unblock_user`

**Purpose**: Unblock a previously blocked user

**Parameters**:
- `user_id` (Int): The current user's ID
- `call_user_id` (Int): The ID of the user to unblock

**Expected Response Format**:
```json
{
  "success": true,
  "message": "User unblocked successfully",
  "data": {
    "user_id": "456717",
    "call_user_id": "456709",
    "blocked": "0",
    "updated_at": "2026-02-09T12:00:00Z"
  }
}
```

**Success Response**:
- HTTP Status: 200
- `success`: true
- `message`: User-friendly message
- `data.blocked`: Should be "0" after unblocking

---

### 3. Block User API (Existing)

**Endpoint**: `POST /api/auth/blocked_user`

**Parameters**:
- `user_id` (Int): The current user's ID
- `call_user_id` (Int): The ID of the user to block
- `blocked` (Int): `1` to block, `0` to unblock (but we use unblock_user API for unblocking)

**Response**: Same as unblock_user

---

## App Implementation Details

### Files Created:
1. **`CheckBlockStatusResponse.kt`** - Response model for check_block_status API
   - Supports multiple response formats (flexible)
   - Handles both flat and nested data structures

### Files Modified:

2. **`ApiManager.kt`**
   - Added `checkBlockStatus()` method
   - Added `unblockUser()` method
   - Added interface methods for both endpoints

3. **`BlockUserRepository.kt`**
   - Added `checkBlockStatus()` method
   - Added `unblockUser()` method

4. **`BlockUserViewModel.kt`**
   - Added LiveData for check block status
   - Added LiveData for unblock user
   - Enhanced error handling with JSON parsing
   - Added comprehensive logging

5. **`UserProfileDetailActivity.kt`**
   - Added `isUserBlocked` state tracking
   - Calls `checkBlockStatus()` on profile load (female users only)
   - Updates UI based on block status
   - Handles both block and unblock actions
   - Shows appropriate confirmation dialogs

6. **`activity_user_profile_detail.xml`**
   - Added ID to "Block user" TextView for dynamic updates

---

## User Flow

### Scenario 1: User Not Blocked
```
1. Female user opens profile
2. App calls check_block_status
3. Response: is_blocked = false
4. UI shows "Block user"
5. User taps "Block user" → confirmation → blocked
6. UI updates to "Unblock user"
```

### Scenario 2: User Already Blocked
```
1. Female user opens profile
2. App calls check_block_status
3. Response: is_blocked = true
4. UI shows "Unblock user"
5. User taps "Unblock user" → confirmation → unblocked
6. UI updates to "Block user"
```

---

## Backend Requirements

### Database Schema

If not already existing, you may need a `blocked_users` table:

```sql
CREATE TABLE blocked_users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,           -- User who blocked
    call_user_id INT NOT NULL,      -- User who was blocked
    blocked INT DEFAULT 0,          -- 0=unblocked, 2=blocked
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_block (user_id, call_user_id)
);
```

### API Implementation Guide

#### 1. Check Block Status (`check_block_status`)

```php
// Example implementation
public function checkBlockStatus(Request $request) {
    $userId = $request->user_id;
    $callUserId = $request->call_user_id;
    
    $blockRecord = BlockedUser::where('user_id', $userId)
        ->where('call_user_id', $callUserId)
        ->first();
    
    if ($blockRecord && $blockRecord->blocked == 2) {
        return response()->json([
            'success' => true,
            'message' => 'Block status retrieved',
            'data' => [
                'is_blocked' => true,
                'blocked_status' => 2,
                'block_id' => $blockRecord->id,
                'user_id' => $blockRecord->user_id,
                'call_user_id' => $blockRecord->call_user_id,
                'created_at' => $blockRecord->created_at,
                'updated_at' => $blockRecord->updated_at
            ]
        ]);
    }
    
    return response()->json([
        'success' => true,
        'message' => 'User is not blocked',
        'data' => [
            'is_blocked' => false,
            'blocked_status' => 0
        ]
    ]);
}
```

#### 2. Unblock User (`unblock_user`)

```php
// Example implementation
public function unblockUser(Request $request) {
    $userId = $request->user_id;
    $callUserId = $request->call_user_id;
    
    $blockRecord = BlockedUser::where('user_id', $userId)
        ->where('call_user_id', $callUserId)
        ->first();
    
    if (!$blockRecord) {
        return response()->json([
            'success' => false,
            'message' => 'No block record found'
        ], 404);
    }
    
    $blockRecord->blocked = 0;
    $blockRecord->save();
    
    return response()->json([
        'success' => true,
        'message' => 'User unblocked successfully',
        'data' => [
            'user_id' => (string)$blockRecord->user_id,
            'call_user_id' => (string)$blockRecord->call_user_id,
            'blocked' => '0',
            'updated_at' => $blockRecord->updated_at
        ]
    ]);
}
```

---

## Testing Checklist

### For Backend Team:
- [ ] Implement `check_block_status` endpoint
- [ ] Implement `unblock_user` endpoint
- [ ] Test with blocked user (should return is_blocked=true, blocked_status=2)
- [ ] Test with non-blocked user (should return is_blocked=false, blocked_status=0)
- [ ] Test unblock action (should update blocked to 0)
- [ ] Verify proper error handling (user not found, etc.)

### For App Team:
- [x] Check block status on profile load
- [x] Show correct button text based on status
- [x] Block user functionality
- [x] Unblock user functionality
- [x] Confirmation dialogs
- [x] Error handling
- [x] Female user restriction
- [ ] Test with actual backend APIs

---

## Logging

The app includes detailed logging with the tag `BlockUserAPI`:

**Check Block Status:**
```
🔍 Checking block status - userId: X, callUserId: Y
===== CHECK BLOCK STATUS RESPONSE =====
HTTP Status: 200
Response Body: {...}
======================================
```

**Block User:**
```
✅ Block user success
```

**Unblock User:**
```
🔓 Unblocking user - userId: X, callUserId: Y
===== UNBLOCK USER RESPONSE =====
✅ Unblock user success
======================================
```

---

## Error Handling

The app handles various error scenarios:
- Network errors → "No internet connection"
- API errors → Shows backend error message
- Missing user data → "Unable to check block status"
- HTTP 4xx/5xx → Parses and shows error message from backend

---

## Notes

1. **Female Users Only**: Block status check only runs for female users
2. **Flexible Response Parsing**: App supports multiple response formats for compatibility
3. **Real-time UI Updates**: Button text updates immediately after blocking/unblocking
4. **Comprehensive Logging**: Easy debugging with detailed logs

---

## Support

For questions or issues:
- Check Logcat with filter: `BlockUserAPI`
- Review the logs for request/response details
- Share complete logs with backend team if needed

---

**Last Updated**: February 9, 2026
