package com.gmwapp.hima.models

import com.google.firebase.Timestamp

data class ChatConversation(
    val threadId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userImage: String = "",
    val lastMessage: String = "",
    /** `text` | `image` | `audio` — drives the preview label in the chat list. */
    val lastMessageType: String = "text",
    val lastMessageTime: Timestamp? = null,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    // Call availability + per-minute pricing. Defaults mirror the rest of the
    // app (10 coins/min audio, 60 coins/min video) for chats whose API payload
    // doesn't include these fields yet.
    val audioStatus: Int = 1,
    val videoStatus: Int = 1,
    val coinPerMinAudio: Int = 10,
    val coinPerMinVideo: Int = 60,
    val language: String? = null
)



