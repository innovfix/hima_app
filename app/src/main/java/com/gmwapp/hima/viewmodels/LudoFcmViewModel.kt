package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.LudoFcmRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.LudoFcmResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class LudoFcmViewModel @Inject constructor(
    private val repository: LudoFcmRepository
) : ViewModel() {

    val ludoFcmResponseLiveData = MutableLiveData<LudoFcmResponse>()
    val ludoFcmErrorLiveData = MutableLiveData<String>()

    fun sendLudoFcm(
        action: String,
        fromUserId: Int? = null,
        toUserId: Int? = null,
        inviteId: String? = null,
        callId: String? = null
    ) {
        viewModelScope.launch {
            repository.sendLudoFcm(
                action = action,
                fromUserId = fromUserId,
                toUserId = toUserId,
                inviteId = inviteId,
                callId = callId,
                callback = object : NetworkCallback<LudoFcmResponse> {
                    override fun onResponse(
                        call: Call<LudoFcmResponse>,
                        response: Response<LudoFcmResponse>
                    ) {

                        Log.d("LudoResponse","${response.body()}")
                        Log.d("LudoResponse","${call.request().url}")

                        if (response.isSuccessful && response.body() != null) {
                            ludoFcmResponseLiveData.postValue(response.body())
                        } else {
                            ludoFcmErrorLiveData.postValue(
                                response.errorBody()?.string() ?: "Failed: ${response.code()}"
                            )
                        }
                    }

                    override fun onFailure(call: Call<LudoFcmResponse>, t: Throwable) {
                        ludoFcmErrorLiveData.postValue(t.message ?: DConstants.LOGIN_ERROR)
                    }

                    override fun onNoNetwork() {
                        ludoFcmErrorLiveData.postValue(DConstants.NO_NETWORK)
                    }
                }
            )
        }
    }
}
