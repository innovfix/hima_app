package com.gmwapp.hima.utils

import android.content.Context
import android.util.Log

/**
 * B_010: remembers the newest friend-request the user has actually looked at.
 *
 * The bottom-nav badge used to count every pending request that existed, so it never
 * cleared however many times the user opened the Requests tab — a creator with 13
 * requests saw "13" on the Chat tab forever and read it as 13 messages. The badge now
 * counts only what is newer than this watermark, which makes it mean "things you have
 * not seen" rather than "things that exist".
 *
 * Deliberately a **watermark, not a boolean**. A "cleared" flag would silence requests
 * that arrive later — a worse bug than the one being fixed. Storing the highest id seen
 * means anything newer still badges.
 *
 * Deliberately **local**. The server needs no schema change and no write path: the app
 * sends this value up and the server only answers how many requests are newer. The cost
 * is that the watermark does not follow the user to another device or survive a
 * reinstall — the badge reappears once and clears again on the next visit, which is a
 * far cheaper failure than a missed request.
 *
 * Keyed per user id: two accounts on one device must not inherit each other's watermark.
 */
object RequestsSeenPrefs {

    private const val TAG = "RequestsSeenPrefs"
    private const val FILE = "requests_seen_prefs"
    private const val KEY_PREFIX = "seen_request_id_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun key(userId: Int) = KEY_PREFIX + userId

    /**
     * Highest request id [userId] has seen, or 0 if they never opened Requests.
     * 0 makes the server report every pending request as new, i.e. today's behaviour —
     * the safe direction to fail in, since it over-notifies rather than under-notifies.
     */
    fun getSeenRequestId(context: Context, userId: Int): Int {
        if (userId <= 0) return 0
        return runCatching { prefs(context).getInt(key(userId), 0) }
            .getOrElse {
                Log.w(TAG, "getSeenRequestId failed for user=$userId: ${it.message}")
                0
            }
    }

    /**
     * Advance the watermark to [maxRequestId] once the Requests list has been rendered.
     *
     * Only ever moves **forward**. A stale or partial list (a search-filtered fetch, a
     * cached page) must never lower the watermark, or requests the user already dismissed
     * would badge again. Returns true if it actually moved.
     */
    fun markSeen(context: Context, userId: Int, maxRequestId: Int): Boolean {
        if (userId <= 0 || maxRequestId <= 0) return false
        return runCatching {
            val p = prefs(context)
            val current = p.getInt(key(userId), 0)
            if (maxRequestId <= current) {
                Log.d(TAG, "markSeen user=$userId no-op: $maxRequestId <= current $current")
                return false
            }
            p.edit().putInt(key(userId), maxRequestId).apply()
            Log.d(TAG, "markSeen user=$userId watermark $current -> $maxRequestId")
            true
        }.getOrElse {
            Log.w(TAG, "markSeen failed for user=$userId: ${it.message}")
            false
        }
    }

    /** Drop the watermark on logout so the next account starts clean. */
    fun clear(context: Context, userId: Int) {
        if (userId <= 0) return
        runCatching { prefs(context).edit().remove(key(userId)).apply() }
    }
}
