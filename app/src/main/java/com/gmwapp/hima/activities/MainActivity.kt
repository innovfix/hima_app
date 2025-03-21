package com.gmwapp.hima.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityMainBinding
import com.gmwapp.hima.dialogs.BottomSheetWelcomeBonus
import com.gmwapp.hima.fragments.FemaleHomeFragment
import com.gmwapp.hima.fragments.HomeFragment
import com.gmwapp.hima.fragments.ProfileFemaleFragment
import com.gmwapp.hima.fragments.ProfileFragment
import com.gmwapp.hima.fragments.RecentFragment
import com.gmwapp.hima.retrofit.responses.RazorPayApiResponse
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.FcmTokenViewModel
import com.gmwapp.hima.viewmodels.OfferViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UpiPaymentViewModel
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.androidbrowserhelper.trusted.LauncherActivity
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import kotlin.math.round


@AndroidEntryPoint
class MainActivity : BaseActivity(), BottomNavigationView.OnNavigationItemSelectedListener,
    BottomSheetWelcomeBonus.OnAddCoinsListener {
    lateinit var binding: ActivityMainBinding
    var isBackPressedAlready = false
    var userName: String? = null
    var userID: String? = null

    val offerViewModel: OfferViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val fcmTokenViewModel: FcmTokenViewModel by viewModels()
    private val upiPaymentViewModel: UpiPaymentViewModel by viewModels()

    private lateinit var call: Call<ApiResponse>
    private lateinit var callRazor: Call<RazorPayApiResponse>

    lateinit var total_amount : String
    lateinit var coinId: String

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.MANAGE_OWN_CALLS] == true) {
            // Permission granted, proceed with call service
        } else {
            // Show an error or disable call-related functionality
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
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userID = userData?.id.toString()

        initUI()
        addObservers()

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
                            handlePaymentGateway(paymentGatewayType)
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


    private fun addObservers() {
        offerViewModel.offerResponseLiveData.observe(this) { response ->
            if (response.success) {
                val coin = response.data[0].coins
                val discountedPrice = response.data[0].price
                val save = response.data[0].save
                val coinId = response.data[0].id
                val total_count = response.data[0].total_count


                val originalPrice = calculateOriginalPrice(discountedPrice, save)


               // Log.d("offerRechargeTotalCount","totalcount ${response.data[0].total_count}")

                Log.d("OrinalPrice","OriginalPrice $originalPrice")
                Log.d("OrinalPrice","discountPrice $discountedPrice")
                Log.d("OrinalPrice","savePercent $save")
                if (BaseApplication.getInstance()?.getPrefs()
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

        total_amount = "$amount"
        var pointsId = "$id"
        coinId = id.toString()
        Log.d("amount", "amount $total_amount")

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        val userId = userData?.id
        val name = userData?.name ?: ""
        val email = "test@gmail.com"
        val mobile = userData?.mobile ?: ""

        if (userId != null && pointsId.isNotEmpty() && total_amount.isNotEmpty()) {
            val userIdWithPoints = "$userId-$pointsId"

            val apiService = RetrofitClient.instance
            call = apiService.addCoins(name, total_amount, email, mobile, userIdWithPoints)

            callRazor = apiService.addCoinsRazorPay(userIdWithPoints,name,total_amount,email,mobile)

            accountViewModel.getSettings()




//            call.enqueue(object : retrofit2.Callback<ApiResponse> {
//                override fun onResponse(
//                    call: retrofit2.Call<ApiResponse>,
//                    response: retrofit2.Response<ApiResponse>
//                ) {
//                    if (response.isSuccessful && response.body()?.success == true) {
//                        Toast.makeText(
//                            this@MainActivity,
//                            response.body()?.message,
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    } else {
//                        // println("Long URL: ${it.longurl}") // Print to the terminal
//                        //Toast.makeText(mContext, it.longurl, Toast.LENGTH_SHORT).show()
//                        val intent = Intent(this@MainActivity, LauncherActivity::class.java)
//                        intent.setData(Uri.parse(response.body()?.longurl))
//                        startActivity(intent)
//                        //  Toast.makeText(this@WalletActivity, response.body()?.message ?: "Error", Toast.LENGTH_SHORT).show()
//                    }
//                }
//
//                override fun onFailure(call: retrofit2.Call<ApiResponse>, t: Throwable) {
//                    Toast.makeText(this@MainActivity, "Failed: ${t.message}", Toast.LENGTH_SHORT)
//                        .show()
//                }
//            })
        } else {
            Toast.makeText(this, "Invalid input data", Toast.LENGTH_SHORT).show()
        }
    }

    fun calculateOriginalPrice(price: Int, savePercentage: Int): Int {
        val originalPrice = price / (1 - (savePercentage / 100.0)) // Use Double for division
        return round(originalPrice).toInt() // Round to the nearest integer
    }

    private fun handlePaymentGateway(paymentGatewayType: String) {
        // Handle the payment gateway type logic
        when (paymentGatewayType) {
            "razorpay" -> {


                callRazor.enqueue(object : retrofit2.Callback<RazorPayApiResponse> {
                    override fun onResponse(call: retrofit2.Call<RazorPayApiResponse>, response: retrofit2.Response<RazorPayApiResponse>) {
                        if (response.isSuccessful && response.body() != null) {
                            val apiResponse = response.body()

                            // Extract the Razorpay payment link
                            val paymentUrl = apiResponse?.short_url
                            Log.d("WalletResponse","${ apiResponse?.short_url}")


                            if (!paymentUrl.isNullOrEmpty()) {

                                val intent =Intent(this@MainActivity, LauncherActivity::class.java)
                                intent.setData(Uri.parse(response.body()?.short_url))
                                Log.d("WalletResponse","${response.body()?.short_url}")
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

                    override fun onFailure(call: retrofit2.Call<RazorPayApiResponse>, t: Throwable) {
                        Toast.makeText(this@MainActivity, "Failed: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })





            }

            "upigateway" ->{

                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                var userid = userData?.id


                userid?.let {
                    val clientTxnId = generateRandomTxnId(it,coinId)  // Generate a new transaction ID
                    upiPaymentViewModel.createUpiPayment(it, clientTxnId, total_amount)
                }

            }


            "instamojo" -> {


                call.enqueue(object : retrofit2.Callback<ApiResponse> {
                    override fun onResponse(call: retrofit2.Call<ApiResponse>, response: retrofit2.Response<ApiResponse>) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(this@MainActivity, response.body()?.message, Toast.LENGTH_SHORT).show()
                        } else {
                            // println("Long URL: ${it.longurl}") // Print to the terminal
                            //Toast.makeText(mContext, it.longurl, Toast.LENGTH_SHORT).show()

                            val intent =
                                Intent(this@MainActivity, LauncherActivity::class.java)
                            intent.setData(Uri.parse(response.body()?.longurl))
                            Log.d("WalletResponse","${response.body()?.longurl}")
                            startActivity(intent)
                            finish()// Directly starting the intent without launcher
                            //  Toast.makeText(this@WalletActivity, response.body()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<ApiResponse>, t: Throwable) {
                        Toast.makeText(this@MainActivity, "Failed: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })




            }
            else -> {
                Toast.makeText(this, "Invalid Payment Gateway", Toast.LENGTH_SHORT).show()
            }
        }

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

    }

