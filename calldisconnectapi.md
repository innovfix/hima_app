# API: `POST /api/auth/call_status`

Unified call-termination reporting. Records authoritative termination state
(`end_reason` + `ended_by_user_id`) on `user_calls`. Replaces the semantic gap
in legacy `call_drop_status` (which only knows "I cut" / "I didn't cut") and
gives the backend enough information to distinguish rejected, not-answered,
and normally-ended calls — as well as who ended each.

Legacy endpoints (`call_drop_status`, `call_reject_count`, `missed_call_count`)
remain available for back-compat.

---

## Endpoint

| | |
|---|---|
| Method | `POST` |
| URL | `https://demolivedb.himaapp.in/api/auth/call_status` |
| Auth | JWT bearer (same as `call_drop_status`) |
| Content-Type | `application/json` or `application/x-www-form-urlencoded` |
| Feature flag | `FEATURE_CALL_STATUS_V2` — off by default per environment |

Live (`himaapp.in`) does **not** expose this endpoint. Demo Android build only.

---

## Request body

| Field | Type | Required | Notes |
|---|---|---|---|
| `user_id` | int | yes | The authenticated user's id. Must equal the token's user. |
| `received_user_id` | int | yes | The peer user id on this call. |
| `call_id` | int | yes | `user_calls.id` of the call being terminated. Must exist. |
| `end_reason` | enum | yes | `ended` / `rejected` / `not_answered` / `failed`. |
| `ended_by` | enum | yes | `caller` / `receiver` / `system`. `system` = timed out with no user tap. |
| `ended_by_user_id` | int | conditional | Required when `ended_by=caller` or `receiver`. Must be null when `ended_by=system`. Must be one of the two participants of the `call_id` row. |
| `duration_seconds` | int | no | Informational only. Backend does not currently rewrite `started_time`/`ended_time` from this field. Pass `0` for `rejected` / `not_answered`. |

### `end_reason` values

| Value | When to send |
|---|---|
| `ended` | Call was picked up and later hung up normally. |
| `rejected` | Receiver tapped Reject before pickup. |
| `not_answered` | Ring timer expired, or caller cancelled before pickup. |
| `failed` | SDK-level error (Agora token failed, network dropped before pickup, etc.). |

### `ended_by` values

| Value | Meaning |
|---|---|
| `caller` | The user who initiated the call row (`user_calls.user_id`) ended it. |
| `receiver` | The other participant (`user_calls.call_user_id`) ended it. |
| `system` | No user tapped anything — ring timeout / Agora signal / network. |

---

## Behaviour

1. **Feature flag check.** If `FEATURE_CALL_STATUS_V2` is off, returns
   `{success:false, message:"Not available"}` with HTTP 200 — client should
   fall back to legacy `call_drop_status`.
2. **Auth.** `user_id` must match the authenticated user (403 otherwise).
3. **Participant check.** `user_id` and `received_user_id` must match the two
   participants of the `call_id` row (either order). Third parties cannot
   terminate calls.
4. **Atomic first-write-wins.** Single conditional SQL UPDATE:

   ```sql
   UPDATE user_calls
   SET end_reason = :reason,
       ended_by_user_id = :by,
       update_current_endedtime = COALESCE(update_current_endedtime, NOW()),
       updated_at = NOW()
   WHERE id = :call_id AND end_reason IS NULL
   ```

   - Affected rows = 1 → row was pristine; termination recorded.
   - Affected rows = 0 → row already has an `end_reason`; response returns
     `already_recorded:true` and the original values. No overwrite ever.
5. **`update_current_endedtime` preservation.** If already non-null (normal
   `ended` rows), stays as-is. If null (`rejected` / `not_answered`), gets set
   to `NOW()`. `COALESCE` guarantees no overwrite.

---

## Success responses

### Freshly recorded

```json
{
  "success": true,
  "message": "Call status recorded",
  "data": {
    "call_id": 46580081,
    "end_reason": "rejected",
    "ended_by_user_id": 73,
    "update_current_endedtime": "2026-04-23 03:41:33",
    "duration_seconds": 0,
    "already_recorded": false
  }
}
```

### Already recorded (idempotent second POST)

```json
{
  "success": true,
  "message": "Call status already recorded",
  "data": {
    "call_id": 46580081,
    "end_reason": "rejected",
    "ended_by_user_id": 73,
    "update_current_endedtime": "2026-04-23 03:41:33",
    "already_recorded": true
  }
}
```

---

## Error responses

All business-validation errors return HTTP 200 with `success:false` (matches
the existing `call_drop_status` convention). True auth errors return 401.
Internal failures return 500.

