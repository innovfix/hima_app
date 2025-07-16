package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.CheckCouponPriceRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CouponPriceResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class CheckCouponPriceViewModel @Inject constructor(
    private val repository: CheckCouponPriceRepository
) : ViewModel() {

    val couponPriceResponseLiveData = MutableLiveData<CouponPriceResponse>()
    val couponPriceErrorLiveData = MutableLiveData<String>()

    fun checkCouponPrice(coinsId: String, couponsId: String) {
        viewModelScope.launch {
            repository.checkCouponPrice(coinsId, couponsId, object : NetworkCallback<CouponPriceResponse> {
                override fun onResponse(
                    call: Call<CouponPriceResponse>,
                    response: Response<CouponPriceResponse>
                ) {
                    couponPriceResponseLiveData.postValue(response.body())
                    Log.d("CheckCoupon", "Response: ${response.body()}")
                }

                override fun onFailure(call: Call<CouponPriceResponse>, t: Throwable) {
                    couponPriceErrorLiveData.postValue("Network error")
                    Log.d("CheckCoupon", "Failure: $t")
                }

                override fun onNoNetwork() {
                    couponPriceErrorLiveData.postValue("No internet connection")
                }
            })
        }
    }
}
