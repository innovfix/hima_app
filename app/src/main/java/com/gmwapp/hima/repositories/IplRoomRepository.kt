package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.*
import javax.inject.Inject

class IplRoomRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun getIplRooms(userId: Int, callback: NetworkCallback<IplRoomsListResponse>) {
        apiManager.getIplRooms(userId, callback)
    }

    fun createIplRoom(userId: Int, roomName: String, teamA: String, teamB: String, callback: NetworkCallback<IplRoomCreateResponse>) {
        apiManager.createIplRoom(userId, roomName, teamA, teamB, callback)
    }

    fun joinIplRoom(userId: Int, roomId: Int, callback: NetworkCallback<IplRoomJoinResponse>) {
        apiManager.joinIplRoom(userId, roomId, callback)
    }

    fun leaveIplRoom(userId: Int, roomId: Int, callback: NetworkCallback<IplRoomLeaveResponse>) {
        apiManager.leaveIplRoom(userId, roomId, callback)
    }

    fun getIplRoomDetails(roomId: Int, callback: NetworkCallback<IplRoomDetailResponse>) {
        apiManager.getIplRoomDetails(roomId, callback)
    }

    fun sendIplReaction(userId: Int, roomId: Int, reactionType: String, callback: NetworkCallback<IplRoomReactionResponse>) {
        apiManager.sendIplReaction(userId, roomId, reactionType, callback)
    }

    fun toggleIplMute(userId: Int, roomId: Int, isMuted: Int, callback: NetworkCallback<IplRoomMuteResponse>) {
        apiManager.toggleIplMute(userId, roomId, isMuted, callback)
    }

    fun getIplMatchSuggestions(callback: NetworkCallback<IplMatchSuggestionsResponse>) {
        apiManager.getIplMatchSuggestions(callback)
    }
}
