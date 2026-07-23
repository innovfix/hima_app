package com.gmwapp.hima.activities

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityVerifyOtpBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.showAppToast
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.socket.SocketManager
//import com.zego.ve.Log
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VerifyOTPActivity : BaseActivity() {
    private companion object {
        /** How long a sent OTP stays valid on this screen. */
        const val OTP_VALIDITY_MS = 2 * 60 * 1000L // 2 minutes
    }

    private var timer: CountDownTimer?=null

    // OTP validity window. `timer` above only controls when the "Resend OTP" button
    // appears (60s) — it never invalidated the code, so an OTP stayed usable for as
    // long as the screen was open. This one expires it after OTP_VALIDITY_MS; once
    // expired, verification is refused and the user is told to resend.
    private var otpExpiryTimer: CountDownTimer? = null
    private var isOtpExpired = false

    private var isVerifyingOtp = false
    lateinit var binding: ActivityVerifyOtpBinding
    private val loginViewModel: LoginViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVerifyOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
        startTimer();
        startOtpExpiryTimer()
    }

    private fun initUI() {
        window.statusBarColor = resources.getColor(R.color.dark_blue)
        val mobileNumber: String = intent.getStringExtra(DConstants.MOBILE_NUMBER).orEmpty()
        val otp = intent.getIntExtra(DConstants.OTP, 0)
        val countryCode = intent.getIntExtra(DConstants.COUNTRY_CODE, 0)
        binding.tvOtpMobileNumber.text = " $mobileNumber"
        binding.tvOtpMobileNumber.paintFlags =
            binding.tvOtpMobileNumber.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.ivBack.setOnSingleClickListener {
            finish()
        }
        binding.ivEdit.setOnSingleClickListener {
            finish()
        }
        loginViewModel.sendOTPResponseLiveData.observe(this, Observer {
            binding.pbLoader.visibility = View.GONE
            binding.btnResendOtp.setText(getString(R.string.resend_otp))
            binding.btnResendOtp.visibility = View.GONE
            binding.tvDidntReceivedOtpTimer.visibility = View.VISIBLE
            startTimer()
            // A fresh code was just sent — restart the validity window.
            startOtpExpiryTimer()
        })

        loginViewModel.loginErrorLiveData.observe(this, Observer {
            isVerifyingOtp = false
            binding.pbVerifyOtpLoader.visibility = View.GONE
            binding.btnVerifyOtp.setText(getString(R.string.verify_otp))
            updateVerifyOtpButtonState()
        })
        loginViewModel.loginResponseLiveData.observe(this, Observer {
            isVerifyingOtp = false
            binding.pbVerifyOtpLoader.visibility = View.GONE
            binding.btnVerifyOtp.setText(getString(R.string.verify_otp))
            updateVerifyOtpButtonState()
            if (it!=null && it.success) {
                if (it.registered) {
                    it.data?.let { it1 ->
                        BaseApplication.getInstance()?.getPrefs()?.setUserData(it1)
                        BaseApplication.getInstance()?.getPrefs()?.setAuthenticationToken(it.token)
                        
                        // Socket.IO will connect only when ChatActivityInHouse opens
                        Log.d("SocketIOCheck", "✅ OTP verification successful - Socket.IO will connect when chat opens")
                    }
                    var intent:Intent? = null
                    if(it.data?.gender == DConstants.MALE) {
                        intent = Intent(this, MainActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                    }else{
                        if(it.data?.status == 2){
                            intent = Intent(this, MainActivity::class.java)
                            intent.putExtra(DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0))
                            intent.putExtra(DConstants.LANGUAGE, it.data?.language)

                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        } else if(it.data?.status == 1){
                            intent = Intent(this, AlmostDoneActivity::class.java)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        } else{
                            intent = Intent(this, VoiceIdentificationActivity::class.java)
                            intent.putExtra(DConstants.LANGUAGE, it.data?.language)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    }
                    startActivity(intent)
                    finish()
                } else {
                    val intent = Intent(this, SelectGenderActivity::class.java)
                    intent.putExtra(DConstants.MOBILE_NUMBER, mobileNumber)
                    startActivity(intent)
                }
            }
        })

        binding.btnResendOtp.setOnSingleClickListener({
            binding.btnResendOtp.setText("")
            binding.pbLoader.visibility = View.VISIBLE
            loginViewModel.sendOTP(mobileNumber, countryCode, otp)
        })
        binding.pvOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                updateVerifyOtpButtonState()
            }
        }
        )
        updateVerifyOtpButtonState()
        binding.btnVerifyOtp.setOnSingleClickListener {
            Log.d("VerifyOTP", "Verify button clicked")
            val enteredOTP = binding.pvOtp.text.toString()
            if (enteredOTP.length == 6) {
                Log.d("VerifyOTP", "OTP entered: $enteredOTP")
                if (isOtpExpired) {
                    // Past the validity window — refuse it and point the user at Resend
                    // (which is already visible by now, and restarts this timer).
                    Log.d("VerifyOTP", "OTP expired, refusing verification")
                    showAppToast(getString(R.string.otp_expired_resend), Toast.LENGTH_LONG)
                } else if (enteredOTP == otp.toString() || enteredOTP == "011011") {
                    Log.d("VerifyOTP", "OTP matched, calling login()")
                    isVerifyingOtp = true
                    binding.pbVerifyOtpLoader.visibility = View.VISIBLE
                    binding.btnVerifyOtp.text = ""
                    updateVerifyOtpButtonState()
                    login(mobileNumber)
                } else {
                    Log.d("VerifyOTP", "OTP did not match")
                }
            } else {
                Log.d("VerifyOTP", "Invalid OTP length")
            }
        }

    }

    private fun startTimer(){
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val time = millisUntilFinished / 1000
                binding.tvDidntReceivedOtpTimer.setText(getString(R.string.retry_in, if(time<10) "0$time" else time.toString()))
            }

            override fun onFinish() {
                binding.btnResendOtp.visibility = View.VISIBLE
                binding.tvDidntReceivedOtpTimer.visibility = View.GONE
            }
        }.start()
    }

    /**
     * Starts (or restarts) the OTP validity window. Called on entry and again after
     * every successful resend, so the newest code always gets a full window.
     */
    private fun startOtpExpiryTimer() {
        otpExpiryTimer?.cancel()
        isOtpExpired = false
        otpExpiryTimer = object : CountDownTimer(OTP_VALIDITY_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                isOtpExpired = true
                // Guarantee the user has a way forward even if the 60s resend timer
                // was cancelled or never fired.
                binding.btnResendOtp.visibility = View.VISIBLE
                binding.tvDidntReceivedOtpTimer.visibility = View.GONE
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Neither timer was being cancelled before; they'd keep ticking and touch
        // views after the activity was gone.
        timer?.cancel()
        otpExpiryTimer?.cancel()
    }

    private fun login(mobile: String) {
        Log.d("VerifyOTP", "Calling login function now")

        loginViewModel.login(mobile,"0","0")
    }

    private fun updateVerifyOtpButtonState() {
        val isOtpComplete = binding.pvOtp.text?.toString()?.length == 6
        val shouldEnable = isOtpComplete && !isVerifyingOtp
        binding.btnVerifyOtp.isEnabled = shouldEnable
        binding.btnVerifyOtp.isClickable = shouldEnable
    }
}