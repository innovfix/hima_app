package com.gmwapp.hima.agora

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object FcmUtils {
    private val _callStatus = MutableLiveData<Pair<String, String>?>()  // Make it nullable
    val callStatus: LiveData<Pair<String, String>?> get() = _callStatus

    private val _callDeclinedStatus = MutableLiveData<Boolean>()
    val callDeclinedStatus: LiveData<Boolean> get() = _callDeclinedStatus


    var isUserAvailable = 1

    private val _updatedTime = MutableLiveData<String?>()
    val updatedTime: LiveData<String?> get() = _updatedTime

    private val _updatedCallSwitch = MutableLiveData<Pair<String, Int>?>()
    val updatedCallSwitch: LiveData<Pair<String, Int>?> get() = _updatedCallSwitch

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





    }
