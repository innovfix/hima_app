package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.GiftImageRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GiftImageResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class GiftImageViewModel @Inject constructor(
    private val repository: GiftImageRepository
) : ViewModel() {

    val giftResponseLiveData = MutableLiveData<GiftImageResponse>()
    val giftErrorLiveData = MutableLiveData<String>()

    fun fetchGiftImages() {
        viewModelScope.launch {
            repository.getGiftImages(object : NetworkCallback<GiftImageResponse> {
                override fun onResponse(
                    call: Call<GiftImageResponse>,
                    response: Response<GiftImageResponse>
                ) {
                    Log.d("GiftIconChecking", "Gift Icon URL: ${response.body()}")

                    giftResponseLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<GiftImageResponse>, t: Throwable) {
                    giftErrorLiveData.postValue("API Failure: ${t.message}")
                }

                override fun onNoNetwork() {
                    giftErrorLiveData.postValue("No Network Connection")
                }
            })
        }
    }
}
