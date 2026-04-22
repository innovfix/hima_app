package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.*
import javax.inject.Inject

class IplRoomRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun getIplRooms(userId: Int, language: String? = null, limit: Int = 10, offset: Int = 0, callback: NetworkCallback<IplRoomsListResponse>) {
        apiManager.getIplRooms(userId, language, limit, offset, callback)
    }

    fun createIplRoom(userId: Int, roomName: String, teamA: String, teamB: String, creatorTeam: String, callback: NetworkCallback<IplRoomCreateResponse>) {
        apiManager.createIplRoom(userId, roomName, teamA, teamB, creatorTeam, callback)
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

    fun joinIplRoomByCode(userId: Int, inviteCode: String, callback: NetworkCallback<IplRoomJoinResponse>) {
        apiManager.joinIplRoomByCode(userId, inviteCode, callback)
    }

    fun joinIplRoomRandom(userId: Int, callback: NetworkCallback<IplRoomJoinResponse>) {
        apiManager.joinIplRoomRandom(userId, callback)
    }

    fun closeIplRoom(userId: Int, roomId: Int, callback: NetworkCallback<IplRoomLeaveResponse>) {
        apiManager.closeIplRoom(userId, roomId, callback)
    }

    fun updateIplTeam(userId: Int, iplTeam: String, callback: NetworkCallback<UpdateIplTeamResponse>) {
        apiManager.updateIplTeam(userId, iplTeam, callback)
    }

    fun lookupRoomByCode(inviteCode: String, callback: NetworkCallback<IplRoomJoinResponse>) {
        apiManager.lookupRoomByCode(inviteCode, callback)
    }

    fun lookupRoomRandom(userId: Int, callback: NetworkCallback<IplRoomJoinResponse>) {
        apiManager.lookupRoomRandom(userId, callback)
    }

    fun listenerJoin(userId: Int, roomId: Int, callback: NetworkCallback<IplRoomLeaveResponse>) {
        apiManager.listenerJoin(userId, roomId, callback)
    }

    fun listenerLeave(userId: Int, roomId: Int, callback: NetworkCallback<IplRoomLeaveResponse>) {
        apiManager.listenerLeave(userId, roomId, callback)
    }
}
