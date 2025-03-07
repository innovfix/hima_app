package com.gmwapp.hima

import android.content.Intent
import android.util.Log
import com.gmwapp.hima.agora.video.FemaleCallAcceptActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        // Send this token to your backend server if needed.
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "From: ${remoteMessage.from}")

        Log.d("FCM_Message", "Message data payload: ${remoteMessage.data}")

        remoteMessage.notification?.let { notification ->
            Log.d("FCM_Message", "Notification Title: ${notification.title}")
            Log.d("FCM_Message", "Notification Content: ${notification.body}")
            BaseApplication.getInstance()?.playIncomingCallSound()
        }
        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM_Message", "Message data payload: ${remoteMessage.data}")
            val callType = remoteMessage.data["callType"]
            if (callType == "incoming_call") {
                val callerId = remoteMessage.data["callerId"] ?: "Unknown"
                // Launch the incoming call screen immediately
                val intent = Intent(this, FemaleCallAcceptActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.putExtra("callerId", callerId)
                startActivity(intent)
            }
        }
    }
}
