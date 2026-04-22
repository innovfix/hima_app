package com.gmwapp.hima.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Requests audio focus for the duration of a VoIP call so other media apps
 * (Spotify, YouTube, etc.) pause while the user is on a Hima call. On focus
 * loss (e.g. a cellular call takes over), the [onFocusLost] callback fires so
 * the caller can mute the Agora local stream; [onFocusGained] restores it.
 */
class CallAudioFocusHelper(
    private val context: Context,
    private val onFocusLost: () -> Unit = {},
    private val onFocusGained: () -> Unit = {}
) {

    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus: Boolean = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost: $change")
                hasFocus = false
                try { onFocusLost() } catch (e: Exception) { Log.e(TAG, "onFocusLost threw", e) }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasFocus = true
                try { onFocusGained() } catch (e: Exception) { Log.e(TAG, "onFocusGained threw", e) }
            }
        }
    }

    fun request() {
        if (hasFocus) return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener)
                .setAcceptsDelayedFocusGain(false)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "requestAudioFocus granted=$hasFocus")
    }

    fun abandon() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusListener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "abandonAudioFocus threw", e)
        } finally {
            hasFocus = false
        }
    }

    companion object {
        private const val TAG = "CallAudioFocus"
    }
}
