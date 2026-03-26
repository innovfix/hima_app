package com.gmwapp.hima.retrofit.responses

data class FirstInstallResponse(
    val success: Boolean,
    val message: String,
    val saved_address: String? = null,
    val ip_address: String? = null
)
