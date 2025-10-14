package com.gmwapp.hima.retrofit.responses

data class CallMaleUserResponse(
    val success: Boolean,
    val message: String,
    val data: CallMaleUserResponseData?,
)

data class CallMaleUserResponseData (
    val call_id: Int,
    val balance_time: String?,
    val audio_status: Int,
    val video_status: Int,
)



