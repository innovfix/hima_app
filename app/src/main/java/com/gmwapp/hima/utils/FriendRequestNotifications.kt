package com.gmwapp.hima.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.ChatListActivity
import com.gmwapp.hima.fragments.FriendsTabFragment
import java.util.concurrent.TimeUnit

/**
 * CHAT-065: WhatsApp-style friend-request notification with inline Accept /
 * Decline action buttons. The buttons route through [FriendRequestActionReceiver]
 * so the accept/reject API call happens without the app being launched, then
 * the notification is replaced with a confirmation that auto-dismisses.
 *
 * Reuses the existing chat channel (`f49d2168-…`) so it inherits the same
 * sound/vibration/heads-up behaviour configured in BaseApplication. Avatar
 * loading and PendingIntent flag helpers mirror [ChatNotifications].
 */
object FriendRequestNotifications {

    private const val TAG = "FriendRequestNotif"

    /** Same channel as chat — gives heads-up + sound on Android 8+. */
    private const val CHANNEL_ID = "f49d2168-bc20-4a4b-a984-a7abffe0d6aa"

    /**
     * Stable per-sender id. Top nibble = 0x5 so it can't collide with chat
     * (0x4xxxxxxx) or call notifications (small ints).
     */
    fun notifIdFor(senderId: Int): Int = 0x50000000 or (senderId and 0x0FFFFFFF)

    fun show(
        context: Context,
        senderId: Int,
        senderName: String,
        senderImage: String,
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "show called on main thread; avatar load is blocking")
        }

        val avatar = loadAvatar(context, senderImage, senderName)

        // Tap-on-body opens the Received Requests tab so the user can also act
        // from inside the app if they ignore the buttons.
        val contentIntent = Intent(context, ChatListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_tab", FriendsTabFragment.TYPE_THEIR_REQUESTS)
        }
        val contentPi = PendingIntent.getActivity(
            context, senderId, contentIntent, piFlags(mutable = false)
        )

        val acceptPi = actionPendingIntent(
            context, FriendRequestActionReceiver.ACTION_ACCEPT,
            senderId, senderName, senderImage,
        )
        val declinePi = actionPendingIntent(
            context, FriendRequestActionReceiver.ACTION_DECLINE,
            senderId, senderName, senderImage,
        )

        val title = context.getString(R.string.fr_notification_title)
        val body = context.getString(R.string.fr_notification_body, senderName)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setLargeIcon(avatar)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.fr_notification_accept),
                acceptPi,
            )
            .addAction(
                R.drawable.baseline_close_24,
                context.getString(R.string.fr_notification_decline),
                declinePi,
            )

        post(context, senderId, builder.build())
    }

    fun showAccepted(
        context: Context,
        senderId: Int,
        senderName: String,
        senderImage: String,
    ) {
        val avatar = loadAvatar(context, senderImage, senderName)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setLargeIcon(avatar)
            .setContentTitle(context.getString(R.string.fr_notification_accepted_title, senderName))
            .setContentText(context.getString(R.string.fr_notification_accepted_body))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(2_000L)
        post(context, senderId, builder.build())
    }

    fun showDeclined(
        context: Context,
        senderId: Int,
        senderName: String,
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(context.getString(R.string.fr_notification_declined_title))
            .setContentText(context.getString(R.string.fr_notification_declined_body))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(1_500L)
        post(context, senderId, builder.build())
    }

    private fun post(context: Context, senderId: Int, notification: android.app.Notification) {
        if (!canPostNotifications(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — skipping senderId=$senderId")
            return
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(notifIdFor(senderId), notification)
        }.onFailure { Log.w(TAG, "notify(senderId=$senderId) failed: ${it.message}") }
    }

    private fun actionPendingIntent(
        context: Context,
        action: String,
        senderId: Int,
        senderName: String,
        senderImage: String,
    ): PendingIntent {
        val intent = Intent(context, FriendRequestActionReceiver::class.java).apply {
            this.action = action
            putExtra(FriendRequestActionReceiver.EXTRA_SENDER_ID, senderId)
            putExtra(FriendRequestActionReceiver.EXTRA_SENDER_NAME, senderName)
            putExtra(FriendRequestActionReceiver.EXTRA_SENDER_IMAGE, senderImage)
        }
        // Per-(action, sender) requestCode so Accept and Decline don't share a
        // PendingIntent and trample each other's extras under FLAG_UPDATE_CURRENT.
        val requestCode = senderId * 10 +
            if (action == FriendRequestActionReceiver.ACTION_ACCEPT) 1 else 2
        return PendingIntent.getBroadcast(
            context, requestCode, intent, piFlags(mutable = false),
        )
    }

    private fun loadAvatar(context: Context, imageUrl: String, senderName: String): Bitmap {
        val remote = runCatching {
            if (imageUrl.isBlank()) null
            else Glide.with(context.applicationContext)
                .asBitmap()
                .load(imageUrl)
                .apply(RequestOptions.circleCropTransform())
                .submit(256, 256)
                .get(2, TimeUnit.SECONDS)
        }.getOrNull()
        return remote ?: AvatarBitmap.circleWithInitial(senderName.ifBlank { "?" })
    }

    private fun piFlags(mutable: Boolean): Int {
        val base = PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        } else base
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
