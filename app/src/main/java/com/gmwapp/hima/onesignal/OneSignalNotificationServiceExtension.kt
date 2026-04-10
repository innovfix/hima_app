package com.gmwapp.hima.onesignal

import android.content.Context
import android.util.Log
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

/**
 * OneSignal Notification Service Extension.
 *
 * Invoked by the OneSignal SDK BEFORE a push notification is displayed,
 * even when the app process is killed. Used to record the notification_id
 * and receive timestamp so we can later attribute conversions when the
 * user opens the app within the conversion window.
 *
 * Note: DND filtering is handled server-side (the backend skips users with
 * DND enabled when sending), so no client-side suppression is needed here.
 */
class OneSignalNotificationServiceExtension : INotificationServiceExtension {

    private val TAG = "OneSignalNSE"

    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        // Save notification_id + receive timestamp for conversion tracking.
        // Used later when user opens the app directly (without tapping the notification).
        try {
            val context: Context = event.context
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
    }
}
