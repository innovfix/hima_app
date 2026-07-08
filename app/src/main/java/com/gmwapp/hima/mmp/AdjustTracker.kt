package com.gmwapp.hima.mmp

import android.app.Application
import android.util.Log
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.AdjustEvent
import com.adjust.sdk.LogLevel
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
     * names already sent to Meta / Firebase / MmpClient).
     *
     * TODO(Adjust dashboard): create each event under Hima App -> Events and
     * replace the matching "TODO_…" placeholder with its real 6-char token.
     * Any value still starting with "TODO_" is skipped at runtime.
     */
    private val EVENT_TOKENS: Map<String, String> = mapOf(
        "complete_registration"        to "TODO_complete_registration",
        "hindi_registration_completed" to "TODO_hindi_registration",
        "voice_verified"               to "TODO_voice_verified",
        "first_call"                   to "TODO_first_call",
        "call_started"                 to "TODO_call_started",
        "two_min_duration_completed"   to "TODO_two_min_duration",
        "2min_call"                    to "TODO_2min_call",
        "initial_checkout"             to "TODO_initial_checkout",
        "purchase"                     to "TODO_purchase",
        "new_user_purchase"            to "TODO_new_user_purchase",
        "new_user_first_purchase"      to "TODO_new_user_first_purchase",
        "start_trial"                  to "TODO_start_trial",
        "subscribe"                    to "TODO_subscribe",
        "d1mp"                         to "TODO_d1mp",
        "daily_active_user"            to "TODO_daily_active_user",
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
     * token yet. When [revenueInr] is a positive amount it is recorded as INR
     * revenue on the event.
     */
    fun trackEvent(
        eventName: String,
        revenueInr: Double? = null,
        params: Map<String, Any?>? = null
    ) {
        val token = EVENT_TOKENS[eventName]
        if (token.isNullOrBlank() || token.startsWith("TODO_")) {
            Log.d(TAG, "skip '$eventName' — no Adjust event token configured yet")
            return
        }
        try {
            val event = AdjustEvent(token)
            revenueInr?.let { if (it > 0.0) event.setRevenue(it, "INR") }
            params?.forEach { (k, v) -> if (v != null) event.addCallbackParameter(k, v.toString()) }
            Adjust.trackEvent(event)
            Log.d(TAG, "tracked '$eventName' token=$token revenue=$revenueInr")
        } catch (t: Throwable) {
            Log.w(TAG, "trackEvent '$eventName' failed: ${t.message}")
        }
    }
}
