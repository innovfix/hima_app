package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
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
import com.gmwapp.hima.viewmodels.FriendRequestViewModel
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UserProfileDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileDetailBinding
    private val friendRequestViewModel: FriendRequestViewModel by viewModels()
    
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
    private var isRejectInProgress = false

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
        
        // Setup observers for ViewModel
        setupObservers()
        
        // Check friend request status from API
        checkFriendRequestStatus()
        
        // Debug: Verify button visibility
        Log.d("UserProfileDetail", "Send Friend Request button visibility: ${binding.btnSendFriendRequest.visibility}")
        Log.d("UserProfileDetail", "Action buttons layout visibility: ${binding.llAcceptRejectButtons.visibility}")
    }

    private fun setupObservers() {
        // Observe check friend request status
        friendRequestViewModel.checkFriendRequestLiveData.observe(this, Observer { response ->
            if (response != null) {
                Log.d("UserProfileDetail", "✅ Friend request status checked: ${response.message}")
                
                // Update UI based on the message from API
                when (response.message) {
                    "You are friends" -> {
                        currentFriendStatus = FriendStatus.FRIENDS
                    }
                    "Friend request already sent" -> {
                        currentFriendStatus = FriendStatus.REQUEST_SENT
                    }
                    "Accept friend request" -> {
                        currentFriendStatus = FriendStatus.REQUEST_RECEIVED
                    }
                    else -> {
                        currentFriendStatus = FriendStatus.NOT_FRIENDS
                    }
                }
                
                updateUIBasedOnFriendStatus()
            }
        })
        
        // Observe friend request send/accept/reject responses
        friendRequestViewModel.sendFriendRequestLiveData.observe(this, Observer { response ->
            if (response != null) {
                Log.d("UserProfileDetail", "✅ Friend request action successful: ${response.message}")
                
                // Show success message
                Toast.makeText(this, response.message, Toast.LENGTH_SHORT).show()
                
                // If reject was successful, hide both button and message card
                if (isRejectInProgress) {
                    isRejectInProgress = false
                    binding.btnSendFriendRequest.visibility = View.GONE
                    binding.cvFriendStatus.visibility = View.GONE
                    binding.llAcceptRejectButtons.visibility = View.GONE
                    Log.d("UserProfileDetail", "✅ Reject successful - hiding all UI elements")
                } else {
                    // Re-check friend request status to update UI for other actions
                    checkFriendRequestStatus()
                }
            }
        })

        // Observe friend request errors
        friendRequestViewModel.friendRequestErrorLiveData.observe(this, Observer { error ->
            Log.e("UserProfileDetail", "❌ Friend request error: $error")
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        })

        // Observe loading state
        friendRequestViewModel.loadingLiveData.observe(this, Observer { isLoading ->
            // You can show/hide a progress bar here if needed
            binding.btnSendFriendRequest.isEnabled = !isLoading
            binding.btnAcceptFriendRequest.isEnabled = !isLoading
        })
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

        // Reject Friend Request (in layout)
        binding.btnRejectFriendRequest.setOnSingleClickListener {
            rejectFriendRequest()
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

    private fun checkFriendRequestStatus() {
        // Get current user ID from preferences
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        
        if (currentUserId == 0) {
            Toast.makeText(this, "Unable to load user data. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("UserProfileDetail", "🔍 Checking friend request status between user $currentUserId and user: $userId")
        
        // Call API to check friend request status
        friendRequestViewModel.checkFriendRequest(
            senderId = currentUserId,
            receiverId = userId,
            userId = currentUserId
        )
    }

    private fun sendFriendRequest() {
        // Get current user ID from preferences
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        
        if (currentUserId == 0) {
            Toast.makeText(this, "Unable to send friend request. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("UserProfileDetail", "📤 Sending friend request from user $currentUserId to user: $userId")
        
        // Call API using ViewModel (status = 0 for new request)
        friendRequestViewModel.sendFriendRequest(
            senderId = currentUserId,
            receiverId = userId,
            status = 0
        )
    }

    private fun acceptFriendRequest() {
        // Get current user ID from preferences
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        
        if (currentUserId == 0) {
            Toast.makeText(this, "Unable to accept friend request. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("UserProfileDetail", "✅ Accepting friend request from user: $userId to $currentUserId")
        
        // Call API using ViewModel (status = 1 for accept)
        friendRequestViewModel.sendFriendRequest(
            senderId = userId,  // The other user is the sender
            receiverId = currentUserId,  // Current user is the receiver
            status = 1  // 1 = accept
        )
    }

    private fun rejectFriendRequest() {
        // Get current user ID from preferences
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        
        if (currentUserId == 0) {
            Toast.makeText(this, "Unable to reject friend request. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("UserProfileDetail", "❌ Rejecting friend request from user: $userId")
        
        isRejectInProgress = true
        
        // Call API using ViewModel (status = 3 for reject)
        friendRequestViewModel.sendFriendRequest(
            senderId = userId,  // The other user is the sender
            receiverId = currentUserId,  // Current user is the receiver
            status = 3  // 3 = reject
        )
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
                binding.llAcceptRejectButtons.visibility = View.GONE
                binding.llCallButtons.visibility = View.GONE
                binding.cvFriendStatus.visibility = View.GONE
                binding.btnSendFriendRequest.isEnabled = true
            }
            
            FriendStatus.REQUEST_SENT -> {
                // Show status that request is sent (hide button, show message)
                binding.btnSendFriendRequest.visibility = View.GONE
                binding.llAcceptRejectButtons.visibility = View.GONE
                binding.llCallButtons.visibility = View.GONE
                binding.cvFriendStatus.visibility = View.VISIBLE
                binding.tvFriendStatus.text = "Friend request sent"
            }
            
            FriendStatus.REQUEST_RECEIVED -> {
                // Show accept and reject friend request buttons
                binding.btnSendFriendRequest.visibility = View.GONE
                binding.llAcceptRejectButtons.visibility = View.VISIBLE
                binding.llCallButtons.visibility = View.GONE
                binding.cvFriendStatus.visibility = View.GONE
            }
            
            FriendStatus.FRIENDS -> {
                // Show call buttons and friend status
                binding.btnSendFriendRequest.visibility = View.GONE
                binding.llAcceptRejectButtons.visibility = View.GONE
                binding.llCallButtons.visibility = View.GONE
                binding.cvFriendStatus.visibility = View.VISIBLE
                binding.tvFriendStatus.text = "You are friends"
            }
        }
    }

    // For testing purposes - simulate different friend statuses
    private fun simulateFriendStatus(status: FriendStatus) {
        currentFriendStatus = status
        updateUIBasedOnFriendStatus()
    }
}

