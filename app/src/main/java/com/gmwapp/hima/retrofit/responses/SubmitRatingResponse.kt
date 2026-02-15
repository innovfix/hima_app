package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class SubmitRatingResponse(
    val success: Boolean,
    val message: String,
    val data: SubmitRatingData?
)

data class SubmitRatingData(
    val id: Int,
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("star_count")
    val starCount: Int,
    val description: String?,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)
