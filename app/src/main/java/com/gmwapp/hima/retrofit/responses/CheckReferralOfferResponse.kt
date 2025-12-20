package com.gmwapp.hima.retrofit.responses

data class CheckReferralOfferResponse(
    val success: Boolean,
    val show_dialog: Boolean,
    val offer_coins: Int,
    val offer_price: Int,
    val coin_id: Int?,
    val message: String
)

