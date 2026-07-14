package com.gmwapp.hima.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Reports the local participant's connected duration after the video call ends. */
class CallModerationFinalizeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val callId = inputData.getInt(KEY_CALL_ID, 0)
        val durationSeconds = inputData.getInt(KEY_DURATION_SECONDS, 0)
        val endedAt = inputData.getString(KEY_ENDED_AT).orEmpty()
        if (callId <= 0 || durationSeconds < 0 || endedAt.isBlank()) return@withContext Result.failure()
        if (runAttemptCount >= MAX_ATTEMPTS) return@withContext Result.failure()

        val token = BaseApplication.getInstance()?.getPrefs()?.getAuthenticationToken().orEmpty()
        if (token.isBlank()) return@withContext Result.failure()

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}call-moderation/calls/$callId/end")
            .header("Authorization", "Bearer $token")
            .post(
                FormBody.Builder()
                    .add("duration_seconds", durationSeconds.toString())
                    .add("ended_at", endedAt)
                    .build(),
            )
            .build()

        try {
            CLIENT.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Result.success()
                    response.code == 408 || response.code == 429 || response.code >= 500 -> Result.retry()
                    else -> Result.failure()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Call-end moderation report failed call=$callId: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CallModerationEnd"
        private const val MAX_ATTEMPTS = 6
        private const val KEY_CALL_ID = "call_id"
        private const val KEY_LOCAL_USER_ID = "local_user_id"
        private const val KEY_DURATION_SECONDS = "duration_seconds"
        private const val KEY_ENDED_AT = "ended_at"
        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        fun enqueue(
            context: Context,
            callId: Int,
            localUserId: Int,
            durationSeconds: Int,
            endedAt: String,
        ): Boolean {
            if (callId <= 0 || localUserId <= 0 || durationSeconds < 0) return false
            val data = Data.Builder()
                .putInt(KEY_CALL_ID, callId)
                .putInt(KEY_LOCAL_USER_ID, localUserId)
                .putInt(KEY_DURATION_SECONDS, durationSeconds)
                .putString(KEY_ENDED_AT, endedAt)
                .build()
            val request = OneTimeWorkRequestBuilder<CallModerationFinalizeWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "call_moderation_finalize_${callId}_$localUserId",
                ExistingWorkPolicy.KEEP,
                request,
            )
            return true
        }
    }
}
