package com.gmwapp.hima.retrofit.responses

data class CouponPriceResponse(
    val success: Boolean,
    val message: String,
    val data: CouponPriceData
)

data class CouponPriceData(
    val price: Int,

)