package com.gmwapp.hima.agora

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.gmwapp.hima.BaseApplication
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
        Log.d("FCM_Message", "Message data payload: ${remoteMessage.data}")


        if (remoteMessage.data.isNotEmpty()) {
            val message = remoteMessage.data["message"] ?: ""
            val callType = remoteMessage.data["callType"]
            val senderId = remoteMessage.data["senderId"]?.toIntOrNull() ?: -1
            val channelName = remoteMessage.data["channelName"] ?: "default_channel"


            val currentActivity = BaseApplication.getInstance()?.getCurrentActivity()

            if (message.startsWith("incoming call")) {
                val parts = message.split(" ")
                if (parts.size == 3) {
                    val callId = parts[2]  // Extract callId from the message
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

                        callType?.let {
                            BaseApplication.getInstance()?.setIncomingCall(senderId,
                                it, channelName, callId.toIntOrNull() ?: 0)
                        }



                        val intent = Intent(this, FemaleCallAcceptActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("CALL_TYPE", callType)
                            putExtra("SENDER_ID", senderId)
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("CALL_ID", callId.toIntOrNull() ?: 0)
                        }

                        Log.d("startingActvity","startingActivity")
                        startActivity(intent)
                    }


//                    val serviceIntent = Intent(this, FcmCallService::class.java).apply {
//                        putExtra("CALL_TYPE", callType)
//                        putExtra("SENDER_ID", senderId)
//                        putExtra("CHANNEL_NAME", channelName)
//                        putExtra("CALL_ID", callId)  // Include CALL_ID
//                    }
//                    startForegroundService(serviceIntent) // Start the service
//                }


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


                var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                if (senderId==previousSenderId){
                    BaseApplication.getInstance()?.clearIncomingCall()
                    BaseApplication.getInstance()?.stopRingtone()
                    val mainIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(mainIntent)
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

            if (message == "VideoAccepted" && gender == "male") {

                Log.d("switchToVideo","$message")
                FcmUtils.UpdateCallSwitch(message, senderId)



            }





        }



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
}