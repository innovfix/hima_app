package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.TransactionChargesResponse
import javax.inject.Inject

class TransactionChargesRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun getTransactionCharges(callback: NetworkCallback<TransactionChargesResponse>) {
        apiManager.getTransactionCharges(callback)
    }
}