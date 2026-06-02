package com.gmwapp.hima.utils

import android.content.Context

/**
 * TC_CH_004: device-local "Clear chat" watermarks.
 *
 * Clearing a chat is a local-only action — the server has no per-user clear
 * endpoint (only `delete_chat_message`, which deletes for BOTH sides). So we
 * persist the id of the newest message present at clear time. Every chat-history
 * load then drops messages whose numeric id is at or below that watermark, and the
 * chat list blanks the thread's preview. A cleared thread therefore stays empty
 * across reopens / app restarts until a NEWER message (higher id) arrives — matching
 * WhatsApp.
 *
 * Source of truth is still the server; this is a local overlay, mirrored on the
 * block / pin / locally-deleted helpers and cleared on logout. See
 * [DeletedChatsPrefsHelper] for the sibling "Delete chat" (hide-thread) state and
 * [LocalChatListState] for how both are applied to the chat list.
 */
object ClearedChatsPrefsHelper {

    const val PREFS_NAME = "cleared_chats_prefs"
    private const val KEY_PREFIX = "cleared_wm_"

    /** Newest message id cleared for [userId], or 0 if the thread was never cleared. */
    fun watermark(context: Context, userId: String): Long {
        if (userId.isBlank()) return 0L
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PREFIX + userId, 0L)
    }

    /**
     * Records that the thread with [userId] was cleared up to [watermarkMessageId]
     * (the newest message id at clear time). Monotonic — a stale call can never lower
     * the bar and un-clear newer history.
     */
    fun setCleared(context: Context, userId: String, watermarkMessageId: Long) {
        if (userId.isBlank() || watermarkMessageId <= 0L) return
        val next = maxOf(watermark(context, userId), watermarkMessageId)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_PREFIX + userId, next)
            .apply()
    }

    /** True when [messageId] is a real (>0) message at or below the clear watermark. */
    fun isClearedAtOrBefore(context: Context, userId: String, messageId: Long): Boolean {
        val wm = watermark(context, userId)
        return wm > 0L && messageId in 1..wm
    }

    /** Wipe on logout / clear_data so a new login doesn't inherit state. */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
