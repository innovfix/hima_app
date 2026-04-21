package com.gmwapp.hima.utils

/**
 * Boolean prefs in [DPreferences] (SharedPreferences "Hima") for male notification gating.
 * Defaults are all true — matches pre-feature behaviour.
 */
object MaleNotificationPreferenceKeys {
    const val CHAT_AND_FRIENDS = "notif_chat_and_friends_enabled"
    const val SUPPORT = "notif_support_enabled"
    const val PROMOTIONS = "notif_promotions_enabled"
    const val CREATOR_ONLINE_MASTER = "notif_creator_online_enabled"
}
