package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityGetNameBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.showAppToast
import com.gmwapp.hima.viewmodels.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GetNameActivity : BaseActivity() {

    private lateinit var binding: ActivityGetNameBinding
    private val profileViewModel: ProfileViewModel by viewModels()

    private var isValidName = false
    private var isSubmitInProgress = false
    private var userId: Int = 0
    private var avatarId: Int = 0
    private var language: String = "Tamil"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGetNameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Pink gradient header extends under the status bar via
        // fitsSystemWindows=true on the AppBarLayout. Force LIGHT
        // (white) status-bar icons so they're readable on pink.
        WindowInsetsControllerCompat(window, binding.root)
            .isAppearanceLightStatusBars = true

        // User has already registered on the server — there's nothing to go back to.
        // Swallow gesture back presses so a stray swipe doesn't leave them stranded.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op: block back */ }
        })

        userId = intent.getIntExtra("USER_ID", 0)
        avatarId = intent.getIntExtra(DConstants.AVATAR_ID, 0)
        language = intent.getStringExtra(DConstants.LANGUAGE) ?: "Tamil"

        val cached = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        if (userId == 0) {
            userId = cached?.id ?: 0
        }
        // Prod update_profile requires a valid avatar_id. If the previous
        // screens didn't forward one, fall back to the user's stored avatar,
        // then to a safe default.
        if (avatarId == 0) {
            avatarId = cached?.avatar_id?.takeIf { it > 0 } ?: 1
        }

        initUI()
        observeViewModel()
        updateContinueButton()
    }

    private fun initUI() {
        binding.etUserName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handleNameInput(s?.toString().orEmpty().trim())
            }
        })

        binding.btnContinue.setOnSingleClickListener {
            // ── DESIGN DUMMY: skip API for dummy number ──
            if (intent.getStringExtra(DConstants.MOBILE_NUMBER) == "9999900000") {
                val dummyIntent = Intent(this, AiOnboardingActivity::class.java)
                dummyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(dummyIntent)
                return@setOnSingleClickListener
            }
            submit(binding.etUserName.text?.toString().orEmpty().trim())
        }
        animateEntryViews(listOf(binding.llHeader, binding.tvInputLabel, binding.cvUserName, binding.tvUserNameHint, binding.rlContinue))
    }

    private fun handleNameInput(text: String) {
        if (text.length < MIN_NAME_LENGTH) {
            isValidName = false
            binding.pbUserNameLoader.visibility = View.GONE
            binding.ivSuccess.visibility = View.GONE
            binding.ivWarning.visibility = View.GONE
            binding.tvUserNameHint.text = getString(R.string.getname_min_chars)
            binding.tvUserNameHint.setTextColor(getColor(R.color.user_name_hint_text))
        } else {
            // ── DESIGN DUMMY: auto-approve name for dummy number ──
            if (intent.getStringExtra(DConstants.MOBILE_NUMBER) == "9999900000") {
                isValidName = true
                binding.pbUserNameLoader.visibility = View.GONE
                binding.ivSuccess.visibility = View.VISIBLE
                binding.ivWarning.visibility = View.GONE
                binding.tvUserNameHint.text = ""
                updateContinueButton()
                return
            }
            // Length OK — ask the server whether the name is available.
            isValidName = false
            binding.pbUserNameLoader.visibility = View.VISIBLE
            binding.ivSuccess.visibility = View.GONE
            binding.ivWarning.visibility = View.GONE
            profileViewModel.userValidation(userId, text)
        }
        updateContinueButton()
    }

    private fun submit(name: String) {
        if (!isValidName || isSubmitInProgress) return
        if (userId == 0) {
            showAppToast(getString(R.string.please_try_again_later), Toast.LENGTH_LONG)
            return
        }
        isSubmitInProgress = true
        setContinueLoading(true)
        // Production backend still requires a non-empty `interests` on update_profile.
        // Send a harmless default so male onboarding (which has no interests step) can pass.
        profileViewModel.updateProfile(userId, avatarId, name, arrayListOf("general"))
    }

    private fun observeViewModel() {
        profileViewModel.userValidationLiveData.observe(this, Observer { response ->
            if (isSubmitInProgress) return@Observer
            // Bail if the user has since emptied or shortened the field past the threshold.
            val current = binding.etUserName.text?.toString().orEmpty().trim()
            if (current.length < MIN_NAME_LENGTH) return@Observer

            binding.pbUserNameLoader.visibility = View.GONE
            if (response != null && response.success) {
                isValidName = true
                binding.ivSuccess.visibility = View.VISIBLE
                binding.ivWarning.visibility = View.GONE
                binding.tvUserNameHint.text = getString(R.string.getname_name_ok)
                binding.tvUserNameHint.setTextColor(getColor(R.color.user_name_hint_text))
            } else {
                isValidName = false
                binding.ivSuccess.visibility = View.GONE
                binding.ivWarning.visibility = View.VISIBLE
                binding.tvUserNameHint.text = response?.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.please_try_again_later)
                binding.tvUserNameHint.setTextColor(getColor(android.R.color.holo_red_light))
            }
            updateContinueButton()
        })

        profileViewModel.userValidationErrorLiveData.observe(this, Observer {
            if (isSubmitInProgress) return@Observer
            binding.pbUserNameLoader.visibility = View.GONE
            isValidName = false
            binding.ivSuccess.visibility = View.GONE
            binding.ivWarning.visibility = View.VISIBLE
            binding.tvUserNameHint.text = getString(R.string.please_try_again_later)
            binding.tvUserNameHint.setTextColor(getColor(android.R.color.holo_red_light))
            updateContinueButton()
        })

        profileViewModel.updateProfileLiveData.observe(this, Observer { response ->
            if (!isSubmitInProgress) return@Observer
            isSubmitInProgress = false
            setContinueLoading(false)
            if (response != null && response.success && response.data != null) {
                BaseApplication.getInstance()?.getPrefs()?.setUserData(response.data)
                val next = Intent(this, AiOnboardingActivity::class.java).apply {
                    putExtra("USER_ID", userId)
                    putExtra(DConstants.AVATAR_ID, avatarId)
                    putExtra(DConstants.LANGUAGE, language)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(next)
                overridePendingTransition(
                    R.anim.onboarding_transition_in,
                    R.anim.onboarding_transition_out
                )
                finish()
            } else {
                showAppToast(
                    response?.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.please_try_again_later),
                    Toast.LENGTH_LONG
                )
            }
        })

        profileViewModel.updateProfileErrorLiveData.observe(this, Observer {
            if (!isSubmitInProgress) return@Observer
            isSubmitInProgress = false
            setContinueLoading(false)
            showAppToast(getString(R.string.please_try_again_later), Toast.LENGTH_LONG)
        })
    }

    private fun setContinueLoading(loading: Boolean) {
        binding.pbContinueLoader.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnContinue.text = if (loading) "" else getString(R.string.continue_text)
        binding.etUserName.isEnabled = !loading
        updateContinueButton()
    }

    private fun updateContinueButton() {
        val enabled = isValidName && !isSubmitInProgress
        binding.btnContinue.isEnabled = enabled
        binding.btnContinue.backgroundTintList = resources.getColorStateList(
            if (enabled) R.color.colorAccent else R.color.kyc_button_disabled, null
        )
    }

    private fun animateEntryViews(views: List<android.view.View>) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 80f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(380)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    companion object {
        private const val MIN_NAME_LENGTH = 2
    }
}
