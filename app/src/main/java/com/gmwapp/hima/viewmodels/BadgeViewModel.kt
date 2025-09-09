package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.BadgeRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.BadgeResponse

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class BadgeViewModel @Inject constructor(private val badgeRepository: BadgeRepository) : ViewModel() {

    val badgeLiveData = MutableLiveData<BadgeResponse>()

    fun getBadgesInformation() {
        viewModelScope.launch {
            badgeRepository.getBadgesInformation(object: NetworkCallback<BadgeResponse> {
                override fun onResponse(
                    call: Call<BadgeResponse>,
                    response: Response<BadgeResponse>
                ) {
                    badgeLiveData.postValue(response.body())
                    Log.d("BadgeResponse", "URL: ${call.request().url}")
                    Log.d("BadgeResponse", "Body: ${response.body()}")
                }

                override fun onFailure(call: Call<BadgeResponse>, t: Throwable) {
                    Log.e("BadgeResponse", "Error: ${t.message}")
                }

                override fun onNoNetwork() {
                    Log.e("BadgeResponse", "No network")
                }
            })
        }
    }
}
