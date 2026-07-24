package com.gmwapp.hima.onesignal

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.gmwapp.hima.agora.FcmCallService
import com.gmwapp.hima.agora.telecom.HimaConnection
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import com.gmwapp.hima.retrofit.responses.UserData
import com.gmwapp.hima.utils.ActiveChatTracker
import com.gmwapp.hima.utils.ChatNotificationStore
import com.gmwapp.hima.utils.ChatNotifications
import com.gmwapp.hima.utils.DPreferences
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension
import org.json.JSONObject

/**
 * OneSignal Notification Service Extension.
 *
 * This is invoked by the OneSignal SDK BEFORE a push notification is displayed,
 * even when the app process is killed. We use it to suppress notifications
 * when the user has Do Not Disturb (DND) enabled and the dnd_until timestamp
 * has not yet passed.
 *
 * We also hijack `type == "message"` pushes so multiple chats from the same
 * sender collapse into a single WhatsApp-style MessagingStyle notification
 * (one stable id per peer, last N lines stacked) instead of spamming the tray
 * with N separate heads-ups.
 */
class OneSignalNotificationServiceExtension : INotificationServiceExtension {

    private val TAG = "OneSignalNSE_DND"

    companion object {
        const val ACTION_CHAT_REFRESH = "com.gmwapp.hima.ACTION_CHAT_REFRESH"
        /**
         * Fired for every `type=message` push so chat-list screens (FriendsTabFragment,
         * HomeFragment, CreatorChatFragment, MainActivity badge) can update in-place
         * instead of waiting for the 30s poll or pull-to-refresh.
         *
         * Extras:
         * - `peer_id` (Int): peer who sent the message
         * - `last_message` (String, optional): preview text
         * - `message_type` (String, optional): `text`|`image`|`audio`|`video`|`file`
         */
        const val ACTION_CHAT_LIST_REFRESH = "com.gmwapp.hima.ACTION_CHAT_LIST_REFRESH"
        const val EXTRA_PEER_ID = "peer_id"
        const val EXTRA_LAST_MESSAGE = "last_message"
        const val EXTRA_MESSAGE_TYPE = "message_type"
        // CM_006-live: carry the message id so the chat-list badge can bump in-place
        // from the push (no socket needed on the list) while staying deduped against
        // the socket path — both count the same id at most once.
        const val EXTRA_MESSAGE_ID = "message_id"

        /**
         * Fired for friend_request_received / friend_request_accepted / friend_request_rejected
         * pushes so FriendsListActivity (male) and FriendsHubFragment (female) can refresh
         * their tab-count badges without waiting for the next onResume.
         */
        const val ACTION_FRIEND_STATUS_CHANGED = "com.gmwapp.hima.ACTION_FRIEND_STATUS_CHANGED"
    }

