package com.gmwapp.hima

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Bundle
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import android.view.WindowManager
import androidx.lifecycle.MutableLiveData
import androidx.work.Configuration
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.FcmNotificationRepository
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.utils.Helper
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.zoho.salesiqembed.ZohoSalesIQ
//import com.zegocloud.uikit.prebuilt.call.core.CallInvitationServiceImpl
//import com.zegocloud.uikit.prebuilt.call.core.notification.RingtoneManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject


import com.appsflyer.AppsFlyerLib;
import com.appsflyer.AppsFlyerConversionListener;
import com.gmwapp.hima.activities.ChatActivityInHouse
import com.gmwapp.hima.activities.ChatListActivity
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.fragments.FriendsTabFragment
import com.gmwapp.hima.socket.SocketManager
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import org.json.JSONObject


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

    val networkConnectedLiveData = MutableLiveData<Boolean>()
    private var appConnectivityManager: ConnectivityManager? = null
    private var appNetworkCallback: ConnectivityManager.NetworkCallback? = null
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


    var messageCameWhenIsAlive = 0
    var freeCoinsStatusApiCalled = false

    // Tracks how many activities are currently in the started state.
    // Used to detect when the app moves between background and foreground.
    private var startedActivityCount = 0

    private val lifecycleCallbacks: ActivityLifecycleCallbacks =
        object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (isFullscreenActivity(activity)) {
                    // Skip for Android 8.0 (Oreo) or use check if activity is fullscreen
                    if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
                ZohoSalesIQ.showLauncher(false)
            }

            override fun onActivityStarted(activity: Activity) {
                currentActivity = activity
                Log.d("myCurrentActivity","$currentActivity")

                // App entered foreground (count went 0 -> 1).
                // If a push notification was received in the last 5 min and not yet
                // counted, record an "open" conversion.
                if (startedActivityCount == 0) {
                    checkAndTrackNotificationOpen()
                }
                startedActivityCount++
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
                if (startedActivityCount > 0) {
                    startedActivityCount--
                }
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
    lateinit var fcmNotificationRepository: FcmNotificationRepository

    companion object {
        private var mInstance: BaseApplication? = null


        fun getInstance(): BaseApplication? {
            return mInstance
        }




        lateinit var firebaseAnalytics: FirebaseAnalytics
            private set

        /**
         * DND check that can be called from notification listeners (FCM + OneSignal).
         */
        fun isDndActiveStatic(userData: com.gmwapp.hima.retrofit.responses.UserData?): Boolean {
            if (userData == null) return false
            if ((userData.dnd_enabled ?: 0) != 1) return false
            val until = userData.dnd_until ?: return false
            return try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                val expiry = sdf.parse(until) ?: return false
                expiry.time > System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e("DND", "Failed to parse dnd_until=$until: ${e.message}")
                false
            }
        }





    }

    override fun onCreate() {
        super.onCreate()
        
        // Test log to verify SocketIOCheck tag is working
        Log.d("SocketIOCheck", "🎯 BaseApplication.onCreate() STARTED - SocketIOCheck tag is working!")
        
        mInstance = this
        mPreferences = DPreferences(this)
        registerAppNetworkConnectivity()
        FirebaseApp.initializeApp(this)
        
        // ✅ Connect to "himadatabase" explicitly with offline persistence
        val firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)  // Enable offline cache
            .build()
        
        // Get Firestore instance for "himadatabase" database
        val db = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "himadatabase")
        db.firestoreSettings = firestoreSettings
        
        registerReceiver(ShutdownReceiver(), IntentFilter(Intent.ACTION_SHUTDOWN));
        if(BuildConfig.DEBUG) {
            OneSignal.Debug.logLevel = LogLevel.VERBOSE
        }

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        appflyer()

        FacebookSdk.setApplicationId(getString(R.string.facebook_app_id))
        FacebookSdk.sdkInitialize(applicationContext)
        AppEventsLogger.activateApp(this)

        // Snapchat App Ads Kit - Install Tracking
        SnapInitHelper.init(this, listOf(getString(R.string.snap_app_id)))
        Log.d("SnapchatSDK", "Snapchat App Ads Kit initialized for install tracking")

        // ========== GET DEBUG KEY HASH FOR META ==========
        try {
            val info: PackageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            )
            info.signatures?.let { signatures ->
                for (signature in signatures) {
                    val md = MessageDigest.getInstance("SHA")
                    md.update(signature.toByteArray())
                    val hashKey = String(Base64.encode(md.digest(), Base64.DEFAULT))
                    
                    Log.e("🔑 DEBUG_HASH", "========================================")
                    Log.e("🔑 DEBUG_HASH", "YOUR DEBUG KEY HASH FOR META:")
                    Log.e("🔑 DEBUG_HASH", hashKey.trim())
                    Log.e("🔑 DEBUG_HASH", "========================================")
                    Log.e("🔑 DEBUG_HASH", "Copy this hash to Meta dashboard!")
                    Log.e("🔑 DEBUG_HASH", "Settings → Basic → Android → Key Hashes")
                }
            }
        } catch (e: NoSuchAlgorithmException) {
            Log.e("🔑 DEBUG_HASH", "Error getting key hash: ${e.message}")
        } catch (e: Exception) {
            Log.e("🔑 DEBUG_HASH", "Error getting key hash: ${e.message}")
        }
        // ==================================================

        if (BuildConfig.DEBUG) {
            OneSignal.Debug.logLevel = LogLevel.VERBOSE
            FacebookSdk.setIsDebugEnabled(true)
            FacebookSdk.addLoggingBehavior(com.facebook.LoggingBehavior.APP_EVENTS)
        }


        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // OneSignal Initialization
        OneSignal.initWithContext(this, ONESIGNAL_APP_ID)

        // ====== DND: suppress OneSignal notifications when DND is active ======
        OneSignal.Notifications.addForegroundLifecycleListener(object : com.onesignal.notifications.INotificationLifecycleListener {
            override fun onWillDisplay(event: com.onesignal.notifications.INotificationWillDisplayEvent) {
                val userData = getInstance()?.getPrefs()?.getUserData()
                if (isDndActiveStatic(userData)) {
                    Log.d("OneSignal_DND", "DND is active — suppressing OneSignal notification")
                    // preventDefault() stops OneSignal from displaying the notification
                    event.preventDefault()
                }
            }
        })

        // Create the same channel ID as your OneSignal dashboard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "f49d2168-bc20-4a4b-a984-a7abffe0d6aa" // 👈 same as dashboard
            val channelName = "Default notification"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                setSound(soundUri, audioAttributes)
                enableLights(true)
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // Ask for permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Only call from Activity, so you can handle this there
            }
        }


        // requestPermission will show the native Android notification permission prompt.
        // NOTE: It's recommended to use a OneSignal In-App Message to prompt instead.
