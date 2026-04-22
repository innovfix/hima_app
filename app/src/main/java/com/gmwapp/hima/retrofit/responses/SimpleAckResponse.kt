package com.gmwapp.hima.retrofit.responses

/**
 * Generic success/message envelope for endpoints that just need to ack an action
 * without returning data (e.g. `delete_chat_message`). Both fields are nullable
 * because older backend routes historically omitted `success`, and we want the
 * wrapper to keep parsing.
 */
data class SimpleAckResponse(
    val success: Boolean? = null,
    val message: String? = null
)
