package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class CreatorWarningsResponse(
    @SerializedName("success")
    val success: Boolean? = null,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("has_warnings")
    val hasWarnings: Boolean? = null,
    
    @SerializedName("warning_count")
    val warningCount: Int? = 0,

    @SerializedName("warning_status_message")
    val warningStatusMessage: String? = null,
    
    @SerializedName("is_blocked")
    val isBlocked: Int? = 0,
    
    @SerializedName("blocked_message")
    val blockedMessage: String? = null,
    
    @SerializedName("data")
    val data: List<WarningItem>? = emptyList()
)

data class WarningItem(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("reason")
    val reason: String? = null,
    
    @SerializedName("admin_notes")
    val adminNotes: String? = null,
    
    @SerializedName("action_type")
    val actionType: String? = null,
    
    @SerializedName("action_date")
    val actionDate: String? = null,
    
    @SerializedName("admin_name")
    val adminName: String? = null
)
