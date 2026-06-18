package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

/**
 * Friends-Gated Chat — response of POST chat_gate_status (user_id, peer_id).
 * The chat screen calls this on open to decide whether to lock the composer and which
 * CTA to show. The server is the source of truth for the per-sender gender+language
 * gate, so the app never re-implements the autopay/friends logic.
 */
data class ChatGateStatusResponse(
    @SerializedName("success") val success: Boolean = false,
    /** true => normal composer; false => locked. */
    @SerializedName("unlocked") val unlocked: Boolean = false,
    /** "autopay" (defer to the Subscribe gate) or "friends" (friendship lock). */
    @SerializedName("mode") val mode: String = "friends",
    /** autopay | need_autopay | friends | need_friend */
    @SerializedName("reason") val reason: String = "",
    /** none | sent | received | friends */
    @SerializedName("friend_status") val friendStatus: String = "none",
    /** send_friend_request | request_sent | accept_request | already_friends */
    @SerializedName("action") val action: String = "send_friend_request",
    @SerializedName("request_id") val requestId: Long? = null,
    @SerializedName("can_send_request") val canSendRequest: Boolean = false
)
