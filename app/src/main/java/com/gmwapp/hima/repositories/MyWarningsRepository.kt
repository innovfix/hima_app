package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MyWarningsResponse
import javax.inject.Inject

class MyWarningsRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun getMyWarnings(
        userId: Int,
        callback: NetworkCallback<MyWarningsResponse>
    ) {
        apiManager.getMyWarnings(userId, callback)
    }
}

