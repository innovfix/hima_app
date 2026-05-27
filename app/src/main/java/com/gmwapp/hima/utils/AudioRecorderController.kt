package com.gmwapp.hima.utils

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.File

class AudioRecorderController(
    private val cacheDir: File,
    private val audioManager: AudioManager
) {

    /**
     * Thrown from [start] when the mic can't be opened safely — either a
     * cellular/VoIP call is active (CHAT-008) or another app refuses to give up
     * audio focus (CHAT-009 / CHAT-010). [message] is user-facing.
     */
    class RecordingBlockedException(message: String) : IllegalStateException(message)

    data class RecordingResult(
        val file: File,
        val durationMs: Long
    )

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false

    /**
     * Invoked when audio focus is lost while recording (e.g. an incoming call
     * takes over the mic). The caller should stop/cancel the in-progress note on
     * the main thread. Fired off the audio thread.
     */
    var onFocusLost: (() -> Unit)? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isRecording()) onFocusLost?.invoke()
            }
        }
    }

    fun isRecording(): Boolean = recorder != null

    fun start(): File {
        cancel()

        // CHAT-008: never open the mic while a phone/VoIP call owns the audio
        // path — a voice note started mid-call would otherwise capture the call.
        if (isCallActive()) {
            throw RecordingBlockedException("Can't record a voice note during a call")
        }

        // CHAT-009 / CHAT-010: take exclusive transient audio focus before
        // recording so other apps (Spotify, split-screen YouTube, …) pause and
        // their audio is not bled into the voice note.
        if (!requestExclusiveAudioFocus()) {
            throw RecordingBlockedException("Couldn't access the microphone — another app is using audio")
        }

        val file = File.createTempFile("chat_audio_${System.currentTimeMillis()}", ".m4a", cacheDir)
        try {
            val mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(22050)
                setAudioEncodingBitRate(64000)
                setAudioChannels(1)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mediaRecorder
            outputFile = file
            startTimeMs = SystemClock.elapsedRealtime()
            return file
        } catch (e: Exception) {
            // prepare()/start() can fail (mic busy, codec error). Don't leak the
            // half-open recorder, the temp file, or the audio focus we just took.
            abandonAudioFocus()
            file.delete()
            throw e
        }
    }

    fun stop(): RecordingResult {
        val currentRecorder = recorder ?: throw IllegalStateException("Recorder not started")
        val file = outputFile ?: throw IllegalStateException("Missing output file")

        var stopFailed = false
        try {
            currentRecorder.stop()
        } catch (_: Exception) {
            stopFailed = true
        } finally {
            // CHAT-001: always tear the mic down — reset+release the recorder and
            // drop audio focus — even when stop() throws. Otherwise MediaRecorder
            // keeps the input open and the phone keeps recording after the note.
            releaseRecorder(currentRecorder)
        }

        outputFile = null
        val durationMs = (SystemClock.elapsedRealtime() - startTimeMs).coerceAtLeast(0L)
        startTimeMs = 0L

        if (stopFailed || !file.exists() || file.length() == 0L) {
            file.delete()
            throw IllegalStateException("Recording failed")
        }

        return RecordingResult(file, durationMs)
    }

    fun cancel() {
        recorder?.let { current ->
            try {
                current.stop()
            } catch (_: Exception) {
            }
            releaseRecorder(current)
        }
        startTimeMs = 0L

        outputFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        outputFile = null
    }

    fun release() {
        cancel()
    }

    /** Stop owning the mic: reset+release the recorder and abandon audio focus. */
    private fun releaseRecorder(mediaRecorder: MediaRecorder) {
        try {
            mediaRecorder.reset()
            mediaRecorder.release()
        } catch (_: Exception) {
        }
        recorder = null
        abandonAudioFocus()
    }

    private fun isCallActive(): Boolean {
        val mode = audioManager.mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    private fun requestExclusiveAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .setAcceptsDelayedFocusGain(false)
            .build()
        focusRequest = req
        hasAudioFocus = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        try {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {
        } finally {
            focusRequest = null
            hasAudioFocus = false
        }
    }
}
