package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.AddUpiActivity
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.UpiListAdapter
import com.gmwapp.hima.databinding.ActivityWithdrawBinding
import com.gmwapp.hima.retrofit.responses.TransactionCharge
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.TransactionChargesViewModel
import com.gmwapp.hima.viewmodels.UPIWithdrawViewModel
import com.gmwapp.hima.viewmodels.UpiViewModel
import com.gmwapp.hima.viewmodels.WithdrawViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.ceil


@AndroidEntryPoint
class WithdrawActivity : BaseActivity() {

    lateinit var binding: ActivityWithdrawBinding

    val profileViewModel: ProfileViewModel by viewModels()
    val withdrawViewModel: WithdrawViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()

    val upiViewModel: UpiViewModel by viewModels()
    val upiWithdrawViewModel: UPIWithdrawViewModel by viewModels()

    val transactionChargesViewModel: TransactionChargesViewModel by viewModels()

    var bankDetails: Boolean = false
    var upiid: Boolean = false
    var minWithdrawAmount :Int?= null
    private val accountViewModel: AccountViewModel by viewModels()

    var isPanVerifiend = false

    var payment_method = ""

    var tdsPercent: Double = 1.0 // default fallback



    private var chargesList: List<TransactionCharge> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWithdrawBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Defense-in-depth: agency creators are paid by their agency and cannot withdraw.
        // The Withdraw button is hidden in EarningsActivity and the server rejects the
        // request anyway; this stops any deep-link/back-stack route into the form.
        if (BaseApplication.getInstance()?.getPrefs()?.getUserData()?.withdrawal_blocked == true) {
            Toast.makeText(this, getString(R.string.withdrawal_agency_blocked_text), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
    }

    /**
     * Shows a withdrawal-response message in a dismissible dialog instead of a toast,
     * so long messages (e.g. the agency-block notice) are never truncated. Same look
     * as the autopay-failed dialog; single "Got it" button.
     */
    private fun showWithdrawMessageDialog(message: String?) {
        if (isFinishing || isDestroyed) return
        val msg = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.withdrawal_agency_blocked_text)
        val view = layoutInflater.inflate(R.layout.dialog_withdrawal_blocked, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.5f)
        view.findViewById<android.widget.TextView>(R.id.tvWithdrawBlockedMsg).text = msg
        view.findViewById<android.view.View>(R.id.btnWithdrawGotIt).setOnClickListener { dialog.dismiss() }
        dialog.show()
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

        transactionChargesViewModel.getTransactionCharges()

        transactionChargesViewModel.chargesResponseLiveData.observe(this) { response ->
            // Handle charges list here
            if (response != null) {
                Log.d("API_DATA", response.data.toString())
                chargesList = response.data ?: emptyList()
                tdsPercent = response.tds_percentage

                val tdsPercentInt = tdsPercent.toInt()
                binding.tvTdsLabel.text = "TDS deduction ($tdsPercentInt%)"

            }
        }

        transactionChargesViewModel.chargesErrorLiveData.observe(this) { error ->
            // Handle errors
            showAppToast(error, Toast.LENGTH_SHORT)
        }

        binding.ivTxInfo.setOnClickListener {
            if (chargesList.isNotEmpty()) {
                showTransactionChargesDialog(chargesList)
            } else {
                showAppToast("Data not available", Toast.LENGTH_SHORT)
            }


        }

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
                    binding.llProgressBar.visibility = View.VISIBLE

                }

