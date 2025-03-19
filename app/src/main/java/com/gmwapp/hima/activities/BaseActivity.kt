package com.gmwapp.hima.activities

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.PowerManager
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SmoothScroller
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.dagger.GetRemainingTimeEvent
import com.gmwapp.hima.dagger.UnauthorizedEvent
import com.gmwapp.hima.dagger.UpdateRemainingTimeEvent
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.utils.Helper
import com.gmwapp.hima.utils.UsersImage
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.widgets.CustomCallView
import com.gmwapp.hima.workers.CallUpdateWorker
//import com.tencent.mmkv.MMKV
//import com.zegocloud.uikit.ZegoUIKit
//import com.zegocloud.uikit.components.audiovideo.ZegoAvatarViewProvider
//import com.zegocloud.uikit.components.audiovideo.ZegoForegroundViewProvider
//import com.zegocloud.uikit.plugin.invitation.ZegoInvitationType
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallConfig
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
//import com.zegocloud.uikit.prebuilt.call.config.DurationUpdateListener
//import com.zegocloud.uikit.prebuilt.call.config.ZegoCallDurationConfig
//import com.zegocloud.uikit.prebuilt.call.config.ZegoHangUpConfirmDialogInfo
//import com.zegocloud.uikit.prebuilt.call.config.ZegoMenuBarButtonName
//import com.zegocloud.uikit.prebuilt.call.core.CallInvitationServiceImpl
//import com.zegocloud.uikit.prebuilt.call.core.invite.ZegoCallInvitationData
//import com.zegocloud.uikit.prebuilt.call.core.invite.advanced.ZegoCallInvitationInCallingConfig
//import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
//import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoUIKitPrebuiltCallConfigProvider
//import com.zegocloud.uikit.service.defines.ZegoUIKitUser
import dagger.hilt.android.AndroidEntryPoint
//import im.zego.connection.internal.ZegoConnectionImpl.context
//import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.TimeZone
import kotlin.math.abs


@AndroidEntryPoint
open class BaseActivity : AppCompatActivity() {
    private var wakeLock:PowerManager.WakeLock? =null
    protected var callType: String? = null
    protected var balanceTime: String? = null
    protected var roomID: String? = null
    protected var lastActiveTime: Long? = null
    private var foregroundViews= arrayListOf<CustomCallView>()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("HH:mm:ss").apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST time zone
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(object :
            ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val app = BaseApplication.getInstance()
                if (Helper.checkNetworkConnection() && app?.isEndCallUpdatePending() == true) {
                   // ZegoUIKitPrebuiltCallService.endCall()
                    app.setEndCallUpdatePending(null)
                }
            }
        })
    }

//    override fun onStart() {
//        super.onStart()
//        EventBus.getDefault().register(this)
//    }

    override fun onResume() {
        super.onResume()
       // getRemainingTime()
        if (BaseApplication.getInstance()
                ?.getRoomId() != null
        ) {
          //  resumeZegoCloud()
        }
    }
