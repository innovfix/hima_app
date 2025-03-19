package com.gmwapp.hima.retrofit.responses

data class TransactionsResponse(
    val success: Boolean,
    val message: String,
    val data: ArrayList<TransactionsResponseData>?,
)

data class TransactionsResponseData(
    val id: Int,
    val user_id: Int,
    val type: String,
    val amount: Int,
    val coins: Int,
    val payment_type: String,
    val datetime: String,
    val user_name: String,
    val date: String,
    val call_type: String,
    val duration: String,
    val call_user_name: String,

)

