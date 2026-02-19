package com.gmwapp.hima.retrofit.responses

data class CallDropStatusRequest(
    val user_id: Int,
    val received_user_id: Int,
    val call_id: Int,
    val call_drop_status: Int,
)

data class CallDropStatusResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val data: CallDropStatusData? = null,
)

data class CallDropStatusData(
    val id: Int? = null,
    val user_id: Int? = null,
    val received_user_id: Int? = null,
    val call_id: Int? = null,
    val call_drop_status: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

