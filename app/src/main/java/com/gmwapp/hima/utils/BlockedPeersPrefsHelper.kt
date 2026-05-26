package com.gmwapp.hima.utils

import android.content.Context

/**
 * Local cache of peer user IDs that the current user has blocked. Lets the
 * chat list show a Blocked badge synchronously — without waiting for the
 * per-thread `check_block_status` round-trip on every visible row.
 *
 * Source of truth is still the server (chat-history API returns
 * `iHaveBlockedThisUser` per thread, and `blocked_user` / `unblock_user`
 * mutate the server state). This cache is updated alongside those calls so
 * the list reflects the state immediately on return to the list screen.
 */
object BlockedPeersPrefsHelper {

    const val PREFS_NAME = "blocked_peers_prefs"
    private const val KEY_BLOCKED_IDS = "blocked_user_ids"

    private fun getSet(context: Context): MutableSet<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_BLOCKED_IDS, null)
            .orEmpty()
        return raw.toMutableSet()
    }

    private fun save(context: Context, set: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_BLOCKED_IDS, set.toSet())
            .apply()
    }

    fun isBlocked(context: Context, userId: String): Boolean {
        if (userId.isBlank()) return false
        return getSet(context).contains(userId)
    }

    fun setBlocked(context: Context, userId: String, blocked: Boolean) {
        if (userId.isBlank()) return
        val set = getSet(context)
        val changed = if (blocked) set.add(userId) else set.remove(userId)
        if (changed) save(context, set)
    }

    /** Wipe on logout / clear_data so a new login doesn't inherit state. */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_BLOCKED_IDS)
            .apply()
    }
}
