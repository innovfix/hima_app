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
                Log.d("FCMNotification", "✅ Response received - Status: ${response.code()}")
                Log.d("FCMNotification", "📝 Response body: ${response.body()}")
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Log.d("FCMNotification", "✅ Notification sent successfully: ${body.message}")
                        notificationResponseLiveData.postValue(body)
                    } else {
                        Log.e("FCMNotification", "⚠️ Response body is null")
                        notificationErrorLiveData.postValue("Response is empty")
                    }
                } else {
                    Log.e("FCMNotification", "❌ Response not successful - Code: ${response.code()}")
                    // Try to read error body as string
                    val errorBody = response.errorBody()?.string()
                    Log.e("FCMNotification", "📝 Error body: $errorBody")
                    notificationErrorLiveData.postValue("Error: ${response.code()} - $errorBody")
                }
            }

            override fun onFailure(call: Call<FcmNotificationResponse>, t: Throwable) {
                Log.e("FCMNotification", "❌ API Call Failed")
                Log.e("FCMNotification", "Error type: ${t.javaClass.simpleName}")
                Log.e("FCMNotification", "Error message: ${t.message}")
                Log.e("FCMNotification", "Error cause: ${t.cause}")
                
                // Log detailed stack trace
                t.printStackTrace()
                
                // Check if it's a JSON parsing error
                if (t is com.google.gson.JsonSyntaxException || 
                    t.message?.contains("Expected BEGIN_OBJECT") == true) {
                    Log.e("FCMNotification", "🚨 JSON PARSING ERROR - API returned non-JSON response")
                    notificationErrorLiveData.postValue("Server returned invalid response format")
                } else {
                    notificationErrorLiveData.postValue("API Failure: ${t.message}")
                }
            }

            override fun onNoNetwork() {
                Log.e("FCMNotification", "⚠️ No Network Connection")
                notificationErrorLiveData.postValue("No Network Connection")
            }
        })
    }

    }


}
