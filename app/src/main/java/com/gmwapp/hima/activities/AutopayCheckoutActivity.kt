package com.gmwapp.hima.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.CFSubscriptionSession
import com.cashfree.pg.core.api.callback.CFSubscriptionResponseCallback
import com.cashfree.pg.core.api.exception.CFException
import com.cashfree.pg.core.api.subscription.CFSubscriptionPayment
import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.cashfree.pg.core.api.utils.CFSubscriptionResponse
import com.cashfree.pg.core.api.webcheckout.CFWebCheckoutTheme
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.utils.SubscriptionStateCache
import com.gmwapp.hima.viewmodels.AutopayViewModel
import com.google.android.material.button.MaterialButton
import com.appsflyer.AppsFlyerLib
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import dagger.hilt.android.AndroidEntryPoint

/**
 * Triggers /autopay_initiate, hands the cashfree_session_id to the
 * Cashfree Android SDK so the UPI mandate UI runs in-app, and falls
 * back to a Custom Tab redirect if the SDK can't be launched.
 *
 * The SDK's verify/failure callbacks plus the existing onResume status
 * re-fetch both reconcile against /subscription_status — the webhook is
 * still the source of truth, so the UI is correct even if a callback is
 * missed (e.g. SDK process killed).
 *
 * Caller must pass EXTRA_PLAN_TYPE = "trial_new" | "direct_old".
 */
@AndroidEntryPoint
class AutopayCheckoutActivity : AppCompatActivity(), CFSubscriptionResponseCallback {

    private val autopayViewModel: AutopayViewModel by viewModels()

    @javax.inject.Inject
    lateinit var apiManager: com.gmwapp.hima.retrofit.ApiManager

    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var btnClose: MaterialButton
    private lateinit var btnRetry: MaterialButton
    private lateinit var btnBack: ImageButton

