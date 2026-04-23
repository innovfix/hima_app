# Backend ticket: "Delete for everyone" for chat messages

**Audience:** Backend / API team
**Endpoints touched:** `POST .../api/auth/delete_chat_message`, `GET .../api/auth/chat_history`, Socket.IO server on `Config.SOCKET_URL + SOCKET_PATH`
**Priority:** Medium (UX-facing; client ships the local half first, peer parity blocks on this ticket)

---

## 1. Motivation

The Android client currently exposes a "Remove message" action that only hides the bubble on the sender's own device — the row stays intact on the peer's device and in the database. Product now wants a true **delete-for-everyone** flow: one tap, the message disappears on both devices, history reloads keep showing it as deleted, and a tombstone bubble ("This message was deleted") replaces the original so the conversation doesn't develop unexplained gaps.

The client half of this has landed on branch `app_releases` (dialog copy, tombstone rendering, optimistic local state, Socket.IO emit, REST fallback, peer-side observer). It currently **fails soft** — the sender sees the tombstone instantly but the peer does not see anything change until this ticket is live.

---

## 2. Schema change (one migration)

Table: `chat_messages` (or whatever the current messages table is called).

```sql
ALTER TABLE chat_messages
  ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;

CREATE INDEX idx_chat_messages_is_deleted ON chat_messages (is_deleted);
```

- `is_deleted = 1` ⇒ the row has been delete-for-everyone'd by the sender.
- `deleted_at` records the moment the backend applied the flag (useful for auditing and a future time-window rule).

**Do not hard-delete rows.** We want the row to survive so the tombstone can be rendered in position on future history reads.

---

## 3. REST endpoint — `POST /delete_chat_message`

Used by the client when Socket.IO isn't connected at tap time.

### Request

`POST /api/auth/delete_chat_message` with `application/x-www-form-urlencoded`:

| Field           | Type    | Required | Notes                                  |
|-----------------|---------|----------|----------------------------------------|
| `from_user_id`  | int     | yes      | Sender's user id (must own the message) |
| `to_user_id`    | int     | yes      | Peer's user id                         |
| `message_id`    | string  | yes      | Id of the message to delete            |

### Behavior

1. Look up `chat_messages` by `message_id`.
2. **Ownership check:** reject (`403` or `{success:false}`) unless `chat_messages.from_user_id == from_user_id` and the row belongs to the conversation with `to_user_id`.
3. If already `is_deleted = 1`, return **success** (idempotent; the client may retry).
4. Update: `is_deleted = 1`, `deleted_at = NOW()`, and **blank the mutable content** (`message = ''`, `attachment_url = NULL`) so nothing is readable from the row going forward.
5. After commit, emit `message_deleted` on the Socket.IO server to both user rooms (see Section 4).
6. Response:

```json
{ "success": true, "message": "Message deleted" }
```

Error cases:

```json
{ "success": false, "message": "You can only delete your own messages" }
{ "success": false, "message": "Message not found" }
```

HTTP 200 with `success:false` is acceptable — the client checks both the HTTP code and `success`. Prefer idempotency: a second delete of an already-deleted row returns `{success:true}`.

---

## 4. Socket.IO contract

Existing server already handles `send_message`, `send_reaction`, `typing`, etc. Add two events:

### 4a. Inbound: `delete_message`

Client emits:

```json
{
  "from_user_id": 123,
  "to_user_id":   456,
  "message_id":   "987654"
}
```

Server handler:

1. Verify the authenticated socket belongs to `from_user_id` (same convention used for `send_message`).
2. Verify ownership of `message_id` as in Section 3.
3. Apply the same DB update as the REST path (or call into the same service layer — recommended so the two paths cannot drift).
4. Broadcast `message_deleted` (Section 4b) to **both** `from_user_id`'s and `to_user_id`'s user rooms, so every open device of either party flips the row.

### 4b. Outbound: `message_deleted`

Server → clients:

```json
{ "message_id": "987654" }
```

That's all the Android client needs — it uses the id to locate the row in its in-memory window and swap it to a tombstone. Including `chat_id`, `from_user_id`, `to_user_id` is fine (future-proofing) but not required.

---

## 5. History contract — `chat_history`

Deleted rows **must still appear** in paginated history results, in their original chronological position, with:

