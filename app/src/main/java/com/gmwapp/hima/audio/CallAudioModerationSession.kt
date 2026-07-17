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
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32

/**
 * Turns the local-mic extractor into one VAD-trimmed WAV per call and hands it to the
 * uploader.
 *
 * Capture requires ALL of: the server's capture_enabled (which already folds in the master
 * switch, app version, consent row and spend ceiling) AND this call being in the sample.
 * The client is never the authority — every check is repeated server-side on upload — but
 * doing them here means unsampled audio is never recorded, never written to disk and never
 * sent, which is what makes the sample rate a real ceiling on cost rather than a filter
 * applied after the money is spent.
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
    private var writer: RandomAccessFile? = null
    private var wavFile: File? = null
    private var pcmBytes = 0
    private var capturedMs = 0L
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
        if (!isCallSampled(callId, cfg.sampleRate)) {
            Log.d(TAG, "Call $callId not in the ${cfg.sampleRate}% sample — not recording")
            return
        }
        if (!capturing.compareAndSet(false, true)) return // already recording

        val engine = engineProvider()
        if (engine == null) {
            capturing.set(false)
            return
        }

        val opened = synchronized(writeLock) {
            if (writer != null) return@synchronized true
            runCatching {
                val dir = File(appContext.cacheDir, CallAudioUploadWorker.CACHE_DIRECTORY)
                check(dir.exists() || dir.mkdirs()) { "cannot create audio cache dir" }
                val file = File(dir, "call-$callId-${System.currentTimeMillis()}.wav")
                val raf = RandomAccessFile(file, "rw")
                raf.write(ByteArray(Pcm16WavEncoder.HEADER_BYTES))
                wavFile = file
                writer = raf
                pcmBytes = 0
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

        val speech = vad.trimToSpeech(chunk.pcm16Le, chunk.sampleRateHz, from, unique)
        capturedMs += chunk.durationMs
        if (speech.isEmpty()) return

        synchronized(writeLock) {
            val raf = writer ?: return
            runCatching {
                raf.write(speech)
                pcmBytes += speech.size
            }.onFailure { Log.w(TAG, "Audio write failed: ${it.message}") }
        }
    }

    /**
     * Closes the WAV and queues it. Nothing is uploaded if the call produced no speech —
     * an empty track costs money to transcribe and tells a reviewer nothing.
     */
    fun finishCall(expectedCallId: Int) {
        // Cancel any deferred start FIRST: a fast-answered call can end before its config
        // HTTP returned, leaving capturedCallId=0. Without this, config landing after the
        // hangup would start a zombie recording of a call that is already over.
        startRequested = false
        if (expectedCallId <= 0 || expectedCallId != capturedCallId) return
        extractor?.dispose()
        extractor = null

        val file: File
        val bytes: Int
        val durationMs: Long
        synchronized(writeLock) {
            val raf = writer ?: return
            file = wavFile ?: return
            bytes = pcmBytes
            durationMs = capturedMs
            writer = null
            wavFile = null
            runCatching {
                raf.seek(0)
                raf.write(Pcm16WavEncoder.monoHeader(bytes, SAMPLE_RATE_HZ))
                raf.fd.sync()
                raf.close()
            }.onFailure { Log.w(TAG, "Unable to finalise WAV: ${it.message}") }
        }

        val speechMs = bytes.toLong() * 1000L / (SAMPLE_RATE_HZ * 2L)
        if (bytes <= 0 || speechMs < MIN_SPEECH_MS) {
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
            runCatching { writer?.close() }
            writer = null
            wavFile?.delete()
            wavFile = null
        }
    }

    /**
     * Must agree with CallAudioModerationGate::isCallSampled byte-for-byte, or the client
     * uploads audio the server rejects (wasted bandwidth) or skips calls the server expects.
     * Verified 2026-07-17: crc32("hima-audio-42") == 3185532901 in both PHP and Java.
     * Hashed on callId ALONE so both participants' devices independently agree and a sampled
     * call yields both microphones or neither.
     */
    private fun isCallSampled(callId: Int, rate: Int): Boolean {
        if (rate <= 0) return false
        if (rate >= 100) return true
        val crc = CRC32().apply { update("hima-audio-$callId".toByteArray(Charsets.US_ASCII)) }.value
        return (crc % 100) < rate
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
        private const val MIN_SPEECH_MS = 1_000L
        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
