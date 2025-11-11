package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class CategoriesResponse(
    val success: Boolean,
    val message: String,
    val total: Int,
    val data: ArrayList<CategoryData>?
)

data class CategoryData(
    val id: Int,
    val category: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)






