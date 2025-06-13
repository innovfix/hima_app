package com.gmwapp.hima.retrofit.responses

data class FemaleUsersResponse(
    val success: Boolean,
    val message: String,
    val data: ArrayList<FemaleUsersResponseData>?,
)

data class FemaleUsersResponseData(
    val id: Int,
    val name: String,
    val image: String,
    val language: String,
    val balance: Int,
    val interests: String,
    val audio_status: Int,
    val video_status: Int,
    val describe_yourself: String,
    val created_at: String,
)