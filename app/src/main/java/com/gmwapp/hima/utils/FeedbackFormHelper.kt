package com.gmwapp.hima.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.gmwapp.hima.dialogs.FeedbackFormDialog
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FeedbackCheckResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackFormHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiManager: ApiManager,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun checkAndShowFeedback(activity: Activity, userId: Int) {
        if (RatingPromptHelper.isCallActivityActive(activity)) {
            Log.d(TAG, "Skipping feedback check - in call activity")
            return
        }
        if (!shouldCheckNow()) {
            Log.d(TAG, "Skipping feedback check - throttled")
            return
        }
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

        apiManager.checkFeedback(userId, object : NetworkCallback<FeedbackCheckResponse> {
            override fun onResponse(
                call: retrofit2.Call<FeedbackCheckResponse>,
                response: retrofit2.Response<FeedbackCheckResponse>
            ) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "checkFeedback http ${response.code()}")
                    return
                }
                val body = response.body() ?: return
                if (!body.success || !body.shouldShow) {
                    Log.d(TAG, "Feedback not eligible: ${body.message}")
                    return
                }
                val form = body.form ?: return

                if (RatingPromptHelper.isCallActivityActive(activity)) {
                    Log.d(TAG, "Aborting feedback - user entered call activity")
                    return
                }
                if (activity.isFinishing || activity.isDestroyed) {
                    Log.d(TAG, "Aborting feedback - activity finishing/destroyed")
                    return
                }
                FeedbackFormDialog(
                    context = activity,
                    form = form,
                    apiManager = apiManager,
                    userId = userId,
                    onTerminal = {
                        // Refresh throttle on any terminal outcome (submit success,
                        // skip success, 409 conflict, 404 not-found) so we don't
                        // re-prompt within the same 24h window.
                        prefs.edit()
                            .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                            .apply()
                    },
                ).show()
            }

            override fun onFailure(call: retrofit2.Call<FeedbackCheckResponse>, t: Throwable) {
                Log.e(TAG, "checkFeedback failed", t)
            }

            override fun onNoNetwork() {
                Log.w(TAG, "checkFeedback no network")
            }
        })
    }

    fun forceCheckFeedback(activity: Activity, userId: Int) {
        prefs.edit().remove(KEY_LAST_CHECK_TIME).apply()
        checkAndShowFeedback(activity, userId)
    }

    private fun shouldCheckNow(): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK_TIME, 0)
        val hours = (System.currentTimeMillis() - last) / (1000 * 60 * 60)
        return hours >= MIN_CHECK_INTERVAL_HOURS
    }

    companion object {
        private const val TAG = "FeedbackFormHelper"
        private const val PREFS_NAME = "feedback_form_prefs"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val MIN_CHECK_INTERVAL_HOURS = 24L
    }
}
