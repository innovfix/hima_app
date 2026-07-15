package com.gmwapp.hima.retrofit.responses

data class FriendTabsCountsResponse(
    val success: Boolean,
    val message: String,
    val data: FriendTabsCountsData?
)

data class FriendTabsCountsData(
    val user_id: Int,
    val chats_count: Int,
    val friends_count: Int,
    val my_requests_count: Int,
    val received_requests_count: Int,
    /**
     * B_010: pending requests newer than the watermark this client sent. This is what the
     * bottom-nav badge counts — the total above is what the Requests tab chip shows.
     * Defaults to -1 so a server that predates this field is detectable; call sites fall
     * back to [received_requests_count] rather than silently badging 0.
     */
    val received_requests_new_count: Int = -1,
    // Default 0 so older backends that don't return this field still parse.
    val favourites_count: Int = 0
)
