package com.gmwapp.hima.retrofit.responses

data class DefaultCouponResponse(
    val success: Boolean,
    val message: String,
    val default_coupon_enabled: Boolean,
    val data: DefaultCouponData?
)

data class DefaultCouponData(
    val id: Int?,
    val coupon_code: String?,
    val save_price: String?,
    val valid: String?,
    val coins: Int?,
    val original_price: Int?,
    val discount_price: String?,
    val offer: String?,
    val type: String?,
    val coupon_name: String?,
    val updated_at: String?,
    val created_at: String?
)
