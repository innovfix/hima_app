package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.CouponRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CouponsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class CouponViewModel @Inject constructor(private val couponRepository: CouponRepository) : ViewModel() {

    val couponsLiveData = MutableLiveData<CouponsResponse>()

    fun getCoupons() {
        viewModelScope.launch {
            couponRepository.getCoupons(object : NetworkCallback<CouponsResponse> {
                override fun onResponse(
                    call: Call<CouponsResponse>,
                    response: Response<CouponsResponse>
                ) {
                    couponsLiveData.postValue(response.body())
                    Log.d("CouponResponse", "URL: ${call.request().url}")
                    Log.d("CouponResponse", "Response: ${response.body()}")
                }

                override fun onFailure(call: Call<CouponsResponse>, t: Throwable) {
                    Log.e("CouponError", "Failure: ${t.localizedMessage}")
                }

                override fun onNoNetwork() {
                    Log.e("CouponError", "No network connection")
                }
            })
        }
    }
}
