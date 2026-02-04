# Backend API Changes Required for Search Functionality

## Overview
Need to add search functionality to 3 friend-related APIs. Currently only `my_chat` API supports search parameter. The other 3 APIs need the same capability.

---

## Changes Required

### 1. **friend_list API** (POST `/friend_list`)

**Current behavior:**
- Accepts: `sender_id` (required)
- Returns: List of all friends (status = 1)

**Required changes:**
- Add optional parameter: `search` (string, nullable)
- When `search` is provided, filter results by:
  - Friend's name (case-insensitive partial match)
  - Friend's language (case-insensitive partial match)
- Return filtered results with same response structure

**Example Request:**
```
POST /friend_list
{
  "sender_id": 123,
  "search": "Priya"
}
```

---

### 2. **my_requests API** (POST `/my_requests`)

**Current behavior:**
- Accepts: `sender_id` (required)
- Returns: List of friend requests sent by the user (status = 0)

**Required changes:**
- Add optional parameter: `search` (string, nullable)
- When `search` is provided, filter results by:
  - Receiver's name (case-insensitive partial match)
  - Receiver's language (case-insensitive partial match)
- Return filtered results with same response structure

**Example Request:**
```
POST /my_requests
{
  "sender_id": 123,
  "search": "Anjali"
}
```

---

### 3. **received_requests API** (POST `/received_requests`)

**Current behavior:**
- Accepts: `receiver_id` (required)
- Returns: List of friend requests received by the user (status = 0)

**Required changes:**
- Add optional parameter: `search` (string, nullable)
- When `search` is provided, filter results by:
  - Sender's name (case-insensitive partial match)
  - Sender's language (case-insensitive partial match)
- Return filtered results with same response structure

**Example Request:**
```
POST /received_requests
{
  "receiver_id": 123,
  "search": "Riya"
}
```

---

## Reference Implementation

The `my_chat` API (POST `/my_chat`) already implements search correctly. Backend team can follow the same pattern:
- Accepts optional `search` parameter
- Filters results when search is provided
- Returns empty/reduced list when no matches found
- Returns all results when search is null/empty

**Example from my_chat:**
```php
$search = $request->input('search');

// ... query building ...

if (!empty($search)) {
    // Apply search filter
    $searchLower = strtolower($search);
    $results = $results->filter(function($item) use ($searchLower) {
        $name = strtolower($item['name'] ?? '');
        $language = strtolower($item['language'] ?? '');
        return strpos($name, $searchLower) !== false || 
               strpos($language, $searchLower) !== false;
    })->values();
}
```

---

## Important Notes

1. **Search should be optional** - If search parameter is not provided or is empty, return all results (current behavior)
2. **Case-insensitive matching** - Search should work regardless of letter case
3. **Partial matching** - Should match if search term appears anywhere in the name or language fields
4. **No breaking changes** - Existing API calls without search parameter must continue to work as before
5. **Response structure** - Keep the exact same response JSON structure, just filtered data

---

## Testing Scenarios

For each API, test:
- ✅ Without search parameter → returns all results
- ✅ With empty search string → returns all results  
- ✅ With valid search term → returns filtered results
- ✅ With search term matching name → returns matches
- ✅ With search term matching language → returns matches
- ✅ With search term with no matches → returns empty list with success: false, message: "No data found"

---

## Implementation Checklist

### friend_list API
- [ ] Add `search` parameter to API
- [ ] Implement name filtering (case-insensitive, partial match)
- [ ] Implement language filtering (case-insensitive, partial match)
- [ ] Test with various search queries
- [ ] Verify backward compatibility (works without search param)

### my_requests API
- [ ] Add `search` parameter to API
- [ ] Implement receiver name filtering (case-insensitive, partial match)
- [ ] Implement receiver language filtering (case-insensitive, partial match)
- [ ] Test with various search queries
- [ ] Verify backward compatibility (works without search param)

### received_requests API
- [ ] Add `search` parameter to API
- [ ] Implement sender name filtering (case-insensitive, partial match)
- [ ] Implement sender language filtering (case-insensitive, partial match)
- [ ] Test with various search queries
- [ ] Verify backward compatibility (works without search param)

---

## Timeline

**Android Implementation:** ✅ Complete (February 4, 2026)
**Backend Implementation:** ⏳ Pending
**Estimated Effort:** 15-20 minutes per API (copy pattern from my_chat)

---

## Contact

For questions or clarifications, please contact the Android team.
