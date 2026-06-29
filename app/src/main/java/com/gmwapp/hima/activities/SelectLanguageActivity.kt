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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.gmwapp.hima.mmp.MmpClient
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
import com.gmwapp.hima.utils.applySystemBarInsets
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

    @javax.inject.Inject
    lateinit var apiManager: com.gmwapp.hima.retrofit.ApiManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Light onboarding theme: white status bar with DARK icons, and pad the
        // root for the status/nav bar insets so content sits below the notch.
        applySystemBarInsets(
            binding.root,
            statusBarColor = R.color.white,
            darkStatusBarIcons = true,
        )
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
            if (it != null && it.success && it.data != null) {
                BaseApplication.getInstance()?.getPrefs()?.setUserData(it.data)
                BaseApplication.getInstance()?.getPrefs()?.setAuthenticationToken(it.token)

                // Socket.IO will connect only when ChatActivityInHouse opens
                Log.d("SocketIOCheck", "✅ Registration successful - Socket.IO will connect when chat opens")

                // Registration analytics — fire for BOTH male AND female signups.
                // (2026-05-22 fix: previously male-only, which broke marketing's
                // Google Ads funnel — voice_verified (female-only) appeared to
                // exceed registration (male-only) because female signups were
                // never tracked. Now both genders fire af_complete_registration
                // + EVENT_NAME_COMPLETED_REGISTRATION + SIGN_UP + backend log.)
                MmpClient.trackSignup(customerUserId = "${it.data.id}")

                val params = Bundle()
                params.putString("user_id", "${it.data.id}")
                params.putString("gender", it.data.gender ?: "")
                AppEventsLogger.newLogger(this).logEvent(AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION, params)

                val bundle = Bundle().apply {
                    putString("user_id", "${it.data.id}")
                    putString("gender", it.data.gender ?: "")
                    // 2026-05-22: enrich SIGN_UP for Google Ads conversion-value
                    // tracking. Marketing imports SIGN_UP from Firebase as a
                    // conversion action in Google Ads, and the value+currency
                    // let bid optimisation use realistic per-signup value.
                    // ₹1 is a placeholder LTV — adjust once we have data.
                    putString("method", "phone")
                    putString(FirebaseAnalytics.Param.CURRENCY, "INR")
                    putDouble(FirebaseAnalytics.Param.VALUE, 1.0)
                }

                BaseApplication.firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, bundle)

                AppEventLogger.logEvent(
                    context = this,
                    eventName = "sign_up",
                    platform = "firebase",
                    userId = it.data.id,
                    params = AppEventLogger.bundleToMap(bundle)
                )

                if (it.data.gender == DConstants.MALE) {
                    // Male-only routing: hit /language_config to decide
                    // AI onboarding vs autopay/skip-to-home (mutually
                    // exclusive per language config, see feature/autopay-wireup).
                    val userId = it.data.id
                    val avatarId = getIntent().getIntExtra(DConstants.AVATAR_ID, 0)
                    val language = selectedLanguage ?: ""

                    apiManager.languageConfig(userId, language, object : com.gmwapp.hima.retrofit.callbacks.NetworkCallback<com.gmwapp.hima.retrofit.responses.LanguageConfigResponse> {
                        override fun onResponse(
                            call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.LanguageConfigResponse>,
                            response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.LanguageConfigResponse>
                        ) {
                            val data = response.body()?.data
                            // Fallback default uses the static whitelist if the
                            // server response is empty/null — see fallbackFeature().
                            val feature = data?.enabled_feature ?: fallbackFeature(language)
                            // Persist for runtime gating across the app — same
                            // value used by Wallet/Chat/Home to suppress autopay UI.
                            data?.let {
                                com.gmwapp.hima.utils.LanguageFeatureCache.update(
                                    this@SelectLanguageActivity, it
                                )
                            }
                            routeMaleUserAfterLanguageConfig(userId, avatarId, language, feature)
                        }
                        override fun onFailure(
                            call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.LanguageConfigResponse>,
                            t: Throwable
                        ) {
                            Log.w("SelectLanguage", "language_config failed: ${t.message}")
                            routeMaleUserAfterLanguageConfig(userId, avatarId, language, fallbackFeature(language))
                        }
                        override fun onNoNetwork() {
                            routeMaleUserAfterLanguageConfig(userId, avatarId, language, fallbackFeature(language))
                        }
                    })
                } else {
                    if (it.data.status == 2) {
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra(
                            DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0)
                        )
                        intent.putExtra(DConstants.LANGUAGE, selectedLanguage)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    } else if (it.data.status == 1) {
                        val intent = Intent(this, AlmostDoneActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    } else {
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
            val gender = intent.getStringExtra(DConstants.GENDER).orEmpty()
            val savedReferCode = DPreferences(this).getReferralCode()
            Log.d("savedReferCode","$savedReferCode")
            Log.d("MobileNumberUser","${intent.getStringExtra(DConstants.MOBILE_NUMBER).orEmpty()}")

            if (gender == DConstants.MALE) {
                profileViewModel.register(
                    intent.getStringExtra(DConstants.MOBILE_NUMBER).orEmpty(),
                    selectedLanguage.toString(),
                    intent.getIntExtra(DConstants.AVATAR_ID, 0),
                    gender,
                    savedReferCode,
                    )
            } else {
                profileViewModel.registerFemale(
                    intent.getStringExtra(DConstants.MOBILE_NUMBER).orEmpty(),
                    selectedLanguage.toString(),
                    intent.getIntExtra(DConstants.AVATAR_ID, 0),
                    gender,
                    intent.getStringExtra(DConstants.AGE).orEmpty(),
                    intent.getStringExtra(DConstants.INTERESTS).orEmpty(),
                    intent.getStringExtra(DConstants.SUMMARY).orEmpty(),
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

    /**
     * Static fallback for /language_config when the API returns null/fails.
     * Mirrors the seed data in `language_configs` table — North Indian
     * languages → autopay, South Indian → ai_onboarding. Prevents Hindi
     * users from getting wrongly routed into the AI-onboarding flow on a
     * transient API hiccup. Case-insensitive lookup so admin/curl-created
     * users with non-standard casing (e.g. "hindi") still map correctly.
     */
    private fun fallbackFeature(language: String): String {
        val autopayLanguages = setOf(
            "hindi", "bengali", "assamese", "gujarati",
            "punjabi", "odia", "marathi"
        )
        val aiOnboardingLanguages = setOf(
            "tamil", "telugu", "kannada", "malayalam"
        )
        val key = language.trim().lowercase()
        return when {
            key in autopayLanguages -> "autopay"
            key in aiOnboardingLanguages -> "ai_onboarding"
            else -> "none"
        }
    }

    /**
     * Per-language admin config decides where new male users go after
     * registration. Mutually exclusive (see backend feature/autopay-wireup):
     *   "ai_onboarding" → existing GetNameActivity → AiOnboardingActivity flow
     *   "autopay"       → skip AI onboarding, autopay offer surfaces from home
     *   "none"          → skip AI onboarding, no autopay either
     */
    private fun routeMaleUserAfterLanguageConfig(
        userId: Int, avatarId: Int, language: String, feature: String
    ) {
        val intent = when (feature) {
            "ai_onboarding" -> Intent(this, GetNameActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra(DConstants.AVATAR_ID, avatarId)
                putExtra(DConstants.LANGUAGE, language)
            }
            else -> Intent(this, MainActivity::class.java).apply {
                putExtra(DConstants.AVATAR_ID, avatarId)
                putExtra(DConstants.LANGUAGE, language)
            }
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        if (feature == "ai_onboarding") {
            overridePendingTransition(R.anim.onboarding_transition_in, R.anim.onboarding_transition_out)
        }
        finish()
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