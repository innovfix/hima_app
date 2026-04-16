package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class AiOnboardingCompleteResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("matched_creators") val matchedCreators: List<MatchedCreator>? = null,
    @SerializedName("messages_sent") val messagesSent: Boolean? = null
)

data class MatchedCreator(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("image") val image: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("audio_status") val audioStatus: Int? = 0,
    @SerializedName("video_status") val videoStatus: Int? = 0
)
