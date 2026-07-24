package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.graphics.Rect
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.gmwapp.hima.adapters.CoinAdapter
import com.gmwapp.hima.adapters.GiftAdapter
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.ZohoHelper
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity
import com.gmwapp.hima.agora.male.MaleCallAcceptActivity
import com.gmwapp.hima.callbacks.NetworkRetryable
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.callbacks.Refreshable
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.utils.CallPermissionHelper
import com.gmwapp.hima.databinding.ActivityMainBinding
import com.gmwapp.hima.dialogs.BottomSheetWelcomeBonus
import com.gmwapp.hima.dialogs.BottomSheetInsufficientCoinsPaywall
import com.gmwapp.hima.dialogs.FreeCoinsWelcomeDialog
import com.gmwapp.hima.dialogs.RatingDialog
import com.gmwapp.hima.fragments.FavouriteFragment
import com.gmwapp.hima.fragments.FriendsHubFragment
import com.gmwapp.hima.fragments.CreatorChatFragment
import com.gmwapp.hima.fragments.FemaleHomeFragment
import com.gmwapp.hima.fragments.HomeFragment
import com.gmwapp.hima.fragments.ProfileFemaleFragment
import com.gmwapp.hima.fragments.ProfileFragment
import com.gmwapp.hima.fragments.RecentFragment
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.retrofit.responses.NewRazorpayLinkResponse
import com.gmwapp.hima.retrofit.responses.PaywallVideoContentResponse
import com.gmwapp.hima.retrofit.responses.RazorPayApiResponse
import com.gmwapp.hima.utils.Config
import com.gmwapp.hima.retrofit.responses.FreeCoinsStatusResponse
import com.gmwapp.hima.retrofit.responses.InstallReferrerResponse
import com.gmwapp.hima.retrofit.responses.LoginResponse
import com.gmwapp.hima.retrofit.responses.MissedCallCountResponse
import com.gmwapp.hima.retrofit.responses.TrackingInfoResponse
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.utils.RatingPromptHelper
import com.gmwapp.hima.utils.FeedbackFormHelper
import com.gmwapp.hima.utils.AppEventLogger
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.FcmTokenViewModel
import com.gmwapp.hima.viewmodels.IndividualAppUpdateViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.OfferViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UpiPaymentViewModel
import com.gmwapp.hima.viewmodels.WalletViewModel
import com.gmwapp.hima.viewmodels.ZohoMailViewModel
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.androidbrowserhelper.trusted.LauncherActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.OneSignal
import com.phonepe.intent.sdk.api.PhonePeInitException
import com.phonepe.intent.sdk.api.PhonePeKt
import com.phonepe.intent.sdk.api.models.PhonePeEnvironment
import com.google.gson.Gson
import com.zoho.salesiqembed.ZohoSalesIQ
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import retrofit2.Call
import java.io.IOException
import kotlin.math.round


@AndroidEntryPoint
class MainActivity : BaseActivity(), BottomNavigationView.OnNavigationItemSelectedListener,
    BottomSheetWelcomeBonus.OnAddCoinsListener,
    BottomSheetInsufficientCoinsPaywall.OnPaywallAddCoinsListener,
    CFCheckoutResponseCallback {

    companion object {
        // B034 — deep-link key for "open MainActivity directly into a specific
        // bottom-nav tab." Used by the missed-call notification PendingIntent
        // so a tap lands on Recent instead of Home. Add new tab values as
        // needed (right now only TAB_RECENT is consumed).
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_RECENT = "recent"
        const val TAB_FAVOURITE = "favourite"
        const val TAB_CHAT = "chat"
        // Sub-tab index inside the Friends hub (male) / Chat hub (female):
        // 0 = Friends, 1 = Requests received, 2 = Sent. -1 = none.
        const val EXTRA_OPEN_SUBTAB = "open_subtab"
    }

    lateinit var binding: ActivityMainBinding
    var isBackPressedAlready = false
    var userName: String? = null
    var userID: String? = null
    var currentVersion = ""

    val appUpdateViewModel: LoginViewModel by viewModels()
    val offerViewModel: OfferViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()
    private val zohoMailViewModel: ZohoMailViewModel by viewModels()

    private val accountViewModel: AccountViewModel by viewModels()
    private val fcmTokenViewModel: FcmTokenViewModel by viewModels()
    private val upiPaymentViewModel: UpiPaymentViewModel by viewModels()
    val individualAppUpdateViewModel: IndividualAppUpdateViewModel by viewModels()

    private var billingManager: BillingManager? = null
    private val WalletViewModel: WalletViewModel by viewModels()
    private val fetchedSkuList: MutableList<String> = mutableListOf()

    /** Google Play billing from Main: [updatePurchaseOnMeta] when [WalletViewModel.navigateToMain] fires. */
    private var pendingPurchaseMetaFromMainActivityGpay = false
    
    @javax.inject.Inject
    lateinit var ratingPromptHelper: RatingPromptHelper

    @javax.inject.Inject
    lateinit var feedbackFormHelper: FeedbackFormHelper

    @javax.inject.Inject
    lateinit var apiManager: ApiManager

    // B072 — prefetch gift catalog + warm icon cache at app start so the
    // first open of the gift bottom sheet in a session doesn't show an
    // empty grid while the network fetch + per-icon Glide downloads race.
    @javax.inject.Inject
    lateinit var giftImageRepository: com.gmwapp.hima.repositories.GiftImageRepository


    private var blockWordDialog: Dialog? = null


    private lateinit var call: Call<ApiResponse>
    private lateinit var callRazor: Call<RazorPayApiResponse>
    private lateinit var callNewRazorPay: Call<NewRazorpayLinkResponse>
    val apiService = RetrofitClient.instance



    lateinit var total_amount : String
    lateinit var coinId: String

    var paymentGateway = ""

    private var lastOrderId: String = ""
    private var isPhonePeInitialized = false
    private var hasTriggeredFreeCoinsStatus: Boolean = false

    private lateinit var activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var appUpdateManager: AppUpdateManager

    private val cfEnvironment = CFSession.Environment.PRODUCTION

    private var cashfreeLastOrderId: String = ""
    private var recentMissedCount: Int = 0
    private var chatFriendsUnread: Int = 0
    private var chatGeneralUnread: Int = 0
    // Pending received friend-requests — shown on the female Chat icon alongside unread messages.
    private var chatRequestsUnread: Int = 0
    // B_010 — Instagram-style "seen the tab" watermark. Opening the Chat tab clears the
    // bottom-nav badge even if messages/requests are still unread; it stays clear until the
    // total climbs ABOVE the level seen at that visit (genuinely new activity), which re-shows
    // it. A watermark, not a boolean, so later activity is never silently swallowed (mirrors
    // RequestsSeenPrefs). Per-session (not persisted): a cold start recomputes from the server,
    // so the badge reappears if unread genuinely remains — over-notifying is the safe direction.
    private var chatBadgeSeen: Boolean = false
    private var chatBadgeSeenLevel: Int = 0
    // Male "Friends" hub (favourite tab) — pending received friend-requests badge.
    private var friendsRequestsUnread: Int = 0
    // B_015 — Instagram-style "seen the tab" watermark for the Friends (favourite) badge,
    // mirroring the B_010 Chat-badge treatment. Opening the Friends tab clears the badge even
    // with a pending request; it re-shows only when the count climbs above the level seen at
    // that visit. Per-session (not persisted): a cold start recomputes from the server.
    private var friendsBadgeSeen: Boolean = false
    private var friendsBadgeSeenLevel: Int = 0
    private val paywallVideoContentPrefsKey = "paywall_video_content_response"
    private val showPaywallInsufficientIntentKey = "show_paywall_insufficient"

    var fromApplication = false



    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.MANAGE_OWN_CALLS] == true) {
            // Permission granted, proceed with call service
        } else {
            // Show an error or disable call-related functionality
        }
    }


    private val activityResultLauncherPhonePe = registerForActivityResult(
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // B120: if the device clock is set wildly wrong (years in the past or
        // future), TLS handshakes fail and Agora rejects tokens as
        // not-yet-valid / expired. The user sees "calls not received." The
        // app can't fix the clock for them, so we prompt them to fix it.
        // Throttled to once-per-day inside the checker.
        com.gmwapp.hima.utils.DeviceTimeChecker.maybeWarnDeviceTime(this)

        // B023 — if the user opened Hima via launcher while a call was
        // ringing, the heads-up notification was the only surface they
        // were tracking, and bringing MainActivity to the foreground used
        // to leave the ring orphaned (no notification, no accept UI).
        // Route them straight to the proper accept screen — same intent
        // shape FCM uses, so the activity hydrates avatar/name correctly.
        // isIncomingCallFresh() honours the 35–45s ring window so a stale
        // flag never hijacks a normal app launch.
        routeIncomingCallIfPending()

        // 2026-05-26 — marketing wants notification open-rate analytics.
        // When MainActivity is launched via a notification tap, FCM
        // populates intent.extras with the push payload's data block.
        // The tracker reads notification_log_id / notification_type and
        // fires /api/auth/notification_clicked. Fire-and-forget — no UI
        // gating; failure here never blocks the launch path.
        com.gmwapp.hima.utils.NotificationClickTracker.maybeTrack(intent, apiManager)

        // B072 — prefetch gift catalog + warm Glide disk cache so the gift
        // bottom sheet renders instantly the first time the user opens it.
        // Idempotent: no-op if a cache is already populated.
        com.gmwapp.hima.utils.GiftManager.prefetch(this, giftImageRepository)

        // Set status bar color to pink
        // Set colors
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.white)

        // ✅ SIMPLE: Set status bar icons to LIGHT (white) - works on all devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        
        // CRITICAL: Ensure bottom navigation is always visible
        binding.bottomNavigationView.elevation = 50f
        binding.bottomNavigationView.translationZ = 50f
        binding.bottomNavigationView.bringToFront()
        (binding.root as ViewGroup).invalidate()
        
        // Apply insets - no padding needed, fragments handle their own
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.bottomNavigationView.setPadding(
                binding.bottomNavigationView.paddingLeft,
                binding.bottomNavigationView.paddingTop,
                binding.bottomNavigationView.paddingRight,
                systemBars.bottom
            )
            // RM_010/RM_010b: hiding the nav while the keyboard is open is handled by
            // keyboardNavListener (a global-layout detector registered below) so it works
            // identically on EVERY Android version. WindowInsets Type.ime() visibility is
            // only reliable on API 30+, so relying on it here left the nav floating above
            // the keyboard on Android 7–10 phones (e.g. POCO on Recent search).
            insets
        }
        // RM_010b 2026-07-03 — version-agnostic keyboard detector for the bottom nav.
        findViewById<View>(R.id.main)?.viewTreeObserver
            ?.addOnGlobalLayoutListener(keyboardNavListener)
        
        // Double-check visibility after layout
        binding.bottomNavigationView.post {
            binding.bottomNavigationView.visibility = View.VISIBLE
            binding.bottomNavigationView.bringToFront()
            // Ensure status bar stays pink
            window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        }
        BaseApplication.getInstance()?.messageCameWhenIsAlive = 1

        // Welcome-gift (₹1 trial) dialog is NOT shown here: at onCreate the
        // subscription cache isn't populated yet, so the eligibility gate can't
        // be trusted. HomeFragment triggers showWelcomeGiftDialog() once its
        // subscription_status observer has the real state (cold-start safe).

        fromApplication = intent.getBooleanExtra("fromApplication", false)

        checkIndividualPaymentType()
        setupNavigateToMainAfterPurchaseObserver()

        try {
            CFPaymentGatewayService.getInstance().setCheckoutCallback(this)
        } catch (e: CFException) {
            e.printStackTrace()
        }


