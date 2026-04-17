package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class UserConcernResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("has_concern") val hasConcern: Boolean? = false,
    @SerializedName("concern") val concern: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("quick_replies") val quickReplies: List<String>? = null
)
