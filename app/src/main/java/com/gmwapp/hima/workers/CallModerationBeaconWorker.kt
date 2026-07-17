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

/**
 * Drops a "this call is expected to be moderated" beacon at call start.
 *
 * This is durable WorkManager work, so it survives the process being killed:
 * if the app dies mid-call before any snapshot uploads and before the end
 * report, the beacon still reaches the server on the next network window. That
 * is the only trace that lets the backend notice a call that otherwise went
 * completely dark. Enqueued the moment capture is confirmed for a call.
 */
class CallModerationBeaconWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val callId = inputData.getInt(KEY_CALL_ID, 0)
        val localUserId = inputData.getInt(KEY_LOCAL_USER_ID, 0)
        if (callId <= 0) return@withContext Result.failure()
        if (runAttemptCount >= MAX_ATTEMPTS) return@withContext Result.failure()

        // Only report under the login that made the call (see upload worker).
        if (localUserId <= 0) return@withContext Result.failure()
        val sessionUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (sessionUserId <= 0) {
            // Session not readable yet; indeterminate, not a mismatch. Retry
            // (bounded by MAX_ATTEMPTS) rather than drop a real beacon.
            return@withContext Result.retry()
        }
        if (sessionUserId != localUserId) {
            Log.w(TAG, "Session changed since call=$callId (was=$localUserId now=$sessionUserId); dropping beacon")
            return@withContext Result.failure()
        }

        val token = BaseApplication.getInstance()?.getPrefs()?.getAuthenticationToken().orEmpty()
        if (token.isBlank()) return@withContext Result.failure()

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}call-moderation/calls/$callId/expect")
            .header("Authorization", "Bearer $token")
            .header(APP_VERSION_CODE_HEADER, BuildConfig.VERSION_CODE.toString())
            .post(FormBody.Builder().build())
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
            Log.w(TAG, "Beacon post failed call=$callId: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CallModerationBeacon"
        private const val APP_VERSION_CODE_HEADER = "X-Hima-Version-Code"
        private const val MAX_ATTEMPTS = 6
        private const val KEY_CALL_ID = "call_id"
        private const val KEY_LOCAL_USER_ID = "local_user_id"
        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        fun enqueue(context: Context, callId: Int, localUserId: Int): Boolean {
            if (callId <= 0 || localUserId <= 0) return false
            val data = Data.Builder()
                .putInt(KEY_CALL_ID, callId)
                .putInt(KEY_LOCAL_USER_ID, localUserId)
                .build()
            val request = OneTimeWorkRequestBuilder<CallModerationBeaconWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .addTag(CallModerationUploadWorker.WORK_TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "call_moderation_beacon_${callId}_$localUserId",
                ExistingWorkPolicy.KEEP,
                request,
            )
            return true
        }
    }
}
