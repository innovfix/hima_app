package com.gmwapp.hima.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Per-user, per-peer set of messageIds the local user has chosen to hide
 * via "Delete for me". Purely client-side: server is never told, peer never
 * affected. Persists across app restarts in SharedPrefs; wiped on clear-data
 * (acceptable — matches WhatsApp behavior).
 *
 * Keyed by (myUserId, peerUserId) so multiple accounts on the same device
 * stay isolated. Values are stored as a Set<String> per chat.
 */
object LocallyDeletedMessagesStore {

    private const val PREFS_NAME = "chat_locally_deleted_messages"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun keyFor(myUserId: Int, peerUserId: Int): String =
        "deleted_for_me__${myUserId}__${peerUserId}"

    fun add(context: Context, myUserId: Int, peerUserId: Int, messageId: String) {
        if (messageId.isBlank()) return
        val p = prefs(context)
        val key = keyFor(myUserId, peerUserId)
        val current = p.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (current.add(messageId)) {
            // mutate-and-put pattern: SharedPreferences returns an immutable view
            // and ignores mutations to the returned set on some OEMs.
            p.edit().putStringSet(key, current).apply()
        }
    }

    fun isLocallyDeleted(
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

    fun getAll(context: Context, myUserId: Int, peerUserId: Int): Set<String> =
        prefs(context).getStringSet(keyFor(myUserId, peerUserId), emptySet()).orEmpty()
}
