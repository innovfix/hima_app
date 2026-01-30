package com.gmwapp.hima.retrofit.responses

data class ClaimFreeCoinsResponse(
    val success: Boolean,
    val message: String,
    val data: ClaimFreeCoinsData?
)

data class ClaimFreeCoinsData(
    val name: String,
    val coins_added: Int,
    val coins: String,
    val total_coins: String
)
