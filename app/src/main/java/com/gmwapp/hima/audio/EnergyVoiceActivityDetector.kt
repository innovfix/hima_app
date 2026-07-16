package com.gmwapp.hima.audio

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Lightweight, language-independent VAD used only to label extracted chunks.
 *
 * It deliberately favours recall: speech is never removed from a chunk and the app does not yet
 * skip or upload anything based on this result. A WebRTC/Silero VAD can later implement the same
 * boundary without changing Agora capture or chunk assembly.
 */
class EnergyVoiceActivityDetector(
    private val frameMs: Int = 20,
    private val minimumSpeechMs: Int = 60,
    private val rmsThresholdDbfs: Double = -52.0,
    private val peakThreshold: Int = 384,
) {
    fun analyse(
        pcm16Le: ByteArray,
        sampleRateHz: Int,
        offsetBytes: Int = 0,
        lengthBytes: Int = pcm16Le.size - offsetBytes,
    ): VoiceActivitySummary {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(offsetBytes >= 0 && lengthBytes >= 0 && offsetBytes + lengthBytes <= pcm16Le.size)
        require(offsetBytes % 2 == 0 && lengthBytes % 2 == 0) {
            "PCM16 ranges must align to two-byte samples"
        }
        if (lengthBytes == 0) return VoiceActivitySummary(false, 0, 0, 0.0, SILENCE_DBFS)

        val samplesPerFrame = (sampleRateHz * frameMs / 1000).coerceAtLeast(1)
        val frameBytes = samplesPerFrame * 2
        val end = offsetBytes + lengthBytes
        var cursor = offsetBytes
        var voicedSamples = 0L
        var analysedSamples = 0L
        var squareSum = 0.0

        while (cursor < end) {
            val thisFrameEnd = (cursor + frameBytes).coerceAtMost(end)
            var frameSquareSum = 0.0
            var framePeak = 0
            var frameSamples = 0
            var sampleCursor = cursor
            while (sampleCursor + 1 < thisFrameEnd) {
                val sample = ((pcm16Le[sampleCursor + 1].toInt() shl 8) or
                    (pcm16Le[sampleCursor].toInt() and 0xff)).toShort().toInt()
                val magnitude = kotlin.math.abs(sample)
                if (magnitude > framePeak) framePeak = magnitude
                val sampleDouble = sample.toDouble()
                frameSquareSum += sampleDouble * sampleDouble
                frameSamples++
                sampleCursor += 2
            }
            if (frameSamples > 0) {
                val rms = sqrt(frameSquareSum / frameSamples)
                val dbfs = toDbfs(rms)
                if (dbfs >= rmsThresholdDbfs && framePeak >= peakThreshold) {
                    voicedSamples += frameSamples
                }
                analysedSamples += frameSamples
                squareSum += frameSquareSum
            }
            cursor = thisFrameEnd
        }

        val voicedMs = voicedSamples * 1000L / sampleRateHz
        val analysedMs = analysedSamples * 1000L / sampleRateHz
        val speechRatio = if (analysedSamples == 0L) 0.0 else voicedSamples.toDouble() / analysedSamples
        val averageRms = if (analysedSamples == 0L) 0.0 else sqrt(squareSum / analysedSamples)
        return VoiceActivitySummary(
            hasSpeech = voicedMs >= minimumSpeechMs,
            voicedMs = voicedMs,
            analysedMs = analysedMs,
            speechRatio = speechRatio,
            averageRmsDbfs = toDbfs(averageRms),
        )
    }

    private fun toDbfs(rms: Double): Double {
        if (rms <= 0.0) return SILENCE_DBFS
        return (20.0 * log10(rms / PCM16_FULL_SCALE)).coerceAtLeast(SILENCE_DBFS)
    }

    private companion object {
        private const val PCM16_FULL_SCALE = 32768.0
        private const val SILENCE_DBFS = -96.0
    }
}
