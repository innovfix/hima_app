package com.gmwapp.hima.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.agora.rtc2.Constants

/**
 * Auto-ends a call that's been stuck in a no-audio state for too long.
 *
 * Originally (B062) this watched only our OWN connection — Agora's
 * `CONNECTION_STATE_RECONNECTING` / `FAILED` armed it, `CONNECTED` cancelled
 * it. The user's network drop would otherwise pin the call to "Reconnecting…"
 * forever because Agora's retry loop never gives up.
 *
 * I024 extended that: the PEER's network drop is just as bad — we hear
 * silence, but Agora keeps telling us we're connected for ~20-30 seconds
 * before finally firing onUserOffline. So we now also arm on
 * `onRemoteAudioStateChanged(FROZEN/FAILED)`. The two sources are tracked
 * with separate flags so we don't cancel prematurely if one recovers while
 * the other is still down — the timer only stops when BOTH are healthy.
 *
 * The watchdog is state-driven, not edge-driven, so repeated FROZEN /
 * RECONNECTING callbacks from the SDK don't reset the user-perceived clock —
 * the 30-second countdown keeps ticking from the first stall.
 *
 * Lifecycle:
 *   onConnectionStateChanged   → call [armOrCancel] (own connection)
 *   onRemoteAudioStateChanged  → call [peerStreamStalled] (peer stream)
 *   onDestroy / leaveChannel   → call [cancelAll]
 */
class ReconnectWatchdog(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    private val onTick: (secondsRemaining: Int) -> Unit = {},
    private val onTimeout: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L

    // I024 — track each stall source independently so we only cancel when
    // BOTH are clear. Without this, the peer recovering would silently cancel
    // a still-broken own-connection timer (or vice versa).
    private var ownConnectionDown = false
    private var peerStreamDown = false
    // Separate from the source flags so we don't depend on
    // Handler.hasCallbacks (API 29+) just to know if our timer is running.
    private var timerActive = false

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "Watchdog timeout after ${timeoutMillis}ms (own=$ownConnectionDown peer=$peerStreamDown) — ending call")
        ownConnectionDown = false
        peerStreamDown = false
        timerActive = false
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
            if (!isArmed()) return
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

    /** True while either stall source is still down. */
    fun isArmed(): Boolean = ownConnectionDown || peerStreamDown

    private fun armIfNeeded() {
        // Edge-triggered: only start a fresh countdown when transitioning
        // from "everything fine" to "something down". Subsequent flag flips
        // from the OTHER source piggy-back on the same in-flight timer so
        // the user's perceived wait clock doesn't reset.
        if (timerActive) return
        timerActive = true
        startedAt = System.currentTimeMillis()
        handler.postDelayed(timeoutRunnable, timeoutMillis)
        handler.post(tickRunnable) // fire first tick immediately
        Log.d(TAG, "Armed watchdog (own=$ownConnectionDown peer=$peerStreamDown timeoutMs=$timeoutMillis)")
    }

    private fun cancelIfClear() {
        if (isArmed() || !timerActive) return
        timerActive = false
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(tickRunnable)
        Log.d(TAG, "Cancelled watchdog (own + peer both clear)")
    }

    /**
     * Drive the watchdog from `onConnectionStateChanged` (OUR side). Arms on
     * RECONNECTING / FAILED, clears on CONNECTED / DISCONNECTED. CONNECTING
     * (initial join) is ignored.
     */
    fun armOrCancel(state: Int) {
        when (state) {
            Constants.CONNECTION_STATE_RECONNECTING,
            Constants.CONNECTION_STATE_FAILED -> {
                ownConnectionDown = true
                armIfNeeded()
            }
            Constants.CONNECTION_STATE_CONNECTED,
            Constants.CONNECTION_STATE_DISCONNECTED -> {
                ownConnectionDown = false
                cancelIfClear()
            }
            // CONNECTION_STATE_CONNECTING — initial join, ignore.
        }
    }

    /**
     * I024 — drive the watchdog from `onRemoteAudioStateChanged`. The peer's
     * own network drop manifests as a FROZEN / FAILED audio stream on our
     * side; we get no callback on OUR connection state because our link to
     * Agora is fine. Without this, the user just hears silence for 20-30s
     * until Agora gives up and calls onUserOffline.
     *
     * @param stalled true if the peer's audio is FROZEN / FAILED for a
     *                non-mute reason; false on DECODING / STARTING (recovery).
     */
    fun peerStreamStalled(stalled: Boolean) {
        peerStreamDown = stalled
        if (stalled) armIfNeeded() else cancelIfClear()
    }

    /**
     * Belt-and-braces cleanup for `onDestroy` / `leaveChannel`. Clears both
     * sources and tears down the timer/ticks unconditionally.
     */
    fun cancel() {
        if (!timerActive && !ownConnectionDown && !peerStreamDown) return
        ownConnectionDown = false
        peerStreamDown = false
        timerActive = false
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(tickRunnable)
        Log.d(TAG, "Force-cancelled watchdog (activity teardown)")
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val TICK_INTERVAL_MS = 1_000L
        private const val TAG = "ReconnectWatchdog"
    }
}
