package com.gmwapp.hima.retrofit.responses

data class ToggleDndResponse(
    val success: Boolean,
    val message: String?,
    val data: ToggleDndData?
)

data class ToggleDndData(
    val dnd_enabled: Int,
    val dnd_until: String?
)
