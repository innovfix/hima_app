package com.gmwapp.hima.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Listens for Bluetooth SCO headset connect / disconnect events during a call
 * and notifies the hosting activity so it can swap between the binary speaker
 * toggle and the 3-way audio-route picker.
 *
 * Uses [BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED] — the broad
 * [BluetoothAdapter.ACTION_STATE_CHANGED] would also fire on plain
 * "Bluetooth on/off" toggles which are irrelevant to call audio routing.
 *
 * No permissions required at runtime on Android 12+ because the receiver is
 * exempt from BLUETOOTH_CONNECT when the broadcast carries no device info that
 * we read out. We only care about the connection state transition itself.
 */
class BluetoothCallWatcher(
    private val context: Context,
    private val onChange: (connected: Boolean) -> Unit
) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) return
            val state = intent.getIntExtra(
                BluetoothHeadset.EXTRA_STATE,
                BluetoothProfile.STATE_DISCONNECTED
            )
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "BT headset CONNECTED")
                    safeInvoke(true)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "BT headset DISCONNECTED")
                    safeInvoke(false)
                }
                // STATE_CONNECTING / STATE_DISCONNECTING are transient — ignore.
            }
        }
    }
    private var registered = false

    fun register() {
        if (registered) return
        try {
            val filter = IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            context.registerReceiver(receiver, filter)
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
        }
    }

    private fun safeInvoke(connected: Boolean) {
        try {
            onChange(connected)
        } catch (e: Exception) {
            Log.e(TAG, "onChange callback threw", e)
        }
    }

    companion object {
        // Unified tag so `adb logcat -s CallAudioRoute` captures the whole
        // audio-routing lifecycle (router, watcher, activity) in one stream.
        private const val TAG = "CallAudioRoute"
    }
}
