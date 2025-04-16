package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.FcmNotificationRepository
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response



@HiltViewModel
class FcmNotificationViewModel @Inject constructor(
    private val repository: FcmNotificationRepository
) : ViewModel() {

    val notificationResponseLiveData = MutableLiveData<FcmNotificationResponse>()
    val notificationErrorLiveData = MutableLiveData<String>()


    fun sendNotification(
        senderId: Int,
        receiverId: Int,
        callType: String,
        channelName: String,
        message: String
    ) {
        viewModelScope.launch {
        repository.sendFcmNotification(senderId, receiverId, callType, channelName, message, object :
            NetworkCallback<FcmNotificationResponse> {
            override fun onResponse(call: Call<FcmNotificationResponse>, response: Response<FcmNotificationResponse>) {
                notificationResponseLiveData.postValue(response.body())
                Log.d("FCMNotification", "Response: ${response.body()?.message}")
            }

            override fun onFailure(call: Call<FcmNotificationResponse>, t: Throwable) {
                notificationErrorLiveData.postValue("API Failure: ${t.message}")
                Log.e("FCMNotification", "Error: ${t.message}")
            }

            override fun onNoNetwork() {
                notificationErrorLiveData.postValue("No Network Connection")
            }
        })
    }

    }


}
