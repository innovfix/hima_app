package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class AiOnboardingReplyResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("ai_message") val aiMessage: String? = null,
    @SerializedName("step") val step: Int? = null,
    @SerializedName("is_complete") val isComplete: Boolean? = null
)
