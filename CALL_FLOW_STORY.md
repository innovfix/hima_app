# Hima call flow — the full story

A narrative walk-through of how an Agora audio/video call travels from one user tapping "Call" on a profile card to both sides hanging up. Every stage cites the file + line number so you can jump straight to the code.

This replaces "where does the thing live" spelunking with "here's the plot."

---

## The cast

| Role | Files |
|---|---|
| **Initiator (caller) screen** — "Connecting…" ring-out screen | `MaleCallConnectingActivity.kt`, `FemaleCallConnectingActivity.kt` |
| **Receiver (callee) screen** — Accept / Reject ring-in screen | `MaleCallAcceptActivity.kt`, `FemaleCallAcceptActivity.kt` |
| **In-call screens** — what both sides see while talking | `MaleAudioCallingActivity.kt`, `FemaleAudioCallingActivity.kt`, `MaleVideoCallingActivity.kt`, `FemaleVideoCallingActivity.kt` |
| **Transport** — the FCM push service | `MyFirebaseMessagingService.kt` |
| **Notification actions** — heads-up Accept/Reject tap handling | `CallActionReceiver.kt` |
| **OS phone integration** — system-managed ConnectionService | `HimaTelecomManager.kt`, `HimaConnection.kt` |
| **Cold-launch handoff** — handle FCM that arrived while process was killed | `SplashScreenActivity.kt` + `BaseApplication.kt` (state cache) |
| **Audio routing** — earpiece / speaker / BT switching | `CallAudioRouter.kt`, `BluetoothCallWatcher.kt` |
| **UX overlays** — signal icon, reconnect pill, audio-route sheet | `CallQualityUi.kt`, `BottomSheetAudioRoute.kt` |
| **Server-side bookkeeping** — REST endpoint tracking who ended and why | `CallStatusViewModel.kt` (`/api/auth/call_status`) |

---

## Act 1 — The caller dials

1. **User A taps "Call" on a profile card.** The app launches the gender-matched Connecting screen:
   - Male initiator → `MaleCallConnectingActivity.onCreate()` at line 88.
   - Female initiator → `FemaleCallConnectingActivity.onCreate()` at line 85.

   The screen immediately shows both avatars (caller left, receiver right), a pink "Connecting…" string, and an animated dots row.

2. **The app creates a call row on the server.** `getCallId()` calls `femaleUsersViewModel.callFemaleUser(userId, receiverId, callType, callSwitch=0)` (male initiator, `MaleCallConnectingActivity.kt:340`) or the `callMaleUser` equivalent on the female side. The response carries a fresh `call_id`, the receiver's current `audio_status` / `video_status` (so an offline peer can be intercepted early), and — on the caller's side — a coin deduction.

   If the peer's `audio_status == 0` (or `video_status == 0` for a video call), the flow aborts with an "User is offline" toast and navigates back to Main (`MaleCallConnectingActivity.kt:363-376`).

3. **Agora token pre-fetch starts in the background** (`MaleCallConnectingActivity.kt:472`). By the time the peer accepts a few seconds later, the token is already sitting in memory so the join is instant.

4. **An FCM push is fired at the peer.** `sendCallNotification(userId, receiverId, callType, "incoming call <callId> <avatar> <name>")` (`MaleCallConnectingActivity.kt:469`) generates a unique channel name (`senderId_timestamp`), sends the push, and starts a **20-second ring timer** in `MaleCallConnectingActivity.timeoutRunnable` (line 66).

5. **The caller waits.** Three things can happen from here:
   - The peer accepts → Act 3.
   - The peer rejects → we receive `callStatus: "rejected"` and route back to Main.
   - The 20-second timer expires → `disconnectCall()` fires and posts `/call_status` with `end_reason=not_answered, ended_by=receiver` (per the Option-1 attribution plan) — see `CallStatus` logcat tag.

---

## Act 2 — The peer is paged (three paths)

