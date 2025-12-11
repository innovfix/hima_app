package com.gmwapp.hima.retrofit.responses

data class CheckCallAvailabilityResponse(
    val success: Boolean,
    val message: String,
    val data: CallAvailabilityData?
)

data class CallAvailabilityData(
    val male_user_id: Int,
    val female_user_id: Int,
    val is_blocked: Boolean,
    val audio_status: Int,
    val video_status: Int
)
