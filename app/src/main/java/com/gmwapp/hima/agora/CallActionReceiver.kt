package com.gmwapp.hima.agora

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.female.FemaleAudioCallingActivity
import com.gmwapp.hima.agora.female.FemaleVideoCallingActivity
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import android.telecom.DisconnectCause
import com.gmwapp.hima.repositories.FcmNotificationRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallEndReason
import com.gmwapp.hima.retrofit.responses.CallEndedBy
import com.gmwapp.hima.retrofit.responses.CallStatusRequest
import com.gmwapp.hima.retrofit.responses.CallStatusResponse
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponse

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extrasSummary = intent.extras?.let { b ->
            b.keySet().joinToString(prefix = "[", postfix = "]") { k ->
                "$k=${b.get(k)}"
            }
        } ?: "[]"
        Log.d("HimaIncomingCall", "CallActionReceiver.onReceive action=${intent.action} extras=$extrasSummary")

        when (intent.action) {
            "ACTION_ACCEPT_CALL" -> {
                val extras = intent.extras
                val callType = extras?.getString("CALL_TYPE")
                val senderId = extras?.getInt("SENDER_ID", -1) ?: -1
                val channelName = extras?.getString("CHANNEL_NAME")
                val callId = extras?.getInt("CALL_ID", -1)

                Log.d("CallReceiver", "Call Accepted: callType=$callType, senderId=$senderId, channelName=$channelName, callId=$callId")

                // GHOST_CALL_FIX_2026_06_22 — the heads-up "Answer" has no liveness
                // gate of its own (unlike FemaleCallAcceptActivity). If this banner
                // is for a call that already ended (she declined it on the full-screen
                // but a sibling banner lingered), joining would drop her into a dead
                // Agora channel. Refuse and clean up instead of ghost-joining.
                if (callId != null && callId > 0 &&
                    BaseApplication.getInstance()?.wasCallRecentlyEnded(callId) == true) {
                    Log.d("HimaIncomingCall", "ACTION_ACCEPT_CALL blocked: callId=$callId already ended (stale banner)")
                    BaseApplication.getInstance()?.stopRingtone()
                    BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
                    Toast.makeText(context.applicationContext, "This call has already ended", Toast.LENGTH_SHORT).show()
                    context.applicationContext.startActivity(
                        Intent(context.applicationContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    )
                    return
                }

                // Channel-less push (OneSignal call_request / bad FCM) carries no
                // real channel — joining the "default_channel" sentinel would drop
                // her into a SHARED room with unrelated callers. Refuse + clean up.
                if (!CallChannel.isJoinable(channelName)) {
                    Log.w("HimaIncomingCall", "ACTION_ACCEPT_CALL: unusable channel='$channelName' — refusing shared-room join")
                    HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
                    BaseApplication.getInstance()?.stopRingtone()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    Toast.makeText(
                        context.applicationContext,
                        "Couldn't connect this call. Please ask them to call again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }

                // B_002 — an OFFLINE device must never enter a call it can never join:
                // Agora can't connect, onJoinChannelSuccess never fires, and the only
                // safety timer (startTimeoutTracking) is armed INSIDE that callback, so
                // nothing ends the call and it hangs "connected" with no audio forever.
                // The full-screen Accept button guards this too; without it here the
                // heads-up "Answer" action would bypass the check entirely.
                //
                // Unlike the two guards above this is NOT terminal — the call is still
                // live, she just can't answer right now. So do NOT tear the ring down:
                // leave the banner + ringtone intact so she can retry or decline, and
                // return BEFORE markRingAccepted so no accepted-state is left behind.
                // isOnline() is lenient + fail-open (see NetworkUtils).
                if (!com.gmwapp.hima.utils.NetworkUtils.isOnline(context)) {
                    Log.d("HimaIncomingCall", "ACTION_ACCEPT_CALL blocked: device offline callId=$callId")
                    Toast.makeText(
                        context.applicationContext,
                        context.getString(R.string.call_accept_no_network),
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }

                // FORCE_CLOSE_REJECT_2026_07_07 — record that THIS caller's ring was
                // accepted, so if the app is swiped from recents during the
                // notification-accept cold-start window (before the calling activity's
                // onCreate flips the in-call state), FcmCallService.onTaskRemoved reads
                // wasRingAcceptedFor()==true and does NOT mistake it for a force-close
                // reject. The full-screen Accept button already does this; the heads-up
                // Answer action did not.
                if (senderId > 0) BaseApplication.getInstance()?.markRingAccepted(senderId)

                val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
                sendAcceptedFcmFireAndForget(
                    context,
                    selfUserId = userId,
                    peerUserId = senderId,
                    callType = callType,
                    channelName = channelName,
                    branchTag = "ACTION_ACCEPT_CALL"
                )

                if (callType=="audio"){
                    val callIntent = Intent(context, FemaleAudioCallingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        if (extras != null) {
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("RECEIVER_ID", senderId)
                            putExtra("CALL_ID", callId)
                        }
                    }
                    context.startActivity(callIntent)
                }

                if (callType=="video"){
                    val callIntent = Intent(context, FemaleVideoCallingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        if (extras != null) {
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("RECEIVER_ID", senderId)
                            putExtra("CALL_ID", callId)
                        }
                    }
                    context.startActivity(callIntent)
                }

                BaseApplication.getInstance()?.stopRingtone()
                HimaTelecomManager.markActive()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                BaseApplication.getInstance()?.clearIncomingCall()

            }

            "ACTION_REJECT_CALL" -> {

                var userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                var userid = userData?.id

                val callType = intent.getStringExtra("CALL_TYPE")
                val receiverId = intent.getIntExtra("SENDER_ID",-1)
                val channelName = intent.getStringExtra("CHANNEL_NAME")
                val callId = intent.getIntExtra("CALL_ID", -1)

                // Instant UI feedback — do not wait for FCM round trip.
                HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                FcmCallService.stop(context)
                BaseApplication.getInstance()?.stopRingtone()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                BaseApplication.getInstance()?.clearIncomingCall()

                Log.d("CallReceiver", "Call Rejected: callType=$callType, senderId=$receiverId, channelName=$channelName, callId=$callId")

                reportCallStatusRejected(context, userid, receiverId, callId)

                val fcmNotificationRepository = (context.applicationContext as BaseApplication).fcmNotificationRepository

                if (userid != null && receiverId != null && callType != null && channelName != null) {
                    fcmNotificationRepository.sendFcmNotification(
                        userid, receiverId, callType, channelName, "rejected",
                        object : NetworkCallback<FcmNotificationResponse> {
                            override fun onResponse(call: retrofit2.Call<FcmNotificationResponse>, response: retrofit2.Response<FcmNotificationResponse>) {
                                Log.d("FCMNotification", "Auto-reject sent: ${response.body()?.message}")
                                HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                                BaseApplication.getInstance()?.stopRingtone()
                                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                                BaseApplication.getInstance()?.clearIncomingCall()

                                val context = context.applicationContext
                                val mainIntent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                context.startActivity(mainIntent)
                            }

                            override fun onFailure(call: retrofit2.Call<FcmNotificationResponse>, t: Throwable) {
                                Log.e("FCMNotification", "Error sending auto-reject: ${t.message}")
                                HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                                BaseApplication.getInstance()?.stopRingtone()
                                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                                BaseApplication.getInstance()?.clearIncomingCall()
                            }

                            override fun onNoNetwork() {
                                Log.e("FCMNotification", "No network for auto-reject")
                                HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                                BaseApplication.getInstance()?.stopRingtone()
                                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                                BaseApplication.getInstance()?.clearIncomingCall()
                            }
                        }
                    )
                }



            }

            // ========== MALE CALL ACTIONS ==========
            // Added for males to accept/reject calls from females
            "ACTION_ACCEPT_CALL_MALE" -> {
                val extras = intent.extras
                val callType = extras?.getString("CALL_TYPE")
                val senderId = extras?.getInt("SENDER_ID", -1) ?: -1
                val channelName = extras?.getString("CHANNEL_NAME")
                val callId = extras?.getInt("CALL_ID", -1)

                Log.d("CallReceiver_Male", "Call Accepted: callType=$callType, senderId=$senderId, channelName=$channelName, callId=$callId")

                // GHOST_CALL_FIX_2026_06_22 — same stale-banner guard as the female
                // heads-up Answer above: never join a call_id we already ended.
                if (callId != null && callId > 0 &&
                    BaseApplication.getInstance()?.wasCallRecentlyEnded(callId) == true) {
                    Log.d("CallReceiver_Male", "ACTION_ACCEPT_CALL_MALE blocked: callId=$callId already ended (stale banner)")
                    BaseApplication.getInstance()?.stopRingtone()
                    BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                    Toast.makeText(context.applicationContext, "This call has already ended", Toast.LENGTH_SHORT).show()
                    context.applicationContext.startActivity(
                        Intent(context.applicationContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    )
                    return
                }

                // Channel-less push — refuse so the male is never dropped into the
                // shared "default_channel" room (checked before the coins gate).
                if (!CallChannel.isJoinable(channelName)) {
                    Log.w("CallReceiver_Male", "ACTION_ACCEPT_CALL_MALE: unusable channel='$channelName' — refusing shared-room join")
                    HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
                    BaseApplication.getInstance()?.stopRingtone()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    Toast.makeText(
                        context.applicationContext,
                        "Couldn't connect this call. Please ask them to call again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }

                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                val userId = userData?.id
                val currentCoins = userData?.coins ?: 0
                if (currentCoins < 10) {
                    Log.d(
                        "MaleCallAccept",
                        "Heads-up answer blocked: insufficient coins=$currentCoins (req=10)"
                    )
                    HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                    BaseApplication.getInstance()?.stopRingtone()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    if (userId != null && senderId > 0 && !callType.isNullOrEmpty() && !channelName.isNullOrEmpty()) {
                        val repo = (context.applicationContext as BaseApplication).fcmNotificationRepository
                        repo.sendFcmNotification(
                            userId, senderId, callType, channelName, "rejected",
                            object : NetworkCallback<FcmNotificationResponse> {
                                override fun onResponse(
                                    call: retrofit2.Call<FcmNotificationResponse>,
                                    response: retrofit2.Response<FcmNotificationResponse>
                                ) {
                                    Log.d(
                                        "FCMNotification_Male",
                                        "Heads-up insufficient-coins reject sent: ${response.body()?.message}"
                                    )
                                }

                                override fun onFailure(
                                    call: retrofit2.Call<FcmNotificationResponse>,
                                    t: Throwable
                                ) {
                                    Log.e("FCMNotification_Male", "Heads-up insufficient-coins reject failed", t)
                                }

                                override fun onNoNetwork() {
                                    Log.e("FCMNotification_Male", "Heads-up insufficient-coins reject — no network")
                                }
                            }
                        )
                    }
                    Toast.makeText(
                        context.applicationContext,
                        "You don't have enough coins to attend the call. Recharge now!",
                        Toast.LENGTH_LONG
                    ).show()
                    val mainIntent = Intent(context.applicationContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.applicationContext.startActivity(mainIntent)
                    return
                }

                // FORCE_CLOSE_REJECT_2026_07_07 — mark THIS caller's ring accepted
                // (coins gate passed) so a swipe-away during the cold-start window
                // isn't mistaken for a force-close reject. See the female branch.
                if (senderId > 0) BaseApplication.getInstance()?.markRingAccepted(senderId)

                sendAcceptedFcmFireAndForget(
                    context,
                    selfUserId = userId,
                    peerUserId = senderId,
                    callType = callType,
                    channelName = channelName,
                    branchTag = "ACTION_ACCEPT_CALL_MALE"
                )

                if (callType=="audio"){
                    val callIntent = Intent(context, MaleAudioCallingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        if (extras != null) {
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("RECEIVER_ID", senderId)
                            putExtra("CALL_ID", callId)
                        }
                    }
                    context.startActivity(callIntent)
                }

                if (callType=="video"){
                    val callIntent = Intent(context, MaleVideoCallingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        if (extras != null) {
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("RECEIVER_ID", senderId)
                            putExtra("CALL_ID", callId)
                        }
                    }
                    context.startActivity(callIntent)
                }

                BaseApplication.getInstance()?.stopRingtone()
                HimaTelecomManager.markActive()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                BaseApplication.getInstance()?.clearIncomingCall()
            }

            "ACTION_REJECT_CALL_MALE" -> {
                var userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                var userid = userData?.id

                val callType = intent.getStringExtra("CALL_TYPE")
                val receiverId = intent.getIntExtra("SENDER_ID",-1)
                val channelName = intent.getStringExtra("CHANNEL_NAME")
                val callId = intent.getIntExtra("CALL_ID", -1)

                // Instant UI feedback — do not wait for FCM round trip.
                HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                FcmCallService.stop(context)
                BaseApplication.getInstance()?.stopRingtone()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                BaseApplication.getInstance()?.clearIncomingCall()

                Log.d("CallReceiver_Male", "Call Rejected: callType=$callType, senderId=$receiverId, channelName=$channelName, callId=$callId")

                reportCallStatusRejected(context, userid, receiverId, callId)

                // Count this notification/lock-screen decline toward the 3-strike
                // auto-offline block. The in-app full-screen Reject button already
                // posts this (MaleCallAcceptActivity); this path was the missing one,
                // so real declines never accrued and the male stayed "online".
                // male_user_id = self (userid), female_user_id = caller (receiverId).
                if (userid != null && receiverId > 0) {
                    (context.applicationContext as BaseApplication).reportRejectCount(userid, receiverId)
                }

                val fcmNotificationRepository = (context.applicationContext as BaseApplication).fcmNotificationRepository

                if (userid != null && receiverId != null && callType != null && channelName != null) {
                    fcmNotificationRepository.sendFcmNotification(
                        userid, receiverId, callType, channelName, "rejected",
                        object : NetworkCallback<FcmNotificationResponse> {
                            override fun onResponse(call: retrofit2.Call<FcmNotificationResponse>, response: retrofit2.Response<FcmNotificationResponse>) {
                                Log.d("FCMNotification_Male", "Auto-reject sent: ${response.body()?.message}")
                                HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                                BaseApplication.getInstance()?.stopRingtone()
                                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                                BaseApplication.getInstance()?.clearIncomingCall()

                                val context = context.applicationContext
                                val mainIntent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                context.startActivity(mainIntent)
                            }

                            override fun onFailure(call: retrofit2.Call<FcmNotificationResponse>, t: Throwable) {
                                Log.e("FCMNotification_Male", "Error sending auto-reject: ${t.message}")
                                HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                                BaseApplication.getInstance()?.stopRingtone()
                                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                                BaseApplication.getInstance()?.clearIncomingCall()
                            }

                            override fun onNoNetwork() {
                                Log.e("FCMNotification_Male", "No network for auto-reject")
                                HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                                BaseApplication.getInstance()?.stopRingtone()
                                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                                BaseApplication.getInstance()?.clearIncomingCall()
                            }
                        }
                    )
                }
            }
            // ========== END MALE CALL ACTIONS ==========

        }
    }

    /**
     * Heads-up "Answer" skips FemaleCallAcceptActivity / MaleCallAcceptActivity, which normally
     * send `accepted` so the caller leaves Connecting and joins Agora. Fire-and-forget here.
     */
    private fun sendAcceptedFcmFireAndForget(
        context: Context,
        selfUserId: Int?,
        peerUserId: Int,
        callType: String?,
        channelName: String?,
        branchTag: String,
    ) {
        if (selfUserId == null || peerUserId <= 0 || callType.isNullOrEmpty() || channelName.isNullOrEmpty()) {
            Log.w(
                "HimaIncomingCall",
                "$branchTag: skip accepted FCM self=$selfUserId peer=$peerUserId ct=$callType ch=$channelName"
            )
            return
        }
        val repo = (context.applicationContext as BaseApplication).fcmNotificationRepository
        Log.d(
            "HimaIncomingCall",
            "$branchTag: sending accepted FCM userId=$selfUserId receiverId=$peerUserId"
        )
        repo.sendFcmNotification(
            selfUserId,
            peerUserId,
            callType,
            channelName,
            "accepted",
            object : NetworkCallback<FcmNotificationResponse> {
                override fun onResponse(
                    call: retrofit2.Call<FcmNotificationResponse>,
                    response: retrofit2.Response<FcmNotificationResponse>
                ) {
                    Log.d("FCMNotification", "Accept-FCM ok body=${response.body()?.message} ($branchTag)")
                }

                override fun onFailure(
                    call: retrofit2.Call<FcmNotificationResponse>,
                    t: Throwable
                ) {
                    Log.e("FCMNotification", "Accept-FCM failure ($branchTag): ${t.message}", t)
                }

                override fun onNoNetwork() {
                    Log.e("FCMNotification", "Accept-FCM no network ($branchTag)")
                }
            }
        )
    }

    private fun reportCallStatusRejected(
        context: Context,
        selfUserId: Int?,
        peerUserId: Int,
        callId: Int,
    ) {
        if (selfUserId == null || peerUserId <= 0 || callId <= 0) {
            Log.w("CallStatus", "Skipping notification-reject call_status self=$selfUserId peer=$peerUserId callId=$callId")
            return
        }
        Log.d("CallStatus", "NotificationReject → rejected/receiver self=$selfUserId peer=$peerUserId callId=$callId")
        val repo = (context.applicationContext as BaseApplication).callStatusRepository
        val request = CallStatusRequest(
            userId = selfUserId,
            receivedUserId = peerUserId,
            callId = callId,
            endReason = CallEndReason.REJECTED,
            endedBy = CallEndedBy.RECEIVER,
            endedByUserId = selfUserId,
            durationSeconds = 0,
        )
        repo.callStatus(request, object : NetworkCallback<CallStatusResponse> {
            override fun onResponse(
                call: retrofit2.Call<CallStatusResponse>,
                response: retrofit2.Response<CallStatusResponse>
            ) {
                Log.d("CallStatus", "Notification-reject posted url=${call.request().url} body=${response.body()}")
            }

            override fun onFailure(call: retrofit2.Call<CallStatusResponse>, t: Throwable) {
                Log.e("CallStatus", "Notification-reject failure url=${call.request().url}", t)
            }

            override fun onNoNetwork() {
                Log.w("CallStatus", "Notification-reject — no network")
            }
        })
        // REJECT_FALSE_MISS_2026_07_09: durable backstop so a notification-decline
        // reject survives offline / process death (idempotent alongside the post above).
        com.gmwapp.hima.workers.CallStatusWorker.enqueueReject(context, selfUserId, peerUserId, callId)
    }
}


