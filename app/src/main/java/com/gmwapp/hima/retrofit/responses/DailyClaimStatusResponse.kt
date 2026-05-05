package com.gmwapp.hima.retrofit.responses

data class DailyClaimStatusResponse(
    val success: Boolean,
    val message: String?,
    val data: DailyClaimStatusData?
)

data class DailyClaimStatusData(
    val can_claim: Boolean,
    val claim_amount: Int,
    val already_claimed_today: Boolean,
    val reason: String? // "available" | "already_claimed" | "subscription_inactive"
)
