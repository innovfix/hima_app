package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CouponsResponse
import com.gmwapp.hima.retrofit.responses.DefaultCouponResponse
import javax.inject.Inject

class CouponRepository @Inject constructor(private val apiManager: ApiManager) {
    fun getCoupons(coinID: String?, userId: Int, callback: NetworkCallback<CouponsResponse>) {
        apiManager.getCoupons(coinID, userId, callback)
    }

    fun getDefaultCoupon(userId: Int, coinsId: String, callback: NetworkCallback<DefaultCouponResponse>) {
        apiManager.getDefaultCoupon(userId, coinsId, callback)
    }
}
