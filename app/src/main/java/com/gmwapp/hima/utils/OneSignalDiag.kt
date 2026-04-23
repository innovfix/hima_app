package com.gmwapp.hima.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.gmwapp.hima.BaseApplication
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.OneSignal
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One-shot diagnostic helper for the OneSignal subscription state.
 *
 * - `dump(checkpoint)` writes a snapshot (external id, subscription id, push token,
 *   optedIn flag, POST_NOTIFICATIONS permission, FCM token) under tag `OneSignalDiag`.
 * - `installObserver(context)` registers a one-time push-subscription observer that
 *   fires every time the SDK's internal state transitions — useful for catching
 *   stray `optOut()` calls and confirming server-side syncs.
 *
 * All output is mirrored to `filesDir/onesignal_diag.log` so the whole history is
 * retrievable via `adb shell run-as <pkg> cat files/onesignal_diag.log`.
 */
object OneSignalDiag {
    private const val TAG = "OneSignalDiag"

    @Volatile
    private var observerInstalled = false

    fun installObserver(context: Context) {
        if (observerInstalled) return
        observerInstalled = true
        try {
            OneSignal.User.pushSubscription.addObserver(object : IPushSubscriptionObserver {
                override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
                    val p = state.previous
                    val c = state.current
                    log(
                        context,
                        "observer",
                        "prev.id=${p.id} prev.token=${p.token?.take(12)} prev.optedIn=${p.optedIn} " +
                            "=> curr.id=${c.id} curr.token=${c.token?.take(12)} curr.optedIn=${c.optedIn}"
                    )
                }
            })
            log(context, "observer", "installed")
        } catch (e: Exception) {
            log(context, "observer_error", e.message ?: "unknown")
        }
    }

    fun dump(context: Context, checkpoint: String) {
        try {
            val extId = OneSignal.User.externalId
            val sub = OneSignal.User.pushSubscription
            val optedIn = sub.optedIn
            val subId = sub.id
            val token = sub.token
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            val appIdField = BaseApplication.getInstance()?.ONESIGNAL_APP_ID
            val storedUser = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            log(
                context,
                checkpoint,
                "appId=$appIdField storedUserId=${storedUser?.id} externalId=$extId " +
                    "subId=$subId optedIn=$optedIn token=${token?.take(16)} postNotifPerm=$perm"
            )

            FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    log(context, "$checkpoint.fcmToken", "fcm=${t.result?.take(16)}")
                } else {
                    log(context, "$checkpoint.fcmToken_error", t.exception?.message ?: "unknown")
                }
            }
        } catch (e: Exception) {
            log(context, "$checkpoint.error", e.message ?: "unknown")
        }
    }

    private fun log(context: Context, checkpoint: String, body: String) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$ts][$checkpoint] $body"
        Log.d(TAG, line)
        try {
            val f = File(context.filesDir, "onesignal_diag.log")
            f.appendText(line + "\n")
        } catch (_: Exception) {
            // best-effort; never block callers on disk write failure
        }
    }
}
