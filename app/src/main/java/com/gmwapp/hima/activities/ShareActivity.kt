package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityShareBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareBinding
    val profileViewModel: ProfileViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()
    var downloadLink: String = ""
    private  var isPanCardVerified = false
    private var disclaimerText: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
    }
    fun initUI(){
        binding.shareLink.isEnabled = false


        loginViewModel.appUpdate()

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.id?.let { profileViewModel.getUsers(it) }


        binding.cvBack.setOnSingleClickListener {
            finish()
        }

        // Copy invite code functionality
        binding.ivCopy.setOnSingleClickListener {
            val inviteCode = binding.tvInvitecode.text.toString()
            if (inviteCode.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Invite Code", inviteCode)
                clipboard.setPrimaryClip(clip)
                showAppToast("Invite code copied!", Toast.LENGTH_SHORT)
            }
        }

        // Info icon click listener to toggle disclaimer
        binding.ivInfo.setOnSingleClickListener {
            if (disclaimerText.isNotEmpty()) {
                if (binding.tvDisclamair.visibility == View.VISIBLE) {
                    binding.tvDisclamair.visibility = View.GONE
                } else {
                    binding.tvDisclamair.visibility = View.VISIBLE
                }
            }
        }

        if (userData?.gender=="female"){
            binding.coinPerInvite.text = "Money per invites"
            binding.coinEarned.text = "Total money earned"
            binding.textView6.text = "How to get money ?"
            binding.tvGetFreeCoin.text = "Get Money"
            binding.tvShare.text = "Share & Get Money"
            binding.coinH.setImageResource(R.drawable.ruppee_coin)
            binding.ivCoin.setImageResource(R.drawable.ruppee_coin)
            binding.ivCoin2.setImageResource(R.drawable.ruppee_coin)

            // NOTE: KYC status now derived from profileViewModel.getUserLiveData
            // (same UserData shape includes pancard_name/pancard_number) rather
            // than triggering a side-effectful login() with dummy OTP args.
        }

        profileViewModel.getUserLiveData.observe(this) { response ->
            response?.data?.let { userData ->
                binding.tvInvites.text = userData.total_referrals ?: "0"
                binding.tvInvitecode.text = userData.refer_code ?: ""

                // Derive KYC state directly from this response so we don't need a
                // separate login() roundtrip.
                isPanCardVerified = !userData.pancard_name.isNullOrEmpty()
                    && !userData.pancard_number.isNullOrEmpty()

                if (userData?.gender=="female"){
                    binding.tvCoinEarned.text = userData.referral_amount_gained.toString()
                    binding.tvCoinPerInvite.text = userData.money_per_referral.toString()
                }else{
                    binding.tvCoinEarned.text = userData.referral_coins_gained.toString()
                    binding.tvCoinPerInvite.text = userData.coins_per_referral.toString()
                }

                // Store disclaimer text and show/hide info icon based on API response
                disclaimerText = userData.disclaimer?.toString() ?: ""
                if (disclaimerText.isNotEmpty() && !disclaimerText.equals("null", ignoreCase = true)) {
                    binding.tvDisclamair.text = disclaimerText
                    binding.tvDisclamair.visibility = View.GONE // Initially hidden, shown on info icon click
                    binding.ivInfo.visibility = View.VISIBLE
                } else {
                    binding.tvDisclamair.visibility = View.GONE
                    binding.ivInfo.visibility = View.GONE
                }

                Log.d("referral", "${userData.referral_coins_gained}")
                Log.d("referral", "${userData.refer_code}")
                Log.d("referral", "${userData.referred_by}")
                Log.d("referral", "${userData.total_referrals}")
            } ?: Log.e("referral", "RegisterResponse is null")
        }

        loginViewModel.appUpdateResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                val firstLink = it.data.firstOrNull()?.link
                if (!firstLink.isNullOrEmpty()) {
                    downloadLink = firstLink
                    binding.shareLink.isEnabled = true // Enable only when link is available
                    Log.d("downloadlink", downloadLink)
                } else {
                    Log.e("downloadlink", "appUpdate response has no link")
                }
            }
        })

        binding.shareLink.setOnClickListener {
            val currentInviteCode = binding.tvInvitecode.text?.toString().orEmpty()
            if (currentInviteCode.isEmpty() || downloadLink.isEmpty()) {
                showAppToast("Please wait, still loading your invite details", Toast.LENGTH_SHORT)
                return@setOnClickListener
            }

            if (userData?.gender=="female") {
                if (isPanCardVerified){

                    val referralCode = binding.tvInvitecode.text?.toString().orEmpty() // your stored invite code
                    val message =
                        "Join Hima App and make real \nfriends!❤\uFE0F\n" + "Use my code $referralCode to sign up. \n \n Download now: $downloadLink"

                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    intent.setPackage("com.whatsapp") // ensures only WhatsApp opens
                    intent.putExtra(Intent.EXTRA_TEXT, message)

                    try {
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        showAppToast("WhatsApp not installed", Toast.LENGTH_SHORT)
                    }
                }else{
                    showAppToast("Please Complete KYC", Toast.LENGTH_SHORT)

                }
            }else{


                val referralCode = binding.tvInvitecode.text // your stored invite code
                val message =
                    "Join Hima App and make real \nfriends!❤\uFE0F\n" + "Use my code $referralCode to sign up. \n \n Download now: $downloadLink"

                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.setPackage("com.whatsapp") // ensures only WhatsApp opens
                intent.putExtra(Intent.EXTRA_TEXT, message)

                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    showAppToast("WhatsApp not installed", Toast.LENGTH_SHORT)
                }
            }


        }

    }


    // Previously this function observed loginResponseLiveData to set
    // isPanCardVerified. That path required calling login(mobile, "0", "0") just
    // to fetch pancard fields, which fired an OTP-login side effect on the
    // backend. KYC is now derived from profileViewModel.getUserLiveData above,
    // so this helper is no longer needed.
}