    /**
     * ONESIGNAL_SWIPE_PARITY_2026_07_23 — start the full-screen ring UI directly.
     *
     * Only used on the foreground+unlocked branch, where FcmCallService runs in
     * backstop-only mode and therefore posts NO notification of its own. Mirrors the
     * intent extras built by [com.gmwapp.hima.utils.CallNotifications] so the accept
     * screen reads exactly the same keys on both delivery paths. Returns false if the
     * launch is refused, letting the caller fall back to the foreground-service path
     * rather than leaving the user with a silent, invisible call.
     */
    private fun launchAcceptActivity(
        context: Context,
        payload: com.gmwapp.hima.utils.CallNotifications.IncomingPayload
    ): Boolean {
        val target = if (payload.isMale) {
            com.gmwapp.hima.agora.male.MaleCallAcceptActivity::class.java
        } else {
            com.gmwapp.hima.agora.female.FemaleCallAcceptActivity::class.java
        }
        val intent = Intent(context, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_TYPE", payload.callType)
            putExtra("SENDER_ID", payload.senderId)
            putExtra("CHANNEL_NAME", payload.channelName)
            putExtra("CALL_ID", payload.callId)
            putExtra("Caller_NAME", payload.callerName)
            putExtra("Caller_Image", payload.callerImage.orEmpty())
        }
        return runCatching { context.startActivity(intent); true }
            .getOrElse {
                Log.e(TAG, "launchAcceptActivity threw senderId=${payload.senderId}: ${it.message}", it)
                false
            }
    }

    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        try {
            val context: Context = event.context
            val prefs = DPreferences(context)
            val userData = prefs.getUserData()

            // AVAILABILITY_DISABLED_SYNC — reflect a server-side auto-disable in the
            // local cache so Home's availability toggle stops showing a stale ON.
            // OFF-only / never auto-re-enable (v1109 ghost-online guard). Cache-only
            // here; the creator taps the toggle to come back online. Does not
            // preventDefault, so the explanatory push still shows.
            syncAvailabilityDisabledToCache(prefs, userData, event.notification.additionalData)

            if (isDndActive(userData)) {
                Log.d(TAG, "DND is active — suppressing OneSignal notification")
                // Returning null prevents the notification from being shown
                event.preventDefault()
                return
            }

            // 2026-06-05 v1108 FIX #6: RE-ADDED the in-active-call suppression.
            // Original removal was over-correction — the stuck-state risk that
            // motivated the removal is now neutralised by:
            //   * BaseApplication.onCreate resets isCallActive=false on cold start
            //   * BaseApplication.onCreate resets AudioManager.mode to MODE_NORMAL
            //   * activeCallActivityCount also reset to 0
            // Without this suppression the OneSignal extension was firing the
            // CallStyle ring notification on top of an already-active call
            // (creator spoke about getting "missed call" notifications mid-call).
            // FCM service has its own busy-check that handles missed-call posting
            // for genuinely-busy cases, so this suppression is purely about
            // not double-ringing on top of an active in-app call.
            if (com.gmwapp.hima.BaseApplication.getInstance()?.isInActiveCall() == true &&
                isCallPush(event.notification) &&
                !isMissedCallPush(event.notification)
            ) {
                Log.d(TAG, "In active call — suppressing OneSignal incoming-call push")
                event.preventDefault()
                return
            }

            // Save notification_id + receive timestamp for conversion tracking.
            // Used later when user opens the app directly (without tapping the notification).
            try {
                val notifId = event.notification.additionalData?.optInt("notification_id", 0) ?: 0
                if (notifId > 0) {
                    context.getSharedPreferences("notif_track", Context.MODE_PRIVATE).edit()
                        .putInt("last_notif_id", notifId)
                        .putLong("last_notif_time", System.currentTimeMillis())
                        // Start a fresh funnel for this notification id (per-action dedupe
                        // lives under notif_counted_actions_<id>).
                        .remove("notif_counted_actions_$notifId")
                        .apply()
                    Log.d(TAG, "Saved last_notif_id=$notifId for conversion tracking")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save last_notif_id: ${e.message}")
            }

            // Route call pushes through our shared helper so OneSignal posts the
            // same CallStyle / missed-call UI as the FCM path. Both helpers
            // call `event.preventDefault()` themselves on success, so anything
            // matching here short-circuits the chat-message branch below.
            if (maybeHandleMissedCall(context, event)) return
            if (maybeHandleIncomingCall(context, event)) return

            // Notify friends-hub screens to refresh badges on friend-request pushes.
            maybeHandleFriendRequest(context, event)

            // Fold per-sender chat pushes into a single MessagingStyle notification.
            maybeHandleChatMessage(context, event)
        } catch (e: Exception) {
            Log.e(TAG, "DND check failed: ${e.message}")
            // On error, fall through and let the notification show normally
        }
    }

