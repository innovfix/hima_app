package com.gmwapp.hima.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.gmwapp.hima.BaseApplication
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for analytics events. Every method fires to BOTH
 * Meta (Facebook AppEventsLogger) AND Firebase Analytics with parameters
 * matched to each platform's standard event schema. Per Yuvanesh on 2026-05-22:
 * "all events in Google ALSO should come in Meta" — so don't fire to only
 * one platform; always mirror.
 *
 * Naming convention:
 *   - Meta side uses AppEventsConstants for standard names + Marketing's
 *     event list (Spend Credits, View Content, etc.).
 *   - Firebase side uses FirebaseAnalytics.Event constants where they match
 *     Meta's standard, and snake_case custom names for Meta-only events.
 *
 * All methods are fail-OPEN — catch and log exceptions so an analytics
 * breakage cannot break the user-facing flow that triggered the event.
 */
object HimaAnalytics {

    private const val TAG = "HimaAnalytics"
    private const val PREFS_D1MP = "hima_d1mp_prefs"
    private const val KEY_SIGNUP_AT = "signup_at_ms"
    private const val KEY_FIRST_PURCHASE_AT = "first_purchase_at_ms"
    private const val KEY_DAY1_PURCHASE_COUNT = "day1_purchase_count"

    // Standard Meta event names not in AppEventsConstants (Facebook hasn't
    // exposed them all). Source: Marketing's email + Facebook Events Manager.
    private const val META_EVENT_SPEND_CREDITS = "fb_mobile_spent_credits"
    private const val META_EVENT_CONTACT = "Contact"
    private const val META_EVENT_VIEW_CONTENT = AppEventsConstants.EVENT_NAME_VIEWED_CONTENT
    private const val META_EVENT_ADD_PAYMENT_INFO = AppEventsConstants.EVENT_NAME_ADDED_PAYMENT_INFO
    private const val META_EVENT_SUBSCRIBE = AppEventsConstants.EVENT_NAME_SUBSCRIBE
    private const val META_EVENT_RATE = AppEventsConstants.EVENT_NAME_RATED
    private const val META_EVENT_ACHIEVE_LEVEL = AppEventsConstants.EVENT_NAME_ACHIEVED_LEVEL
    private const val META_EVENT_UNLOCK_ACHIEVEMENT = AppEventsConstants.EVENT_NAME_UNLOCKED_ACHIEVEMENT
    private const val META_EVENT_D1MP = "d1mp"  // Day-1 Multiple Purchase, custom per marketing request

