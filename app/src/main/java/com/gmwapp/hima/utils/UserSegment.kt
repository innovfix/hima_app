package com.gmwapp.hima.utils

import android.content.Context
import com.gmwapp.hima.BaseApplication

/**
 * Single source of truth for "is this a new user?" — used to decide
 * which trial-offer surface to show (₹1 trial sheet vs the ₹299/10d
 * old-user subscribe sheet).
 *
 * Definition (matches backend AutopayController::autopayInitiate):
 *   new = userData.coins == 0 AND !subscriptionState.everActive
 *
 * Replaces DevUserMode for production paths. DevUserMode still exists
 * as a QA dev-toggle override; once that's deleted (Chunk 9 of the
 * autopay wire-up), this is the only check.
 */
object UserSegment {

    fun isNewUser(context: Context): Boolean {
        // Honour the QA override (Settings > Dev > Subscription Switch
        // implicitly drives DevUserMode) for as long as that toggle exists.
        if (DevUserMode.hasOverride(context)) {
            return DevUserMode.isNewUser(context)
        }
        val coins = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.coins ?: 0
        val everActive = SubscriptionStateCache.everActive(context)
        return coins == 0 && !everActive
    }

    fun isOldUser(context: Context): Boolean = !isNewUser(context)
}
