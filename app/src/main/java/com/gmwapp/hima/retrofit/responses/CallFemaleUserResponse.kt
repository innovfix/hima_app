package com.gmwapp.hima.retrofit.responses

data class CallFemaleUserResponse(
    val success: Boolean,
    val message: String,
    val data: CallFemaleUserResponseData?,
    val coin_status: Int? = null,
)
data class CallFemaleUserResponseData (
    val call_id: Int,
    val balance_time: String?,
    val audio_status: Int,
    val video_status: Int,
    )


