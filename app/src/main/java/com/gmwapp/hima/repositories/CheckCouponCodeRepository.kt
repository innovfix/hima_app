package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CheckCouponCodeResponse
import javax.inject.Inject

class CheckCouponCodeRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun checkCouponCode(
        couponCode: String,
        callback: NetworkCallback<CheckCouponCodeResponse>
    ) {
        apiManager.checkCouponCode(couponCode, callback)
    }
}
