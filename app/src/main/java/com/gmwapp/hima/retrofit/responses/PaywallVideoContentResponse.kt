package com.gmwapp.hima.retrofit.responses

data class PaywallVideoContentResponse(
    val success: Boolean,
    val message: String,
    val data: PaywallVideoContentData? = null
)

data class PaywallVideoContentData(
    val id: Int? = null,
    val user_id: Int? = null,
    val language: String? = null,
    val text_one: String? = null,
    val text_three: String? = null,
    val youtube_video_link: String? = null,
    val coin_id: Int? = null,
    val coin_amount: Int? = null,
    val coin_value: Int? = null,
    val coin: Int? = null
)
