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
    val ai_onboarding_enabled: Boolean
)
