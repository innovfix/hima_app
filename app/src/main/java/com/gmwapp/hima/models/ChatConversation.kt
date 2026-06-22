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
    // Call availability + per-minute pricing. Status defaults to 1 (enabled)
    // because the my_chat API frequently omits these fields entirely;
    // showing the row as enabled keeps the call buttons tappable. The
    // downstream call-connection flow does the authoritative availability
    // check and falls back to a toast if the creator is genuinely offline.
    val audioStatus: Int = 1,
    val videoStatus: Int = 1,
    val coinPerMinAudio: Int = 10,
    val coinPerMinVideo: Int = 60,
    val language: String? = null,
    /** Local pin state for list sorting; from [com.gmwapp.hima.utils.PinnedChatsPrefsHelper]. */
    val isPinned: Boolean = false,
    /** TC_022: true when the current user has blocked this chat partner — drives the
     *  "Blocked" indicator so blocked conversations stay visible in the chat list. */
    val isBlocked: Boolean = false,
    /** True when the chat partner has blocked ME — shows a "you can't message" marker
     *  in my own chat list so a blocked conversation doesn't look normal. */
    val peerBlockedMe: Boolean = false,
)



