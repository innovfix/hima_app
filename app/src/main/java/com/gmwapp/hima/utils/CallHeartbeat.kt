package com.gmwapp.hima.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * TC-NET-005 — per-side call liveness heartbeat (Phase A wiring).
 *
 * While an in-call activity is on screen, ping the server every 10s with
 * (call_id, user_id) so the backend records this participant's last_seen in the
 * call_heartbeats table. A later server-side orphan detector (Phase B) reads
 * those rows to spot a ONE-sided network drop — where one party silently
 * vanishes while the other keeps an open, still-billing call — and ends the
 * call stamped at the last good heartbeat so billing stops at the real drop
 * moment (no charge for dead air).
 *
 * This object only WRITES heartbeats; it changes no call/billing behaviour on
 * its own. Singleton + single timer because only one call is ever active.
 *
 * Threading: start() is invoked from Agora's IRtcEngineEventHandler WORKER
 * thread (onUserJoined), while stop()/the timer run on the main thread. To avoid
 * a data race on the shared state, ALL mutation is funneled onto the main-looper
 * [handler] — start()/stop() just post the real work there, so it is serialized.
 */
object CallHeartbeat {

    private const val TAG = "CallHeartbeat"
    private const val INTERVAL_MS = 10_000L

    private val handler = Handler(Looper.getMainLooper())

    // @Volatile for the cross-thread reader activeCallId() (FCM callEnded handler).
    // All WRITES happen on the main thread via [handler], so start/stop/timer
    // never race each other.
    @Volatile private var activeCallId = 0
    private var runnable: Runnable? = null // touched only on the main thread

    // One shared client — building a fresh OkHttpClient per 10s beat would leak a
    // connection pool + dispatcher threads on every tick across a long call.
    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /** Begin heartbeating for [callId]. Idempotent for the same call. Thread-safe. */
    fun start(callId: Int) {
        if (callId <= 0) return
        handler.post {
            if (activeCallId == callId && runnable != null) return@post
            stopInternal()
            activeCallId = callId
            val r = object : Runnable {
                override fun run() {
                    if (activeCallId <= 0) return
                    postBeat(activeCallId)
                    handler.postDelayed(this, INTERVAL_MS)
                }
            }
            runnable = r
            handler.post(r) // immediate first beat, then every INTERVAL_MS
            Log.d(TAG, "started callId=$callId")
        }
    }

    /**
     * The call_id currently being heart-beaten (the connected, in-call id), or 0
     * if no call is active. Used by the FCM callEnded handler to gate an in-call
     * teardown on a MATCHING call_id (NET-004) so a stale/wrong callEnded can
     * never end a different live call.
     */
    fun activeCallId(): Int = activeCallId

    /** Stop heartbeating (call ended / activity destroyed). Safe to call twice / any thread. */
    fun stop() {
        handler.post { stopInternal() }
    }

    /** Always runs on the main thread (posted from start/stop). */
    private fun stopInternal() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        if (activeCallId != 0) Log.d(TAG, "stopped callId=$activeCallId")
        activeCallId = 0
    }

    private fun postBeat(callId: Int) {
        val prefs = BaseApplication.getInstance()?.getPrefs() ?: return
        val userId = prefs.getUserData()?.id ?: return
        val token = prefs.getAuthenticationToken().orEmpty()
        if (userId <= 0 || token.isBlank() || callId <= 0) return
        Thread({
            try {
                val url = BuildConfig.BASE_URL + "call_heartbeat"
                val body = okhttp3.FormBody.Builder()
                    .add("call_id", callId.toString())
                    .add("user_id", userId.toString())
                    .build()
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { /* fire-and-forget */ }
            } catch (t: Throwable) {
                // Best-effort: a dropped beat is exactly what the detector treats
                // as a (possible) drop; no need to surface errors to the user.
                Log.w(TAG, "beat failed callId=$callId: ${t.message}")
            }
        }, "CallHeartbeat-$callId").start()
    }
}
