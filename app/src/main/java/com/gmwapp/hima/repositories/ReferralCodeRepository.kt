package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ReferralCodeResponse
import javax.inject.Inject



class ReferralCodeRepository @Inject constructor(private val apiManager: ApiManager) {
    fun checkReferCode(
        number: String,
        refer_code: String,
        callback: NetworkCallback<ReferralCodeResponse>
    ) {
        apiManager.checkReferCode(number, refer_code, callback)
    }
}