                binding.ivAddUpi.setBackgroundResource(R.drawable.tick_svg)
                binding.ivAddUpi.rotation = 0f
            } else {
                // Set a different background drawable for invalid UPI ID
                showAppToast("Invalid UPI ID", Toast.LENGTH_SHORT)
            }




        }



        val upiDetailsLayout = findViewById<LinearLayout>(R.id.ll_upi_details)
        val addUpiImage = findViewById<CardView>(R.id.cv_add_upi)
        val rvUpiTypes = findViewById<RecyclerView>(R.id.rv_upi_types)
        val etUpiId = findViewById<EditText>(R.id.et_upi_id)

         payment_method = intent.getStringExtra("payment_method").orEmpty()
         val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()


        binding.tvCurrentBalance.text = "₹" + userData?.balance.toString()

        val upi = userData?.upi_id.toString()


        if (payment_method == "upi_transfer") {
            if (userData?.upi_id.isNullOrEmpty()) {
                upiid = false
                binding.cvPreferredPaymentMethod.visibility = View.GONE
                binding.tvUpi.text = userData?.upi_id
                binding.llTransactionfee.visibility = View.VISIBLE

            }
            else {
                upiid = true
                binding.cvPreferredPaymentMethod.visibility = View.GONE
                binding.tvUpi.text = userData?.upi_id
                binding.llTransactionfee.visibility = View.VISIBLE


            }
            binding.cvAddBank.visibility = View.GONE
        }
        else if (payment_method == "bank_transfer"){
            binding.cvAddUpi.visibility = View.GONE
            binding.cvAddBank.visibility = View.VISIBLE
            // Show the same Withdrawal Summary card (transaction fee + TDS + amount
            // you'll receive) that the UPI flow shows. validateFields() already
            // computes the slab-based fee + TDS regardless of payment method, it
            // just gates the calculation on this card being visible.
            binding.llTransactionfee.visibility = View.VISIBLE
        }


        if (userData?.holder_name.isNullOrEmpty()) {
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
            if (isPanVerifiend) {
            val intent = Intent(this, AddUpiActivity::class.java)
            startActivity(intent)

//            isExpanded = !isExpanded
//            if (isExpanded) {
//                expandView(upiDetailsLayout, rvUpiTypes)
//                rotateImage(addUpiImage, 0f, 45f)
//            } else {
//                collapseView(upiDetailsLayout)
//                rotateImage(addUpiImage, 45f, 0f)
//            }
        }else{
                showAppToast("Please complete kyc", Toast.LENGTH_SHORT)
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
            if (isPanVerifiend) {
                val intent = Intent(this, BankUpdateActivity::class.java)
                startActivity(intent)
            }else{
                showAppToast("Please complete kyc", Toast.LENGTH_SHORT)
            }
        }

        binding.cvPanDetails.setOnSingleClickListener {
            val intent = Intent(this, KycActivity::class.java)
            startActivity(intent)

        }

        binding.btnWithdraw.setOnClickListener {

            if (!isNetworkAvailable(this)) {
                showAppToast("No Internet connection. Please try again.", Toast.LENGTH_SHORT)
                return@setOnClickListener  //
            }

            val rawAmount = binding.etAmount.text?.toString()?.trim().orEmpty()
            val amount = rawAmount.toDoubleOrNull()?.toInt()
            if (amount == null || amount <= 0) {
                showAppToast("Enter a valid amount", Toast.LENGTH_SHORT)
                return@setOnClickListener
            }
            val paymentMethod = payment_method.orEmpty().trim()

            Log.d("paymentMethod","$paymentMethod")
            if (paymentMethod.equals("upi_transfer", ignoreCase = true)){


                val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
                if (userId != null) {
                    upiWithdrawViewModel.addWithdrawal(userId, amount, paymentMethod)
                    Log.d("paymentMethod","upi")

                }

            }else {

                val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
                if (userId != null) {
                    withdrawViewModel.addWithdrawal(userId, amount, paymentMethod)
                    Log.d("paymentMethod","bank")

                }
            }
        }

        withdrawViewModel.withdrawResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                Log.d("paymentMethod","bankResposne")

                Log.d("paymentMethod","$it")

                showAppToast(it.message, Toast.LENGTH_SHORT)
                val intent = Intent(this, PaymentInitiatedActivity::class.java)
                startActivity(intent)
                finish()
            }
            else {
                // Failure (incl. the agency-block message) — show in a dialog so the
                // full text is readable instead of a truncated toast.
                showWithdrawMessageDialog(it?.message)
            }
        })

        upiWithdrawViewModel.upiWithdrawResponseLiveData.observe(this, Observer {
            if (it != null) {
                Log.d("paymentMethod","UpiResposne $it")

                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                userData?.let { u -> profileViewModel.getUsers(u.id) }
                if (it.success == true) {
                    showAppToast(it.message, Toast.LENGTH_SHORT)
                    finish()
                } else {
                    // Failure (incl. the agency-block message) — dialog, not a truncated toast.
                    showWithdrawMessageDialog(it.message)
                }
            }
            else {
                showAppToast("Please try again", Toast.LENGTH_SHORT)
            }
        })



