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
        val peerIcon: IconCompat = runCatching {
            if (peerImage.isBlank()) null
            else loadBitmap(context, peerImage)?.let { IconCompat.createWithBitmap(it) }
        }.getOrNull() ?: IconCompat.createWithResource(context, R.drawable.logo)

        val peerPerson = Person.Builder()
            .setName(peerName.ifBlank { "User" })
            .setKey(peerId.toString())
            .setIcon(peerIcon)
            .build()

        // MessagingStyle requires a Person for "me" — we never show our own outgoing
        // messages in this stream, so a blank label is fine.
        val mePerson = Person.Builder().setName("").setKey("me").build()

        val style = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle(peerName)
        entries.forEach { entry ->
            style.addMessage(NotificationCompat.MessagingStyle.Message(entry.text, entry.ts, peerPerson))
        }

        val contentIntent = Intent(context, ChatActivityInHouse::class.java).apply {
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentPi)
            .setDeleteIntent(deletePi)
            .setGroup(GROUP_KEY)
            .build()

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
