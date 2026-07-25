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

    // Incoming-call push titles are "<name> is calling you" / "<name> is video calling
    // you" (@string/incoming_audio_call_title / incoming_video_call_title). When the
    // structured name field is missing, that whole title seeds the ring screen's caller
    // name, so it flashes "Surya is calling you" for a split second before getUserAvatar
    // replaces it with the real short name. Strip the calling boilerplate too.
    private val CALL_SUFFIX = Regex("\\s+is\\s+(video\\s+)?calling\\s+you\\b.*$", RegexOption.IGNORE_CASE)

    fun sanitizePeerName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        // Remove the push-title boilerplate, then hide ALL digits like every other
        // screen (home-screen behaviour via DisplayName.clean) — short digit tails
        // such as "hOwri140" are no longer kept.
        val noBoilerplate = raw.trim()
            .replace(MESSAGE_SUFFIX, "")
            .trim()
        return DisplayName.clean(noBoilerplate)
    }

    /**
     * Like [sanitizePeerName] but also strips the incoming-call title suffix
     * ("… is calling you" / "… is video calling you"). Used for the full-screen ring's
     * caller name, which can be seeded from the notification title on the OneSignal path.
     * Returns "" when nothing but boilerplate remains, so the caller can fall back to a
     * neutral default instead of showing the raw title.
     */
    fun sanitizeCallerName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val noBoilerplate = raw.trim()
            .replace(CALL_SUFFIX, "")
            .replace(MESSAGE_SUFFIX, "")
            .trim()
        // "" when nothing but boilerplate remains, so callers can fall back to a
        // neutral default; otherwise hide all digits like the rest of the app.
        if (noBoilerplate.isBlank()) return ""
        return DisplayName.clean(noBoilerplate)
    }
}
