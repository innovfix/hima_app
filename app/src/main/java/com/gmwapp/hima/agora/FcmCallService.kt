package com.gmwapp.hima.agora

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.utils.CallNotifications

/**
 * Foreground service that keeps the app process warm and the call-style
 * notification visible from FCM-receive until accept/reject — closes B022
 * (long cold-start delay when notification accept is tapped while the app
 * is killed). Reuses [CallNotifications.buildIncomingCallNotification] so
 * the UI is identical to the legacy notify path.
 *
 * Lifecycle:
 *   - Started from [MyFirebaseMessagingService] after [HimaTelecomManager.tryAddIncomingCall]
 *     succeeds. The active Telecom self-managed call satisfies the API 34+
 *     `phoneCall` FGS-type pre-condition.
 *   - Stopped from [CallActionReceiver] on reject, from the accept activity's
 *     `maybeAutoAccept` once the activity is keeping the process alive, and
 *     from the FCM call-ended paths in [MyFirebaseMessagingService].
 *   - Self-times out at 35s (matches the notification's `setTimeoutAfter`)
 *     so a dropped FCM-end message can't leave the service running forever.
 */
class FcmCallService : Service() {

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        Log.d(TAG, "self-timeout reached; stopping")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // FORCE_CLOSE_REJECT_2026_07_07 — the incoming call this service is currently
    // ringing for, so onTaskRemoved (fired when the user swipes Hima from recents)
    // knows which call to reject. Only set while a ring is up; cleared implicitly
    // by the service stopping.
    private var lastPayload: CallNotifications.IncomingPayload? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "stop action received")
            timeoutHandler.removeCallbacks(timeoutRunnable)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val payload = CallNotifications.IncomingPayload(
            isMale = intent?.getBooleanExtra(EXTRA_IS_MALE, false) ?: false,
            callType = intent?.getStringExtra(EXTRA_CALL_TYPE),
            senderId = intent?.getIntExtra(EXTRA_SENDER_ID, 0) ?: 0,
            callId = intent?.getIntExtra(EXTRA_CALL_ID, 0) ?: 0,
            channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME) ?: "",
            callerName = intent?.getStringExtra(EXTRA_CALLER_NAME)?.takeIf { it.isNotBlank() } ?: "Caller",
            callerImage = intent?.getStringExtra(EXTRA_CALLER_IMAGE),
        )

        lastPayload = payload

        val notification = try {
            CallNotifications.buildIncomingCallNotification(this, payload)
        } catch (e: Exception) {
            Log.e(TAG, "buildIncomingCallNotification threw; falling back to stopSelf", e)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(CallNotifications.INCOMING_CALL_NOTIFICATION_ID, notification)
            Log.d(TAG, "startForeground done callId=${payload.callId} isMale=${payload.isMale}")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground threw", e)
            stopSelf()
            return START_NOT_STICKY
        }

        val avatarUrl = payload.callerImage.orEmpty()
        if (avatarUrl.isNotBlank()) {
            Glide.with(applicationContext)
                .asBitmap()
                .load(avatarUrl)
                .apply(RequestOptions.circleCropTransform())
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        runCatching {
                            startForeground(
                                CallNotifications.INCOMING_CALL_NOTIFICATION_ID,
                                CallNotifications.buildIncomingCallNotification(this@FcmCallService, payload, resource)
                            )
                        }.onFailure { Log.w(TAG, "avatar refresh startForeground failed: ${it.message}") }
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }

        timeoutHandler.removeCallbacks(timeoutRunnable)
        timeoutHandler.postDelayed(timeoutRunnable, SELF_TIMEOUT_MS)
        return START_NOT_STICKY
    }

    /**
     * FORCE_CLOSE_REJECT_2026_07_07 — the user swiped Hima away from recents while
     * this incoming-call ring was still up. A foreground-service notification
     * SURVIVES task removal, so without this the ring banner lingers and gets
     * re-shown when she reopens (the duplicate ghost-ring bug). Two things:
     *   1. If the ring was NOT accepted / no call is active, treat the close as a
     *      Decline — stamp the server row rejected (clears pending so it can't be
     *      resurrected) and tell the caller to stop ringing.
     *   2. Always tear the banner + service down.
     *
     * The accept/active guard is essential: no manual-accept path stops this
     * service, so it can still be alive up to its 35s self-timeout DURING a live
     * call — swiping away then must NOT reject the call she actually took. Both
     * guard flags are read on the main thread while the process is still alive, so
     * they are accurate here.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val app = BaseApplication.getInstance()
            val p = lastPayload
            if (app != null && p != null && p.callId > 0) {
                // "Accepted" MUST be judged by wasRingAcceptedFor() ALONE — NOT
                // isInActiveCall(). The latter counts activeCallActivityCount, which
                // increments for the Accept/ring screen itself (it's in
                // CALL_ACTIVITY_NAMES), so it is already true during the ring and
                // would make this guard skip the reject in the exact "force-close
                // while ringing" case we are fixing. markRingAccepted() is set the
                // instant either accept path (full-screen button OR the notification
                // Answer action in CallActionReceiver) is taken, keyed on this
                // caller, so it is the correct — and un-polluted — "was accepted"
                // signal.
                val accepted = app.wasRingAcceptedFor(p.senderId)
                if (accepted) {
                    Log.d(TAG, "onTaskRemoved: ring accepted/active callId=${p.callId} — NOT rejecting")
                } else {
                    val selfId = app.getPrefs()?.getUserData()?.id
                    Log.d(TAG, "onTaskRemoved: force-close during ring callId=${p.callId} — rejecting")
                    app.rejectCallOnAppClose(selfId, p.senderId, p.callId, p.callType, p.channelName)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onTaskRemoved reject failed (best-effort): ${t.message}")
        }
        timeoutHandler.removeCallbacks(timeoutRunnable)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FcmCallService"
        private const val ACTION_STOP = "com.gmwapp.hima.FcmCallService.STOP"
        // Matches CallNotifications.setTimeoutAfter — once the heads-up
        // auto-dismisses we must not keep the FGS alive any longer.
        private const val SELF_TIMEOUT_MS = 35_000L

        private const val EXTRA_IS_MALE = "is_male"
        private const val EXTRA_CALL_TYPE = "CALL_TYPE"
        private const val EXTRA_SENDER_ID = "SENDER_ID"
        private const val EXTRA_CALL_ID = "CALL_ID"
        private const val EXTRA_CHANNEL_NAME = "CHANNEL_NAME"
        private const val EXTRA_CALLER_NAME = "Caller_NAME"
        private const val EXTRA_CALLER_IMAGE = "Caller_Image"

        fun start(context: Context, payload: CallNotifications.IncomingPayload) {
            val intent = Intent(context, FcmCallService::class.java).apply {
                putExtra(EXTRA_IS_MALE, payload.isMale)
                putExtra(EXTRA_CALL_TYPE, payload.callType)
                putExtra(EXTRA_SENDER_ID, payload.senderId)
                putExtra(EXTRA_CALL_ID, payload.callId)
                putExtra(EXTRA_CHANNEL_NAME, payload.channelName)
                putExtra(EXTRA_CALLER_NAME, payload.callerName)
                putExtra(EXTRA_CALLER_IMAGE, payload.callerImage)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.e(TAG, "startForegroundService threw", it) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FcmCallService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "stop intent failed: ${it.message}") }
        }
    }
}
