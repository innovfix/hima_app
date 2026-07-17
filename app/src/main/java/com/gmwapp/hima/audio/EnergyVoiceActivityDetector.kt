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

        var voicedSamples = 0L
        var analysedSamples = 0L
        var squareSum = 0.0

        forEachFrame(pcm16Le, sampleRateHz, offsetBytes, lengthBytes) { _, _, frameSamples, voiced, frameSquareSum ->
            if (voiced) voicedSamples += frameSamples
            analysedSamples += frameSamples
            squareSum += frameSquareSum
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

    /**
     * Byte ranges (into [pcm16Le]) that contain speech, padded and merged.
     *
     * [analyse] answers "did anyone speak in this window", which over a 30s window is true
     * almost always and therefore trims nothing. This answers "WHERE did they speak", which
     * is what lets silence be dropped before upload — on this app each device records only
     * its own microphone, so roughly half of every track is the other person talking.
     *
     * Shares [forEachFrame] with [analyse] deliberately: two copies of the threshold logic
     * would drift, and a chunk could then be labelled hasSpeech while trimming to nothing.
     *
     * [paddingMs] is padded onto both sides of every voiced run before merging. Speech
     * starts soft and trails off, so cutting on the exact threshold crossing clips the first
     * and last syllable of every sentence — which wrecks a transcript far more than the few
     * percent of silence the padding costs back.
     */
    fun voicedRanges(
        pcm16Le: ByteArray,
        sampleRateHz: Int,
        offsetBytes: Int = 0,
        lengthBytes: Int = pcm16Le.size - offsetBytes,
        paddingMs: Int = DEFAULT_PADDING_MS,
    ): List<IntRange> {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(offsetBytes >= 0 && lengthBytes >= 0 && offsetBytes + lengthBytes <= pcm16Le.size)
        require(offsetBytes % 2 == 0 && lengthBytes % 2 == 0) {
            "PCM16 ranges must align to two-byte samples"
        }
        if (lengthBytes == 0) return emptyList()

        val padBytes = alignDown((sampleRateHz.toLong() * paddingMs / 1000L * 2L).toInt())
        val end = offsetBytes + lengthBytes
        val merged = mutableListOf<IntRange>()

        forEachFrame(pcm16Le, sampleRateHz, offsetBytes, lengthBytes) { frameStart, frameEnd, _, voiced, _ ->
            if (!voiced) return@forEachFrame
            val from = (frameStart - padBytes).coerceAtLeast(offsetBytes)
            val to = (frameEnd + padBytes).coerceAtMost(end)
            val last = merged.lastOrNull()
            // Padding makes neighbouring runs overlap; merging keeps one contiguous span per
            // utterance instead of re-cutting mid-word at every frame boundary.
            if (last != null && from <= last.last + 1) {
                merged[merged.size - 1] = last.first..maxOf(last.last, to - 1)
            } else {
                merged += from..(to - 1)
            }
        }

        return merged
    }

    /**
     * The speech-only PCM, concatenated. Returns an empty array when nothing was voiced —
     * callers must treat that as "nothing worth uploading" rather than as an error.
     */
    fun trimToSpeech(
        pcm16Le: ByteArray,
        sampleRateHz: Int,
        offsetBytes: Int = 0,
        lengthBytes: Int = pcm16Le.size - offsetBytes,
        paddingMs: Int = DEFAULT_PADDING_MS,
    ): ByteArray {
        val ranges = voicedRanges(pcm16Le, sampleRateHz, offsetBytes, lengthBytes, paddingMs)
        if (ranges.isEmpty()) return ByteArray(0)

        val total = ranges.sumOf { it.last - it.first + 1 }
        val out = ByteArray(total)
        var written = 0
        for (r in ranges) {
            val len = r.last - r.first + 1
            System.arraycopy(pcm16Le, r.first, out, written, len)
            written += len
        }
        return out
    }

    /**
     * Single frame walk shared by [analyse] and [voicedRanges]. Keeps the last partial frame
     * rather than discarding it, so a run of speech at the very end of a window is not lost.
     */
    private inline fun forEachFrame(
        pcm16Le: ByteArray,
        sampleRateHz: Int,
        offsetBytes: Int,
        lengthBytes: Int,
        onFrame: (frameStart: Int, frameEnd: Int, frameSamples: Int, voiced: Boolean, frameSquareSum: Double) -> Unit,
    ) {
        val samplesPerFrame = (sampleRateHz * frameMs / 1000).coerceAtLeast(1)
        val frameBytes = samplesPerFrame * 2
        val end = offsetBytes + lengthBytes
        var cursor = offsetBytes

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
                val voiced = toDbfs(rms) >= rmsThresholdDbfs && framePeak >= peakThreshold
                onFrame(cursor, thisFrameEnd, frameSamples, voiced, frameSquareSum)
            }
            cursor = thisFrameEnd
        }
    }

    private fun alignDown(bytes: Int): Int = bytes - (bytes % 2)

    private fun toDbfs(rms: Double): Double {
        if (rms <= 0.0) return SILENCE_DBFS
        return (20.0 * log10(rms / PCM16_FULL_SCALE)).coerceAtLeast(SILENCE_DBFS)
    }

    private companion object {
        private const val PCM16_FULL_SCALE = 32768.0
        private const val SILENCE_DBFS = -96.0

        /** Kept either side of every voiced run so soft onsets and trailing syllables survive. */
        private const val DEFAULT_PADDING_MS = 300
    }
}
