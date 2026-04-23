package com.gmwapp.hima.agora

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.telecom.DisconnectCause
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.BankUpdateActivity
import com.gmwapp.hima.activities.EarningsActivity
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.activities.NewLoginActivity
import com.gmwapp.hima.agora.female.FemaleAudioCallingActivity
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity
import com.gmwapp.hima.agora.female.FemaleVideoCallingActivity
import com.gmwapp.hima.agora.male.MaleCallAcceptActivity
import com.gmwapp.hima.agora.telecom.HimaConnection
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import com.gmwapp.hima.repositories.FcmNotificationRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponse
import com.gmwapp.hima.utils.MaleNotificationFcmGate
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.onesignal.OneSignal
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var fcmNotificationRepository: FcmNotificationRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMNewToken", "New token: $token")

        // FirebaseMessagingService is not a Hilt entry point, so the ViewModel/repository
        // graph isn't available here. Hand off to WorkManager so the registration survives
        // process death and retries transient failures.
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userId = prefs?.getUserData()?.id ?: 0
        val authToken = prefs?.getAuthenticationToken().orEmpty()
        if (userId <= 0 || authToken.isBlank()) {
            Log.d("FCMNewToken", "No signed-in user — skipping register (uid=$userId)")
            return
        }

        val input = androidx.work.Data.Builder()
            .putInt(com.gmwapp.hima.workers.FcmTokenRegisterWorker.KEY_USER_ID, userId)
            .putString(com.gmwapp.hima.workers.FcmTokenRegisterWorker.KEY_TOKEN, token)
            .putString(com.gmwapp.hima.workers.FcmTokenRegisterWorker.KEY_AUTH_TOKEN, authToken)
            .build()

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val request = androidx.work.OneTimeWorkRequestBuilder<
            com.gmwapp.hima.workers.FcmTokenRegisterWorker
        >()
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                java.util.concurrent.TimeUnit.SECONDS
            )
            .build()

        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "${com.gmwapp.hima.workers.FcmTokenRegisterWorker.WORK_NAME_PREFIX}$userId",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
        Log.d("FCMNewToken", "Enqueued FcmTokenRegisterWorker for user=$userId")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var gender = userData?.gender
        Log.d("FCM", "From: ${remoteMessage.from}")
        Log.d("FCM_Data_Complete", "From: ${remoteMessage.data}")
        Log.d("FCM_Message", "Message data payload: ${remoteMessage.data["message"]}")

        // DND check: drop ALL incoming notifications when DND is active and not yet expired
        if (BaseApplication.isDndActiveStatic(userData)) {
            Log.d("FCM", "DND is active, dropping notification.")
            return
        }

        if (remoteMessage.getPriority() == RemoteMessage.PRIORITY_HIGH) {
            Log.d("FCM_Message", "🔥 High-priority notification received!");
        } else {
            Log.d("FCM_Message", "⚠️ Low-priority notification received!");
        }


        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"] ?: ""
            val message = remoteMessage.data["message"] ?: ""
            val callType = remoteMessage.data["callType"]
            val senderId = remoteMessage.data["senderId"]?.toIntOrNull() ?: -1
            val channelName = remoteMessage.data["channelName"] ?: "default_channel"
            val fcmCurrentActivity = BaseApplication.getInstance()?.getCurrentActivity()
            Log.d(
                "MaleVideoEndFlow",
                "FCM rx type=$type message=$message senderId=$senderId callType=$callType gender=$gender currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
            )

            // Admin/server forced logout/clear session.
            if (type == "clear_data" || message == "clear_data") {
                handleClearDataFcm()
                return
            }

            if (type == "ludo_invite" ||
                type == "ludo_invite_accepted" ||
                type == "ludo_invite_rejected" ||
                type == "ludo_invite_expired" ||
                type == "game_end" ||
                message == "game_end"
            ) {
                val ludoInviteId = remoteMessage.data["invite_id"]
                val roomCode = remoteMessage.data["room_code"]
                val fromUserId = remoteMessage.data["from_user_id"]?.toIntOrNull()
                    ?: remoteMessage.data["by_user_id"]?.toIntOrNull()
                val fromUserName = remoteMessage.data["from_user_name"]
                val joinUrl = remoteMessage.data["join_url"]

                FcmUtils.updateLudoEvent(
                    FcmUtils.LudoEvent(
                        type = if (message == "game_end") "game_end" else type,
                        inviteId = ludoInviteId,
                        roomCode = roomCode,
                        fromUserId = fromUserId,
                        fromUserName = fromUserName,
                        joinUrl = joinUrl
                    )
                )
                return
            }

            if (MaleNotificationFcmGate.shouldDropDataPayload(
                    gender,
                    remoteMessage.data,
                    BaseApplication.getInstance()?.getPrefs()
                )
            ) {
                Log.d("FCM", "Dropped by male Manage Notifications prefs (type=${remoteMessage.data["type"]})")
                return
            }

            val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()

            if (message.startsWith("incoming call")) {
                val parts = message.split(" ")
                if (parts.size >= 5) {
                    val callId = parts[2]  // Extract callId from the message
                    val receiverImg = parts[3]  // Extract receiver image URL
                    val receiverName = parts[4]  // Extract receiver name

                    Log.d("startingActvity","$gender")


                    // 🕒 CHECK CALL TIME DIFFERENCE
                    val fcmTimestamp = remoteMessage.data["timestamp"]
                    val currentTime = System.currentTimeMillis()
                    val callTimestamp = try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        sdf.timeZone = java.util.TimeZone.getDefault()
                        sdf.parse(fcmTimestamp)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                    val timeDiffSeconds = (currentTime - callTimestamp) / 1000
                    Log.w("FCM_Time", "⚠️ TimeDiffercne = ($timeDiffSeconds s late)")

                    if (timeDiffSeconds > 20) {
                        Log.w("FCM_Time", "⚠️ Ignoring old call notification ($timeDiffSeconds s late)")
                        return
                    }

                    // SINGLE-CALL GUARD: we already have a fresh pending incoming call (ringing
                    // or awaiting accept). A different caller arriving now would replace the
                    // notification and cause two rings, so auto-reject them as busy. Same-sender
                    // duplicate FCMs are also ignored to avoid double ringtone/notification.
                    val appForBusy = BaseApplication.getInstance()
                    if (appForBusy?.isIncomingCallFresh() == true) {
                        val pendingSenderId = appForBusy.getSenderIdForSplashActivity()
                        if (pendingSenderId != senderId) {
                            Log.d(
                                "FCM",
                                "Busy: already ringing from $pendingSenderId, auto-rejecting new incoming from $senderId"
                            )
                            sendAutoRejectNotification(
                                userData?.id,
                                senderId,
                                callType,
                                channelName
                            )
                        } else {
                            Log.d(
                                "FCM",
                                "Duplicate incoming FCM from same sender $senderId — ignoring"
                            )
                        }
                        return
                    }


                    if (gender == "female") {
                        if (currentActivity is FemaleCallAcceptActivity ||
                            currentActivity is FemaleAudioCallingActivity ||
                            currentActivity is FemaleVideoCallingActivity ||
                            currentActivity is com.gmwapp.hima.activities.IplRoomCallActivity) {

                            Log.d("FCM", "User is already in a call. Ignoring incoming call notification.")

                            val receiverId = senderId
                            sendAutoRejectNotification(userData?.id, receiverId, callType, channelName)
                            return
                        }

                        BaseApplication.getInstance()?.saveSenderId(senderId)
                        BaseApplication.getInstance()?.playIncomingCallSound()

                        callType?.let {
                            BaseApplication.getInstance()?.setIncomingCall(
                                senderId,
                                it, channelName, callId.toIntOrNull() ?: 0
                            )
                        }

                        val intent = Intent(this, FemaleCallAcceptActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("CALL_TYPE", callType)
                            putExtra("SENDER_ID", senderId)
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("Caller_NAME", receiverName)
                            putExtra("Caller_Image", receiverImg)
                            putExtra("CALL_ID", callId.toIntOrNull() ?: 0)
                        }

                        val telecomExtras = Bundle().apply {
                            putString(HimaConnection.EXTRA_CALL_TYPE, callType)
                            putInt(HimaConnection.EXTRA_SENDER_ID, senderId)
                            putString(HimaConnection.EXTRA_CHANNEL_NAME, channelName)
                            putInt(HimaConnection.EXTRA_CALL_ID, callId.toIntOrNull() ?: 0)
                            putString(HimaConnection.EXTRA_CALLER_NAME, receiverName)
                            putString(HimaConnection.EXTRA_CALLER_IMAGE, receiverImg)
                            putString(HimaConnection.EXTRA_RECEIVER_GENDER, "female")
                        }
                        logIncomingCallEntry(
                            "female_incoming",
                            gender,
                            callType,
                            callId.toIntOrNull() ?: 0,
                            senderId,
                            channelName
                        )
                        Log.d(INCOMING_CALL_LOG_TAG, "female branch: before tryAddIncomingCall")
                        val telecomOkFemale = HimaTelecomManager.tryAddIncomingCall(this, telecomExtras)
                        Log.d(
                            INCOMING_CALL_LOG_TAG,
                            "female branch: after tryAddIncomingCall telecomOk=$telecomOkFemale (CallStyle still posted; self-managed has no system UI)"
                        )
                        Log.d(INCOMING_CALL_LOG_TAG, "female branch: before notifyIncomingCallWithCallStyle")
                        notifyIncomingCallWithCallStyle(
                            isMale = false,
                            callType,
                            senderId,
                            channelName,
                            callId.toIntOrNull() ?: 0,
                            receiverName,
                            receiverImg
                        )
                        Log.d(INCOMING_CALL_LOG_TAG, "female branch: after notifyIncomingCallWithCallStyle")

                        Log.d("callType", "$callType")
                        if (!isAppInBackground(applicationContext)) {
                            Log.d("FCMService", "App is in foreground — launching FemaleCallAcceptActivity")
                            startActivity(intent)
                        }

//                        if (BaseApplication.getInstance()?.isAppInForeground() == true) {
//                            // App is in foreground, open activity directly
//                            startActivity(intent)
//                        } else {
//                            // App is in background, show notification instead
//                            showIncomingCallNotification(callType, senderId, channelName, callId.toIntOrNull() ?: 0, receiverName, receiverImg)
//                        }


                        if (currentActivity !is MainActivity &&
                            currentActivity !is EarningsActivity &&
                            currentActivity !is BankUpdateActivity) {

                            // App is NOT in these activities → Show notification
                          //  showIncomingCallNotification(callType, senderId, channelName, callId.toIntOrNull() ?: 0, receiverName, receiverImg)
                        } else {
                            Log.d("currentActivity", "User is in $currentActivity, skipping notification")

                        }


                    }

                    // ========== MALE INCOMING CALL HANDLING ==========
                    // Added for males to receive calls from females
                    if (gender == "male") {
                        // Import male activities
                        val MaleCallAcceptActivity = com.gmwapp.hima.agora.male.MaleCallAcceptActivity::class.java
                        val MaleAudioCallingActivity = com.gmwapp.hima.agora.male.MaleAudioCallingActivity::class.java
                        val MaleVideoCallingActivity = com.gmwapp.hima.agora.male.MaleVideoCallingActivity::class.java
                        val IplRoomCallActivity = com.gmwapp.hima.activities.IplRoomCallActivity::class.java

                        if (currentActivity?.javaClass == MaleCallAcceptActivity ||
                            currentActivity?.javaClass == MaleAudioCallingActivity ||
                            currentActivity?.javaClass == MaleVideoCallingActivity ||
                            currentActivity?.javaClass == IplRoomCallActivity) {

                            Log.d("FCM", "Male user is already in a call. Ignoring incoming call notification.")

                            val receiverId = senderId
                            sendAutoRejectNotification(userData?.id, receiverId, callType, channelName)
                            return
                        }

                        BaseApplication.getInstance()?.saveSenderId(senderId)
                        BaseApplication.getInstance()?.playIncomingCallSound()

                        callType?.let {
                            BaseApplication.getInstance()?.setIncomingCall(
                                senderId,
                                it, channelName, callId.toIntOrNull() ?: 0
                            )
                        }

                        val intent = Intent(this, MaleCallAcceptActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("CALL_TYPE", callType)
                            putExtra("SENDER_ID", senderId)
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("Caller_NAME", receiverName)
                            putExtra("Caller_Image", receiverImg)
                            putExtra("CALL_ID", callId.toIntOrNull() ?: 0)
                        }

                        val telecomExtrasMale = Bundle().apply {
                            putString(HimaConnection.EXTRA_CALL_TYPE, callType)
                            putInt(HimaConnection.EXTRA_SENDER_ID, senderId)
                            putString(HimaConnection.EXTRA_CHANNEL_NAME, channelName)
                            putInt(HimaConnection.EXTRA_CALL_ID, callId.toIntOrNull() ?: 0)
                            putString(HimaConnection.EXTRA_CALLER_NAME, receiverName)
                            putString(HimaConnection.EXTRA_CALLER_IMAGE, receiverImg)
                            putString(HimaConnection.EXTRA_RECEIVER_GENDER, "male")
                        }
                        logIncomingCallEntry(
                            "male_incoming",
                            gender,
                            callType,
                            callId.toIntOrNull() ?: 0,
                            senderId,
                            channelName
                        )
                        Log.d(INCOMING_CALL_LOG_TAG, "male branch: before tryAddIncomingCall")
                        val telecomOkMale = HimaTelecomManager.tryAddIncomingCall(this, telecomExtrasMale)
                        Log.d(
                            INCOMING_CALL_LOG_TAG,
                            "male branch: after tryAddIncomingCall telecomOk=$telecomOkMale (CallStyle still posted; self-managed has no system UI)"
                        )
                        Log.d(INCOMING_CALL_LOG_TAG, "male branch: before notifyIncomingCallWithCallStyle")
                        notifyIncomingCallWithCallStyle(
                            isMale = true,
                            callType,
                            senderId,
                            channelName,
                            callId.toIntOrNull() ?: 0,
                            receiverName,
                            receiverImg
                        )
                        Log.d(INCOMING_CALL_LOG_TAG, "male branch: after notifyIncomingCallWithCallStyle")

                        Log.d("MaleCallAccept_CallType", "$callType")
                        if (!isAppInBackground(applicationContext)) {
                            Log.d("FCMService_Male", "App is in foreground — launching MaleCallAcceptActivity")
                            startActivity(intent)
                        }

                        if (currentActivity !is MainActivity &&
                            currentActivity !is EarningsActivity &&
                            currentActivity !is BankUpdateActivity) {
                            // App is NOT in these activities → Show notification
                            // Already shown above
                        } else {
                            Log.d("currentActivity_Male", "User is in $currentActivity, skipping notification")
                        }
                    }
                    // ========== END MALE INCOMING CALL HANDLING ==========



//
//                    val serviceIntent = Intent(this, FcmCallService::class.java).apply {
//                        putExtra("CALL_TYPE", callType)
//                        putExtra("SENDER_ID", senderId)
//                        putExtra("CHANNEL_NAME", channelName)
//                        putExtra("CALL_ID", callId)
//                    }
//                    startForegroundService(serviceIntent)




                }
            }



            if (message == "accepted" || message == "rejected" && gender=="male") {
                Log.d(
                    "MaleVideoEndFlow",
                    "route=male_accepted_rejected_updateCallStatus message=$message channelName=$channelName currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
                )
                FcmUtils.updateCallStatus(message, channelName)
            }

            // Handle call status updates for females (receiving acceptance/rejection from males)
            if ((message == "accepted" || message == "rejected") && gender == "female") {
                Log.d("FCM_Female", "Received call status: $message from male user: $senderId")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=female_accepted_rejected_updateCallStatus message=$message senderId=$senderId currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
                )
                FcmUtils.updateCallStatus(message, senderId.toString())
            }

            if (message == "userBusy" && gender == "male") {
                Log.d(
                    "MaleVideoEndFlow",
                    "route=userBusy_male currentActivity=${fcmCurrentActivity?.javaClass?.simpleName} callType=$callType senderId=$senderId"
                )
                Log.d("FCM", "User is busy. Checking current activity.")
                
                // Get current activity
                val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
                
                when {
                    // Direct call connecting screen - show message and redirect to random call
                    currentActivity is com.gmwapp.hima.agora.male.MaleCallConnectingActivity -> {
                        Log.d("FCM", "User is on direct call connecting screen. Showing busy message.")
                        
                        // Get callType and receiver name from FCM data
                        val userName = remoteMessage.data["receiverName"] ?: "User"
                        
                        // Notify MaleCallConnectingActivity via FcmUtils
                        FcmUtils.updateUserBusyStatus(callType ?: "audio", userName)
                    }
                    
                    // Random call connecting screen - silently retry with another user
                    currentActivity is AgoraRandomCallActivity -> {
                        Log.d("FCM", "User is on random call connecting screen. Triggering retry.")
                        
                        // Get callType from FCM data
                        val userName = remoteMessage.data["receiverName"] ?: "User"
                        
                        // Notify AgoraRandomCallActivity via FcmUtils to retry
                        FcmUtils.updateUserBusyStatus(callType ?: "audio", userName)
                    }
                    
                    // User is in other activities - ignore
                    else -> {
                        Log.d("FCM", "User is not on connecting screen (current: ${currentActivity?.javaClass?.simpleName}). Ignoring userBusy message.")
                        // User might be in:
                        // - MaleAudioCallingActivity / MaleVideoCallingActivity (already in a call)
                        // - MainActivity (already went back)
                        // In all these cases, do nothing
                    }
                }
            }


            if (message == "callDeclined" && gender == "female") {
                Log.d(
                    "MaleVideoEndFlow",
                    "route=callDeclined_female senderId=$senderId currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
                )
                Log.d("FCM", "User is busy. Redirecting to MainActivity.")


                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val isScreenLocked = keyguardManager.isKeyguardLocked
                var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                if (senderId==previousSenderId) {
                    HimaTelecomManager.endActiveCall(DisconnectCause.REMOTE)
                    BaseApplication.getInstance()?.stopRingtone()
                    cancelIncomingCallNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
//                    // Stop the foreground service
//                    val serviceIntent = Intent(this, FcmCallService::class.java)
//                    stopService(serviceIntent)  // Stop the service

                    if (isScreenLocked) {
                        // If the screen is locked, forcefully close the app
                        Log.d("isScreenLocked", "$isScreenLocked")
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(mainIntent)
                        currentActivity?.moveTaskToBack(true) // Move app to background
                        currentActivity?.finishAffinity()
                    }else{
                        val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()


                        if (isAppInBackground(applicationContext)) {
                            Log.d("FCMService", "App is in background (Minimized)")
                            val mainIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(mainIntent)
                            currentActivity?.moveTaskToBack(true) // Move app to background
                            currentActivity?.finishAffinity()

                        } else {
                            Log.d("FCMService", "App is in foreground (Visible)")
                            if (currentActivity !is MainActivity) {
                                val mainIntent = Intent(this, MainActivity::class.java).apply {
                                    flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                startActivity(mainIntent)
                            }
//                        Log.d("currentactvityt","$mainIntent")

//                            if (currentActivity is FemaleCallAcceptActivity) {
//                                currentActivity.finishAffinity() // Close all activities
//                                currentActivity.moveTaskToBack(true) // Send app to background
//                            }

                    }



                    }



                }

            }

            // ========== MALE HANDLER FOR CALL DECLINED ==========
            // Added for males to receive call cancellation from females
            if (message == "callDeclined" && gender == "male") {
                Log.d(
                    "MaleVideoEndFlow",
                    "route=callDeclined_male senderId=$senderId previousSenderId=${BaseApplication.getInstance()?.getSenderId()} currentActivity=${fcmCurrentActivity?.javaClass?.simpleName}"
                )
                Log.d("FCM_Male", "Female caller cancelled. Closing incoming call screen.")

                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val isScreenLocked = keyguardManager.isKeyguardLocked
                var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                
                if (senderId == previousSenderId) {
                    HimaTelecomManager.endActiveCall(DisconnectCause.REMOTE)
                    BaseApplication.getInstance()?.stopRingtone()
                    cancelIncomingCallNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()

                    if (isScreenLocked) {
                        // If the screen is locked, forcefully close the app
                        Log.d("isScreenLocked_Male", "$isScreenLocked")
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(mainIntent)
                        currentActivity?.moveTaskToBack(true) // Move app to background
                        currentActivity?.finishAffinity()
                    } else {
                        val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()

                        if (isAppInBackground(applicationContext)) {
                            Log.d("FCMService_Male", "App is in background (Minimized)")
                            val mainIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(mainIntent)
                            currentActivity?.moveTaskToBack(true) // Move app to background
                            currentActivity?.finishAffinity()
                        } else {
                            Log.d("FCMService_Male", "App is in foreground (Visible)")
                            if (currentActivity !is MainActivity) {
                                val mainIntent = Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                startActivity(mainIntent)
                            }
                        }
                    }
                }
            }
            // ========== END MALE HANDLER FOR CALL DECLINED ==========

            if (message == "remainingTimeUpdated" && gender == "female") {

                var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                if (senderId==previousSenderId){

                    FcmUtils.updateRemainingTime(message)

                }

            }


            if (message.startsWith("switchToVideo") && gender == "female") {
                    val parts = message.split(" ")
                    if (parts.size == 2) {
                        val callId = parts[1]  // Extract callId from the message
                        val callidInt: Int = callId.toIntOrNull() ?: 0  // Defaults to 0 if conversion fails
                        Log.d("callIdofSwitch", "$callId")


                var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                if (senderId==previousSenderId){

                    Log.d("switchToVideo","$message")
                    Log.d(
                        "MaleVideoEndFlow",
                        "route=female_switchToVideo -> UpdateCallSwitch(callidInt=$callidInt senderId=$senderId)"
                    )
                    FcmUtils.UpdateCallSwitch("switchToVideo",callidInt)

                }

            }}

            if (message.startsWith("switchToAudio") && gender == "female") {
                val parts = message.split(" ")
                if (parts.size == 2) {
                    val callId = parts[1]  // Extract callId from the message
                    val callidInt: Int = callId.toIntOrNull() ?: 0  // Defaults to 0 if conversion fails
                    Log.d("callIdofSwitch", "$callId")


                    var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                    if (senderId==previousSenderId){

                        Log.d("switchToVideo","$message")
                        Log.d(
                            "MaleVideoEndFlow",
                            "route=female_switchToAudio -> UpdateCallSwitch(callidInt=$callidInt senderId=$senderId)"
                        )
                        FcmUtils.UpdateCallSwitch("switchToAudio",callidInt)

                    }

                }}

            if (message == "VideoAccepted" && gender == "male") {

                Log.d("switchToVideo","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=male_VideoAccepted -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)



            }

            if (message == "AudioAccepted" && gender == "male") {

                Log.d("AudioAccepted","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=male_AudioAccepted -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)



            }

            if (message == "SwitchDeclined" && gender == "male") {

                Log.d("SwitchDeclined","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=male_SwitchDeclined -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)



            }


            if (message.startsWith("switchToVideo") && gender == "male") {
                val parts = message.split(" ")
                if (parts.size == 2) {
                    val callId = parts[1]  // Extract callId from the message
                    val callidInt: Int = callId.toIntOrNull() ?: 0  // Defaults to 0 if conversion fails
                    Log.d("callIdofSwitch", "$callId")
                    Log.d("switchToVideo","$message")
                    Log.d(
                        "MaleVideoEndFlow",
                        "route=male_switchToVideo -> UpdateCallSwitch(callidInt=$callidInt senderId=$senderId)"
                    )
                    FcmUtils.UpdateCallSwitch("switchToVideo",callidInt)

                }}




            if (message == "VideoAccepted" && gender == "female") {
                Log.d("switchToVideo","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=female_VideoAccepted -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)
            }

            if (message == "giftSent" && gender == "female") {
                var giftImage= callType
                FcmUtils.giftReceivedImage(giftImage.toString())
            }


            if (message.startsWith("switchToAudio") && gender == "male") {
                val parts = message.split(" ")
                if (parts.size == 2) {
                    val callId = parts[1]  // Extract callId from the message
                    val callidInt: Int = callId.toIntOrNull() ?: 0  // Defaults to 0 if conversion fails
                    Log.d("callIdofSwitch", "$callId")
                    Log.d("switchToAudio","$message")
                    Log.d(
                        "MaleVideoEndFlow",
                        "route=male_switchToAudio -> UpdateCallSwitch(callidInt=$callidInt senderId=$senderId)"
                    )
                    FcmUtils.UpdateCallSwitch("switchToAudio",callidInt)

                }}


            if (message == "AudioAccepted" && gender == "female") {

                Log.d("AudioAccepted","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=female_AudioAccepted -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)

            }

            if (message == "SwitchDeclined" && gender == "female") {

                Log.d("SwitchDeclined","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=female_SwitchDeclined -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)

            }



            if (message == "greyScreenEnable") {

                Log.d("greyScreenLog","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=greyScreenEnable -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)
                FcmUtils.greyScreenLiveData.postValue(message)

            }

            if (message == "greyScreenDisable") {

                Log.d("greyScreenLog","$message")
                Log.d(
                    "MaleVideoEndFlow",
                    "route=greyScreenDisable -> UpdateCallSwitch(senderId=$senderId)"
                )
                FcmUtils.UpdateCallSwitch(message, senderId)
                FcmUtils.greyScreenLiveData.postValue(message)

            }


        }



    }

    private fun handleClearDataFcm() {
        try {
            Log.w("FCM_ClearData", "Received clear_data. Clearing user session.")

            // Stop any ongoing call UI/notifications/ringtone best-effort.
            HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
            BaseApplication.getInstance()?.stopRingtone()
            cancelIncomingCallNotification()
            BaseApplication.getInstance()?.clearIncomingCall()

            // Clear stored login/session data.
            BaseApplication.getInstance()?.getPrefs()?.clearUserData()
            // Wipe per-peer conversation shortcuts so the previous user's avatars
            // don't stick around when a different account signs in on this device.
            runCatching {
                androidx.core.content.pm.ShortcutManagerCompat
                    .removeAllDynamicShortcuts(applicationContext)
            }
            try {
                // Detach external id so the (now logged-out) device stops
                // receiving user-targeted pushes. Do NOT optOut() — that persists
                // enabled=false server-side and blocks the next login on this device.
                OneSignal.logout()
            } catch (e: Exception) {
                Log.e("OneSignalLoginScreen", "Failed to logout: ${e.message}")
            }

            // If app is visible, redirect to login; otherwise show a notification that opens login.
            if (!isAppInBackground(applicationContext)) {
                val intent = Intent(this, NewLoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            } else {
                Log.e("FCM_ClearData", "Failed to clear session:")
            }
        } catch (e: Exception) {
            Log.e("FCM_ClearData", "Failed to clear session: ${e.message}", e)
        }
    }

    private fun showSessionClearedNotification() {
        createSystemNotificationChannel()

        val intent = Intent(this, NewLoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            9901,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(this, "system_events")
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Session cleared")
            .setContentText("Please login again to continue.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(9901, notification)
    }

    private fun createSystemNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "system_events",
                "System",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }



    private fun isAppInBackground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return true

        for (process in appProcesses) {
            if (process.processName == context.packageName) {
                return process.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }
        return true
    }

    /** Ensures channel exists, then logs device/permission state for incoming-call debugging. */
    private fun logIncomingCallEntry(
        leg: String,
        gender: String?,
        callType: String?,
        callId: Int,
        senderId: Int,
        channelName: String
    ) {
        createNotificationChannel()
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val locked = km.isKeyguardLocked
        val bg = isAppInBackground(applicationContext)
        val postNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent()
        } else {
            null
        }
        val manageOwnCallsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.MANAGE_OWN_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            null
        }
        val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(CALLS_NOTIFICATION_CHANNEL_ID)?.importance
        } else {
            null
        }
        Log.d(
            INCOMING_CALL_LOG_TAG,
            "[$leg] sdk=${Build.VERSION.SDK_INT} gender=$gender callType=$callType callId=$callId " +
                "senderId=$senderId channel=$channelName keyguardLocked=$locked appInBackground=$bg " +
                "postNotificationsGranted=$postNotificationsGranted " +
                "canUseFullScreenIntent=$canUseFullScreenIntent manageOwnCallsGranted=$manageOwnCallsGranted " +
                "calls_v3_channelImportance=$channelImportance"
        )
    }


    private fun sendAutoRejectNotification(senderId: Int?, receiverId: Int?, callType: String?, channelName: String?) {
        if (senderId != null && receiverId != null && callType != null && channelName != null) {
            fcmNotificationRepository.sendFcmNotification(
                senderId, receiverId, callType, channelName, "userBusy",
                object : NetworkCallback<FcmNotificationResponse> {
                    override fun onResponse(call: retrofit2.Call<FcmNotificationResponse>, response: retrofit2.Response<FcmNotificationResponse>) {
                        Log.d("FCMNotification", "Auto-reject sent: ${response.body()?.message}")
                    }

                    override fun onFailure(call: retrofit2.Call<FcmNotificationResponse>, t: Throwable) {
                        Log.e("FCMNotification", "Error sending auto-reject: ${t.message}")
                    }

                    override fun onNoNetwork() {
                        Log.e("FCMNotification", "No network for auto-reject")
                    }
                }
            )
        }
    }

    private fun notifyIncomingCallWithCallStyle(
        isMale: Boolean,
        callType: String?,
        senderId: Int,
        channelName: String,
        callId: Int,
        receiverName: String,
        receiverImg: String
    ) {
        createNotificationChannel()
        val chImp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(CALLS_NOTIFICATION_CHANNEL_ID)?.importance
        } else null
        Log.d(
            INCOMING_CALL_LOG_TAG,
            "notifyIncomingCallWithCallStyle: begin isMale=$isMale callId=$callId senderId=$senderId calls_v3_importance=$chImp"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(
                INCOMING_CALL_LOG_TAG,
                "notifyIncomingCallWithCallStyle: POST_NOTIFICATIONS granted=$granted (still attempting CallStyle / Telecom-linked notify)"
            )
        }

        val targetClass = if (isMale) MaleCallAcceptActivity::class.java else FemaleCallAcceptActivity::class.java
        val contentReq = if (isMale) 201 else 101
        val acceptAction = if (isMale) "ACTION_ACCEPT_CALL_MALE" else "ACTION_ACCEPT_CALL"
        val rejectAction = if (isMale) "ACTION_REJECT_CALL_MALE" else "ACTION_REJECT_CALL"
        val acceptReq = if (isMale) 202 else 102
        val rejectReq = if (isMale) 203 else 103

        val tapIntent = Intent(this, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
            putExtra("Caller_NAME", receiverName)
            putExtra("Caller_Image", receiverImg)
        }
        val contentPi = PendingIntent.getActivity(
            this,
            contentReq,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = acceptAction
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
        }
        val acceptPi = PendingIntent.getBroadcast(
            this,
            acceptReq,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = rejectAction
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
        }
        val rejectPi = PendingIntent.getBroadcast(
            this,
            rejectReq,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder()
            .setName(receiverName)
            .setImportant(true)
            .build()

        fun buildNotification(person: Person): Notification {
            return NotificationCompat.Builder(this, CALLS_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        person,
                        rejectPi,
                        acceptPi
                    )
                )
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentPi)
                .setFullScreenIntent(contentPi, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(35_000L)
                .addPerson(person)
                .build()
        }

        val notifTag = callId.toString()
        try {
            Log.d(
                INCOMING_CALL_LOG_TAG,
                "notifyIncomingCallWithCallStyle: posting notify tag=$notifTag id=$INCOMING_CALL_NOTIFICATION_ID channel=$CALLS_NOTIFICATION_CHANNEL_ID"
            )
            NotificationManagerCompat.from(this).notify(
                notifTag,
                INCOMING_CALL_NOTIFICATION_ID,
                buildNotification(caller)
            )
            Log.d(
                INCOMING_CALL_LOG_TAG,
                "notifyIncomingCallWithCallStyle: CallStyle notification posted (isMale=$isMale, tag=$notifTag, id=$INCOMING_CALL_NOTIFICATION_ID)"
            )

            Glide.with(this)
                .asBitmap()
                .load(receiverImg)
                .apply(RequestOptions.circleCropTransform())
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        val currentTag = BaseApplication.getInstance()?.getLastIncomingCallTag()
                        if (currentTag != notifTag) {
                            Log.d(
                                INCOMING_CALL_LOG_TAG,
                                "avatar refresh skipped: call $notifTag no longer pending (current=$currentTag)"
                            )
                            return
                        }
                        val personWithIcon = Person.Builder()
                            .setName(receiverName)
                            .setImportant(true)
                            .setIcon(IconCompat.createWithBitmap(resource))
                            .build()
                        NotificationManagerCompat.from(applicationContext).notify(
                            notifTag,
                            INCOMING_CALL_NOTIFICATION_ID,
                            buildNotification(personWithIcon)
                        )
                        Log.d(INCOMING_CALL_LOG_TAG, "notifyIncomingCallWithCallStyle: refreshed with caller avatar bitmap")
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        } catch (e: SecurityException) {
            Log.e(INCOMING_CALL_LOG_TAG, "notifyIncomingCallWithCallStyle: SecurityException ${e.message}", e)
        } catch (e: Exception) {
            Log.e(INCOMING_CALL_LOG_TAG, "notifyIncomingCallWithCallStyle: Exception ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CALLS_NOTIFICATION_CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setBypassDnd(true)
                }
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        /** Channel ID bump: channel importance / options are immutable per ID on Android O+. */
        const val CALLS_NOTIFICATION_CHANNEL_ID = "calls_v3"
        private const val INCOMING_CALL_LOG_TAG = "HimaIncomingCall"
        private const val INCOMING_CALL_NOTIFICATION_ID = 1
    }


    fun cancelIncomingCallNotification() {
        BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
    }

}