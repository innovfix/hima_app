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
        if (startedForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    // FORCE_CLOSE_REJECT_2026_07_07 — the incoming call this service is currently
    // ringing for, so onTaskRemoved (fired when the user swipes Hima from recents)
    // knows which call to reject. Only set while a ring is up; cleared implicitly
    // by the service stopping.
    private var lastPayload: CallNotifications.IncomingPayload? = null
    private var startedForeground = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "stop action received")
            timeoutHandler.removeCallbacks(timeoutRunnable)
            if (startedForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
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

        // RING_BACKSTOP_2026_07_23 — when the app is already foreground and
        // unlocked, the accept Activity is the visible ring UI. Starting an FGS
        // there would add a duplicate system CallStyle banner (B030), so keep a
        // plain, bounded Service only to receive onTaskRemoved if the user swipes
        // Hima away. If a later full start promotes this same instance, the
        // normal foreground path below still runs.
        val backstopOnly = intent?.getBooleanExtra(EXTRA_BACKSTOP_ONLY, false) == true
        if (backstopOnly && !startedForeground) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            timeoutHandler.postDelayed(timeoutRunnable, SELF_TIMEOUT_MS)
            Log.d(TAG, "backstop-only started callId=${payload.callId} isMale=${payload.isMale}")
            return START_NOT_STICKY
        }

        val notification = try {
            CallNotifications.buildIncomingCallNotification(this, payload)
        } catch (e: Exception) {
            Log.e(TAG, "buildIncomingCallNotification threw; using display fallback", e)
            postDisplayFallback(payload)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(CallNotifications.INCOMING_CALL_NOTIFICATION_ID, notification)
            startedForeground = true
            Log.d(TAG, "startForeground done callId=${payload.callId} isMale=${payload.isMale}")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground threw; using display fallback", e)
            postDisplayFallback(payload)
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
                val claimed = app.claimUnacceptedIncomingCallForTaskRemoval(
                    senderId = p.senderId,
                    callId = p.callId
                )
                if (!claimed) {
                    Log.d(
                        TAG,
                        "onTaskRemoved: accepted, newer, or already-cleared ring callId=${p.callId} — NOT rejecting"
                    )
                    // RING_TELECOM_LEAK_2026_07_23 — `claimed` is ALSO false when the
                    // accept activity's own onDestroy force-close-reject already ran and
                    // consumed the claim (it wins this race on Realme/OPPO by ~18ms). In
                    // that case the reject IS posted correctly, but the Telecom connection
                    // and the stale accept-activity ref were only ever torn down in the
                    // `else` branch below — so a self-managed connection stayed registered.
                    // The next incoming FCM then evaluated maleOnAnotherAppCall==true and
                    // auto-replied "userBusy" before ringing, which killed the caller's
                    // NEXT call to this user ~1s in with "This call has already ended".
                    // Safe here: skipped entirely when the ring was ACCEPTED (a live call),
                    // and endIncomingCallIfMatches is id-matched, so a newer call's
                    // connection is left untouched. Both calls are idempotent no-ops when
                    // there is nothing left to clean up.
                    if (!app.wasRingAcceptedFor(p.senderId)) {
                        app.finishAcceptActivityIfMatches(p.senderId, p.callId)
                        val ended = com.gmwapp.hima.agora.telecom.HimaTelecomManager
                            .endIncomingCallIfMatches(
                                senderId = p.senderId,
                                callId = p.callId,
                                reason = android.telecom.DisconnectCause.REJECTED
                            )
                        Log.d(
                            TAG,
                            "onTaskRemoved: post-claim telecom sweep callId=${p.callId} endedConnection=$ended"
                        )
                    }
                } else {
                    // Do not leave a stale Activity object as BaseApplication.currentActivity.
                    // Realme/OPPO task removal can omit/delay onDestroy; the next FCM would
                    // otherwise see FemaleCallAcceptActivity and immediately reply userBusy.
                    app.stopRingtone()
                    app.cancelAllIncomingCallNotifications()
                    app.finishAcceptActivityIfMatches(p.senderId, p.callId)
                    val selfId = app.getPrefs()?.getUserData()?.id
                    Log.d(TAG, "onTaskRemoved: force-close during ring callId=${p.callId} — rejecting")
                    app.rejectCallOnAppClose(selfId, p.senderId, p.callId, p.callType, p.channelName)
                    com.gmwapp.hima.agora.telecom.HimaTelecomManager.endIncomingCallIfMatches(
                        senderId = p.senderId,
                        callId = p.callId,
                        reason = android.telecom.DisconnectCause.REJECTED
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onTaskRemoved reject failed (best-effort): ${t.message}")
        }
        timeoutHandler.removeCallbacks(timeoutRunnable)
        if (startedForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * A startForegroundService dispatch returns before this service builds and
     * posts its notification. If that asynchronous step fails after OneSignal
     * has suppressed its default card, post the established tagged CallStyle
     * fallback so the receiver is not left with an invisible ring. Ownership is
     * deliberately bypassed here because this is the owning FCM/OneSignal
     * service recovering from its own failed foreground promotion.
     */
    private fun postDisplayFallback(payload: CallNotifications.IncomingPayload) {
        runCatching {
            val posted = CallNotifications.showIncoming(
                this,
                payload,
                bypassFcmOwnership = true
            )
            if (!posted) {
                Log.w(TAG, "display fallback did not post")
            }
        }.onFailure {
            Log.e(TAG, "display fallback threw", it)
        }
    }

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
        private const val EXTRA_BACKSTOP_ONLY = "backstop_only"

        private fun serviceIntent(
            context: Context,
            payload: CallNotifications.IncomingPayload,
            backstopOnly: Boolean
        ) = Intent(context, FcmCallService::class.java).apply {
            putExtra(EXTRA_IS_MALE, payload.isMale)
            putExtra(EXTRA_CALL_TYPE, payload.callType)
            putExtra(EXTRA_SENDER_ID, payload.senderId)
            putExtra(EXTRA_CALL_ID, payload.callId)
            putExtra(EXTRA_CHANNEL_NAME, payload.channelName)
            putExtra(EXTRA_CALLER_NAME, payload.callerName)
            putExtra(EXTRA_CALLER_IMAGE, payload.callerImage)
            putExtra(EXTRA_BACKSTOP_ONLY, backstopOnly)
        }

        /**
         * Starts the background/locked incoming-call FGS. Returns whether Android
         * accepted the service dispatch so fallback providers can retain their
         * own notification if dispatch is rejected synchronously.
         */
        fun start(context: Context, payload: CallNotifications.IncomingPayload): Boolean {
            val intent = serviceIntent(context, payload, backstopOnly = false)
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.onFailure { Log.e(TAG, "startForegroundService threw", it) }
                .getOrDefault(false)
        }

        /**
         * Foreground+unlocked swipe-away watcher. It deliberately does not call
         * startForeground, so it cannot recreate the duplicate CallStyle popup
         * that B030 removed. The caller must only use this while the app is
         * visibly foreground, where a normal started service is permitted.
         */
        fun startBackstopOnly(
            context: Context,
            payload: CallNotifications.IncomingPayload
        ): Boolean {
            val intent = serviceIntent(context, payload, backstopOnly = true)
            return runCatching {
                context.startService(intent)
                true
            }.onFailure { Log.e(TAG, "startService(backstop-only) threw", it) }
                .getOrDefault(false)
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
