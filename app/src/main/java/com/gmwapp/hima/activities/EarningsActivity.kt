package com.gmwapp.hima.activities

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.EarningsAdapter
import com.gmwapp.hima.databinding.ActivityEarningsBinding
import com.gmwapp.hima.databinding.BottomSheetSelectPaymentBinding
import com.gmwapp.hima.dialogs.BottomSheetSelectPayment
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.EarningsViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EarningsActivity : BaseActivity() {
    lateinit var binding: ActivityEarningsBinding
    private val earningsViewModel: EarningsViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()
    val profileViewModel: ProfileViewModel by viewModels()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEarningsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initUI()
    }

    private fun initUI() {

       // panVerification()
        updateEarnings()

        binding.ivBack.setOnSingleClickListener {
            finish()
        }


        binding.btnWithdraw.setOnSingleClickListener {


//            val intent = Intent(this, WithdrawActivity::class.java)
//            intent.putExtra("balance", binding.tvEarnings.text.toString()) // Replace key_name and value_to_pass with your actual key and value.
//            startActivity(intent)

            val bottomSheet: BottomSheetSelectPayment = BottomSheetSelectPayment()
            bottomSheet.show(supportFragmentManager, "BottomSheetSelectPayment")

        }

        binding.cvPanDetails.setOnSingleClickListener {
            val intent = Intent(this, KycActivity::class.java)
            startActivity(intent)

        }


        val prefs = BaseApplication.getInstance()?.getPrefs()
        val settingsData = prefs?.getSettingsData()
        val supportMail = settingsData?.support_mail
        binding.tvSupportMail.text = supportMail
        binding.tlBalanceHint.text =
            getString(R.string.balance_hint_text, settingsData?.minimum_withdrawals)
        accountViewModel.getSettings()
        prefs?.getUserData()?.let {
                earningsViewModel.getEarnings(it.id)
                val balance = it.balance
                if (settingsData?.minimum_withdrawals != null && balance != null && balance < settingsData.minimum_withdrawals) {
                    binding.btnWithdraw.visibility = View.GONE
                    binding.vDivider.visibility = View.VISIBLE
                    binding.ivBalance.visibility = View.VISIBLE
                    binding.tlBalanceHint.visibility = View.VISIBLE
                } else {
                    binding.btnWithdraw.visibility = View.VISIBLE
                    binding.vDivider.visibility = View.GONE
                    binding.ivBalance.visibility = View.GONE
                    binding.tlBalanceHint.visibility = View.GONE
                }
                binding.tvCurrentBalance.text = "₹" +balance.toString()
            }
        accountViewModel.settingsLiveData.observe(this, Observer {
            if (it!=null && it.success) {
                if (it.data != null) {
                    if (it.data.size > 0) {
                        val settingsData1 = it.data[0]
                        prefs?.setSettingsData(settingsData1)
                        val supportMail = settingsData1.support_mail
                        binding.tlBalanceHint.text =
                            getString(R.string.balance_hint_text, settingsData1.minimum_withdrawals)
                        binding.tvSupportMail.text = supportMail
                        val userData = prefs?.getUserData()

                        val subject = getString(
                            R.string.delete_account_mail_subject,
                            userData?.mobile,
                            userData?.language
                        )

                        val body = getString(
                            R.string.mail_body,
                            userData?.mobile,
                            android.os.Build.MODEL,
                            userData?.language,
                            BuildConfig.VERSION_CODE
                        )
                        binding.tvSupportMail.setOnSingleClickListener {
                            val intent = Intent(Intent.ACTION_VIEW)

                            val data =
                                Uri.parse(("mailto:$supportMail?subject=$subject").toString() + "&body=$body")
                            intent.setData(data)

                            startActivity(intent)
                        }
                        binding.tvSupportMail.paintFlags =
                            binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    }
                }
            }
        })
        earningsViewModel.earningsResponseLiveData.observe(this, Observer {
            if (it.data != null) {
                binding.cvPanDetails.visibility=View.GONE
                binding.rvEarnings.setLayoutManager(
                    LinearLayoutManager(
                        this, LinearLayoutManager.VERTICAL, false
                    )
                )

                var earningsAdapter = EarningsAdapter(this, it.data)
                binding.rvEarnings.setAdapter(earningsAdapter)
            }
        })

    }

//    fun panVerification(){
//        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
//
//        userData?.let { loginViewModel.login(it.mobile) }
//        loginViewModel.loginResponseLiveData.observe(this, Observer {
//
//            if (it.success) {
//                if (!it.data?.pancard_name.isNullOrEmpty()&& !it.data?.pancard_number.isNullOrEmpty()){
//                    binding.ivAddPan.setBackgroundResource(R.drawable.tick_circle_svg) // Replace with your valid drawable resource
//                    // Rotate the drawable by a specified angle (e.g., 45 degrees)
//                    binding.ivAddPan.rotation = 0f // This rotates the ImageView by 45 degrees
//                }
//
//            }
//        })
//    }

    fun updateEarnings(){
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
            profileViewModel.getUsers(it)
        }

        profileViewModel.getUserLiveData.observe(this, Observer {
            val prefs = BaseApplication.getInstance()?.getPrefs()
            prefs?.setUserData(it?.data)

        })
    }

    override fun onResume() {
        super.onResume()
        if (binding.cvPanDetails.visibility == View.VISIBLE)
        //panVerification()
        updateEarnings()
    }
}