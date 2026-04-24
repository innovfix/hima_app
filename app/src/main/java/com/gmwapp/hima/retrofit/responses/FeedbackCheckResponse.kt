package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class FeedbackCheckResponse(
    val success: Boolean,
    @SerializedName("should_show") val shouldShow: Boolean,
    val form: FeedbackFormData?,
    val message: String?,
)

data class FeedbackFormData(
    val id: Int,
    val title: String,
    val description: String?,
    @SerializedName("allow_skip") val allowSkip: Boolean,
    val questions: List<FeedbackQuestion>,
)

data class FeedbackQuestion(
    val id: Int,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("question_text") val questionText: String,
    @SerializedName("help_text") val helpText: String?,
    @SerializedName("question_type") val questionType: String,
    @SerializedName("is_required") val isRequired: Boolean,
    @SerializedName("min_rating") val minRating: Int?,
    @SerializedName("max_rating") val maxRating: Int?,
    @SerializedName("max_length") val maxLength: Int?,
    val options: List<String>?,
)

object FeedbackQuestionType {
    const val RATING = "rating"
    const val TEXT = "text"
    const val SINGLE = "single_choice"
    const val MULTI = "multi_choice"
}
