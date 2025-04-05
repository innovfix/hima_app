package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.TransactionsResponse
import javax.inject.Inject

class TransactionsRepositories @Inject constructor(private val apiManager: ApiManager) {
  fun getTransactions(userId: Int, offset: Int, limit: Int,callback: NetworkCallback<TransactionsResponse>) {
        apiManager.getTransactions(userId,offset,limit, callback)
    }
}