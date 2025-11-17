package com.gmwapp.hima.models

import com.google.gson.annotations.SerializedName

data class Chat(
    @SerializedName("chat_id")
    val chatId: String,
    @SerializedName("user1_id")
    val user1Id: Int? = null,
    @SerializedName("user2_id")
    val user2Id: Int? = null,
    @SerializedName("other_user_id")
    val otherUserId: Int? = null,
    @SerializedName("other_user_name")
    val otherUserName: String? = null,
    @SerializedName("other_user_image")
    val otherUserImage: String? = null,
    @SerializedName("last_message")
    val lastMessage: String? = null,
    @SerializedName("last_message_time")
    val lastMessageTime: String? = null,
    @SerializedName("unread_count")
    val unreadCount: Int = 0
)

