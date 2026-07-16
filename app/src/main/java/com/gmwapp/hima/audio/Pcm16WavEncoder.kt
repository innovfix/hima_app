package com.gmwapp.hima.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Pcm16WavEncoder {
    private const val HEADER_BYTES = 44

    fun encodeMono(pcm16Le: ByteArray, sampleRateHz: Int): ByteArray {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(pcm16Le.size % 2 == 0) { "PCM16 data must end on a sample boundary" }

        val output = ByteArray(HEADER_BYTES + pcm16Le.size)
        val header = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm16Le.size)
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
        header.putInt(pcm16Le.size)
        System.arraycopy(pcm16Le, 0, output, HEADER_BYTES, pcm16Le.size)
        return output
    }
}
