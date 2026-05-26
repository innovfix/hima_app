package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

/**
 * Response for POST /api/auth/notification_clicked. The backend's
 * NotificationAnalyticsController::trackClick always returns 200 with
 * success+reason+match, so callers should treat this as fire-and-forget
 * for analytics — no UI is gated on it.
 */
data class NotificationClickResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("match") val match: String? = null,
    @SerializedName("updated") val updated: Int? = null,
    @SerializedName("notification_log_id") val notificationLogId: Long? = null
)
