package com.gmwapp.hima.retrofit.responses

data class GetFemaleTalkDurationResponse(
    val success: Boolean,
    val message: String?,
    val data: TalkDurationData?
)

data class TalkDurationData(
    val user_id: Int,
    val total_talk_duration_minutes: Int
)

