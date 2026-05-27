package com.gmwapp.hima.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.appsflyer.AppsFlyerLib
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.retrofit.responses.SubscriptionStatusData

/**
 * In-memory cache of the user's subscription state, populated by
 * SubscriptionStatusResponse from /api/auth/subscription_status.
 *
 * Single source of truth across the app. Populated on resume from
 * Home / Chat / CancelSubscription / AutopayCheckout activities and
 * invalidated on OneSignal subscription_status push events. All
 * synchronous callsites in the app read through [isActive] /
 * [everActive] / [isNewUser] which return false until the first API
 * response — that's intentional: the app starts in a "not subscribed"
 * UI state and updates a moment later, same convention as the rest of
 * the backend-driven UI in this codebase.
 */
object SubscriptionStateCache {

    @Volatile private var cachedIsActive: Boolean? = null
    @Volatile private var cachedEverActive: Boolean? = null
    @Volatile private var cachedStatus: String? = null
    @Volatile private var cachedNextBillingDate: String? = null
    @Volatile private var cachedCancelledAt: String? = null
    @Volatile private var cachedIsNewUser: Boolean? = null
    @Volatile private var lastFetchedMs: Long = 0L

    fun update(data: SubscriptionStatusData) {
        val firstObservation = cachedIsActive == null
        val wasActive = cachedIsActive == true
        cachedIsActive = data.is_active
        cachedEverActive = data.ever_active
        cachedStatus = data.status
        cachedNextBillingDate = data.next_billing_date
        cachedCancelledAt = data.cancelled_at
        cachedIsNewUser = data.is_new_user
        lastFetchedMs = System.currentTimeMillis()

        // Marketing's StartTrial event needs to fire whenever the user goes
        // from inactive -> active, NOT only from AutopayCheckoutActivity. That
        // activity can be killed by Android during the Cashfree Custom Tab
        // redirect (especially Android 16); when the user returns they land
        // on ChatActivity or HomeFragment, and the activity-side hook never
        // fires. SubscriptionStateCache is a singleton object, survives
        // activity destruction, and is called from EVERY subscription_status
        // observer in the app, so firing here makes attribution robust.
        if (firstObservation && (data.is_active || data.ever_active == true)) {
            // Returning subscriber observed for the first time this app session.
            // They didn't convert just now — they've been paying. Silence the
            // per-user flag so a future inactive->active flap (e.g. billing
            // retry succeeding) doesn't fire either; only NEW users
            // transitioning for the first time should get attributed.
            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            if (userId != null) {
                BaseApplication.getInstance()
                    ?.getSharedPreferences("autopay_marketing_events", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putBoolean("start_trial_fired_$userId", true)
                    ?.apply()
            }
        } else if (!wasActive && data.is_active) {
            maybeFireStartTrialOnActivation()
        }
    }

    private fun maybeFireStartTrialOnActivation() {
        val app = BaseApplication.getInstance() ?: return
        val userId = app.getPrefs()?.getUserData()?.id ?: return
        val prefs = app.getSharedPreferences("autopay_marketing_events", Context.MODE_PRIVATE)
        // Per-user flag so a logout+different-account flow re-fires for the
        // new user (single-flag-per-install would suppress the legitimate
        // conversion of the second account on a shared device).
        val firedKey = "start_trial_fired_$userId"
        if (prefs.getBoolean(firedKey, false)) return

        val language = app.getPrefs()?.getUserData()?.language
        // Plan type isn't carried in subscription_status, so default to the
        // trial price (1.0 INR). Acceptable approximation — marketing cares
        // about conversion volume, not per-event price precision.
        val priceForPlan = 1.0
        val planType = "trial_new"

        try {
            val fbParams = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
                putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, priceForPlan)
                putString("plan_type", planType)
                putString("language", language ?: "")
                putString("user_id", "${userId ?: ""}")
            }
            AppEventsLogger.newLogger(app).logEvent(
                AppEventsConstants.EVENT_NAME_START_TRIAL,
                priceForPlan,
                fbParams
            )
            Log.i("StartTrialCache", "Meta StartTrial fired from cache transition for user $userId")
        } catch (t: Throwable) {
            Log.w("StartTrialCache", "Meta StartTrial failed: ${t.message}")
        }

        try {
            val afParams = HashMap<String, Any>()
            afParams["af_price"] = "$priceForPlan"
            afParams["af_currency"] = "INR"
            afParams["plan_type"] = planType
            afParams["language"] = language ?: ""
            AppsFlyerLib.getInstance().logEvent(app, "af_start_trial", afParams)
        } catch (t: Throwable) {
            Log.w("StartTrialCache", "AppsFlyer start_trial failed: ${t.message}")
        }

        try {
            val firebaseBundle = Bundle().apply {
                putString("plan_type", planType)
                putString("language", language ?: "")
                putString("user_id", "${userId ?: ""}")
                putDouble("price", priceForPlan)
            }
            BaseApplication.firebaseAnalytics.logEvent("start_trial", firebaseBundle)
        } catch (t: Throwable) {
            Log.w("StartTrialCache", "Firebase start_trial failed: ${t.message}")
        }

        prefs.edit().putBoolean(firedKey, true).apply()
    }

    fun clear() {
        cachedIsActive = null
        cachedEverActive = null
        cachedStatus = null
        cachedNextBillingDate = null
        cachedCancelledAt = null
        cachedIsNewUser = null
        lastFetchedMs = 0L
    }

    fun isActive(@Suppress("UNUSED_PARAMETER") context: android.content.Context): Boolean =
        cachedIsActive ?: false

    fun everActive(@Suppress("UNUSED_PARAMETER") context: android.content.Context): Boolean =
        cachedEverActive ?: false

    fun isNewUser(@Suppress("UNUSED_PARAMETER") context: android.content.Context): Boolean =
        cachedIsNewUser ?: false

    fun status(): String? = cachedStatus
    fun nextBillingDate(): String? = cachedNextBillingDate
    fun cancelledAt(): String? = cachedCancelledAt
    fun lastFetchedMs(): Long = lastFetchedMs

    /** True if the cache has been populated from the API at least once. */
    fun isPopulated(): Boolean = cachedIsActive != null

    /**
     * Real-time push events from the OneSignal subscription_status handler.
     * Foreground activities observe this to react instantly (re-fetch
     * status, show ll_chat_ended_banner, swap chat lock UI).
     */
    enum class PushEvent { FAILED, CANCELLED }

    private val _pushEvent = MutableLiveData<PushEvent>()
    val pushEvent: LiveData<PushEvent> = _pushEvent

    fun postPushEvent(event: PushEvent) {
        clear()
        _pushEvent.postValue(event)
    }
}
