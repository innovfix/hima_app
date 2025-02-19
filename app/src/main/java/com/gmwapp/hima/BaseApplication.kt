package com.gmwapp.hima

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.content.Context

import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.utils.DPreferences
import com.google.firebase.FirebaseApp
import com.onesignal.OneSignal
import com.onesignal.common.OneSignalUtils
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotification
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import com.zegocloud.uikit.prebuilt.call.core.CallInvitationServiceImpl
import com.zegocloud.uikit.prebuilt.call.core.notification.RingtoneManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@HiltAndroidApp
class BaseApplication : Application(), Configuration.Provider {
    private var isReceiverDetailsAvailable: Boolean = false
    private var startTime: String? = null
    private var callUserId: String? = null
    private var callUserName: String? = null
    private var callId: Int? = null
    private var mPreferences: DPreferences? = null
    private var called: Boolean? = null
    private var callType: String? = null
    private var roomId: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var endCallUpdatePending: Boolean? = null
    val ONESIGNAL_APP_ID = "2c7d72ae-8f09-48ea-a3c8-68d9c913c592"
    private val lifecycleCallbacks: ActivityLifecycleCallbacks =
        object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

            }

            override fun onActivityStarted(activity: Activity) {

            }

            override fun onActivityResumed(activity: Activity) {

                if(getInstance()?.getPrefs()?.getUserData()?.gender == DConstants.MALE) {
                    CallInvitationServiceImpl.getInstance().hideIncomingCallDialog()
                    RingtoneManager.stopRingTone()
                }
            }

            override fun onActivityPaused(p0: Activity) {
            }

            override fun onActivityStopped(p0: Activity) {
            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {

            }

        }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    companion object {
        private var mInstance: BaseApplication? = null


        fun getInstance(): BaseApplication? {
            return mInstance
        }



    }

    override fun onCreate() {
        super.onCreate()
        mInstance = this
        mPreferences = DPreferences(this)
        FirebaseApp.initializeApp(this)
        registerReceiver(ShutdownReceiver(), IntentFilter(Intent.ACTION_SHUTDOWN));
        if(BuildConfig.DEBUG) {
            OneSignal.Debug.logLevel = LogLevel.VERBOSE
        }

        // OneSignal Initialization
        OneSignal.initWithContext(this, ONESIGNAL_APP_ID)


        // requestPermission will show the native Android notification permission prompt.
        // NOTE: It's recommended to use a OneSignal In-App Message to prompt instead.
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(false)
        }
        var userId = getInstance()?.getPrefs()
            ?.getUserData()?.id.toString() // Set user_id
        Log.d("userIDCheck", "Logging in with userId: $userId")


        if (!userId.isNullOrEmpty()) {
            Log.d("OneSignal", "Logging in with userId: $userId")
            OneSignal.login(userId)
        } else {
            Log.e("OneSignal", "User ID is null or empty, cannot log in.")
        }

        registerActivityLifecycleCallbacks(lifecycleCallbacks)



    }


    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks?) {
        super.registerActivityLifecycleCallbacks(callback)
    }

    fun getPrefs(): DPreferences? {
        return mPreferences
    }

    fun setCalled(called: Boolean) {
        this.called = called
    }

    fun isCalled(): Boolean? {
        return this.called
    }

    fun setRoomId(roomId: String?) {
        this.roomId = roomId
    }

    fun getRoomId(): String? {
        return this.roomId
    }

    fun setMediaPlayer(mediaPlayer: MediaPlayer?) {
        this.mediaPlayer = mediaPlayer
    }

    fun getMediaPlayer(): MediaPlayer? {
        return this.mediaPlayer
    }

    fun setReceiverDetailsAvailable(isReceiverDetailsAvailable: Boolean) {
        this.isReceiverDetailsAvailable = isReceiverDetailsAvailable
    }

    fun isReceiverDetailsAvailable(): Boolean {
        return this.isReceiverDetailsAvailable
    }

    fun setCallUserId(callUserId: String?) {
        this.callUserId = callUserId
    }

    fun getCallUserId(): String? {
        return this.callUserId
    }

    fun setCallUserName(callUserName: String?) {
        this.callUserName = callUserName
    }

    fun getCallUserName(): String? {
        return this.callUserName
    }

    fun setStartTime(startTime: String?) {
        this.startTime = startTime
    }

    fun getStartTime(): String? {
        return this.startTime
    }

    fun setCallId(callId: Int?) {
        this.callId = callId
    }

    fun getCallId(): Int? {
        return this.callId
    }

    fun setCallType(callType: String?) {
        this.callType = callType
    }

    fun getCallType(): String? {
        return this.callType
    }

    fun setEndCallUpdatePending(endCallUpdatePending: Boolean?) {
        this.endCallUpdatePending = endCallUpdatePending
    }

    fun isEndCallUpdatePending(): Boolean? {
        return this.endCallUpdatePending
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

}
