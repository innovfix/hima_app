package com.gmwapp.hima.retrofit.responses

data class FemaleDiscoveryResponse(
    val success: Boolean,
    val message: String,
    val data: List<FemaleDiscoveryCreator>?,
    val online_count: Int?
)

data class FemaleDiscoveryCreator(
    val name: String?,
    val avatar: String?
)
