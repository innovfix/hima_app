package com.gmwapp.hima.retrofit.responses

data class PaySprintPanVerifyResponse(
    val statuscode: Int,
    val status: Boolean,
    val message: String,
    val reference_id: Long,
    val data: PaySprintPanData?
)

data class PaySprintPanData(
    val client_id: String,
    val pan_number: String,
    val full_name: String,
    val category: String
)


data class PaySprintBankVerifyResponse(
    val statuscode: Int,
    val status: Boolean,
    val message: String,
    val reference_id: Long,
    val data: BankVerifyData?
)

data class BankVerifyData(
    val mwtxnrefid: String,
    val reqdtls: BankReqDetails,
    val nwresmsg: String,
    val txnrefno: String,
    val nwrespcode: String,
    val c_name: String
)

data class BankReqDetails(
    val usrtxnrefno: String,
    val txntype: String,
    val chantxnrefno: String
)
