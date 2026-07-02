package com.gmwapp.hima.retrofit.responses

data class IcebreakerQuestionsResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: List<IcebreakerQuestion>? = null
)

data class IcebreakerQuestion(
    val id: Int,
    val question: String
)
