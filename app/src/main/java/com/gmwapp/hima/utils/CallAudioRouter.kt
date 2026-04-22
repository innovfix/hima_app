package com.gmwapp.hima.utils

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Overrides Android/Agora audio routing so the Speaker toggle works when Bluetooth
 * or wired headsets are connected. [RtcEngine.setEnableSpeakerphone] alone is ignored
 * in that case; this uses [AudioManager.setCommunicationDevice] (API 31+) or legacy
 * speakerphone / Bluetooth SCO APIs.
 */
class CallAudioRouter(context: Context) {

    private val am: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var previousMode: Int = am.mode
    private var initialized = false

    fun init() {
        if (initialized) return
        previousMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        initialized = true
    }

    /** Force the built-in phone speaker even if BT/wired headset is connected. */
    fun forceSpeaker() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    val ok = am.setCommunicationDevice(speaker)
                    if (!ok) Log.w(TAG, "setCommunicationDevice(speaker) returned false")
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                }
            } else {
                @Suppress("DEPRECATION")
                if (am.isBluetoothScoOn) {
                    am.stopBluetoothSco()
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "forceSpeaker", e)
        }
    }

    /** Hand the route back to BT (if connected), wired, or earpiece. */
    fun useDefaultRoute() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                if (isBluetoothHeadsetConnected()) {
                    am.startBluetoothSco()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "useDefaultRoute", e)
        }
    }

    fun release() {
        if (!initialized) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            if (am.isBluetoothScoOn) {
                am.stopBluetoothSco()
            }
            am.mode = previousMode
        } catch (e: Exception) {
            Log.e(TAG, "release", e)
        } finally {
            initialized = false
        }
    }

    private fun isBluetoothHeadsetConnected(): Boolean =
        try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
        } catch (_: Exception) {
            false
        }

    companion object {
        private const val TAG = "CallAudioRouter"
    }
}
