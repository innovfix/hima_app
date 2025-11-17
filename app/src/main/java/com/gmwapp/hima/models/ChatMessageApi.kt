package com.gmwapp.hima.models

import com.google.gson.annotations.SerializedName

data class ChatMessageApi(
    val id: Int,
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
    val timestamp: String,
    @SerializedName("from_user_id")
    val fromUserId: Int? = null,
    @SerializedName("to_user_id")
    val toUserId: Int? = null
)

