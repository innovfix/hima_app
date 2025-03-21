package com.gmwapp.hima.repositories


import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.UserAvatarResponse
import javax.inject.Inject

class UserAvatarRepository @Inject constructor(private val apiManager: ApiManager) {

    fun getUserAvatar(
        userId: Int,
        callback: NetworkCallback<UserAvatarResponse>
    ) {
        apiManager.getUserAvatar(userId, callback)
    }
}
