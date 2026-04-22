package com.gmwapp.hima.retrofit.responses

import com.google.gson.JsonElement

data class IcebreakerQuestionsResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: JsonElement? = null
)
