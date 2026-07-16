package com.gmwapp.hima.audio

/**
 * One bounded piece of a participant's local microphone track.
 *
 * Audio stays in memory. The current app intentionally has no uploader or persistent sink; a
 * future transcription integration can consume [pcm16Le] directly or call [toWav].
 */
data class ExtractedAudioChunk(
    val sequence: Long,
    val startMs: Long,
    val endMs: Long,
    val overlapMs: Int,
    val sampleRateHz: Int,
    val pcm16Le: ByteArray,
    val voiceActivity: VoiceActivitySummary,
) {
    val durationMs: Long
        get() = endMs - startMs

    fun toWav(): ByteArray = Pcm16WavEncoder.encodeMono(pcm16Le, sampleRateHz)
}

data class VoiceActivitySummary(
    val hasSpeech: Boolean,
    val voicedMs: Long,
    val analysedMs: Long,
    val speechRatio: Double,
    val averageRmsDbfs: Double,
)
