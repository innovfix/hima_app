package com.gmwapp.hima.agora.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.gmwapp.hima.R

class CallingService : Service() {
    companion object {
        const val callingChannelId = "callingChannelId"
        private const val channelName = "callingName"
        private const val NOTIFICATION_ID = 1
        @Volatile var isRunning: Boolean = false

    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true                      // ✅ mark running
        notificationService()

    }

    /**
     * The Notification is mandatory for background services
     * */
    private fun notificationService() {
        // Channel creation BEFORE building the notification — leaving it
        // nested inside the Builder.apply{} block worked by luck because
        // notification.build() runs last, but having the channel as an
        // explicit prerequisite is clearer.
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(callingChannelId) == null) {
            val channel = NotificationChannel(
                callingChannelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.running_service_to_call)
                setSound(null, null)        // Call audio is already playing — don't ding on top.
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, callingChannelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.running_service_to_call))
            .setSmallIcon(R.drawable.logo)
            // B046 — the trio that keeps this notification non-dismissable:
            //   1. setOngoing — legacy flag, still respected pre-Android 14.
            //   2. setAutoCancel(false) — never auto-clear if tapped.
            //   3. CATEGORY_CALL — Android 14+ only honours the "can't swipe
            //      away a foreground-service notification" behaviour for
            //      notifications explicitly categorised as a phone call.
            //      Without this the user CAN swipe the session pill away
            //      mid-call (which is exactly the reported bug).
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_CALL)
            .setOnlyAlertOnce(true)
            .build()
        // Explicit FLAG_NO_CLEAR on top of setOngoing — belt + braces for
        // OEM builds (some Xiaomi/Vivo) that ignore one but honour the other.
        notification.flags = notification.flags or
            Notification.FLAG_NO_CLEAR or
            Notification.FLAG_ONGOING_EVENT

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    /**
     * Main process for the service - find the background location and print it with Toast Message
     * */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    /**
     * Mandatory override when extend the Service()
     * */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isRunning = false                     // ✅ clear flag
        super.onDestroy()
    }
}
