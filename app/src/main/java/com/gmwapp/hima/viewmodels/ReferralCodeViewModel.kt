package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.FcmTokenRepository
import com.gmwapp.hima.repositories.ReferralCodeRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FcmTokenResponse
import com.gmwapp.hima.retrofit.responses.ReferralCodeResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject



@HiltViewModel
class ReferralCodeViewModel @Inject constructor(
    private val repository: ReferralCodeRepository
) : ViewModel() {

    val referCodeResponseLiveData = MutableLiveData<ReferralCodeResponse>()
    val referCodeErrorLiveData = MutableLiveData<String>()

    fun checkReferCode(number: String, refer_code: String) {
        viewModelScope.launch {
            repository.checkReferCode(number, refer_code, object :
                NetworkCallback<ReferralCodeResponse> {
                override fun onResponse(
                    call: Call<ReferralCodeResponse>,
                    response: Response<ReferralCodeResponse>
                ) {
                    referCodeResponseLiveData.postValue(response.body())
                    Log.d("referCodeResponseLiveData","${response.body()?.message}")
                }

                override fun onFailure(call: Call<ReferralCodeResponse>, t: Throwable) {
                    referCodeErrorLiveData.postValue("API Failure: ${t.message}")
                    Log.d("referCodeErrorLiveData","${t.message}")

                }

                override fun onNoNetwork() {
                    referCodeErrorLiveData.postValue("No Network Connection")
                }
            })
        }
    }
}
