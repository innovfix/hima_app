package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.SupportBotAttachResponse
import com.gmwapp.hima.retrofit.responses.SupportBotFeedbackResponse
import com.gmwapp.hima.retrofit.responses.SupportBotReplyResponse
import com.gmwapp.hima.retrofit.responses.SupportBotSessionResponse
import com.gmwapp.hima.retrofit.responses.SupportBotStartResponse
import javax.inject.Inject

class SupportBotRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun start(callback: NetworkCallback<SupportBotStartResponse>) =
        apiManager.supportBotStart(callback)

    fun reply(
        sessionId: Int,
        choiceKey: String?,
        userMessage: String?,
        callback: NetworkCallback<SupportBotReplyResponse>
    ) = apiManager.supportBotReply(sessionId, choiceKey, userMessage, callback)

    fun session(
        sessionId: Int,
        callback: NetworkCallback<SupportBotSessionResponse>
    ) = apiManager.supportBotSession(sessionId, callback)

    fun feedback(
        sessionId: Int,
        solved: Int,
        csat: Int?,
        callback: NetworkCallback<SupportBotFeedbackResponse>
    ) = apiManager.supportBotFeedback(sessionId, solved, csat, callback)

    fun attach(
        sessionId: Int,
        part: okhttp3.MultipartBody.Part,
        callback: NetworkCallback<SupportBotAttachResponse>
    ): retrofit2.Call<SupportBotAttachResponse>? =
        apiManager.supportBotAttach(sessionId, part, callback)
}
