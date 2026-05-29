package com.gmwapp.hima.agora.telecom

import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.util.Log

/**
 * System [ConnectionService] entry for self-managed incoming Hima calls.
 *
 * v1106 (2026-05-29) — every callback wrapped in try-catch and parameters
 * marked nullable. Some OEM Android implementations (Xiaomi, OnePlus, Vivo,
 * Realme observed in Crashlytics for v1079-v1105) violate the documented
 * @NonNull contract and pass `null` for ConnectionRequest, which Kotlin's
 * runtime null check converted into a fatal NullPointerException
 * (76 affected users on v1105 alone). Returning a failed connection on
 * bad input + swallowing any other error keeps the user's call attempt
 * gracefully degrading (no Telecom hold/switch support that one call)
 * instead of force-closing the app.
 */
class HimaConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        return try {
            Log.d(INCOMING_CALL_LOG_TAG, "HimaConnectionService.onCreateIncomingConnection")
            if (request == null) {
                Log.w(INCOMING_CALL_LOG_TAG, "onCreateIncomingConnection: null request from OS — returning failed connection")
                return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
            }
            val extras = request.extras ?: Bundle()
            val conn = HimaConnection(applicationContext, extras, isIncoming = true)
            activeConnection = conn
            Log.d(INCOMING_CALL_LOG_TAG, "HimaConnectionService: activeConnection assigned (incoming)")
            conn
        } catch (t: Throwable) {
            Log.e(INCOMING_CALL_LOG_TAG, "onCreateIncomingConnection threw — returning failed connection", t)
            Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
        }
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        try {
            Log.e(INCOMING_CALL_LOG_TAG, "HimaConnectionService.onCreateIncomingConnectionFailed")
            super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        } catch (t: Throwable) {
            Log.e(INCOMING_CALL_LOG_TAG, "onCreateIncomingConnectionFailed threw — swallowing", t)
        }
    }

    /**
     * I039 — symmetric outgoing path. When the connecting activity calls
     * [com.gmwapp.hima.agora.telecom.HimaTelecomManager.tryPlaceOutgoingCall],
     * Telecom routes the placeCall through here. We bundle the original extras
     * (set by the caller via PhoneAccount.EXTRA_CUSTOM_EXTRAS) into a HimaConnection
     * marked outgoing so Telecom now knows the user is in a Hima call. A subsequent
     * SIM / WhatsApp incoming call then triggers the system second-call UI instead
     * of ringing on top of Agora audio.
     */
    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        return try {
            Log.d(INCOMING_CALL_LOG_TAG, "HimaConnectionService.onCreateOutgoingConnection")
            if (request == null) {
                Log.w(INCOMING_CALL_LOG_TAG, "onCreateOutgoingConnection: null request from OS — returning failed connection")
                return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
            }
            // Telecom nests our extras under EXTRA_OUTGOING_CALL_EXTRAS when placeCall
            // forwards them; fall back to the top-level bundle for forward-compat.
            val extras = request.extras?.getBundle(
                android.telecom.TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS
            ) ?: request.extras ?: Bundle()
            val conn = HimaConnection(applicationContext, extras, isIncoming = false)
            activeConnection = conn
            Log.d(INCOMING_CALL_LOG_TAG, "HimaConnectionService: activeConnection assigned (outgoing)")
            conn
        } catch (t: Throwable) {
            Log.e(INCOMING_CALL_LOG_TAG, "onCreateOutgoingConnection threw — returning failed connection", t)
            Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
        }
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        try {
            Log.e(INCOMING_CALL_LOG_TAG, "HimaConnectionService.onCreateOutgoingConnectionFailed")
            super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        } catch (t: Throwable) {
            Log.e(INCOMING_CALL_LOG_TAG, "onCreateOutgoingConnectionFailed threw — swallowing", t)
        }
    }

    companion object {
        private const val INCOMING_CALL_LOG_TAG = "HimaIncomingCall"

        @Volatile
        var activeConnection: HimaConnection? = null
    }
}
