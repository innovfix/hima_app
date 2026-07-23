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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Durably delivers a `call_status` outcome to the backend.
 *
 * REJECT_FALSE_MISS_2026_07_09 — a female who declines an incoming call posts
 * call_status(end_reason=rejected). The inline post is fire-and-forget: if the
 * device is offline (e.g. "Emergency calls only") or the app is dying, that POST
 * is dropped, the server never learns the call was rejected, and the missed-call
 * backstop then pushes "Missed call" to the very person who rejected. This worker
 * is the durable backstop: WorkManager holds the job across process death / no
 * network (NetworkType.CONNECTED constraint + exponential backoff) and fires it
 * when connectivity returns, so the reject reliably reaches the server. call_status
 * is idempotent server-side ("already recorded"), so a redundant online post is a
 * harmless no-op alongside the inline call.
 */
class CallStatusWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Bound the retry tail so a permanently-failing job can't run forever.
        if (runAttemptCount > MAX_ATTEMPTS) {
            Log.w(TAG, "Giving up after $runAttemptCount attempts")
            return@withContext Result.failure()
        }

        val userId = inputData.getInt(KEY_USER_ID, 0)
        val callId = inputData.getInt(KEY_CALL_ID, 0)
        val endReason = inputData.getString(KEY_END_REASON).orEmpty()
        // Permanent-invalid inputs the server rejects outright — never retry.
        if (userId <= 0 || callId <= 0 || endReason.isBlank()) {
            Log.w(TAG, "Invalid input (user=$userId call=$callId reason=$endReason); dropping")
            return@withContext Result.failure()
        }
        val authToken = inputData.getString(KEY_AUTH_TOKEN).orEmpty()
        if (authToken.isBlank()) {
            Log.w(TAG, "Missing auth token for call=$callId; dropping")
            return@withContext Result.failure()
        }

        val receivedUserId = inputData.getInt(KEY_RECEIVED_USER_ID, 0)
        val endedBy = inputData.getString(KEY_ENDED_BY).orEmpty()
        val endedByUserId = inputData.getInt(KEY_ENDED_BY_USER_ID, 0)
        val durationSeconds = inputData.getInt(KEY_DURATION_SECONDS, 0)

        val json = JSONObject().apply {
            put("user_id", userId)
            put("received_user_id", receivedUserId)
            put("call_id", callId)
            put("end_reason", endReason)
            put("ended_by", endedBy)
            if (endedByUserId > 0) put("ended_by_user_id", endedByUserId)
            put("duration_seconds", durationSeconds)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}call_status")
            .post(body)
            .header("Authorization", "Bearer $authToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                return@withContext when {
                    // 2xx incl. the server's idempotent "already recorded" / "not found"
                    // 200s — the outcome is settled, no point retrying.
                    response.isSuccessful -> {
                        Log.d(TAG, "call_status($endReason) delivered for call=$callId")
                        Result.success()
                    }
                    response.code in 400..499 -> {
                        Log.w(TAG, "Permanent HTTP ${response.code} for call=$callId; dropping")
                        Result.failure()
                    }
                    else -> {
                        Log.w(TAG, "Transient HTTP ${response.code} for call=$callId; will retry")
                        Result.retry()
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network failure for call=$callId: ${e.message}; will retry")
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error for call=$callId: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CallStatusWorker"
        private const val MAX_ATTEMPTS = 12

        const val KEY_USER_ID = "user_id"
        const val KEY_RECEIVED_USER_ID = "received_user_id"
        const val KEY_CALL_ID = "call_id"
        const val KEY_END_REASON = "end_reason"
        const val KEY_ENDED_BY = "ended_by"
        const val KEY_ENDED_BY_USER_ID = "ended_by_user_id"
        const val KEY_DURATION_SECONDS = "duration_seconds"
        const val KEY_AUTH_TOKEN = "auth_token"

        /**
         * Durable backstop for a REJECT outcome. Safe to call alongside the inline
         * call_status post — server is idempotent. Reads the signed-in user's auth
         * token from prefs; no-ops if unavailable. Unique per call so duplicate
         * enqueues (multiple reject paths for the same ring) collapse to one job
         * and an in-flight retry is never cancelled.
         */
        @JvmStatic
        fun enqueueReject(context: Context, selfUserId: Int, peerUserId: Int, callId: Int) {
            enqueueTerminal(
                context = context,
                selfUserId = selfUserId,
                peerUserId = peerUserId,
                callId = callId,
                endReason = "rejected",
                endedBy = "receiver"
            )
        }

        /**
         * Durable close for a new incoming row that this device auto-rejected
         * because it was already handling another call/ring. The backend terminal
         * compare-and-set makes a late retry harmless if that row connected or was
         * closed by another actor first.
         */
        @JvmStatic
        fun enqueueNotAnsweredByReceiver(
            context: Context,
            selfUserId: Int,
            peerUserId: Int,
            callId: Int
        ) {
            enqueueTerminal(
                context = context,
                selfUserId = selfUserId,
                peerUserId = peerUserId,
                callId = callId,
                endReason = "not_answered",
                endedBy = "receiver"
            )
        }

        private fun enqueueTerminal(
            context: Context,
            selfUserId: Int,
            peerUserId: Int,
            callId: Int,
            endReason: String,
            endedBy: String
        ) {
            if (selfUserId <= 0 || callId <= 0) {
                Log.w(TAG, "enqueueTerminal skipped (self=$selfUserId call=$callId reason=$endReason)")
                return
            }
            val authToken = BaseApplication.getInstance()?.getPrefs()?.getAuthenticationToken().orEmpty()
            if (authToken.isBlank()) {
                Log.w(TAG, "enqueueTerminal skipped — no auth token (call=$callId reason=$endReason)")
                return
            }

            val input = Data.Builder()
                .putInt(KEY_USER_ID, selfUserId)
                .putInt(KEY_RECEIVED_USER_ID, peerUserId)
                .putInt(KEY_CALL_ID, callId)
                .putString(KEY_END_REASON, endReason)
                .putString(KEY_ENDED_BY, endedBy)
                .putInt(KEY_ENDED_BY_USER_ID, selfUserId)
                .putInt(KEY_DURATION_SECONDS, 0)
                .putString(KEY_AUTH_TOKEN, authToken)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CallStatusWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "call_status_${endReason}_$callId",
                ExistingWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Enqueued durable $endReason call_status for call=$callId")
        }
    }
}
