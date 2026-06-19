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
    // Default 0 so older backends that don't return this field still parse.
    val favourites_count: Int = 0
)
