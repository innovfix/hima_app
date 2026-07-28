package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.gmwapp.hima.mmp.MmpClient
import com.bumptech.glide.Glide
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.base.exception.CFException
import com.cashfree.pg.core.api.CFSession
import com.cashfree.pg.core.api.callback.CFCheckoutResponseCallback
import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.cashfree.pg.core.api.webcheckout.CFWebCheckoutPayment
import com.cashfree.pg.ui.api.upi.intent.CFUPIIntentCheckout
import com.cashfree.pg.ui.api.upi.intent.CFUPIIntentCheckoutPayment
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BillingManager.BillingManager
import com.gmwapp.hima.R
import com.gmwapp.hima.TokenGenerator
import com.gmwapp.hima.YoutubeRechargeActivity
import com.gmwapp.hima.adapters.CoinAdapter
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.ActivityWalletBinding
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.retrofit.responses.NewRazorpayLinkResponse
import com.gmwapp.hima.retrofit.responses.RazorPayApiResponse
import com.gmwapp.hima.utils.Config
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.AppEventLogger
import com.gmwapp.hima.utils.applySystemBarInsets
import androidx.appcompat.app.AlertDialog
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.CashfreeOrderViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UpiPaymentViewModel
import com.gmwapp.hima.viewmodels.UpiViewModel
import com.gmwapp.hima.viewmodels.WalletViewModel
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CheckReferralOfferResponse
import retrofit2.Call
import retrofit2.Response
import com.google.androidbrowserhelper.trusted.LauncherActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.phonepe.intent.sdk.api.PhonePeInitException
import com.phonepe.intent.sdk.api.PhonePeKt
import com.phonepe.intent.sdk.api.models.PhonePeEnvironment
import dagger.hilt.android.AndroidEntryPoint
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response as OkHttpResponse
import org.json.JSONObject
import java.io.IOException
import java.security.Key
import java.util.Date
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import com.gmwapp.hima.utils.applyLightSystemBars


@AndroidEntryPoint
class WalletActivity : BaseActivity(), CFCheckoutResponseCallback {

    companion object {
        // I021 — set by the in-call low-balance banner so this activity opens
        // with the user's chosen package already selected. -1 (or missing) =
        // no pre-selection, default behaviour (first item selected).
        const val EXTRA_PRE_SELECTED_COIN_ID = "pre_selected_coin_id"
    }

    lateinit var binding: ActivityWalletBinding
    private val WalletViewModel: WalletViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val upiPaymentViewModel: UpiPaymentViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()

    private lateinit var call: Call<ApiResponse>
    private lateinit var callRazor: Call<RazorPayApiResponse>
    private lateinit var callNewRazorPay: Call<NewRazorpayLinkResponse>

    val profileViewModel: ProfileViewModel by viewModels()
    private val cashfreeOrderViewModel : CashfreeOrderViewModel by viewModels()

    @Inject
    lateinit var apiManager: ApiManager

    private lateinit var selectedCoin : String
    private lateinit var selectedSavePercent : String

    private lateinit var email :String
    private lateinit var mobile :String
    private lateinit var total_amount :String
    private lateinit var userIdWithPoints :String
    private lateinit var name :String
    private val fetchedSkuList: MutableList<String> = mutableListOf()

    var fromDeepLink = false

    val apiService = RetrofitClient.instance


    private var billingManager: BillingManager? = null

    /** Login response for payment type must be observed once; onResume only re-fetches login. */
    private var paymentTypeLoginObserverRegistered = false

    private val viewModel: UpiViewModel by viewModels()
    var amount = ""
    var pointsId = ""
    var paymentGateway = ""

    private var lastOrderId: String = ""
    private var cashfreeLastOrderId: String = ""
    private var isPhonePeInitialized = false

    private val cfEnvironment = CFSession.Environment.PRODUCTION

    var messageCameWhenIsAlive = 0

    /** True only for Google Play billing flow — [updatePurchaseOnMeta] must run when [WalletViewModel.navigateToMain] fires. */
    private var pendingPurchaseMetaFromPlayBilling = false

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val statusCode = result.resultCode
        val status = result.data?.getStringExtra("status") ?: "UNKNOWN"
        Log.d("PhonePe", "SDK resultCode: $statusCode, status: $status")

        if (lastOrderId.isNotEmpty()) {
            Log.d("lastOrderId","$lastOrderId")
            checkOrderStatus(lastOrderId)
        }

