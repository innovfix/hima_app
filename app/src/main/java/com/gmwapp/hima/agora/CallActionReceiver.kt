package com.gmwapp.hima.agora

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity



class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_ACCEPT_CALL" -> {
                val callIntent = Intent(context, FemaleCallAcceptActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtras(intent.extras!!)
                }
                context.startActivity(callIntent)

            }
            "ACTION_REJECT_CALL" -> {
                val callId = intent.getIntExtra("CALL_ID", -1)
                Log.d("CallReceiver", "Call Rejected: $callId")
                // Handle call rejection logic here
            }
        }
    }
}

