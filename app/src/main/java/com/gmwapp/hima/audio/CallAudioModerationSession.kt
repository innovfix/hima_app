package com.gmwapp.hima.audio

import android.content.Context
import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.workers.CallAudioUploadWorker
import io.agora.rtc2.RtcEngine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turns the local-mic extractor into one AAC recording of the whole call and hands it to
 * the uploader.
 *
 * Capture requires the server's capture_enabled, which already folds in the master switch,
 * app version, consent row and spend ceiling. The client is never the authority — every
 * check is repeated server-side on upload.
 *
 * Two deliberate changes from the original design, both because the recording is now the
 * product rather than a disposable input to the AI:
 *
 *  - The FULL call is written, not just VAD-trimmed speech. Trimming destroyed the timing
 *    of the conversation, so the file was useless as a record of what happened.
 *  - There is no client-side sampling. Every eligible call is recorded and stored, and the
 *    SERVER decides which recordings are analysed. Skipping on the device used to be free
 *    because the audio was deleted after analysis anyway; now a call skipped here is gone.
 */
class CallAudioModerationSession(
    context: Context,
    private val callIdProvider: () -> Int,
    private val engineProvider: () -> RtcEngine?,
) {
    private data class Config(
        val captureEnabled: Boolean,
        val sampleRate: Int,
        val maxMinutes: Int,
        val consentVersion: String,
    )

    private val appContext = context.applicationContext
    private val configExecutor = Executors.newSingleThreadExecutor()
    private val prepared = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    // Guards beginCapture against a double-start: the peer-connect thread and the
    // config-fetch thread can both try to start once the race below is resolved.
    private val capturing = AtomicBoolean(false)

    @Volatile private var config: Config? = null
    @Volatile private var extractor: AgoraLocalAudioExtractor? = null
    @Volatile private var pendingPaused = false
    // The peer connected and asked us to record. If config had not arrived yet, this
    // stays true so the config-fetch callback can start capture the moment it lands.
    @Volatile private var startRequested = false

    private val vad = EnergyVoiceActivityDetector()
    private val writeLock = Any()
    private var recorder: AacAudioRecorder? = null
    private var audioFile: File? = null
    private var pcmBytes = 0
    private var capturedMs = 0L
    // Speech is no longer what gets written — the FULL call is. It is still measured,
    // because the server stores it and the moderation pipeline uses it to size the model's
    // token budget, and because a call with no speech in it is not worth uploading.
    private var speechBytes = 0
    private var capturedCallId = 0
    private var capReached = false

    /** Fetched during ring so the first spoken word is not lost waiting on HTTP. */
    fun prepare() {
        if (disposed.get() || !prepared.compareAndSet(false, true)) return
        configExecutor.execute {
            config = fetchConfig()
            CallAudioUploadWorker.deleteExpired(appContext)
            // The race fix: if the peer already connected while this HTTP call was in
            // flight, startAfterPeerConnected() returned early on a null config and set
            // startRequested. Start now that config has landed, so a fast-answered call
            // is not silently dropped.
            if (startRequested && !disposed.get()) beginCapture()
        }
    }

    /**
     * [initiallyPaused] carries the caller's live mute state. Agora keeps delivering local
     * mic frames while muted (mute stops publishing, not capture), so starting unpaused
     * would record speech the user believes is private.
     *
     * If config has not arrived yet, capture is deferred to the prepare() callback rather
     * than abandoned — the peer connecting faster than the config HTTP round-trip must not
     * lose the call.
     */
    fun startAfterPeerConnected(initiallyPaused: Boolean) {
        if (disposed.get()) return
        startRequested = true
        pendingPaused = initiallyPaused || pendingPaused
        beginCapture()
    }

    /**
     * Idempotent — safe to call from both the peer-connect thread and the config-fetch
     * callback. The [capturing] CAS ensures only the first wins; a no-op config (disabled,
     * not sampled, blank consent) resets the flag so a later valid state could still start.
     */
    private fun beginCapture() {
        if (disposed.get() || !startRequested) return
        val cfg = config ?: return // config not here yet; prepare()'s callback will retry
        val callId = callIdProvider()
        if (!cfg.captureEnabled || cfg.consentVersion.isBlank() || callId <= 0) return
        // No client-side sampling any more. Every eligible call is recorded and stored; the
        // SERVER decides which recordings get analysed. Sampling on the device made sense
        // when audio existed only to be moderated and was deleted straight after — now the
        // recording is the product, and a call skipped here is gone for good.
        if (!capturing.compareAndSet(false, true)) return // already recording

        val engine = engineProvider()
        if (engine == null) {
            capturing.set(false)
            return
        }

        val opened = synchronized(writeLock) {
            if (recorder != null) return@synchronized true
            runCatching {
                val dir = File(appContext.cacheDir, CallAudioUploadWorker.CACHE_DIRECTORY)
                check(dir.exists() || dir.mkdirs()) { "cannot create audio cache dir" }
                val file = File(dir, "call-$callId-${System.currentTimeMillis()}.m4a")
                val enc = AacAudioRecorder(sampleRateHz = SAMPLE_RATE_HZ, bitRate = AAC_BIT_RATE)
                check(enc.start(file)) { "AAC encoder unavailable" }
                audioFile = file
                recorder = enc
                pcmBytes = 0
                speechBytes = 0
                capturedMs = 0
                capReached = false
                capturedCallId = callId
            }.onFailure { Log.w(TAG, "Unable to open audio file: ${it.message}") }.isSuccess
        }
        if (!opened) {
            capturing.set(false)
            return
        }

        val ex = AgoraLocalAudioExtractor(logTag = "CallAudioModeration") { chunk -> onChunk(chunk, cfg) }
        if (!ex.attach(engine)) {
            closeAndDiscard()
            capturing.set(false)
            return
        }
        extractor = ex
        // Honour the latest mute state; it may have moved during ring or during the
        // deferred config window.
        ex.start(initiallyPaused = pendingPaused)
        Log.d(TAG, "Recording call $capturedCallId (sample=${cfg.sampleRate}%, cap=${cfg.maxMinutes}m)")
    }

    /**
     * Mirrors the extractor's own mute handling so muted speech is never captured.
     * Remembered when called before capture starts — mute is often toggled during ring,
     * long before the peer connects.
     */
    fun setPaused(paused: Boolean) {
        pendingPaused = paused
        extractor?.setPaused(paused)
    }

    private fun onChunk(chunk: ExtractedAudioChunk, cfg: Config) {
        if (disposed.get() || capReached) return

        // The chunker overlaps windows by 1s so a word at a boundary stays whole. Appending
        // the overlap too would duplicate that second in the transcript, so skip it.
        val overlapBytes = (chunk.overlapMs.toLong() * chunk.sampleRateHz / 1000L * 2L).toInt().let { it - it % 2 }
        val from = overlapBytes.coerceIn(0, chunk.pcm16Le.size)
        val unique = chunk.pcm16Le.size - from
        if (unique <= 0) return

        // Wall-clock cap, not speech-time: "record the first N minutes" is about the call,
        // not about how much of it was talking.
        if (cfg.maxMinutes > 0) {
            val capMs = cfg.maxMinutes * 60_000L
            if (capturedMs >= capMs) {
                capReached = true
                return
            }
        }

        capturedMs += chunk.durationMs

        // The FULL call is written now, silence included. Previously only the VAD-trimmed
        // speech was kept, which made the recording useless as a record of the call: pauses
        // collapsed, timing was destroyed, and a reviewer could not tell a two-second gap
        // from a two-minute one. The VAD result is still measured — the server needs
        // speech_ms, and it decides whether the call is worth analysing at all — but it no
        // longer decides what is stored.
        // voicedRanges rather than trimToSpeech: we only need the LENGTH, and
        // trimToSpeech allocates and copies the speech itself — up to ~1 MB per 30s
        // chunk, thrown away immediately. That is avoidable GC churn during a call.
        speechBytes += vad.voicedRanges(chunk.pcm16Le, chunk.sampleRateHz, from, unique)
            .sumOf { it.last - it.first + 1 }

        // Byte ceiling against the raw PCM fed in. AAC output is ~8x smaller, so this is a
        // generous bound in practice; it exists so a pathologically long call cannot fill
        // the user's cache or produce a file the server will reject.
        val room = (MAX_PCM_BYTES - pcmBytes).let { it - (it % 2) }
        if (room <= 0) {
            capReached = true
            return
        }
        val length = minOf(unique, room)

        synchronized(writeLock) {
            val enc = recorder ?: return
            runCatching {
                enc.write(chunk.pcm16Le, from, length)
                pcmBytes += length
            }.onFailure { Log.w(TAG, "Audio write failed: ${it.message}") }
        }
        if (pcmBytes >= MAX_PCM_BYTES) capReached = true
    }

    /**
     * Finalises the M4A and queues it. Nothing is uploaded if the call produced no speech —
     * a recording of silence costs storage and tells a reviewer nothing.
     */
    fun finishCall(expectedCallId: Int) {
        // Cancel any deferred start FIRST: a fast-answered call can end before its config
        // HTTP returned, leaving capturedCallId=0. Without this, config landing after the
        // hangup would start a zombie recording of a call that is already over.
        startRequested = false
        if (expectedCallId <= 0 || expectedCallId != capturedCallId) return
        // Dispose the extractor BEFORE stopping the encoder: its dispose() blocks until the
        // final flush has actually written, so the tail of the call reaches the encoder
        // before the container is closed. Reversing these loses the last chunk, and on a
        // short call that chunk IS the call.
        extractor?.dispose()
        extractor = null

        val file: File
        val durationMs: Long
        val speechMs: Long
        val finalised: Boolean
        synchronized(writeLock) {
            val enc = recorder ?: return
            file = audioFile ?: return
            durationMs = capturedMs
            speechMs = speechBytes.toLong() * 1000L / (SAMPLE_RATE_HZ * BYTES_PER_SAMPLE)
            recorder = null
            audioFile = null
            finalised = enc.stop()
        }

        // A failed finalise means no moov atom — the MP4 is unplayable no matter its size,
        // so there is nothing worth uploading or keeping.
        if (!finalised) {
            file.delete()
            Log.w(TAG, "Call $expectedCallId produced no usable recording")
            return
        }

        if (speechMs < MIN_SPEECH_MS) {
            file.delete()
            Log.d(TAG, "Call $expectedCallId had ${speechMs}ms of speech — nothing worth uploading")
            return
        }

        val queued = CallAudioUploadWorker.enqueue(
            context = appContext,
            callId = expectedCallId,
            localUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0,
            consentVersion = config?.consentVersion.orEmpty(),
            durationMs = durationMs,
            speechMs = speechMs,
            audioPath = file.absolutePath,
        )
        if (!queued) file.delete()
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        if (capturedCallId > 0) finishCall(capturedCallId) else closeAndDiscard()
        extractor?.dispose()
        extractor = null
        configExecutor.shutdown()
    }

    private fun closeAndDiscard() {
        synchronized(writeLock) {
            runCatching { recorder?.stop() }
            recorder = null
            audioFile?.delete()
            audioFile = null
        }
    }

    private fun fetchConfig(): Config? {
        val token = BaseApplication.getInstance()?.getPrefs()?.getAuthenticationToken().orEmpty()
        if (token.isBlank()) return null
        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}call-audio-moderation/config")
            .header("Authorization", "Bearer $token")
            .header("X-Hima-Version-Code", BuildConfig.VERSION_CODE.toString())
            .get()
            .build()
        return try {
            CLIENT.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val data = JSONObject(response.body?.string().orEmpty()).optJSONObject("data") ?: return null
                Config(
                    captureEnabled = data.optBoolean("capture_enabled", false),
                    sampleRate = data.optInt("sample_rate", 0).coerceIn(0, 100),
                    maxMinutes = data.optInt("max_minutes", 0).coerceAtLeast(0),
                    consentVersion = data.optString("consent_version", ""),
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Audio moderation config unavailable: ${t.message}")
            null
        }
    }

    private companion object {
        private const val TAG = "CallAudioModeration"
        private const val SAMPLE_RATE_HZ = 16_000
        private const val BYTES_PER_SAMPLE = 2
        private const val MIN_SPEECH_MS = 1_000L

        /**
         * AAC-LC at 32 kbps mono. 4 KB/s against raw PCM's 32 KB/s, so an average 97-second
         * call goes from 3.1 MB to 388 KB leaving the user's phone. Speech at 16 kHz mono
         * survives this comfortably; the model bills per second of duration rather than per
         * byte, so the compression costs nothing in transcription accuracy or spend.
         */
        private const val AAC_BIT_RATE = 32_000

        /**
         * Ceiling on RAW PCM fed to the encoder, not on the output file. AAC at 32 kbps is
         * ~8x smaller than the PCM feeding it, so 320 MiB of PCM encodes to roughly 40 MB —
         * comfortably under the 45 MB the uploader allows and the 50M that nginx and
         * php-fpm enforce on prod. That is ~2.8 hours of call at 16 kHz mono. A runaway
         * guard, not a feature cap: whole calls are meant to be kept, and audio_max_minutes
         * remains the admin-facing control.
         */
        private const val MAX_PCM_BYTES = 320 * 1024 * 1024
        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
