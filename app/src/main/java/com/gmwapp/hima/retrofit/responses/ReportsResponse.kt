package com.gmwapp.hima.retrofit.responses

data class ReportsResponse(
    val success: Boolean,
    val message: String,
    val data: List<ReportData>
)

data class ReportData(
    val user_name: String,
    val today_calls: Int,
    val today_earnings: String,
    // Split-earnings: today's gift income, separate from today_earnings (call income).
    // Old backend omits it → null → app shows ₹0.
    val today_gift_earnings: String? = null,
    val first_call: Int,
    val call_rates: String?
)