FCM lands on the receiver's device. The same message gets handled very differently depending on app state. `MyFirebaseMessagingService.onMessageReceived()` (`MyFirebaseMessagingService.kt:105`) routes the call.

### Path A — App is in the foreground

`MyFirebaseMessagingService.kt:307` detects the app is live, launches the gender-matched Accept activity directly, and the ring UI takes over the screen. The ringtone starts immediately (`playIncomingCallSound()` at line 252 / 357).

### Path B — App is backgrounded

`HimaTelecomManager.tryAddIncomingCall()` (line 62–93) registers a self-managed `PhoneAccount` on Android 8+ and hands the call to the system Telecom service. A WhatsApp-style **CallStyle notification** (`notifyIncomingCallWithCallStyle`, line 1100 of MyFirebaseMessagingService) is posted with Accept/Reject action buttons. Channel `calls_v3`, `setBypassDnd(true)`, 35-second auto-dismiss timeout, vibration pattern `0, 1000, 500, 1000`.

### Path C — App process is dead (cold start)

FCM still delivers, but the activity can't be launched directly. Instead the handler stashes the call details on `BaseApplication` (`setIncomingCall(senderId, callType, channelName, callId)` at line 251) and fires the notification. When the user taps that notification:
1. `SplashScreenActivity.onCreate()` runs first (line 100).
2. Inside `initUI()` (line 242) it checks `BaseApplication.isIncomingCall()`.
3. If true, after a 2-second delay it launches `FemaleCallAcceptActivity` with the cached extras (line 297-306) and calls `finish()` on itself.

### DND suppression (all paths)

Before any of the above, line 113-116 of `MyFirebaseMessagingService` calls `isDndActiveStatic(userData)`. If the receiver set DND and the "until" timestamp hasn't elapsed, the FCM is dropped on the floor — no ring, no notification, no missed-call log. The caller's device still runs the 20-second timer and ends up posting `not_answered/system`.

---

## Act 3 — Someone answers (or rejects)

The ring UI offers three user actions: Accept button, Reject button, or ignore (timeout).

### Accept — from the Accept activity

`FemaleCallAcceptActivity.kt:172-210` / `MaleCallAcceptActivity.kt:162-232`:

1. **Male-only coin gate** — if the user's coin balance is under 10, the tap is silently turned into a "rejected" FCM and a toast (`MaleCallAcceptActivity.kt:164-194`). Female side has no coin check.
2. Send `sendCallNotification(..., "accepted")` back to the caller.
3. `HimaTelecomManager.markActive()` flips the self-managed Telecom connection to ACTIVE so the OS stops treating this as a ring.
4. Stop the ringtone, cancel the CallStyle notification, clear `BaseApplication.incomingCall` state.
5. Start the matching `CallingActivity` with `CHANNEL_NAME`, `RECEIVER_ID`, `CALL_ID`, plus the pre-fetched `AGORA_TOKEN` / `AGORA_APP_ID` as extras. `IS_CALLER=false` is the default — only Connecting-side flows set it to `true`.

### Accept — from the system heads-up notification

`CallActionReceiver.onReceive("ACTION_ACCEPT_CALL")` (`CallActionReceiver.kt:36-75` for female, `142-179` for male) is a `BroadcastReceiver` so there's no activity window between tap and action. It routes to the right `CallingActivity` directly, stops the ringtone, cancels the notification, and marks Telecom active.

### Reject

Same two entry points (button tap, notification tap) — both end up sending `"rejected"` back via FCM **and** posting to the new `/call_status` endpoint with `end_reason=rejected, ended_by=receiver, ended_by_user_id=<self>` (see `CallStatusViewModel.kt`). The Male side additionally pings `call_reject_count` for a legacy stats counter. Then: end the Telecom connection with `DisconnectCause.REJECTED`, stop ring, navigate to Main.

### Timeout (35 s, no tap)

The CallStyle notification auto-dismisses (`setTimeoutAfter(35_000L)`). The receiver never posts anything — only the caller's 20-second timer on the other device ends up writing `not_answered/receiver` per the Option-1 rule.

