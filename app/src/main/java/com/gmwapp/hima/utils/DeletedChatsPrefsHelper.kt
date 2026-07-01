package com.gmwapp.hima.utils

import android.content.Context

/**
 * Per-conversation "delete chat" watermark (WhatsApp-style).
 *
 * When a user picks "Delete chat", we record the timestamp (epoch millis) of the newest
 * message at that moment, keyed by (owner user id, peer user id). The chat LIST then hides
 * any conversation whose newest message is at or before that watermark — so the row leaves
 * the list. A NEW message (timestamp beyond the watermark) makes the conversation reappear,
 * exactly like WhatsApp.
 *
 * This is device-local only — it never deletes on the server or for the peer. Message hiding
 * inside the conversation is handled separately by [ClearedChatsPrefsHelper]; delete records
 * both so cleared messages also don't reappear on reopen.
 *
 * Keyed by owner id so a different account on the same device never inherits the watermark.
 */
object DeletedChatsPrefsHelper {

    private const val PREFS_NAME = "deleted_chats_prefs"

    private fun keyFor(ownerId: Int, peerId: Int): String = "deleted_upto_${ownerId}_${peerId}"

    /** Records that the conversation is deleted up to [uptoMillis] for this (owner, peer) pair. */
    fun setDeletedUpto(context: Context, ownerId: Int, peerId: Int, uptoMillis: Long) {
        if (ownerId <= 0 || peerId <= 0 || uptoMillis <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // The watermark only ever moves forward — a later delete extends it, never rewinds.
        val existing = prefs.getLong(keyFor(ownerId, peerId), 0L)
        if (uptoMillis > existing) {
            prefs.edit().putLong(keyFor(ownerId, peerId), uptoMillis).apply()
        }
    }

    /** Deleted-upto watermark (epoch millis) for this pair, or 0 if never deleted. */
    fun getDeletedUpto(context: Context, ownerId: Int, peerId: Int): Long {
        if (ownerId <= 0 || peerId <= 0) return 0L
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(keyFor(ownerId, peerId), 0L)
    }

    /**
     * True if the conversation should still be hidden from the chat list: it was deleted and
     * its newest message ([lastMessageMillis]) is at or before the watermark. A newer message
     * (or a null/0 watermark) returns false, so the chat reappears.
     */
    fun isHidden(context: Context, ownerId: Int, peerId: Int, lastMessageMillis: Long): Boolean {
        val upto = getDeletedUpto(context, ownerId, peerId)
        return upto > 0L && lastMessageMillis <= upto
    }

    /** Wipes only the watermarks belonging to [ownerId] (call on logout). */
    fun clearForUser(context: Context, ownerId: Int) {
        if (ownerId <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val prefix = "deleted_upto_${ownerId}_"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }
}
