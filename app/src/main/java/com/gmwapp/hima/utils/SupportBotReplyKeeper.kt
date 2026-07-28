package com.gmwapp.hima.utils

import com.gmwapp.hima.repositories.SupportBotRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.SupportBotFeedbackResponse
import com.gmwapp.hima.retrofit.responses.SupportBotReplyResponse
import retrofit2.Call
import retrofit2.Response

/**
 * App-scoped keeper for the ONE in-flight support-bot reply.
 *
 * SupportBotActivity is destroyed on back and recreated on return. Previously the
 * reply request lived in the activity-scoped ViewModel, so leaving mid-reply
 * abandoned the answer (the server kept the session "in progress" and the user
 * came back to a long typing-dots stare / reload).
 *
 * By owning the reply call here — outside any activity/ViewModel — the request is
 * NEVER abandoned:
 *  - user still on screen  -> the attached listener gets the result live.
 *  - user left mid-reply   -> the result is STORED and handed to the next screen
 *                             the moment it re-attaches (onStart). No re-fetch, no
 *                             orphan, no waiting on the server's stale timeout.
 *
 * Main-thread only (Retrofit callbacks dispatch on the main thread, and the
 * activity/ViewModel touch this only from the main thread), so no locking needed.
 * Holds at most one response + one listener; the listener MUST be detached in
 * onStop so a destroyed activity can't leak.
 */
object SupportBotReplyKeeper {

    interface Listener {
        fun onReplyLoading(loading: Boolean)
        fun onReplyResult(result: SupportBotReplyResponse)
        /**
         * BUG 18 — a FEEDBACK result ("Yes" / "Still need help"). Kept here for the
         * same reason as a reply: leaving the screen mid-feedback used to drop the
         * answer permanently, because feedback ran on activity-scoped LiveData while
         * only reply() had a keeper. The server had already advanced the session, so
         * the returning screen repainted a stale cached input_mode=none and the user
         * landed on a dead screen with no composer and no AI message.
         */
        fun onFeedbackResult(result: SupportBotFeedbackResponse)
        /**
         * A reply failed. [retryAfterSeconds] > 0 means the server rate-limited us
         * (HTTP 429): the screen should show a short back-off and gate the retry for
         * that long, instead of an instant "Try again" that just fires another
         * request into the same throttled window. 0 = generic network/transport/
         * non-2xx failure -> a normal retry is fine.
         */
        fun onReplyError(retryAfterSeconds: Int)
    }

    private var pendingSessionId: Int = 0
    private var loading: Boolean = false
    private var storedResult: SupportBotReplyResponse? = null
    private var storedFeedback: SupportBotFeedbackResponse? = null
    private var storedError: Boolean = false
    /** Seconds to wait before retrying, carried with a stored 429 error. */
    private var storedRetryAfter: Int = 0
    private var listener: Listener? = null

    /**
     * True while a reply for [sessionId] is in-flight OR its result/error is waiting
     * to be consumed (it landed while no screen was attached). The returning screen
     * checks this to decide "let the keeper finish it" vs "do a normal resume".
     */
    fun isBusyFor(sessionId: Int): Boolean =
        sessionId > 0 && pendingSessionId == sessionId &&
            (loading || storedResult != null || storedFeedback != null || storedError)

