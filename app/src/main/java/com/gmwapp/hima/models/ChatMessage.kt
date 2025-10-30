package com.gmwapp.hima.models

import java.util.Date

data class ChatMessage(
    val id: String,
    val message: String,
    val timestamp: String,
    val isSentByMe: Boolean,
    val date: Date? = null,  // Store the actual Date object for date grouping
    val isDateHeader: Boolean = false,  // True if this is a date header separator
    val dateHeaderText: String = ""  // The date header text (e.g., "Today", "Yesterday", "24 Dec 2024")
)


