package com.gmwapp.hima.utils

object DisplayName {
    // Strip EVERY digit from a name, not just a trailing run — this matches how the
    // home screen hides numbers in creator/user names (`name.replace(Regex("[0-9]"), "")`).
    private val DIGITS = Regex("[0-9]")

    /**
     * Hides all digits in a user/creator display name (home-screen behaviour), so
     * "Priya123" / "hOwri140" render as "Priya" / "hOwri". Falls back to the trimmed
     * original when stripping would leave fewer than 2 characters, so a handle that is
     * mostly/all digits never renders blank.
     *
     * NOT used on the user's own Profile fragment or Edit Profile screen — those show
     * the raw name so the user can see/edit exactly what they entered.
     */
    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        val stripped = trimmed.replace(DIGITS, "").trim()
        return if (stripped.length >= 2) stripped else trimmed
    }
}
