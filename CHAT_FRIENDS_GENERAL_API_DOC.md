# Chat list — Friends vs General (creator side)

Two endpoints split the flat `POST /api/auth/my_chat` list for the female creator **Chat** bottom-nav tab (Friends / General sub-tabs).

> **Status:** Backend contract CONFIRMED. Source of truth for this feature.

## Endpoints

| Sub-tab | Endpoint | Auth |
|---|---|---|
| Friends (default) | `POST /api/auth/my_chat/friends` | Bearer JWT (auth group) |
| General | `POST /api/auth/my_chat/general` | Bearer JWT (auth group) |
| Messages (unchanged) | `POST /api/auth/my_chat` | Bearer JWT |

Unauthenticated → HTTP `401` with `success: false`.

## Request

Same payload style as `my_chat`. Accepts **form body** or **JSON**.

| Field | Type | Required | Notes |
|---|---|---|---|
| `user_id` | int / numeric string | yes | Creator / current user id |
| `search` | string | no | Case-insensitive substring on partner name |
| `limit` | int | no | Page size (app sends `100`) |
| `offset` | int | no | Default `0` when paginating |

**Pagination rule:** pagination only runs if either `limit` or `offset` is sent. If neither is sent, all rows are returned (matches `my_chat`).

### Validation errors (HTTP 200, `success: false`)

| Condition | Message |
|---|---|
| Missing `user_id` | `"user_id is required."` |
| Non-numeric `user_id` | `"user_id must be a numeric value."` |
| `user_id === 0` | `"Invalid user_id. Zero is not allowed."` |

## Success response (both endpoints)

Same envelope as `my_chat`:

```json
{
  "success": true,
  "message": "Chat list retrieved successfully.",
  "data": {
    "user_id": 12345,
    "chats": [ /* ChatItem[] */ ],
    "total_chats": 2
  }
}
```

### `ChatItem`

| Field | Type | Notes |
|---|---|---|
| `chat_id` | string | Real DM: canonical `"minId_maxId"`. Friends-only: `"0"` when no thread yet. |
| `user` | object | Same as `my_chat` partner object (includes `audio_status`, `video_status`, `coin_per_min_audio`, `coin_per_min_video`, `language`). |
| `last_message` | object \| null | `null` when `chat_id === "0"`. |
| `unread_count` | int | `0` when no thread. |
| `i_have_blocked_this_user` | bool | Block state; same semantics as `my_chat`. |

## `POST /api/auth/my_chat/friends`

**Purpose:** All accepted friends (`friend_requests.status = 1`), merged with chat metadata when a thread exists.

**Exclusions:** Friends you have **chat-blocked** (`chat_blocked_users.user_id = you` AND `blocked_user_id = friend` AND `blocked = 1`) do NOT appear.

**Sort:**
1. Rows with `last_message` — by last message time DESC.
2. Rows without a thread (`chat_id = "0"`, `last_message = null`) — by `user.name` ASC, case-insensitive.

**Empty friends:** `success: true`, `chats: []`, `total_chats: 0`.

### Example

```http
POST /api/auth/my_chat/friends HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/x-www-form-urlencoded

user_id=72&limit=100&offset=0
```

Friend with an active thread:

```json
{
  "chat_id": "72_182632",
  "user": { "id": 182632, "name": "yakini", "image": "...", "audio_status": 1, "video_status": 0, "coin_per_min_audio": 10, "coin_per_min_video": 60 },
  "last_message": { "message": "hi", "timestamp": "2026-04-20 12:00:00", "message_type": "text" },
  "unread_count": 1,
  "i_have_blocked_this_user": false
}
```

Friend with no DM yet:

```json
{
  "chat_id": "0",
  "user": { "id": 155069, "name": "Anu", "image": "...", "audio_status": 1, "video_status": 1, "coin_per_min_audio": 10, "coin_per_min_video": 60 },
  "last_message": null,
  "unread_count": 0,
  "i_have_blocked_this_user": false
}
```

## `POST /api/auth/my_chat/general`

**Purpose:** Only chat threads where the other participant is **not** an accepted friend (general inbox).

**Sort:** `last_message` time DESC (same as `my_chat`).

**Blocked:** Blocked users are NOT removed (same behavior as `my_chat`); `i_have_blocked_this_user` reflects block state.

**Empty:** `success: true`, `chats: []`, `total_chats: 0`.

### Example

```http
POST /api/auth/my_chat/general HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{ "user_id": 72, "limit": 100, "offset": 0 }
```

## Server error envelope

Unexpected exception (HTTP `200` in line with existing `my_chat` error style):

```json
{ "success": false, "message": "Error retrieving chat list: <details>" }
```

## Client mapping

| UI | Endpoint |
|---|---|
| Creator Chat tab — Friends sub-tab | `POST /api/auth/my_chat/friends` |
| Creator Chat tab — General sub-tab | `POST /api/auth/my_chat/general` |
| `ChatListActivity` "Messages" (unchanged) | `POST /api/auth/my_chat` |

**Opening a chat with no thread yet:** use `chat_id = "0"` + `user.id` as the peer. First send creates the real `minId_maxId` thread (existing app behavior in `ChatActivityInHouse`).

## Client Retrofit hookup (already done)

See [app/src/main/java/com/gmwapp/hima/retrofit/ApiManager.kt](app/src/main/java/com/gmwapp/hima/retrofit/ApiManager.kt):

- Interface: `getMyChatFriends(...)` @ `@POST("my_chat/friends")`
- Interface: `getMyChatGeneral(...)` @ `@POST("my_chat/general")`
- Manager wrappers: `ApiManager.getMyChatFriends(...)`, `ApiManager.getMyChatGeneral(...)`

Relative paths (`my_chat/friends`, `my_chat/general`) resolve to `/api/auth/my_chat/friends` and `/api/auth/my_chat/general` because the Retrofit base URL already includes the `/api/auth/` prefix (same as existing `my_chat`).

Deserialization reuses `MyChatResponse` / `MyChatData` / `ChatItem` / `ChatUser` / `LastMessage`. Extra JSON keys like `i_have_blocked_this_user` are ignored by Gson (not modeled yet — see follow-ups below).

## Follow-ups / optional later

1. Add `i_have_blocked_this_user: Boolean?` to `ChatItem` if the client needs to render a block indicator in the list (currently blocking is handled inside `ChatActivityInHouse`).
2. Paginate with `limit` / `offset` once lists exceed 100 rows.
3. Surface the three validation-error messages as a user-facing toast instead of treating them as "empty list".

## QA checklist

1. `/my_chat/friends` with 5 accepted friends (2 with DMs, 3 without) → exactly 5 rows; 2 first, then 3 sorted alphabetically; the 3 have `chat_id = "0"` and `last_message = null`.
2. Chat-block a friend → that friend disappears from `/my_chat/friends` (still in `friend_list` API).
3. `/my_chat/general` never returns a `user.id` that appears in the caller's accepted friend list.
4. `search` filters both endpoints by partner name (case-insensitive substring).
5. `/my_chat` behavior is unchanged — `ChatListActivity` Chat sub-tab and Recent's unread badge still work.
6. Passing `user_id=0` or a non-numeric value returns the documented validation errors.
7. Tapping a `chat_id = "0"` row opens `ChatActivityInHouse` with the right peer and first send creates a real `minId_maxId` thread.
