package com.gmwapp.hima.fragments

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatActivity.POWER_SERVICE
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.dagger.SetupZegoKitEvent
import com.gmwapp.hima.utils.Helper
import com.gmwapp.hima.utils.UsersImage
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.widgets.CustomCallEmptyView
import com.gmwapp.hima.widgets.CustomCallView
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
//import com.tencent.mmkv.MMKV
//import com.zegocloud.uikit.components.audiovideo.ZegoAvatarViewProvider
//import com.zegocloud.uikit.components.audiovideo.ZegoForegroundViewProvider
//import com.zegocloud.uikit.plugin.invitation.ZegoInvitationType
//import com.zegocloud.uikit.plugin.signaling.ZegoSignalingPlugin
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallConfig
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
//import com.zegocloud.uikit.prebuilt.call.config.DurationUpdateListener
//import com.zegocloud.uikit.prebuilt.call.config.ZegoCallDurationConfig
//import com.zegocloud.uikit.prebuilt.call.config.ZegoHangUpConfirmDialogInfo
//import com.zegocloud.uikit.prebuilt.call.config.ZegoMenuBarButtonName
//import com.zegocloud.uikit.prebuilt.call.core.CallInvitationServiceImpl
//import com.zegocloud.uikit.prebuilt.call.core.invite.PrebuiltCallInviteExtendedData
//import com.zegocloud.uikit.prebuilt.call.core.invite.ZegoCallInvitationData
//import com.zegocloud.uikit.prebuilt.call.core.invite.advanced.ZegoCallInvitationInCallingConfig
//import com.zegocloud.uikit.prebuilt.call.core.invite.ui.CallRouteActivity
//import com.zegocloud.uikit.prebuilt.call.core.notification.NotificationUtil
//import com.zegocloud.uikit.prebuilt.call.core.notification.PrebuiltCallNotificationManager
//import com.zegocloud.uikit.prebuilt.call.core.notification.RingtoneManager
//import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
//import com.zegocloud.uikit.prebuilt.call.invite.internal.CallInviteActivity
//import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoUIKitPrebuiltCallConfigProvider
//import com.zegocloud.uikit.service.defines.ZegoUIKitUser
//import dagger.hilt.android.AndroidEntryPoint
//import im.zego.connection.internal.ZegoConnectionImpl
//import im.zego.uikit.libuikitreport.ReportUtil
//import im.zego.zim.ZIM
//import im.zego.zim.callback.ZIMEventHandler
//import im.zego.zim.entity.ZIMCallInvitationAcceptedInfo
//import im.zego.zim.entity.ZIMCallInvitationCancelledInfo
//import im.zego.zim.entity.ZIMCallInvitationEndedInfo
//import im.zego.zim.entity.ZIMCallInvitationReceivedInfo
//import im.zego.zim.entity.ZIMCallInvitationTimeoutInfo
//import im.zego.zim.entity.ZIMCallUserStateChangeInfo
//import im.zego.zim.enums.ZIMCallUserState
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Arrays


