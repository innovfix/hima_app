package com.gmwapp.hima.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.gmwapp.hima.repositories.SupportBotRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.SupportBotAttachResponse
import com.gmwapp.hima.retrofit.responses.SupportBotFeedbackResponse
import com.gmwapp.hima.retrofit.responses.SupportBotReplyResponse
import com.gmwapp.hima.retrofit.responses.SupportBotSessionResponse
import com.gmwapp.hima.retrofit.responses.SupportBotStartResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

/**
 * Structural copy of AiOnboardingViewModel — same LiveData + NetworkCallback
 * shape — so this is familiar to anyone who has touched that screen.
 */
@HiltViewModel
class SupportBotViewModel @Inject constructor(
    private val repository: SupportBotRepository
) : ViewModel() {

    val startLiveData = MutableLiveData<SupportBotStartResponse>()
    val replyLiveData = MutableLiveData<SupportBotReplyResponse>()
    val sessionLiveData = MutableLiveData<SupportBotSessionResponse>()
    val feedbackLiveData = MutableLiveData<SupportBotFeedbackResponse>()
    val attachLiveData = MutableLiveData<SupportBotAttachResponse>()
    val errorLiveData = MutableLiveData<String>()
    val loadingLiveData = MutableLiveData<Boolean>()

    fun start() {
        loadingLiveData.value = true
        repository.start(object : NetworkCallback<SupportBotStartResponse> {
            override fun onNoNetwork() = fail("No internet connection")

            override fun onResponse(
                call: Call<SupportBotStartResponse>,
                response: Response<SupportBotStartResponse>
            ) {
                loadingLiveData.value = false
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    startLiveData.value = body
                } else {
                    // A failure here means the user falls back to the old
                    // raise-ticket form, so it must be loud, not swallowed.
                    errorLiveData.value = "start_failed"
                }
            }

            override fun onFailure(call: Call<SupportBotStartResponse>, t: Throwable) =
                fail(t.message ?: "Network error")
        })
    }

    fun reply(sessionId: Int, choiceKey: String? = null, userMessage: String? = null) {
        loadingLiveData.value = true
        repository.reply(sessionId, choiceKey, userMessage, object : NetworkCallback<SupportBotReplyResponse> {
            override fun onNoNetwork() = fail("No internet connection")

            override fun onResponse(
                call: Call<SupportBotReplyResponse>,
                response: Response<SupportBotReplyResponse>
            ) {
                loadingLiveData.value = false
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    replyLiveData.value = body
                } else {
                    errorLiveData.value = "reply_failed"
                }
            }

            override fun onFailure(call: Call<SupportBotReplyResponse>, t: Throwable) =
                fail(t.message ?: "Network error")
        })
    }

    /**
     * Resume (P1-G). A failure here is soft: the caller falls back to start()
     * so a stale/expired saved id never blocks the user from getting help.
     */
    fun session(sessionId: Int) {
        loadingLiveData.value = true
        repository.session(sessionId, object : NetworkCallback<SupportBotSessionResponse> {
            override fun onNoNetwork() = fail("No internet connection")

            override fun onResponse(
                call: Call<SupportBotSessionResponse>,
                response: Response<SupportBotSessionResponse>
            ) {
                loadingLiveData.value = false
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    sessionLiveData.value = body
                } else {
                    // Expired / not found / not ours — resume simply isn't
                    // available; the activity starts fresh instead.
                    errorLiveData.value = "resume_failed"
                }
            }

            override fun onFailure(call: Call<SupportBotSessionResponse>, t: Throwable) =
                fail(t.message ?: "Network error")
        })
    }

    fun feedback(sessionId: Int, solved: Boolean, csat: Int? = null) {
        loadingLiveData.value = true
        repository.feedback(sessionId, if (solved) 1 else 0, csat, object : NetworkCallback<SupportBotFeedbackResponse> {
            override fun onNoNetwork() = fail("No internet connection")

            override fun onResponse(
                call: Call<SupportBotFeedbackResponse>,
                response: Response<SupportBotFeedbackResponse>
            ) {
                loadingLiveData.value = false
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    feedbackLiveData.value = body
                } else {
                    errorLiveData.value = "feedback_failed"
                }
            }

            override fun onFailure(call: Call<SupportBotFeedbackResponse>, t: Throwable) =
                fail(t.message ?: "Network error")
        })
    }

    /** Spec #8 — optional attachment on the ticket just raised. */
    fun attach(sessionId: Int, part: okhttp3.MultipartBody.Part) {
        loadingLiveData.value = true
        repository.attach(sessionId, part, object : NetworkCallback<SupportBotAttachResponse> {
            override fun onNoNetwork() = fail("No internet connection")

            override fun onResponse(
                call: Call<SupportBotAttachResponse>,
                response: Response<SupportBotAttachResponse>
            ) {
                loadingLiveData.value = false
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    attachLiveData.value = body
                } else {
                    errorLiveData.value = "attach_failed"
                }
            }

            override fun onFailure(call: Call<SupportBotAttachResponse>, t: Throwable) =
                fail(t.message ?: "Network error")
        })
    }

    private fun fail(message: String) {
        loadingLiveData.value = false
        errorLiveData.value = message
    }
}
