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

    // The server also sends the PREFIX form as the push title: "Audio Call from <name>" /
    // "Video Call from <name>" (also "Incoming/Missed call from <name>"). On the OneSignal
    // path this whole title seeds the ring's caller name when the structured name field is
    // absent, so the ring shows "Audio Call from Pankaj" instead of "Pankaj". CALL_SUFFIX
    // only strips trailing boilerplate, so strip this leading boilerplate too.
    private val CALL_PREFIX =
        Regex("^\\s*(incoming\\s+|missed\\s+)?(audio\\s+|video\\s+)?call\\s+from(?:\\s+|$)", RegexOption.IGNORE_CASE)

    // CALL_PREFIX above is anchored with "^\s*", i.e. "start of string, then WHITESPACE
    // only". Two real-world titles slipped past it and rendered in full on the ring
    // screen (QA 28-Jul, "Audio Call from Pank…", ~2 calls in 10):
    //
    //   * a leading symbol — the server decorates some templates ("☎ Audio Call from X").
    //     A symbol is not whitespace, so the anchor never matched and NOTHING was
    //     stripped. CallNotifications.normalizeMissedCallCallerName hit exactly this and
    //     documents it: "the server sends e.g. '📞 Missed call from Kishore12'".
    //   * "voice" — the alternation only knows audio|video, yet the ring screen's own
    //     label is "Incoming Voice Call", so "Voice Call from X" passed through clean.
    //
    // So match the SAME shape anywhere in the string and keep the captured tail, which
    // is what the missed-call normaliser already does successfully.
    private val CALL_PREFIX_CAPTURE = Regex(
        "(?:incoming\\s+|missed\\s+)?(?:audio\\s+|video\\s+|voice\\s+)?call\\s+from\\s*[:\\-]?\\s+(.+?)\\s*$",
        RegexOption.IGNORE_CASE
    )

    /** Leading emoji / symbols / punctuation sitting before the real name. */
    private val LEADING_JUNK = Regex("^[^\\p{L}\\p{N}]+")

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
        val trimmed = raw.trim()
            .replace(CALL_SUFFIX, "")
            .replace(MESSAGE_SUFFIX, "")
            .trim()
            .removeSuffix("…")
            .trim()

        // "<anything> call from <name>" -> keep only <name>. Runs first because it is
        // the only branch that survives a decorated title; a plain name simply does not
        // match and falls through untouched.
        CALL_PREFIX_CAPTURE.find(trimmed)?.groupValues?.getOrNull(1)
            ?.trim()?.removeSuffix("…")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return DisplayName.clean(it) }

        // Not the prefix form. Keep the original anchored strip so every case that
        // already worked keeps behaving identically (notably a bare "Audio Call from"
        // with no name, which must still collapse to ""), then drop any leading
        // emoji/symbol so a decorated title can never reach the screen.
        val noBoilerplate = trimmed
            .replace(CALL_PREFIX, "")
            .replace(LEADING_JUNK, "")
            .trim()
        // "" when nothing but boilerplate remains, so callers can fall back to a
        // neutral default; otherwise hide all digits like the rest of the app.
        if (noBoilerplate.isBlank()) return ""
        return DisplayName.clean(noBoilerplate)
    }
}
