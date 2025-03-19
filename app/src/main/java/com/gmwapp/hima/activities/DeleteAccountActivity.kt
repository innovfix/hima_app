package com.gmwapp.hima.activities

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.DeleteReasonAdapter
import com.gmwapp.hima.callbacks.OnButtonClickListener
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.ActivityDeleteAccountBinding
import com.gmwapp.hima.dialogs.BottomSheetDeleteAccount
import com.gmwapp.hima.retrofit.responses.Reason
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxItemDecoration
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
//import com.tencent.mmkv.MMKV
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class DeleteAccountActivity : BaseActivity(), OnButtonClickListener {
    lateinit var binding: ActivityDeleteAccountBinding
    private var isMoreWarnings: Boolean? = false
    private val selectedReasons: ArrayList<String> = ArrayList()
    private val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeleteAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initUI()
    }

    override fun onButtonClick() {
        var reason = ""
        if (selectedReasons.size > 0) {
            reason = selectedReasons.joinToString(separator = ",") { it }
        } else {
            reason = binding.etDescription.text.toString()
        }
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let { it1 ->
            profileViewModel.deleteUsers(
                it1, reason
            )
        }
    }

    fun String.fromHtml(): Spanned {
        return Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun initUI() {
        binding.cvDeleteAccount.setBackgroundResource(R.drawable.warning_background)
        binding.cvDescription.setBackgroundResource(R.drawable.d_button_bg_user_name)
        binding.ivBack.setOnSingleClickListener {
            finish()
        }
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val supportMail = prefs?.getSettingsData()?.support_mail
        val userData = prefs?.getUserData()
        val subject = getString(R.string.delete_account_mail_subject, userData?.mobile, userData?.language)

        val body = getString(R.string.mail_body, userData?.mobile,android.os.Build.MODEL,userData?.language,
            BuildConfig.VERSION_CODE
        )
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
        binding.clViewMore.setOnSingleClickListener({
            if (isMoreWarnings == true) {
                changeWarningHints(View.GONE)
                isMoreWarnings = false
            } else {
                changeWarningHints(View.VISIBLE)
                isMoreWarnings = true
            }
        })
        binding.btnDeleteAccount.setOnSingleClickListener({
            val bottomSheet = BottomSheetDeleteAccount()
            bottomSheet.show(
                supportFragmentManager, "BottomSheetDeleteAccount"
            )
        })
        profileViewModel.deleteUserErrorLiveData.observe(this, Observer {
            Toast.makeText(this@DeleteAccountActivity, getString(R.string.please_try_again_later), Toast.LENGTH_LONG).show()
        })
        profileViewModel.deleteUserLiveData.observe(this, Observer {
            if (it!=null && it.success) {
//                MMKV.defaultMMKV().remove("user_id");
//                MMKV.defaultMMKV().remove("user_name");
//                ZegoUIKitPrebuiltCallService.unInit()
                prefs?.clearUserData()
                val intent = Intent(this, NewLoginActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(
                    this@DeleteAccountActivity, it?.message, Toast.LENGTH_LONG
                ).show()
            }
        })
        val staggeredGridLayoutManager = FlexboxLayoutManager(this).apply {
            flexWrap = FlexWrap.WRAP
            alignItems = AlignItems.FLEX_START
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.FLEX_START
        }
        val itemDecoration = FlexboxItemDecoration(this).apply {
            setDrawable(
                ContextCompat.getDrawable(
                    this@DeleteAccountActivity, R.drawable.bg_divider
                )
            )
            setOrientation(FlexboxItemDecoration.VERTICAL)
        }
        binding.rvReasons.addItemDecoration(itemDecoration)
        binding.rvReasons.setLayoutManager(staggeredGridLayoutManager)

        binding.etDescription.setOnTouchListener { v, _ ->
            binding.cvDescription.setBackgroundResource(R.drawable.card_view_border_country_selected)
            false
        }
        binding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (TextUtils.isEmpty(s)) {
                   // binding.btnDeleteAccount.setBackgroundResource(R.drawable.d_button_bg_disabled)
                   // binding.btnDeleteAccount.setTextColor(getColor(R.color.black))
                    binding.btnDeleteAccount.isEnabled = false
                } else {
                    binding.tvRemainingText.text = getString(
                        R.string.description_remaining_text,
                        s.length
                    )
                //    binding.btnDeleteAccount.setBackgroundResource(R.drawable.d_button_bg_red)
                  //  binding.btnDeleteAccount.setTextColor(getColor(R.color.white))
                    binding.btnDeleteAccount.isEnabled = true
                }
            }

            override fun afterTextChanged(s: Editable) {
            }
        })
        var deleteReasonAdapter = DeleteReasonAdapter(this, arrayListOf(
            Reason(getString(R.string.not_able_to_here_hima), false),
            Reason(getString(R.string.abusive_language), false),
            Reason(getString(R.string.hima_not_polite), false),
            Reason(getString(R.string.hima_not_interested), false),
            Reason(getString(R.string.ask_for_money), false),
            Reason(getString(R.string.other), false)
        ), false, object : OnItemSelectionListener<Reason> {
            override fun onItemSelected(reason: Reason) {
                if (reason.name == "Other") {
                    selectedReasons.clear()
                    binding.btnDeleteAccount.isEnabled = false
                   // binding.btnDeleteAccount.setBackgroundResource(R.drawable.d_button_bg_disabled)
                    binding.etDescription.setText("")
                    if (reason.isSelected == true) {
                        binding.tvRemainingText.visibility = View.GONE
                        binding.tvDescription.visibility = View.GONE
                        binding.cvDescription.visibility = View.GONE
                    } else {
                        binding.tvRemainingText.text =
                            getString(R.string.description_remaining_text, 0)
                        binding.tvRemainingText.visibility = View.VISIBLE
                        binding.tvDescription.visibility = View.VISIBLE
                        binding.cvDescription.visibility = View.VISIBLE
                    }
                } else {
                    if (reason.isSelected == true) {
                        selectedReasons.remove(reason.name)
                    } else {
                        selectedReasons.add(reason.name)
                    }
                    if (selectedReasons.size > 0) {
                        binding.btnDeleteAccount.isEnabled = true
                       // binding.btnDeleteAccount.setTextColor(getColor(R.color.white))
                      //  binding.btnDeleteAccount.setBackgroundResource(R.drawable.d_button_bg_red)
                    } else {
                        binding.btnDeleteAccount.isEnabled = false
                       // binding.btnDeleteAccount.setTextColor(getColor(R.color.black))
                     //   binding.btnDeleteAccount.setBackgroundResource(R.drawable.d_button_bg_disabled)
                    }
                }
            }
        })
        binding.rvReasons.setAdapter(deleteReasonAdapter)

    }

    private fun changeWarningHints(visibility: Int) {
        binding.tvHint3.visibility = visibility
        binding.ivHint3.visibility = visibility
        binding.tvHint4.visibility = visibility
        binding.ivHint4.visibility = visibility
        binding.tvHint5.visibility = visibility
        binding.ivHint5.visibility = visibility
        binding.tvHint6.visibility = visibility
        binding.ivHint6.visibility = visibility
        binding.tvHint7.visibility = visibility
        binding.ivHint7.visibility = visibility
        if (visibility == View.VISIBLE) {
            binding.tvViewMore.text = getString(R.string.view_less)
            binding.ivViewMore.rotation = 180F
        } else {
            binding.tvViewMore.text = getString(R.string.view_more)
            binding.ivViewMore.rotation = 0F
        }
    }
}