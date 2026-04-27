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
        /**
         * Fired for every `type=message` push so chat-list screens (FriendsTabFragment,
         * HomeFragment, CreatorChatFragment, MainActivity badge) can update in-place
         * instead of waiting for the 30s poll or pull-to-refresh.
         *
         * Extras:
         * - `peer_id` (Int): peer who sent the message
         * - `last_message` (String, optional): preview text
         * - `message_type` (String, optional): `text`|`image`|`audio`|`video`|`file`
         */
        const val ACTION_CHAT_LIST_REFRESH = "com.gmwapp.hima.ACTION_CHAT_LIST_REFRESH"
        const val EXTRA_PEER_ID = "peer_id"
        const val EXTRA_LAST_MESSAGE = "last_message"
        const val EXTRA_MESSAGE_TYPE = "message_type"
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

            // If the user is already inside an Agora call, drop any server-side
            // OneSignal *incoming* call push before it can ring a second time. The
            // FCM CallStyle path already guards on `currentActivity`, but OneSignal
            // pushes are separate and would otherwise stack in the tray.
            //
            // Missed-call pushes are explicitly excluded — they arrive *after* a
            // call ends, so a tiny race where `isInActiveCall()` is still true
            // would otherwise eat the missed-call notification entirely.
            if (com.gmwapp.hima.BaseApplication.getInstance()?.isInActiveCall() == true &&
                isCallPush(event.notification) &&
                !isMissedCallPush(event.notification)
            ) {
                Log.d(TAG, "In active call — suppressing OneSignal incoming-call push")
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

            // Route call pushes through our shared helper so OneSignal posts the
            // same CallStyle / missed-call UI as the FCM path. Both helpers
            // call `event.preventDefault()` themselves on success, so anything
            // matching here short-circuits the chat-message branch below.
            if (maybeHandleMissedCall(context, event)) return
            if (maybeHandleIncomingCall(context, event)) return

            // Fold per-sender chat pushes into a single MessagingStyle notification.
            maybeHandleChatMessage(context, event)
        } catch (e: Exception) {
            Log.e(TAG, "DND check failed: ${e.message}")
            // On error, fall through and let the notification show normally
        }
    }

    /**
     * Detects "Missed call …" pushes from either a structured `type=missed_call`
     * payload or free-text title/body so server-side template variations are
     * still rendered with the rich custom UI.
     */
    private fun isMissedCallPush(notif: com.onesignal.notifications.IDisplayableNotification): Boolean {
        val data = notif.additionalData
        val type = data?.optString("type", "")?.lowercase().orEmpty()
        if (type == "missed_call" || type == "call_missed") return true
        val title = notif.title.orEmpty().lowercase()
        val body = notif.body.orEmpty().lowercase()
        return title.contains("missed call") || body.contains("missed call")
    }

    /**
     * If the push is an incoming-call push (and not a missed-call notice),
     * post our own [com.gmwapp.hima.utils.CallNotifications.showIncoming] and
     * suppress the OneSignal default. Returns true when handled.
     */
    private fun maybeHandleIncomingCall(
        context: Context,
        event: INotificationReceivedEvent
    ): Boolean {
        val data = event.notification.additionalData ?: return false
        if (!isCallPush(event.notification)) return false
        if (isMissedCallPush(event.notification)) return false

        val callType = firstNonEmpty(data, "callType", "call_type")
        val senderId = data.optInt("senderId", 0).takeIf { it > 0 }
            ?: data.optInt("sender_id", 0).takeIf { it > 0 }
            ?: data.optInt("user_id", 0)
        val callId = data.optInt("call_id", 0)
        val channelName = firstNonEmpty(data, "channelName", "channel_name") ?: "default_channel"
        val callerName = firstNonEmpty(data, "callerName", "sender_name", "name", "title")
            ?: event.notification.title?.trim().orEmpty()
        val callerImage = firstNonEmpty(data, "callerImage", "sender_image", "image", "avatar").orEmpty()

        if (senderId <= 0) return false

        val userData = com.gmwapp.hima.BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val isMale = userData?.gender == com.gmwapp.hima.constants.DConstants.MALE

        // Defer preventDefault until our custom CallStyle has actually posted.
        // Otherwise a throw inside showIncoming would leave the user with NO
        // notification at all.
        val ok = runCatching {
            com.gmwapp.hima.utils.CallNotifications.showIncoming(
                context,
                com.gmwapp.hima.utils.CallNotifications.IncomingPayload(
                    isMale = isMale,
                    callType = callType,
                    senderId = senderId,
                    callId = callId,
                    channelName = channelName,
                    callerName = callerName,
                    callerImage = callerImage
                )
            )
            true
        }.getOrElse {
            Log.e(TAG, "showIncoming threw senderId=$senderId: ${it.message}", it)
            false
        }
        if (!ok) {
            // Let OneSignal show its default heads-up so the user still sees something.
            return false
        }
        event.preventDefault()
        Log.d(TAG, "OneSignal call push -> CallStyle posted senderId=$senderId callType=$callType")
        return true
    }

    /**
     * If the push is a "missed call …" notice, post our own custom rich
     * notification (Call back / Message actions on a quieter channel) and
     * suppress the OneSignal default. Returns true when handled.
     */
    private fun maybeHandleMissedCall(
        context: Context,
        event: INotificationReceivedEvent
    ): Boolean {
        if (!isMissedCallPush(event.notification)) return false
        val data = event.notification.additionalData ?: return false

        val callType = firstNonEmpty(data, "callType", "call_type") ?: "audio"
        val senderId = data.optInt("senderId", 0).takeIf { it > 0 }
            ?: data.optInt("sender_id", 0).takeIf { it > 0 }
            ?: data.optInt("user_id", 0)
        val callerName = firstNonEmpty(data, "callerName", "sender_name", "name", "title")
            ?: event.notification.title?.removePrefix("Missed call from")?.trim().orEmpty()
        val callerImage = firstNonEmpty(data, "callerImage", "sender_image", "image", "avatar").orEmpty()

        if (senderId <= 0) return false

        // showMissed returns true only after successfully posting; if it threw,
        // we let OneSignal's default render so the user still sees *something*.
        val posted = runCatching {
            com.gmwapp.hima.utils.CallNotifications.showMissed(
                context,
                com.gmwapp.hima.utils.CallNotifications.MissedPayload(
                    callType = callType,
                    senderId = senderId,
                    callerName = callerName,
                    callerImage = callerImage
                )
            )
        }.getOrElse {
            Log.e(TAG, "showMissed threw senderId=$senderId: ${it.message}", it)
            false
        }
        if (!posted) return false
        event.preventDefault()
        Log.d(TAG, "OneSignal missed call push -> custom posted senderId=$senderId callType=$callType")
        return true
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
        val messageType = data.optString("message_type", "text").ifBlank { "text" }

        // Always tell the chat list / badge to refresh — this is cheap and
        // idempotent. The thread-level refresh below only fires when the chat
        // is actively open.
        sendChatListRefresh(context, peerId, text, messageType)

        // WhatsApp-style behaviour: if the user is already looking at the chat
        // for this peer, don't show a heads-up — broadcast a refresh signal so
        // the open activity can catch up via REST in case the Socket.IO event
        // was missed (reconnect gap, dropped event, etc.).
        // T11: pass context so the prefs-backed fallback works when the NSE runs in a separate process.
        if (ActiveChatTracker.isActiveFor(context, peerId)) {
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

        // H15: preventDefault BEFORE show — if show() throws, OneSignal's default
        // would otherwise also fire and the user would see two notifications for
        // the same message. We post a minimal fallback below in the catch block.
        event.preventDefault()
        try {
            ChatNotifications.show(context, peerId, peerName, peerImage, entries)
            Log.d(TAG, "Chat notif posted for peerId=$peerId (lines=${entries.size})")
        } catch (e: Exception) {
            Log.e(TAG, "Chat notif post failed for peerId=$peerId: ${e.message}")
            postFallbackNotification(context, peerId, peerName, text)
        }
    }

    /**
     * Fires [ACTION_CHAT_LIST_REFRESH] so any chat list / badge that is on screen
     * updates in-place. Safe to call from background threads; uses a package-scoped
     * broadcast so it's never visible outside the app.
     */
    private fun sendChatListRefresh(
        context: Context,
        peerId: Int,
        lastMessage: String,
        messageType: String
    ) {
        val intent = Intent(ACTION_CHAT_LIST_REFRESH)
            .setPackage(context.packageName)
            .putExtra(EXTRA_PEER_ID, peerId)
            .putExtra(EXTRA_LAST_MESSAGE, lastMessage)
            .putExtra(EXTRA_MESSAGE_TYPE, messageType)
        context.sendBroadcast(intent)
    }

    /**
     * Last-resort heads-up if [ChatNotifications.show] throws after we've already
     * called preventDefault on the OneSignal display path. Plain-text only; no
     * MessagingStyle dependencies that could throw the same way.
     */
    private fun postFallbackNotification(
        context: android.content.Context,
        peerId: Int,
        peerName: String,
        text: String
    ) {
        try {
            val builder = androidx.core.app.NotificationCompat.Builder(
                context,
                "f49d2168-bc20-4a4b-a984-a7abffe0d6aa" // same channel id as ChatNotifications
            )
                .setSmallIcon(com.gmwapp.hima.R.drawable.logo)
                .setContentTitle(peerName.ifBlank { "New message" })
                .setContentText(text)
                .setAutoCancel(true)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(ChatNotifications.notifIdFor(peerId), builder.build())
        } catch (t: Throwable) {
            Log.w(TAG, "Fallback notif also failed for peerId=$peerId: ${t.message}")
        }
    }

    /**
     * Conservative heuristic for "this push is about an incoming call."
     * Matches either (a) structured `additionalData` keys the backend attaches
     * to call pushes, or (b) free-text titles/bodies when the payload is less
     * structured. Anything else (wallet, friend request, chat, Ludo, etc.) is
     * left alone.
     */
    private fun isCallPush(notif: com.onesignal.notifications.IDisplayableNotification): Boolean {
        val data = notif.additionalData
        if (data != null) {
            val keys = arrayOf("callType", "channelName", "call_id", "senderId")
            if (keys.any { data.has(it) && !data.isNull(it) }) return true
            val type = data.optString("type", "").lowercase()
            if (type.startsWith("call") || type.contains("incoming")) return true
        }
        val title = notif.title.orEmpty().lowercase()
        val body = notif.body.orEmpty().lowercase()
        val haystack = "$title $body"
        return haystack.contains("video call from") ||
            haystack.contains("audio call from") ||
            haystack.contains("wants to talk to you") ||
            haystack.contains("incoming call")
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
