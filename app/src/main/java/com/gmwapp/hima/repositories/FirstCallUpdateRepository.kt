package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FirstCallUpdateResponse
import javax.inject.Inject

class FirstCallUpdateRepository @Inject constructor(private val apiManager: ApiManager) {

    fun updateFirstCallStatus(
        userId: Int,
        callStatus: Int,
        callback: NetworkCallback<FirstCallUpdateResponse>
    ) {
        apiManager.updateFirstCallStatus(userId, callStatus, callback)
    }
}
