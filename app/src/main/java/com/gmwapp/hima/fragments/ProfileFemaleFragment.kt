package com.gmwapp.hima.fragments

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.bitmap.TransformationUtils.circleCrop
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.AccountPrivacyActivity
import com.gmwapp.hima.activities.EarningsActivity
import com.gmwapp.hima.activities.EditProfileActivity
import com.gmwapp.hima.activities.RefundWebViewActivity
import com.gmwapp.hima.activities.ShareActivity
import com.gmwapp.hima.activities.TermConditionWebViewActivity
import com.gmwapp.hima.activities.TransactionsActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.databinding.FragmentProfileBinding
import com.gmwapp.hima.databinding.FragmentProfileFemaleBinding
import com.gmwapp.hima.dialogs.BottomSheetLogout
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFemaleFragment : BaseFragment() {
    lateinit var binding: FragmentProfileFemaleBinding
    private val EDIT_PROFILE_REQUEST_CODE = 1
    private val accountViewModel: AccountViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()
    private var isPanVerified = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileFemaleBinding.inflate(layoutInflater)
        initUI()
        panVerification()
        return binding.root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK && requestCode == EDIT_PROFILE_REQUEST_CODE){
            updateValues()
        }
    }

    private fun updateValues(){
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val supportMail = prefs?.getSettingsData()?.support_mail
        val subject = getString(R.string.delete_account_mail_subject, userData?.mobile, userData?.language)

        val body = ""
        binding.tvSupportMail.setOnSingleClickListener {
            val intent = Intent(Intent.ACTION_VIEW)

            val data = Uri.parse(("mailto:$supportMail?subject=$subject").toString() + "&body=$body")
            intent.setData(data)

            startActivity(intent)
        }
        binding.tvSupportMail.paintFlags =
            binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvSupportMail.text =
            supportMail
        binding.tvName.text = userData?.name
        Glide.with(this).load(userData?.image).
        apply(RequestOptions.circleCropTransform()).into(binding.ivProfile)
    }

    private fun initUI(){
        updateValues()
        val prefs = BaseApplication.getInstance()?.getPrefs()

        binding.clEarnings.setOnSingleClickListener( {
            val intent = Intent(context, EarningsActivity::class.java)
            startActivity(intent)
        })
        binding.ivEditProfile.setOnSingleClickListener( {
            val intent = Intent(context, EditProfileActivity::class.java)
            startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
        })
        binding.clAccountPrivacy.setOnSingleClickListener( {
            val intent = Intent(context, AccountPrivacyActivity::class.java)
            startActivity(intent)
        })
        binding.cvLogout.setOnSingleClickListener( {
            val bottomSheet: BottomSheetLogout =
                BottomSheetLogout()
            fragmentManager?.let { it1 ->
                bottomSheet.show(
                    it1,
                    "ProfileFragment"
                )
            }
        })

        binding.clReferEarn.setOnSingleClickListener {
            val intent = Intent(context, ShareActivity::class.java)
            if (isPanVerified){
                startActivity(intent)
            }else{
                Toast.makeText(requireContext(), "Please Complete Kyc", Toast.LENGTH_SHORT).show()

            }
        }

        binding.clTermsCondition.setOnSingleClickListener {
            val intent = Intent(context, TermConditionWebViewActivity::class.java)

            startActivity(intent)
        }

        binding.clRefund.setOnSingleClickListener {
            val intent = Intent(context, RefundWebViewActivity::class.java)

            startActivity(intent)
        }



        accountViewModel.getSettings()
        accountViewModel.settingsLiveData.observe(viewLifecycleOwner, Observer {
            if (it!=null && it.success) {
                if (it.data != null) {
                    if (it.data.size > 0) {
                        prefs?.setSettingsData(it.data.get(0))
                        val supportMail = prefs?.getSettingsData()?.support_mail
                        binding.tvSupportMail.text =
                            supportMail
                        val userData = prefs?.getUserData()
                        val subject = getString(R.string.delete_account_mail_subject, userData?.mobile,  userData?.language)

                        val body = ""
                        binding.tvSupportMail.setOnSingleClickListener {
                            val intent = Intent(Intent.ACTION_VIEW)

                            val data = Uri.parse(("mailto:$supportMail?subject=$subject").toString() + "&body=$body")
                            intent.setData(data)

                            startActivity(intent)
                        }
                        binding.tvSupportMail.paintFlags =
                            binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    }
                }
            }
        })
    }

    fun panVerification(){
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        userData?.let { loginViewModel.login(it.mobile,"0","0") }
        loginViewModel.loginResponseLiveData.observe(viewLifecycleOwner, Observer {

            if (it.success) {
                if (!it.data?.pancard_name.isNullOrEmpty()&& !it.data?.pancard_number.isNullOrEmpty()){
                    isPanVerified = true
                }

            }
        })
    }

    override fun onResume() {
        super.onResume()
            panVerification()
    }
}