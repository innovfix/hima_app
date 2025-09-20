package com.gmwapp.hima.retrofit.responses

data class BadgeResponse(
    val success: Boolean,
    val message: String,
    val tips: String?,
    val average_duration: Int,
    val matched_badge: MatchedBadge?,
    val badges: List<BadgeData>?
)

data class MatchedBadge(
    val id: Int,
    val badge: String,
    val average_time: String,
    val audio: String,
    val video: String
)

data class BadgeData(
    val id: Int,
    val badge: String,
    val average_time: String,
    val audio: String,
    val video: String,
    val created_at: String,
    val updated_at: String,
    val is_matched: Boolean
)
