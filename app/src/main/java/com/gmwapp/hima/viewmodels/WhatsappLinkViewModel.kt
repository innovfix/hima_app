package com.gmwapp.hima.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.ExplanationVideoRepository
import com.gmwapp.hima.repositories.WhatsappLinkRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ExplanationVideoResponse
import com.gmwapp.hima.retrofit.responses.WhatsappLinkResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class WhatsappLinkViewModel @Inject constructor(
    private val repository: WhatsappLinkRepository
) : ViewModel() {
    val whatsappResponseLiveData = MutableLiveData<WhatsappLinkResponse>()
    val whatsappErrorLiveData = MutableLiveData<String>()


    fun fetchLink(language: String) {
        viewModelScope.launch {
            repository.getWhatsappLink(language, object :
                NetworkCallback<WhatsappLinkResponse> {
                override fun onResponse(
                    call: Call<WhatsappLinkResponse>,
                    response: Response<WhatsappLinkResponse>
                ) {
                    whatsappResponseLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<WhatsappLinkResponse>, t: Throwable) {
                    whatsappErrorLiveData.postValue("API Failure: ${t.message}")
                }

                override fun onNoNetwork() {
                    whatsappErrorLiveData.postValue("No Network Connection")
                }
            })
        }
    }
}