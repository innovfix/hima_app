package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class ListCreatorOnlineNotificationsRequest(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("only_online") val onlyOnline: Int? = null,
    @SerializedName("search") val search: String? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("offset") val offset: Int? = null
)

data class ListCreatorOnlineNotificationsResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: CreatorOnlineListData? = null
)

data class CreatorOnlineListData(
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("creators") val creators: List<CreatorOnlineRow>? = null,
    @SerializedName("total") val total: Int? = null
)

data class CreatorOnlineRow(
    @SerializedName("creator_id") val creatorId: Int,
    @SerializedName("trigger_reason") val triggerReason: String? = null,
    @SerializedName("is_online") val isOnline: Int? = null,
    @SerializedName("user") val user: CreatorOnlineUser? = null
)

data class CreatorOnlineUser(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("audio_status") val audioStatus: Int? = null,
    @SerializedName("video_status") val videoStatus: Int? = null
)
