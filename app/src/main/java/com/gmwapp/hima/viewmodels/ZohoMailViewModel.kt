package com.gmwapp.hima.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.ZohoMailRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ZohoMailItem
import com.gmwapp.hima.retrofit.responses.ZohoMailResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class ZohoMailViewModel @Inject constructor(
    private val repository: ZohoMailRepository
) : ViewModel() {

    val zohoMailLiveData = MutableLiveData<List<ZohoMailItem>>()
    val zohoMailErrorLiveData = MutableLiveData<String>()

    fun fetchZohoMail(
        language: String,
        onResult: (String?, String?, String?, String?) -> Unit
    ) {
        viewModelScope.launch {
            repository.getZohoMailList(language, object : NetworkCallback<ZohoMailResponse> {
                override fun onResponse(
                    call: Call<ZohoMailResponse>,
                    response: Response<ZohoMailResponse>
                ) {
                    val firstItem = response.body()?.data?.firstOrNull()
                    val email = firstItem?.mail
                    val department = firstItem?.department
                    val appKey = firstItem?.appkey
                    val accessKey = firstItem?.accesskey
                    onResult(email, department, appKey, accessKey)
                }

                override fun onFailure(call: Call<ZohoMailResponse>, t: Throwable) {
                    onResult(null, null, null, null)
                }

                override fun onNoNetwork() {
                    onResult(null, null, null, null)
                }
            })
        }
    }

}
