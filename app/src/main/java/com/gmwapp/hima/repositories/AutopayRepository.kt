package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AutopayInitiateResponse
import com.gmwapp.hima.retrofit.responses.LanguageConfigResponse
import com.gmwapp.hima.retrofit.responses.SubscriptionCancelResponse
import com.gmwapp.hima.retrofit.responses.SubscriptionStatusResponse
import javax.inject.Inject

class AutopayRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun languageConfig(userId: Int, language: String, cb: NetworkCallback<LanguageConfigResponse>) =
        apiManager.languageConfig(userId, language, cb)

    fun autopayInitiate(userId: Int, planType: String, cb: NetworkCallback<AutopayInitiateResponse>) =
        apiManager.autopayInitiate(userId, planType, cb)

    fun subscriptionStatus(userId: Int, cb: NetworkCallback<SubscriptionStatusResponse>) =
        apiManager.subscriptionStatus(userId, cb)

    fun subscriptionCancel(
        userId: Int, reasonId: Int?, reasonText: String?,
        cb: NetworkCallback<SubscriptionCancelResponse>
    ) = apiManager.subscriptionCancel(userId, reasonId, reasonText, cb)
}
