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


data class IndividualAppUpdateResponse(
    val success: Boolean,
    val message: String,
    val data: AppUpdateData
)

data class AppUpdateData(
    val user_id: Int,
    val user_name: String,
    val current_version: String,
    val minimum_version: String,
    val update_type: String,
    val link: String,
    val description: String,
)


