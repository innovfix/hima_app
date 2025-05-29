package com.gmwapp.hima.retrofit.responses

data class RazorPayApiResponse(
    val reference_id: String,
    val short_url: String,
    val status: String
)



data class NewRazorpayLinkResponse(
    val success: Boolean,
    val message: String,
    val data: LinkData
)

data class LinkData(
    val short_url: String
)

