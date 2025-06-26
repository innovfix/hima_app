package com.gmwapp.hima.activities

import android.content.ActivityNotFoundException
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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityNewLoginBinding
import com.gmwapp.hima.databinding.ActivityShareBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareBinding
    val profileViewModel: ProfileViewModel by viewModels()
    val viewModel: LoginViewModel by viewModels()
     var downloadLink : String = ""
    private val loginViewModel: LoginViewModel by viewModels()
    private  var isPanCardVerified = false


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


        viewModel.appUpdate()

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.id?.let { profileViewModel.getUsers(it) }


        binding.ivBack.setOnSingleClickListener {
            finish()
        }

        if (userData?.gender=="female"){
            binding.coinPerInvite.text = "Money per invites"
            binding.coinEarned.text = "Total money earned"
            binding.textView6.text = "How to get money ?"
            binding.tvGetFreeCoin.text = "Get Money"
            binding.tvShare.text = "Share & Get Money"
            userData?.let { loginViewModel.login(it.mobile) }
            binding.coinH.setImageResource(R.drawable.ruppee_2)
            binding.ivCoin.setImageResource(R.drawable.ruppee_1)
            binding.ivCoin2.setImageResource(R.drawable.ruppee_1)

            panVerificationObserver()
        }

        profileViewModel.getUserLiveData.observe(this) { response ->
            response?.data?.let { userData ->
                binding.tvInvites.text = userData.total_referrals.toString()
                binding.tvInvitecode.text = userData.refer_code.toString()

                if (userData?.gender=="female"){
                    binding.tvCoinEarned.text = userData.referral_amount_gained.toString()
                    binding.tvCoinPerInvite.text = userData.money_per_referral.toString()

                    var disclaimer = userData.disclaimer.toString()
                    if (disclaimer.isNullOrEmpty()){
                        binding.tvDisclamair.visibility = View.GONE
                    }else{
                        binding.tvDisclamair.visibility = View.VISIBLE
                        binding.tvDisclamair.text = disclaimer

                    }

                }else{
                    binding.tvCoinEarned.text = userData.referral_coins_gained.toString()
                    binding.tvCoinPerInvite.text = userData.coins_per_referral.toString()

                }

                Log.d("referral", "${userData.referral_coins_gained}")
                Log.d("referral", "${userData.refer_code}")
                Log.d("referral", "${userData.referred_by}")
                Log.d("referral", "${userData.total_referrals}")
            } ?: Log.e("referral", "RegisterResponse is null")
        }

        viewModel.appUpdateResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                 downloadLink = it.data[0].link
                binding.shareLink.isEnabled = true // Enable only when link is available

                Log.d("downloadlink","$downloadLink")
            }
        })

        binding.shareLink.setOnClickListener {

            if (userData?.gender=="female") {
                if (isPanCardVerified){

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
                        Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                    }
                }else{
                    Toast.makeText(this, "Please Complete KYC", Toast.LENGTH_SHORT).show()

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
                    Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                }
            }


        }

    }


    fun panVerificationObserver(){

        loginViewModel.loginResponseLiveData.observe(this, Observer {

            if (it.success) {
                if (!it.data?.pancard_name.isNullOrEmpty()&& !it.data?.pancard_number.isNullOrEmpty()){
                    isPanCardVerified = true
                }

            }
        })
    }
}