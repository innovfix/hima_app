package com.gmwapp.hima.retrofit.responses

data class UserAvatarResponse(
    val success: Boolean,
    val message: String,
    val data: UserAvatarData?
)

data class UserAvatarData(
    val id: Int,
    val name: String,
    val user_gender: String,
    val avatar_id: Int,
    val image: String
)

