package com.gmwapp.hima.audio

import android.util.Log
import android.os.SystemClock
import io.agora.rtc2.Constants
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.audio.AudioParams
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Extracts only this device's local Agora microphone track.
 *
 * The observer copies a small PCM frame and immediately returns; chunking, VAD and callbacks run
 * on a serial worker. No file, database, network, transcription or moderation action exists here.
 */
class AgoraLocalAudioExtractor(
    private val logTag: String,
    private val onChunk: (ExtractedAudioChunk) -> Unit,
) {
    private val stateLock = Any()
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HimaAudioExtractor-$logTag").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val chunker = OverlappingPcmChunker(sampleRateHz = SAMPLE_RATE_HZ)
    private val vad = EnergyVoiceActivityDetector()
    private var engine: RtcEngine? = null
    private var active = false
    private var paused = false
    private var disposed = false
    private var pauseStartedAtMs = 0L

    // Worker-thread-only output coordinates. They stay monotonic even when a mute closes and
    // resets the underlying overlap window.
    private var nextSequence = 0L
    private var timelineCursorMs = 0L

    fun attach(engine: RtcEngine): Boolean {
        synchronized(stateLock) {
            if (disposed) return false
            if (this.engine === engine) return true
            this.engine?.registerAudioFrameObserver(null)
            val result = engine.registerAudioFrameObserver(observer)
            if (result != 0) {
                Log.w(logTag, "Local audio observer registration failed: code=$result")
                return false
            }
            this.engine = engine
            return true
        }
    }

    /** Idempotent for Agora reconnect callbacks. */
    fun start(initiallyPaused: Boolean) {
        synchronized(stateLock) {
            if (disposed || active) return
            active = true
            paused = initiallyPaused
            pauseStartedAtMs = if (initiallyPaused) SystemClock.elapsedRealtime() else 0L
            worker.execute {
                chunker.reset()
                nextSequence = 0L
                timelineCursorMs = 0L
            }
        }
    }

    /**
     * Pausing closes the current pre-mute window so muted/private speech can never share a later
     * chunk. Unmuting starts a clean transcription boundary.
     */
    fun setPaused(shouldPause: Boolean) {
        synchronized(stateLock) {
            if (disposed || paused == shouldPause) return
            paused = shouldPause
            val now = SystemClock.elapsedRealtime()
            val mutedGapMs = if (!shouldPause && pauseStartedAtMs > 0L) {
                (now - pauseStartedAtMs).coerceAtLeast(0L)
            } else 0L
            pauseStartedAtMs = if (shouldPause) now else 0L
            worker.execute {
                if (shouldPause) {
                    emit(chunker.flush())
                } else {
                    timelineCursorMs += mutedGapMs
                }
            }
        }
    }

    fun dispose() {
        val engineToDetach: RtcEngine?
        synchronized(stateLock) {
            if (disposed) return
            disposed = true
            active = false
            paused = true
            engineToDetach = engine
            engine = null
        }
        // Never hold stateLock while asking Agora to unregister. Some SDK versions wait for an
        // in-flight callback to return; that callback also needs stateLock, so holding it here
        // could deadlock teardown.
        try {
            engineToDetach?.registerAudioFrameObserver(null)
        } catch (t: Throwable) {
            Log.w(logTag, "Local audio observer detach failed safely: ${t.message}")
        }
        synchronized(stateLock) {
            worker.execute { emit(chunker.flush()) }
            worker.shutdown()
        }
    }

    private fun acceptRecordedFrame(
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        source: ByteBuffer?,
    ) {
        if (source == null || samplesPerChannel <= 0) return
        val expectedBytes = samplesPerChannel * bytesPerSample * channels
        if (bytesPerSample != BYTES_PER_SAMPLE || channels != CHANNELS ||
            samplesPerSec != SAMPLE_RATE_HZ || expectedBytes <= 0
        ) return

        synchronized(stateLock) {
            if (disposed || !active || paused) return
            val duplicate = source.duplicate()
            if (duplicate.remaining() < expectedBytes) {
                duplicate.rewind()
                if (duplicate.remaining() < expectedBytes) return
            }
            val pcm = ByteArray(expectedBytes)
            duplicate.get(pcm)
            worker.execute {
                chunker.append(pcm).forEach(::emit)
            }
        }
    }

    private fun emit(window: OverlappingPcmChunker.Window?) {
        if (window == null) return
        val uniqueLength = window.pcm16Le.size - window.overlapBytes
        if (uniqueLength <= 0) return
        val voiceActivity = vad.analyse(
            pcm16Le = window.pcm16Le,
            sampleRateHz = SAMPLE_RATE_HZ,
            offsetBytes = window.overlapBytes,
            lengthBytes = uniqueLength,
        )
        val durationMs = window.pcm16Le.size / BYTES_PER_SAMPLE * 1000L / SAMPLE_RATE_HZ
        val startMs = (timelineCursorMs - window.overlapMs).coerceAtLeast(0L)
        val endMs = startMs + durationMs
        timelineCursorMs += durationMs - window.overlapMs
        try {
            onChunk(
                ExtractedAudioChunk(
                    sequence = nextSequence++,
                    startMs = startMs,
                    endMs = endMs,
                    overlapMs = window.overlapMs,
                    sampleRateHz = SAMPLE_RATE_HZ,
                    pcm16Le = window.pcm16Le,
                    voiceActivity = voiceActivity,
                )
            )
        } catch (t: Throwable) {
            Log.w(logTag, "Audio chunk consumer failed safely: ${t.message}")
        }
    }

    private val observer = object : IAudioFrameObserver {
        override fun onRecordAudioFrame(
            channelId: String?, type: Int, samplesPerChannel: Int, bytesPerSample: Int,
            channels: Int, samplesPerSec: Int, buffer: ByteBuffer?, renderTimeMs: Long,
            avsync_type: Int,
        ): Boolean {
            acceptRecordedFrame(
                samplesPerChannel, bytesPerSample, channels, samplesPerSec, buffer
            )
            return true
        }

        override fun onPlaybackAudioFrame(
            channelId: String?, type: Int, samplesPerChannel: Int, bytesPerSample: Int,
            channels: Int, samplesPerSec: Int, buffer: ByteBuffer?, renderTimeMs: Long,
            avsync_type: Int,
        ) = true

        override fun onMixedAudioFrame(
            channelId: String?, type: Int, samplesPerChannel: Int, bytesPerSample: Int,
            channels: Int, samplesPerSec: Int, buffer: ByteBuffer?, renderTimeMs: Long,
            avsync_type: Int,
        ) = true

        override fun onEarMonitoringAudioFrame(
            type: Int, samplesPerChannel: Int, bytesPerSample: Int, channels: Int,
            samplesPerSec: Int, buffer: ByteBuffer?, renderTimeMs: Long, avsync_type: Int,
        ) = true

        override fun onPlaybackAudioFrameBeforeMixing(
            channelId: String?, uid: Int, type: Int, samplesPerChannel: Int,
            bytesPerSample: Int, channels: Int, samplesPerSec: Int, buffer: ByteBuffer?,
            renderTimeMs: Long, avsync_type: Int, rtpTimestamp: Int,
        ) = true

        override fun getObservedAudioFramePosition() = Constants.POSITION_RECORD

        override fun getRecordAudioParams() = audioParams()
        override fun getPlaybackAudioParams() = audioParams()
        override fun getMixedAudioParams() = audioParams()
        override fun getEarMonitoringAudioParams() = audioParams()
    }

    private fun audioParams() = AudioParams(
        SAMPLE_RATE_HZ,
        CHANNELS,
        Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY,
        SAMPLES_PER_CALLBACK,
    )

    private companion object {
        private const val SAMPLE_RATE_HZ = 16_000
        private const val CHANNELS = 1
        private const val BYTES_PER_SAMPLE = 2
        private const val SAMPLES_PER_CALLBACK = 1_024
    }
}
