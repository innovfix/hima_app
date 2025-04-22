package com.gmwapp.hima.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.AccountPrivacyActivity
import com.gmwapp.hima.activities.EditProfileActivity
import com.gmwapp.hima.activities.RefundWebViewActivity
import com.gmwapp.hima.activities.ShareActivity
import com.gmwapp.hima.activities.TermConditionWebViewActivity
import com.gmwapp.hima.activities.TransactionsActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.activities.WebviewActivity
import com.gmwapp.hima.databinding.FragmentProfileBinding
import com.gmwapp.hima.dialogs.BottomSheetLogout
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFragment : BaseFragment() {
    lateinit var binding: FragmentProfileBinding
    private val EDIT_PROFILE_REQUEST_CODE = 1
    private val accountViewModel: AccountViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(layoutInflater)
        initUI()
        return binding.root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == EDIT_PROFILE_REQUEST_CODE) {
            updateValues()
        }
    }

    private fun updateValues() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        binding.tvName.text = userData?.name
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val supportMail = prefs?.getSettingsData()?.support_mail
        val subject = getString(R.string.delete_account_mail_subject, userData?.mobile, userData?.language)

        val body = ""
        binding.tvSupportMail.text = supportMail
        binding.tvSupportMail.paintFlags = binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvSupportMail.setOnSingleClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            val data = Uri.parse("mailto:$supportMail?subject=$subject&body=$body")
            intent.data = data
            startActivity(intent)
        }

        Glide.with(this)
            .load(userData?.image)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivProfile)
    }

    private fun initUI() {
        updateValues()

        val prefs = BaseApplication.getInstance()?.getPrefs()

        binding.clWallet.setOnSingleClickListener {
            val intent = Intent(context, WalletActivity::class.java)
            startActivity(intent)
        }

        binding.ivEditProfile.setOnSingleClickListener {
            if (isInternetAvailable()) {
                val intent = Intent(context, EditProfileActivity::class.java)
                startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
            } else {
                Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }

        binding.clTransactions.setOnSingleClickListener {
            val intent = Intent(context, TransactionsActivity::class.java)
            startActivity(intent)
        }

        binding.clAccountPrivacy.setOnSingleClickListener {
            val intent = Intent(context, AccountPrivacyActivity::class.java)
            startActivity(intent)
        }


        binding.clReferEarn.setOnSingleClickListener {
            val intent = Intent(context, ShareActivity::class.java)
            startActivity(intent)
        }

        binding.clTermsCondition.setOnSingleClickListener {
            val intent = Intent(context, TermConditionWebViewActivity::class.java)

            startActivity(intent)
        }

        binding.clRefund.setOnSingleClickListener {
            val intent = Intent(context, RefundWebViewActivity::class.java)

            startActivity(intent)
        }

        binding.cvLogout.setOnSingleClickListener {
            val bottomSheet = BottomSheetLogout()
            fragmentManager?.let {
                bottomSheet.show(it, "ProfileFragment")
            }


        }

        accountViewModel.getSettings()
        accountViewModel.settingsLiveData.observe(viewLifecycleOwner, Observer {
            if (it!=null && it.success && it.data?.isNotEmpty() == true) {
                prefs?.setSettingsData(it.data[0])
                val supportMail = prefs?.getSettingsData()?.support_mail
                binding.tvSupportMail.text = supportMail

                val userData = prefs?.getUserData()
                val subject = getString(R.string.delete_account_mail_subject, userData?.mobile, userData?.language)
                val body = ""

                binding.tvSupportMail.setOnSingleClickListener {
                    val intent = Intent(Intent.ACTION_VIEW)
                    val data = Uri.parse("mailto:$supportMail?subject=$subject&body=$body")
                    intent.data = data
                    startActivity(intent)
                }

                binding.tvSupportMail.paintFlags = binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        })
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }
}
