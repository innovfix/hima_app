package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class LogAppEventResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("event_id")
    val eventId: Int?
)

