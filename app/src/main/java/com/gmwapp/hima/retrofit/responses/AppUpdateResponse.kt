package com.gmwapp.hima.retrofit.responses

data class AppUpdateResponse(
    val success: Boolean,
    val message: String,
    val data: ArrayList<AppUpdateModel>
)

data class AppUpdateModel(
    val id: Int,
    val link: String,
    val app_version: Int,
    val minimum_required_version: Int,
    val description: String,
    val bank:Int,
    val upi:Int
)


