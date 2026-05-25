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

    // 2026-05-22 — debounce peer-stream recovery. Agora flaps the remote-audio
    // state between FROZEN and DECODING when the peer's network drops (the SDK
    // briefly thinks the stream recovered before realizing the peer is gone),
    // which used to de-arm the watchdog and hide the "Reconnecting…" banner on
    // the un-affected side after ~5s while the peer was actually offline.
    // We now require sustained DECODING/STARTING for PEER_RECOVERY_CONFIRM_MS
    // before truly clearing the peer-down flag. Any FROZEN/FAILED within that
    // window cancels the pending de-arm.
    private var peerRecoveryScheduled = false

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "Watchdog timeout after ${timeoutMillis}ms (own=$ownConnectionDown peer=$peerStreamDown) — ending call")
        ownConnectionDown = false
        peerStreamDown = false
        timerActive = false
        peerRecoveryScheduled = false
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(peerRecoveryRunnable)
        try {
            onTimeout()
        } catch (e: Exception) {
            Log.e(TAG, "onTimeout callback threw", e)
        }
    }

    // Fires PEER_RECOVERY_CONFIRM_MS after the FIRST sustained DECODING/STARTING
    // following a stall. If a fresh FROZEN/FAILED arrives in the meantime,
    // [peerStreamStalled] cancels this runnable and resets the schedule flag.
    private val peerRecoveryRunnable = Runnable {
        peerStreamDown = false
        peerRecoveryScheduled = false
        Log.d(TAG, "Peer recovery confirmed after ${PEER_RECOVERY_CONFIRM_MS}ms of sustained DECODING — clearing peer-down flag")
        cancelIfClear()
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
        // 2026-05-23 v1069 — DISABLED. The 30s auto-end timer was potentially
        // ending calls during initial Agora join when state flapped. Reverting
        // to Play Store v1064 behavior where Agora's own onUserOffline is the
        // only call-end signal. Method kept as no-op for callers.
        return
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
     * 2026-05-22: Recovery (stalled=false) is now debounced. Agora flaps
     * FROZEN ↔ DECODING during a real peer disconnect, which used to clear
     * the peer-down flag after ~5s and hide the banner while the peer was
     * still offline. Now the first DECODING after a stall schedules a
     * recovery confirmation in [PEER_RECOVERY_CONFIRM_MS]; a fresh
     * FROZEN/FAILED in that window cancels the confirmation. So both sides
     * of a real disconnect see the "Reconnecting…" banner for roughly the
     * same duration.
     *
     * @param stalled true if the peer's audio is FROZEN / FAILED for a
     *                non-mute reason; false on DECODING / STARTING (recovery).
     */
    fun peerStreamStalled(stalled: Boolean) {
        if (stalled) {
            // Cancel any pending recovery — peer is still / again stalled.
            if (peerRecoveryScheduled) {
                handler.removeCallbacks(peerRecoveryRunnable)
                peerRecoveryScheduled = false
            }
            peerStreamDown = true
            armIfNeeded()
        } else {
            // Only the FIRST DECODING after a stall starts the confirmation
            // timer. Subsequent DECODINGs don't re-arm or re-schedule (they're
            // expected during a healthy stream). If we weren't stalled to
            // begin with, ignore — nothing to clear.
            if (!peerStreamDown || peerRecoveryScheduled) return
            peerRecoveryScheduled = true
            handler.postDelayed(peerRecoveryRunnable, PEER_RECOVERY_CONFIRM_MS)
        }
    }

    /**
     * Belt-and-braces cleanup for `onDestroy` / `leaveChannel`. Clears both
     * sources and tears down the timer/ticks unconditionally.
     */
    fun cancel() {
        if (!timerActive && !ownConnectionDown && !peerStreamDown && !peerRecoveryScheduled) return
        ownConnectionDown = false
        peerStreamDown = false
        peerRecoveryScheduled = false
        timerActive = false
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(peerRecoveryRunnable)
        Log.d(TAG, "Force-cancelled watchdog (activity teardown)")
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val TICK_INTERVAL_MS = 1_000L
        // 2026-05-22 — minimum sustained DECODING/STARTING window before we
        // accept that the peer's stream has truly recovered. Lower = banner
        // can hide too early during a real disconnect (Agora's noisy FROZEN
        // ↔ DECODING flapping fooled the previous instant-de-arm logic into
        // hiding after ~5s while peer was still offline). Higher = brief
        // healthy-network blips show the banner for longer. 12s balances
        // both: longer than Agora's typical 5-8s flap cycle on a real drop,
        // shorter than the 30s watchdog timeout so a true recovery still
        // de-arms in time to keep the call alive.
        private const val PEER_RECOVERY_CONFIRM_MS = 12_000L
        private const val TAG = "ReconnectWatchdog"
    }
}
