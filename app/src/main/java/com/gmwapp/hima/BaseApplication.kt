package com.gmwapp.hima

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Bundle
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.net.Uri

import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.FcmNotificationRepository
import com.gmwapp.hima.utils.DPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.zoho.salesiqembed.ZohoSalesIQ
//import com.zegocloud.uikit.prebuilt.call.core.CallInvitationServiceImpl
//import com.zegocloud.uikit.prebuilt.call.core.notification.RingtoneManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


import com.appsflyer.AppsFlyerLib;
import com.appsflyer.AppsFlyerConversionListener;

import java.util.Map;


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
   // val ONESIGNAL_APP_ID = "2c7d72ae-8f09-48ea-a3c8-68d9c913c592"
   val ONESIGNAL_APP_ID = "5cd4154a-1ece-4c3b-b6af-e88bafee64cd"

    //val testingOneSingalAppId = "b5aee4f0-ef38-4116-a04d-ee279ee1f11f"
    private lateinit var sharedPreferences: SharedPreferences

    private var currentActivity: Activity? = null

    private var senderId: Int? = null
    private var callTypeForSplashActivity: String? = null
    private var channelName: String? = null
    private var callIdForSplashActivity: Int? = null
    private var incomingCall: Boolean = false



    private val lifecycleCallbacks: ActivityLifecycleCallbacks =
        object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT


            }

            override fun onActivityStarted(activity: Activity) {
                currentActivity = activity
                Log.d("myCurrentActivity","$currentActivity")
            }

            override fun onActivityResumed(activity: Activity) {

                currentActivity = activity

                if(getInstance()?.getPrefs()?.getUserData()?.gender == DConstants.MALE) {
//                    CallInvitationServiceImpl.getInstance().hideIncomingCallDialog()
//                    RingtoneManager.stopRingTone()
                }
            }

            override fun onActivityPaused(p0: Activity) {
            }

            override fun onActivityStopped(p0: Activity) {
            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }

        }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var fcmNotificationRepository: FcmNotificationRepository

    companion object {
        private var mInstance: BaseApplication? = null


        fun getInstance(): BaseApplication? {
            return mInstance
        }




            lateinit var firebaseAnalytics: FirebaseAnalytics
                private set





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

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        appflyer()

        FacebookSdk.setApplicationId(getString(R.string.facebook_app_id))
        FacebookSdk.sdkInitialize(applicationContext)
        AppEventsLogger.activateApp(this)

        if (BuildConfig.DEBUG) {
            OneSignal.Debug.logLevel = LogLevel.VERBOSE
            FacebookSdk.setIsDebugEnabled(true)
            FacebookSdk.addLoggingBehavior(com.facebook.LoggingBehavior.APP_EVENTS)
        }


        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // OneSignal Initialization
        OneSignal.initWithContext(this, ONESIGNAL_APP_ID)


        // requestPermission will show the native Android notification permission prompt.
        // NOTE: It's recommended to use a OneSignal In-App Message to prompt instead.
//        CoroutineScope(Dispatchers.IO).launch {
//            OneSignal.Notifications.requestPermission(false)
//        }
        var userId = getInstance()?.getPrefs()
            ?.getUserData()?.id.toString() // Set user_id
        Log.d("userIDCheck", "Logging in with userId: $userId")

      //  initZoho()





//        if (!userId.isNullOrEmpty() && userId != "null") {
//            Log.d("OneSignalLogin", "Logging in with userId: $userId")
//
//            OneSignal.login(userId)
//            val externalId = OneSignal.User.externalId
//            Log.d("OneSignalExternalId", "externalId : $externalId")
//
//            OneSignal.User.pushSubscription.optIn()
//        } else {
//            Log.e("OneSignalLogin", "User ID is null or invalid.")
//        }

//        CoroutineScope(Dispatchers.Main).launch {
//            delay(2000) // wait to ensure OneSignal is initialized fully
//
//            // 1. FULL RESET before login
//            OneSignal.logout()
//            OneSignal.User.pushSubscription.optOut()
//
//            // 2. Fetch user ID
//            val userId = getInstance()?.getPrefs()?.getUserData()?.id.toString()
//
//            if (!userId.isNullOrEmpty() && userId != "null") {
//                Log.d("OneSignalFix", "Attempting clean login with userId: $userId")
//
//                // 3. Force fresh login
//                OneSignal.login(userId)
//
//                // 4. Re-subscribe and assign external ID
//                OneSignal.User.pushSubscription.optIn()
//
//                // 5. Prompt notification permission (Android 13+)
//                OneSignal.Notifications.requestPermission(true)
//
//                // 6. Debug logs to confirm status
//                delay(1000)
//                Log.d("OneSignalFix", "externalId: ${OneSignal.User.externalId}")
//                Log.d("OneSignalFix", "pushToken: ${OneSignal.User.pushSubscription.token}")
//                Log.d("OneSignalFix", "optedIn: ${OneSignal.User.pushSubscription.optedIn}")
//            } else {
//                Log.e("OneSignalFix", "Invalid user ID: $userId")
//            }
//        }


        registerActivityLifecycleCallbacks(lifecycleCallbacks)



    }

    fun getCurrentActivity(): Activity? {
        return currentActivity
    }


    fun playIncomingCallSound() {
        stopRingtone()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE) // makes it respect silent mode
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val uri = Uri.parse("android.resource://${packageName}/${R.raw.rhythm}")
        mediaPlayer = MediaPlayer()
        mediaPlayer?.apply {
            setAudioAttributes(audioAttributes)
            setDataSource(applicationContext, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    fun playSendGiftSound() {
        stopRingtone()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE) // makes it respect silent mode
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val uri = Uri.parse("android.resource://${packageName}/${R.raw.gift_tune}")
        mediaPlayer = MediaPlayer()
        mediaPlayer?.apply {
            setAudioAttributes(audioAttributes)
            setDataSource(applicationContext, uri)
            prepare()
            start()
        }
    }


    fun isRingtonePlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }


    fun stopRingtone() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()  // Release resources
        }
        mediaPlayer = null  // Ensure it's set to null after stopping
        Log.d("MediaPlayer", "Ringtone stopped and released.")
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

    fun saveSenderId(senderId: Int) {
        sharedPreferences.edit().putInt("SENDER_ID", senderId).apply()
    }

    fun getSenderId(): Int {
        return sharedPreferences.getInt("SENDER_ID", -1)
    }


    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()


    fun setIncomingCall(senderId: Int, callType: String, channelName: String, callId: Int) {
        this.senderId = senderId
        this.callTypeForSplashActivity = callType
        this.channelName = channelName
        this.callIdForSplashActivity = callId
        this.incomingCall = true
    }

    fun clearIncomingCall() {
        this.incomingCall = false
    }

    fun isIncomingCall(): Boolean = incomingCall
    fun getSenderIdForSplashActivity(): Int = senderId ?: -1
    fun getCallTypeForSplashActivity(): String = callTypeForSplashActivity.toString()
    fun getChannelName(): String = channelName.toString()
    fun getCallIdForSplashActivity(): Int? = callIdForSplashActivity

    fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = applicationContext.packageName

        for (appProcess in appProcesses) {
            if (appProcess.processName == packageName &&
                appProcess.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true  // App is in foreground
            }
        }
        return false  // App is in background
    }

    fun appflyer() {
        val conversionDataListener = object : AppsFlyerConversionListener {
            override fun onConversionDataSuccess(conversionData: MutableMap<String, Any>?) {
                conversionData?.let {
                    for ((key, value) in it) {
                        Log.d("AppsFlyer", "Conversion data: $key = $value")
                    }
                } ?: Log.d("AppsFlyer", "Conversion data is null")
            }

            override fun onConversionDataFail(errorMessage: String?) {
                Log.e("AppsFlyer", "Conversion data failure: $errorMessage")
            }

            override fun onAppOpenAttribution(attributionData: MutableMap<String, String>?) {
                attributionData?.let {
                    for ((key, value) in it) {
                        Log.d("AppsFlyer", "Attribution data: $key = $value")
                    }
                } ?: Log.d("AppsFlyer", "Attribution data is null")
            }

            override fun onAttributionFailure(errorMessage: String?) {
                Log.e("AppsFlyer", "Attribution failure: $errorMessage")
            }
        }

        AppsFlyerLib.getInstance().init("a3v6JFHivKze4bos9RQMf8", conversionDataListener, applicationContext)
        AppsFlyerLib.getInstance().start(applicationContext)
    }

    fun initZoho(){
        var userGender = getInstance()?.getPrefs()?.getUserData()?.gender

        if (userGender=="female") {
            ZohoSalesIQ.init(
                this,
                "2VOIpTcfcgE%2BUhLrpfL5Sdw0%2FON4sOEn3pE5EmwuYzOaU8BLhSO8qhPCANC9LnQa_in",
                "xHGPBNAi6lBPVQB2vh987S6JIsEJi2TiySCStaNGyaA0yKccjOKLb0DSeC9vfeZ3XUwTbvVRwZtx9nig%2FzwmR2tPURa1wqMQ7ShS5BnSf6nDpAt6FvbISA%3D%3D"
            );
        }
    }
}