//        CoroutineScope(Dispatchers.IO).launch {
//            OneSignal.Notifications.requestPermission(false)
//        }
        var userId = getInstance()?.getPrefs()
            ?.getUserData()?.id.toString() // Set user_id
        Log.d("userIDCheck", "Logging in with userId: $userId")

        // Socket.IO will be connected only when ChatActivityInHouse is opened
        // No automatic connection in BaseApplication to save resources
        Log.d("SocketIOCheck", "📍 Socket.IO will connect when ChatActivityInHouse opens")

        //  initZoho()


        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                // Parsed additionalData
                val data = event.notification.additionalData

                // Track notification tap conversion (fire-and-forget)
                try {
                    val notifId = data?.optInt("notification_id", 0) ?: 0
                    if (notifId > 0) {
                        trackNotificationConversion(notifId, "click")
                    }
                } catch (e: Exception) {
                    Log.e("NotifConversion", "click tracking failed: ${e.message}")
                }

                if (data != null) {
                    val user_id = data.optInt("user_id")
                    val prefs = getSharedPreferences("my_app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("notification_user_id", user_id.toString()).apply()
                    Log.d("NotificationDataOneSingal", "$data")
                } else {
                    // Raw fallback
                    Log.d("NotificationDataOneSingal", event.notification.rawPayload)
                }


                if (data != null) {
                    val type = data.optString("type", "")
                    if (type == "message") {
                        val peerUserId = parseMessageNotificationPeerUserId(data)
                        if (peerUserId > 0) {
                            val displayName = parseMessageNotificationOptionalString(
                                data,
                                "user_name", "sender_name", "name", "username", "title"
                            ) ?: "User"
                            val imageUrl = parseMessageNotificationOptionalString(
                                data,
                                "user_image", "image", "image_url", "profile_image", "sender_image", "avatar"
                            ).orEmpty()
                            Log.d("OneSignalClick", "✅ Opening ChatActivityInHouse for peerUserId=$peerUserId")
                            val intent = Intent(applicationContext, ChatActivityInHouse::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("USER_ID", peerUserId)
                                putExtra("USER_NAME", displayName)
                                putExtra("USER_IMAGE", imageUrl)
                            }
                            startActivity(intent)
                        } else {
                            Log.w(
                                "OneSignalClick",
                                "message notification missing peer user id in additionalData — opening ChatListActivity. Payload: $data"
                            )
                            val intent = Intent(applicationContext, ChatListActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(intent)
                        }
                    }
                   else if (type == "friend_request") {
                        Log.d("OneSignalClick", "✅ App OPEN - Opening ChatListActivity")
                        val intent = Intent(applicationContext, com.gmwapp.hima.activities.FriendsListActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("target_tab", FriendsTabFragment.TYPE_THEIR_REQUESTS)   // tell the activity which tab to open

                        }
                        startActivity(intent)
                    }
                    else if (type == "friend_request_accepted") {
                        Log.d("OneSignalClick", "✅ App OPEN - Opening ChatListActivity")
                        val intent = Intent(applicationContext, com.gmwapp.hima.activities.FriendsListActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("target_tab", FriendsTabFragment.TYPE_FRIENDS)   // tell the activity which tab to open

                        }
                        startActivity(intent)
                    }

                    else if (type == "creator_warning") {

                        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                        if (userData == null || userData.id <= 0) {
                            Log.w("OneSignalClick", "creator_warning click ignored (userData is null/empty)")
                            return
                        }

                        Log.d("OneSignalClick", "✅ App OPEN - Opening TicketsListActivity")
                        val intent = Intent(applicationContext, com.gmwapp.hima.activities.MyWarningsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                    }

                    else if (type == "block") {

                        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                        if (userData == null || userData.id <= 0) {
                            Log.w("OneSignalClick", "creator_warning click ignored (userData is null/empty)")
                            return
                        }

                        Log.d("OneSignalClick", "✅ App OPEN - Opening TicketsListActivity")
                        val intent = Intent(applicationContext, com.gmwapp.hima.activities.MyWarningsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                    }

                    else if (type == "your ticket") {
                        Log.d("OneSignalClick", "✅ App OPEN - Opening TicketsListActivity")
                        val intent = Intent(applicationContext, com.gmwapp.hima.activities.TicketsListActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("TAB_POSITION", 1) // 1 = Resolved tab
                        }
                        startActivity(intent)
                    }


                    else{

                    val intent = Intent(applicationContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("fromApplication", true)

                    }
                    startActivity(intent)
                }
                }

            }
        })




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

    /**
     * OneSignal `additionalData` keys vary by backend; try common names for the **other** user's id.
     */
    private fun parseMessageNotificationPeerUserId(data: JSONObject): Int {
        val keys = arrayOf("user_id", "sender_id", "from_user_id", "senderId", "sender_user_id")
        for (key in keys) {
            if (!data.has(key) || data.isNull(key)) continue
            val id = when (val raw = data.opt(key)) {
                is Number -> raw.toInt()
                else -> data.optString(key, "").trim().toIntOrNull() ?: 0
            }
            if (id > 0) return id
        }
        return -1
    }

    private fun parseMessageNotificationOptionalString(data: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val s = data.optString(key, "").trim()
            if (s.isNotEmpty()) return s
        }
        return null
    }

    /**
     * Fire-and-forget POST to /api/notification-conversions to record that the user
     * either tapped a push notification (action="click") or opened the app within the
     * conversion window after receiving one (action="open").
     *
     * Marks the notification as counted in SharedPreferences so we don't double-count.
     */
    fun trackNotificationConversion(notificationId: Int, action: String) {
        if (notificationId <= 0) return

        // Avoid double counting: if we already posted a conversion for this notif, skip.
        val trackPrefs = applicationContext.getSharedPreferences("notif_track", Context.MODE_PRIVATE)
        val lastNotifId = trackPrefs.getInt("last_notif_id", 0)
        val alreadyCounted = trackPrefs.getBoolean("last_notif_counted", false)
        if (lastNotifId == notificationId && alreadyCounted) {
            Log.d("NotifConversion", "Skip notif=$notificationId — already counted")
            return
        }
        trackPrefs.edit().putBoolean("last_notif_counted", true).apply()

        val userId = try {
            getPrefs()?.getUserData()?.id ?: 0
        } catch (e: Exception) {
            0
        }

        Thread {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val formBuilder = okhttp3.FormBody.Builder()
                    .add("notification_id", notificationId.toString())
                    .add("action", action)
                if (userId > 0) {
                    formBuilder.add("user_id", userId.toString())
                }

                // BuildConfig.BASE_URL points at "<host>/api/auth/" — strip the auth/
                // suffix so this endpoint also works correctly on demo + prod builds.
                val apiRoot = BuildConfig.BASE_URL.removeSuffix("auth/")
                val request = okhttp3.Request.Builder()
                    .url("${apiRoot}notification-conversions")
                    .post(formBuilder.build())
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d(
                        "NotifConversion",
                        "Posted notif=$notificationId action=$action user=$userId -> HTTP ${response.code} url=${request.url}"
                    )
                }
            } catch (e: Exception) {
                Log.e("NotifConversion", "Failed to post conversion: ${e.message}")
                // On failure, allow retry on next foreground.
                trackPrefs.edit().putBoolean("last_notif_counted", false).apply()
            }
        }.start()
    }

    /**
     * Called from ActivityLifecycleCallbacks when the app comes to foreground.
     * If a push notification was received in the last 5 minutes and the user
     * opened the app directly (without tapping the notification), record an
     * "open" conversion so it shows up in the dashboard tracker.
     */
    private fun checkAndTrackNotificationOpen() {
        try {
            val prefs = applicationContext.getSharedPreferences("notif_track", Context.MODE_PRIVATE)
            val notifId = prefs.getInt("last_notif_id", 0)
            val notifTime = prefs.getLong("last_notif_time", 0L)
            val counted = prefs.getBoolean("last_notif_counted", false)

            if (notifId <= 0 || notifTime <= 0L || counted) return

            val ageMs = System.currentTimeMillis() - notifTime
            val windowMs = 5 * 60 * 1000L // 5 minutes
            if (ageMs in 0..windowMs) {
                Log.d("NotifConversion", "App foregrounded ${ageMs}ms after notif=$notifId — recording open")
                trackNotificationConversion(notifId, "open")
            }
        } catch (e: Exception) {
            Log.e("NotifConversion", "checkAndTrackNotificationOpen failed: ${e.message}")
        }
    }

    fun playIncomingCallSound() {
        // Stop any previous ringtone first
        stopRingtone()

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val ringtoneUri = android.provider.Settings.System.DEFAULT_RINGTONE_URI
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(applicationContext, ringtoneUri)
                isLooping = true
                setOnPreparedListener { start() } // start only after prepared
                setOnCompletionListener { stopRingtone() } // safety
                setOnErrorListener { _, _, _ ->
                    stopRingtone()
                    true
                }
                prepareAsync() // async is safe
            }
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Error playing ringtone: ${e.message}")
            stopRingtone()
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