    /**
     * AVAILABILITY_DISABLED_SYNC (app-side, cache-only). When the server's
     * missed-call auto-disable cron turns this creator's availability off it
     * sends a `type=availability_disabled` push carrying `disabled_types`
     * (e.g. ["Audio","Video"]). We mirror that into the cached UserData so the
     * Home toggle reflects the real (OFF) state instead of a stale ON.
     *
     * Strictly OFF-only and display-only: we NEVER write status=1 from a push
     * (that auto-re-online path was the v1109 "ghost online" bug, removed in
     * v1110). The creator consciously taps the toggle to come back online.
     * Uses the direct setUserData (NOT the preserve-merge, which keeps
     * prev.audio_status and would drop this update). Wrapped so it can never
     * break the notification flow.
     */
    private fun syncAvailabilityDisabledToCache(
        prefs: DPreferences,
        userData: UserData?,
        data: JSONObject?
    ) {
        try {
            if (userData == null || data == null) return
            val type = data.optString("type", "").lowercase()
            if (type != "availability_disabled" && type != "call_status_disabled") return

            // Only turn OFF the channel(s) the server actually disabled. If the
            // payload omits disabled_types, fall back to both (the cron's common
            // full-disable case).
            var offAudio = true
            var offVideo = true
            val types = data.optJSONArray("disabled_types")
            if (types != null && types.length() > 0) {
                offAudio = false
                offVideo = false
                for (i in 0 until types.length()) {
                    when (types.optString(i, "").lowercase()) {
                        "audio" -> offAudio = true
                        "video" -> offVideo = true
                    }
                }
            }

            val newAudio = if (offAudio) 0 else userData.audio_status
            val newVideo = if (offVideo) 0 else userData.video_status
            if (newAudio == userData.audio_status && newVideo == userData.video_status) return

            // Direct write so the OFF sticks; the preserve-merge would keep the
            // stale ON. Never writes 1 from here.
            prefs.setUserData(userData.copy(audio_status = newAudio, video_status = newVideo))
            Log.d(TAG, "availability_disabled sync -> audio=$newAudio video=$newVideo")
        } catch (e: Exception) {
            Log.e(TAG, "availability_disabled sync failed: ${e.message}")
        }
    }

    /**
     * Detects "Missed call …" pushes from either a structured `type=missed_call`
     * payload or free-text title/body so server-side template variations are
     * still rendered with the rich custom UI.
     */
    private fun isMissedCallPush(notif: com.onesignal.notifications.IDisplayableNotification): Boolean {
        val data = notif.additionalData
        val type = data?.optString("type", "")?.lowercase().orEmpty()
        if (type == "missed_call" || type == "call_missed") return true
        // Status/availability notifications (e.g. the creator auto-disable push) can carry
        // "missed"/"call" in their copy — never treat those as missed calls, or they get
        // re-rendered as a fake "Tap to call back" that self-calls the recipient.
        if (type == "availability_disabled" || type == "call_status_disabled" || type == "call_status") return false
        val title = notif.title.orEmpty().lowercase()
        val body = notif.body.orEmpty().lowercase()
        // Match "missed call", "missed audio call" AND "missed video call". The audio/video
        // word sits between "missed" and "call", so contains("missed call") alone misses
        // those titles and the push wrongly renders as an incoming CallStyle banner.
        return (title.contains("missed") && title.contains("call")) ||
            (body.contains("missed") && body.contains("call"))
    }

