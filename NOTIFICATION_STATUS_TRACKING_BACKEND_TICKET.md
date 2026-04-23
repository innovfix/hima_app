# Backend ticket: User notification-enabled tracking

**Audience:** Backend / API team
**Endpoints touched:** new `POST .../api/auth/update_notification_status`; minor additions to `users` table; read-side helper for FCM push skip.
**Priority:** Medium (analytics + re-engagement; we currently cannot tell how many users have silenced the app at the OS level, and we waste FCM sends on users whose devices will drop them).

---

## 1. Motivation

The Android client requests and re-requests `POST_NOTIFICATIONS` in several places, but **none of that state is ever reported to the server**. Product keeps asking questions we cannot answer:

1. **What percentage of users have notifications enabled at the OS level?**
2. **Which users revoked the permission after install?** (We can't target them for a re-enable campaign.)
3. **Why is our FCM delivery rate lower than the send rate?** (Partly: we push to users whose OS has notifications off, so the system drops them silently.)
4. **Is the "enable notifications" nudge (`NotificationImportanceActivity`) actually converting?** We have no before/after signal.

Current client behaviour, with file:line references so backend can verify against the source:

| Event | What the client does today | Gap |
|---|---|---|
| App start, Android 13+ | `MainActivity.kt:332-338` checks `POST_NOTIFICATIONS`, fires the system dialog once per day. | Result of the permission check/dialog is only stored locally in `app_prefs` (`notif_permission_last_asked` timestamp). |
| App start (all versions) | `BaseApplication.kt:317-321` does the same check. | Same — never reported. |
| Female home, when audio/video is on | `FemaleHomeFragment.kt:237-282` asks for `POST_NOTIFICATIONS` and tracks local `notif_permission_last_asked`. | Never reported. |
| User opens `NotificationImportanceActivity` after denying | Reads `NotificationManagerCompat.areNotificationsEnabled()` on resume (`NotificationImportanceActivity.kt:72-81`) to decide whether to finish. | Never reported. |
| User toggles notifications off in Android Settings while app is backgrounded | App has no hook — next `onResume` of any activity could detect this. | Currently ignored; backend keeps pushing. |

Existing notification-related endpoints (`ApiManager.kt`) do **not** cover this:

- `set_female_notification_preference` / `get_female_notification_preference` (`ApiManager.kt:1067`, `ApiManager.kt:2674`) — per-pair subscription toggle (a female opting into a specific male's online alerts, used by `ChatListAdapter.kt:309`). Unrelated to OS permission.
- `send-fcm-notification` / `send_message_notification` — outbound, not a status report.
- `list_creator_online_notifications` — read path.

We need a single, explicit `user_id → notifications_enabled` fact written to the server on every app open and on every permission state change, so we can (a) report on it, (b) target re-engagement, and (c) short-circuit the FCM send path for users whose devices will ignore the push anyway.

---

## 2. Schema change (one migration)

Table: `users`.

```sql
ALTER TABLE users
  ADD COLUMN notifications_enabled          TINYINT(1)  NOT NULL DEFAULT 1,
  ADD COLUMN post_notifications_granted     TINYINT(1)  NOT NULL DEFAULT 1,
  ADD COLUMN notifications_status_updated_at TIMESTAMP  NULL     DEFAULT NULL,
  ADD COLUMN notifications_os_version       VARCHAR(16) NULL     DEFAULT NULL;

CREATE INDEX idx_users_notifications_enabled ON users (notifications_enabled);
```

- `notifications_enabled` — the authoritative "will this device surface a notification if we push?" value. Derived client-side as `NotificationManagerCompat.areNotificationsEnabled()`. Default `1` on create so existing rows are assumed-enabled until the client reports otherwise (matches today's assumption).
- `post_notifications_granted` — Android 13+ runtime permission (`POST_NOTIFICATIONS`). Separated from `notifications_enabled` because a user can grant the runtime permission but still have the channel or app-level toggle off, and vice versa on < API 33. Both needed for accurate analytics.
- `notifications_status_updated_at` — last time the client reported. Lets us find stale rows (e.g. users we haven't heard from in 30 days — treat as unknown).
- `notifications_os_version` — e.g. `"33"`, `"34"`. Optional, but cheap to capture and useful for slicing (Android 13+ is the only version where `POST_NOTIFICATIONS` exists, so analytics will want to filter on it).

No backfill required. Clients will start writing on their first app open after shipping.

---

## 3. New endpoint — `POST /api/auth/update_notification_status`

### Request

`POST /api/auth/update_notification_status` with `application/json`:

```json
{
  "user_id":                   678,
  "notifications_enabled":     1,
  "post_notifications_granted": 1,
  "os_version":                "34"
}
```

| Field                        | Type   | Required | Notes                                                                                       |
|------------------------------|--------|----------|---------------------------------------------------------------------------------------------|
| `user_id`                    | int    | yes      | Authenticated user.                                                                         |
| `notifications_enabled`      | 0/1    | yes      | From `NotificationManagerCompat.areNotificationsEnabled()`.                                 |
| `post_notifications_granted` | 0/1    | yes      | On Android < 13 the client sends `1` (permission doesn't exist). Backend treats both the same. |
| `os_version`                 | string | no       | Android SDK int as string. Used for slicing only.                                           |

### Behavior

1. Validate `user_id` matches the authenticated session.
2. Update `users` row: set the four new columns, `notifications_status_updated_at = NOW()`.
3. Idempotent — if the incoming values match the stored values, still bump `notifications_status_updated_at` so we know the client is alive.
4. Respond:

```json
{ "success": true, "message": "Notification status updated" }
```

Error cases:

```json
{ "success": false, "message": "User not found" }
{ "success": false, "message": "Invalid payload" }
```

HTTP 200 with `success:false` is acceptable — matches the `call_drop_status` / `chat-delete` convention.

---

## 4. Read-side helper: skip FCM push when `notifications_enabled = 0`

Low-effort win that pays for this ticket on day one. Wherever the backend currently constructs an FCM push to a user (message-received pings, incoming-call pings, creator-online pings, generic marketing), gate the send:

```php
if (user.notifications_enabled == 0) {
    // Optionally still deliver the data payload (data-only FCM) so the app can update
    // silently, but skip the notification payload — the OS would drop it anyway.
    skip_notification_payload = true;
}
```

Two caveats so we don't break call reliability:

- **Incoming-call pushes (`send-fcm-notification` with `call_type`) must still fire** — those use a data payload to trigger `MyFirebaseMessagingService.notifyIncomingCallWithCallStyle` (`MyFirebaseMessagingService.kt:892`), which builds a `CallStyle` notification. The OS will suppress the visible shade UI when the user has disabled notifications, but the data payload still wakes the service, which is what the Telecom-linked CallStyle path depends on. **Do not** gate the `send-fcm-notification` endpoint on `notifications_enabled`.
- **Chat message pushes** (`send_message_notification`) are pure notification payloads — safe to skip.

Concretely: gate marketing / non-call pushes, leave call pushes alone.

---

## 5. Analytics queries product will want on day one

```sql
-- % of users with notifications enabled, by gender
SELECT gender,
       SUM(notifications_enabled) / COUNT(*) AS pct_enabled
FROM users
WHERE notifications_status_updated_at > NOW() - INTERVAL 7 DAY
GROUP BY gender;

-- Users who revoked after install (candidate list for re-enable campaign)
SELECT id, name, phone, notifications_status_updated_at
FROM users
WHERE notifications_enabled = 0
  AND notifications_status_updated_at > NOW() - INTERVAL 14 DAY
ORDER BY notifications_status_updated_at DESC;

-- Split: granted runtime permission vs actually seeing notifications
SELECT post_notifications_granted,
       notifications_enabled,
       COUNT(*) AS n
FROM users
GROUP BY post_notifications_granted, notifications_enabled;
```

The last query is particularly useful — it's the only way to see users who tapped "Allow" on the dialog but then silenced the channel in settings (a real case we currently can't detect).

---

## 6. Auth / ownership rules (explicit)

- Only the authenticated user may update their own row. `user_id` in the payload must match the session.
- No admin override needed.
- No rate limit required for v1 — client fires at most once per app open plus once per permission callback (see section 8). If abuse appears, cap at one write per `user_id` per minute.

---

## 7. Feature flag + rollout

- Put the new endpoint behind a server-side flag `FEATURE_NOTIFICATION_STATUS_V1`.
- When the flag is **off**, the endpoint returns `{success:false, message:"Not available"}`. The client treats `success:false` as a no-op (logs and moves on) — no regression.
- When the flag is **on**, the client fires on every qualifying event (section 8).
- Backend should land the schema migration and the endpoint **before** the client flips, so early adopter clients don't 404.
- The FCM skip-logic in section 4 can ship independently (and should be flag-gated separately — `FEATURE_SKIP_FCM_WHEN_DISABLED`) so we can A/B test engagement impact.

---

## 8. Manual test plan

Pre-req: one real Android 13+ device logged in as user `A`.

1. **Fresh install, grant on first prompt:** Install app, complete login, tap Allow on the OS dialog. Expect one POST with `notifications_enabled=1`, `post_notifications_granted=1`.
2. **Fresh install, deny on first prompt:** Install, deny the OS dialog. Expect one POST with `notifications_enabled=0`, `post_notifications_granted=0`. `NotificationImportanceActivity` should open; the row should already reflect `0`.
3. **Revoke from Settings:** With app granted, open Android Settings → App → Notifications → toggle off. Return to app (any activity's `onResume`). Expect a POST with `notifications_enabled=0`, `post_notifications_granted=1` (Android 13: permission stays granted but channel master is off).
4. **Re-enable from the nudge:** From state (2), open `NotificationImportanceActivity`, tap Enable, grant. `onResume` detects the change and finishes the activity (today's behaviour — `NotificationImportanceActivity.kt:48-55`). Expect a POST with both fields `1`.
5. **Android < 13 device:** Install on Android 12 or lower. `post_notifications_granted` should always be `1` in the payload; `notifications_enabled` reflects `NotificationManagerCompat.areNotificationsEnabled()`.
6. **Idempotent ping:** Kill and reopen the app without changing anything. Expect a POST with the same values as before; backend bumps `notifications_status_updated_at` without changing the flags.
7. **FCM skip (section 4):** With user in state (2), trigger a chat message from another user. Expect no visible notification (correct — and today's behaviour because the OS drops it). In backend logs, verify we skipped the send. Then trigger an incoming call from another user — the call CallStyle notification **must still fire** (section 4 caveat).
8. **Stale row:** Seed a user whose `notifications_status_updated_at` is 60 days old. Confirm analytics queries in section 5 exclude them when filtered on `> NOW() - INTERVAL 7 DAY`.
9. **Auth mismatch:** Hand-craft a POST with `user_id` belonging to another user. Expect `success:false`.

---

## 9. What the client will do (not shipped yet — coordinate with this ticket)

Client-side follow-up planned once backend contract is agreed:

- **Add** `ApiManager.updateNotificationStatus(userId, notificationsEnabled, postNotificationsGranted, osVersion, callback)` + a `@POST("update_notification_status")` interface entry, mirroring existing patterns (`ApiManager.kt:2674`).
- **Fire on app open:** Call it from `MainActivity.onCreate` (around `MainActivity.kt:332`) after the permission check resolves, so we capture state on every cold start.
- **Fire after permission callback:** Every `requestPermissionLauncher` callback (`MainActivity.kt:335`, `FemaleHomeFragment.kt:250`, `FemaleHomeFragment.kt:271`, `FemaleHomeFragment.kt:280`, `NotificationImportanceActivity.kt:66`) should fire the POST with the fresh result.
- **Fire on `onResume` of `MainActivity`:** Catches users who toggled notifications off in Settings while backgrounded. Cheap — one POST per foreground.
- **Reuse** the existing `hasNotificationPermission()` logic (`NotificationImportanceActivity.kt:72-81`) — extract to a shared util so all call sites report the same truth.
- **Don't** block UI on the response. Fire-and-forget, log the result.

Log tag: `NotifStatus`. Every call logs the request body and the response so we can trace skipped pushes back to the last status report.

---

## 10. Rollback

- Drop the new endpoint route.
- Leave the four added `users` columns in place (harmless; defaults keep existing behaviour).
- Turn off `FEATURE_SKIP_FCM_WHEN_DISABLED` — backend goes back to sending every push regardless of state.
- Client continues to compile — if the new endpoint 404s, the fire-and-forget call logs and moves on. No user-facing regression.
