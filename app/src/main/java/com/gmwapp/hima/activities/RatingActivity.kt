package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityRatingBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.BlockUserViewModel
import com.gmwapp.hima.viewmodels.RatingViewModel
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AddFavoriteResponse
import com.gmwapp.hima.retrofit.responses.CheckFavoriteResponse
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxItemDecoration
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import dagger.hilt.android.AndroidEntryPoint
import com.gmwapp.hima.utils.applyImmersiveSystemBars

@AndroidEntryPoint
class RatingActivity : BaseActivity() {

    @Inject
    lateinit var apiManager: ApiManager

    val viewModel: RatingViewModel by viewModels()
    val blockUserViewModel: BlockUserViewModel by viewModels()


    lateinit var binding: ActivityRatingBinding
    private lateinit var reviewItemsMap: Map<Int, List<String>> // Declare without initializing
    private var selectedRating: Int = 0 // Add a variable to track selected rating
    private var selectedReviewPosition: Int = RecyclerView.NO_POSITION // Track the selected review position
    private var discription: String = ""
    var isBlocked = false
    
    // Track API completion status
    private var wasRatingApiCalled = false
    private var wasFavoriteApiCalled = false
    private var isRatingApiCompleted = false
    private var isFavoriteApiCompleted = false
    // Friends-Gated Chat (Variant A): instant action-chip state
    private var isFavouriteSelected = false
    private var isFriendRequestSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRatingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize reviewItemsMap here when the context is ready
        reviewItemsMap = mapOf(
            5 to listOf(getString(R.string.fun_conversation), getString(R.string.help_advice), getString(R.string.friendly_conversation), getString(R.string.pleasant_voice)),
            4 to listOf(getString(R.string.fun_conversation), getString(R.string.help_advice), getString(R.string.friendly_conversation), getString(R.string.pleasant_voice)),
            3 to listOf(getString(R.string.boring), getString(R.string.disinterested), getString(R.string.bad_conversation), getString(R.string.lack_of_enthusiasm)),
            2 to listOf(getString(R.string.not_replying), getString(R.string.abusive_language), getString(R.string.rude_behaviour), getString(R.string.bad_connectivity)),
            1 to listOf(getString(R.string.not_replying), getString(R.string.abusive_language), getString(R.string.rude_behaviour), getString(R.string.bad_connectivity))
        )

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // BUG #25: lift the content above the keyboard by padding the bottom with the
            // IME inset instead of letting adjustPan slide the WHOLE window up (which
            // dragged the title/subtitle over the status bar). systemBars.top is kept, so
            // the top never moves; the NestedScrollView shrinks and scrolls the focused
            // comments field into view. ime.bottom already includes the nav bar → max()
            // avoids double-counting; keyboard-closed ime.bottom = 0 (unchanged layout).
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom)
            )
            insets
        }

        initUi()
        showBlockToogle()
        showFavouriteToggle()
        setupActionChips()

        // Add listener to review text input for validation
        binding.etUserName.addTextChangedListener {
            validatebtn() // Validate the button whenever the user types
            // Bug #11 — keep the character counter in sync with what's typed
            // (was frozen at "0/100" because tv_char_count was never updated).
            binding.tvCharCount.text = "${it?.length ?: 0}/100"
        }


        applyImmersiveSystemBars()

        // Dark background — render status-bar icons LIGHT (white).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

        // UI-only ambience: drifting "smoke" glows behind the dark feedback form.
        run {
            val d = resources.displayMetrics.density
            fun loop(a: android.animation.ObjectAnimator, dur: Long) {
                a.duration = dur
                a.repeatCount = android.animation.ObjectAnimator.INFINITE
                a.repeatMode = android.animation.ObjectAnimator.REVERSE
                a.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                a.start()
            }
            fun driftGlow(v: android.view.View, dx: Float, dy: Float, durX: Long, durY: Long) {
                loop(android.animation.ObjectAnimator.ofFloat(v, "translationX", dx), durX)
                loop(android.animation.ObjectAnimator.ofFloat(v, "translationY", dy), durY)
                loop(android.animation.ObjectAnimator.ofFloat(v, "scaleX", 1.28f), durX + 1500)
                loop(android.animation.ObjectAnimator.ofFloat(v, "scaleY", 1.28f), durY + 1500)
                loop(android.animation.ObjectAnimator.ofFloat(v, "alpha", 0.45f, 1f), durX)
            }
            driftGlow(binding.glowTl, 85f * d, 95f * d, 6200, 8000)
            driftGlow(binding.glowBr, -82f * d, -90f * d, 7000, 9200)
        }
        }

    private fun initUi() {

        // Close button click listeners
        binding.ivClose.setOnClickListener { finish() }
        binding.cvClose.setOnClickListener { finish() }

        FcmUtils.greyScreenLiveData.postValue("NoData")





        // Set title text with dynamic receiver name. Backend usernames often
        // carry a trailing numeric id (e.g. "Chandana572") — strip those digits
        // so the user sees just the display name.
        val rawName = intent.getStringExtra(DConstants.RECEIVER_NAME) ?: "User"
        val displayName = rawName.replace(Regex("\\d+$"), "").trim().ifBlank { "User" }
        binding.tvTitle.text = getString(R.string.review_hint, displayName)

        // Check if female user AND call type was video, then call API for call duration
        val gender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
        val callType = intent.getStringExtra(DConstants.CALL_TYPE)
        
        if (gender == "female") {
            val callId = intent.getIntExtra(DConstants.CALL_ID, 0)
            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            if (callId != 0 && userId != null) {
                viewModel.getUserCallDuration(callId, userId)
            }
        }

        // Observe call duration response
        viewModel.userCallDurationLiveData.observe(this) { response ->
            if (response != null) {
                // Show dialog only if success=false AND valid=1
                if (!response.success && response.valid == 1) {
                    showCallDurationDialog(response.title, response.message)
                }
                // If success=true or valid!=1, don't show anything
            }
        }

        viewModel.ratingResponseLiveData.observe(this, Observer { response ->
            if (response != null && response.success) {
                // Handle successful rating submission
                showAppToast("Rating submitted successfully", Toast.LENGTH_SHORT)
                isRatingApiCompleted = true
            } else {
                // Handle failure
                //    showAppToast("Rating submission failed", Toast.LENGTH_SHORT)
                isRatingApiCompleted = true
            }
            
            // Check if we can close the activity now
            checkAndCloseActivity()
        })



        binding.btnSubmit.setOnSingleClickListener {

            val userid = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            val call_userid = intent.getIntExtra(DConstants.RECEIVER_ID, 0)

            var gender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender

            val isBlocking = gender == "female" && binding.cbBlockUser.isChecked
            // Favourite + Add-friend are applied instantly via the action chips, not on Submit.


            val rating = if (selectedRating > 0) selectedRating else 0 // Default to 3 if no rating is selected
            val description = binding.etUserName.text.toString().takeIf { it.isNotEmpty() } ?: "No data provided" // Default description
            val title = discription.takeIf { it.isNotEmpty() } ?: "No data provided" // Default title if it's empty
            Log.d("ReviewActivity", "User ID: $userid, Call User ID: $call_userid, Rating: $rating, Comment: $description, Interests: $title")




            // Reset completion flags
            wasRatingApiCalled = false
            wasFavoriteApiCalled = false
            isRatingApiCompleted = false
            isFavoriteApiCompleted = false
            
            if (rating > 0) {
                // Proceed with rating submission
                wasRatingApiCalled = true

                // 2026-05-22 — Rate event to Meta + Firebase. Fires only on
                // actual rating submit (rating > 0).
                com.gmwapp.hima.utils.HimaAnalytics.logRate(
                    ctx = this,
                    rating = rating,
                    maxRating = 5,
                    contentType = "creator",
                )

                if (isBlocking) {
                    blockMale(userid, call_userid)
                }

                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                    if (call_userid != null) {
                        viewModel.updatedrating(it,call_userid,rating.toString(),title,description)
                    }
                }

            } else {
                // If no rating is provided (favourite/friend are applied instantly via chips)
                if (isBlocking) {
                    // Only blocking - wait for block API to complete before finishing
                    blockMale(userid, call_userid)
                    // Don't finish here - let observeBlockuser() handle it
                } else {
                    // Neither rating nor blocking - just finish
                    finish()
                }
            }
        }

        // Setup rv_rating
        val starAdapter = HorizontalStarAdapter(this, 5) { rating ->
            // Update the selected rating
            selectedRating = rating

            // Update the review RecyclerView based on the selected rating
            updateReviewRecyclerView(rating)

            // Validate the button whenever the rating is selected
            validatebtn()

            // Uncomment this to show a Toast inside the Activity with the selected rating count
            // showAppToast("Selected Rating: $rating", Toast.LENGTH_SHORT)
        }

        binding.rvRating.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRating.adapter = starAdapter


        // Setup rv_review
        val staggeredGridLayoutManager = FlexboxLayoutManager(this).apply {
            flexWrap = FlexWrap.WRAP
            alignItems = AlignItems.FLEX_START
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.CENTER
        }

        val itemDecoration = FlexboxItemDecoration(this).apply {
            setDrawable(ContextCompat.getDrawable(this@RatingActivity, R.drawable.bg_divider))
            setOrientation(FlexboxItemDecoration.VERTICAL)
        }

        binding.rvReview.apply {
            layoutManager = staggeredGridLayoutManager // Use staggeredGridLayoutManager directly
            addItemDecoration(itemDecoration)
        }

        updateReviewRecyclerView(0) // Initialize with no reviews
    }

    private fun updateReviewRecyclerView(rating: Int) {
        val reviews = reviewItemsMap[rating] ?: emptyList()
        binding.rvReview.adapter = ReviewAdapter(this, reviews)

        // Add blink animation to rvReview
        val blinkAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.blink)
        binding.rvReview.startAnimation(blinkAnimation)
    }

    private fun validatebtn() {


        val review = binding.etUserName.text.toString()
        val rating = selectedRating // Get the selected rating

        // Check if review is not empty, rating is selected, and a review is selected
        if (review.isNotEmpty() && rating > 0 && selectedReviewPosition != RecyclerView.NO_POSITION) {
            binding.btnSubmit.isEnabled = true // Enable the button if all conditions are met
        } else {
            binding.btnSubmit.isEnabled = true // Disable the button if conditions are not met
        }
    }

    inner class ReviewAdapter(
        private val context: Context,
        private val reviews: List<String>
    ) : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

        private var selectedPosition = RecyclerView.NO_POSITION // Tracks the currently selected position

        inner class ReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val reviewTextView: TextView = view.findViewById(R.id.tv_interest)
            val main: MaterialCardView = view.findViewById(R.id.main)
            val interestIcon: ImageView = view.findViewById(R.id.iv_interest)

            fun bind(position: Int) {
                reviewTextView.text = reviews[position]
                
                // Hide the icon as we're only showing text
                interestIcon.visibility = View.GONE

                // Change background and text color based on selection
                if (position == selectedPosition) {
                    main.setBackgroundResource(R.drawable.d_button_bg_interest_selected)
                    reviewTextView.setTextColor(ContextCompat.getColor(context, R.color.black))
                } else {
                    main.setBackgroundResource(R.drawable.d_button_bg_interest)
                    reviewTextView.setTextColor(ContextCompat.getColor(context, R.color.interest_text_color))
                }

                // Handle click event
                itemView.setOnClickListener {
                    val previousPosition = selectedPosition
                    selectedPosition = position

                    // Notify the adapter about the changes
                    notifyItemChanged(previousPosition) // Deselect the previous item
                    notifyItemChanged(selectedPosition) // Select the new item

                    // Set the selected review position in the activity
                    selectedReviewPosition = position

                    // Show a toast with the selected item's text
//                    context.showAppToast("Selected: ${reviews[position]}", Toast.LENGTH_SHORT)
                    discription = reviews[position]

                    // Validate button on review selection
                    validatebtn()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.adapter_interest, parent, false)
            return ReviewViewHolder(view)
        }

        override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int = reviews.size
    }

    fun showBlockToogle(){
        var gender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
        if(gender=="female"){
            binding.llBlockUser.visibility= View.VISIBLE
        }
    }

    fun showFavouriteToggle(){
        var gender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
        if(gender=="male"){
            // 2026-05-22: always show for males. Previously we'd hide if the
            // checkFavorite API returned is_favorite=true, but that produced
            // intermittent disappearance (testers saw the toggle missing for
            // already-favourited callees). Now backend dedupes on the
            // add_favorite call instead. Box defaults UNCHECKED so re-adding
            // requires an explicit user action.
            binding.llFavouriteUser.visibility = View.VISIBLE
            binding.cbFavouriteUser.isChecked = false
        }
    }

    /**
     * Friends-Gated Chat (Variant A): instant action chips above Submit. Favourite chip
     * (male only) and Add-Friend chip (all) apply on TAP, not on Submit.
     */
    private fun setupActionChips() {
        val gender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
        val isMale = gender == "male"
        // The chip row supersedes the legacy favourite checkbox.
        binding.llFavouriteUser.visibility = View.GONE
        binding.llActionChips.visibility = View.VISIBLE
        binding.chipFavourite.visibility = if (isMale) View.VISIBLE else View.GONE
        // When the favourite chip is hidden (female viewer), the friend chip is the
        // only one in the row — drop its 6dp start gap (only meant to separate it
        // from favourite) so it spans full width and lines up with the Submit button.
        if (!isMale) {
            (binding.chipFriend.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = 0
                binding.chipFriend.layoutParams = it
            }
        }

        val userid = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
        val callUserId = intent.getIntExtra(DConstants.RECEIVER_ID, 0)

        binding.chipFavourite.setOnSingleClickListener {
            if (isFavouriteSelected || userid == null || callUserId == 0) return@setOnSingleClickListener
            setFavouriteChipSelected(true)            // optimistic
            addToFavoriteInstant(userid, callUserId)
        }
        binding.chipFriend.setOnSingleClickListener {
            if (isFriendRequestSent || userid == null || callUserId == 0) return@setOnSingleClickListener
            sendFriendRequestInstant(userid, callUserId)
        }

        // Disable the chip while the status check is in-flight to prevent a tap
        // racing with the API response and sending a duplicate request.
        if (userid != null && callUserId != 0) {
            binding.chipFriend.isClickable = false
            checkAndUpdateFriendChipState(userid, callUserId)
            // B_024 — pre-fill the Favourite chip so an already-favourited creator
            // shows "Already Favourite" instead of the default "Add to Favourite"
            // (mirrors the Already-Friends pre-fill above). Male/User side only.
            if (isMale) {
                binding.chipFavourite.isClickable = false
                checkAndUpdateFavouriteChipState(userid, callUserId)
            }
        }
    }

    /** B_024 — queries favourite status and pre-fills the chip's already-favourite state. */
    private fun checkAndUpdateFavouriteChipState(userId: Int, callUserId: Int) {
        apiManager.checkFavorite(userId, callUserId, object : NetworkCallback<CheckFavoriteResponse> {
            override fun onResponse(
                call: Call<CheckFavoriteResponse>,
                response: Response<CheckFavoriteResponse>
            ) {
                if (response.isSuccessful && response.body()?.success == true &&
                    response.body()?.is_favorite == true
                ) {
                    setFavouriteChipAlreadyFavourited()
                } else {
                    binding.chipFavourite.isClickable = true  // re-enable for a normal add
                }
            }
            override fun onFailure(call: Call<CheckFavoriteResponse>, t: Throwable) {
                binding.chipFavourite.isClickable = true  // re-enable on error so user can still add
            }
            override fun onNoNetwork() {
                binding.chipFavourite.isClickable = true
            }
        })
    }

    /** Queries the friend-request status and pre-fills the chip accordingly. */
    private fun checkAndUpdateFriendChipState(userId: Int, callUserId: Int) {
        apiManager.checkFriendRequest(userId, callUserId, userId, object : NetworkCallback<com.gmwapp.hima.retrofit.responses.FriendRequestResponse> {
            override fun onResponse(
                call: Call<com.gmwapp.hima.retrofit.responses.FriendRequestResponse>,
                response: Response<com.gmwapp.hima.retrofit.responses.FriendRequestResponse>
            ) {
                // check_friend_request reports the state in `message` (it never populates
                // data.status), so read that — mirroring UserProfileDetailActivity's mapping.
                when (if (response.isSuccessful) response.body()?.message else null) {
                    "You are friends"             -> setFriendChipAlreadyFriends()
                    "Friend request already sent" -> setFriendChipRequestSent()
                    else                          -> binding.chipFriend.isClickable = true  // re-enable for normal send
                }
            }
            override fun onFailure(call: Call<com.gmwapp.hima.retrofit.responses.FriendRequestResponse>, t: Throwable) {
                binding.chipFriend.isClickable = true  // re-enable on error so user can still try
            }
            override fun onNoNetwork() {
                binding.chipFriend.isClickable = true
            }
        })
    }

    private fun setFriendChipAlreadyFriends() {
        isFriendRequestSent = true   // disable tap
        binding.chipFriend.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
        binding.chipFriend.strokeWidth = 0
        binding.tvChipFriend.setTextColor(ContextCompat.getColor(this, R.color.white))
        binding.tvChipFriend.text = "✓ Already Friends"
    }

    private fun setFriendChipRequestSent() {
        isFriendRequestSent = true   // disable tap
        binding.chipFriend.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
        binding.chipFriend.strokeWidth = 0
        binding.tvChipFriend.setTextColor(ContextCompat.getColor(this, R.color.white))
        binding.tvChipFriend.text = "✓ Request Sent"
    }

    private fun setFavouriteChipSelected(selected: Boolean) {
        isFavouriteSelected = selected
        if (selected) {
            binding.chipFavourite.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
            binding.chipFavourite.strokeWidth = 0
            binding.tvChipFavourite.setTextColor(ContextCompat.getColor(this, R.color.white))
            binding.tvChipFavourite.text = "✓ Favourited"
        } else {
            binding.chipFavourite.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white))
            binding.chipFavourite.strokeWidth = (resources.displayMetrics.density).toInt()
            binding.tvChipFavourite.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
            binding.tvChipFavourite.text = "♡ Add to Favourite"
        }
    }

    /**
     * B_024 — the creator is already in the user's Favourites, so show a settled
     * "Already Favourite" state (mirrors setFriendChipAlreadyFriends). Sets
     * isFavouriteSelected so the tap guard blocks a redundant re-add — the label is
     * informational only, no add_favorite call is made.
     */
    private fun setFavouriteChipAlreadyFavourited() {
        isFavouriteSelected = true   // disable tap
        binding.chipFavourite.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
        binding.chipFavourite.strokeWidth = 0
        binding.tvChipFavourite.setTextColor(ContextCompat.getColor(this, R.color.white))
        binding.tvChipFavourite.text = "✓ Already Favourite"
    }

    /** Instant favourite add from the chip — does NOT close the sheet (unlike addToFavorite). */
    private fun addToFavoriteInstant(userId: Int, favoriteId: Int) {
        apiManager.addFavorite(userId, favoriteId, object : NetworkCallback<AddFavoriteResponse> {
            override fun onResponse(call: Call<AddFavoriteResponse>, response: Response<AddFavoriteResponse>) {
                if (!(response.isSuccessful && response.body()?.success == true)) {
                    setFavouriteChipSelected(false)   // revert optimistic state
                    showAppToast(response.body()?.message ?: "Couldn't add to favourites", Toast.LENGTH_SHORT)
                }
            }
            override fun onFailure(call: Call<AddFavoriteResponse>, t: Throwable) {
                setFavouriteChipSelected(false)
                showAppToast("Couldn't add to favourites", Toast.LENGTH_SHORT)
            }
            override fun onNoNetwork() {
                setFavouriteChipSelected(false)
                showAppToast("No network connection", Toast.LENGTH_SHORT)
            }
        })
    }

    /** Instant friend request from the after-call sheet (status 0 = new request). */
    private fun sendFriendRequestInstant(userId: Int, callUserId: Int) {
        apiManager.sendFriendRequest(userId, callUserId, 0, object : NetworkCallback<com.gmwapp.hima.retrofit.responses.FriendRequestResponse> {
            override fun onResponse(
                call: Call<com.gmwapp.hima.retrofit.responses.FriendRequestResponse>,
                response: Response<com.gmwapp.hima.retrofit.responses.FriendRequestResponse>
            ) {
                if (response.isSuccessful && response.body()?.success == true) {
                    isFriendRequestSent = true
                    binding.chipFriend.setCardBackgroundColor(ContextCompat.getColor(this@RatingActivity, R.color.colorAccent))
                    binding.chipFriend.strokeWidth = 0
                    binding.tvChipFriend.setTextColor(ContextCompat.getColor(this@RatingActivity, R.color.white))
                    binding.tvChipFriend.text = "✓ Request sent"
                    showAppToast(response.body()?.message ?: "Friend request sent", Toast.LENGTH_SHORT)
                } else {
                    showAppToast(response.body()?.message ?: "Couldn't send request", Toast.LENGTH_SHORT)
                }
            }
            override fun onFailure(call: Call<com.gmwapp.hima.retrofit.responses.FriendRequestResponse>, t: Throwable) {
                showAppToast("Couldn't send request", Toast.LENGTH_SHORT)
            }
            override fun onNoNetwork() {
                showAppToast("No network connection", Toast.LENGTH_SHORT)
            }
        })
    }

    private fun checkIfUserIsAlreadyFavorite() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        val receiverId = intent.getIntExtra(DConstants.RECEIVER_ID, 0)
        
        if (receiverId == 0) {
            // If no receiver ID, show the toggle (default behavior)
            binding.llFavouriteUser.visibility = View.VISIBLE
            return
        }
        
        Log.d("RatingActivity", "Checking if user is favorite: userId=${userData.id}, receiverId=$receiverId")
        
        // Use the new check_favorite API to check if user is already in favorites
        apiManager.checkFavorite(
            userData.id,
            receiverId,
            object : NetworkCallback<CheckFavoriteResponse> {
                override fun onResponse(
                    call: Call<CheckFavoriteResponse>,
                    response: Response<CheckFavoriteResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val checkResponse = response.body()
                        if (checkResponse != null && checkResponse.success) {
                            val isAlreadyFavorite = checkResponse.is_favorite
                            
                            Log.d("RatingActivity", "Favorite check result: receiverId=$receiverId, isAlreadyFavorite=$isAlreadyFavorite")
                            
                            // Hide toggle if user is already a favorite, show otherwise
                            binding.llFavouriteUser.visibility = if (isAlreadyFavorite) View.GONE else View.VISIBLE
                        } else {
                            Log.d("RatingActivity", "Favorite check failed: ${checkResponse?.message}")
                            binding.llFavouriteUser.visibility = View.VISIBLE
                        }
                    } else {
                        Log.d("RatingActivity", "Favorite check API call unsuccessful: ${response.code()}")
                        binding.llFavouriteUser.visibility = View.VISIBLE
                    }
                }

                override fun onFailure(call: Call<CheckFavoriteResponse>, t: Throwable) {
                    Log.e("RatingActivity", "Favorite check API call failed: ${t.message}", t)
                    binding.llFavouriteUser.visibility = View.VISIBLE
                }

                override fun onNoNetwork() {
                    Log.d("RatingActivity", "No network for favorite check")
                    binding.llFavouriteUser.visibility = View.VISIBLE
                }
            }
        )
    }

    fun blockMale(userid: Int?, call_userid: Int) {
        var blocked = 0
        isBlocked = binding.cbBlockUser.isChecked
        if (isBlocked){
            blocked = 1
        }else{
            blocked = 2
        }

        if(blocked==1) {
            userid?.let { blockUserViewModel.blockUser(it, call_userid, blocked) }
        }
        observeBlockuser()

        Log.d("isBlocked","$isBlocked")
    }

    fun observeBlockuser(){
        blockUserViewModel.blockUserLiveData.observe(this) {
            showAppToast(it.message, Toast.LENGTH_SHORT)
            if (selectedRating == 0) {
                finish()
            }
        }

        blockUserViewModel.blockUserErrorLiveData.observe(this) {
            showAppToast(it, Toast.LENGTH_SHORT)
            if (selectedRating == 0) {
                finish()
            }
        }
    }

    fun addToFavorite(userId: Int, favoriteId: Int) {
        apiManager.addFavorite(userId, favoriteId, object : NetworkCallback<AddFavoriteResponse> {
            override fun onResponse(call: Call<AddFavoriteResponse>, response: Response<AddFavoriteResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()
                    if (result?.success == true) {
                        showAppToast(result.message ?: "Added to favorites", Toast.LENGTH_SHORT)
                        isFavoriteApiCompleted = true
                    } else {
                        showAppToast(result?.message ?: "Failed to add to favorites", Toast.LENGTH_SHORT)
                        isFavoriteApiCompleted = true
                    }
                } else {
                    showAppToast("Failed to add to favorites", Toast.LENGTH_SHORT)
                    isFavoriteApiCompleted = true
                }
                
                // Check if we can close the activity now
                checkAndCloseActivity()
            }

            override fun onFailure(call: Call<AddFavoriteResponse>, t: Throwable) {
                isFavoriteApiCompleted = true
                showAppToast("Error: ${t.message}", Toast.LENGTH_SHORT)
                
                // Check if we can close the activity now
                checkAndCloseActivity()
            }

            override fun onNoNetwork() {
                isFavoriteApiCompleted = true
                showAppToast("No network connection", Toast.LENGTH_SHORT)
                
                // Check if we can close the activity now
                checkAndCloseActivity()
            }
        })
    }
    
    private fun checkAndCloseActivity() {
        // Close activity when:
        // 1. Rating API was not called OR rating API is completed
        // 2. Favorite API was not called OR favorite API is completed
        val ratingDone = !wasRatingApiCalled || isRatingApiCompleted
        val favoriteDone = !wasFavoriteApiCalled || isFavoriteApiCompleted
        
        if (ratingDone && favoriteDone) {
            finish()
        }
    }

    fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("from_rating", true)
        startActivity(intent)
        finish()
    }

    private fun showCallDurationDialog(title: String, message: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_call_duration, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val titleTextView = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        titleTextView?.text = title
        
        val messageTextView = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        messageTextView?.text = message
        
        val okButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dialog_ok)
        okButton?.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

}