//        upiViewModel.upiResponseLiveData.observe(this, Observer {
//            if (it != null && it.success) {
//                showAppToast(it.message, Toast.LENGTH_SHORT)
//                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
//                    profileViewModel.getUsers(it)
//                }
//                binding.etUpiId.setText("")
//            }
//            else {
//                showAppToast(it.message, Toast.LENGTH_SHORT)
//            }
//        })

        upiViewModel.upiResponseLiveData.observe(this, Observer { response ->
            binding.llProgressBar.visibility = View.GONE

            if (response != null) {
                if (response.success) {
                    showAppToast(response.message ?: "Success", Toast.LENGTH_SHORT)
                    BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                        profileViewModel.getUsers(it)
                    }
                    binding.etUpiId.setText("")
                } else {
                    showAppToast(response.message ?: "Something went wrong", Toast.LENGTH_SHORT)
                }
            } else {
                showAppToast("Invalid Upi Id", Toast.LENGTH_SHORT)
            }
        })


        profileViewModel.getUserLiveData.observe(this, Observer {

            it?.data?.let { it1 ->
                BaseApplication.getInstance()?.getPrefs()?.setUserData(it1)
            }

            binding.tvCurrentBalance.text = "₹" + it?.data?.balance.toString()


            Log.d("UserUpiID","${it.data?.upi_id}")
            if (it.data?.upi_id.isNullOrEmpty()) {
                upiid = false
                binding.cvPreferredPaymentMethod.visibility = View.GONE
                binding.tvUpi.text = it.data?.upi_id
                Log.d("UserUpiID1","${it.data?.upi_id}")

            }
            else {

                upiid = true
                binding.cvPreferredPaymentMethod.visibility = View.GONE
                binding.tvUpi.text = it.data?.upi_id
                binding.ivAddUpi.setBackgroundResource(R.drawable.tick_svg)

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
        var isInRange = false


        // Initially disable the button
        binding.btnWithdraw.isEnabled = false

        // Check if amount is empty or not a valid number
        if (amount.isEmpty() || !isValidAmount(amount)) {
            // Optionally, show a message or highlight the field
         //   binding.etAmount.error = "Min withdrwal amount Rs.$minWithdrawAmount"
        }

        val amountStr = amount.trim()

        if (amountStr.isEmpty() || !isValidAmount(amountStr)) {
            // reset displays
            binding.tvTotalAmount?.text = "= ₹0"
            binding.tvTxFeeAmount?.text = "= ₹0"
            binding.tvTdsAmount?.text = "= ₹0"
            binding.tvAmountReceive?.text = "= ₹0"
            return
        }

// Safe parse
        val amountVal = amountStr.toDoubleOrNull()
        if (amountVal == null) {
            // reset again just in case
            binding.tvTotalAmount?.text = "= ₹0"
            binding.tvTxFeeAmount?.text = "= ₹0"
            binding.tvTdsAmount?.text = "= ₹0"
            binding.tvAmountReceive?.text = "= ₹0"
            return
        }

// ---------- Transaction Fee from API slabs ----------
        var txFee = 0.0
        if (binding.llTransactionfee.visibility == View.VISIBLE) {
            for (charge in chargesList) {
                val min = charge.min_amount ?: 0
                val max = charge.max_amount ?: Int.MAX_VALUE
                if (amountVal >= min && amountVal <= max) {
                    txFee = (charge.deduction_charge ?: 0).toDouble()
                    isInRange = true   // ✅ new
                    break
                }
            }
        }
        if (binding.llTransactionfee.visibility == View.VISIBLE) {
            if (!isInRange) {
                binding.tvTotalAmount?.text = "= ₹0"
                binding.tvTxFeeAmount?.text = "= ₹0"
                binding.tvTdsAmount?.text = "= ₹0"
                binding.tvAmountReceive?.text = "= ₹0"
                binding.btnWithdraw.isEnabled = true

                return   // ✅ stops further TDS calculation
            }
        }
// ---------- TDS: 1% rounded UP to next rupee (so 0.10 -> 1) ----------
        val rawTds = amountVal * (tdsPercent / 100.0)
        val tds = String.format("%.2f", rawTds).toDouble()  // keep 2 decimal places


// ---------- Final amounts ----------
        val totalDeduction = txFee + tds
        val receivable = (amountVal - totalDeduction).coerceAtLeast(0.0)

// ---------- Update UI ----------
        binding.tvTotalAmount?.text = "= ${formatAmount(amountVal)}"
        binding.tvTxFeeAmount?.text = "= ${formatAmount(txFee)}"
        binding.tvTdsAmount?.text = "= ${formatAmount(tds)}"
        binding.tvAmountReceive?.text = "= ${formatAmount(receivable)}"







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

                if (
                    amount.isNotEmpty() &&
                    isValidAmount(amount) &&
                    amount.toDouble() < min &&
                    bankDetails
                ) {
                    binding.tvTotalAmount?.text = "= ₹0"
                    binding.tvTxFeeAmount?.text = "= ₹0"
                    binding.tvTdsAmount?.text = "= ₹0"
                    binding.tvAmountReceive?.text = "= ₹0"
                    binding.btnWithdraw.isEnabled = true

                }
            }




        }

    }

    private fun formatAmount(value: Double): String {
        return if (value % 1.0 == 0.0) "₹${value.toInt()}" else String.format("₹%.2f", value)
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

        userData?.let { loginViewModel.login(it.mobile,"0","0") }
        userData?.let { profileViewModel.getUsers(it.id) }

        loginViewModel.loginResponseLiveData.observe(this, Observer {

            if (it.success) {
                if (!it.data?.pancard_name.isNullOrEmpty()&& !it.data?.pancard_number.isNullOrEmpty()){
                    binding.ivAddPan.setBackgroundResource(R.drawable.tick_circle_svg) // Replace with your valid drawable resource
                    // Rotate the drawable by a specified angle (e.g., 45 degrees)
                    binding.ivAddPan.rotation = 0f // This rotates the ImageView by 45 degrees
                    isPanVerifiend = true
                    binding.kycLL.visibility= View.GONE
                    if (payment_method == "upi_transfer") {
                        binding.cvAddUpi.visibility = View.VISIBLE
                    }
                }else{
                    binding.kycLL.visibility= View.VISIBLE
                    if (payment_method == "upi_transfer"){
                        binding.cvAddUpi.visibility= View.VISIBLE

                    }


                }
            }

            profileViewModel.getUserLiveData.observe(this, Observer {
                BaseApplication.getInstance()?.getPrefs()?.setUserData(it?.data)

                if (it?.data?.holder_name.isNullOrEmpty()) {
                    bankDetails = false
                    binding.ivAddBank.setBackgroundResource(R.drawable.ic_add_upi) // Replace with your valid drawable resource

                    Log.d("HolderName","${it?.data?.holder_name}")
                    // Rotate the drawable by a specified angle (e.g., 45 degrees)
                    binding.ivAddBank.rotation = 0f // This rotates the ImageView by 45 degrees
                }
                else {
                    bankDetails = true
                    Log.d("HolderName","${it?.data?.holder_name}")

                    binding.ivAddBank.setBackgroundResource(R.drawable.tick_circle_svg) // Replace with your valid drawable resource
                    // Rotate the drawable by a specified angle (e.g., 45 degrees)
                    binding.ivAddBank.rotation = 0f // This rotates the ImageView by 45 degrees

                }
                validateFields()


            })
        })
    }

    override fun onResume() {
        super.onResume()
        panVerification()
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.let { profileViewModel.getUsers(it.id) }
    }

    private fun showTransactionChargesDialog(charges: List<TransactionCharge>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tx_fee_info, null)
        val table = dialogView.findViewById<LinearLayout>(R.id.table_container)
        val ivClose = dialogView.findViewById<ImageView>(R.id.iv_close)


        fun addHLine() {
            val line = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1.dp
                )
                setBackgroundResource(R.drawable.divider_horizontal)
            }
            table.addView(line)
        }

        // Header
        val header = layoutInflater.inflate(R.layout.row_table_linear, table, false) as LinearLayout
        header.findViewById<TextView>(R.id.col1).apply {
            text = "From"; setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.findViewById<TextView>(R.id.col2).apply {
            text = "To"; setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.findViewById<TextView>(R.id.col3).apply {
            text = "Charges"; setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        table.addView(header)
        addHLine() // line under header

        // Rows
        charges.forEachIndexed { index, c ->
            val row = layoutInflater.inflate(R.layout.row_table_linear, table, false) as LinearLayout
            row.findViewById<TextView>(R.id.col1).text = "₹${c.min_amount ?: 0}"
            row.findViewById<TextView>(R.id.col2).text = c.max_amount?.let { "₹$it" } ?: "-"
            row.findViewById<TextView>(R.id.col3).text = "₹${c.deduction_charge ?: 0}"
            table.addView(row)

            // horizontal line after every row except the last (we'll add a final bottom line)
            if (index != charges.lastIndex) addHLine()
        }

        // Final bottom line to close the grid inside the frame
        addHLine()

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        dialog.show()
        ivClose.setOnClickListener { dialog.dismiss() }

    }

    // tiny dp helper
    private val Int.dp: Int get() =
        (this * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun makeCell(text: String, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            setPadding(12, 8, 12, 8)
            textSize = 14f
            gravity = Gravity.CENTER   // ✅ center text inside the cell

            setTextColor(ContextCompat.getColor(this@WithdrawActivity, R.color.black_light))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                activeNetwork.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }




}