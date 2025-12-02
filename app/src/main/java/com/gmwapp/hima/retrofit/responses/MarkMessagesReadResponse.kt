package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class MarkMessagesReadResponse(
    val success: Boolean,
    val message: String,
    val data: MarkMessagesReadData?
)

data class MarkMessagesReadData(
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("chat_id")
    val chatId: String,
    @SerializedName("last_message_id")
    val lastMessageId: Int,
    @SerializedName("messages_marked_read")
    val messagesMarkedRead: Int,
    @SerializedName("remaining_unread_count")
    val remainingUnreadCount: Int
)








