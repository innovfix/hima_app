package com.gmwapp.hima.retrofit.responses

data class CheckCouponCodeResponse(
    val success: Boolean,
    val message: String?,
    val data: List<CouponDetail>
)

data class CouponDetail(
    val id: Int,
    val coupon_code: String,
    val save_price: String,
    val valid: String,
    val coins: Int,
    val original_price: String,
    val discount_price: String,
    val offer: String
)