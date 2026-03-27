package com.gmwapp.hima

import com.gmwapp.hima.utils.showAppToast

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UpiViewModel

import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AddUpiActivity : AppCompatActivity() {


    private val upiViewModel: UpiViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()

    private lateinit var ivBack: ImageView
    private lateinit var etUpiId: TextInputEditText
    private lateinit var btnVerify: MaterialButton
    private lateinit var llProgress: LinearLayout
    private lateinit var llStatus: LinearLayout
    private lateinit var ivStatus: ImageView
    private lateinit var tvStatus: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_upi)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        bindViews()
        setupListeners()
        observeViewModels()
    }

    private fun bindViews() {
        ivBack = findViewById(R.id.iv_back)
        etUpiId = findViewById(R.id.et_upi_id)
        btnVerify = findViewById(R.id.btn_verify)
        llProgress = findViewById(R.id.ll_progress)
        llStatus = findViewById(R.id.ll_status)
        ivStatus = findViewById(R.id.iv_status)
        tvStatus = findViewById(R.id.tv_status)

        llProgress.visibility = View.GONE
        llStatus.visibility = View.GONE
        btnVerify.isEnabled = false
        btnVerify.alpha = 0.5f
    }

    private fun setupListeners() {
        ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
            profileViewModel.getUsers(it)
        }

        etUpiId.addTextChangedListener(SimpleTextWatcher {
            val upi = etUpiId.text?.toString()?.trim().orEmpty()
            val isValid = isValidUpiId(upi)
            btnVerify.isEnabled = isValid
            // Update button appearance for enabled/disabled state
            if (isValid) {
                btnVerify.alpha = 1.0f
                btnVerify.setBackgroundResource(R.drawable.gradient_pink)
            } else {
                btnVerify.alpha = 0.5f
                btnVerify.setBackgroundResource(R.drawable.gradient_pink)
            }
            if (llStatus.visibility == View.VISIBLE) llStatus.visibility = View.GONE
        })

        btnVerify.setOnClickListener {
            closeKeyboard()

            val upiId = etUpiId.text?.toString()?.trim().orEmpty()
            if (!isValidUpiId(upiId)) {
                showAppToast("Invalid UPI ID", Toast.LENGTH_SHORT)
                return@setOnClickListener
            }

            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            if (userId == null) {
                showAppToast("User not found", Toast.LENGTH_SHORT)
                return@setOnClickListener
            }

            btnVerify.isEnabled = false
            btnVerify.alpha = 0.5f
            llProgress.visibility = View.VISIBLE
            upiViewModel.updatedUpi(userId, upiId)
        }
    }

    private fun observeViewModels() {
        // Observe verify result
        upiViewModel.upiResponseLiveData.observe(this) { response ->
            llProgress.visibility = View.GONE

            if (response?.success == true) {
                // refresh user data like in WithdrawActivity
                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                    profileViewModel.getUsers(it)
                }

                showStatus(success = true, message = response.message ?: "UPI verified")
                // optional: finish this screen after success
                // finish()

                showAppToast("${response.message}", Toast.LENGTH_SHORT)

                // also clear field
                etUpiId.setText("")
                finish()
            } else {
                showStatus(success = false, message = response?.message ?: "Invalid UPI ID")
                btnVerify.isEnabled = true
                btnVerify.alpha = 1.0f
            }
        }

        // Keep prefs in sync
        profileViewModel.getUserLiveData.observe(this) { res ->
            res?.data?.let { BaseApplication.getInstance()?.getPrefs()?.setUserData(it) }
            etUpiId.setText("${res.data?.upi_id}")

        }
    }

    private fun showStatus(success: Boolean, message: String) {
        llStatus.visibility = View.VISIBLE
        if (success) {
            ivStatus.visibility = View.VISIBLE
            ivStatus.setImageResource(R.drawable.tick_circle_svg)
        } else {
            ivStatus.visibility = View.GONE   // hide when not success
        }
        tvStatus.text = message
    }


    private fun isValidUpiId(upiId: String): Boolean {
        val upiPattern = "^[a-zA-Z0-9.\\-_]+@[a-zA-Z]+$"
        return upiId.matches(Regex(upiPattern))
    }

    private fun closeKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { v -> imm.hideSoftInputFromWindow(v.windowToken, 0) }
    }
}



class SimpleTextWatcher(private val onChanged: (String) -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        onChanged.invoke(s?.toString() ?: "")
    }
    override fun afterTextChanged(s: Editable?) {}
}