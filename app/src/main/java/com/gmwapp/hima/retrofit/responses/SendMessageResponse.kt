package com.gmwapp.hima.retrofit.responses

import com.gmwapp.hima.models.ChatMessageApi

data class SendMessageResponse(
    val status: Boolean,
    val message: String?,
    val data: ChatMessageApi?
)

