package com.gmwapp.hima.retrofit.responses

data class GiftImageResponse(
    val success: Boolean,
    val message: String,
    val data: List<GiftData>
)
data class GiftData(
    val id: Int,
    val gift_icon: String,
    val coins: Int,
    val updated_at: String,
    val created_at: String,
    // Feature 2 (customizable gifts) — nullable so legacy/older responses still parse.
    val name: String? = null,
    val earning: Double? = null,
    val show_name: Boolean? = null
)