    /**
     * If the push is an incoming-call push (and not a missed-call notice),
     * post our own [com.gmwapp.hima.utils.CallNotifications.showIncoming] and
     * suppress the OneSignal default. Returns true when handled.
     */
    private fun maybeHandleIncomingCall(
        context: Context,
        event: INotificationReceivedEvent
    ): Boolean {
        val data = event.notification.additionalData ?: return false
        if (!isCallPush(event.notification)) return false
        if (isMissedCallPush(event.notification)) return false

        // 2026-06-05 v1108 REAL FIX: backend sends "caller_id" and "type"
        // ("audio"/"video"); previous extraction only checked senderId/sender_id/
        // user_id and callType/call_type, so senderId became 0 → return false →
        // CallStyle ring UI never posted → creators only saw OneSignal default
        // text ("missed call") with NO RING. Added the two backend field names
        // so the call ring notification actually fires.
        val callType = firstNonEmpty(data, "callType", "call_type", "type")
        val senderId = data.optInt("senderId", 0).takeIf { it > 0 }
            ?: data.optInt("sender_id", 0).takeIf { it > 0 }
            ?: data.optInt("caller_id", 0).takeIf { it > 0 }
            ?: data.optInt("user_id", 0)
        val callId = data.optInt("call_id", 0)
        val channelName = firstNonEmpty(data, "channelName", "channel_name") ?: "default_channel"
        val callerName = firstNonEmpty(data, "callerName", "sender_name", "name", "title")
            ?: event.notification.title?.trim().orEmpty()
        val callerImage = firstNonEmpty(data, "callerImage", "sender_image", "image", "avatar").orEmpty()

        if (senderId <= 0) return false

        // Ghost-call guard (mirrors MyFirebaseMessagingService's call_id check):
        // OneSignal re-delivers queued/restored pushes on reconnect, so a ring
        // for a call the user already cut can arrive again. The FCM path drops
        // these by call_id; the OneSignal path previously only checked the push
        // AGE, so a recent redelivery slipped through and re-rang a dead call
        // (un-answerable — channel/token already gone). Refuse any call_id we
        // positively know was ended or busy-rejected.
        val app = com.gmwapp.hima.BaseApplication.getInstance()
        // GHOST_RING_2026_07_24 — wasForceRejectedCallId was MISSING here (the FCM path has
        // it). When the user SWIPES a ring it's recorded as force-rejected, not
        // recently-ended/busy — so a delayed OneSignal redelivery (seen 26s late in prod)
        // slipped past this guard and re-launched the accept screen + ringtone for ~1s (the
        // "she gets the ring again for a sec after it disconnects" ghost). Add the check so
        // the dead call is dropped BEFORE launchAcceptActivity ever runs.
        if (callId > 0 && (app?.wasCallRecentlyEnded(callId) == true ||
                app?.wasCallBusyRejected(callId) == true ||
                app?.wasForceRejectedCallId(callId) == true)) {
            Log.d(TAG, "Dropping OneSignal incoming push — call_id=$callId already ended/busy/force-rejected (ghost re-ring)")
            event.preventDefault()
            return true
        }

        // Stale-call guard (mirrors the FCM path's freshness check): after the
        // device has been offline, OneSignal mass-delivers the queued call backlog
        // on reconnect. Drop call pushes older than the freshness window so they
        // don't render as fake ringing notifications that pile up and can't be
        // dismissed (no live cancel is coming for a long-dead call). Age comes from
        // `call_sent_at` if the backend sends it, else OneSignal's own sentTime —
        // so the guard works even though the call template carries no custom
        // timestamp (the previous call_sent_at-only check always failed open here).
        // Fail-open only when NEITHER source yields a usable send-time.
        val ageMs = com.gmwapp.hima.utils.CallNotifications.incomingCallPushAgeMs(
            data.optString("call_sent_at", ""),
            event.notification.sentTime
        )
        if (ageMs != null && ageMs > com.gmwapp.hima.utils.CallNotifications.STALE_INCOMING_CALL_MAX_AGE_MS) {
            Log.w(TAG, "Dropping stale OneSignal call push (${ageMs / 1000}s old) senderId=$senderId")
            event.preventDefault()
            return true
        }

        val userData = com.gmwapp.hima.BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val isMale = userData?.gender == com.gmwapp.hima.constants.DConstants.MALE

        // B-lockcall — set the accept-screen state BEFORE posting our FSI banner.
        // showIncoming's full-screen intent can launch MaleCallAcceptActivity /
        // FemaleCallAcceptActivity before FCM's data message (the only other path
        // that sets this state) arrives; without the state set, the screen's
        // stale-launch guard finishes it and the locked device shows only the
        // unlock screen. When FCM already owns this call it has set the state,
        // Telecom and watcher itself, so the duplicate OneSignal path returns
        // below. This fallback does NOT claim FCM ownership — see
        // setIncomingCallStateForFallback.
        if (app?.isCallOwnedByFcm(senderId) != true) {
            app?.setIncomingCallStateForFallback(senderId, callType ?: "audio", channelName, callId)
            app?.setIncomingCallerInfo(callerName, callerImage)
        } else {
            // Preserve the existing cross-provider ownership rule: FCM already
            // started Telecom, the watcher and the ring UI for this sender.
            // Suppress only the duplicate OneSignal card; do not register a
            // second self-managed call or restart the service.
            event.preventDefault()
            Log.d(TAG, "OneSignal call push suppressed — FCM already owns senderId=$senderId")
            return true
        }

        val payload = com.gmwapp.hima.utils.CallNotifications.IncomingPayload(
            isMale = isMale,
            callType = callType,
            senderId = senderId,
            callId = callId,
            channelName = channelName,
            callerName = callerName,
            callerImage = callerImage
        )

        // RING_BACKSTOP_2026_07_23 — OneSignal is a real incoming-call delivery
        // fallback, so it must establish the same bounded FcmCallService watcher
        // as the primary FCM path. Register the self-managed Telecom call first:
        // on Android 14+ that is the platform precondition for a phoneCall FGS.
        val telecomExtras = Bundle().apply {
            putString(HimaConnection.EXTRA_CALL_TYPE, callType)
            putInt(HimaConnection.EXTRA_SENDER_ID, senderId)
            putString(HimaConnection.EXTRA_CHANNEL_NAME, channelName)
            putInt(HimaConnection.EXTRA_CALL_ID, callId)
            putString(HimaConnection.EXTRA_CALLER_NAME, callerName)
            putString(HimaConnection.EXTRA_CALLER_IMAGE, callerImage)
            putString(HimaConnection.EXTRA_RECEIVER_GENDER, if (isMale) "male" else "female")
        }
        val telecomOk = HimaTelecomManager.tryAddIncomingCall(context, telecomExtras)

        // ONESIGNAL_SWIPE_PARITY_2026_07_23 — mirror the FCM path's B030 decision.
        //
        // Bug: when OneSignal won the delivery race (it beat FCM by ~1.1s on the
        // creator's device), this path ALWAYS took FcmCallService.start() — a real
        // foreground service — while the FCM path under the same foreground+unlocked
        // conditions takes startBackstopOnly(). On Realme/OPPO, swiping the ring away
        // with the FGS up force-kills the process, so NEITHER the accept activity's
        // onDestroy NOR FcmCallService.onTaskRemoved ever runs. The "rejected" relay is
        // therefore never sent and the CALLER keeps ringing until his own 40s timeout.
        // (Verified: an unconditional onDestroy log never printed, and her pid changed
        // mid-call 10843 -> 12299.) The plain backstop-only Service survives the swipe
        // and still receives onTaskRemoved, which is exactly why the FCM-delivered leg
        // already worked. Only the foreground+unlocked branch changes; when the screen
        // is locked or the app is backgrounded the FGS is genuinely required to ring, so
        // that path is left completely untouched.
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE)
            as? android.app.KeyguardManager
        val locked = keyguard?.isKeyguardLocked == true
        val foreground = com.gmwapp.hima.BaseApplication.getInstance()?.isAppInForeground() == true

