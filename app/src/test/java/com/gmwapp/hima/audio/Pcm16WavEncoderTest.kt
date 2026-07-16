package com.gmwapp.hima.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Pcm16WavEncoderTest {
    @Test
    fun emitsStandardMonoPcm16HeaderAndUnchangedPayload() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = Pcm16WavEncoder.encodeMono(pcm, sampleRateHz = 16_000)
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(40, header.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(16_000, header.getInt(24))
        assertEquals(32_000, header.getInt(28))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(4, header.getInt(40))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }
}
