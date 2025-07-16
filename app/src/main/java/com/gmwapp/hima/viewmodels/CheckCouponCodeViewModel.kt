package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.CheckCouponCodeRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CheckCouponCodeResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class CheckCouponCodeViewModel @Inject constructor(
    private val repository: CheckCouponCodeRepository
) : ViewModel() {

    val couponCodeLiveData = MutableLiveData<CheckCouponCodeResponse>()
    val couponCodeErrorLiveData = MutableLiveData<String>()

    fun checkCouponCode(couponCode: String) {
        viewModelScope.launch {
            repository.checkCouponCode(couponCode, object : NetworkCallback<CheckCouponCodeResponse> {
                override fun onResponse(
                    call: Call<CheckCouponCodeResponse>,
                    response: Response<CheckCouponCodeResponse>
                ) {
                    couponCodeLiveData.postValue(response.body())
                    Log.d("CheckCouponCode", "Response: ${response.body()}")
                }

                override fun onFailure(call: Call<CheckCouponCodeResponse>, t: Throwable) {
                    couponCodeErrorLiveData.postValue("Error: ${t.message}")
                    Log.d("CheckCouponCode", "Failure: $t")
                }

                override fun onNoNetwork() {
                    couponCodeErrorLiveData.postValue("No internet connection")
                }
            })
        }
    }
}
