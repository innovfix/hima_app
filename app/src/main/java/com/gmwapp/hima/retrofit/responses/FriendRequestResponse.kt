package com.gmwapp.hima.retrofit.responses

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
    val online_status: String
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
        last_seen = this.friends_since
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
    val last_seen: String?
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

