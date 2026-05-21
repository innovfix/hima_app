package com.gmwapp.hima.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Listens for wired headset plug / unplug events during a call so the hosting
 * activity can update the speaker-button icon to reflect the actual audio
 * route. Mirrors [BluetoothCallWatcher] but for the 3.5mm / USB-C / USB
 * headset path.
 *
 * Uses [Intent.ACTION_HEADSET_PLUG]. That broadcast is sticky — Android
 * re-delivers the current state immediately after [register] — so we drop
 * the first delivery and only forward genuine plug-state transitions.
 *
 * No runtime permission required: the action describes the audio path, not
 * the headset device.
 */
class WiredHeadsetWatcher(
    private val context: Context,
    private val onChange: (plugged: Boolean) -> Unit
) {

    private var initialReceived = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_HEADSET_PLUG) return
            val plugged = intent.getIntExtra("state", 0) == 1
            if (!initialReceived) {
                initialReceived = true
                Log.d(TAG, "Wired initial sticky plugged=$plugged (skipped)")
                return
            }
            Log.d(TAG, "Wired headset ${if (plugged) "PLUGGED" else "UNPLUGGED"}")
            try {
                onChange(plugged)
            } catch (e: Exception) {
                Log.e(TAG, "onChange callback threw", e)
            }
        }
    }
    private var registered = false

    fun register() {
        if (registered) return
        try {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
            registered = true
        } catch (e: Exception) {
            Log.w(TAG, "register failed: ${e.message}")
        }
    }

    fun unregister() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.w(TAG, "unregister failed (already gone?): ${e.message}")
        } finally {
            registered = false
            initialReceived = false
        }
    }

    companion object {
        private const val TAG = "CallAudioRoute"
    }
}
