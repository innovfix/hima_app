package com.gmwapp.hima.models

import com.google.firebase.Timestamp

data class ChatConversation(
    val threadId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userImage: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Timestamp? = null,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)



