package com.gmwapp.hima.models

data class AiChatMessage(
    val id: Int,
    val text: String,
    val type: MessageType
)

enum class MessageType {
    AI_RECEIVED,
    USER_SENT,
    TYPING_INDICATOR,
    PEOPLE_CARDS,
    PAY_CTA,
    SENDING_STATUS
}
