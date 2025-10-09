package com.gmwapp.hima.models

data class ChatMessage(
    val id: String,
    val message: String,
    val timestamp: String,
    val isSentByMe: Boolean
)


