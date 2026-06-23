package com.gmwapp.hima.utils

import java.text.SimpleDateFormat
import java.util.*

object DateTimeUtils {
    
    /**
     * Format last seen time in a human-readable format
     * Examples: "Now", "2 min ago", "1 hour ago", "Yesterday", "3 days ago", "Jan 15"
     */
    fun formatLastSeen(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "Unknown"
        
        try {
            // Try multiple date formats
            val formats = listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd",
                "dd MMM yyyy",
                "MMM dd, yyyy"
            )
            
            var lastSeenDate: Date? = null
            
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.getDefault())
                    lastSeenDate = sdf.parse(dateString)
                    if (lastSeenDate != null) break
                } catch (e: Exception) {
                    // Try next format
                }
            }
            
            if (lastSeenDate == null) {
                return dateString  // Return original if can't parse
            }
            
            val now = Calendar.getInstance()
            val lastSeen = Calendar.getInstance().apply { time = lastSeenDate }
            
            // Get time difference
            val diffMillis = now.timeInMillis - lastSeen.timeInMillis
            val diffMinutes = diffMillis / (1000 * 60)
            val diffHours = diffMillis / (1000 * 60 * 60)
            val diffDays = diffMillis / (1000 * 60 * 60 * 24)
            
            return when {
                // Less than 1 minute
                diffMinutes < 1 -> "Now"
                
                // Less than 1 hour
                diffMinutes < 60 -> {
                    if (diffMinutes == 1L) "1 min ago" else "$diffMinutes min ago"
                }
                
                // Less than 24 hours
                diffHours < 24 -> {
                    if (diffHours == 1L) "1 hour ago" else "$diffHours hours ago"
                }
                
                // Yesterday
                diffDays == 1L && lastSeen.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
                
                // Less than 7 days
                diffDays < 7 -> {
                    if (diffDays == 1L) "1 day ago" else "$diffDays days ago"
                }
                
                // Same year - show month and day
                lastSeen.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> {
                    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                    sdf.format(lastSeenDate)
                }
                
                // Different year - show full date
                else -> {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    sdf.format(lastSeenDate)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return dateString ?: "Unknown"
        }
    }

    // Returns the 12-hour clock time in English ("1:06 PM"). Accepts EITHER a
    // full datetime ("yyyy-MM-dd HH:mm:ss", as in the transaction `datetime`) OR
    // a time-only value ("HH:mm:ss" / "HH:mm" — the backend stores `started_time`
    // time-only, e.g. "13:06:45"). Without the time-only patterns the start time
    // silently dropped (parse failure → ""), which is why it never rendered.
    // Locale.ENGLISH keeps AM/PM from rendering in regional script on
    // Hindi/Tamil/etc. locale devices. Returns "" on null/blank/unparseable input.
    fun formatCallStartTime(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val output = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        for (pattern in listOf("yyyy-MM-dd HH:mm:ss", "HH:mm:ss", "HH:mm")) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
                val parsed = fmt.parse(value.trim()) ?: continue
                return output.format(parsed)
            } catch (_: Exception) { /* try next pattern */ }
        }
        return ""
    }

    /**
     * Builds the two-line transaction subtitle shared by the male & female
     * transaction lists (FI_05), so the long "date, time · duration" string no
     * longer wraps mid-word in the narrow row:
     *   line 1:  "<date>, <start-time>"   (start time accented)
     *   line 2:  "<duration>"             (smaller + lighter)
     * Any part is omitted if absent, so a row with no time/duration still reads
     * cleanly. Render in tv_transaction_date with maxLines=2.
     */
    fun buildTxnSubtitle(date: String?, startTimeRaw: String?, duration: String?): CharSequence {
        val time = formatCallStartTime(startTimeRaw)
        val sb = android.text.SpannableStringBuilder()
        if (!date.isNullOrBlank()) sb.append(date.trim())
        if (time.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append(", ")
            val start = sb.length
            sb.append(time)
            sb.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#4F46E5")),
                start, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (!duration.isNullOrBlank()) {
            if (sb.isNotEmpty()) sb.append("\n")
            val start = sb.length
            sb.append(duration.trim())
            sb.setSpan(android.text.style.RelativeSizeSpan(0.86f), start, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#9CA3AF")),
                start, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return sb
    }
}























