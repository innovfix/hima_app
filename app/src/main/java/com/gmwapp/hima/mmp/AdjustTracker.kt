package com.gmwapp.hima.mmp

import android.app.Application
import android.util.Log
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.AdjustEvent
import com.adjust.sdk.LogLevel
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig

/**
 * Adjust MMP wrapper.
 *
 * Runs ALONGSIDE the existing analytics stack — the in-house [MmpClient], Meta
 * [com.facebook.appevents.AppEventsLogger] and Firebase Analytics. It never
 * replaces any of them: every existing event site now ALSO calls
 * [AdjustTracker] so the same events are mirrored into Adjust.
 *
 * Init:
 *  - Call [init] once in Application.onCreate(). Install + session tracking then
 *    happens automatically (Adjust registers its own ActivityLifecycleCallbacks),
 *    so `app_install` / session ("activate app") need NO event token.
 *
 * Custom events:
 *  - Adjust identifies events by 6-char EVENT TOKENS created in the Adjust
 *    dashboard (Hima App -> Events), NOT by name. Until a real token is pasted
 *    into [EVENT_TOKENS], that event is a safe no-op (logged and skipped) so the
 *    app still builds and runs. Paste the tokens in and the events start flowing.
 *
 * All methods are fail-open: analytics breakage must never crash the user flow
 * that triggered the event.
 */
object AdjustTracker {

    private const val TAG = "AdjustTracker"

    @Volatile private var initialized = false

    /**
     * Adjust event tokens keyed by the app's own logical event names (the same
     * names already sent to Meta / Firebase / MmpClient). Tokens come from the
     * Adjust dashboard (Hima App -> Events), filled in 2026-07-09.
     *
     * Any value still starting with "TODO_" is skipped at runtime.
     *
     * Note: `2min_call` (male single-call 120s) and `two_min_duration_completed`
     * (female cumulative 2 min) both map to the dashboard's
     * "two_minute_duration_complete" event — the dashboard has one 2-minute
     * event, so both app events report to it.
     */
    private val EVENT_TOKENS: Map<String, String> = mapOf(
        "complete_registration"        to "3qvcls", // Complete_registration
        "hindi_registration_completed" to "1zvngu", // HIndi_Registration_Complete
        "voice_verified"               to "9hm0p7", // Voice_Verified
        "female_voice_submitted"       to "pme4wb", // female_voice_submitted
        "first_call"                   to "avhcv2", // First_Call
        "call_started"                 to "1dz7el", // Call_Started
        "two_min_duration_completed"   to "43kw07", // two_minute_duration_complete
        "2min_call"                    to "43kw07", // -> shares two_minute_duration_complete
        "initial_checkout"             to "h2ybx8", // Initial_checkout
        "purchase"                     to "mf46ew", // Purchases
        "new_user_purchase"            to "hsggks", // new_user_purchase
        "new_user_first_purchase"      to "fb4mf6", // New_User_First_Purchase
        "start_trial"                  to "11zogt", // Start_Trial
        "subscribe"                    to "4hn62l", // Subscribe
        "d1mp"                         to "nr9xw7", // D1MP
        "daily_active_user"            to "mxh5o0", // Daily_Active_Users
    )

    /** Must be called once in Application.onCreate(). Safe to call again (no-op). */
    fun init(app: Application) {
        if (initialized) return
        try {
            val environment = when (BuildConfig.ADJUST_ENVIRONMENT) {
                "production" -> AdjustConfig.ENVIRONMENT_PRODUCTION
                else -> AdjustConfig.ENVIRONMENT_SANDBOX
            }
            val config = AdjustConfig(app, BuildConfig.ADJUST_APP_TOKEN, environment)
            config.setLogLevel(if (BuildConfig.DEBUG) LogLevel.VERBOSE else LogLevel.WARN)
            // Remember more recent dedup ids than the default (10) so a purchase
            // that fires several times still collapses to one even across a busy
            // session. See trackEvent(dedupId=...).
            config.setEventDeduplicationIdsMaxSize(20)
            Adjust.initSdk(config)
            initialized = true
            Log.d(TAG, "Adjust initialised env=$environment token=${BuildConfig.ADJUST_APP_TOKEN}")
        } catch (t: Throwable) {
            Log.w(TAG, "Adjust init failed: ${t.message}")
        }
    }

