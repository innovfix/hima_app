package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.BlockUserResponse
import javax.inject.Inject

class BlockUserRepository @Inject constructor(private val apiManager: ApiManager) {

    fun blockUser(
        userId: Int,
        callUserId: Int,
        blocked: Int,
        callback: NetworkCallback<BlockUserResponse>
    ) {
        apiManager.blockUser(userId, callUserId, blocked, callback)
    }
}
