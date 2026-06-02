package com.gmwapp.hima.utils

import android.content.Context
import com.gmwapp.hima.models.ChatConversation

/**
 * TC_CH_004: applies the device-local chat overlays ([DeletedChatsPrefsHelper] +
 * [ClearedChatsPrefsHelper]) on top of the server `my_chat` data before it reaches
 * the [com.gmwapp.hima.adapters.ChatListAdapter]:
 *
 *  - **Deleted** threads are dropped from the list until a newer message arrives.
 *  - **Cleared** threads stay in the list but with a blanked preview / zero unread
 *    (the row shows the "No messages yet" placeholder).
 *
 * Call this at every surface that lists conversations (Home → My Chats, Friends /
 * General tabs) right before handing the list to the adapter.
 */
object LocalChatListState {

    fun apply(context: Context, conversations: List<ChatConversation>): List<ChatConversation> {
        if (conversations.isEmpty()) return conversations
        return conversations.mapNotNull { conv ->
            when {
                DeletedChatsPrefsHelper.isHidden(context, conv) -> null
                ClearedChatsPrefsHelper.isClearedAtOrBefore(context, conv.userId, conv.lastMessageId) ->
                    conv.copy(lastMessage = "", lastMessageType = "text", unreadCount = 0)
                else -> conv
            }
        }
    }
}
