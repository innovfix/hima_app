package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallStatusRequest
import com.gmwapp.hima.retrofit.responses.CallStatusResponse
import javax.inject.Inject

class CallStatusRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun callStatus(
        request: CallStatusRequest,
        callback: NetworkCallback<CallStatusResponse>
    ) {
        apiManager.callStatus(request, callback)
    }
}
