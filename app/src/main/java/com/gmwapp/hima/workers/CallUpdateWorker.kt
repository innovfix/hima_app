package com.gmwapp.hima.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.constants.DConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CallUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(100, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
        .build()

    // [B3] max WorkManager runs before we stop retrying a transient failure.
    // Paired with EXPONENTIAL backoff (30s base) on the request → ~30s,1m,2m,4m…
    private val MAX_ATTEMPTS = 5

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("CallUpdateWorkerCheck", "Starting worker execution")

                val userId = inputData.getInt(DConstants.USER_ID, 0)
                val callId = inputData.getInt(DConstants.CALL_ID, 0)
                val startedTime = inputData.getString(DConstants.STARTED_TIME) ?: ""
                val endedTime = inputData.getString(DConstants.ENDED_TIME) ?: ""
                val isIndividual = inputData.getBoolean(DConstants.IS_INDIVIDUAL, false)

                Log.d("CallUpdateWorkerCheck", "User ID: $userId, Call ID: $callId, Started Time: $startedTime, Ended Time: $endedTime")

                val endpoint = if (isIndividual) {
                    Log.d("CallUpdateWorkerCheck", "Updating Individual Call")
                    "individual_update_connected_call"
                } else {
                    Log.d("CallUpdateWorkerCheck", "Updating Group Call")
                    "update_connected_call"
                }

                val body = FormBody.Builder()
                    .add("user_id", userId.toString())
                    .add("call_id", callId.toString())
                    .add("started_time", startedTime)
                    .add("ended_time", endedTime)
                    .build()

                val authToken = com.gmwapp.hima.BaseApplication.getInstance()?.getPrefs()?.getAuthenticationToken() ?: ""
                val request = Request.Builder()
                    .url("${BuildConfig.BASE_URL}$endpoint")
                    .post(body)
                    .header("Authorization", "Bearer $authToken")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                Log.d("CallUpdateWorkerCheck", "Response code: ${response.code}, body: $responseBody")

                if (response.isSuccessful) {
                    Log.d("CallUpdateWorkerCheck", "Call update successful")
                    Result.success()
                } else {
                    // [B3] Silent Worker Failure fix (ticket #47424): a TRANSIENT server
                    // error (5xx / 408 / 429) must NOT permanently lose the call — retry
                    // with the request's exponential backoff. update_connected_call is an
                    // idempotent UPDATE by call_id (+ enqueueUniqueWork KEEP), so a retry
                    // can't double-bill. 4xx = permanent client error → give up. Cap total
                    // attempts so a genuinely-down backend eventually stops.
                    val code = response.code
                    val transient = code >= 500 || code == 408 || code == 429
                    Log.e("CallUpdateWorkerCheck", "Call update failed code=$code transient=$transient attempt=$runAttemptCount")
                    if (transient && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                }
            } catch (e: Exception) {
                // Network/IO exception (timeout, connection reset, DNS) = transient → retry.
                Log.e("CallUpdateWorkerCheck", "Exception (attempt=$runAttemptCount): ${e.localizedMessage}", e)
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        }
    }
}
