package com.gmwapp.hima.receivers

//import android.content.Context
//import android.media.AudioManager
//import android.media.MediaPlayer
//import android.text.TextUtils
//import android.util.Log
//import androidx.core.content.ContextCompat.getSystemService
//import com.gmwapp.hima.BaseApplication
//import com.zegocloud.uikit.prebuilt.call.MyZPNsReceiver
//import im.zego.zpns.entity.ZPNsMessage
//import im.zego.zpns.enums.ZPNsConstants.PushSource
//
//
//class CallReceiver : MyZPNsReceiver() {
//    private var mediaPlayer: MediaPlayer? = null
//
//    override fun onThroughMessageReceived(context: Context, message: ZPNsMessage) {
//        super.onThroughMessageReceived(context, message)
//        try {
//            val pushMessage = getZIMPushMessage(message)
//            if (message.pushSource == PushSource.FCM) {
//                if(TextUtils.isEmpty(pushMessage?.title)){
//                    BaseApplication.getInstance()?.getMediaPlayer()?.stop()
//                }else if (!TextUtils.isEmpty(pushMessage?.invitationID)) {
//                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
//                    audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.let {
//                        audioManager.setStreamVolume(
//                            AudioManager.STREAM_MUSIC,
//                            it,
//                            0
//                        )
//                    }
//                    val resID = context.resources.getIdentifier("rhythm", "raw", context.packageName)
//                    BaseApplication.getInstance()?.getMediaPlayer()?.stop()
//                    mediaPlayer = MediaPlayer.create(context, resID)
//                    BaseApplication.getInstance()?.setMediaPlayer(mediaPlayer)
//                    mediaPlayer?.isLooping = true
//                    mediaPlayer?.start()
//                }else{
//                    BaseApplication.getInstance()?.getMediaPlayer()?.stop()
//                }
//            }else{
//                BaseApplication.getInstance()?.getMediaPlayer()?.stop()
//            }
//        } catch (e: Exception) {
//            BaseApplication.getInstance()?.getMediaPlayer()?.stop()
//        }
//    }
//
//}