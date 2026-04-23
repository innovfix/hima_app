package com.gmwapp.hima.onesignal

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gmwapp.hima.utils.ActiveChatTracker
import com.gmwapp.hima.utils.ChatNotificationStore
import com.gmwapp.hima.utils.ChatNotifications
import com.gmwapp.hima.utils.DPreferences
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension
import org.json.JSONObject

/**
 * OneSignal Notification Service Extension.
 *
 * This is invoked by the OneSignal SDK BEFORE a push notification is displayed,
 * even when the app process is killed. We use it to suppress notifications
 * when the user has Do Not Disturb (DND) enabled and the dnd_until timestamp
 * has not yet passed.
 *
 * We also hijack `type == "message"` pushes so multiple chats from the same
 * sender collapse into a single WhatsApp-style MessagingStyle notification
 * (one stable id per peer, last N lines stacked) instead of spamming the tray
 * with N separate heads-ups.
 */
class OneSignalNotificationServiceExtension : INotificationServiceExtension {

    private val TAG = "OneSignalNSE_DND"

    companion object {
        const val ACTION_CHAT_REFRESH = "com.gmwapp.hima.ACTION_CHAT_REFRESH"
    }

    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        try {
            val context: Context = event.context
            val prefs = DPreferences(context)
            val userData = prefs.getUserData()

            if (isDndActive(userData)) {
                Log.d(TAG, "DND is active — suppressing OneSignal notification")
                // Returning null prevents the notification from being shown
                event.preventDefault()
                return
            }

            // Save notification_id + receive timestamp for conversion tracking.
            // Used later when user opens the app directly (without tapping the notification).
            try {
                val notifId = event.notification.additionalData?.optInt("notification_id", 0) ?: 0
                if (notifId > 0) {
                    context.getSharedPreferences("notif_track", Context.MODE_PRIVATE).edit()
                        .putInt("last_notif_id", notifId)
                        .putLong("last_notif_time", System.currentTimeMillis())
                        .putBoolean("last_notif_counted", false)
                        .apply()
                    Log.d(TAG, "Saved last_notif_id=$notifId for conversion tracking")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save last_notif_id: ${e.message}")
            }

            // Fold per-sender chat pushes into a single MessagingStyle notification.
            maybeHandleChatMessage(context, event)
        } catch (e: Exception) {
            Log.e(TAG, "DND check failed: ${e.message}")
            // On error, fall through and let the notification show normally
        }
    }

    /**
     * If the payload is a chat message, append to the per-peer store and post a
     * MessagingStyle notification ourselves, then tell OneSignal to skip its default
     * display. Anything else (calls, friend requests, warnings, Ludo, etc.) falls
     * through untouched.
     */
    private fun maybeHandleChatMessage(context: Context, event: INotificationReceivedEvent) {
        val data = event.notification.additionalData ?: return
        val type = data.optString("type", "")
        if (type != "message") return

        val peerId = parsePeerId(data)
        if (peerId <= 0) return

        val text = event.notification.body.orEmpty().trim()
        if (text.isBlank()) return

        // WhatsApp-style behaviour: if the user is already looking at the chat
        // for this peer, don't show a heads-up — broadcast a refresh signal so
        // the open activity can catch up via REST in case the Socket.IO event
        // was missed (reconnect gap, dropped event, etc.).
        if (ActiveChatTracker.isActiveFor(peerId)) {
            Log.d(TAG, "chat visible for peerId=$peerId — suppressing heads-up, broadcasting refresh")
            val refresh = Intent(ACTION_CHAT_REFRESH)
                .setPackage(context.packageName)
                .putExtra("peer_id", peerId)
            context.sendBroadcast(refresh)
            event.preventDefault()
            return
        }

        // Fall back to previously-seen metadata if this follow-up push omits name/image.
        val (storedName, storedImage) = ChatNotificationStore.getMeta(context, peerId)
        val peerName = firstNonEmpty(
            data, "user_name", "sender_name", "name", "username", "title"
        ) ?: event.notification.title?.trim()?.takeIf { it.isNotEmpty() } ?: storedName
        val peerImage = firstNonEmpty(
            data, "user_image", "image", "image_url", "profile_image", "sender_image", "avatar"
        ) ?: storedImage.orEmpty()

        ChatNotificationStore.saveMeta(context, peerId, peerName, peerImage)
        val entries = ChatNotificationStore.append(context, peerId, text, System.currentTimeMillis())

        try {
            ChatNotifications.show(context, peerId, peerName, peerImage, entries)
            event.preventDefault() // replace OneSignal's default heads-up
            Log.d(TAG, "Chat notif posted for peerId=$peerId (lines=${entries.size})")
        } catch (e: Exception) {
            Log.e(TAG, "Chat notif post failed for peerId=$peerId: ${e.message}")
            // On failure don't preventDefault — let OneSignal show its default.
        }
    }

    /** OneSignal payload keys vary by server; try the usual peer-id aliases. */
    private fun parsePeerId(data: JSONObject): Int {
        val keys = arrayOf("user_id", "sender_id", "from_user_id", "senderId", "sender_user_id", "peer_id")
        for (key in keys) {
            if (!data.has(key) || data.isNull(key)) continue
            val id = when (val raw = data.opt(key)) {
                is Number -> raw.toInt()
                else -> data.optString(key, "").trim().toIntOrNull() ?: 0
            }
            if (id > 0) return id
        }
        return -1
    }

    private fun firstNonEmpty(data: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val s = data.optString(key, "").trim()
            if (s.isNotEmpty()) return s
        }
        return null
    }

    private fun isDndActive(userData: com.gmwapp.hima.retrofit.responses.UserData?): Boolean {
        if (userData == null) return false
        if ((userData.dnd_enabled ?: 0) != 1) return false
        val until = userData.dnd_until ?: return false
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val expiry = sdf.parse(until) ?: return false
            expiry.time > System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse dnd_until=$until: ${e.message}")
            false
        }
    }
}
