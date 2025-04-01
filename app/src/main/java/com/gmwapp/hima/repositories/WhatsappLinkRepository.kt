package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ExplanationVideoResponse
import com.gmwapp.hima.retrofit.responses.WhatsappLinkResponse
import javax.inject.Inject

class WhatsappLinkRepository  @Inject constructor(private val apiManager: ApiManager) {

    fun getWhatsappLink(
        language: String,
        callback: NetworkCallback<WhatsappLinkResponse>
    ) {
        apiManager.getWhatsappLink(language, callback)
    }
}