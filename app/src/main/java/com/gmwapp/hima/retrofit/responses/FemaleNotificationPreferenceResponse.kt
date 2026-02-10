package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class FemaleNotificationPreferenceRequest(
    @SerializedName("male_user_id")
    val maleUserId: Int,
    @SerializedName("female_user_id")
    val femaleUserId: Int,
    @SerializedName("status")
    val status: Int
)

data class FemaleNotificationPreferenceResponse(
    @SerializedName("success")
    val success: Boolean? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("data")
    val data: FemaleNotificationPreferenceData? = null
)

data class FemaleNotificationPreferenceData(
    @SerializedName("male_user_id")
    val maleUserId: Int? = null,
    @SerializedName("female_user_id")
    val femaleUserId: Int? = null,
    @SerializedName("status")
    val status: Int? = null
)
