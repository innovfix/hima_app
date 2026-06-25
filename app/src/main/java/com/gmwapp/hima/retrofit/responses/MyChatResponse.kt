package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class MyChatResponse(
    val success: Boolean,
    val message: String,
    val data: MyChatData?
)

data class MyChatData(
    @SerializedName("user_id")
    val userId: Int,
    val chats: List<ChatItem>,
    @SerializedName("total_chats")
    val totalChats: Int
)

data class ChatItem(
    @SerializedName("chat_id")
    val chatId: String,
    val user: ChatUser,
    @SerializedName("last_message")
    val lastMessage: LastMessage?,
    @SerializedName("unread_count")
    val unreadCount: Int,
    // TC_022: true when the current user has blocked this chat partner. Backend already
    // returns this; surfaced so the chat list can show a "Blocked" indicator and keep the
    // conversation visible instead of it vanishing with no trace.
    @SerializedName("i_have_blocked_this_user")
    val iHaveBlockedThisUser: Boolean? = false,
    // True when the chat partner has blocked ME — lets a blocked user see an
    // indication in their own chat list instead of the row looking normal.
    @SerializedName("peer_blocked_me")
    val peerBlockedMe: Boolean? = false
)

data class ChatUser(
    val id: Int,
    val name: String,
    @SerializedName("user_gender")
    val userGender: String?,
    @SerializedName("avatar_id")
    val avatarId: Int?,
    val image: String?,
    val gender: String?,
    val language: String?,
    val age: Int?,
    val mobile: String?,
    val interests: String?,
    @SerializedName("describe_yourself")
    val describeYourself: String?,
    val voice: String?,
    val status: Int?,
    // Defaulted to 1 because the my_chat endpoint frequently omits these
    // fields entirely. Treating "missing" as enabled keeps the call buttons
    // tappable; the per-call availability check downstream still gates the
    // real connection flow if the creator is genuinely offline.
    @SerializedName("audio_status")
    val audioStatus: Int? = 1,
    @SerializedName("video_status")
    val videoStatus: Int? = 1,
    @SerializedName("coin_per_min_audio")
    val coinPerMinAudio: Int? = 10,
    @SerializedName("coin_per_min_video")
    val coinPerMinVideo: Int? = 60,
    // FEMALE_3_REJECT_BLOCK — true when this female viewer auto-blocked this male
    // (rejected 3x in 5 min). Distinct from a mutual user-block: drives a greyed
    // call button for the 60-min cooldown. Old backend omits it → defaults false.
    @SerializedName("call_blocked")
    val callBlocked: Boolean? = false
)

data class LastMessage(
    val id: Int,
    @SerializedName("chat_id")
    val chatId: String,
    @SerializedName("from_user_id")
    val fromUserId: Int,
    @SerializedName("to_user_id")
    val toUserId: Int,
    val message: String,
    @SerializedName("message_type")
    val messageType: String,
    @SerializedName("attachment_url")
    val attachmentUrl: String?,
    @SerializedName("is_read")
    val isRead: Boolean,
    @SerializedName("read_at")
    val readAt: String?,
    val timestamp: String,
    @SerializedName("created_at")
    val createdAt: String
)











