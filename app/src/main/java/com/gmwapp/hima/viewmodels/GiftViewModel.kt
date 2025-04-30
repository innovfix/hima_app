package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.GiftRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.SendGiftResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class GiftViewModel @Inject constructor(
    private val repository: GiftRepository
) : ViewModel() {

    val giftResponseLiveData = MutableLiveData<SendGiftResponse>()
    val giftErrorLiveData = MutableLiveData<String>()

    fun sendGift(userId: Int, receiverId: Int, giftId: Int) {
        viewModelScope.launch {
            repository.sendGift(userId, receiverId, giftId, object :
                NetworkCallback<SendGiftResponse> {
                override fun onResponse(
                    call: Call<SendGiftResponse>,
                    response: Response<SendGiftResponse>
                ) {
                    giftResponseLiveData.postValue(response.body())
                    Log.d("giftSent","${response.body()}")
                }

                override fun onFailure(call: Call<SendGiftResponse>, t: Throwable) {
                    giftErrorLiveData.postValue("API Failure: ${t.message}")
                }

                override fun onNoNetwork() {
                    giftErrorLiveData.postValue("No Network Connection")
                }
            })
        }
    }
}
