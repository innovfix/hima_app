package com.gmwapp.hima.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
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
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.ChatActivityInHouse

/**
 * Posts per-conversation WhatsApp-style notifications: one stable notification id per
 * peer, stacking the last few messages via [NotificationCompat.MessagingStyle]. The
 * store in [ChatNotificationStore] is the source of truth for the lines shown, so
 * repeated pushes from the same sender collapse instead of each spawning a new alert.
 */
object ChatNotifications {

    private const val TAG = "ChatNotifications"
    private const val DIAG_TAG = "ChatNotifDiag"

    /** Same channel already created in BaseApplication.onCreate so sound/vibration don't regress. */
    private const val CHANNEL_ID = "f49d2168-bc20-4a4b-a984-a7abffe0d6aa"

    private const val GROUP_KEY = "chat_messages"

    /**
     * Stable per-peer id. Sets the top bit so it can't collide with the small
     * call-notification ids (1, 9901) used elsewhere in the app.
     */
    fun notifIdFor(peerId: Int): Int = 0x40000000 or (peerId and 0x0FFFFFFF)

    fun show(
        context: Context,
        peerId: Int,
        peerName: String,
        peerImage: String,
        entries: List<ChatNotificationStore.Entry>
    ) {
        if (entries.isEmpty()) return

        // Peer avatar is best-effort. Glide's .submit().get() is a blocking call —
        // safe here because the NSE invokes us on a background worker thread.
        // Conversation notifications on Android 11+ only honour Bitmap /
        // AdaptiveBitmap icons for a Person avatar; a Resource-typed icon is
        // silently replaced by a generic grey silhouette. So always feed a
        // Bitmap: remote URL first, then a locally-drawn initial-letter circle.
        val remoteBitmap: Bitmap? = runCatching {
            if (peerImage.isBlank()) null else loadBitmap(context, peerImage)
        }.getOrNull()
        val peerBitmap: Bitmap = remoteBitmap
            ?: AvatarBitmap.circleWithInitial(peerName.ifBlank { "?" })
        val peerIcon: IconCompat = IconCompat.createWithBitmap(peerBitmap)
        Log.d(
            TAG,
            "avatar peerId=$peerId urlBlank=${peerImage.isBlank()} " +
                "remoteOk=${remoteBitmap != null} usedFallback=${remoteBitmap == null}"
        )

        val peerPerson = Person.Builder()
            .setName(peerName.ifBlank { "User" })
            .setKey(peerId.toString())
            .setIcon(peerIcon)
            .build()

        // MessagingStyle requires a Person for "me" with a NON-EMPTY name — passing
        // a blank one throws IllegalArgumentException("User's name must not be empty")
        // at Builder time, which aborts our custom Conversation path and forces
        // OneSignal to fall back to its default rendering. Use the logged-in user's
        // name when available, otherwise a generic fallback.
        val myUserData = com.gmwapp.hima.BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val myDisplayName = myUserData?.name?.takeIf { it.isNotBlank() } ?: "You"
        val mePerson = Person.Builder()
            .setName(myDisplayName)
            .setKey(myUserData?.id?.toString() ?: "me")
            .build()

        // 1:1 chat — do NOT call setConversationTitle (that's the group-chat signal).
        // Android pulls the peer name off peerPerson.name. Combining setConversationTitle
        // with setGroupConversation(false) is contradictory and blocks Conversation
        // promotion on API 30+ on several OEM skins.
        val style = NotificationCompat.MessagingStyle(mePerson)
            .setGroupConversation(false)
        // Backend currently sends a rolling counter ("N new messages") as the push
        // body rather than real chat text, so stacking every entry turns into a
        // repetitive list. Show only the most recent entry so each push replaces
        // the previous body in-place instead of accumulating lines. If backend
        // starts sending real message text in contents.en, swap this back to
        // `entries.forEach { ... }` to get full WhatsApp-style line stacking.
        entries.lastOrNull()?.let { latest ->
            style.addMessage(
                NotificationCompat.MessagingStyle.Message(latest.text, latest.ts, peerPerson)
            )
        }

        val contentIntent = Intent(context, ChatActivityInHouse::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("USER_ID", peerId)
            putExtra("USER_NAME", peerName)
            putExtra("USER_IMAGE", peerImage)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            peerId, // requestCode per peer so the extras don't collide across senders
            contentIntent,
            pendingIntentFlags(mutable = false)
        )

        val deleteIntent = Intent(context, ChatNotifDeleteReceiver::class.java).apply {
            putExtra(ChatNotifDeleteReceiver.EXTRA_PEER_ID, peerId)
        }
        val deletePi = PendingIntent.getBroadcast(
            context,
            peerId,
            deleteIntent,
            pendingIntentFlags(mutable = false)
        )

        // Publish a long-lived, conversation-categorised dynamic shortcut so Android
        // 11+ promotes the notification to the Conversations section, rendering the
        // peer avatar large on the left with the app icon as a small badge overlay.
        // Falls through cleanly on older OS versions — the shortcut just goes unused.
        val shortcutId = "chat_peer_$peerId"
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(peerName.ifBlank { "Chat" })
            .setLongLabel(peerName.ifBlank { "Chat" })
            .setIntent(contentIntent)
            .setIcon(peerIcon)
            .setPerson(peerPerson)
            .setLongLived(true)
            .setCategories(setOf("android.shortcut.conversation"))
            .build()
        val shortcutPushed = runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut); true
        }.getOrElse {
            Log.w(TAG, "pushDynamicShortcut(peerId=$peerId) failed: ${it.message}"); false
        }
        // Definitive signal on whether Conversation promotion can happen for this
        // notification — if pushed=false or count=0, the OEM blocked shortcut
        // publishing (quota / permission) and the problem is not in the builder.
        Log.d(
            TAG,
            "conv-shortcut peer=$peerId id=$shortcutId pushed=$shortcutPushed " +
                "count=${ShortcutManagerCompat.getDynamicShortcuts(context).size}"
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentPi)
            .setDeleteIntent(deletePi)
            .setGroup(GROUP_KEY)
            .setShortcutId(shortcutId)
            .setLocusId(LocusIdCompat(shortcutId))

        // On Android 11+ (API 30) the MessagingStyle Person icon is used as the
        // conversation avatar automatically — calling setLargeIcon there forces the
        // classic "app icon left, big icon right" layout and suppresses Conversation
        // promotion. Only set it on API 29 and earlier where there is no conversation
        // category and the avatar would otherwise not appear.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            peerBitmap?.let { builder.setLargeIcon(it) }
        }

        val notification = builder.build()

        // One-shot diagnostic snapshot: every input Android examines when deciding
        // whether to promote this notification to a Conversation. Single line per
        // push under tag ChatNotifDiag so one adb filter captures it all.
        dumpDiagnostics(context, peerId, shortcutId, shortcutPushed, notification, peerBitmap)

        if (!canPostNotifications(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — skipping notify(peerId=$peerId)")
            return
        }

        try {
            NotificationManagerCompat.from(context).notify(notifIdFor(peerId), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notify(peerId=$peerId) failed: ${e.message}")
        }
    }

    /**
     * Log every precondition Android checks before promoting a MessagingStyle
     * notification to a Conversation. Produces a single line under tag [DIAG_TAG];
     * any value that doesn't match the expected shape below will pinpoint which
     * gate is failing. Purely read-only — does not affect what is posted.
     *
     * Expected on Android 11+ for a promoted row:
     *   sdk≥30, pushed=true, matchFound=true, matchLongLived=true,
     *   matchPersons≥1, matchCats=android.shortcut.conversation,
     *   channelImportance≥3, notifsEnabled=true, hasLargeIcon=false,
     *   n.shortcutId=<same as shortcutId>, n.locusId=<same>,
     *   template=android.app.Notification$MessagingStyle,
     *   convoTitle=null, isGroupConvo=false.
     */
    private fun dumpDiagnostics(
        context: Context,
        peerId: Int,
        shortcutId: String,
        pushed: Boolean,
        notification: android.app.Notification,
        peerBitmap: Bitmap?
    ) {
        try {
            val sdk = Build.VERSION.SDK_INT
            val dynamic = ShortcutManagerCompat.getDynamicShortcuts(context)
            val match = dynamic.firstOrNull { it.id == shortcutId }
            val matchCats = match?.categories?.joinToString(",") ?: "<none>"
            // NOTE: persons and isLongLived on ShortcutInfoCompat are @RestrictTo so we
            // cannot read them back here. They are set as literals in this file
            // (setLongLived(true) + setPerson(peerPerson) above), so if matchFound=true
            // those gates are satisfied by construction.

            val nm = NotificationManagerCompat.from(context)
            val areEnabled = nm.areNotificationsEnabled()
            val channel = if (sdk >= Build.VERSION_CODES.O) {
                nm.getNotificationChannel(CHANNEL_ID)
            } else null
            val channelImportance = channel?.importance
            val channelName = channel?.name?.toString()
            val conversationId = if (sdk >= Build.VERSION_CODES.R) channel?.conversationId else null
            val parentChannelId = if (sdk >= Build.VERSION_CODES.R) channel?.parentChannelId else null

            // Notification extras contain the raw values the system reads.
            val extras = notification.extras
            val hasLargeIcon = extras?.get(android.app.Notification.EXTRA_LARGE_ICON) != null ||
                extras?.get(android.app.Notification.EXTRA_LARGE_ICON_BIG) != null
            val nShortcutId: String? = if (sdk >= Build.VERSION_CODES.Q) notification.shortcutId else "<api<29>"
            val nLocusId: String? = if (sdk >= Build.VERSION_CODES.Q) notification.locusId?.id else "<api<29>"
            val templateClass = extras?.getString(android.app.Notification.EXTRA_TEMPLATE)
            val convoTitle = extras?.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)
            val isGroupConvo = extras?.getBoolean(android.app.Notification.EXTRA_IS_GROUP_CONVERSATION)

            Log.d(
                DIAG_TAG,
                "peer=$peerId shortcutId=$shortcutId pushed=$pushed " +
                    "sdk=$sdk dynamicCount=${dynamic.size} matchFound=${match != null} " +
                    "matchCats=$matchCats " +
                    "channelImportance=$channelImportance channelName=$channelName " +
                    "channelConvoId=$conversationId parentChannelId=$parentChannelId " +
                    "notifsEnabled=$areEnabled peerBitmap=${peerBitmap != null} hasLargeIcon=$hasLargeIcon " +
                    "n.shortcutId=$nShortcutId n.locusId=$nLocusId " +
                    "template=$templateClass convoTitle=\"$convoTitle\" isGroupConvo=$isGroupConvo"
            )
        } catch (t: Throwable) {
            Log.w(DIAG_TAG, "diag dump failed: ${t.message}")
        }
    }

    private fun loadBitmap(context: Context, url: String): Bitmap? {
        return runCatching {
            Glide.with(context.applicationContext)
                .asBitmap()
                .load(url)
                .apply(RequestOptions.circleCropTransform())
                .submit(256, 256)
                .get()
        }.onFailure { Log.d(TAG, "peer avatar fetch failed: ${it.message}") }.getOrNull()
    }

    private fun pendingIntentFlags(mutable: Boolean): Int {
        val base = PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        } else {
            base
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
