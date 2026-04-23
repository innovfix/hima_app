# Backend ticket: Unified call disconnect tracking (rejected / not answered / ended)

**Audience:** Backend / API team
**Endpoints touched:** `POST .../api/auth/call_drop_status` (extend), `POST .../api/auth/call_reject_count` (extend), `POST .../api/auth/missed_call_count` (retire or replace), optionally a new `POST .../api/auth/call_status` that supersedes all three
**Priority:** Medium (analytics + history correctness; currently we cannot tell rejected vs missed vs ended, or who dropped the call)

---

## 1. Motivation

Right now the Android client reports call termination to the backend in three inconsistent ways, and none of them carries enough information to answer the simple questions product keeps asking:

1. **Who ended this call — the user or the creator?**
2. **Was this call rejected before pickup, or did it ring out unanswered, or was it a normal hang-up after the conversation?**
3. **Why is the missed-call counter unreliable?** (Answer: because `missed_call_count` is never actually called by the client.)

Current client behaviour, with file:line references so backend can verify against the source:

| Termination event | What the client does today | Gap |
|---|---|---|
| Receiver taps **Reject** (female side) | Fires `send-fcm-notification` with `message:"rejected"` only. No stats API. | No row written in the DB for this rejection. |
| Receiver taps **Reject** (male side) | Fires `send-fcm-notification` with `message:"rejected"` **and** `call_reject_count`. | Female-side parity missing; `call_reject_count` has no `call_id`, so we can't correlate to the call row. |
| Ringing call times out / peer never picks up | **Nothing.** `missed_call_count` exists in `ApiManager.kt:2668` but is never invoked anywhere. | Backend has no record the call was missed. |
| Call ended after being answered | Both sides POST `call_drop_status` with `call_drop_status: 1` and the same `call_id`. | Payload has no "who ended it" or "reason" field, so the two identical rows are indistinguishable. |

Key client call sites:

- `FemaleCallAcceptActivity.kt:207-232` — female-side reject button, fires FCM only.
- `MaleCallAcceptActivity.kt:231-258` — male-side reject button, fires FCM + `callRejectCount`.
- `FemaleAudioCallingActivity.kt:2195-2199`, `MaleAudioCallingActivity.kt:1657-1661`, `FemaleVideoCallingActivity.kt:1827-1831`, `MaleVideoCallingActivity.kt:1847-1851` — hang-up path, fires `call_drop_status` with no end-reason.
- `CallDropStatusViewModel.kt:26-65` — the request body has only `user_id`, `received_user_id`, `call_id`, `call_drop_status`.
- `ApiManager.kt:2668-2672` — `missed_call_count` endpoint (dead code on client).
- `ApiManager.kt:3080-3083` — `call_drop_status` endpoint (active).

We need the backend to accept a single, well-shaped "this call ended, here's why, here's who" message from the client, and we need the client to actually fire it on **every** termination path — reject, timeout, or normal hang-up.

---

## 2. Schema change (one migration)

Table: `calls` (or whatever the current outgoing/incoming call row is — the one pointed to by `call_id` in `call_drop_status`).

```sql
ALTER TABLE calls
  ADD COLUMN end_reason       ENUM('ended','rejected','not_answered','failed') NULL DEFAULT NULL,
  ADD COLUMN ended_by_user_id INT UNSIGNED                                     NULL DEFAULT NULL,
  ADD COLUMN ended_at         TIMESTAMP                                        NULL DEFAULT NULL;

CREATE INDEX idx_calls_end_reason ON calls (end_reason);
CREATE INDEX idx_calls_ended_by   ON calls (ended_by_user_id);
```

- `end_reason` is the authoritative "what happened":
  - `ended` — call was picked up and later hung up normally.
  - `rejected` — receiver tapped Reject before pickup.
  - `not_answered` — caller gave up or the ring timer expired without pickup.
  - `failed` — SDK-level error (Agora token failed, network dropped before pickup, etc.). Optional; backend may collapse into `not_answered` if we don't want the extra state.
- `ended_by_user_id` is the **user id of the person whose device initiated the termination**. For `not_answered` this is typically the caller (they gave up). For `rejected` it's the receiver. For `ended` it's whoever hit hang-up first.
- `ended_at` is when the backend applied the transition (not client wall-clock, to avoid clock-skew lies).

Do **not** rely on `call_drop_status = 1` any more; keep the column for back-compat during rollout but treat `end_reason IS NOT NULL` as the source of truth once the client ships.

