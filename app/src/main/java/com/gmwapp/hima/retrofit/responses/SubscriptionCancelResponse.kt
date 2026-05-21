package com.gmwapp.hima.retrofit.responses

data class SubscriptionCancelResponse(
    val success: Boolean,
    val message: String?,
    val data: SubscriptionCancelData?
)

data class SubscriptionCancelData(
    val cancelled_at: String?
)