        if (statusCode == RESULT_OK) {
            // showAppToast("Payment Successful", Toast.LENGTH_LONG)
        } else {
            //  showAppToast("Payment Failed or Cancelled", Toast.LENGTH_LONG)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        applySystemBarInsets(binding.root, R.color.white, darkStatusBarIcons = true)

        // 2026-05-22 — Add Payment Info event when wallet/payment screen opens.
        // Fires to both Meta (AppEventsConstants.EVENT_NAME_ADDED_PAYMENT_INFO)
        // and Firebase (Event.ADD_PAYMENT_INFO).
        com.gmwapp.hima.utils.HimaAnalytics.logAddPaymentInfo(this, success = true)

        applyLightSystemBars()

        fromDeepLink = intent.getBooleanExtra("from_deeplink", false)

        setupPaymentTypeLoginObserverOnce()
        checkIndividualPaymentType()
        initUI()
        // Welcome-gift banner is triggered from onResume (gate needs subscription
        // state, which isn't guaranteed populated this early in onCreate).
        setupWalletObservers()
        intializePhonpe()
        checkReferralOffer()

        // Re-gate when admin flips enabled_feature or re_subscription_enabled
        // while Wallet is open. Registered once in onCreate (the lifecycle
        // owner cleans this up on destroy).
        com.gmwapp.hima.utils.LanguageFeatureCache.updates.observe(this) {
            refreshSubscribeBannerVisibility()
        }

        messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0



        onBackPressedDispatcher.addCallback(this) {
            if (fromDeepLink){
            startActivity(Intent(this@WalletActivity, MainActivity::class.java))
            finish()
        }else if (messageCameWhenIsAlive == 0) {
            val intent = Intent(this@WalletActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
                startActivity(intent)
                finish()
            }else{
            finish()
        }

        }

        try {
            CFPaymentGatewayService.getInstance().setCheckoutCallback(this)
        } catch (e: CFException) {
            e.printStackTrace()
            FirebaseCrashlytics.getInstance().recordException(e)
        }



    }

    override fun onResume() {
        super.onResume()
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.id?.let { profileViewModel.getUsers(it) }
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.let { WalletViewModel.getCoins(it.id) }
        checkIndividualPaymentType()
        refreshSubscribeBannerVisibility()
        showWalletTrialDialog()
        Log.d("cashfreeLastOrderId","$cashfreeLastOrderId")
        if (cashfreeLastOrderId.isNotEmpty()){
            checkCashfreeOderStatus(cashfreeLastOrderId)
            cashfreeLastOrderId = "" // reset so it won't run again

        }
    }

    /**
     * Subscribe banner visibility — pure state read, safe to call repeatedly.
     * Hidden for: languages where admin disabled autopay, users currently
     * active, and lapsed users when admin has disabled re-subscription
     * for their language. Lapsed users in re-subscription-allowed
     * languages DO see the banner — the CTA routes them to PLAN_DIRECT_OLD
     * (₹299 first charge, no second free trial).
     */
    /**
     * Welcome-gift (₹1 trial) offer shown as a DIALOG over the wallet. Gated to
     * male users on autopay languages who have never had an autopay mandate
     * (WelcomeGiftPromo). The ₹1 button opens the real autopay checkout; Skip Now
     * closes. Shown once per Wallet visit, only once subscription state is known.
     */
    private var walletTrialDialog: android.app.Dialog? = null
    private var walletTrialDialogShown = false

    private fun showWalletTrialDialog() {
        if (isFinishing || isDestroyed) return
        if (walletTrialDialogShown) return
        if (walletTrialDialog?.isShowing == true) return
        if (!com.gmwapp.hima.utils.WelcomeGiftPromo.isEligible(this)) return
        walletTrialDialogShown = true
        walletTrialDialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(R.layout.view_wallet_trial_card)
            setCancelable(true)
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            window?.setBackgroundDrawableResource(android.R.color.transparent)

            val flipper = findViewById<android.widget.ViewFlipper>(R.id.wgFlipper)
            flipper?.startFlipping()
            setOnDismissListener { flipper?.stopFlipping() }

            findViewById<View>(R.id.tvSkip)?.setOnClickListener { dismiss() }
            findViewById<View>(R.id.btnTrial)?.setOnClickListener {
                startActivity(
                    AutopayCheckoutActivity.intentFor(
                        this@WalletActivity,
                        AutopayCheckoutActivity.PLAN_TRIAL_NEW
                    )
                )
                dismiss()
            }
            show()
        }
    }

    private fun refreshSubscribeBannerVisibility() {
        val isActive = com.gmwapp.hima.utils.SubscriptionStateCache.isActive(this)
        val everActive = com.gmwapp.hima.utils.SubscriptionStateCache.everActive(this)
        val autopayLanguage = com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEnabled(this)
        val reSubEnabled = com.gmwapp.hima.utils.LanguageFeatureCache.isReSubscriptionEnabled(this)
        val isMale = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender ==
            com.gmwapp.hima.constants.DConstants.MALE
        // Re-subscribe banner: ONLY for cancelled/lapsed users (ever had a mandate,
        // not active now) on an autopay language where admin allows re-subscription.
        // Never-active users get the ₹1 welcome dialog instead, so the two surfaces
        // never collide; active subscribers see nothing.
        val showResubBanner = isMale && autopayLanguage && everActive && !isActive && reSubEnabled
        binding.cvSubscribeBanner.visibility = if (showResubBanner) View.VISIBLE else View.GONE
        // Banner only shows for lapsed (everActive) users → openCheckout picks
        // PLAN_DIRECT_OLD; CTA reads "₹299 only".
        binding.btnSubscribeNow.text = if (!everActive) "₹1 only" else "₹299 only"
    }


