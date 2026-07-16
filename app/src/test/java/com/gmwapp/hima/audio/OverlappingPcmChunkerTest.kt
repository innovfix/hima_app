package com.gmwapp.hima.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlappingPcmChunkerTest {
    @Test
    fun fullWindowsCarryExactTailAndAdvanceByUniqueDuration() {
        val chunker = OverlappingPcmChunker(
            sampleRateHz = 1_000,
            chunkDurationMs = 100,
            overlapDurationMs = 20,
        )
        val firstPcm = pcmSamples(0, 100)
        val first = chunker.append(firstPcm).single()

        assertEquals(0L, first.sequence)
        assertEquals(0L, first.startMs)
        assertEquals(100L, first.endMs)
        assertEquals(0, first.overlapMs)

        val secondNewAudio = pcmSamples(100, 80)
        val second = chunker.append(secondNewAudio).single()
        assertEquals(1L, second.sequence)
        assertEquals(80L, second.startMs)
        assertEquals(180L, second.endMs)
        assertEquals(20, second.overlapMs)
        assertArrayEquals(firstPcm.copyOfRange(160, 200), second.pcm16Le.copyOfRange(0, 40))
        assertArrayEquals(secondNewAudio, second.pcm16Le.copyOfRange(40, 200))
    }

    @Test
    fun flushDoesNotEmitAnOverlapOnlyDuplicate() {
        val chunker = OverlappingPcmChunker(
            sampleRateHz = 1_000,
            chunkDurationMs = 100,
            overlapDurationMs = 20,
        )
        assertEquals(1, chunker.append(pcmSamples(0, 100)).size)
        assertNull(chunker.flush())
    }

    @Test
    fun flushEmitsPartialWindowWhenNewAudioExists() {
        val chunker = OverlappingPcmChunker(
            sampleRateHz = 1_000,
            chunkDurationMs = 100,
            overlapDurationMs = 20,
        )
        chunker.append(pcmSamples(0, 100))
        chunker.append(pcmSamples(100, 10))

        val partial = chunker.flush()!!
        assertEquals(1L, partial.sequence)
        assertEquals(80L, partial.startMs)
        assertEquals(110L, partial.endMs)
        assertEquals(20, partial.overlapMs)
        assertTrue(partial.pcm16Le.size == 60)
    }

    @Test
    fun hourLongInputStaysInBoundedThirtySecondWindows() {
        val chunker = OverlappingPcmChunker()
        val agoraSizedFrame = ByteArray(1_024 * 2)
        var completed = 0
        var largestWindow = 0

        repeat(16_000 * 3_600 / 1_024) {
            chunker.append(agoraSizedFrame).forEach { window ->
                completed++
                largestWindow = maxOf(largestWindow, window.pcm16Le.size)
            }
        }

        assertTrue(completed >= 120)
        assertTrue(largestWindow <= 16_000 * 30 * 2)
    }

    private fun pcmSamples(start: Int, count: Int): ByteArray {
        val output = ByteArray(count * 2)
        repeat(count) { index ->
            val sample = start + index
            output[index * 2] = sample.toByte()
            output[index * 2 + 1] = (sample ushr 8).toByte()
        }
        return output
    }
}
