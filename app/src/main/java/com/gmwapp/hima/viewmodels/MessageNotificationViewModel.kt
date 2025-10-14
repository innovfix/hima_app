package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.MessageNotificationRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MessageNotificationResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class MessageNotificationViewModel @Inject constructor(
    private val repository: MessageNotificationRepository
) : ViewModel() {

    val notificationResponseLiveData = MutableLiveData<MessageNotificationResponse>()
    val notificationErrorLiveData = MutableLiveData<String>()

    fun sendMessageNotification(
        senderId: Int,
        receiverId: Int,
        message: String
    ) {
        viewModelScope.launch {
            repository.sendMessageNotification(senderId, receiverId, message, object :
                NetworkCallback<MessageNotificationResponse> {
                override fun onResponse(call: Call<MessageNotificationResponse>, response: Response<MessageNotificationResponse>) {
                    notificationResponseLiveData.postValue(response.body())
                    Log.d("MessageNotification", "Response: ${response.body()?.message}")
                }

                override fun onFailure(call: Call<MessageNotificationResponse>, t: Throwable) {
                    notificationErrorLiveData.postValue("API Failure: ${t.message}")
                    Log.e("MessageNotification", "Error: ${t.message}")
                }

                override fun onNoNetwork() {
                    notificationErrorLiveData.postValue("No Network Connection")
                }
            })
        }
    }
}

