# User Profile Detail & Friend Request Feature - Mock-up Implementation

## Overview
This document describes the newly implemented User Profile Detail screen with friend request functionality for the HIMA app. This is a mock-up design ready for backend integration.

## Features Implemented

### 1. User Profile Detail Screen
A beautiful, modern profile screen that displays:
- **Large profile image** with parallax scrolling effect
- **User information**: Name, Age
- **Languages spoken** (displayed as chips)
- **Interests** (displayed in a flexible grid)
- **About Me** section (user description)
- **Collapsing toolbar** with smooth animations

### 2. Friend Request System (Mock-up)
The profile screen supports multiple friendship states:

#### States:
1. **Not Friends** - Shows "Send Friend Request" button
2. **Request Sent** - Shows status message "Friend request sent"
3. **Request Received** - Shows "Accept Friend Request" button
4. **Friends** - Shows audio/video call buttons

### 3. Navigation
- **From Home Screen**: Click on any female user card to open their profile
- **From Recent Screen**: Click on the profile image/container to open their profile

## Files Created/Modified

### New Files Created:

1. **Layout File**: `app/src/main/res/layout/activity_user_profile_detail.xml`
   - Modern, material design with collapsing toolbar
   - Sections for languages, interests, and about me
   - Dynamic action buttons based on friend status

2. **Activity File**: `app/src/main/java/com/gmwapp/hima/activities/UserProfileDetailActivity.kt`
   - Handles UI population from intent data
   - Manages friend request states
   - Controls visibility of buttons based on friendship status
   - Placeholder methods for API integration

3. **Data Models**: `app/src/main/java/com/gmwapp/hima/retrofit/responses/FriendRequestResponse.kt`
   - `FriendRequestResponse`: For sending/accepting friend requests
   - `FriendListResponse`: For fetching friend list
   - `FriendStatusResponse`: For checking friendship status

4. **Drawable Resources**:
   - `ic_back.xml` - Back arrow icon
   - `ic_user_add.xml` - Add friend icon
   - `ic_about.xml` - About section icon
   - `ic_interests.xml` - Interests section icon
   - `bottom_sheet_background.xml` - Background for action buttons

### Modified Files:

1. **FemaleUserAdapter.kt**
   - Added click listener on profile card
   - Opens UserProfileDetailActivity with user data

2. **RecentCallsAdapter.kt**
   - Added click listener on profile container
   - Opens UserProfileDetailActivity with user data

## UI/UX Features

### Design Elements:
- ✨ **Gradient overlays** for better readability
- 🎨 **Material Design cards** with elevation and rounded corners
- 📱 **Collapsing toolbar** with parallax scrolling
- 🎯 **Section headers** with icon badges
- 💫 **Smooth animations** and transitions
- 🔘 **Gradient buttons** matching app theme

### Color Scheme:
- Uses existing app colors and gradients
- Consistent with the current design language
- Accessible and visually appealing

## Usage

### Opening Profile Detail:
```kotlin
val intent = Intent(context, UserProfileDetailActivity::class.java).apply {
    putExtra(DConstants.USER_ID, userId)
    putExtra("USER_NAME", userName)
    putExtra("USER_IMAGE", userImage)
    putExtra("USER_LANGUAGE", userLanguage)
    putExtra("USER_INTERESTS", userInterests)
    putExtra("USER_ABOUT", userAbout)
    putExtra("USER_AGE", userAge)
    putExtra("AUDIO_STATUS", audioStatus)
    putExtra("VIDEO_STATUS", videoStatus)
}
startActivity(intent)
```

### Friend Status States:
```kotlin
enum class FriendStatus {
    NOT_FRIENDS,        // Show send request button
    REQUEST_SENT,       // Show status message
    REQUEST_RECEIVED,   // Show accept request button
    FRIENDS            // Show call buttons
}
```

## Backend Integration TODO

To complete this feature, you need to implement the following API endpoints:

### 1. Send Friend Request
```kotlin
POST /api/friend-request/send
Body: {
    "sender_id": int,
    "receiver_id": int
}
Response: FriendRequestResponse
```

### 2. Accept Friend Request
```kotlin
POST /api/friend-request/accept
Body: {
    "request_id": int,
    "user_id": int
}
Response: FriendRequestResponse
```

### 3. Check Friend Status
```kotlin
GET /api/friend-status/{user_id}/{friend_id}
Response: FriendStatusResponse
```

### 4. Get Friends List
```kotlin
GET /api/friends/{user_id}
Response: FriendListResponse
```

### 5. Reject Friend Request
```kotlin
POST /api/friend-request/reject
Body: {
    "request_id": int,
    "user_id": int
}
Response: FriendRequestResponse
```

## Integration Steps

### In UserProfileDetailActivity.kt:

1. **Replace mock friend status** with API call:
```kotlin
private fun checkFriendStatus() {
    // Call API to check friendship status
    // Update currentFriendStatus based on response
    // Call updateUIBasedOnFriendStatus()
}
```

2. **Implement sendFriendRequest()**:
```kotlin
private fun sendFriendRequest() {
    // API call to send friend request
    viewModel.sendFriendRequest(userId) { response ->
        if (response.success) {
            currentFriendStatus = FriendStatus.REQUEST_SENT
            updateUIBasedOnFriendStatus()
        }
    }
}
```

3. **Implement acceptFriendRequest()**:
```kotlin
private fun acceptFriendRequest() {
    // API call to accept friend request
    viewModel.acceptFriendRequest(requestId) { response ->
        if (response.success) {
            currentFriendStatus = FriendStatus.FRIENDS
            updateUIBasedOnFriendStatus()
        }
    }
}
```

## Chat Functionality (Future Implementation)

For chat functionality between friends:
1. Add chat button in the action buttons section
2. Check if users are friends before allowing chat
3. Integrate with existing ChatActivity
4. Store friend list locally for quick access

## Testing the Mock-up

Currently, the profile screen will:
- ✅ Display all user information passed via intent
- ✅ Show "Send Friend Request" button by default
- ✅ Update UI when buttons are clicked (mock state changes)
- ✅ Show appropriate buttons based on friend status
- ✅ Display toast messages for actions

## Screenshots Reference

The design follows these patterns:
- **Home Screen Cards**: Similar to existing FemaleUserAdapter layout
- **Profile Header**: Gradient overlay with user info (like Instagram/Tinder)
- **Content Sections**: Clean card-based layout with icons
- **Action Buttons**: Bottom sheet style with gradient backgrounds

## Notes

- Age data is not available in `FemaleUsersResponseData`, so it shows 0 or hides the age text
- Language and interests data might be empty in recent calls (CallsListResponseData)
- The profile detail will show available data gracefully
- All UI elements are responsive and handle missing data

## Future Enhancements

1. **Friend List Screen**: Show all friends with search/filter
2. **Chat Integration**: Enable chat between friends only
3. **Block/Unblock**: Add option to block users
4. **Report User**: Add reporting functionality
5. **Online Status**: Show real-time online/offline status
6. **Last Seen**: Display last seen timestamp
7. **Mutual Friends**: Show common friends
8. **Friend Suggestions**: Recommend potential friends

## Support

For any questions or issues with this implementation, contact the development team.

---
**Created**: October 2025  
**Version**: 1.0 (Mock-up)  
**Status**: Ready for Backend Integration

