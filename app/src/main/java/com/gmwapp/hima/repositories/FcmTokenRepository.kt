package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import javax.inject.Inject
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FcmTokenResponse


class FcmTokenRepository @Inject constructor(private val apiManager: ApiManager) {
    fun sendFcmToken(
        userId: Int,
        token: String,
        callback: NetworkCallback<FcmTokenResponse>
    ) {
        apiManager.sendFcmToken(userId, token, callback)
    }
}
