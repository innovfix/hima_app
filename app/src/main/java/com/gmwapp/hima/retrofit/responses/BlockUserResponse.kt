package com.gmwapp.hima.retrofit.responses


data class BlockUserResponse(
    val success: Boolean,
    val message: String,
    val data: BlockedUserData
)

data class BlockedUserData(
    val user_id: String,
    val call_user_id: String,
    val blocked: String,
    val updated_at: String
)
