package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.appsflyer.AppsFlyerLib
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.LanguageAdapter
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivitySelectLanguageBinding
import com.gmwapp.hima.retrofit.responses.Language
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.AppEventLogger
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.widgets.SpacesItemDecoration
import com.gmwapp.hima.socket.SocketManager
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class SelectLanguageActivity : BaseActivity() {
    lateinit var binding: ActivitySelectLanguageBinding
    private val profileViewModel: ProfileViewModel by viewModels()
    private var selectedLanguage: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
    }

    private fun initUI() {
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvLanguages.layoutManager = layoutManager
        binding.ivBack.setOnSingleClickListener {
            handleBackPress()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                this@SelectLanguageActivity.handleBackPress()
            }
        })
        profileViewModel.registerErrorLiveData.observe(this, Observer {
            setContinueLoading(false)
            showAppToast(it, Toast.LENGTH_LONG)
        })
        profileViewModel.registerLiveData.observe(this, Observer {
            setContinueLoading(false)
            if (it!=null && it.success) {
                BaseApplication.getInstance()?.getPrefs()?.setUserData(it.data)
                BaseApplication.getInstance()?.getPrefs()?.setAuthenticationToken(it.token)
                
                // Socket.IO will connect only when ChatActivityInHouse opens
                Log.d("SocketIOCheck", "✅ Registration successful - Socket.IO will connect when chat opens")

                if (it.data?.gender == DConstants.MALE) {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra(
                        DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0)
                    )


                    val registrationEvent = HashMap<String, Any>()
                    registrationEvent["user_id"] = "${it.data.id}"  // Optional custom parameter

                    AppsFlyerLib.getInstance().logEvent(
                        this,
                        "af_complete_registration",
                        registrationEvent
                    )



                    val params = Bundle()
                    params.putString("user_id", "${it.data.id}") // optional
                    AppEventsLogger.newLogger(this).logEvent(AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION, params)

                    val bundle = Bundle().apply {
                        putString("user_id", "${it.data.id}") // optional: useful for debugging
                    }

                    BaseApplication.firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, bundle)

                    // Log to backend (only Firebase events)
                    AppEventLogger.logEvent(
                        context = this,
                        eventName = "sign_up",
                        platform = "firebase",
                        userId = it.data.id,
                        params = AppEventLogger.bundleToMap(bundle)
                    )


                    intent.putExtra(DConstants.LANGUAGE, selectedLanguage)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                } else {
                    if(it.data?.status == 2){
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra(
                            DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0)
                        )
                        intent.putExtra(DConstants.LANGUAGE, selectedLanguage)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    } else if(it.data?.status == 1){
                        val intent = Intent(this, AlmostDoneActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    } else{
                        val intent = Intent(this, VoiceIdentificationActivity::class.java)
                        intent.putExtra(DConstants.LANGUAGE, selectedLanguage)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    }
                }
            } else {
                showAppToast(it?.message ?: "An unknown error occurred", Toast.LENGTH_LONG)
            }
        })
        binding.btnContinue.setOnSingleClickListener {
            if (selectedLanguage.isNullOrEmpty()) {
                showAppToast("Please select a language", Toast.LENGTH_SHORT)
                return@setOnSingleClickListener
            }
            setContinueLoading(true)
            val gender = intent.getStringExtra(DConstants.GENDER).toString()
            val savedReferCode = DPreferences(this).getReferralCode()

            if (gender == DConstants.MALE) {
                profileViewModel.register(
                    intent.getStringExtra(DConstants.MOBILE_NUMBER).toString(),
                    selectedLanguage.toString(),
                    intent.getIntExtra(DConstants.AVATAR_ID, 0),
                    gender,
                    savedReferCode,
                    )
            } else {
                profileViewModel.registerFemale(
                    intent.getStringExtra(DConstants.MOBILE_NUMBER).toString(),
                    selectedLanguage.toString(),
                    intent.getIntExtra(DConstants.AVATAR_ID, 0),
                    gender,
                    intent.getStringExtra(DConstants.AGE).toString(),
                    intent.getStringExtra(DConstants.INTERESTS).toString(),
                    intent.getStringExtra(DConstants.SUMMARY).toString(),
                    savedReferCode,
                )
            }
        }
        val interestsListAdapter = LanguageAdapter(this, arrayListOf(
            Language(getString(R.string.tamil), R.drawable.tamil, false),
            Language(getString(R.string.telugu), R.drawable.telugu, false),
            Language(getString(R.string.malayalam), R.drawable.malayalam, false),
            Language(getString(R.string.kannada), R.drawable.kannada, false),
            Language(getString(R.string.hindi), R.drawable.hindi, false),
            Language(getString(R.string.punjabi), R.drawable.punjabi, false),
            Language(getString(R.string.marathi), R.drawable.marathi, false),
            Language(getString(R.string.bengali), R.drawable.bengali, false),
            Language(getString(R.string.assamese), R.drawable.assamese, false),
            Language(getString(R.string.odia), R.drawable.odia, false),
            Language(getString(R.string.gujarati), R.drawable.gujarati, false),
        ), object : OnItemSelectionListener<Language> {
            override fun onItemSelected(language: Language) {
                selectedLanguage = language.name
                updateContinueButtonState()
          //      binding.btnContinue.setBackgroundResource(R.drawable.d_button_bg_white)
            }

        }

        )
        binding.rvLanguages.setAdapter(interestsListAdapter)
        updateContinueButtonState()
    }

    private fun setContinueLoading(isLoading: Boolean) {
        binding.pbContinueLoader.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnContinue.text = if (isLoading) "" else getString(R.string.continue_text)
        if (isLoading) {
            binding.btnContinue.isEnabled = false
        } else {
            // Keep button clickable to show toast when no language is selected.
            binding.btnContinue.isEnabled = true
            updateContinueButtonState()
        }
    }

    private fun updateContinueButtonState() {
        val hasSelection = !selectedLanguage.isNullOrEmpty()
        // Keep button clickable so users get toast feedback when no language is selected.
        binding.btnContinue.isEnabled = true
        binding.btnContinue.backgroundTintList = if (hasSelection) {
            resources.getColorStateList(R.color.colorAccent, null)
        } else {
            resources.getColorStateList(R.color.kyc_button_disabled, null)
        }
    }

    private fun hasUnsavedInput(): Boolean {
        return !selectedLanguage.isNullOrEmpty()
    }

    private fun handleBackPress() {
        if (!hasUnsavedInput()) {
            finish()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_discard_changes, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btn_keep_editing).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btn_go_back).setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.show()
    }

}