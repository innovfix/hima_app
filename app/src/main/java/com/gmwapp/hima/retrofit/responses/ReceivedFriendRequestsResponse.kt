package com.gmwapp.hima.retrofit.responses

data class ReceivedFriendRequestsResponse(
    val success: Boolean,
    val message: String,
    val count: Int,
    val data: ArrayList<ReceivedFriendRequestData>?
)

data class ReceivedFriendRequestData(
    val request_id: Int,
    val sender_id: Int,
    val receiver_id: Int,
    val status: Int,
    val created_at: String,
    val sender_data: SenderData
)

data class SenderData(
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
    val status: Int
)

// Extension function to convert ReceivedFriendRequestData to FriendData for adapter compatibility
fun ReceivedFriendRequestData.toFriendData(): FriendData {
    return FriendData(
        id = this.request_id,
        friend_id = this.sender_data.id,
        name = this.sender_data.name,
        image = this.sender_data.image,
        language = this.sender_data.language,
        audio_status = 1, // Default, can be updated if API provides
        video_status = 1, // Default, can be updated if API provides
        is_online = false, // Default, can be updated if needed
        last_seen = this.created_at
    )
}










