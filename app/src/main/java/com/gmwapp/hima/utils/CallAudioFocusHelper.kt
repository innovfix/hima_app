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

    /** True while this helper currently holds audio focus. */
    fun hasFocus(): Boolean = hasFocus

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // B186 — only true loss (LOSS / LOSS_TRANSIENT) should mute the
            // call. LOSS_TRANSIENT_CAN_DUCK is the system saying "lower
            // your volume, you don't have to stop" — for a VoIP voice
            // stream that's a no-op, and crucially the system does NOT
            // send a matching AUDIOFOCUS_GAIN back when the duck resolves,
            // so muting on DUCK left both streams permanently silent
            // ("call connected, no audio either side").
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost: $change")
                hasFocus = false
                try { onFocusLost() } catch (e: Exception) { Log.e(TAG, "onFocusLost threw", e) }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Explicitly NO-OP. Don't mute, don't touch state.
                Log.d(TAG, "Audio focus duck (ignored for VoIP)")
            }
            // B186 — handle every gain variant. With a GAIN_TRANSIENT_EXCLUSIVE
            // request, some Android versions return focus via GAIN_TRANSIENT
            // (or its variants), not the plain GAIN constant. Listening only
            // for GAIN missed those and left the call muted.
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> {
                Log.d(TAG, "Audio focus gained: $change")
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
            // GAIN_TRANSIENT_EXCLUSIVE matches native phone-call behaviour:
            // while we hold focus, the system denies any other app's request
            // (e.g. user tapping Play in Spotify mid-call). Plain TRANSIENT
            // would let the other app grab focus back and play over us — see
            // bug B148.
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
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
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
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
