package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import javax.inject.Inject
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponse


class FcmNotificationRepository @Inject constructor(private val apiManager: ApiManager) {
    fun sendFcmNotification(
        senderId: Int,
        receiverId: Int,
        callType: String,
        channelName: String,
        message: String,
        callback: NetworkCallback<FcmNotificationResponse>
    ) {
        apiManager.sendFcmNotification(senderId, receiverId, callType, channelName, message, callback)
    }
}

