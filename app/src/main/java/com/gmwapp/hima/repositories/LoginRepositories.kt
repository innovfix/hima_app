package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AppUpdateResponse
import com.gmwapp.hima.retrofit.responses.LoginResponse
import com.gmwapp.hima.retrofit.responses.SendOTPResponse
import javax.inject.Inject

class LoginRepositories @Inject constructor(private val apiManager: ApiManager) {
    fun login(mobile: String,code: String,code_verifier: String, callback: NetworkCallback<LoginResponse>) {
        apiManager.login(mobile,code, code_verifier, callback)
    }


    fun appupdate(callback: NetworkCallback<AppUpdateResponse>) {
        apiManager.appUpdate(callback)
    }





    fun sendOTP(
        mobile: String,
        countryCode: Int,
        otp: Int,
        callback: NetworkCallback<SendOTPResponse>
    ) {
        apiManager.sendOTP(mobile, countryCode, otp, callback)
    }
}