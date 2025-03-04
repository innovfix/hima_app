package com.gmwapp.hima.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.FemaleUsersRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.UpdateConnectedCallResponse
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@HiltWorker
class CallUpdateWorker @AssistedInject constructor(
    val femaleUsersRepositories: FemaleUsersRepositories,
    @Assisted appContext: Context,
    @Assisted val workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                var updateConnectedCall: Response<UpdateConnectedCallResponse>? = null
                if (workerParams.inputData.getBoolean(DConstants.IS_INDIVIDUAL, false)) {
                    updateConnectedCall = femaleUsersRepositories.individualUpdateConnectedCall(
                        workerParams.inputData.getInt(DConstants.USER_ID, 0),
                        workerParams.inputData.getInt(DConstants.CALL_ID, 0),
                        workerParams.inputData.getString(DConstants.STARTED_TIME).toString(),
                        workerParams.inputData.getString(DConstants.ENDED_TIME).toString(),
                    )
                } else {
                    updateConnectedCall = femaleUsersRepositories.updateConnectedCall(
                        workerParams.inputData.getInt(DConstants.USER_ID, 0),
                        workerParams.inputData.getInt(DConstants.CALL_ID, 0),
                        workerParams.inputData.getString(DConstants.STARTED_TIME).toString(),
                        workerParams.inputData.getString(DConstants.ENDED_TIME).toString(),
                    )
                }

                if (updateConnectedCall.isSuccessful == true) {
                    if (updateConnectedCall.body()?.success == true) {
                        Result.success()

                    } else {
                        Result.failure()
                    }
                } else {
                    Result.failure()
                }
            } catch (e: Exception) {
                Result.failure()
            }

        }
    }
}
