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
    }

    override fun onCreate() {
        notificationService()
        super.onCreate()
    }

    /**
     * The Notification is mandatory for background services
     * */
    private fun notificationService() {
        Notification.Builder(this, callingChannelId).apply {
            setContentTitle(getString(R.string.app_name))
            setOngoing(true)
            setContentText(getString(R.string.running_service_to_call))
            setSmallIcon(R.drawable.logo)
            val importance = NotificationManager.IMPORTANCE_HIGH
            NotificationChannel(callingChannelId, channelName, importance).apply {
                description = getString(R.string.running_service_to_call)
                with((this@CallingService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)) {
                    createNotificationChannel(this@apply)
                }
            }
            ServiceCompat.startForeground(
                this@CallingService, 1, this.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        }
    }

    /**
     * Main process for the service - find the background location and print it with Toast Message
     * */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    /**
     * Mandatory override when extend the Service()
     * */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
