package com.gmwapp.hima.viewmodels

import com.gmwapp.hima.utils.toUserMessage

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.AutopayRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AutopayInitiateResponse
import com.gmwapp.hima.retrofit.responses.LanguageConfigResponse
import com.gmwapp.hima.retrofit.responses.SubscriptionCancelResponse
import com.gmwapp.hima.retrofit.responses.SubscriptionStatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class AutopayViewModel @Inject constructor(
    private val repository: AutopayRepository
) : ViewModel() {

    val languageConfigLiveData = MutableLiveData<LanguageConfigResponse>()
    val initiateLiveData = MutableLiveData<AutopayInitiateResponse>()
    val statusLiveData = MutableLiveData<SubscriptionStatusResponse>()
    val cancelLiveData = MutableLiveData<SubscriptionCancelResponse>()
    val errorLiveData = MutableLiveData<String>()

    fun languageConfig(userId: Int, language: String) {
        viewModelScope.launch {
            repository.languageConfig(userId, language, object : NetworkCallback<LanguageConfigResponse> {
                override fun onResponse(call: Call<LanguageConfigResponse>, response: Response<LanguageConfigResponse>) {
                    languageConfigLiveData.postValue(response.body())
                }
                override fun onFailure(call: Call<LanguageConfigResponse>, t: Throwable) {
                    errorLiveData.postValue(t.toUserMessage())
                }
                override fun onNoNetwork() { errorLiveData.postValue("No internet connection") }
            })
        }
    }

    fun autopayInitiate(userId: Int, planType: String) {
        viewModelScope.launch {
            repository.autopayInitiate(userId, planType, object : NetworkCallback<AutopayInitiateResponse> {
                override fun onResponse(call: Call<AutopayInitiateResponse>, response: Response<AutopayInitiateResponse>) {
                    initiateLiveData.postValue(response.body())
                }
                override fun onFailure(call: Call<AutopayInitiateResponse>, t: Throwable) {
                    errorLiveData.postValue(t.toUserMessage())
                }
                override fun onNoNetwork() { errorLiveData.postValue("No internet connection") }
            })
        }
    }

    fun subscriptionStatus(userId: Int) {
        viewModelScope.launch {
            repository.subscriptionStatus(userId, object : NetworkCallback<SubscriptionStatusResponse> {
                override fun onResponse(call: Call<SubscriptionStatusResponse>, response: Response<SubscriptionStatusResponse>) {
                    statusLiveData.postValue(response.body())
                }
                override fun onFailure(call: Call<SubscriptionStatusResponse>, t: Throwable) {
                    errorLiveData.postValue(t.toUserMessage())
                }
                override fun onNoNetwork() { errorLiveData.postValue("No internet connection") }
            })
        }
    }

    fun subscriptionCancel(userId: Int, reasonId: Int?, reasonText: String?) {
        viewModelScope.launch {
            repository.subscriptionCancel(userId, reasonId, reasonText, object : NetworkCallback<SubscriptionCancelResponse> {
                override fun onResponse(call: Call<SubscriptionCancelResponse>, response: Response<SubscriptionCancelResponse>) {
                    cancelLiveData.postValue(response.body())
                }
                override fun onFailure(call: Call<SubscriptionCancelResponse>, t: Throwable) {
                    errorLiveData.postValue(t.toUserMessage())
                }
                override fun onNoNetwork() { errorLiveData.postValue("No internet connection") }
            })
        }
    }
}
