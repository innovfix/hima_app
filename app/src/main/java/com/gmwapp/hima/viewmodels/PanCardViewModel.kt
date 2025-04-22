package com.gmwapp.hima.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.PanCardRepository
import com.gmwapp.hima.retrofit.responses.PanCardResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import retrofit2.Call
import retrofit2.Response


@HiltViewModel
class PanCardViewModel @Inject constructor(private val repository: PanCardRepository) : ViewModel() {

    val panUpdateLiveData = MutableLiveData<PanCardResponse>()
    val panUpdateErrorLiveData = MutableLiveData<String>()

    fun updatePanCard(userId: Int, panName: String, panNumber: String) {
        viewModelScope.launch {
            repository.updatePanCard(userId, panName, panNumber, object : NetworkCallback<PanCardResponse> {
                override fun onResponse(call: Call<PanCardResponse>, response: Response<PanCardResponse>) {
                    panUpdateLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<PanCardResponse>, t: Throwable) {
                    panUpdateErrorLiveData.postValue(t.message)
                }

                override fun onNoNetwork() {
                    panUpdateErrorLiveData.postValue("No network connection")
                }
            })
        }
    }
}
