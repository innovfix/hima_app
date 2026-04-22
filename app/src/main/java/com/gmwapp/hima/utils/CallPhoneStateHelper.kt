package com.gmwapp.hima.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Observes the cellular phone state during an Agora VoIP call so the app can
 * mute its microphone when the user takes or places a GSM call and restore
 * audio when the cellular call ends.
 *
 * Uses [TelephonyCallback] on Android 12+ and the deprecated
 * [PhoneStateListener] on older versions. Requires the
 * `READ_PHONE_STATE` runtime permission on Android 6+; if the permission is
 * not granted this helper is a no-op and safely logs the skip.
 */
class CallPhoneStateHelper(
    private val context: Context,
    private val onCellularCallActive: () -> Unit,
    private val onCellularCallEnded: () -> Unit
) {

    private val telephonyManager: TelephonyManager =
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var phoneStateListener: PhoneStateListener? = null

    @Suppress("NewApi") // Guarded below.
    private var telephonyCallback: TelephonyCallback? = null

    private var wasActive = false

    @SuppressLint("MissingPermission")
    fun register() {
        if (!hasPhoneStatePermission()) {
            Log.w(TAG, "READ_PHONE_STATE not granted, skipping cellular interrupt detection")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleState(state)
                    }
                }
                telephonyCallback = cb
                telephonyManager.registerTelephonyCallback(context.mainExecutor, cb)
            } else {
                val l = object : PhoneStateListener() {
                    @Deprecated("Kept for API < 31")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleState(state)
                    }
                }
                phoneStateListener = l
                @Suppress("DEPRECATION")
                telephonyManager.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "register failed (SecurityException)", e)
        } catch (e: Exception) {
            Log.e(TAG, "register failed", e)
        }
    }

    fun unregister() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
                telephonyCallback = null
            } else {
                phoneStateListener?.let {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                }
                phoneStateListener = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "unregister failed", e)
        }
    }

    private fun handleState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!wasActive) {
                    wasActive = true
                    Log.d(TAG, "Cellular call active (state=$state) -> muting Agora")
                    try { onCellularCallActive() } catch (e: Exception) { Log.e(TAG, "callback threw", e) }
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (wasActive) {
                    wasActive = false
                    Log.d(TAG, "Cellular call ended -> restoring Agora")
                    try { onCellularCallEnded() } catch (e: Exception) { Log.e(TAG, "callback threw", e) }
                }
            }
        }
    }

    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CallPhoneState"
    }
}
