package com.gmwapp.hima.retrofit.responses

data class FemaleTransactionsResponse(
    val success: Boolean,
    val message: String,
    val data: ArrayList<FemaleTransactionsResponseData>?,
)

data class FemaleTransactionsResponseData(
    val id: Int,
    val user_id: Int,
    val type: String, // audio, video, referral, gift_received
    val amount: Double?,
    val coins: Double,
    val payment_type: String?,
    val datetime: String,
    val date: String,
    val title: String?, // Full string for audio/video sessions (e.g., "Audio session with username")
    val description: String?, // Additional description or duration info
    val started_time: String? = null, // FI_05: actual call start time (for call_income rows)
)