@AndroidEntryPoint
open class BaseFragment : Fragment() {
    private var lastAction: String? = null
    private var wakeLock:PowerManager.WakeLock? =null
    private lateinit var mContext:Context;
//    private var zimBackup:ZIM? = null;
//    private val zimEventHandler: ZIMEventHandler = object : ZIMEventHandler() {
//        override fun onCallInvitationReceived(
//            zim: ZIM?,
//            info: ZIMCallInvitationReceivedInfo?,
//            callID: String?
//        ) {
//            super.onCallInvitationReceived(zim, info, callID)
//            zimBackup = zim
//            if(lastAction == Intent.ACTION_SCREEN_OFF){
//                playMedia()
//            }else{
//                try {
//                    val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
//                    audioManager?.getStreamMaxVolume(AudioManager.STREAM_RING)?.let {
//                        audioManager.setStreamVolume(
//                            AudioManager.STREAM_RING,
//                            it,
//                            0
//                        )
//
//                    }
//                } catch (e: Exception) {
//                }
//            }
//        }
//
//        override fun onCallInvitationCancelled(
//            zim: ZIM?,
//            info: ZIMCallInvitationCancelledInfo?,
//            callID: String?
//        ) {
//            super.onCallInvitationCancelled(zim, info, callID)
//            mContext?.let { NotificationManagerCompat.from(it).cancel(PrebuiltCallNotificationManager.incoming_call_notification_id) }
//            zimBackup = null
//            stopMedia()
//        }
//
//        override fun onCallUserStateChanged(
//            zim: ZIM?,
//            info: ZIMCallUserStateChangeInfo?,
//            callID: String?
//        ) {
//            super.onCallUserStateChanged(zim, info, callID)
//            try {
//                if(info?.callUserList?.get(0)?.state == ZIMCallUserState.ACCEPTED || info?.callUserList?.get(0)?.state == ZIMCallUserState.REJECTED) {
//                    zimBackup = null
//                    stopMedia()
//                    mContext?.let { NotificationManagerCompat.from(it).cancel(PrebuiltCallNotificationManager.incoming_call_notification_id) }
//                }
//            } catch (e: Exception) {
//            }
//
//
//        }
//
//        override fun onCallInvitationEnded(
//            zim: ZIM?,
//            info: ZIMCallInvitationEndedInfo?,
//            callID: String?
//        ) {
//            super.onCallInvitationEnded(zim, info, callID)
//            zimBackup = null
//            stopMedia()
//            mContext?.let { NotificationManagerCompat.from(it).cancel(PrebuiltCallNotificationManager.incoming_call_notification_id) }
//        }
//
//        override fun onCallInvitationTimeout(
//            zim: ZIM?,
//            info: ZIMCallInvitationTimeoutInfo?,
//            callID: String?
//        ) {
//            super.onCallInvitationTimeout(zim, info, callID)
//            zimBackup = null
//            stopMedia()
//            mContext?.let { NotificationManagerCompat.from(it).cancel(PrebuiltCallNotificationManager.incoming_call_notification_id) }
//        }
//    }

    protected var lastActiveTime: Long = 0;
    var receivedId: Int = 0
    var callId: Int = 0
    var balanceTime: String? = null
    private var foregroundViews= arrayListOf<CustomCallView>()
    val profileViewModel: ProfileViewModel by viewModels()
    protected var roomID: String? = null
    fun showErrorMessage(message: String) {
        if (message == DConstants.NO_NETWORK) {
            Toast.makeText(
                context, getString(R.string.please_try_again_later), Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                context, message, Toast.LENGTH_LONG
            ).show()
        }
    }

//    override fun onStart() {
//        super.onStart()
//        EventBus.getDefault().register(this)
//    }

    override fun onResume() {
        super.onResume()
        stopMedia()
    }
    override fun onAttach(context: Context) {
        super.onAttach(context)
        this.mContext = context;
    }
    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

//    @Subscribe(threadMode = ThreadMode.MAIN)
//    fun onSetupZegoKitEvent(event: SetupZegoKitEvent?) {
//        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
//        if (userData != null) {
//            setupZegoUIKit(userData.id, userData.name)
//        }
//    }

//    protected fun activateWakeLock(){
//        try {
//            val powerManager = ZegoConnectionImpl.context.getSystemService(POWER_SERVICE) as PowerManager
//            wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "Hima:Calling")
//            wakeLock?.acquire()
//        } catch (e: Exception) {
//        }
//    }

    protected fun releaseWakeLock(){
        try {
            wakeLock?.release();
            wakeLock = null;
        } catch (e: Exception) {
        }
    }

    fun playMedia(){
        try {
            val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
            audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.let {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    it,
                    0
                )
            }
          //  RingtoneManager.playRingTone(true)
        } catch (e: Exception) {
        }
    }

    fun stopMedia(){
        try {
            BaseApplication.getInstance()?.getMediaPlayer()?.stop()
        } catch (e: Exception) {
        }
    }

