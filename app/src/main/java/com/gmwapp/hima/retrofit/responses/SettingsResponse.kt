package com.gmwapp.hima.retrofit.responses

data class SettingsResponse(
    val success: Boolean,
    val message: String,
    val data: ArrayList<SettingsResponseData>?,
)

data class SettingsResponseData(
    val id: Int,
    val privacy_policy: String,
    val support_mail: String,
    val demo_video: String,
    val minimum_withdrawals: Int,
    val payment_gateway_type : String,
    val auto_disable_info : Any?
)