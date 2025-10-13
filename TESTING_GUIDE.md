# Testing Guide - Friend Request System

## How to Access & Test the Friend Request System

### Prerequisites
- Login as a **male user** (the feature is for male users viewing female profiles)
- Ensure you have female users in your database/API

---

## 🎯 Test Scenario 1: Access from Home Screen

### Steps:
1. **Launch the app**
2. **Login** with male user credentials
3. **Tap on "Home" tab** (first icon in bottom navigation)
4. **Scroll through the list** of female users
5. **Tap anywhere on any user card** (not just the call buttons)
   - The entire card background is clickable
6. ✅ **Profile Detail Screen opens** with:
   - Large profile image at top
   - User info (name, age)
   - Languages section
   - Interests section
   - About Me section
   - "Send Friend Request" button at bottom

### What to Test:
- ✅ Profile image loads correctly
- ✅ User information displays properly
- ✅ Languages show as chips
- ✅ Interests display in flexible grid
- ✅ About text is readable
- ✅ "Send Friend Request" button is visible
- ✅ Click "Send Friend Request" → Button disappears, status shows "Friend request sent"

---

## 🎯 Test Scenario 2: Access from Recent Screen

### Steps:
1. **Launch the app**
2. **Login** with male user credentials
3. **Tap on "Recent" tab** (second icon in bottom navigation)
4. You should see **users you've called before**
5. **Tap on the profile image** (circular image on the left)
   - The entire profile container (image area) is clickable
6. ✅ **Profile Detail Screen opens**

### What to Test:
- ✅ Same as Scenario 1
- ⚠️ Note: Recent users may have limited data (language, interests, about may be empty)
- ✅ Screen handles missing data gracefully

---

## 🎯 Test Scenario 3: Friend Request States

### State 1: Not Friends (Default)
```
UI Elements:
- ✅ "Send Friend Request" button visible (purple gradient)
- ❌ "Accept Friend Request" button hidden
- ❌ Call buttons hidden
- ❌ Status message hidden
```

**Test:** Click "Send Friend Request"
- ✅ Toast message appears: "Friend request sent to [username]"
- ✅ UI updates to "Request Sent" state

---

### State 2: Request Sent
```
UI Elements:
- ❌ "Send Friend Request" button hidden
- ❌ "Accept Friend Request" button hidden
- ❌ Call buttons hidden
- ✅ Status card visible: "Friend request sent" (with checkmark icon)
```

**Current Behavior:** This is a mock state. In production, this will be fetched from API.

---

### State 3: Request Received (To Test This)
To simulate this state, you need to modify the code temporarily:

**File:** `UserProfileDetailActivity.kt`
**Line:** ~40 (in onCreate after getUserDataFromIntent())

Add this line:
```kotlin
currentFriendStatus = FriendStatus.REQUEST_RECEIVED
```

```
UI Elements:
- ❌ "Send Friend Request" button hidden
- ✅ "Accept Friend Request" button visible (green gradient)
- ❌ Call buttons hidden
- ❌ Status message hidden
```

**Test:** Click "Accept Friend Request"
- ✅ Toast message: "You are now friends with [username]"
- ✅ UI updates to "Friends" state

---

### State 4: Friends
After accepting friend request (or simulate by changing code):

```
UI Elements:
- ❌ "Send Friend Request" button hidden
- ❌ "Accept Friend Request" button hidden
- ✅ Call buttons visible (Audio & Video)
- ✅ Status card visible: "You are friends" (with checkmark icon)
```

**Test:** Click Audio/Video Call buttons
- ✅ Navigates to call screen
- ✅ Only works if audio_status/video_status is enabled

---

## 🎯 Test Scenario 4: UI/UX Elements

### Scrolling Behavior:
1. **Open any profile**
2. **Scroll down slowly**
   - ✅ Profile image parallax effect works
   - ✅ Toolbar collapses smoothly
   - ✅ User name in toolbar appears when scrolled

