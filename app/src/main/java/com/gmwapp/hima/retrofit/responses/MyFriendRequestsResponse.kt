package com.gmwapp.hima.retrofit.responses

data class MyFriendRequestsResponse(
    val success: Boolean,
    val message: String,
    val count: Int,
    val data: ArrayList<MyFriendRequestData>?
)

data class MyFriendRequestData(
    val request_id: Int,
    val sender_id: Int,
    val receiver_id: Int,
    val status: Int,
    val created_at: String,
    val receiver_data: ReceiverData
)

data class ReceiverData(
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

// Extension function to convert MyFriendRequestData to FriendData for adapter compatibility
fun MyFriendRequestData.toFriendData(): FriendData {
    return FriendData(
        id = this.request_id,
        friend_id = this.receiver_data.id,
        name = this.receiver_data.name,
        image = this.receiver_data.image,
        language = this.receiver_data.language,
        audio_status = 1, // Default, can be updated if API provides
        video_status = 1, // Default, can be updated if API provides
        is_online = false, // Default, can be updated if needed
        last_seen = this.created_at
    )
}

