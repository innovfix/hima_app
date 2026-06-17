package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class CheckBlockStatusResponse(
    @SerializedName("success")
    val success: Boolean? = null,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("data")
    val data: BlockStatusData? = null,
    
    @SerializedName("is_blocked")
    val isBlocked: Boolean? = null,
    
    @SerializedName("blocked_status")
    val blockedStatus: Int? = null
)

data class BlockStatusData(
    @SerializedName("is_blocked")
    val isBlocked: Boolean? = null,
    
    @SerializedName("blocked_status")
    val blockedStatus: Int? = null,  // 0 = unblocked, 2 = blocked

    // TC_027: true when the PEER (the creator being viewed) has blocked ME (the viewer).
    // Distinct from is_blocked/blocked_status, which describe whether I blocked them.
    @SerializedName("blocked_by_peer")
    val blockedByPeer: Boolean? = null,

    @SerializedName("block_id")
    val blockId: Int? = null,
    
    @SerializedName("user_id")
    val userId: Int? = null,
    
    @SerializedName("call_user_id")
    val callUserId: Int? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null
)
