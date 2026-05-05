package com.gmwapp.hima.retrofit.responses

data class LanguageConfigResponse(
    val success: Boolean,
    val message: String?,
    val data: LanguageConfigData?
)

data class LanguageConfigData(
    val language: String,
    val enabled_feature: String, // "autopay" | "ai_onboarding" | "none"
    val autopay_enabled: Boolean,
    val ai_onboarding_enabled: Boolean,
    // Per-language admin toggle that decides whether lapsed users in this
    // language are allowed to re-subscribe through the in-app sheets.
    // Nullable to stay backwards-compatible with older backend responses
    // missing the field — read site treats null as true (allow re-subscribe).
    val re_subscription_enabled: Boolean? = null
)