---

## Act 4 — Both sides land in the channel

Caller and receiver both arrive at the gender + media matched `CallingActivity`. Entry is **symmetric** from here on — the only per-side difference is the `IS_CALLER` boolean (true on the connecting side, false on the accept side).

1. **`onCreate` unpacks extras** — `CHANNEL_NAME`, `RECEIVER_ID`, `CALL_ID`, `AGORA_TOKEN`, `AGORA_APP_ID`, `IS_CALLER` (e.g. `FemaleAudioCallingActivity.kt:358-360`).

2. **Permission + engine boot** — `setupAudioSDKEngine()` (`MaleAudioCallingActivity.kt:265` / video variants) creates the Agora `RtcEngine`, attaches the local `IRtcEngineEventHandler`, sets profile `AUDIO_PROFILE_SPEECH_STANDARD`, enables audio (+ video for the video activities), initialises `CallAudioRouter`, and registers `CallPhoneStateHelper` and the new `BluetoothCallWatcher` inside `setupCallInterruptHandlers()` (line 301).

3. **Join Agora** — `joinChannel()` (`MaleAudioCallingActivity.kt:1274`, `FemaleAudioCallingActivity.kt:1389`):
   - `ChannelMediaOptions { channelProfile = COMMUNICATION; clientRoleType = BROADCASTER; publishMicrophoneTrack = true; publishCameraTrack = <video?>; autoSubscribeAudio = true; autoSubscribeVideo = <video?> }`.
   - `engine.joinChannel(token, channelName, uid=0, options)`.

4. **`onJoinChannelSuccess`** (`MaleAudioCallingActivity.kt:1073` and the three analogues) — sets `isJoined = true`, starts a second timeout that listens for the remote user to arrive.

5. **`onUserJoined`** — the peer has successfully joined the same Agora channel.
   - `startTime = dateFormat.format(Date())` in IST.
   - `startCallingService()` kicks off a foreground service so the OS won't kill the call if the activity goes offscreen.
   - `femaleCallAttend` / `getRemainingTime` API calls record the call is now live and fetch the remaining per-call seconds.
   - Firebase Analytics + AppsFlyer fire `call_started`.

---

## Act 5 — The call is live

Both sides now see the other, the timer starts ticking, and the brand-new in-call UX we just added kicks in.

### Real-time signal quality (new)

Every second Agora fires `onNetworkQuality(uid, txQuality, rxQuality)`. Each of the four `CallingActivity`s forwards this to `CallQualityUi.apply(..., rxQuality, null)` which maps:
- `QUALITY_EXCELLENT / GOOD` → `ic_signal_strong` (green).
- `QUALITY_POOR / BAD` → `ic_signal_weak` (yellow).
- `QUALITY_VBAD / DOWN` → `ic_signal_poor` (red).

### Reconnect banner (new)

Agora fires `onConnectionStateChanged(state, reason)` whenever the underlying socket transitions. The same helper flips `@id/reconnect_banner` (the "Reconnecting…" pill under the top bar) between visible and gone based on:
- `CONNECTION_STATE_RECONNECTING / FAILED` → show.
- `CONNECTION_STATE_CONNECTED / DISCONNECTED` → hide.

So when the user drops onto a weaker network, the pill appears and the signal icon goes yellow/red; when connectivity recovers the pill vanishes.

### Mic mute (existing)

Button `@id/btn_MuteUnmute` in every layout → `toggleMute()` (e.g. `MaleAudioCallingActivity.kt:1640`). Single call: `engine.muteLocalAudioStream(isMuted)` + swap `mute_img` ↔ `unmute_img`. `isMuted` now survives rotation and process death — see Act 5b.

### Audio-route picker (new)

