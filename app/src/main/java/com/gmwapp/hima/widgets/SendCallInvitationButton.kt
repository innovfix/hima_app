package com.gmwapp.hima.widgets

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
//import com.zego.ve.Log
//import com.zegocloud.uikit.ZegoUIKit
//import com.zegocloud.uikit.plugin.adapter.plugins.signaling.ZegoSignalingPluginNotificationConfig
//import com.zegocloud.uikit.plugin.invitation.ZegoInvitationType
//import com.zegocloud.uikit.prebuilt.call.core.CallInvitationServiceImpl
//import com.zegocloud.uikit.prebuilt.call.core.invite.PrebuiltCallRepository
//import com.zegocloud.uikit.prebuilt.call.core.notification.PrebuiltCallNotificationManager
//import com.zegocloud.uikit.prebuilt.call.invite.internal.CallInviteActivity
//import com.zegocloud.uikit.prebuilt.call.invite.internal.CallStateListener
//import com.zegocloud.uikit.prebuilt.call.invite.internal.ClickListener
//import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoCallUser
//import com.zegocloud.uikit.prebuilt.call.invite.widget.ZegoSendCallInvitationButton
//import com.zegocloud.uikit.service.defines.ZegoUIKitUser
//import timber.log.Timber
//
//class SendCallInvitationButton : ZegoSendCallInvitationButton {
//    private var isVideoCall = false
//    private var customData = ""
//
//    private val resourceID = ""
//    private val sendInvitationListener: ClickListener? = null
//    private val showErrorToast = true
//    private val callStateListener: CallStateListener = object : CallStateListener {
//        override fun onStateChanged(before: Int, after: Int) {
//            Timber.d("onStateChanged() called with: before = [$before], after = [$after]")
//            if (after == PrebuiltCallRepository.CONNECTED) {
//                CallInviteActivity.startCallPage(context)
//            } else {
//                CallInvitationServiceImpl.getInstance().openCamera(false)
//                CallInvitationServiceImpl.getInstance().removeCallStateListener(this)
//            }
//        }
//    }
//
//    constructor(context: Context) : super(context)
//
//    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
//
//    override fun initView() {
//        super.initView()
//    }
//
//    override fun generateCallID(): String? {
//        var callID: String? = null
//        val userID = ZegoUIKit.getLocalUser().userID
//        if (userID != null) {
//            callID = "call_" + userID + "_" + System.currentTimeMillis()
//        }
//        return callID
//    }
//
//    override fun setIsVideoCall(isVideoCall: Boolean) {
//        this.isVideoCall = isVideoCall
//        if (isVideoCall) {
//            setType(ZegoInvitationType.VIDEO_CALL)
//        } else {
//            setType(ZegoInvitationType.VOICE_CALL)
//        }
//    }
//
//    override fun setType(type: Int) {
//        throw UnsupportedOperationException("unSupport operation")
//    }
//
//    override fun setCustomData(customData: String?) {
//        this.customData = customData!!
//    }
//
//    override fun invokedWhenClick() {
//        val translationText = CallInvitationServiceImpl.getInstance()
//            .callInvitationConfig.translationText
//        val invitationType = ZegoInvitationType.getZegoInvitationType(type)
//        CallInvitationServiceImpl.getInstance()
//            .sendInvitation(
//                invitees, invitationType, customData, timeout,
//                this.callID, getSendInvitationConfig()
//            ) { result ->
//
//                val uiKitUser = ZegoUIKit.getLocalUser()
//                val code = result["code"] as Int
//                val message = result["message"] as String?
//                val errorInvitees = result["errorInvitees"] as List<ZegoUIKitUser>?
//
//                if (code == 0) {
//                    if (errorInvitees!!.isEmpty() || errorInvitees.size != invitees.size) {
//                        CallInvitationServiceImpl.getInstance()
//                            .addCallStateListener(callStateListener)
//                    }
//                    if (!errorInvitees.isEmpty()) {
//                        var error: String? = ""
//                        if (translationText != null) {
//                            error = translationText.sendCallButtonErrorOffLine
//                        }
//                        val sb = StringBuilder(error)
//                        var count = 0
//                        for (errorInvitee in errorInvitees) {
//                            sb.append(errorInvitee.userID)
//                            sb.append(" ")
//                            count += 1
//                            if (count == 5) {
//                                sb.append("...")
//                                break
//                            }
//                        }
//                        showError(-5, sb.toString())
//                    }
//                } else {
//                    var error: String? = ""
//                    if (translationText != null) {
//                        error = translationText.sendCallButtonError
//                    }
//                    showError(code, String.format(error!!, code, message))
//                }
//                if (sendInvitationListener != null) {
//                    val callbackErrorInvitees: MutableList<ZegoCallUser> =
//                        ArrayList()
//                    if (errorInvitees != null && errorInvitees.size > 0) {
//                        for (errorInvitee in errorInvitees) {
//                            val zegoCallUser = ZegoCallUser(
//                                errorInvitee.userID,
//                                errorInvitee.userName
//                            )
//                            callbackErrorInvitees.add(zegoCallUser)
//                        }
//                    }
//                    sendInvitationListener.onClick(code, message, callbackErrorInvitees)
//                }
//            }
//    }
//
//    private fun getSendInvitationConfig(): ZegoSignalingPluginNotificationConfig {
//        val offlineMessage = PrebuiltCallNotificationManager.getBackgroundNotificationMessage(
//            isVideoCall,
//            invitees.size > 1
//        )
//
//        val uiKitUser = ZegoUIKit.getLocalUser()
//        var userName: String? = ""
//        if (uiKitUser != null) {
//            userName = uiKitUser.userName
//        }
//        val offlineTitle = PrebuiltCallNotificationManager.getBackgroundNotificationTitle(
//            isVideoCall,
//            invitees.size > 1, userName
//        )
//
//        val offlineResourceID = if (TextUtils.isEmpty(resourceID)) {
//            "zego_call"
//        } else {
//            resourceID
//        }
//
//        val notificationConfig = ZegoSignalingPluginNotificationConfig()
//        notificationConfig.resourceID = offlineResourceID
//        notificationConfig.title = offlineTitle
//        notificationConfig.message = offlineMessage
//        return notificationConfig
//    }
//}

class SendCallInvitationButton {

}