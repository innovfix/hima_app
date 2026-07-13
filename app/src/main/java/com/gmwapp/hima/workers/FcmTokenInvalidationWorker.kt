package com.gmwapp.hima.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.gmwapp.hima.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

// Logout clears local state immediately, but the server-side FCM token must still be
// cleared so push notifications stop targeting this device for the old account.
// Runs via WorkManager so it survives process death and offline windows.
class FcmTokenInvalidationWorker(
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

        // If the same user is currently signed in when this worker runs, the
        // logout that scheduled us has already been undone by a subsequent
        // re-login (the login flow also calls cancelUniqueWork, but cancellation
        // cannot stop every request already in flight). A different signed-in
        // user is safe: the server deletes only this old user's exact token.
        if (isSameUserSignedIn(userId)) {
            Log.w(
                TAG,
                "User $userId is signed in again — aborting stale token invalidation"
            )
            return@withContext Result.success()
        }

        val authToken = inputData.getString(KEY_AUTH_TOKEN).orEmpty()
        if (authToken.isBlank()) {
            Log.w(TAG, "Missing auth token; dropping invalidation for user=$userId")
            return@withContext Result.failure()
        }

        val fcmToken = try {
            Tasks.await(
                FirebaseMessaging.getInstance().token,
                20,
                TimeUnit.SECONDS
            ).orEmpty().trim()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to fetch FCM token for user=$userId: ${e.message}")
            return@withContext Result.retry()
        }

        if (fcmToken.isBlank() || fcmToken == "0") {
            Log.w(TAG, "Firebase returned no usable token for user=$userId")
            return@withContext Result.retry()
        }

        val body = FormBody.Builder()
            .add("token", fcmToken)
            .build()

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}invalidate_fcm_token")
            .post(body)
            .header("Authorization", "Bearer $authToken")
            .build()

        // getToken() can wait for up to 20 seconds. Close the resulting relogin
        // race by checking the signed-in account again immediately before I/O.
        if (isSameUserSignedIn(userId)) {
            Log.w(
                TAG,
                "User $userId signed in while resolving FCM token — skipping invalidation"
            )
            return@withContext Result.success()
        }

        try {
            client.newCall(request).execute().use { response ->
                return@withContext when {
                    response.isSuccessful -> {
                        Log.d(TAG, "FCM token invalidated safely for user=$userId")
                        Result.success()
                    }
                    response.code in 400..499 -> {
                        // Bad request, unauthorized, or user not found — retrying won't help.
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

    private fun isSameUserSignedIn(userId: Int): Boolean {
        val currentUserId = runCatching {
            com.gmwapp.hima.utils.DPreferences(applicationContext).getUserData()?.id ?: 0
        }.getOrNull() ?: 0
        return currentUserId == userId
    }

    companion object {
        private const val TAG = "FcmTokenInvalidate"
        const val KEY_USER_ID = "user_id"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val WORK_NAME_PREFIX = "fcm_token_invalidate_"
    }
}
