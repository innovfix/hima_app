package com.gmwapp.hima.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Pcm16WavEncoder {
    const val HEADER_BYTES = 44

    fun encodeMono(pcm16Le: ByteArray, sampleRateHz: Int): ByteArray {
        require(pcm16Le.size % 2 == 0) { "PCM16 data must end on a sample boundary" }

        val output = ByteArray(HEADER_BYTES + pcm16Le.size)
        System.arraycopy(monoHeader(pcm16Le.size, sampleRateHz), 0, output, 0, HEADER_BYTES)
        System.arraycopy(pcm16Le, 0, output, HEADER_BYTES, pcm16Le.size)
        return output
    }

    /**
     * The 44-byte header on its own, so a whole call can be streamed to disk instead of
     * assembled in memory: a 10-minute track is ~19 MB of PCM, which is not something to
     * hold on a mid-range handset mid-call. Write [HEADER_BYTES] of padding, append PCM as
     * it arrives, then seek back and overwrite with this once the final size is known.
     */
    fun monoHeader(pcmBytes: Int, sampleRateHz: Int): ByteArray {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(pcmBytes >= 0) { "pcmBytes cannot be negative" }

        val out = ByteArray(HEADER_BYTES)
        val header = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcmBytes)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(sampleRateHz)
        header.putInt(sampleRateHz * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmBytes)
        return out
    }
}