        val watcherStarted = if (foreground && !locked) {
            // backstopOnly returns early WITHOUT posting a notification or calling
            // startForeground, so nothing would ring unless we launch the accept screen
            // ourselves — exactly what MyFirebaseMessagingService does at :590.
            // DUAL_RING_CLAIM_2026_07_24 — FCM delivers this same call and, in
            // foreground+unlocked, also launches the accept screen. Claim the launch:
            // if FCM already won, start only the swipe-reject backstop and SKIP our
            // redundant startActivity so the ring can't flash twice. If we win (FCM
            // dropped/slower), we launch it. Either way preventDefault below hides
            // OneSignal's own card.
            val backstopOk = FcmCallService.startBackstopOnly(context, payload)
            val claimed = com.gmwapp.hima.BaseApplication.getInstance()
                ?.claimRingSurface(payload.callId) ?: true
            val lightweightOk = backstopOk &&
                (if (claimed) launchAcceptActivity(context, payload) else true)
            if (lightweightOk) {
                Log.d(TAG, "OneSignal call push -> backstop-only + accept screen (fg, unlocked, claimed=$claimed) senderId=$senderId")
                true
            } else {
                // Safety net: never leave the user with no visible ring. If either half of
                // the lightweight path failed (e.g. a background-activity-launch block),
                // fall straight back to the proven foreground-service path.
                Log.w(TAG, "OneSignal backstop-only path failed — falling back to full FcmCallService.start")
                (Build.VERSION.SDK_INT < 34 || telecomOk) && FcmCallService.start(context, payload)
            }
        } else {
            (Build.VERSION.SDK_INT < 34 || telecomOk) && FcmCallService.start(context, payload)
        }

        if (watcherStarted) {
            // FcmCallService owns the custom CallStyle notification and the
            // swipe-away reject backstop. Suppress OneSignal's default card.
            event.preventDefault()
            Log.d(
                TAG,
                "OneSignal call push -> FcmCallService started senderId=$senderId callType=$callType telecomOk=$telecomOk"
            )
            return true
        }

