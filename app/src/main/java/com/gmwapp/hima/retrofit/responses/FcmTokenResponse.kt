package com.gmwapp.hima.retrofit.responses

data class FcmTokenResponse(
    val success: Boolean,
    val message: String,
    val data: TokenData?
)

data class TokenData(
    val id: Int,
    val user_id: Int,
    val token: String,
    val created_at: String,
    val updated_at: String
)

