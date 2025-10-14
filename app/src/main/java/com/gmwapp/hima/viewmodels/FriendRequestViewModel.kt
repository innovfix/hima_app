package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.FriendRequestRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FriendRequestResponse
import com.gmwapp.hima.retrofit.responses.MyFriendRequestsResponse
import com.gmwapp.hima.retrofit.responses.ReceivedFriendRequestsResponse
import com.gmwapp.hima.retrofit.responses.FriendListResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class FriendRequestViewModel @Inject constructor(
    private val repository: FriendRequestRepository
) : ViewModel() {

    val sendFriendRequestLiveData = MutableLiveData<FriendRequestResponse>()
    val myFriendRequestsLiveData = MutableLiveData<MyFriendRequestsResponse>()
    val receivedFriendRequestsLiveData = MutableLiveData<ReceivedFriendRequestsResponse>()
    val friendsListLiveData = MutableLiveData<FriendListResponse>()
    val friendRequestErrorLiveData = MutableLiveData<String>()
    val loadingLiveData = MutableLiveData<Boolean>()

    fun sendFriendRequest(
        senderId: Int,
        receiverId: Int,
        status: Int = 0
    ) {
        loadingLiveData.postValue(true)
        viewModelScope.launch {
            repository.sendFriendRequest(senderId, receiverId, status, object :
                NetworkCallback<FriendRequestResponse> {
                override fun onResponse(
                    call: Call<FriendRequestResponse>,
                    response: Response<FriendRequestResponse>
                ) {
                    loadingLiveData.postValue(false)
                    if (response.body() != null) {
                        val responseBody = response.body()!!
                        if (responseBody.success) {
                            // Success case - friend request sent
                            sendFriendRequestLiveData.postValue(responseBody)
                            Log.d("FriendRequestVM", "✅ Friend request sent: ${responseBody.message}")
                        } else {
                            // API returned success: false - show the error message
                            friendRequestErrorLiveData.postValue(responseBody.message)
                            Log.e("FriendRequestVM", "❌ API Error: ${responseBody.message}")
                        }
                    } else {
                        friendRequestErrorLiveData.postValue("Failed to send friend request")
                        Log.e("FriendRequestVM", "❌ Error: ${response.errorBody()?.string()}")
                        Log.e("FriendRequestVMBody", "❌ Error: ${response.body()}")
                    }
                }

                override fun onFailure(call: Call<FriendRequestResponse>, t: Throwable) {
                    loadingLiveData.postValue(false)
                    friendRequestErrorLiveData.postValue("Network error: ${t.message}")
                    Log.e("FriendRequestVM", "❌ API Failure: ${t.message}")
                }

                override fun onNoNetwork() {
                    loadingLiveData.postValue(false)
                    friendRequestErrorLiveData.postValue("No internet connection")
                    Log.e("FriendRequestVM", "❌ No Network Connection")
                }
            })
        }
    }

    fun getMyFriendRequests(senderId: Int) {
        loadingLiveData.postValue(true)
        viewModelScope.launch {
            repository.getMyFriendRequests(senderId, object :
                NetworkCallback<MyFriendRequestsResponse> {
                override fun onResponse(
                    call: Call<MyFriendRequestsResponse>,
                    response: Response<MyFriendRequestsResponse>
                ) {
                    loadingLiveData.postValue(false)
                    if (response.body() != null) {
                        val responseBody = response.body()!!
                        if (responseBody.success) {
                            myFriendRequestsLiveData.postValue(responseBody)
                            Log.d("FriendRequestVM", "✅ My requests loaded: ${responseBody.count} requests")
                        } else {
                            friendRequestErrorLiveData.postValue(responseBody.message)
                            Log.e("FriendRequestVM", "❌ API Error: ${responseBody.message}")
                        }
                    } else {
                        friendRequestErrorLiveData.postValue("Failed to load friend requests")
                        Log.e("FriendRequestVM", "❌ Error: ${response.errorBody()?.string()}")
                    }
                }

                override fun onFailure(call: Call<MyFriendRequestsResponse>, t: Throwable) {
                    loadingLiveData.postValue(false)
                    friendRequestErrorLiveData.postValue("Network error: ${t.message}")
                    Log.e("FriendRequestVM", "❌ API Failure: ${t.message}")
                }

                override fun onNoNetwork() {
                    loadingLiveData.postValue(false)
                    friendRequestErrorLiveData.postValue("No internet connection")
                    Log.e("FriendRequestVM", "❌ No Network Connection")
                }
            })
        }
    }

    fun getReceivedFriendRequests(receiverId: Int) {
        loadingLiveData.postValue(true)
        viewModelScope.launch {
            repository.getReceivedFriendRequests(receiverId, object :
                NetworkCallback<ReceivedFriendRequestsResponse> {
                override fun onResponse(
                    call: Call<ReceivedFriendRequestsResponse>,
                    response: Response<ReceivedFriendRequestsResponse>
                ) {
                    loadingLiveData.postValue(false)
                    if (response.body() != null) {
                        val responseBody = response.body()!!
                        if (responseBody.success) {
                            receivedFriendRequestsLiveData.postValue(responseBody)
                            Log.d("FriendRequestVM", "✅ Received requests loaded: ${responseBody.count} requests")
                        } else {
                            friendRequestErrorLiveData.postValue(responseBody.message)
                            Log.e("FriendRequestVM", "❌ API Error: ${responseBody.message}")
                        }
                    } else {
                        friendRequestErrorLiveData.postValue("Failed to load received requests")
                        Log.e("FriendRequestVM", "❌ Error: ${response.errorBody()?.string()}")
                    }
                }

                override fun onFailure(call: Call<ReceivedFriendRequestsResponse>, t: Throwable) {
                    loadingLiveData.postValue(false)
                    friendRequestErrorLiveData.postValue("Network error: ${t.message}")
                    Log.e("FriendRequestVM", "❌ API Failure: ${t.message}")
                }

                override fun onNoNetwork() {
                    loadingLiveData.postValue(false)
                    friendRequestErrorLiveData.postValue("No internet connection")
                    Log.e("FriendRequestVM", "❌ No Network Connection")
                }
            })
        }
    }

    fun getFriendsList(senderId: Int) {
        loadingLiveData.postValue(true)
        viewModelScope.launch {
            repository.getFriendsList(senderId, object :
                NetworkCallback<FriendListResponse> {
                override fun onResponse(
                    call: Call<FriendListResponse>,
                    response: Response<FriendListResponse>
                ) {
                    loadingLiveData.postValue(false)
                    if (response.body() != null) {
                        val responseBody = response.body()!!
                        if (responseBody.success) {
                            friendsListLiveData.postValue(responseBody)
                            Log.d("FriendRequestVM", "✅ Friends list loaded: ${responseBody.count} friends")
                        } else {
                            friendRequestErrorLiveData.postValue(responseBody.message)
                            Log.e("FriendRequestVM", "❌ API Error: ${responseBody.message}")
                        }
                    } else {
                        friendRequestErrorLiveData.postValue("Failed to load friends list")
                        Log.e("FriendRequestVM", "❌ Error: ${response.errorBody()?.string()}")
                    }
                }

                override fun onFailure(call: Call<FriendListResponse>, t: Throwable) {
                    loadingLiveData.postValue(false)
                    friendRequestErrorLiveData.postValue("Network error: ${t.message}")
                    Log.e("FriendRequestVM", "❌ API Failure: ${t.message}")
                }

                override fun onNoNetwork() {
                    loadingLiveData.postValue(false)
                    friendRequestErrorLiveData.postValue("No internet connection")
                    Log.e("FriendRequestVM", "❌ No Network Connection")
                }
            })
        }
    }
}

