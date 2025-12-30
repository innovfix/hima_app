package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.UpdateIpResponse
import javax.inject.Inject

class UpdateIpRepository @Inject constructor(private val apiManager: ApiManager) {
    fun updateIp(
        callback: NetworkCallback<UpdateIpResponse>
    ) {
        apiManager.updateIp(callback)
    }
}

