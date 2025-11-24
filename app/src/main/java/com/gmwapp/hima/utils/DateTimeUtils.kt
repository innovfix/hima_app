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
}


















