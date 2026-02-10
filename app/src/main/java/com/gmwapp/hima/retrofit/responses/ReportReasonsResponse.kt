package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class ReportReasonsResponse(
    @SerializedName("success")
    val success: Boolean? = null,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("data")
    val data: List<ReportReason>? = emptyList(),
    
    @SerializedName("error")
    val error: String? = null
) {
    override fun toString(): String {
        return "ReportReasonsResponse(success=$success, message=$message, data=$data, error=$error)"
    }
}

data class ReportReason(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("requires_text")
    val requires_text: Int? = 0
) {
    override fun toString(): String {
        return "ReportReason(id=$id, reason=$reason, requires_text=$requires_text)"
    }
}