    /**
     * Associate all subsequent Adjust events with the internal user id (mirrors
     * MmpClient.identify). Sent as a global callback parameter so every event
     * carries it. Call after login and after registration.
     */
    fun setUserId(userId: String?) {
        if (userId.isNullOrBlank() || userId == "0" || userId == "null") return
        try {
            Adjust.addGlobalCallbackParameter("customer_user_id", userId)
        } catch (t: Throwable) {
            Log.w(TAG, "setUserId failed: ${t.message}")
        }
    }

    /**
     * Mirror an app event into Adjust. Safe no-op if [eventName] has no real
     * token yet.
     *
     * @param revenueInr when a positive amount, recorded as INR revenue on the
     *   event. IMPORTANT: only pass this for events that represent REAL money
     *   earned exactly once (e.g. `purchase`). For funnel/segment/duplicate
     *   events (initial_checkout, new_user_purchase, d1mp, …) pass the amount as
     *   a normal [params] value instead — otherwise Adjust's total-revenue
     *   metric sums them and over-reports revenue.
     * @param dedupId when set, Adjust drops the event if it has already seen the
     *   same id (see setEventDeduplicationIdsMaxSize). Used on `purchase` so the
     *   several fires of one purchase collapse to a single counted event.
     */
    fun trackEvent(
        eventName: String,
        revenueInr: Double? = null,
        params: Map<String, Any?>? = null,
        dedupId: String? = null
    ) {
        val token = EVENT_TOKENS[eventName]
        if (token.isNullOrBlank() || token.startsWith("TODO_")) {
            Log.d(TAG, "skip '$eventName' — no Adjust event token configured yet")
            return
        }
        try {
            val event = AdjustEvent(token)
            revenueInr?.let { if (it > 0.0) event.setRevenue(it, "INR") }
            if (!dedupId.isNullOrBlank()) event.setDeduplicationId(dedupId)
            params?.forEach { (k, v) -> if (v != null) event.addCallbackParameter(k, v.toString()) }
            Adjust.trackEvent(event)
            Log.d(TAG, "tracked '$eventName' token=$token revenue=$revenueInr dedupId=$dedupId")
        } catch (t: Throwable) {
            Log.w(TAG, "trackEvent '$eventName' failed: ${t.message}")
        }
    }

    /**
     * Track a coin purchase with mutually-exclusive segmentation: fires EXACTLY
     * ONE of purchase / new_user_purchase / new_user_first_purchase, so each of
     * those events carries a CLEAN count + revenue and the three sum to the true
     * total with no double-counting.
     *
     *   returning user        -> purchase
     *   new user, first ever  -> new_user_first_purchase
     *   new user, repeat      -> new_user_purchase
     *
     * "first ever" is remembered with an ADJUST-OWNED SharedPreferences flag so it
     * never touches the Meta/Firebase first-purchase state. Adjust's deduplication
     * list is GLOBAL across events, so passing the same per-purchase [dedupId] to
     * every call means the several fires of one purchase (across retries AND across
     * payment activities) collapse to a single counted event — whichever fires
     * first wins, and because the most-specific event is the only one this method
     * emits per call, the segment stays correct.
     *
     * @param isNewUser caller-supplied so it matches the app's existing
     *   same-day-signup definition used for Meta/Firebase.
     */
    fun trackCoinPurchase(
        revenueInr: Double,
        userId: String?,
        coinId: String?,
        isNewUser: Boolean,
        dedupId: String? = null
    ) {
        if (revenueInr <= 0.0) return
        val params = mapOf<String, Any?>("user_id" to userId, "coin_id" to coinId)
        if (!isNewUser) {
            trackEvent("purchase", revenueInr = revenueInr, params = params, dedupId = dedupId)
            return
        }
        // New user: first-ever vs repeat, via an Adjust-owned flag (independent of
        // the Meta/Firebase "first_purchase_logged_*" key).
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val key = "adjust_first_purchase_logged_$userId"
        val isFirstEver = prefs?.getString(key).isNullOrEmpty()
        if (isFirstEver) {
            trackEvent("new_user_first_purchase", revenueInr = revenueInr, params = params, dedupId = dedupId)
            prefs?.setString(key, "true")
        } else {
            trackEvent("new_user_purchase", revenueInr = revenueInr, params = params, dedupId = dedupId)
        }
    }
}
