package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class AiOnboardingStartResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("session_id") val sessionId: Int? = null,
    @SerializedName("ai_message") val aiMessage: String? = null,
    @SerializedName("step") val step: Int? = null
)
