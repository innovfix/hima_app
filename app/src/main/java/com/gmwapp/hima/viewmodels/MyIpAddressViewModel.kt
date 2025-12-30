package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.MyIpAddressRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MyIpAddressResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class MyIpAddressViewModel @Inject constructor(
    private val repository: MyIpAddressRepository
) : ViewModel() {

    val myIpAddressResponseLiveData = MutableLiveData<MyIpAddressResponse>()
    val myIpAddressErrorLiveData = MutableLiveData<String>()

    fun myipaddress(userId: Int) {
        viewModelScope.launch {
            repository.myipaddress(userId, object : NetworkCallback<MyIpAddressResponse> {
                override fun onResponse(
                    call: Call<MyIpAddressResponse>,
                    response: Response<MyIpAddressResponse>
                ) {
                    myIpAddressResponseLiveData.postValue(response.body())
                    Log.d("MyIpAddress", "IP updated: ${response.body()?.ip_address}")
                }

                override fun onFailure(call: Call<MyIpAddressResponse>, t: Throwable) {
                    myIpAddressErrorLiveData.postValue("API Failure: ${t.message}")
                    Log.e("MyIpAddress", "Failed to update IP: ${t.message}")
                }

                override fun onNoNetwork() {
                    myIpAddressErrorLiveData.postValue("No Network Connection")
                    Log.e("MyIpAddress", "No network connection")
                }
            })
        }
    }
}

