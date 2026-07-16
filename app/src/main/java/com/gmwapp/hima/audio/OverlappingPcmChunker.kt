package com.gmwapp.hima.audio

/**
 * Bounded PCM16 mono window assembler. A one-second tail is copied into the next 30-second
 * window so a word at a boundary remains complete for a future transcription engine.
 */
class OverlappingPcmChunker(
    val sampleRateHz: Int = 16_000,
    chunkDurationMs: Int = 30_000,
    overlapDurationMs: Int = 1_000,
) {
    private val bytesPerSample = 2
    private val targetBytes: Int
    private val overlapBytes: Int
    private val stepSamples: Long
    private var buffer: ByteArray
    private var size = 0
    private var sequence = 0L
    private var windowStartSample = 0L

    init {
        require(sampleRateHz > 0)
        require(chunkDurationMs > 0)
        require(overlapDurationMs >= 0 && overlapDurationMs < chunkDurationMs)
        targetBytes = sampleRateHz * chunkDurationMs / 1000 * bytesPerSample
        overlapBytes = sampleRateHz * overlapDurationMs / 1000 * bytesPerSample
        stepSamples = ((targetBytes - overlapBytes) / bytesPerSample).toLong()
        buffer = ByteArray(targetBytes)
    }

    fun append(pcm16Le: ByteArray): List<Window> {
        require(pcm16Le.size % bytesPerSample == 0) { "PCM16 data must align to samples" }
        if (pcm16Le.isEmpty()) return emptyList()
        val completed = mutableListOf<Window>()
        var sourceOffset = 0
        while (sourceOffset < pcm16Le.size) {
            val copied = minOf(targetBytes - size, pcm16Le.size - sourceOffset)
            System.arraycopy(pcm16Le, sourceOffset, buffer, size, copied)
            size += copied
            sourceOffset += copied
            if (size == targetBytes) {
                completed += snapshot()
                advance()
            }
        }
        return completed
    }

    /** Emits the last partial window only when it contains audio newer than its overlap. */
    fun flush(): Window? {
        val currentOverlap = currentOverlapBytes()
        if (size <= currentOverlap) {
            reset()
            return null
        }
        val window = snapshot()
        reset()
        return window
    }

    fun reset() {
        if (size > 0) buffer.fill(0)
        size = 0
        sequence = 0L
        windowStartSample = 0L
    }

    private fun snapshot(): Window {
        val pcm = buffer.copyOf(size)
        val overlap = currentOverlapBytes()
        val durationSamples = size / bytesPerSample
        val startMs = windowStartSample * 1000L / sampleRateHz
        val endMs = (windowStartSample + durationSamples) * 1000L / sampleRateHz
        return Window(
            sequence = sequence,
            startMs = startMs,
            endMs = endMs,
            overlapBytes = overlap,
            overlapMs = overlap / bytesPerSample * 1000 / sampleRateHz,
            pcm16Le = pcm,
        )
    }

    private fun advance() {
        if (overlapBytes > 0) {
            System.arraycopy(buffer, targetBytes - overlapBytes, buffer, 0, overlapBytes)
        }
        size = overlapBytes
        sequence++
        windowStartSample += stepSamples
    }

    private fun currentOverlapBytes(): Int = if (sequence == 0L) 0 else overlapBytes

    data class Window(
        val sequence: Long,
        val startMs: Long,
        val endMs: Long,
        val overlapMs: Int,
        val overlapBytes: Int,
        val pcm16Le: ByteArray,
    )
}
