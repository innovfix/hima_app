package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.DailyClaimResponse
import com.gmwapp.hima.retrofit.responses.DailyClaimStatusResponse
import javax.inject.Inject

class DailyClaimRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun dailyClaimStatus(userId: Int, cb: NetworkCallback<DailyClaimStatusResponse>) =
        apiManager.dailyClaimStatus(userId, cb)

    fun dailyClaim(userId: Int, cb: NetworkCallback<DailyClaimResponse>) =
        apiManager.dailyClaim(userId, cb)
}
