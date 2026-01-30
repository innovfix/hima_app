package com.gmwapp.hima.retrofit.responses

data class FreeCoinsStatusResponse(
    val success: Boolean,
    val enabled: Boolean,
    val value: Int? = null,
    val makePayment: Int? = null,
    val coin_id: Int? = null,
    val price: Int? = null,
    val badge_text: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val button_text: String? = null
)
