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
    val cancelled_at: String?,
    // Server-computed eligibility for the ₹1 trial. Authoritative source —
    // the app's UserSegment.isNewUser reads only this. Null until the
    // backend deploy with the AutopayController response change lands;
    // SubscriptionStateCache treats null as false (fail-closed → old).
    val is_new_user: Boolean? = null
)
