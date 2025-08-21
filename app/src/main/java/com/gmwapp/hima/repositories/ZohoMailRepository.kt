package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ZohoMailResponse
import javax.inject.Inject

class ZohoMailRepository @Inject constructor(private val apiManager: ApiManager) {
    fun getZohoMailList(language: String, callback: NetworkCallback<ZohoMailResponse>) {
        apiManager.getZohoMailList(language, callback)
    }
}