---

## 3. Preferred shape: new unified endpoint — `POST /call_status`

Replaces `call_drop_status`, `missed_call_count`, and the disconnect-related part of `call_reject_count`. The client can migrate all four termination paths to this one endpoint.

### Request

`POST /api/auth/call_status` with `application/json`:

```json
{
  "call_id":          12345,
  "user_id":          678,
  "received_user_id": 910,
  "end_reason":       "rejected",
  "ended_by":         "receiver",
  "ended_by_user_id": 910,
  "duration_seconds": 0
}
```

| Field             | Type   | Required | Notes                                                                 |
|-------------------|--------|----------|-----------------------------------------------------------------------|
| `call_id`         | int    | yes      | Existing call row id. Must exist.                                     |
| `user_id`         | int    | yes      | The authenticated caller on the client (initiator of the call row).   |
| `received_user_id`| int    | yes      | The peer user id.                                                     |
| `end_reason`      | enum   | yes      | `ended` / `rejected` / `not_answered` / `failed`.                     |
| `ended_by`        | enum   | yes      | `caller` / `receiver` / `system`. `system` = timed out with no tap.    |
| `ended_by_user_id`| int    | no       | Convenience — the actual user id behind `ended_by`. `null` if `system`.|
| `duration_seconds`| int    | no       | 0 for `rejected` / `not_answered`. Real value for `ended`.            |

### Behavior

1. Look up the call row by `call_id`.
2. **Participant check:** reject unless `user_id` and `received_user_id` match the row's two participants (either order). Prevents third parties forging terminations.
3. **Idempotency:** if `end_reason` is already set, return success without re-writing. First write wins — don't let a later "ended" overwrite an earlier "rejected" on the same row.
4. Update: `end_reason`, `ended_by_user_id`, `ended_at = NOW()`, and update `duration_seconds` if provided.
5. Respond:

```json
{ "success": true, "message": "Call status recorded" }
```

Error cases:

```json
{ "success": false, "message": "Call not found" }
{ "success": false, "message": "Not a participant of this call" }
```

HTTP 200 with `success:false` is acceptable — the client checks both the HTTP code and the `success` field, matching the chat-delete ticket convention.

---

## 4. Fallback shape: extend the existing `call_drop_status` (if section 3 is too big)

If the team can't ship a new endpoint this cycle, extend `call_drop_status` with two new optional fields and treat them as the source of truth when present:

```
user_id             (existing)
received_user_id    (existing)
call_id             (existing)
call_drop_status    (existing)  -- keep for back-compat
end_reason          NEW: 'ended' | 'rejected' | 'not_answered' | 'failed'
ended_by_user_id    NEW: int
```

Client will start sending `end_reason` + `ended_by_user_id` on all termination paths. Old clients (no field) keep writing `call_drop_status = 1` and the row stays `end_reason = NULL`, which we can backfill as `'ended'` once the old client versions are deprecated.

This fallback also implies the client now needs to call `call_drop_status` on **rejection** and **timeout** (it currently does not), so either way — section 3 or section 4 — the client work is the same shape; only the URL differs.

---

## 5. Retiring `missed_call_count` and clarifying `call_reject_count`

- **`missed_call_count` (`ApiManager.kt:2668`)**: dead code on the client. Safe to delete server-side at any time, or leave orphaned. Once section 3 is live, the missed-call badge in the recents tab can be computed from `calls WHERE end_reason IN ('not_answered','rejected') AND received_user_id = me AND seen = 0`.
- **`call_reject_count` (`MaleCallAcceptActivity.kt:235`)**: keep it for now — it drives a male-side product counter and doesn't carry a `call_id`, so it's measuring something different (rate of rejections received, not per-call status). Once `/call_status` is live, backend can derive the same count from `SELECT COUNT(*) FROM calls WHERE end_reason='rejected' AND received_user_id = male_user_id` and `call_reject_count` can be retired in a follow-up.

---

## 6. Auth / ownership rules (explicit)

- Only the two participants of a call row may write to its status. Enforce at the endpoint.
- First write wins: once `end_reason` is set, subsequent writes are no-ops (return success). This prevents the caller-side `ended` + receiver-side `rejected` race from corrupting each other — whichever POST lands first is the truth. The reject path always fires before the call is answered, so in practice rejections land first and subsequent stray `ended` writes are rejected cleanly.
- Admins / moderators are out of scope for this ticket.
- No time window — a call row can be closed at any age (matches current `call_drop_status` behaviour).

