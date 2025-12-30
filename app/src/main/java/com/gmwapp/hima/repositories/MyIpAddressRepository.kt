package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MyIpAddressResponse
import javax.inject.Inject

class MyIpAddressRepository @Inject constructor(private val apiManager: ApiManager) {
    fun myipaddress(
        userId: Int,
        callback: NetworkCallback<MyIpAddressResponse>
    ) {
        apiManager.myipaddress(userId, callback)
    }
}

