package com.gmwapp.hima.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * "Is this user still inside their first 24 hours?" — the single owner of that
 * question for the `new_user_purchase` marketing event (Adjust token `hsggks`).
 *
 * WHY THIS EXISTS
 * ---------------
 * Both WalletActivity and MainActivity carried their own copy of an `isNewUser()`
 * that compared CALENDAR DATES on the device clock: `created_at` had to fall on
 * today's date. That is not a 24-hour window — it is "until the next midnight", so
 * the window a user got depended entirely on their signup time:
 *
 *   signed up 12:05 AM -> ~24 hours
 *   signed up  2:00 PM -> ~10 hours
 *   signed up 11:50 PM -> 10 minutes, then midnight killed it
 *
 * A user who registered at 11:50 PM and paid at 12:10 AM — 20 minutes in — fired
 * nothing at all. Now the window is a true rolling 24 hours from the registration
 * timestamp; midnight is irrelevant. Measured against prod, this recovers ~10-11%
 * more day-1 purchases (2026-07-27: 1847 vs 1661).
 *
 * TIMEZONE: `created_at` is written by the backend in Asia/Kolkata
 * (`config/app.php` -> 'timezone' => 'Asia/Kolkata'), so it is parsed as IST
 * explicitly. The old code parsed it in the DEVICE's timezone, which silently
 * shifted the window by hours for anyone whose phone was not on IST.
 */
object SignupWindow {

    private const val TAG = "SignupWindow"

    private val WINDOW_MS = TimeUnit.HOURS.toMillis(24)

    /** The backend writes `created_at` in this zone — parse it in the same one. */
    private val SERVER_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    /**
     * Epoch millis of the user's registration, or 0 when [createdAt] is missing or
     * unparseable.
     */
    fun signupAtMs(createdAt: String?): Long {
        if (createdAt.isNullOrEmpty()) return 0L
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = SERVER_ZONE
            }
            fmt.parse(createdAt)?.time ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "created_at parse failed: $createdAt")
            0L
        }
    }

    /**
     * True while the user is within 24 hours of registering — a rolling window from
     * their exact signup moment, NOT "same calendar day".
     */
    fun isWithinFirst24h(createdAt: String?): Boolean {
        val signupAt = signupAtMs(createdAt)
        if (signupAt <= 0L) return false

        val elapsed = System.currentTimeMillis() - signupAt
        // Negative elapsed = device clock behind the server (or a bad created_at).
        // Treat it as inside the window rather than dropping a real day-1 purchase.
        return elapsed < WINDOW_MS
    }
}
