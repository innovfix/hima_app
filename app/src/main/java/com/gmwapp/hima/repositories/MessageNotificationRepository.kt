package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MessageNotificationResponse
import javax.inject.Inject

class MessageNotificationRepository @Inject constructor(private val apiManager: ApiManager) {
    
    fun sendMessageNotification(
        senderId: Int,
        receiverId: Int,
        message: String,
        callback: NetworkCallback<MessageNotificationResponse>
    ) {
        apiManager.sendMessageNotification(senderId, receiverId, message, callback)
    }
}


