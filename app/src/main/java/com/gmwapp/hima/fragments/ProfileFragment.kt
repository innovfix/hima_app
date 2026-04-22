package com.gmwapp.hima.fragments

import com.gmwapp.hima.utils.showAppToast

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
import androidx.core.content.ContextCompat
import com.gmwapp.hima.activities.CancelSubscriptionActivity
import com.gmwapp.hima.activities.CommunityGuidelineActivity
import com.gmwapp.hima.activities.DummySubscriptionActivity
import com.gmwapp.hima.dialogs.BottomSheetTrialOffer
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.AccountPrivacyActivity
import com.gmwapp.hima.activities.MyWarningsActivity
import com.gmwapp.hima.activities.EditProfileActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.activities.FriendsListActivity
import com.gmwapp.hima.activities.HelpAndSupportActivity
import com.gmwapp.hima.activities.RefundWebViewActivity
import com.gmwapp.hima.activities.ShareActivity
import com.gmwapp.hima.activities.TermConditionWebViewActivity
import com.gmwapp.hima.activities.TransactionsActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.callbacks.NetworkRetryable
import com.gmwapp.hima.fragments.FriendsTabFragment
import com.gmwapp.hima.databinding.FragmentProfileBinding
import com.gmwapp.hima.dialogs.BottomSheetLogout
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFragment : BaseFragment(), NetworkRetryable {
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
        
        // Show/Hide warnings card based on user gender (only for female users)
        val userGender = prefs?.getUserData()?.gender
        android.util.Log.d("ProfileFragment", "User gender: $userGender")
        android.util.Log.d("ProfileFragment", "DConstants.FEMALE: ${DConstants.FEMALE}")
        android.util.Log.d("ProfileFragment", "Comparison result: ${userGender?.equals(DConstants.FEMALE, ignoreCase = true)}")
        
        if (userGender?.equals(DConstants.FEMALE, ignoreCase = true) == true) {
            binding.clWarnings.visibility = View.VISIBLE
            android.util.Log.d("ProfileFragment", "Warnings card set to VISIBLE")
        } else {
            binding.clWarnings.visibility = View.GONE
            android.util.Log.d("ProfileFragment", "Warnings card set to GONE")
        }

        binding.clWallet.setOnSingleClickListener {
            val intent = Intent(context, WalletActivity::class.java)
            startActivity(intent)
        }
        
        binding.avatarContainer.setOnSingleClickListener {
            if (isInternetAvailable()) {
                val intent = Intent(context, EditProfileActivity::class.java)
                startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
            } else {
                requireContext().showAppToast("No internet connection", Toast.LENGTH_SHORT)
            }
        }
        
        binding.editIconBadge.setOnSingleClickListener {
            if (isInternetAvailable()) {
                val intent = Intent(context, EditProfileActivity::class.java)
                startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
            } else {
                requireContext().showAppToast("No internet connection", Toast.LENGTH_SHORT)
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

        binding.clMyFriends.setOnSingleClickListener {
            val intent = Intent(context, FriendsListActivity::class.java)
            intent.putExtra("target_tab", FriendsTabFragment.TYPE_CHAT)
            startActivity(intent)
        }

        binding.clWarnings.setOnSingleClickListener {
            val intent = Intent(context, MyWarningsActivity::class.java)
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

        binding.clGuideline.setOnSingleClickListener {
            val intent = Intent(context, CommunityGuidelineActivity::class.java)

            startActivity(intent)
        }

        binding.clHelpSupport.setOnSingleClickListener {
            val intent = Intent(context, HelpAndSupportActivity::class.java)
            startActivity(intent)
        }

        refreshActiveSubscriptionCard()

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

    override fun onResume() {
        super.onResume()
        refreshActiveSubscriptionCard()
    }

    private fun refreshActiveSubscriptionCard() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(
            DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE
        )
        val active = prefs.getBoolean(DummySubscriptionActivity.KEY_ACTIVE, false)

        if (active) {
            binding.activeSubIcon.setCardBackgroundColor(
                ContextCompat.getColor(ctx, R.color.green).let {
                    android.graphics.Color.argb(30, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it))
                }
            )
            binding.ivActiveSubIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.green))
            binding.tvActiveSubLabel.text = "Active Subscription"
            binding.tvActiveSubStatus.text = "Active · Tap to manage"
            binding.tvActiveSubStatus.setTextColor(ContextCompat.getColor(ctx, R.color.green))
            binding.cvActiveSubscription.setOnClickListener {
                startActivity(Intent(ctx, CancelSubscriptionActivity::class.java))
            }
        } else {
            binding.activeSubIcon.setCardBackgroundColor(
                android.graphics.Color.parseColor("#FFF0F7")
            )
            binding.ivActiveSubIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.pink))
            binding.tvActiveSubLabel.text = "Subscribe"
            binding.tvActiveSubStatus.text = "Start 2-day trial for ₹1"
            binding.tvActiveSubStatus.setTextColor(ContextCompat.getColor(ctx, R.color.pink))
            binding.cvActiveSubscription.setOnClickListener {
                val sheet = BottomSheetTrialOffer()
                sheet.setOnTryNowClickListener {
                    startActivity(Intent(ctx, DummySubscriptionActivity::class.java))
                }
                sheet.show(parentFragmentManager, "trial_offer")
            }
        }
    }

    override fun onNetworkRetry() {
        updateValues()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }
}
