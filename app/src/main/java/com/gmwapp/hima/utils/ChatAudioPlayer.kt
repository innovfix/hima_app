package com.gmwapp.hima.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper

class ChatAudioPlayer(
    private val context: Context,
    private val onPlaybackStateChanged: (String) -> Unit,
    private val onProgressChanged: (String, Int, Int) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var currentMessageId: String? = null
    private val knownDurationsMs = mutableMapOf<String, Int>()

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

    fun toggle(messageId: String, source: String, onError: (String) -> Unit) {
        if (currentMessageId == messageId) {
            val player = mediaPlayer ?: return
            if (player.isPlaying) {
                player.pause()
                stopProgressUpdates()
            } else {
                player.start()
                startProgressUpdates()
            }
            onPlaybackStateChanged(messageId)
            return
        }

        val previousId = currentMessageId
        releaseCurrentPlayer()
        previousId?.let(onPlaybackStateChanged)

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
}
