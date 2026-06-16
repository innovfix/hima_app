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
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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

    // TC_015: resolve an audio message's duration from its file metadata without
    // playing it, so the timer doesn't sit at "--:--" / 0 when the server omits
    // the duration (which it does for both old messages and freshly-synced ones).
    // Best-effort, off the main thread, cached in knownDurationsMs; on success
    // [onResolved] fires on the main thread so the row can refresh.
    private val durationExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val durationPrefetchInFlight =
        java.util.Collections.synchronizedSet(HashSet<String>())
    // TC_015: ids whose metadata read already finished (success or failure) this
    // session — stops a permanently-unreadable file from re-fetching its metadata
    // on every re-bind/scroll. Cleared in [release] so a fresh session retries.
    private val durationPrefetchAttempted =
        java.util.Collections.synchronizedSet(HashSet<String>())

    fun prefetchDuration(messageId: String, source: String?, onResolved: (String) -> Unit) {
        if (source.isNullOrBlank()) return
        knownDurationsMs[messageId]?.let { if (it > 0) return }
        if (messageId in durationPrefetchAttempted) return
        if (!durationPrefetchInFlight.add(messageId)) return
        try {
            durationExecutor.execute {
                var resolved = 0
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    val uri = Uri.parse(source)
                    val scheme = uri.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") {
                        retriever.setDataSource(source, HashMap<String, String>())
                    } else {
                        retriever.setDataSource(context, uri)
                    }
                    resolved = retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                } catch (e: Exception) {
                    // best-effort — leave unresolved, UI keeps "--:--"
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                    durationPrefetchInFlight.remove(messageId)
                    durationPrefetchAttempted.add(messageId)
                }
                if (resolved > 0) {
                    // Touch knownDurationsMs ONLY on the main thread: it is an
                    // access-ordered LinkedHashMap shared with playback, and access
                    // order mutates the list even on reads, so a background write
                    // here would race every main-thread access. Hand off to main.
                    mainHandler.post {
                        knownDurationsMs[messageId] = resolved
                        onResolved(messageId)
                    }
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            durationPrefetchInFlight.remove(messageId)
        }
    }

    fun release() {
        val previousId = currentMessageId
        releaseCurrentPlayer()
        currentMessageId = null
        previousId?.let(onPlaybackStateChanged)
        runCatching { durationExecutor.shutdownNow() }
        durationPrefetchInFlight.clear()
        durationPrefetchAttempted.clear()
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
                        .setUsage(AudioAttributes.USAGE_MEDIA)
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
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        resumeOnFocusGain = false
    }
}
