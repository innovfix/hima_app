package com.gmwapp.hima.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.SupportBotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
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

    // ─────────────────────────── attachments (Spec #8) ───────────────────────
    //
    // The whole attachment lifecycle lives HERE, not in the Activity, on
    // purpose. The upload (a singleton OkHttp call) and this ViewModel both
    // survive a rotation while the Activity does not — so if the Activity owned
    // the temp file, its onDestroy would delete the file mid-upload on every
    // rotation. The ViewModel owns the file and deletes it only when the upload
    // actually resolves (or at real teardown, onCleared), never mid-flight.

    /** Validation / failure codes for the UI: too_big | bad_type | failed | no_network | in_flight. */
    val attachErrorLiveData = MutableLiveData<String>()
    val attachInFlight = MutableLiveData<Boolean>()

    /** The single temp file currently on disk, owned by the ViewModel. */
    private var attachTempFile: java.io.File? = null

    /** The in-flight upload, retained so we can cancel it before deleting the file. */
    private var attachCall: Call<SupportBotAttachResponse>? = null

    fun uploadAttachment(appContext: android.content.Context, uri: android.net.Uri, sessionId: Int, maxBytes: Long) {
        if (attachInFlight.value == true) {
            attachErrorLiveData.value = "in_flight"
            return
        }
        val resolver = appContext.contentResolver

        // Size FIRST, when the provider reports it — a fast reject.
        val size = runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (i >= 0 && c.moveToFirst()) c.getLong(i) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
        if (size > maxBytes) { attachErrorLiveData.value = "too_big"; return }

        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (!(mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/"))) {
            attachErrorLiveData.value = "bad_type"; return
        }

        attachInFlight.value = true
        viewModelScope.launch {
            // Create the target file and record it NOW — on the main thread,
            // BEFORE any IO. onCleared() (also main-thread) can then always find
            // and delete it, regardless of when the copy finishes or is
            // cancelled, so a completed-after-teardown file can never be
            // orphaned. attachTempFile is touched ONLY on the main thread (here,
            // finishAttach, onCleared), so there is no cross-thread field access.
            // copyInto writes into this file and never touches the field.
            val target = java.io.File(appContext.cacheDir, "support_" + System.nanoTime())
            attachTempFile = target
            val ok = withContext(Dispatchers.IO) { copyInto(resolver, uri, target, maxBytes) }
            if (!ok) { finishAttach(); attachErrorLiveData.value = "too_big"; return@launch }
            val part = MultipartBody.Part.createFormData(
                "file", target.name, target.asRequestBody(mime.toMediaTypeOrNull())
            )
            attachCall = repository.attach(sessionId, part, object : NetworkCallback<SupportBotAttachResponse> {
                override fun onNoNetwork() { finishAttach(); attachErrorLiveData.value = "no_network" }

                override fun onResponse(
                    call: Call<SupportBotAttachResponse>,
                    response: Response<SupportBotAttachResponse>
                ) {
                    val body = response.body()
                    // Deliver the body BEFORE cleanup so the observer sees it.
                    if (response.isSuccessful && body != null) attachLiveData.value = body
                    else attachErrorLiveData.value = "failed"
                    finishAttach()
                }

                // Network/upload failure ALSO resets the in-flight flag — the
                // bug was that this path left it stuck true forever, blocking
                // every later attachment.
                override fun onFailure(call: Call<SupportBotAttachResponse>, t: Throwable) {
                    attachErrorLiveData.value = "failed"; finishAttach()
                }
            })
        }
    }

    /**
     * Delete the temp file and free the slot, on any resolution. Cancels the
     * Call FIRST so OkHttp is not mid-read of a file we are about to delete
     * (harmless on the success/failure paths where it is already done, load-
     * bearing when called from onCleared while an upload is still running).
     */
    private fun finishAttach() {
        attachCall?.cancel(); attachCall = null
        attachTempFile?.delete(); attachTempFile = null
        attachInFlight.value = false
    }

    /**
     * Copy the picked file into [target] (created and tracked by the caller on
     * the main thread), bounded by [maxBytes]. Returns true on success. On
     * cancellation or failure it deletes the partial and, for cancellation,
     * rethrows. It never touches attachTempFile — the caller owns that field on
     * the main thread — so there is no cross-thread state and no race between
     * completion and recording.
     */
    private suspend fun copyInto(
        resolver: android.content.ContentResolver,
        uri: android.net.Uri,
        target: java.io.File,
        maxBytes: Long
    ): Boolean {
        return try {
            resolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(8 * 1024)
                    var total = 0L
                    while (true) {
                        // JVM blocking IO is not interrupted by coroutine
                        // cancellation, so check each loop; on cancel this throws
                        // and the catch deletes the partial before propagating.
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw java.io.IOException("attachment exceeds limit")
                        output.write(buf, 0, n)
                    }
                }
            }
            true
        } catch (c: kotlinx.coroutines.CancellationException) {
            target.delete(); throw c
        } catch (t: Throwable) {
            target.delete(); false
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Teardown for real (last Activity finished, never rotation). Cancel any
        // in-flight upload BEFORE deleting its file, so OkHttp is not left
        // reading a file we removed. viewModelScope is cancelled by the base
        // class, which stops a copy in progress; its bounded copy deletes the
        // partial on cancellation, so nothing is left behind.
        attachCall?.cancel(); attachCall = null
        attachTempFile?.delete(); attachTempFile = null
    }

    private fun fail(message: String) {
        loadingLiveData.value = false
        errorLiveData.value = message
    }
}
