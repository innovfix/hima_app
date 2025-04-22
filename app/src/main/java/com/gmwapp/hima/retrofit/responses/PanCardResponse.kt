package com.gmwapp.hima.retrofit.responses

data class PanCardResponse(
    val success: Boolean,
    val message: String,
    val data: PanCardData?
)

data class PanCardData(
    val id: Int,
    val name: String?,
    val mobile: String?,
    val pancard_name: String?,
    val pancard_number: String?,
    val balance: Int?,
    val image: String?
)

