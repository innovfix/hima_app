package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Observer
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
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.ActivityPaymentBinding
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.retrofit.responses.NewRazorpayLinkResponse
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.utils.AppEventLogger
import com.gmwapp.hima.utils.Config
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UpiPaymentViewModel
import com.gmwapp.hima.viewmodels.WalletViewModel
import com.gmwapp.hima.viewmodels.CouponViewModel
import com.google.androidbrowserhelper.trusted.LauncherActivity
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
@AndroidEntryPoint
class PaymentActivity : AppCompatActivity(), CFCheckoutResponseCallback {
    lateinit var binding: ActivityPaymentBinding
    private val WalletViewModel: WalletViewModel by viewModels()
    var paymentGateway = ""
    private var billingManager: BillingManager? = null
    val profileViewModel: ProfileViewModel by viewModels()
    private val upiPaymentViewModel: UpiPaymentViewModel by viewModels()
    private val couponViewModel: CouponViewModel by viewModels()


    private lateinit var callNewRazorPay: Call<NewRazorpayLinkResponse>

    val apiService = RetrofitClient.instance


    private var lastOrderId: String = ""
    private var isPhonePeInitialized = false
    private var cashfreeLastOrderId: String = ""
    private val cfEnvironment = CFSession.Environment.PRODUCTION
    
    // ✅ ADD THESE VARIABLES TO STORE COUPON DATA
    private var selectedCouponId: String = ""
    private var selectedCouponCode: String = ""
    
    // ✅ ADD THESE VARIABLES TO STORE ORIGINAL PAYMENT DATA
    private var originalCoinSelected: String = ""
    private var originalSavePercent: String = ""
    private var originalAmount: String = ""
    
    // ✅ ADD THESE VARIABLES TO STORE CURRENTLY APPLIED COUPON STATE
    private var currentCouponCode: String? = null
    private var currentCouponId: String = ""
    private var currentOriginalPrice: String? = null
    private var currentDiscountedPrice: String? = null
    private var currentSave: String? = null
    private var currentCoins: String? = null
    private var currentOffer: String? = null
    private var isCouponApplied: Boolean = false

