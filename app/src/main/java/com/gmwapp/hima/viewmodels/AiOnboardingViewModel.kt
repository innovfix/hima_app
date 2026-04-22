package com.gmwapp.hima.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.gmwapp.hima.repositories.AiOnboardingRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AiOnboardingCompleteResponse
import com.gmwapp.hima.retrofit.responses.AiOnboardingReplyResponse
import com.gmwapp.hima.retrofit.responses.AiOnboardingStartResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class AiOnboardingViewModel @Inject constructor(
    private val repository: AiOnboardingRepository
) : ViewModel() {

    val startLiveData = MutableLiveData<AiOnboardingStartResponse>()
    val replyLiveData = MutableLiveData<AiOnboardingReplyResponse>()
    val completeLiveData = MutableLiveData<AiOnboardingCompleteResponse>()
    val errorLiveData = MutableLiveData<String>()
    val loadingLiveData = MutableLiveData<Boolean>()

    fun startOnboarding(userId: Int, concern: String) {
        loadingLiveData.value = true
        repository.startOnboarding(userId, concern, object : NetworkCallback<AiOnboardingStartResponse> {
            override fun onNoNetwork() {
                loadingLiveData.value = false
                errorLiveData.value = "No internet connection"
            }
            override fun onResponse(call: Call<AiOnboardingStartResponse>, response: Response<AiOnboardingStartResponse>) {
                loadingLiveData.value = false
                if (response.isSuccessful && response.body() != null) {
                    startLiveData.value = response.body()
                } else {
                    errorLiveData.value = "Failed to start onboarding"
                }
            }

            override fun onFailure(call: Call<AiOnboardingStartResponse>, t: Throwable) {
                loadingLiveData.value = false
                errorLiveData.value = t.message ?: "Network error"
            }
        })
    }

    fun replyOnboarding(sessionId: Int, userMessage: String) {
        loadingLiveData.value = true
        repository.replyOnboarding(sessionId, userMessage, object : NetworkCallback<AiOnboardingReplyResponse> {
            override fun onNoNetwork() {
                loadingLiveData.value = false
                errorLiveData.value = "No internet connection"
            }
            override fun onResponse(call: Call<AiOnboardingReplyResponse>, response: Response<AiOnboardingReplyResponse>) {
                loadingLiveData.value = false
                if (response.isSuccessful && response.body() != null) {
                    replyLiveData.value = response.body()
                } else {
                    errorLiveData.value = "Failed to get AI reply"
                }
            }

            override fun onFailure(call: Call<AiOnboardingReplyResponse>, t: Throwable) {
                loadingLiveData.value = false
                errorLiveData.value = t.message ?: "Network error"
            }
        })
    }

    fun completeOnboarding(sessionId: Int) {
        loadingLiveData.value = true
        repository.completeOnboarding(sessionId, object : NetworkCallback<AiOnboardingCompleteResponse> {
            override fun onNoNetwork() {
                loadingLiveData.value = false
                errorLiveData.value = "No internet connection"
            }
            override fun onResponse(call: Call<AiOnboardingCompleteResponse>, response: Response<AiOnboardingCompleteResponse>) {
                loadingLiveData.value = false
                if (response.isSuccessful && response.body() != null) {
                    completeLiveData.value = response.body()
                } else {
                    errorLiveData.value = "Failed to complete onboarding"
                }
            }

            override fun onFailure(call: Call<AiOnboardingCompleteResponse>, t: Throwable) {
                loadingLiveData.value = false
                errorLiveData.value = t.message ?: "Network error"
            }
        })
    }
}
