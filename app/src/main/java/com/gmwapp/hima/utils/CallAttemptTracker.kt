package com.gmwapp.hima.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * CALL-104: per-peer client-side guard against rapid call hammering.
 *
 * Counts consecutive failed outbound-call attempts (cancel, no-pickup,
 * rejected) for each (myUserId, peerUserId) pair. Once the user hits
 * [THRESHOLD] failures against a SPECIFIC peer, a 1-minute cooldown locks
 * out further outbound calls to that peer only — calls to other peers are
 * unaffected. Surfaced to the user as a toast, since the server-side
 * throttle the QA team observed gives ZERO feedback.
 *
 * Counter resets to 0 in two cases:
 *   1. A call to the same peer actually connects (status=accepted).
 *   2. The cooldown deadline against that peer passes.
 *
 * Persisted via SharedPrefs so killing the app can't reset the counter
 * mid-cooldown. Keyed per (myUserId, peerUserId) so account switches AND
 * peer switches start fresh.
 */
object CallAttemptTracker {

    private const val PREFS_NAME = "call_attempt_tracker"
    private const val KEY_COUNT_PREFIX = "failed_count_"
    private const val KEY_COOLDOWN_UNTIL_PREFIX = "cooldown_until_"

    const val THRESHOLD: Int = 4
    const val COOLDOWN_MS: Long = 60_000L // 1 minute

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun myUserId(): Int? {
        return com.gmwapp.hima.BaseApplication.getInstance()
            ?.getPrefs()
            ?.getUserData()
            ?.id
            ?.takeIf { it > 0 }
    }

    private fun countKey(myUserId: Int, peerUserId: Int): String =
        "${KEY_COUNT_PREFIX}${myUserId}_$peerUserId"

    private fun cooldownKey(myUserId: Int, peerUserId: Int): String =
        "${KEY_COOLDOWN_UNTIL_PREFIX}${myUserId}_$peerUserId"

    /**
     * Record one failed outbound-call attempt to [peerUserId]. When the
     * cumulative count for this peer crosses [THRESHOLD] this also opens a
     * 1-minute cooldown against that peer.
     */
    fun recordFailedAttempt(context: Context, peerUserId: Int) {
        if (peerUserId <= 0) return
        val me = myUserId() ?: return
        val p = prefs(context)
        val newCount = p.getInt(countKey(me, peerUserId), 0) + 1
        val edit = p.edit().putInt(countKey(me, peerUserId), newCount)
        if (newCount >= THRESHOLD) {
            edit.putLong(
                cooldownKey(me, peerUserId),
                System.currentTimeMillis() + COOLDOWN_MS
            )
        }
        edit.apply()
    }

    /**
     * True iff the user is currently inside a 1-minute cooldown for calls to
     * [peerUserId]. When the deadline has just passed, this call also wipes
     * the per-peer deadline and counter so the next tap starts fresh.
     */
    fun isInCooldown(context: Context, peerUserId: Int): Boolean {
        if (peerUserId <= 0) return false
        val me = myUserId() ?: return false
        val p = prefs(context)
        val deadline = p.getLong(cooldownKey(me, peerUserId), 0L)
        if (deadline <= 0L) return false
        if (System.currentTimeMillis() < deadline) return true
        p.edit()
            .remove(cooldownKey(me, peerUserId))
            .remove(countKey(me, peerUserId))
            .apply()
        return false
    }

    /** Milliseconds until the cooldown for [peerUserId] lifts. Zero when not in cooldown. */
    fun cooldownRemainingMs(context: Context, peerUserId: Int): Long {
        if (peerUserId <= 0) return 0L
        val me = myUserId() ?: return 0L
        val deadline = prefs(context).getLong(cooldownKey(me, peerUserId), 0L)
        val remaining = deadline - System.currentTimeMillis()
        return if (remaining > 0L) remaining else 0L
    }

    /**
     * Wipe the counter + any cooldown for the (myUserId, [peerUserId]) pair —
     * call this when an outbound call to that peer genuinely connects so a
     * recovered run starts with the full 4-attempt budget again.
     */
    fun clearAttempts(context: Context, peerUserId: Int) {
        if (peerUserId <= 0) return
        val me = myUserId() ?: return
        prefs(context).edit()
            .remove(countKey(me, peerUserId))
            .remove(cooldownKey(me, peerUserId))
            .apply()
    }
}