    /** Fire a reply. Owns the call so it outlives the calling activity. */
    fun send(
        repository: SupportBotRepository,
        sessionId: Int,
        choiceKey: String?,
        userMessage: String?
    ) {
        pendingSessionId = sessionId
        loading = true
        storedResult = null
        storedError = false
        storedRetryAfter = 0
        listener?.onReplyLoading(true)
        repository.reply(sessionId, choiceKey, userMessage, object : NetworkCallback<SupportBotReplyResponse> {
            override fun onNoNetwork() = deliverError(0)

            override fun onResponse(
                call: Call<SupportBotReplyResponse>,
                response: Response<SupportBotReplyResponse>
            ) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    deliver(body)
                } else {
                    // Tell a 429 rate-limit (back off) apart from a generic failure.
                    // Laravel sends Retry-After in whole seconds; clamp + default.
                    val retryAfter = if (response.code() == 429)
                        (response.headers()["Retry-After"]?.toIntOrNull()?.coerceIn(1, 60) ?: 5)
                    else 0
                    deliverError(retryAfter)
                }
            }

            override fun onFailure(call: Call<SupportBotReplyResponse>, t: Throwable) = deliverError(0)
        })
    }

    /**
     * BUG 18 — fire a feedback tap ("Yes" / "Still need help"). Mirrors [send] exactly:
     * the call is owned here, so backing out mid-feedback no longer abandons the answer.
     */
    fun sendFeedback(
        repository: SupportBotRepository,
        sessionId: Int,
        solved: Int,
        csat: Int?
    ) {
        pendingSessionId = sessionId
        loading = true
        storedResult = null
        storedFeedback = null
        storedError = false
        storedRetryAfter = 0
        listener?.onReplyLoading(true)
        repository.feedback(sessionId, solved, csat, object : NetworkCallback<SupportBotFeedbackResponse> {
            override fun onNoNetwork() = deliverError(0)

            override fun onResponse(
                call: Call<SupportBotFeedbackResponse>,
                response: Response<SupportBotFeedbackResponse>
            ) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    deliverFeedback(body)
                } else {
                    // The 2nd-attempt feedback re-runs the model, so it shares the
                    // rate-limit bucket — same 429 back-off contract as a reply.
                    val retryAfter = if (response.code() == 429)
                        (response.headers()["Retry-After"]?.toIntOrNull()?.coerceIn(1, 60) ?: 5)
                    else 0
                    deliverError(retryAfter)
                }
            }

            override fun onFailure(call: Call<SupportBotFeedbackResponse>, t: Throwable) = deliverError(0)
        })
    }

    private fun deliverFeedback(r: SupportBotFeedbackResponse) {
        loading = false
        val l = listener
        if (l != null) {
            l.onReplyLoading(false)
            l.onFeedbackResult(r)
            storedFeedback = null
        } else {
            storedFeedback = r
        }
    }

    private fun deliver(r: SupportBotReplyResponse) {
        loading = false
        val l = listener
        if (l != null) {
            l.onReplyLoading(false)
            l.onReplyResult(r)
            storedResult = null
        } else {
            // No screen attached — hold it for the next attach().
            storedResult = r
        }
    }

    private fun deliverError(retryAfterSeconds: Int) {
        loading = false
        val l = listener
        if (l != null) {
            l.onReplyLoading(false)
            l.onReplyError(retryAfterSeconds)
            storedError = false
        } else {
            storedError = true
            storedRetryAfter = retryAfterSeconds
        }
    }

    /** The visible screen registers here; replays anything that landed while away
     *  for THIS session (a mismatched session leaves the stored result untouched). */
    fun attach(sessionId: Int, l: Listener) {
        listener = l
        if (pendingSessionId != sessionId) return
        if (loading) {
            l.onReplyLoading(true)
            return
        }
        storedResult?.let { r ->
            storedResult = null
            l.onReplyLoading(false)
            l.onReplyResult(r)
            return
        }
        storedFeedback?.let { r ->
            storedFeedback = null
            l.onReplyLoading(false)
            l.onFeedbackResult(r)
            return
        }
        if (storedError) {
            storedError = false
            l.onReplyLoading(false)
            l.onReplyError(storedRetryAfter)
        }
    }

    fun detach(l: Listener) {
        if (listener === l) listener = null
    }

    /** Called when the session ends/clears so a finished reply is never replayed. */
    fun clear() {
        pendingSessionId = 0
        loading = false
        storedResult = null
        storedFeedback = null
        storedError = false
        storedRetryAfter = 0
    }
}