---

## 7. Feature flag + rollout

- Put the new endpoint (or the new fields, if we go with section 4) behind a server-side flag `FEATURE_CALL_STATUS_V2`.
- When the flag is **off**, the new endpoint returns `{success:false, message:"Not available"}` and the client falls back to the old `call_drop_status` behaviour (hang-up only, no reject/timeout reporting) — i.e. today's behaviour, no regression.
- When the flag is **on**, the client fires `/call_status` on all four termination paths (`ended`, `rejected`, `not_answered`, `failed`).
- Backend should start writing the new columns **before** the client flips, so that early adopter clients don't 404.

---

## 8. Manual test plan

Pre-req: two real devices, one logged in as male (caller) `A` and one as female (receiver) `B`. Pair them so they can call each other.

1. **Normal end — caller hangs up:** A calls B, B answers, both talk 10s, A taps hang-up. Backend row: `end_reason='ended'`, `ended_by_user_id=A`, `duration_seconds≈10`.
2. **Normal end — receiver hangs up:** Reverse of (1), B ends. Row: `end_reason='ended'`, `ended_by_user_id=B`.
3. **Rejected by female receiver:** A calls B, B taps Reject without answering. Row: `end_reason='rejected'`, `ended_by_user_id=B`, `duration_seconds=0`. FCM also delivered to A as today.
4. **Rejected by male receiver:** B calls A, A taps Reject. Same as (3) with roles swapped. `call_reject_count` still fires as today (see section 5).
5. **Not answered — caller gives up:** A calls B, B's phone rings but A cancels after 5s. Row: `end_reason='not_answered'`, `ended_by_user_id=A`.
6. **Not answered — ring timeout:** A calls B, neither side taps anything, ring timer expires. Row: `end_reason='not_answered'`, `ended_by='system'`, `ended_by_user_id=null`.
7. **Idempotency — double hang-up:** Simulate both sides pressing hang-up simultaneously (easiest via adb shell). Exactly one row mutation; the second request returns `{success:true}` with no change. `end_reason` reflects whichever arrived first.
8. **Rejected after answered (edge case):** Shouldn't happen via UI, but force via debug panel — user hits Reject after pickup. Expect `end_reason='ended'` or `'rejected'` depending on which branch of the state machine fired; whichever you pick, document it. No corruption either way.
9. **Participant check:** Hand-craft a `/call_status` POST with `user_id` for a user who isn't on the row. Expect `success:false`.
10. **History reload parity:** Recent-calls screen on both A and B shows the correct icon (missed / rejected / outgoing / incoming) for each row from (1)-(6) after force-close and reopen. This is the main product-visible win.

---

## 9. What the client will do (not shipped yet — coordinate with this ticket)

Client-side follow-up planned once backend contract is agreed:

- **Fire on reject:** `FemaleCallAcceptActivity.kt:207` and `MaleCallAcceptActivity.kt:231` both POST `/call_status` with `end_reason='rejected'`, `ended_by='receiver'`, `ended_by_user_id=<receiver>`. FCM to the caller stays as-is.
- **Fire on caller cancel:** Wherever the caller's "cancel" button lives in the dialing screens, POST `/call_status` with `end_reason='not_answered'`, `ended_by='caller'`.
- **Fire on ring timeout:** The existing ring-timeout handler (client has one via `HimaTelecomManager`) POSTs `/call_status` with `end_reason='not_answered'`, `ended_by='system'`.
- **Fire on hang-up:** The four existing `saveCallDropStatus(...)` call sites in the `*CallingActivity`s are replaced with `/call_status` using `end_reason='ended'` and the correct `ended_by_user_id` for this device.
- **Retire:** the dead `missed_call_count` path in `ApiManager.kt:2668` and `MissedCallCountResponse.kt`.

Log tag: `CallStatus`. Every termination path logs the request body and the response; on-call can trace a missed event end-to-end.

---

## 10. Rollback

- Drop the new endpoint route (or revert the added fields on `call_drop_status`).
- Leave `end_reason`, `ended_by_user_id`, `ended_at` columns in place (harmless; future retries will use them).
- Client continues to compile — if the new endpoint 404s, fallback keeps the existing `call_drop_status: 1` behaviour, matching pre-ticket state. Reject and timeout events would go un-reported again, but nothing breaks.
