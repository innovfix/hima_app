package com.gmwapp.hima.viewmodels

import com.gmwapp.hima.utils.toUserMessage

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.DailyClaimRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.DailyClaimResponse
import com.gmwapp.hima.retrofit.responses.DailyClaimStatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class DailyClaimViewModel @Inject constructor(
    private val repository: DailyClaimRepository
) : ViewModel() {

    val statusLiveData = MutableLiveData<DailyClaimStatusResponse>()
    val claimLiveData = MutableLiveData<DailyClaimResponse>()
    val errorLiveData = MutableLiveData<String>()

    fun dailyClaimStatus(userId: Int) {
        viewModelScope.launch {
            repository.dailyClaimStatus(userId, object : NetworkCallback<DailyClaimStatusResponse> {
                override fun onResponse(call: Call<DailyClaimStatusResponse>, response: Response<DailyClaimStatusResponse>) {
                    statusLiveData.postValue(response.body())
                }
                override fun onFailure(call: Call<DailyClaimStatusResponse>, t: Throwable) {
                    errorLiveData.postValue(t.toUserMessage())
                }
                override fun onNoNetwork() { errorLiveData.postValue("No internet connection") }
            })
        }
    }

    fun dailyClaim(userId: Int) {
        viewModelScope.launch {
            repository.dailyClaim(userId, object : NetworkCallback<DailyClaimResponse> {
                override fun onResponse(call: Call<DailyClaimResponse>, response: Response<DailyClaimResponse>) {
                    claimLiveData.postValue(response.body())
                }
                override fun onFailure(call: Call<DailyClaimResponse>, t: Throwable) {
                    errorLiveData.postValue(t.toUserMessage())
                }
                override fun onNoNetwork() { errorLiveData.postValue("No internet connection") }
            })
        }
    }
}
