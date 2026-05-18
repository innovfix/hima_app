package com.gmwapp.hima.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import java.util.concurrent.TimeUnit

/**
 * B120: Detects a wildly-wrong device clock and prompts the user to fix it.
 *
 * Calls fail silently on devices whose clock is off by more than a few
 * minutes because:
 *  - TLS handshakes against the API server reject certificates whose
 *    `notBefore`/`notAfter` window doesn't cover the device's local time.
 *  - The Agora SDK validates token expiry against `System.currentTimeMillis()`,
 *    so a clock set years in the past sees every token as "not yet valid"
 *    and a clock set years in the future sees every token as "expired."
 *
 * The app cannot programmatically fix the user's clock (would require
 * WRITE_SETTINGS / system permission). All we can do is notice the skew and
 * tell the user to enable automatic date/time in Settings.
 *
 * Throttled to once-per-day so a power user who genuinely runs a custom
 * clock isn't spammed.
 */
object DeviceTimeChecker {

    private const val TAG = "DeviceTimeChecker"
    private const val PREFS = "app_prefs"
    private const val KEY_LAST_PROMPT = "device_time_skew_last_prompt"

    /** Jan 1 2025 00:00 UTC — any time before this on a 2026+ device is clearly wrong. */
    private const val SANE_MIN_MILLIS = 1_735_689_600_000L

    /** Jan 1 2040 00:00 UTC — any time after this is clearly wrong for the current era. */
    private const val SANE_MAX_MILLIS = 2_208_988_800_000L

    fun isDeviceTimeSuspicious(): Boolean {
        val now = System.currentTimeMillis()
        return now < SANE_MIN_MILLIS || now > SANE_MAX_MILLIS
    }

    /**
     * Shows a one-time-per-day dialog if the clock looks wrong. Returns true
     * if the dialog was shown (caller can use this to suppress other prompts
     * stacking on top).
     */
    fun maybeWarnDeviceTime(activity: Activity): Boolean {
        if (!isDeviceTimeSuspicious()) return false
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_PROMPT, 0L)
        val now = System.currentTimeMillis()
        // Throttle: don't re-prompt within a day. But if `last` was written
        // BEFORE our sanity-min, the user just clock-corrected — let it fire.
        if (last in SANE_MIN_MILLIS..now &&
            now - last < TimeUnit.DAYS.toMillis(1)
        ) return false
        prefs.edit().putLong(KEY_LAST_PROMPT, now).apply()

        Log.w(
            TAG,
            "Device time suspicious: now=$now SANE_MIN=$SANE_MIN_MILLIS SANE_MAX=$SANE_MAX_MILLIS"
        )

        try {
            AlertDialog.Builder(activity)
                .setTitle("Phone date and time is wrong")
                .setMessage(
                    "Your phone's date/time is set incorrectly. Hima calls and chat " +
                        "may not work until you fix it.\n\nTap 'Open Settings' and turn " +
                        "on 'Automatic date & time.'"
                )
                .setCancelable(false)
                .setPositiveButton("Open Settings") { _, _ ->
                    try {
                        activity.startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open date settings: ${e.message}")
                    }
                }
                .setNegativeButton("Not now", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show device-time dialog: ${e.message}")
            return false
        }
        return true
    }
}
