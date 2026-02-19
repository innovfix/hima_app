package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallDropStatusRequest
import com.gmwapp.hima.retrofit.responses.CallDropStatusResponse
import javax.inject.Inject

class CallDropStatusRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun callDropStatus(
        request: CallDropStatusRequest,
        callback: NetworkCallback<CallDropStatusResponse>
    ) {
        apiManager.callDropStatus(request, callback)
    }
}

