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
        val conn = HimaConnection(applicationContext, extras)
        activeConnection = conn
        Log.d(INCOMING_CALL_LOG_TAG, "HimaConnectionService: activeConnection assigned")
        return conn
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ) {
        Log.e(INCOMING_CALL_LOG_TAG, "HimaConnectionService.onCreateIncomingConnectionFailed")
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    companion object {
        private const val INCOMING_CALL_LOG_TAG = "HimaIncomingCall"

        @Volatile
        var activeConnection: HimaConnection? = null
    }
}