        // If Android rejects the service dispatch, preserve the established
        // display-only fallback. Defer preventDefault until this custom
        // notification path has completed, so the user is never left with no
        // visible ring because of a synchronous failure.
        val ok = runCatching {
            com.gmwapp.hima.utils.CallNotifications.showIncoming(context, payload)
        }.getOrElse {
            Log.e(TAG, "showIncoming threw senderId=$senderId: ${it.message}", it)
            false
        }
        if (!ok) {
            // Let OneSignal show its default heads-up so the user still sees something.
            return false
        }
        event.preventDefault()
        Log.d(TAG, "OneSignal call push -> CallStyle posted senderId=$senderId callType=$callType")
        return true
    }

    /**
     * If the push is a "missed call …" notice, post our own MessagingStyle
     * notification (same pattern as chat messages) and suppress the OneSignal default.
     */
    private fun maybeHandleMissedCall(
        context: Context,
        event: INotificationReceivedEvent
    ): Boolean {
        if (!isMissedCallPush(event.notification)) return false
        val data = event.notification.additionalData
        Log.d(
            "MissedCallDiag",
            "nse-entry title=\"${event.notification.title}\" body=\"${event.notification.body}\" " +
                "dataKeys=${data?.keys()?.asSequence()?.toList()} dataDump=${data?.toString()?.take(500)}"
        )

        val callType = data?.let { firstNonEmpty(it, "callType", "call_type") } ?: "audio"
        // Aliases observed across server templates — anything numeric and >0 wins.
        val realSenderId = if (data == null) 0 else
            listOf(
                "senderId", "sender_id", "user_id",
                "callerId", "caller_id",
                "from_user_id", "from_id", "peer_id", "sender_user_id"
            ).firstNotNullOfOrNull { k -> data.optInt(k, 0).takeIf { it > 0 } } ?: 0
        val titleLine = event.notification.title?.trim().orEmpty()
        val rawCaller = (data?.let { firstNonEmpty(it, "callerName", "sender_name", "name", "title") }
            ?: titleLine.takeIf { it.isNotBlank() })
            .orEmpty()
        val callerName = com.gmwapp.hima.utils.CallNotifications.normalizeMissedCallCallerName(rawCaller)
            .ifBlank { "Caller" }
        val callerImage = (data?.let {
            firstNonEmpty(it, "callerImage", "sender_image", "image", "avatar")
        }).orEmpty()

        // No real id -> derive a stable one from the normalized caller name so repeat
        // missed calls from the same caller dedupe under one row.
        val isSynthetic = realSenderId <= 0
        val effectiveSenderId = if (!isSynthetic) realSenderId
            else (callerName.hashCode() and 0x0FFFFFFF).coerceAtLeast(1)

        Log.d(
            "MissedCallDiag",
            "nse-parsed rawCaller=\"$rawCaller\" callerName=\"$callerName\" callerImage=\"$callerImage\" " +
                "realSenderId=$realSenderId synthetic=$isSynthetic effectiveId=$effectiveSenderId callType=$callType"
        )

        // Suppress OneSignal's default missed-call UI BEFORE we try to post our
        // own. If showMissed throws or returns false we accept the silent miss;
        // the diagnostic Log lines tell us why.
        event.preventDefault()
        Log.d("MissedCallDiag", "nse-preventDefault called effectiveId=$effectiveSenderId")
        val showResult = runCatching {
            com.gmwapp.hima.utils.CallNotifications.showMissed(
                context,
                com.gmwapp.hima.utils.CallNotifications.MissedPayload(
                    callType = callType,
                    senderId = effectiveSenderId,
                    callerName = callerName,
                    callerImage = callerImage,
                    isSynthetic = isSynthetic
                )
            )
        }
        showResult.onSuccess {
            Log.d("MissedCallDiag", "nse-showMissed returned=$it effectiveId=$effectiveSenderId")
        }.onFailure {
            Log.e("MissedCallDiag", "nse-showMissed threw effectiveId=$effectiveSenderId: ${it.message}", it)
        }
        return true
    }

    /**
     * Fires [ACTION_FRIEND_STATUS_CHANGED] for friend-request push types so
     * FriendsListActivity (male) / FriendsHubFragment (female) can refresh their
     * tab-count badges immediately. Does NOT suppress the notification — the system
     * still shows the push; this is purely an in-app signal.
     */
    private fun maybeHandleFriendRequest(context: Context, event: INotificationReceivedEvent) {
        val data = event.notification.additionalData ?: return
        val type = data.optString("type", "").lowercase()
        if (type !in setOf("friend_request_received", "friend_request_accepted", "friend_request_rejected")) return
        context.sendBroadcast(
            Intent(ACTION_FRIEND_STATUS_CHANGED).setPackage(context.packageName)
        )
    }

    /**
     * If the payload is a chat message, append to the per-peer store and post a
     * MessagingStyle notification ourselves, then tell OneSignal to skip its default
     * display. Anything else (calls, friend requests, warnings, Ludo, etc.) falls
     * through untouched.
     */
    private fun maybeHandleChatMessage(context: Context, event: INotificationReceivedEvent) {
        val data = event.notification.additionalData ?: return
        val type = data.optString("type", "")
        if (type != "message") return

        val peerId = parsePeerId(data)
        if (peerId <= 0) return

        val text = event.notification.body.orEmpty().trim()
        if (text.isBlank()) return
        val messageType = data.optString("message_type", "text").ifBlank { "text" }
        // Numeric id if the backend sends one (may arrive as number or string).
        val messageId = data.optLong("message_id", 0L).takeIf { it > 0L }
            ?: data.optString("message_id", "").toLongOrNull()?.takeIf { it > 0L }

        // Always tell the chat list / badge to refresh — this is cheap and
        // idempotent. The thread-level refresh below only fires when the chat
        // is actively open.
        sendChatListRefresh(context, peerId, text, messageType, messageId)

        // WhatsApp-style behaviour: if the user is already looking at the chat
        // for this peer, don't show a heads-up — broadcast a refresh signal so
        // the open activity can catch up via REST in case the Socket.IO event
        // was missed (reconnect gap, dropped event, etc.).
        // T11: pass context so the prefs-backed fallback works when the NSE runs in a separate process.
        if (ActiveChatTracker.isActiveFor(context, peerId)) {
            Log.d(TAG, "chat visible for peerId=$peerId — suppressing heads-up, broadcasting refresh")
            val refresh = Intent(ACTION_CHAT_REFRESH)
                .setPackage(context.packageName)
                .putExtra("peer_id", peerId)
            context.sendBroadcast(refresh)
            event.preventDefault()
            return
        }

        // Fall back to previously-seen metadata if this follow-up push omits name/image.
        val (storedName, storedImage) = ChatNotificationStore.getMeta(context, peerId)
        // Prefer structured name fields. The notification title is only a last
        // resort AND must be sanitised: it is the full "<name> sent you a message"
        // headline, so feeding it raw poisoned the chat header + profile with the
        // whole sentence. sanitizePeerName() recovers the leading username.
        val peerName = com.gmwapp.hima.utils.PeerNameUtils.sanitizePeerName(
            firstNonEmpty(data, "user_name", "sender_name", "name", "username")
                ?: event.notification.title
        ).ifBlank { storedName }
        val peerImage = firstNonEmpty(
            data, "user_image", "image", "image_url", "profile_image", "sender_image", "avatar"
        ) ?: storedImage.orEmpty()

        ChatNotificationStore.saveMeta(context, peerId, peerName, peerImage)
        // Dedup key: OneSignal's per-push notification id. When OneSignal's receive
        // handler is cancelled (JobCancellationException) it reprocesses the SAME
        // push, which used to append the message twice → doubled MessagingStyle line.
        // The id is identical across reprocessing (and present even on text pushes,
        // which carry no backend message_id), so it reliably drops the duplicate.
        val dedupId = event.notification.notificationId?.takeIf { it.isNotBlank() }
        val entries = ChatNotificationStore.append(context, peerId, text, System.currentTimeMillis(), dedupId)

        // H15: preventDefault BEFORE show — if show() throws, OneSignal's default
        // would otherwise also fire and the user would see two notifications for
        // the same message. We post a minimal fallback below in the catch block.
        event.preventDefault()
        try {
            ChatNotifications.show(context, peerId, peerName, peerImage, entries)
            Log.d(TAG, "Chat notif posted for peerId=$peerId (lines=${entries.size})")
        } catch (e: Exception) {
            Log.e(TAG, "Chat notif post failed for peerId=$peerId: ${e.message}")
            postFallbackNotification(context, peerId, peerName, text)
        }
    }

    /**
     * Fires [ACTION_CHAT_LIST_REFRESH] so any chat list / badge that is on screen
     * updates in-place. Safe to call from background threads; uses a package-scoped
     * broadcast so it's never visible outside the app.
     */
    private fun sendChatListRefresh(
        context: Context,
        peerId: Int,
        lastMessage: String,
        messageType: String,
        messageId: Long? = null
    ) {
        val intent = Intent(ACTION_CHAT_LIST_REFRESH)
            .setPackage(context.packageName)
            .putExtra(EXTRA_PEER_ID, peerId)
            .putExtra(EXTRA_LAST_MESSAGE, lastMessage)
            .putExtra(EXTRA_MESSAGE_TYPE, messageType)
        if (messageId != null) intent.putExtra(EXTRA_MESSAGE_ID, messageId)
        context.sendBroadcast(intent)
    }

    /**
     * Last-resort heads-up if [ChatNotifications.show] throws after we've already
     * called preventDefault on the OneSignal display path. Plain-text only; no
     * MessagingStyle dependencies that could throw the same way.
     */
    private fun postFallbackNotification(
        context: android.content.Context,
        peerId: Int,
        peerName: String,
        text: String
    ) {
        try {
            val builder = androidx.core.app.NotificationCompat.Builder(
                context,
                "f49d2168-bc20-4a4b-a984-a7abffe0d6aa" // same channel id as ChatNotifications
            )
                .setSmallIcon(com.gmwapp.hima.R.drawable.logo)
                .setContentTitle(peerName.ifBlank { "New message" })
                .setContentText(text)
                .setAutoCancel(true)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(ChatNotifications.notifIdFor(peerId), builder.build())
        } catch (t: Throwable) {
            Log.w(TAG, "Fallback notif also failed for peerId=$peerId: ${t.message}")
        }
    }

    /**
     * Conservative heuristic for "this push is about an incoming call."
     * Matches either (a) structured `additionalData` keys the backend attaches
     * to call pushes, or (b) free-text titles/bodies when the payload is less
     * structured. Anything else (wallet, friend request, chat, Ludo, etc.) is
     * left alone.
     */
    private fun isCallPush(notif: com.onesignal.notifications.IDisplayableNotification): Boolean {
        val data = notif.additionalData
        if (data != null) {
            val keys = arrayOf("callType", "channelName", "call_id", "senderId")
            if (keys.any { data.has(it) && !data.isNull(it) }) return true
            val type = data.optString("type", "").lowercase()
            // Status/availability notifications are NOT calls even though their type may
            // start with "call" (e.g. call_status_disabled) — exclude before the prefix test
            // so the creator auto-disable push never renders as an incoming ring.
            if (type == "availability_disabled" || type == "call_status_disabled" || type == "call_status") return false
            if (type.startsWith("call") || type.contains("incoming")) return true
        }
        val title = notif.title.orEmpty().lowercase()
        val body = notif.body.orEmpty().lowercase()
        val haystack = "$title $body"
        return haystack.contains("video call from") ||
            haystack.contains("audio call from") ||
            haystack.contains("wants to talk to you") ||
            haystack.contains("incoming call")
    }

    /** OneSignal payload keys vary by server; try the usual peer-id aliases. */
    private fun parsePeerId(data: JSONObject): Int {
        val keys = arrayOf("user_id", "sender_id", "from_user_id", "senderId", "sender_user_id", "peer_id")
        for (key in keys) {
            if (!data.has(key) || data.isNull(key)) continue
            val id = when (val raw = data.opt(key)) {
                is Number -> raw.toInt()
                else -> data.optString(key, "").trim().toIntOrNull() ?: 0
            }
            if (id > 0) return id
        }
        return -1
    }

    private fun firstNonEmpty(data: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val s = data.optString(key, "").trim()
            if (s.isNotEmpty()) return s
        }
        return null
    }

    private fun isDndActive(userData: com.gmwapp.hima.retrofit.responses.UserData?): Boolean {
        if (userData == null) return false
        if ((userData.dnd_enabled ?: 0) != 1) return false
        val until = userData.dnd_until ?: return false
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val expiry = sdf.parse(until) ?: return false
            expiry.time > System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse dnd_until=$until: ${e.message}")
            false
        }
    }
}
