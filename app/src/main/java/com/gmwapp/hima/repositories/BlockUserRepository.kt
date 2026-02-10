package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.BlockUserResponse
import com.gmwapp.hima.retrofit.responses.CheckBlockStatusResponse
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

    fun checkBlockStatus(
        userId: Int,
        callUserId: Int,
        callback: NetworkCallback<CheckBlockStatusResponse>
    ) {
        apiManager.checkBlockStatus(userId, callUserId, callback)
    }

    fun unblockUser(
        userId: Int,
        callUserId: Int,
        callback: NetworkCallback<BlockUserResponse>
    ) {
        apiManager.unblockUser(userId, callUserId, callback)
    }
}