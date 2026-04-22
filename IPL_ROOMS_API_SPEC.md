# IPL Rooms - Backend API Specification

**Date:** 2026-04-03
**Base URL:** `https://himaapp.in/`
**All endpoints use:** `POST` method with `application/x-www-form-urlencoded` body

---

## Summary

The Android client has fully integrated 8 API endpoints for the IPL Rooms feature (a Clubhouse-style voice room where users discuss IPL matches). The backend needs to implement all 8 endpoints listed below. Currently the app uses **mock/hardcoded data** as fallback when APIs fail.

---

## API 1: List Rooms

**Purpose:** Fetch all available IPL voice rooms for a user.

| Field        | Value                  |
|--------------|------------------------|
| **Endpoint** | `POST /ipl-rooms-list` |

### Request Parameters (form-urlencoded)

| Param    | Type | Required | Description          |
|----------|------|----------|----------------------|
| `user_id`| Int  | Yes      | Logged-in user's ID  |

### Response (JSON)

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
      "created_at": "2026-04-03T18:30:00Z"
    }
  ]
}
```

### Response Fields

| Field          | Type    | Description                                    |
|----------------|---------|------------------------------------------------|
| `id`           | Int     | Unique room ID                                 |
| `name`         | String  | Room display name                              |
| `team_a`       | String  | Team A abbreviation (e.g. "MI", "CSK", "RCB")  |
| `team_b`       | String  | Team B abbreviation                             |
| `creator_id`   | Int     | User ID of room creator                         |
| `creator_name` | String  | Display name of room creator                    |
| `member_count` | Int     | Current number of members in room               |
| `max_members`  | Int     | Maximum allowed members (default 4)             |
| `is_live`      | Boolean | Whether room is currently active                |
| `created_at`   | String  | ISO 8601 timestamp of room creation (nullable)  |

### Valid Team Abbreviations

| Abbreviation | Team Name                      |
|--------------|--------------------------------|
| MI           | Mumbai Indians                 |
| CSK          | Chennai Super Kings            |
| RCB          | Royal Challengers Bengaluru    |
| KKR          | Kolkata Knight Riders          |
| DC           | Delhi Capitals                 |
| SRH          | Sunrisers Hyderabad            |
| RR           | Rajasthan Royals               |
| PBKS         | Punjab Kings                   |
| GT           | Gujarat Titans                 |
| LSG          | Lucknow Super Giants           |

---

## API 2: Create Room

**Purpose:** Create a new IPL voice room.

| Field        | Value                    |
|--------------|--------------------------|
| **Endpoint** | `POST /ipl-rooms-create` |

### Request Parameters (form-urlencoded)

| Param      | Type   | Required | Description                        |
|------------|--------|----------|------------------------------------|
| `user_id`  | Int    | Yes      | Logged-in user's ID (becomes creator) |
| `room_name`| String | Yes      | Display name for the room          |
| `team_a`   | String | Yes      | Team A abbreviation (e.g. "MI")    |
| `team_b`   | String | Yes      | Team B abbreviation (e.g. "CSK")   |

### Validation Rules
- `team_a` and `team_b` must be different teams
- `team_a` and `team_b` must be valid abbreviations from the team list above
- `room_name` should not be empty

### Response (JSON)

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
    "created_at": "2026-04-03T18:30:00Z"
  }
}
```

### Notes
- Creator should automatically be added as first member of the room
- `max_members` defaults to 4
- `is_live` should be `true` on creation

---

## API 3: Join Room

**Purpose:** User joins an existing IPL voice room.

| Field        | Value                  |
|--------------|------------------------|
| **Endpoint** | `POST /ipl-rooms-join` |

### Request Parameters (form-urlencoded)

| Param    | Type | Required | Description           |
|----------|------|----------|-----------------------|
| `user_id`| Int  | Yes      | User who wants to join|
| `room_id`| Int  | Yes      | Room to join          |

### Validation Rules
- Room must exist and `is_live` must be true
- Room must not be full (`member_count < max_members`)
- User should not already be in the room

