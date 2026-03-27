package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class BlockUserResponse(
    @SerializedName("success")
    val success: Boolean? = null,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("data")
    val data: BlockedUserData? = null
)

data class BlockedUserData(
    @SerializedName("user_id")
    val user_id: String? = null,

    @SerializedName("call_user_id")
    val call_user_id: String? = null,

    @SerializedName("blocked")
    val blocked: String? = null,

    @SerializedName("updated_at")
    val updated_at: String? = null
)
