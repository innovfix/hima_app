package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallFemaleUserResponse
import com.gmwapp.hima.retrofit.responses.FemaleCallAttendResponse
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponse
import com.gmwapp.hima.retrofit.responses.RandomUsersResponse
import com.gmwapp.hima.retrofit.responses.ReportsResponse
import com.gmwapp.hima.retrofit.responses.TransactionsResponse
import com.gmwapp.hima.retrofit.responses.UpdateCallStatusResponse
import com.gmwapp.hima.retrofit.responses.UpdateConnectedCallResponse
import retrofit2.Response
import javax.inject.Inject

class FemaleUsersRepositories @Inject constructor(private val apiManager: ApiManager) {
    fun getFemaleUsers(userId: Int, callback: NetworkCallback<FemaleUsersResponse>) {
        apiManager.getFemaleUsers(userId, callback)
    }

    fun getRandomUser(userId: Int,callType: String, callback: NetworkCallback<RandomUsersResponse>) {
        apiManager.getRandomUser(userId,callType, callback)
    }

  fun getCalldetailsUser(userId: Int, callback: NetworkCallback<ReportsResponse>) {
        apiManager.getCalldetails(userId,callback)
    }

    fun updateCallStatus(
        userId: Int,
        callType: String,
        status: Int,
        callback: NetworkCallback<UpdateCallStatusResponse>
    ) {
        apiManager.updateCallStatus(
            userId, callType, status, callback
        )
    }

    fun femaleCallAttend(
        userId: Int,
        callId: Int,
        startTime: String,
        callback: NetworkCallback<FemaleCallAttendResponse>
    ) {
        apiManager.femaleCallAttend(
            userId, callId, startTime, callback
        )
    }

    fun callFemaleUser(
        userId: Int,
        callUserId: Int,
        callType: String,
        call_switch: Int,
        callback: NetworkCallback<CallFemaleUserResponse>
    ) {
        apiManager.callFemaleUser(
            userId, callUserId, callType,call_switch, callback
        )
    }

    suspend fun updateConnectedCall(
        userId: Int,
        callId: Int,
        startedTime: String,
        endedTime: String,
    ) : Response<UpdateConnectedCallResponse> {
        return apiManager.updateConnectedCall(
            userId, callId, startedTime,endedTime
        )
    }

    suspend fun individualUpdateConnectedCall(
        userId: Int,
        callId: Int,
        startedTime: String,
        endedTime: String,
    ) : Response<UpdateConnectedCallResponse> {
        return apiManager.individualUpdateConnectedCall(
            userId, callId, startedTime,endedTime
        )
    }
}