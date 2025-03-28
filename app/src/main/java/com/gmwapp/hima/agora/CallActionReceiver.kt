package com.gmwapp.hima.agora

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        val callType = intent?.getStringExtra("CALL_TYPE")
        val senderId = intent?.getIntExtra("SENDER_ID", -1) ?: -1
        val channelName = intent?.getStringExtra("CHANNEL_NAME")
        val callId = intent?.getStringExtra("CALL_ID")

        Log.d("CallActionReceiver", "Action: $action, Sender: $senderId")

        if (action == "ACTION_ACCEPT_CALL" || action == "ACTION_REJECT_CALL") {
            val callIntent = Intent(context, FemaleCallAcceptActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("CALL_TYPE", callType)
                putExtra("SENDER_ID", senderId)
                putExtra("CHANNEL_NAME", channelName)
                putExtra("CALL_ID", callId)
                putExtra("CALL_STATUS", if (action == "ACTION_ACCEPT_CALL") "accepted" else "rejected")
            }
            context?.startActivity(callIntent)

            // Stop the service after user action
            val serviceIntent = Intent(context, FcmCallService::class.java)
            context?.stopService(serviceIntent)
        }
    }
}
