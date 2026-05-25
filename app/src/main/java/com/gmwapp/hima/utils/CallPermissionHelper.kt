package com.gmwapp.hima.utils

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.gmwapp.hima.constants.DConstants

/**
 * Prompts that improve incoming-call reliability: full-screen intent (Android 14+).
 */
object CallPermissionHelper {
    private const val TAG = "CallPermissionHelper"
    private const val PREFS = "app_prefs"
    private const val KEY_FSI_LAST_PROMPT = "call_fsi_permission_last_prompt"

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

    /** Run the daily FSI prompt for male/female callers. */
    fun maybePromptCallReliabilityPermissions(activity: Activity) {
        val gender = com.gmwapp.hima.BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
            ?: return
        if (gender != DConstants.FEMALE && gender != DConstants.MALE) return
        maybePromptFullScreenIntentPermission(activity)
    }
}
