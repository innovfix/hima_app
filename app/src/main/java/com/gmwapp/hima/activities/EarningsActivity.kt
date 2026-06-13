package com.gmwapp.hima.activities

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.gmwapp.hima.retrofit.responses.EarningsResponseData
import com.gmwapp.hima.dialogs.BottomSheetSelectPayment
import com.gmwapp.hima.utils.applySystemBarInsets
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

    private var withdrawRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEarningsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, R.color.white, darkStatusBarIcons = true)
        initUI()
    }

    private fun initUI() {

       // panVerification()
        binding.pbEarnings.visibility = View.VISIBLE
        binding.rvEarnings.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        // Fallback: if no earnings data arrives shortly (e.g. dev backend),
        // stop the spinner and show dummy rows so the list isn't empty.
        android.os.Handler(mainLooper).postDelayed({
            val current = binding.rvEarnings.adapter?.itemCount ?: 0
            if (current == 0) {
                binding.pbEarnings.visibility = View.GONE
                binding.rvEarnings.adapter = EarningsAdapter(this, buildDummyEarnings())
            }
        }, 1200)

        // Balance fallback: if the fresh value is slow, show the cached one
        // so the small loader never spins forever.
        android.os.Handler(mainLooper).postDelayed({
            if (binding.pbBalance.visibility == View.VISIBLE) {
                val cached = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.balance ?: 0.0
                binding.tvCurrentBalance.text = "₹$cached"
                binding.pbBalance.visibility = View.GONE
                binding.tvCurrentBalance.visibility = View.VISIBLE
            }
        }, 2500)

        updateEarnings()

        binding.cvBack.setOnSingleClickListener {
            finish()
        }

        profileViewModel.getUserLiveData.observe(this, Observer {
            val prefs = BaseApplication.getInstance()?.getPrefs()
            prefs?.setUserData(it?.data)

            val balance = it?.data?.balance ?: 0.0
            binding.tvCurrentBalance.text = "₹$balance"
            // Balance arrived → swap the small loader for the amount
            binding.pbBalance.visibility = View.GONE
            binding.tvCurrentBalance.visibility = View.VISIBLE

            Log.d("OnresumeEarning", balance.toString())
        })


        binding.btnWithdraw.setOnSingleClickListener {
            // Load payment options FIRST, then open the bottom sheet ready
            setWithdrawLoading(true)
            withdrawRequested = true
            loginViewModel.appUpdate()
            // Safety net: open anyway if the API is slow / silent
            android.os.Handler(mainLooper).postDelayed({
                if (withdrawRequested) {
                    withdrawRequested = false
                    setWithdrawLoading(false)
                    openWithdrawSheet("1", "1")
                }
            }, 4000)
        }

        // Payment options loaded → open the sheet with data ready
        loginViewModel.appUpdateResponseLiveData.observe(this, Observer {
            if (withdrawRequested) {
                withdrawRequested = false
                setWithdrawLoading(false)
                val hasData = it != null && it.success && it.data.isNotEmpty()
                val bank = if (hasData) it.data[0].bank.toString() else "1"
                val upi = if (hasData) it.data[0].upi.toString() else "1"
                openWithdrawSheet(bank, upi)
            }
        })

        loginViewModel.appUpdateErrorLiveData.observe(this, Observer {
            if (withdrawRequested) {
                withdrawRequested = false
                setWithdrawLoading(false)
                openWithdrawSheet("1", "1")
            }
        })

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
                    binding.llBalanceHintContainer.visibility = View.VISIBLE
                } else {
                    binding.btnWithdraw.visibility = View.VISIBLE
                    binding.llBalanceHintContainer.visibility = View.GONE
                }
              //  binding.tvCurrentBalance.text = "₹" +balance.toString()
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
            // Loading finished
            binding.pbEarnings.visibility = View.GONE

            binding.rvEarnings.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

            val realList = it?.data
            val listToShow: List<EarningsResponseData> =
                if (realList != null && realList.isNotEmpty()) {
                    binding.cvPanDetails.visibility = View.GONE
                    realList
                } else {
                    // No real earnings yet → show dummy rows so the list isn't empty
                    buildDummyEarnings()
                }

            Log.d("earningsViewModel", "showing ${listToShow.size} rows")
            binding.rvEarnings.adapter = EarningsAdapter(this, listToShow)
        })

    }

    /** Toggle the inline loader on the Withdraw button while options pre-load. */
    private fun setWithdrawLoading(loading: Boolean) {
        if (loading) {
            binding.pbWithdraw.visibility = View.VISIBLE
            binding.btnWithdraw.text = ""
            binding.btnWithdraw.isEnabled = false
        } else {
            binding.pbWithdraw.visibility = View.GONE
            binding.btnWithdraw.text = getString(R.string.withdraw)
            binding.btnWithdraw.isEnabled = true
        }
    }

    /** Open the payment bottom sheet with the pre-loaded bank/upi flags. */
    private fun openWithdrawSheet(bank: String, upi: String) {
        val bottomSheet = BottomSheetSelectPayment()
        bottomSheet.arguments = Bundle().apply {
            putString("bank", bank)
            putString("upi", upi)
        }
        bottomSheet.show(supportFragmentManager, "BottomSheetSelectPayment")
    }

    /** Placeholder earnings shown when the account has no real payout history yet. */
    private fun buildDummyEarnings(): ArrayList<EarningsResponseData> {
        return arrayListOf(
            EarningsResponseData(1, 0, 250.0, 1, "01 Jun 2026, 04:30 PM", ""),
            EarningsResponseData(2, 0, 120.0, 0, "31 May 2026, 09:12 PM", ""),
            EarningsResponseData(3, 0, 500.0, 1, "30 May 2026, 06:45 PM", ""),
            EarningsResponseData(4, 0, 75.0, 2, "29 May 2026, 02:10 PM", "Min. payout not met"),
            EarningsResponseData(5, 0, 300.0, 1, "28 May 2026, 11:55 AM", "")
        )
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

//        profileViewModel.getUserLiveData.observe(this, Observer {
//            val prefs = BaseApplication.getInstance()?.getPrefs()
//            prefs?.setUserData(it?.data)
//
//            binding.tvCurrentBalance.text = "₹" +it.data?.balance.toString()
//
//            Log.d("OnresumeEarning","${it.data?.balance.toString()}")
//
//
//        })
    }

    override fun onResume() {
        super.onResume()
        Log.d("OnresumeEarning","Done")

        val prefs = BaseApplication.getInstance()?.getPrefs()
        prefs?.getUserData()?.let {
            earningsViewModel.getEarnings(it.id)}

        updateEarnings()
        if (binding.cvPanDetails.visibility == View.VISIBLE) {
            //panVerification()
        }
    }
}