//
//    protected open fun resumeZegoCloud(){
//        addRoomStateChangedListener()
//    }
//
//    protected open fun addRoomStateChangedListener() {
//        ZegoUIKit.addRoomStateChangedListener { room, reason, _, _ ->
//            when (reason) {
//                ZegoRoomStateChangedReason.LOGINED -> {
//                    if(CallInvitationServiceImpl.getInstance().callInvitationData.type == 1) {
//                        activateWakeLock()
//                    }
//                }
//
//                ZegoRoomStateChangedReason.LOGOUT -> {
//                    releaseWakeLock()
//                    lifecycleScope.launch {
//                        lastActiveTime = null
//                        delay(500)
//                        if (BaseApplication.getInstance()?.getRoomId() != null) {
//                            roomID = null
//                            val applicationInstance = BaseApplication.getInstance()
//                            applicationInstance?.setRoomId(null)
//                            applicationInstance?.setCallId(null)
//                            var endTime = dateFormat.format(Date()) // Set call end time in IST
//
//                            val constraints =
//                                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
//                                    .build()
//                            val data: Data = Data.Builder().putInt(
//                                DConstants.USER_ID,
//                                applicationInstance?.getPrefs()?.getUserData()?.id
//                                    ?: 0
//                            ).putInt(DConstants.CALL_ID, applicationInstance?.getCallId()?:0)
//                                .putString(DConstants.STARTED_TIME, applicationInstance?.getStartTime())
//                                .putBoolean(DConstants.IS_INDIVIDUAL,
//                                    applicationInstance?.isReceiverDetailsAvailable() == true
//                                )
//                                .putString(DConstants.ENDED_TIME, endTime).build()
//                            val oneTimeWorkRequest = OneTimeWorkRequest.Builder(
//                                CallUpdateWorker::class.java
//                            ).setInputData(data).setConstraints(constraints).build()
//                            WorkManager.getInstance(this@BaseActivity)
//                                .enqueue(oneTimeWorkRequest)
//                            val intent = Intent(this@BaseActivity, ReviewActivity::class.java)
//                            intent.putExtra(DConstants.RECEIVER_NAME, applicationInstance?.getCallUserName())
//                            intent.putExtra(DConstants.RECEIVER_ID, applicationInstance?.getCallUserId())
//                            startActivity(intent)
//                        }
//                    }
//                }
//
//
//                else -> {
//                }
//            }
//        }
//    }
//
//    protected fun getRemainingTime() {
//        val instance = BaseApplication.getInstance()
//        if (instance?.getRoomId() != null) {
//            instance?.getPrefs()?.getUserData()?.id?.let {
//                instance.getCallType()?.let { it1 ->
//                    profileViewModel.getRemainingTime(
//                            it, it1,object : NetworkCallback<GetRemainingTimeResponse> {
//                                override fun onResponse(
//                                    call: Call<GetRemainingTimeResponse>, response: Response<GetRemainingTimeResponse>
//                                ) {
//                                    val body = response?.body()
//                                    if (body?.success == true) {
//                                        balanceTime = body.data?.remaining_time
//                                        EventBus.getDefault().post(UpdateRemainingTimeEvent(balanceTime));
//
//                                        ZegoUIKitPrebuiltCallService.sendInRoomCommand(
//                                            DConstants.REMAINING_TIME + "=" + balanceTime, arrayListOf(null)
//                                        ) {}
//                                    }
//                                }
//
//                                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
//                                }
//
//                                override fun onNoNetwork() {
//                                }
//                            }
//                        )
//                    }
//                }
//        }
//    }
//
//    protected fun activateWakeLock(){
//        try {
//            val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
//            wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "Hima:Calling")
//            wakeLock?.acquire()
//        } catch (e: Exception) {
//        }
//    }

    protected fun releaseWakeLock(){
        try {
            wakeLock?.release();
        } catch (e: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    fun showErrorMessage(message: String) {
        if (message == DConstants.NO_NETWORK) {
            Toast.makeText(
                this@BaseActivity, getString(R.string.please_try_again_later), Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this@BaseActivity, message, Toast.LENGTH_LONG
            ).show()
        }
    }

//    @Subscribe(threadMode = ThreadMode.MAIN)
//    fun onGetRemainingTimeEvent(event: GetRemainingTimeEvent?) {
//        getRemainingTime()
//    }
//
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    fun onMessageEvent(event: UnauthorizedEvent?) {
//        Toast.makeText(
//            this@BaseActivity,
//            getString(R.string.please_login_again_to_continue),
//            Toast.LENGTH_SHORT
//        ).show()
//        MMKV.defaultMMKV().remove("user_id");
//        MMKV.defaultMMKV().remove("user_name");
//        ZegoUIKitPrebuiltCallService.unInit()
//            val prefs = BaseApplication.getInstance()?.getPrefs()
//        prefs?.clearUserData()
//        val intent = Intent(this, LoginActivity::class.java)
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
//        startActivity(intent)
//    }
//
//    fun setupZegoUIKit(Userid: Any, userName: String) {
//        val appID: Long = 364167780
//        val appSign = "3dd4f50fa22240d5943b75a843ef9711c7fa0424e80f8eb67c2bc0552cd1c2f3"
//        val userID: String = Userid.toString()
//
//        val callInvitationConfig = ZegoUIKitPrebuiltCallInvitationConfig()
//
//        callInvitationConfig.callingConfig = ZegoCallInvitationInCallingConfig()
//        callInvitationConfig.callingConfig.canInvitingInCalling = false
//        callInvitationConfig.callingConfig.onlyInitiatorCanInvite = true
//        callInvitationConfig.endCallWhenInitiatorLeave = true
//        callInvitationConfig.outgoingCallRingtone = "silent"
//        callInvitationConfig.incomingCallRingtone = "silent"
//        callInvitationConfig.provider = object : ZegoUIKitPrebuiltCallConfigProvider {
//
//            override fun requireConfig(invitationData: ZegoCallInvitationData): ZegoUIKitPrebuiltCallConfig {
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
//                config.durationConfig = ZegoCallDurationConfig().apply {
//                    isVisible = false
//                    durationUpdateListener = object : DurationUpdateListener {
//                        override fun onDurationUpdate(seconds: Long) {
//                            Log.d(
//                                "TAG",
//                                "onDurationUpdate() called with: seconds = [$seconds]"
//                            )
//                            foregroundViews.forEach {
//                                it?.updateTime(seconds.toInt())
//                            }
//
//                            ZegoUIKitPrebuiltCallService.sendInRoomCommand(
//                                "active&is_direct_call="+BaseApplication.getInstance()?.isReceiverDetailsAvailable()
//                                +DConstants.REMAINING_TIME + "="+balanceTime, arrayListOf(null)
//                            ) {}
//                            if (roomID != null && lastActiveTime != null && System.currentTimeMillis() - lastActiveTime!! > 15 * 1000) {
//                                ZegoUIKitPrebuiltCallService.endCall()
//                                config.durationConfig = null
//                                if (!Helper.checkNetworkConnection()) {
//                                    BaseApplication.getInstance()?.setEndCallUpdatePending(true)
//                                }
//                            }
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
//                        val imageView = ImageView(parent.context)
//                        val requestOptions = RequestOptions().circleCrop()
//
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
//                            val requestOptions = RequestOptions().circleCrop()
//                            Glide.with(parent.context).load(
//                                UsersImage(
//                                    profileViewModel, uiKitUser.userID.toInt()
//                                ).execute().get()
//                            ).apply(requestOptions).into(imageView)
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
//                        foregroundView?.setContext(this@BaseActivity)
//                        foregroundView?.setBalanceTime(balanceTime)
//                        foregroundViews.add(foregroundView)
//
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
//        ZegoUIKitPrebuiltCallService.events.callEvents.addInRoomCommandListener { zegoUIKitUser, s ->
//            lastActiveTime = System.currentTimeMillis()
//        }
//
//        ZegoUIKitPrebuiltCallService.init(
//            BaseApplication.getInstance(), appID, appSign, userID, userName, callInvitationConfig
//        )
//
//    }

    fun setCenterLayoutManager(recyclerView: RecyclerView) {
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)

                val itemPosition = parent.getChildAdapterPosition(view)

                val itemWidth = view.layoutParams.width
                val offset = (parent.width - itemWidth) / 2

                if (itemPosition == 0) {
                    outRect.left = offset
                } else if (itemPosition == state.itemCount - 1) {
                    outRect.right = offset
                }
            }
        })
        val centerLayoutManager = CenterLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.setLayoutManager(centerLayoutManager)
    }
}

