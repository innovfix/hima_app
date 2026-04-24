package com.gmwapp.hima.agora.telecom

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
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
            if (!isCellularIdle(appContext)) {
                Log.d(INCOMING_CALL_LOG_TAG, "tryAddIncomingCall: cellular not idle, skip addNewIncomingCall")
                return false
            }
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

    private fun isCellularIdle(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return true
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return telephony.callState == TelephonyManager.CALL_STATE_IDLE
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
