package com.gmwapp.hima.retrofit.responses

data class TrialOfferConfigResponse(
    val success: Boolean,
    val message: String?,
    val data: TrialOfferConfigData?
)

data class TrialOfferConfigData(
    // Absolute URL of an admin-uploaded mp4. Preferred field — new APKs
    // play this via VideoView. Null = no video uploaded for this language.
    val video_url: String?,
    // Legacy YouTube link kept on the server for one transition release.
    // New APKs ignore this field; only video_url drives playback.
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
