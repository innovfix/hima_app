package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CreateCashfreeOrderResponse
import javax.inject.Inject

class CashfreeOrderRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun createOrder(
        userId: Int,
        coinsId: Int,
        callback: NetworkCallback<CreateCashfreeOrderResponse>
    ) {
        apiManager.createCashfreeOrder(userId, coinsId, callback)
    }
}
