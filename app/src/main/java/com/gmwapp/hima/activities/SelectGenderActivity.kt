package com.gmwapp.hima.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.PagerSnapHelper
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.AvatarsListAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivitySelectGenderBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.onesignal.OneSignal
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class SelectGenderActivity : BaseActivity() {
    lateinit var binding: ActivitySelectGenderBinding
    private val profileViewModel: ProfileViewModel by viewModels()
    private var selectedGender = "male"
    private var avatarsListAdapter: AvatarsListAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectGenderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
    }

    override fun onResume() {
        super.onResume()
        setContinueLoading(false)
    }

    private fun initUI() {
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.rvAvatars)
        setCenterLayoutManager(binding.rvAvatars)
        
        binding.ivBack.setOnSingleClickListener {
            finish()
        }
        
        binding.btnContinue.setOnSingleClickListener {
            setContinueLoading(true)
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
        
        // Set initial male selection
        updateGenderSelection(true)
    }

    private fun setContinueLoading(isLoading: Boolean) {
        binding.pbContinueLoader.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnContinue.text = if (isLoading) "" else getString(R.string.continue_text)
        binding.btnContinue.isEnabled = !isLoading
    }
    
    private fun updateGenderSelection(isMale: Boolean) {
        val accentColor = ColorStateList.valueOf(getColor(R.color.colorAccent))
        val dividerColor = ColorStateList.valueOf(getColor(R.color.divider))
        val greyColor = getColor(R.color.grey_medium)
        val accentTextColor = getColor(R.color.colorAccent)
        val whiteColor = getColor(R.color.white)
        val greyLightColor = getColor(R.color.grey_extra_light)
        
        if (isMale) {
            // Male selected
            binding.cvMale.setStrokeColor(accentColor)
            binding.cvMale.strokeWidth = 6
            binding.cvMale.setCardBackgroundColor(whiteColor)
            binding.btnMale.setTextColor(accentTextColor)
            binding.iconMale.setBackgroundResource(R.drawable.circle_bg_accent)
            binding.iconMale.setTextColor(whiteColor)
            
            // Female unselected
            binding.cvFemale.setStrokeColor(dividerColor)
            binding.cvFemale.strokeWidth = 3
            binding.cvFemale.setCardBackgroundColor(whiteColor)
            binding.btnFemale.setTextColor(greyColor)
            binding.iconFemale.setBackgroundResource(R.drawable.circle_bg_grey)
            binding.iconFemale.setTextColor(greyColor)
        } else {
            // Female selected
            binding.cvFemale.setStrokeColor(accentColor)
            binding.cvFemale.strokeWidth = 6
            binding.cvFemale.setCardBackgroundColor(whiteColor)
            binding.btnFemale.setTextColor(accentTextColor)
            binding.iconFemale.setBackgroundResource(R.drawable.circle_bg_accent)
            binding.iconFemale.setTextColor(whiteColor)
            
            // Male unselected
            binding.cvMale.setStrokeColor(dividerColor)
            binding.cvMale.strokeWidth = 3
            binding.cvMale.setCardBackgroundColor(whiteColor)
            binding.btnMale.setTextColor(greyColor)
            binding.iconMale.setBackgroundResource(R.drawable.circle_bg_grey)
            binding.iconMale.setTextColor(greyColor)
        }
    }

}