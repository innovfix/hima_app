package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class RatingPromptResponse(
    val success: Boolean,
    @SerializedName("should_show")
    val shouldShow: Boolean,
    @SerializedName("trigger_group")
    val triggerGroup: Int?,
    val message: String
)

data class UpdateRatingPromptResponse(
    val success: Boolean,
    val message: String
)