    fun intializePhonpe(){
        try {
            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            val userId = userData?.id?.toString().orEmpty()
            val isInitialized = PhonePeKt.init(
                context = this,
                merchantId = "SU2505161111008337542920", // Replace in PROD
                flowId = userId,
                phonePeEnvironment = PhonePeEnvironment.RELEASE, // Use RELEASE in prod
                enableLogging = true,
                appId = null
            )

            if (isInitialized) {
                isPhonePeInitialized = true
            } else {
                Log.e("PhonePe", "SDK Initialization Failed")
                showAppToast("PhonePe SDK init failed", Toast.LENGTH_SHORT)
            }
        } catch (e: Exception) {
            Log.e("PhonePe", "init failed", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            showAppToast("PhonePe SDK init failed", Toast.LENGTH_SHORT)
        }
    }

    private fun fetchOrderFromBackend(coinId: String) {
        val client = OkHttpClient()

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val user_id = userData?.id.toString()
        val formBody = FormBody.Builder()
            .add("user_id", user_id)
            .add("coins_id", coinId)
            .build()

        Log.d("SelectedCoinID", " $coinId")

        val request = Request.Builder()
            .url("${Config.API_ROOT}phonepe/live/create-order") // Should return { token, orderId }
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    showAppToast("API Failure: ${e.message}", Toast.LENGTH_SHORT)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: OkHttpResponse) {
                val responseStr = response.body?.string() ?: return
                Log.d("PhonePeResponse", "Backend Response: $responseStr")

                try {
                    val json = JSONObject(responseStr)
                    val token = json.getString("token")
                    val orderId = json.getString("orderId")

                    lastOrderId = orderId

                    runOnUiThread {
                        startPhonePeCheckout(orderId, token)
                    }

                } catch (e: Exception) {
                    runOnUiThread {
                        showAppToast("Invalid server response", Toast.LENGTH_SHORT)
                        Log.d("PhonpeException","$e")
                    }
                }
            }
        })
    }

    private fun startPhonePeCheckout(orderId: String, token: String) {
        if (!isAnyUPIAppInstalled()) {
            showAppToast("No UPI app installed", Toast.LENGTH_LONG)
            return
        }

        // v1106 (2026-05-29) — guard against destroyed activity. 3 users on
        // v20-v1105 crashed with "Attempting to launch an unregistered
        // ActivityResultLauncher" because this method can fire from an
        // async network callback after the user has navigated away. Catch
        // IllegalStateException too in case Android internals throw it.
        if (isFinishing || isDestroyed) {
            Log.w("PhonePe", "startPhonePeCheckout skipped — activity finishing/destroyed")
            return
        }

        try {
            PhonePeKt.startCheckoutPage(
                context = this,
                token = token,
                orderId = orderId,
                activityResultLauncher = activityResultLauncher
            )
        } catch (e: PhonePeInitException) {
            Log.e("PhonePe", "Checkout Failed: ${e.message}")
            showAppToast("Could not start payment", Toast.LENGTH_SHORT)
        } catch (e: IllegalStateException) {
            Log.e("PhonePe", "ActivityResultLauncher not ready: ${e.message}", e)
            showAppToast("Could not start payment. Please try again.", Toast.LENGTH_SHORT)
        } catch (e: Throwable) {
            Log.e("PhonePe", "Unexpected error launching checkout: ${e.message}", e)
            showAppToast("Could not start payment. Please try again.", Toast.LENGTH_SHORT)
        }
    }

    private fun checkOrderStatus(orderId: String) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var user_id = userData?.id
        val client = OkHttpClient()

        val json = """{ "orderId": "$orderId" }"""
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, json)

        val request = Request.Builder()
            .url("${Config.API_ROOT}phonepe/live/check-status")
            .post(body) // ✅ Correct method
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    showAppToast("Status check failed", Toast.LENGTH_SHORT)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: OkHttpResponse) {
                // B-v1110 #2 — the PhonePe status response was parsed with
                // getJSONObject/getString and no try/catch, so any unexpected
                // shape (e.g. missing "state") threw JSONException and crashed the
                // wallet mid-payment. Coins are still credited by the backend
                // webhook/cron, so we must never tell the user "Failed" on a parse
                // error — only show a non-success message, never a false failure.
                try {
                    val resultStr = response.body?.string()
                    if (resultStr.isNullOrBlank()) {
                        runOnUiThread {
                            showAppToast("Couldn't confirm payment. If money was deducted, coins will be added shortly.", Toast.LENGTH_LONG)
                        }
                        return
                    }
                    val json = JSONObject(resultStr)
                    val state = json.optJSONObject("phonepe_status")?.optString("state") ?: ""
                    val localRecord = json.optJSONObject("local_record")
                    val coin_id = localRecord?.optString("coin_id") ?: ""
                    val order_id = localRecord?.optString("order_id") ?: ""
                    Log.d("PhonePeOrderStatus", "Order Status: $resultStr")
                    Log.d("PhonePeOrderState", "Order State: $state,  Coin_id : $coin_id , Order_id :$order_id ")
                    Log.d("PhoneperesultStr", "$resultStr")

                    val isFirstRecharge = com.gmwapp.hima.utils.FirstPurchaseTracker.readFlag(json)

                    if (state == "COMPLETED") {
                        runOnUiThread {
                            showAppToast("Payment Successful", Toast.LENGTH_LONG)
                            user_id?.let { WalletViewModel.addCoins(it, coin_id, 1, order_id, "Coins purchased") }
                            updatePurchaseOnMeta(isFirstRecharge)
                            // Notification conversion: attribute this recharge (₹) to the
                            // most recent notification the user received.
                            val app = BaseApplication.getInstance()
                            val rupees = app?.getPrefs()?.getString("last_coin_amount")?.toDoubleOrNull()?.toInt() ?: 0
                            app?.trackNotificationConversion(app.getLastNotificationId(), "purchase", rupees)
                        }
                    } else {
                        runOnUiThread {
                            showAppToast("Payment is being confirmed — coins will be added shortly.", Toast.LENGTH_LONG)
                        }
                    }
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                    runOnUiThread {
                        showAppToast("Couldn't confirm status. If money was deducted, coins will be added shortly.", Toast.LENGTH_LONG)
                    }
                }
            }
        })
    }

    private fun isAnyUPIAppInstalled(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("upi://pay")
        val pm = packageManager
        val activities = pm.queryIntentActivities(intent, 0)
        return activities.isNotEmpty()
    }

    /**
     * Single registration for profile + navigate observers. Nested observers caused duplicate
     * emissions and NPEs when touching [binding] after teardown (Crashlytics #1).
     */
    private fun setupWalletObservers() {
        profileViewModel.getUserLiveData.observe(this, Observer { response ->
            if (isFinishing || isDestroyed) return@Observer
            try {
                response?.data?.let { u ->
                    // B075 — wallet refresh: preserve toggle / DND intent.
                    BaseApplication.getInstance()?.getPrefs()?.setUserDataPreservingLocalIntent(u)
                }
                val coins = response?.data?.coins
                Log.d("coinsUpdated_", "${coins ?: 0}")
                binding.tvCoins.text = (coins ?: 0).toString()
            } catch (e: Exception) {
                Log.e("WalletActivity", "getUserLiveData observer", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        })

        WalletViewModel.navigateToMain.observe(this, Observer { shouldNavigate ->
            if (isFinishing || isDestroyed) return@Observer
            if (!shouldNavigate) return@Observer
            try {
                showAppToast("Coin purchased successfully", Toast.LENGTH_SHORT)
                if (pendingPurchaseMetaFromPlayBilling) {
                    updatePurchaseOnMeta()
                    pendingPurchaseMetaFromPlayBilling = false
                }
                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                    profileViewModel.getUsers(it)
                }
                WalletViewModel._navigateToMain.postValue(false)
            } catch (e: Exception) {
                Log.e("WalletActivity", "navigateToMain observer", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        })
    }

    private fun initUI() {

        accountViewModel.getSettings()

        upiPaymentViewModel.upiPaymentLiveData.observe(this, Observer { response ->
            if (response != null && response.status) {
                val paymentUrl = response.data.firstOrNull()?.payment_url

                if (!paymentUrl.isNullOrEmpty()) {
                    Log.d("UPI Payment", "Payment URL: $paymentUrl")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
                    startActivity(intent)
                } else {
                    Log.e("UPI Payment Error", "Payment URL is null or empty")
                    showAppToast("Payment URL not found. Please try again later.", Toast.LENGTH_LONG)
                }
            } else {
                Log.e("UPI Payment Error", "Invalid response: ${response?.data}")
                showAppToast("Payment failed. Please check your internet or payment details.", Toast.LENGTH_LONG)
            }
        })

        accountViewModel.settingsLiveData.observe(this, Observer { response ->
            if (response?.success == true) {
                response.data?.let { settingsList ->
                    if (settingsList.isNotEmpty()) {
                        val settingsData = settingsList[0]
                        settingsData.payment_gateway_type?.let { paymentGatewayType ->
                            Log.d("settingsData", "settingsData $paymentGatewayType")
                            //handlePaymentGateway(paymentGatewayType)
                           // paymentGateway = paymentGatewayType
                            Log.d("paymentGateway","$paymentGateway")
                        } ?: run {
                            // Show Toast if payment_gateway_type is null
                            showAppToast("Please try again later", Toast.LENGTH_SHORT)
                        }
                    }
                }
            }
        })



        binding.llRecharge.setOnSingleClickListener {
            val intent = Intent(this, YoutubeRechargeActivity::class.java)
            startActivity(intent)
        }

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        binding.tvCoins.text = (userData?.coins ?: 0).toString()


        refreshSubscribeBannerVisibility()

        val openCheckout = {
            // Plan-type rule mirrors the chat-side trial sheet: anyone who
            // has never had an autopay mandate gets the ₹1 trial; lapsed /
            // cancelled subscribers go to ₹299 direct.
            val planType = if (!com.gmwapp.hima.utils.SubscriptionStateCache.everActive(this))
                AutopayCheckoutActivity.PLAN_TRIAL_NEW
            else
                AutopayCheckoutActivity.PLAN_DIRECT_OLD
            startActivity(AutopayCheckoutActivity.intentFor(this, planType))
        }
        binding.cvSubscribeBanner.setOnSingleClickListener { openCheckout() }
        binding.btnSubscribeNow.setOnSingleClickListener { openCheckout() }

        val layoutManager = GridLayoutManager(this, 3)
        binding.cvBack.setOnSingleClickListener {
            if (fromDeepLink){
                startActivity(Intent(this@WalletActivity, MainActivity::class.java))
                finish()
            }else if (messageCameWhenIsAlive == 0) {
                val intent = Intent(this@WalletActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            }

            else{
                finish()
            }
        }
//        binding.rvPlans.addItemDecoration(SpacesItemDecoration(20))
        binding.rvPlans.setLayoutManager(layoutManager)
//        binding.rvPlans.addItemDecoration(SpacesItemDecoration(10))
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.let { WalletViewModel.getCoins(it.id) }
        WalletViewModel.coinsLiveData.observe(this, Observer {
            // ✅ Add null check before accessing properties
            if (it == null) {
                Log.w("WalletActivity", "CoinsResponse is null")
                return@Observer
            }

            if(it.success){
                //  showAppToast(it.message, Toast.LENGTH_SHORT)
            }
            else{
                showAppToast(it.message, Toast.LENGTH_SHORT)
            }

            if (it.success && it.data != null) {
                // Create the adapter
               // binding.ivBonus.visibility= View.VISIBLE
                var bannerOfferImage= it.banner_image
                Glide.with(this)
                    .load(bannerOfferImage)
                    .into(binding.ivBonus)

                Log.d("CoinResposneData","${it.data}")


                fetchedSkuList.clear() // Clear old SKUs to avoid duplicates

                it.data.forEach { coinItem ->
                    val sku = "${coinItem.id}"
                    fetchedSkuList.add(sku)

                    val preferences = DPreferences(this)
                    preferences.setSkuList(fetchedSkuList)
                }




                // I021 — if launched from the in-call banner, honour the
                // EXTRA_PRE_SELECTED_COIN_ID so the matching coin is pre-
                // selected before the adapter is built. CoinAdapter.onBindViewHolder
                // reads coin.isSelected, and its own "auto-select position 0"
                // fallback (line 41 there) only fires when NO coin is selected —
                // so pre-marking one suppresses the default.
                val preSelectedId = intent.getIntExtra(EXTRA_PRE_SELECTED_COIN_ID, -1)
                val preSelectedCoin: CoinsResponseData? = if (preSelectedId > 0) {
                    it.data.firstOrNull { c -> c.id == preSelectedId }
                } else null

                if (preSelectedCoin != null) {
                    it.data.forEach { c -> c.isSelected = (c.id == preSelectedId) }
                }

                val coinAdapter = CoinAdapter(this, it.data, object : OnItemSelectionListener<CoinsResponseData> {
                    override fun onItemSelected(coin: CoinsResponseData) {
                        // Update button text and make it visible when an item is selected
                        binding.btnAddCoins.text = getString(R.string.add_quantity_coins, coin.coins)
                        binding.btnAddCoins.visibility = View.VISIBLE
                        amount = coin.price.toString()
                        pointsId = coin.id.toString()
                        // 2026-05-21: reverted to match live APK behavior.
                        // Auto-routing to coin.pg sent some users to PhonePe during the
                        // PhonePe outage, lowering success rate vs. live APK which stays
                        // on whichever gateway was last set. Restore that behavior:
                        // paymentGateway = coin.pg.toString()  // disabled

                        selectedCoin = coin.coins.toString()
                        selectedSavePercent = coin.save.toString()
                        Log.d("pgCheck","${coin.pg}")


                    }

                    val number: CoinsResponseData?
                        get() = null
                })

                // Set the adapter
                binding.rvPlans.adapter = coinAdapter

                // Set default button text and visibility for the first item,
                // OR for the pre-selected item if one was requested via I021.
                if (it.data.isNotEmpty()) {
                    val firstCoin = preSelectedCoin ?: it.data[0]
                    binding.btnAddCoins.text = getString(R.string.add_quantity_coins, firstCoin.coins)
                    binding.btnAddCoins.visibility = View.VISIBLE
                    amount = firstCoin.price.toString()
                    pointsId = firstCoin.id.toString()
                    // 2026-05-21: reverted to match live APK behavior (same reason as per-coin path above)
                    // paymentGateway = firstCoin.pg.toString()  // disabled

                    selectedCoin = firstCoin.coins.toString()
                    selectedSavePercent = firstCoin.save.toString()

                    if (preSelectedCoin != null) {
                        // Scroll the RV so the pre-selected card is visible.
                        binding.rvPlans.scrollToPosition(it.data.indexOf(preSelectedCoin))
                    }
                }
            }

        })

        billingManager = BillingManager(this)


        binding.btnAddCoins.setOnClickListener(View.OnClickListener { view: View? ->
            handleCoinPurchase()
        })








//
//        binding.btnAddCoins.setOnSingleClickListener {
//            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
//
//            val userId = userData?.id
//             name = userData?.name ?: ""
//             email = "test@gmail.com"
//             mobile = userData?.mobile ?: ""
//
//            // get 2 percentage of amount
//            val twoPercentage = amount.toDouble() * 0.02
//            val roundedAmount = Math.round(twoPercentage)
//             total_amount = (amount.toDouble() + roundedAmount).toString()
//
//            Log.d("Amount", "amount $amount")
//            Log.d("Amount", "Totalamount $total_amount")
//            Log.d("Amount", "Roundamount $roundedAmount")
//            if (userId != null && pointsId.isNotEmpty() && total_amount.isNotEmpty()) {
//                 userIdWithPoints = "$userId-$pointsId"
//
//
//                  accountViewModel.getSettings()
//
//
//
////                val intent = Intent(this@WalletActivity, PaymentActivity::class.java).apply {
////                    putExtra("AMOUNT", total_amount)
////                    putExtra("COIN_SELECTED", selectedCoin )
////                    putExtra("SAVE_PERCENT", selectedSavePercent)
////
////                }
////                startActivity(intent)
//
//            } else {
//                showAppToast("Invalid input data", Toast.LENGTH_SHORT)
//            }
//        }
//    }

//
//    private fun addObservers() {
//        viewModel.addpointResponseLiveData.observe(this, Observer {
//            if (it != null && it.success) {
//
//                showAppToast(it.message, Toast.LENGTH_SHORT)
//
//            }
//            else{
//                showAppToast(it.message, Toast.LENGTH_SHORT)
//            }
//        })
//
//        upiPaymentViewModel.upiPaymentLiveData.observe(this, Observer { response ->
//            if (response != null && response.status) {
//                val paymentUrl = response.data.firstOrNull()?.payment_url
//
//                if (!paymentUrl.isNullOrEmpty()) {
//                    Log.d("UPI Payment", "Payment URL: $paymentUrl")
//                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
//                    startActivity(intent)
//                } else {
//                    Log.e("UPI Payment Error", "Payment URL is null or empty")
//                    showAppToast("Payment URL not found. Please try again later.", Toast.LENGTH_LONG)
//                }
//            } else {
//                Log.e("UPI Payment Error", "Invalid response: ${response?.data}")
//                showAppToast("Payment failed. Please check your internet or payment details.", Toast.LENGTH_LONG)
//            }
//        })
//
//
//
//
//        accountViewModel.settingsLiveData.observe(this, Observer { response ->
//            if (response?.success == true) {
//                response.data?.let { settingsList ->
//                    if (settingsList.isNotEmpty()) {
//                        val settingsData = settingsList[0]
//                        settingsData.payment_gateway_type?.let { paymentGatewayType ->
//                            Log.d("settingsData", "settingsData $paymentGatewayType")
//                            handlePaymentGateway(paymentGatewayType)
//                        } ?: run {
//                            // Show Toast if payment_gateway_type is null
//                            showAppToast("Please try again later", Toast.LENGTH_SHORT)
//                        }
//                    }
//                }
//            }
//        })
//
//
//
//    }
//
//    private fun handlePaymentGateway(paymentGatewayType: String) {
//        // Handle the payment gateway type logic
//        when (paymentGatewayType) {
//            "razorpay" -> {
//
//                callRazor = apiService.addCoinsRazorPay(userIdWithPoints,name,total_amount,email,mobile)
//
//
//                callRazor.enqueue(object : retrofit2.Callback<RazorPayApiResponse> {
//                    override fun onResponse(call: retrofit2.Call<RazorPayApiResponse>, response: retrofit2.Response<RazorPayApiResponse>) {
//                        if (response.isSuccessful && response.body() != null) {
//                            val apiResponse = response.body()
//
//                            // Extract the Razorpay payment link
//                            val paymentUrl = apiResponse?.short_url
//
//                            if (!paymentUrl.isNullOrEmpty()) {
//
//                                val intent =Intent(this@WalletActivity, LauncherActivity::class.java)
//                                intent.setData(Uri.parse(response.body()?.short_url))
//                                Log.d("WalletResponse","${response.body()?.short_url}")
//                                startActivity(intent)
//
////                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
////                                startActivity(intent)
//                            } else {
//                                showAppToast("Failed to get payment link", Toast.LENGTH_SHORT)
//                            }
//                        } else {
//                            showAppToast("Error: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT)
//                        }
//                    }
//
//                    override fun onFailure(call: retrofit2.Call<RazorPayApiResponse>, t: Throwable) {
//                        showAppToast("Failed: ${t.message}", Toast.LENGTH_SHORT)
//                    }
//                })
//
//
//
//
//
//            }
//
//            "upigateway" ->{
//
//                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
//                var userid = userData?.id
//
//
//                userid?.let {
//                    val clientTxnId = generateRandomTxnId(it,pointsId)  // Generate a new transaction ID
//                    upiPaymentViewModel.createUpiPayment(it, clientTxnId, total_amount)
//                }
//
//            }
//
//            "instamojo" -> {
//
//
//                call = apiService.addCoins(name, total_amount, email, mobile, userIdWithPoints)
//
//
//                call.enqueue(object : retrofit2.Callback<ApiResponse> {
//                    override fun onResponse(call: retrofit2.Call<ApiResponse>, response: retrofit2.Response<ApiResponse>) {
//                        if (response.isSuccessful && response.body()?.success == true) {
//                            showAppToast(response.body()?.message, Toast.LENGTH_SHORT)
//                        } else {
//                            // println("Long URL: ${it.longurl}") // Print to the terminal
//                            //Toast.makeText(mContext, it.longurl, Toast.LENGTH_SHORT).show()
//
//                            val intent =
//                                Intent(this@WalletActivity, LauncherActivity::class.java)
//                            intent.setData(Uri.parse(response.body()?.longurl))
//                            Log.d("WalletResponse","${response.body()?.longurl}")
//                            startActivity(intent)
//                            finish()// Directly starting the intent without launcher
//                            //  showAppToast(response.body()?.message ?: "Error", Toast.LENGTH_SHORT)
//                        }
//                    }
//
//                    override fun onFailure(call: retrofit2.Call<ApiResponse>, t: Throwable) {
//                        showAppToast("Failed: ${t.message}", Toast.LENGTH_SHORT)
//                    }
//                })
//
//
//
//
//            }
//            else -> {
//                showAppToast("Invalid Payment Gateway", Toast.LENGTH_SHORT)
//            }
//        }
//
//    }

//    fun generateRandomTxnId(userId: Int, coinId: String): String {
//        return "$userId-$coinId-${System.currentTimeMillis()}"
//    }

    }

    fun generateRandomTxnId(userId: Int, coinId: String): String {
        return "$userId-$coinId-${System.currentTimeMillis()}"
    }

    /**
     * Gate for `new_user_purchase`: 24 hours from the REGISTRATION MOMENT, not
     * "same calendar date". Used to reset at midnight, so an 11:50 PM signup got a
     * 10-minute window. See SignupWindow.
     */
    private fun isNewUser(createdAt: String?): Boolean =
        com.gmwapp.hima.utils.SignupWindow.isWithinFirst24h(createdAt)

    /**
     * @param isFirstRecharge server verdict on whether this payment is the user's
     *   first ever recharge (see FirstPurchaseTracker.readFlag). Null from callers
     *   that have no gateway status payload to read it from — e.g. Play billing.
     */
    fun updatePurchaseOnMeta(isFirstRecharge: Boolean? = null){
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()
        val userId = userData?.id
        val coinId = prefs?.getString("last_coin_id")
        val coinAmount = prefs?.getString("last_coin_amount")?.toDoubleOrNull() ?: 0.0

        if (coinAmount > 0.0) {
            val params = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
                putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, coinAmount)
                putString("user_id", "$userId")
                putString("coin_id", "$coinId")
            }
            AppEventsLogger.newLogger(this).logEvent(AppEventsConstants.EVENT_NAME_PURCHASED, coinAmount, params)

            // 2026-05-22 — Day-1 Multiple Purchase tracking (marketing request).
            // Fires alongside PURCHASE if user is within 24h of signup and this
            // is their 2nd+ purchase. See HimaAnalytics.logPurchaseAndMaybeD1mp.
            // Parsed via SignupWindow so D1MP anchors on the same IST-correct signup
            // moment as new_user_purchase (this used to parse in the DEVICE timezone,
            // shifting the 24h window for anyone not on IST).
            val signupAtMs = com.gmwapp.hima.utils.SignupWindow.signupAtMs(userData?.created_at)
            com.gmwapp.hima.utils.HimaAnalytics.logPurchaseAndMaybeD1mp(this, signupAtMs, coinAmount, "INR")
        } else {
            Log.w("FB_Event", "Skipped PURCHASE event. Invalid coinAmount = $coinAmount")
        }


        val purchaseBundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.CURRENCY, "INR")
            putDouble(FirebaseAnalytics.Param.VALUE, coinAmount)
            putString(FirebaseAnalytics.Param.ITEM_ID, coinId)
            putString("user_id", userId.toString()) // optional: useful for debugging

        }

        BaseApplication.firebaseAnalytics.logEvent(FirebaseAnalytics.Event.PURCHASE, purchaseBundle)

        MmpClient.trackPurchase(
            revenueInr = coinAmount,
            productId = coinId,
            customerUserId = userId?.toString()
        )

        // Log to backend (only Firebase events)
        AppEventLogger.logEvent(
            context = this,
            eventName = "purchase",
            platform = "firebase",
            userId = userId,
            params = AppEventLogger.bundleToMap(purchaseBundle),
            value = coinAmount
        )

        // Adjust (mirrors alongside Meta + MMP + Firebase).
        com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
            "purchase",
            revenueInr = coinAmount,
            params = mapOf("user_id" to "$userId", "coin_id" to "$coinId")
        )

        // Log new_user_purchase event if user registered today
        if (isNewUser(userData?.created_at)) {
            // Firebase Analytics - new_user_purchase
            val newUserPurchaseBundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.CURRENCY, "INR")
                putDouble(FirebaseAnalytics.Param.VALUE, coinAmount)
                putString(FirebaseAnalytics.Param.ITEM_ID, coinId)
                putString("user_id", "$userId")
                putString("created_at", userData?.created_at ?: "")
            }
            BaseApplication.firebaseAnalytics.logEvent("new_user_purchase", newUserPurchaseBundle)
            
            // Meta/Facebook Analytics - new_user_purchase
            val newUserParams = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
                putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, coinAmount)
                putString("user_id", "$userId")
                putString("coin_id", "$coinId")
                putString("created_at", userData?.created_at ?: "")
            }
            AppEventsLogger.newLogger(this).logEvent("new_user_purchase", coinAmount, newUserParams)
            
            // Log to backend (only Firebase events)
            AppEventLogger.logEvent(
                context = this,
                eventName = "new_user_purchase",
                platform = "firebase",
                userId = userId,
                params = AppEventLogger.bundleToMap(newUserPurchaseBundle),
                value = coinAmount
            )

            // Adjust (mirrors alongside Meta + Firebase + backend).
            com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                "new_user_purchase",
                revenueInr = coinAmount,
                params = mapOf("user_id" to "$userId", "coin_id" to "$coinId")
            )

            Log.d("NewUserPurchase", "✅ new_user_purchase event logged for user $userId (created: ${userData?.created_at})")
        } else {
            Log.d("NewUserPurchase", "⏭️ Skipped new_user_purchase - User not new (created: ${userData?.created_at})")
        }

        // new_user_first_purchase = the user's FIRST EVER recharge, decided by the
        // server (`is_first_recharge` on the gateway status response). Deliberately
        // OUTSIDE the isNewUser() block above: a first ever recharge on day 2, day 30
        // or after a reinstall still counts. See FirstPurchaseTracker.
        com.gmwapp.hima.utils.FirstPurchaseTracker.maybeFire(
            context = this,
            isFirstRecharge = isFirstRecharge,
            userId = userId,
            coinId = coinId,
            coinAmount = coinAmount,
            createdAt = userData?.created_at,
        )
    }

    fun generateJwtToken(): String {

        val token = TokenGenerator.getToken()


        // ✅ Log for Postman testing
        Log.d("JWT_TOKEN", "Generated Token: $token")
        return token


    }


    private fun cashfreeUPIIntentPayment(paymentSessionID: String, orderID: String) {
        try {
            val cfSession = CFSession.CFSessionBuilder()
                .setEnvironment(cfEnvironment)
                .setPaymentSessionID(paymentSessionID)
                .setOrderId(orderID)
                .build()

            val cfUPIIntentCheckout = CFUPIIntentCheckout.CFUPIIntentBuilder()
                .setOrder(listOf(
                    CFUPIIntentCheckout.CFUPIApps.GOOGLE_PAY,
                    CFUPIIntentCheckout.CFUPIApps.PHONEPE,
                    CFUPIIntentCheckout.CFUPIApps.BHIM
                ))
                .build()

            val payment = CFUPIIntentCheckoutPayment.CFUPIIntentPaymentBuilder()
                .setSession(cfSession)
                .setCfUPIIntentCheckout(cfUPIIntentCheckout)
                .build()

            // Register callback
            CFPaymentGatewayService.getInstance().setCheckoutCallback(this)

            // Start payment directly with selected app
            CFPaymentGatewayService.getInstance().doPayment(this, payment)


        } catch (e: CFException) {
            Log.e("CashfreeUPI", "Error starting UPI intent: ${e.message}")
            showAppToast("Cashfree error: ${e.message}", Toast.LENGTH_SHORT)
        }
    }


    fun cashfreeCheckout(paymentSessionID:String,orderID:String){

        try {
            val cfSession = CFSession.CFSessionBuilder()
                .setEnvironment(cfEnvironment)
                .setPaymentSessionID(paymentSessionID)
                .setOrderId(orderID)
                .build()

//            val cfTheme = CFWebCheckoutTheme.CFWebCheckoutThemeBuilder()
//                .setNavigationBarBackgroundColor("#6A3FD3")
//                .setNavigationBarTextColor("#FFFFFF")
//                .build()

            val cfWebCheckoutPayment = CFWebCheckoutPayment.CFWebCheckoutPaymentBuilder()
                .setSession(cfSession)
//                .setCFWebCheckoutUITheme(cfTheme)
                .build()

            CFPaymentGatewayService.getInstance()
                .doPayment(this@WalletActivity, cfWebCheckoutPayment)

        } catch (e: CFException) {
            e.printStackTrace()
        }
    }


    override fun onPaymentVerify(orderID: String?) {
        Log.d("WebCheckout", "Payment verified for order: $orderID")
    }

    override fun onPaymentFailure(cfErrorResponse: CFErrorResponse?, orderID: String?) {
        Log.e("WebCheckout", "Payment failed for $orderID: ${cfErrorResponse?.getMessage()}")
    }

    private fun fetchOrderOfCashfree(coinId: String) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val user_id = userData?.id
        val client = OkHttpClient()

        val json = """{
        "user_id": "$user_id",
        "coins_id": "$coinId"
    }"""
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, json)

        val request = Request.Builder()
            .url("${Config.API_ROOT}cashfree/create-order")
            .post(body) // ✅ POST request like PhonePe example
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    showAppToast("Order creation failed: ${e.message}", Toast.LENGTH_SHORT)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: OkHttpResponse) {
                val resultStr = response.body?.string()
                Log.d("CashfreeOrderResponse", "$resultStr")

                try {
                    val json = JSONObject(resultStr)
                    val success = json.optBoolean("success", false)

                    if (success) {
                        val sessionId = json.getString("payment_session_id")
                        val orderId = json.getString("order_id")

                        cashfreeLastOrderId = orderId
                        runOnUiThread {
                            // Start the Cashfree payment flow
                           // cashfreeCheckout(sessionId, orderId)
                            cashfreeUPIIntentPayment(sessionId, orderId)
                        }
                    } else {
                        runOnUiThread {
                            val errorMsg = json.optJSONObject("errors")?.toString() ?: "Order creation failed"
                            showAppToast(errorMsg, Toast.LENGTH_SHORT)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showAppToast("Invalid server response", Toast.LENGTH_SHORT)
                    }
                }
            }
        })
    }

    fun checkCashfreeOderStatus(orderId: String) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val user_id = userData?.id
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("${Config.API_ROOT}cashfree/check-order-status?order_id=$orderId")
            .get() // ✅ This endpoint uses GET (based on your Postman test)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    showAppToast("Status check failed: ${e.message}", Toast.LENGTH_SHORT)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: OkHttpResponse) {
                val resultStr = response.body?.string()
                Log.d("CashfreeOrderStatus", "$resultStr")

                try {
                    val json = JSONObject(resultStr)

                    // Check if payment was completed (you may need to adapt based on backend's status field)
                    val paymentStatus = json.optString("order_status", "UNKNOWN")
                    val coin_id = json.optString("coin_id", "")
                    val order_id = json.optString("order_id", "")

                    Log.d("cashfreePaymentStatus", "Status: $paymentStatus, Coin ID: $coin_id, Order ID: $order_id")

                    val isFirstRecharge = com.gmwapp.hima.utils.FirstPurchaseTracker.readFlag(json)

                    if (paymentStatus.equals("PAID", ignoreCase = true)) {
                        runOnUiThread {
                            showAppToast("Payment Successful", Toast.LENGTH_LONG)
                            user_id?.let { WalletViewModel.add_coins_cashfree(it, coin_id, 1, order_id, "Coins purchased") }
                            updatePurchaseOnMeta(isFirstRecharge)
                        }
                    } else {
                        runOnUiThread {
                            showAppToast("Payment Failed", Toast.LENGTH_LONG)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showAppToast("Invalid response", Toast.LENGTH_SHORT)
                    }
                }
            }
        })
    }


    private fun setupPaymentTypeLoginObserverOnce() {
        if (paymentTypeLoginObserverRegistered) return
        paymentTypeLoginObserverRegistered = true
        loginViewModel.loginResponseLiveData.observe(this, Observer { response ->
            if (response?.success == true) {
                response.data?.payment_type?.let { pg ->
                    if (pg.isNotEmpty()) paymentGateway = pg.toString()
                }
            }
        })
    }

    /** Fetches payment gateway type from backend (safe to call from onResume). */
    fun checkIndividualPaymentType() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.let { loginViewModel.login(it.mobile, "0", "0") }
    }

    private fun checkReferralOffer() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id ?: return
        
        apiManager.checkReferralOffer(userId, object : NetworkCallback<CheckReferralOfferResponse> {
            override fun onResponse(
                call: Call<CheckReferralOfferResponse>,
                response: Response<CheckReferralOfferResponse>
            ) {
                val responseBody = response.body()
                Log.d("CheckReferralOffer", "Response: success=${responseBody?.success}, show_dialog=${responseBody?.show_dialog}, coin_id=${responseBody?.coin_id}")
                
                if (responseBody != null && responseBody.show_dialog) {
                    runOnUiThread {
                        showReferralOfferDialog(
                            responseBody.offer_coins,
                            responseBody.offer_price,
                            responseBody.coin_id ?: 0
                        )
                    }
                }
            }

            override fun onFailure(call: Call<CheckReferralOfferResponse>, t: Throwable) {
                Log.e("CheckReferralOffer", "API call failed: ${t.message}")
                // No toast - silent failure as per requirements
            }

            override fun onNoNetwork() {
                Log.e("CheckReferralOffer", "No network connection")
                // No toast - silent failure as per requirements
            }
        })
    }

    private fun showReferralOfferDialog(coins: Int, price: Int, coinId: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_referral_offer, null)
        
        // Set coins and price
        val tvOfferCoins = dialogView.findViewById<android.widget.TextView>(R.id.tv_offer_coins)
        val tvOfferPrice = dialogView.findViewById<android.widget.TextView>(R.id.tv_offer_price)
        val btnBuyNow = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_buy_now)
        val btnMaybeLater = dialogView.findViewById<android.widget.TextView>(R.id.btn_maybe_later)
        
        tvOfferCoins.text = coins.toString()
        tvOfferPrice.text = "₹$price"
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Set transparent background for rounded corners
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Set dimming to show black background overlay
        dialog.window?.setDimAmount(0.5f)
        
        // Buy Now button click
        btnBuyNow.setOnClickListener {
            if (coinId > 0) {
                // Set the variables to match what btnAddCoins expects
                pointsId = coinId.toString()
                amount = price.toString()
                selectedCoin = coins.toString()
                
                // Call the same function that btnAddCoins calls
                handleCoinPurchase()
            }
            dialog.dismiss()
        }
        
        // Maybe Later button click
        btnMaybeLater.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun handleCoinPurchase() {
        accountViewModel.getSettings()
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id
        val pointsIdInt = pointsId.toIntOrNull()
        val priceDouble = amount?.toDoubleOrNull() ?: 0.0

        MmpClient.trackEvent(
            eventName = "initiated_checkout",
            revenue = priceDouble,
            params = mapOf("coin_id" to "$pointsId"),
            customerUserId = userId?.toString()
        )

        val firebaseBundle = Bundle().apply {
            putString("user_id", "$userId")
            putString("coin_id", "$pointsId")
            putDouble("price", priceDouble)
        }
        BaseApplication.firebaseAnalytics.logEvent("initial_checkout", firebaseBundle)

        val checkoutAmount = amount.toDoubleOrNull() ?: 0.0
        if (checkoutAmount > 0.0) {
            val checkoutParams = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
                putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, checkoutAmount)
                putString("user_id", "$userId")
                putString("coin_id", "$pointsId")
            }

            // 2026-05-24 v1074 — Meta initial_checkout RESTORED per marketing
            // (they now want every Firebase/Google-Ads event also in Meta).
            try {
                AppEventsLogger.newLogger(this).logEvent(
                    "initial_checkout",
                    checkoutAmount,
                    checkoutParams
                )
            } catch (t: Throwable) {
                Log.w("FB_Event", "Meta initial_checkout failed: ${t.message}")
            }

            // Log to backend (only Firebase events)
            AppEventLogger.logEvent(
                context = this,
                eventName = "initial_checkout",
                platform = "firebase",
                userId = userId,
                params = AppEventLogger.bundleToMap(firebaseBundle),
                value = checkoutAmount
            )

            // Adjust (mirrors alongside Meta + MMP + Firebase).
            com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                "initial_checkout",
                revenueInr = priceDouble,
                params = mapOf("user_id" to "$userId", "coin_id" to "$pointsId")
            )
        } else {
            Log.w("FB_Event", "Skipped INITIATED_CHECKOUT event. Invalid amount = $checkoutAmount")
        }

        BaseApplication.getInstance()?.getPrefs()?.apply {
            setString("last_coin_id", pointsId)
            setString("last_coin_amount", amount.toString())
            setString("last_coin_pg", paymentGateway.toString())
        }

        if (userId != null && pointsId.isNotEmpty()) {
            if (pointsIdInt != null) {
                if (paymentGateway.isNotEmpty()) {
                    when (paymentGateway) {
                        "phonepe" -> {
                            // 2026-05-21: reverted to match live APK behavior — silent fail if PhonePe SDK
                            // isn't ready (rather than showing a user-facing "PhonePe not ready" toast that
                            // didn't exist in live APK). The button just doesn't progress; user can retry.
                            if (isPhonePeInitialized) {
                                fetchOrderFromBackend(pointsId)
                            }
                        }

                        "gpay" -> {
                            val bm = billingManager
                            if (bm == null) {
                                showAppToast("Google Play billing is still loading. Please wait and try again.", Toast.LENGTH_SHORT)
                            } else {
                            val random4Digit = (1000..9999).random()
                            val preferences = DPreferences(this)
                            preferences.clearSelectedOrderId()
                            preferences.setSelectedUserId(userId.toString())
                            preferences.setSelectedPlanId(java.lang.String.valueOf(pointsIdInt))
                            preferences.setSelectedOrderId(java.lang.String.valueOf(random4Digit))
                            WalletViewModel.tryCoins(userId, pointsIdInt, 0, random4Digit, "try")
                            pendingPurchaseMetaFromPlayBilling = true
                            bm.purchaseProduct(pointsId)
                            }
                        }

                        "razorpay" -> {
                            callNewRazorPay = apiService.callNewRazorPay(userId, pointsId)
                            callNewRazorPay.enqueue(object : retrofit2.Callback<NewRazorpayLinkResponse> {
                                override fun onResponse(call: retrofit2.Call<NewRazorpayLinkResponse>, response: retrofit2.Response<NewRazorpayLinkResponse>) {
                                    if (response.isSuccessful && response.body() != null) {
                                        val apiResponse = response.body()
                                        val paymentUrl = apiResponse?.data?.short_url
                                        Log.d("paymentUrlRazorPay", "$paymentUrl")
                                        if (!paymentUrl.isNullOrEmpty()) {
                                            val intent = Intent(this@WalletActivity, LauncherActivity::class.java)
                                            intent.setData(Uri.parse(paymentUrl))
                                            Log.d("paymentUrlRazorPay", "$paymentUrl")
                                            startActivity(intent)
                                        } else {
                                            showAppToast("Failed to get payment link", Toast.LENGTH_SHORT)
                                        }
                                    } else {
                                        showAppToast("Error: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT)
                                    }
                                }

                                override fun onFailure(call: retrofit2.Call<NewRazorpayLinkResponse>, t: Throwable) {
                                    showAppToast("Failed: ${t.message}", Toast.LENGTH_SHORT)
                                }
                            })
                        }

                        "cashfree" -> {
                            fetchOrderOfCashfree(pointsId)
                        }

                        "upigateway" -> {
                            val amountValue = amount.toDoubleOrNull()
                            if (amountValue == null) {
                                return
                            }
                            val twoPercentage = amountValue * 0.02
                            val roundedAmount = Math.round(twoPercentage)
                            total_amount = (amountValue + roundedAmount).toString()
                            Log.d("upigateway", "Clicked")
                            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                            var userid = userData?.id
                            userid?.let {
                                val clientTxnId = generateRandomTxnId(it, pointsId)
                                upiPaymentViewModel.createUpiPayment(it, clientTxnId, total_amount)
                            }
                        }

                        else -> {
                            showAppToast("Invalid Payment Gateway", Toast.LENGTH_SHORT)
                        }
                    }
                }
                // 2026-05-21: removed "Payment gateway not available, please reopen the app" toast
                // (was a NEW addition in autopay merge that didn't exist in live APK). Silent fail
                // matches live APK behavior — user just sees no progression and can retry.
            }
        } else {
            showAppToast("Invalid input data", Toast.LENGTH_SHORT)
        }
    }


}
