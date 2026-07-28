package com.gmwapp.hima.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes the local microphone stream to AAC-LC in an MP4/M4A container.
 *
 * Replaces raw WAV, which was 32 KB/s — a 2-minute call was 3.1 MB leaving the user's phone
 * on mobile data, twice per call. AAC at 32 kbps is 4 KB/s: the same call is 388 KB, an 8x
 * reduction, with no meaningful loss on speech at 16 kHz mono.
 *
 * AAC rather than Opus deliberately. Opus is the better codec per byte, but Android's Opus
 * ENCODER and the OGG muxer are both API 29+, and this app ships minSdk 24. Opus would mean
 * two encoders, two container formats in storage and two paths through the moderation
 * pipeline, to save roughly Rs 5,900/month. AAC-LC has been in MediaCodec since API 16, so
 * every device takes the same path. It is also the format both OpenRouter and Gemini accept
 * without ambiguity ('aac'/'m4a'), whereas their 'ogg' is documented as OGG Vorbis.
 *
 * Not thread-safe: [write] and [stop] must be called from the single thread that owns the
 * recording — in practice the extractor's serial worker.
 */
class AacAudioRecorder(
    private val sampleRateHz: Int = 16_000,
    private val bitRate: Int = 32_000,
) {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var presentationTimeUs = 0L
    private var failed = false

    /** Bytes of PCM accepted so far. The caller's size cap is applied against this. */
    var pcmBytesWritten: Int = 0
        private set

    private val bufferInfo = MediaCodec.BufferInfo()

    /**
     * Returns false if the encoder could not be created — the caller must then abandon the
     * recording rather than silently producing an empty file. A device with no AAC encoder
     * is not expected (API 16+), but a codec can still fail to configure under memory
     * pressure, and that must not take the call down.
     */
    fun start(output: File): Boolean {
        return try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateHz, CHANNELS).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "AAC encoder unavailable: ${t.message}")
            releaseQuietly()
            false
        }
    }

    /**
     * Feeds PCM16 mono. Input is copied into the codec's buffers, so the caller may reuse
     * its array immediately.
     *
     * A single failure marks the recording dead rather than throwing: a codec hiccup
     * mid-call must not propagate into the Agora callback path and disturb the call itself.
     */
    fun write(pcm16Le: ByteArray, offset: Int = 0, length: Int = pcm16Le.size - offset) {
        val encoder = codec ?: return
        if (failed || length <= 0) return

        try {
            var written = 0
            var stalls = 0
            while (written < length) {
                val inputIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex < 0) {
                    // No input buffer free: drain output to make room and retry. Dropping
                    // immediately would silently lose speech. But this must be bounded — a
                    // wedged codec would otherwise spin this loop forever on the extractor's
                    // worker thread, and the recording would hang for the rest of the call.
                    if (++stalls > MAX_INPUT_STALLS) {
                        Log.w(TAG, "Encoder stalled; dropping ${length - written} bytes of this chunk")
                        return
                    }
                    drain(endOfStream = false)
                    continue
                }
                stalls = 0
                val inputBuffer = encoder.getInputBuffer(inputIndex) ?: continue
                inputBuffer.clear()
                val chunk = minOf(inputBuffer.capacity(), length - written)
                inputBuffer.put(pcm16Le, offset + written, chunk)
                encoder.queueInputBuffer(inputIndex, 0, chunk, presentationTimeUs, 0)

                // Timestamps are derived from bytes fed, not wall clock. Muting pauses the
                // stream, so a clock-based stamp would leave gaps the muxer renders as
                // silence — and stretch a 2-minute recording across a 10-minute call.
                presentationTimeUs += chunk.toLong() * 1_000_000L / (sampleRateHz.toLong() * BYTES_PER_SAMPLE)
                written += chunk
                pcmBytesWritten += chunk
                drain(endOfStream = false)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "AAC encode failed, abandoning recording: ${t.message}")
            failed = true
        }
    }

    /**
     * Flushes the encoder, finalises the container and releases everything.
     *
     * Returns false when the file is unusable — encoder failure, or no frames ever reached
     * the muxer. The caller must delete the file in that case; a truncated MP4 has no moov
     * atom and nothing can play it.
     */
    fun stop(): Boolean {
        val encoder = codec
        if (encoder == null) {
            releaseQuietly()
            return false
        }

        var ok = !failed
        try {
            if (!failed) {
                val inputIndex = encoder.dequeueInputBuffer(END_OF_STREAM_TIMEOUT_US)
                if (inputIndex >= 0) {
                    encoder.queueInputBuffer(
                        inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    drain(endOfStream = true)
                } else {
                    // Could not signal EOS; the tail is lost but whatever the muxer already
                    // holds is still a valid file.
                    Log.w(TAG, "No input buffer for end-of-stream; finalising what we have")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "AAC finalise failed: ${t.message}")
            ok = false
        }

        if (!muxerStarted) {
            // The muxer never received a track, so the file has no moov atom and is not a
            // playable MP4 regardless of its size.
            ok = false
        }

        releaseQuietly()
        return ok && pcmBytesWritten > 0
    }

    /**
     * Moves encoded frames from the codec into the muxer.
     *
     * The muxer's track can only be added on INFO_OUTPUT_FORMAT_CHANGED — that is where the
     * codec-specific data (AudioSpecificConfig) arrives, and without it the file is
     * undecodable. Writing samples before the track exists throws, which is why every
     * sample write is guarded on [muxerStarted].
     */
    private fun drain(endOfStream: Boolean) {
        val encoder = codec ?: return
        val output = muxer ?: return

        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Only keep waiting when we have told the codec the stream is over;
                    // otherwise return and let more input arrive.
                    if (!endOfStream) return
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = output.addTrack(encoder.outputFormat)
                        output.start()
                        muxerStarted = true
                    }
                }

                outputIndex >= 0 -> {
                    val encoded = encoder.getOutputBuffer(outputIndex)
                    if (encoded != null && bufferInfo.size > 0 && muxerStarted &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        writeSample(output, encoded)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    private fun writeSample(output: MediaMuxer, encoded: ByteBuffer) {
        encoded.position(bufferInfo.offset)
        encoded.limit(bufferInfo.offset + bufferInfo.size)
        output.writeSampleData(trackIndex, encoded, bufferInfo)
    }

    private fun releaseQuietly() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        // stop() throws if no data was ever written, so it is attempted separately from
        // release() — release must happen either way or the muxer leaks a file handle.
        if (muxerStarted) runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        muxer = null
        muxerStarted = false
    }

    private companion object {
        private const val TAG = "AacAudioRecorder"
        private const val CHANNELS = 1
        private const val BYTES_PER_SAMPLE = 2
        private const val MAX_INPUT_SIZE = 16_384
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        // ~2s of retries at the dequeue timeout above before giving up on a chunk.
        private const val MAX_INPUT_STALLS = 200
        private const val END_OF_STREAM_TIMEOUT_US = 100_000L
    }
}
