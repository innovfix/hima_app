package com.gmwapp.hima.retrofit.responses

import com.gmwapp.hima.models.ChatMessageApi

data class MessageListResponse(
    val status: Boolean,
    val message: String?,
    val data: MessageListData?
)

data class MessageListData(
    val messages: List<ChatMessageApi>,
    val has_more: Boolean,
    val next_page: Int?
)

