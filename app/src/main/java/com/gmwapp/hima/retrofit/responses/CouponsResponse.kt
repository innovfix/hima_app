package com.gmwapp.hima.retrofit.responses

data class CouponsResponse(
    val success: Boolean,
    val message: String,
    val data: List<CouponData>?
)

data class CouponData(
    val id: Int,
    val coupon_code: String,
    val save_price: String,
    val valid: String,
    val coins: Int,
    val original_price: Int,
    val discount_price: Int,
    val offer: String,
    val type: String,
    val coupon_name: String,
    val updated_at: String,
    val created_at: String
)