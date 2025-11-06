package com.gmwapp.hima.retrofit.responses

data class CallRejectCountResponse(
    val success: Boolean,
    val message: String,
    val data: CallRejectCountData?
)

data class CallRejectCountData(
    val male_user_id: String,
    val female_user_id: String,
    val rejecting_count: Int,
    val datetime: String
)

