package com.gmwapp.hima.retrofit.responses

data class CheckReferralOfferResponse(
    val success: Boolean,
    val show_dialog: Boolean,
    // Gson can legitimately hand null for absent JSON fields; if we declared
    // these non-nullable Int, the deserialiser would throw
    // KotlinNullPointerException instead of surfacing the problem sanely.
    val offer_coins: Int?,
    val offer_price: Int?,
    val coin_id: Int?,
    val message: String?
)



