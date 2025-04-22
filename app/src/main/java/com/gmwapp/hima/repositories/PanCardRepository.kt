package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import javax.inject.Inject
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.PanCardResponse


class PanCardRepository @Inject constructor(private val apiManager: ApiManager) {
    fun updatePanCard(
        userId: Int,
        pancardName: String,
        pancardNumber: String,
        callback: NetworkCallback<PanCardResponse>
    ) {
        apiManager.updatePanCard(userId, pancardName, pancardNumber, callback)
    }
}
