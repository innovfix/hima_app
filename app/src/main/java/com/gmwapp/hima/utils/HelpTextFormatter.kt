package com.gmwapp.hima.utils

/**
 * CMS/API text often stores newlines as the two-character sequence `\` + `n` instead of real line breaks.
 * Converts those (and related escapes) for display in TextViews.
 */
fun String.unescapeHelpContent(): String {
    if (isEmpty()) return this
    return this
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace("\\r", "\n")
        .replace("\\t", "\t")
        .replace("\\'", "'")
        .replace("\\u0027", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("\\\\", "\\")
}
