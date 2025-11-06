package com.gmwapp.hima.retrofit.responses

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class SubqueriesResponse(
    val success: Boolean,
    val message: String,
    val total: Int,
    val data: ArrayList<SubqueryData>?
)

@Parcelize
data class SubqueryData(
    val id: Int,
    @SerializedName("category_id")
    val categoryId: Int,
    val title: String,
    val description: String,
    @SerializedName("video_link")
    val videoLink: String?,
    val image: String?,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
) : Parcelable

