package com.gmwapp.hima.verification

data class PanRequest(
    val pan: String,
    val name: String
)

data class PanResponse(
    val success: Boolean,
    val message: String?,
    val pan: String?,
    val name: String?
)
