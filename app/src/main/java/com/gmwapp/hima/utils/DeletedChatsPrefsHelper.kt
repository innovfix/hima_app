package com.gmwapp.hima.utils

import android.content.Context
import com.gmwapp.hima.models.ChatConversation

/**
 * TC_CH_004: device-local "Delete chat" watermarks.
 *
 * Deleting a chat removes the thread from the chat list (local-only — the server
 * keeps the conversation). We persist the id of the newest message at delete time;
 * the chat list then hides any thread whose last-message id is at or below that
 * watermark. A newer incoming message (higher id) makes the thread reappear — matching
 * WhatsApp. Delete also sets the [ClearedChatsPrefsHelper] watermark for the same peer
 * so a reappeared thread opens empty (old history stays gone), exactly like WhatsApp.
 *
 * See [ClearedChatsPrefsHelper] for the sibling "Clear chat" state and
 * [LocalChatListState] for how both overlays are applied to the chat list.
 */
object DeletedChatsPrefsHelper {

    const val PREFS_NAME = "deleted_chats_prefs"
    private const val KEY_PREFIX = "deleted_wm_"

    /** Newest message id deleted for [userId], or 0 if the thread was never deleted. */
    fun watermark(context: Context, userId: String): Long {
        if (userId.isBlank()) return 0L
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PREFIX + userId, 0L)
    }

    /**
     * Records that the thread with [userId] was deleted up to [watermarkMessageId]
     * (the newest message id at delete time). Monotonic — a stale call can't lower the
     * bar. A watermark of 0 (empty thread) still hides a thread whose last message id
     * is 0 and reappears the moment any real message arrives.
     */
    fun setDeleted(context: Context, userId: String, watermarkMessageId: Long) {
        if (userId.isBlank()) return
        val next = maxOf(watermark(context, userId), watermarkMessageId)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_PREFIX + userId, next)
            .apply()
    }

    /** True when [conversation] is currently hidden: deleted with no newer message since. */
    fun isHidden(context: Context, conversation: ChatConversation): Boolean {
        val wm = watermark(context, conversation.userId)
        return wm > 0L && conversation.lastMessageId in 0..wm
    }

    /** Wipe on logout / clear_data so a new login doesn't inherit state. */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
