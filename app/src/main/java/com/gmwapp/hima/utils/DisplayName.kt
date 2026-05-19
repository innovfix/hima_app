package com.gmwapp.hima.utils

object DisplayName {
    private val TRAILING_DIGITS = Regex("[\\s_-]*\\d+\\s*$")

    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        val stripped = trimmed.replace(TRAILING_DIGITS, "").trim()
        return if (stripped.length >= 2) stripped else trimmed
    }
}
