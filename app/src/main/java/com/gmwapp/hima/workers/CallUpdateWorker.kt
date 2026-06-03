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
                    // W055: the server has now debited coins for this call.
                    // Signal the coin header to re-fetch; otherwise it keeps
                    // showing the pre-debit balance until some later getUsers.
                    applicationContext.sendBroadcast(
                        android.content.Intent(DConstants.ACTION_COINS_REFRESH)
                            .setPackage(applicationContext.packageName)
                    )
                    // The call-end record is now committed server-side. Tell the
                    // Recent/Favourite lists to refresh: the flag covers a later
                    // onResume (list not foregrounded), the broadcast covers an
                    // immediate in-place reload if the list is already on screen.
                    // Without this, this deferred worker finishes AFTER the list
                    // already reloaded, so the just-ended call never appears
                    // until a manual pull-to-refresh.
                    com.gmwapp.hima.agora.FcmUtils.shouldRefreshCallList = 1
                    applicationContext.sendBroadcast(
                        android.content.Intent(DConstants.ACTION_CALL_LIST_REFRESH)
                            .setPackage(applicationContext.packageName)
                    )
                    Result.success()
                } else {
                    // KNOWN GAP: on a failed write we do NOT signal a list refresh,
                    // so a call whose end-record failed won't appear on Recent until
                    // a manual pull-to-refresh. We deliberately do NOT Result.retry()
                    // here: this POST is non-idempotent (records/debits the call), so
                    // a retry after a server-side success-but-lost-response would
                    // double-record. Reliable retry needs server-side idempotency
                    // (dedupe by call_id) before it's safe to enable.
                    Log.e("CallUpdateWorkerCheck", "Call update failed with code: ${response.code}")
                    Result.failure()
                }
            } catch (e: Exception) {
                Log.e("CallUpdateWorkerCheck", "Exception: ${e.localizedMessage}", e)
                Result.failure()
            }
        }
    }
}
