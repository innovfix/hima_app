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
    val block_words_raw: String?
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
