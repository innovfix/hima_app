package com.gmwapp.hima.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.base.exception.CFException
import com.cashfree.pg.core.api.CFSession
import com.cashfree.pg.core.api.callback.CFCheckoutResponseCallback
import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.cashfree.pg.core.api.webcheckout.CFWebCheckoutPayment
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
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UpiPaymentViewModel
import com.gmwapp.hima.viewmodels.WalletViewModel
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


    private lateinit var callNewRazorPay: Call<NewRazorpayLinkResponse>

    val apiService = RetrofitClient.instance


    private var lastOrderId: String = ""
    private var isPhonePeInitialized = false
    private var cashfreeLastOrderId: String = ""
    private val cfEnvironment = CFSession.Environment.PRODUCTION

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
            // Toast.makeText(this, "Payment Successful", Toast.LENGTH_LONG).show()
        } else {
            //  Toast.makeText(this, "Payment Failed or Cancelled", Toast.LENGTH_LONG).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        getPaymentGateway()
        initUI()
        startPayment()
        observeAddCoins()
        intializePhonpe()

    }

    private fun initUI(){

        val coinSelected = intent.getStringExtra("COIN_SELECTED")
        val savePercent = intent.getStringExtra("SAVE_PERCENT")
        val amount = intent.getStringExtra("AMOUNT")

        var couponCode = intent.getStringExtra("COUPON_CODE")
        val originalPrice = intent.getStringExtra("ORIGINAL_PRICE")
        val discountedPrice = intent.getStringExtra("DISCOUNTED_PRICE")
        val save = intent.getStringExtra("SAVE")
        val coins = intent.getStringExtra("COINS")
        val offer = intent.getStringExtra("OFFER")  // Get offer text from coupon

        Log.d("couponcode","$couponCode")
        Log.d("couponcode","$originalPrice")
        Log.d("couponcode","$discountedPrice")
        Log.d("couponcode","$save")
        Log.d("couponcode","Offer: $offer")

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
            startActivity(intent)
            binding.etCouponCode.text.clear()

        }

        binding.ivBack.setOnClickListener {
            var intent = Intent(this, WalletActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        val cleanedCouponCode = couponCode?.trim()
        binding.etCouponCode.clearFocus()
        binding.etCouponCode.error = null
        binding.etCouponCode.setText(cleanedCouponCode)

        if (couponCode != null && originalPrice != null && discountedPrice != null && save != null) {
            binding.etCouponCode.setText(couponCode)
            binding.tvTotalAmount.text = "$originalPrice" // Set original price
            binding.tvFinalAmount.text = "$originalPrice" // Use a different field for discounted price
            // Show offer text with "Save" prefix (e.g., "Save 40%")
            binding.tvSavePercent.text = formatOfferText(offer, save)
            binding.tvCoinsText.text = coins
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
            val couponCode = it.getStringExtra("COUPON_CODE")
            val originalPrice = it.getStringExtra("ORIGINAL_PRICE")
            val discountedPrice = it.getStringExtra("DISCOUNTED_PRICE")
            val coins = it.getStringExtra("COINS")
            val save = intent.getStringExtra("SAVE")
            val offer = intent.getStringExtra("OFFER")  // Get offer text from coupon

            binding.etCouponCode?.setText(couponCode)
            binding.tvTotalAmount.text = "$originalPrice" // Set original price
            binding.tvFinalAmount.text = "$discountedPrice" // Use a different field for discounted price
            // Show offer text with "Save" prefix (e.g., "Save 40%")
            binding.tvSavePercent.text = formatOfferText(offer, save)
            binding.tvCoinsText.text = coins+" Coins"

            Log.d("PaymentActivityCheck", "Coupon Code: $couponCode")
            Log.d("PaymentActivity", "Original Price: $originalPrice")
            Log.d("PaymentActivity", "Discounted Price: $discountedPrice")
            Log.d("PaymentActivity", "Offer: $offer")
            Log.d("PaymentActivity", "Coins: $coins")
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
                                billingManager!!.purchaseProduct(
                                    // "coin_14",
                                    coinID,
                                )
                                WalletViewModel.navigateToMain.observe(this, Observer { shouldNavigate ->

                                    if (shouldNavigate) {
                                        Toast.makeText(
                                            this,
                                            "Coin purchased successfully",
                                            Toast.LENGTH_SHORT
                                        )
                                            .show()
                                        userData?.id?.let { profileViewModel.getUsers(it) }

                                        updatePurchaseOnMeta()

                                        profileViewModel.getUserLiveData.observe(this, Observer {
                                            it.data?.let { it1 ->
                                                BaseApplication.getInstance()?.getPrefs()
                                                    ?.setUserData(it1)
                                            }
                                           // binding.tvCoins.text = it.data?.coins.toString()
                                            WalletViewModel._navigateToMain.postValue(false)
                                        })
                                    } else {

                                        profileViewModel.getUserLiveData.observe(this, Observer {
                                            it.data?.let { it1 ->
                                                BaseApplication.getInstance()?.getPrefs()
                                                    ?.setUserData(it1)
                                            }
                                           // binding.tvCoins.text = it.data?.coins.toString()

                                        })
                                    }
                                })
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
                                                Toast.makeText(this@PaymentActivity, "Failed to get payment link", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(this@PaymentActivity, "Error: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    override fun onFailure(call: retrofit2.Call<NewRazorpayLinkResponse>, t: Throwable) {
                                        Toast.makeText(this@PaymentActivity, "Failed: ${t.message}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@PaymentActivity, "Status check failed", Toast.LENGTH_SHORT).show()
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
                Log.d("PhoneperesultStr", "$resultStr")


                if (state=="COMPLETED"){
                    runOnUiThread{
                        Toast.makeText(this@PaymentActivity, "Payment Successful", Toast.LENGTH_LONG).show()
                        user_id?.let { WalletViewModel.addCoins(it, coin_id, 1, order_id, "Coins purchased") }
                        observeAddCoins()
                        updatePurchaseOnMeta()
                    }

                }else{
                    runOnUiThread{
                        Toast.makeText(this@PaymentActivity, "Payment Failed", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this@PaymentActivity, "API Failure: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@PaymentActivity, "Invalid server response", Toast.LENGTH_SHORT).show()
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
                activityResultLauncher = activityResultLauncher
            )
        } catch (e: PhonePeInitException) {
            Log.e("PhonePe", "Checkout Failed: ${e.message}")
            Toast.makeText(this, "Could not start payment", Toast.LENGTH_SHORT).show()
        }
    }


    private fun isAnyUPIAppInstalled(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("upi://pay")
        val pm = packageManager
        val activities = pm.queryIntentActivities(intent, 0)
        return activities.isNotEmpty()
    }

    fun observeAddCoins(){
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        WalletViewModel.navigateToMain.observe(this, Observer { shouldNavigate ->

            if (shouldNavigate) {
                Toast.makeText(
                    this,
                    "Coin purchased successfully",
                    Toast.LENGTH_SHORT
                )
                    .show()
                userData?.id?.let { profileViewModel.getUsers(it) }

                profileViewModel.getUserLiveData.observe(this, Observer {
                    it.data?.let { it1 ->
                        BaseApplication.getInstance()?.getPrefs()
                            ?.setUserData(it1)
                    }
//                    binding.tvCoins.text = it.data?.coins.toString()
                    WalletViewModel._navigateToMain.postValue(false)
                })
            } else {

                profileViewModel.getUserLiveData.observe(this, Observer {
                    it.data?.let { it1 ->
                        BaseApplication.getInstance()?.getPrefs()
                            ?.setUserData(it1)
                    }
//                    binding.tvCoins.text = it.data?.coins.toString()

                })
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
            Toast.makeText(this, "Payment Failed: ${cfErrorResponse?.getMessage()}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Cashfree checkout failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
            .url("https://himaapp.in/api/cashfree/create-order")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PaymentActivity, "Order creation failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            cashfreeCheckout(sessionId, orderId)
                        }
                    } else {
                        runOnUiThread {
                            val errorMsg = json.optJSONObject("errors")?.toString() ?: "Order creation failed"
                            Toast.makeText(this@PaymentActivity, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@PaymentActivity, "Invalid server response", Toast.LENGTH_SHORT).show()
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
            .url("https://himaapp.in/api/cashfree/check-order-status?order_id=$orderId")
            .get()
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PaymentActivity, "Status check failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(this@PaymentActivity, "Payment Successful", Toast.LENGTH_LONG).show()
                            user_id?.let { WalletViewModel.add_coins_cashfree(it, coin_id, 1, order_id, "Coins purchased") }
                            observeAddCoins()
                            updatePurchaseOnMeta()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@PaymentActivity, "Payment Failed", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@PaymentActivity, "Invalid response", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

}