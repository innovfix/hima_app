package com.gmwapp.hima.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.mmp.AdjustTracker
import com.gmwapp.hima.mmp.MmpClient
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
    private const val PREFS_VOICE_SUBMITTED = "hima_voice_submitted_prefs"
    private const val KEY_VOICE_SUBMITTED_PREFIX = "female_voice_submitted_"

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
        // 2026-05-23 v26 — DISABLED per marketing. They keep only:
        // purchase, complete_registration, activate_app, app_install,
        // new_user_purchase, new_user_first_purchase, voice_verified,
        // 2min_call. Method kept as no-op so existing call sites compile.
        return
    }

    // -----------------------------------------------------------------
    // 2. View Content — fires when male views female profile
    // -----------------------------------------------------------------
    fun logViewContent(ctx: Context, contentId: String, contentType: String = "creator_profile", value: Double = 0.0) {
        // 2026-05-23 v26 — DISABLED per marketing.
        return
    }

    // -----------------------------------------------------------------
    // 3. Add Payment Info — fires when payment screen opens
    // -----------------------------------------------------------------
    fun logAddPaymentInfo(ctx: Context, success: Boolean = true) {
        // 2026-05-23 v26 — DISABLED per marketing.
        return
    }

    // -----------------------------------------------------------------
    // 4. Contact — fires on first call connection
    // -----------------------------------------------------------------
    fun logContact(ctx: Context, contentType: String = "voice_call") {
        // 2026-05-23 v26 — DISABLED per marketing. The 2-min-call event below
        // replaces this for true engagement signal.
        return
    }

    // -----------------------------------------------------------------
    // 5. Rate — fires on user rating submit
    // -----------------------------------------------------------------
    fun logRate(ctx: Context, rating: Int, maxRating: Int = 5, contentType: String = "creator") {
        // 2026-05-23 v26 — DISABLED per marketing.
        return
    }

    // -----------------------------------------------------------------
    // 2026-05-23 v26 — 2-Minute Call event (new, per marketing request)
    // -----------------------------------------------------------------
    /**
     * Fired ONCE per call when the connected call duration reaches 120 seconds.
     * Use a per-callId flag in the calling activity so this fires at most once
     * per call_id (even across reconnects or switch-audio-to-video).
     *
     * @param callId      the user_calls.id for this session (dedup key)
     * @param contentType "audio_call" / "video_call"
     * @param durationSec actual duration in seconds (>=120)
     */
    fun log2MinCall(ctx: Context, callId: Int, contentType: String, durationSec: Long) {
        try {
            val params = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, contentType)
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, callId.toString())
                putLong("duration_seconds", durationSec)
            }
            AppEventsLogger.newLogger(ctx).logEvent("2min_call", durationSec.toDouble(), params)

            val fbBundle = Bundle().apply {
                putString("content_type", contentType)
                putString("call_id", callId.toString())
                putLong("duration_seconds", durationSec)
            }
            BaseApplication.firebaseAnalytics.logEvent("2min_call", fbBundle)

            // Adjust (mirrors alongside Meta + Firebase).
            com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                "2min_call",
                params = mapOf(
                    "content_type" to contentType,
                    "call_id" to callId.toString(),
                    "duration_seconds" to durationSec
                )
            )

            Log.d(TAG, "2min_call fired: callId=$callId type=$contentType dur=${durationSec}s")
        } catch (t: Throwable) {
            Log.w(TAG, "log2MinCall failed: ${t.message}")
        }
    }

    // -----------------------------------------------------------------
    // Hindi registration completed (CMO request, F4)
    // -----------------------------------------------------------------
    /**
     * Fired when a user COMPLETES registration having selected Hindi.
     * Fires ALONGSIDE the standard all-users signup events (MMP trackSignup,
     * Meta COMPLETED_REGISTRATION, Firebase SIGN_UP) — it never replaces them.
     * Covers both male & female; `gender` lets marketing split the two.
     * Mirrored to BOTH Meta and Firebase per the always-mirror policy above.
     *
     * @param userId the newly-registered UserData.id
     * @param gender "Male" / "Female" (empty string if unknown)
     */
    fun logHindiRegistration(ctx: Context, userId: String, gender: String) {
        try {
            val params = Bundle().apply {
                putString("user_id", userId)
                putString("gender", gender)
                putString("language", "Hindi")
            }
            AppEventsLogger.newLogger(ctx).logEvent("hindi_registration_completed", params)

            val fbBundle = Bundle().apply {
                putString("user_id", userId)
                putString("gender", gender)
                putString("language", "Hindi")
            }
            BaseApplication.firebaseAnalytics.logEvent("hindi_registration_completed", fbBundle)

            // Adjust (mirrors alongside Meta + Firebase).
            com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                "hindi_registration_completed",
                params = mapOf("user_id" to userId, "gender" to gender, "language" to "Hindi")
            )

            Log.d(TAG, "hindi_registration_completed fired: userId=$userId gender=$gender")
        } catch (t: Throwable) {
            Log.w(TAG, "logHindiRegistration failed: ${t.message}")
        }
    }

    // -----------------------------------------------------------------
    // Female voice submitted (CMO request)
    // -----------------------------------------------------------------
    /**
     * Fired ONCE per user when a female successfully SUBMITS her registration
     * voice recording (the upload succeeds, status becomes pending review) — the
     * funnel step BEFORE [checkAndLogVoiceVerified]'s voice_verified, which only
     * fires later when admin APPROVES (status == 2).
     *
     * Idempotent per user: a SharedPreferences guard committed SYNCHRONOUSLY and
     * BEFORE emitting ensures a re-record after rejection, or a rapid re-entry,
     * cannot double-count the funnel (same rationale as the voice_verified guard).
     * A rare lost event is preferable to a double-counted one, so the guard stays
     * set regardless of individual platform emit failures.
     *
     * Mirrored to ALL platforms (Firebase, Meta, MMP/AppsFlyer, backend, Adjust)
     * per the always-mirror policy above.
     *
     * @param userId   the female UserData.id
     * @param gender   "Female" (passed through for marketing parity with voice_verified)
     * @param language the registration language selected on this screen
     */
    fun logFemaleVoiceSubmitted(ctx: Context, userId: Int, gender: String, language: String) {
        try {
            val prefs = ctx.getSharedPreferences(PREFS_VOICE_SUBMITTED, Context.MODE_PRIVATE)
            val key = "$KEY_VOICE_SUBMITTED_PREFIX$userId"
            if (prefs.getBoolean(key, false)) {
                Log.d(TAG, "female_voice_submitted already logged for user $userId — skip")
                return
            }
            // Persist the idempotency guard SYNCHRONOUSLY and BEFORE emitting so a
            // process kill mid-registration can't re-fire on next launch.
            prefs.edit().putBoolean(key, true).commit()

            val params = mapOf(
                "user_id" to "$userId",
                "gender" to gender,
                "language" to language
            )

            // 1. Firebase Analytics
            val fbBundle = Bundle().apply {
                putString("user_id", "$userId")
                putString("gender", gender)
                putString("language", language)
            }
            runCatching {
                BaseApplication.firebaseAnalytics.logEvent("female_voice_submitted", fbBundle)
            }.onFailure { Log.w(TAG, "female_voice_submitted Firebase emit failed: ${it.message}") }

            // 2. Meta/Facebook Analytics
            val metaParams = Bundle().apply {
                putString("user_id", "$userId")
                putString("gender", gender)
                putString("language", language)
            }
            runCatching {
                AppEventsLogger.newLogger(ctx).logEvent("female_voice_submitted", metaParams)
            }.onFailure { Log.w(TAG, "female_voice_submitted Meta emit failed: ${it.message}") }

            // 3. MMP / AppsFlyer
            runCatching {
                MmpClient.trackEvent(
                    eventName = "female_voice_submitted",
                    params = params,
                    customerUserId = "$userId"
                )
            }.onFailure { Log.w(TAG, "female_voice_submitted MMP emit failed: ${it.message}") }

            // 4. Backend (so it appears in the admin /app-events viewer)
            runCatching {
                AppEventLogger.logEvent(
                    context = ctx,
                    eventName = "female_voice_submitted",
                    platform = "firebase",
                    userId = userId,
                    params = params
                )
            }.onFailure { Log.w(TAG, "female_voice_submitted backend emit failed: ${it.message}") }

            // 5. Adjust
            runCatching {
                AdjustTracker.trackEvent("female_voice_submitted", params = params)
            }.onFailure { Log.w(TAG, "female_voice_submitted Adjust emit failed: ${it.message}") }

            Log.d(TAG, "female_voice_submitted fired: userId=$userId gender=$gender language=$language")
        } catch (t: Throwable) {
            Log.w(TAG, "logFemaleVoiceSubmitted failed: ${t.message}")
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

            // Adjust (mirrors alongside Meta + Firebase).
            com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                "subscribe",
                revenueInr = value.takeIf { it > 0.0 },
                params = mapOf("plan_id" to planId, "currency" to currency)
            )
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

                // Adjust (mirrors alongside Meta + Firebase).
                com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                    "d1mp",
                    revenueInr = cumulativeValue.toDouble(),
                    params = mapOf("currency" to currency, "purchase_count" to newCount)
                )

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