```json
{
  "id": 987654,
  "chat_id": "123_456",
  "from_user_id": 123,
  "to_user_id":   456,
  "message": "",
  "attachment_url": null,
  "message_type": "text",
  "is_deleted": 1,
  "timestamp": "2026-04-22T15:03:00+00:00",
  "created_at": "2026-04-22T15:03:00+00:00"
}
```

The Android `ChatMessageApi` now reads `is_deleted` as an optional int (0 / 1). When it's 1, the client renders the tombstone and drops any reactions/attachment on the floor.

**Do not** return the original `message` body or the `attachment_url` for a deleted row. That information is gone.

Keep returning reactions for non-deleted rows exactly as today.

---

## 6. Auth / ownership rules (explicit)

- **Only the sender** of a message may delete it.
- The receiver attempting to delete must be rejected at both the REST and Socket.IO entry points.
- Admins / moderators are out of scope for this ticket.
- **No time window** for MVP — a sender can delete any message they sent, regardless of age. Adding a "within 7 days" rule later is straightforward: just gate the update on `created_at >= NOW() - INTERVAL 7 DAY`.

---

## 7. Feature flag + rollout

- Put the new endpoint and Socket.IO handler behind a server-side feature flag (e.g. `FEATURE_CHAT_DELETE_FOR_EVERYONE`) so the demo server can ship first while prod waits.
- When the flag is **off**, the REST endpoint should return `{success:false, message:"Not available"}` so the client's fallback completes cleanly, rolls back the local tombstone, and shows the `chat_delete_failed` toast.
- When the flag is **on**, both REST and Socket.IO paths must be live simultaneously — shipping only one creates confusing asymmetries (socket deletes succeed but REST fallbacks 404, or vice versa).

---

## 8. Manual test plan

Pre-req: two real devices (or one device + the existing debug panel) logged in as `A` and `B`, friends/talking.

1. **Happy path (socket live):** A sends "hello" → A long-presses → Delete → confirm. A's bubble flips to italic "This message was deleted" and toast "Message deleted". B's chat bubble flips to the same tombstone within a second.
2. **Happy path (socket down, REST fallback):** Toggle A's device to airplane-mode-then-wifi-only briefly to force a socket disconnect. Repeat step 1. A still tombstones; check server logs confirm the REST path ran; B's device eventually tombstones on the next history reload.
3. **Peer-side rejection:** B long-presses a message received from A. Delete option must not appear.
4. **Already deleted:** A tries to delete a row that's already `is_deleted = 1` (e.g. via race with another device). Response is `{success:true}`; client stays on tombstone. No DB mutations beyond idempotent update.
5. **Ownership rejection:** Manually craft a `POST /delete_chat_message` with `from_user_id = B` for a message owned by A. Expect `success:false` and no DB mutation.
6. **History reload parity:** After a successful delete, both A and B force-close and reopen the chat. Tombstone renders in the correct chronological slot. Pagination back into older history still works.
7. **Media cleanup:** Delete an image message. `attachment_url` must come back as null from `chat_history` (client relies on this to avoid loading stale thumbnails).

---

## 9. What the client does today (ship-ready)

So backend knows what to plug into:

- **Emit:** `SocketManager.deleteMessage(fromUserId, toUserId, messageId)` — payload shape in Section 4a.
- **Listen:** `SocketManager.chatMessageDeleted` (`StateFlow<String?>`) on event `message_deleted` (Section 4b).
- **REST fallback:** `ApiManager.deleteChatMessage(fromUserId, toUserId, messageId, callback)` against `POST /delete_chat_message` (Section 3). Response parsed into `SimpleAckResponse(success, message)`.
- **History:** `ChatMessageApi.isDeleted: Int?` (`@SerializedName("is_deleted")`) already plumbed — if the field is missing the row renders normally, if it's `1` the row renders as a tombstone (Section 5).

Log tag: `ChatDelete`. Hook both the REST path and socket emit/receive log lines so on-call can trace a missed delete end-to-end.

---

## 10. Rollback

- Drop the new endpoint route and Socket.IO handler.
- Leave the `is_deleted` / `deleted_at` columns in place (harmless, and future retries of this feature will use them).
- Client continues to compile — `deleteChatMessage` calls will return 404 from the REST side and `emit("delete_message", ...)` is a no-op if the server ignores it. The sender-side tombstone still works locally; the peer-side parity just doesn't happen. This matches the pre-ticket behaviour.
