package com.gmwapp.hima.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.agora.rtc2.Constants

/**
 * Auto-ends a call that's been stuck in Agora's `CONNECTION_STATE_RECONNECTING`
 * or `CONNECTION_STATE_FAILED` for too long. Without this, killing the network
 * mid-call left the four calling activities pinned to the "Reconnecting…"
 * banner indefinitely because Agora's own retry loop never gives up (B062).
 *
 * Lifecycle:
 *   onConnectionStateChanged → call [armOrCancel]
 *   onDestroy / leaveChannel → call [cancel]
 *
 * The watchdog is state-driven, not edge-driven, so repeated RECONNECTING
 * callbacks don't reset the timer — the user's "I've been stuck for 30s"
 * clock keeps ticking until the SDK actually recovers (CONNECTED) or finally
 * drops (DISCONNECTED).
 */
class ReconnectWatchdog(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    private val onTick: (secondsRemaining: Int) -> Unit = {},
    private val onTimeout: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var armed = false
    private var startedAt = 0L

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "Reconnect timeout reached after ${timeoutMillis}ms — ending call")
        armed = false
        handler.removeCallbacks(tickRunnable)
        try {
            onTimeout()
        } catch (e: Exception) {
            Log.e(TAG, "onTimeout callback threw", e)
        }
    }

    // B064 — emit a per-second countdown so the activity can update the
    // reconnect banner ("Reconnecting… 23s left") instead of leaving the
    // user staring at an indefinite "Reconnecting…" pill.
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!armed) return
            val elapsed = System.currentTimeMillis() - startedAt
            val remaining = ((timeoutMillis - elapsed) / 1000L).toInt().coerceAtLeast(0)
            try {
                onTick(remaining)
            } catch (e: Exception) {
                Log.e(TAG, "onTick callback threw", e)
            }
            if (remaining > 0) {
                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
    }

    /**
     * Drive the watchdog from `onConnectionStateChanged`. Arms on the first
     * RECONNECTING or FAILED state; ignores subsequent RECONNECTING calls
     * (the original timer keeps running so the user-perceived wait clock
     * isn't reset by Agora's internal retry chatter). Cancels on CONNECTED
     * (network came back) or DISCONNECTED (call already ending).
     */
    fun armOrCancel(state: Int) {
        when (state) {
            Constants.CONNECTION_STATE_RECONNECTING,
            Constants.CONNECTION_STATE_FAILED -> {
                if (!armed) {
                    armed = true
                    startedAt = System.currentTimeMillis()
                    handler.postDelayed(timeoutRunnable, timeoutMillis)
                    handler.post(tickRunnable) // fire first tick immediately
                    Log.d(TAG, "Armed reconnect watchdog (state=$state, timeoutMs=$timeoutMillis)")
                }
            }
            Constants.CONNECTION_STATE_CONNECTED,
            Constants.CONNECTION_STATE_DISCONNECTED -> cancel()
            // CONNECTION_STATE_CONNECTING — ignore. We're not yet reconnecting;
            // this is the initial join phase.
        }
    }

    fun cancel() {
        if (!armed) return
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(tickRunnable)
        armed = false
        Log.d(TAG, "Cancelled reconnect watchdog")
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val TICK_INTERVAL_MS = 1_000L
        private const val TAG = "ReconnectWatchdog"
    }
}