Tapping `@id/btn_speaker` enters `onSpeakerButtonClicked()`:
- If no Bluetooth headset is connected (`CallAudioRouter.isBluetoothConnected()`), fall through to the existing binary `toggleSpeaker()` — same 2-state UX as before.
- If a BT headset is present, open `BottomSheetAudioRoute.show(...)`. The sheet shows three rows: Earpiece / Speaker / Bluetooth. Selecting one calls `applyAudioRoute(route)` which dispatches to `CallAudioRouter.forceEarpiece() / forceSpeaker() / forceBluetooth()` and swaps the button icon.

The router's routes use `setCommunicationDevice()` on API 31+ and the legacy `isSpeakerphoneOn` / `startBluetoothSco` / `stopBluetoothSco` on older devices.

### Live Bluetooth watcher (new)

`BluetoothCallWatcher` registers a `BroadcastReceiver` for `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` in `setupCallInterruptHandlers()` (alongside the existing `CallPhoneStateHelper` pattern). When the user plugs in a BT headset mid-call, the next speaker-button tap sees `isBluetoothConnected() == true` and opens the picker. Plug out — next tap reverts to the binary toggle. No automatic route switch; the user always chooses.

### Video-only: camera flip (new)

Both video activities now have an `ImageButton @id/btn_camera_flip` overlaid on the local-preview CardView. A single `engine.switchCamera()` flips front ↔ back. Wrapped in `runCatching` so a hardware hiccup logs a warning rather than crashing the call.

### Act 5b — Surviving process death

`onSaveInstanceState` (e.g. `MaleAudioCallingActivity.kt` near the end) stashes `isMuted` + `isSpeakerOn` into the bundle. `onRestoreInstanceState` reapplies them to Agora (`muteLocalAudioStream`, `setEnableSpeakerphone`) and to the button icons. So a screen rotation or a low-memory kill no longer leaves the UI and the engine disagreeing.

---

## Act 6 — Hang up

Three ways a call ends.

### Normal hang-up (either side)

User taps the end-call button → `showEndCallConfirmationDialog()` (`MaleAudioCallingActivity.kt:1637`). On confirm:
1. Legacy `CallDropStatusViewModel.saveCallDropStatus(... = 1)` for back-compat.
2. New `CallStatusViewModel.saveCallStatus(end_reason=ended, ended_by=caller|receiver by IS_CALLER, ended_by_user_id=self)` — this is what the recents screen reads to classify the row.
3. `leaveChannel(binding.LeaveButton)` → `engine.leaveChannel()` → Agora fires `onLeaveChannel`.
4. `release()` on the engine, `CallAudioRouter.release()`, `phoneStateHelper.unregister()`, `btWatcher.unregister()`.
5. Navigate to Main.

### Peer hangs up first

Other user's device fires `onUserOffline(uid, reason)` on the local Agora handler (line 1099 in `MaleAudioCallingActivity`). Flow is the same as normal hang-up: `updateCallEndDetails()`, stop timer, leave channel, go to Main. The peer already wrote `ended_by=<their side>` — this side's `/call_status` post is idempotent (first-write-wins).

### Network drops mid-call

`onConnectionLost` fires. With the new UX the pill already shows "Reconnecting…"; Agora tries to rejoin. If it succeeds (`onRejoinChannelSuccess`), the banner hides and the call continues. If it gives up (`CONNECTION_STATE_FAILED`), the engine teardown path runs and the call ends as `end_reason=failed / ended_by=system`.

---

## Audio vs. video — the diff

| Aspect | Audio | Video |
|---|---|---|
| Agora setup | `enableAudio()` only | `enableVideo() + enableAudio()` |
| `ChannelMediaOptions` | `publishCameraTrack=false`, `autoSubscribeVideo=false` | Both `true` |
| Remote rendering | none (no SurfaceView) | `setupRemoteVideo(uid)` inside `onUserJoined` |
| Local preview | none | `setupLocalVideo()` + the new camera-flip overlay |
| Camera permission | not requested | `CAMERA` + `RECORD_AUDIO` |

---

## Male-initiated vs female-initiated — the diff

