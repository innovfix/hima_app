package com.gmwapp.hima.retrofit.responses

data class SubscriptionStatusResponse(
    val success: Boolean,
    val message: String?,
    val data: SubscriptionStatusData?
)

data class SubscriptionStatusData(
    val is_active: Boolean,
    val ever_active: Boolean,
    val status: String, // "none" | "pending" | "active" | "cancelled" | "failed"
    val next_billing_date: String?,
    val cancelled_at: String?
)
