package com.gmwapp.hima.utils

import android.content.Context
import com.gmwapp.hima.BaseApplication

/**
 * B_028 follow-up — "Delete for me" preview must not collapse the whole thread.
 *
 * The my_chat / friends API returns only ONE `lastMessage` per conversation. When the
 * user hides that single last message via "Delete for me" (a device-local hide the
 * server never learns about), the old B_028 code blanked the list preview to "" — which
 * the row adapter renders as "No messages yet", even though the thread still has plenty
 * of earlier messages. That reads as a bug (see on-device report 2026-07-24).
 *
 * WhatsApp shows the PREVIOUS message instead. We can do the same because the chat screen
 * keeps a process-scoped snapshot of the full message list per peer in
 * [ChatHistoryMemoryCache] (populated whenever that chat is opened — which it always was,
 * since the delete happens inside it). This helper walks that snapshot newest-first and
 * returns the newest still-visible message's preview.
 *
 * Returns null when there is no snapshot (e.g. app restarted and the chat wasn't reopened
 * this session) or every remaining message is also hidden — the caller then keeps the old
 * blank/self-healing fallback (a newer message restores the real preview on its own).
 */
object ChatPreviewFallback {

    /** (previewText, previewType) of the newest non-hidden message, or null. */
    fun previousVisiblePreview(context: Context, myId: Int, peerId: Int): Pair<String, String>? {
        if (myId <= 0 || peerId <= 0) return null
        val cache = BaseApplication.getInstance()?.chatHistoryMemoryCache ?: return null
        val snapshot = cache.getSnapshot(peerId) ?: return null
        for (msg in snapshot.asReversed()) {
            if (msg.isDateHeader) continue
            if (LocallyDeletedMessagesStore.isLocallyDeleted(context, myId, peerId, msg.id)) continue
            val type = msg.messageType.lowercase().ifBlank { "text" }
            // A "delete for everyone" tombstone carries no real body; skip it so we surface
            // the previous genuine message rather than another empty preview.
            val text = if (msg.isDeleted) "" else msg.message
            // Skip blank text rows so we don't just reintroduce "No messages yet"; non-text
            // types (image/audio/video/file) legitimately have empty text — the adapter
            // renders their type label, so let those through.
            if (type == "text" && text.isBlank()) continue
            return Pair(text, type)
        }
        return null
    }
}
