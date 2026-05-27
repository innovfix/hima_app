package com.gmwapp.hima.models

import com.google.gson.annotations.SerializedName

// T4: id widened to Long to keep parity with the socket payload and avoid
// truncation when the backend emits snowflake-style ids beyond Int.MAX_VALUE.
data class ChatMessageApi(
    val id: Long,
    @SerializedName("client_msg_id")
    val clientMsgId: String? = null,
    @SerializedName("chat_id")
    val chatId: String,
    val from: String,
    val to: String,
    val message: String,
    @SerializedName("message_type")
    val messageType: String = "text",
    @SerializedName("attachment_url")
    val attachmentUrl: String? = null,
    @SerializedName("is_read")
    val isRead: Boolean = false,
    @SerializedName("delivered")
    val delivered: Boolean = false, // CHAT-026
    val timestamp: String,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("from_user_id")
    val fromUserId: Int? = null,
    @SerializedName("to_user_id")
    val toUserId: Int? = null,
    val reactions: List<MessageReaction>? = null,  // Array of {user_id, reaction_emoji}
    /** 1 when the sender has deleted-for-everyone; backend also blanks `message`/`attachment_url`. */
    @SerializedName("is_deleted")
    val isDeleted: Int? = null
)

