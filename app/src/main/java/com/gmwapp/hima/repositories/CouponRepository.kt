package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CouponsResponse
import javax.inject.Inject

class CouponRepository @Inject constructor(private val apiManager: ApiManager) {
    fun getCoupons(callback: NetworkCallback<CouponsResponse>) {
        apiManager.getCoupons(callback)
    }
}
