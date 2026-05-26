package com.gmwapp.hima.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * CHAT-095: formats the server's `last_online` timestamp (now sourced from
 * users.last_active_at) into one of seven display states for the chat header.
 *
 *   1 · "Online"                          — under 60 seconds
 *   2 · "Last seen 23 min ago"            — 1–59 minutes
 *   3 · "Last seen today at 3:42 PM"      — earlier today
 *   4 · "Last seen yesterday at 11:30 PM" — yesterday
 *   5 · "Last seen 3 days ago"            — 2–6 days
 *   6 · "Last seen on 25 May 2026"        — 7+ days
 *   7 · "Offline"                         — null/blank/unparseable
 *
 * Only state 1 should be rendered in the online-green color; everything
 * else (including "Offline") is the muted grey.
 */
object LastSeenFormatter {

    data class Display(
        val text: String,
        val isOnline: Boolean
    )

    private const val ONLINE_THRESHOLD_SEC = 60L
    private const val MINUTES_THRESHOLD_SEC = 60L * 60L
    private const val WEEK_THRESHOLD_DAYS = 7

    /**
     * Server returns IST datetime strings like "2026-05-26 14:32:15". The
     * timezone is implicit (IST, Asia/Kolkata) — we parse with that explicit
     * zone, then format relative to the device's local wall-clock.
     */
    fun format(rawTimestamp: String?, now: Date = Date()): Display {
        val parsed = parseServerTimestamp(rawTimestamp)
            ?: return Display(text = "Offline", isOnline = false)

        val deltaSec = ((now.time - parsed.time) / 1000L).coerceAtLeast(0L)

        // State 1 — Online
        if (deltaSec < ONLINE_THRESHOLD_SEC) {
            return Display(text = "Online", isOnline = true)
        }

        // State 2 — minutes
        if (deltaSec < MINUTES_THRESHOLD_SEC) {
            val mins = (deltaSec / 60L).coerceAtLeast(1L)
            return Display(text = "Last seen $mins min ago", isOnline = false)
        }

        val calNow = Calendar.getInstance().apply { time = now }
        val calThen = Calendar.getInstance().apply { time = parsed }
        val sameDay = isSameDay(calNow, calThen)
        val isYesterday = isYesterday(calNow, calThen)
        val daysDiff = daysBetween(calNow, calThen)

        val timeFmt = SimpleDateFormat("h:mm a", Locale.ENGLISH)

        // State 3 — today
        if (sameDay) {
            return Display(text = "Last seen today at ${timeFmt.format(parsed)}", isOnline = false)
        }

        // State 4 — yesterday
        if (isYesterday) {
            return Display(text = "Last seen yesterday at ${timeFmt.format(parsed)}", isOnline = false)
        }

        // State 5 — 2–6 days
        if (daysDiff in 2..(WEEK_THRESHOLD_DAYS - 1)) {
            return Display(text = "Last seen $daysDiff days ago", isOnline = false)
        }

        // State 6 — 7+ days → absolute date "25 May 2026"
        val absFmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
        return Display(text = "Last seen on ${absFmt.format(parsed)}", isOnline = false)
    }

    private fun parseServerTimestamp(raw: String?): Date? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        // Server formats: "2026-05-26 14:32:15" (IST) or ISO with tz. Try IST
        // first because that's what chat_history actually returns today.
        val candidates = listOf(
            "yyyy-MM-dd HH:mm:ss" to TimeZone.getTimeZone("Asia/Kolkata"),
            "yyyy-MM-dd'T'HH:mm:ssXXX" to null,
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" to null,
            "yyyy-MM-dd'T'HH:mm:ss" to TimeZone.getTimeZone("Asia/Kolkata")
        )
        for ((pattern, tz) in candidates) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.ENGLISH)
                if (tz != null) fmt.timeZone = tz
                return fmt.parse(trimmed) ?: continue
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, then: Calendar): Boolean {
        val ydy = now.clone() as Calendar
        ydy.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(ydy, then)
    }

    /** Calendar-day difference (whole days), not wall-clock seconds /86400. */
    private fun daysBetween(now: Calendar, then: Calendar): Int {
        val n = now.clone() as Calendar
        val t = then.clone() as Calendar
        n.set(Calendar.HOUR_OF_DAY, 0); n.set(Calendar.MINUTE, 0); n.set(Calendar.SECOND, 0); n.set(Calendar.MILLISECOND, 0)
        t.set(Calendar.HOUR_OF_DAY, 0); t.set(Calendar.MINUTE, 0); t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0)
        val diffMs = n.timeInMillis - t.timeInMillis
        return (diffMs / (24L * 60L * 60L * 1000L)).toInt()
    }
}
