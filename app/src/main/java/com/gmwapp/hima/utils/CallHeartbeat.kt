package com.gmwapp.hima.utils

import android.util.Log

/**
 * TC-NET-005 — tracks the call_id of the currently-connected in-call session.
 *
 * NOTE: this previously also POSTed a 10s network heartbeat from both sides to
 * record per-side liveness. At Hima's scale (hundreds–thousands of concurrent
 * calls) that added ~hundreds of requests/sec + a DB write each — real load on
 * an already-busy box. Liveness is now captured server-side for FREE by
 * piggybacking the EXISTING 30s `get_remaining_time` ping both sides already
 * send during a call (the backend records last_seen there). So this object no
 * longer does ANY network I/O — it only remembers which call is active so the
 * FCM `callEnded` handler can gate an in-call teardown on a MATCHING call_id
 * (NET-004). start()/stop() are kept (and still called from the 4 in-call
 * activities) purely to maintain that id.
 */
object CallHeartbeat {

    private const val TAG = "CallHeartbeat"

    // @Volatile: written from Agora's worker thread (onUserJoined) and the main
    // thread (onDestroy), read from the FCM callEnded handler. A plain int read
    // is atomic; @Volatile guarantees visibility across those threads.
    @Volatile private var activeCallId = 0

    /** Record the connected call id. Safe to call from any thread. */
    fun start(callId: Int) {
        if (callId <= 0) return
        activeCallId = callId
        Log.d(TAG, "active callId=$callId")
    }

    /**
     * The call_id currently connected (the in-call id), or 0 if no call is
     * active. Used by the FCM callEnded handler to gate an in-call teardown on a
     * MATCHING call_id (NET-004) so a stale/wrong callEnded can never end a
     * different live call.
     */
    fun activeCallId(): Int = activeCallId

    /** Clear on call end / activity destroy. Safe to call twice / from any thread. */
    fun stop() {
        if (activeCallId != 0) Log.d(TAG, "cleared callId=$activeCallId")
        activeCallId = 0
    }
}
