# IPL Rooms - API Documentation

**Base URL:** `development = https://demolivedb.himaapp.in/api/auth`, `production = https://himaapp.in/api/auth`
**Auth:** All endpoints require JWT Bearer token in `Authorization` header
**Content-Type:** `application/x-www-form-urlencoded`
**Method:** All endpoints use `POST`

---

## Coin & Monetization Rules

| Rule | Value |
|------|-------|
| Min coins to join room | **60 coins** |
| Coin deduction rate | **10 coins/min** (all time in room, not just speaking) |
| Creator income rate | **1 Rs/min** per user in room |
| Charge starts after | **10 seconds** (under 10s = free) |
| Minutes rounded | **Up** (ceil) - 1 min 1 sec = 2 min charge |
| Settlement timing | **At room end** (creator leaves or user kicked) |
| Auto-kick | User kicked when coins can't cover the next minute |
| Creator pays? | **No** - creator only earns |

### Example

3 users join a room. User A stays 5 min, User B stays 10 min, User C stays 3 min.

| User | Time | Coins Deducted | Creator Earns |
|------|------|----------------|---------------|
| User A | 5 min | 50 coins | 5 Rs |
| User B | 10 min | 100 coins | 10 Rs |
| User C | 3 min | 30 coins | 3 Rs |
| **Total** | | **180 coins** | **18 Rs** |

### Auto-Kick Flow

1. User joins with 60 coins (6 min max)
2. Every 5s poll (room-details), server checks: can user afford next minute?
3. At ~6 min mark: `7 min * 10 = 70 coins > 60 coins` - user is kicked
4. Settlement: 6 min * 10 = 60 coins deducted, creator gets 6 Rs
5. User is removed from room

---

## Room Creation Rules

- **Only today's match teams** can create rooms
- If today's matches are MI vs CSK and RCB vs KKR, only those 4 teams are valid
- Room capacity: **1 creator + 3 joiners = 4 max**
- Creator is auto-added as first member (does not pay coins)

---

## 1. List Rooms

`POST /api/auth/ipl-rooms-list`

Returns all currently active (live) IPL voice rooms.

### Request

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | int | Yes | Logged-in user's ID |

### Response

```json
{
  "success": true,
  "message": "Rooms fetched successfully",
  "data": [
    {
      "id": 1,
      "name": "MI vs CSK Discussion",
      "team_a": "MI",
      "team_b": "CSK",
      "creator_id": 101,
      "creator_name": "Rohit Fan",
      "member_count": 3,
      "max_members": 4,
      "is_live": true,
      "created_at": "2026-04-03T18:30:00+00:00"
    }
  ]
}
```

### Error

```json
{ "success": false, "message": "user_id is required." }
```

---

## 2. Create Room

`POST /api/auth/ipl-rooms-create`

Creates a new IPL voice room. **Only today's match teams allowed.** Creator is auto-added as first member (no coin charge for creator).

