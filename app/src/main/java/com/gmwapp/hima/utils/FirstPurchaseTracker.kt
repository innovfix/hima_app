package com.gmwapp.hima.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.gmwapp.hima.BaseApplication
import com.google.firebase.analytics.FirebaseAnalytics
import org.json.JSONObject

/**
 * `new_user_first_purchase` — the user's FIRST EVER recharge, once per user, for life.
 *
 * WHY THIS EXISTS
 * ---------------
 * The event used to be logged inline in WalletActivity/MainActivity behind two
 * device-local gates: `isNewUser(created_at)` (registered TODAY) and a
 * SharedPreferences flag. That made it "first purchase on signup day", not
 * "first purchase ever":
 *   - signed up Monday, first recharge Tuesday  -> never fired
 *   - registered 11:58 PM, paid 12:03 AM        -> never fired
 *   - reinstall / new phone, recharged again    -> fired a SECOND time
 *   - paid from PaymentActivity                 -> never fired (that screen had no such block)
 *
 * Truth now comes from the server: `is_first_recharge` on the PhonePe/Cashfree
 * check-status responses, which asks the ledger whether the user had any
 * `transactions.type = 'add_coins'` row before this order was created. That
 * survives reinstalls, new devices and day-N purchases.
 *
 * Fires to the same four sinks the old inline block used: Firebase, Meta,
 * our backend event log, and Adjust (token `fb4mf6` = New_User_First_Purchase).
 *
 * Fail-open: analytics must never crash a payment flow.
 */
object FirstPurchaseTracker {

    private const val TAG = "FirstPurchase"
    private const val EVENT = "new_user_first_purchase"

    /** Key on the check-status payloads. Absent/null => server didn't answer. */
    private const val SERVER_FLAG = "is_first_recharge"

    /**
     * Read the server's verdict off a check-status response.
     *
     * Returns null when the backend didn't send the field (older backend, or no
     * local payment row) — callers pass that through and [maybeFire] falls back
     * to the legacy same-day rule rather than guessing.
     */
    fun readFlag(json: JSONObject?): Boolean? {
        if (json == null) return null
        if (!json.has(SERVER_FLAG) || json.isNull(SERVER_FLAG)) return null
        return json.optBoolean(SERVER_FLAG, false)
    }

    /**
     * Log the event if this recharge is the user's first ever.
     *
     * @param isFirstRecharge server truth from [readFlag]; null = unknown, use the
     *   legacy "registered today" rule so behaviour is never worse than before the
     *   backend flag ships.
     */
    fun maybeFire(
        context: Context,
        isFirstRecharge: Boolean?,
        userId: Int?,
        coinId: String?,
        coinAmount: Double,
        createdAt: String?,
    ) {
        try {
            if (userId == null || userId == 0) return

            val firstEver = isFirstRecharge ?: isRegisteredToday(createdAt)
            if (!firstEver) {
                Log.d(TAG, "skip $EVENT for user $userId — not their first recharge (server=$isFirstRecharge)")
                return
            }

            // Per-user, per-install guard. NOT the source of truth any more — just a
            // belt-and-braces block against a double callback firing it twice on the
            // same device. Claimed BEFORE emitting so a race can't slip through.
            if (!claimOnce(userId)) {
                Log.d(TAG, "skip $EVENT — already logged for user $userId on this install")
                return
            }

            val firebaseBundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.CURRENCY, "INR")
                putDouble(FirebaseAnalytics.Param.VALUE, coinAmount)
                putString(FirebaseAnalytics.Param.ITEM_ID, coinId)
                putString("user_id", "$userId")
                putString("created_at", createdAt ?: "")
            }
            BaseApplication.firebaseAnalytics.logEvent(EVENT, firebaseBundle)

            val metaParams = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
                putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, coinAmount)
                putString("user_id", "$userId")
                putString("coin_id", "$coinId")
                putString("created_at", createdAt ?: "")
            }
            AppEventsLogger.newLogger(context).logEvent(EVENT, coinAmount, metaParams)

            AppEventLogger.logEvent(
                context = context,
                eventName = EVENT,
                platform = "firebase",
                userId = userId,
                params = AppEventLogger.bundleToMap(firebaseBundle),
                value = coinAmount
            )

            com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                EVENT,
                revenueInr = coinAmount,
                params = mapOf("user_id" to "$userId", "coin_id" to "$coinId")
            )

            Log.d(TAG, "✅ $EVENT logged for user $userId (₹$coinAmount, server=$isFirstRecharge)")
        } catch (t: Throwable) {
            Log.w(TAG, "maybeFire failed: ${t.message}")
        }
    }

    /**
     * Returns true the first time it is called for [userId] on this install, false
     * afterwards.
     */
    private fun claimOnce(userId: Int): Boolean {
        val prefs = BaseApplication.getInstance()?.getPrefs() ?: return true
        // Same key the old inline block used, so users who already fired the event
        // on this device don't fire it again after the update.
        val key = "first_purchase_logged_$userId"
        if (prefs.getString(key) != null) return false
        prefs.setString(key, "true")
        return true
    }

    /**
     * Legacy fallback ONLY (server said nothing): the pre-2026-07-28 rule —
     * `created_at` falls on today's device date.
     */
    private fun isRegisteredToday(createdAt: String?): Boolean {
        if (createdAt.isNullOrEmpty()) return false
        return try {
            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .parse(createdAt) ?: return false
            val created = java.util.Calendar.getInstance().apply { time = parsed }
            val today = java.util.Calendar.getInstance()
            created.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                created.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
        } catch (e: Exception) {
            Log.w(TAG, "created_at parse failed: $createdAt")
            false
        }
    }
}
