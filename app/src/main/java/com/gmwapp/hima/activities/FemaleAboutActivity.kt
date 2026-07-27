package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.FemaleInterestsListAdapter
import com.gmwapp.hima.adapters.InterestsListAdapter
import com.gmwapp.hima.adapters.LanguageAdapter
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityFemaleAboutBinding
import com.gmwapp.hima.databinding.AdapterInterestFemalePillBinding
import com.gmwapp.hima.databinding.ActivitySelectLanguageBinding
import com.gmwapp.hima.retrofit.responses.Interests
import com.gmwapp.hima.retrofit.responses.Language
import com.gmwapp.hima.utils.applySystemBarInsets
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.widgets.SpacesItemDecoration
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxItemDecoration
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class FemaleAboutActivity : BaseActivity() {
    lateinit var binding: ActivityFemaleAboutBinding
    private val interestChips = ArrayList<Pair<AdapterInterestFemalePillBinding, Interests>>()
    private var selectedInterests: ArrayList<String> = ArrayList()
    private var isValidAge = false;
    private var isContinueLoading = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFemaleAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Light onboarding theme: white status bar with DARK icons, and pad the
        // root for the status/nav bar insets so content sits below the notch.
        applySystemBarInsets(
            binding.root,
            statusBarColor = R.color.white,
            darkStatusBarIcons = true,
        )
        setupKeyboardLift()
        initUI()
    }

    /**
     * BUG #26 (same root cause as Bug #9) — lift the content above the soft keyboard.
     *
     * AppTheme sets windowIsTranslucent=true app-wide, so Android IGNORES adjustResize
     * (a translucent window is never resized for the IME); the low "Give us a quick
     * summary about you" field just sits UNDER the keyboard. Measure the keyboard height
     * off the visible display frame, pad the scroll's content column by it so there's
     * room to scroll, then bring the focused summary field into view. Keyboard-closed the
     * padding is 0, so the resting layout is unchanged.
     */
    private fun setupKeyboardLift() {
        val root = binding.root
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            root.getWindowVisibleDisplayFrame(rect)
            val screenH = root.rootView.height
            val keypad = screenH - rect.bottom
            // >15% of the screen ⇒ keyboard, not just the nav/status bars.
            val kbHeight = if (keypad > screenH * 0.15) keypad else 0
            if (binding.contentColumn.paddingBottom != kbHeight) {
                binding.contentColumn.updatePadding(bottom = kbHeight)
                if (kbHeight > 0 && binding.etSummary.hasFocus()) {
                    binding.svDetails.post {
                        binding.svDetails.smoothScrollTo(0, binding.cvSummary.bottom)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setContinueLoading(false)
    }

    private fun initUI() {
        binding.ivBack.setOnClickListener {
            handleBackPress()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                this@FemaleAboutActivity.handleBackPress()
            }
        })

      //  binding.cvEnterYourAge.setBackgroundResource(R.drawable.card_view_border)
  //      binding.cvSummary.setBackgroundResource(R.drawable.card_view_border)

//        binding.etEnterYourAge.setOnTouchListener { v, _ ->
//            binding.cvEnterYourAge.setBackgroundResource(R.drawable.card_view_border_age_selected)
//            false
//        }

        binding.etEnterYourAge.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.toString().isNotEmpty() && s.toString().toInt() < 18) {
                    isValidAge = false
                   // binding.cvEnterYourAge.setBackgroundResource(R.drawable.card_view_border_error)
                    binding.tvEnterYourAgeHint.text =
                        getString(R.string.you_must_be_at_least_18_years_old)
                    binding.tvEnterYourAgeHint.setTextColor(getColor(R.color.Red))
                } else if (s.toString().isNotEmpty() && s.toString().toInt() > 99) {
                    isValidAge = false
                  //  binding.cvEnterYourAge.setBackgroundResource(R.drawable.card_view_border_error)
                    binding.tvEnterYourAgeHint.text =
                        getString(R.string.you_must_be_below_100_years_old)
                    binding.tvEnterYourAgeHint.setTextColor(getColor(R.color.Red))
                } else{
                    isValidAge = true
                   // binding.cvEnterYourAge.setBackgroundResource(R.drawable.d_button_bg_user_name)
                    binding.tvEnterYourAgeHint.text =
                        getString(R.string.enter_your_age_hint)
                    binding.tvEnterYourAgeHint.setTextColor(getColor(R.color.interest_disabled_text_color))
                }
                updateButton()
            }

            override fun afterTextChanged(s: Editable) {
            }
        })
        binding.tvRemainingText.text = getString(
            R.string.description_remaining_text,
            0
        )
        binding.etSummary.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                if (!TextUtils.isEmpty(s)) {
                    binding.tvRemainingText.text = getString(
                        R.string.description_remaining_text, s.length
                    )
                }

                updateButton()


            }

            override fun afterTextChanged(s: Editable) {
                updateButton()
            }
        })
        binding.btnContinue.setOnSingleClickListener {
            val age = binding.etEnterYourAge.text.toString()
            val interests = selectedInterests.toString()
            val summary = binding.etSummary.text.toString().trim().replace("\\s+".toRegex(), " ")

            if (age.isEmpty()) {
                showAppToast("Please enter your age", Toast.LENGTH_SHORT)
                return@setOnSingleClickListener
            }
            if (!isValidAge) {
                showAppToast("Please enter a valid age", Toast.LENGTH_SHORT)
                return@setOnSingleClickListener
            }
            if (selectedInterests.isEmpty()) {
                showAppToast("Please select at least 1 interest", Toast.LENGTH_SHORT)
                return@setOnSingleClickListener
            }
            if (summary.length < 15) {
                showAppToast("Minimum 15 letters required", Toast.LENGTH_SHORT)
                return@setOnSingleClickListener
            }

            // Marketing funnel — Details Entered (creator age / bio / interests).
            com.gmwapp.hima.utils.HimaAnalytics.logDetailsEntered(
                this,
                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            )
            setContinueLoading(true)
            val intent = Intent(this, SelectLanguageActivity::class.java)
            intent.putExtra(DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID,0))
            intent.putExtra(
                DConstants.MOBILE_NUMBER, getIntent().getStringExtra(DConstants.MOBILE_NUMBER)
            )
            intent.putExtra(DConstants.GENDER, getIntent().getStringExtra(DConstants.GENDER))
            intent.putExtra(DConstants.AGE, age)
            intent.putExtra(DConstants.INTERESTS, interests)
            intent.putExtra(DConstants.SUMMARY, summary)
            startActivity(intent)
        }

        setupInterestChips()
    }

    /**
     * Build the interest pills directly into the [FlexboxLayout]. We use a static
     * FlexboxLayout (not RecyclerView + FlexboxLayoutManager) because the layout
     * manager under-measures its wrap_content height inside a scroll container and
     * clipped the last row of chips.
     */
    private fun setupInterestChips() {
        val interests = arrayListOf(
            Interests(getString(R.string.politics), R.drawable.politics, false),
            Interests(getString(R.string.art), R.drawable.art, false),
            Interests(getString(R.string.sports), R.drawable.sports, false),
            Interests(getString(R.string.movies), R.drawable.movie, false),
            Interests(getString(R.string.music), R.drawable.music, false),
            Interests(getString(R.string.foodie), R.drawable.foodie, false),
            Interests(getString(R.string.travel), R.drawable.travel, false),
            Interests(getString(R.string.photography), R.drawable.photography, false),
            Interests(getString(R.string.love), R.drawable.love, false),
            Interests(getString(R.string.cooking), R.drawable.cooking, false),
        )

        val inflater = layoutInflater
        binding.fblInterests.removeAllViews()
        interestChips.clear()
        interests.forEach { interest ->
            val item = AdapterInterestFemalePillBinding.inflate(inflater, binding.fblInterests, false)
            item.tvInterest.text = interest.name
            item.main.setOnSingleClickListener {
                if (interest.isSelected == true) {
                    interest.isSelected = false
                    selectedInterests.remove(interest.name)
                } else {
                    if (selectedInterests.size >= 4) return@setOnSingleClickListener
                    interest.isSelected = true
                    selectedInterests.add(interest.name)
                }
                refreshInterestChips()
                updateButton()
            }
            interestChips.add(item to interest)
            binding.fblInterests.addView(item.root)
        }
        refreshInterestChips()
    }

    private fun refreshInterestChips() {
        val limitReached = selectedInterests.size >= 4
        interestChips.forEach { (item, interest) ->
            when {
                interest.isSelected == true -> {
                    item.main.isEnabled = true
                    item.main.setBackgroundResource(R.drawable.bg_interest_chip_female_selected)
                    item.tvInterest.setTextColor(getColor(R.color.colorAccent))
                }
                limitReached -> {
                    item.main.isEnabled = false
                    item.main.setBackgroundResource(R.drawable.bg_interest_chip_female_disabled)
                    item.tvInterest.setTextColor(getColor(R.color.interest_disabled_text_color))
                }
                else -> {
                    item.main.isEnabled = true
                    item.main.setBackgroundResource(R.drawable.bg_interest_chip_female)
                    item.tvInterest.setTextColor(getColor(R.color.onboarding_title))
                }
            }
        }
    }

    private fun hasUnsavedInput(): Boolean {
        val hasAge = binding.etEnterYourAge.text?.toString()?.trim()?.isNotEmpty() == true
        val hasSummary = binding.etSummary.text?.toString()?.trim()?.isNotEmpty() == true
        val hasInterests = selectedInterests.isNotEmpty()
        return hasAge || hasSummary || hasInterests
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

    private fun updateButton() {
        if (isContinueLoading) {
            binding.btnContinue.isEnabled = false
            return
        }
        // Keep button clickable to show validation toasts even when form is incomplete.
        binding.btnContinue.isEnabled = true
        if (isValidAge && selectedInterests.size > 0 && binding.etSummary.text.length >= 15) {
            binding.btnContinue.backgroundTintList = ContextCompat.getColorStateList(this, R.color.colorAccent)
        } else {
            binding.btnContinue.backgroundTintList = ContextCompat.getColorStateList(this, R.color.kyc_button_disabled)
        }
    }

    private fun setContinueLoading(isLoading: Boolean) {
        isContinueLoading = isLoading
        binding.pbContinueLoader.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnContinue.text = if (isLoading) "" else getString(R.string.continue_text)
        updateButton()
    }

}