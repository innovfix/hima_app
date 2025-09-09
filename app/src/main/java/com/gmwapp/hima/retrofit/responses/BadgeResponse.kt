package com.gmwapp.hima.retrofit.responses

data class BadgeResponse(
    val success: Boolean,
    val message: String,
    val tips: String?,
    val data: List<BadgeData>?
)

data class BadgeData(
    val id: Int,
    val badge: String,
    val average_time: String,
    val audio: String,
    val video: String,
    val created_at: String,
    val updated_at: String
)