    private var planType: String = PLAN_TRIAL_NEW
    private var checkoutLaunched: Boolean = false
    private var checkoutComplete: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Defensive guard: backend rejects autopay_initiate for non-autopay
        // languages anyway, but we don't want to render the checkout shell
        // (and confuse the user) if any caller leaks through with the gate
        // disabled. Existing subscribers keep access for cancel-flow paths.
        // Cold-start safe: isAutopayEligible returns true when the cache
        // says autopay OR when the cache hasn't populated yet AND the user's
        // stored language is in the static autopay whitelist. Without the
        // optimistic branch, a fresh-install Hindi user who taps Try on the
        // trial sheet (which already opens optimistically) hits this gate,
        // gets "Subscription is not available for your language", and the
        // checkout closes — gates have to agree.
        val autopayLanguage = com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEligible(this)
        val ever = com.gmwapp.hima.utils.SubscriptionStateCache.everActive(this)
        val active = com.gmwapp.hima.utils.SubscriptionStateCache.isActive(this)
        val reSubEnabled = com.gmwapp.hima.utils.LanguageFeatureCache.isReSubscriptionEnabled(this)
        if (!autopayLanguage && !ever) {
            Toast.makeText(
                this,
                "Subscription is not available for your language.",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }
        // Per-language re-enable prevention: lapsed users on a re-sub-blocked
        // language should never reach checkout — entry surfaces are gated,
        // but this catches stale taps and any deeplink path.
        if (ever && !active && !reSubEnabled) {
            Toast.makeText(
                this,
                "Re-subscription is not available for your language.",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        setContentView(R.layout.activity_autopay_checkout)

        progress = findViewById(R.id.progress)
        status = findViewById(R.id.tv_status)
        statusTitle = findViewById(R.id.tv_status_title)
        statusIcon = findViewById(R.id.iv_status_icon)
        btnClose = findViewById(R.id.btn_close)
        btnRetry = findViewById(R.id.btn_retry)
        btnBack = findViewById(R.id.btn_back)
        btnClose.setOnClickListener { finish() }
        btnBack.setOnClickListener { finish() }
        btnRetry.setOnClickListener { retryInitiate() }

        planType = intent.getStringExtra(EXTRA_PLAN_TYPE) ?: PLAN_TRIAL_NEW

        // Register SDK callback BEFORE doSubscriptionPayment. SDK requires
        // this in onCreate so it can re-attach the callback after process
        // recreation (e.g. when the system kills us while UPI app is open).
        try {
            CFPaymentGatewayService.getInstance().setSubscriptionCheckoutCallback(this)
        } catch (e: CFException) {
            Log.e(TAG, "Cashfree setSubscriptionCheckoutCallback failed", e)
        }

        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
        if (userId == null) {
            showFailure("Session expired", "Please log in again.")
            return
        }

        // Initiate response → launch SDK (or fallback to Custom Tab).
        autopayViewModel.initiateLiveData.observe(this) { resp ->
            if (resp == null || checkoutLaunched) return@observe
            if (!resp.success || resp.data == null) {
                showFailure("Couldn't start autopay", resp?.message ?: "Please try again.")
                return@observe
            }
            val data = resp.data!!
            val sessionId = data.cashfree_session_id
            val subscriptionId = data.cashfree_subscription_id
            // Prefer native SDK when we have both IDs. Fallback to redirect
            // URL only if either is missing (defensive — backend always
            // returns both, but a stale build or transient bug shouldn't
            // hard-block the user).
            if (!sessionId.isNullOrEmpty() && !subscriptionId.isNullOrEmpty()) {
                checkoutLaunched = true
                showLoading("Opening UPI mandate", "Approve the autopay request in your UPI app.")
                try {
                    launchCashfreeSdk(sessionId, subscriptionId)
                } catch (e: CFException) {
                    Log.e(TAG, "Cashfree SDK launch failed, falling back to browser", e)
                    fallbackToBrowser(data.redirect_url)
                }
            } else if (!data.redirect_url.isNullOrEmpty()) {
                checkoutLaunched = true
                showLoading("Opening UPI mandate", "Approve the autopay request in your UPI app.")
                fallbackToBrowser(data.redirect_url)
            } else {
                showFailure("Couldn't start autopay", "Please try again.")
            }
        }

        // Status re-fetch (called on resume + after SDK callback) →
        // close on success.
        autopayViewModel.statusLiveData.observe(this) { resp ->
            val data = resp?.data ?: return@observe
            SubscriptionStateCache.update(data)
            if (data.is_active && !checkoutComplete) {
                checkoutComplete = true
                // 2026-05-26 — marketing's StartTrial event. Fires here
                // (and ONLY here) so it represents an actual completed
                // payment, not just intent. Mirrors initial_checkout
                // pattern in MainActivity: same event on FB + AppsFlyer
                // + Firebase + our backend so every marketing dashboard
                // sees it. Guarded by !checkoutComplete so the observer
                // re-firing on rotation can't double-count.
                fireStartTrialEventsAllPlatforms()
                Toast.makeText(this, "Autopay active. Enjoy!", Toast.LENGTH_SHORT).show()
                finish()
            } else if (checkoutLaunched && !data.is_active) {
                // User came back without completing — show retry/close.
                showFailure(
                    "Mandate not yet active",
                    "Your bank may still be processing. Try again or close and come back later."
                )
            }
        }

        autopayViewModel.errorLiveData.observe(this) { msg ->
            showFailure("Network error", msg ?: "Please check your connection and try again.")
        }

        // Kick off.
        showLoading("Setting up autopay", "Just a moment while we prepare your subscription.")
        autopayViewModel.autopayInitiate(userId, planType)
    }

    override fun onResume() {
        super.onResume()
        // After the SDK closes (success/failure) or the user returns from
        // the browser fallback, re-check status. The webhook should have
        // already flipped the server-side state — but if the SDK callback
        // never fired (process kill, intent app crash), this is our backstop.
        if (checkoutLaunched && !checkoutComplete) {
            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
            showLoading("Confirming subscription", "Checking with our server…")
            autopayViewModel.subscriptionStatus(userId)
        }
    }

    private fun launchCashfreeSdk(sessionId: String, subscriptionId: String) {
        // Production: backend always creates real Cashfree subscriptions
        // against api.cashfree.com/pg (see CashfreeSubscriptionsClient
        // guardEnvParity), so the SDK must point at PRODUCTION too. A
        // sandbox session ID against PRODUCTION env (or vice versa) gets
        // rejected as "Invalid session_id".
        val cfSession = CFSubscriptionSession.CFSubscriptionSessionBuilder()
            .setEnvironment(CFSubscriptionSession.Environment.PRODUCTION)
            .setSubscriptionSessionID(sessionId)
            .setSubscriptionId(subscriptionId)
            .build()

        val brandHex = String.format(
            "#%06X",
            0xFFFFFF and ContextCompat.getColor(this, R.color.colorPrimary)
        )
        val theme = CFWebCheckoutTheme.CFWebCheckoutThemeBuilder()
            .setNavigationBarBackgroundColor(brandHex)
            .setNavigationBarTextColor("#FFFFFF")
            .build()

        val payment = CFSubscriptionPayment.CFSubscriptionCheckoutBuilder()
            .setSubscriptionSession(cfSession)
            .setSubscriptionUITheme(theme)
            .build()

        CFPaymentGatewayService.getInstance()
            .doSubscriptionPayment(this, payment)
    }

    private fun fallbackToBrowser(url: String?) {
        if (url.isNullOrEmpty()) {
            showFailure("Couldn't start autopay", "Please try again.")
            return
        }
        try {
            openInCustomTab(url)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e2: Exception) {
                showFailure(
                    "No browser available",
                    "Install a browser app to complete the UPI mandate."
                )
            }
        }
    }

    private fun openInCustomTab(url: String) {
        val brand = ContextCompat.getColor(this, R.color.colorPrimary)
        val colorScheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(brand)
            .build()
        val tab = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorScheme)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()
        tab.launchUrl(this, Uri.parse(url))
    }

