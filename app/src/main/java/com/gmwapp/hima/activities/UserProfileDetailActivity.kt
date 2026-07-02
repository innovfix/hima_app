package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.gmwapp.hima.socket.SocketManager
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
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AddFavoriteResponse
import com.gmwapp.hima.retrofit.responses.CheckFavoriteResponse
import com.gmwapp.hima.retrofit.responses.FemaleNotificationPreferenceResponse
import com.gmwapp.hima.retrofit.responses.GetFemaleNotificationPreferenceResponse
import com.gmwapp.hima.retrofit.responses.RegisterResponse
import com.gmwapp.hima.retrofit.responses.RemoveFavoriteResponse
import com.gmwapp.hima.retrofit.responses.ReportReason
import com.google.android.material.appbar.AppBarLayout
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject
import com.gmwapp.hima.viewmodels.BlockUserViewModel
import com.gmwapp.hima.viewmodels.ReportUserViewModel
import kotlin.math.abs

@AndroidEntryPoint
class UserProfileDetailActivity : AppCompatActivity() {

    @Inject
    lateinit var apiManager: ApiManager

    private lateinit var binding: ActivityUserProfileDetailBinding
    private val friendRequestViewModel: FriendRequestViewModel by viewModels()
    private val blockUserViewModel: BlockUserViewModel by viewModels()
    private val reportUserViewModel: ReportUserViewModel by viewModels()
    
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
    private var isFavorite: Boolean = false
    private var reportReasons: List<ReportReason> = emptyList()
    private var reportReasonsLoadingDialog: AlertDialog? = null
    private var reportDialog: AlertDialog? = null
    private var isUserBlocked: Boolean = false
    private var blockStatusChecked: Boolean = false
    private var isClosingDueToBlock = false
    private var isUpdatingNotifyPreference = false
    private var displayedUserName: String = ""

    // TC_019: live creator-presence updates (toggle-off / logout) pushed via socket.
    private val socketManager = SocketManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from intent
        getUserDataFromIntent()

        // 2026-05-22 — View Content event: log every creator profile view to
        // Meta + Firebase. Marketing uses this for retargeting + lookalike audiences.
        // userId is set inside getUserDataFromIntent (set as profile.id) — pass
        // safely as String to dedupe in Ads Manager.
        val viewedUserId = intent?.getIntExtra("user_id", 0) ?: 0
        if (viewedUserId > 0) {
            com.gmwapp.hima.utils.HimaAnalytics.logViewContent(
                ctx = this,
                contentId = viewedUserId.toString(),
                contentType = "creator_profile",
            )
        }

        // Setup toolbar
        setupToolbar()

        // Populate UI from whatever extras we already have…
        populateUserData()
        // …then refresh from the API so language / interests / about / age
        // appear even when the launcher (chat list, notification, etc.) didn't
        // pass them in the intent.
        if (userId > 0) fetchProfileFromApi()

        // TC_019: update the call buttons live if this creator goes offline/online
        // while we're on her profile — instead of the user tapping a dead "Call".
        observeCreatorPresence()

        // Show profile name in the top bar after collapsing
        setupToolbarTitleOnScroll()
        
        // Setup click listeners
        setupClickListeners()
        
        // Setup observers for ViewModel
        setupObservers()
        
        // Show loading state immediately so the button area isn't blank while the API responds
        binding.btnAddFriendPrimary.visibility = View.VISIBLE
        binding.btnAddFriendPrimary.alpha = 0.5f
        binding.btnAddFriendPrimary.isClickable = false
        binding.tvAddFriendText.text = "Loading..."

        // Check friend request status from API
        checkFriendRequestStatus()
        
        // Check if user is already in favorites
        checkFavoriteStatus()

        // Load report reasons
        loadReportReasons()

        // Load notify-me toggle state for male users viewing female profile
        loadFemaleNotificationPreference()

        // Block status: onResume -> checkBlockStatus()

        window.statusBarColor = Color.parseColor("#ffffff") // startColor of your gradient

