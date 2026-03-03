package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class MyWarningsResponse(
    @SerializedName("success")
    val success: Boolean? = null,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("warning_system_image")
    val warningSystemImage: String? = null,

    @SerializedName("current_level_warning_data")
    val currentLevelWarningData: CurrentLevelWarningData? = null,

    @SerializedName("old_warning_data")
    val oldWarningData: List<MyWarningItem>? = emptyList(),
)

data class CurrentLevelWarningData(
    @SerializedName("level")
    val level: Int? = null,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("details")
    val details: String? = null,
)

data class MyWarningItem(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("warning_level")
    val warningLevel: Int? = null,

    @SerializedName("warning_level_title")
    val warningLevelTitle: String? = null,

    @SerializedName("warning_level_details")
    val warningLevelDetails: String? = null,

    @SerializedName("strict_warning_hours")
    val strictWarningHours: Int? = null,

    @SerializedName("suspension_days")
    val suspensionDays: Int? = null,

    @SerializedName("block_enabled")
    val blockEnabled: Boolean? = null,

    @SerializedName("withdrawal_disabled")
    val withdrawalDisabled: Boolean? = null,

    @SerializedName("date")
    val date: String? = null,
)

