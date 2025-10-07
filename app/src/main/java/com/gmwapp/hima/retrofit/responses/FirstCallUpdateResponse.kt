package com.gmwapp.hima.retrofit.responses

data class FirstCallUpdateResponse(
    val success: Boolean,
    val message: String,
    val data: Data?
) {
    data class Data(
        val user_id: Int,
        val first_call: String
    )
}