### Request

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | int | Yes | Creator's user ID |
| `room_name` | string | Yes | Display name for the room |
| `team_a` | string | Yes | Team A abbreviation (must be from today's match) |
| `team_b` | string | Yes | Team B abbreviation (must be from today's match) |

### Valid Team Abbreviations

`MI`, `CSK`, `RCB`, `KKR`, `DC`, `SRH`, `RR`, `PBKS`, `GT`, `LSG`

### Response

```json
{
  "success": true,
  "message": "Room created successfully",
  "data": {
    "id": 10,
    "name": "MI vs CSK Discussion",
    "team_a": "MI",
    "team_b": "CSK",
    "creator_id": 101,
    "creator_name": "Rohit Fan",
    "member_count": 1,
    "max_members": 4,
    "is_live": true,
    "created_at": "2026-04-03T18:30:00+00:00"
  }
}
```

### Errors

```json
{ "success": false, "message": "room_name is required." }
{ "success": false, "message": "team_a and team_b must be different teams." }
{ "success": false, "message": "Only today's match teams can create rooms. Today's matches: MI vs CSK, RCB vs KKR" }
{ "success": false, "message": "User not found." }
```

---

## 3. Join Room

`POST /api/auth/ipl-rooms-join`

User joins an existing live room. **Requires minimum 60 coins.** Uses DB locking to prevent exceeding max members (4).

### Request

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | int | Yes | User who wants to join |
| `room_id` | int | Yes | Room to join |

### Response

```json
{
  "success": true,
  "message": "Joined room successfully",
  "data": {
    "room_id": 1,
    "members": [
      {
        "id": 101,
        "name": "Rohit Fan",
        "avatar_url": "https://example.com/avatar1.jpg",
        "is_muted": false,
        "is_speaking": false,
        "is_creator": true
      },
      {
        "id": 102,
        "name": "Dhoni Lover",
        "avatar_url": null,
        "is_muted": false,
        "is_speaking": false,
        "is_creator": false
      }
    ]
  }
}
```

### Errors

```json
{ "success": false, "message": "You need at least 60 coins to join a room." }
{ "success": false, "message": "Room not found." }
{ "success": false, "message": "Room is no longer active." }
{ "success": false, "message": "Room is full." }
{ "success": false, "message": "You are already in this room." }
```

---

## 4. Get Room Details

`POST /api/auth/ipl-rooms-details`

Fetches current room state with all members. **Client polls this every 5 seconds.** Server auto-kicks members who can't afford the next minute.

### Request

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `room_id` | int | Yes | Room to fetch |

### Response

```json
{
  "success": true,
  "message": "Room details fetched",
  "data": {
    "id": 1,
    "name": "MI vs CSK Discussion",
    "team_a": "MI",
    "team_b": "CSK",
    "creator_id": 101,
    "creator_name": "Rohit Fan",
    "is_live": true,
    "members": [
      {
        "id": 101,
        "name": "Rohit Fan",
        "avatar_url": "https://example.com/avatar1.jpg",
        "is_muted": false,
        "is_speaking": false,
        "is_creator": true,
        "elapsed_minutes": 0,
        "remaining_minutes": 0
      },
      {
        "id": 102,
        "name": "Dhoni Lover",
        "avatar_url": null,
        "is_muted": false,
        "is_speaking": false,
        "is_creator": false,
        "elapsed_minutes": 3,
        "remaining_minutes": 3
      }
    ]
  }
}
```

### New Member Fields

| Field | Type | Description |
|-------|------|-------------|
| `elapsed_minutes` | int | Minutes the user has been in the room |
| `remaining_minutes` | int | Minutes the user can still afford (based on coins). 0 for creator. |

### Notes

- Server auto-kicks members who can't afford the next minute on each poll
- When `is_live` is `false`, client should show room-ended state
- `remaining_minutes` = floor(user_coins / 10) for non-creators, 0 for creator

### Errors

```json
{ "success": false, "message": "room_id is required." }
{ "success": false, "message": "Room not found." }
```

---

## 5. Leave Room

`POST /api/auth/ipl-rooms-leave`

User leaves the room. **Triggers coin settlement:**
- **Non-creator leaves:** Coins deducted for time spent, creator gets income, member removed
- **Creator leaves:** All non-creator members settled, room closed (`is_live = false`)
- **Last member leaves:** Room closed

### Request

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | int | Yes | User leaving |
| `room_id` | int | Yes | Room to leave |

### Settlement on Leave

When a non-creator member leaves or is kicked:
1. Calculate minutes = ceil(seconds_in_room / 60)
2. Deduct `minutes * 10` coins from user (capped at user's balance, won't go negative)
3. Credit `minutes * 1 Rs` to creator's balance
4. Record `coins_deduction` transaction for user
5. Record `call_income` transaction for creator

### Response

```json
{
  "success": true,
  "message": "Left room successfully"
}
```

### Errors

```json
{ "success": false, "message": "user_id is required." }
{ "success": false, "message": "room_id is required." }
{ "success": false, "message": "You are not in this room." }
```

---

## 6. Send Reaction

`POST /api/auth/ipl-rooms-reaction`

Sends a reaction/emoji in the room. Fire-and-forget from client side.

### Request

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | int | Yes | User sending reaction |
| `room_id` | int | Yes | Room ID |
| `reaction_type` | string | Yes | One of: `four`, `six`, `wicket`, `great` |

### Reaction Types

| Value | Description |
|-------|-------------|
| `four` | Celebrating a boundary |
| `six` | Celebrating a six |
| `wicket` | Celebrating a wicket |
| `great` | General appreciation |

### Response

```json
{ "success": true, "message": "Reaction sent" }
```

### Errors

```json
{ "success": false, "message": "reaction_type must be one of: four, six, wicket, great" }
{ "success": false, "message": "Room not found or not active." }
```

---

## 7. Toggle Mute

`POST /api/auth/ipl-rooms-mute`

Updates user's mute status in the room. **Note:** Coins are charged for all time in room, regardless of mute status.

### Request

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | int | Yes | User toggling mute |
| `room_id` | int | Yes | Room ID |
| `is_muted` | int | Yes | `1` = muted, `0` = unmuted |

### Response

```json
{ "success": true, "message": "Mute status updated" }
```

### Errors

```json
{ "success": false, "message": "is_muted must be 0 or 1." }
{ "success": false, "message": "You are not in this room." }
```

---

## 8. Get Match Suggestions

`POST /api/auth/ipl-rooms-matches`

Returns **today's matches only** for the "Create Room" dialog. Cached for 5 minutes.

### Request

No parameters required (auth token only).

### Response

```json
{
  "success": true,
  "message": "Matches fetched",
  "data": [
    "MI vs CSK",
    "RCB vs KKR"
  ]
}
```

### Notes

- Returns only today's active matches
- Client uses these to auto-fill team_a and team_b in room creation
- If no matches today or API fails, client falls back to hardcoded suggestions

---

## Agora Voice Channel

The client uses the existing Agora token endpoint for voice:

```
POST /api/auth/agora/token
```

| Param | Value |
|-------|-------|
| `channel_name` | `ipl_room_{roomId}` |
| `uid` | User's ID |
| `role` | `publisher` |

No changes were made to the Agora endpoint.

---

## Transaction Types

| Type | Who | Description |
|------|-----|-------------|
| `coins_deduction` | Joiner | Coins deducted for time in room. Reason: `IPL Room #X (name) - Y min` |
| `call_income` | Creator | Income earned from user. Reason: `IPL Room #X - User name (Y min)` |

---

## Database Tables

| Table | Description |
|-------|-------------|
| `ipl_rooms` | Room info (name, teams, creator, is_live) |
| `ipl_room_members` | Room membership (user, mute status, creator flag, joined_at) |
| `ipl_room_reactions` | Reaction log (room, user, type) |
| `ipl_matches` | Match schedule - only today's date matches are used |

---

## Error Format

All error responses follow:

```json
{
  "success": false,
  "message": "Error description here"
}
```

HTTP 401 is returned only for missing/invalid JWT token. All other errors return HTTP 200 with `"success": false`.