//    fun isRingtonePlaying(): Boolean {
//        return mediaPlayer?.isPlaying ?: false
//    }

    fun isRingtonePlaying(): Boolean {
        val player = mediaPlayer ?: return false
        return try {
            player.isPlaying
        } catch (e: IllegalStateException) {
            false
        }
    }



    fun stopRingtone() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Error stopping ringtone: ${e.message}")
        } finally {
            mediaPlayer = null
            Log.d("MediaPlayer", "Ringtone stopped and released safely.")
        }
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

    private fun registerAppNetworkConnectivity() {
        networkConnectedLiveData.postValue(Helper.checkNetworkConnection())
        appConnectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        appNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkConnectedLiveData.postValue(true)
                handleEndCallOnNetworkReturn()
            }

            override fun onLost(network: Network) {
                networkConnectedLiveData.postValue(false)
            }
        }
        try {
            appConnectivityManager?.registerDefaultNetworkCallback(appNetworkCallback!!)
        } catch (e: Exception) {
            Log.e("BaseApplication", "registerDefaultNetworkCallback failed: ${e.message}", e)
        }
    }

    private fun handleEndCallOnNetworkReturn() {
        if (Helper.checkNetworkConnection() && isEndCallUpdatePending() == true) {
            setEndCallUpdatePending(null)
        }
    }

    fun saveSenderId(senderId: Int) {
        sharedPreferences.edit().putInt("SENDER_ID", senderId).apply()
    }

    fun getSenderId(): Int {
        return sharedPreferences.getInt("SENDER_ID", -1)
    }


    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()


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

    fun initZoho(appKey: String?, accessKey: String?) {
        var userGender = getInstance()?.getPrefs()?.getUserData()?.gender

        if (userGender=="female") {
            ZohoSalesIQ.init(
                this,
                appKey,
                accessKey
            );
        }
    }

    private fun isFullscreenActivity(activity: Activity): Boolean {
        val attrs = activity.window.attributes
        return (attrs.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0
    }

    fun getInstallReferrer() {
        val referrerClient = InstallReferrerClient.newBuilder(this).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        try {
                            val response: ReferrerDetails = referrerClient.installReferrer
                            val referrerUrl = response.installReferrer
                            
                            // Build complete response data map
                            val responseData = mutableMapOf<String, Any>()
                            responseData["response_code"] = responseCode
                            responseData["install_referrer"] = referrerUrl ?: ""
                            responseData["referrer_click_timestamp_seconds"] = response.referrerClickTimestampSeconds
                            responseData["install_begin_timestamp_seconds"] = response.installBeginTimestampSeconds
                            
                            if (!referrerUrl.isNullOrEmpty()) {
                                // Parse UTM parameters
                                val utmParams = parseUtmParameters(referrerUrl)
                                val source = utmParams["utm_source"] ?: "unknown"
                                val campaign = utmParams["utm_campaign"] ?: "unknown"
                                val medium = utmParams["utm_medium"] ?: "unknown"
                                
                                responseData["utm_source"] = source
                                responseData["utm_campaign"] = campaign
                                responseData["utm_medium"] = medium
                                responseData["utm_params"] = utmParams
                                
                                // Log with tag AppDownloadSoruce
                                Log.d("AppDownloadSoruce", "Full Referrer: $referrerUrl")
                                Log.d("AppDownloadSoruce", "Source: $source")
                                Log.d("AppDownloadSoruce", "Campaign: $campaign")
                                Log.d("AppDownloadSoruce", "Medium: $medium")
                                
                            } else {
                                Log.d("AppDownloadSoruce", "No referrer data (organic install)")
                            }
                            
                            // Check if userData is empty/null, then send to backend
                            val userData = getPrefs()?.getUserData()
                            if (userData == null) {
                                sendInstallReferrerToBackend(responseData)
                            }
                            
                        } catch (e: Exception) {
                            Log.e("AppDownloadSoruce", "Error getting referrer: ${e.message}")
                        }
                    }
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                        Log.e("AppDownloadSoruce", "API not supported")
                        // Still send response with error code
                        val userData = getPrefs()?.getUserData()
                        if (userData == null) {
                            val responseData = mapOf(
                                "response_code" to responseCode,
                                "error" to "FEATURE_NOT_SUPPORTED"
                            )
                            sendInstallReferrerToBackend(responseData)
                        }
                    }
                    InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                        Log.e("AppDownloadSoruce", "Service unavailable")
                        // Still send response with error code
                        val userData = getPrefs()?.getUserData()
                        if (userData == null) {
                            val responseData = mapOf(
                                "response_code" to responseCode,
                                "error" to "SERVICE_UNAVAILABLE"
                            )
                            sendInstallReferrerToBackend(responseData)
                        }
                    }
                }
                referrerClient.endConnection()
            }

            override fun onInstallReferrerServiceDisconnected() {
                // Retry later if needed
            }
        })
    }
    
    private fun sendInstallReferrerToBackend(responseData: Map<String, Any>) {
        try {
            // Convert responseData to JSON string
            val gson = com.google.gson.Gson()
            val responseDataJson = gson.toJson(responseData)
            
            // Save response data to SharedPreferences before calling API
            getPrefs()?.setString("install_referrer_response_data", responseDataJson)
            Log.d("AppDownloadSoruce", "✅ Install referrer response data saved to SharedPreferences")
            
            val apiManager = getApiManager()
            if (apiManager == null) {
                Log.e("AppDownloadSoruce", "ApiManager not available")
                return
            }
            
            // Get device info
            val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
            val appVersion = try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                packageInfo.versionName ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }
            val osVersion = "Android ${Build.VERSION.RELEASE}"
            
            // Get user_id from preferences, default to 0 if null or empty
            val userData = getPrefs()?.getUserData()
            val userId = userData?.id ?: 0
            
            apiManager.logInstallReferrer(
                responseDataJson,
                deviceInfo,
                appVersion,
                osVersion,
                userId,
                object : com.gmwapp.hima.retrofit.callbacks.NetworkCallback<com.gmwapp.hima.retrofit.responses.InstallReferrerResponse> {
                    override fun onResponse(
                        call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.InstallReferrerResponse>,
                        response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.InstallReferrerResponse>
                    ) {
                        if (response.isSuccessful) {
                            Log.d("AppDownloadSoruce", "✅ Install referrer logged to backend successfully")
                        } else {
                            Log.e("AppDownloadSoruce", "❌ Failed to log install referrer: ${response.code()}")
                        }
                    }
                    
                    override fun onFailure(
                        call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.InstallReferrerResponse>,
                        t: Throwable
                    ) {
                        Log.e("AppDownloadSoruce", "❌ Error logging install referrer: ${t.message}", t)
                    }
                    
                    override fun onNoNetwork() {
                        Log.w("AppDownloadSoruce", "⚠️ No network for logging install referrer")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("AppDownloadSoruce", "Error sending install referrer to backend: ${e.message}", e)
        }
    }
    
    private fun getApiManager(): com.gmwapp.hima.retrofit.ApiManager? {
        return try {
            val okHttpClientBuilder = okhttp3.OkHttpClient.Builder()
            if (com.gmwapp.hima.BuildConfig.DEBUG) {
                val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor()
                loggingInterceptor.level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
                okHttpClientBuilder.addInterceptor(loggingInterceptor)
            }
            val okHttpClient = okHttpClientBuilder.build()
            
            val gson = com.google.gson.GsonBuilder().setLenient().create()
            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl(com.gmwapp.hima.BuildConfig.BASE_URL)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(gson))
                .client(okHttpClient)
                .build()
            
            com.gmwapp.hima.retrofit.ApiManager(retrofit)
        } catch (e: Exception) {
            Log.e("BaseApplication", "Failed to create ApiManager: ${e.message}")
            null
        }
    }

    // Helper function to parse UTM parameters
    private fun parseUtmParameters(referrerUrl: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        
        try {
            val parts = referrerUrl.split("&")
            for (part in parts) {
                val keyValue = part.split("=")
                if (keyValue.size == 2) {
                    val key = keyValue[0]
                    val value = URLDecoder.decode(keyValue[1], "UTF-8")
                    params[key] = value
                }
            }
        } catch (e: Exception) {
            Log.e("AppDownloadSoruce", "Error parsing UTM: ${e.message}")
        }
        
        return params
    }

    fun isChatListActivityVisible(): Boolean {
        return currentActivity?.let { current ->
            current::class.java.simpleName == "ChatListActivity" ||
                    current::class.java.simpleName == "FriendsListActivity" ||
            current::class.java.simpleName == "MyWarningsActivity" ||
            current::class.java.simpleName == "FriendsListActivity"

        } ?: false
    }

    /**
     * Initialize Socket.IO connection if user is already logged in
     * Uses userId instead of JWT token as per Socket.IO server requirements
     */
    fun initializeSocketIO() {
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        Log.d("SocketIOCheck", "🚀 initializeSocketIO() CALLED")
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        
        try {
            val prefs = getPrefs()
            Log.d("SocketIOCheck", "📦 Prefs instance: ${if (prefs != null) "✅ Found" else "❌ Null"}")
            
            val userData = prefs?.getUserData()
            Log.d("SocketIOCheck", "👤 UserData: ${if (userData != null) "✅ Found (ID: ${userData.id})" else "❌ Null"}")
            
            val userId = userData?.id
            Log.d("SocketIOCheck", "🆔 User ID: $userId")
            
            if (userId != null && userId > 0) {
                Log.d("SocketIOCheck", "🔌 Initializing Socket.IO in BaseApplication with User ID: $userId")
                SocketManager.getInstance().connect(userId)
            } else {
                Log.d("SocketIOCheck", "⚠️ No user ID found (userId=$userId) - Socket.IO will connect after login")
                Log.d("SocketIOCheck", "💡 This is normal if user hasn't logged in yet")
            }
        } catch (e: Exception) {
            Log.e("SocketIOCheck", "❌ Error in initializeSocketIO: ${e.message}", e)
            Log.e("SocketIOCheck", "❌ Stack trace: ${e.stackTraceToString()}")
        }
        
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
    }

}