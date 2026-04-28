package com.gmwapp.hima.retrofit.responses

data class AutopayInitiateResponse(
    val success: Boolean,
    val message: String?,
    val data: AutopayInitiateData?
)

data class AutopayInitiateData(
    val subscription_id: Long?,
    val cashfree_subscription_id: String?,
    val plan_type: String?, // "trial_new" | "direct_old"
    val cashfree_session_id: String?,
    val redirect_url: String?,
    val enabled_feature: String? = null // populated when autopay rejected for language
)
