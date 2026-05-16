package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class FemaleUsersResponse(
    val success: Boolean,
    val message: String,
    val data: ArrayList<FemaleUsersResponseData>?,
)

data class FemaleUsersResponseData(
    val id: Int,
    val name: String,
    val image: String,
    val language: String,
    val balance: Int,
    val interests: String,
    val audio_status: Int,
    val video_status: Int,
    val describe_yourself: String,
    val created_at: String,
    val verified_datetime: String?,
    val coin_per_min_audio: Int?,
    val coin_per_min_video: Int?,
    val is_star: Int?,
    val star: Int?,
    val ipl_team: String? = null,
    @SerializedName("last_message")
    val last_message: LastMessage? = null,
    @SerializedName("unread_count")
    val unread_count: Int = 0,
    // B095 — derived server-side from active `call_attend` rows (LEFT JOIN
    // where ended_time IS NULL within the last 30 minutes). When the
    // creator is currently in a call this is 1; otherwise 0. Forward-
    // compatible: old backend builds omit the field → Gson defaults to 0
    // → adapter renders her as available (current behavior). Once the
    // backend ships the field, busy creators are visually distinct
    // without any Android update.
    @SerializedName("is_busy")
    val is_busy: Int? = 0,
)