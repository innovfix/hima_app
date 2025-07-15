package com.gmwapp.hima.activities

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import android.os.Bundle

import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.InterestsListAdapter
import com.gmwapp.hima.adapters.InterestsReviewListAdapter
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityReviewBinding

import com.gmwapp.hima.retrofit.responses.Interests
import com.gmwapp.hima.retrofit.responses.InterestsReview
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.RatingViewModel
import com.gmwapp.hima.workers.CallUpdateWorker
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxItemDecoration
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent

import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class ReviewActivity : BaseActivity() {
    private var interestsListAdapter: InterestsReviewListAdapter? = null
    private val selectedInterests: ArrayList<String> = ArrayList()
    private lateinit var binding: ActivityReviewBinding

    val viewModel: RatingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initUI()
    }

    private fun initUI() {
        // Close button functionality
        binding.ivClose.setOnClickListener { finish() }

        // Set default rating
        binding.rating.rating = 0f

        // Set title text with dynamic receiver name
        binding.tvTitle.text = getString(
            R.string.review_hint,
            intent.getStringExtra(DConstants.RECEIVER_NAME) ?: "User"
        )

        binding.etUserName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // Update the character count
                val charCount = s?.length ?: 0
                val maxLength = 100 // or whatever the max length is for the EditText
                binding.tvCharCount.text = getString(R.string._0_100, charCount, maxLength)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed for this case
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Not needed for this case
            }
        })

        // Submit button functionality
        binding.btnSubmit.setOnClickListener {

//            val call_userid = intent.getIntExtra(DConstants.RECEIVER_ID,0).toInt()



            val userid = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            val call_userid = intent.getIntExtra(DConstants.RECEIVER_ID, 0)
            Log.d("ReviewActivity", "User ID: $userid, Call User ID: $call_userid")
            val rating = binding.rating.rating.toInt()
            val description = binding.etUserName.text.toString()
            val title = selectedInterests.joinToString(",")

            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                if (call_userid != null) {
                    viewModel.updatedrating(it,call_userid,rating.toString(),title,description)
                }
            }

            Log.d("ReviewActivity", "User ID: $userid, Call User ID: $call_userid, Rating: $rating, Comment: $description, Interests: $title")


        }



        viewModel.ratingResponseLiveData.observe(this, Observer {
            if (it.success) {
                Toast.makeText(this, "Rating submitted successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            else{
                Toast.makeText(this, "Rating submission failed", Toast.LENGTH_SHORT).show()
            }
        })

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        // Retrieve existing interests
        val interests = userData?.interests?.split(",")?.map { it.trim() }
        interests?.let { selectedInterests.addAll(it) }

        // Initialize RecyclerView and adapter
        val initialRating = binding.rating.rating
        interestsListAdapter = InterestsReviewListAdapter(
            this,
            createInterestList(interests, initialRating),
            isLimitreached = selectedInterests.size >= 5,
            onItemSelectionListener = object : OnItemSelectionListener<InterestsReview> {
                override fun onItemSelected(interest: InterestsReview) {
                    if (interest.isSelected == true) {
                        selectedInterests.remove(interest.name)
                    } else {
                        selectedInterests.add(interest.name)
                    }
                    interestsListAdapter?.updateLimitReached(selectedInterests.size >= 5)
                }
            }
        )

        val staggeredGridLayoutManager = FlexboxLayoutManager(this).apply {
            flexWrap = FlexWrap.WRAP
            alignItems = AlignItems.FLEX_START
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.CENTER
        }

        val itemDecoration = FlexboxItemDecoration(this).apply {
            setDrawable(ContextCompat.getDrawable(this@ReviewActivity, R.drawable.bg_divider))
            setOrientation(FlexboxItemDecoration.VERTICAL)
        }

        binding.rvInterests.apply {
            adapter = interestsListAdapter
            layoutManager = staggeredGridLayoutManager
            addItemDecoration(itemDecoration)
        }

        // Add listener to update the list based on the rating
        binding.rating.setOnRatingBarChangeListener { _, rating, _ ->
            Log.d("ReviewActivity", "Rating changed to: $rating")
            interestsListAdapter?.updateData(createInterestList(interests, rating))
        }
    }

    private fun createInterestList(existingInterests: List<String>?, rating: Float): ArrayList<InterestsReview> {
        Log.d("ReviewActivity", "Creating list for rating: $rating")
        val interestOptions = when {
            rating >= 4 -> listOf(
                getString(R.string.not_replying),
                getString(R.string.bad_connectivity)
            )
            rating >= 2 -> listOf(
                getString(R.string.rude_behaviour),
                getString(R.string.abusive_language)
            )
            else -> listOf(
                getString(R.string.not_replying),
                getString(R.string.abusive_language),
                getString(R.string.rude_behaviour),
                getString(R.string.bad_connectivity)
            )
        }

        Log.d("ReviewActivity", "Interest options: $interestOptions")

        return interestOptions.map { interest ->
            InterestsReview(interest, existingInterests?.contains(interest) == true)
        } as ArrayList<InterestsReview>
    }

}

