package com.gmwapp.hima.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper

class ChatAudioPlayer(
    private val context: Context,
    private val onPlaybackStateChanged: (String) -> Unit,
    private val onProgressChanged: (String, Int, Int) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var currentMessageId: String? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    // CHAT-004: voice notes are played on the voice-call audio stream so
    // Android treats them as a phone call and blocks every screen recorder
    // (including privileged OEM ones) from capturing the audio. Saving the
    // user's previous audio mode + speaker state lets us restore the device
    // to its original behaviour when playback ends, so the user doesn't get
    // stuck in IN_COMMUNICATION mode (which would change media volume
    // routing for the rest of the app).
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeakerphoneOn: Boolean? = null
    // M19: cap to 64 entries with simple LRU so a long-lived chat doesn't grow
    // this map unbounded as the user scrolls through audio messages.
    private val knownDurationsMs = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean = size > 64
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return
            val messageId = currentMessageId ?: return
            val duration = player.duration.coerceAtLeast(0)
            val position = player.currentPosition.coerceAtLeast(0)
            knownDurationsMs[messageId] = duration
            onProgressChanged(messageId, position, duration)
            if (player.isPlaying) {
                mainHandler.postDelayed(this, 250L)
            }
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        val player = mediaPlayer ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (player.isPlaying) {
                    player.pause()
                    resumeOnFocusGain = true
                    stopProgressUpdates()
                    currentMessageId?.let(onPlaybackStateChanged)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    player.start()
                    startProgressUpdates()
                    currentMessageId?.let(onPlaybackStateChanged)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                val previousId = currentMessageId
                releaseCurrentPlayer()
                currentMessageId = null
                previousId?.let(onPlaybackStateChanged)
            }
        }
    }

    fun toggle(messageId: String, source: String, onError: (String) -> Unit) {
        if (currentMessageId == messageId) {
            val player = mediaPlayer ?: return
            if (player.isPlaying) {
                player.pause()
                resumeOnFocusGain = false
                stopProgressUpdates()
            } else {
                if (!requestAudioFocus()) {
                    onError("Couldn't play this audio message")
                    return
                }
                player.start()
                startProgressUpdates()
            }
            onPlaybackStateChanged(messageId)
            return
        }

        val previousId = currentMessageId
        releaseCurrentPlayer()
        previousId?.let(onPlaybackStateChanged)

        if (!requestAudioFocus()) {
            onError("Couldn't play this audio message")
            return
        }

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    // CHAT-004: USAGE_VOICE_COMMUNICATION puts the playback on
                    // the phone-call audio stream — every screen recorder
                    // (including privileged OEM ones on MIUI / ColorOS /
                    // OneUI) is forbidden by Android from capturing this
                    // stream, the same way it can't capture a real phone
                    // call. setAllowedCapturePolicy is kept as a
                    // belt-and-suspenders measure for the AudioPlaybackCapture
                    // path on stock Android. Speaker output is forced on
                    // separately in [enableSpeakerForCallPlayback] so users
                    // still hear the audio out loud, matching the previous UX.
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                        }
                    }
                    .build()
            )
            setOnPreparedListener { prepared ->
                currentMessageId = messageId
                knownDurationsMs[messageId] = prepared.duration.coerceAtLeast(0)
                prepared.start()
                onPlaybackStateChanged(messageId)
                startProgressUpdates()
            }
            setOnCompletionListener { completed ->
                stopProgressUpdates()
                completed.seekTo(0)
                val duration = completed.duration.coerceAtLeast(0)
                onProgressChanged(messageId, 0, duration)
                onPlaybackStateChanged(messageId)
            }
            setOnErrorListener { _, _, _ ->
                releaseCurrentPlayer()
                currentMessageId = null
                onPlaybackStateChanged(messageId)
                onError("Couldn't play this audio message")
                true
            }
        }

        try {
            setDataSource(player, source)
            mediaPlayer = player
            player.prepareAsync()
        } catch (e: Exception) {
            releaseCurrentPlayer()
            currentMessageId = null
            onPlaybackStateChanged(messageId)
            onError(e.message ?: "Couldn't play this audio message")
        }
    }

    fun isPlaying(messageId: String): Boolean =
        currentMessageId == messageId && (mediaPlayer?.isPlaying == true)

    fun getProgress(messageId: String): Int =
        if (currentMessageId == messageId) mediaPlayer?.currentPosition ?: 0 else 0

    fun getDuration(messageId: String, fallbackMs: Long): Int {
        if (currentMessageId == messageId) {
            val activeDuration = mediaPlayer?.duration ?: 0
            if (activeDuration > 0) return activeDuration
        }
        return knownDurationsMs[messageId] ?: fallbackMs.toInt()
    }

    fun release() {
        val previousId = currentMessageId
        releaseCurrentPlayer()
        currentMessageId = null
        previousId?.let(onPlaybackStateChanged)
    }

    private fun setDataSource(player: MediaPlayer, source: String) {
        val uri = Uri.parse(source)
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            player.setDataSource(source)
        } else {
            player.setDataSource(context, uri)
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        mainHandler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable)
    }

    private fun releaseCurrentPlayer() {
        stopProgressUpdates()
        abandonAudioFocus()
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // CHAT-004: match the MediaPlayer's usage so the focus
                        // request targets the voice-call stream too.
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) enableSpeakerForCallPlayback()
        return granted
    }

    private fun abandonAudioFocus() {
        restoreSpeakerAfterCallPlayback()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        resumeOnFocusGain = false
    }

    /**
     * CHAT-004: switch the device into voice-communication mode and force
     * speaker output so the user hears the voice note on the loudspeaker
     * even though the playback uses the phone-call audio path. The phone-call
     * stream is what makes screen recorders unable to capture the audio.
     * Idempotent — only saves the previous state on the first call so repeated
     * play/pause cycles don't overwrite the user's original speaker setting.
     */
    private fun enableSpeakerForCallPlayback() {
        if (savedSpeakerphoneOn != null) return
        try {
            savedAudioMode = audioManager.mode
            @Suppress("DEPRECATION")
            savedSpeakerphoneOn = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        } catch (_: SecurityException) {
            // Some OEMs require MODIFY_AUDIO_SETTINGS; if missing, the player
            // will route through earpiece. The capture-blocking is what matters.
            savedSpeakerphoneOn = null
        }
    }

    /**
     * CHAT-004: restore the device to whatever audio mode + speaker state it
     * was in before we started playback. No-op if we never entered call mode.
     */
    private fun restoreSpeakerAfterCallPlayback() {
        val previous = savedSpeakerphoneOn ?: return
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = previous
            audioManager.mode = savedAudioMode
        } catch (_: SecurityException) {
            // Best effort.
        } finally {
            savedSpeakerphoneOn = null
        }
    }
}
