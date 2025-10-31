package com.gmwapp.hima.retrofit.responses

data class UserCallDurationResponse(
    val valid: Int,
    val success: Boolean,
    val message: String,
    val title: String,
    val duration_seconds: Int
)

