package com.gmwapp.hima.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class EnergyVoiceActivityDetectorTest {
    private val detector = EnergyVoiceActivityDetector()

    @Test
    fun silenceIsNotSpeech() {
        val summary = detector.analyse(ByteArray(16_000 * 2), sampleRateHz = 16_000)

        assertFalse(summary.hasSpeech)
        assertEquals(0L, summary.voicedMs)
        assertEquals(1_000L, summary.analysedMs)
    }

    @Test
    fun ordinarySpeechLevelToneIsSpeech() {
        val summary = detector.analyse(tone(durationMs = 200, amplitude = 4_000), 16_000)

        assertTrue(summary.hasSpeech)
        assertTrue(summary.voicedMs >= 180)
        assertTrue(summary.averageRmsDbfs > -30.0)
    }

    @Test
    fun veryLowLevelNoiseDoesNotBecomeSpeech() {
        val summary = detector.analyse(tone(durationMs = 500, amplitude = 100), 16_000)

        assertFalse(summary.hasSpeech)
    }

    private fun tone(durationMs: Int, amplitude: Int): ByteArray {
        val samples = 16_000 * durationMs / 1_000
        val output = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(samples) { index ->
            val value = (sin(2.0 * PI * 440.0 * index / 16_000.0) * amplitude).toInt()
            output.putShort(value.toShort())
        }
        return output.array()
    }
}
