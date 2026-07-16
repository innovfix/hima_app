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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Uploads one already-captured local Agora frame. Work is unique by
 * (call, authenticated local user, interval), so lifecycle retries cannot
 * produce multiple rows or multiple warning candidates.
 */
class CallModerationUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val callId = inputData.getInt(KEY_CALL_ID, 0)
        val intervalSeconds = inputData.getInt(KEY_INTERVAL_SECONDS, 0)
        val capturedAt = inputData.getString(KEY_CAPTURED_AT).orEmpty()
        val consentVersion = inputData.getString(KEY_CONSENT_VERSION).orEmpty()
        val imagePath = inputData.getString(KEY_IMAGE_PATH).orEmpty()
        val image = File(imagePath)

        if (
            callId <= 0 || intervalSeconds !in ALLOWED_INTERVALS ||
            capturedAt.isBlank() || consentVersion.isBlank() ||
            !image.isFile || image.length() <= 0L
        ) {
            deletePrivateTemp(image)
            Log.w(TAG, "Invalid upload input call=$callId interval=$intervalSeconds")
            return@withContext Result.failure()
        }

        if (runAttemptCount >= MAX_ATTEMPTS) {
            deletePrivateTemp(image)
            Log.w(TAG, "Upload exhausted retries call=$callId interval=$intervalSeconds")
            return@withContext Result.failure()
        }

        val authToken = BaseApplication.getInstance()
            ?.getPrefs()?.getAuthenticationToken().orEmpty()
        if (authToken.isBlank()) {
            deletePrivateTemp(image)
            return@withContext Result.failure()
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("call_id", callId.toString())
            .addFormDataPart("interval_seconds", intervalSeconds.toString())
            .addFormDataPart("captured_at", capturedAt)
            .addFormDataPart("consent_version", consentVersion)
            .addFormDataPart(
                "image",
                image.name,
                image.asRequestBody("image/jpeg".toMediaType()),
            )
            .build()

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}call-moderation/snapshots")
            .header("Authorization", "Bearer $authToken")
            .header(APP_VERSION_CODE_HEADER, BuildConfig.VERSION_CODE.toString())
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                return@withContext when {
                    response.isSuccessful -> {
                        deletePrivateTemp(image)
                        Log.d(TAG, "Stored call=$callId interval=$intervalSeconds")
                        Result.success()
                    }
                    response.code == 408 || response.code == 429 || response.code >= 500 -> {
                        Log.w(TAG, "Transient HTTP ${response.code}; retrying call=$callId interval=$intervalSeconds")
                        Result.retry()
                    }
                    else -> {
                        // A permanent authorization/validation failure must not
                        // leave sensitive material in the app cache indefinitely.
                        deletePrivateTemp(image)
                        Log.w(TAG, "Permanent HTTP ${response.code}; dropping call=$callId interval=$intervalSeconds")
                        Result.failure()
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error call=$callId interval=$intervalSeconds: ${e.message}")
            Result.retry()
        } catch (t: Throwable) {
            deletePrivateTemp(image)
            Log.e(TAG, "Unexpected upload failure call=$callId interval=$intervalSeconds", t)
            Result.failure()
        }
    }

    private fun deletePrivateTemp(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        private const val TAG = "CallModerationUpload"
        private const val APP_VERSION_CODE_HEADER = "X-Hima-Version-Code"
        private const val MAX_ATTEMPTS = 5
        private val ALLOWED_INTERVALS = setOf(60, 300, 600)

        private const val KEY_CALL_ID = "call_id"
        private const val KEY_LOCAL_USER_ID = "local_user_id"
        private const val KEY_INTERVAL_SECONDS = "interval_seconds"
        private const val KEY_CAPTURED_AT = "captured_at"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val KEY_IMAGE_PATH = "image_path"

        fun enqueue(
            context: Context,
            callId: Int,
            localUserId: Int,
            intervalSeconds: Int,
            capturedAt: String,
            consentVersion: String,
            imagePath: String,
        ): Boolean {
            if (callId <= 0 || localUserId <= 0 || intervalSeconds !in ALLOWED_INTERVALS) {
                return false
            }

            val input = Data.Builder()
                .putInt(KEY_CALL_ID, callId)
                .putInt(KEY_LOCAL_USER_ID, localUserId)
                .putInt(KEY_INTERVAL_SECONDS, intervalSeconds)
                .putString(KEY_CAPTURED_AT, capturedAt)
                .putString(KEY_CONSENT_VERSION, consentVersion)
                .putString(KEY_IMAGE_PATH, imagePath)
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<CallModerationUploadWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "call_moderation_upload_${callId}_${localUserId}_$intervalSeconds",
                ExistingWorkPolicy.KEEP,
                request,
            )
            return true
        }
    }
}
