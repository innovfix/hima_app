package com.gmwapp.hima.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.SendMessageResponse
import retrofit2.Call
import retrofit2.Response

/**
 * CHAT-065 sibling: handles the inline Reply RemoteInput action on chat
 * notifications. Reads the typed text, sends it through the same
 * [com.gmwapp.hima.retrofit.ApiManager.sendMessage] path the in-app chat
 * uses, then re-fires the MessagingStyle notification with the reply
 * appended as a "You: …" line so the user sees their message land — exactly
 * like WhatsApp. The notification auto-dismisses 3 s after a successful
 * send. On failure the notification is left in place and a brief toast is
 * shown so the user can retry from inside the app.
 */
class ChatReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return

        val results = RemoteInput.getResultsFromIntent(intent)
        val text = results?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            Log.w(TAG, "Reply text empty — ignoring")
            return
        }

        val peerId = intent.getIntExtra(EXTRA_PEER_ID, 0)
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME).orEmpty()
        val peerImage = intent.getStringExtra(EXTRA_PEER_IMAGE).orEmpty()
        if (peerId <= 0) {
            Log.w(TAG, "Missing peerId — dropping reply")
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val app = BaseApplication.getInstance()
        val myUserId = app?.getPrefs()?.getUserData()?.id ?: 0
        val apiManager = app?.getApiManager()

        if (myUserId <= 0 || apiManager == null) {
            Log.w(TAG, "Missing user/apiManager — can't send")
            toastOnMain(appContext, R.string.chat_notification_reply_failed)
            pendingResult.finish()
            return
        }

        Log.d(TAG, "Inline reply peerId=$peerId myUserId=$myUserId len=${text.length}")

        apiManager.sendMessage(
            myUserId, peerId, text, "text",
            object : NetworkCallback<SendMessageResponse> {
                override fun onResponse(
                    call: Call<SendMessageResponse>,
                    response: Response<SendMessageResponse>,
                ) {
                    val ok = response.isSuccessful && response.body()?.status == true
                    Handler(Looper.getMainLooper()).post {
                        if (ok) {
                            // Append "You: …" to the store so the next render
                            // (and any subsequent peer push) keeps the reply in
                            // the thread.
                            ChatNotificationStore.append(
                                appContext, peerId, text,
                                System.currentTimeMillis(), isMe = true,
                            )
                            val entries = ChatNotificationStore.get(appContext, peerId)
                            // Re-fire the notification with the reply visible.
                            runCatching {
                                ChatNotifications.show(
                                    appContext, peerId, peerName, peerImage, entries,
                                )
                            }.onFailure {
                                Log.w(TAG, "Re-fire after reply failed: ${it.message}")
                            }
                            // Auto-dismiss after 3 s so the WhatsApp-style "your
                            // reply just landed" confirmation fades away.
                            Handler(Looper.getMainLooper()).postDelayed({
                                runCatching {
                                    NotificationManagerCompat.from(appContext)
                                        .cancel(ChatNotifications.notifIdFor(peerId))
                                    ChatNotificationStore.clear(appContext, peerId)
                                }
                            }, 3_000L)
                        } else {
                            Log.w(
                                TAG,
                                "sendMessage failed ok=$ok code=${response.code()} " +
                                    "msg=${response.body()?.message}",
                            )
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.chat_notification_reply_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        pendingResult.finish()
                    }
                }

                override fun onFailure(call: Call<SendMessageResponse>, t: Throwable) {
                    Log.w(TAG, "sendMessage failed: ${t.message}")
                    toastOnMain(appContext, R.string.chat_notification_reply_failed)
                    pendingResult.finish()
                }

                override fun onNoNetwork() {
                    Log.w(TAG, "sendMessage no network")
                    toastOnMain(appContext, R.string.chat_notification_reply_no_network)
                    pendingResult.finish()
                }
            },
        )
    }

    private fun toastOnMain(context: Context, msgRes: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_REPLY = "com.gmwapp.hima.action.CHAT_INLINE_REPLY"
        const val KEY_TEXT_REPLY = "key_chat_inline_reply"
        const val EXTRA_PEER_ID = "peer_id"
        const val EXTRA_PEER_NAME = "peer_name"
        const val EXTRA_PEER_IMAGE = "peer_image"
        private const val TAG = "ChatReplyReceiver"
    }
}
