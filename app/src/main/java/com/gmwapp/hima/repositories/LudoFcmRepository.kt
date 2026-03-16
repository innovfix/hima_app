package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.LudoFcmResponse
import javax.inject.Inject

class LudoFcmRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun sendLudoFcm(
        action: String,
        fromUserId: Int? = null,
        toUserId: Int? = null,
        inviteId: String? = null,
        callId: String? = null,
        callback: NetworkCallback<LudoFcmResponse>
    ) {
        apiManager.sendLudoFcm(
            action = action,
            fromUserId = fromUserId,
            toUserId = toUserId,
            inviteId = inviteId,
            callId = callId,
            callback = callback
        )
    }
}
