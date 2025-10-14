package com.gmwapp.hima.retrofit.responses

data class MessageNotificationResponse(
    val success: Boolean,
    val message: String,
    val sender_id: String?,
    val receiver_id: String?,
    val response_status: Int?
)

