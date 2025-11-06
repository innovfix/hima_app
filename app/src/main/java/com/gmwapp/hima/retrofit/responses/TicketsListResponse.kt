package com.gmwapp.hima.retrofit.responses

/**
 * Response model for tickets_list API
 * Expected response structure:
 * {
 *   "success": true,
 *   "message": "Tickets list retrieved successfully.",
 *   "total": 1,
 *   "data": [
 *     {
 *       "id": 9,
 *       "user_id": 51,
 *       "message": "upi_id issue",
 *       "reply": "",
 *       "status": 0,
 *       "created_at": "2025-11-05 12:11:14",
 *       "updated_at": "2025-11-05 12:11:14",
 *       "screenshots": [
 *         "https://test.himaapp.in/storage/app/public/tickets/...",
 *         "https://test.himaapp.in/storage/app/public/tickets/...",
 *         "https://test.himaapp.in/storage/app/public/tickets/..."
 *       ]
 *     }
 *   ]
 * }
 */
data class TicketsListResponse(
    val success: Boolean,
    val message: String,
    val total: Int,
    val data: List<TicketDataResponse>?
)

data class TicketDataResponse(
    val id: Int,
    val user_id: Int,
    val message: String,
    val screenshot: String? = null, // Legacy single screenshot field
    val screenshots: List<String>? = null, // New multiple screenshots array
    val reply: String? = null, // Can be null if no reply yet
    val status: Int, // 0 = active/pending, 1 = resolved
    val created_at: String,
    val updated_at: String
)

