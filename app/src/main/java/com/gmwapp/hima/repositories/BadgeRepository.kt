package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.BadgeResponse
import javax.inject.Inject

class BadgeRepository @Inject constructor(private val apiManager: ApiManager) {
    fun getBadgesInformation(callback: NetworkCallback<BadgeResponse>) {
        apiManager.getBadgesInformation(callback)
    }
}
