package com.gmwapp.hima.retrofit.responses

data class DailyClaimResponse(
    val success: Boolean,
    val message: String?,
    val data: DailyClaimData?
)

data class DailyClaimData(
    val coins_added: Int,
    val total_coins: Int
)
