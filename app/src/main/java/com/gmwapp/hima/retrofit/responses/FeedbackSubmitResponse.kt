package com.gmwapp.hima.retrofit.responses

data class FeedbackSubmitResponse(
    val success: Boolean,
    val message: String?,
)

// Parsed out of HTTP 400 error bodies on /submit — `errors` maps question_id (as string) to reason.
data class FeedbackErrorResponse(
    val success: Boolean,
    val message: String?,
    val errors: Map<String, String>?,
)
