package com.gmwapp.hima.viewmodels

import com.gmwapp.hima.utils.toUserMessage

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.ExplanationVideoRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ExplanationVideoResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class ExplanationVideoViewModel @Inject constructor(
    private val repository: ExplanationVideoRepository
) : ViewModel() {

    val videoResponseLiveData = MutableLiveData<ExplanationVideoResponse>()
    val videoErrorLiveData = MutableLiveData<String>()

    fun fetchVideos(language: String) {
        viewModelScope.launch {
            repository.getExplanationVideos(language, object :
                NetworkCallback<ExplanationVideoResponse> {
                override fun onResponse(
                    call: Call<ExplanationVideoResponse>,
                    response: Response<ExplanationVideoResponse>
                ) {
                    videoResponseLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<ExplanationVideoResponse>, t: Throwable) {
                    videoErrorLiveData.postValue(t.toUserMessage())
                }

                override fun onNoNetwork() {
                    videoErrorLiveData.postValue("No Network Connection")
                }
            })
        }
    }
}
