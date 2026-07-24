package com.gmwapp.hima.agora.telecom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.agora.CallChannel
import com.gmwapp.hima.agora.female.FemaleAudioCallingActivity
import com.gmwapp.hima.agora.female.FemaleVideoCallingActivity
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponse

/**
 * Self-managed [Connection] for incoming Agora calls surfaced through the system Telecom UI.
 */
class HimaConnection(
    private val appContext: Context,
    private val extras: Bundle,
    // I039 — outgoing connections start in DIALING (no system ringer on our side; we
    // already have our own connecting UI). Incoming stays in RINGING so Telecom's
    // self-managed second-call UI can offer Hold & Answer / End & Answer against
    // another active call.
    private val isIncoming: Boolean = true
) : Connection() {

    internal fun matchesIncomingCall(senderId: Int, callId: Int): Boolean =
        isIncoming &&
            com.gmwapp.hima.agora.IncomingCallTeardownPolicy.identityMatches(
                currentSenderId = extras.getInt(EXTRA_SENDER_ID, -1),
                currentCallId = extras.getInt(EXTRA_CALL_ID, 0),
                expectedSenderId = senderId,
                expectedCallId = callId
            )

    init {
        // v1106 (2026-05-29) — wrap each Connection API call individually
        // in try-catch. Some OEM Android implementations (observed in
        // Crashlytics for v1105 on certain devices) strip or relocate methods
        // like setVideoState/getVideoState which causes a fatal
        // NoSuchMethodError during init, force-closing the app the moment
        // a Telecom call is created. Each call is independent so we keep
        // whatever parts the OEM does support and skip the missing ones —
        // worst case the Telecom hold/switch UX degrades for that one call
        // but the app stays alive.
        try { connectionProperties = PROPERTY_SELF_MANAGED } catch (t: Throwable) { Log.w(TAG, "init: setting PROPERTY_SELF_MANAGED failed", t) }
        try { setAudioModeIsVoip(true) } catch (t: Throwable) { Log.w(TAG, "init: setAudioModeIsVoip failed", t) }
        val name = extras.getString(EXTRA_CALLER_NAME) ?: "Hima call"
        try { setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED) } catch (t: Throwable) { Log.w(TAG, "init: setCallerDisplayName failed", t) }
        val callType = extras.getString(EXTRA_CALL_TYPE) ?: "audio"
        try {
            if (callType == "video") {
                setVideoState(VideoProfile.STATE_BIDIRECTIONAL)
            } else {
                setVideoState(VideoProfile.STATE_AUDIO_ONLY)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "init: setVideoState failed (OEM Connection class missing method?)", t)
        }
        // CAPABILITY_HOLD + CAPABILITY_SUPPORT_HOLD let Telecom offer the system
        // second-call UI ("Hold & Answer", "End & Answer") when another call
        // arrives while this Hima call is active — and route the user's choice
        // back to us via onHold() / onUnhold(). Without these, Telecom would
        // only offer "End & Answer" and audio mixing would persist.
        try {
            connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_HOLD or CAPABILITY_SUPPORT_HOLD
        } catch (t: Throwable) {
            Log.w(TAG, "init: setting connectionCapabilities failed", t)
        }
        try {
            if (isIncoming) {
                setRinging()
            } else {
                setDialing()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "init: setRinging/setDialing failed", t)
        }
        val vs = try { videoState } catch (t: Throwable) {
            Log.w(TAG, "init: reading videoState failed (OEM Connection class missing getter?)", t)
            -1
        }
        Log.d(
            INCOMING_CALL_LOG_TAG,
            "HimaConnection init isIncoming=$isIncoming callType=$callType videoState=$vs extrasKeys=${extras.keySet()}"
        )
    }

    /**
     * Telecom hands hold control to us when the user chose "Hold & Answer" on the
     * system second-call UI (or accepted another self-managed VoIP call). Mute the
     * Agora outbound stream via [TelecomCallController] (which the active calling
     * activity registered against in onCreate) and flip our state to HELD so
     * Telecom shows the right indicator.
     */
    override fun onHold() {
        super.onHold()
        Log.d(TAG, "onHold — muting Agora outbound and entering HELD state")
        TelecomCallController.dispatchHold(true)
        setOnHold()
    }

    override fun onUnhold() {
        super.onUnhold()
        Log.d(TAG, "onUnhold — unmuting Agora outbound and returning to ACTIVE state")
        TelecomCallController.dispatchHold(false)
        setActive()
    }

    /**
     * I039 — called by Telecom when the system wants this self-managed connection to show
     * its own incoming-call UI. The crucial property: Telecom grants this launch permission
     * to surface over an active call (SIM or another self-managed VoIP app). Without this
     * override, Android's "no full-screen intent over a call" restriction made our incoming
     * UI invisible to users already on a phone call, and the call quietly converted to a
     * missed-call notification.
     *
     * Mirrors the activity + extras shape used by [com.gmwapp.hima.utils.CallNotifications.showIncoming]
     * so the accept screen looks the same whether it was launched here or via the heads-up
     * notification's content intent.
     */
    override fun onShowIncomingCallUi() {
        super.onShowIncomingCallUi()
        Log.d(TAG, "onShowIncomingCallUi — launching accept activity")
        // Pull values out of the constructor `extras` Bundle before building the Intent —
        // inside `apply { }` the implicit receiver shadows it with Intent.getExtras (nullable).
        val gender = extras.getString(EXTRA_RECEIVER_GENDER)
        val callType = extras.getString(EXTRA_CALL_TYPE)
        val senderId = extras.getInt(EXTRA_SENDER_ID, -1)
        val channelName = extras.getString(EXTRA_CHANNEL_NAME)
        val callId = extras.getInt(EXTRA_CALL_ID, 0)
        val callerName = extras.getString(EXTRA_CALLER_NAME)
        val callerImage = extras.getString(EXTRA_CALLER_IMAGE)

        // GHOST_RING_2026_07_24 — the FCM and OneSignal push handlers both drop an
        // incoming ring whose call_id was already rejected/ended/busy-rejected, but the
        // Telecom framework can call THIS onShowIncomingCallUi again for a still-registered
        // self-managed connection (e.g. after the ring was dismissed while the caller was
        // still ringing) — re-launching the accept screen with no such guard. That is the
        // "she swipes the ring and sees it again" ghost. Mirror the handler guards here and
        // tear the connection down instead of resurrecting a dead ring.
        val app = com.gmwapp.hima.BaseApplication.getInstance()
        if (callId > 0 && app != null &&
            (app.wasForceRejectedCallId(callId) || app.wasCallRecentlyEnded(callId) ||
                app.wasCallBusyRejected(callId))
        ) {
            Log.d(TAG, "onShowIncomingCallUi SKIP — call_id=$callId already rejected/ended (ghost re-ring)")
            runCatching {
                setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.REJECTED))
                destroy()
            }
            return
        }

        val targetClass = if (gender == "male") {
            com.gmwapp.hima.agora.male.MaleCallAcceptActivity::class.java
        } else {
            com.gmwapp.hima.agora.female.FemaleCallAcceptActivity::class.java
        }
        val intent = Intent(appContext, targetClass)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("CALL_TYPE", callType)
            .putExtra("SENDER_ID", senderId)
            .putExtra("CHANNEL_NAME", channelName)
            .putExtra("CALL_ID", callId)
            .putExtra("Caller_NAME", callerName)
            .putExtra("Caller_Image", callerImage)
        try {
            appContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "onShowIncomingCallUi: startActivity failed ${e.message}", e)
        }
    }

    override fun onAnswer() {
        super.onAnswer()
        Log.d(TAG, "onAnswer")
        BaseApplication.getInstance()?.stopRingtone()
        BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()

        val callType = extras.getString(EXTRA_CALL_TYPE)
        val senderId = extras.getInt(EXTRA_SENDER_ID, -1)
        val channelName = extras.getString(EXTRA_CHANNEL_NAME)
        val callId = extras.getInt(EXTRA_CALL_ID, 0)
        val gender = extras.getString(EXTRA_RECEIVER_GENDER)
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id

        if (userId != null && senderId > 0 && !callType.isNullOrEmpty() &&
            !channelName.isNullOrEmpty() && channelName != CallChannel.DEFAULT_SENTINEL) {
            sendCallStatus(userId, senderId, callType, channelName, "accepted")
        }

        val intent = buildAnswerIntent(gender, callType, channelName, senderId, callId)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            appContext.startActivity(intent)
            setActive()
        } else {
            clearActiveConnectionRefIfSelf()
            setDisconnected(DisconnectCause(DisconnectCause.ERROR))
            destroy()
        }
    }

    /**
     * System "silence ringer" — invoked when the user presses a hardware volume
     * button (or the system silence action) while this self-managed call is
     * RINGING. Because this is a Telecom call, the framework owns the volume
     * buttons, so the press never reaches Activity.onKeyDown — this is the only
     * reliable hook to mute our own MediaPlayer ringtone, exactly the way a
     * native incoming call goes silent on a volume press. We stop the sound but
     * keep the connection ringing so the user can still answer/decline.
     */
    override fun onSilence() {
        super.onSilence()
        Log.d(TAG, "onSilence — silencing incoming ringtone")
        BaseApplication.getInstance()?.stopRingtone()
    }

    override fun onReject() {
        super.onReject()
        handleRejectOrDisconnect()
    }

    override fun onDisconnect() {
        super.onDisconnect()
        handleRejectOrDisconnect()
    }

    private fun handleRejectOrDisconnect() {
        Log.d(TAG, "onReject/onDisconnect")
        BaseApplication.getInstance()?.stopRingtone()
        BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()

        val callType = extras.getString(EXTRA_CALL_TYPE)
        val senderId = extras.getInt(EXTRA_SENDER_ID, -1)
        val channelName = extras.getString(EXTRA_CHANNEL_NAME)
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id

        if (userId != null && senderId > 0 && !callType.isNullOrEmpty() && !channelName.isNullOrEmpty()) {
            sendCallStatus(userId, senderId, callType, channelName, "rejected")
        }
        clearActiveConnectionRefIfSelf()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    private fun clearActiveConnectionRefIfSelf() {
        if (HimaConnectionService.activeConnection === this) {
            HimaConnectionService.activeConnection = null
            Log.d(INCOMING_CALL_LOG_TAG, "HimaConnection: activeConnection cleared (self)")
        }
    }

    private fun sendCallStatus(
        localUserId: Int,
        remoteUserId: Int,
        callType: String,
        channelName: String,
        message: String
    ) {
        val repo = BaseApplication.getInstance()?.fcmNotificationRepository ?: run {
            Log.e(TAG, "No FcmNotificationRepository")
            return
        }
        repo.sendFcmNotification(
            localUserId,
            remoteUserId,
            callType, channelName, message,
            object : NetworkCallback<FcmNotificationResponse> {
                override fun onResponse(
                    call: retrofit2.Call<FcmNotificationResponse>,
                    response: retrofit2.Response<FcmNotificationResponse>
                ) {
                    Log.d(TAG, "sendCallStatus $message ok")
                }

                override fun onFailure(call: retrofit2.Call<FcmNotificationResponse>, t: Throwable) {
                    Log.e(TAG, "sendCallStatus $message fail: ${t.message}")
                }

                override fun onNoNetwork() {
                    Log.e(TAG, "sendCallStatus $message no network")
                }
            }
        )
    }

    private fun buildAnswerIntent(
        gender: String?,
        callType: String?,
        channelName: String?,
        senderId: Int,
        callId: Int
    ): Intent? {
        // Never build a join intent for a channel-less push: a blank channel, or
        // the "default_channel" sentinel the receive paths substitute when no
        // channel is present, would drop the answerer into a SHARED Agora room
        // (crossed-over audio / wrong-call UI) instead of the caller's unique
        // channel. Returning null makes onAnswer() disconnect cleanly.
        if (channelName.isNullOrEmpty() || channelName == CallChannel.DEFAULT_SENTINEL ||
            callType.isNullOrEmpty()) return null
        return when {
            gender == "male" && callType == "audio" ->
                Intent(appContext, MaleAudioCallingActivity::class.java).apply {
                    putExtra("CHANNEL_NAME", channelName)
                    putExtra("RECEIVER_ID", senderId)
                    putExtra("CALL_ID", callId)
                }
            gender == "male" && callType == "video" ->
                Intent(appContext, MaleVideoCallingActivity::class.java).apply {
                    putExtra("CHANNEL_NAME", channelName)
                    putExtra("RECEIVER_ID", senderId)
                    putExtra("CALL_ID", callId)
                }
            gender != "male" && callType == "audio" ->
                Intent(appContext, FemaleAudioCallingActivity::class.java).apply {
                    putExtra("CHANNEL_NAME", channelName)
                    putExtra("RECEIVER_ID", senderId)
                    putExtra("CALL_ID", callId)
                }
            gender != "male" && callType == "video" ->
                Intent(appContext, FemaleVideoCallingActivity::class.java).apply {
                    putExtra("CHANNEL_NAME", channelName)
                    putExtra("RECEIVER_ID", senderId)
                    putExtra("CALL_ID", callId)
                }
            else -> null
        }
    }

    /**
     * Forwards an explicit user audio-route choice to Telecom. On self-managed
     * connections (Samsung Android 16 being the problem case), Telecom's
     * `CallAudioRouteController` owns the baseline route and will override
     * anything we set via `AudioManager.setCommunicationDevice`. The only API
     * the OEM stack respects is [Connection.setAudioRoute], so call it first
     * whenever the user picks Earpiece / Speaker / Bluetooth.
     */
    fun applyRouteFromApp(route: com.gmwapp.hima.utils.CallAudioRouter.AudioRoute) {
        val telecomRoute = when (route) {
            com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
            com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
        }
        try {
            setAudioRoute(telecomRoute)
            Log.d("CallAudioRoute", "HimaConnection.setAudioRoute($route) -> telecomRoute=$telecomRoute ok")
        } catch (e: Exception) {
            Log.e("CallAudioRoute", "HimaConnection.setAudioRoute failed: ${e.message}", e)
        }
    }

    companion object {
        private const val INCOMING_CALL_LOG_TAG = "HimaIncomingCall"
        private const val TAG = "HimaConnection"

        const val EXTRA_CALL_TYPE = "CALL_TYPE"
        const val EXTRA_SENDER_ID = "SENDER_ID"
        const val EXTRA_CHANNEL_NAME = "CHANNEL_NAME"
        const val EXTRA_CALL_ID = "CALL_ID"
        const val EXTRA_CALLER_NAME = "Caller_NAME"
        const val EXTRA_CALLER_IMAGE = "Caller_Image"
        const val EXTRA_RECEIVER_GENDER = "CALL_RECEIVER_GENDER"
    }
}
