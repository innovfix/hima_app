package com.gmwapp.hima.agora.telecom

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.gmwapp.hima.R

/**
 * Registers a self-managed [PhoneAccount] and routes incoming VoIP calls through [TelecomManager].
 */
object HimaTelecomManager {
    private const val INCOMING_CALL_LOG_TAG = "HimaIncomingCall"
    const val PHONE_ACCOUNT_ID = "hima_voip_self_managed"

    @Volatile
    private var loggedPhoneAccountRegistered = false

    fun registerPhoneAccountIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val appContext = context.applicationContext
            val telecom = appContext.getSystemService(TelecomManager::class.java)
            if (telecom == null) {
                Log.w(INCOMING_CALL_LOG_TAG, "registerPhoneAccountIfNeeded: no TelecomManager")
                return
            }
            val component = ComponentName(appContext, HimaConnectionService::class.java)
            val handle = PhoneAccountHandle(component, PHONE_ACCOUNT_ID)

            val label = appContext.getString(R.string.app_name)
            val account = PhoneAccount.Builder(handle, label)
                .setCapabilities(
                    PhoneAccount.CAPABILITY_SELF_MANAGED or
                        PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                )
                .setShortDescription("Hima calls")
                .build()
            telecom.registerPhoneAccount(account)
            if (!loggedPhoneAccountRegistered) {
                loggedPhoneAccountRegistered = true
                Log.d(INCOMING_CALL_LOG_TAG, "registerPhoneAccount: registered self-managed phone account")
            }
        } catch (e: Exception) {
            Log.e(INCOMING_CALL_LOG_TAG, "registerPhoneAccount failed: ${e.message}", e)
        }
    }

    /**
     * @return true if [TelecomManager.addNewIncomingCall] completed without throwing (self-managed;
     *   system in-call UI is still app-owned / CallStyle).
     */
    fun tryAddIncomingCall(context: Context, extras: Bundle): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.d(INCOMING_CALL_LOG_TAG, "tryAddIncomingCall: skip SDK<26 (O)")
            return false
        }
        return try {
            val appContext = context.applicationContext
            registerPhoneAccountIfNeeded(appContext)
            val telecom = appContext.getSystemService(TelecomManager::class.java)
            if (telecom == null) {
                Log.w(INCOMING_CALL_LOG_TAG, "tryAddIncomingCall: no TelecomManager service")
                return false
            }
            val handle = PhoneAccountHandle(
                ComponentName(appContext, HimaConnectionService::class.java),
                PHONE_ACCOUNT_ID
            )
            // I039 — previously we bailed if TelephonyManager reported a cellular call in
            // progress. That was the wrong instinct: the whole reason to use self-managed
            // Telecom is so the system can coordinate a second incoming call (hold/switch/
            // end-and-answer) over an active SIM or VoIP call. Forwarding to Telecom is now
            // unconditional; HimaConnection.onShowIncomingCallUi launches our accept screen
            // and Telecom grants it permission to surface over an active call (the only
            // path that bypasses the "no full-screen intent during a call" restriction).
            telecom.addNewIncomingCall(handle, extras)
            Log.d(INCOMING_CALL_LOG_TAG, "tryAddIncomingCall: addNewIncomingCall invoked ok")
            true
        } catch (e: SecurityException) {
            Log.e(INCOMING_CALL_LOG_TAG, "tryAddIncomingCall: addNewIncomingCall SecurityException ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(INCOMING_CALL_LOG_TAG, "tryAddIncomingCall: exception ${e.message}", e)
            false
        }
    }

    /**
     * I039 — symmetric to [tryAddIncomingCall]. Lets the outgoing call path
     * (MaleCallConnectingActivity / FemaleCallConnectingActivity) register the
     * call with Telecom so the system can offer Hold & Answer / End & Answer
     * when a SIM or other VoIP call arrives mid-Hima-call.
     *
     * @return true on a successful placeCall dispatch. False on permission
     *   errors, missing TelecomManager, or any unexpected exception — callers
     *   should continue with the existing Agora flow; the only thing lost is
     *   the system-coordinated second-call UX.
     */
    fun tryPlaceOutgoingCall(context: Context, extras: Bundle): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.d(INCOMING_CALL_LOG_TAG, "tryPlaceOutgoingCall: skip SDK<26 (O)")
            return false
        }
        return try {
            val appContext = context.applicationContext
            registerPhoneAccountIfNeeded(appContext)
            val telecom = appContext.getSystemService(TelecomManager::class.java)
            if (telecom == null) {
                Log.w(INCOMING_CALL_LOG_TAG, "tryPlaceOutgoingCall: no TelecomManager service")
                return false
            }
            val handle = PhoneAccountHandle(
                ComponentName(appContext, HimaConnectionService::class.java),
                PHONE_ACCOUNT_ID
            )
            // tel: scheme is a placeholder — the Hima call has no PSTN number. The
            // peer is identified inside `extras` (sender id, channel name). Telecom
            // accepts any tel: URI for self-managed accounts; the URI itself is
            // never dialed.
            val callUri = Uri.fromParts("tel", "hima", null)
            val outgoingExtras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras)
            }
            telecom.placeCall(callUri, outgoingExtras)
            Log.d(INCOMING_CALL_LOG_TAG, "tryPlaceOutgoingCall: placeCall invoked ok")
            true
        } catch (e: SecurityException) {
            Log.e(INCOMING_CALL_LOG_TAG, "tryPlaceOutgoingCall: placeCall SecurityException ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(INCOMING_CALL_LOG_TAG, "tryPlaceOutgoingCall: exception ${e.message}", e)
            false
        }
    }

    /**
     * Tears down the self-managed [HimaConnection] still tracked as active (e.g. after app UI
     * accept/reject/hangup without going through system Telecom callbacks).
     */
    fun endActiveCall(reason: Int = DisconnectCause.LOCAL) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val conn = HimaConnectionService.activeConnection ?: return
            conn.setDisconnected(DisconnectCause(reason))
            conn.destroy()
            HimaConnectionService.activeConnection = null
            Log.d(INCOMING_CALL_LOG_TAG, "endActiveCall: destroyed connection reason=$reason")
        } catch (e: Exception) {
            Log.e(INCOMING_CALL_LOG_TAG, "endActiveCall: ${e.message}", e)
            HimaConnectionService.activeConnection = null
        }
    }

    fun markActive() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            HimaConnectionService.activeConnection?.setActive()
            Log.d(INCOMING_CALL_LOG_TAG, "markActive: setActive invoked")
        } catch (e: Exception) {
            Log.e(INCOMING_CALL_LOG_TAG, "markActive: ${e.message}", e)
        }
    }

    /**
     * Asks the active self-managed [HimaConnection] to route audio via the
     * Telecom API. Needed because on Samsung One UI / Android 16 the system
     * `CallAudioRouteController` keeps re-applying its preferred baseline
     * (usually Bluetooth when a headset is connected) and quietly overrides
     * [android.media.AudioManager.setCommunicationDevice] changes. The
     * Telecom route set from inside a self-managed [android.telecom.Connection]
     * is treated as authoritative by the OEM stack.
     *
     * No-op when no self-managed connection exists (e.g. outgoing calls the
     * app set up without going through Telecom) — the AudioManager-level
     * routing in [com.gmwapp.hima.utils.CallAudioRouter] still runs as a
     * fallback.
     */
    fun setAudioRoute(route: com.gmwapp.hima.utils.CallAudioRouter.AudioRoute) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val conn = HimaConnectionService.activeConnection
        if (conn == null) {
            Log.d("CallAudioRoute", "HimaTelecomManager.setAudioRoute($route) no active connection — skip")
            return
        }
        conn.applyRouteFromApp(route)
    }
}
