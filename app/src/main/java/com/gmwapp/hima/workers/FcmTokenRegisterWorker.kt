package com.gmwapp.hima.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gmwapp.hima.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

// Registers the current device's FCM token against the signed-in user.
// Used as a safety net from FirebaseMessagingService.onNewToken (which is not a Hilt
// entry point) so token rotations reach the backend without requiring MainActivity.
class FcmTokenRegisterWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = inputData.getInt(KEY_USER_ID, 0)
        if (userId <= 0) {
            Log.w(TAG, "Missing user_id; dropping work")
            return@withContext Result.failure()
        }
        val token = inputData.getString(KEY_TOKEN).orEmpty()
        if (token.isBlank()) {
            Log.w(TAG, "Missing token for user=$userId; dropping work")
            return@withContext Result.failure()
        }
        val authToken = inputData.getString(KEY_AUTH_TOKEN).orEmpty()

        val body = FormBody.Builder()
            .add("user_id", userId.toString())
            .add("token", token)
            .build()

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}send_fcm_token")
            .post(body)
            .header("Authorization", "Bearer $authToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                return@withContext when {
                    response.isSuccessful -> {
                        Log.d(TAG, "FCM token registered for user=$userId")
                        Result.success()
                    }
                    response.code in 400..499 -> {
                        Log.w(TAG, "Permanent HTTP ${response.code} for user=$userId")
                        Result.failure()
                    }
                    else -> {
                        Log.w(TAG, "Transient HTTP ${response.code} — will retry")
                        Result.retry()
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network failure: ${e.message} — will retry")
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "FcmTokenRegister"
        const val KEY_USER_ID = "user_id"
        const val KEY_TOKEN = "token"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val WORK_NAME_PREFIX = "fcm_token_register_"
    }
}
