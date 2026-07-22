package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.gmwapp.hima.widgets.GridSpacingItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
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
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        applyStatusBarPaddingForToolbar()
        initUI()
    }

    private fun applyStatusBarPaddingForToolbar() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appbarLayout) { v, windowInsets ->
            val top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.appbarLayout)
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
            // B_034: enforce the screen's stated requirement — at least one reason (or a
            // typed "Other" description) is mandatory. The button also starts disabled
            // (XML android:enabled="false") and is enabled only by the reason/description
            // listeners; this is the last-line guard against any enabled-state race before
            // routing to Raise Ticket.
            val hasReason = selectedReasons.isNotEmpty() ||
                binding.etDescription.text.toString().trim().isNotEmpty()
            if (!hasReason) {
                showAppToast(getString(R.string.delete_reason_text), Toast.LENGTH_LONG)
                return@setOnSingleClickListener
            }
            // Account deletion is handled via a support ticket (the ticket queue is
            // monitored; the old mail flow was a dead-end). Route the user to Raise Ticket.
            showAppToast(getString(R.string.delete_account_raise_ticket_toast), Toast.LENGTH_LONG)
            startActivity(Intent(this, SubmitTicketActivity::class.java))
        })
        profileViewModel.deleteUserErrorLiveData.observe(this, Observer {
            showAppToast(getString(R.string.please_try_again_later), Toast.LENGTH_LONG)
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
                showAppToast(it?.message, Toast.LENGTH_LONG)
            }
        })
        val spanCount = 2
        binding.rvReasons.layoutManager = GridLayoutManager(this, spanCount)
        val gap = (8 * resources.displayMetrics.density).toInt()
        binding.rvReasons.addItemDecoration(GridSpacingItemDecoration(gap, includeEdge = true))

        binding.etDescription.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.cvDescription.strokeColor = ContextCompat.getColor(this, R.color.colorPrimaryDark)
                binding.cvDescription.strokeWidth = 4
            } else {
                binding.cvDescription.strokeColor = ContextCompat.getColor(this, android.R.color.darker_gray)
                binding.cvDescription.strokeWidth = 2
            }
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
                        // B_040 — scroll the just-revealed description field into view so the
                        // user doesn't have to hunt for it below the reason options. post{}
                        // waits for the visibility change to lay out before scrolling.
                        binding.svDetails.post {
                            binding.svDetails.smoothScrollTo(0, binding.cvDescription.bottom)
                        }
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
        // Get parent LinearLayouts for each warning item
        val hint3Parent = binding.tvHint3.parent as? View
        val hint4Parent = binding.tvHint4.parent as? View
        val hint5Parent = binding.tvHint5.parent as? View
        val hint6Parent = binding.tvHint6.parent as? View
        val hint7Parent = binding.tvHint7.parent as? View
        
        hint3Parent?.visibility = visibility
        hint4Parent?.visibility = visibility
        hint5Parent?.visibility = visibility
        hint6Parent?.visibility = visibility
        hint7Parent?.visibility = visibility
        
        if (visibility == View.VISIBLE) {
            binding.tvViewMore.text = getString(R.string.view_less)
            binding.ivViewMore.rotation = 180F
        } else {
            binding.tvViewMore.text = getString(R.string.view_more)
            binding.ivViewMore.rotation = 0F
        }
    }
}