package com.gmwapp.hima.retrofit.responses

data class ZohoMailResponse(
    val success: Boolean,
    val message: String,
    val data: List<ZohoMailItem>
)

data class ZohoMailItem(
    val id: Int,
    val language: String,
    val mail: String,
    val department: String,
    val updated_at: String,
    val created_at: String
)
