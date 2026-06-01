package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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
import com.gmwapp.hima.databinding.ActivitySelectLanguageBinding
import com.gmwapp.hima.retrofit.responses.Interests
import com.gmwapp.hima.retrofit.responses.Language
import com.gmwapp.hima.utils.applySystemBarInsets
import com.gmwapp.hima.utils.registerOnboardingBackConfirm
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.showOnboardingBackConfirm
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
    private var interestsListAdapter: FemaleInterestsListAdapter? = null
    private var selectedInterests: ArrayList<String> = ArrayList()
    private var isValidAge = false;
    private var isContinueLoading = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFemaleAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Pink gradient AppBarLayout extends under the status bar via
        // fitsSystemWindows=true in the layout. Use LIGHT (white) icons.
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = false
        initUI()
    }

    override fun onResume() {
        super.onResume()
        setContinueLoading(false)
    }

    private fun initUI() {
        // LAI-133: same back policy as the other onboarding screens — only
        // confirm when the user has typed something they'd lose.
        binding.ivBack.setOnClickListener {
            if (hasUnsavedInput()) showOnboardingBackConfirm() else finish()
        }
        registerOnboardingBackConfirm(shouldConfirm = { hasUnsavedInput() })

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

        val staggeredGridLayoutManager = FlexboxLayoutManager(this).apply {
            flexWrap = FlexWrap.WRAP
            alignItems = AlignItems.FLEX_START
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.FLEX_START
        }
        val itemDecoration = FlexboxItemDecoration(this).apply {
            setDrawable(ContextCompat.getDrawable(this@FemaleAboutActivity, R.drawable.bg_divider))
            setOrientation(FlexboxItemDecoration.VERTICAL)
        }
        binding.rvInterests.addItemDecoration(itemDecoration)
        binding.rvInterests.setLayoutManager(staggeredGridLayoutManager)
        interestsListAdapter = FemaleInterestsListAdapter(this, arrayListOf(
            Interests(
                getString(R.string.politics), R.drawable.politics, false
            ),
            Interests(
                getString(R.string.art), R.drawable.art, false
            ),
            Interests(
                getString(R.string.sports), R.drawable.sports, false
            ),
            Interests(
                getString(R.string.movies), R.drawable.movie, false
            ),
            Interests(
                getString(R.string.music), R.drawable.music, false
            ),
            Interests(
                getString(R.string.foodie), R.drawable.foodie, false
            ),
            Interests(
                getString(R.string.travel), R.drawable.travel, false
            ),
            Interests(
                getString(R.string.photography), R.drawable.photography, false
            ),
            Interests(
                getString(R.string.love), R.drawable.love, false
            ),
            Interests(
                getString(R.string.cooking), R.drawable.cooking, false
            ),
        ), false, object : OnItemSelectionListener<Interests> {
            override fun onItemSelected(interest: Interests) {
                if (interest.isSelected == true) {
                    selectedInterests.remove(interest.name)
                } else {
                    selectedInterests.add(interest.name)
                }
                interestsListAdapter?.updateLimitReached(selectedInterests.size == 4)
                updateButton()
            }
        })
        binding.rvInterests.setAdapter(interestsListAdapter)

    }

    private fun hasUnsavedInput(): Boolean {
        val hasAge = binding.etEnterYourAge.text?.toString()?.trim()?.isNotEmpty() == true
        val hasSummary = binding.etSummary.text?.toString()?.trim()?.isNotEmpty() == true
        val hasInterests = selectedInterests.isNotEmpty()
        return hasAge || hasSummary || hasInterests
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