    private fun showLoading(title: String, detail: String) {
        statusTitle.text = title
        status.text = detail
        progress.visibility = View.VISIBLE
        statusIcon.visibility = View.GONE
        btnRetry.visibility = View.GONE
        btnClose.visibility = View.GONE
    }

    private fun showFailure(title: String, detail: String) {
        statusTitle.text = title
        status.text = detail
        progress.visibility = View.GONE
        statusIcon.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE
        btnClose.visibility = View.VISIBLE
    }

    private fun retryInitiate() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: run {
            showFailure("Session expired", "Please log in again.")
            return
        }
        checkoutLaunched = false
        checkoutComplete = false
        showLoading("Setting up autopay", "Just a moment while we prepare your subscription.")
        autopayViewModel.autopayInitiate(userId, planType)
    }

    // ----- Cashfree SDK callbacks -----
    // SDK fires onSubscriptionVerify after a successful mandate authorisation.
    // We don't trust the SDK payload directly — the webhook is the source
    // of truth — so we just re-poll subscriptionStatus to close the screen.
    override fun onSubscriptionVerify(cfSubscriptionResponse: CFSubscriptionResponse?) {
        Log.d(TAG, "Cashfree onSubscriptionVerify resp=$cfSubscriptionResponse")
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        showLoading("Confirming mandate", "Activating your subscription…")
        autopayViewModel.subscriptionStatus(userId)
    }

    override fun onSubscriptionFailure(cfErrorResponse: CFErrorResponse?) {
        val msg = cfErrorResponse?.message ?: "Payment was cancelled."
        Log.w(TAG, "Cashfree onSubscriptionFailure msg=$msg")
        // Don't surface a hard "failure" toast — the webhook may still
        // have flipped state if the user actually completed and then
        // dismissed the SDK. onResume polls status and closes us if so.
        showFailure("Payment didn't go through", msg)
    }

    /**
     * Fire-and-forget marketing event: paid trial activated. Sent on
     * Facebook, AppsFlyer, Firebase, and our own backend in parallel.
     * Each platform wrapped in try/catch so one SDK's failure doesn't
     * block the others, and none can crash the checkout flow.
     */
    private fun fireStartTrialEventsAllPlatforms() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
        val language = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.language
        val priceForPlan = if (planType == PLAN_DIRECT_OLD) 299.0 else 1.0

        // 1. Facebook (Meta) — what marketing was checking in Events Manager
        try {
            val fbParams = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
                putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, priceForPlan)
                putString("plan_type", planType)
                putString("language", language ?: "")
                putString("user_id", "${userId ?: ""}")
            }
            AppEventsLogger.newLogger(this).logEvent(
                AppEventsConstants.EVENT_NAME_START_TRIAL,
                priceForPlan,
                fbParams
            )
        } catch (t: Throwable) {
            Log.w("FB_Event", "Meta StartTrial failed: ${t.message}")
        }

        // 2. AppsFlyer
        try {
            val afParams = HashMap<String, Any>()
            afParams["af_price"] = "$priceForPlan"
            afParams["af_currency"] = "INR"
            afParams["plan_type"] = planType
            afParams["language"] = language ?: ""
            AppsFlyerLib.getInstance().logEvent(this, "af_start_trial", afParams)
        } catch (t: Throwable) {
            Log.w("AF_Event", "AppsFlyer start_trial failed: ${t.message}")
        }

        // 3. Firebase Analytics / Google Analytics 4
        try {
            val firebaseBundle = Bundle().apply {
                putString("plan_type", planType)
                putString("language", language ?: "")
                putString("user_id", "${userId ?: ""}")
                putDouble("price", priceForPlan)
            }
            BaseApplication.firebaseAnalytics.logEvent("start_trial", firebaseBundle)
        } catch (t: Throwable) {
            Log.w("FB_Analytics", "Firebase start_trial failed: ${t.message}")
        }

        // 4. Our own backend — funnel view at /autopay-events admin
        try {
            com.gmwapp.hima.utils.AutopayEventTracker.track(
                apiManager,
                "trial_activated",
                mapOf(
                    "plan_type" to planType,
                    "language" to (language ?: ""),
                    "price" to priceForPlan,
                )
            )
        } catch (t: Throwable) {
            Log.w("BackendEvent", "Backend trial_activated failed: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "AutopayCheckout"

        const val EXTRA_PLAN_TYPE = "extra_plan_type"
        const val PLAN_TRIAL_NEW = "trial_new"
        const val PLAN_DIRECT_OLD = "direct_old"

        @JvmStatic
        fun intentFor(ctx: android.content.Context, planType: String): Intent =
            Intent(ctx, AutopayCheckoutActivity::class.java)
                .putExtra(EXTRA_PLAN_TYPE, planType)
    }
}
