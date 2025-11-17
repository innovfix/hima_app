package com.gmwapp.hima.retrofit.responses

import com.gmwapp.hima.models.Chat

data class ChatListResponse(
    val status: Boolean,
    val message: String?,
    val data: ChatListData?
)

data class ChatListData(
    val chats: List<Chat>,
    val current_page: Int,
    val last_page: Int
)

