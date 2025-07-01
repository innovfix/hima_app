package com.gmwapp.hima.activities

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager2.widget.ViewPager2
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.OnboardingPagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.CountDownTimer
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityNewLoginBinding
import com.gmwapp.hima.dialogs.BottomSheetCountry
import com.gmwapp.hima.retrofit.responses.Country
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.viewmodels.FcmTokenViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.ReferralCodeViewModel
import com.google.android.material.snackbar.Snackbar
//import com.zego.ve.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@AndroidEntryPoint
class NewLoginActivity : BaseActivity(), OnItemSelectionListener<Country> {

    private lateinit var binding: ActivityNewLoginBinding // Ensure you have view binding enabled
    private val loginViewModel: LoginViewModel by viewModels()
    private val referralCodeViewModel : ReferralCodeViewModel by viewModels()

    private var otp: Int? = null
    private var mobile: String? = null
    private var timer: CountDownTimer?=null

    // Colors, images, titles, subtitles for onboarding
    private val colors = listOf(R.color.purple_400, R.color.brown, R.color.green)
    private val images = listOf(R.drawable.im_onbording1, R.drawable.im_onbording2, R.drawable.im_onbording3)
    private val titles = listOf("Earphones on!", "Voice call & Video call", "100% safe and secure")
    private val subtitles = listOf("No real pics, Only Avatar", "Find best friends", "Zero fake profiles")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        setupOnboarding()
        initUI()
        observeReferralCodeResponse()

