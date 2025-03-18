package com.gmwapp.hima.agora



import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity

class FcmCallService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification("Incoming call..."))

        // Start ringtone
        mediaPlayer = MediaPlayer.create(this, R.raw.rhythm)
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callType = intent?.getStringExtra("CALL_TYPE") ?: "audio"
        val senderId = intent?.getIntExtra("SENDER_ID", -1) ?: -1
        val channelName = intent?.getStringExtra("CHANNEL_NAME") ?: "default_channel"
        val callId = intent?.getIntExtra("CALL_ID", 0) ?: 0

        Log.d("FcmCallService", "Service started with callType: $callType, senderId: $senderId")

        // Launch Call Accept Activity when the app is opened
        val callIntent = Intent(this, FemaleCallAcceptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
        }
        startActivity(callIntent)

        return START_STICKY // Keeps the service running if killed
    }

    override fun onDestroy() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(contentText: String): Notification {
        val channelId = "call_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Call Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Incoming Call")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.love)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .build()
    }
}

