package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class SettingsResponse(
    val success: Boolean,
    val message: String,
    val data: ArrayList<SettingsResponseData>?,
)

data class SettingsResponseData(
    val id: Int,
    val privacy_policy: String,
    val terms_conditions: String,
    val guideline: String,
    val refund_cancellation: String,
    val support_mail: String,
    val demo_video: String,
    val minimum_withdrawals: Int,
    val payment_gateway_type : String,
    @SerializedName("auto_disable_info ") // Notice the space at the end
    val auto_disable_info: String?,

    @SerializedName("block_words\t")
    val block_words_raw: String?,
    
    val audio_income: Int?,
    val video_income: Int?,
    // Missed-call notification remote kill-switch (admin-controlled). 0/absent = OFF
    // (no missed-call notifications shown anywhere in the app); 1 = ON. Ships OFF;
    // flip the settings_list value to 1 to re-enable without a new APK. Read offline
    // from DPreferences by CallNotifications.showMissed. See MEMORY: missed-call remote flag.
    val missed_call_notifications_enabled: Int? = 0,
    // Icebreaker feature toggles (admin-controlled). Default master off; audio/video on.
    val icebreaker_enabled: Int? = 0,
    val icebreaker_audio_enabled: Int? = 1,
    val icebreaker_video_enabled: Int? = 1,
    // F1 Call Duration Bonus config (admin-controlled). DISPLAY ONLY — money is credited
    // server-side; the app just uses this to decide which popups to show and when.
    val call_bonus: CallBonusConfig? = null
) {
    // Computed property to get List<String>
    val blockWords: List<String>
        get() = block_words_raw
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.split(",")
            ?.map { it.trim().trim('\'', '"') }
            ?: emptyList()
}

// F1 Call Duration Bonus — admin config mirrored from settings_list.call_bonus.
data class CallBonusConfig(
    val master: Int? = 0,
    val audio_enabled: Int? = 0,
    val video_enabled: Int? = 0,
    val teaser_lead_seconds: Int? = 60,
    val audio_tiers: List<BonusTier>? = null,
    val video_tiers: List<BonusTier>? = null
)

// One milestone: `min` = minutes into the call, `amt` = rupees shown/credited.
data class BonusTier(
    val min: Int = 0,
    val amt: Double = 0.0
)
