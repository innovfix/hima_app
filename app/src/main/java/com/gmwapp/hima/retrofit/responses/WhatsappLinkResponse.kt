package com.gmwapp.hima.retrofit.responses

data class WhatsappLinkResponse(
    val success: Boolean,
    val message: String,
    val data: List<WhatsappData>
)

data class WhatsappData(
    val id: Int,
    val language: String,
    val link: String,
    val updated_at: String,
    val created_at: String
)
