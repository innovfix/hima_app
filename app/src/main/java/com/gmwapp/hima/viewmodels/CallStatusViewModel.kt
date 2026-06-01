package com.gmwapp.hima.viewmodels

import com.gmwapp.hima.utils.toUserMessage

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.CallStatusRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallStatusRequest
import com.gmwapp.hima.retrofit.responses.CallStatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class CallStatusViewModel @Inject constructor(
    private val repository: CallStatusRepository
) : ViewModel() {

    val callStatusLiveData = MutableLiveData<CallStatusResponse>()
    val callStatusErrorLiveData = MutableLiveData<String>()

    fun saveCallStatus(
        userId: Int,
        receivedUserId: Int,
        callId: Int,
        endReason: String,
        endedBy: String,
        endedByUserId: Int? = null,
        durationSeconds: Int? = null,
    ) {
        if (callId <= 0) {
            Log.w(TAG, "Skipping call_status — invalid callId=$callId reason=$endReason by=$endedBy")
            return
        }
        viewModelScope.launch {
            val request = CallStatusRequest(
                userId = userId,
                receivedUserId = receivedUserId,
                callId = callId,
                endReason = endReason,
                endedBy = endedBy,
                endedByUserId = endedByUserId,
                durationSeconds = durationSeconds,
            )

            repository.callStatus(request, object : NetworkCallback<CallStatusResponse> {
                override fun onResponse(
                    call: Call<CallStatusResponse>,
                    response: Response<CallStatusResponse>
                ) {
                    if (response.isSuccessful) {
                        callStatusLiveData.postValue(response.body())
                        Log.d(TAG, "Saved url=${call.request().url} body=${response.body()}")
                    } else {
                        val msg = response.errorBody()?.string() ?: "Failed: ${response.code()}"
                        callStatusErrorLiveData.postValue(msg)
                        Log.e(TAG, "Error ${response.code()} url=${call.request().url} body=$msg")
                    }
                }

                override fun onFailure(call: Call<CallStatusResponse>, t: Throwable) {
                    callStatusErrorLiveData.postValue(t.toUserMessage(DConstants.LOGIN_ERROR))
                    Log.e(TAG, "Failure url=${call.request().url}", t)
                }

                override fun onNoNetwork() {
                    callStatusErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }

    companion object {
        private const val TAG = "CallStatus"
    }
}
