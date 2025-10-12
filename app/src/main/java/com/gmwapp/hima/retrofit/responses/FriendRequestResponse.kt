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
    val data: ArrayList<FriendData>?
)

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

