package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CreatorWarningsResponse
import javax.inject.Inject

class CreatorWarningsRepository @Inject constructor(private val apiManager: ApiManager) {

    fun getCreatorWarnings(
        userId: Int,
        callback: NetworkCallback<CreatorWarningsResponse>
    ) {
        apiManager.getCreatorWarnings(userId, callback)
    }
}
