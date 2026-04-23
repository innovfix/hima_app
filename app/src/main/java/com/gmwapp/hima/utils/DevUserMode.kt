package com.gmwapp.hima.utils

import android.content.Context
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.activities.DummySubscriptionActivity

/**
 * Dev-only override to force "new user" vs "old user" state.
 *
 * Toggled from the Profile screen. When the pref is set, it overrides the
 * backend-derived detection (`userData.coins == 0`). When unset, falls back
 * to the real coin value.
 */
object DevUserMode {
    private const val PREFS = "DevUserTypePrefs"
    private const val KEY_IS_NEW_USER = "isNewUserMode"
    private const val KEY_SET = "hasOverride"

    fun setNewUser(context: Context, isNew: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_NEW_USER, isNew)
            .putBoolean(KEY_SET, true)
            .apply()
    }

    fun hasOverride(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SET, false)

    /**
     * Returns current user-type state. If a dev override exists, returns it.
     * Otherwise: once the user has subscribed at least once (even if later
     * cancelled), they are treated as old user. Else falls back to
     * `coins == 0`.
     */
    fun isNewUser(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SET, false)) {
            return prefs.getBoolean(KEY_IS_NEW_USER, true)
        }
        val subPrefs = context.getSharedPreferences(
            DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE
        )
        if (subPrefs.getBoolean(DummySubscriptionActivity.KEY_EVER_ACTIVE, false)) {
            return false
        }
        val coins = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.coins ?: 0
        return coins == 0
    }
}
