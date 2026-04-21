package com.gmwapp.hima.utils

import android.media.MediaRecorder
import android.os.SystemClock
import java.io.File

class AudioRecorderController(private val cacheDir: File) {

    data class RecordingResult(
        val file: File,
        val durationMs: Long
    )

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    fun isRecording(): Boolean = recorder != null

    fun start(): File {
        cancel()

        val file = File.createTempFile("chat_audio_${System.currentTimeMillis()}", ".m4a", cacheDir)
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
            currentRecorder.reset()
            currentRecorder.release()
            recorder = null
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
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Exception) {
        }

        recorder = null
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
}