//    fun setupZegoUIKit(Userid: Any, userName: String) {
//        val appID: Long = 364167780
//        val appSign = "3dd4f50fa22240d5943b75a843ef9711c7fa0424e80f8eb67c2bc0552cd1c2f3"
//        val userID: String = Userid.toString()
//        val callInvitationConfig = ZegoUIKitPrebuiltCallInvitationConfig()
//
//        callInvitationConfig.callingConfig = ZegoCallInvitationInCallingConfig()
//        callInvitationConfig.callingConfig.canInvitingInCalling = false
//        callInvitationConfig.callingConfig.onlyInitiatorCanInvite = true
//        callInvitationConfig.endCallWhenInitiatorLeave = true
//        callInvitationConfig.incomingCallRingtone = "rhythm"
//        callInvitationConfig.outgoingCallRingtone = "silent"
//        ZegoUIKitPrebuiltCallService.events.callEvents.setOnlySelfInRoomListener {
//            ZegoUIKitPrebuiltCallService.endCall()
//        }
//        callInvitationConfig.provider = object : ZegoUIKitPrebuiltCallConfigProvider {
//            override fun requireConfig(invitationData: ZegoCallInvitationData): ZegoUIKitPrebuiltCallConfig {
//                callId = invitationData.customData.toInt()
//                val config: ZegoUIKitPrebuiltCallConfig = when {
//                    invitationData.type == ZegoInvitationType.VIDEO_CALL.value && invitationData.invitees.size > 1 -> {
//                        ZegoUIKitPrebuiltCallConfig.groupVideoCall()
//                    }
//
//                    invitationData.type != ZegoInvitationType.VIDEO_CALL.value && invitationData.invitees.size > 1 -> {
//                        ZegoUIKitPrebuiltCallConfig.groupVoiceCall()
//                    }
//
//                    invitationData.type != ZegoInvitationType.VIDEO_CALL.value -> {
//                        ZegoUIKitPrebuiltCallConfig.oneOnOneVoiceCall()
//                    }
//
//                    else -> {
//                        val oneOnOneVideoCall = ZegoUIKitPrebuiltCallConfig.oneOnOneVideoCall()
//                        oneOnOneVideoCall.bottomMenuBarConfig.buttons = ArrayList(
//                            Arrays.asList(
//                                ZegoMenuBarButtonName.SWITCH_CAMERA_BUTTON,
//                                ZegoMenuBarButtonName.HANG_UP_BUTTON,
//                                ZegoMenuBarButtonName.TOGGLE_MICROPHONE_BUTTON,
//                                ZegoMenuBarButtonName.SWITCH_AUDIO_OUTPUT_BUTTON
//                            )
//                        )
//                        oneOnOneVideoCall
//                    }
//                }
//
//                // Set up call duration configuration with a listener
//                // Set up call duration configuration with a listener
//                config.durationConfig = ZegoCallDurationConfig().apply {
//                    isVisible = false
//                    durationUpdateListener = object : DurationUpdateListener {
//                        override fun onDurationUpdate(seconds: Long) {
//                            Log.d("TAG", "onDurationUpdate() called with: seconds = [$seconds] [$lastActiveTime]")
//                            if (balanceTime !=null) {
//                                foregroundViews.forEach {
//                                    it.setBalanceTime(balanceTime)
//                                    it.updateTime(seconds.toInt())
//                                }
//
//                            }
//                            ZegoUIKitPrebuiltCallService.sendInRoomCommand("active", arrayListOf(null)
//                            ) {}
//                            if(roomID!=null && lastActiveTime!=0L && System.currentTimeMillis() - lastActiveTime > 15 * 1000){
//                                ZegoUIKitPrebuiltCallService.endCall()
//                                config.durationConfig = null;
//                                if(!Helper.checkNetworkConnection()){
//                                    BaseApplication.getInstance()?.setEndCallUpdatePending(true);
//                                }
//                                setupZegoUIKit(userID, userName);
//                            }
//
//                        }
//                    }
//                }
//
//                config.avatarViewProvider = object : ZegoAvatarViewProvider {
//                    override fun onUserIDUpdated(
//                        parent: ViewGroup, uiKitUser: ZegoUIKitUser
//                    ): View {
//                        try {
//                            (parent.context as AppCompatActivity).window.setFlags(
//                                WindowManager.LayoutParams.FLAG_SECURE,
//                                WindowManager.LayoutParams.FLAG_SECURE
//                            )
//                        } catch (e: Exception) {
//                        }
//
//                        val imageView = ImageView(parent.context)
//                        val requestOptions = RequestOptions().circleCrop()
//
//                        Glide.with(parent.context).load(uiKitUser.avatar?.ifEmpty {
//                            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.image
//                        }).apply(requestOptions).into(imageView)
//                        // Set different avatars for different users based on the user parameter in the callback.
//                        if (uiKitUser.userID == userID) {
//                            val avatarUrl =
//                                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.image
//                            if (!avatarUrl.isNullOrEmpty()) {
//                                val requestOptions = RequestOptions().circleCrop()
//                                Glide.with(parent.context).load(avatarUrl).apply(requestOptions)
//                                    .into(imageView)
//                            }
//                        } else {
//                            receivedId = uiKitUser.userID.toInt()
//                            val requestOptions = RequestOptions().circleCrop()
//                            try {
//                                Glide.with(parent.context).load(
//                                    UsersImage(
//                                        profileViewModel,
//                                        uiKitUser.userID.toInt()
//                                    ).execute().get()
//                                ).apply(requestOptions).into(imageView)
//                            } catch (e: Exception) {
//                            }
//
//                        }
//                        return imageView
//                    }
//                }
//
//                config.useSpeakerWhenJoining = true
//                config.hangUpConfirmDialogInfo = ZegoHangUpConfirmDialogInfo()
//                config.audioVideoViewConfig.videoViewForegroundViewProvider =
//                    ZegoForegroundViewProvider { parent, uiKitUser ->
//                        var foregroundView = CustomCallView(parent.context, uiKitUser.userID)
//                        foregroundViews.add(foregroundView)
//                        foregroundView
//                    }
//                config.topMenuBarConfig.buttons.add(ZegoMenuBarButtonName.MINIMIZING_BUTTON)
//                config.topMenuBarConfig.hideByClick = false
//                config.topMenuBarConfig.hideAutomatically = true
//                config.bottomMenuBarConfig.hideByClick = false
//                config.bottomMenuBarConfig.hideAutomatically = false
//                return config
//            }
//        }
//
//        ZegoUIKitPrebuiltCallService.events.callEvents.addInRoomCommandListener { zegoUIKitUser, s ->
//            lastActiveTime = System.currentTimeMillis();
//            if(s.contains("is_direct_call")){
//                try {
//                    BaseApplication.getInstance()?.setReceiverDetailsAvailable(s.split("is_direct_call=")[1]=="true")
//                } catch (e: Exception) {
//                }
//            }
//            if(s.contains(DConstants.REMAINING_TIME)){
//                try {
//                    balanceTime = s.split(DConstants.REMAINING_TIME+"=")[1]
//                } catch (e: Exception) {
//                }
//            }
//        }
//        ZegoSignalingPlugin.getInstance().registerZIMEventHandler(zimEventHandler)
//        ZegoUIKitPrebuiltCallService.init(
//            BaseApplication.getInstance(), appID, appSign, userID, userName, callInvitationConfig
//        )
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        ZegoSignalingPlugin.getInstance().unregisterZIMEventHandler(zimEventHandler)
//    }
//
//    protected fun registerBroadcastReceiver() {
//        val theFilter = IntentFilter()
//        theFilter.addAction(Intent.ACTION_SCREEN_ON)
//        theFilter.addAction(Intent.ACTION_SCREEN_OFF)
//
//        val screenOnOffReceiver: BroadcastReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                val strAction = intent.action
//                if (zimBackup != null) {
//                    if (strAction == Intent.ACTION_SCREEN_OFF && (lastAction == null || lastAction == Intent.ACTION_SCREEN_ON)) {
//                        val keyguardManager =
//                            ZegoConnectionImpl.context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
//
//                        if (keyguardManager.inKeyguardRestrictedInputMode()
//                        ) {
//                            try {
//                                val callNotification = createCallNotification(context)
//                                var hasNotificationPermission = true
//                                if (Build.VERSION.SDK_INT >= 33) {
//                                    hasNotificationPermission =
//                                        (ContextCompat.checkSelfPermission(
//                                            context,
//                                            "android.permission.POST_NOTIFICATIONS"
//                                        )
//                                                == PackageManager.PERMISSION_GRANTED)
//                                }
//
//                                val notificationsEnabled =
//                                    NotificationManagerCompat.from(context)
//                                        .areNotificationsEnabled()
//                                if (hasNotificationPermission && notificationsEnabled) {
//
//                                    NotificationManagerCompat.from(mContext).cancel(PrebuiltCallNotificationManager.incoming_call_notification_id)
//                                    CallInvitationServiceImpl.getInstance().dismissCallNotification()
//                                    NotificationManagerCompat.from(mContext).notify(
//                                        PrebuiltCallNotificationManager.incoming_call_notification_id,
//                                        callNotification
//                                    )
//                                }
//                            } catch (e: Exception) {
//                            }
//                        }
//                    }
//                }
//                lastAction = strAction
//            }
//        }
//
//        BaseApplication.getInstance()?.registerReceiver(screenOnOffReceiver, theFilter)
//    }
//
//    fun createCallNotification(context: Context?): Notification {
//        val title: String
//        val body: String
//        val isVideoCall: Boolean
//
//        val app_state: String
//        val call_id: String
//
//        val zimPushMessage = CallInvitationServiceImpl.getInstance().zimPushMessage
//        if (zimPushMessage == null) {
//            val invitationData = CallInvitationServiceImpl.getInstance().callInvitationData
//            isVideoCall = invitationData.type == ZegoInvitationType.VIDEO_CALL.value
//            val isGroup = invitationData.invitees.size > 1
//            title = PrebuiltCallNotificationManager.getBackgroundNotificationTitle(
//                isVideoCall,
//                isGroup,
//                invitationData.inviter.userName
//            )
//            body = PrebuiltCallNotificationManager.getBackgroundNotificationMessage(
//                isVideoCall,
//                isGroup
//            )
//
//            app_state = "background"
//            call_id = invitationData.invitationID
//        } else {
//            val gson = Gson()
//            val extendedData = gson.fromJson(
//                zimPushMessage.payLoad,
//                PrebuiltCallInviteExtendedData::class.java
//            )
//            isVideoCall = extendedData.type == ZegoInvitationType.VIDEO_CALL.value
//            title = zimPushMessage.title
//            body = zimPushMessage.body
//
//            app_state = "restarted"
//            call_id = zimPushMessage.invitationID
//        }
//        val hashMap = HashMap<String, Any>()
//        hashMap["call_id"] = call_id
//        hashMap["app_state"] = app_state
//        ReportUtil.reportEvent("call/displayNotification", hashMap)
//
//        val invitationConfig = CallInvitationServiceImpl.getInstance()
//            .callInvitationConfig
//        var channelID = MMKV.defaultMMKV().getString("channelID", null)
//        if (channelID == null) {
//            channelID = if (invitationConfig?.notificationConfig != null) {
//                invitationConfig.notificationConfig.channelID
//            } else {
//                PrebuiltCallNotificationManager.incoming_call_channel_id
//            }
//        }
//
//        val clickIntent: PendingIntent = getClickIntent(mContext)
//        val acceptIntent: PendingIntent = getAcceptIntent(mContext)
//        val declineIntent: PendingIntent = getDeclineIntent(mContext)
//        val lockScreenIntent: PendingIntent = getLockScreenIntent(mContext)
//
//        return NotificationUtil.generateNotification(
//            context,
//            channelID,
//            title,
//            body,
//            isVideoCall,
//            (30000 * 2).toLong(),
//            declineIntent,
//            acceptIntent,
//            clickIntent,
//            null,
//            lockScreenIntent
//        )
//    }
//
//    private fun getDeclineIntent(context: Context): PendingIntent {
//        val intent = CallRouteActivity.getDeclineIntent(context)
//        return getPendingActivityIntent(context, intent)
//    }
//
//    private fun getClickIntent(context: Context): PendingIntent {
//        val intent = CallRouteActivity.getContentIntent(context)
//        return getPendingActivityIntent(context, intent)
//    }
//    private fun getAcceptIntent(context: Context): PendingIntent {
//        val intent = CallRouteActivity.getAcceptIntent(context)
//        return getPendingActivityIntent(context, intent)
//    }
//
//
//    private fun getLockScreenIntent(context: Context): PendingIntent {
//        val invitationData = CallInvitationServiceImpl.getInstance().callInvitationData
//        val intent = if (invitationData == null) {
//            CallInviteActivity.getPageIntent(context, CallInviteActivity.PAGE_LOCKSCREEN, null)
//        } else {
//            CallInviteActivity.getPageIntent(context, CallInviteActivity.PAGE_INCOMING, null)
//        }
//        //  use CallRouteActivity.getLockScreenIntent will not working
//        //        Intent intent = CallRouteActivity.getLockScreenIntent(context);
//        return getPendingActivityIntent(context, intent)
//    }
    fun getPendingActivityIntent(context: Context?, intent: Intent?): PendingIntent {
        val openIntent = if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return openIntent
    }
}