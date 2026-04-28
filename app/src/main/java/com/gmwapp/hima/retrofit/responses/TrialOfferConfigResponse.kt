package com.gmwapp.hima.retrofit.responses

data class TrialOfferConfigResponse(
    val success: Boolean,
    val message: String?,
    val data: TrialOfferConfigData?
)

data class TrialOfferConfigData(
    val youtube_url: String?,
    val headline: String?,
    val updated_at: String?
)
