package com.gmwapp.hima.retrofit.responses

data class AgoraTokenResponse(
    val success: Boolean,
    val message: String? = null,
    val token: String? = null,
    val app_id: String? = null,
    val channel_name: String? = null,
    val uid: Int? = null
)