//        var gender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
//        if (gender=="female"){
//            ZohoSalesIQ.showLauncher(true)
//        }

        // C-27: the notification-permission request now lives in ONE place (the Android 13+
        // block below). The previous duplicate OneSignal.Notifications.requestPermission() here
        // raced the AndroidX launcher (two native prompts fired on the same pass because the
        // block below re-checks the stale local lastAskedTime), which could leave the OneSignal
        // push subscription stuck "unsubscribed" — every chat push then came back from OneSignal
        // as "All included players are not subscribed" and reached zero devices.
        val notifPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val lastAskedTime = notifPrefs.getLong("notif_permission_last_asked", 0L)
        val oneDayMillis = 24 * 60 * 60 * 1000L

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        AppEventsLogger.newLogger(this).logEvent("TestEventFromApp")

        logDailyActiveUserIfNeeded()

        // Check rating eligibility for FEMALE users immediately (they don't have bottom sheet)
        if (userData?.gender == DConstants.FEMALE) {
            userData.id?.let { userId ->
                Log.d("RatingEligibility", "Female user - calling check_rating_eligibility")
                checkRatingEligibility(userId)
            }
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (System.currentTimeMillis() - lastAskedTime >= oneDayMillis) {
                    notifPrefs.edit().putLong("notif_permission_last_asked", System.currentTimeMillis()).apply()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                // C-27: permission already granted — make sure the OneSignal push subscription is
                // actually registered as SUBSCRIBED (it can be left opted-out if optIn ran before
                // the OS grant on an earlier launch).
                reassertOneSignalPushSubscription()
            }
        } else {
            // Pre-Android-13: notifications are granted by default; just ensure the subscription.
            reassertOneSignalPushSubscription()
        }

        userData?.let { ud ->
            if (ud.gender == DConstants.FEMALE || ud.gender == DConstants.MALE) {
                CallPermissionHelper.maybePromptCallReliabilityPermissions(this)
            }
        }

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            currentVersion = pInfo.versionCode.toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        appUpdateManager = AppUpdateManagerFactory.create(applicationContext)

        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                Log.e("Update", "Update flow failed! Result code: ${result.resultCode}")
            }
        }
       // appUpdateViewModel.appUpdate()


        userData?.let { individualAppUpdateViewModel.checkUserAppVersion(it.id,currentVersion) }

//        appUpdateViewModel.appUpdateResponseLiveData.observe(this, Observer {
//            if (it != null && it.success) {
//
//                val latestVersion = it.data[0].app_version.toString()
//                checkForInAppUpdate(latestVersion)
//
//            }
//        })

        individualAppUpdateViewModel.individualUpdateLiveData.observe(this) { response ->

            if (response != null && response.success) {
                val data = response.data
                val link = response.data.link
                val description = response.data.description


                if (data.current_version.toInt()>= data.minimum_version.toInt()){
                    Log.d("VerisonUpdate","You are to date")
                }else if (data.current_version.toInt() < data.minimum_version.toInt() &&
                    data.update_type == "mandatory") {
                    Log.d("individualAppUpdateViewModel","Mandatory")
                    showUpdateDialog(link, description)
                } else {
                    checkForInAppUpdate()
                }
            }
        }








        userID = userData?.id.toString()
//        if (userID!=null){
//            OneSignal.login(userID!!)
//            Log.e("OneSignalLogin", "User ID is $userID - MainActivity")
//
//            val externalId = OneSignal.User.externalId
//            Log.d("OneSignalExternalId", "externalId : $externalId")
//
//           OneSignal.User.pushSubscription.optIn()
//        }

        // Call user-install-referrer API if user is logged in, gender is male, and install referrer data exists
        userData?.id?.let { userId ->
            if (userData.gender == DConstants.MALE) {
                val savedResponseData = BaseApplication.getInstance()?.getPrefs()?.getString("install_referrer_response_data")
                if (!savedResponseData.isNullOrEmpty()) {
                    callUserInstallReferrerApi(userId, savedResponseData)
                }
            }
        }

        // Call tracking_info with saved_address + user_id
        userData?.id?.let { userId ->
            val prefs = BaseApplication.getInstance()?.getPrefs()
            val savedAddress = prefs?.getString("saved_address")
            Log.d("saved_address","$savedAddress")
            if (!savedAddress.isNullOrBlank()) {
                callTrackingInfoApi(userId, savedAddress)
            }
        }

        billingManager = BillingManager(this)
        accountViewModel.getSettings()
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.let { WalletViewModel.getCoins(it.id) }


        showBlockWordsDetectedDialog()

        Handler(Looper.getMainLooper()).post {
            checkAndShowBlockwordDialog()
        }

            initUI()
        getSkuListID()
        addObservers()
        intializePhonpe()

        updateFcmToken(userData?.id)

        userName = userData?.name

        onBackPressedDispatcher.addCallback(this) {
            if (isBackPressedAlready) {
                finish()
            } else {
                showAppToast(getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT)
                isBackPressedAlready = true
                Handler().postDelayed({
                    isBackPressedAlready = false
                }, 3000)
            }
        }
    }
//    override fun resumeZegoCloud(){
//        addRoomStateChangedListener()
//        moveTaskToBack(true)
//    }



    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        com.gmwapp.hima.utils.OneSignalDiag.dump(this, "post_perm_result.granted=$isGranted")
        if (isGranted) {
            // C-27: register the OneSignal push subscription right now instead of waiting for the
            // next cold start — otherwise the user stays "unsubscribed" and keeps missing chat
            // pushes for the rest of this session even though they just granted permission.
            reassertOneSignalPushSubscription()
        } else {
            maybeShowNotificationImportance()
        }
    }

    /**
     * C-27: re-assert the OneSignal external id + push opt-in so the subscription is registered as
     * SUBSCRIBED once notification permission exists. login()/optIn() are idempotent no-ops when the
     * state already matches (the same contract BaseApplication's cold-start subscribe relies on).
     */
    private fun reassertOneSignalPushSubscription() {
        runCatching {
            val uid = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            if (uid != null && uid > 0) {
                OneSignal.login(com.gmwapp.hima.BuildConfig.ONESIGNAL_EXTERNAL_PREFIX + uid.toString())
                OneSignal.User.pushSubscription.optIn()
            }
        }.onFailure { android.util.Log.w("OneSignalFix", "reassert push subscription failed: ${it.message}") }
    }

    private fun maybeShowNotificationImportance() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val key = "notif_importance_last_shown"
        val last = prefs.getLong(key, 0L)
        val oneDay = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - last < oneDay) return
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        startActivity(Intent(this, NotificationImportanceActivity::class.java))
    }



    private fun initUI() {

        FcmUtils.greyScreenLiveData.postValue("NoData")

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


        val prefs = BaseApplication.getInstance()?.getPrefs()
        prefs?.getUserData()?.id?.let { profileViewModel.getUsers(it) }

        profileViewModel.getUserLiveData.observe(this, Observer { response ->
            response?.data?.let { userData ->
                // B075 — bootstrap refresh; preserve toggle / DND intent.
                prefs?.setUserDataPreservingLocalIntent(userData)
            } ?: run {
                Log.e("Observer", "RegisterResponse is null")
            }
        })


        Log.d("DEBUG", "Received userID: $userID")

        userID?.toIntOrNull()?.let { offerViewModel.getOffer(it) }
        
        // 2026-05-22: Favourite is now MALE-only (female creators don't favourite back —
        // the Add-to-Favourite action lives on the male side after a call ends).
        // Female nav: Home, Chat, Recent, Profile (4 items).
        // Male nav:   Home, Recent, Favourite, Profile (4 items; Chat hidden).
        val userGender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
        binding.bottomNavigationView.menu.findItem(R.id.favourite)?.isVisible = (userGender == DConstants.MALE)
        binding.bottomNavigationView.menu.findItem(R.id.chat)?.isVisible = (userGender == DConstants.FEMALE)
        
        binding.bottomNavigationView.setOnNavigationItemSelectedListener(this)
        
        // Ensure bottom navigation is visible on top
        binding.bottomNavigationView.bringToFront()
        binding.bottomNavigationView.invalidate()
        
        removeShiftMode()
    }