        // Make status bar icons light (white) so they're visible on black background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        
        // Debug: Verify button visibility
        Log.d("UserProfileDetail", "Send Friend Request button visibility: ${binding.btnSendFriendRequest.visibility}")
        Log.d("UserProfileDetail", "Action buttons layout visibility: ${binding.llAcceptRejectButtons.visibility}")
    }

    override fun onResume() {
        super.onResume()
        Log.d("BlockUserAPI", "LIFECYCLE onResume peer=$userId — re-checking block status")
        checkBlockStatus()
        // Re-check friend status too: a remove/accept/cancel done elsewhere (e.g. the
        // Friends list) while this profile is backstacked must reflect on return —
        // otherwise the button shows a stale state (e.g. "Request Sent" after a remove).
        checkFriendRequestStatus()
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
                showAppToast(response.message, Toast.LENGTH_SHORT)
                
                // If reject was successful, hide both button and message card
                if (isRejectInProgress) {
                    isRejectInProgress = false
                    binding.btnSendFriendRequest.visibility = View.GONE
                    binding.cvFriendStatus.visibility = View.GONE
                    binding.llAcceptRejectButtons.visibility = View.GONE
                    // Friends-Gated Chat: also hide the Variant-B button (post-reject the
                    // backend reports "request_sent", so re-checking would mislead).
                    binding.btnAddFriendPrimary.visibility = View.GONE
                    binding.tvDeclineFriend.visibility = View.GONE
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
            showAppToast(error, Toast.LENGTH_SHORT)
            // The legacy action row is hidden by default, so a failed
            // check_friend_request would otherwise leave no add-friend button.
            // Render the Variant-B button from the current (default NOT_FRIENDS)
            // status so the screen is never buttonless. A duplicate reverse send
            // is still rejected server-side ("Request already sent…").
            updateUIBasedOnFriendStatus()
        })

        // Observe loading state
        friendRequestViewModel.loadingLiveData.observe(this, Observer { isLoading ->
            // You can show/hide a progress bar here if needed
            binding.btnSendFriendRequest.isEnabled = !isLoading
            binding.btnAcceptFriendRequest.isEnabled = !isLoading
        })

        reportUserViewModel.reportReasonsLiveData.observe(this, Observer { response ->
            Log.d("UserProfileDetail", "Report Reasons Response: $response")
            if (response?.success == true && !response.data.isNullOrEmpty()) {
                reportReasons = response.data ?: emptyList()
                Log.d("UserProfileDetail", "✅ Loaded ${reportReasons.size} report reasons")
            } else {
                Log.e("UserProfileDetail", "❌ Failed to load report reasons: ${response?.message}")
            }
        })

        reportUserViewModel.reportReasonsErrorLiveData.observe(this, Observer { error ->
            if (error.isNullOrBlank()) return@Observer
            Log.e("UserProfileDetail", "❌ Report reasons error: $error")
        })

        reportUserViewModel.reportUserLiveData.observe(this, Observer { response ->
            Log.d("UserProfileDetail", "===== REPORT USER OBSERVER =====")
            Log.d("UserProfileDetail", "Response received: $response")
            Log.d("UserProfileDetail", "Response is null: ${response == null}")
            
            if (response != null) {
                Log.d("UserProfileDetail", "Response success: ${response.success}")
                Log.d("UserProfileDetail", "Response message: ${response.message}")
                Log.d("UserProfileDetail", "Response data: ${response.data}")
                Log.d("UserProfileDetail", "Response error: ${response.error}")
                
                val message = response.message ?: "Report submitted"
                showAppToast(message, Toast.LENGTH_SHORT)
                reportDialog?.dismiss()
                Log.d("UserProfileDetail", "✅ Report submitted successfully")
            } else {
                Log.e("UserProfileDetail", "❌ Response is NULL")
            }
            Log.d("UserProfileDetail", "================================")
        })

        reportUserViewModel.reportUserErrorLiveData.observe(this, Observer { error ->
            Log.e("UserProfileDetail", "===== REPORT USER ERROR OBSERVER =====")
            Log.e("UserProfileDetail", "❌ Report user error: $error")
            Log.e("UserProfileDetail", "====================================")
            showAppToast(error, Toast.LENGTH_SHORT)
        })

        blockUserViewModel.blockUserLiveData.observe(this, Observer { response ->
            if (response != null) {
                showAppToast(response.message, Toast.LENGTH_SHORT)
                isUserBlocked = true
                updateBlockButtonUI()
                Log.d(
                    "BlockUserAPI",
                    "AFTER-BLOCK observed peer=$userId self=${BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id} " +
                        "respBody=$response"
                )
                checkBlockStatus()
            }
        })

        blockUserViewModel.blockUserErrorLiveData.observe(this, Observer { error ->
            Log.e("UserProfileDetail", "❌ Block user error: $error")
            showAppToast(error, Toast.LENGTH_SHORT)
        })
        
        // Observe check block status
        blockUserViewModel.checkBlockStatusLiveData.observe(this, Observer { response ->
            Log.d("UserProfileDetail", "===== CHECK BLOCK STATUS OBSERVER =====")
            Log.d("UserProfileDetail", "Response: $response")
            // TC_027: if the user already left (back-pressed) while this check was
            // in flight, don't re-fire finish()/toast on a closing activity.
            if (isFinishing || isDestroyed) return@Observer

            if (response != null) {
                blockStatusChecked = true

                val statusFromPayload =
                    response.data?.blockedStatus ?: response.blockedStatus
                val isBlockedBool =
                    response.isBlocked == true || response.data?.isBlocked == true
                // API may return 1 or 2 for "blocked" after POST uses blocked=1; treat any non‑zero as blocked
                isUserBlocked = isBlockedBool ||
                    (statusFromPayload != null && statusFromPayload != 0)

                Log.d(
                    "BlockUserAPI",
                    "DECIDE peer=$userId self=${BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id} " +
                        "topIsBlocked=${response.isBlocked} dataIsBlocked=${response.data?.isBlocked} " +
                        "topBlockedStatus=${response.blockedStatus} dataBlockedStatus=${response.data?.blockedStatus} " +
                        "blockedByPeer=${response.data?.blockedByPeer} computedIsUserBlocked=$isUserBlocked"
                )
                updateBlockButtonUI()

                // TC_027: if this creator has blocked the viewer, her profile must not be
                // viewable and no call may be placed — close the screen with a clear
                // message. This is the chokepoint, so it covers every entry point
                // (recent / chat / search / deep link), not just the recent list.
                if (response.data?.blockedByPeer == true && !isClosingDueToBlock) {
                    isClosingDueToBlock = true
                    showAppToast(getString(R.string.peer_calls_blocked), Toast.LENGTH_SHORT)
                    finish()
                    return@Observer
                }
            }
            Log.d("UserProfileDetail", "====================================")
        })
        
        blockUserViewModel.checkBlockStatusErrorLiveData.observe(this, Observer { error ->
            Log.e("UserProfileDetail", "❌ Check block status error: $error")
            blockStatusChecked = true
            // Don't show toast for check errors, just log it
        })
        
        // Observe unblock user
        blockUserViewModel.unblockUserLiveData.observe(this, Observer { response ->
            if (response != null) {
                showAppToast(response.message, Toast.LENGTH_SHORT)
                isUserBlocked = false
                updateBlockButtonUI()
                checkBlockStatus()
            }
        })

        blockUserViewModel.unblockUserErrorLiveData.observe(this, Observer { error ->
            Log.e("UserProfileDetail", "❌ Unblock user error: $error")
            showAppToast(error, Toast.LENGTH_SHORT)
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
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.cvBack.setOnClickListener {
            finish()
        }
    }

    private fun setupToolbarTitleOnScroll() {
        binding.tvToolbarUserName.text = displayedUserName
        binding.tvToolbarUserName.visibility = View.GONE

        binding.appBarLayout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                val isCollapsed = abs(verticalOffset) >= appBarLayout.totalScrollRange
                binding.tvToolbarUserName.visibility = if (isCollapsed) View.VISIBLE else View.GONE
            }
        )
    }

    // TC_011 (B6): only the call type the creator actually enabled should look
    // and act enabled. audioStatus / videoStatus come from the list card's
    // intent extras and are refreshed from the profile API. Previously both
    // buttons were always fully enabled, so a user could see — and even start —
    // a video call on a creator who had only turned audio on.
    // TC_019: when the backend pushes a creator_status_changed for the creator we're
    // viewing, refresh audio/video status and re-apply the button availability so the
    // buttons dim/disable (or re-enable) live — no refetch, no dead "Call" tap.
    private fun observeCreatorPresence() {
        lifecycleScope.launch {
            socketManager.creatorStatusChanged.collect { ev ->
                // TC_019: a presence event can land in the window after the activity
                // starts finishing but before lifecycleScope cancels this collector —
                // skip it so applyCallButtonAvailability() never touches a torn-down
                // view hierarchy.
                if (isFinishing || isDestroyed) return@collect
                if (ev.creatorId == userId) {
                    audioStatus = ev.audioStatus
                    videoStatus = ev.videoStatus
                    applyCallButtonAvailability()
                    Log.d(
                        "UserProfileDetail",
                        "TC_019 live presence: creator=$userId audio=$audioStatus video=$videoStatus online=${ev.online}"
                    )
                }
            }
        }
    }

    private fun applyCallButtonAvailability() {
        val audioEnabled = audioStatus == 1
        val videoEnabled = videoStatus == 1
        binding.btnAudioCall.isEnabled = audioEnabled
        binding.btnAudioCall.isClickable = audioEnabled
        binding.btnAudioCall.alpha = if (audioEnabled) 1f else 0.4f
        binding.btnVideoCall.isEnabled = videoEnabled
        binding.btnVideoCall.isClickable = videoEnabled
        binding.btnVideoCall.alpha = if (videoEnabled) 1f else 0.4f
    }

    private fun populateUserData() {
        displayedUserName = extractNameOnly(userName).ifBlank { userName }

        // v1106 (2026-05-29) — Glide rejects load attempts on a destroyed
        // activity with IllegalArgumentException ("cannot start a load for a
        // destroyed activity"). populateUserData() can be re-invoked from
        // async callbacks (line 607: runOnUiThread { populateUserData() })
        // after the user has already navigated away. 5 users on v1064-v1105
        // hit this in Crashlytics. Guard the load.
        if (isFinishing || isDestroyed) return

        // TC_011: reflect the creator's per-type audio/video toggle on the buttons.
        applyCallButtonAvailability()

        // Set user image
        Glide.with(this)
            .load(userImage)
            .placeholder(R.drawable.star)
            .error(R.drawable.star)
            .centerCrop()
            .into(binding.ivProfileImage)

        // Set user name and age - remove trailing numbers from username
        binding.tvUserName.text = displayedUserName
        if (userAge > 0) {
            binding.tvUserAge.visibility = View.VISIBLE
            binding.tvUserAge.text = "$userAge years old"
        } else {
            binding.tvUserAge.visibility = View.GONE
        }

        // Set language
        if (userLanguage.isNotEmpty()) {
            binding.chipGroupLanguages.visibility = View.VISIBLE
            binding.chipLanguage.text = userLanguage
        } else {
            binding.chipGroupLanguages.visibility = View.GONE
        }

        // Set interests
        if (userInterests.isNotEmpty()) {
            binding.rvInterests.visibility = View.VISIBLE
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
        
        // Report/Block is available to anyone viewing someone else's profile.
        binding.cvReportBlockSection.visibility =
            if (shouldShowReportBlockSection()) View.VISIBLE else View.GONE

        // Show online notify UI only for male users (UI only, logic to be added later)
        val currentUserGender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender?.lowercase()
        if (currentUserGender == DConstants.MALE) {
            binding.cvOnlineNotifySection.visibility = View.VISIBLE
        } else {
            binding.cvOnlineNotifySection.visibility = View.GONE
        }
    }

    private fun setupInterests() {
        // Backend data is inconsistent: some creators store interests with a space after
        // the comma ("Love, Movies") and some without ("Art,Movies,Cooking"). Split on the
        // comma only and trim each token so every interest becomes its own chip with its
        // real icon — regardless of spacing/brackets/quotes.
        val interestsAsString = userInterests
            .trim('[', ']')
            .split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotBlank() }

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

        // v1110 TC_HL_08 FIX: previously the user could tap call buttons on a
        // blocked creator's profile and the call would still be initiated. The
        // chat screen already guards on isCallBlocked but this screen did not.
        // Guard both buttons with the same isUserBlocked flag we already compute
        // from the profile response (line 303-306) and show a clear toast so the
        // user understands why nothing happened.
        binding.btnAudioCall.setOnSingleClickListener {
            if (isUserBlocked) {
                android.widget.Toast.makeText(
                    this,
                    "You can't call this user — you have been blocked.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnSingleClickListener
            }
            // TC_011: block the call type the creator hasn't enabled.
            if (audioStatus != 1) {
                android.widget.Toast.makeText(
                    this,
                    "Audio call isn't available for this creator right now.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnSingleClickListener
            }
            startCall("audio")
        }

        binding.btnVideoCall.setOnSingleClickListener {
            if (isUserBlocked) {
                android.widget.Toast.makeText(
                    this,
                    "You can't call this user — you have been blocked.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnSingleClickListener
            }
            // TC_011: block the call type the creator hasn't enabled.
            if (videoStatus != 1) {
                android.widget.Toast.makeText(
                    this,
                    "Video call isn't available for this creator right now.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnSingleClickListener
            }
            startCall("video")
        }
        
        // Favourite Toggle
        binding.cvFavourite.setOnSingleClickListener {
            toggleFavorite()
        }

        binding.llReportUser.setOnSingleClickListener {
            showReportDialog()
        }

        binding.llBlockUser.setOnSingleClickListener {
            if (isUserBlocked) {
                showUnblockConfirmationDialog()
            } else {
                showBlockConfirmationDialog()
            }
        }

        binding.swNotifyOnline.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingNotifyPreference) return@setOnCheckedChangeListener
            updateFemaleNotificationPreference(isChecked)
        }
    }

    /**
     * Keeps [ChatListAdapter] bell state in sync: it reads `notify_online_prefs` / `notify_<id>` only.
     */
    private fun mirrorNotifyPrefToLocal(femaleUserId: Int, enabled: Boolean) {
        if (femaleUserId <= 0) return
        com.gmwapp.hima.utils.NotifyOnlinePrefsHelper.setSubscribedWithMeta(
            this,
            femaleUserId,
            enabled,
            displayedUserName,
            userImage
        )
    }

    private fun updateFemaleNotificationPreference(isEnabled: Boolean) {
        val maleUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (maleUserId == 0 || userId == 0) {
            showAppToast("Unable to update preference. Please try again.", Toast.LENGTH_SHORT)
            rollbackNotifyToggle(!isEnabled)
            return
        }

        val status = if (isEnabled) 1 else 0
        isUpdatingNotifyPreference = true
        binding.swNotifyOnline.isEnabled = false

        apiManager.setFemaleNotificationPreference(
            maleUserId = maleUserId,
            femaleUserId = userId,
            status = status,
            callback = object : NetworkCallback<FemaleNotificationPreferenceResponse> {
                override fun onResponse(
                    call: Call<FemaleNotificationPreferenceResponse>,
                    response: Response<FemaleNotificationPreferenceResponse>
                ) {
                    binding.swNotifyOnline.isEnabled = true

                    if (response.isSuccessful && response.body()?.success == true) {
                        showAppToast(response.body()?.message ?: "Preference updated successfully.", Toast.LENGTH_SHORT)
                        mirrorNotifyPrefToLocal(userId, isEnabled)
                        isUpdatingNotifyPreference = false
                        return
                    }

                    val errorMessage = extractNotifyPreferenceErrorMessage(response)
                    showAppToast(errorMessage, Toast.LENGTH_SHORT)
                    rollbackNotifyToggle(!isEnabled)
                    isUpdatingNotifyPreference = false
                }

                override fun onFailure(call: Call<FemaleNotificationPreferenceResponse>, t: Throwable) {
                    binding.swNotifyOnline.isEnabled = true
                    rollbackNotifyToggle(!isEnabled)
                    isUpdatingNotifyPreference = false
                    showAppToast(DConstants.LOGIN_ERROR, Toast.LENGTH_SHORT)
                }

                override fun onNoNetwork() {
                    binding.swNotifyOnline.isEnabled = true
                    rollbackNotifyToggle(!isEnabled)
                    isUpdatingNotifyPreference = false
                    showAppToast(DConstants.NO_NETWORK, Toast.LENGTH_SHORT)
                }
            }
        )
    }

    /**
     * Fills in language / interests / about / age / image from the backend.
     * Callers (chat list, notification, push tap) often only pass id + name +
     * image, so without this fetch the profile page shows blank "Languages" /
     * "Interests" / "About Me" sections.
     */
    private fun fetchProfileFromApi() {
        apiManager.getUser(userId, object : NetworkCallback<RegisterResponse> {
            override fun onResponse(
                call: Call<RegisterResponse>,
                response: Response<RegisterResponse>
            ) {
                if (!response.isSuccessful) return
                val data = response.body()?.data ?: return

                if (userLanguage.isBlank()) userLanguage = data.language.orEmpty()
                if (userInterests.isBlank()) userInterests = data.interests.orEmpty()
                if (userAbout.isBlank()) userAbout = data.describe_yourself.orEmpty()
                if (userAge <= 0) userAge = data.age ?: 0
                if (userImage.isBlank() && data.image.isNotBlank()) userImage = data.image
                // TC_011: let the freshly-fetched status win in BOTH directions, so a
                // creator who just turned a call type OFF is reflected (the old
                // one-way "promote 0→1 only" left a stale-enabled button). Keep the
                // existing value when the API omits the field (null) instead of
                // forcing it to 0 and disabling a known-available type.
                data.audio_status?.let { audioStatus = it }
                data.video_status?.let { videoStatus = it }

                runOnUiThread { populateUserData() }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                Log.w("UserProfileDetail", "getUser failed: ${t.message}")
            }

            override fun onNoNetwork() {
                Log.w("UserProfileDetail", "getUser skipped: no network")
            }
        })
    }

    private fun loadFemaleNotificationPreference() {
        val currentUserGender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender?.lowercase()
        if (currentUserGender != DConstants.MALE) return

        val maleUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (maleUserId == 0 || userId == 0) return

        binding.swNotifyOnline.isEnabled = false

        apiManager.getFemaleNotificationPreference(
            maleUserId = maleUserId,
            femaleUserId = userId,
            callback = object : NetworkCallback<GetFemaleNotificationPreferenceResponse> {
                override fun onResponse(
                    call: Call<GetFemaleNotificationPreferenceResponse>,
                    response: Response<GetFemaleNotificationPreferenceResponse>
                ) {
                    binding.swNotifyOnline.isEnabled = true

                    if (response.isSuccessful && response.body()?.success == true) {
                        val isEnabled = response.body()?.data?.status == 1
                        isUpdatingNotifyPreference = true
                        binding.swNotifyOnline.isChecked = isEnabled
                        isUpdatingNotifyPreference = false
                        mirrorNotifyPrefToLocal(userId, isEnabled)
                    } else {
                        // Keep default state (off) if API fails
                        isUpdatingNotifyPreference = true
                        binding.swNotifyOnline.isChecked = false
                        isUpdatingNotifyPreference = false
                    }
                }

                override fun onFailure(call: Call<GetFemaleNotificationPreferenceResponse>, t: Throwable) {
                    binding.swNotifyOnline.isEnabled = true
                    isUpdatingNotifyPreference = true
                    binding.swNotifyOnline.isChecked = false
                    isUpdatingNotifyPreference = false
                }

                override fun onNoNetwork() {
                    binding.swNotifyOnline.isEnabled = true
                    isUpdatingNotifyPreference = true
                    binding.swNotifyOnline.isChecked = false
                    isUpdatingNotifyPreference = false
                }
            }
        )
    }

    private fun extractNotifyPreferenceErrorMessage(
        response: Response<FemaleNotificationPreferenceResponse>
    ): String {
        response.body()?.message?.takeIf { it.isNotBlank() }?.let { return it }

        val rawError = response.errorBody()?.string()
        if (!rawError.isNullOrBlank()) {
            return try {
                val parsed = com.google.gson.Gson().fromJson(
                    rawError,
                    FemaleNotificationPreferenceResponse::class.java
                )
                parsed?.message ?: "Failed to update preference."
            } catch (_: Exception) {
                "Failed to update preference."
            }
        }

        return "Failed to update preference."
    }

    private fun rollbackNotifyToggle(previousValue: Boolean) {
        isUpdatingNotifyPreference = true
        binding.swNotifyOnline.isChecked = previousValue
        isUpdatingNotifyPreference = false
    }

    private fun loadReportReasons() {
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (currentUserId == 0) {
            return
        }
        reportUserViewModel.getReportReasons(currentUserId)
    }

    private fun showReportDialog() {
        if (reportReasons.isNotEmpty()) {
            showReportReasonPickerDialog()
            return
        }
        if (reportReasonsLoadingDialog?.isShowing == true) {
            return
        }

        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (currentUserId == 0) {
            showAppToast(R.string.report_unable_to_identify_user, Toast.LENGTH_SHORT)
            return
        }

        reportReasonsLoadingDialog = AlertDialog.Builder(this)
            .setMessage(R.string.loading_report_reasons)
            .setCancelable(true)
            .setOnDismissListener {
                reportReasonsLoadingDialog = null
            }
            .create()
        reportReasonsLoadingDialog?.show()

        reportUserViewModel.getReportReasons(currentUserId) { reasons ->
            reportReasonsLoadingDialog?.dismiss()
            reportReasonsLoadingDialog = null
            if (!reasons.isNullOrEmpty()) {
                reportReasons = reasons
                showReportReasonPickerDialog()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(R.string.report_reasons_unavailable_title)
                    .setMessage(R.string.report_reasons_unavailable_message)
                    .setPositiveButton(R.string.retry) { _, _ -> showReportDialog() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun showReportReasonPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_report_user, null)
        val chipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_group_reasons)
        val reasonInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_reason_text)
        val reasonEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_reason_text)

        var selectedReason: ReportReason? = null

        chipGroup.removeAllViews()
        reportReasons.forEach { reason ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = reason.reason
                isCheckable = true
                tag = reason
                
                // Styling for unselected state
                chipBackgroundColor = resources.getColorStateList(R.color.chip_background_selector, null)
                setTextColor(resources.getColorStateList(R.color.chip_text_selector, null))
                chipStrokeWidth = 3f
                chipStrokeColor = resources.getColorStateList(R.color.chip_stroke_selector, null)
                chipCornerRadius = 24f
                
                // Typography
                textSize = 13f
                setTextAppearance(R.style.ChipTextAppearance)
            }
            chipGroup.addView(chip)
        }

        reasonInputLayout.visibility = View.GONE
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedId = checkedIds.firstOrNull()
            selectedReason = selectedId?.let { id ->
                val chip = group.findViewById<com.google.android.material.chip.Chip>(id)
                chip.tag as? ReportReason
            }
            val requiresText = selectedReason?.requires_text == 1
            reasonInputLayout.visibility = if (requiresText) View.VISIBLE else View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
            .setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_submit)
            .setOnClickListener {
                val reason = selectedReason
                if (reason == null) {
                    showAppToast("Please select a reason", Toast.LENGTH_SHORT)
                    return@setOnClickListener
                }
                val reasonText = reasonEditText.text?.toString()?.trim()
                if (reason.requires_text == 1 && reasonText.isNullOrEmpty()) {
                    showAppToast("Please provide details", Toast.LENGTH_SHORT)
                    return@setOnClickListener
                }
                submitReport(reason.id, reasonText)
            }

        reportDialog = dialog
        dialog.show()
    }

    private fun submitReport(reasonId: Int, reasonText: String?) {
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (currentUserId == 0) {
            showAppToast("Unable to submit report. Please try again.", Toast.LENGTH_SHORT)
            return
        }
        reportUserViewModel.reportUser(currentUserId, userId, reasonId, reasonText)
    }

    private fun showBlockConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_block_user_confirmation, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
            .setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_block)
            .setOnClickListener {
                blockUser()
                dialog.dismiss()
            }

        dialog.show()
    }

    private fun blockUser() {
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (currentUserId == 0) {
            showAppToast("Unable to block user. Please try again.", Toast.LENGTH_SHORT)
            return
        }
        Log.d("BlockUserAPI", "USER-INTENT block peer=$userId self=$currentUserId")
        blockUserViewModel.blockUser(currentUserId, userId, 1)
    }
    
    private fun unblockUser() {
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (currentUserId == 0) {
            showAppToast("Unable to unblock user. Please try again.", Toast.LENGTH_SHORT)
            return
        }
        Log.d("BlockUserAPI", "USER-INTENT unblock peer=$userId self=$currentUserId")
        blockUserViewModel.unblockUser(currentUserId, userId)
    }
    
    private fun checkBlockStatus() {
        if (!shouldShowReportBlockSection()) return

        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (currentUserId == 0) {
            Log.e("UserProfileDetail", "Unable to check block status - no current user ID")
            return
        }

        Log.d("UserProfileDetail", "🔍 Checking block status for user $userId")
        blockUserViewModel.checkBlockStatus(currentUserId, userId)
    }

    /**
     * Report/Block is shown for anyone viewing another user's profile. We hide it only when
     * the target id is missing or matches the current user (self-profile entry points).
     */
    private fun shouldShowReportBlockSection(): Boolean {
        if (userId <= 0) return false
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (currentUserId == 0) return false
        return userId != currentUserId
    }
    
    private fun updateBlockButtonUI() {
        if (isUserBlocked) {
            binding.tvBlockUser.text = "Unblock user"
            Log.d("UserProfileDetail", "✅ Updated UI to show Unblock")
        } else {
            binding.tvBlockUser.text = "Block user"
            Log.d("UserProfileDetail", "✅ Updated UI to show Block")
        }
    }
    
    private fun showUnblockConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_unblock_user_confirmation, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
            .setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_unblock)
            .setOnClickListener {
                unblockUser()
                dialog.dismiss()
            }

        dialog.show()
    }

    private fun checkFriendRequestStatus() {
        // Get current user ID from preferences
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        
        if (currentUserId == 0) {
            showAppToast("Unable to load user data. Please try again.", Toast.LENGTH_SHORT)
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
            showAppToast("Unable to send friend request. Please try again.", Toast.LENGTH_SHORT)
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
            showAppToast("Unable to accept friend request. Please try again.", Toast.LENGTH_SHORT)
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
            showAppToast("Unable to reject friend request. Please try again.", Toast.LENGTH_SHORT)
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
            showAppToast("You must be friends to make a call", Toast.LENGTH_SHORT)
            return
        }

        // Female callers are recipients, not payers — route them through the
        // female call flow so they don't hit the male coin gate. Mirrors the
        // pattern in FavouriteFragment / ChatListAdapter.
        val callerGender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
        val activityClass = if (callerGender == DConstants.FEMALE) {
            com.gmwapp.hima.agora.female.FemaleCallConnectingActivity::class.java
        } else {
            MaleCallConnectingActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
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
        // Friends-Gated Chat (Variant B): a single prominent Add-Friend button under the
        // About Me card drives every state, shown for all viewers (friends UI everywhere).
        // The legacy bottom action row + status card are kept hidden.
        binding.llCallButtons.visibility = View.GONE
        binding.btnSendFriendRequest.visibility = View.GONE
        binding.llAcceptRejectButtons.visibility = View.GONE
        binding.cvFriendStatus.visibility = View.GONE
        binding.llActionButtons.visibility = View.GONE

        val card = binding.btnAddFriendPrimary
        val label = binding.tvAddFriendText
        val decline = binding.tvDeclineFriend
        card.visibility = View.VISIBLE
        card.alpha = 1f
        card.isClickable = true
        decline.visibility = View.GONE
        when (currentFriendStatus) {
            FriendStatus.NOT_FRIENDS -> {
                label.text = "Add Friend"
                card.setOnClickListener { sendFriendRequest() }
            }
            FriendStatus.REQUEST_SENT -> {
                label.text = "Request Sent"
                card.alpha = 0.5f
                card.isClickable = false
                card.setOnClickListener(null)
            }
            FriendStatus.REQUEST_RECEIVED -> {
                label.text = "Accept Friend Request"
                card.setOnClickListener { acceptFriendRequest() }
                decline.visibility = View.VISIBLE
                decline.setOnClickListener { rejectFriendRequest() }
            }
            FriendStatus.FRIENDS -> {
                label.text = "✓ Friends — Remove"
                card.setOnClickListener { confirmRemoveFriend() }
            }
        }
    }

    private fun confirmRemoveFriend() {
        // Friends-Gated Chat: branded avatar-confirm dialog (replaces the plain AlertDialog).
        val view = layoutInflater.inflate(R.layout.dialog_remove_friend, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )

        val name = displayedUserName.ifBlank { userName }.ifBlank { "this user" }
        view.findViewById<android.widget.TextView>(R.id.tv_dialog_title).text = "Remove $name?"

        val avatar = view.findViewById<android.widget.ImageView>(R.id.iv_dialog_avatar)
        if (userImage.isNotBlank() && !isDestroyed && !isFinishing) {
            Glide.with(this).load(userImage).placeholder(R.drawable.star).into(avatar)
        }

        view.findViewById<View>(R.id.btn_dialog_keep).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btn_dialog_remove).setOnClickListener {
            dialog.dismiss()
            removeFriend()
        }
        dialog.show()
    }

    private fun removeFriend() {
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (currentUserId == 0) {
            showAppToast("Unable to remove friend. Please try again.", Toast.LENGTH_SHORT)
            return
        }
        // status = 2 on an accepted friendship -> backend deletes the row (B6), so the
        // pair can re-friend later. The sendFriendRequest observer re-checks status after.
        friendRequestViewModel.sendFriendRequest(
            senderId = currentUserId,
            receiverId = userId,
            status = 2
        )
    }

    // For testing purposes - simulate different friend statuses
    private fun simulateFriendStatus(status: FriendStatus) {
        currentFriendStatus = status
        updateUIBasedOnFriendStatus()
    }

    /**
     * Extracts the name part from username by removing trailing numbers
     * Examples: "hello12" -> "hello", "john5" -> "john", "user123" -> "user"
     */
    private fun extractNameOnly(username: String): String {
        if (username.isEmpty()) return username
        return com.gmwapp.hima.utils.PeerNameUtils.sanitizePeerName(username)
    }

    /**
     * Check if the current user has already favorited this profile
     */
    private fun checkFavoriteStatus() {
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0

        if (currentUserId == 0) {
            Log.e("UserProfileDetail", "Unable to check favorite status - invalid user ID")
            binding.cvFavourite.visibility = View.GONE
            return
        }

        // Hide on self-profile — you can't favourite yourself. The favourite
        // toggle itself is gender-agnostic (both male and creator/female users
        // can favourite other users); FavouriteFragment already filters by the
        // viewer's gender so the result list is correct on either side.
        if (currentUserId == userId) {
            binding.cvFavourite.visibility = View.GONE
            return
        }

        // Reset visibility in case a previous run on the same activity instance
        // hid the heart (e.g. self-profile check above on a previous call).
        binding.cvFavourite.visibility = View.VISIBLE

        Log.d("UserProfileDetail", "🔍 Checking favorite status for user: $userId")
        
        apiManager.checkFavorite(currentUserId, userId, object : NetworkCallback<CheckFavoriteResponse> {
            override fun onResponse(call: Call<CheckFavoriteResponse>, response: Response<CheckFavoriteResponse>) {
                if (response.isSuccessful) {
                    val result = response.body()
                    isFavorite = result?.is_favorite ?: false
                    runOnUiThread {
                        updateFavoriteUI()
                    }
                    Log.d("UserProfileDetail", "✅ Favorite status: $isFavorite")
                } else {
                    Log.e("UserProfileDetail", "❌ Failed to check favorite status: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<CheckFavoriteResponse>, t: Throwable) {
                Log.e("UserProfileDetail", "❌ Error checking favorite status: ${t.message}")
            }

            override fun onNoNetwork() {
                Log.e("UserProfileDetail", "❌ No network while checking favorite status")
                runOnUiThread {
                    showAppToast("No internet connection", Toast.LENGTH_SHORT)
                }
            }
        })
    }
    
    /**
     * Toggle favorite status (add or remove from favorites)
     */
    private fun toggleFavorite() {
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        
        if (currentUserId == 0) {
            showAppToast("Unable to update favorites. Please try again.", Toast.LENGTH_SHORT)
            return
        }
        
        if (isFavorite) {
            // Remove from favorites
            removeFromFavorites(currentUserId)
        } else {
            // Add to favorites
            addToFavorites(currentUserId)
        }
    }
    
    /**
     * Add user to favorites
     */
    private fun addToFavorites(currentUserId: Int) {
        Log.d("UserProfileDetail", "➕ Adding user $userId to favorites")
        
        apiManager.addFavorite(currentUserId, userId, object : NetworkCallback<AddFavoriteResponse> {
            override fun onResponse(call: Call<AddFavoriteResponse>, response: Response<AddFavoriteResponse>) {
                Log.d("UserProfileDetail", "➕ ${response.body()}")

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        isFavorite = true
                        runOnUiThread {
                            updateFavoriteUI()
                            showAppToast(result.message ?: "Added to favorites", Toast.LENGTH_SHORT)
                        }
                        Log.d("UserProfileDetail", "✅ Added to favorites successfully")
                    } else {
                        runOnUiThread {
                            showAppToast(result?.message ?: "Failed to add to favorites", Toast.LENGTH_SHORT)
                        }
                    }
                } else {
                    runOnUiThread {
                        showAppToast("Failed to add to favorites", Toast.LENGTH_SHORT)
                    }
                }
            }

            override fun onFailure(call: Call<AddFavoriteResponse>, t: Throwable) {
                Log.e("UserProfileDetail", "❌ Error adding to favorites: ${t.message}")
                runOnUiThread {
                    showAppToast("Network error. Please try again.", Toast.LENGTH_SHORT)
                }
            }

            override fun onNoNetwork() {
                Log.e("UserProfileDetail", "❌ No network while adding favorite")
                runOnUiThread {
                    showAppToast("No internet connection", Toast.LENGTH_SHORT)
                }
            }
        })
    }
    
    /**
     * Remove user from favorites
     */
    private fun removeFromFavorites(currentUserId: Int) {
        Log.d("UserProfileDetail", "➖ Removing user $userId from favorites")
        
        apiManager.removeFavorite(currentUserId, userId, object : NetworkCallback<RemoveFavoriteResponse> {
            override fun onResponse(call: Call<RemoveFavoriteResponse>, response: Response<RemoveFavoriteResponse>) {
                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        isFavorite = false
                        runOnUiThread {
                            updateFavoriteUI()
                            showAppToast(result.message ?: "Removed from favorites", Toast.LENGTH_SHORT)
                        }
                        Log.d("UserProfileDetail", "✅ Removed from favorites successfully")
                    } else {
                        runOnUiThread {
                            showAppToast(result?.message ?: "Failed to remove from favorites", Toast.LENGTH_SHORT)
                        }
                    }
                } else {
                    runOnUiThread {
                        showAppToast("Failed to remove from favorites", Toast.LENGTH_SHORT)
                    }
                }
            }

            override fun onFailure(call: Call<RemoveFavoriteResponse>, t: Throwable) {
                Log.e("UserProfileDetail", "❌ Error removing from favorites: ${t.message}")
                runOnUiThread {
                    showAppToast("Network error. Please try again.", Toast.LENGTH_SHORT)
                }
            }

            override fun onNoNetwork() {
                Log.e("UserProfileDetail", "❌ No network while removing favorite")
                runOnUiThread {
                    showAppToast("No internet connection", Toast.LENGTH_SHORT)
                }
            }
        })
    }
    
    /**
     * Update the favorite icon based on current favorite state
     */
    private fun updateFavoriteUI() {
        if (isFavorite) {
            // Show filled heart
            binding.ivFavourite.setImageResource(R.drawable.ic_heart_filled)
            binding.ivFavourite.setColorFilter(resources.getColor(R.color.colorAccent, null))
        } else {
            // Show outline heart
            binding.ivFavourite.setImageResource(R.drawable.ic_heart_outline)
            binding.ivFavourite.setColorFilter(resources.getColor(R.color.white, null))
        }
    }
}

