package com.gmwapp.hima.retrofit.responses

data class TransactionChargesResponse(
    val success: Boolean,
    val message: String,
    val tds_percentage: Double,

    val data: List<TransactionCharge>
)

data class TransactionCharge(
    val id: Int,
    val min_amount: Int,
    val max_amount: Int?,   // nullable because API can return null
    val deduction_charge: Int,
    val created_at: String,
    val updated_at: String
)