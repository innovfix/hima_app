package com.gmwapp.hima.utils

import com.gmwapp.hima.repositories.SupportBotRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
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
        /** Network/transport/non-2xx failure — the screen should offer a retry. */
        fun onReplyError()
    }

    private var pendingSessionId: Int = 0
    private var loading: Boolean = false
    private var storedResult: SupportBotReplyResponse? = null
    private var storedError: Boolean = false
    private var listener: Listener? = null

    /**
     * True while a reply for [sessionId] is in-flight OR its result/error is waiting
     * to be consumed (it landed while no screen was attached). The returning screen
     * checks this to decide "let the keeper finish it" vs "do a normal resume".
     */
    fun isBusyFor(sessionId: Int): Boolean =
        sessionId > 0 && pendingSessionId == sessionId &&
            (loading || storedResult != null || storedError)

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
        listener?.onReplyLoading(true)
        repository.reply(sessionId, choiceKey, userMessage, object : NetworkCallback<SupportBotReplyResponse> {
            override fun onNoNetwork() = deliverError()

            override fun onResponse(
                call: Call<SupportBotReplyResponse>,
                response: Response<SupportBotReplyResponse>
            ) {
                val body = response.body()
                if (response.isSuccessful && body != null) deliver(body) else deliverError()
            }

            override fun onFailure(call: Call<SupportBotReplyResponse>, t: Throwable) = deliverError()
        })
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

    private fun deliverError() {
        loading = false
        val l = listener
        if (l != null) {
            l.onReplyLoading(false)
            l.onReplyError()
            storedError = false
        } else {
            storedError = true
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
        if (storedError) {
            storedError = false
            l.onReplyLoading(false)
            l.onReplyError()
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
        storedError = false
    }
}
