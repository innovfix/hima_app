package com.gmwapp.hima.onesignal

import android.content.Context
import android.util.Log
import com.gmwapp.hima.utils.DPreferences
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

/**
 * OneSignal Notification Service Extension.
 *
 * This is invoked by the OneSignal SDK BEFORE a push notification is displayed,
 * even when the app process is killed. We use it to suppress notifications
 * when the user has Do Not Disturb (DND) enabled and the dnd_until timestamp
 * has not yet passed.
 */
class OneSignalNotificationServiceExtension : INotificationServiceExtension {

    private val TAG = "OneSignalNSE_DND"

    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        try {
            val context: Context = event.context
            val prefs = DPreferences(context)
            val userData = prefs.getUserData()

            if (isDndActive(userData)) {
                Log.d(TAG, "DND is active — suppressing OneSignal notification")
                // Returning null prevents the notification from being shown
                event.preventDefault()
                return
            }

            // Save notification_id + receive timestamp for conversion tracking.
            // Used later when user opens the app directly (without tapping the notification).
            try {
                val notifId = event.notification.additionalData?.optInt("notification_id", 0) ?: 0
                if (notifId > 0) {
                    context.getSharedPreferences("notif_track", Context.MODE_PRIVATE).edit()
                        .putInt("last_notif_id", notifId)
                        .putLong("last_notif_time", System.currentTimeMillis())
                        .putBoolean("last_notif_counted", false)
                        .apply()
                    Log.d(TAG, "Saved last_notif_id=$notifId for conversion tracking")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save last_notif_id: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "DND check failed: ${e.message}")
            // On error, fall through and let the notification show normally
        }
    }

    private fun isDndActive(userData: com.gmwapp.hima.retrofit.responses.UserData?): Boolean {
        if (userData == null) return false
        if ((userData.dnd_enabled ?: 0) != 1) return false
        val until = userData.dnd_until ?: return false
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val expiry = sdf.parse(until) ?: return false
            expiry.time > System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse dnd_until=$until: ${e.message}")
            false
        }
    }
}
