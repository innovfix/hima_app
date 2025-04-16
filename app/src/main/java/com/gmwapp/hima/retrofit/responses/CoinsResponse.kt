package com.gmwapp.hima.retrofit.responses

data class CoinsResponse(
    val success: Boolean,
    val message: String,
    val banner_image: String,
    val data: ArrayList<CoinsResponseData>?,
)

data class CoinsResponseData(
    val id: Int,
    val price: Int,
    val actual_price: Int,
    val coins: Int,
    val save: Int?,
    val popular: Int?,
    val updated_at: String?,
    val created_at: String?,
    var isSelected: Boolean?
)



