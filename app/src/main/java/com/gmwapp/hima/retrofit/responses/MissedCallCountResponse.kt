package com.gmwapp.hima.retrofit.responses

data class MissedCallCountResponse(
    val success: Boolean,
    val message: String,
    val data: MissedCallCountData?
)

data class MissedCallCountData(
    val user_id: Int,
    val missed_call_count: Int,
    val mode: String? = null,
    val seen_upto: String? = null
)