    /** Google Play billing: [updatePurchaseOnMeta] when [WalletViewModel.navigateToMain] fires. */
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
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        getPaymentGateway()
        initUI()
        startPayment()
        setupPaymentWalletObservers()
        intializePhonpe()

    }

    private fun initUI(){
        // Store original payment data in class variables
        originalCoinSelected = intent.getStringExtra("COIN_SELECTED") ?: ""
        originalSavePercent = intent.getStringExtra("SAVE_PERCENT") ?: ""
        originalAmount = intent.getStringExtra("AMOUNT") ?: ""

        val coinSelected = originalCoinSelected
        val savePercent = originalSavePercent
        val amount = originalAmount

        var couponCode = intent.getStringExtra("COUPON_CODE")
        val originalPrice = intent.getStringExtra("ORIGINAL_PRICE")
        val discountedPrice = intent.getStringExtra("DISCOUNTED_PRICE")
        val save = intent.getStringExtra("SAVE")
        val coins = intent.getStringExtra("COINS")
        val offer = intent.getStringExtra("OFFER")  // Get offer text from coupon
        // ✅ ADD THIS LINE TO GET COUPON_ID
        selectedCouponId = intent.getStringExtra("COUPON_ID") ?: ""
        
        val coinId = BaseApplication.getInstance()?.getPrefs()?.getString("last_coin_id") ?: ""
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0

        Log.d("couponcode","$couponCode")
        Log.d("couponcode","$originalPrice")
        Log.d("couponcode","$discountedPrice")
        Log.d("couponcode","$save")
        Log.d("couponcode","Offer: $offer")
        // ✅ LOG THE COUPON_ID
        Log.d("couponcode","Coupon ID: $selectedCouponId")

        binding.tvCoinsText.text = coinSelected + " Coins"
        binding.tvTotalAmount.text = "₹$amount"
        binding.tvSavePercent.text = "Save $savePercent%"
        binding.tvFinalAmount.text =  "₹$amount"

        binding.tvChange.setOnClickListener {
            var intent = Intent(this, WalletActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()

        }

        binding.llAllCoupons.setOnClickListener {
            var intent = Intent(this, CouponActivity::class.java)
            // Pass original payment data to CouponActivity
            intent.putExtra("COIN_SELECTED", originalCoinSelected)
            intent.putExtra("SAVE_PERCENT", originalSavePercent)
            intent.putExtra("AMOUNT", originalAmount)
            // Pass current coupon state if applied
            if (isCouponApplied) {
                intent.putExtra("CURRENT_COUPON_CODE", currentCouponCode)
                intent.putExtra("CURRENT_COUPON_ID", currentCouponId)
                intent.putExtra("CURRENT_ORIGINAL_PRICE", currentOriginalPrice)
                intent.putExtra("CURRENT_DISCOUNTED_PRICE", currentDiscountedPrice)
                intent.putExtra("CURRENT_SAVE", currentSave)
                intent.putExtra("CURRENT_COINS", currentCoins)
                intent.putExtra("CURRENT_OFFER", currentOffer)
            }
            startActivity(intent)
           // binding.etCouponCode.text.clear()

        }

        binding.ivBack.setOnClickListener {
            var intent = Intent(this, WalletActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // ✅ Call the default coupon API ONLY if user didn't select a coupon
        if (userId != 0 && coinId.isNotEmpty()) {
            // Check if user selected a coupon from CouponActivity
            if (selectedCouponId.isEmpty()) {
                // No coupon selected, fetch default coupon
                observeDefaultCoupon()
                couponViewModel.getDefaultCoupon(userId, coinId)
                Log.d("CouponLogic", "No user coupon selected, fetching default coupon")
            } else {
                // User already selected a coupon, don't override it
                Log.d("CouponLogic", "User selected coupon with ID: $selectedCouponId, skipping default coupon fetch")
                updateUIWithCoupon(couponCode, originalPrice, discountedPrice, save, coins, offer)
            }
        } else {
            // Use existing data if no user or coin id
            updateUIWithCoupon(couponCode, originalPrice, discountedPrice, save, coins, offer)
        }
    }

    private fun observeDefaultCoupon() {
        couponViewModel.defaultCouponLiveData.observe(this, Observer { response ->
            if (response?.success == true && response.default_coupon_enabled && response.data != null) {
                // API returned a default coupon
                val data = response.data
                val newCouponCode = data.coupon_code
                val newOriginalPrice = data.original_price?.toString()?.let { "₹$it" } ?: "₹0"
                val newDiscountedPrice = data.discount_price?.let { "₹$it" } ?: "₹0"
                val newSave = data.save_price?.replace("₹", "")?.replace("Save ", "")?.trim()
                val newCoins = data.coins?.toString()?.let { "$it Coins" } ?: "0 Coins"
                val newOffer = data.offer
                // ✅ ADD THIS LINE TO STORE DEFAULT COUPON_ID
                selectedCouponId = data.id?.toString() ?: ""

                Log.d("DefaultCoupon", "Coupon applied from API: $newCouponCode")
                Log.d("DefaultCoupon", "Coupon ID: $selectedCouponId")
                updateUIWithCoupon(newCouponCode, newOriginalPrice, newDiscountedPrice, newSave, newCoins, newOffer)
            } else {
                // API returned false or no data, use existing data
                Log.d("DefaultCoupon", "No default coupon, using existing data")
                val couponCode = intent.getStringExtra("COUPON_CODE")
                val originalPrice = intent.getStringExtra("ORIGINAL_PRICE")
                val discountedPrice = intent.getStringExtra("DISCOUNTED_PRICE")
                val save = intent.getStringExtra("SAVE")
                val coins = intent.getStringExtra("COINS")
                val offer = intent.getStringExtra("OFFER")
                updateUIWithCoupon(couponCode, originalPrice, discountedPrice, save, coins, offer)
            }
        })

        couponViewModel.defaultCouponErrorLiveData.observe(this, Observer { error ->
            Log.e("DefaultCouponError", "Error: $error")
            // Use existing data on error
            val couponCode = intent.getStringExtra("COUPON_CODE")
            val originalPrice = intent.getStringExtra("ORIGINAL_PRICE")
            val discountedPrice = intent.getStringExtra("DISCOUNTED_PRICE")
            val save = intent.getStringExtra("SAVE")
            val coins = intent.getStringExtra("COINS")
            val offer = intent.getStringExtra("OFFER")
            updateUIWithCoupon(couponCode, originalPrice, discountedPrice, save, coins, offer)
        })
    }

    private fun updateUIWithCoupon(
        couponCode: String?,
        originalPrice: String?,
        discountedPrice: String?,
        save: String?,
        coins: String?,
        offer: String?
    ) {
        val cleanedCouponCode = couponCode?.trim()
        binding.etCouponCode.clearFocus()
        binding.etCouponCode.error = null
        binding.etCouponCode.setText(cleanedCouponCode)

        if (couponCode != null && originalPrice != null && discountedPrice != null && save != null) {
            // Format coins to ensure "Coins" text is appended
            val formattedCoins = formatCoinsText(coins)
            
            // Store current coupon state
            currentCouponCode = couponCode
            currentCouponId = selectedCouponId
            currentOriginalPrice = originalPrice
            currentDiscountedPrice = discountedPrice
            currentSave = save
            currentCoins = formattedCoins
            currentOffer = offer
            isCouponApplied = true
            
            binding.etCouponCode.setText(couponCode)
            binding.tvTotalAmount.text = "$originalPrice" // Set original price
            binding.tvFinalAmount.text = "$discountedPrice" // Use a different field for discounted price
            // Show offer text with "Save" prefix (e.g., "Save 40%")
            binding.tvSavePercent.text = formatOfferText(offer, save)
            binding.tvCoinsText.text = formattedCoins
            
            // ✅ Show coupon applied indicators
            binding.tvApplied.visibility = View.VISIBLE
            binding.ivCorrect.visibility = View.VISIBLE
            
            // ✅ Disable EditText when coupon is applied
            binding.etCouponCode.isEnabled = false
        } else {
            // No coupon applied, reset state
            isCouponApplied = false
            currentCouponCode = null
            currentCouponId = ""
            currentOriginalPrice = null
            currentDiscountedPrice = null
            currentSave = null
            currentCoins = null
            currentOffer = null
        }
    }
    
    /**
     * Format coins text to ensure "Coins" suffix is present
     * Examples:
     * - "50" → "50 Coins"
     * - "50 Coins" → "50 Coins"
     * - "100" → "100 Coins"
     */
    private fun formatCoinsText(coins: String?): String {
        if (coins.isNullOrBlank()) {
            return "0 Coins"
        }
        
        val trimmedCoins = coins.trim()
        
        // Check if "Coins" is already present (case-insensitive)
        if (trimmedCoins.contains("Coins", ignoreCase = true)) {
            return trimmedCoins
        }
        
        // Remove any non-numeric characters except digits and decimals
        val numericValue = trimmedCoins.replace("[^0-9.]".toRegex(), "").trim()
        
        return if (numericValue.isNotEmpty()) {
            "$numericValue Coins"
        } else {
            "0 Coins"
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        var intent = Intent(this, WalletActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update activity's intent reference
        handleIntent(intent) // Handle new intent data

    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            // Restore original payment data first
            val coinSelected = it.getStringExtra("COIN_SELECTED") ?: originalCoinSelected
            val savePercent = it.getStringExtra("SAVE_PERCENT") ?: originalSavePercent
            val amount = it.getStringExtra("AMOUNT") ?: originalAmount
            
            // Check if there's a new coupon from CouponActivity
            val newCouponCode = it.getStringExtra("COUPON_CODE")
            val newCouponId = it.getStringExtra("COUPON_ID")
            val newOriginalPrice = it.getStringExtra("ORIGINAL_PRICE")
            val newDiscountedPrice = it.getStringExtra("DISCOUNTED_PRICE")
            val newCoins = it.getStringExtra("COINS")
            val newSave = it.getStringExtra("SAVE")
            val newOffer = it.getStringExtra("OFFER")
            
            // Check if we need to restore previous coupon state
            val restoreCouponCode = it.getStringExtra("RESTORE_COUPON_CODE")
            val restoreCouponId = it.getStringExtra("RESTORE_COUPON_ID")
            val restoreOriginalPrice = it.getStringExtra("RESTORE_ORIGINAL_PRICE")
            val restoreDiscountedPrice = it.getStringExtra("RESTORE_DISCOUNTED_PRICE")
            val restoreCoins = it.getStringExtra("RESTORE_COINS")
            val restoreSave = it.getStringExtra("RESTORE_SAVE")
            val restoreOffer = it.getStringExtra("RESTORE_OFFER")
            
            // If new coupon data is provided, use it
            if (newCouponCode != null && newOriginalPrice != null && newDiscountedPrice != null && newSave != null) {
                // Apply new coupon from CouponActivity
                selectedCouponId = newCouponId ?: ""
                updateUIWithCoupon(newCouponCode, newOriginalPrice, newDiscountedPrice, newSave, newCoins, newOffer)
            } else if (restoreCouponCode != null && restoreOriginalPrice != null && restoreDiscountedPrice != null && restoreSave != null) {
                // Restore previous coupon state (default or previously selected)
                selectedCouponId = restoreCouponId ?: ""
                updateUIWithCoupon(restoreCouponCode, restoreOriginalPrice, restoreDiscountedPrice, restoreSave, restoreCoins, restoreOffer)
            } else {
                // No new coupon, restore the last applied coupon state if exists
                if (isCouponApplied && currentCouponCode != null) {
                    // Restore the previously applied coupon
                    selectedCouponId = currentCouponId
                    updateUIWithCoupon(
                        currentCouponCode,
                        currentOriginalPrice,
                        currentDiscountedPrice,
                        currentSave,
                        currentCoins,
                        currentOffer
                    )
                } else {
                    // No coupon was applied, show original payment data
                    binding.tvCoinsText.text = coinSelected + " Coins"
                    binding.tvTotalAmount.text = "₹$amount"
                    binding.tvSavePercent.text = "Save $savePercent%"
                    binding.tvFinalAmount.text = "₹$amount"
                    binding.etCouponCode?.setText("")
                    binding.tvApplied.visibility = View.GONE
                    binding.ivCorrect.visibility = View.GONE
                    binding.etCouponCode.isEnabled = true
                }
            }

            Log.d("PaymentActivityCheck", "Coin Selected: $coinSelected")
            Log.d("PaymentActivityCheck", "Amount: $amount")
            Log.d("PaymentActivityCheck", "Coupon Applied: $isCouponApplied")
            Log.d("PaymentActivityCheck", "Current Coupon Code: $currentCouponCode")
            Log.d("PaymentActivityCheck", "New Coupon Code: $newCouponCode")
            Log.d("PaymentActivityCheck", "Restore Coupon Code: $restoreCouponCode")
        }
    }

    /**
     * Format offer text to always include "Save" prefix
     * Examples:
     * - "40%" → "Save 40%"
     * - "Save 40%" → "Save 40%"
     * - "50% Off" → "Save 50% Off"
     * - null → "Save ₹250"
     */
    private fun formatOfferText(offer: String?, save: String?): String {
        return when {
            offer.isNullOrBlank() -> {
                // No offer text, use calculated save amount
                "Save ₹$save"
            }
            offer.startsWith("Save", ignoreCase = true) -> {
                // Already has "Save" prefix
                offer
            }
            else -> {
                // Add "Save" prefix
                "Save $offer"
            }
        }
    }

    fun getPaymentGateway(){

        paymentGateway = BaseApplication.getInstance()?.getPrefs()?.getString("last_coin_pg").toString()

        Log.d("LatestPg","$paymentGateway")
    }


    fun startPayment(){
        binding.btnPay.setOnClickListener {


        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id
        val coinID = BaseApplication.getInstance()?.getPrefs()?.getString("last_coin_id")
        val pointsIdInt = coinID?.toIntOrNull()



        if (coinID != null) {
            if (userId != null && coinID.isNotEmpty()) {
                if (coinID != null) {


                    if (paymentGateway.isNotEmpty()) {

                        when (paymentGateway) {

                            "phonepe"->{

                                if (isPhonePeInitialized){
                                    fetchOrderFromBackend(coinID)
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
                                if (pointsIdInt != null) {
                                    WalletViewModel.tryCoins(userId, pointsIdInt, 0, random4Digit, "try")
                                }
                                pendingPurchaseMetaFromPlayBilling = true
                                billingManager!!.purchaseProduct(
                                    // "coin_14",
                                    coinID,
                                )
                            }

                            "razorpay" -> {

                                callNewRazorPay = apiService.callNewRazorPay(userId,coinID)


                                callNewRazorPay.enqueue(object : retrofit2.Callback<NewRazorpayLinkResponse> {
                                    override fun onResponse(call: retrofit2.Call<NewRazorpayLinkResponse>, response: retrofit2.Response<NewRazorpayLinkResponse>) {
                                        if (response.isSuccessful && response.body() != null) {
                                            val apiResponse = response.body()

                                            // Extract the Razorpay payment link
                                            val paymentUrl = apiResponse?.data?.short_url

                                            Log.d("paymentUrlRazorPay","$paymentUrl")

                                            if (!paymentUrl.isNullOrEmpty()) {

                                                val intent =Intent(this@PaymentActivity, LauncherActivity::class.java)
                                                intent.setData(Uri.parse(paymentUrl))
                                                Log.d("paymentUrlRazorPay","$paymentUrl")
                                                startActivity(intent)

    //                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
    //                                startActivity(intent)
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
                                fetchOrderOfCashfree(coinID)
                            }

                            "upigateway" -> {

                                var amount = binding.tvFinalAmount.text.toString().trim()
                                val amountValue = amount.toDoubleOrNull()
                                if (amountValue == null) {
                                    return@setOnClickListener
                                }


                                val twoPercentage = amountValue * 0.02
                                val roundedAmount = Math.round(twoPercentage)
                               var total_amount = (amountValue + roundedAmount).toString()

                                Log.d("upigateway","Clicked")
                                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                                var userid = userData?.id
                                userid?.let {
                                    val clientTxnId = generateRandomTxnId(
                                        it,
                                        coinID
                                    )  // Generate a new transaction ID
                                    upiPaymentViewModel.createUpiPayment(it, clientTxnId, total_amount)
                                }

                            }


                            else -> {
                                showAppToast("Invalid Payment Gateway", Toast.LENGTH_SHORT)
                            }


                        }
                    }
                }
            } else {
                showAppToast("Invalid input data", Toast.LENGTH_SHORT)
            }
        }
    }}

    fun generateRandomTxnId(userId: Int, coinId: String): String {
        return "$userId-$coinId-${System.currentTimeMillis()}"
    }



    fun updatePurchaseOnMeta(){
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()
        val userId = userData?.id
        val coinId = prefs?.getString("last_coin_id")
        val coinAmount = prefs?.getString("last_coin_amount")?.toDoubleOrNull() ?: 0.0
        val params = Bundle().apply {
            putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "INR")
            putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, coinAmount)
            putString("user_id", "$userId")
            putString("coin_id", "$coinId")
        }
        AppEventsLogger.newLogger(this).logEvent(AppEventsConstants.EVENT_NAME_PURCHASED, coinAmount, params)

        // Adjust — mirror the Meta purchase here too so this payment path is not
        // missed on Adjust. (PaymentActivity only logs to Meta; backend/Firebase
        // logging happens in WalletActivity and MainActivity.)
        com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
            "purchase",
            revenueInr = coinAmount,
            params = mapOf("user_id" to "$userId", "coin_id" to "$coinId")
        )
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
                Log.d("PhoneperesultStr", "$resultStr")
                
                try {
                    val json = JSONObject(resultStr)
                    
                    // ✅ SAFELY GET phonepe_status
                    if (!json.has("phonepe_status")) {
                        Log.e("PhonePeError", "phonepe_status not found in response. Response: $resultStr")
                        runOnUiThread {
                            showAppToast("Invalid response: missing phonepe_status", Toast.LENGTH_SHORT)
                        }
                        return
                    }
                    
                    val phonePeStatus = json.getJSONObject("phonepe_status")
                    val state = phonePeStatus.optString("state", "UNKNOWN")
                    
                    // ✅ SAFELY GET local_record
                    if (!json.has("local_record") || json.isNull("local_record")) {
                        Log.e("PhonePeError", "local_record not found or null in response. Response: $resultStr")
                        runOnUiThread {
                            showAppToast("Invalid response: missing local_record", Toast.LENGTH_SHORT)
                        }
                        return
                    }
                    
                    val localRecord = json.getJSONObject("local_record")
                    val coin_id = localRecord.optString("coin_id", "")
                    val order_id = localRecord.optString("order_id", "")
                    
                    Log.d("PhonePeOrderStatus", "Order Status: $resultStr")
                    Log.d("PhonePeOrderState", "Order State: $state,  Coin_id : $coin_id , Order_id :$order_id ")

                    if (state == "COMPLETED") {
                        runOnUiThread {
                            showAppToast("Payment Successful", Toast.LENGTH_LONG)
                            if (coin_id.isNotEmpty() && order_id.isNotEmpty()) {
                                user_id?.let { WalletViewModel.addCoins(it, coin_id, 1, order_id, "Coins purchased") }
                                updatePurchaseOnMeta()
                            } else {
                                Log.e("PhonePeError", "Missing coin_id or order_id. coin_id=$coin_id, order_id=$order_id")
                                showAppToast("Error: Missing coin_id or order_id", Toast.LENGTH_SHORT)
                            }
                        }
                    } else {
                        runOnUiThread {
                            showAppToast("Payment Failed", Toast.LENGTH_LONG)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PhonePeException", "Error parsing response: ${e.message}\nResponse: $resultStr")
                    runOnUiThread {
                        showAppToast("Error: ${e.message}", Toast.LENGTH_SHORT)
                    }
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
        // ✅ SET COUPON_ID TO "0" IF EMPTY
        val couponIdToPass = if (selectedCouponId.isNotEmpty()) selectedCouponId else "0"
        Log.d("couponIdToPass","$couponIdToPass")


        val formBody = FormBody.Builder()
            .add("user_id", user_id)
            .add("coins_id", coinId)
            // ✅ ALWAYS PASS COUPON_ID (0 if empty)
            .add("coupon_id", couponIdToPass)
            .build()

        Log.d("SelectedCoinID", " $coinId")
        Log.d("PhonePeRequest", "Coupon ID: $couponIdToPass")

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
                activityResultLauncher = activityResultLauncher
            )
        } catch (e: PhonePeInitException) {
            Log.e("PhonePe", "Checkout Failed: ${e.message}")
            showAppToast("Could not start payment", Toast.LENGTH_SHORT)
        }
    }


    private fun isAnyUPIAppInstalled(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("upi://pay")
        val pm = packageManager
        val activities = pm.queryIntentActivities(intent, 0)
        return activities.isNotEmpty()
    }

    /** Single observers — avoids nested LiveData leaks (same pattern as [WalletActivity]). */
    private fun setupPaymentWalletObservers() {
        profileViewModel.getUserLiveData.observe(this, Observer { response ->
            if (isFinishing || isDestroyed) return@Observer
            try {
                response?.data?.let { u ->
                    // B075 — payment refresh: preserve toggle / DND intent.
                    BaseApplication.getInstance()?.getPrefs()?.setUserDataPreservingLocalIntent(u)
                }
            } catch (e: Exception) {
                Log.e("PaymentActivity", "getUserLiveData", e)
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
                Log.e("PaymentActivity", "navigateToMain", e)
            }
        })
    }

    // ===================== Cashfree Payment Methods =====================

    override fun onPaymentVerify(orderID: String?) {
        Log.d("WebCheckout", "Payment verified for order: $orderID")
        orderID?.let {
            checkCashfreeOderStatus(it)
        }
    }

    override fun onPaymentFailure(cfErrorResponse: CFErrorResponse?, orderID: String?) {
        Log.e("WebCheckout", "Payment failed for $orderID: ${cfErrorResponse?.getMessage()}")
        runOnUiThread {
            showAppToast("Payment Failed: ${cfErrorResponse?.getMessage()}", Toast.LENGTH_LONG)
        }
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


    fun cashfreeCheckout(paymentSessionID: String, orderID: String) {
        try {
            val cfSession = CFSession.CFSessionBuilder()
                .setEnvironment(cfEnvironment)
                .setPaymentSessionID(paymentSessionID)
                .setOrderId(orderID)
                .build()

            val cfWebCheckoutPayment = CFWebCheckoutPayment.CFWebCheckoutPaymentBuilder()
                .setSession(cfSession)
                .build()

            CFPaymentGatewayService.getInstance()
                .doPayment(this@PaymentActivity, cfWebCheckoutPayment)

        } catch (e: CFException) {
            e.printStackTrace()
            showAppToast("Cashfree checkout failed: ${e.message}", Toast.LENGTH_SHORT)
        }
    }

    private fun fetchOrderOfCashfree(coinId: String) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val user_id = userData?.id
        val client = OkHttpClient()

        // ✅ SET COUPON_ID TO "0" IF EMPTY
        val couponIdToPass = if (selectedCouponId.isNotEmpty()) selectedCouponId else "0"
        Log.d("couponIdToPass","$couponIdToPass")

        // ✅ ALWAYS INCLUDE COUPON_ID IN JSON BODY (0 if empty)
        val json = """{
        "user_id": "$user_id",
        "coins_id": "$coinId",
        "coupon_id": "$couponIdToPass"
    }"""
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, json)
        
        // ✅ LOG THE REQUEST
        Log.d("CashfreeRequest", "Request Body: $json")

        val request = Request.Builder()
            .url("${Config.API_ROOT}cashfree/create-order")
            .post(body)
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
                          //  cashfreeCheckout(sessionId, orderId)
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
            .get()
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

    override fun onResume() {
        super.onResume()

        Log.d("cashfreeLastOrderId","$cashfreeLastOrderId")
        if (cashfreeLastOrderId.isNotEmpty()){
            checkCashfreeOderStatus(cashfreeLastOrderId)
            cashfreeLastOrderId = "" // reset so it won't run again

        }
    }

}