    // -----------------------------------------------------------------
    // 1. Spend Credits — fires on every call deduction
    // -----------------------------------------------------------------
    /**
     * @param coinsSpent how many coins were deducted (e.g. 60 for video, 10 for audio)
     * @param contentType "audio_call" / "video_call"
     * @param contentId   the call_id (so Meta can dedupe within a session)
     */
    fun logSpendCredits(ctx: Context, coinsSpent: Int, contentType: String, contentId: String? = null) {
        if (coinsSpent <= 0) return
        try {
            val params = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, contentType)
                contentId?.let { putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, it) }
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
            }
            // valueToSum (in coins) — Facebook uses this for SUM aggregation in Ads Manager.
            AppEventsLogger.newLogger(ctx).logEvent(META_EVENT_SPEND_CREDITS, coinsSpent.toDouble(), params)

            // Firebase mirror — uses standard SPEND_VIRTUAL_CURRENCY event
            val fbBundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.VIRTUAL_CURRENCY_NAME, "coins")
                putLong(FirebaseAnalytics.Param.VALUE, coinsSpent.toLong())
                putString(FirebaseAnalytics.Param.ITEM_NAME, contentType)
                contentId?.let { putString(FirebaseAnalytics.Param.ITEM_ID, it) }
            }
            BaseApplication.firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SPEND_VIRTUAL_CURRENCY, fbBundle)
        } catch (t: Throwable) {
            Log.w(TAG, "logSpendCredits failed: ${t.message}")
        }
    }

    // -----------------------------------------------------------------
    // 2. View Content — fires when male views female profile
    // -----------------------------------------------------------------
    fun logViewContent(ctx: Context, contentId: String, contentType: String = "creator_profile", value: Double = 0.0) {
        try {
            val params = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, contentId)
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, contentType)
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
            }
            AppEventsLogger.newLogger(ctx).logEvent(META_EVENT_VIEW_CONTENT, value, params)

            val fbBundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_ID, contentId)
                putString(FirebaseAnalytics.Param.ITEM_NAME, contentType)
                putString(FirebaseAnalytics.Param.CURRENCY, "INR")
                if (value > 0.0) putDouble(FirebaseAnalytics.Param.VALUE, value)
            }
            BaseApplication.firebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, fbBundle)
        } catch (t: Throwable) {
            Log.w(TAG, "logViewContent failed: ${t.message}")
        }
    }

    // -----------------------------------------------------------------
    // 3. Add Payment Info — fires when payment screen opens
    // -----------------------------------------------------------------
    fun logAddPaymentInfo(ctx: Context, success: Boolean = true) {
        try {
            val params = Bundle().apply {
                putInt(AppEventsConstants.EVENT_PARAM_SUCCESS, if (success) 1 else 0)
            }
            AppEventsLogger.newLogger(ctx).logEvent(META_EVENT_ADD_PAYMENT_INFO, params)

            val fbBundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.PAYMENT_TYPE, "selected")
                putString("success", success.toString())
            }
            BaseApplication.firebaseAnalytics.logEvent(FirebaseAnalytics.Event.ADD_PAYMENT_INFO, fbBundle)
        } catch (t: Throwable) {
            Log.w(TAG, "logAddPaymentInfo failed: ${t.message}")
        }
    }

    // -----------------------------------------------------------------
    // 4. Contact — fires on first call connection
    // -----------------------------------------------------------------
    fun logContact(ctx: Context, contentType: String = "voice_call") {
        try {
            val params = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, contentType)
            }
            AppEventsLogger.newLogger(ctx).logEvent(META_EVENT_CONTACT, params)
            BaseApplication.firebaseAnalytics.logEvent("contact", params)
        } catch (t: Throwable) {
            Log.w(TAG, "logContact failed: ${t.message}")
        }
    }

    // -----------------------------------------------------------------
    // 5. Rate — fires on user rating submit
    // -----------------------------------------------------------------
    fun logRate(ctx: Context, rating: Int, maxRating: Int = 5, contentType: String = "creator") {
        try {
            val params = Bundle().apply {
                putInt(AppEventsConstants.EVENT_PARAM_MAX_RATING_VALUE, maxRating)
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, contentType)
            }
            AppEventsLogger.newLogger(ctx).logEvent(META_EVENT_RATE, rating.toDouble(), params)

            val fbBundle = Bundle().apply {
                putLong("rating", rating.toLong())
                putLong("max_rating", maxRating.toLong())
                putString("content_type", contentType)
            }
            BaseApplication.firebaseAnalytics.logEvent("rate", fbBundle)
        } catch (t: Throwable) {
            Log.w(TAG, "logRate failed: ${t.message}")
        }
    }

    // -----------------------------------------------------------------
    // 6. Subscribe — fires on autopay subscription activation
    // -----------------------------------------------------------------
    fun logSubscribe(ctx: Context, planId: String, value: Double = 0.0, currency: String = "INR") {
        try {
            val params = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, planId)
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, currency)
            }
            AppEventsLogger.newLogger(ctx).logEvent(META_EVENT_SUBSCRIBE, value, params)

            val fbBundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_ID, planId)
                putString(FirebaseAnalytics.Param.CURRENCY, currency)
                if (value > 0.0) putDouble(FirebaseAnalytics.Param.VALUE, value)
            }
            BaseApplication.firebaseAnalytics.logEvent("subscribe", fbBundle)
        } catch (t: Throwable) {
            Log.w(TAG, "logSubscribe failed: ${t.message}")
        }
    }

    // -----------------------------------------------------------------
    // 7. d1mp — Day-1 Multiple Purchase (custom per marketing)
    // -----------------------------------------------------------------
    /**
     * Call AFTER a successful purchase. We track:
     *   - signup_at (set on first call after signup)
     *   - day1_purchase_count (incremented every purchase within 24h of signup)
     *
     * If the count reaches >= 2 within 24h of signup, fire d1mp event with the
     * cumulative value spent on Day 1.
     *
     * @param signupAtMs UNIX ms of when user signed up (from UserData.created_at)
     * @param purchaseValue value of THIS purchase in INR
     */
    fun logPurchaseAndMaybeD1mp(ctx: Context, signupAtMs: Long, purchaseValue: Double, currency: String = "INR") {
        if (signupAtMs <= 0L || purchaseValue <= 0.0) return
        try {
            val now = System.currentTimeMillis()
            val day1Window = TimeUnit.HOURS.toMillis(24)
            val withinDay1 = (now - signupAtMs) < day1Window
            if (!withinDay1) return  // not a day-1 purchase, do nothing

            val prefs = ctx.getSharedPreferences(PREFS_D1MP, Context.MODE_PRIVATE)
            val storedSignup = prefs.getLong(KEY_SIGNUP_AT, 0L)
            if (storedSignup != signupAtMs) {
                // First time we see this user (or signup ts changed) — reset
                prefs.edit().clear()
                    .putLong(KEY_SIGNUP_AT, signupAtMs)
                    .apply()
            }

            val newCount = prefs.getInt(KEY_DAY1_PURCHASE_COUNT, 0) + 1
            val cumulativeValue = prefs.getFloat("day1_value", 0f) + purchaseValue.toFloat()
            prefs.edit()
                .putInt(KEY_DAY1_PURCHASE_COUNT, newCount)
                .putFloat("day1_value", cumulativeValue)
                .apply()

            if (newCount >= 2) {
                // d1mp triggers on the 2nd+ purchase within 24h.
                val params = Bundle().apply {
                    putString(AppEventsConstants.EVENT_PARAM_CURRENCY, currency)
                    putInt("purchase_count", newCount)
                }
                AppEventsLogger.newLogger(ctx).logEvent(META_EVENT_D1MP, cumulativeValue.toDouble(), params)

                val fbBundle = Bundle().apply {
                    putString(FirebaseAnalytics.Param.CURRENCY, currency)
                    putDouble(FirebaseAnalytics.Param.VALUE, cumulativeValue.toDouble())
                    putLong("purchase_count", newCount.toLong())
                }
                BaseApplication.firebaseAnalytics.logEvent(META_EVENT_D1MP, fbBundle)

                Log.d(TAG, "d1mp fired: count=$newCount cumulative=$cumulativeValue $currency")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "logPurchaseAndMaybeD1mp failed: ${t.message}")
        }
    }

    // -----------------------------------------------------------------
    // 8. Achieve Level / Unlock Achievement (for future creator badges)
    // -----------------------------------------------------------------
    fun logAchieveLevel(ctx: Context, level: String) {
        try {
            val params = Bundle().apply { putString(AppEventsConstants.EVENT_PARAM_LEVEL, level) }
            AppEventsLogger.newLogger(ctx).logEvent(META_EVENT_ACHIEVE_LEVEL, params)

            val fbBundle = Bundle().apply { putString(FirebaseAnalytics.Param.LEVEL_NAME, level) }
            BaseApplication.firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LEVEL_UP, fbBundle)
        } catch (t: Throwable) {
            Log.w(TAG, "logAchieveLevel failed: ${t.message}")
        }
    }

    fun logUnlockAchievement(ctx: Context, description: String) {
        try {
            val params = Bundle().apply { putString(AppEventsConstants.EVENT_PARAM_DESCRIPTION, description) }
            AppEventsLogger.newLogger(ctx).logEvent(META_EVENT_UNLOCK_ACHIEVEMENT, params)

            val fbBundle = Bundle().apply { putString(FirebaseAnalytics.Param.ACHIEVEMENT_ID, description) }
            BaseApplication.firebaseAnalytics.logEvent(FirebaseAnalytics.Event.UNLOCK_ACHIEVEMENT, fbBundle)
        } catch (t: Throwable) {
            Log.w(TAG, "logUnlockAchievement failed: ${t.message}")
        }
    }
}
