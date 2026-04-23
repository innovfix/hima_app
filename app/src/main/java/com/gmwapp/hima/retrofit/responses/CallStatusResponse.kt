package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class CallStatusRequest(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("received_user_id") val receivedUserId: Int,
    @SerializedName("call_id") val callId: Int,
    @SerializedName("end_reason") val endReason: String,
    @SerializedName("ended_by") val endedBy: String,
    @SerializedName("ended_by_user_id") val endedByUserId: Int? = null,
    @SerializedName("duration_seconds") val durationSeconds: Int? = null,
)

data class CallStatusResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val data: CallStatusData? = null,
)

data class CallStatusData(
    @SerializedName("call_id") val callId: Int? = null,
    @SerializedName("end_reason") val endReason: String? = null,
    @SerializedName("ended_by_user_id") val endedByUserId: Int? = null,
    @SerializedName("update_current_endedtime") val updateCurrentEndedTime: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Int? = null,
    @SerializedName("already_recorded") val alreadyRecorded: Boolean? = null,
)

object CallEndReason {
    const val ENDED = "ended"
    const val REJECTED = "rejected"
    const val NOT_ANSWERED = "not_answered"
    const val FAILED = "failed"
}

object CallEndedBy {
    const val CALLER = "caller"
    const val RECEIVER = "receiver"
    const val SYSTEM = "system"
}
