package com.gmwapp.hima.utils

import com.gmwapp.hima.adapters.AiChatMessage

/**
 * Process-scoped, stale-while-revalidate cache for the support-bot chat.
 *
 * SupportBotActivity is a plain `standard` activity that is destroyed on back
 * and recreated on return. Without this, every re-entry did a full
 * `support_bot_session` network fetch BEFORE it could show anything — the
 * visible "why is it loading again" delay the owner reported (chatting is fast
 * because nothing is torn down; returning is slow because the screen is rebuilt
 * from the server).
 *
 * Here we keep the last-rendered transcript + input state in memory, so a return
 * paints the chat INSTANTLY, and the activity then refreshes from the server
 * quietly in the background (stale-while-revalidate).
 *
 * In-memory only, ON PURPOSE: a real process kill clears it and the screen falls
 * back to the network fetch (still correct, just not instant). Cleared when the
 * session ends so a finished/expired chat is never restored.
 */
object SupportBotSessionCache {
    var sessionId: Int = 0
        private set
    var messages: List<AiChatMessage> = emptyList()
        private set
    var inputMode: String? = null
        private set
    var ticketId: Int? = null
        private set
    var feedbackPrompt: String? = null
        private set
    var attachmentAdded: Boolean = false
        private set
    var ratingSent: Boolean = false
        private set

    fun save(
        sessionId: Int,
        messages: List<AiChatMessage>,
        inputMode: String?,
        ticketId: Int?,
        feedbackPrompt: String?,
        attachmentAdded: Boolean,
        ratingSent: Boolean
    ) {
        this.sessionId = sessionId
        this.messages = messages
        this.inputMode = inputMode
        this.ticketId = ticketId
        this.feedbackPrompt = feedbackPrompt
        this.attachmentAdded = attachmentAdded
        this.ratingSent = ratingSent
    }

    /** True when we hold a usable transcript for exactly this session. */
    fun hasFor(id: Int): Boolean = id > 0 && sessionId == id && messages.isNotEmpty()

    fun clear() {
        sessionId = 0
        messages = emptyList()
        inputMode = null
        ticketId = null
        feedbackPrompt = null
        attachmentAdded = false
        ratingSent = false
    }
}
