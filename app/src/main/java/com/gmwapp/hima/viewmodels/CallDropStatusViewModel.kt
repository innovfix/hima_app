package com.gmwapp.hima.viewmodels

import com.gmwapp.hima.utils.toUserMessage

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.CallDropStatusRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallDropStatusRequest
import com.gmwapp.hima.retrofit.responses.CallDropStatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class CallDropStatusViewModel @Inject constructor(
    private val repository: CallDropStatusRepository
) : ViewModel() {

    val callDropStatusLiveData = MutableLiveData<CallDropStatusResponse>()
    val callDropStatusErrorLiveData = MutableLiveData<String>()

    fun saveCallDropStatus(
        userId: Int,
        receivedUserId: Int,
        callId: Int,
        callDropStatus: Int,
    ) {
        viewModelScope.launch {
            val request = CallDropStatusRequest(
                user_id = userId,
                received_user_id = receivedUserId,
                call_id = callId,
                call_drop_status = callDropStatus
            )

            repository.callDropStatus(request, object : NetworkCallback<CallDropStatusResponse> {
                override fun onResponse(
                    call: Call<CallDropStatusResponse>,
                    response: Response<CallDropStatusResponse>
                ) {
                    if (response.isSuccessful) {
                        callDropStatusLiveData.postValue(response.body())
                        Log.d("CallDropStatusAPI", "Saved: ${call.request().url} body=${response.body()}")
                    } else {
                        val msg = response.errorBody()?.string() ?: "Failed: ${response.code()}"
                        callDropStatusErrorLiveData.postValue(msg)
                        Log.e("CallDropStatusAPI", "Error ${response.code()} url=${call.request().url} body=$msg")
                    }
                }

                override fun onFailure(call: Call<CallDropStatusResponse>, t: Throwable) {
                    callDropStatusErrorLiveData.postValue(t.toUserMessage(DConstants.LOGIN_ERROR))
                    Log.e("CallDropStatusAPI", "Failure url=${call.request().url}", t)
                }

                override fun onNoNetwork() {
                    callDropStatusErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}