### Response (JSON)

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
        "avatar_url": "https://example.com/avatar2.jpg",
        "is_muted": false,
        "is_speaking": false,
        "is_creator": false
      }
    ]
  }
}
```

### Response Fields (IplMemberData)

| Field         | Type    | Description                      |
|---------------|---------|----------------------------------|
| `id`          | Int     | User ID of the member            |
| `name`        | String  | Display name                     |
| `avatar_url`  | String? | Profile picture URL (nullable)   |
| `is_muted`    | Boolean | Whether member is currently muted|
| `is_speaking` | Boolean?| Whether member is speaking (nullable) |
| `is_creator`  | Boolean | Whether this member created the room |

---

## API 4: Get Room Details

**Purpose:** Fetch current room state including all members. **Client polls this every 5 seconds** to keep UI in sync.

| Field        | Value                     |
|--------------|---------------------------|
| **Endpoint** | `POST /ipl-rooms-details` |

### Request Parameters (form-urlencoded)

| Param    | Type | Required | Description   |
|----------|------|----------|---------------|
| `room_id`| Int  | Yes      | Room to fetch |

### Response (JSON)

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
        "is_speaking": true,
        "is_creator": true
      }
    ]
  }
}
```

### Important Notes
- **This endpoint is polled every 5 seconds** by the client, so it must be fast/lightweight
- Should return current member list with their mute/speaking status
- When `is_live` is `false`, client will show room ended state
- Consider caching or optimizing for frequent polling

---

## API 5: Leave Room

**Purpose:** User leaves an IPL voice room.

| Field        | Value                   |
|--------------|-------------------------|
| **Endpoint** | `POST /ipl-rooms-leave` |

### Request Parameters (form-urlencoded)

| Param    | Type | Required | Description         |
|----------|------|----------|---------------------|
| `user_id`| Int  | Yes      | User who is leaving |
| `room_id`| Int  | Yes      | Room to leave       |

### Response (JSON)

```json
{
  "success": true,
  "message": "Left room successfully"
}
```

### Notes
- Decrement `member_count` for the room
- If the creator leaves, decide whether to:
  - Close the room (set `is_live = false`), OR
  - Transfer creator role to next member
- If last member leaves, set `is_live = false`

---

## API 6: Send Reaction

**Purpose:** User sends a reaction/emoji in the room (e.g., celebrating a four or six).

| Field        | Value                      |
|--------------|----------------------------|
| **Endpoint** | `POST /ipl-rooms-reaction` |

### Request Parameters (form-urlencoded)

| Param          | Type   | Required | Description              |
|----------------|--------|----------|--------------------------|
| `user_id`      | Int    | Yes      | User sending reaction    |
| `room_id`      | Int    | Yes      | Room where reaction sent |
| `reaction_type`| String | Yes      | Type of reaction         |

### Valid Reaction Types

| Value    | Display Text | Description           |
|----------|-------------|------------------------|
| `four`   | FOUR        | Celebrating a boundary |
| `six`    | SIX         | Celebrating a six      |
| `wicket` | WICKET      | Celebrating a wicket   |
| `great`  | GREAT       | General appreciation   |

### Response (JSON)

```json
{
  "success": true,
  "message": "Reaction sent"
}
```

### Notes
- This is fire-and-forget from the client side
- Client shows floating animation locally regardless of API response
- Could be used for analytics/engagement tracking
- Consider rate-limiting to prevent spam

---

## API 7: Toggle Mute

**Purpose:** Update user's mute status in the room (synced with Agora audio mute).

| Field        | Value                  |
|--------------|------------------------|
| **Endpoint** | `POST /ipl-rooms-mute` |

### Request Parameters (form-urlencoded)

| Param     | Type | Required | Description                      |
|-----------|------|----------|----------------------------------|
| `user_id` | Int  | Yes      | User toggling mute               |
| `room_id` | Int  | Yes      | Room where mute is toggled       |
| `is_muted`| Int  | Yes      | 1 = muted, 0 = unmuted          |

### Response (JSON)

```json
{
  "success": true,
  "message": "Mute status updated"
}
```

### Notes
- Client also controls Agora SDK mute locally
- This API keeps the server-side state in sync
- The mute status is returned in room details polling (API 4)

---

## API 8: Get Match Suggestions

**Purpose:** Fetch list of upcoming/ongoing IPL match suggestions for the "Create Room" dialog.

| Field        | Value                     |
|--------------|---------------------------|
| **Endpoint** | `POST /ipl-rooms-matches` |

### Request Parameters

None required.

### Response (JSON)

