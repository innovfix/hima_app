package com.gmwapp.hima.utils

import android.content.Context

/**
 * TC_017: per-conversation "clear for me" watermark.
 *
 * When a user blocks + "also delete chat", we record the timestamp (epoch millis) of the
 * newest cleared message, keyed by (owner user id, peer user id). Chat history is then
 * filtered to drop any message at or before that watermark, so cleared messages do NOT
 * reappear after a server re-fetch, a reopen, or an app relaunch on this device.
 *
 * This is device-local "delete for me" — it never deletes on the server or for the peer.
 * Keyed by owner id so a different account on the same device never inherits the watermark.
 */
object ClearedChatsPrefsHelper {

    private const val PREFS_NAME = "cleared_chats_prefs"

    private fun keyFor(ownerId: Int, peerId: Int): String = "cleared_upto_${ownerId}_${peerId}"

    /** Records that everything up to [uptoMillis] is cleared for this (owner, peer) pair. */
    fun setClearedUpto(context: Context, ownerId: Int, peerId: Int, uptoMillis: Long) {
        if (ownerId <= 0 || peerId <= 0 || uptoMillis <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // The watermark only ever moves forward — a later clear extends it, never rewinds.
        val existing = prefs.getLong(keyFor(ownerId, peerId), 0L)
        if (uptoMillis > existing) {
            prefs.edit().putLong(keyFor(ownerId, peerId), uptoMillis).apply()
        }
    }

    /** Cleared-upto watermark (epoch millis) for this pair, or 0 if the chat was never cleared. */
    fun getClearedUpto(context: Context, ownerId: Int, peerId: Int): Long {
        if (ownerId <= 0 || peerId <= 0) return 0L
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(keyFor(ownerId, peerId), 0L)
    }

    /** Wipes only the watermarks belonging to [ownerId] (call on logout so the next account on this device keeps its own watermarks). */
    fun clearForUser(context: Context, ownerId: Int) {
        if (ownerId <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val prefix = "cleared_upto_${ownerId}_"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }
}
