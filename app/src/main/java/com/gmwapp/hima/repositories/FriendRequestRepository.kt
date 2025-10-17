package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FriendRequestResponse
import com.gmwapp.hima.retrofit.responses.MyFriendRequestsResponse
import com.gmwapp.hima.retrofit.responses.ReceivedFriendRequestsResponse
import com.gmwapp.hima.retrofit.responses.FriendListResponse
import javax.inject.Inject

class FriendRequestRepository @Inject constructor(private val apiManager: ApiManager) {
    
    fun sendFriendRequest(
        senderId: Int,
        receiverId: Int,
        status: Int,
        callback: NetworkCallback<FriendRequestResponse>
    ) {
        apiManager.sendFriendRequest(senderId, receiverId, status, callback)
    }

    fun getMyFriendRequests(
        senderId: Int,
        callback: NetworkCallback<MyFriendRequestsResponse>
    ) {
        apiManager.getMyFriendRequests(senderId, callback)
    }

    fun getReceivedFriendRequests(
        receiverId: Int,
        callback: NetworkCallback<ReceivedFriendRequestsResponse>
    ) {
        apiManager.getReceivedFriendRequests(receiverId, callback)
    }

    fun getFriendsList(
        senderId: Int,
        callback: NetworkCallback<FriendListResponse>
    ) {
        apiManager.getFriendsList(senderId, callback)
    }

    fun checkFriendRequest(
        senderId: Int,
        receiverId: Int,
        userId: Int,
        callback: NetworkCallback<FriendRequestResponse>
    ) {
        apiManager.checkFriendRequest(senderId, receiverId, userId, callback)
    }
}

