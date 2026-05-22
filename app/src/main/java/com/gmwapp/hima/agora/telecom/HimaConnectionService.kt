package com.gmwapp.hima.agora.telecom

import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

/**
 * System [ConnectionService] entry for self-managed incoming Hima calls.
 */
class HimaConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        Log.d(INCOMING_CALL_LOG_TAG, "HimaConnectionService.onCreateIncomingConnection")
        val extras = request.extras ?: Bundle()
        val conn = HimaConnection(applicationContext, extras, isIncoming = true)
        activeConnection = conn
        Log.d(INCOMING_CALL_LOG_TAG, "HimaConnectionService: activeConnection assigned (incoming)")
        return conn
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ) {
        Log.e(INCOMING_CALL_LOG_TAG, "HimaConnectionService.onCreateIncomingConnectionFailed")
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
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
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        Log.d(INCOMING_CALL_LOG_TAG, "HimaConnectionService.onCreateOutgoingConnection")
        // Telecom nests our extras under EXTRA_OUTGOING_CALL_EXTRAS when placeCall
        // forwards them; fall back to the top-level bundle for forward-compat.
        val extras = request.extras?.getBundle(
            android.telecom.TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS
        ) ?: request.extras ?: Bundle()
        val conn = HimaConnection(applicationContext, extras, isIncoming = false)
        activeConnection = conn
        Log.d(INCOMING_CALL_LOG_TAG, "HimaConnectionService: activeConnection assigned (outgoing)")
        return conn
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ) {
        Log.e(INCOMING_CALL_LOG_TAG, "HimaConnectionService.onCreateOutgoingConnectionFailed")
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    companion object {
        private const val INCOMING_CALL_LOG_TAG = "HimaIncomingCall"

        @Volatile
        var activeConnection: HimaConnection? = null
    }
}
