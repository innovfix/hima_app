package com.gmwapp.hima.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BillingManager.BillingManager
import com.gmwapp.hima.R
import com.gmwapp.hima.YoutubeRechargeActivity
import com.gmwapp.hima.adapters.CoinAdapter
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.ActivityWalletBinding
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.retrofit.responses.RazorPayApiResponse
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UpiPaymentViewModel
import com.gmwapp.hima.viewmodels.UpiViewModel
import com.gmwapp.hima.viewmodels.WalletViewModel
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call

@AndroidEntryPoint
class WalletActivity : BaseActivity()  {
    lateinit var binding: ActivityWalletBinding
    private val WalletViewModel: WalletViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val upiPaymentViewModel: UpiPaymentViewModel by viewModels()
    private lateinit var call: Call<ApiResponse>
    private lateinit var callRazor: Call<RazorPayApiResponse>

    val profileViewModel: ProfileViewModel by viewModels()

    private lateinit var selectedCoin : String
    private lateinit var selectedSavePercent : String

    private lateinit var email :String
    private lateinit var mobile :String
    private lateinit var total_amount :String
    private lateinit var userIdWithPoints :String
    private lateinit var name :String

    val apiService = RetrofitClient.instance


    private var billingManager: BillingManager? = null

    private val viewModel: UpiViewModel by viewModels()
    var amount = ""
    var pointsId = ""
    var paymentGateway = ""



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
        observeCoins()
    }

    override fun onResume() {
        super.onResume()
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.id?.let { profileViewModel.getUsers(it) }
    }

    fun observeCoins(){
        profileViewModel.getUserLiveData.observe(this, Observer {
            it.data?.let { it1 ->
                BaseApplication.getInstance()?.getPrefs()?.setUserData(it1)
            }
            Log.d("coinsUpdated_","$${it.data?.coins.toString()}")
            binding.tvCoins.text = it.data?.coins.toString()
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
                            paymentGateway = paymentGatewayType
                            Log.d("paymentGateway","$paymentGateway")
                        } ?: run {
                            // Show Toast if payment_gateway_type is null
                            Toast.makeText(this, "Please try again later", Toast.LENGTH_SHORT).show()
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
        binding.tvCoins.text = userData?.coins.toString()


        val layoutManager = GridLayoutManager(this, 3)
        binding.ivBack.setOnSingleClickListener {
            finish()
        }
//        binding.rvPlans.addItemDecoration(SpacesItemDecoration(20))
        binding.rvPlans.setLayoutManager(layoutManager)
//        binding.rvPlans.addItemDecoration(SpacesItemDecoration(10))
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.let { WalletViewModel.getCoins(it.id) }
        WalletViewModel.coinsLiveData.observe(this, Observer {

            if(it.success){
              //  Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
            }
            else{
                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
            }

            if (it!=null && it.success && it.data != null) {
                // Create the adapter
                val coinAdapter = CoinAdapter(this, it.data, object : OnItemSelectionListener<CoinsResponseData> {
                    override fun onItemSelected(coin: CoinsResponseData) {
                        // Update button text and make it visible when an item is selected
                        binding.btnAddCoins.text = getString(R.string.add_quantity_coins, coin.coins)
                        binding.btnAddCoins.visibility = View.VISIBLE
                        amount = coin.price.toString()
                        pointsId = coin.id.toString()

                        selectedCoin = coin.coins.toString()
                        selectedSavePercent = coin.save.toString()

                    }

                    val number: CoinsResponseData?
                        get() = null
                })

                // Set the adapter
                binding.rvPlans.adapter = coinAdapter

                // Set default button text and visibility for the first item
                if (it.data.isNotEmpty()) {
                    val firstCoin = it.data[0]
                    binding.btnAddCoins.text = getString(R.string.add_quantity_coins, firstCoin.coins)
                    binding.btnAddCoins.visibility = View.VISIBLE
                    amount = firstCoin.price.toString()
                    pointsId = firstCoin.id.toString()

                    selectedCoin = firstCoin.coins.toString()
                    selectedSavePercent = firstCoin.save.toString()
                }
            }

        })

        billingManager = BillingManager(this)


        binding.btnAddCoins.setOnClickListener(View.OnClickListener { view: View? ->
            accountViewModel.getSettings()
            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            val userId = userData?.id
            val pointsIdInt = pointsId.toIntOrNull()
            val twoPercentage = amount.toDouble() * 0.02
            val roundedAmount = Math.round(twoPercentage)
            total_amount = (amount.toDouble() + roundedAmount).toString()

            if (userId != null && pointsId.isNotEmpty()) {
                if (pointsIdInt != null) {


                    if (paymentGateway.isNotEmpty()) {

                        when (paymentGateway) {
                            "gpay" -> {

                                // ✅ Save userId and pointsIdInt BEFORE launching billing
                                val preferences = DPreferences(this)
                                preferences.setSelectedUserId(userId.toString())
                                preferences.setSelectedPlanId(java.lang.String.valueOf(pointsIdInt))
                                billingManager!!.purchaseProduct(
                                    //"coin_14",
                                    pointsId,
                                    userId,
                                    pointsIdInt
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

                                        profileViewModel.getUserLiveData.observe(this, Observer {
                                            it.data?.let { it1 ->
                                                BaseApplication.getInstance()?.getPrefs()
                                                    ?.setUserData(it1)
                                            }
                                            binding.tvCoins.text = it.data?.coins.toString()
                                            WalletViewModel._navigateToMain.postValue(false)
                                        })
                                    } else {

                                        profileViewModel.getUserLiveData.observe(this, Observer {
                                            it.data?.let { it1 ->
                                                BaseApplication.getInstance()?.getPrefs()
                                                    ?.setUserData(it1)
                                            }
                                            binding.tvCoins.text = it.data?.coins.toString()

                                        })
                                    }
                                })
                                }

                            "upigateway" -> {

                                Log.d("upigateway","Clicked")
                                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                                var userid = userData?.id
                                userid?.let {
                                    val clientTxnId = generateRandomTxnId(
                                        it,
                                        pointsId
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
//                Toast.makeText(this, "Invalid input data", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }

//
//    private fun addObservers() {
//        viewModel.addpointResponseLiveData.observe(this, Observer {
//            if (it != null && it.success) {
//
//                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
//
//            }
//            else{
//                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
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
//                    Toast.makeText(this, "Payment URL not found. Please try again later.", Toast.LENGTH_LONG).show()
//                }
//            } else {
//                Log.e("UPI Payment Error", "Invalid response: ${response?.data}")
//                Toast.makeText(this, "Payment failed. Please check your internet or payment details.", Toast.LENGTH_LONG).show()
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
//                            Toast.makeText(this, "Please try again later", Toast.LENGTH_SHORT).show()
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
//                                Toast.makeText(this@WalletActivity, "Failed to get payment link", Toast.LENGTH_SHORT).show()
//                            }
//                        } else {
//                            Toast.makeText(this@WalletActivity, "Error: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT).show()
//                        }
//                    }
//
//                    override fun onFailure(call: retrofit2.Call<RazorPayApiResponse>, t: Throwable) {
//                        Toast.makeText(this@WalletActivity, "Failed: ${t.message}", Toast.LENGTH_SHORT).show()
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
//                            Toast.makeText(this@WalletActivity, response.body()?.message, Toast.LENGTH_SHORT).show()
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
//                            //  Toast.makeText(this@WalletActivity, response.body()?.message ?: "Error", Toast.LENGTH_SHORT).show()
//                        }
//                    }
//
//                    override fun onFailure(call: retrofit2.Call<ApiResponse>, t: Throwable) {
//                        Toast.makeText(this@WalletActivity, "Failed: ${t.message}", Toast.LENGTH_SHORT).show()
//                    }
//                })
//
//
//
//
//            }
//            else -> {
//                Toast.makeText(this, "Invalid Payment Gateway", Toast.LENGTH_SHORT).show()
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


}