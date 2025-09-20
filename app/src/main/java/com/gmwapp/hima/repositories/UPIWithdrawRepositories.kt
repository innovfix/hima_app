package com.gmwapp.hima.repositories
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.UpiWithdrawResponse
import javax.inject.Inject

class UPIWithdrawRepositories@Inject constructor(private val apiManager: ApiManager) {

    fun addWithdrawal(
        userId: Int,
        amount: Int,
        paymentMethod: String,
        callback: NetworkCallback<UpiWithdrawResponse>
    ) {
        apiManager.addUpiWithdrawal(userId, amount, paymentMethod, callback)
    }
}