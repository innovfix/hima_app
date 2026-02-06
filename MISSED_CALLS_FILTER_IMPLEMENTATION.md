# Missed Calls Filter Implementation

## Overview
Added a new "Missed" filter option to the recent calls screen, allowing users to filter and view only missed calls.

---

## Changes Made

### 1. menu_recent_sort.xml (MODIFIED)
**Location:** `app/src/main/res/menu/menu_recent_sort.xml`

**Changes:**
- Added new menu item: `action_sort_missed`
- Title: "Missed"

**Menu Options (after change):**
1. Recent
2. Talk Time
3. Name (A-Z)
4. **Missed** (NEW)

---

### 2. RecentFragment.kt (MODIFIED)
**Location:** `app/src/main/java/com/gmwapp/hima/fragments/RecentFragment.kt`

**Changes:**
- Updated `showSortMenu()` method
- Added handler for `R.id.action_sort_missed`
- Sets `currentSortType = "missed"`
- Updates label to "Missed"
- Calls API with type = "missed"

---

## How It Works

### User Flow:
1. User taps on **Sort button** (filter icon in header)
2. Popup menu appears with 4 options
3. User selects **"Missed"**
4. Label updates to show "Missed"
5. API is called with `sortType = "missed"`
6. RecyclerView shows only missed calls

### API Call:
```kotlin
recentViewModel.getCallsList(
    userId = userData.id,
    gender = userData.gender,
    limit = limit,
    offset = offset,
    sortType = "missed",  // ← Passed as type parameter
    search = searchQuery
)
```

The API will receive `type = "missed"` and should return only calls that were missed (not answered).

---

## UI Updates

### Filter Button Label Changes:
- **Recent** → Shows "Recent"
- **Talk Time** → Shows "Talk Time"
- **Name (A-Z)** → Shows "A-Z"
- **Missed** → Shows "Missed" (NEW)

### Empty State:
If no missed calls found, the empty state message appears:
- "No data found"
- "Your call history will appear here"

---

## Features

✅ **New filter option** - "Missed" added to sort menu
✅ **Passes type parameter** - API receives `type = "missed"`
✅ **Updates UI label** - Shows "Missed" when selected
✅ **Resets pagination** - Starts from offset 0
✅ **Works with search** - Can combine missed filter with search
✅ **Consistent behavior** - Works exactly like other filters

---

## Backend Requirements

The backend API should handle `type = "missed"` parameter:

**Endpoint:** (Existing calls list API)

**When type = "missed":**
- Return only calls where the call was **not answered**
- This could mean:
  - Call status = "missed"
  - Call duration = 0
  - Call was rejected/not picked up
  - (Depends on your backend logic)

**Example filtering logic (backend should implement):**
```php
if ($type === 'missed') {
    $query->where('call_status', 'missed')
          ->orWhere('duration', 0);
}
```

---

## Testing

### To Test:
1. Open app and go to "Recent" calls tab
2. Tap the **filter button** (with icon next to subtitle)
3. Verify menu shows 4 options including "Missed"
4. Select **"Missed"**
5. Verify label changes to "Missed"
6. Verify API is called with `type = "missed"`
7. Verify only missed calls are shown (once backend implements filtering)

### Expected Behavior:
- Menu shows "Missed" option
- Selecting it updates the label
- API receives `sortType = "missed"`
- RecyclerView filters to show only missed calls
- Search works with missed filter

---

## Files Changed

**Modified (2 files):**
1. `menu_recent_sort.xml` - Added "Missed" menu item
2. `RecentFragment.kt` - Added handler for missed filter

**Status:**
- ✅ No linter errors
- ✅ Build ready
- ✅ App-side implementation complete
- ⏳ Backend needs to handle `type = "missed"` parameter

---

## Implementation Date
February 5, 2026

## Status
✅ **App-Side Complete**
⏳ **Waiting for Backend to Filter by type = "missed"**
