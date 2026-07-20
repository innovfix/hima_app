package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class FriendRequestResponse(
    val success: Boolean,
    val message: String,
    val data: FriendRequestData?
)

data class FriendRequestData(
    val id: Int,
    val sender_id: Int,
    val receiver_id: Int,
    val status: String, // "pending", "accepted", "rejected"
    val created_at: String,
    val updated_at: String
)

// Response for getting friend list
data class FriendListResponse(
    val success: Boolean,
    val message: String,
    val count: Int,
    val data: List<FriendListData>?
)

data class FriendListData(
    val request_id: Int,
    val status: Int,
    val friends_since: String,
    val friend_data: FriendUserData
)

data class FriendUserData(
    val id: Int,
    val name: String,
    val mobile: String,
    val age: Int,
    val gender: String,
    val avatar_id: Int,
    val image: String,
    val language: String,
    val interests: String,
    val describe_yourself: String,
    val voice: String,
    val status: Int,
    val balance: Int,
    val online_status: String,
    // QA bug #8 — block state so the Friends screen can sink blocked friends to the
    // bottom, mirroring the chat list. Same serialized names the chat-list endpoint
    // (MyChatResponse.ChatItem) already returns. Nullable + default false: if the
    // backend hasn't been updated yet, these stay false and ordering is unchanged.
    @SerializedName("i_have_blocked_this_user")
    val iHaveBlockedThisUser: Boolean? = false,
    @SerializedName("peer_blocked_me")
    val peerBlockedMe: Boolean? = false
)

fun FriendListData.toFriendData(): FriendData {
    return FriendData(
        id = this.request_id,
        friend_id = this.friend_data.id,
        name = this.friend_data.name,
        image = this.friend_data.image,
        language = this.friend_data.language,
        audio_status = 0,
        video_status = 0,
        is_online = this.friend_data.online_status.isNotEmpty(),
        last_seen = this.friends_since,
        isBlocked = this.friend_data.iHaveBlockedThisUser == true,
        peerBlockedMe = this.friend_data.peerBlockedMe == true
    )
}

data class FriendData(
    val id: Int,
    val friend_id: Int,
    val name: String,
    val image: String,
    val language: String,
    val audio_status: Int,
    val video_status: Int,
    val is_online: Boolean,
    val last_seen: String?,
    var hasChatHistory: Boolean = false,  // Track if chat exists with this friend
    // QA bug #8 — sink blocked friends to the bottom of the Friends screen. Default
    // false so an un-upgraded backend (no block fields) leaves ordering untouched.
    val isBlocked: Boolean = false,
    val peerBlockedMe: Boolean = false
)

// Response for checking friend status
data class FriendStatusResponse(
    val success: Boolean,
    val message: String,
    val data: FriendStatusData?
)

data class FriendStatusData(
    val status: String, // "not_friends", "request_sent", "request_received", "friends"
    val request_id: Int? // If there's a pending request
)