open class CenterLayoutManager : LinearLayoutManager {
    private val mShrinkAmount = 0.20f
    private val mShrinkDistance = 0.9f

    constructor(context: Context?) : super(context)

    constructor(context: Context?, orientation: Int, reverseLayout: Boolean) : super(
        context, orientation, reverseLayout
    )

    constructor(
        context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
        lp.width = width / 2
        lp.height = width / 2
        return true
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView, state: RecyclerView.State, position: Int
    ) {
        val smoothScroller: SmoothScroller = CenterSmoothScroller(recyclerView.context)
        smoothScroller.targetPosition = position
        startSmoothScroll(smoothScroller)
    }

    private class CenterSmoothScroller(context: Context?) : LinearSmoothScroller(context) {
        override fun calculateDtToFit(
            viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int
        ): Int {
            return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
        }
    }

    override fun canScrollHorizontally(): Boolean {
        return true
    }

    override fun scrollHorizontallyBy(
        dx: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?
    ): Int {
        val orientation = orientation
        if (orientation == HORIZONTAL) {
            val scrolled = super.scrollHorizontallyBy(dx, recycler, state)

            val midpoint = width / 2f
            val d0 = 0f
            val d1 = mShrinkDistance * midpoint
            val s0 = 1f
            val s1 = 1f - mShrinkAmount
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val childMidpoint = (getDecoratedRight(child!!) + getDecoratedLeft(child)) / 2f
                val d = d1.coerceAtMost(abs(midpoint - childMidpoint))
                val scale = s0 + (s1 - s0) * (d - d0) / (d1 - d0)
                child.scaleX = scale
                child.scaleY = scale

            }
            return scrolled
        } else {
            return 0
        }

    }
}