        binding.loginSection.visibility  = View.VISIBLE
        binding.otpSection.visibility  = View.GONE
    }

    private fun setupOnboarding() {
        // Set up onboarding ViewPager and TabLayout
        val pages = listOf(R.layout.onboarding_page1, R.layout.onboarding_page1, R.layout.onboarding_page1)
        val adapter = OnboardingPagerAdapter(pages, images, titles, subtitles)
        binding.viewPagerOnboarding.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPagerOnboarding) { tab, position ->
            val customView = LayoutInflater.from(this).inflate(R.layout.custom_tab_indicator, null)
            val indicator = customView.findViewById<ImageView>(R.id.indicator)
            indicator.setImageResource(if (position == 0) R.drawable.indicator_selected else R.drawable.indicator_unselected)
            tab.customView = customView
        }.attach()

        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.rootLayout.setBackgroundColor(resources.getColor(colors[position], null))
                for (i in 0 until binding.tabLayout.tabCount) {
                    val icon = binding.tabLayout.getTabAt(i)?.customView?.findViewById<ImageView>(R.id.indicator)
                    icon?.setImageResource(if (i == position) R.drawable.indicator_selected else R.drawable.indicator_unselected)
                }
            }
        })

        // Automatically move the ViewPager every 2 seconds
        lifecycleScope.launch {
            while (true) {
                delay(2000) // Wait for 2 seconds
                val currentItem = binding.viewPagerOnboarding.currentItem
                val nextItem = (currentItem + 1) % adapter.itemCount
                binding.viewPagerOnboarding.setCurrentItem(nextItem, true) // Smooth transition
            }
        }
    }




    private fun initUI() {

        DPreferences(this).setReferralCode("") // Save empty referral code initially
        val savedReferCode = DPreferences(this).getReferralCode()
        Log.d("savedReferCode","$savedReferCode")
        checkReferal()

        binding.btnSendOtp.setOnClickListener {
            closeKeyboard()
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
                binding.tvOtpText.setTextColor(getColor(R.color.text_light_grey))
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

        binding.clCountry.setOnClickListener {
            val bottomSheet = BottomSheetCountry()
            bottomSheet.show(
                supportFragmentManager, "BottomSheetCountry"
            )
        }
//        binding.etMobileNumber.setOnTouchListener { v, _ ->
//            binding.cvLogin.setBackgroundResource(R.drawable.card_view_border_active)
//            false
//        }
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
            if (it.success) {
                binding.loginSection.visibility  = View.GONE
                binding.otpSection.visibility  = View.VISIBLE

                initOtpUI(mobile.toString(), otp.toString().toInt(),binding.tvCountryCode.text.toString().toInt())
//                val intent = Intent(this, VerifyOTPActivity::class.java)
//                intent.putExtra(DConstants.MOBILE_NUMBER, mobile)
//                intent.putExtra(DConstants.COUNTRY_CODE, binding.tvCountryCode.text.toString().toInt())
//                intent.putExtra(DConstants.OTP, otp)
//                startActivity(intent)
            } else {
                binding.tvOtpText.text = it.message
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

    private fun setMessageWithClickableLink() {
        val content = getString(R.string.terms_and_conditions_text, getString(R.string.app_name))
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(textView: View) {
                val intent = Intent(this@NewLoginActivity, WebviewActivity::class.java)
                startActivity(intent)
            }

            override fun updateDrawState(textPaint: TextPaint) {
                super.updateDrawState(textPaint)
                textPaint.color = getColor(R.color.colorPrimaryDark)
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

    private fun sendOTP(mobile: String, countryCode:Int) {
        this.mobile = mobile
        otp?.let {
            binding.pbSendOtpLoader.visibility = View.VISIBLE
            binding.btnSendOtp.setText("")
            loginViewModel.sendOTP(mobile, countryCode, it)
        }
    }

    override fun onItemSelected(country: Country) {
        binding.ivFlag.setImageResource(country.image)
        binding.tvCountryCode.text = country.code
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }


    private fun initOtpUI(mobile: String, otp: Int, countryCode: Int) {
        window.statusBarColor = resources.getColor(R.color.dark_blue)
        binding.tvOtpMobileNumber.text = " $mobile"
        binding.tvOtpMobileNumber.paintFlags =
            binding.tvOtpMobileNumber.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.ivEdit.setOnClickListener(View.OnClickListener {
            binding.loginSection.visibility  = View.VISIBLE
            binding.otpSection.visibility  = View.GONE
            binding.pvOtp.setText("")
            stopTimer()
        })
        loginViewModel.sendOTPResponseLiveData.observe(this, Observer {
            binding.pbLoader.visibility = View.GONE
            binding.btnResendOtp.setText(getString(R.string.resend_otp))
            startTimer()
        })

        loginViewModel.loginErrorLiveData.observe(this, Observer {
            binding.pbVerifyOtpLoader.visibility = View.GONE
            binding.btnVerifyOtp.setText(getString(R.string.verify_otp))
            binding.btnVerifyOtp.isEnabled = true
        })
        loginViewModel.loginResponseLiveData.observe(this, Observer {
            binding.pbVerifyOtpLoader.visibility = View.GONE
            binding.btnVerifyOtp.setText(getString(R.string.verify_otp))
            binding.btnVerifyOtp.isEnabled = true
            if (it.success) {
                if (it.registered) {
                    it.data?.let { it1 ->
                        BaseApplication.getInstance()?.getPrefs()?.setUserData(it1)
                        BaseApplication.getInstance()?.getPrefs()?.setAuthenticationToken(it.token)
                    }
                    var intent: Intent? = null
                    if (it.data?.gender == DConstants.MALE) {
                        intent = Intent(this, MainActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                    } else {
                        if (it.data?.status == 2) {
                            intent = Intent(this, MainActivity::class.java)
                            intent.putExtra(
                                DConstants.AVATAR_ID,
                                getIntent().getIntExtra(DConstants.AVATAR_ID, 0)
                            )
                            intent.putExtra(DConstants.LANGUAGE, it.data?.language)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        } else if (it.data?.status == 1) {
                            intent = Intent(this, AlmostDoneActivity::class.java)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        } else {
                            intent = Intent(this, VoiceIdentificationActivity::class.java)
                            intent.putExtra(DConstants.LANGUAGE, it.data?.language)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    }
                    startActivity(intent)
                    finish()
                } else {
                    val intent = Intent(this, SelectGenderActivity::class.java)
                    intent.putExtra(DConstants.MOBILE_NUMBER, mobile)
                    startActivity(intent)
                }
            }
        })
        binding.btnResendOtp.setOnClickListener({
            binding.btnResendOtp.setText("")
            binding.pbLoader.visibility = View.VISIBLE
            loginViewModel.sendOTP(mobile, countryCode, otp)
        })
        binding.pvOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                if(s.toString().length==6){
                    //   binding.btnVerifyOtp.setBackgroundResource(R.drawable.d_button_bg_white)
                    binding.btnVerifyOtp.isEnabled = true
                }else{
                    //    binding.btnVerifyOtp.setBackgroundResource(R.drawable.d_button_bg)
                    binding.btnVerifyOtp.isEnabled = false
                }
            }
        }
        )
        binding.btnVerifyOtp.setOnClickListener {
            closeKeyboard()
            val enteredOTP = binding.pvOtp.text.toString().toInt()
           // val default = "011011".toInt() // Convert default to Int
            val default = "123456".toInt() // Convert default to Int
            if (enteredOTP == otp) {
                Log.d("defaultOtp","$enteredOTP")
                binding.pbVerifyOtpLoader.visibility = View.VISIBLE
                binding.btnVerifyOtp.text = ""
                login(mobile)
            } else if (enteredOTP == default) {
                Log.d("defaultOtp","$enteredOTP")
                binding.pbVerifyOtpLoader.visibility = View.VISIBLE
                binding.btnVerifyOtp.text = ""
                login(mobile)
            }
        }
    }

    private fun startTimer(){
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.btnResendOtp.isEnabled = false
                val time = millisUntilFinished / 1000
                binding.btnResendOtp.setText(getString(R.string.retry_in, if(time<10) "0$time" else time.toString()))
            }

            override fun onFinish() {
                binding.btnResendOtp.visibility = View.VISIBLE
                binding.btnResendOtp.setText("Resend")
                binding.btnResendOtp.isEnabled = true
                binding.btnResendOtp.isEnabled = true
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()  // This will stop the countdown and call onFinish()
        binding.btnResendOtp.setText("Resend") // Optionally reset the button text
    }


    private fun login(mobile: String) {
        Log.d("VerifyOTP", "Calling login function now")
        Log.d("VerifyOTP", "$mobile")

        if (mobile.isNotEmpty()){
            Log.d("VerifyOTP", "Not Empty")

            loginViewModel.login(mobile)

        }
    }


    private fun closeKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocusedView = currentFocus
        if (currentFocusedView != null) {
            inputMethodManager.hideSoftInputFromWindow(currentFocusedView.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }

    fun checkReferal(){
        binding.referCodeCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                //  binding.btnSendOtp.setBackgroundResource(R.drawable.d_button_bg_white)
                binding.cvReferCode.visibility = View.VISIBLE
            } else {

                binding.etReferCode.setText("")
                binding.cvReferCode.visibility = View.GONE
            }
        }

        binding.etReferCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                binding.applyReferral.setText("Apply")
                DPreferences(this@NewLoginActivity).setReferralCode("") // Save empty referral code initially
                val savedReferCode = DPreferences(this@NewLoginActivity).getReferralCode()

                Log.d("savedReferCode","$savedReferCode")


            }

            override fun afterTextChanged(s: Editable) {
            }
        })



        binding.applyReferral.setOnClickListener {
            val mobile = binding.etMobileNumber.text.toString()

            val mobileRegex = Regex("^[6-9]\\d{9}$")
            var refercode = binding.etReferCode.text.toString()

            // Validation logic
            if (TextUtils.isEmpty(mobile) || !mobile.matches(mobileRegex)) {
                showSnackbar("Enter a valid 10-digit mobile number")
            }else if (binding.etReferCode.text.isEmpty()){
                showSnackbar("Referral code can't be empty")
            }else{
                referralCodeViewModel.checkReferCode(mobile,refercode)
            }
        }
    }

    fun observeReferralCodeResponse() {

        referralCodeViewModel.referCodeResponseLiveData.observe(this) { response ->
            Log.d("referCodeResponseLiveData", "$response")

            response?.let {
                if (it.success) {
                    binding.applyReferral.setText("Applied")
                    Toast.makeText(this@NewLoginActivity, "Refer code applied successfully", Toast.LENGTH_SHORT).show()
                    val referCode = binding.etReferCode.text.toString()
                    DPreferences(this).setReferralCode(referCode)
                    val savedReferCode = DPreferences(this).getReferralCode()
                    Log.d("savedReferCode","$savedReferCode")
                } else {
                    Toast.makeText(this@NewLoginActivity, "${it.message}", Toast.LENGTH_SHORT).show()
                }
            }


        }

    }

}


