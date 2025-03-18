package com.gmwapp.hima.retrofit.responses

data class FcmNotificationResponse(
    val success: Boolean,
    val message: String,
    val response: ResponseData?,
    val data_sent: NotificationData?,
    val fcm_token: String?
)

data class ResponseData(
    val name: String
)

data class NotificationData(
    val senderId: String,
    val receiverId: String,
    val callType: String,
    val channelName: String,
    val message: String
)
