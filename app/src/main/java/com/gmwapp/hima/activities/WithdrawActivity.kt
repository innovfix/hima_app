package com.gmwapp.hima.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.animation.addListener
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.UpiListAdapter
import com.gmwapp.hima.databinding.ActivityWithdrawBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UpiViewModel
import com.gmwapp.hima.viewmodels.WithdrawViewModel
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class WithdrawActivity : BaseActivity() {

    lateinit var binding: ActivityWithdrawBinding

    val profileViewModel: ProfileViewModel by viewModels()
    val withdrawViewModel: WithdrawViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()

    val upiViewModel: UpiViewModel by viewModels()

    var bankDetails: Boolean = false
    var upiid: Boolean = false
    var minWithdrawAmount :Int?= null
    private val accountViewModel: AccountViewModel by viewModels()


    var payment_method = ""



    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWithdrawBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.main.setOnApplyWindowInsetsListener { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBarsInsets.top, bottom = systemBarsInsets.bottom)
            insets
        }
        initUI()
    }

    private fun initUI() {

        panVerification()
        accountViewModel.getSettings()

        accountViewModel.settingsLiveData.observe(this, Observer { response ->
            if (response?.success == true) {
                response.data?.let { settingsList ->
                    Log.d("settinglist","$settingsList")
                    if (settingsList.isNotEmpty()) {
                        val settingsData = settingsList[0]
                        settingsData.minimum_withdrawals?.let {
                            binding.tvMinimumAmount.setText("Minimum withdrawal : Rs $it")
                            minWithdrawAmount= it
                        }
                    }
                }
            }
        })


        binding.ivBack.setOnClickListener{
            onBackPressed()
        }


        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateFields()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etAmount.addTextChangedListener(textWatcher)
        binding.etUpiId.addTextChangedListener(textWatcher)


        binding.tvVerify.setOnSingleClickListener {

            closeKeyboard()
            val upiId = binding.etUpiId.text.toString()
            if (isValidUpiId(upiId)) {

                val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
                if (userId != null) {
                    upiViewModel.updatedUpi(userId, upiId)
                }

                binding.ivAddUpi.setBackgroundResource(R.drawable.tick_svg)
                binding.ivAddUpi.rotation = 0f
            } else {
                // Set a different background drawable for invalid UPI ID
                Toast.makeText(this, "Invalid UPI ID", Toast.LENGTH_SHORT).show()
            }




        }



        val upiDetailsLayout = findViewById<LinearLayout>(R.id.ll_upi_details)
        val addUpiImage = findViewById<ImageView>(R.id.iv_add_upi)
        val rvUpiTypes = findViewById<RecyclerView>(R.id.rv_upi_types)
        val etUpiId = findViewById<EditText>(R.id.et_upi_id)

         payment_method = intent.getStringExtra("payment_method").toString()
         val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()


        binding.tvCurrentBalance.text = "₹" + userData?.balance.toString()

        val upi = userData?.upi_id.toString()


        if (payment_method == "upi_transfer") {
            if (userData?.upi_id.isNullOrEmpty()) {
                upiid = false
                binding.cvPreferredPaymentMethod.visibility = View.GONE
                binding.tvUpi.text = userData?.upi_id
            }
            else {
                upiid = true
                binding.cvPreferredPaymentMethod.visibility = View.VISIBLE
                binding.tvUpi.text = userData?.upi_id

            }
            binding.cvAddUpi.visibility = View.VISIBLE
            binding.cvAddBank.visibility = View.GONE
        }
        else if (payment_method == "bank_transfer"){
            binding.cvAddUpi.visibility = View.GONE
            binding.cvAddBank.visibility = View.VISIBLE
        }


        if (userData?.bank.isNullOrEmpty()) {
            bankDetails = false
            binding.ivAddBank.setBackgroundResource(R.drawable.ic_add_upi) // Replace with your valid drawable resource

            // Rotate the drawable by a specified angle (e.g., 45 degrees)
            binding.ivAddBank.rotation = 0f // This rotates the ImageView by 45 degrees
        }
        else {
            bankDetails = true
            binding.ivAddBank.setBackgroundResource(R.drawable.tick_circle_svg) // Replace with your valid drawable resource
            // Rotate the drawable by a specified angle (e.g., 45 degrees)
            binding.ivAddBank.rotation = 0f // This rotates the ImageView by 45 degrees

        }





        val textList = listOf("@ybl", "@sbi", "@okicici", "@okaxis")

        var isExpanded = false

        addUpiImage.setOnClickListener {
            isExpanded = !isExpanded
            if (isExpanded) {
                expandView(upiDetailsLayout, rvUpiTypes)
                rotateImage(addUpiImage, 0f, 45f)
            } else {
                collapseView(upiDetailsLayout)
                rotateImage(addUpiImage, 45f, 0f)
            }
        }

        // Setup RecyclerView
        val upiAdapter = UpiListAdapter(textList) { selectedText ->
            val baseText = etUpiId.text.toString().substringBefore("@")
            etUpiId.setText("$baseText$selectedText")
        }
        rvUpiTypes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvUpiTypes.adapter = upiAdapter



        binding.cvAddBank.setOnSingleClickListener {
            val intent = Intent(this, BankUpdateActivity::class.java)
            startActivity(intent)

        }

        binding.cvPanDetails.setOnSingleClickListener {
            val intent = Intent(this, KycActivity::class.java)
            startActivity(intent)

        }

        binding.btnWithdraw.setOnClickListener {
            val amount = binding.etAmount.text.toString().toInt()
            val paymentMethod = payment_method

            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            if (userId != null) {
                withdrawViewModel.addWithdrawal(userId, amount, paymentMethod)
            }
        }

        withdrawViewModel.withdrawResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
                val intent = Intent(this, PaymentInitiatedActivity::class.java)
                startActivity(intent)
                finish()
            }
            else {
                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
            }
        })

        upiViewModel.upiResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                    profileViewModel.getUsers(it)
                }
                binding.etUpiId.setText("")
            }
            else {
                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
            }
        })

        profileViewModel.getUserLiveData.observe(this, Observer {

            it.data?.let { it1 ->
                BaseApplication.getInstance()?.getPrefs()?.setUserData(it1)
            }

            if (userData?.upi_id.isNullOrEmpty()) {
                upiid = false
                binding.cvPreferredPaymentMethod.visibility = View.GONE
                binding.tvUpi.text = it.data?.upi_id
            }
            else {
                upiid = true
                binding.cvPreferredPaymentMethod.visibility = View.VISIBLE
                binding.tvUpi.text = it.data?.upi_id
            }

        })



    }

    private fun rotateImage(view: ImageView, fromAngle: Float, toAngle: Float) {
        ObjectAnimator.ofFloat(view, "rotation", fromAngle, toAngle).apply {
            duration = 300
            start()
        }
    }

    private fun expandView(view: View, upiList: RecyclerView) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val targetHeight = view.measuredHeight

        view.layoutParams.height = 0
        view.visibility = View.VISIBLE
        upiList.visibility = View.VISIBLE

        ValueAnimator.ofInt(0, targetHeight).apply {
            addUpdateListener {
                view.layoutParams.height = it.animatedValue as Int
                view.requestLayout()
            }
            duration = 300
            start()
        }
    }

    private fun collapseView(view: View) {
        val initialHeight = view.measuredHeight
        ValueAnimator.ofInt(initialHeight, 0).apply {
            addUpdateListener {
                view.layoutParams.height = it.animatedValue as Int
                view.requestLayout()
            }
            addListener(onEnd = { view.visibility = View.GONE })
            duration = 300
            start()
        }
    }

    private fun isValidUpiId(upiId: String): Boolean {
        val upiPattern = "^[a-zA-Z0-9.\\-_]+@[a-zA-Z]+$"
        return upiId.matches(Regex(upiPattern))
    }


    private fun closeKeyboard() {
        val inputMethodManager = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocusedView = this.currentFocus
        if (currentFocusedView != null) {
            inputMethodManager.hideSoftInputFromWindow(currentFocusedView.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }

    private fun validateFields() {
        val amount = binding.etAmount.text.toString().trim()
        val upiId = binding.etUpiId.text.toString().trim()

        // Initially disable the button
        binding.btnWithdraw.isEnabled = false

        // Check if amount is empty or not a valid number
        if (amount.isEmpty() || !isValidAmount(amount)) {
            // Optionally, show a message or highlight the field
            binding.etAmount.error = "Min withdrwal amount Rs.$minWithdrawAmount"
        }


        if (payment_method == "upi_transfer") {
            if (isValidUpiId(upiId)) {
                binding.ivAddUpi.setBackgroundResource(R.drawable.tick_svg)
                binding.ivAddUpi.rotation = 0f
            }



            minWithdrawAmount?.let { min ->
                if (
                    amount.isNotEmpty() &&
                    isValidAmount(amount) &&
                    amount.toDouble() >= min &&
                    upiid
                ) {
                    binding.btnWithdraw.isEnabled = true
                }
            }

        }
        else if (payment_method == "bank_transfer") {
//            if (amount.isNotEmpty() && isValidAmount(amount)  && amount.toDouble() >= minWithdrawAmount && bankDetails) {
//                binding.btnWithdraw.isEnabled = true
//            }


            minWithdrawAmount?.let { min ->
                if (
                    amount.isNotEmpty() &&
                    isValidAmount(amount) &&
                    amount.toDouble() >= min &&
                    bankDetails
                ) {
                    binding.btnWithdraw.isEnabled = true
                }
            }


        }

    }

    // Helper function to check if amount is a valid number
    private fun isValidAmount(amount: String): Boolean {
        return try {
            amount.toDouble() // Attempt to convert the string to a double
            true
        } catch (e: NumberFormatException) {
            false // If conversion fails, return false
        }
    }

    fun panVerification(){
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        userData?.let { loginViewModel.login(it.mobile) }
        loginViewModel.loginResponseLiveData.observe(this, Observer {

            if (it.success) {
                if (!it.data?.pancard_name.isNullOrEmpty()&& !it.data?.pancard_number.isNullOrEmpty()){
                    binding.ivAddPan.setBackgroundResource(R.drawable.tick_circle_svg) // Replace with your valid drawable resource
                    // Rotate the drawable by a specified angle (e.g., 45 degrees)
                    binding.ivAddPan.rotation = 0f // This rotates the ImageView by 45 degrees
                }

            }
        })
    }

    override fun onResume() {
        super.onResume()
        panVerification()
    }


}