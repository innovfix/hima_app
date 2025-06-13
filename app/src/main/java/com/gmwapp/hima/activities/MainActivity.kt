package com.gmwapp.hima.activities

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
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BillingManager.BillingManager
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.CoinAdapter
import com.gmwapp.hima.adapters.GiftAdapter
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityMainBinding
import com.gmwapp.hima.dialogs.BottomSheetWelcomeBonus
import com.gmwapp.hima.fragments.FemaleHomeFragment
import com.gmwapp.hima.fragments.HomeFragment
import com.gmwapp.hima.fragments.ProfileFemaleFragment
import com.gmwapp.hima.fragments.ProfileFragment
import com.gmwapp.hima.fragments.RecentFragment
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.retrofit.responses.NewRazorpayLinkResponse
import com.gmwapp.hima.retrofit.responses.RazorPayApiResponse
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.FcmTokenViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.OfferViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UpiPaymentViewModel
import com.gmwapp.hima.viewmodels.WalletViewModel
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
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.OneSignal
import com.phonepe.intent.sdk.api.PhonePeInitException
import com.phonepe.intent.sdk.api.PhonePeKt
import com.phonepe.intent.sdk.api.models.PhonePeEnvironment
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
    BottomSheetWelcomeBonus.OnAddCoinsListener {
    lateinit var binding: ActivityMainBinding
    var isBackPressedAlready = false
    var userName: String? = null
    var userID: String? = null
    var currentVersion = ""

    val appUpdateViewModel: LoginViewModel by viewModels()
    val offerViewModel: OfferViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val fcmTokenViewModel: FcmTokenViewModel by viewModels()
    private val upiPaymentViewModel: UpiPaymentViewModel by viewModels()
    private var billingManager: BillingManager? = null
    private val WalletViewModel: WalletViewModel by viewModels()
    private val fetchedSkuList: MutableList<String> = mutableListOf()


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

    private lateinit var activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var appUpdateManager: AppUpdateManager



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
            // Toast.makeText(this, "Payment Successful", Toast.LENGTH_LONG).show()
        } else {
            //  Toast.makeText(this, "Payment Failed or Cancelled", Toast.LENGTH_LONG).show()
        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        AppEventsLogger.newLogger(this).logEvent("TestEventFromApp")



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        appUpdateViewModel.appUpdate()


        appUpdateViewModel.appUpdateResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {

                val latestVersion = it.data[0].app_version.toString()
                checkForInAppUpdate(latestVersion)

            }
        })








        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
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
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.press_back_again_to_exit),
                    Toast.LENGTH_SHORT
                ).show()
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
        if (isGranted) {
            // Permission granted, notifications will work
        } else {
            // Permission denied, notify the user
        }
    }



    private fun initUI() {

        upiPaymentViewModel.upiPaymentLiveData.observe(this, Observer { response ->
            if (response != null && response.status) {
                val paymentUrl = response.data.firstOrNull()?.payment_url

                if (!paymentUrl.isNullOrEmpty()) {
                    Log.d("UPI Payment", "Payment URL: $paymentUrl")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
                    startActivity(intent)
                } else {
                    Log.e("UPI Payment Error", "Payment URL is null or empty")
                    Toast.makeText(this, "Payment URL not found. Please try again later.", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("UPI Payment Error", "Invalid response: ${response?.data}")
                Toast.makeText(this, "Payment failed. Please check your internet or payment details.", Toast.LENGTH_LONG).show()
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
                            Toast.makeText(this, "Please try again later", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })


        val prefs = BaseApplication.getInstance()?.getPrefs()
        prefs?.getUserData()?.id?.let { profileViewModel.getUsers(it) }

        profileViewModel.getUserLiveData.observe(this, Observer { response ->
            response?.data?.let { userData ->
                prefs?.setUserData(userData)
            } ?: run {
                Log.e("Observer", "RegisterResponse is null")
            }
        })


        Log.d("DEBUG", "Received userID: $userID")

        userID?.toIntOrNull()?.let { offerViewModel.getOffer(it) }
        binding.bottomNavigationView.setOnNavigationItemSelectedListener(this)
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
            if (response.success) {
                val coin = response.data[0].coins
                val discountedPrice = response.data[0].price
                val save = response.data[0].save
                val coinId = response.data[0].id
                val total_count = response.data[0].total_count
                paymentGateway = response.data[0].pg


                val originalPrice = calculateOriginalPrice(discountedPrice, save)


               // Log.d("offerRechargeTotalCount","totalcount ${response.data[0].total_count}")

                Log.d("OrinalPrice","OriginalPrice $originalPrice")
                Log.d("OrinalPrice","discountPrice $discountedPrice")
                Log.d("OrinalPrice","savePercent $save")
                val isBlockWord = intent.getBooleanExtra("blockword", false)

                if (!isBlockWord && BaseApplication.getInstance()?.getPrefs()
                        ?.getUserData()?.gender == DConstants.MALE
                ) {
                    val bottomSheet = BottomSheetWelcomeBonus(coin, originalPrice, discountedPrice,coinId,total_count)
                    bottomSheet.show(supportFragmentManager, "BottomSheetWelcomeBonus")
                }
            }
        }
        binding.bottomNavigationView.setOnNavigationItemSelectedListener(this)
        binding.bottomNavigationView.selectedItemId = R.id.home
        removeShiftMode()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                val homeFragment = if (BaseApplication.getInstance()?.getPrefs()
                        ?.getUserData()?.gender == DConstants.FEMALE
                ) FemaleHomeFragment() else HomeFragment()
                supportFragmentManager.beginTransaction().replace(R.id.flFragment, homeFragment)
                    .commit()
                return true
            }

            R.id.recent -> {
                supportFragmentManager.beginTransaction().replace(R.id.flFragment, RecentFragment())
                    .commit()
                return true
            }

            R.id.profile -> {
                if (BaseApplication.getInstance()?.getPrefs()
                        ?.getUserData()?.gender == DConstants.MALE
                ) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.flFragment, ProfileFragment()).commit()
                } else {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.flFragment, ProfileFemaleFragment()).commit()
                }
                return true
            }
        }
        return false
    }

    @SuppressLint("RestrictedApi")
    fun removeShiftMode() {
        binding.bottomNavigationView.labelVisibilityMode =
            NavigationBarView.LABEL_VISIBILITY_LABELED
        val menuView = binding.bottomNavigationView.getChildAt(0) as BottomNavigationMenuView
        for (i in 0 until menuView.childCount) {
            val item = menuView.getChildAt(i) as BottomNavigationItemView
            item.setShifting(false)
            item.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED)

            // set once again checked value, so view will be updated
            item.setChecked(item.itemData!!.isChecked)
        }
    }




    override fun onAddCoins(amount: String, id: Int) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id
        var pointsId = "$id"
        val pointsIdInt = pointsId.toIntOrNull()
        total_amount = "$amount"

        if (userId != null && pointsId.isNotEmpty()) {
            if (pointsIdInt != null) {

                if (paymentGateway.isNotEmpty()) {

                    when (paymentGateway) {

                        "phonepe"->{

                            if (isPhonePeInitialized){
                                fetchOrderFromBackend(pointsId)
                            }
                        }

                        "gpay" -> {

                            val random4Digit = (1000..9999).random()

                            // ✅ Save userId and pointsIdInt BEFORE launching billing
                            val preferences = DPreferences(this)
                            preferences.clearSelectedOrderId()
                            preferences.setSelectedUserId(userId.toString())
                            preferences.setSelectedPlanId(java.lang.String.valueOf(pointsIdInt))
                            preferences.setSelectedOrderId(java.lang.String.valueOf(random4Digit))
                            WalletViewModel.tryCoins(userId, pointsIdInt, 0, random4Digit, "try")
                            billingManager!!.purchaseProduct(
                              //  "coin_14",
                               pointsId,
                            )
                            WalletViewModel.navigateToMain.observe(
                                this,
                                Observer { shouldNavigate ->
                                    Log.d("shouldNavigateFromMain","$shouldNavigate")
                                    if (shouldNavigate){
                                    val intent = Intent(this, MainActivity::class.java)
                                    intent.flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish() // ✅ Now this works because we are in an Activity
                                }})

                        }

                        "razorpay" -> {

                            callNewRazorPay = apiService.callNewRazorPay(userId,pointsId)


                            callNewRazorPay.enqueue(object : retrofit2.Callback<NewRazorpayLinkResponse> {
                                override fun onResponse(call: retrofit2.Call<NewRazorpayLinkResponse>, response: retrofit2.Response<NewRazorpayLinkResponse>) {
                                    if (response.isSuccessful && response.body() != null) {
                                        val apiResponse = response.body()

                                        // Extract the Razorpay payment link
                                        val paymentUrl = apiResponse?.data?.short_url

                                        Log.d("paymentUrlRazorPay","$paymentUrl")

                                        if (!paymentUrl.isNullOrEmpty()) {

                                            val intent =Intent(this@MainActivity, LauncherActivity::class.java)
                                            intent.setData(Uri.parse(paymentUrl))
                                            Log.d("paymentUrlRazorPay","$paymentUrl")
                                            startActivity(intent)

//                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
//                                startActivity(intent)
                                        } else {
                                            Toast.makeText(this@MainActivity, "Failed to get payment link", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(this@MainActivity, "Error: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                override fun onFailure(call: retrofit2.Call<NewRazorpayLinkResponse>, t: Throwable) {
                                    Toast.makeText(this@MainActivity, "Failed: ${t.message}", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }



                        "upigateway" -> {

                            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                            var userid = userData?.id
                            userid?.let {
                                val clientTxnId = generateRandomTxnId(
                                    it,
                                    id.toString()
                                )  // Generate a new transaction ID
                                upiPaymentViewModel.createUpiPayment(it, clientTxnId, total_amount)
                            }

                        }


                        else -> {
                            Toast.makeText(this, "Invalid Payment Gateway", Toast.LENGTH_SHORT)
                                .show()
                        }


                    }
                }
            }
        } else {
            Toast.makeText(this, "Invalid input data", Toast.LENGTH_SHORT).show()
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
////                        //  Toast.makeText(this@WalletActivity, response.body()?.message ?: "Error", Toast.LENGTH_SHORT).show()
////                    }
////                }
////
////                override fun onFailure(call: retrofit2.Call<ApiResponse>, t: Throwable) {
////                    Toast.makeText(this@MainActivity, "Failed: ${t.message}", Toast.LENGTH_SHORT)
////                        .show()
////                }
////            })
//        } else {
//            Toast.makeText(this, "Invalid input data", Toast.LENGTH_SHORT).show()
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

            userId?.let { fcmTokenViewModel.sendToken(it, token) }
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


    private fun checkForInAppUpdate(latestVersion:String){

        if (latestVersion>currentVersion) {

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
            Toast.makeText(this, "PhonePe SDK init failed", Toast.LENGTH_SHORT).show()
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
            .url("https://himaapp.in/api/phonepe/live/create-order") // Should return { token, orderId }
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "API Failure: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@MainActivity, "Invalid server response", Toast.LENGTH_SHORT).show()
                        Log.d("PhonpeException","$e")
                    }
                }
            }
        })
    }

    private fun startPhonePeCheckout(orderId: String, token: String) {
        if (!isAnyUPIAppInstalled()) {
            Toast.makeText(this, "No UPI app installed", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Could not start payment", Toast.LENGTH_SHORT).show()
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
            .url("https://himaapp.in/api/phonepe/live/check-status")
            .post(body) // ✅ Correct method
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Status check failed", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@MainActivity, "Payment Successful", Toast.LENGTH_LONG).show()
                        user_id?.let { WalletViewModel.addCoins(it, coin_id, 1, order_id, "Coins purchased") }
                        observeAddCoins()
                    }

                }else{
                    runOnUiThread{
                        Toast.makeText(this@MainActivity, "Payment Failed", Toast.LENGTH_LONG).show()
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

    fun observeAddCoins(){

        WalletViewModel.navigateToMain.observe(
            this,
            Observer { shouldNavigate ->
                Log.d("shouldNavigateFromMain","$shouldNavigate")
                if (shouldNavigate){
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish() // ✅ Now this works because we are in an Activity
                                }})
    }




}

