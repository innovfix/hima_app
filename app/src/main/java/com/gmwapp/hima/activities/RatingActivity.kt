package com.gmwapp.hima.activities

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityRatingBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.BlockUserViewModel
import com.gmwapp.hima.viewmodels.RatingViewModel
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxItemDecoration
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RatingActivity : BaseActivity() {

    val viewModel: RatingViewModel by viewModels()
    val blockUserViewModel: BlockUserViewModel by viewModels()


    lateinit var binding: ActivityRatingBinding
    private lateinit var reviewItemsMap: Map<Int, List<String>> // Declare without initializing
    private var selectedRating: Int = 0 // Add a variable to track selected rating
    private var selectedReviewPosition: Int = RecyclerView.NO_POSITION // Track the selected review position
    private var discription: String = ""
    var isBlocked = false

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
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initUi()
        showBlockToogle()

        // Add listener to review text input for validation
        binding.etUserName.addTextChangedListener {
            validatebtn() // Validate the button whenever the user types
        }
    }

    private fun initUi() {


        binding.ivClose.setOnClickListener { finish() }


        // Set title text with dynamic receiver name
        binding.tvTitle.text = getString(
            R.string.review_hint,
            intent.getStringExtra(DConstants.RECEIVER_NAME) ?: "User"
        )

        viewModel.ratingResponseLiveData.observe(this, Observer { response ->
            if (response != null && response.success) {
                // Handle successful rating submission
                Toast.makeText(this, "Rating submitted successfully", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                // Handle failure
            //    Toast.makeText(this, "Rating submission failed", Toast.LENGTH_SHORT).show()
                finish()
            }
        })



        binding.btnSubmit.setOnSingleClickListener {

            val userid = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            val call_userid = intent.getIntExtra(DConstants.RECEIVER_ID, 0)

            var gender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender

            if (gender=="female"){
                blockMale(userid,call_userid)
            }

            val rating = if (selectedRating > 0) selectedRating else 0 // Default to 3 if no rating is selected
            val description = binding.etUserName.text.toString().takeIf { it.isNotEmpty() } ?: "No data provided" // Default description
            val title = discription.takeIf { it.isNotEmpty() } ?: "No data provided" // Default title if it's empty
            Log.d("ReviewActivity", "User ID: $userid, Call User ID: $call_userid, Rating: $rating, Comment: $description, Interests: $title")




            if (rating > 0) {500
                // Proceed with rating submission
                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                    if (call_userid != null) {
                        viewModel.updatedrating(it,call_userid,rating.toString(),title,description)
                    }
                }
            } else {
                // If no rating is provided, just finish the activity
              //  Toast.makeText(this, "No rating provided", Toast.LENGTH_SHORT).show()
                finish()
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
            // Toast.makeText(this, "Selected Rating: $rating", Toast.LENGTH_SHORT).show()
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
            val main: ConstraintLayout = view.findViewById(R.id.main)

            fun bind(position: Int) {
                reviewTextView.text = reviews[position]

                // Change background and text color based on selection
                if (position == selectedPosition) {
                    main.setBackgroundResource(R.drawable.d_button_bg_interest_selected)
                    reviewTextView.setTextColor(ContextCompat.getColor(context, R.color.black))
                } else {
                    main.setBackgroundResource(R.drawable.d_button_bg_interest_disabled)
                    reviewTextView.setTextColor(ContextCompat.getColor(context, R.color.white))
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
//                    Toast.makeText(context, "Selected: ${reviews[position]}", Toast.LENGTH_SHORT).show()
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

    fun blockMale(userid: Int?, call_userid: Int) {
        var blocked = 0
        isBlocked = binding.cbBlockUser.isChecked
        if (isBlocked){
            blocked = 1
        }else{
            blocked = 0
        }

        userid?.let { blockUserViewModel.blockUser(it,call_userid,blocked) }

        observeBlockuser()

        Log.d("isBlocked","$isBlocked")
    }

    fun observeBlockuser(){
        blockUserViewModel.blockUserLiveData.observe(this) {
            Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
        }

        blockUserViewModel.blockUserErrorLiveData.observe(this) {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        }
    }

}




