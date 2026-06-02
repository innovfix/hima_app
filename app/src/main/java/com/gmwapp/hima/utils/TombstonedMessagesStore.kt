package com.gmwapp.hima.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Per-user, per-peer set of messageIds that have been "deleted for everyone".
 *
 * Why this exists (server-independent tombstones): delete-for-everyone is
 * confirmed server-side and normally re-surfaced on reload via the
 * `chat_history` `is_deleted` flag. But if any backend the app talks to drops
 * that flag from its response, the tombstone is lost on reopen and the message
 * reappears. To make the delete stick **no matter the server**, we also record
 * the id locally the moment we either (a) delete it ourselves or (b) receive
 * the live `message_deleted` socket event. On every chat load the converters
 * OR this in, so the bubble stays a tombstone regardless of what the history
 * endpoint returns.
 *
 * Distinct from [LocallyDeletedMessagesStore]: that one *hides* a message
 * (delete-for-me); this one *tombstones* it (delete-for-everyone, shows
 * "This message was deleted"). Keyed by (myUserId, peerUserId) so multiple
 * accounts on one device stay isolated.
 */
object TombstonedMessagesStore {

    private const val PREFS_NAME = "chat_tombstoned_messages"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun keyFor(myUserId: Int, peerUserId: Int): String =
        "deleted_for_everyone__${myUserId}__${peerUserId}"

    fun add(context: Context, myUserId: Int, peerUserId: Int, messageId: String) {
        if (messageId.isBlank()) return
        val p = prefs(context)
        val key = keyFor(myUserId, peerUserId)
        val current = p.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (current.add(messageId)) {
            // mutate-and-put: SharedPreferences returns an immutable view and
            // ignores in-place mutations on some OEMs.
            p.edit().putStringSet(key, current).apply()
        }
    }

    /** Undo a tombstone record — used when an optimistic delete is rolled back. */
    fun remove(context: Context, myUserId: Int, peerUserId: Int, messageId: String) {
        if (messageId.isBlank()) return
        val p = prefs(context)
        val key = keyFor(myUserId, peerUserId)
        val current = p.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (current.remove(messageId)) {
            p.edit().putStringSet(key, current).apply()
        }
    }

    fun isTombstoned(
        context: Context,
        myUserId: Int,
        peerUserId: Int,
        messageId: String
    ): Boolean {
        if (messageId.isBlank()) return false
        return prefs(context)
            .getStringSet(keyFor(myUserId, peerUserId), emptySet())
            .orEmpty()
            .contains(messageId)
    }
}
