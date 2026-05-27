package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class FallbackSendMessageResponse(
    val success: Boolean,
    val message: String?,
    val data: FallbackMessageData?
)

data class FallbackMessageData(
    val message: FallbackMessage?,
    @SerializedName("is_blocked")
    val isBlocked: Boolean? = null,
    @SerializedName("notification_sent")
    val notificationSent: Boolean? = null,
    // CHAT-023/024: true when the server treated this as a duplicate send.
    val duplicate: Boolean? = null
)

data class FallbackMessage(
    val id: Int,
    @SerializedName("client_msg_id")
    val clientMsgId: String? = null,
    @SerializedName("chat_id")
    val chatId: String,
    @SerializedName("from_user_id")
    val fromUserId: Int,
    @SerializedName("to_user_id")
    val toUserId: Int,
    val message: String,
    @SerializedName("message_type")
    val messageType: String = "text",
    @SerializedName("attachment_url")
    val attachmentUrl: String? = null,
    @SerializedName("is_read")
    val isRead: Boolean = false,
    val timestamp: String,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)











