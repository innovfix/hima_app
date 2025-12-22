package com.gmwapp.hima.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.agora.female.FemaleAudioCallingActivity
import com.gmwapp.hima.agora.female.FemaleVideoCallingActivity
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.RatingPromptResponse
import com.gmwapp.hima.retrofit.responses.UpdateRatingPromptResponse
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingPromptHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiManager: ApiManager
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val reviewManager: ReviewManager = ReviewManagerFactory.create(context)
    
    companion object {
        private const val TAG = "RatingPromptHelper"
        private const val PREFS_NAME = "rating_prompt_prefs"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_PROMPT_SHOWN = "prompt_shown"
        private const val MIN_CHECK_INTERVAL_HOURS = 24L // Check at most once per day
        
        /**
         * Check if current activity is a call activity
         */
        fun isCallActivityActive(context: Context): Boolean {
            return when (context) {
                is MaleAudioCallingActivity -> true
                is MaleVideoCallingActivity -> true
                is FemaleAudioCallingActivity -> true
                is FemaleVideoCallingActivity -> true
                is Activity -> {
                    val activityName = context::class.java.simpleName
                    activityName == "MaleAudioCallingActivity" ||
                    activityName == "MaleVideoCallingActivity" ||
                    activityName == "FemaleAudioCallingActivity" ||
                    activityName == "FemaleVideoCallingActivity"
                }
                else -> false
            }
        }
    }

    /**
     * Check if enough time has passed since last check
     */
    private fun shouldCheckConditions(): Boolean {
        val lastCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0)
        val currentTime = System.currentTimeMillis()
        val hoursSinceLastCheck = (currentTime - lastCheckTime) / (1000 * 60 * 60)
        
        return hoursSinceLastCheck >= MIN_CHECK_INTERVAL_HOURS
    }

    /**
     * Main entry point to check and show rating prompt
     */
    fun checkAndShowRatingPrompt(activity: Activity, userId: Int) {
        // CRITICAL: Never show during call activities
        if (isCallActivityActive(activity)) {
            Log.d(TAG, "Skipping rating prompt - user is in call activity")
            return
        }

        // Check if we should check conditions (avoid excessive API calls)
        if (!shouldCheckConditions()) {
            Log.d(TAG, "Skipping rating prompt check - checked recently")
            return
        }

        // Update last check time
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

        // Check conditions via API
        apiManager.checkRatingPrompt(userId, object : NetworkCallback<RatingPromptResponse> {
            override fun onResponse(call: retrofit2.Call<RatingPromptResponse>, response: retrofit2.Response<RatingPromptResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val ratingResponse = response.body()!!
                    if (ratingResponse.success && ratingResponse.shouldShow) {
                        Log.d(TAG, "Rating prompt should be shown: ${ratingResponse.message}")
                        showInAppReview(activity, userId, ratingResponse.triggerGroup)
                    } else {
                        Log.d(TAG, "Rating prompt should not be shown: ${ratingResponse.message}")
                    }
                } else {
                    Log.e(TAG, "Failed to check rating prompt: ${response.message()}")
                }
            }

            override fun onFailure(call: retrofit2.Call<RatingPromptResponse>, t: Throwable) {
                Log.e(TAG, "Error checking rating prompt", t)
            }

            override fun onNoNetwork() {
                Log.w(TAG, "No network connection for rating prompt check")
            }
        })
    }

    /**
     * Show Google Play In-App Review dialog
     */
    private fun showInAppReview(activity: Activity, userId: Int, triggerGroup: Int?) {
        // Double-check we're not in a call activity
        if (isCallActivityActive(activity)) {
            Log.d(TAG, "Aborting rating prompt - user entered call activity")
            return
        }

        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { requestFlow ->
            if (requestFlow.isSuccessful) {
                val reviewInfo = requestFlow.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    // Review flow completed - update backend
                    markPromptShown(userId, triggerGroup)
                }
            } else {
                // Handle error
                val errorCode = (requestFlow.exception as? com.google.android.play.core.review.ReviewException)?.errorCode
                Log.e(TAG, "Failed to request review flow. Error code: $errorCode")
                
                // Still mark as shown to prevent repeated failures
                markPromptShown(userId, triggerGroup)
            }
        }
    }

    /**
     * Mark prompt as shown in backend
     */
    private fun markPromptShown(userId: Int, triggerGroup: Int?) {
        apiManager.updateRatingPromptShown(userId, 0, object : NetworkCallback<UpdateRatingPromptResponse> {
            override fun onResponse(
                call: retrofit2.Call<UpdateRatingPromptResponse>,
                response: retrofit2.Response<UpdateRatingPromptResponse>
            ) {
                if (response.isSuccessful) {
                    Log.d(TAG, "Rating prompt tracking updated successfully")
                    prefs.edit().putBoolean(KEY_PROMPT_SHOWN, true).apply()
                } else {
                    Log.e(TAG, "Failed to update rating prompt tracking: ${response.message()}")
                }
            }

            override fun onFailure(call: retrofit2.Call<UpdateRatingPromptResponse>, t: Throwable) {
                Log.e(TAG, "Error updating rating prompt tracking", t)
            }

            override fun onNoNetwork() {
                Log.w(TAG, "No network connection for rating prompt tracking update")
            }
        })
    }

    /**
     * Manually trigger rating prompt check (for testing or after specific events)
     */
    fun forceCheckRatingPrompt(activity: Activity, userId: Int) {
        prefs.edit().remove(KEY_LAST_CHECK_TIME).apply()
        checkAndShowRatingPrompt(activity, userId)
    }
}
