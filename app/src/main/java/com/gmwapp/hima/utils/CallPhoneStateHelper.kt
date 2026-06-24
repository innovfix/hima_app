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
            // Only a *real incoming* cellular call is a genuine interrupt, and it
            // always passes through RINGING first. We deliberately do NOT key off
            // CALL_STATE_OFFHOOK here: while a Hima call is up, our own
            // self-managed VoIP connection (HimaConnection, set ACTIVE via
            // Telecom) is reported as OFFHOOK by the device on some OEMs
            // (Samsung observed). Treating that OFFHOOK as a cellular call fired
            // the "On hold — phone call in progress" banner (and the peer's
            // "‹Name› is on hold" banner) and muted the mic on EVERY Hima call
            // even though no SIM call existed. RINGING is unambiguous: our active
            // VoIP connection is ACTIVE, never RINGING. (A SIM call that gets
            // answered still mutes correctly — RINGING precedes OFFHOOK; and
            // audio-focus loss is a second safety net.) Callbacks are idempotent
            // downstream, so firing once per RINGING/IDLE edge is safe.
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "Incoming cellular call ringing -> muting Agora")
                try { onCellularCallActive() } catch (e: Exception) { Log.e(TAG, "callback threw", e) }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Cellular state idle -> restoring Agora")
                try { onCellularCallEnded() } catch (e: Exception) { Log.e(TAG, "callback threw", e) }
            }
            // CALL_STATE_OFFHOOK intentionally ignored (see above).
        }
    }

    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CallPhoneState"

        /**
         * One-shot read of the current cellular phone state. Returns true if a
         * SIM call is currently ringing or off-hook, so callers can refuse to
         * start a Hima call that would silently lose audio to the telephony
         * stack. Treats "no permission" as not-busy so the gate doesn't break
         * call flow on devices/builds without READ_PHONE_STATE. (B067)
         */
        @SuppressLint("MissingPermission")
        fun isCellularCallBusy(context: Context): Boolean {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
            val tm = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager ?: return false
            return try {
                val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    tm.callStateForSubscription
                } else {
                    @Suppress("DEPRECATION")
                    tm.callState
                }
                state == TelephonyManager.CALL_STATE_RINGING ||
                    state == TelephonyManager.CALL_STATE_OFFHOOK
            } catch (e: SecurityException) {
                Log.w(TAG, "isCellularCallBusy: SecurityException, treating as not busy", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "isCellularCallBusy threw, treating as not busy", e)
                false
            }
        }
    }
}
