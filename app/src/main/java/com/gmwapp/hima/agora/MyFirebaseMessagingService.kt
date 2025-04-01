package com.gmwapp.hima.agora

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.Notification
import android.provider.Settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.BankUpdateActivity
import com.gmwapp.hima.activities.EarningsActivity
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.female.FemaleAudioCallingActivity
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity
import com.gmwapp.hima.agora.female.FemaleVideoCallingActivity
import com.gmwapp.hima.repositories.FcmNotificationRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponse
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
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
        // Send this token to your backend server if needed.
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var gender = userData?.gender
        Log.d("FCM", "From: ${remoteMessage.from}")
        Log.d("FCM_Message", "Message data payload: ${remoteMessage.data["message"]}")

        if (remoteMessage.getPriority() == RemoteMessage.PRIORITY_HIGH) {
            Log.d("FCM_Message", "🔥 High-priority notification received!");
        } else {
            Log.d("FCM_Message", "⚠️ Low-priority notification received!");
        }


        if (remoteMessage.data.isNotEmpty()) {
            val message = remoteMessage.data["message"] ?: ""
            val callType = remoteMessage.data["callType"]
            val senderId = remoteMessage.data["senderId"]?.toIntOrNull() ?: -1
            val channelName = remoteMessage.data["channelName"] ?: "default_channel"


            val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()

            if (message.startsWith("incoming call")) {
                val parts = message.split(" ")
                if (parts.size >= 5) {
                    val callId = parts[2]  // Extract callId from the message
                    val receiverImg = parts[3]  // Extract receiver image URL
                    val receiverName = parts[4]  // Extract receiver name

                    Log.d("startingActvity","$gender")


                    if (gender == "female") {
                        if (currentActivity is FemaleCallAcceptActivity ||
                            currentActivity is FemaleAudioCallingActivity ||
                            currentActivity is FemaleVideoCallingActivity) {

                            Log.d("FCM", "User is already in a call. Ignoring incoming call notification.")

                            val receiverId = senderId
                            sendAutoRejectNotification(userData?.id, receiverId, callType, channelName)
                            return
                        }

                        BaseApplication.getInstance()?.saveSenderId(senderId)
                        BaseApplication.getInstance()?.playIncomingCallSound()
                        showIncomingCallNotification(callType, senderId, channelName, callId.toIntOrNull() ?: 0, receiverName, receiverImg)


                        callType?.let {
                            BaseApplication.getInstance()?.setIncomingCall(senderId,
                                it, channelName, callId.toIntOrNull() ?: 0)
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



                        Log.d("callType","$callType")
                        if (!Settings.canDrawOverlays(this)) {
                            startActivity(intent)
                        }else{
                            if (isAppInBackground(applicationContext)) {
                                Log.d("FCMService", "App is in background (Minimized)")
                                // Handle background notification logic
                            } else {
                                Log.d("FCMService", "App is in foreground (Visible)")
                                startActivity(intent)
                            }

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
                FcmUtils.updateCallStatus(message, channelName)
            }

            if (message == "userBusy" && gender == "male") {
                Log.d("FCM", "User is busy. Redirecting to MainActivity.")

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "User is busy", Toast.LENGTH_LONG).show()

                }
                    val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(mainIntent)
            }


            if (message == "callDeclined" && gender == "female") {
                Log.d("FCM", "User is busy. Redirecting to MainActivity.")


                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val isScreenLocked = keyguardManager.isKeyguardLocked
                var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                if (senderId==previousSenderId) {
                    BaseApplication.getInstance()?.clearIncomingCall()
                    BaseApplication.getInstance()?.stopRingtone()
//                    // Stop the foreground service
//                    val serviceIntent = Intent(this, FcmCallService::class.java)
//                    stopService(serviceIntent)  // Stop the service
                    cancelIncomingCallNotification()

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
                            val mainIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(mainIntent)
//                        Log.d("currentactvityt","$mainIntent")

//                            if (currentActivity is FemaleCallAcceptActivity) {
//                                currentActivity.finishAffinity() // Close all activities
//                                currentActivity.moveTaskToBack(true) // Send app to background
//                            }

                    }



                    }



                }

            }

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
                        FcmUtils.UpdateCallSwitch("switchToAudio",callidInt)

                    }

                }}

            if (message == "VideoAccepted" && gender == "male") {

                Log.d("switchToVideo","$message")
                FcmUtils.UpdateCallSwitch(message, senderId)



            }

            if (message == "AudioAccepted" && gender == "male") {

                Log.d("AudioAccepted","$message")
                FcmUtils.UpdateCallSwitch(message, senderId)



            }


            if (message.startsWith("switchToVideo") && gender == "male") {
                val parts = message.split(" ")
                if (parts.size == 2) {
                    val callId = parts[1]  // Extract callId from the message
                    val callidInt: Int = callId.toIntOrNull() ?: 0  // Defaults to 0 if conversion fails
                    Log.d("callIdofSwitch", "$callId")
                    Log.d("switchToVideo","$message")
                    FcmUtils.UpdateCallSwitch("switchToVideo",callidInt)

                }}




            if (message == "VideoAccepted" && gender == "female") {

                Log.d("switchToVideo","$message")
                FcmUtils.UpdateCallSwitch(message, senderId)

            }


            if (message.startsWith("switchToAudio") && gender == "male") {
                val parts = message.split(" ")
                if (parts.size == 2) {
                    val callId = parts[1]  // Extract callId from the message
                    val callidInt: Int = callId.toIntOrNull() ?: 0  // Defaults to 0 if conversion fails
                    Log.d("callIdofSwitch", "$callId")
                    Log.d("switchToAudio","$message")
                    FcmUtils.UpdateCallSwitch("switchToAudio",callidInt)

                }}


            if (message == "AudioAccepted" && gender == "female") {

                Log.d("AudioAccepted","$message")
                FcmUtils.UpdateCallSwitch(message, senderId)

            }


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

    private fun showIncomingCallNotification(
        callType: String?,
        senderId: Int,
        channelName: String,
        callId: Int,
        receiverName: String,
        receiverImg: String
    ) {
        createNotificationChannel() // Ensure the notification channel exists

        // Check if we have permission to post notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("Notification", "Permission for notifications not granted!")
                return  // Exit if permission is not granted
            }
        }

        val intent = Intent(this, FemaleCallAcceptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
            putExtra("Caller_NAME", receiverName)
            putExtra("Caller_Image", receiverImg)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "ACTION_ACCEPT_CALL"
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            this, 2, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "ACTION_REJECT_CALL"
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            this, 3, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )



        try {
            val remoteViews = RemoteViews(packageName, R.layout.notification_layout)
            remoteViews.setTextViewText(R.id.caller_name, "$receiverName")
            remoteViews.setTextViewText(R.id.call_type, "Incoming ${callType?.capitalize()} Call")


            // Detect Dark Mode
            val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val textColor = if (isDarkMode) ContextCompat.getColor(this, R.color.white)
            else ContextCompat.getColor(this, R.color.black)

            // Set text color dynamically
            remoteViews.setTextColor(R.id.caller_name, textColor)
            remoteViews.setTextColor(R.id.call_type, textColor)

            remoteViews.setOnClickPendingIntent(R.id.btn_accept, pendingIntent)
            remoteViews.setOnClickPendingIntent(R.id.btn_decline, pendingIntent)

            val builder = NotificationCompat.Builder(this, "calls")
                .setSmallIcon(R.drawable.notification_icon)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setCustomContentView(remoteViews)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)

            Glide.with(this)
                .asBitmap()
                .load(receiverImg)
                .apply(RequestOptions.circleCropTransform())
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        remoteViews.setImageViewBitmap(R.id.profile_image, resource)
                        val manager = NotificationManagerCompat.from(applicationContext)
                        manager.notify(1, builder.build()) // Update notification with image
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // Handle if needed
                    }
                })


            val manager = NotificationManagerCompat.from(this)
            manager.notify(1, builder.build())
            Log.d("NotificationDebug", "Notification sent")

        } catch (e: SecurityException) {
            Log.e("NotificationError", "SecurityException: ${e.message}")
        } catch (e: Exception){
            Log.e("NotificationError", "General Exception: ${e.message}")
        }
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "calls",
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }


    fun cancelIncomingCallNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(1) // 1 is the notification ID used in showIncomingCallNotification()
    }







}