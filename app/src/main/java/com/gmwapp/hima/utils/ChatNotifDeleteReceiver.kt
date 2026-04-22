package com.gmwapp.hima.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fired when the user swipes away a chat MessagingStyle notification. Clears the
 * per-peer rolling text stack so the next incoming push starts a fresh conversation
 * instead of reviving previously-dismissed lines.
 */
class ChatNotifDeleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val peerId = intent.getIntExtra(EXTRA_PEER_ID, -1)
        if (peerId <= 0) return
        Log.d(TAG, "Swipe-dismiss — clearing chat notif stack for peerId=$peerId")
        ChatNotificationStore.clear(context, peerId)
    }

    companion object {
        const val EXTRA_PEER_ID = "peer_id"
        private const val TAG = "ChatNotifDelete"
    }
}