| Aspect | Male caller | Female caller |
|---|---|---|
| Call-create endpoint | `callFemaleUser(male_id, female_id, type)` | `callMaleUser(female_id, male_id, type)` |
| Channel naming | `senderId_timestamp` | `channel_$callId` |
| Pricing gate on accept | male-accepter's coin check (≥10) in `MaleCallAcceptActivity.kt:164` | female-accepter has no coin check |
| Ring UI flag | full flow | optional `designOnly = true` bypass for design previews |
| Legacy counter | `callRejectCount` on male-side reject (`MaleCallAcceptActivity.kt:239`) | no equivalent |

---

## End-to-end sequence

```mermaid
sequenceDiagram
    participant A as Caller (A)
    participant API as Hima backend
    participant FCM as FCM
    participant B as Peer (B)
    participant AG as Agora cloud

    A->>A: Tap "Call" on profile
    A->>API: callFemaleUser(...)
    API-->>A: call_id, audio/video_status
    A->>API: getAgoraToken(channel) [prefetch]
    A->>API: send_fcm_notification("incoming call ...")
    API->>FCM: push to B
    FCM-->>B: data-only FCM

    alt B app foreground
        B->>B: Launch *CallAcceptActivity
    else B app backgrounded
        B->>B: Telecom + CallStyle notification
    else B app killed
        B->>B: Stash on BaseApplication;<br/>SplashScreenActivity resumes on open
    end

    B->>B: Ringtone + UI
    B->>API: send_fcm_notification("accepted")
    API->>FCM: push to A
    FCM-->>A: accepted
    A->>A: Launch *CallingActivity (IS_CALLER=true)
    B->>B: Launch *CallingActivity (IS_CALLER=false)

    par Both join Agora
        A->>AG: joinChannel(token, channel)
        B->>AG: joinChannel(token, channel)
    end

    AG-->>A: onJoinChannelSuccess
    AG-->>B: onJoinChannelSuccess
    AG-->>A: onUserJoined(B.uid)
    AG-->>B: onUserJoined(A.uid)

    loop During call
        AG-->>A: onNetworkQuality → CallQualityUi
        AG-->>B: onNetworkQuality → CallQualityUi
        Note over A,B: Mic mute, audio route picker,<br/>camera flip all available.
    end

    A->>AG: leaveChannel() (hang up)
    AG-->>B: onUserOffline(A.uid)
    A->>API: POST /call_status (end_reason=ended)
    B->>API: POST /call_status (idempotent / first-write-wins)
```

---

## Where to start when debugging

| Symptom | Look here first |
|---|---|
| Caller sees "Connecting…" forever | `MaleCallConnectingActivity.getCallId()` and `callFemaleUser` response observer. Check `audio_status`/`video_status` on the peer row. |
| Peer never rings | FCM delivery → `MyFirebaseMessagingService.onMessageReceived()` DND check → Telecom path in `HimaTelecomManager.tryAddIncomingCall()`. |
| Peer's cold-start ring is missing | `BaseApplication.isIncomingCall()` → `SplashScreenActivity.initUI()` line 287. |
| Audio one-way | Permissions in `setupAudioSDKEngine()`; `CallAudioRouter.currentRoute()` output; `onUserMuteAudio` events. |
| Weak connection not indicated | `onNetworkQuality` not firing, or `ivSignalStrength` binding null. Check `CallQualityUi.apply` logs. |
| BT headset not offered | `CallAudioRouter.isBluetoothConnected()` returning false; `BluetoothCallWatcher` receiver not registered. |
| Reconnect pill stuck visible | Agora never fires `CONNECTION_STATE_CONNECTED` after a drop — check network, then the engine release path. |
| Recents screen shows wrong end reason | `/call_status` first-write-wins. Check `CallStatus` logcat tag for the exact payload from each device. |

Every one of those has `Log.d(...)` / `Log.w(...)` calls at the cited locations — if you tail logcat with `adb logcat -s CallStatus:* SocketIOCheck:* CallAudioRouter:* BluetoothCallWatcher:*` during a real call you'll see the full story unfold line by line.
