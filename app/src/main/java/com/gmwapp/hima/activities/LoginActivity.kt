package com.gmwapp.hima.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityLoginBinding
import com.gmwapp.hima.dialogs.BottomSheetCountry
import com.gmwapp.hima.retrofit.responses.Country
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlin.random.Random


@AndroidEntryPoint
class LoginActivity : BaseActivity(), OnItemSelectionListener<Country> {
    lateinit var binding: ActivityLoginBinding
    var mobile: String? = null
    var otp:Int?= null;
    private val loginViewModel: LoginViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
    }

    override fun onItemSelected(country: Country) {
        binding.ivFlag.setImageResource(country.image)
        binding.tvCountryCode.text = country.code
    }

    private fun initUI() {
        binding.cvLogin.setBackgroundResource(R.drawable.card_view_border)

        binding.btnSendOtp.setOnSingleClickListener {
            binding.btnSendOtp.isEnabled = false

            // Retrieve mobile number
            val mobile = binding.etMobileNumber.text.toString()
            val countryCode = binding.tvCountryCode.text.toString().toInt()

            // Regex for validating 10-digit mobile numbers starting with 6-9
            val mobileRegex = Regex("^[6-9]\\d{9}$")

            // Validation logic
            if (TextUtils.isEmpty(mobile) || !mobile.matches(mobileRegex)) {
                // Invalid mobile number case
                binding.tvOtpText.text = getString(R.string.invalid_phone_number_text)
                binding.tvOtpText.setTextColor(getColor(R.color.white))
             //   binding.cvLogin.setBackgroundResource(R.drawable.card_view_border_error)
                showSnackbar("Enter a valid 10-digit mobile number")
                binding.btnSendOtp.isEnabled = true
            } else {
                // Valid mobile number case
                val r = Random(System.currentTimeMillis())
                otp = r.nextInt(100000, 999999)

                // Send OTP
                sendOTP(mobile, countryCode)
            }
        }

        // Country selection removed - India flag is now fixed
        binding.etMobileNumber.setOnTouchListener { v, _ ->
            binding.cvLogin.setBackgroundResource(R.drawable.card_view_border_active)
            false
        }
        binding.cbTermsAndConditions.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
              //  binding.btnSendOtp.setBackgroundResource(R.drawable.d_button_bg_white)
                binding.btnSendOtp.isEnabled = true
            } else {
//binding.btnSendOtp.setBackgroundResource(R.drawable.d_button_bg)
                binding.btnSendOtp.isEnabled = false
            }
        }
        binding.etMobileNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                window.statusBarColor = resources.getColor(R.color.dark_blue)
                if (!binding.cbTermsAndConditions.isChecked || TextUtils.isEmpty(s)) {
               //     binding.btnSendOtp.setBackgroundResource(R.drawable.d_button_bg)
                    binding.btnSendOtp.isEnabled = false
                } else {
              //      binding.btnSendOtp.setBackgroundResource(R.drawable.d_button_bg_white)
                    binding.btnSendOtp.isEnabled = true
                }
            }

            override fun afterTextChanged(s: Editable) {
            }
        })
        loginViewModel.sendOTPResponseLiveData.observe(this, Observer {
            binding.pbSendOtpLoader.visibility = View.GONE
            binding.btnSendOtp.setText(getString(R.string.send_otp))
            binding.btnSendOtp.isEnabled = true
            if (it!=null && it.success) {
                val intent = Intent(this, VerifyOTPActivity::class.java)
                intent.putExtra(DConstants.MOBILE_NUMBER, mobile)
                intent.putExtra(DConstants.COUNTRY_CODE, binding.tvCountryCode.text.toString().toInt())
                intent.putExtra(DConstants.OTP, otp)
                startActivity(intent)
            } else {
                binding.tvOtpText.text = it?.message
                binding.tvOtpText.setTextColor(getColor(R.color.error))
               // binding.cvLogin.setBackgroundResource(R.drawable.card_view_border_error)
            }
        })
        loginViewModel.sendOTPErrorLiveData.observe(this, Observer {
            binding.pbSendOtpLoader.visibility = View.GONE
            binding.btnSendOtp.setText(getString(R.string.send_otp))
            binding.btnSendOtp.isEnabled = true
            binding.tvOtpText.text = getString(R.string.please_try_again_later)
            binding.tvOtpText.setTextColor(getColor(R.color.error))
           // binding.cvLogin.setBackgroundResource(R.drawable.card_view_border_error)
        })

        setMessageWithClickableLink()
    }

    private fun sendOTP(mobile: String, countryCode:Int) {
        this.mobile = mobile
        otp?.let {
            binding.pbSendOtpLoader.visibility = View.VISIBLE
            binding.btnSendOtp.setText("")
            loginViewModel.sendOTP(mobile, countryCode, it)
        }
    }

    private fun setMessageWithClickableLink() {
        val content = getString(R.string.terms_and_conditions_text, getString(R.string.app_name))
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(textView: View) {
                val intent = Intent(this@LoginActivity, WebviewActivity::class.java)
                startActivity(intent)
            }

            override fun updateDrawState(textPaint: TextPaint) {
                super.updateDrawState(textPaint)
                textPaint.color = getColor(R.color.yellow)
                textPaint.isUnderlineText = false
            }
        }
        val startIndex = content.indexOf("terms & conditions")
        val endIndex = startIndex + "terms & conditions".length
        val spannableString = SpannableString(content)
        spannableString.setSpan(
            clickableSpan,
            startIndex,
            endIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvTermsAndConditions.text = spannableString
        binding.tvTermsAndConditions.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

}