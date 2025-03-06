package com.gmwapp.hima

data class Coupon(
    val id: String,
    val offer: String,
    val couponCode: String,
    val saveAmount: String,
    val validity: String,
    val originalPrice: String,

    val discountedPrice: String,
    val coins: String,
)



