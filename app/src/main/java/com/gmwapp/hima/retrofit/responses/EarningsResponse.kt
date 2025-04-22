package com.gmwapp.hima.retrofit.responses

data class EarningsResponse(
    val success: Boolean,
    val message: String,
    val data: ArrayList<EarningsResponseData>?,
)

data class EarningsResponseData(
    val id: Int,
    val user_id: Int,
    val amount: Int,
    val status: Int,
    val datetime: String,
    val reason: String,
)

