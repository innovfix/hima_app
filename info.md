## Option 1 — Change the client to attribute ring timeout to the receiver (recommended, applied)

Android `.dev` ring-timeout handler changes from:

```
end_reason       = "not_answered"
ended_by         = "system"
ended_by_user_id = null
```

to:

```
end_reason       = "not_answered"
ended_by         = "receiver"
ended_by_user_id = <receiver user_id>
```

That way every `not_answered` row carries a clean attribution:

| Path                                                        | `ended_by`  | Meaning                          |
|-------------------------------------------------------------|-------------|----------------------------------|
| Caller hit Cancel before ring expired                       | `caller`    | "Kishore gave up"                |
| Ring expired, receiver never tapped                         | `receiver`  | "Kavya didn't pick up"           |
| SDK / network killed the call before either side could act  | `system`    | Truly no human at fault          |

`system` stays in the enum but becomes rare — only true SDK failures, not ordinary "didn't answer". This is the cleanest model.

**No backend change needed** — the current endpoint already accepts `ended_by='receiver'` with `ended_by_user_id=<receiver>`.

---

### Status: applied

- `MaleCallConnectingActivity.kt` — `disconnectCall()` ring-timeout path now sends `ended_by=receiver, ended_by_user_id=receiverId`.
- `FemaleCallConnectingActivity.kt` — same change in its `disconnectCall()`.
- Caller-cancel paths (back press / tvCancel) unchanged — still `ended_by=caller`.
- Log line now reads `timeout → not_answered/receiver` (was `/system`).
- Build: **BUILD SUCCESSFUL**.
