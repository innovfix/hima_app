package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class GetFemaleNotificationPreferenceRequest(
    @SerializedName("male_user_id")
    val maleUserId: Int,
    @SerializedName("female_user_id")
    val femaleUserId: Int
)

data class GetFemaleNotificationPreferenceResponse(
    @SerializedName("success")
    val success: Boolean? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("data")
    val data: GetFemaleNotificationPreferenceData? = null
)

data class GetFemaleNotificationPreferenceData(
    @SerializedName("male_user_id")
    val maleUserId: Int? = null,
    @SerializedName("female_user_id")
    val femaleUserId: Int? = null,
    @SerializedName("status")
    val status: Int? = 0
)