//    private fun observePaymentType(){
//        WalletViewModel.coinsLiveData.observe(this, Observer {
//
//            val firstCoinItem = it.data?.firstOrNull()
//                firstCoinItem?.let { coinItem ->
//                    val paymentGatewayType= "${coinItem.pg}"
//                    Log.d("paymentType","$paymentGatewayType")
//                    paymentGateway = paymentGatewayType
//                }
//
//        })
//    }

    private fun checkAndShowBlockwordDialog() {
        val prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE)
        val wasDetected = prefs.getBoolean("blockword_detected", false)

        Log.d("blockword_detected","$wasDetected")
        if (wasDetected) {
            prefs.edit().putBoolean("blockword_detected", false).apply() // Reset

            // Show the dialog
            showBlockWordsDetectedDialogFemale()
        }
    }

    private fun showBlockWordsDetectedDialogFemale(){


            if (blockWordDialog?.isShowing == true) return  // Already showing

            blockWordDialog = Dialog(this).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(R.layout.dialog_block_words_detected)
                window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.9).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                findViewById<Button>(R.id.btn_iUnderstand)?.setOnClickListener {
                    dismiss()  // Dismiss the dialog
                }
                show()
            }


    }

    // Welcome-gift (₹1 trial) dialog. Gated to male users on autopay languages
    // who have never had an autopay mandate (see WelcomeGiftPromo). The ₹1 CTA
    // opens the real autopay checkout; Skip Now closes. Called by HomeFragment
    // once subscription_status is known. Public + idempotent.
    private var welcomeGiftDialog: Dialog? = null

    fun showWelcomeGiftDialog() {
        if (isFinishing || isDestroyed) return
        if (welcomeGiftDialog?.isShowing == true) return
        if (!com.gmwapp.hima.utils.WelcomeGiftPromo.isEligible(this)) return

        welcomeGiftDialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_welcome_gift)
            setCancelable(true)
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            window?.setBackgroundDrawableResource(android.R.color.transparent)

            findViewById<View>(R.id.tvSkip)?.setOnClickListener { dismiss() }
            findViewById<View>(R.id.btnTrial)?.setOnClickListener {
                startActivity(
                    AutopayCheckoutActivity.intentFor(
                        this@MainActivity,
                        AutopayCheckoutActivity.PLAN_TRIAL_NEW
                    )
                )
                dismiss()
            }
            show()
        }
    }

    private fun showUpdateDialog(link: String, description: String) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_dialog_update, null)
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.setCancelable(false);

        val btnUpdate = view.findViewById<View>(R.id.btnUpdate)
        val dialogMessage = view.findViewById<TextView>(R.id.dialog_message)
        dialogMessage.text = description
        btnUpdate.setOnClickListener(View.OnClickListener {
            val url = link;
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        })


        // Customize your bottom dialog here
        // For example, you can set text, buttons, etc.

        bottomSheetDialog.show()
    }


    private fun showBlockWordsDetectedDialog(){
        val isBlockWord = intent.getBooleanExtra("blockword",false)
        if (isBlockWord){

            if (blockWordDialog?.isShowing == true) return  // Already showing

            blockWordDialog = Dialog(this).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(R.layout.dialog_block_words_detected)
                window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.9).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                findViewById<Button>(R.id.btn_iUnderstand)?.setOnClickListener {
                    dismiss()  // Dismiss the dialog
                }
                show()
            }

        }
    }



    private fun addObservers() {
        offerViewModel.offerResponseLiveData.observe(this) { response ->
            // Fallback: if best_offers is false/empty then also call free_coins_status and rating eligibility
            val currentUserData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            val currentUserId = currentUserData?.id
            if (!hasTriggeredFreeCoinsStatus &&
                currentUserId != null &&
                currentUserData.gender == DConstants.MALE &&
                (response == null || response.success != true || response.data.isNullOrEmpty())
            ) {
                hasTriggeredFreeCoinsStatus = true
                Log.d("FreeCoinsStatus", "best_offers is false/empty → calling free_coins_status (fallback)")
                callFreeCoinsStatusApi(currentUserId)
                
                // Also check rating eligibility when bottom sheet is not shown
                Log.d("RatingEligibility", "No bottom sheet → calling check_rating_eligibility")
                checkRatingEligibility(currentUserId)
            }

            if (response?.success == true && !response.data.isNullOrEmpty()) {
                val coin = response.data[0].coins
                val discountedPrice = response.data[0].price
                val save = response.data[0].save
                val coinId = response.data[0].id
                val total_count = response.data[0].total_count
              //  paymentGateway = response.data[0].pg


                val originalPrice = calculateOriginalPrice(discountedPrice, save)


               // Log.d("offerRechargeTotalCount","totalcount ${response.data[0].total_count}")

                Log.d("OrinalPrice","OriginalPrice $originalPrice")
                Log.d("OrinalPrice","discountPrice $discountedPrice")
                Log.d("OrinalPrice","savePercent $save")
                val isBlockWord = intent.getBooleanExtra("blockword", false)

                if (!isBlockWord && !fromApplication && BaseApplication.getInstance()?.getPrefs()
                        ?.getUserData()?.gender == DConstants.MALE
                ) {

                    val existing = supportFragmentManager.findFragmentByTag("BottomSheetWelcomeBonus")
                    if (existing == null) {
                        val bottomSheet = BottomSheetWelcomeBonus.newInstance(
                            coin,
                            originalPrice,
                            discountedPrice,
                            coinId,
                            total_count
                        )
                        
                        // Set dismiss listener to call free_coins_status API and rating eligibility
                        bottomSheet.setOnDismissListener(object : BottomSheetWelcomeBonus.OnDismissListener {
                            override fun onBottomSheetDismissed() {
                                Log.d("BottomSheetWelcomeBonus", "✅ Bottom sheet dismissed - calling free_coins_status API")
                                
                                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                                userData?.id?.let { userId ->
                                    Log.d("BottomSheetWelcomeBonus", "📡 Calling free_coins_status API with userId: $userId")
                                    if (!hasTriggeredFreeCoinsStatus) {
                                        hasTriggeredFreeCoinsStatus = true
                                        callFreeCoinsStatusApi(userId)
                                    }
                                    
                                    // Check rating eligibility after bottom sheet is dismissed (for male users)
                                    Log.d("BottomSheetWelcomeBonus", "📡 Calling check_rating_eligibility API with userId: $userId")
                                    checkRatingEligibility(userId)
                                }
                            }
                        })
                        
                        bottomSheet.show(supportFragmentManager, "BottomSheetWelcomeBonus")
                        Log.d("BottomSheetWelcomeBonus", "Bottom sheet shown with dismiss listener set")
                    }
                }
            }
        }
        binding.bottomNavigationView.setOnNavigationItemSelectedListener(this)
        binding.bottomNavigationView.selectedItemId = R.id.home

        // B034 — if the launching intent asked for a specific tab (e.g.
        // missed-call notification → "recent"), honour it AFTER the default
        // Home selection so the deep-link wins. selectedItemId triggers
        // onNavigationItemSelected → the right Fragment swap.
        routeToTabIfRequested(intent)

        // Ensure bottom navigation is always visible on top
        binding.bottomNavigationView.bringToFront()
        binding.bottomNavigationView.invalidate()

        removeShiftMode()
    }

    /**
     * B034 — when launched (or re-launched via singleTop's onNewIntent) with
     * [EXTRA_OPEN_TAB], jump the bottom nav to the requested tab. Today the
     * only consumer is the missed-call notification PendingIntent built in
     * [com.gmwapp.hima.utils.CallNotifications.showMissed], routing to
     * [TAB_RECENT].
     */
    private fun routeToTabIfRequested(routingIntent: Intent?) {
        val tab = routingIntent?.getStringExtra(EXTRA_OPEN_TAB) ?: return
        val targetItem = when (tab) {
            TAB_RECENT -> R.id.recent
            TAB_FAVOURITE -> R.id.favourite
            TAB_CHAT -> R.id.chat
            else -> {
                android.util.Log.w("MainActivity", "Unknown OPEN_TAB extra: $tab")
                return
            }
        }
        if (binding.bottomNavigationView.menu.findItem(targetItem) == null) {
            // The target menu item is hidden for gender-restricted users in
            // some flows — bail rather than crash.
            android.util.Log.w("MainActivity", "OPEN_TAB target $tab not in current bottom nav")
            return
        }
        // Stash the requested Friends/Chat sub-tab BEFORE selecting the tab so the
        // hub fragment can pick it up in its onResume after the swap (-1 = none).
        pendingFriendsSubTab = routingIntent.getIntExtra(EXTRA_OPEN_SUBTAB, -1)
        if (binding.bottomNavigationView.selectedItemId != targetItem) {
            binding.bottomNavigationView.selectedItemId = targetItem
        } else {
            // Already on the target tab — no fragment swap (so no onResume), apply now.
            applyPendingFriendsSubTabToCurrentHub()
        }
    }

    /** Friends/Chat hub sub-tab requested by a notification deep-link; consumed once by the hub fragment (-1 = none). */
    var pendingFriendsSubTab: Int = -1
        private set

    /** Consumed by [FriendsHubFragment]/[CreatorChatFragment] to jump to the requested sub-tab. */
    fun consumePendingFriendsSubTab(): Int {
        val v = pendingFriendsSubTab
        pendingFriendsSubTab = -1
        return v
    }

    private fun applyPendingFriendsSubTabToCurrentHub() {
        when (val current = supportFragmentManager.findFragmentById(R.id.flFragment)) {
            is FriendsHubFragment -> current.applyPendingSubTab()
            is CreatorChatFragment -> current.applyPendingSubTab()
        }
    }


    /**
     * Avoids stacking multiple async [androidx.fragment.app.FragmentTransaction.replace] + [commit]
     * calls, which can leave [R.id.flFragment] empty when tabs are tapped very quickly.
     */
    private fun isAlreadyShowingTab(itemId: Int): Boolean {
        val current = supportFragmentManager.findFragmentById(R.id.flFragment) ?: return false
        return when (itemId) {
            R.id.home -> current is HomeFragment || current is FemaleHomeFragment
            R.id.chat -> current is CreatorChatFragment
            R.id.recent -> current is RecentFragment
            R.id.favourite -> current is FriendsHubFragment
            R.id.profile -> current is ProfileFragment || current is ProfileFemaleFragment
            else -> false
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        supportFragmentManager.executePendingTransactions()
        if (isAlreadyShowingTab(item.itemId)) {
            // User re-tapped the tab they're already on.
            val current = supportFragmentManager.findFragmentById(R.id.flFragment)
            if (item.itemId == R.id.home && current is HomeFragment) {
                // Re-tapping Home always returns to the All tab, even if they'd switched
                // to Chats / New / Star / an interest (showAllTab refreshes if already on All).
                current.showAllTab()
            } else {
                // Otherwise just refresh the current tab's data.
                (current as? Refreshable)?.refresh()
            }
            return true
        }

        val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(30)
        }

        animateBottomNavItem(item)

        val transaction = supportFragmentManager.beginTransaction()
        transaction.setReorderingAllowed(true)
        transaction.setCustomAnimations(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )

        when (item.itemId) {
            R.id.home -> {
                window.statusBarColor = ContextCompat.getColor(this, R.color.white)

                val homeFragment = if (BaseApplication.getInstance()?.getPrefs()
                        ?.getUserData()?.gender == DConstants.FEMALE
                ) FemaleHomeFragment() else HomeFragment()
                transaction.replace(R.id.flFragment, homeFragment).commit()
                return true
            }

            R.id.chat -> {
                window.statusBarColor = ContextCompat.getColor(this, R.color.white)
                // B_010 — opening the Chat tab acknowledges the badge (Instagram-style):
                // clear it now, and mark the current unread+requests total as "seen" so it
                // stays clear until something NEW pushes the total above this level.
                chatBadgeSeen = true
                chatBadgeSeenLevel = (chatFriendsUnread.coerceAtLeast(0) + chatRequestsUnread.coerceAtLeast(0))
                binding.bottomNavigationView.removeBadge(R.id.chat)
                transaction.replace(R.id.flFragment, CreatorChatFragment()).commit()
                return true
            }

            R.id.recent -> {
                window.statusBarColor = ContextCompat.getColor(this, R.color.white)

                transaction.replace(R.id.flFragment, RecentFragment()).commit()
                return true
            }

            R.id.favourite -> {
                window.statusBarColor = ContextCompat.getColor(this, R.color.white)
                // B_015 — opening the Friends tab acknowledges the badge (Instagram-style):
                // clear it now and keep it clear until a new request pushes the count above
                // this level.
                friendsBadgeSeen = true
                friendsBadgeSeenLevel = friendsRequestsUnread.coerceAtLeast(0)
                binding.bottomNavigationView.removeBadge(R.id.favourite)
                transaction.replace(R.id.flFragment, FriendsHubFragment()).commit()
                return true
            }

            R.id.profile -> {
                window.statusBarColor = ContextCompat.getColor(this, R.color.grey_extra_light)

                if (BaseApplication.getInstance()?.getPrefs()
                        ?.getUserData()?.gender == DConstants.MALE
                ) {
                    transaction.replace(R.id.flFragment, ProfileFragment()).commit()
                } else {
                    transaction.replace(R.id.flFragment, ProfileFemaleFragment()).commit()
                }
                return true
            }
        }
        return false
    }
    
    private fun animateBottomNavItem(selectedItem: MenuItem) {
        // Get the bottom navigation view
        val bottomNav = binding.bottomNavigationView
        
        // Animate all items
        for (i in 0 until bottomNav.menu.size()) {
            val menuItem = bottomNav.menu.getItem(i)
            val view = bottomNav.findViewById<View>(menuItem.itemId)
            
            if (view != null) {
                if (menuItem.itemId == selectedItem.itemId) {
                    // Selected item - bounce animation
                    val animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.bottom_nav_bounce)
                    view.startAnimation(animation)
                } else {
                    // Unselected items - subtle scale down
                    view.animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(150)
                        .withEndAction {
                            view.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150)
                                .start()
                        }
                        .start()
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    fun removeShiftMode() {
        binding.bottomNavigationView.labelVisibilityMode =
            NavigationBarView.LABEL_VISIBILITY_LABELED
        val bottomNav = binding.bottomNavigationView
        // Post so this runs after Material binds menu items (tooltip can be re-applied earlier)
        bottomNav.post {
            val menuView = bottomNav.getChildAt(0) as BottomNavigationMenuView
            for (i in 0 until menuView.childCount) {
                val item = menuView.getChildAt(i) as BottomNavigationItemView
                item.setShifting(false)
                item.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED)
                ViewCompat.setTooltipText(item, null)

                // set once again checked value, so view will be updated
                item.setChecked(item.itemData!!.isChecked)
                // After Material updates state, consume long-press so tab title tooltip never shows
                item.setOnLongClickListener { true }
            }
        }
    }




    override fun onAddCoins(amount: String, id: Int) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id
        var pointsId = "$id"
        val pointsIdInt = pointsId.toIntOrNull()
        val priceDouble = amount?.toDoubleOrNull() ?: 0.0

        total_amount = "$amount"

        // 2026-05-24 v1074 — Meta initial_checkout RESTORED per marketing:
        // they now want every Firebase/Google-Ads event also in Meta.
        val checkoutAmount = amount.toDoubleOrNull() ?: 0.0
        if (checkoutAmount > 0.0) {
            try {
                val metaParams = Bundle().apply {
                    putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
                    putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, checkoutAmount)
                    putString("user_id", "$userId")
                    putString("coin_id", "$pointsId")
                }
                AppEventsLogger.newLogger(this).logEvent(
                    "initial_checkout",
                    checkoutAmount,
                    metaParams
                )
            } catch (t: Throwable) {
                Log.w("FB_Event", "Meta initial_checkout failed: ${t.message}")
            }
        }


        val af_price = getDiscountedPriceFromTotal(total_amount)
        MmpClient.trackEvent(
            eventName = "initiated_checkout",
            revenue = af_price.toDouble(),
            params = mapOf("coin_id" to "$pointsId"),
            customerUserId = userId?.toString()
        )

        val firebaseBundle = Bundle().apply {
            putString("user_id", "$userId")
            putString("coin_id", "$pointsId")
            putDouble("price", priceDouble)
        }
        BaseApplication.firebaseAnalytics.logEvent("initial_checkout", firebaseBundle)

        // Log to backend (only Firebase events)
        AppEventLogger.logEvent(
            context = this,
            eventName = "initial_checkout",
            platform = "firebase",
            userId = userId,
            params = AppEventLogger.bundleToMap(firebaseBundle),
            value = priceDouble
        )

        // Adjust (mirrors alongside Meta + MMP + Firebase).
        com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
            "initial_checkout",
            revenueInr = af_price.toDouble(),
            params = mapOf("user_id" to "$userId", "coin_id" to "$pointsId")
        )

        BaseApplication.getInstance()?.getPrefs()?.apply {
            setString("last_coin_id", pointsId)
            setString("last_coin_amount", amount.toString())
        }

        if (userId != null && pointsId.isNotEmpty() && pointsIdInt != null) {
            if (paymentGateway.isNotEmpty()) {
                startAddCoinsPaymentFlow(userId, pointsId, pointsIdInt, id)
            } else {
                fetchPaymentGatewayAndStart(
                    mobile = userData?.mobile,
                    userId = userId,
                    pointsId = pointsId,
                    pointsIdInt = pointsIdInt,
                    selectedCoinId = id
                )
            }
        } else {
            showAppToast("Invalid input data", Toast.LENGTH_SHORT)
        }
    }

    private fun fetchPaymentGatewayAndStart(
        mobile: String?,
        userId: Int,
        pointsId: String,
        pointsIdInt: Int,
        selectedCoinId: Int
    ) {
        if (mobile.isNullOrBlank()) {
            showAppToast("Please try again later", Toast.LENGTH_SHORT)
            return
        }

        showAppToast("Please wait...", Toast.LENGTH_SHORT)

        val observer = object : Observer<LoginResponse> {
            override fun onChanged(response: LoginResponse) {
                loginViewModel.loginResponseLiveData.removeObserver(this)

                val gateway = response.data?.payment_type
                if (response.success && !gateway.isNullOrBlank()) {
                    paymentGateway = gateway
                    startAddCoinsPaymentFlow(userId, pointsId, pointsIdInt, selectedCoinId)
                } else {
                    showAppToast("Payment gateway not available", Toast.LENGTH_SHORT)
                }
            }
        }

        loginViewModel.loginResponseLiveData.observe(this, observer)
        loginViewModel.login(mobile, "0", "0")
    }

    private fun startAddCoinsPaymentFlow(
        userId: Int,
        pointsId: String,
        pointsIdInt: Int,
        selectedCoinId: Int
    ) {
        when (paymentGateway) {
            "phonepe" -> {
                if (isPhonePeInitialized) {
                    fetchOrderFromBackend(pointsId)
                } else {
                    showAppToast("Please try again later", Toast.LENGTH_SHORT)
                }
            }

            "gpay" -> {
                val random4Digit = (1000..9999).random()

                val preferences = DPreferences(this)
                preferences.clearSelectedOrderId()
                preferences.setSelectedUserId(userId.toString())
                preferences.setSelectedPlanId(java.lang.String.valueOf(pointsIdInt))
                preferences.setSelectedOrderId(java.lang.String.valueOf(random4Digit))
                WalletViewModel.tryCoins(userId, pointsIdInt, 0, random4Digit, "try")
                pendingPurchaseMetaFromMainActivityGpay = true
                billingManager!!.purchaseProduct(pointsId)
            }

            "razorpay" -> {
                callNewRazorPay = apiService.callNewRazorPay(userId, pointsId)
                callNewRazorPay.enqueue(object : retrofit2.Callback<NewRazorpayLinkResponse> {
                    override fun onResponse(
                        call: retrofit2.Call<NewRazorpayLinkResponse>,
                        response: retrofit2.Response<NewRazorpayLinkResponse>
                    ) {
                        if (response.isSuccessful && response.body() != null) {
                            val paymentUrl = response.body()?.data?.short_url
                            Log.d("paymentUrlRazorPay", "$paymentUrl")

                            if (!paymentUrl.isNullOrEmpty()) {
                                val intent = Intent(this@MainActivity, LauncherActivity::class.java)
                                intent.data = Uri.parse(paymentUrl)
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
                val currentUserData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                currentUserData?.id?.let {
                    val clientTxnId = generateRandomTxnId(it, selectedCoinId.toString())
                    upiPaymentViewModel.createUpiPayment(it, clientTxnId, total_amount)
                }
            }

            else -> {
                showAppToast("Invalid Payment Gateway", Toast.LENGTH_SHORT)
            }
        }
    }


//    override fun onAddCoins(amount: String, id: Int) {
//
//        total_amount = "$amount"
//        var pointsId = "$id"
//        coinId = id.toString()
//        Log.d("amount", "amount $total_amount")
//
//        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
//
//        val userId = userData?.id
//        val name = userData?.name ?: ""
//        val email = "test@gmail.com"
//        val mobile = userData?.mobile ?: ""
//
//        if (userId != null && pointsId.isNotEmpty() && total_amount.isNotEmpty()) {
//            val userIdWithPoints = "$userId-$pointsId"
//
//            val apiService = RetrofitClient.instance
//            call = apiService.addCoins(name, total_amount, email, mobile, userIdWithPoints)
//
//            callRazor = apiService.addCoinsRazorPay(userIdWithPoints,name,total_amount,email,mobile)
//
//            accountViewModel.getSettings()
//
//
//
//
////            call.enqueue(object : retrofit2.Callback<ApiResponse> {
////                override fun onResponse(
////                    call: retrofit2.Call<ApiResponse>,
////                    response: retrofit2.Response<ApiResponse>
////                ) {
////                    if (response.isSuccessful && response.body()?.success == true) {
////                        Toast.makeText(
////                            this@MainActivity,
////                            response.body()?.message,
////                            Toast.LENGTH_SHORT
////                        ).show()
////                    } else {
////                        // println("Long URL: ${it.longurl}") // Print to the terminal
////                        //Toast.makeText(mContext, it.longurl, Toast.LENGTH_SHORT).show()
////                        val intent = Intent(this@MainActivity, LauncherActivity::class.java)
////                        intent.setData(Uri.parse(response.body()?.longurl))
////                        startActivity(intent)
////                        //  showAppToast(response.body()?.message ?: "Error", Toast.LENGTH_SHORT)
////                    }
////                }
////
////                override fun onFailure(call: retrofit2.Call<ApiResponse>, t: Throwable) {
////                    showAppToast("Failed: ${t.message}", Toast.LENGTH_SHORT)
////                        .show()
////                }
////            })
//        } else {
//            showAppToast("Invalid input data", Toast.LENGTH_SHORT)
//        }
//    }

    fun calculateOriginalPrice(price: Int, savePercentage: Int): Int {
        val originalPrice = price / (1 - (savePercentage / 100.0)) // Use Double for division
        return round(originalPrice).toInt() // Round to the nearest integer
    }



    fun generateRandomTxnId(userId: Int, coinId: String): String {
        return "$userId-$coinId-${System.currentTimeMillis()}"
    }


    fun updateFcmToken(userId: Int?) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            // Get new FCM registration token
            val token = task.result
            Log.d("FCM", "Device token: $token")

            userId?.let {
                // User is signing in — drop any pending logout-time FCM invalidation
                // for this userId so we don't race-reset the token we're about to register.
                androidx.work.WorkManager.getInstance(applicationContext)
                    .cancelUniqueWork(
                        "${com.gmwapp.hima.workers.FcmTokenInvalidationWorker.WORK_NAME_PREFIX}$it"
                    )
                // Send token to backend
                fcmTokenViewModel.sendToken(it, token)
            }
            observeTokenResponse()
        }
    }

    fun observeTokenResponse() {
        fcmTokenViewModel.tokenResponseLiveData.observe(this) { response ->
            Log.d("FCMToken", "$response")

            response?.let {
                if (it.success) {
                    Log.d("FCMToken", "Token saved successfully!")
                } else {
                    Log.e("FCMToken", "Failed to save token")
                }
            }
        }

    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.MANAGE_OWN_CALLS
                )
            )
        }
    }

    override fun onStart() {
        super.onStart()
        requestPermissions()
    }

    private fun routeIncomingCallIfPending() {
        val app = BaseApplication.getInstance() ?: return
        if (!app.isIncomingCallFresh()) return

        val senderId = app.getSenderIdForSplashActivity()
        if (senderId <= 0) return

        val callType = app.getCallTypeForSplashActivity()
        val channel = app.getChannelName()
        val callId = app.getCallIdForSplashActivity() ?: 0
        val callerName = app.getIncomingCallerName()
        val callerImage = app.getIncomingCallerImage()

        val gender = app.getPrefs()?.getUserData()?.gender
        val acceptCls = if (gender == DConstants.FEMALE)
            FemaleCallAcceptActivity::class.java
        else
            MaleCallAcceptActivity::class.java

        val intent = Intent(this, acceptCls).apply {
            // Use SINGLE_TOP so re-entering with the same call doesn't
            // stack a second accept activity; CLEAR_TOP brings the
            // existing one to front if it's already alive.
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channel)
            putExtra("Caller_NAME", callerName)
            putExtra("Caller_Image", callerImage)
            putExtra("CALL_ID", callId)
        }
        Log.d("HimaIncomingCall", "MainActivity routing to $acceptCls (senderId=$senderId callType=$callType)")
        startActivity(intent)
    }


    private fun checkForInAppUpdate(){



            appUpdateManager = AppUpdateManagerFactory.create(applicationContext)
            // Before starting an update, register a listener for updates.
            appUpdateManager.registerListener(listener)

            // Returns an intent object that you use to check for an update.
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo

            // Checks that the platform will allow the specified type of update.
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    // This example applies an immediate update. To apply a flexible update
                    // instead, pass in AppUpdateType.FLEXIBLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    // Request the update.

                    appUpdateManager.startUpdateFlowForResult(
                        // Pass the intent that is returned by 'getAppUpdateInfo()'.
                        appUpdateInfo,
                        // an activity result launcher registered via registerForActivityResult
                        activityResultLauncher,
                        // Or pass 'AppUpdateType.FLEXIBLE' to newBuilder() for
                        // flexible updates.
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()

                    )

                } else {
                    Log.d("UpdateCheck", "No update available.")
                }


            }.addOnFailureListener { exception ->
                Log.e("UpdateCheck", "Failed to check for update: ${exception.message}")
            }



    }

    val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            popupSnackbarForCompleteUpdate()
        }
    }

    fun popupSnackbarForCompleteUpdate() {
        Snackbar.make(
            binding.root,  // Default root container
            "An update has just been downloaded.",
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction("RESTART") { appUpdateManager.completeUpdate() }
            setActionTextColor(getColor(R.color.white))
            show()
        }
    }


    override fun onStop() {
        super.onStop()
        appUpdateManager.unregisterListener(listener)

    }

    // RM_010b 2026-07-03 — universal keyboard detector. WindowInsets Type.ime()
    // visibility is only reliably reported on API 30+, so on Android 7–10 the
    // inset-based nav-hide never fired and the bottom nav floated above the
    // keyboard (e.g. Recent search) on those phones — reported on a POCO device.
    // getWindowVisibleDisplayFrame works on every version: if the gap between the
    // full screen height and the visible bottom exceeds 15% of the screen, the
    // keyboard is open → hide the nav; otherwise show it. Guarded + idempotent
    // (only touches visibility on change) so the frequent layout passes stay cheap.
    private val keyboardNavListener = ViewTreeObserver.OnGlobalLayoutListener {
        runCatching {
            if (!::binding.isInitialized) return@runCatching
            val root = findViewById<View>(R.id.main) ?: return@runCatching
            val visible = Rect()
            root.getWindowVisibleDisplayFrame(visible)
            val screenHeight = root.rootView.height
            if (screenHeight <= 0) return@runCatching
            val keyboardOpen = screenHeight - visible.bottom > screenHeight * 0.15
            val target = if (keyboardOpen) View.GONE else View.VISIBLE
            if (binding.bottomNavigationView.visibility != target) {
                binding.bottomNavigationView.visibility = target
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(listener)
        runCatching {
            findViewById<View>(R.id.main)?.viewTreeObserver
                ?.removeOnGlobalLayoutListener(keyboardNavListener)
        }
    }



    override fun onResume() {
        super.onResume()

        com.gmwapp.hima.utils.OneSignalDiag.dump(this, "main_activity_resumed")

        checkIndividualPaymentType()
        handleInsufficientCoinPaywallIntent()

        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()
        userData?.id?.let { userId ->
            callPaywallVideoContentApi(userId)
            // Warm the trial-offer video cache so when the user opens the
            // wallet (or any surface that shows BottomSheetTrialOffer) the
            // mp4 plays from disk in <1 s instead of streaming the 20+ MB
            // clip from demohima on every open. Safe to call repeatedly —
            // single-flight + skips if the file is already cached.
            com.gmwapp.hima.utils.TrialOfferConfigCache.prefetch(this, apiManager, userId)
        }
        if (userData?.gender=="female") {
            ZohoHelper.initZohoWithUser(userData, zohoMailViewModel)
        }
        checkAndShowBlockwordDialog()
        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                // If the update is downloaded but not installed,
                // notify the user to complete the update.
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    popupSnackbarForCompleteUpdate()
                }
            }

        if (cashfreeLastOrderId.isNotEmpty()){
            checkCashfreeOderStatus(cashfreeLastOrderId)
            cashfreeLastOrderId = "" // reset so it won't run again
        }
        
        // Check and show rating prompt if conditions are met
        userData?.id?.let { userId ->
            ratingPromptHelper.checkAndShowRatingPrompt(this, userId)
            feedbackFormHelper.checkAndShowFeedback(this, userId)
        }

        // Refresh bottom nav badge for missed calls
        loadRecentMissedCountBadge()
        loadChatUnreadCountBadge()
        // Male "Friends" hub badge for pending friend-requests (female path covers requests inside loadChatUnreadCountBadge).
        loadFriendsRequestCountBadge()

        // Realtime: keep the chat badge fresh on incoming pushes / socket events.
        registerChatListBadgeReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterChatListBadgeReceiver()
    }

    private var chatListBadgeReceiver: android.content.BroadcastReceiver? = null
    private var chatListBadgeReceiverRegistered: Boolean = false

    private fun registerChatListBadgeReceiver() {
        if (chatListBadgeReceiverRegistered) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action != com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_LIST_REFRESH) return
                val peerId = intent.getIntExtra(
                    com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.EXTRA_PEER_ID,
                    -1
                )
                // Skip optimistic bump if user is reading that thread.
                if (peerId > 0 &&
                    com.gmwapp.hima.utils.ActiveChatTracker.isActiveFor(this@MainActivity, peerId)
                ) {
                    return
                }
                // Optimistic bump on the friends bucket — `loadChatUnreadCountBadge`
                // immediately afterward corrects the split if the peer actually
                // belongs in `general`. B_010: this pushes the total above the "seen"
                // watermark, so updateChatBadge() re-shows the badge for genuinely new
                // activity even after the tab was previously opened.
                chatFriendsUnread = (chatFriendsUnread + 1).coerceAtLeast(0)
                updateChatBadge()
                loadChatUnreadCountBadge()
            }
        }
        val filter = android.content.IntentFilter(
            com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_LIST_REFRESH
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        chatListBadgeReceiver = receiver
        chatListBadgeReceiverRegistered = true
    }

    private fun unregisterChatListBadgeReceiver() {
        if (!chatListBadgeReceiverRegistered) return
        val receiver = chatListBadgeReceiver ?: return
        runCatching { unregisterReceiver(receiver) }
        chatListBadgeReceiver = null
        chatListBadgeReceiverRegistered = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // B034 — honour tab-deep-link extras when MainActivity is reopened
        // (e.g. missed-call notification while app already in background
        // task). The setIntent() call above is what makes the new payload
        // visible to routeToTabIfRequested.
        routeToTabIfRequested(intent)
        // 2026-05-26 — also track notification opens when the app was
        // already running and brought forward by a tap (vs cold-start
        // which onCreate handles). Same fire-and-forget contract.
        com.gmwapp.hima.utils.NotificationClickTracker.maybeTrack(intent, apiManager)
    }

    fun refreshRecentMissedCountBadge() {
        loadRecentMissedCountBadge()
    }

    fun setRecentMissedCount(count: Int) {
        recentMissedCount = count.coerceAtLeast(0)
        updateRecentBadge()
    }

    private fun loadRecentMissedCountBadge() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        apiManager.getMissedCallCount(userData.id, 0, object : NetworkCallback<MissedCallCountResponse> {
            override fun onResponse(
                call: Call<MissedCallCountResponse>,
                response: retrofit2.Response<MissedCallCountResponse>
            ) {
                val count = if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.data?.missed_call_count ?: 0
                } else {
                    0
                }
                recentMissedCount = count
                updateRecentBadge()
            }

            override fun onFailure(call: Call<MissedCallCountResponse>, t: Throwable) {
                recentMissedCount = 0
                updateRecentBadge()
            }

            override fun onNoNetwork() {
                recentMissedCount = 0
                updateRecentBadge()
            }
        })
    }

    /**
     * Attach a count badge to a bottom-nav item using Material's BadgeDrawable.
     * It anchors to the item's icon and is clipped to the nav bar, so a badge can
     * NEVER strand mid-screen the way the old hand-positioned content-root dots did
     * (e.g. the "3" that landed on Profile ▸ Terms & Condition). Safe no-op when the
     * item is hidden for this gender — Material won't render a badge on a GONE item,
     * and a genuinely-absent id is swallowed rather than crashing.
     */
    private fun setNavBadge(itemId: Int, count: Int, @androidx.annotation.ColorRes colorRes: Int) {
        val nav = binding.bottomNavigationView
        runCatching {
            if (count <= 0) {
                nav.removeBadge(itemId)
                return
            }
            nav.getOrCreateBadge(itemId).apply {
                backgroundColor = ContextCompat.getColor(this@MainActivity, colorRes)
                maxCharacterCount = 3
                number = count
                isVisible = true
            }
        }
    }

    private fun updateRecentBadge() {
        setNavBadge(R.id.recent, recentMissedCount.coerceAtLeast(0), R.color.chat_recording_red)
    }

    fun refreshChatUnreadCountBadge() {
        loadChatUnreadCountBadge()
    }

    // Lets CreatorChatFragment push its freshly-fetched counts up so the badge
    // stays in sync as the creator reads messages, without a duplicate fetch.
    fun updateChatUnreadCountBadge(friendsUnread: Int, generalUnread: Int) {
        chatFriendsUnread = friendsUnread.coerceAtLeast(0)
        chatGeneralUnread = generalUnread.coerceAtLeast(0)
        updateChatBadge()
    }

    /**
     * Public: re-fetch the full Chat badge (unread messages + pending friend-requests)
     * from the server. Called right after a friend-request accept/reject so the bottom-nav
     * badge's request portion updates immediately instead of waiting for the next onResume.
     * The server drops the friend_tabs_counts cache on the mutation, so this returns fresh.
     */
    fun refreshChatUnreadBadge() {
        loadChatUnreadCountBadge()
    }

    private fun loadChatUnreadCountBadge() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        if (userData.gender != DConstants.FEMALE) return

        apiManager.getMyChatFriends(userData.id, null, 100, 0, object : NetworkCallback<com.gmwapp.hima.retrofit.responses.MyChatResponse> {
            override fun onResponse(
                call: Call<com.gmwapp.hima.retrofit.responses.MyChatResponse>,
                response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.MyChatResponse>
            ) {
                chatFriendsUnread = if (response.isSuccessful && response.body()?.success == true) {
                    // B_010 — count CONVERSATIONS that have unread, not the total message
                    // count. A friend who sends 10 messages is one unread chat (1), not 10,
                    // so the badge stays a small, meaningful "how many chats need me" number
                    // and lines up with the "Friends (N)" tab (also unread-chat count).
                    response.body()?.data?.chats?.count { it.unreadCount > 0 } ?: 0
                } else {
                    0
                }
                updateChatBadge()
            }

            override fun onFailure(call: Call<com.gmwapp.hima.retrofit.responses.MyChatResponse>, t: Throwable) {
                chatFriendsUnread = 0
                updateChatBadge()
            }

            override fun onNoNetwork() {
                chatFriendsUnread = 0
                updateChatBadge()
            }
        })

        // NOTE: the `my_chat/general` bucket is deliberately NOT counted here.
        // The female Chats screen only shows Friends / Requests / Sent tabs —
        // there is no "General" tab (TYPE_CHAT_GENERAL is never instantiated), so
        // any unread in the general bucket is invisible and un-openable. Counting
        // it made the home badge read higher (e.g. 5) than what she sees the moment
        // she opens Chats (e.g. 1), which CreatorChatFragment computes without it.
        // Keep both paths honest: badge = friends unread + received friend-requests.
        chatGeneralUnread = 0

        // Pending received friend-requests also surface on the Chat icon — from any tab,
        // not only while the Chat fragment is in front.
        // B_010: only requests she hasn't seen. The watermark rides up with the request.
        apiManager.getFriendTabsCounts(
            userData.id,
            object : NetworkCallback<com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse> {
            override fun onResponse(
                call: Call<com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse>,
                response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse>
            ) {
                chatRequestsUnread = if (response.isSuccessful && response.body()?.success == true) {
                    unseenRequestsOf(response.body()?.data)
                } else {
                    0
                }
                updateChatBadge()
            }

            override fun onFailure(call: Call<com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse>, t: Throwable) {
                chatRequestsUnread = 0
                updateChatBadge()
            }

            override fun onNoNetwork() {
                chatRequestsUnread = 0
                updateChatBadge()
            }
        },
            com.gmwapp.hima.utils.RequestsSeenPrefs.getSeenRequestId(this, userData.id)
        )
    }

    /**
     * B_010: how many received requests the user hasn't seen yet.
     *
     * Prefers the server's watermark-aware count. A server that predates the field sends
     * -1, and we fall back to the full count — badging a request twice is a nuisance;
     * silently badging 0 would hide it entirely, so we fail towards over-notifying.
     */
    private fun unseenRequestsOf(
        data: com.gmwapp.hima.retrofit.responses.FriendTabsCountsData?
    ): Int {
        data ?: return 0
        val fresh = data.received_requests_new_count
        return if (fresh >= 0) fresh.coerceAtLeast(0)
        else data.received_requests_count.coerceAtLeast(0)
    }

    private fun updateChatBadge() {
        // The Chat tab exists only for female users. Skip entirely when it isn't the
        // visible tab so a male never gets a chat badge; friend-requests surface on the
        // Friends (Favourite) badge instead.
        if (binding.bottomNavigationView.menu.findItem(R.id.chat)?.isVisible != true) {
            binding.bottomNavigationView.removeBadge(R.id.chat)
            return
        }
        // Chat icon badge = friends-chat unread + pending received friend-requests.
        // The general bucket is intentionally excluded — it has no tab in the female
        // Chats UI, so counting it would over-report vs. what she can actually open.
        val total = (chatFriendsUnread.coerceAtLeast(0) + chatRequestsUnread.coerceAtLeast(0))
        // B_010 — Instagram-style watermark. After the Chat tab was opened, keep the badge
        // cleared while the total stays at or below the level seen then, even if some
        // messages/requests are still unread. Ratchet the level DOWN as counts fall (so a
        // later increase re-badges); a total ABOVE the level means genuinely new activity,
        // which clears the ack and shows the badge again.
        if (chatBadgeSeen) {
            if (total <= chatBadgeSeenLevel) {
                chatBadgeSeenLevel = total
                binding.bottomNavigationView.removeBadge(R.id.chat)
                return
            }
            chatBadgeSeen = false
        }
        setNavBadge(R.id.chat, total, R.color.colorAccent)
    }

    // Female Chat tab pushes its freshly-fetched received-request count so the
    // Chat icon badge updates immediately, without waiting for the next onResume fetch.
    fun setChatRequestsCount(count: Int) {
        chatRequestsUnread = count.coerceAtLeast(0)
        updateChatBadge()
    }

    // ---- Male "Friends" hub (favourite tab) — pending friend-request badge ----

    fun refreshFriendsRequestCountBadge() {
        loadFriendsRequestCountBadge()
    }

    // Lets FriendsHubFragment push its freshly-fetched received-request count up.
    fun setFriendsRequestCount(count: Int) {
        friendsRequestsUnread = count.coerceAtLeast(0)
        updateFriendsBadge()
    }

    private fun loadFriendsRequestCountBadge() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        if (userData.gender != DConstants.MALE) return
        // B_010: only requests he hasn't seen — this badge is pure requests, so the
        // watermark is the whole reason it can ever reach zero.
        apiManager.getFriendTabsCounts(
            userData.id,
            object : NetworkCallback<com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse> {
            override fun onResponse(
                call: Call<com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse>,
                response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse>
            ) {
                friendsRequestsUnread = if (response.isSuccessful && response.body()?.success == true) {
                    unseenRequestsOf(response.body()?.data)
                } else {
                    0
                }
                updateFriendsBadge()
            }

            override fun onFailure(call: Call<com.gmwapp.hima.retrofit.responses.FriendTabsCountsResponse>, t: Throwable) {
                friendsRequestsUnread = 0
                updateFriendsBadge()
            }

            override fun onNoNetwork() {
                friendsRequestsUnread = 0
                updateFriendsBadge()
            }
        },
            com.gmwapp.hima.utils.RequestsSeenPrefs.getSeenRequestId(this, userData.id)
        )
    }

    private fun updateFriendsBadge() {
        val count = friendsRequestsUnread.coerceAtLeast(0)
        // B_015 — Instagram-style watermark (mirrors updateChatBadge). After the Friends tab
        // was opened, keep the badge cleared while the count stays at or below the level seen
        // then; ratchet the level down as it falls (so a later request re-badges); a count
        // above the level is a genuinely new request, which clears the ack and shows it.
        if (friendsBadgeSeen) {
            if (count <= friendsBadgeSeenLevel) {
                friendsBadgeSeenLevel = count
                binding.bottomNavigationView.removeBadge(R.id.favourite)
                return
            }
            friendsBadgeSeen = false
        }
        setNavBadge(R.id.favourite, count, R.color.colorAccent)
    }

    fun getSkuListID() {
        BaseApplication.getInstance()?.getPrefs()?.getUserData()
            ?.let { WalletViewModel.getCoins(it.id) }

        WalletViewModel.coinsLiveData.observe(this, Observer {


            if (it != null && it.success && it.data != null) {

                fetchedSkuList.clear() // Clear old SKUs to avoid duplicates
                it.data.forEach { coinItem ->
                    val sku = "${coinItem.id}"
                    fetchedSkuList.add(sku)

                    val preferences = DPreferences(this)
                    preferences.setSkuList(fetchedSkuList)
                }

            }

        })
    }

    fun intializePhonpe(){

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var userId = userData?.id.toString()
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

            override fun onResponse(call: okhttp3.Call, response: Response) {
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

        try {
            PhonePeKt.startCheckoutPage(
                context = this,
                token = token,
                orderId = orderId,
                activityResultLauncher = activityResultLauncherPhonePe
            )
        } catch (e: PhonePeInitException) {
            Log.e("PhonePe", "Checkout Failed: ${e.message}")
            showAppToast("Could not start payment", Toast.LENGTH_SHORT)
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

            override fun onResponse(call: okhttp3.Call, response: Response) {
                val resultStr = response.body?.string()
                val json = JSONObject(resultStr)
                val phonePeStatus = json.getJSONObject("phonepe_status")
                val state = phonePeStatus.getString("state")

                val localRecord = json.getJSONObject("local_record")
                val coin_id = localRecord.getString("coin_id")
                val order_id = localRecord.getString("order_id")
                Log.d("PhonePeOrderStatus", "Order Status: $resultStr")
                Log.d("PhonePeOrderState", "Order State: $state,  Coin_id : $coin_id , Order_id :$order_id ")


                if (state=="COMPLETED"){
                    runOnUiThread{
                        showAppToast("Payment Successful", Toast.LENGTH_LONG)
                        user_id?.let { WalletViewModel.addCoins(it, coin_id, 1, order_id, "Coins purchased") }
                        updatePurchaseOnMeta()
                    }

                }else{
                    runOnUiThread{
                        showAppToast("Payment Failed", Toast.LENGTH_LONG)
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

    /** Single observer — avoids stacking [WalletViewModel.navigateToMain] on every payment poll. */
    private fun setupNavigateToMainAfterPurchaseObserver() {
        WalletViewModel.navigateToMain.observe(
            this,
            Observer { shouldNavigate ->
                Log.d("shouldNavigateFromMain", "$shouldNavigate")
                if (!shouldNavigate) return@Observer
                if (isFinishing || isDestroyed) return@Observer
                if (pendingPurchaseMetaFromMainActivityGpay) {
                    updatePurchaseOnMeta()
                    pendingPurchaseMetaFromMainActivityGpay = false
                }
                val intent = Intent(this, MainActivity::class.java)
                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            })
    }

    private fun isNewUser(createdAt: String?): Boolean {
        if (createdAt.isNullOrEmpty()) return false
        
        try {
            // Parse the created_at timestamp (format: "2025-11-05 12:09:17" or similar)
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val createdDate = dateFormat.parse(createdAt) ?: return false
            val createdCalendar = java.util.Calendar.getInstance().apply { time = createdDate }
            
            // Get today and yesterday dates
            val today = java.util.Calendar.getInstance()
            val yesterday = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            
            // Check if created_at is today (compare only dates, not time)
            val createdDateOnly = createdCalendar.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val todayDateOnly = today.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val yesterdayDateOnly = yesterday.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            return createdDateOnly == todayDateOnly
            
        } catch (e: Exception) {
            Log.e("NewUserCheck", "Error parsing created_at: $createdAt", e)
            return false
        }
    }

    fun updatePurchaseOnMeta(){
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
        } else {
            Log.w("FB_Event", "Skipped PURCHASE event. Invalid coinAmount = $coinAmount")
        }

        val purchaseBundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.CURRENCY, "INR")
            putDouble(FirebaseAnalytics.Param.VALUE, coinAmount)
            putString(FirebaseAnalytics.Param.ITEM_ID, coinId)
            putString("user_id", userID) // optional: useful for debugging

        }

        BaseApplication.firebaseAnalytics.logEvent(FirebaseAnalytics.Event.PURCHASE, purchaseBundle)

        MmpClient.trackPurchase(
            revenueInr = coinAmount,
            productId = coinId,
            customerUserId = userID
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

        // Marketing — Repeat Purchase Day N. Anchors the user's first purchase (IST)
        // on the first call, then fires repeat_purchase_day_{1,2,3,7,14,30} once when a
        // later purchase lands in that window.
        com.gmwapp.hima.utils.HimaAnalytics.logRepeatPurchase(this, coinAmount, userId)

        // Check rating prompt after successful purchase
        userId?.let {
            ratingPromptHelper.forceCheckRatingPrompt(this, it)
        }

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
            
            // Log new_user_first_purchase event - only once per user
            if (shouldLogFirstPurchase(userId)) {
                // Firebase Analytics - new_user_first_purchase
                val firstPurchaseBundle = Bundle().apply {
                    putString(FirebaseAnalytics.Param.CURRENCY, "INR")
                    putDouble(FirebaseAnalytics.Param.VALUE, coinAmount)
                    putString(FirebaseAnalytics.Param.ITEM_ID, coinId)
                    putString("user_id", "$userId")
                    putString("created_at", userData?.created_at ?: "")
                }
                BaseApplication.firebaseAnalytics.logEvent("new_user_first_purchase", firstPurchaseBundle)
                
                // Meta/Facebook Analytics - new_user_first_purchase
                val firstPurchaseParams = Bundle().apply {
                    putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
                    putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, coinAmount)
                    putString("user_id", "$userId")
                    putString("coin_id", "$coinId")
                    putString("created_at", userData?.created_at ?: "")
                }
                AppEventsLogger.newLogger(this).logEvent("new_user_first_purchase", coinAmount, firstPurchaseParams)
                
                // Log to backend (only Firebase events)
                AppEventLogger.logEvent(
                    context = this,
                    eventName = "new_user_first_purchase",
                    platform = "firebase",
                    userId = userId,
                    params = AppEventLogger.bundleToMap(firstPurchaseBundle),
                    value = coinAmount
                )

                // Adjust (mirrors alongside Meta + Firebase + backend).
                com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                    "new_user_first_purchase",
                    revenueInr = coinAmount,
                    params = mapOf("user_id" to "$userId", "coin_id" to "$coinId")
                )

                // Mark first purchase as logged
                markFirstPurchaseLogged(userId)
                
                Log.d("NewUserPurchase", "✅ new_user_first_purchase event logged for user $userId (FIRST PURCHASE)")
            } else {
                Log.d("NewUserPurchase", "⏭️ Skipped new_user_first_purchase - Already logged for user $userId")
            }
        } else {
            Log.d("NewUserPurchase", "⏭️ Skipped new_user_purchase - User not new (created: ${userData?.created_at})")
        }

    }

    /**
     * Check if first purchase event should be logged for this user
     * Returns true only if first purchase hasn't been logged yet
     */
    private fun shouldLogFirstPurchase(userId: Int?): Boolean {
        if (userId == null || userId == 0) return false
        
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val key = "first_purchase_logged_$userId"
        val alreadyLogged = prefs?.getString(key)
        
        return alreadyLogged == null
    }

    /**
     * Mark first purchase as logged for this user
     */
    private fun markFirstPurchaseLogged(userId: Int?) {
        if (userId == null || userId == 0) return
        
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val key = "first_purchase_logged_$userId"
        prefs?.setString(key, "true")
        
        Log.d("NewUserPurchase", "✅ Marked first purchase as logged for user $userId")
    }

    fun logDailyActiveUserIfNeeded() {
        val prefs = BaseApplication.getInstance()?.getPrefs()
        // B-v1110 #6 — java.time.LocalDate is API 26+; on API 24/25 devices
        // (minSdk=24) it threw ClassNotFoundException at startup. SimpleDateFormat
        // is available on every API level and yields the same "yyyy-MM-dd" string,
        // so the day-boundary comparison below is unchanged.
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val lastLoggedDate = prefs?.getString("last_dau_logged_date")
        val bundle = Bundle().apply {
            putString("user_id", "${prefs?.getUserData()?.id}") // optional: useful for debugging
        }

        if (lastLoggedDate != todayDate) {
            FirebaseAnalytics.getInstance(this).logEvent("daily_active_user", bundle)
            prefs?.setString("last_dau_logged_date", todayDate)

            // Log to backend (only Firebase events)
            val userId = prefs?.getUserData()?.id
            AppEventLogger.logEvent(
                context = this,
                eventName = "daily_active_user",
                platform = "firebase",
                userId = userId,
                params = AppEventLogger.bundleToMap(bundle)
            )

            // Marketing — Day 7 Active user (unique). Fires once when the user opens
            // the app on or after Day 7 post-registration. created_at is server (IST)
            // wall-clock "yyyy-MM-dd HH:mm:ss".
            val createdAt = prefs?.getUserData()?.created_at
            val regMs = try {
                if (!createdAt.isNullOrBlank()) {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                    }.parse(createdAt)?.time ?: 0L
                } else 0L
            } catch (e: Exception) { 0L }
            com.gmwapp.hima.utils.HimaAnalytics.logDay7Active(this, userId, regMs)

            if (prefs?.getUserData()?.gender == "male") {
                MmpClient.trackEvent(
                    eventName = "daily_active_user",
                    customerUserId = "${prefs?.getUserData()?.id}"
                )
            }

            // Adjust (mirrors alongside Firebase + backend; all genders).
            com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                "daily_active_user",
                params = mapOf("user_id" to "${prefs?.getUserData()?.id}")
            )
        }

    }

    fun getDiscountedPriceFromTotal(totalAmountStr: String): Int {
        val totalAmount = totalAmountStr.toDoubleOrNull()?.let { Math.round(it).toInt() } ?: 0
        if (totalAmount <= 0) {
            Log.w("PriceCalc", "Invalid totalAmountStr=$totalAmountStr, defaulting discounted price to 0")
            return 0
        }

        for (price in 0..totalAmount) {
            val extra = Math.round(price * 0.02).toInt()
            if (price + extra == totalAmount) {
                return price
            }
        }

        // Some paywall amounts are already final amounts (not price+2% shaped).
        // Return totalAmount as a safe fallback to avoid checkout crash.
        Log.w("PriceCalc", "Could not reverse-calc 2% price for total=$totalAmount. Using fallback.")
        return totalAmount
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
                .doPayment(this@MainActivity, cfWebCheckoutPayment)

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

            override fun onResponse(call: okhttp3.Call, response: Response) {
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

            override fun onResponse(call: okhttp3.Call, response: Response) {
                val resultStr = response.body?.string()
                Log.d("CashfreeOrderStatus", "$resultStr")

                try {
                    val json = JSONObject(resultStr)

                    // Check if payment was completed (you may need to adapt based on backend's status field)
                    val paymentStatus = json.optString("order_status", "UNKNOWN")
                    val coin_id = json.optString("coin_id", "")
                    val order_id = json.optString("order_id", "")

                    Log.d("cashfreePaymentStatus", "Status: $paymentStatus, Coin ID: $coin_id, Order ID: $order_id")

                    if (paymentStatus.equals("PAID", ignoreCase = true)) {
                        runOnUiThread {
                            showAppToast("Payment Successful", Toast.LENGTH_LONG)
                            user_id?.let { WalletViewModel.add_coins_cashfree(it, coin_id, 1, order_id, "Coins purchased") }
                            updatePurchaseOnMeta()
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

    fun checkIndividualPaymentType(){
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.let { loginViewModel.login(it.mobile,"0","0") }
        loginViewModel.loginResponseLiveData.observe(this, Observer {
            // ✅ Add null check before accessing properties
            if (it == null) {
                Log.w("MainActivity", "LoginResponse is null")
                return@Observer
            }

            if (it.success) {
                if (!it.data?.payment_type.isNullOrEmpty()){
                    paymentGateway = it.data?.payment_type.toString()
                }
            }
        })
    }

    private fun callFreeCoinsStatusApi(userId: Int) {
        apiManager.getFreeCoinsStatus(userId, object : NetworkCallback<FreeCoinsStatusResponse> {
            override fun onResponse(
                call: retrofit2.Call<FreeCoinsStatusResponse>,
                response: retrofit2.Response<FreeCoinsStatusResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val freeCoinsStatus = response.body()!!
                    Log.d("FreeCoinsStatus", "Success: ${freeCoinsStatus.success}, Enabled: ${freeCoinsStatus.enabled}, Value: ${freeCoinsStatus.value}")
                    Log.d("FreeCoinsStatus", "Success: ${freeCoinsStatus}, Enabled: ${freeCoinsStatus.enabled}, Value: ${freeCoinsStatus.value}")
                    // Show dialog if enabled is true
                    if (freeCoinsStatus.enabled) {
                        val coinsValue = freeCoinsStatus.value ?: 0
                        val makePayment = freeCoinsStatus.makePayment
                        val coinId = freeCoinsStatus.coin_id
                        val price = freeCoinsStatus.price
                        val badgeText = freeCoinsStatus.badge_text
                        val title = freeCoinsStatus.title
                        val subtitle = freeCoinsStatus.subtitle
                        val description = freeCoinsStatus.description
                        val buttonText = freeCoinsStatus.button_text
                        
                        runOnUiThread {
                            showFreeCoinsWelcomeDialog(
                                coinsValue,
                                makePayment,
                                coinId,
                                price,
                                badgeText,
                                title,
                                subtitle,
                                description,
                                buttonText
                            )
                        }
                    }
                } else {
                    Log.e("FreeCoinsStatus", "API call failed: ${response.code()}")
                }
            }

            override fun onFailure(
                call: retrofit2.Call<FreeCoinsStatusResponse>,
                t: Throwable
            ) {
                Log.e("FreeCoinsStatus", "API call error: ${t.message}")
            }

            override fun onNoNetwork() {
                Log.w("FreeCoinsStatus", "No network connection")
            }
        })
    }

    private fun showFreeCoinsWelcomeDialog(
        coinsValue: Int,
        makePayment: Int?,
        coinId: Int?,
        price: Int?,
        badgeText: String?,
        title: String?,
        subtitle: String?,
        description: String?,
        buttonText: String?
    ) {
        val existing = supportFragmentManager.findFragmentByTag("FreeCoinsWelcomeDialog")
        if (existing == null) {
            val dialog = FreeCoinsWelcomeDialog.newInstance(
                coinsValue,
                makePayment,
                coinId,
                price,
                badgeText,
                title,
                subtitle,
                description,
                buttonText
            )
            dialog.setOnCoinsClaimedListener(object : FreeCoinsWelcomeDialog.OnCoinsClaimedListener {
                override fun onCoinsClaimed(coinsAdded: Int) {
                    // Refresh coins balance in MainActivity
                    refreshCoinsBalance()
                }
            })
            dialog.setOnBuyCoinsListener(object : FreeCoinsWelcomeDialog.OnBuyCoinsListener {
                override fun onBuyCoins(totalAmount: String, coinId: Int) {
                    onAddCoins(totalAmount, coinId)
                }
            })
            
            // Set dismiss listener to call API again when dialog is dismissed
            dialog.setOnDismissListener(object : FreeCoinsWelcomeDialog.OnDismissListener {
                override fun onDialogDismissed() {
                    Log.d("FreeCoinsDialog", "✅ onDialogDismissed callback triggered!")
                    Log.d("FreeCoinsDialog", "Dialog dismissed - calling API again to check")
                    
                    // Call API again after dismissal
                    val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                    if (userData == null) {
                        Log.e("FreeCoinsDialog", "❌ userData is null, cannot call API")
                        return
                    }

                }
            })
            
            Log.d("FreeCoinsDialog", "Dismiss listener set successfully")
            
            dialog.show(supportFragmentManager, "FreeCoinsWelcomeDialog")
        }
    }

    private fun refreshCoinsBalance() {
        // Refresh coins in HomeFragment
        val currentFragment = supportFragmentManager.findFragmentById(R.id.flFragment)
        if (currentFragment is HomeFragment) {
            currentFragment.refreshCoinsBalance()
            Log.d("MainActivity", "Refreshing coins balance in HomeFragment")
        } else {
            Log.d("MainActivity", "HomeFragment not currently visible, skipping refresh")
        }
    }

    override fun onNetworkRetry() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        profileViewModel.getUsers(userData.id)
        WalletViewModel.getCoins(userData.id)
        when (val f = supportFragmentManager.findFragmentById(R.id.flFragment)) {
            is NetworkRetryable -> f.onNetworkRetry()
            else -> { }
        }
    }

    private fun callUserInstallReferrerApi(userId: Int, responseData: String) {
        apiManager.logUserInstallReferrer(userId, responseData, object : NetworkCallback<InstallReferrerResponse> {
            override fun onResponse(
                call: retrofit2.Call<InstallReferrerResponse>,
                response: retrofit2.Response<InstallReferrerResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val apiResponse = response.body()!!
                    if (apiResponse.success) {
                        Log.d("UserInstallReferrer", "✅ User install referrer logged successfully: ${apiResponse.message}")
                        // Clear saved data after successful API call
                        BaseApplication.getInstance()?.getPrefs()?.setString("install_referrer_response_data", "")
                    } else {
                        Log.e("UserInstallReferrer", "❌ API returned success=false: ${apiResponse.message}")
                    }
                } else {
                    Log.e("UserInstallReferrer", "❌ API call failed: ${response.code()}")
                }
            }

            override fun onFailure(
                call: retrofit2.Call<InstallReferrerResponse>,
                t: Throwable
            ) {
                Log.e("UserInstallReferrer", "❌ API call error: ${t.message}", t)
            }

            override fun onNoNetwork() {
                Log.w("UserInstallReferrer", "⚠️ No network connection")
            }
        })
    }

    private fun callTrackingInfoApi(userId: Int, savedAddress: String) {
        apiManager.trackingInfo(savedAddress, userId, object : NetworkCallback<TrackingInfoResponse> {
            override fun onResponse(
                call: retrofit2.Call<TrackingInfoResponse>,
                response: retrofit2.Response<TrackingInfoResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val apiResponse = response.body()!!
                    Log.d("TrackingInfo", "Tracking info API response: ${apiResponse.message}")
                } else {
                    Log.e("TrackingInfo", "Tracking info API failed: ${response.code()}")
                }
            }

            override fun onFailure(call: retrofit2.Call<TrackingInfoResponse>, t: Throwable) {
                Log.e("TrackingInfo", "Tracking info API error: ${t.message}", t)
            }

            override fun onNoNetwork() {
                Log.w("TrackingInfo", "No network for tracking_info API")
            }
        })
    }

    private fun callPaywallVideoContentApi(userId: Int) {
        apiManager.getPaywallVideoContent(userId, object : NetworkCallback<PaywallVideoContentResponse> {
            override fun onResponse(
                call: retrofit2.Call<PaywallVideoContentResponse>,
                response: retrofit2.Response<PaywallVideoContentResponse>
            ) {
                val prefs = BaseApplication.getInstance()?.getPrefs()
                val body = response.body()

                if (body != null) {
                    prefs?.setString(paywallVideoContentPrefsKey, Gson().toJson(body))
                    Log.d("PaywallVideoContent", "Saved latest response: ${body.message}")
                } else {
                    val fallback = PaywallVideoContentResponse(
                        success = false,
                        message = "HTTP ${response.code()}: ${response.message()}"
                    )
                    prefs?.setString(paywallVideoContentPrefsKey, Gson().toJson(fallback))
                    Log.e("PaywallVideoContent", "Empty response body. Saved fallback for code ${response.code()}")
                }
            }

            override fun onFailure(call: retrofit2.Call<PaywallVideoContentResponse>, t: Throwable) {
                Log.e("PaywallVideoContent", "API call failed: ${t.message}", t)
            }

            override fun onNoNetwork() {
                Log.w("PaywallVideoContent", "No network for paywall_video_content API")
            }
        })
    }

    private fun handleInsufficientCoinPaywallIntent() {
        if (!intent.getBooleanExtra(showPaywallInsufficientIntentKey, false)) return

        intent.removeExtra(showPaywallInsufficientIntentKey)
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val cachedJson = prefs?.getString(paywallVideoContentPrefsKey).orEmpty()
        if (cachedJson.isBlank()) return

        try {
            val cachedResponse = Gson().fromJson(cachedJson, PaywallVideoContentResponse::class.java)
            val data = cachedResponse?.data ?: return

            if (data.coin_id == null || data.coin_amount == null || data.coin_amount <= 0) return
            val coinValueForButton = data.coin_value ?: data.coin ?: data.coin_amount

            val existing = supportFragmentManager.findFragmentByTag("InsufficientCoinsPaywall")
            if (existing != null) return

            BottomSheetInsufficientCoinsPaywall.newInstance(
                titleText = data.text_one,
                subtitleText = data.text_three,
                youtubeVideoLink = data.youtube_video_link,
                coinId = data.coin_id,
                coinAmount = data.coin_amount,
                coinValue = coinValueForButton
            ).show(supportFragmentManager, "InsufficientCoinsPaywall")
        } catch (e: Exception) {
            Log.e("PaywallVideoContent", "Failed to parse cached paywall response", e)
        }
    }

    private fun checkRatingEligibility(userId: Int) {
        apiManager.checkRatingEligibility(userId, object : NetworkCallback<com.gmwapp.hima.retrofit.responses.CheckRatingEligibilityResponse> {
            override fun onResponse(
                call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.CheckRatingEligibilityResponse>,
                response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.CheckRatingEligibilityResponse>
            ) {
                // Log complete request details
                Log.d("CheckRatingEligibility", "═══════════════════════════════════════")
                Log.d("CheckRatingEligibility", "📡 REQUEST URL: ${call.request().url}")
                Log.d("CheckRatingEligibility", "📤 REQUEST METHOD: ${call.request().method}")
                Log.d("CheckRatingEligibility", "👤 USER ID: $userId")
                Log.d("CheckRatingEligibility", "───────────────────────────────────────")
                Log.d("CheckRatingEligibility", "📥 RESPONSE CODE: ${response.code()}")
                Log.d("CheckRatingEligibility", "📊 RESPONSE MESSAGE: ${response.message()}")
                
                if (response.isSuccessful && response.body() != null) {
                    val apiResponse = response.body()!!
                    Log.d("CheckRatingEligibility", "✅ SUCCESS BODY:")
                    Log.d("CheckRatingEligibility", "   success: ${apiResponse.success}")
                    Log.d("CheckRatingEligibility", "   eligible: ${apiResponse.eligible}")
                    Log.d("CheckRatingEligibility", "   message: ${apiResponse.message}")
                    
                    if (apiResponse.success && apiResponse.eligible) {
                        // User is eligible - show beautiful rating dialog
                        Log.d("CheckRatingEligibility", "🎯 Showing rating dialog")
                        showRatingDialog(userId)
                    } else {
                        // User is not eligible - don't show toast, just log
                        Log.d("CheckRatingEligibility", "⛔ User is NOT eligible")
                    }
                } else {
                    // API call failed - log complete error response
                    Log.e("CheckRatingEligibility", "❌ API FAILED:")
                    try {
                        val errorBody = response.errorBody()?.string()
                        Log.e("CheckRatingEligibility", "   ERROR BODY: $errorBody")
                    } catch (e: Exception) {
                        Log.e("CheckRatingEligibility", "   Could not read error body: ${e.message}")
                    }
                }
                Log.d("CheckRatingEligibility", "═══════════════════════════════════════")
            }

            override fun onFailure(
                call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.CheckRatingEligibilityResponse>,
                t: Throwable
            ) {
                // Don't show toast for errors as per requirement
                Log.e("CheckRatingEligibility", "═══════════════════════════════════════")
                Log.e("CheckRatingEligibility", "💥 NETWORK FAILURE:")
                Log.e("CheckRatingEligibility", "📡 REQUEST URL: ${call.request().url}")
                Log.e("CheckRatingEligibility", "❌ ERROR: ${t.message}")
                Log.e("CheckRatingEligibility", "📚 STACK TRACE:", t)
                Log.e("CheckRatingEligibility", "═══════════════════════════════════════")
            }

            override fun onNoNetwork() {
                // Don't show toast for errors as per requirement
                Log.w("CheckRatingEligibility", "═══════════════════════════════════════")
                Log.w("CheckRatingEligibility", "⚠️ NO NETWORK CONNECTION")
                Log.w("CheckRatingEligibility", "═══════════════════════════════════════")
            }
        })
    }

    private fun showRatingDialog(userId: Int) {
        val ratingDialog = RatingDialog(this, userId, apiManager)
        ratingDialog.setOnRatingSubmittedListener(object : RatingDialog.OnRatingSubmittedListener {
            override fun onRatingSubmitted(starCount: Int) {
                Log.d("MainActivity", "✅ Rating submitted with $starCount stars")
                // You can add any additional logic here after rating is submitted
            }
        })
        ratingDialog.show()
    }

}

// I am MainActivity

//new branch track creator first call
