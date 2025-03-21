package com.gmwapp.hima.retrofit.responses

data class AddCoinsResponse(
    val success: Boolean,
    val message: String,
    val data: AddCoinsData,
)

data class AddCoinsData(
    val name: String,
    val coins: String,
    val total_coins: String,
)
