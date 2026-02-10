package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class ReportUserResponse(
    @SerializedName("success")
    val success: Boolean? = null,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("data")
    val data: Any? = null,
    
    @SerializedName("error")
    val error: String? = null,
    
    @SerializedName("status")
    val status: Int? = null
) {
    override fun toString(): String {
        return "ReportUserResponse(success=$success, message=$message, data=$data, error=$error, status=$status)"
    }
}
