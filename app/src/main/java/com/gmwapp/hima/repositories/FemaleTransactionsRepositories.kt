package com.gmwapp.hima.repositories

import android.util.Log
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FemaleTransactionsResponse
import javax.inject.Inject

class FemaleTransactionsRepositories @Inject constructor(private val apiManager: ApiManager) {
    
    companion object {
        private const val TAG = "femaleTrasactionLog"
    }
    
    fun getFemaleTransactions(userId: Int, offset: Int, limit: Int, type: String?, callback: NetworkCallback<FemaleTransactionsResponse>) {
        Log.d(TAG, "getFemaleTransactions: Repository called with userId=$userId, offset=$offset, limit=$limit, type=$type")
        Log.d(TAG, "getFemaleTransactions: Calling ApiManager.getFemaleTransactions()")
        try {
            apiManager.getFemaleTransactions(userId, offset, limit, type, callback)
            Log.d(TAG, "getFemaleTransactions: ApiManager call initiated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "getFemaleTransactions: Exception in repository", e)
            Log.e(TAG, "getFemaleTransactions: Exception message = ${e.message}")
            e.printStackTrace()
        }
    }
}

