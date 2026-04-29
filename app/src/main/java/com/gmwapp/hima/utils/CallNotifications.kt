package com.gmwapp.hima.utils

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.CallActionReceiver
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity
import com.gmwapp.hima.agora.female.FemaleCallConnectingActivity
import com.gmwapp.hima.agora.male.MaleCallAcceptActivity
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.constants.DConstants

/**
 * Single source of truth for both incoming and missed call notifications.
 *
 * - [showIncoming] posts a `NotificationCompat.CallStyle.forIncomingCall`
 *   heads-up identical to the FCM path so an OneSignal incoming-call push
 *   renders the same Answer/Decline UI as the FCM-driven one.
 * - [showMissed] posts a quieter rich notification with `Call back` + `Message`
 *   actions on a dedicated default-importance channel (vibrate, no ringer).
 */
object CallNotifications {

    private const val TAG = "HimaIncomingCall"

    /** Mirrors [com.gmwapp.hima.agora.MyFirebaseMessagingService.CALLS_NOTIFICATION_CHANNEL_ID]. */
    private const val CALLS_NOTIFICATION_CHANNEL_ID = "calls_v3"
    private const val INCOMING_CALL_NOTIFICATION_ID = 1

    /**
     * Missed-call notification re-uses the chat channel + group so it renders
     * inside the WhatsApp-style "Conversations" section on Android 11+ next
     * to actual chat messages from the same peer.
     *
     * Mirrors `com.gmwapp.hima.utils.ChatNotifications` constants.
     */
    private const val CHAT_NOTIFICATION_CHANNEL_ID = "f49d2168-bc20-4a4b-a984-a7abffe0d6aa"
    private const val CHAT_GROUP_KEY = "chat_messages"

    /** Legacy channel id; kept for reference. No longer used by [showMissed]. */
    private const val MISSED_CALLS_NOTIFICATION_CHANNEL_ID = "missed_calls_v1"

    /** OR'd with peerId so missed-call ids don't collide with chat (`0x40000000`) or call (`0x00000001`). */
    private const val MISSED_CALL_ID_MASK = 0x60000000

    data class IncomingPayload(
        val isMale: Boolean,
        val callType: String?,        // "audio" / "video"
        val senderId: Int,
        val callId: Int,
        val channelName: String,
        val callerName: String,
        val callerImage: String?
    )

    data class MissedPayload(
        val callType: String?,        // "audio" / "video"
        val senderId: Int,
        val callerName: String,
        val callerImage: String?,
        /**
         * `true` when [senderId] was synthesised from the caller name because
         * the OneSignal push didn't carry a real peer id. Used by [showMissed]
         * to route the tap to MainActivity (instead of a wrong chat thread)
         * and to skip the conversation shortcut.
         */
        val isSynthetic: Boolean = false
    )

