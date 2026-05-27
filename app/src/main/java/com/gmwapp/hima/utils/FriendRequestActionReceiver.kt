package com.gmwapp.hima.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FriendRequestResponse
import retrofit2.Call
import retrofit2.Response

/**
 * CHAT-065: Handles Accept / Decline button taps on the friend-request
 * notification. Routes the action through the same `sendFriendRequest`
 * endpoint that the in-app Friends tab uses (status=1 accept, status=3
 * reject), then replaces the original notification with a confirmation that
 * auto-dismisses via [FriendRequestNotifications].
 *
 * On API failure the original notification is left in place so the user can
 * retry from inside the app; we surface a brief toast so they aren't left
 * silently confused.
 */
class FriendRequestActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val senderId = intent.getIntExtra(EXTRA_SENDER_ID, 0)
        val senderName = intent.getStringExtra(EXTRA_SENDER_NAME).orEmpty()
        val senderImage = intent.getStringExtra(EXTRA_SENDER_IMAGE).orEmpty()

        if (senderId <= 0) {
            Log.w(TAG, "Missing senderId — dropping action=$action")
            return
        }

        val status = when (action) {
            ACTION_ACCEPT -> STATUS_ACCEPT
            ACTION_DECLINE -> STATUS_REJECT
            else -> {
                Log.w(TAG, "Unknown action=$action")
                return
            }
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val app = BaseApplication.getInstance()
        val myUserId = app?.getPrefs()?.getUserData()?.id ?: 0
        val apiManager = app?.getApiManager()

        if (myUserId <= 0 || apiManager == null) {
            Log.w(TAG, "Missing myUserId/apiManager — opening app instead")
            toastOnMain(appContext, R.string.fr_notification_action_failed)
            pendingResult.finish()
            return
        }

        Log.d(TAG, "Action=$action senderId=$senderId myUserId=$myUserId status=$status")

        apiManager.sendFriendRequest(
            senderId, myUserId, status,
            object : NetworkCallback<FriendRequestResponse> {
                override fun onResponse(
                    call: Call<FriendRequestResponse>,
                    response: Response<FriendRequestResponse>,
                ) {
                    val ok = response.isSuccessful && response.body()?.success == true
                    Log.d(TAG, "API response ok=$ok body.success=${response.body()?.success}")
                    Handler(Looper.getMainLooper()).post {
                        if (ok) {
                            when (status) {
                                STATUS_ACCEPT -> FriendRequestNotifications.showAccepted(
                                    appContext, senderId, senderName, senderImage,
                                )
                                STATUS_REJECT -> FriendRequestNotifications.showDeclined(
                                    appContext, senderId, senderName,
                                )
                            }
                        } else {
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.fr_notification_action_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        pendingResult.finish()
                    }
                }

                override fun onFailure(call: Call<FriendRequestResponse>, t: Throwable) {
                    Log.w(TAG, "sendFriendRequest failed: ${t.message}")
                    toastOnMain(appContext, R.string.fr_notification_action_failed)
                    pendingResult.finish()
                }

                override fun onNoNetwork() {
                    Log.w(TAG, "sendFriendRequest no network")
                    toastOnMain(appContext, R.string.fr_notification_action_no_network)
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
        const val ACTION_ACCEPT = "com.gmwapp.hima.action.FRIEND_REQUEST_ACCEPT"
        const val ACTION_DECLINE = "com.gmwapp.hima.action.FRIEND_REQUEST_DECLINE"

        const val EXTRA_SENDER_ID = "sender_id"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_SENDER_IMAGE = "sender_image"

        // sendFriendRequest status param values, mirrored from FriendsTabFragment.
        private const val STATUS_ACCEPT = 1
        private const val STATUS_REJECT = 3

        private const val TAG = "FrReqActionReceiver"
    }
}
