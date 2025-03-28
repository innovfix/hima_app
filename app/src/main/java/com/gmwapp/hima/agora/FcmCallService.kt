package com.gmwapp.hima.agora

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity

class FcmCallService : Service() {

    private var callType: String? = null
    private var senderId: Int = -1
    private var channelName: String? = null
    private var callId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        callType = intent?.getStringExtra("CALL_TYPE")
        senderId = intent?.getIntExtra("SENDER_ID", -1) ?: -1
        channelName = intent?.getStringExtra("CHANNEL_NAME")
        callId = intent?.getStringExtra("CALL_ID")

        showIncomingCallNotification()

        return START_NOT_STICKY
    }

    private fun showIncomingCallNotification() {
        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "ACTION_ACCEPT_CALL"
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
        }
        val rejectIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "ACTION_REJECT_CALL"
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
        }

        val acceptPendingIntent = PendingIntent.getBroadcast(
            this, 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val rejectPendingIntent = PendingIntent.getBroadcast(
            this, 1, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "CALL_CHANNEL_ID")
            .setContentTitle("Incoming Call")
            .setContentText("From: $senderId")
            .setSmallIcon(R.drawable.notification_icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(R.drawable.accept_audio, "Accept", acceptPendingIntent)
            .addAction(R.drawable.decline, "Reject", rejectPendingIntent)
            .setFullScreenIntent(acceptPendingIntent, true)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "CALL_CHANNEL_ID", "Incoming Calls", NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
