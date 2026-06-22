package com.gmwapp.hima.utils

import com.gmwapp.hima.BaseApplication

/**
 * Client-side FCM filtering for male users based on Manage Notifications prefs.
 * Must never drop operational / call / money / session payloads.
 */
object MaleNotificationFcmGate {

    private val LUDO_TYPES = setOf(
        "ludo_invite",
        "ludo_invite_accepted",
        "ludo_invite_rejected",
        "ludo_invite_expired",
        "game_end"
    )

    private val PROMO_TYPES = setOf(
        "coupon_enabled",
        "coupon_disabled",
        "smart_notification",
        "new_user_engagement"
    )

    fun isOperationalOrCritical(type: String, message: String): Boolean {
        if (type == "clear_data" || message == "clear_data") return true
        if (type in LUDO_TYPES || message == "game_end") return true
        if (message.startsWith("incoming call")) return true
        if (message == "callDeclined" || message == "userBusy") return true
        if (message == "accepted" || message == "rejected") return true
        if (message == "remainingTimeUpdated") return true
        if (message.startsWith("switchToVideo") || message.startsWith("switchToAudio")) return true
        if (message == "VideoAccepted" || message == "AudioAccepted" || message == "SwitchDeclined") return true
        if (message == "giftSent") return true
        if (message == "greyScreenEnable" || message == "greyScreenDisable") return true
        if (type == "withdrawal_paid") return true
        if (type == "audio" || type == "video") return true
        return false
    }

    /**
     * @return true if this data payload should be dropped (skip rest of FCM handler).
     */
    fun shouldDropDataPayload(
        gender: String?,
        data: Map<String, String>,
        prefs: DPreferences?
    ): Boolean {
        if (!gender.equals("male", ignoreCase = true) || prefs == null) return false

        val type = data["type"] ?: ""
        val message = data["message"] ?: ""

        if (isOperationalOrCritical(type, message)) return false

        if (type == "message" || type == "friend_request" || type == "friend_request_accepted" || type == "friend_request_rejected") {
            return !prefs.getBoolPref(MaleNotificationPreferenceKeys.CHAT_AND_FRIENDS, true)
        }
        if (type == "your ticket") {
            return !prefs.getBoolPref(MaleNotificationPreferenceKeys.SUPPORT, true)
        }
        if (type in PROMO_TYPES) {
            return !prefs.getBoolPref(MaleNotificationPreferenceKeys.PROMOTIONS, true)
        }

        if (looksLikeCreatorOnline(data, type, message)) {
            if (!prefs.getBoolPref(MaleNotificationPreferenceKeys.CREATOR_ONLINE_MASTER, true)) {
                return true
            }
            val femaleId = extractFemaleIdForOnlinePing(data)
            if (femaleId != null && femaleId > 0) {
                val ctx = BaseApplication.getInstance() ?: return false
                val subscribed = NotifyOnlinePrefsHelper.prefs(ctx)
                    .getBoolean("notify_$femaleId", false)
                if (!subscribed) return true
            }
            return false
        }

        // Unknown / empty type → promotions bucket (marketing / misc broadcasts)
        if (type.isEmpty() || type !in KNOWN_NON_PROMO_TYPES) {
            return !prefs.getBoolPref(MaleNotificationPreferenceKeys.PROMOTIONS, true)
        }

        return false
    }

    /** Types we treat outside the generic "unknown → promo" bucket. */
    private val KNOWN_NON_PROMO_TYPES: Set<String> = buildSet {
        addAll(PROMO_TYPES)
        add("message")
        add("friend_request")
        add("friend_request_accepted")
        add("friend_request_rejected")
        add("your ticket")
        addAll(LUDO_TYPES)
        add("clear_data")
        add("withdrawal_paid")
        add("audio")
        add("video")
    }

    private fun looksLikeCreatorOnline(data: Map<String, String>, type: String, message: String): Boolean {
        if (type.isNotEmpty()) return false
        if (message.startsWith("incoming call")) return false
        val screen = data["screen"]?.trim().orEmpty()
        if (screen.isNotEmpty()) return true
        if (data.containsKey("female_id") || data.containsKey("female_user_id")) return true
        return false
    }

    private fun extractFemaleIdForOnlinePing(data: Map<String, String>): Int? {
        data["female_id"]?.toIntOrNull()?.let { return it }
        data["female_user_id"]?.toIntOrNull()?.let { return it }
        data["user_id"]?.toIntOrNull()?.let { return it }
        data["senderId"]?.toIntOrNull()?.let { return it }
        return null
    }
}
