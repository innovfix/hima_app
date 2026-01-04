package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AgoraTokenResponse
import javax.inject.Inject

class AgoraRepository @Inject constructor(private val apiManager: ApiManager) {

    fun getAgoraToken(
        channelName: String,
        uid: Int,
        role: String = "publisher",
        expire: Int = 3600,
        callback: NetworkCallback<AgoraTokenResponse>
    ) {
        apiManager.getAgoraToken(channelName, uid, role, expire, callback)
    }
}
