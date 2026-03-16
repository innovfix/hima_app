package com.gmwapp.hima.agora

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object FcmUtils {
    data class LudoEvent(
        val type: String,
        val inviteId: String? = null,
        val roomCode: String? = null,
        val fromUserId: Int? = null,
        val fromUserName: String? = null,
        val joinUrl: String? = null
    )

    private val _callStatus = MutableLiveData<Pair<String, String>?>()  // Make it nullable
    val callStatus: LiveData<Pair<String, String>?> get() = _callStatus

    private val _callDeclinedStatus = MutableLiveData<Boolean>()
    val callDeclinedStatus: LiveData<Boolean> get() = _callDeclinedStatus

    var blockWordDetected = false

    var isUserAvailable = 1

    var shouldRefreshCallList = 0

    private val _updatedTime = MutableLiveData<String?>()
    val updatedTime: LiveData<String?> get() = _updatedTime

    private val _updatedCallSwitch = MutableLiveData<Pair<String, Int>?>()
    val updatedCallSwitch: LiveData<Pair<String, Int>?> get() = _updatedCallSwitch

    private val _userBusyStatus = MutableLiveData<Pair<String, String>?>() // (callType, userName)
    val userBusyStatus: LiveData<Pair<String, String>?> get() = _userBusyStatus

    private val _ludoEvent = MutableLiveData<LudoEvent?>()
    val ludoEvent: LiveData<LudoEvent?> get() = _ludoEvent

    fun updateCallStatus(status: String, channelName: String) {
        _callStatus.postValue(Pair(status, channelName))
        Log.d("FcmUtils", "Call status updated: $status, Channel: $channelName")
    }

    fun clearCallStatus() {
        _callStatus.postValue(null)
        Log.d("FcmUtils", "Call status cleared")
    }



    fun updateRemainingTime(message: String) {
        _updatedTime.postValue(message)
    }

    fun clearRemainingTime() {
        _updatedTime.postValue(null)
    }



    fun UpdateCallSwitch(message: String, senderId: Int) {
        _updatedCallSwitch.postValue(Pair(message,senderId))
    }

    fun clearCallSwitch() {
        _updatedCallSwitch.postValue(null)
    }

    fun updateUserBusyStatus(callType: String, userName: String) {
        _userBusyStatus.postValue(Pair(callType, userName))
        Log.d("FcmUtils", "User busy status updated: callType=$callType, userName=$userName")
    }

    fun clearUserBusyStatus() {
        _userBusyStatus.postValue(null)
        Log.d("FcmUtils", "User busy status cleared")
    }

    fun updateLudoEvent(event: LudoEvent) {
        _ludoEvent.postValue(event)
        Log.d("FcmUtils", "Ludo event updated: type=${event.type}, invite=${event.inviteId}")
    }

    fun clearLudoEvent() {
        _ludoEvent.postValue(null)
    }


    private val _giftReceived = MutableLiveData<String?>()
    val giftReceived: LiveData<String?> get() = _giftReceived

    fun giftReceivedImage(image: String) {
        _giftReceived.postValue(image)
    }

    fun cleargiftReceived() {
        _giftReceived.postValue(null)
    }


    val greyScreenLiveData = MutableLiveData<String>()



}