### Back Navigation:
1. **Tap back arrow** (top left)
   - ✅ Returns to previous screen (Home or Recent)

### Missing Data Handling:
1. **Open profile with missing data**
   - ✅ Empty sections hide gracefully
   - ✅ No crashes or errors

---

## 🎯 Test Scenario 5: Different User Data

Test with different user profiles:

### User with All Data:
- Name: ✅
- Age: ✅
- Language: ✅
- Interests: ✅ Multiple interests displayed
- About: ✅ Full description

### User with Minimal Data:
- Name: ✅
- Age: ❌ (hides age text)
- Language: ❌ (hides language section)
- Interests: ❌ (hides interests section)
- About: ❌ (shows "No description available")

---

## 🐛 Known Limitations (Mock-up)

1. **Friend status is not persisted**
   - Closing and reopening the profile resets status
   - This will work when API is integrated

2. **No actual friend request sent**
   - Button clicks only update UI locally
   - Need backend API to send actual requests

3. **Limited data from Recent screen**
   - CallsListResponseData doesn't include all user fields
   - Will need to fetch full profile from API

4. **No friend list screen yet**
   - Can't view all friends
   - Will be implemented in next phase

---

## 📊 What to Check for Bugs

### Visual Issues:
- ✅ Images load without distortion
- ✅ Text is readable on all backgrounds
- ✅ Buttons are properly aligned
- ✅ Cards have proper elevation/shadows
- ✅ Colors match app theme

### Functional Issues:
- ✅ All buttons are clickable
- ✅ No crashes when clicking
- ✅ Back button works
- ✅ Toast messages appear
- ✅ UI updates correctly

### Performance:
- ✅ Smooth scrolling
- ✅ No lag when opening profile
- ✅ Fast image loading
- ✅ No memory leaks

---

## 🔄 Simulating Different States (For Testing)

To test different friend states without API, temporarily modify:

**File:** `app/src/main/java/com/gmwapp/hima/activities/UserProfileDetailActivity.kt`

**In `onCreate()` method, after line ~55, add:**

```kotlin
// Test different states:
currentFriendStatus = FriendStatus.NOT_FRIENDS      // Default
// currentFriendStatus = FriendStatus.REQUEST_SENT   // After sending request
// currentFriendStatus = FriendStatus.REQUEST_RECEIVED  // Incoming request
// currentFriendStatus = FriendStatus.FRIENDS        // Already friends
```

Uncomment the state you want to test, then rebuild and run the app.

---

## 📝 Testing Checklist

### Home Screen Navigation:
- [ ] Home tab shows female users
- [ ] User cards are clickable
- [ ] Profile detail opens correctly
- [ ] Data passes correctly from Home to Profile

### Recent Screen Navigation:
- [ ] Recent tab shows call history
- [ ] Profile images are clickable
- [ ] Profile detail opens correctly
- [ ] Data passes correctly from Recent to Profile

### Profile Detail UI:
- [ ] Profile image displays
- [ ] User info displays
- [ ] Languages section works
- [ ] Interests section works
- [ ] About section works
- [ ] Scroll behavior is smooth
- [ ] Collapsing toolbar works

### Friend Request Functionality:
- [ ] Send Friend Request button works
- [ ] Accept Friend Request button works (when simulated)
- [ ] Call buttons appear for friends
- [ ] Status messages display correctly
- [ ] Toast messages appear

### Edge Cases:
- [ ] Missing user data handled gracefully
- [ ] Empty strings don't break UI
- [ ] Null values don't cause crashes
- [ ] Back navigation works from all states

---

## 🚀 Ready for Production

When backend API is ready:
1. Replace mock friend status logic with API calls
2. Add proper error handling
3. Add loading indicators
4. Implement friend list screen
5. Add chat functionality for friends only

See `USER_PROFILE_FEATURE_IMPLEMENTATION.md` for API integration details.

