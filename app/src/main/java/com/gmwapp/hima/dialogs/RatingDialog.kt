package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.Toast
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.DialogRatingBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.SubmitRatingResponse
import retrofit2.Call
import retrofit2.Response

class RatingDialog(
    private val context: Context,
    private val userId: Int,
    private val apiManager: ApiManager
) {
    private var dialog: Dialog? = null
    private lateinit var binding: DialogRatingBinding
    private var selectedStarCount = 0
    private val stars = mutableListOf<ImageView>()
    private var onRatingSubmittedListener: OnRatingSubmittedListener? = null

    interface OnRatingSubmittedListener {
        fun onRatingSubmitted(starCount: Int)
    }

    fun setOnRatingSubmittedListener(listener: OnRatingSubmittedListener) {
        this.onRatingSubmittedListener = listener
    }

    fun show() {
        dialog = Dialog(context)
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogRatingBinding.inflate(LayoutInflater.from(context))
        dialog?.setContentView(binding.root)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // Set dialog width to match_parent to allow FrameLayout padding to work
        dialog?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        dialog?.setCancelable(true)

        setupStars()
        setupListeners()

        dialog?.show()
    }

    private fun setupStars() {
        stars.clear()
        stars.add(binding.star1)
        stars.add(binding.star2)
        stars.add(binding.star3)
        stars.add(binding.star4)
        stars.add(binding.star5)

        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                updateStarSelection(index + 1)
            }
        }
    }

    private fun updateStarSelection(count: Int) {
        selectedStarCount = count

        // Update star visuals with gold color for filled stars
        stars.forEachIndexed { index, star ->
            if (index < count) {
                star.setImageResource(R.drawable.ic_star_filled)
                // Apply gold color filter
                star.setColorFilter(
                    android.graphics.Color.parseColor("#FFD700"),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            } else {
                star.setImageResource(R.drawable.ic_star_empty)
                star.clearColorFilter()
            }
        }

        // Show/hide description field based on rating
        if (count < 4) {
            binding.tilDescription.visibility = View.VISIBLE
            binding.etDescription.requestFocus()
        } else {
            binding.tilDescription.visibility = View.GONE
            binding.etDescription.setText("")
        }

        Log.d("RatingDialog", "Selected $count stars")
    }

    private fun setupListeners() {
        binding.ivClose.setOnClickListener {
            dialog?.dismiss()
        }

        binding.btnSubmit.setOnClickListener {
            if (selectedStarCount == 0) {
                Toast.makeText(context, "Please select a rating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if description is required and provided
            if (selectedStarCount < 4) {
                val description = binding.etDescription.text.toString().trim()
                if (description.isEmpty()) {
                    Toast.makeText(context, "Please tell us what went wrong", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                submitRating(description)
            } else {
                submitRating(null)
            }
        }
    }

    private fun submitRating(description: String?) {
        // Show loading state
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Submitting..."

        apiManager.submitRating(userId, selectedStarCount, description, object : NetworkCallback<SubmitRatingResponse> {
            override fun onResponse(
                call: Call<SubmitRatingResponse>,
                response: Response<SubmitRatingResponse>
            ) {
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "Submit Rating"

                // Log complete request details
                Log.d("SubmitRating", "═══════════════════════════════════════")
                Log.d("SubmitRating", "📡 REQUEST URL: ${call.request().url}")
                Log.d("SubmitRating", "📤 REQUEST METHOD: ${call.request().method}")
                Log.d("SubmitRating", "👤 USER ID: $userId")
                Log.d("SubmitRating", "⭐ STAR COUNT: $selectedStarCount")
                Log.d("SubmitRating", "📝 DESCRIPTION: ${description ?: "null"}")
                Log.d("SubmitRating", "───────────────────────────────────────")
                Log.d("SubmitRating", "📥 RESPONSE CODE: ${response.code()}")
                Log.d("SubmitRating", "📊 RESPONSE MESSAGE: ${response.message()}")

                if (response.isSuccessful && response.body() != null) {
                    val apiResponse = response.body()!!
                    Log.d("SubmitRating", "✅ SUCCESS BODY:")
                    Log.d("SubmitRating", "   success: ${apiResponse.success}")
                    Log.d("SubmitRating", "   message: ${apiResponse.message}")
                    Log.d("SubmitRating", "   data: ${apiResponse.data}")
                    
                    if (apiResponse.success) {
                        Toast.makeText(context, apiResponse.message, Toast.LENGTH_SHORT).show()
                        onRatingSubmittedListener?.onRatingSubmitted(selectedStarCount)
                        dialog?.dismiss()
                    }
                } else {
                    // API call failed - log complete error response
                    Log.e("SubmitRating", "❌ API FAILED:")
                    try {
                        val errorBody = response.errorBody()?.string()
                        Log.e("SubmitRating", "   ERROR BODY: $errorBody")
                    } catch (e: Exception) {
                        Log.e("SubmitRating", "   Could not read error body: ${e.message}")
                    }
                }
                Log.d("SubmitRating", "═══════════════════════════════════════")
            }

            override fun onFailure(call: Call<SubmitRatingResponse>, t: Throwable) {
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "Submit Rating"
                
                // Don't show toast for errors as per requirement
                Log.e("SubmitRating", "═══════════════════════════════════════")
                Log.e("SubmitRating", "💥 NETWORK FAILURE:")
                Log.e("SubmitRating", "📡 REQUEST URL: ${call.request().url}")
                Log.e("SubmitRating", "❌ ERROR: ${t.message}")
                Log.e("SubmitRating", "📚 STACK TRACE:", t)
                Log.e("SubmitRating", "═══════════════════════════════════════")
            }

            override fun onNoNetwork() {
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "Submit Rating"
                
                // Don't show toast for errors as per requirement
                Log.w("SubmitRating", "═══════════════════════════════════════")
                Log.w("SubmitRating", "⚠️ NO NETWORK CONNECTION")
                Log.w("SubmitRating", "═══════════════════════════════════════")
            }
        })
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}
