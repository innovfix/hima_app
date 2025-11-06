package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

/**
 * Response model for create_ticket API
 * Expected response structure:
 * {
 *   "success": true,
 *   "message": "Ticket created successfully.",
 *   "data": {
 *     "id": 7,
 *     "user_id": "51",
 *     "message": "upi_id issue",
 *     "status": 0,
 *     "created_at": "2025-11-05 12:09:17",
 *     "screenshots": [
 *       "https://test.himaapp.in/storage/app/public/tickets/...",
 *       "https://test.himaapp.in/storage/app/public/tickets/..."
 *     ]
 *   }
 * }
 */
data class SubmitTicketResponse(
    val success: Boolean,
    val message: String,
    val data: CreateTicketData?
)

data class CreateTicketData(
    val id: Int,
    @SerializedName("user_id")
    val user_id: String,
    val message: String,
    val status: Int, // 0 = active/pending, 1 = resolved
    @SerializedName("created_at")
    val created_at: String,
    val screenshots: List<String>?
)

