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
                    // Cheap parse: look for "alive":false. Backend returns
                    //   { "alive": true|false, "ended_time": "...", "call_id": N }
                    val dead = raw.contains("\"alive\":false") || raw.contains("\"alive\": false")
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
