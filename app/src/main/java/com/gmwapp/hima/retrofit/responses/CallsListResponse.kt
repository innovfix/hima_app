package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class CallsListResponse(
    val success: Boolean,
    val message: String,
    val total: Int? = null,
    val data: ArrayList<CallsListResponseData>?,
)

data class CallsListResponseData(
    val id: Int,
    val name: String,
    val date: String,
    val image: String,
    val started_time: String,
    val duration: String,
    val income: String,
    val audio_status: Int,
    val video_status: Int,
    val blocked: Int,
    val language: String = "",
    val interests: String = "",
    val describe_yourself: String = "",
    // FI_06: 'rejected' marks a call declined while ringing (vs an unanswered "missed").
    // Defaults to "" so older backend responses that omit the key stay compatible.
    // @SerializedName pins the JSON key (sibling CallStatusResponse convention) so the
    // mapping survives any future field-name obfuscation.
    @SerializedName("end_reason")
    val end_reason: String = "",
    // FEMALE_3_REJECT_BLOCK — true when this female viewer auto-blocked this male
    // (rejected 3x in 5 min). Greys the callback button in Recents for the 60-min
    // cooldown. Old backend omits it → defaults false.
    @SerializedName("call_blocked")
    val call_blocked: Boolean? = false,
)