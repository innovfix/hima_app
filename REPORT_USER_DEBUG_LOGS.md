# Report User API - Debug Logs Guide

## Overview
This document explains how to view and share the complete API response logs for the Report User feature with the backend team.

## How to View Logs

### In Android Studio:
1. Open **Logcat** (View → Tool Windows → Logcat)
2. Filter by tag: `ReportUserAPI` or `UserProfileDetail`
3. Try to submit a report
4. Copy the complete log output

### Filter Options:
- **Tag: `ReportUserAPI`** - Shows detailed API request/response logs
- **Tag: `UserProfileDetail`** - Shows UI-level logs

## What Information is Being Logged

### 1. Report Reasons API (`report_reasons`)

**Request Information:**
```
- Request URL
- User ID parameter
```

**Response Information:**
```
- HTTP Status Code (e.g., 200, 400, 500)
- Is Successful (true/false)
- Response Body (complete JSON)
- Response Message
- Response Headers
- Error Body (if request failed)
```

**Expected Response Format:**
```json
{
  "success": true,
  "message": "Report reasons retrieved successfully",
  "data": [
    {
      "id": 1,
      "reason": "Fake profile",
      "requires_text": 0
    },
    {
      "id": 2,
      "reason": "Not replying",
      "requires_text": 0
    }
  ]
}
```

### 2. Report User API (`report_user`)

**Request Information:**
```
- Request URL
- User ID
- Report User ID (the user being reported)
- Reason ID (from report reasons)
- Reason Text (optional additional text)
```

**Response Information:**
```
- HTTP Status Code
- Is Successful
- Response Body (complete JSON)
- All response fields (success, message, data, error, status)
- Response Headers
- Raw Response
- Error Body (if request failed)
```

**Expected Response Format:**
```json
{
  "success": true,
  "message": "User reported successfully"
}
```

## Issues Found and Resolved

### Issue 1: NULL Response Body (RESOLVED)
**Problem**: When submitting a report, the response body was NULL in successful callback.

**Root Cause**: The backend was returning HTTP 409 (Conflict) with error message in the error body:
```json
{
  "success": false,
  "message": "You have already reported this user recently",
  "data": null,
  "error_code": 3001
}
```

**Why it appeared NULL**: 
- Retrofit only populates `response.body()` for successful HTTP responses (2xx status codes)
- For error responses (4xx, 5xx), the data is in `response.errorBody()`
- The app was only checking `response.body()`, which is NULL for error responses

**Solution**: 
- Updated the app to check `response.isSuccessful`
- If not successful, parse `response.errorBody()` to extract the error message
- Display the backend error message to the user via Toast

**Backend is working correctly!** ✅
The backend is returning proper error responses with meaningful messages.

## What to Share with Backend Team

### 1. Complete Log Output
Copy everything from Logcat between:
```
===== SUBMITTING REPORT USER =====
...
======================================
```

### 2. Specific Information to Include:

#### Request Details:
- **Endpoint**: `POST /report_user`
- **Parameters sent**:
  - `user_id`: [from logs]
  - `report_user_id`: [from logs]
  - `reason_id`: [from logs]
  - `reason_text`: [from logs]

#### Response Details:
- **HTTP Status Code**: [from logs - e.g., 200, 404, 500]
- **Response Body**: [from logs - will show "null" if issue persists]
- **Error Body**: [from logs - if status code is not 2xx]
- **Response Headers**: [from logs]

### 3. Expected vs Actual

**What the app expects:**
```json
{
  "success": true,
  "message": "User reported successfully"
}
```

**What the app is receiving:**
```
null or empty response
```

## API Endpoints

Both endpoints use `@FormUrlEncoded` and `@POST`:

### 1. Get Report Reasons
```
POST /report_reasons
Parameters:
  - user_id: Int
```

### 2. Submit Report
```
POST /report_user
Parameters:
  - user_id: Int
  - report_user_id: Int
  - reason_id: Int
  - reason_text: String
```

## Response Model Structure

The app now accepts flexible response formats with these optional fields:

```kotlin
data class ReportUserResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val data: Any? = null,
    val error: String? = null,
    val status: Int? = null
)
```

## Testing Steps

1. Open the app and navigate to a female user's profile (as a female user)
2. Tap on "Report User"
3. Select a reason (e.g., "Fake profile", "Not replying", "Other")
4. If "Other" is selected, enter additional text
5. Tap "Submit"
6. Check Logcat for the complete logs
7. Copy and share the logs with backend team

## Common Issues and Solutions

### Issue 1: "Response body is NULL!"
**Cause**: Backend is not returning any JSON response
**Solution**: Backend needs to return a JSON response with at least `success` and `message` fields

### Issue 2: HTTP 404 or 500 errors
**Cause**: Endpoint doesn't exist or server error
**Solution**: Backend needs to check if the endpoint is implemented and working

### Issue 3: HTTP 200 but empty body
**Cause**: Backend returns success status but no JSON body
**Solution**: Backend needs to return a JSON response even on success

## Backend Requirements

Please refer to `REPORT_USER_BACKEND_REQUIREMENTS.md` for complete API specifications and database schema requirements.

## Contact

If you need any additional logging or information, please let the development team know.

---
Last Updated: February 9, 2026
