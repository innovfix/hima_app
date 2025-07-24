package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.IndividualAppUpdateResponse
import javax.inject.Inject

class IndividualAppUpdateRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun updateUserVersion(
        userId: Int,
        currentVersion: String,
        callback: NetworkCallback<IndividualAppUpdateResponse>
    ) {
        apiManager.individualAppUpdate(userId, currentVersion, callback)
    }
}
