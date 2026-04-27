package com.gmwapp.hima.retrofit.responses

data class UpdateActiveStatusResponse(
    val success: Boolean,
    val message: String,
    val data: UpdateActiveStatusData?
)

data class UpdateActiveStatusData(
    val user_id: Int,
    val datetime: String?,
    val updated: Boolean
)
