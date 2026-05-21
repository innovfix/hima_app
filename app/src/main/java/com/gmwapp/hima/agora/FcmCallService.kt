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
