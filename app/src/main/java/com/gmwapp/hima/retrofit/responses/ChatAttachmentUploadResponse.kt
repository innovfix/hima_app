package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class ChatAttachmentUploadResponse(
    val success: Boolean,
    val message: String? = null,
    val data: ChatAttachmentUploadData? = null
)

data class ChatAttachmentUploadData(
    @SerializedName("url")
    val url: String? = null,
    /** Present when upload fails because one/both users are below the media-capable app version. */
    @SerializedName("required_min_version")
    val requiredMinVersion: Int? = null
)
