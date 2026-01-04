package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.AgoraRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AgoraTokenResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class AgoraViewModel @Inject constructor(
    private val agoraRepository: AgoraRepository
) : ViewModel() {

    val agoraTokenLiveData = MutableLiveData<AgoraTokenResponse>()
    val agoraTokenErrorLiveData = MutableLiveData<String>()

    fun getAgoraToken(
        channelName: String,
        uid: Int,
        role: String = "publisher",
        expire: Int = 3600
    ) {
        viewModelScope.launch {
            agoraRepository.getAgoraToken(
                channelName,
                uid,
                role,
                expire,
                object : NetworkCallback<AgoraTokenResponse> {
                    override fun onResponse(
                        call: Call<AgoraTokenResponse>,
                        response: Response<AgoraTokenResponse>
                    ) {
                        if (response.isSuccessful && response.body() != null) {
                            agoraTokenLiveData.postValue(response.body())
                            Log.d("AgoraToken", "Token received: ${response.body()?.token?.take(50)}...")
                            Log.d("AgoraToken", "Token received: ${call.request().url}")
                        } else {
                            agoraTokenErrorLiveData.postValue("Failed to get token: ${response.code()}")
                            Log.e("AgoraToken", "Failed response: ${response.code()}")
                        }
                    }

                    override fun onFailure(call: Call<AgoraTokenResponse>, t: Throwable) {
                        agoraTokenErrorLiveData.postValue("Network error: ${t.message}")
                        Log.e("AgoraToken", "Error getting token: ${t.message}")
                    }

                    override fun onNoNetwork() {
                        agoraTokenErrorLiveData.postValue(DConstants.NO_NETWORK)
                        Log.e("AgoraToken", "No network connection")
                    }
                }
            )
        }
    }
}
