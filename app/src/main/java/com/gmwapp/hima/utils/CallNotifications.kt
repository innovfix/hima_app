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
import androidx.core.content.ContextCompat
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
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for both incoming and missed call notifications.
 *
 * - [showIncoming] posts a `NotificationCompat.CallStyle.forIncomingCall`
 *   heads-up identical to the FCM path so an OneSignal incoming-call push
 *   renders the same Answer/Decline UI as the FCM-driven one.
 * - [showMissed] posts a [NotificationCompat.MessagingStyle] notification on the
 *   same channel/group as chat so it matches chat message notifications (tap
 *   to call back; no separate Call back / Message actions).
 */
object CallNotifications {

    private const val TAG = "HimaIncomingCall"
    private const val MISSED_CALL_DIAG_TAG = "MissedCallDiag"

    /** Mirrors [com.gmwapp.hima.agora.MyFirebaseMessagingService.CALLS_NOTIFICATION_CHANNEL_ID]. */
    private const val CALLS_NOTIFICATION_CHANNEL_ID = "calls_v3"
    const val INCOMING_CALL_NOTIFICATION_ID = 1

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

    /**
     * Strips "Missed call from …" / similar prefixes from server or OneSignal title
     * so [Person] name and avatar initial match the real peer (e.g. "Kishore12").
     * Safe to call on already-clean names (idempotent).
     */
    fun normalizeMissedCallCallerName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = raw.trim().removeSuffix("…").trim()
        // Extract the caller name AFTER "missed call from" / "missed call:" /
        // "missed call - " anywhere in the string. Crucial because the server
        // sends e.g. "📞 Missed call from Kishore12" (phone emoji + space prefix),
        // which a `^missed\s+call\s+from` strip-style regex would never match.
        val extract = Regex(
            "missed\\s+call(?:\\s+from)?\\s*[:\\-]?\\s+(.+?)\\s*\$",
            RegexOption.IGNORE_CASE
        ).find(cleaned)?.groupValues?.getOrNull(1)
            ?.trim()?.removeSuffix("…")?.trim()
        if (!extract.isNullOrBlank()) return extract
        // No "missed call …" pattern matched — strip any leading non-letter /
        // non-digit characters (emoji, symbols, whitespace) and return.
        return cleaned
            .replace(Regex("^[^\\p{L}\\p{N}]+"), "")
            .trim()
            .removeSuffix("…")
            .trim()
    }

    /** T26-style cache: avoid pushDynamicShortcut on every missed-call for same peer. */
    private val missedCallPushedShortcutIds = mutableSetOf<String>()

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

    /**
     * Builds (but does not post) an incoming-call CallStyle notification.
     * Used by [FcmCallService] which needs the [Notification] for
     * [Service.startForeground]; for normal "just post it" callers use
     * [showIncoming] instead.
     */
    fun buildIncomingCallNotification(
        context: Context,
        payload: IncomingPayload,
        avatar: Bitmap? = null
    ): Notification {
        ensureCallsChannel(context)
        val isMale = payload.isMale
        val targetClass = if (isMale) MaleCallAcceptActivity::class.java else FemaleCallAcceptActivity::class.java
        val contentReq = if (isMale) 201 else 101
        val acceptAction = if (isMale) "ACTION_ACCEPT_CALL_MALE" else "ACTION_ACCEPT_CALL"
        val rejectAction = if (isMale) "ACTION_REJECT_CALL_MALE" else "ACTION_REJECT_CALL"
        val acceptReq = if (isMale) 202 else 102
        val rejectReq = if (isMale) 203 else 103

        val tapIntent = Intent(context, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_TYPE", payload.callType)
            putExtra("SENDER_ID", payload.senderId)
            putExtra("CHANNEL_NAME", payload.channelName)
            putExtra("CALL_ID", payload.callId)
            putExtra("Caller_NAME", payload.callerName)
            putExtra("Caller_Image", payload.callerImage.orEmpty())
        }
        val contentPi = PendingIntent.getActivity(
            context, contentReq, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val acceptIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = acceptAction
            putExtra("CALL_TYPE", payload.callType)
            putExtra("SENDER_ID", payload.senderId)
            putExtra("CHANNEL_NAME", payload.channelName)
            putExtra("CALL_ID", payload.callId)
        }
        val acceptPi = PendingIntent.getBroadcast(
            context, acceptReq, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val rejectIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = rejectAction
            putExtra("CALL_TYPE", payload.callType)
            putExtra("SENDER_ID", payload.senderId)
            putExtra("CHANNEL_NAME", payload.channelName)
            putExtra("CALL_ID", payload.callId)
        }
        val rejectPi = PendingIntent.getBroadcast(
            context, rejectReq, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val personBuilder = Person.Builder()
            .setName(payload.callerName)
            .setImportant(true)
        if (avatar != null) personBuilder.setIcon(IconCompat.createWithBitmap(avatar))
        val caller = personBuilder.build()

        return NotificationCompat.Builder(context, CALLS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(caller, rejectPi, acceptPi)
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPi)
            .setFullScreenIntent(contentPi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(35_000L)
            .addPerson(caller)
            .build()
    }

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
        // Defensive: normalize upstream title/body (e.g. "📞 Missed call from Kishore12")
        // so Person name + avatar initial match chat-style notifications.
        val cleanedName = normalizeMissedCallCallerName(payload.callerName)
        val safeName = cleanedName.ifBlank { "Caller" }
        val safeCallType = (payload.callType ?: "audio").lowercase()
        val senderId = payload.senderId
        // OneSignal missed-call payloads from the server typically don't carry an
        // avatar field, so fall back to the most-recent chat-notification image
        // we cached for this peer (set by ChatNotificationStore.saveMeta on every
        // chat push). This makes the notification AND the connecting screen show
        // the real photo when the user has chatted with this peer before.
        val rawImage = payload.callerImage.orEmpty()
        val cachedImage = if (rawImage.isBlank() && !payload.isSynthetic && senderId > 0) {
            ChatNotificationStore.getMeta(context, senderId).second.orEmpty()
        } else ""
        val callerImage = rawImage.ifBlank { cachedImage }

        // First letter for the avatar circle. If the name starts with a non-letter
        // (e.g. leftover emoji), fall back to "?" so AvatarBitmap doesn't try to
        // render a multi-codepoint character that would degrade to garbled text.
        val firstChar = safeName.firstOrNull()
        val firstLetter = if (firstChar != null && firstChar.isLetter())
            firstChar.uppercaseChar().toString()
        else "?"
        Log.d(
            MISSED_CALL_DIAG_TAG,
            "showMissed-entry rawName=\"${payload.callerName}\" safeName=\"$safeName\" " +
                "firstLetter=$firstLetter senderId=$senderId callType=$safeCallType " +
                "imgUrl=\"$callerImage\" imgBlank=${callerImage.isBlank()} synthetic=${payload.isSynthetic}"
        )

        return runCatching {
            // 1. Person + bitmap — align with ChatNotifications.show (bitmap Person icon).
            val remoteBitmap: Bitmap? = runCatching {
                if (callerImage.isBlank()) null else loadBitmap(context, callerImage)
            }.getOrNull()
            val peerBitmap: Bitmap = remoteBitmap
                ?: AvatarBitmap.circleWithInitial(
                    if (firstLetter == "?") "?" else safeName
                )
            Log.d(
                MISSED_CALL_DIAG_TAG,
                "showMissed-bitmap source=${if (remoteBitmap != null) "remote" else "initial"} " +
                    "size=${peerBitmap.width}x${peerBitmap.height} initialUsed=$firstLetter"
            )
            val peerIcon: IconCompat = IconCompat.createWithBitmap(peerBitmap)
            val peerPerson = Person.Builder()
                .setName(safeName.ifBlank { "User" })
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
            //
            //    `action = Intent.ACTION_VIEW` is required by
            //    ShortcutManagerCompat.pushDynamicShortcut on Android 11+ — without
            //    it, the shortcut push throws "intent's action must be set", the
            //    notification fails Conversation promotion, and the collapsed row
            //    falls back to the small icon (HiMa logo) instead of the
            //    Person.icon avatar. Mirrors ChatNotifications.show.
            val openIntent = if (payload.isSynthetic) {
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
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
                    action = Intent.ACTION_VIEW
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
                pendingIntentFlagsForMissed(mutable = false)
            )

            // 5. Conversation shortcut — mirror ChatNotifications (disk + in-memory cache).
            val shortcutId = if (payload.isSynthetic) null else "chat_peer_$senderId"
            val shortcutPushed = if (shortcutId != null) {
                val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                    .setShortLabel(safeName.ifBlank { "Chat" })
                    .setLongLabel(safeName.ifBlank { "Chat" })
                    .setIntent(openIntent)
                    .setIcon(peerIcon)
                    .setPerson(peerPerson)
                    .setLongLived(true)
                    .setCategories(setOf("android.shortcut.conversation"))
                    .build()
                val alreadyOnDisk = runCatching {
                    ShortcutManagerCompat.getDynamicShortcuts(context).any { it.id == shortcutId }
                }.getOrDefault(false)
                when {
                    shortcutId in missedCallPushedShortcutIds || alreadyOnDisk -> {
                        if (shortcutId !in missedCallPushedShortcutIds && alreadyOnDisk) {
                            missedCallPushedShortcutIds.add(shortcutId)
                        }
                        true
                    }
                    else -> {
                        runCatching {
                            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                            missedCallPushedShortcutIds.add(shortcutId)
                            true
                        }.getOrElse {
                            Log.w(TAG, "showMissed: pushDynamicShortcut failed: ${it.message}")
                            false
                        }
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
                // Force title/text so the fallback (non-MessagingStyle) row and any
                // wrapper that overrides MessagingStyle's auto-derived title still
                // show the cleaned caller name (e.g. "Kishore12") instead of the
                // server's "Missed call from Kishore12" line.
                .setContentTitle(safeName)
                .setContentText(bodyText)
            if (shortcutId != null) {
                builder.setShortcutId(shortcutId)
                builder.setLocusId(LocusIdCompat(shortcutId))
            }

            // On API < R or when the shortcut wasn't pushed (OEM throttle / no
            // permission / pre-R), fall back to setLargeIcon so the avatar still
            // shows. On Android 11+ with shortcutPushed=true we intentionally
            // *omit* setLargeIcon so Android promotes this to a Conversation row
            // and uses Person.icon as the collapsed-row avatar (with the small
            // icon as a tiny badge), matching ChatNotifications behaviour.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !shortcutPushed) {
                builder.setLargeIcon(peerBitmap)
            }

            if (!canPostNotifications(context)) {
                Log.w(
                    MISSED_CALL_DIAG_TAG,
                    "showMissed skipped: POST_NOTIFICATIONS not granted senderId=$senderId shortcutId=$shortcutId"
                )
                return@runCatching false
            }

            val built = builder.build()
            val extras = built.extras
            val hasLargeIcon = extras?.get(Notification.EXTRA_LARGE_ICON) != null ||
                extras?.get(Notification.EXTRA_LARGE_ICON_BIG) != null
            val builtTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val builtText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val template = extras?.getString(Notification.EXTRA_TEMPLATE)
            Log.d(
                MISSED_CALL_DIAG_TAG,
                "showMissed-built notifId=$notifId title=\"$builtTitle\" text=\"$builtText\" " +
                    "template=$template hasLargeIcon=$hasLargeIcon shortcutPushed=$shortcutPushed " +
                    "shortcutId=$shortcutId sdk=${Build.VERSION.SDK_INT} synthetic=${payload.isSynthetic}"
            )

            try {
                NotificationManagerCompat.from(context).notify(notifId, built)
                Log.d(
                    MISSED_CALL_DIAG_TAG,
                    "showMissed-notify OK notifId=$notifId senderId=$senderId " +
                        "channel=$CHAT_NOTIFICATION_CHANNEL_ID shortcutPushed=$shortcutPushed"
                )
            } catch (t: Throwable) {
                Log.e(MISSED_CALL_DIAG_TAG, "showMissed-notify FAIL notifId=$notifId: ${t.message}", t)
                throw t
            }
            ChatNotifications.postGroupSummary(context)
            true
        }.getOrElse { t ->
            Log.e(MISSED_CALL_DIAG_TAG, "showMissed-outer threw senderId=$senderId: ${t.message}", t)
            false
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingIntentFlagsForMissed(mutable: Boolean): Int {
        val base = PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        } else {
            base
        }
    }

    /**
     * Loads a remote bitmap via Glide, blocking up to 2s — same bound as [ChatNotifications].
     */
    private fun loadBitmap(context: Context, url: String): Bitmap? {
        return runCatching {
            Glide.with(context.applicationContext)
                .asBitmap()
                .load(url)
                .apply(RequestOptions.circleCropTransform())
                .submit(256, 256)
                .get(2, TimeUnit.SECONDS)
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
            // TC_025 (B9) — do NOT setBypassDnd. Hima is a social app, not a
            // primary phone replacement; when the user turns on system Do Not
            // Disturb we must respect it and stay silent. This mirrors the B199
            // decision already applied to the FCM "calls_v5" channel.
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
