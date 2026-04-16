package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AiOnboardingCompleteResponse
import com.gmwapp.hima.retrofit.responses.AiOnboardingReplyResponse
import com.gmwapp.hima.retrofit.responses.AiOnboardingStartResponse
import javax.inject.Inject

class AiOnboardingRepository @Inject constructor(private val apiManager: ApiManager) {

    fun startOnboarding(userId: Int, concern: String, callback: NetworkCallback<AiOnboardingStartResponse>) {
        apiManager.aiOnboardingStart(userId, concern, callback)
    }

    fun replyOnboarding(sessionId: Int, userMessage: String, callback: NetworkCallback<AiOnboardingReplyResponse>) {
        apiManager.aiOnboardingReply(sessionId, userMessage, callback)
    }

    fun completeOnboarding(sessionId: Int, callback: NetworkCallback<AiOnboardingCompleteResponse>) {
        apiManager.aiOnboardingComplete(sessionId, callback)
    }
}
