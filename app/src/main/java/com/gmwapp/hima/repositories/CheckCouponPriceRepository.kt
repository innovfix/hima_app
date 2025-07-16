package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CouponPriceResponse
import javax.inject.Inject

class CheckCouponPriceRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun checkCouponPrice(
        coinsId: String,
        couponsId: String,
        callback: NetworkCallback<CouponPriceResponse>
    ) {
        apiManager.checkCouponPrice(coinsId, couponsId, callback)
    }
}
