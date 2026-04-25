package com.gmwapp.hima.utils

import android.content.Context
import com.gmwapp.hima.activities.DummySubscriptionActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dummy daily-coins reward while the fake subscription is active.
 * Credits 10 coins once per calendar day. Non-consuming display counter —
 * does not touch the real backend coins field.
 */
object DummyDailyCoins {
    private const val KEY_LAST_CREDIT_DATE = "daily_credit_date"
    private const val KEY_CLAIMED_DATE = "daily_claimed_date"
    private const val KEY_DAILY_COINS = "daily_coins"
    private const val KEY_PENDING_DIALOG = "pending_daily_dialog"
    const val DAILY_AMOUNT = 10

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Called on subscription switch-on. Credits if new day; always flags a pending dialog. */
    fun creditIfNewDay(context: Context) {
        val prefs = context.getSharedPreferences(
            DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE
        )
        val alreadyToday = prefs.getString(KEY_LAST_CREDIT_DATE, null) == todayKey()
        val editor = prefs.edit().putBoolean(KEY_PENDING_DIALOG, true)
        if (!alreadyToday) {
            editor.putString(KEY_LAST_CREDIT_DATE, todayKey())
                .putInt(KEY_DAILY_COINS, DAILY_AMOUNT)
        }
        editor.apply()
    }

    /** Called on Home resume. Flags dialog when a new day is detected; does NOT credit coins yet. */
    fun autoCreditIfDue(context: Context) {
        val prefs = context.getSharedPreferences(
            DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE
        )
        if (prefs.getString(KEY_LAST_CREDIT_DATE, null) == todayKey()) return
        prefs.edit().putBoolean(KEY_PENDING_DIALOG, true).apply()
    }

    /** Called when user taps "Claim 10 coins". Marks today as claimed so dialog won't show again today. */
    fun claimCoins(context: Context) {
        val prefs = context.getSharedPreferences(
            DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE
        )
        prefs.edit()
            .putString(KEY_CLAIMED_DATE, todayKey())
            .putString(KEY_LAST_CREDIT_DATE, todayKey())
            .putInt(KEY_DAILY_COINS, DAILY_AMOUNT)
            .apply()
    }

    fun getCoins(context: Context): Int {
        val prefs = context.getSharedPreferences(
            DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE
        )
        if (prefs.getString(KEY_LAST_CREDIT_DATE, null) != todayKey()) return 0
        return prefs.getInt(KEY_DAILY_COINS, 0)
    }

    /** True if user hasn't tapped the Claim button today. */
    fun hasPendingClaim(context: Context): Boolean {
        val prefs = context.getSharedPreferences(
            DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE
        )
        return prefs.getString(KEY_CLAIMED_DATE, null) != todayKey()
    }

    /** Returns true once per credit event; clears the flag. */
    fun consumePendingDialog(context: Context): Boolean {
        val prefs = context.getSharedPreferences(
            DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE
        )
        if (!prefs.getBoolean(KEY_PENDING_DIALOG, false)) return false
        prefs.edit().putBoolean(KEY_PENDING_DIALOG, false).apply()
        return true
    }
}
