package com.gmwapp.hima.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.PagerSnapHelper
import com.gmwapp.hima.R
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.adapters.AvatarsListAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivitySelectGenderBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.utils.applySystemBarInsets
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.onesignal.OneSignal
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response


@AndroidEntryPoint
class SelectGenderActivity : BaseActivity() {
    lateinit var binding: ActivitySelectGenderBinding
    private val profileViewModel: ProfileViewModel by viewModels()
    private var selectedGender = "male"
    private var avatarsListAdapter: AvatarsListAdapter? = null
    private var entranceAnimator: AnimatorSet? = null
    private var breathingAnimator: ObjectAnimator? = null

    @javax.inject.Inject
    lateinit var apiManager: ApiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectGenderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Light onboarding theme: white status bar with DARK icons, and pad the
        // root for the status/nav bar insets so content sits below the notch.
        applySystemBarInsets(
            binding.root,
            statusBarColor = R.color.white,
            darkStatusBarIcons = true,
        )
        callTrackingInfoFromSavedAddress()
        initUI()
    }

    override fun onResume() {
        super.onResume()
        setContinueLoading(false)
    }

    override fun onDestroy() {
        // Stop the infinite breathing loop so it doesn't leak the activity.
        entranceAnimator?.cancel()
        breathingAnimator?.cancel()
        super.onDestroy()
    }

    private fun initUI() {
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.rvAvatars)
        setCenterLayoutManager(binding.rvAvatars)
        
        binding.ivBack.setOnSingleClickListener {
            handleBackPress()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                this@SelectGenderActivity.handleBackPress()
            }
        })
        
        binding.btnContinue.setOnSingleClickListener {
            setContinueLoading(true)
            // Marketing funnel — Male User Selected / Female Selected.
            com.gmwapp.hima.utils.HimaAnalytics.logGenderSelected(this, selectedGender)
            var intent:Intent? = null
            if (selectedGender == DConstants.MALE) {
                intent = Intent(this, SelectLanguageActivity::class.java)
              //  OneSignal.User.addTag("gender", "male")
            } else {
                intent = Intent(this, FemaleAboutActivity::class.java)
             //   OneSignal.User.addTag("gender", "female")
            }
            val layoutManager = binding.rvAvatars.layoutManager as CenterLayoutManager
            val avatarId =
                profileViewModel.avatarsListLiveData.value?.data?.get(layoutManager.findFirstCompletelyVisibleItemPosition())?.id
            intent.putExtra(DConstants.AVATAR_ID, avatarId)
            intent.putExtra(
                DConstants.MOBILE_NUMBER, getIntent().getStringExtra(DConstants.MOBILE_NUMBER)
            )
            intent.putExtra(DConstants.GENDER, selectedGender)
            startActivity(intent)
            overridePendingTransition(R.anim.onboarding_transition_in, R.anim.onboarding_transition_out)
        }
        
        // Male Card Click
        binding.cvMale.setOnSingleClickListener {
            selectedGender = DConstants.MALE
            profileViewModel.getAvatarsList(selectedGender)
            updateGenderSelection(true)
        }
        
        // Female Card Click
        binding.cvFemale.setOnSingleClickListener {
            selectedGender = DConstants.FEMALE
            profileViewModel.getAvatarsList(selectedGender)
            updateGenderSelection(false)
        }
        
        profileViewModel.getAvatarsList("male")
        profileViewModel.avatarsListLiveData.observe(this, Observer {
            if (it?.data != null) {
                avatarsListAdapter = AvatarsListAdapter(
                    this, it.data
                )
                binding.rvAvatars.setAdapter(avatarsListAdapter)
                binding.rvAvatars.smoothScrollToPosition(0)
                binding.rvAvatars.post {
                    avatarsListAdapter?.setSelectedPosition(0)
                }
            }
        })

        binding.rvAvatars.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager as? CenterLayoutManager ?: return
                    var selectedPos = layoutManager.findFirstCompletelyVisibleItemPosition()
                    if (selectedPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        selectedPos = layoutManager.findFirstVisibleItemPosition()
                    }
                    if (selectedPos >= 0) {
                        avatarsListAdapter?.setSelectedPosition(selectedPos)
                    }
                }
            }
        })
        
        // Set initial male selection (no entrance animation on first paint —
        // just start the idle breathing on the default-selected card).
        updateGenderSelection(true, animateEntrance = false)
    }

    private fun setContinueLoading(isLoading: Boolean) {
        binding.pbContinueLoader.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnContinue.text = if (isLoading) "" else getString(R.string.continue_text)
        binding.btnContinue.isEnabled = !isLoading
    }
    
    private fun updateGenderSelection(isMale: Boolean, animateEntrance: Boolean = true) {
        applyCardState(
            card = binding.cvMale,
            icon = binding.iconMale,
            title = binding.btnMale,
            selected = isMale,
        )
        applyCardState(
            card = binding.cvFemale,
            icon = binding.iconFemale,
            title = binding.btnFemale,
            selected = !isMale,
        )

        val selectedIcon = if (isMale) binding.iconMale else binding.iconFemale
        val otherIcon = if (isMale) binding.iconFemale else binding.iconMale
        animateGenderIcon(selectedIcon, otherIcon, animateEntrance)
    }

    /**
     * Combo motion for the selected gender icon:
     *  1. a 3D coin-flip reveal,
     *  2. a quick wiggle of the ♂/♀ symbol as it lands,
     *  3. then a continuous "breathing" pulse while the card stays selected.
     * The previously-selected icon is reset to rest.
     */
    private fun animateGenderIcon(selected: TextView, other: TextView, entrance: Boolean) {
        // Tear down any in-flight motion so rapid toggles stay clean.
        entranceAnimator?.cancel()
        breathingAnimator?.cancel()
        breathingAnimator = null
        resetIconTransforms(other)
        resetIconTransforms(selected)

        val density = resources.displayMetrics.density
        // Push the 3D camera back so the coin-flip doesn't fish-eye / clip.
        selected.cameraDistance = 8000 * density

        if (entrance) {
            val flip = ObjectAnimator.ofFloat(selected, View.ROTATION_Y, 0f, 360f).apply {
                duration = 600
                interpolator = AccelerateDecelerateInterpolator()
            }
            val wiggle = ObjectAnimator.ofFloat(
                selected, View.ROTATION, 0f, -14f, 12f, -6f, 0f
            ).apply {
                duration = 550
                startDelay = 470
                interpolator = AccelerateDecelerateInterpolator()
            }
            entranceAnimator = AnimatorSet().apply {
                playTogether(flip, wiggle)
                start()
            }
            startBreathing(selected, delayMs = 700)
        } else {
            startBreathing(selected, delayMs = 0)
        }
    }

    private fun startBreathing(icon: TextView, delayMs: Long) {
        val anim = ObjectAnimator.ofPropertyValuesHolder(
            icon,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f),
        ).apply {
            duration = 1200
            startDelay = delayMs
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        breathingAnimator = anim
        anim.start()
    }

    private fun resetIconTransforms(icon: TextView) {
        icon.animate().cancel()
        icon.rotation = 0f
        icon.rotationY = 0f
        icon.scaleX = 1f
        icon.scaleY = 1f
    }

    /** Paint a gender card to match the redesigned selected / unselected look. */
    private fun applyCardState(
        card: com.google.android.material.card.MaterialCardView,
        icon: android.widget.TextView,
        title: android.widget.TextView,
        selected: Boolean,
    ) {
        val density = resources.displayMetrics.density
        if (selected) {
            card.setStrokeColor(ColorStateList.valueOf(getColor(R.color.colorAccent)))
            card.strokeWidth = (2 * density).toInt()
            icon.setBackgroundResource(R.drawable.circle_bg_accent)
            icon.setTextColor(getColor(R.color.white))
            title.setTextColor(getColor(R.color.colorAccent))
        } else {
            card.setStrokeColor(ColorStateList.valueOf(getColor(R.color.onboarding_card_border)))
            card.strokeWidth = (1 * density).toInt()
            icon.setBackgroundResource(R.drawable.circle_bg_grey)
            icon.setTextColor(getColor(R.color.black_light))
            title.setTextColor(getColor(R.color.onboarding_title))
        }
    }

    private fun handleBackPress() {
        // User just passed OTP verification to get here — a slip of the back button
        // shouldn't silently drop them back to login. Confirm the intent.
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

    private fun callTrackingInfoFromSavedAddress() {
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val savedAddress = prefs?.getString("saved_address")

        if (savedAddress.isNullOrBlank()) {
            android.util.Log.d("SelectGenderTracking", "saved_address is empty, skipping tracking_info")
            return
        }

        apiManager.trackingInfoRaw(savedAddress, 0, object : NetworkCallback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                val rawBody = try {
                    response.body()?.string()
                } catch (e: Exception) {
                    null
                }
                android.util.Log.d(
                    "SelectGenderTracking",
                    "tracking_info success code=${response.code()} raw=$rawBody"
                )
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                android.util.Log.e("SelectGenderTracking", "tracking_info failure: ${t.message}", t)
            }

            override fun onNoNetwork() {
                android.util.Log.e("SelectGenderTracking", "tracking_info no network")
            }
        })
    }

}