    fun showIncoming(context: Context, payload: IncomingPayload) {
        ensureCallsChannel(context)
        val chImp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.getNotificationChannel(CALLS_NOTIFICATION_CHANNEL_ID)?.importance
        } else null
        Log.d(
            TAG,
            "showIncoming: begin isMale=${payload.isMale} callId=${payload.callId} senderId=${payload.senderId} calls_v3_importance=$chImp"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "showIncoming: POST_NOTIFICATIONS granted=$granted")
        }

        val isMale = payload.isMale
        val callType = payload.callType
        val senderId = payload.senderId
        val callId = payload.callId
        val channelName = payload.channelName
        val receiverName = payload.callerName
        val receiverImg = payload.callerImage.orEmpty()

        val targetClass = if (isMale) MaleCallAcceptActivity::class.java else FemaleCallAcceptActivity::class.java
        val contentReq = if (isMale) 201 else 101
        val acceptAction = if (isMale) "ACTION_ACCEPT_CALL_MALE" else "ACTION_ACCEPT_CALL"
        val rejectAction = if (isMale) "ACTION_REJECT_CALL_MALE" else "ACTION_REJECT_CALL"
        val acceptReq = if (isMale) 202 else 102
        val rejectReq = if (isMale) 203 else 103

        val tapIntent = Intent(context, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
            putExtra("Caller_NAME", receiverName)
            putExtra("Caller_Image", receiverImg)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            contentReq,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = acceptAction
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
        }
        val acceptPi = PendingIntent.getBroadcast(
            context,
            acceptReq,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = rejectAction
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
        }
        val rejectPi = PendingIntent.getBroadcast(
            context,
            rejectReq,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder()
            .setName(receiverName)
            .setImportant(true)
            .build()

        fun buildNotification(person: Person): Notification {
            return NotificationCompat.Builder(context, CALLS_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        person,
                        rejectPi,
                        acceptPi
                    )
                )
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentPi)
                .setFullScreenIntent(contentPi, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(35_000L)
                .addPerson(person)
                .build()
        }

        val notifTag = callId.toString()
        try {
            Log.d(
                TAG,
                "showIncoming: posting notify tag=$notifTag id=$INCOMING_CALL_NOTIFICATION_ID channel=$CALLS_NOTIFICATION_CHANNEL_ID"
            )
            NotificationManagerCompat.from(context).notify(
                notifTag,
                INCOMING_CALL_NOTIFICATION_ID,
                buildNotification(caller)
            )
            Log.d(
                TAG,
                "showIncoming: CallStyle notification posted (isMale=$isMale, tag=$notifTag, id=$INCOMING_CALL_NOTIFICATION_ID)"
            )

            // Async avatar refresh — re-notify with bitmap once Glide resolves.
            Glide.with(context.applicationContext)
                .asBitmap()
                .load(receiverImg)
                .apply(RequestOptions.circleCropTransform())
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        val currentTag = BaseApplication.getInstance()?.getLastIncomingCallTag()
                        if (currentTag != notifTag) {
                            Log.d(TAG, "avatar refresh skipped: call $notifTag no longer pending (current=$currentTag)")
                            return
                        }
                        val personWithIcon = Person.Builder()
                            .setName(receiverName)
                            .setImportant(true)
                            .setIcon(IconCompat.createWithBitmap(resource))
                            .build()
                        NotificationManagerCompat.from(context.applicationContext).notify(
                            notifTag,
                            INCOMING_CALL_NOTIFICATION_ID,
                            buildNotification(personWithIcon)
                        )
                        Log.d(TAG, "showIncoming: refreshed with caller avatar bitmap")
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        } catch (e: SecurityException) {
            Log.e(TAG, "showIncoming: SecurityException ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "showIncoming: Exception ${e.message}", e)
        }
    }

    /**
     * Posts a missed-call notification that visually matches a received chat
     * message: `NotificationCompat.MessagingStyle` on the chat channel, with
     * the caller as a `Person` (avatar + name) and a single message line of
     * "Missed audio call. Tap to call back." (or video).
     *
     * Tap starts a callback ([MaleCallConnectingActivity] / [FemaleCallConnectingActivity])
     * for that peer when we have a real peer id; synthetic id routes to [MainActivity].
     * There are no inline action buttons — the heads-up should be indistinguishable
     * from a chat message notification so it lands inside the Conversations section on Android 11+.
     *
     * @return `true` on a successful `notify`, `false` if anything threw or
     *   the user revoked POST_NOTIFICATIONS.
     */
    fun showMissed(context: Context, payload: MissedPayload): Boolean {
        // Defensive: callers come from OneSignal / FCM payloads with varying
        // shapes. Compute safe values up-front so a downstream Person/Icon
        // call can't throw on empty / null fields.
        // Also strip any leaked "Missed call from " prefix from upstream paths
        // so the avatar's first-letter (e.g. "K" for "Kishore12") never gets
        // computed off the literal title string.
        val rawName = payload.callerName.trim()
        val cleanedName = rawName
            .removePrefix("Missed call from ").removePrefix("missed call from ")
            .removePrefix("Missed call from").removePrefix("missed call from")
            .removeSuffix("…").trim()
        val safeName = cleanedName.takeIf { it.isNotBlank() } ?: "Caller"
        val safeCallType = (payload.callType ?: "audio").lowercase()
        val senderId = payload.senderId
        val callerImage = payload.callerImage.orEmpty()

        val firstLetter = safeName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Log.d(
            "MissedCallDiag",
            "showMissed avatar safeName=$safeName firstLetter=$firstLetter rawName=\"$rawName\" " +
                "imgBlank=${callerImage.isBlank()} synthetic=${payload.isSynthetic}"
        )
        Log.d(
            TAG,
            "showMissed begin senderId=$senderId callType=$safeCallType nameLen=${payload.callerName.length} imgBlank=${callerImage.isBlank()}"
        )

        return runCatching {
            // 1. Person + bitmap — same fallback ChatNotifications.show uses.
            val remoteBitmap: Bitmap? = runCatching {
                if (callerImage.isBlank()) null else loadBitmap(context, callerImage)
            }.getOrNull()
            val peerBitmap: Bitmap = remoteBitmap ?: AvatarBitmap.circleWithInitial(safeName)
            val peerIcon: IconCompat = IconCompat.createWithBitmap(peerBitmap)
            val peerPerson = Person.Builder()
                .setName(safeName)
                .setKey(senderId.toString())
                .setIcon(peerIcon)
                .build()

            // 2. "Me" person — MessagingStyle requires a non-empty name.
            val myUserData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            val myDisplayName = myUserData?.name?.takeIf { it.isNotBlank() } ?: "You"
            val mePerson = Person.Builder()
                .setName(myDisplayName)
                .setKey(myUserData?.id?.toString() ?: "me")
                .build()

            // 3. MessagingStyle with the missed-call line as the only message.
            val style = NotificationCompat.MessagingStyle(mePerson)
                .setGroupConversation(false)
            val bodyText = context.getString(
                if (safeCallType == "video") R.string.missed_call_body_video
                else R.string.missed_call_body_audio
            )
            style.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    bodyText,
                    System.currentTimeMillis(),
                    peerPerson
                )
            )

            // 4. Tap intent. Real senderId -> start call-connecting (callback).
            //    Synthetic id (push didn't carry a peer id) -> open MainActivity
            //    so we don't start a call with a bogus peer id.
            val openIntent = if (payload.isSynthetic) {
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("FROM_MISSED_CALL", true)
                }
            } else {
                val myUser = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                val callbackClass = if (myUser?.gender == DConstants.FEMALE) {
                    FemaleCallConnectingActivity::class.java
                } else {
                    MaleCallConnectingActivity::class.java
                }
                Intent(context, callbackClass).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(DConstants.CALL_TYPE, safeCallType)
                    putExtra(DConstants.RECEIVER_ID, senderId)
                    putExtra(DConstants.RECEIVER_NAME, safeName)
                    putExtra(DConstants.CALL_ID, 0)
                    putExtra(DConstants.IMAGE, callerImage)
                    putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
                    putExtra(
                        DConstants.TEXT,
                        context.getString(R.string.wait_user_hint, safeName)
                    )
                    putExtra("FROM_MISSED_CALL", true)
                }
            }
            val contentPi = PendingIntent.getActivity(
                context,
                senderId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 5. Conversation shortcut: only push when we have a real peer id,
            //    since the shortcut intent must match the tap (call-connecting). For
            //    synthetic-id pushes we skip this and rely on setLargeIcon
            //    below for the avatar — the row still looks like a chat
            //    notification, just isn't promoted to the Conversations
            //    section on Android 11+.
            val shortcutId = if (payload.isSynthetic) null else "chat_peer_$senderId"
            val shortcutPushed = if (shortcutId != null) {
                val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                    .setShortLabel(safeName)
                    .setLongLabel(safeName)
                    .setIntent(openIntent)
                    .setIcon(peerIcon)
                    .setPerson(peerPerson)
                    .setLongLived(true)
                    .setCategories(setOf("android.shortcut.conversation"))
                    .build()
                val alreadyOnDisk = runCatching {
                    ShortcutManagerCompat.getDynamicShortcuts(context).any { it.id == shortcutId }
                }.getOrDefault(false)
                if (alreadyOnDisk) {
                    true
                } else {
                    runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
                        .map { true }
                        .getOrElse {
                            Log.w(TAG, "showMissed: pushDynamicShortcut failed: ${it.message}")
                            false
                        }
                }
            } else {
                false
            }

            // 6. Builder — chat channel + group so the row sits next to actual
            //    chat notifications. Distinct id from chat (`0x40000000`) so a
            //    later chat message doesn't replace the missed-call line.
            val notifId = MISSED_CALL_ID_MASK or (senderId and 0x0FFFFFFF)
            // CATEGORY_MESSAGE (not CATEGORY_MISSED_CALL) so Android renders this
            // identically to a chat heads-up — same Conversations-section row,
            // same bundling under the chat group summary, no Android default
            // missed-call card. The body text still says "Missed audio/video
            // call …" so the user sees what happened.
            val builder = NotificationCompat.Builder(context, CHAT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.logo) // matches ChatNotifications
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPi)
                .setGroup(CHAT_GROUP_KEY)
            if (shortcutId != null) {
                builder.setShortcutId(shortcutId)
                builder.setLocusId(LocusIdCompat(shortcutId))
            }

            // On API < R or when the shortcut wasn't pushed (OEM throttle / no
            // permission), fall back to setLargeIcon so the avatar still shows.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !shortcutPushed) {
                builder.setLargeIcon(peerBitmap)
            }

            if (!canPostNotifications(context)) {
                Log.w(TAG, "showMissed: POST_NOTIFICATIONS not granted — skipping notify")
                return@runCatching false
            }

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
            // Refresh the same chat group summary chat messages use, so a
            // missed-call row bundles next to chat heads-ups under one expandable
            // "Messages" header instead of floating outside the bundle.
            ChatNotifications.postGroupSummary(context)
            Log.d(
                TAG,
                "showMissed posted notifId=$notifId senderId=$senderId channel=$CHAT_NOTIFICATION_CHANNEL_ID shortcutPushed=$shortcutPushed"
            )
            true
        }.getOrElse { t ->
            Log.e(TAG, "showMissed threw senderId=$senderId: ${t.message}", t)
            false
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Loads a remote bitmap via Glide, blocking up to its default timeout.
     * Used by [showMissed]'s synchronous bitmap path so the heads-up posts with
     * the real avatar (when reachable) instead of always doing an async refresh.
     */
    private fun loadBitmap(context: Context, url: String): Bitmap? {
        return runCatching {
            Glide.with(context.applicationContext)
                .asBitmap()
                .load(url)
                .apply(RequestOptions.circleCropTransform())
                .submit(256, 256)
                .get()
        }.getOrNull()
    }

    fun cancelIncoming(context: Context) {
        BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
    }

    fun cancelMissed(context: Context, senderId: Int) {
        val notifId = MISSED_CALL_ID_MASK or (senderId and 0x0FFFFFFF)
        runCatching { NotificationManagerCompat.from(context).cancel(notifId) }
    }

    private fun ensureCallsChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CALLS_NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CALLS_NOTIFICATION_CHANNEL_ID,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setBypassDnd(true)
            }
        }
        nm.createNotificationChannel(channel)
    }

    private fun ensureMissedCallsChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(MISSED_CALLS_NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            MISSED_CALLS_NOTIFICATION_CHANNEL_ID,
            "Missed Calls",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
        }
        nm.createNotificationChannel(channel)
    }
}
