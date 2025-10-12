package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.InterestsFemaleListAdapter
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityUserProfileDetailBinding
import com.gmwapp.hima.retrofit.responses.Interests
import com.gmwapp.hima.utils.Helper
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.chip.Chip

class UserProfileDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileDetailBinding
    
    // User data from intent
    private var userId: Int = 0
    private var userName: String = ""
    private var userImage: String = ""
    private var userLanguage: String = ""
    private var userInterests: String = ""
    private var userAbout: String = ""
    private var userAge: Int = 0
    private var audioStatus: Int = 0
    private var videoStatus: Int = 0
    
    // Friend status states
    private enum class FriendStatus {
        NOT_FRIENDS,
        REQUEST_SENT,
        REQUEST_RECEIVED,
        FRIENDS
    }
    
    private var currentFriendStatus: FriendStatus = FriendStatus.NOT_FRIENDS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from intent
        getUserDataFromIntent()
        
        // Setup toolbar
        setupToolbar()
        
        // Populate UI
        populateUserData()
        
        // Setup click listeners
        setupClickListeners()
        
        // Update UI based on friend status (Mock data for now)
        updateUIBasedOnFriendStatus()
        
        // Debug: Verify button visibility
        Log.d("UserProfileDetail", "Send Friend Request button visibility: ${binding.btnSendFriendRequest.visibility}")
        Log.d("UserProfileDetail", "Action buttons layout visibility: ${binding.llActionButtons.visibility}")
    }

    private fun getUserDataFromIntent() {
        userId = intent.getIntExtra(DConstants.USER_ID, 0)
        userName = intent.getStringExtra("USER_NAME") ?: "Unknown"
        userImage = intent.getStringExtra("USER_IMAGE") ?: ""
        userLanguage = intent.getStringExtra("USER_LANGUAGE") ?: ""
        userInterests = intent.getStringExtra("USER_INTERESTS") ?: ""
        userAbout = intent.getStringExtra("USER_ABOUT") ?: ""
        userAge = intent.getIntExtra("USER_AGE", 0)
        audioStatus = intent.getIntExtra("AUDIO_STATUS", 0)
        videoStatus = intent.getIntExtra("VIDEO_STATUS", 0)
        
        Log.d("UserProfileDetail", "User ID: $userId, Name: $userName")
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun populateUserData() {
        // Set user image
        Glide.with(this)
            .load(userImage)
            .placeholder(R.drawable.star)
            .error(R.drawable.star)
            .centerCrop()
            .into(binding.ivProfileImage)

        // Set user name and age
        binding.tvUserName.text = userName
        if (userAge > 0) {
            binding.tvUserAge.text = "$userAge years old"
        } else {
            binding.tvUserAge.visibility = View.GONE
        }

        // Set language
        if (userLanguage.isNotEmpty()) {
            binding.chipLanguage.text = userLanguage
        } else {
            binding.chipGroupLanguages.visibility = View.GONE
        }

        // Set interests
        if (userInterests.isNotEmpty()) {
            setupInterests()
        } else {
            binding.rvInterests.visibility = View.GONE
        }

        // Set about
        if (userAbout.isNotEmpty()) {
            binding.tvAbout.text = userAbout
        } else {
            binding.tvAbout.text = "No description available"
        }
    }

    private fun setupInterests() {
        val interestsAsString = userInterests.trim('[', ']').split(", ")
        
        val staggeredGridLayoutManager = FlexboxLayoutManager(this).apply {
            flexWrap = FlexWrap.WRAP
            alignItems = AlignItems.FLEX_START
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.FLEX_START
        }

        binding.rvInterests.layoutManager = staggeredGridLayoutManager

        val interests = arrayListOf<Interests>()
        interestsAsString.forEach { interestName ->
            if (interestName.isNotBlank()) {
                interests.add(Helper.getInterestObject(this, interestName))
            }
        }

        val interestsListAdapter = InterestsFemaleListAdapter(
            this, 
            interests, 
            false, 
            object : OnItemSelectionListener<Interests> {
                override fun onItemSelected(interest: Interests) {
                    // Handle interest item selection if needed
                }
            }
        )

        binding.rvInterests.adapter = interestsListAdapter
    }

    private fun setupClickListeners() {
        // Send Friend Request
        binding.btnSendFriendRequest.setOnSingleClickListener {
            sendFriendRequest()
        }

        // Accept Friend Request
        binding.btnAcceptFriendRequest.setOnSingleClickListener {
            acceptFriendRequest()
        }

        // Audio Call (for friends only)
        binding.btnAudioCall.setOnSingleClickListener {
            startCall("audio")
        }

        // Video Call (for friends only)
        binding.btnVideoCall.setOnSingleClickListener {
            startCall("video")
        }
    }

    private fun sendFriendRequest() {
        // TODO: API call to send friend request
        Log.d("UserProfileDetail", "Sending friend request to user: $userId")
        
        // Update UI to show request sent
        currentFriendStatus = FriendStatus.REQUEST_SENT
        updateUIBasedOnFriendStatus()
        
        // Show toast or snackbar
        android.widget.Toast.makeText(this, "Friend request sent to $userName", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun acceptFriendRequest() {
        // TODO: API call to accept friend request
        Log.d("UserProfileDetail", "Accepting friend request from user: $userId")
        
        // Update UI to show they are now friends
        currentFriendStatus = FriendStatus.FRIENDS
        updateUIBasedOnFriendStatus()
        
        // Show toast or snackbar
        android.widget.Toast.makeText(this, "You are now friends with $userName", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun startCall(callType: String) {
        if (currentFriendStatus != FriendStatus.FRIENDS) {
            android.widget.Toast.makeText(this, "You must be friends to make a call", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, MaleCallConnectingActivity::class.java).apply {
            putExtra(DConstants.CALL_TYPE, callType)
            putExtra(DConstants.RECEIVER_ID, userId)
            putExtra(DConstants.RECEIVER_NAME, userName)
            putExtra(DConstants.CALL_ID, 0)
            putExtra(DConstants.IMAGE, userImage)
            putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
            putExtra(DConstants.TEXT, getString(R.string.wait_user_hint, userName))
        }
        FcmUtils.isUserAvailable = 1
        startActivity(intent)
    }

    private fun updateUIBasedOnFriendStatus() {
        when (currentFriendStatus) {
            FriendStatus.NOT_FRIENDS -> {
                // Show send friend request button
                binding.btnSendFriendRequest.visibility = View.VISIBLE
                binding.btnAcceptFriendRequest.visibility = View.GONE
                binding.llCallButtons.visibility = View.GONE
                binding.cvFriendStatus.visibility = View.GONE
            }
            
            FriendStatus.REQUEST_SENT -> {
                // Show status that request is sent
                binding.btnSendFriendRequest.visibility = View.GONE
                binding.btnAcceptFriendRequest.visibility = View.GONE
                binding.llCallButtons.visibility = View.GONE
                binding.cvFriendStatus.visibility = View.VISIBLE
                binding.tvFriendStatus.text = "Friend request sent"
            }
            
            FriendStatus.REQUEST_RECEIVED -> {
                // Show accept friend request button
                binding.btnSendFriendRequest.visibility = View.GONE
                binding.btnAcceptFriendRequest.visibility = View.VISIBLE
                binding.llCallButtons.visibility = View.GONE
                binding.cvFriendStatus.visibility = View.GONE
            }
            
            FriendStatus.FRIENDS -> {
                // Show call buttons and friend status
                binding.btnSendFriendRequest.visibility = View.GONE
                binding.btnAcceptFriendRequest.visibility = View.GONE
                binding.llCallButtons.visibility = View.VISIBLE
                binding.cvFriendStatus.visibility = View.VISIBLE
                binding.tvFriendStatus.text = "You are friends"
                
                // Enable/disable call buttons based on status
                if (audioStatus == 1) {
                    binding.btnAudioCall.isEnabled = true
                    binding.btnAudioCall.alpha = 1.0f
                } else {
                    binding.btnAudioCall.isEnabled = false
                    binding.btnAudioCall.alpha = 0.5f
                }
                
                if (videoStatus == 1) {
                    binding.btnVideoCall.isEnabled = true
                    binding.btnVideoCall.alpha = 1.0f
                } else {
                    binding.btnVideoCall.isEnabled = false
                    binding.btnVideoCall.alpha = 0.5f
                }
            }
        }
    }

    // For testing purposes - simulate different friend statuses
    private fun simulateFriendStatus(status: FriendStatus) {
        currentFriendStatus = status
        updateUIBasedOnFriendStatus()
    }
}

