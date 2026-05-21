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
import com.appsflyer.AppsFlyerLib
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val recentMissedDotTag = "recent_missed_dot"
    private var chatFriendsUnread: Int = 0
    private var chatGeneralUnread: Int = 0
    private val chatUnreadDotTag = "chat_unread_dot"
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
            insets
        }
        
        // Double-check visibility after layout
        binding.bottomNavigationView.post {
            binding.bottomNavigationView.visibility = View.VISIBLE
            binding.bottomNavigationView.bringToFront()
            // Ensure status bar stays pink
            window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        }
        BaseApplication.getInstance()?.messageCameWhenIsAlive = 1

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

        val notifPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val lastAskedTime = notifPrefs.getLong("notif_permission_last_asked", 0L)
        val oneDayMillis = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastAskedTime >= oneDayMillis) {
            notifPrefs.edit().putLong("notif_permission_last_asked", System.currentTimeMillis()).apply()
            CoroutineScope(Dispatchers.IO).launch {
                OneSignal.Notifications.requestPermission(false)
            }
        }

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
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
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
            // Permission granted, notifications will work
        } else {
            maybeShowNotificationImportance()
        }
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
        
        // Favourite is shown to both genders. Chat tab stays creator-only.
        // Female nav: Home, Chat, Recent, Favourite, Profile (5 items).
        // Male nav:   Home, Recent, Favourite, Profile (4 items; Chat hidden).
        val userGender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
        binding.bottomNavigationView.menu.findItem(R.id.favourite)?.isVisible = true
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
            else -> {
                android.util.Log.w("MainActivity", "Unknown OPEN_TAB extra: $tab")
                return
            }
        }
        if (binding.bottomNavigationView.menu.findItem(targetItem) == null) {
            // The Recent menu item is hidden for gender-restricted users in
            // some flows — bail rather than crash.
            android.util.Log.w("MainActivity", "OPEN_TAB target $tab not in current bottom nav")
            return
        }
        if (binding.bottomNavigationView.selectedItemId != targetItem) {
            binding.bottomNavigationView.selectedItemId = targetItem
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
            R.id.favourite -> current is FavouriteFragment
            R.id.profile -> current is ProfileFragment || current is ProfileFemaleFragment
            else -> false
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        supportFragmentManager.executePendingTransactions()
        if (isAlreadyShowingTab(item.itemId)) {
            // User re-tapped the tab they're already on — refresh its data.
            val current = supportFragmentManager.findFragmentById(R.id.flFragment)
            (current as? Refreshable)?.refresh()
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

                transaction.replace(R.id.flFragment, FavouriteFragment()).commit()
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

        val checkoutAmount = amount.toDoubleOrNull() ?: 0.0
        if (checkoutAmount > 0.0) {
            val checkoutParams = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
                putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, checkoutAmount)
                putString("user_id", "$userId")
                putString("coin_id", "$pointsId")
            }

            AppEventsLogger.newLogger(this).logEvent(
                AppEventsConstants.EVENT_NAME_INITIATED_CHECKOUT,
                checkoutAmount,
                checkoutParams
            )
        } else {
            Log.w("FB_Event", "Skipped INITIATED_CHECKOUT event. Invalid amount = $checkoutAmount")
        }


        var af_price =  getDiscountedPriceFromTotal(total_amount)
        val checkoutEvent = HashMap<String, Any>()
        checkoutEvent["af_price"] = "$af_price"          // Cart total
        checkoutEvent["af_currency"] = "INR"

        AppsFlyerLib.getInstance().logEvent(
            this,
            "af_initiated_checkout",
            checkoutEvent
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

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(listener)
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
                // belongs in `general`.
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
    }

    fun refreshRecentMissedCountBadge() {
        loadRecentMissedCountBadge()
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

    private fun updateRecentBadge() {
        val displayCount = recentMissedCount.coerceAtLeast(0)

        // Always remove native Material badge — we use a custom overlay for pixel-perfect placement.
        binding.bottomNavigationView.removeBadge(R.id.recent)

        if (displayCount > 0) {
            placeRecentBadge(displayCount, R.color.chat_recording_red)
        } else {
            hideBadge(recentMissedDotTag)
        }
    }

    private fun getRecentBottomNavItemView(): BottomNavigationItemView? {
        val menuView = binding.bottomNavigationView.getChildAt(0) as? BottomNavigationMenuView ?: return null
        for (i in 0 until menuView.childCount) {
            val item = menuView.getChildAt(i)
            if (item is BottomNavigationItemView && item.itemData?.itemId == R.id.recent) {
                return item
            }
        }
        return null
    }

    private fun makeBadgeDot(tag: String): TextView {
        val dp = resources.displayMetrics.density
        val dotSize = (18 * dp).toInt()
        val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        return TextView(this).apply {
            this.tag = tag
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
            textSize = 9f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            // Start hidden — visibility flips on only after the post-runnable
            // positions the dot. Otherwise it briefly flashes at (0,0) of the
            // content root, which sits over the Home tab area.
            visibility = View.INVISIBLE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@MainActivity, R.color.colorAccent))
            }
            rootView.addView(this, FrameLayout.LayoutParams(dotSize, dotSize))
        }
    }

    // Single badge on Recent tab — top-right of icon; reuses recentMissedDotTag TextView.
    private fun placeRecentBadge(count: Int, @androidx.annotation.ColorRes badgeColorRes: Int = R.color.colorAccent) {
        val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val dp = resources.displayMetrics.density
        val dotSize = (18 * dp).toInt()

        val dot = rootView.findViewWithTag<TextView>(recentMissedDotTag)
            ?: makeBadgeDot(recentMissedDotTag)
        dot.text = count.coerceAtMost(99).toString()
        (dot.background as? GradientDrawable)?.setColor(
            ContextCompat.getColor(this, badgeColorRes)
        )

        binding.bottomNavigationView.post {
            val itemView = getRecentBottomNavItemView()
            val iconView = itemView?.findViewById<View>(
                com.google.android.material.R.id.navigation_bar_item_icon_view
            )
            if (iconView == null) {
                // Tab not on screen — keep the dot hidden so it doesn't sit at (0,0).
                dot.visibility = View.GONE
                return@post
            }

            val iconPos = IntArray(2)
            iconView.getLocationInWindow(iconPos)
            val rootPos = IntArray(2)
            rootView.getLocationInWindow(rootPos)

            val iconLeft = iconPos[0] - rootPos[0]
            val iconRight = iconLeft + iconView.width
            val iconTop = iconPos[1] - rootPos[1]
            val topMargin = iconTop - dotSize / 2

            (dot.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.leftMargin = iconRight - dotSize / 2
                it.topMargin = topMargin
                dot.layoutParams = it
            }
            // Now that the dot is correctly positioned, reveal it.
            dot.visibility = View.VISIBLE
        }
    }

    private fun hideBadge(tag: String) {
        val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        rootView.findViewWithTag<TextView>(tag)?.visibility = View.GONE
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

    private fun loadChatUnreadCountBadge() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        if (userData.gender != DConstants.FEMALE) return

        apiManager.getMyChatFriends(userData.id, null, 100, 0, object : NetworkCallback<com.gmwapp.hima.retrofit.responses.MyChatResponse> {
            override fun onResponse(
                call: Call<com.gmwapp.hima.retrofit.responses.MyChatResponse>,
                response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.MyChatResponse>
            ) {
                chatFriendsUnread = if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.data?.chats?.sumOf { it.unreadCount } ?: 0
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

        apiManager.getMyChatGeneral(userData.id, null, 100, 0, object : NetworkCallback<com.gmwapp.hima.retrofit.responses.MyChatResponse> {
            override fun onResponse(
                call: Call<com.gmwapp.hima.retrofit.responses.MyChatResponse>,
                response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.MyChatResponse>
            ) {
                chatGeneralUnread = if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.data?.chats?.sumOf { it.unreadCount } ?: 0
                } else {
                    0
                }
                updateChatBadge()
            }

            override fun onFailure(call: Call<com.gmwapp.hima.retrofit.responses.MyChatResponse>, t: Throwable) {
                chatGeneralUnread = 0
                updateChatBadge()
            }

            override fun onNoNetwork() {
                chatGeneralUnread = 0
                updateChatBadge()
            }
        })
    }

    private fun updateChatBadge() {
        val total = (chatFriendsUnread.coerceAtLeast(0) + chatGeneralUnread.coerceAtLeast(0))

        binding.bottomNavigationView.removeBadge(R.id.chat)

        if (total > 0) {
            placeChatBadge(total)
        } else {
            hideBadge(chatUnreadDotTag)
        }
    }

    private fun getChatBottomNavItemView(): BottomNavigationItemView? {
        val menuView = binding.bottomNavigationView.getChildAt(0) as? BottomNavigationMenuView ?: return null
        for (i in 0 until menuView.childCount) {
            val item = menuView.getChildAt(i)
            if (item is BottomNavigationItemView && item.itemData?.itemId == R.id.chat) {
                return item
            }
        }
        return null
    }

    private fun placeChatBadge(count: Int) {
        val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val dp = resources.displayMetrics.density
        val dotSize = (18 * dp).toInt()

        val dot = rootView.findViewWithTag<TextView>(chatUnreadDotTag)
            ?: makeBadgeDot(chatUnreadDotTag)
        dot.text = count.coerceAtMost(99).toString()

        binding.bottomNavigationView.post {
            val itemView = getChatBottomNavItemView()
            val iconView = itemView?.findViewById<View>(
                com.google.android.material.R.id.navigation_bar_item_icon_view
            )
            if (iconView == null) {
                // Chat tab is hidden (e.g. male user) — don't leave a stray dot at (0,0).
                dot.visibility = View.GONE
                return@post
            }

            val iconPos = IntArray(2)
            iconView.getLocationInWindow(iconPos)
            val rootPos = IntArray(2)
            rootView.getLocationInWindow(rootPos)

            val iconLeft = iconPos[0] - rootPos[0]
            val iconRight = iconLeft + iconView.width
            val iconTop = iconPos[1] - rootPos[1]
            val topMargin = iconTop - dotSize / 2

            (dot.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.leftMargin = iconRight - dotSize / 2
                it.topMargin = topMargin
                dot.layoutParams = it
            }
            dot.visibility = View.VISIBLE
        }
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

        val purchaseEvent = HashMap<String, Any>()
        purchaseEvent["af_revenue"] = coinAmount       // Total amount (decimal preferred)
        purchaseEvent["af_currency"] = "INR"        // 3-letter code, e.g. "INR", "USD"
        purchaseEvent["af_coin_id"] = "$coinId"

        AppsFlyerLib.getInstance().logEvent(
            this,
            "af_purchase",
            purchaseEvent
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
        val todayDate = java.time.LocalDate.now().toString()
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

            if (prefs?.getUserData()?.gender=="male"){

            val eventValues = HashMap<String, Any>()
            eventValues["user_id"] = "${prefs?.getUserData()?.id}"
            AppsFlyerLib.getInstance().logEvent(
                this,
                "daily_active_user",
                eventValues
            )

        }
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