```json
{
  "success": true,
  "message": "Matches fetched",
  "data": [
    "MI vs CSK",
    "RCB vs KKR",
    "DC vs SRH",
    "RR vs PBKS",
    "GT vs LSG"
  ]
}
```

### Notes
- Returns array of match strings displayed as selectable chips
- Client uses these to auto-fill team_a and team_b in room creation
- If this API fails, client falls back to 10 hardcoded match suggestions
- Ideally this should return today's/upcoming actual IPL matches

---

## Database Schema Suggestion

### Table: `ipl_rooms`

| Column       | Type         | Description                          |
|--------------|--------------|--------------------------------------|
| id           | INT (PK, AI) | Room ID                              |
| name         | VARCHAR(255) | Room display name                    |
| team_a       | VARCHAR(10)  | Team A abbreviation                  |
| team_b       | VARCHAR(10)  | Team B abbreviation                  |
| creator_id   | INT (FK)     | References users.id                  |
| max_members  | INT          | Default 4                            |
| is_live      | BOOLEAN      | Whether room is active               |
| created_at   | TIMESTAMP    | Room creation time                   |
| updated_at   | TIMESTAMP    | Last update time                     |

### Table: `ipl_room_members`

| Column     | Type         | Description                            |
|------------|--------------|----------------------------------------|
| id         | INT (PK, AI) | Membership ID                          |
| room_id    | INT (FK)     | References ipl_rooms.id                |
| user_id    | INT (FK)     | References users.id                    |
| is_muted   | BOOLEAN      | Current mute status                    |
| is_creator | BOOLEAN      | Whether this user created the room     |
| joined_at  | TIMESTAMP    | When user joined                       |

### Table: `ipl_room_reactions` (optional, for analytics)

| Column        | Type         | Description                         |
|---------------|--------------|-------------------------------------|
| id            | INT (PK, AI) | Reaction ID                         |
| room_id       | INT (FK)     | References ipl_rooms.id             |
| user_id       | INT (FK)     | References users.id                 |
| reaction_type | VARCHAR(20)  | "four", "six", "wicket", "great"    |
| created_at    | TIMESTAMP    | When reaction was sent              |

### Table: `ipl_matches` (for match suggestions)

| Column     | Type         | Description                            |
|------------|--------------|----------------------------------------|
| id         | INT (PK, AI) | Match ID                               |
| team_a     | VARCHAR(10)  | Team A abbreviation                    |
| team_b     | VARCHAR(10)  | Team B abbreviation                    |
| match_date | DATE         | Match date                             |
| is_active  | BOOLEAN      | Whether to show in suggestions         |

---

## Agora Token (Existing API - May Need Update)

The client also calls an existing Agora token API for voice channel access. The channel name format used for IPL rooms is:

```
Channel name: ipl_room_{roomId}
```

Make sure the existing `getAgoraToken` endpoint supports generating tokens for channels with this naming pattern.

---

## Error Response Format

All endpoints should return errors in this format:

```json
{
  "success": false,
  "message": "Error description here",
  "data": null
}
```

---

## Priority Order for Implementation

| Priority | API                    | Reason                                        |
|----------|------------------------|-----------------------------------------------|
| 1        | List Rooms             | Core feature - needed to show room list        |
| 2        | Create Room            | Users need to create rooms                     |
| 3        | Join Room              | Users need to enter rooms                      |
| 4        | Get Room Details       | Needed for live room state (polled every 5s)   |
| 5        | Leave Room             | Users need to exit rooms cleanly               |
| 6        | Toggle Mute            | Important for voice room UX                    |
| 7        | Get Match Suggestions  | Nice-to-have, client has fallback mock data    |
| 8        | Send Reaction          | Nice-to-have, fire-and-forget                  |

---

## Quick Checklist

- [ ] `POST /ipl-rooms-list` - List all active rooms
- [ ] `POST /ipl-rooms-create` - Create new room
- [ ] `POST /ipl-rooms-join` - Join existing room
- [ ] `POST /ipl-rooms-details` - Get room details (polled every 5s)
- [ ] `POST /ipl-rooms-leave` - Leave room
- [ ] `POST /ipl-rooms-mute` - Toggle mute status
- [ ] `POST /ipl-rooms-matches` - Get match suggestions
- [ ] `POST /ipl-rooms-reaction` - Send reaction
- [ ] Agora token support for `ipl_room_{roomId}` channels
- [ ] Database tables created
