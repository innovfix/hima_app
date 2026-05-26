package com.gmwapp.hima.utils

import android.content.Intent
import android.util.Log
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.NotificationClickResponse
import retrofit2.Call
import retrofit2.Response

/**
 * Fire-and-forget click reporter for /api/auth/notification_clicked.
 * Called from MainActivity.onCreate / onNewIntent — inspects the launch
 * intent for the data keys FCM populates from the push payload's
 * `data` block (which is whatever the server send-side puts there).
 *
 * Marketing wanted "how many people clicked the notification and came
 * to the app" — this is the receive half. The backend matches one of
 * two ways:
 *   • notification_log_id (Long) — exact row in notification_logs.
 *     Used once the server-side embeds the id in every push.
 *   • notification_type (String) — fuzzy match against the most recent
 *     unopened notification of that type for this user in the last
 *     hour. Works from day one without server-side changes.
 *
 * Silent on every failure. Never throws to the caller. Open-rate
 * analytics must never break the user's launch path.
 */
object NotificationClickTracker {

    private const val TAG = "NotifClickTracker"
    // Both keys are read from intent extras. FCM auto-populates the
    // extras with whatever was in the push's `data` block.
    private const val KEY_LOG_ID = "notification_log_id"
    private const val KEY_TYPE = "notification_type"
    // Avoid double-reporting on configuration changes / re-renders
    // (onCreate fires more than once across rotation). Marker added to
    // the intent after a successful report.
    private const val EXTRA_REPORTED = "_notification_click_reported"

    fun maybeTrack(intent: Intent?, apiManager: ApiManager) {
        if (intent == null) return
        val extras = intent.extras ?: return
        if (extras.getBoolean(EXTRA_REPORTED, false)) return

        val logIdStr = extras.getString(KEY_LOG_ID) ?: extras.get(KEY_LOG_ID)?.toString()
        val logId = logIdStr?.toLongOrNull() ?: 0L
        val type = (extras.getString(KEY_TYPE) ?: extras.get(KEY_TYPE)?.toString() ?: "").trim()

        // If neither hint is present, the launch wasn't from a tracked
        // notification (probably launcher icon or in-app navigation).
        if (logId <= 0L && type.isEmpty()) return

        // Mark the intent so we don't re-report on rotation / re-creation.
        intent.putExtra(EXTRA_REPORTED, true)

        apiManager.notificationClicked(logId, type, object : NetworkCallback<NotificationClickResponse> {
            override fun onResponse(
                call: Call<NotificationClickResponse>,
                response: Response<NotificationClickResponse>
            ) {
                val body = response.body()
                Log.d(TAG, "reported: success=${body?.success} match=${body?.match} reason=${body?.reason}")
            }
            override fun onFailure(call: Call<NotificationClickResponse>, t: Throwable) {
                // Silent — analytics failure is not user-visible.
            }
            override fun onNoNetwork() { /* same */ }
        })
    }
}
