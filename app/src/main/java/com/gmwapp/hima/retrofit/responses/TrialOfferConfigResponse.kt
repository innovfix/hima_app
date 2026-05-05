package com.gmwapp.hima.retrofit.responses

data class TrialOfferConfigResponse(
    val success: Boolean,
    val message: String?,
    val data: TrialOfferConfigData?
)

data class TrialOfferConfigData(
    val youtube_url: String?,
    val headline: String?,
    // Per-language admin overrides for the BottomSheetTrialOffer copy.
    // Null/blank => app keeps the hardcoded XML default for that line,
    // so admins can override only the lines they care about.
    val price_text: String?,
    val feature_1_text: String?,
    val feature_2_text: String?,
    val footer_text: String?,
    val cta_text: String?,
    val secondary_link_text: String?,
    val updated_at: String?
)
