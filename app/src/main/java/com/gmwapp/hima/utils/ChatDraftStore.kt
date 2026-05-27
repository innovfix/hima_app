package com.gmwapp.hima.utils

import android.content.Context

/**
 * CHAT-108: Per-peer composer draft persistence so a half-typed message
 * survives switching chats, cold restarts, and process kills — not just
 * the rotation/config-change window that the existing
 * [android.app.Activity.onSaveInstanceState] bundle covers.
 *
 * SharedPreferences-backed by design — same rationale as
 * [ChatNotificationStore]: the data must survive cold-start boundaries
 * and we don't need anything richer than a string per peer.
 *
 * Eviction:
 * - [save] with blank text removes the entry (= drafts are auto-pruned
 *   when the user clears the composer).
 * - [clear] called explicitly on send-success in [com.gmwapp.hima.activities.ChatActivityInHouse].
 * - [clearAll] called from logout / `clear_data` so a new account never
 *   sees the previous user's drafts on shared peer ids.
 */
object ChatDraftStore {

    private const val PREFS = "chat_draft_store"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(peerId: Int) = "draft_$peerId"

    fun save(ctx: Context, peerId: Int, text: String?) {
        val trimmed = text?.trimEnd().orEmpty()
        val editor = prefs(ctx).edit()
        if (trimmed.isEmpty()) editor.remove(key(peerId)) else editor.putString(key(peerId), trimmed)
        editor.apply()
    }

    /** Returns the saved draft, or `""` if none. Never null so callers can `setText` safely. */
    fun get(ctx: Context, peerId: Int): String =
        prefs(ctx).getString(key(peerId), null).orEmpty()

    fun clear(ctx: Context, peerId: Int) {
        prefs(ctx).edit().remove(key(peerId)).apply()
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
