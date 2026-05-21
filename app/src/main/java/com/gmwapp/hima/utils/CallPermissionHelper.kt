package com.gmwapp.hima.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.gmwapp.hima.constants.DConstants

/**
 * Prompts that improve incoming-call reliability: full-screen intent (Android 14+)
 * and optional battery optimization exemption.
 */
object CallPermissionHelper {
    private const val TAG = "CallPermissionHelper"
    private const val PREFS = "app_prefs"
    private const val KEY_FSI_LAST_PROMPT = "call_fsi_permission_last_prompt"
    private const val KEY_BATTERY_OPT_PROMPTED = "call_battery_opt_prompted"

    /**
     * Once per day, if full-screen intents are not allowed, open system settings so the user can enable them.
     */
    fun maybePromptFullScreenIntentPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val nm = activity.getSystemService(NotificationManager::class.java) ?: return
        if (nm.canUseFullScreenIntent()) return

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_FSI_LAST_PROMPT, 0L)
        val oneDay = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - last < oneDay) return

        prefs.edit().putLong(KEY_FSI_LAST_PROMPT, System.currentTimeMillis()).apply()

        try {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open full-screen intent settings: ${e.message}")
        }
    }

    /**
     * One-time dialog asking the user to exempt Hima from battery
     * optimizations. Uses the direct one-tap system dialog
     * (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — that intent requires
     * the manifest permission of the same name, and Play policy allows it
     * for calling apps whose ringtone/wakeup must survive Doze.
     * Falls back to the app-details screen if the direct intent is
     * unavailable on a given OEM build.
     */
    @SuppressLint("BatteryLife")
    fun maybePromptBatteryOptimizationExemption(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = activity.getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(activity.packageName)) return

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BATTERY_OPT_PROMPTED, false)) return
        prefs.edit().putBoolean(KEY_BATTERY_OPT_PROMPTED, true).apply()

        AlertDialog.Builder(activity)
            .setTitle("Reliable incoming calls")
            .setMessage(
                "Allow Hima to run without battery restrictions so incoming-call " +
                    "ringtones still play when the screen is off."
            )
            .setPositiveButton("Allow") { _, _ ->
                val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                try {
                    activity.startActivity(directIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Direct battery-opt intent unavailable: ${e.message}")
                    try {
                        activity.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", activity.packageName, null)
                            }
                        )
                    } catch (e2: Exception) {
                        Log.e(TAG, "App-details fallback also failed: ${e2.message}")
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Run FSI prompt (daily) and battery prompt (once) for male/female callers. */
    fun maybePromptCallReliabilityPermissions(activity: Activity) {
        val gender = com.gmwapp.hima.BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
            ?: return
        if (gender != DConstants.FEMALE && gender != DConstants.MALE) return
        maybePromptFullScreenIntentPermission(activity)
        maybePromptBatteryOptimizationExemption(activity)
    }
}