| Status | Body | Condition |
|---|---|---|
| 401 | `{"success":false,"message":"Unauthorized."}` | Missing/invalid token. |
| 200 | `{"success":false,"message":"Not available"}` | Feature flag off. |
| 200 | `{"success":false,"message":"user_id is required."}` | `user_id` / `received_user_id` / `call_id` missing or empty. |
| 200 | `{"success":false,"message":"Invalid user_id."}` | Non-numeric / <= 0. |
| 200 | `{"success":false,"message":"end_reason must be one of: ended, rejected, not_answered, failed."}` | Unknown `end_reason`. |
| 200 | `{"success":false,"message":"ended_by must be one of: caller, receiver, system."}` | Unknown `ended_by`. |
| 200 | `{"success":false,"message":"ended_by_user_id is required when ended_by is caller or receiver."}` | Missing when required. |
| 200 | `{"success":false,"message":"ended_by_user_id must be null when ended_by=system."}` | Provided when forbidden. |
| 200 | `{"success":false,"message":"Invalid ended_by_user_id."}` | Non-numeric / <= 0. |
| 200 | `{"success":false,"message":"Invalid duration_seconds."}` | Non-numeric / negative. |
| 403 | `{"success":false,"message":"user_id must match authenticated user."}` | Token-user mismatch. |
| 200 | `{"success":false,"message":"Call not found."}` | `call_id` not in `user_calls`. |
| 200 | `{"success":false,"message":"Not a participant of this call."}` | `user_id` + `received_user_id` don't match the call's two participants. |
| 200 | `{"success":false,"message":"ended_by_user_id must be one of the call participants."}` | Pointing at a user outside the pair. |
| 500 | `{"success":false,"message":"Error saving call status."}` | Unexpected DB error (also logged server-side). |

---

## cURL examples

```bash
BASE="https://demolivedb.himaapp.in/api/auth"
TOKEN="<bearer token>"
```

### UC1 — normal end (caller hung up a connected call)

```bash
curl -sS -X POST "$BASE/call_status" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": 72,
    "received_user_id": 73,
    "call_id": 46580081,
    "end_reason": "ended",
    "ended_by": "caller",
    "ended_by_user_id": 72,
    "duration_seconds": 60
  }'
```

### UC3 — receiver taps Reject before pickup

```bash
curl -sS -X POST "$BASE/call_status" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": 73,
    "received_user_id": 72,
    "call_id": 46580081,
    "end_reason": "rejected",
    "ended_by": "receiver",
    "ended_by_user_id": 73,
    "duration_seconds": 0
  }'
```

### UC4 — caller cancels before pickup

```bash
curl -sS -X POST "$BASE/call_status" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": 72,
    "received_user_id": 73,
    "call_id": 46580081,
    "end_reason": "not_answered",
    "ended_by": "caller",
    "ended_by_user_id": 72,
    "duration_seconds": 0
  }'
```

### UC5 — ring timed out with no user tap

```bash
curl -sS -X POST "$BASE/call_status" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": 72,
    "received_user_id": 73,
    "call_id": 46580081,
    "end_reason": "not_answered",
    "ended_by": "system"
  }'
```

### Flag-off probe

```bash
# When the feature flag is disabled server-side:
curl -sS -X POST "$BASE/call_status" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -d '{"user_id":72,"received_user_id":73,"call_id":1,"end_reason":"ended","ended_by":"caller","ended_by_user_id":72}'
# {"success":false,"message":"Not available"}
```

---

## Client integration notes

| Client event | Payload sketch |
|---|---|
| Receiver taps Reject (dialer still ringing) | `end_reason=rejected`, `ended_by=receiver`, `ended_by_user_id=<self>` |
| Caller presses Cancel before pickup | `end_reason=not_answered`, `ended_by=caller`, `ended_by_user_id=<self>` |
| Ring timer expires on caller side with no answer | `end_reason=not_answered`, `ended_by=system` (omit `ended_by_user_id`) |
| Either side hangs up a connected call | `end_reason=ended`, `ended_by=<self_role>`, `ended_by_user_id=<self>` |
| SDK error before pickup (Agora / token / network) | `end_reason=failed`, `ended_by=system` |

Clients should still call the legacy endpoints while the flag is being rolled
out, and rely on the `{success:false,"message":"Not available"}` probe to
detect whether `call_status` is active on this environment.

---

## Consumption on the read side

Once the flag is on and demo clients start writing, the updated readers in
`/var/www/demolivedb` classify calls as follows:

- `calls_list` with `type=missed` and `missed_call_count` include any row
  where `end_reason IN ('rejected','not_answered')`, **plus** legacy rows
  (`end_reason IS NULL`) that match the historical null-time + 50s heuristic.
- `calls_list` regular (recent) view keeps `started_time IS NOT NULL` but
  explicitly excludes `end_reason IN ('rejected','not_answered')` so
  rejected-after-pickup edge cases don't pollute the recent list.

Live readers on `himaapp.in` still use the legacy heuristic only; this is
deliberate and safe — rows with `end_reason` set but `started_time IS NULL`
will simply classify as "missed" on the live app via its existing logic, which
is the pre-change behaviour.

---

## Schema reference

New columns on `user_calls`, applied by migration
[`2026_04_23_100000_add_end_reason_to_user_calls_table.php`](database/migrations/2026_04_23_100000_add_end_reason_to_user_calls_table.php):

| Column | Type | Default | Written by |
|---|---|---|---|
| `end_reason` | `ENUM('ended','rejected','not_answered','failed')` | `NULL` | `call_status` endpoint only |
| `ended_by_user_id` | `BIGINT UNSIGNED` | `NULL` | `call_status` endpoint only |

Indexes on these columns are packaged in a **separate deferred migration**
[`2026_04_23_100001_add_indexes_for_end_reason_on_user_calls_table.php`](database/migrations/2026_04_23_100001_add_indexes_for_end_reason_on_user_calls_table.php)
to be run during a low-traffic window (`ALGORITHM=INPLACE, LOCK=NONE`).

No existing columns were altered. Schema changes are purely additive and
rollback-safe: reverting only the migrations leaves NULLs on all rows.
