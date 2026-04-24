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
    private val extras: Bundle
) : Connection() {

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        setAudioModeIsVoip(true)
        val name = extras.getString(EXTRA_CALLER_NAME) ?: "Incoming call"
        setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)
        val callType = extras.getString(EXTRA_CALL_TYPE) ?: "audio"
        if (callType == "video") {
            setVideoState(VideoProfile.STATE_BIDIRECTIONAL)
        } else {
            setVideoState(VideoProfile.STATE_AUDIO_ONLY)
        }
        connectionCapabilities = CAPABILITY_MUTE
        setRinging()
        val vs = videoState
        Log.d(
            INCOMING_CALL_LOG_TAG,
            "HimaConnection init callType=$callType videoState=$vs extrasKeys=${extras.keySet()}"
        )
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

        if (userId != null && senderId > 0 && !callType.isNullOrEmpty() && !channelName.isNullOrEmpty()) {
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
        if (channelName.isNullOrEmpty() || callType.isNullOrEmpty()) return null
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
