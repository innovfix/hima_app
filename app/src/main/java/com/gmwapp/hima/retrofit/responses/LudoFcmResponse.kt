package com.gmwapp.hima.retrofit.responses

data class LudoFcmResponse(
    val status: Boolean,
    val message: String,
    val code: String? = null,
    val data: LudoFcmData? = null
)

data class LudoFcmData(
    val invite_id: String? = null,
    val room_code: String? = null,
    val expires_in_seconds: Int? = null,
    val deep_link: String? = null,
    val join_url: String? = null
)
