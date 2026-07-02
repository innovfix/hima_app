package com.gmwapp.hima.utils

/**
 * Normalises a peer's display name before it reaches the chat header / profile.
 *
 * Chat push notifications arrive with a title that is the full headline
 * "<name> sent you a message" (also "… sent you a photo / gift / voice message").
 * Only the leading token is the actual username. Historically that whole title
 * leaked into [ChatNotificationStore] and the "USER_NAME" intent extra, so the
 * chat screen header and the profile screen showed "hOwri140 sent you a message"
 * in place of the username. This strips the boilerplate suffix so we recover the
 * real name, and also self-heals any already-poisoned value cached on disk.
 */
object PeerNameUtils {
    // Everything from " sent you …" onward is boilerplate, not part of the name.
    private val MESSAGE_SUFFIX = Regex("\\s+sent you\\b.*$", RegexOption.IGNORE_CASE)

    // Pre-existing rule (was duplicated as extractNameOnly): drop a trailing run of
    // 6+ digits some generated usernames carry. Short digit tails (e.g. "hOwri140")
    // are kept because they are part of the name.
    private val TRAILING_DIGITS = Regex("\\d{6,}$")

    fun sanitizePeerName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim()
            .replace(MESSAGE_SUFFIX, "")
            .replace(TRAILING_DIGITS, "")
            .trim()
    }
}
