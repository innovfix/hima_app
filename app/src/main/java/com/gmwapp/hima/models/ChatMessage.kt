package com.gmwapp.hima.models

import java.util.Date

/**
 * Outgoing message delivery / read UI (single tick = sent, double grey = received/delivered, double blue = seen).
 * Ignored in the adapter when [isSentByMe] is false.
 */
enum class MessageDeliveryStatus {
    /** Optimistic row before server/socket ack */
    SENDING,
    /** Bug 10: send timed out or backend rejected. Bubble stays visible
     *  with a red error icon and a "Tap to retry" affordance so the user
     *  can resend without re-typing — and so the network-reconnect listener
     *  has something to enumerate when the connection comes back. */
    FAILED,
    /** Single tick — accepted, not yet shown as delivered to peer */
    SENT,
    /** Double grey ticks — in thread / delivered, peer has not read */
    DELIVERED,
    /** Double blue ticks — peer read ([ChatMessageApi.isRead] / socket) */
    READ,
}

data class ChatMessage(
    val id: String,
    val message: String,
    val timestamp: String,
    val isSentByMe: Boolean,
    val date: Date? = null,  // Store the actual Date object for date grouping
    val isDateHeader: Boolean = false,  // True if this is a date header separator
    val dateHeaderText: String = "",  // The date header text (e.g., "Today", "Yesterday", "24 Dec 2024")
    val reactions: Map<Int, String> = emptyMap(),  // Map of userId -> reactionEmoji (WhatsApp style)
    val messageType: String = "text",
    val attachmentUrl: String? = null,
    val audioDurationMs: Long = 0L,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.DELIVERED,
    /**
     * True once this message has been deleted for everyone (via local tap or a remote
     * `message_deleted` socket event / history flag). The adapter renders a tombstone
     * bubble instead of the original content, and long-press/reactions are disabled.
     */
    val isDeleted: Boolean = false,
)


