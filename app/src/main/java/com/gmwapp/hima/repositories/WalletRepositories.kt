package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AddCoinsResponse
import com.gmwapp.hima.retrofit.responses.CoinsResponse
import javax.inject.Inject

class WalletRepositories @Inject constructor(private val apiManager: ApiManager) {
  fun getCoins(userId: Int, callback: NetworkCallback<CoinsResponse>) {
        apiManager.getCoins(userId, callback)
    }

    fun addCoins(
        userId: Int,
        coinId: String,
        status: Int,
        orderId: String,
        massage: String,
        callback: NetworkCallback<AddCoinsResponse>
    ) {
        apiManager.addCoins(userId, coinId, status, orderId, massage, callback)
    }

    fun tryCoins(
        userId: Int,
        coinId: Int,
        status: Int,
        orderId: Int,
        massage: String,
        callback: NetworkCallback<AddCoinsResponse>
    ) {
        apiManager.tryCoins(userId, coinId, status, orderId, massage, callback)
    }

    fun addCoinsGpay(
        userId: Int,
        coinId: String,
        status: Int,
        orderId: String,
        massage: String,
        callback: NetworkCallback<AddCoinsResponse>
    ) {
        apiManager.addCoinsGpay(userId, coinId, status, orderId, massage, callback)
    }

 }