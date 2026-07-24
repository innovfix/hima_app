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
     * names already sent to Meta / Firebase / MmpClient). Tokens from the Adjust
     * dashboard (Hima App -> Events).
     *
     * Any value still starting with "TODO_" is skipped at runtime.
     *
     * Note: `2min_call` and `two_min_duration_completed` both map to the
     * dashboard's single "two_minute_duration_complete" event.
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

        // 2026-07 Marketing spec — funnel / calls / retention.
        "phone_number_screen"          to "4y2oo3", // Phone_Number_Screen
        "otp_send"                     to "8gv0ju", // Otp_Send
        "otp_verified"                 to "p4fyor", // Otp_Verified
        "male_user_selected"           to "x5yoch", // Male_User_Selected
        "female_selected"              to "es64i1", // Female_Selected
        "language_selected"            to "tkm31e", // Language_Selected
        "app_login"                    to "rm90qp", // App_Login
        "details_entered"              to "kehj5p", // Details_Entered
        "random_video_call"            to "g18ppn", // Random_Video_Call
        "random_audio_call"            to "xksloz", // Random_Audio_Call
        "direct_call"                  to "n30nkl", // Direct_Call
        "repeat_purchase_day_1"        to "6nqhb6", // Repeat_Purchase_Day_1
        "repeat_purchase_day_2"        to "rty9xl", // Repeat_Purchase_Day_2
        "repeat_purchase_day_3"        to "l9en5n", // Repeat_Purchase_Day_3
        "repeat_purchase_day_7"        to "3xuco1", // Repeat_Purchase_Day_7
        "repeat_purchase_day_14"       to "ac4nj5", // Repeat_Purchase_Day_14
        "repeat_purchase_day_30"       to "5auybu", // Repeat_Purchase_Day_30
        "day_7_active"                 to "hizaty", // Day_7_Active
        "first_withdraw"               to "dbhns9", // First_Withdraw
        "button_enabled"               to "sf3tar", // Button_Enabled
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
