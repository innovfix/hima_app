package com.gmwapp.hima.agora

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.female.FemaleAudioCallingActivity
import com.gmwapp.hima.agora.female.FemaleCallAcceptActivity
import com.gmwapp.hima.agora.female.FemaleVideoCallingActivity
import com.gmwapp.hima.repositories.FcmNotificationRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponse
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        when (intent.action) {
            "ACTION_ACCEPT_CALL" -> {
                val extras = intent.extras
                val callType = extras?.getString("CALL_TYPE")
                val senderId = extras?.getInt("SENDER_ID")
                val channelName = extras?.getString("CHANNEL_NAME")
                val callId = extras?.getInt("CALL_ID", -1)

                Log.d("CallReceiver", "Call Accepted: callType=$callType, senderId=$senderId, channelName=$channelName, callId=$callId")


                if (callType=="audio"){
                    val callIntent = Intent(context, FemaleAudioCallingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        if (extras != null) {
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("RECEIVER_ID", senderId)
                            putExtra("CALL_ID", callId)
                        }
                    }
                    context.startActivity(callIntent)
                }

                if (callType=="video"){
                    val callIntent = Intent(context, FemaleVideoCallingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        if (extras != null) {
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("RECEIVER_ID", senderId)
                            putExtra("CALL_ID", callId)
                        }
                    }
                    context.startActivity(callIntent)
                }


            }

            "ACTION_REJECT_CALL" -> {

                var userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                var userid = userData?.id

                val callType = intent.getStringExtra("CALL_TYPE")
                val receiverId = intent.getIntExtra("SENDER_ID",-1)
                val channelName = intent.getStringExtra("CHANNEL_NAME")
                val callId = intent.getIntExtra("CALL_ID", -1)


                Log.d("CallReceiver", "Call Rejected: callType=$callType, senderId=$receiverId, channelName=$channelName, callId=$callId")

                val fcmNotificationRepository = (context.applicationContext as BaseApplication).fcmNotificationRepository

                if (userid != null && receiverId != null && callType != null && channelName != null) {
                    fcmNotificationRepository.sendFcmNotification(
                        userid, receiverId, callType, channelName, "rejected",
                        object : NetworkCallback<FcmNotificationResponse> {
                            override fun onResponse(call: retrofit2.Call<FcmNotificationResponse>, response: retrofit2.Response<FcmNotificationResponse>) {
                                Log.d("FCMNotification", "Auto-reject sent: ${response.body()?.message}")
                                BaseApplication.getInstance()?.stopRingtone()
                                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                notificationManager.cancel(1)

                                val context = context.applicationContext
                                val mainIntent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                context.startActivity(mainIntent)
                            }

                            override fun onFailure(call: retrofit2.Call<FcmNotificationResponse>, t: Throwable) {
                                Log.e("FCMNotification", "Error sending auto-reject: ${t.message}")
                            }

                            override fun onNoNetwork() {
                                Log.e("FCMNotification", "No network for auto-reject")
                            }
                        }
                    )
                }



            }

        }
    }
}


