package com.gmwapp.hima.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes

/**
 * Tester report: a creator with a broken camera crashed the app when
 * accepting a video call. We now detect the bad camera up-front
 * ([CameraAvailability]) and connect the channel audio-only. THIS helper
 * runs alongside that — it owns the 30-second grace banner so both sides
 * (the creator AND the caller) understand why no video is showing,
 * then triggers a clean disconnect.
 *
 * Lifecycle on the creator side:
 *   - Camera probe fails inside joinChannel/enableVideoCall.
 *   - Activity instantiates this with a banner View + an onTimeout that
 *     calls leaveChannel().
 *   - [start] reveals the banner, kicks off the per-second countdown, and
 *     schedules the disconnect 30 s out.
 *   - If the call ends earlier (peer hangs up, network drop), the activity
 *     must call [cancel] so the disconnect doesn't fire twice.
 *
 * Lifecycle on the caller side (mirror):
 *   - FCM "cameraUnavailable" message lands → MaleVideoCallingActivity
 *     observes via FcmUtils, builds an instance with onTimeout = no-op
 *     (the creator will leave the channel and we'll find out via
 *     onUserOffline). Only the banner countdown ticks here.
 *
 * Single Handler on the main looper — the countdown isn't safety-critical
 * (it's purely informational; the disconnect happens on the creator side
 * regardless of whether the caller's banner is in sync).
 */
class CameraUnavailableNotice(
    private val context: Context,
    private val banner: TextView,
    @StringRes private val bannerCopyRes: Int,
    private val totalSeconds: Int = DEFAULT_GRACE_SECONDS,
    private val onTimeout: (() -> Unit)? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private var secondsRemaining: Int = totalSeconds
    private var running: Boolean = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            banner.text = context.getString(bannerCopyRes, secondsRemaining)
            if (secondsRemaining <= 0) {
                running = false
                try {
                    onTimeout?.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "onTimeout callback threw", e)
                }
                return
            }
            secondsRemaining -= 1
            handler.postDelayed(this, 1000L)
        }
    }

    fun start() {
        if (running) return
        running = true
        secondsRemaining = totalSeconds
        banner.visibility = View.VISIBLE
        banner.bringToFront()
        handler.post(tickRunnable)
    }

    fun cancel() {
        if (!running && banner.visibility == View.GONE) return
        running = false
        handler.removeCallbacks(tickRunnable)
        banner.visibility = View.GONE
    }

    companion object {
        const val DEFAULT_GRACE_SECONDS = 30
        private const val TAG = "CameraUnavailableNotice"
    }
}
