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
    // NET-004 — the peer can fail in TWO ways, detected at very different points
    // in time, so each gets its own window:
    //   • peerStallTimeoutMillis  — peer audio FROZEN but still in the channel
    //     (onRemoteAudioStateChanged). Detected EARLY, the instant audio goes
    //     quiet; the peer is still connected and may genuinely recover, so we
    //     can afford the longer wait.
    //   • peerGoneTimeoutMillis   — peer HARD OFFLINE (onUserOffline). Detected
    //     LATE: Agora itself already waited ~20s before declaring them gone, so
    //     piling on a long grace = a ~50s dead screen. Wait less here.
    // Own-network reconnect keeps the full [timeoutMillis] grace. When several
    // sources are down at once the LONGEST of their windows wins (only ever
    // lengthen a live timer, never shorten it).
    private val peerStallTimeoutMillis: Long = PEER_STALL_TIMEOUT_MS,
    private val peerGoneTimeoutMillis: Long = PEER_GONE_TIMEOUT_MS,
    private val onTick: (secondsRemaining: Int) -> Unit = {},
    // I038 — single source of truth for banner visibility. Fires only on
    // armed→unarmed transitions so the activity can flip the pill view in
    // one place instead of duplicating `isArmed()` checks across every
    // Agora callback (and racing with CallQualityUi's banner toggling).
    private val onArmedChanged: (Boolean) -> Unit = {},
    private val onTimeout: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    // The timeout the in-flight timer was armed with (own-network = long,
    // peer-only = short). Drives the countdown + the short→long upgrade.
    private var activeTimeoutMs = timeoutMillis

    // I024 — track each stall source independently so we only cancel when
    // BOTH are clear. Without this, the peer recovering would silently cancel
    // a still-broken own-connection timer (or vice versa).
    private var ownConnectionDown = false
    // Peer audio FROZEN but still in the channel (recoverable, caught early).
    private var peerStreamDown = false
    // Peer HARD OFFLINE — Agora removed them from the channel (caught late).
    private var peerOfflineDown = false
    // Separate from the source flags so we don't depend on
    // Handler.hasCallbacks (API 29+) just to know if our timer is running.
    private var timerActive = false
    // Tracks the last value passed to onArmedChanged so duplicate calls
    // (e.g. peer-already-down + own-just-went-down) don't re-notify.
    private var lastNotifiedArmed = false

    // I038 — defer treating a peer-stream FROZEN as a real stall until it's
    // persisted for PEER_STALL_DEBOUNCE_MS. Agora reports FROZEN on tiny
    // jitter bursts (sub-second) which used to flash the "Reconnecting…"
    // pill on otherwise-healthy calls; that ruins perceived quality. If
    // DECODING arrives first, this runnable never fires and the user never
    // sees the banner.
    private val peerStallDebounceRunnable = Runnable {
        peerStreamDown = true
        armIfNeeded()
    }

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "Watchdog timeout after ${activeTimeoutMs}ms (own=$ownConnectionDown frozen=$peerStreamDown offline=$peerOfflineDown) — ending call")
        ownConnectionDown = false
        peerStreamDown = false
        peerOfflineDown = false
        timerActive = false
        activeTimeoutMs = timeoutMillis
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(peerStallDebounceRunnable)
        notifyArmedIfChanged()
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
            val remaining = ((activeTimeoutMs - elapsed) / 1000L).toInt().coerceAtLeast(0)
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

    /** True while any stall source is still down. */
    fun isArmed(): Boolean = ownConnectionDown || peerStreamDown || peerOfflineDown

    // The grace window for the CURRENT set of active sources: the LONGEST window
    // among them, so an own-network drop (or a recoverable frozen stall) is never
    // cut short by a co-occurring hard-offline. 0 when nothing is down.
    private fun currentWindow(): Long {
        var w = 0L
        if (ownConnectionDown) w = maxOf(w, timeoutMillis)
        if (peerStreamDown) w = maxOf(w, peerStallTimeoutMillis)
        if (peerOfflineDown) w = maxOf(w, peerGoneTimeoutMillis)
        return w
    }

    private fun armIfNeeded() {
        // Each failure mode has its own window (own=longest, frozen=medium,
        // offline=shortest); when several overlap the longest wins.
        val window = currentWindow()
        // Edge-triggered: only start a fresh countdown on "everything fine" →
        // "something down". Subsequent flips piggy-back on the in-flight timer so
        // the perceived wait clock doesn't reset — EXCEPT when our own connection
        // drops onto a running short (peer-only) timer: upgrade to the longer
        // own-network grace (only ever lengthen, never shorten, a live timer).
        if (timerActive) {
            if (window > activeTimeoutMs) {
                handler.removeCallbacks(timeoutRunnable)
                startedAt = System.currentTimeMillis()
                activeTimeoutMs = window
                handler.postDelayed(timeoutRunnable, window)
                handler.post(tickRunnable)
            }
            notifyArmedIfChanged()
            return
        }
        timerActive = true
        startedAt = System.currentTimeMillis()
        activeTimeoutMs = window
        handler.postDelayed(timeoutRunnable, window)
        handler.post(tickRunnable) // fire first tick immediately
        notifyArmedIfChanged()
        Log.d(TAG, "Armed watchdog (own=$ownConnectionDown frozen=$peerStreamDown offline=$peerOfflineDown timeoutMs=$window)")
    }

    private fun cancelIfClear() {
        if (isArmed() || !timerActive) {
            notifyArmedIfChanged()
            return
        }
        timerActive = false
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(tickRunnable)
        notifyArmedIfChanged()
        Log.d(TAG, "Cancelled watchdog (own + peer both clear)")
    }

    private fun notifyArmedIfChanged() {
        val nowArmed = isArmed()
        if (nowArmed != lastNotifiedArmed) {
            lastNotifiedArmed = nowArmed
            try {
                onArmedChanged(nowArmed)
            } catch (e: Exception) {
                Log.e(TAG, "onArmedChanged callback threw", e)
            }
        }
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
        if (stalled) {
            // Already counted as down — nothing to schedule.
            if (peerStreamDown) return
            // I038 — debounce. Defer arming until the peer's audio has been
            // FROZEN for PEER_STALL_DEBOUNCE_MS continuously. Sub-debounce
            // jitter never paints the pill. Own-connection RECONNECTING is
            // unaffected (Agora already only emits it after sustained trouble).
            handler.removeCallbacks(peerStallDebounceRunnable)
            handler.postDelayed(peerStallDebounceRunnable, PEER_STALL_DEBOUNCE_MS)
        } else {
            handler.removeCallbacks(peerStallDebounceRunnable)
            peerStreamDown = false
            // Decoding audio frames means the peer is definitively back on the
            // wire — clear a co-existing hard-offline grace too (belt-and-braces
            // alongside the onUserJoined→peerOffline(false) clear).
            peerOfflineDown = false
            cancelIfClear()
        }
    }

    /**
     * NET-004 — drive the watchdog from `onUserOffline` (peer HARD OFFLINE).
     * Distinct from [peerStreamStalled]: Agora has already removed the peer from
     * the channel after its own ~20s timeout, so we (a) arm IMMEDIATELY (no
     * jitter debounce — this is not a sub-second blip) and (b) on the SHORTER
     * [peerGoneTimeoutMillis] window, because the peer has effectively been gone
     * for ~20s already. Cleared by [peerStreamStalled]`(false)` (audio resumed)
     * or an explicit `peerOffline(false)` on `onUserJoined` (peer rejoined).
     *
     * @param gone true when onUserOffline fires for a network timeout (reason=1);
     *             false when the peer rejoins (onUserJoined) and recovery clears it.
     */
    fun peerOffline(gone: Boolean) {
        if (gone) {
            if (peerOfflineDown) return
            peerOfflineDown = true
            armIfNeeded()
        } else {
            if (!peerOfflineDown) return
            peerOfflineDown = false
            cancelIfClear()
        }
    }

    /**
     * Belt-and-braces cleanup for `onDestroy` / `leaveChannel`. Clears all
     * sources and tears down the timer/ticks unconditionally.
     */
    fun cancel() {
        handler.removeCallbacks(peerStallDebounceRunnable)
        if (!timerActive && !ownConnectionDown && !peerStreamDown && !peerOfflineDown) return
        ownConnectionDown = false
        peerStreamDown = false
        peerOfflineDown = false
        timerActive = false
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(tickRunnable)
        notifyArmedIfChanged()
        Log.d(TAG, "Force-cancelled watchdog (activity teardown)")
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        // NET-004 — peer AUDIO-FROZEN window (peer still in the channel, caught
        // early). A touch shorter than the own-network grace, but still generous:
        // we catch the stall the instant audio goes quiet and the peer is still
        // connected, so give a real chance to recover. The FROZEN debounce + a
        // real rejoin (DECODING) cancel the timer well before this fires.
        const val PEER_STALL_TIMEOUT_MS = 25_000L
        // NET-004 — peer HARD-OFFLINE window (Agora kicked them out, caught late).
        // Short on purpose: Agora already waited ~20s before firing onUserOffline,
        // so a long grace on top = a ~50s dead screen. 15s (~35s total perceived)
        // is plenty to ride out a quick rejoin without stranding the survivor.
        const val PEER_GONE_TIMEOUT_MS = 15_000L
        private const val TICK_INTERVAL_MS = 1_000L
        // I038 — minimum FROZEN duration before we treat a peer stall as
        // real. Tuned for Indian mobile networks where multi-second jitter
        // bursts on otherwise-fine calls are routine; real outages last
        // much longer. Banner appears ~3.5s after stall start; the frozen
        // auto-end safety net is unaffected (it begins when this fires).
        private const val PEER_STALL_DEBOUNCE_MS = 3_500L
        private const val TAG = "ReconnectWatchdog"
    }
}
