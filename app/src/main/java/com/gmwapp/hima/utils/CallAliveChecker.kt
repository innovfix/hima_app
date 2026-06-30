package com.gmwapp.hima.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gmwapp.hima.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Quick backend status check used by all 4 calling activities when our
 * own connection comes back after a RECONNECTING period. The scenario:
 *
 *   1. User A loses internet, sees "Reconnecting… 25s" countdown.
 *   2. User B (with internet) presses End during A's offline window.
 *   3. A's network restores at, say, 15s remaining.
 *   4. Agora's onUserOffline for B's leave doesn't always fire fast on A
 *      after the link restores (the SDK has to re-sync state first), so
 *      A's screen stays on "Reconnecting…" until the 30s watchdog fires.
 *      This makes A think the call hung for 30s after B left.
 *
 * Fix: as soon as A's connection comes back, ping /api/auth/check_call_alive
 * with the active call_id. If backend says ended_time is set (B has already
 * recorded the hang-up), hang up immediately on A's side too. Avoids the
 * cosmetic 5-30s lag.
 *
 * Fire-and-forget on a background thread with 3s timeouts. Returns the
 * "should end" decision via a main-thread callback so the activity can
 * call leaveChannel() safely.
 */
object CallAliveChecker {

    private const val TAG = "CallAliveChecker"

    /**
     * @param callId   the active call's id (skip if 0 / unknown)
     * @param onShouldEnd run on main thread IF backend confirms the call has ended.
     *                    NOT called if call is alive, network fails, or any error.
     */
    fun checkAndEndIfDead(callId: Int, onShouldEnd: () -> Unit) {
        // 2026-05-23 v1068 — DISABLED. Was causing calls to end immediately
        // for some users when backend race or timing made the call look "dead"
        // right after Agora CONNECTED. Reverting to Agora's natural onUserOffline
        // + the 30s watchdog as fallback. User-reported regression vs Play Store v1064.
        return
        doCheck(callId, requireEndReason = false, onShouldEnd = onShouldEnd)
    }

    /**
     * Caller-side, PRE-CONNECT variant. Safe to poll from the "Connecting…"
     * screens (Male/FemaleCallConnectingActivity) where the call has NOT yet
     * been answered — so the v1068 "looked dead right after CONNECTED" race
     * cannot apply (an unanswered call has ended_time NULL and created < 30s,
     * so backend reports alive=true during legitimate ringing).
     *
     * Why this exists: the callee's decline reaches the caller only via a
     * best-effort FCM "rejected" relay. When that push is dropped/delayed
     * (Doze, battery optimization, stale token, flaky link) the caller would
     * otherwise dangle on "Connecting…" for the full 40s timeout even though
     * the backend already stamped the call ended (CALL_DEAD_MARKER /
     * end_reason='rejected'). One PK lookup confirms the authoritative state.
     */
    fun checkConnectingDead(callId: Int, onShouldEnd: () -> Unit) {
        // requireEndReason=true: only disconnect when the call was ACTIVELY ended
        // (end_reason set, e.g. 'rejected'/'not_answered'), NOT when the backend's
        // 30s "never-answered" age-guard alone flips alive=false. The connecting
        // screen keeps its own 40s ring timeout (sized for slow ROMs that defer the
        // ringer UI up to ~15s); acting on the age-guard here would cut a still-
        // legitimately-ringing call short at ~30s.
        doCheck(callId, requireEndReason = true, onShouldEnd = onShouldEnd)
    }

    /**
     * BLOCKING, FCM-thread variant. Returns whether the backend currently
     * considers this call a LIVE incoming ring (alive=true). Used by the
     * incoming-call FCM path (TC-INC-001) to second-guess the 20s staleness
     * guard: a push judged "too late" by the device clock may actually be a
     * still-ringing call (clock skew between caller/recipient, or a missing/
     * unparseable timestamp that makes the diff look enormous). Rather than
     * silently dropping the whole banner, confirm against server truth.
     *
     * Runs on the calling (FCM dispatch) thread. Timeouts are kept TIGHT (2s
     * each phase, ~6s absolute worst case) and a callTimeout caps the total:
     * onMessageReceived has a ~20s system budget, and a Doze wake can deliver
     * several stale pushes back-to-back, each hitting this path — so the block
     * must stay well under that budget even when serialized. Returns:
     *   true  -> backend says alive (ring it)
     *   false -> backend says ended / not found / error / no call_id (drop)
     * Fail-CLOSED on any error so a flaky link can't conjure a ghost ring.
     */
    fun isRingingNow(callId: Int): Boolean {
        if (callId <= 0) return false
        return try {
            val url = BuildConfig.BASE_URL + "check_call_alive"
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build()
            val body = okhttp3.FormBody.Builder().add("call_id", callId.toString()).build()
            val req = okhttp3.Request.Builder().url(url).post(body).build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: return false
                Log.d(TAG, "isRingingNow callId=$callId resp=$raw")
                raw.contains("\"alive\":true") || raw.contains("\"alive\": true")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "isRingingNow failed (fail-closed → drop): ${t.message}")
            false
        }
    }

    private fun doCheck(callId: Int, requireEndReason: Boolean, onShouldEnd: () -> Unit) {
        if (callId <= 0) {
            Log.d(TAG, "skip: callId=$callId")
            return
        }
        Thread({
            try {
                val url = BuildConfig.BASE_URL + "check_call_alive"
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .writeTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()
                val body = okhttp3.FormBody.Builder()
                    .add("call_id", callId.toString())
                    .build()
                val req = okhttp3.Request.Builder().url(url).post(body).build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string() ?: return@Thread
                    Log.d(TAG, "callId=$callId resp=$raw")
                    // Backend returns:
                    //   { "alive": true|false, "ended_time": "...", "reason": "...", "call_id": N }
                    val notAlive = raw.contains("\"alive\":false") || raw.contains("\"alive\": false")
                    // A genuine peer-end carries a non-null end_reason ("reason":"rejected"…).
                    // The age-guard path leaves it null ("reason":null), which we must ignore.
                    val hasEndReason = Regex("\"reason\"\\s*:\\s*\"[^\"]+\"").containsMatchIn(raw)
                    val dead = notAlive && (!requireEndReason || hasEndReason)
                    if (dead) {
                        Handler(Looper.getMainLooper()).post {
                            try { onShouldEnd() } catch (t: Throwable) { Log.w(TAG, "onShouldEnd threw: ${t.message}") }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "checkAndEndIfDead failed (silently — watchdog/Agora fallback will end call): ${t.message}")
            }
        }, "CallAliveChecker-$callId").start()
    }
}
