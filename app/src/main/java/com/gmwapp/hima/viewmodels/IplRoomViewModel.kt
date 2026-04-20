package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.models.IplRoom
import com.gmwapp.hima.models.IplTeam
import com.gmwapp.hima.models.RoomMember
import com.gmwapp.hima.repositories.IplRoomRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class IplRoomViewModel @Inject constructor(
    private val repository: IplRoomRepository
) : ViewModel() {

    private val TAG = "IplRoomViewModel"

    // Room list
    private val _rooms = MutableLiveData<MutableList<IplRoom>>()
    val rooms: LiveData<MutableList<IplRoom>> = _rooms

    // Create room result
    val createRoomLiveData = MutableLiveData<IplRoomCreateResponse>()

    // Join room result
    val joinRoomLiveData = MutableLiveData<IplRoomJoinResponse>()

    // Room details (for polling)
    val roomDetailLiveData = MutableLiveData<IplRoomDetailResponse>()

    // Leave result
    val leaveRoomLiveData = MutableLiveData<IplRoomLeaveResponse>()

    // Match suggestions
    val matchSuggestionsLiveData = MutableLiveData<List<IplMatchData>>()

    // Join by code / random
    val joinByCodeLiveData = MutableLiveData<IplRoomJoinResponse>()
    val joinRandomLiveData = MutableLiveData<IplRoomJoinResponse>()

    // Lookup (without joining)
    val lookupByCodeLiveData = MutableLiveData<IplRoomJoinResponse>()
    val lookupRandomLiveData = MutableLiveData<IplRoomJoinResponse>()

    // Close room
    val closeRoomLiveData = MutableLiveData<IplRoomLeaveResponse>()

    // Error + loading
    val errorLiveData = MutableLiveData<String>()
    val isLoading = MutableLiveData<Boolean>()

    private val PAGE_SIZE = 10
    val hasMoreRooms = MutableLiveData<Boolean>(true)

    fun getRooms(userId: Int, language: String? = null, offset: Int = 0) {
        isLoading.postValue(true)
        viewModelScope.launch {
            repository.getIplRooms(userId, language, PAGE_SIZE, offset, object : NetworkCallback<IplRoomsListResponse> {
                override fun onResponse(call: Call<IplRoomsListResponse>, response: Response<IplRoomsListResponse>) {
                    isLoading.postValue(false)
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        val mapped = body.data.map { it.toIplRoom() }.toMutableList()
                        if (offset == 0) {
                            _rooms.postValue(mapped)
                        } else {
                            val current = _rooms.value ?: mutableListOf()
                            current.addAll(mapped)
                            _rooms.postValue(current)
                        }
                        hasMoreRooms.postValue(mapped.size >= PAGE_SIZE)
                    } else {
                        if (offset == 0) _rooms.postValue(mutableListOf())
                        hasMoreRooms.postValue(false)
                        errorLiveData.postValue(body?.message ?: "Failed to load rooms")
                    }
                }

                override fun onFailure(call: Call<IplRoomsListResponse>, t: Throwable) {
                    isLoading.postValue(false)
                    Log.e(TAG, "getRooms failed: ${t.message}")
                    errorLiveData.postValue(t.message ?: "Network error")
                }

                override fun onNoNetwork() {
                    isLoading.postValue(false)
                    errorLiveData.postValue("No internet connection")
                }
            })
        }
    }

    fun createRoom(userId: Int, roomName: String, teamA: String, teamB: String, creatorTeam: String = "") {
        isLoading.postValue(true)
        viewModelScope.launch {
            repository.createIplRoom(userId, roomName, teamA, teamB, creatorTeam, object : NetworkCallback<IplRoomCreateResponse> {
                override fun onResponse(call: Call<IplRoomCreateResponse>, response: Response<IplRoomCreateResponse>) {
                    isLoading.postValue(false)
                    val body = response.body()
                    if (body != null && body.success) {
                        createRoomLiveData.postValue(body)
                        // Add to local list
                        body.data?.let { roomData ->
                            val current = _rooms.value ?: mutableListOf()
                            current.add(0, roomData.toIplRoom())
                            _rooms.postValue(current)
                        }
                    } else {
                        errorLiveData.postValue(body?.message ?: "Failed to create room")
                    }
                }

                override fun onFailure(call: Call<IplRoomCreateResponse>, t: Throwable) {
                    isLoading.postValue(false)
                    errorLiveData.postValue(t.message ?: "Network error")
                }

                override fun onNoNetwork() {
                    isLoading.postValue(false)
                    errorLiveData.postValue("No internet connection")
                }
            })
        }
    }

    fun joinRoom(userId: Int, roomId: Int) {
        viewModelScope.launch {
            repository.joinIplRoom(userId, roomId, object : NetworkCallback<IplRoomJoinResponse> {
                override fun onResponse(call: Call<IplRoomJoinResponse>, response: Response<IplRoomJoinResponse>) {
                    joinRoomLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<IplRoomJoinResponse>, t: Throwable) {
                    errorLiveData.postValue(t.message ?: "Failed to join room")
                }

                override fun onNoNetwork() {
                    errorLiveData.postValue("No internet connection")
                }
            })
        }
    }

    fun leaveRoom(userId: Int, roomId: Int) {
        viewModelScope.launch {
            repository.leaveIplRoom(userId, roomId, object : NetworkCallback<IplRoomLeaveResponse> {
                override fun onResponse(call: Call<IplRoomLeaveResponse>, response: Response<IplRoomLeaveResponse>) {
                    leaveRoomLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<IplRoomLeaveResponse>, t: Throwable) {
                    Log.e(TAG, "leaveRoom failed: ${t.message}")
                    errorLiveData.postValue("Couldn't leave the room. Please try again.")
                }

                override fun onNoNetwork() {
                    Log.e(TAG, "leaveRoom: no network")
                    errorLiveData.postValue("No internet connection")
                }
            })
        }
    }

    fun getRoomDetails(roomId: Int) {
        viewModelScope.launch {
            repository.getIplRoomDetails(roomId, object : NetworkCallback<IplRoomDetailResponse> {
                override fun onResponse(call: Call<IplRoomDetailResponse>, response: Response<IplRoomDetailResponse>) {
                    roomDetailLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<IplRoomDetailResponse>, t: Throwable) {
                    Log.e(TAG, "getRoomDetails failed: ${t.message}")
                }

                override fun onNoNetwork() {}
            })
        }
    }

    fun sendReaction(userId: Int, roomId: Int, reactionType: String) {
        viewModelScope.launch {
            repository.sendIplReaction(userId, roomId, reactionType, object : NetworkCallback<IplRoomReactionResponse> {
                override fun onResponse(call: Call<IplRoomReactionResponse>, response: Response<IplRoomReactionResponse>) {
                    Log.d(TAG, "Reaction sent: $reactionType")
                    if (!response.isSuccessful || response.body()?.success != true) {
                        errorLiveData.postValue("Couldn't send reaction. Check your internet.")
                    }
                }

                override fun onFailure(call: Call<IplRoomReactionResponse>, t: Throwable) {
                    Log.e(TAG, "sendReaction failed: ${t.message}")
                    errorLiveData.postValue("Couldn't send reaction. Check your internet.")
                }

                override fun onNoNetwork() {
                    errorLiveData.postValue("No internet connection")
                }
            })
        }
    }

    fun toggleMute(userId: Int, roomId: Int, isMuted: Boolean) {
        viewModelScope.launch {
            repository.toggleIplMute(userId, roomId, if (isMuted) 1 else 0, object : NetworkCallback<IplRoomMuteResponse> {
                override fun onResponse(call: Call<IplRoomMuteResponse>, response: Response<IplRoomMuteResponse>) {
                    Log.d(TAG, "Mute toggled: $isMuted")
                }

                override fun onFailure(call: Call<IplRoomMuteResponse>, t: Throwable) {
                    Log.e(TAG, "toggleMute failed: ${t.message}")
                }

                override fun onNoNetwork() {}
            })
        }
    }

    fun getMatchSuggestions() {
        viewModelScope.launch {
            repository.getIplMatchSuggestions(object : NetworkCallback<IplMatchSuggestionsResponse> {
                override fun onResponse(call: Call<IplMatchSuggestionsResponse>, response: Response<IplMatchSuggestionsResponse>) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        matchSuggestionsLiveData.postValue(body.data)
                    } else {
                        errorLiveData.postValue(body?.message ?: "Failed to load matches")
                    }
                }

                override fun onFailure(call: Call<IplMatchSuggestionsResponse>, t: Throwable) {
                    Log.e(TAG, "getMatchSuggestions failed: ${t.message}")
                }

                override fun onNoNetwork() {
                    errorLiveData.postValue("No internet connection")
                }
            })
        }
    }

    fun joinRoomByCode(userId: Int, inviteCode: String) {
        isLoading.postValue(true)
        Log.d(TAG, "joinRoomByCode: userId=$userId, code=$inviteCode")
        viewModelScope.launch {
            repository.joinIplRoomByCode(userId, inviteCode, object : NetworkCallback<IplRoomJoinResponse> {
                override fun onResponse(call: Call<IplRoomJoinResponse>, response: Response<IplRoomJoinResponse>) {
                    isLoading.postValue(false)
                    Log.d(TAG, "joinRoomByCode response: code=${response.code()}, body=${response.body()}, errorBody=${response.errorBody()?.string()}")
                    if (response.isSuccessful) {
                        joinByCodeLiveData.postValue(response.body())
                    } else {
                        errorLiveData.postValue("Server error: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<IplRoomJoinResponse>, t: Throwable) {
                    isLoading.postValue(false)
                    Log.e(TAG, "joinRoomByCode failed", t)
                    errorLiveData.postValue(t.message ?: "Failed to join room")
                }

                override fun onNoNetwork() {
                    isLoading.postValue(false)
                    errorLiveData.postValue("No internet connection")
                }
            })
        }
    }

    fun joinRoomRandom(userId: Int) {
        isLoading.postValue(true)
        Log.d(TAG, "joinRoomRandom: userId=$userId")
        viewModelScope.launch {
            repository.joinIplRoomRandom(userId, object : NetworkCallback<IplRoomJoinResponse> {
                override fun onResponse(call: Call<IplRoomJoinResponse>, response: Response<IplRoomJoinResponse>) {
                    isLoading.postValue(false)
                    Log.d(TAG, "joinRoomRandom response: code=${response.code()}, body=${response.body()}, errorBody=${response.errorBody()?.string()}")
                    if (response.isSuccessful) {
                        joinRandomLiveData.postValue(response.body())
                    } else {
                        errorLiveData.postValue("Server error: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<IplRoomJoinResponse>, t: Throwable) {
                    isLoading.postValue(false)
                    Log.e(TAG, "joinRoomRandom failed", t)
                    errorLiveData.postValue(t.message ?: "Failed to join room")
                }

                override fun onNoNetwork() {
                    isLoading.postValue(false)
                    errorLiveData.postValue("No internet connection")
                }
            })
        }
    }

    fun closeRoom(userId: Int, roomId: Int) {
        viewModelScope.launch {
            repository.closeIplRoom(userId, roomId, object : NetworkCallback<IplRoomLeaveResponse> {
                override fun onResponse(call: Call<IplRoomLeaveResponse>, response: Response<IplRoomLeaveResponse>) {
                    closeRoomLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<IplRoomLeaveResponse>, t: Throwable) {
                    Log.e(TAG, "closeRoom failed: ${t.message}")
                }

                override fun onNoNetwork() {
                    Log.e(TAG, "closeRoom: no network")
                }
            })
        }
    }

    fun lookupRoomByCode(inviteCode: String) {
        viewModelScope.launch {
            repository.lookupRoomByCode(inviteCode, object : NetworkCallback<IplRoomJoinResponse> {
                override fun onResponse(call: Call<IplRoomJoinResponse>, response: Response<IplRoomJoinResponse>) {
                    lookupByCodeLiveData.postValue(response.body())
                }
                override fun onFailure(call: Call<IplRoomJoinResponse>, t: Throwable) {
                    Log.e(TAG, "lookupByCode failed: ${t.message}")
                    errorLiveData.postValue("Failed to find room")
                }
                override fun onNoNetwork() {
                    errorLiveData.postValue("No network")
                }
            })
        }
    }

    fun lookupRoomRandom(userId: Int) {
        viewModelScope.launch {
            repository.lookupRoomRandom(userId, object : NetworkCallback<IplRoomJoinResponse> {
                override fun onResponse(call: Call<IplRoomJoinResponse>, response: Response<IplRoomJoinResponse>) {
                    lookupRandomLiveData.postValue(response.body())
                }
                override fun onFailure(call: Call<IplRoomJoinResponse>, t: Throwable) {
                    Log.e(TAG, "lookupRandom failed: ${t.message}")
                    errorLiveData.postValue("Failed to find room")
                }
                override fun onNoNetwork() {
                    errorLiveData.postValue("No network")
                }
            })
        }
    }

    fun listenerJoin(userId: Int, roomId: Int) {
        viewModelScope.launch {
            repository.listenerJoin(userId, roomId, object : NetworkCallback<IplRoomLeaveResponse> {
                override fun onResponse(call: Call<IplRoomLeaveResponse>, response: Response<IplRoomLeaveResponse>) {
                    Log.d(TAG, "Listener join tracked")
                }
                override fun onFailure(call: Call<IplRoomLeaveResponse>, t: Throwable) {
                    Log.e(TAG, "listenerJoin failed: ${t.message}")
                }
                override fun onNoNetwork() {
                    Log.e(TAG, "listenerJoin: no network")
                }
            })
        }
    }

    fun listenerLeave(userId: Int, roomId: Int) {
        viewModelScope.launch {
            repository.listenerLeave(userId, roomId, object : NetworkCallback<IplRoomLeaveResponse> {
                override fun onResponse(call: Call<IplRoomLeaveResponse>, response: Response<IplRoomLeaveResponse>) {
                    Log.d(TAG, "Listener leave tracked")
                }
                override fun onFailure(call: Call<IplRoomLeaveResponse>, t: Throwable) {
                    Log.e(TAG, "listenerLeave failed: ${t.message}")
                }
                override fun onNoNetwork() {
                    Log.e(TAG, "listenerLeave: no network")
                }
            })
        }
    }

    fun updateIplTeam(userId: Int, iplTeam: String) {
        viewModelScope.launch {
            repository.updateIplTeam(userId, iplTeam, object : NetworkCallback<UpdateIplTeamResponse> {
                override fun onResponse(call: Call<UpdateIplTeamResponse>, response: Response<UpdateIplTeamResponse>) {
                    Log.d(TAG, "IPL team updated: $iplTeam")
                }

                override fun onFailure(call: Call<UpdateIplTeamResponse>, t: Throwable) {
                    Log.e(TAG, "updateIplTeam failed: ${t.message}")
                }

                override fun onNoNetwork() {
                    Log.e(TAG, "updateIplTeam: no network")
                }
            })
        }
    }
}

// Extension: map API response to local model
fun IplRoomData.toIplRoom(): IplRoom {
    val teamAEnum = IplTeam.values().find { it.abbreviation == teamA } ?: IplTeam.MI
    val teamBEnum = IplTeam.values().find { it.abbreviation == teamB } ?: IplTeam.CSK
    return IplRoom(
        id = id,
        name = name,
        teamA = teamAEnum,
        teamB = teamBEnum,
        creatorName = creatorName,
        members = emptyList(),
        memberCount = memberCount,
        maxMembers = maxMembers,
        isLive = isLive,
        inviteCode = inviteCode
    )
}

fun IplMemberData.toRoomMember(): RoomMember {
    return RoomMember(
        id = id,
        name = name,
        avatarUrl = avatarUrl ?: "",
        isMuted = isMuted,
        isSpeaking = isSpeaking ?: false,
        isCreator = isCreator,
        elapsedMinutes = elapsedMinutes ?: 0,
        elapsedSeconds = elapsedSeconds ?: 0,
        remainingMinutes = remainingMinutes ?: 0,
        remainingSeconds = remainingSeconds ?: 0,
        iplTeam = iplTeam
    )
}
