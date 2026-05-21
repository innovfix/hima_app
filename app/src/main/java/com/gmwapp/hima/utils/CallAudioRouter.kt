package com.gmwapp.hima.utils

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    /**
     * Callback invoked on the main thread whenever the active output route may
     * have changed (wired headset plugged/unplugged, BT connected/disconnected).
     * Activities use this to reconcile the speaker icon with the real audio
     * route — without it the icon shows whatever the user last tapped, even
     * if hardware events silently re-routed the audio.
     */
    private var routeChangeListener: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var deviceCallback: AudioDeviceCallback? = null

    fun init() {
        if (initialized) return
        previousMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        registerDeviceCallback()
        initialized = true
    }

    /**
     * Register a listener that fires when wired/BT devices are plugged or
     * unplugged. Pass null to clear. Activities should set this in onResume /
     * after init() and clear it in onPause / release(). The listener runs on
     * the main thread.
     */
    fun setRouteChangeListener(listener: (() -> Unit)?) {
        routeChangeListener = listener
    }

    // A single Bluetooth headset connect typically fires onAudioDevicesAdded
    // twice in a row (A2DP profile, then SCO/BLE profile), and unplug events
    // can fire pairs as well. Invoking the listener for every event makes
    // the icon flicker through transient states (e.g. EARPIECE for ~50ms
    // before settling on BLUETOOTH). Debounce so the listener runs once
    // after the system has finished announcing the change.
    private val pendingRefresh = Runnable { routeChangeListener?.invoke() }
    private fun postDebouncedRefresh() {
        mainHandler.removeCallbacks(pendingRefresh)
        mainHandler.postDelayed(pendingRefresh, ROUTE_REFRESH_DEBOUNCE_MS)
    }

    // B050 race-killer. Agora's setEnableSpeakerphone posts to its own worker
    // thread, so its write to AudioManager.isSpeakerphoneOn can land AFTER
    // our setCommunicationDevice() call and silently revert the user's
    // choice ~50–150ms after the tap — which is what the "speaker toggle
    // lag" complaint actually was. After applying a route, schedule a single
    // delayed verify; if the OS-reported route diverged from intent, re-apply
    // once. Cancelled on a fresh toggle so rapid taps don't fight us.
    private var lastIntendedRoute: AudioRoute? = null
    private val verifyRunnable = Runnable {
        val intended = lastIntendedRoute ?: return@Runnable
        val actual = currentRoute()
        if (actual != intended) {
            Log.w(TAG, "verifyAndReapply: intended=$intended actual=$actual — reapplying")
            when (intended) {
                AudioRoute.SPEAKER -> forceSpeaker()
                AudioRoute.EARPIECE -> forceEarpiece()
                AudioRoute.BLUETOOTH -> forceBluetooth()
            }
        }
    }

    /**
     * Schedule a delayed verify of the audio route. Call this from the
     * activity immediately after `applyAudioRoute` so any async Agora-worker
     * write that races past our explicit routing gets reverted. Subsequent
     * calls cancel the previous pending verify.
     */
    fun verifyAndReapply(intended: AudioRoute) {
        lastIntendedRoute = intended
        mainHandler.removeCallbacks(verifyRunnable)
        mainHandler.postDelayed(verifyRunnable, VERIFY_REAPPLY_DELAY_MS)
    }

    private fun registerDeviceCallback() {
        if (deviceCallback != null) return
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                postDebouncedRefresh()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                postDebouncedRefresh()
            }
        }
        try {
            am.registerAudioDeviceCallback(cb, mainHandler)
            deviceCallback = cb
        } catch (e: Exception) {
            Log.e(TAG, "registerAudioDeviceCallback", e)
        }
    }

    private fun unregisterDeviceCallback() {
        val cb = deviceCallback ?: return
        try {
            mainHandler.removeCallbacks(pendingRefresh)
            am.unregisterAudioDeviceCallback(cb)
        } catch (e: Exception) {
            Log.e(TAG, "unregisterAudioDeviceCallback", e)
        } finally {
            deviceCallback = null
        }
    }

    /**
     * Force the built-in phone speaker even if BT/wired headset is connected.
     * Returns true on success, false if the OS rejected the route change —
     * which happens with a wired headset on Android <12 where the system
     * hard-routes to the wired output and `isSpeakerphoneOn` is silently
     * ignored. Callers should surface that to the user instead of flipping
     * the speaker icon to a state the audio path didn't actually reach.
     */
    @Synchronized
    fun forceSpeaker(): Boolean {
        // Skip if already on speaker. Avoids a redundant clear-then-set
        // cycle (which causes a brief audio dropout) when a hardware event
        // or stray tap re-requests the route we're already on.
        if (currentRoute() == AudioRoute.SPEAKER) return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Clear the current communication device first. Some OEM
                // builds (notably Samsung) leave a wired-headset route as
                // the active comm device, and setCommunicationDevice() then
                // returns false because "device already set". Clearing first
                // makes the speaker selection take effect.
                am.clearCommunicationDevice()
                val speaker = am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    val ok = am.setCommunicationDevice(speaker)
                    if (!ok) Log.w(TAG, "setCommunicationDevice(speaker) returned false; trying legacy flag")
                }
                // Belt-and-braces: also set the legacy flag for Agora's
                // internal routing which still reads it on some builds.
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                // Verify the OS actually accepted the route change — wired
                // headphones can silently win even on API 31+ on certain
                // OEM builds, so don't trust the call's return value alone.
                am.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                @Suppress("DEPRECATION")
                if (am.isBluetoothScoOn) {
                    am.stopBluetoothSco()
                }
                // Pre-Android 12 hard-routes audio to a wired headset in
                // MODE_IN_COMMUNICATION; no public API can override that.
                // Surface the rejection so the UI doesn't lie about state.
                if (isWiredHeadsetConnected()) {
                    Log.w(TAG, "forceSpeaker: wired headset on Android <12 — cannot override system routing")
                    return false
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn
            }
        } catch (e: Exception) {
            Log.e(TAG, "forceSpeaker", e)
            false
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

    /**
     * Route audio away from the speaker — to earpiece, wired headset, or
     * BT if connected (whatever the system picks as default). Returns true
     * if the speaker was successfully disengaged. On API 31+ the speaker
     * stays active until the comm device is cleared *and* a new one set,
     * so we always clear-then-set and verify with a read-back.
     */
    @Synchronized
    fun forceEarpiece(): Boolean {
        // Skip if speaker is already off — the user's actual intent. The
        // route may legitimately be EARPIECE, wired headset (also reported
        // as EARPIECE), or BLUETOOTH; all satisfy "stop using speaker".
        // Avoids a redundant clear-then-set dropout.
        if (currentRoute() != AudioRoute.SPEAKER) return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Same OEM quirk as forceSpeaker: setCommunicationDevice can
                // return false when another device is already set. Clearing
                // first ensures the new selection takes effect — and on its
                // own is enough to drop the speaker route, which is the
                // actual user intent when they tap "speaker off".
                am.clearCommunicationDevice()
                val earpiece = am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (earpiece != null) {
                    val ok = am.setCommunicationDevice(earpiece)
                    if (!ok) Log.w(TAG, "setCommunicationDevice(earpiece) returned false")
                }
                // Belt-and-braces: also clear the legacy flag in case Agora
                // re-reads it. setEnableSpeakerphone(false) elsewhere may
                // have raced ahead and left the flag on.
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                // Success = speaker is no longer the comm device. The actual
                // route may be earpiece OR wired headset; both satisfy the
                // user's "stop using speaker" intent.
                am.communicationDevice?.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                @Suppress("DEPRECATION")
                if (am.isBluetoothScoOn) {
                    am.stopBluetoothSco()
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                @Suppress("DEPRECATION")
                !am.isSpeakerphoneOn
            }
        } catch (e: Exception) {
            Log.e(TAG, "forceEarpiece", e)
            false
        }
    }

    /**
     * Force a connected Bluetooth SCO headset. Returns true on success.
     * No-op (false) if no BT comm device is available.
     */
    @Synchronized
    fun forceBluetooth(): Boolean {
        // Skip if already on BT. Avoids redundant dropouts when the user
        // re-selects BT in the picker.
        if (currentRoute() == AudioRoute.BLUETOOTH) return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
                val bt = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (bt == null) {
                    Log.w(TAG, "forceBluetooth: no BT communication device available")
                    return false
                }
                val ok = am.setCommunicationDevice(bt)
                if (!ok) Log.w(TAG, "setCommunicationDevice(bt) returned false")
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                am.communicationDevice?.type.let {
                    it == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                @Suppress("DEPRECATION")
                am.startBluetoothSco()
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn
            }
        } catch (e: Exception) {
            Log.e(TAG, "forceBluetooth", e)
            false
        }
    }

    /**
     * Whether any Bluetooth (SCO or A2DP) headset is currently connected to the
     * device. Public so UI code can decide whether to show the 3-way audio
     * picker vs a binary speaker toggle.
     */
    fun isBluetoothConnected(): Boolean = isBluetoothHeadsetConnected()

    enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH }

    /**
     * Best-effort read of the currently-active communication audio route.
     * Falls back to EARPIECE when the platform reports nothing specific —
     * that's also what Android routes to by default in MODE_IN_COMMUNICATION.
     */
    @Suppress("DEPRECATION")
    fun currentRoute(): AudioRoute {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dev = am.communicationDevice ?: return AudioRoute.EARPIECE
            return when (dev.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.SPEAKER
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioRoute.BLUETOOTH
                else -> AudioRoute.EARPIECE
            }
        }
        return when {
            am.isSpeakerphoneOn -> AudioRoute.SPEAKER
            am.isBluetoothScoOn -> AudioRoute.BLUETOOTH
            else -> AudioRoute.EARPIECE
        }
    }

    fun release() {
        if (!initialized) return
        try {
            unregisterDeviceCallback()
            routeChangeListener = null
            mainHandler.removeCallbacks(verifyRunnable)
            lastIntendedRoute = null
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

    /**
     * True when a wired (3.5mm / USB-C / USB) headset is currently plugged
     * in. Used by callers to decide between the binary speaker toggle and
     * surfacing a "speaker unavailable" message — on Android <12 the system
     * hard-routes to the wired output and the app cannot override it.
     */
    fun isWiredHeadsetConnected(): Boolean =
        try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        } catch (_: Exception) {
            false
        }

    companion object {
        private const val TAG = "CallAudioRouter"
        // 150ms is long enough to coalesce A2DP+SCO pair-events (typically
        // ~30–80ms apart) without delaying icon updates noticeably.
        private const val ROUTE_REFRESH_DEBOUNCE_MS = 150L
        // 250ms gives Agora's internal worker thread time to flush its own
        // setSpeakerphoneOn write before we verify. Shorter delays missed
        // late writes on slower devices; longer ones felt like UI lag.
        private const val VERIFY_REAPPLY_DELAY_MS = 250L
    }
}
