package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.UpdateIpRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.UpdateIpResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class UpdateIpViewModel @Inject constructor(
    private val repository: UpdateIpRepository
) : ViewModel() {

    val updateIpResponseLiveData = MutableLiveData<UpdateIpResponse>()
    val updateIpErrorLiveData = MutableLiveData<String>()

    fun updateIp() {
        viewModelScope.launch {
            repository.updateIp(object : NetworkCallback<UpdateIpResponse> {
                override fun onResponse(
                    call: Call<UpdateIpResponse>,
                    response: Response<UpdateIpResponse>
                ) {
                    updateIpResponseLiveData.postValue(response.body())
                    Log.d("UpdateIp", "IP updated: ${response.body()?.ip_address}")
                }

                override fun onFailure(call: Call<UpdateIpResponse>, t: Throwable) {
                    updateIpErrorLiveData.postValue("API Failure: ${t.message}")
                    Log.e("UpdateIp", "Failed to update IP: ${t.message}")
                }

                override fun onNoNetwork() {
                    updateIpErrorLiveData.postValue("No Network Connection")
                    Log.e("UpdateIp", "No network connection")
                }
            })
        }
    }
}

