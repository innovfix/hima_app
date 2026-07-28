package com.gmwapp.hima.agora.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.gmwapp.hima.R

class CallingService : Service() {
    companion object {
        const val callingChannelId = "callingChannelId"
        private const val channelName = "callingName"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "CallingService"
        @Volatile var isRunning: Boolean = false

        // B033 — the in-call activity that owns this service. Each call
        // activity sets this to its own class name right before starting
        // the service so the notification's contentIntent can target the
        // exact screen the user expects to return to. Cleared on
        // service destroy. Only one call activity is alive at any moment
        // (B125 guards against parallel calls), so the single-slot store
        // is safe.
        @Volatile var callerActivityClassName: String? = null

        // Bug 1 (video-blank-on-background) — the active call activity sets this
        // to true for VIDEO calls (false for audio) right before starting the
        // service, so the foreground service can also declare the `camera` type.
        // On Android 14/15 (targetSdk 35) a call that uses the camera around the
        // background→foreground transition needs FOREGROUND_SERVICE_TYPE_CAMERA;
        // without it the OS can suspend the camera and the peer's view stays
        // blank after the user returns from Recent Apps. Audio calls keep the
        // microphone-only type so they never require the camera permission gate.
        @Volatile var withCamera: Boolean = false
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
            // B033 — tap returns the user to the active call screen. Without
            // a contentIntent the notification did nothing on tap (or fell
            // back to launching MainActivity → Home tab), which surprised
            // users who'd backgrounded the call expecting the notification
            // to be their return path.
            .setContentIntent(buildReturnToCallPendingIntent())
            .build()
        // Explicit FLAG_NO_CLEAR on top of setOngoing — belt + braces for
        // OEM builds (some Xiaomi/Vivo) that ignore one but honour the other.
        notification.flags = notification.flags or
            Notification.FLAG_NO_CLEAR or
            Notification.FLAG_ONGOING_EVENT

        // B-v1110 #3 — startForeground can throw on Android 12+ even with the
        // manifest FGS type + RECORD_AUDIO declared: ForegroundServiceStartNot-
        // AllowedException (started while backgrounded), a microphone-type
        // SecurityException (RECORD_AUDIO not granted at this instant), or
        // ForegroundServiceDidNotStartInTimeException (past the start window).
        // Any of these previously hard-crashed the call. Catch, report, and stop
        // cleanly so the process never crashes; the call path already treats a
        // missing foreground service as a failed start.
        // Bug 1 — add the camera FGS type for video calls, but ONLY when the
        // CAMERA runtime permission is actually held; requesting the camera type
        // without the permission would throw on Android 14+. Audio calls (and
        // video calls that somehow lack camera permission) stay microphone-only.
        val cameraGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val fgsType = if (withCamera && cameraGranted) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed: ${t.javaClass.simpleName}: ${t.message}", t)
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(t)
            // If the camera-typed start was rejected, retry microphone-only rather
            // than crashing the whole call — the call keeps working, only the
            // background-camera hardening is lost.
            if (fgsType != ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) {
                try {
                    ServiceCompat.startForeground(
                        this, NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                    return
                } catch (t2: Throwable) {
                    Log.e(TAG, "mic-only startForeground fallback failed: ${t2.message}", t2)
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(t2)
                }
            }
            runCatching { stopSelf() }
        }
    }

    /**
     * Build the PendingIntent that fires when the user taps the session
     * notification. Targets the call activity recorded in
     * [callerActivityClassName]; falls back to the app launcher if the
     * class can't be resolved (defensive — shouldn't happen in practice
     * since each call activity sets the field before starting the service).
     */
    private fun buildReturnToCallPendingIntent(): PendingIntent? {
        val callerClass = callerActivityClassName
        val target: Intent = if (callerClass != null) {
            runCatching {
                Intent(this, Class.forName(callerClass)).apply {
                    // CLEAR_TOP + SINGLE_TOP: the existing call activity is
                    // brought to front (no new instance created) so the
                    // user resumes the same call rather than starting over.
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            }.getOrElse {
                Log.w(TAG, "Failed to resolve $callerClass for return-to-call intent", it)
                packageManager.getLaunchIntentForPackage(packageName) ?: return null
            }
        } else {
            packageManager.getLaunchIntentForPackage(packageName) ?: return null
        }
        return PendingIntent.getActivity(
            this,
            0,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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

    /**
     * BUG #17 — creator force-closes (swipes Hima from Recents) during a LIVE call. The
     * user's side ends cleanly on its own because Agora fires onUserOffline, but the
     * creator was left with "Your session is in progress" showing indefinitely, and
     * tapping it deep-linked into a dead call screen.
     *
     * A foreground service is deliberately NOT killed when its task is removed — that is
     * the whole point of an FGS, so a backgrounded call survives the app being closed.
     * Something has to stop it explicitly. The only place that happened was the call
     * activities' onDestroy, and onDestroy is not guaranteed to run on task removal, so
     * on a swipe nothing ever took the notification down.
     *
     * FcmCallService already does exactly this for the RINGING case
     * (FORCE_CLOSE_REJECT_2026_07_07); the live-call case never got the equivalent.
     *
     * Deliberately app-side only: this does NOT tell the server the call ended. That
     * would mean a new write to the call/billing row, which needs a backend decision
     * rather than an assumption here.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Clear first — once stopSelf() lands the process can go at any moment.
        runCatching { com.gmwapp.hima.BaseApplication.getInstance()?.clearCallStateOnTaskRemoved() }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { stopSelf() }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isRunning = false                     // ✅ clear flag
        // B033 — release the caller class so a future, unrelated service
        // start (rare but possible) doesn't post a notification pointing
        // at a dead activity.
        callerActivityClassName = null
        // Bug 1 — reset so a later audio call never inherits a stale camera type.
        withCamera = false
        super.onDestroy()
    }
}
