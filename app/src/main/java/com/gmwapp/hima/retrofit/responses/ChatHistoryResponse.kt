package com.gmwapp.hima.retrofit.responses

import com.gmwapp.hima.models.ChatMessageApi
import com.google.gson.annotations.SerializedName

data class ChatHistoryResponse(
    val success: Boolean,
    val message: String,
    val data: ChatHistoryData?
)

data class ChatHistoryData(
    @SerializedName("chat_id")
    val chatId: String,
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("receiver_id")
    val receiverId: Int,
    @SerializedName("last_online")
    val lastOnline: String?,
    @SerializedName("last_online_status")
    val lastOnlineStatus: String?,
    @SerializedName("total_messages")
    val totalMessages: Int,
    @SerializedName("returned_messages")
    val returnedMessages: Int,
    val limit: Int,
    val offset: Int,
    @SerializedName("has_more")
    val hasMore: Boolean,
    @SerializedName("i_have_blocked_this_user")
    val iHaveBlockedThisUser: Boolean,
    /**
     * True when the chat partner has blocked the current user. Drives the
     * dimmed-call-buttons + banner + send-intercept UI on chat detail.
     * Defaults to false for backward compat with older servers that
     * don't return this field.
     */
    @SerializedName("this_user_has_blocked_me")
    val thisUserHasBlockedMe: Boolean = false,
    val messages: List<ChatMessageApi>
)

