package com.gmwapp.hima

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.util.Log
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.atomic.AtomicInteger
import android.view.WindowManager
import androidx.lifecycle.MutableLiveData
import androidx.work.Configuration
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.CallStatusRepository
import com.gmwapp.hima.repositories.FcmNotificationRepository
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.utils.Helper
import com.gmwapp.hima.utils.OneSignalDiag
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
import com.gmwapp.hima.activities.NewLoginActivity
import com.gmwapp.hima.dagger.UnauthorizedEvent
import com.gmwapp.hima.fragments.FriendsTabFragment
import com.gmwapp.hima.socket.SocketManager
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import android.widget.Toast


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
    // Separate player for the gift "send" sound effect, kept distinct from
    // the incoming-call ringtone player so a rapid burst of gifts doesn't
    // tear down the ringtone path (B071 fix). Also lets us prepareAsync
    // without blocking the main thread on each gift tap.
    private var giftSoundPlayer: MediaPlayer? = null

    /**
     * Audio mode captured before [playIncomingCallSound] flips to [AudioManager.MODE_RINGTONE].
     * Restored by [stopRingtone] only when this flag is set (i.e. only when *we* changed it).
     * `null` means we did not modify the mode and must leave it alone.
     */
    private var ringtoneSavedAudioMode: Int? = null
    /**
     * True when [playIncomingCallSound] called [AudioManager.clearCommunicationDevice] so we
     * can log it; we don't try to restore that pin since it was almost always stale.
     */
    private var ringtoneClearedCommDevice: Boolean = false
    /** We called [AudioManager.startBluetoothSco] for incoming ringtone; must [stopBluetoothSco] in [stopRingtone]. */
    private var ringtoneWeStartedSco: Boolean = false
    /** We called [AudioManager.setCommunicationDevice] for incoming ringtone; must [clearCommunicationDevice] in [stopRingtone]. */
    private var ringtoneSetCommDevice: Boolean = false
    private var endCallUpdatePending: Boolean? = null

    // Audio focus for the incoming-call ringtone phase. Holding focus with
    // USAGE_NOTIFICATION_RINGTONE makes YouTube / Spotify / other media pause
    // while Hima is ringing — without this the ringtone gets mixed with /
    // drowned out by whatever the user is already playing (B150).
    private var ringtoneFocusRequest: AudioFocusRequest? = null
    private val ringtoneFocusAutoReleaseHandler =
        android.os.Handler(android.os.Looper.getMainLooper())
    private val ringtoneFocusAutoRelease = Runnable { releaseRingtoneFocus() }

    val networkConnectedLiveData = MutableLiveData<Boolean>()
    private var appConnectivityManager: ConnectivityManager? = null
    private var appNetworkCallback: ConnectivityManager.NetworkCallback? = null
    // Per-flavor OneSignal project id from app/build.gradle.kts (see app/build.gradle.kts productFlavors).
    val ONESIGNAL_APP_ID: String = BuildConfig.ONESIGNAL_APP_ID
    private lateinit var sharedPreferences: SharedPreferences

    private var currentActivity: Activity? = null

    private var senderId: Int? = null
    private var callTypeForSplashActivity: String? = null
    private var channelName: String? = null
    private var callIdForSplashActivity: Int? = null
    private var incomingCall: Boolean = false
    // B023 — cached caller display info so MainActivity can hand off to the
    // call-accept activity (with proper avatar/name) when the user opens the
    // app while a call is ringing, instead of the ring being dropped silently.
    private var incomingCallerName: String? = null
    private var incomingCallerImage: String? = null


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
                if (activity.javaClass.simpleName in CALL_ACTIVITY_NAMES) {
                    // B156a — mark the app as in-a-call from the moment any
                    // call screen is *created*, not when it reaches resume.
                    // FCM-incoming busy-check reads this via isInActiveCall().
                    activeCallActivityCount.incrementAndGet()
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
                    // Heartbeat to bump users.datetime so the backend marks the
                    // user as freshly active. Throttled internally to ~4 min.
                    runCatching { activeStatusReporter.reportActive() }
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
                if (activity.javaClass.simpleName in CALL_ACTIVITY_NAMES) {
                    // Defensive: only decrement if we're still above 0.
                    // ActivityLifecycleCallbacks pair onCreate→onDestroy reliably
                    // on the main thread, but rare WindowManager/process-kill
                    // paths can drop an onCreate without a paired onDestroy.
                    activeCallActivityCount.updateAndGet { current ->
                        if (current > 0) current - 1 else 0
                    }
                }
            }

        }

    @Inject
    lateinit var fcmNotificationRepository: FcmNotificationRepository

    @Inject
    lateinit var callStatusRepository: CallStatusRepository

    @Inject
    lateinit var chatHistoryMemoryCache: com.gmwapp.hima.utils.ChatHistoryMemoryCache

    @Inject
    lateinit var activeStatusReporter: com.gmwapp.hima.utils.ActiveStatusReporter

    companion object {
        private var mInstance: BaseApplication? = null


        fun getInstance(): BaseApplication? {
            return mInstance
        }




        lateinit var firebaseAnalytics: FirebaseAnalytics
            private set

        // B156a — simpleName lookup avoids importing all 8 call activities
        // into BaseApplication. Checked in onActivityCreated / onActivityDestroyed
        // to keep the live-call-activity counter (`activeCallActivityCount`)
        // in sync. If any class moves package the entry still matches.
        private val CALL_ACTIVITY_NAMES: Set<String> = setOf(
            "FemaleCallConnectingActivity",
            "FemaleCallAcceptActivity",
            "FemaleAudioCallingActivity",
            "FemaleVideoCallingActivity",
            "MaleCallConnectingActivity",
            "MaleCallAcceptActivity",
            "MaleAudioCallingActivity",
            "MaleVideoCallingActivity"
        )

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

        /** Same id as [com.gmwapp.hima.agora.MyFirebaseMessagingService] CallStyle notification. */
        private const val INCOMING_CALL_NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()

        // Test log to verify SocketIOCheck tag is working
        Log.d("SocketIOCheck", "🎯 BaseApplication.onCreate() STARTED - SocketIOCheck tag is working!")

        mInstance = this

        // 2026-06-05 v1108 FIX: cold-start cannot have an active call. If
        // isCallActive was left stuck=true by a crashed/killed previous call,
        // the user would be (a) silently suppressed from incoming pushes and
        // (b) blocked from outgoing calls with "You're already in a call".
        // Force-clear at every process start so the flag can never persist
        // across app launches.
        isCallActive = false
        activeCallActivityCount.set(0)

        // 2026-06-05 v1108 FIX #2: Agora call sets AudioManager.mode to
        // MODE_IN_COMMUNICATION during the call and is supposed to reset to
        // MODE_NORMAL on leaveChannel. If the call activity crashed / was
        // OEM-killed before leaveChannel ran, the mode stays stuck. The FCM
        // dispatcher's isOnAnotherAppCall() reads AudioManager.mode and
        // returns true on stuck IN_COMMUNICATION — every new incoming call
        // then routes through postBusyMissedCall() and the recipient only
        // sees "Missed call. Tap to call back." with NO RING. Force-reset
        // to MODE_NORMAL at process start; if another app is genuinely on
        // a call right now, their own audio stack will re-claim the mode
        // within milliseconds, so this is safe.
        runCatching {
            val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (am?.mode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                am?.mode == android.media.AudioManager.MODE_IN_CALL) {
                am?.mode = android.media.AudioManager.MODE_NORMAL
                Log.d("HimaColdStart", "Reset stale AudioManager.mode -> MODE_NORMAL")
            }
        }

        // 2026-06-05 v1108 FIX #7: Android Telecom framework keeps stale
        // HimaConnection objects alive when call activities don't call
        // connection.destroy() on normal leaveChannel (only the REJECT/ERROR
        // paths in HimaConnection do). A stuck CONNECTING connection blocks
        // every subsequent outgoing call with the system error:
        // "Cannot place a call as there is already another call connecting."
        // Force end-all-self-managed-calls at cold start so a previous
        // crashed/killed call activity can't strand a connection forever.
        runCatching {
            val tm = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            // endCall() ends any active managed call our app owns. For
            // self-managed connections we rely on HimaConnectionService's own
            // lifecycle, but ending now flushes anything stranded.
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    @Suppress("MissingPermission") tm?.endCall()
                }
            } catch (_: Throwable) { /* ANSWER_PHONE_CALLS not granted is fine */ }
            Log.d("HimaColdStart", "Telecom endCall flush attempted")
        }
        // First launch after a version bump: wipe stale system-tray notifications. Old chat
        // notifications were posted with PendingIntents pointing at the now-deleted ChatActivity,
        // so without this they'd either crash on tap or open the wrong screen.
        runCatching {
            val versionPrefs = getSharedPreferences("app_version_prefs", Context.MODE_PRIVATE)
            val lastVersion = versionPrefs.getInt("last_known_version_code", -1)
            val currentVersion = BuildConfig.VERSION_CODE
            if (lastVersion != currentVersion) {
                Log.d(
                    "AppUpgrade",
                    "version bump $lastVersion -> $currentVersion; cancelling stale system notifications"
                )
                runCatching {
                    androidx.core.app.NotificationManagerCompat.from(this).cancelAll()
                }
                versionPrefs.edit().putInt("last_known_version_code", currentVersion).apply()
            }
        }
        // Force light theme app-wide. The app has no dark-mode design pass yet, so
        // letting the system flip to night caused white-on-white headers and
        // unreadable bubbles. Pin to NIGHT_NO so every screen inherits light tokens.
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )
        mPreferences = DPreferences(this)
        // Bind the in-memory chat-history cache to whoever was last signed in. If
        // anything reads the cache before login (or by a different user) it will
        // be empty until setOwner is re-called with the live id.
        runCatching {
            chatHistoryMemoryCache.setOwner(mPreferences?.getUserData()?.id ?: 0)
        }
        // Cold-start active heartbeat — fires for users already logged in so the
        // first launch (before any foreground transition) counts.
        runCatching { activeStatusReporter.reportActive() }
        HimaTelecomManager.registerPhoneAccountIfNeeded(this)
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

        // 2026-05-25 v1075 — Meta SDK v18 defaults AutoInit/AutoLog to FALSE for
        // GDPR compliance. Events won't reach Meta Events Manager unless we
        // explicitly enable them BEFORE sdkInitialize. Discovered after marketing
        // reported 0 events arriving in Meta dashboard for v1074.
        FacebookSdk.setApplicationId(getString(R.string.facebook_app_id))
        FacebookSdk.setClientToken(getString(R.string.facebook_client_token))
        FacebookSdk.setAutoInitEnabled(true)
        FacebookSdk.setAutoLogAppEventsEnabled(true)
        FacebookSdk.setAdvertiserIDCollectionEnabled(true)
        FacebookSdk.sdkInitialize(applicationContext)
        FacebookSdk.fullyInitialize()
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

        // App-side feature kill-switches. Logged once per cold start so future
        // confusion ("why is Ludo gone?") is answerable with `adb logcat -s FeatureFlags`.
        Log.d(
            "FeatureFlags",
            "IPL_ENABLED=${com.gmwapp.hima.utils.FeatureFlags.IPL_ENABLED} " +
                "LUDO_ENABLED=${com.gmwapp.hima.utils.FeatureFlags.LUDO_ENABLED}"
        )

        // OneSignal Initialization
        OneSignal.initWithContext(this, ONESIGNAL_APP_ID)
        OneSignalDiag.installObserver(this)
        OneSignalDiag.dump(this, "post_init")

        // Idempotent subscribe. If an earlier build's logout/optOut-then-login churn
        // left the device stuck with optedOut=true on OneSignal's servers, this line
        // flips it back on next launch — login() and optIn() are safe no-ops when
        // the state already matches, so running this every cold start is harmless.
        runCatching {
            val savedUserId = getPrefs()?.getUserData()?.id
            if (savedUserId != null && savedUserId > 0) {
                OneSignal.login(savedUserId.toString())
                // Always re-assert opt-in. The local `optedIn` flag reads cached state
                // and can be stale-true while the server still has enabled=false, so
                // guarding on it was the bug that stranded users after re-login.
                OneSignal.User.pushSubscription.optIn()
                Log.d("OneSignalFix", "BaseApp idempotent subscribe: externalId=$savedUserId optedIn=${OneSignal.User.pushSubscription.optedIn}")
                OneSignalDiag.dump(this, "post_login_immediate")
                // OneSignal syncs asynchronously; snapshot again once the network round-trip has had time to land.
                android.os.Handler(mainLooper).postDelayed({
                    OneSignalDiag.dump(this, "post_login_delayed_3s")
                }, 3000)
            }
        }.onFailure { Log.e("OneSignalFix", "BaseApp idempotent subscribe failed: ${it.message}") }

        // ====== Force FCM token resync on every app start =====================
        // onNewToken only fires when Firebase rotates the device token. If the
        // server-side mapping becomes stale for any other reason (user logged
        // in on a second device and the backend stores one-token-per-user, a
        // Samsung power-save kill that silently invalidates the mapping, etc.)
        // the app would keep running with a dead mapping until reinstall.
        // Re-push the current token on every cold start so a silently-stale
        // mapping heals itself the next time the user opens the app.
        runCatching {
            val signedInUserId = getPrefs()?.getUserData()?.id ?: 0
            val authToken = getPrefs()?.getAuthenticationToken().orEmpty()
            if (signedInUserId > 0 && authToken.isNotBlank()) {
                Log.d("CreatorCallDiag", "BaseApp.fcmTokenSync userId=$signedInUserId authToken=${authToken.take(8)}…")
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        Log.d("CreatorCallDiag", "BaseApp.fcmTokenSync tokenPrefix=${token?.take(12)}…")
                        // 2026-05-23 v1070 — fire DIRECT POST to /api/auth/update_fcm_token
                        // immediately on a background thread. WorkManager's queue can
                        // sit for minutes on some OEM ROMs (Xiaomi/Vivo aggressive
                        // power-save), leaving the backend with the previous token
                        // and silently breaking incoming-call FCM (female "accepted"
                        // signal can't reach the new install). Direct push happens
                        // in <2s on any network.
                        if (!token.isNullOrBlank()) {
                            Thread({
                                try {
                                    val url = com.gmwapp.hima.BuildConfig.BASE_URL + "send_fcm_token"
                                    val client = okhttp3.OkHttpClient.Builder()
                                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                        .writeTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                        .build()
                                    val body = okhttp3.FormBody.Builder()
                                        .add("user_id", signedInUserId.toString())
                                        .add("token", token)
                                        .build()
                                    val req = okhttp3.Request.Builder()
                                        .url(url)
                                        .post(body)
                                        .header("Authorization", "Bearer $authToken")
                                        .build()
                                    client.newCall(req).execute().use { resp ->
                                        Log.d("CreatorCallDiag", "BaseApp.fcmTokenSync.direct HTTP ${resp.code}")
                                    }
                                } catch (t: Throwable) {
                                    Log.w("CreatorCallDiag", "BaseApp.fcmTokenSync.direct failed (worker fallback active): ${t.message}")
                                }
                            }, "fcm-token-direct-$signedInUserId").start()
                        }
                        // Belt-and-braces: also enqueue the worker so retries
                        // happen on backoff if the direct push above fails.
                        val input = androidx.work.Data.Builder()
                            .putInt(com.gmwapp.hima.workers.FcmTokenRegisterWorker.KEY_USER_ID, signedInUserId)
                            .putString(com.gmwapp.hima.workers.FcmTokenRegisterWorker.KEY_TOKEN, token ?: "")
                            .putString(com.gmwapp.hima.workers.FcmTokenRegisterWorker.KEY_AUTH_TOKEN, authToken)
                            .build()
                        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                            "${com.gmwapp.hima.workers.FcmTokenRegisterWorker.WORK_NAME_PREFIX}$signedInUserId",
                            androidx.work.ExistingWorkPolicy.REPLACE,
                            androidx.work.OneTimeWorkRequestBuilder<com.gmwapp.hima.workers.FcmTokenRegisterWorker>()
                                .setInputData(input)
                                .setConstraints(
                                    androidx.work.Constraints.Builder()
                                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                        .build()
                                )
                                .setBackoffCriteria(
                                    androidx.work.BackoffPolicy.EXPONENTIAL,
                                    30,
                                    java.util.concurrent.TimeUnit.SECONDS
                                )
                                .build()
                        )
                    }
                    .addOnFailureListener {
                        Log.e("CreatorCallDiag", "BaseApp.fcmTokenSync.failed ${it.message}")
                    }
            } else {
                Log.d("CreatorCallDiag", "BaseApp.fcmTokenSync skipped userId=$signedInUserId authToken=${authToken.take(8)}…")
            }
        }.onFailure { Log.e("CreatorCallDiag", "BaseApp.fcmTokenSync threw: ${it.message}") }

        // ====== Foreground-notification listener =====================
        // Order matters inside onWillDisplay:
        //   1. subscription_status (autopay failed/cancelled) — bypasses DND so
        //      the user always learns their subscription is dead, and so the
        //      SubscriptionStateCache locks foreground screens instantly.
        //   2. DND suppression.
        //   3. Chat message: list refresh + open-thread suppression.
        //   4. Missed-call: render rich CallStyle UI.
        //   5. In-call: suppress further incoming-call pushes (no double-ring).
        //   6. Incoming call: post the same CallStyle UI as the FCM path.
        OneSignal.Notifications.addForegroundLifecycleListener(object : com.onesignal.notifications.INotificationLifecycleListener {
            override fun onWillDisplay(event: com.onesignal.notifications.INotificationWillDisplayEvent) {
                val data = event.notification.additionalData
                val type = data?.optString("type")

                // Subscription status push (autopay failed / cancelled) — clear
                // cache + emit in-app event so foreground chat screens lock
                // instantly. Bypasses DND: the user must know the subscription
                // is no longer active.
                if (type == "subscription_status") {
                    val ev = data.optString("event")
                    val pushEvent = when (ev) {
                        "failed" -> com.gmwapp.hima.utils.SubscriptionStateCache.PushEvent.FAILED
                        "cancelled" -> com.gmwapp.hima.utils.SubscriptionStateCache.PushEvent.CANCELLED
                        else -> null
                    }
                    pushEvent?.let { com.gmwapp.hima.utils.SubscriptionStateCache.postPushEvent(it) }
                    Log.d("OneSignal_AutopayPush", "subscription_status event=$ev")
                    return
                }

                val userData = getInstance()?.getPrefs()?.getUserData()
                if (isDndActiveStatic(userData)) {
                    Log.d("OneSignal_DND", "DND is active — suppressing OneSignal notification")
                    event.preventDefault()
                    return
                }
                val additional = event.notification.additionalData
                // Silent gate refresh: when the male's friend request is rejected while
                // he is in the chat screen, suppress the notification banner and post the
                // peer_id so ChatActivityInHouse can re-fetch the gate immediately.
                if (additional?.optString("type", "") == "friend_request_rejected") {
                    val peerId = additional.optInt("peer_id", 0)
                    if (peerId > 0) {
                        com.gmwapp.hima.agora.FcmUtils.friendRequestRejectedLiveData.postValue(peerId)
                    }
                    event.preventDefault()
                    return
                }
                if (additional?.optString("type", "") == "message") {
                    val peerUserId = this@BaseApplication.parseMessageNotificationPeerUserId(additional)
                    val lastMessage = event.notification.body.orEmpty().trim()
                    val messageType = additional.optString("message_type", "text")
                        .ifBlank { "text" }
                    // Always tell the chat list / bottom-nav badge to refresh; the
                    // thread-level suppression below only handles the open-thread case.
                    if (peerUserId > 0) {
                        val listRefresh = Intent(
                            com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_LIST_REFRESH
                        )
                            .setPackage(packageName)
                            .putExtra(
                                com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.EXTRA_PEER_ID,
                                peerUserId
                            )
                            .putExtra(
                                com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.EXTRA_LAST_MESSAGE,
                                lastMessage
                            )
                            .putExtra(
                                com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.EXTRA_MESSAGE_TYPE,
                                messageType
                            )
                        sendBroadcast(listRefresh)
                    }
                    if (com.gmwapp.hima.utils.ActiveChatTracker.isActiveFor(peerUserId)) {
                        Log.d(
                            "OneSignal_ForegroundChat",
                            "chat visible for peerId=$peerUserId — suppressing foreground heads-up"
                        )
                        val refresh = Intent(
                            com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_REFRESH
                        )
                            .setPackage(packageName)
                            .putExtra("peer_id", peerUserId)
                        sendBroadcast(refresh)
                        event.preventDefault()
                        return
                    }
                }
                // Missed-call detection — needed up-front so we can exclude it
                // from the in-call suppression below (a tiny race where
                // isInActiveCall() is still true after a decline would
                // otherwise eat the missed-call notification entirely).
                val title = event.notification.title.orEmpty().lowercase()
                val body = event.notification.body.orEmpty().lowercase()
                val isMissedCall =
                    additional?.optString("type", "")?.lowercase() == "missed_call" ||
                        additional?.optString("type", "")?.lowercase() == "call_missed" ||
                        title.contains("missed call") || body.contains("missed call")

                if (isInActiveCall() &&
                    looksLikeCallPush(event.notification.additionalData) &&
                    !isMissedCall
                ) {
                    Log.d("OneSignal_InCall", "Already in active call — suppressing OneSignal incoming-call push")
                    event.preventDefault()
                    return
                }

                // Missed call: always render the rich custom notification regardless
                // of foreground state so behaviour is identical to the killed-app
                // path handled by the OneSignal NSE.
                if (isMissedCall) {
                    Log.d(
                        "MissedCallDiag",
                        "fg-entry title=\"${event.notification.title}\" body=\"${event.notification.body}\" " +
                            "dataKeys=${additional?.keys()?.asSequence()?.toList()} " +
                            "dataDump=${additional?.toString()?.take(500)}"
                    )
                    val missed = parseOneSignalMissedCallPayload(additional, event.notification)
                    if (missed == null) {
                        Log.w(
                            "MissedCallDiag",
                            "fg-parse failed: no usable payload — falling back to OneSignal default UI"
                        )
                    } else {
                        Log.d(
                            "MissedCallDiag",
                            "fg-parsed callerName=\"${missed.callerName}\" senderId=${missed.senderId} " +
                                "synthetic=${missed.isSynthetic} callType=${missed.callType} " +
                                "imgBlank=${missed.callerImage.isNullOrBlank()}"
                        )
                        event.preventDefault()
                        Log.d("MissedCallDiag", "fg-preventDefault called senderId=${missed.senderId}")
                        val showResult = runCatching {
                            com.gmwapp.hima.utils.CallNotifications.showMissed(
                                applicationContext,
                                missed
                            )
                        }
                        showResult.onSuccess {
                            Log.d("MissedCallDiag", "fg-showMissed returned=$it senderId=${missed.senderId}")
                        }.onFailure {
                            Log.e("MissedCallDiag", "fg-showMissed threw senderId=${missed.senderId}: ${it.message}", it)
                        }
                        return
                    }
                }

                // Incoming call: post the same CallStyle UI as the FCM path.
                if (looksLikeCallPush(additional) && !isInActiveCall() && !isMissedCall) {
                    val incoming = parseOneSignalIncomingCallPayload(additional, event.notification)
                    if (incoming != null) {
                        val posted = runCatching {
                            com.gmwapp.hima.utils.CallNotifications.showIncoming(
                                applicationContext,
                                incoming
                            )
                            true
                        }.getOrElse {
                            Log.e("OneSignal_Incoming", "showIncoming threw: ${it.message}", it)
                            false
                        }
                        if (posted) {
                            event.preventDefault()
                            return
                        }
                    }
                }
            }

            private fun parseOneSignalIncomingCallPayload(
                additional: org.json.JSONObject?,
                notif: com.onesignal.notifications.IDisplayableNotification
            ): com.gmwapp.hima.utils.CallNotifications.IncomingPayload? {
                val data = additional ?: return null
                val callType = optStringOrNull(data, "callType")
                    ?: optStringOrNull(data, "call_type")
                val senderId = data.optInt("senderId", 0).takeIf { it > 0 }
                    ?: data.optInt("sender_id", 0).takeIf { it > 0 }
                    ?: data.optInt("user_id", 0)
                if (senderId <= 0) return null
                val callId = data.optInt("call_id", 0)
                val channelName = optStringOrNull(data, "channelName")
                    ?: optStringOrNull(data, "channel_name")
                    ?: "default_channel"
                val callerName = optStringOrNull(data, "callerName")
                    ?: optStringOrNull(data, "sender_name")
                    ?: optStringOrNull(data, "name")
                    ?: notif.title?.trim().orEmpty()
                val callerImage = optStringOrNull(data, "callerImage")
                    ?: optStringOrNull(data, "sender_image")
                    ?: optStringOrNull(data, "image")
                    ?: optStringOrNull(data, "avatar")
                    ?: ""
                val isMale = getInstance()?.getPrefs()?.getUserData()?.gender ==
                    com.gmwapp.hima.constants.DConstants.MALE
                return com.gmwapp.hima.utils.CallNotifications.IncomingPayload(
                    isMale = isMale,
                    callType = callType,
                    senderId = senderId,
                    callId = callId,
                    channelName = channelName,
                    callerName = callerName,
                    callerImage = callerImage
                )
            }

            private fun parseOneSignalMissedCallPayload(
                additional: org.json.JSONObject?,
                notif: com.onesignal.notifications.IDisplayableNotification
            ): com.gmwapp.hima.utils.CallNotifications.MissedPayload? {
                // Aliases observed across server templates — anything numeric and >0 wins.
                val realSenderId = if (additional == null) 0 else
                    listOf(
                        "senderId", "sender_id", "user_id",
                        "callerId", "caller_id",
                        "from_user_id", "from_id", "peer_id", "sender_user_id"
                    ).firstNotNullOfOrNull { k -> additional.optInt(k, 0).takeIf { it > 0 } } ?: 0
                val callType = additional?.let { optStringOrNull(it, "callType") }
                    ?: additional?.let { optStringOrNull(it, "call_type") }
                    ?: "audio"
                val titleLine = notif.title?.trim().orEmpty()
                val rawCaller = (additional?.let { optStringOrNull(it, "callerName") }
                    ?: additional?.let { optStringOrNull(it, "sender_name") }
                    ?: additional?.let { optStringOrNull(it, "name") }
                    ?: titleLine.takeIf { it.isNotBlank() })
                    .orEmpty()
                val callerName = com.gmwapp.hima.utils.CallNotifications.normalizeMissedCallCallerName(rawCaller)
                    .ifBlank { "Caller" }
                val callerImage = additional?.let { optStringOrNull(it, "callerImage") }
                    ?: additional?.let { optStringOrNull(it, "sender_image") }
                    ?: additional?.let { optStringOrNull(it, "image") }
                    ?: additional?.let { optStringOrNull(it, "avatar") }
                    ?: ""

                // No real id -> derive a stable one from the normalized caller name so repeat
                // missed calls from the same caller dedupe under one row.
                val isSynthetic = realSenderId <= 0
                val effectiveSenderId = if (!isSynthetic) realSenderId
                    else (callerName.hashCode() and 0x0FFFFFFF).coerceAtLeast(1)

                Log.d(
                    "MissedCallDiag",
                    "bg-app keys=${additional?.keys()?.asSequence()?.toList()} " +
                        "realSenderId=$realSenderId synthetic=$isSynthetic effectiveId=$effectiveSenderId " +
                        "callerName=$callerName callType=$callType title=\"${notif.title}\""
                )

                return com.gmwapp.hima.utils.CallNotifications.MissedPayload(
                    callType = callType,
                    senderId = effectiveSenderId,
                    callerName = callerName,
                    callerImage = callerImage,
                    isSynthetic = isSynthetic
                )
            }

            private fun optStringOrNull(data: org.json.JSONObject, key: String): String? {
                if (!data.has(key) || data.isNull(key)) return null
                val s = data.optString(key, "").trim()
                return s.takeIf { it.isNotEmpty() }
            }

            private fun looksLikeCallPush(additional: org.json.JSONObject?): Boolean {
                if (additional == null) return false
                // Any of these keys being present is a strong signal it's a
                // call-type push (channelName + callType + call_id are all
                // standard fields the backend attaches to call pushes).
                val keys = arrayOf("callType", "channelName", "call_id", "senderId")
                if (keys.any { additional.has(it) && !additional.isNull(it) }) return true
                val type = additional.optString("type", "").lowercase()
                return type.startsWith("call") || type.contains("incoming")
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
                    // T14: full additionalData / rawPayload contain PII — debug only.
                    if (BuildConfig.DEBUG) {
                        Log.d("NotificationDataOneSingal", "$data")
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("NotificationDataOneSingal", event.notification.rawPayload)
                    }
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
                        // Open the integrated in-app Friends hub (bottom-nav) on the
                        // Requests tab — NOT the standalone FriendsListActivity — so the
                        // app shell (bottom nav) is preserved.
                        openFriendsHubFromNotification(1)
                    }
                    else if (type == "friend_request_accepted") {
                        openFriendsHubFromNotification(0)
                    }
                    else if (type == "friend_request_rejected") {
                        // Tapping the notification (rare — foreground handler suppresses the
                        // banner) opens the Friends hub on the Sent tab.
                        openFriendsHubFromNotification(2)
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

        // Listen for auth failures surfaced by the OkHttp interceptor (401 or a 302 to
        // /login). Without this, a stale bearer token leaves the user stuck — every API
        // silently fails and they'd have to clear app data to recover.
        //
        // Try-catch around register: Google Play's Pairip anti-tamper hardening
        // (com.pairip.application.Application) wraps the Application class on AAB
        // distribution and breaks EventBus reflection — the @Subscribe method on
        // BaseApplication becomes invisible, register() throws EventBusException, and
        // the app fatal-crashes on every launch. Local APK installs don't hit Pairip,
        // so the crash never reproduces in dev. Swallow the registration failure
        // (graceful degradation: auto-logout-on-401 stops working on Pairip-wrapped
        // builds, but the app still opens) instead of taking the whole app down.
        try {
            if (!EventBus.getDefault().isRegistered(this)) {
                EventBus.getDefault().register(this)
            }
        } catch (t: Throwable) {
            Log.w("BaseApp", "EventBus register skipped: ${t.message}")
        }

        refreshLanguageFeatureFlag()
    }

    /**
     * Pulls the per-language `enabled_feature` flag and writes it to
     * [com.gmwapp.hima.utils.LanguageFeatureCache]. Called on cold start
     * so that autopay UI surfaces (Wallet banner, Chat lock, trial sheet,
     * crown, etc.) gate correctly even on a deeplink-into-Chat path that
     * never visits HomeFragment.
     */
    private fun refreshLanguageFeatureFlag() {
        val userData = getPrefs()?.getUserData() ?: return
        val userId = userData.id
        val language = userData.language.orEmpty()
        if (userId <= 0 || language.isBlank()) return
        val apiManager = getApiManager() ?: return
        com.gmwapp.hima.utils.LanguageFeatureCache.refresh(this, apiManager, userId, language)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnauthorizedEvent(event: UnauthorizedEvent) {
        // Guard: if we've already logged the user out (or they were never logged in),
        // a stray late-arriving 401 from an in-flight request shouldn't trigger another
        // toast + navigation.
        val prefs = getPrefs() ?: return
        if (prefs.getUserData() == null) return

        Log.w("Unauthorized", "Session expired — clearing data and routing to login")
        performGlobalSessionTeardown()
        prefs.clearUserData()
        Toast.makeText(this, "Session expired, please log in again", Toast.LENGTH_LONG).show()

        val intent = Intent(this, NewLoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    /**
     * Single source of truth for "tear down everything tied to the current account
     * on this device". Mirrors what `BottomSheetLogout` does so that 401 / clear_data
     * paths don't leave stale OneSignal external-id, sockets, dynamic shortcuts, or
     * cached chat content that the next user could see.
     */
    /**
     * T32: same teardown order applies to BottomSheetLogout and FCM clear_data, so
     * expose it here as the single source of truth instead of duplicating the
     * sequence at each call site.
     */
    fun performGlobalSessionTeardown() {
        SocketManager.getInstance().disconnect()
        com.gmwapp.hima.utils.ActiveChatTracker.clear(this)
        runCatching {
            OneSignal.User.removeTag("gender_language")
            OneSignal.User.removeTag("gender")
            OneSignal.User.removeTag("language")
            OneSignal.User.removeTag("user_id")
            OneSignal.logout()
        }.onFailure { Log.e("Unauthorized", "OneSignal teardown failed: ${it.message}") }
        runCatching {
            androidx.core.content.pm.ShortcutManagerCompat
                .removeAllDynamicShortcuts(this)
        }
        val teardownUserId = getPrefs()?.getUserData()?.id ?: 0
        runCatching {
            chatHistoryMemoryCache.clearAll()
            com.gmwapp.hima.utils.PinnedChatsPrefsHelper.clearAll(this)
            com.gmwapp.hima.utils.ChatNotificationStore.clearAll(this)
            // TC_017: wipe only this user's watermarks so a different account
            // that previously cleared chats on this device keeps its own state.
            com.gmwapp.hima.utils.ClearedChatsPrefsHelper.clearForUser(this, teardownUserId)
        }
        // Drop the throttle so the next login fires the heartbeat immediately.
        runCatching { activeStatusReporter.reset() }
        // T13: `my_app_prefs` (set by the OneSignal click handler) survives the
        // standard `clearUserData()` wipe — without this, the next user inherits
        // the previous user's `notification_user_id` from the prior install.
        runCatching {
            getSharedPreferences("my_app_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    fun getCurrentActivity(): Activity? {
        return currentActivity
    }

    /**
     * OneSignal `additionalData` keys vary by backend; try common names for the **other** user's id.
     */
    private fun parseMessageNotificationPeerUserId(data: JSONObject): Int {
        val keys = arrayOf("user_id", "peer_id", "sender_id", "from_user_id", "senderId", "sender_user_id")
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
     * Friend-request notification tap → open the integrated in-app Friends hub
     * (a bottom-nav tab inside MainActivity) instead of the standalone
     * FriendsListActivity, so the app shell (bottom nav) is preserved.
     * Male  → Friends tab ([com.gmwapp.hima.fragments.FriendsHubFragment]);
     * Female → Chat tab ([com.gmwapp.hima.fragments.CreatorChatFragment]).
     * [subTab]: 0 = Friends, 1 = Requests received, 2 = Sent.
     */
    private fun openFriendsHubFromNotification(subTab: Int) {
        val gender = try { getPrefs()?.getUserData()?.gender } catch (e: Exception) { null }
        val tab = if (gender == com.gmwapp.hima.constants.DConstants.FEMALE)
            com.gmwapp.hima.activities.MainActivity.TAB_CHAT
        else
            com.gmwapp.hima.activities.MainActivity.TAB_FAVOURITE
        val intent = Intent(applicationContext, com.gmwapp.hima.activities.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(com.gmwapp.hima.activities.MainActivity.EXTRA_OPEN_TAB, tab)
            putExtra(com.gmwapp.hima.activities.MainActivity.EXTRA_OPEN_SUBTAB, subTab)
        }
        startActivity(intent)
    }

    /**
     * Fire-and-forget POST to /api/notification-conversions to record that the user
     * either tapped a push notification (action="click") or opened the app within the
     * conversion window after receiving one (action="open").
     *
     * Marks the notification as counted in SharedPreferences so we don't double-count.
     */
    fun trackNotificationConversion(notificationId: Int, action: String, amount: Int = 0) {
        if (notificationId <= 0) return

        // Per-(notification, action) dedupe so a single notification can record the
        // whole funnel (click -> open -> purchase -> autopay -> call_enable -> ...)
        // without double-counting the SAME action. A single boolean would let the
        // first action block every later one.
        val trackPrefs = applicationContext.getSharedPreferences("notif_track", Context.MODE_PRIVATE)
        val countedKey = "notif_counted_actions_$notificationId"
        // Synchronized so concurrent callers can't both pass the check-then-write
        // for the same action (SharedPreferences read-modify-write is not atomic).
        synchronized(notifTrackLock) {
            val counted = trackPrefs.getStringSet(countedKey, emptySet()) ?: emptySet()
            if (action in counted) {
                Log.d("NotifConversion", "Skip notif=$notificationId action=$action — already counted")
                return
            }
            // Copy the set — getStringSet may return a shared, mutable instance.
            trackPrefs.edit().putStringSet(countedKey, counted + action).apply()
        }

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
                if (amount > 0) {
                    formBuilder.add("amount", amount.toString())
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
                        "Posted notif=$notificationId action=$action amount=$amount user=$userId -> HTTP ${response.code} url=${request.url}"
                    )
                }
            } catch (e: Exception) {
                Log.e("NotifConversion", "Failed to post conversion: ${e.message}")
                // On failure, allow retry of THIS action only (copy the set).
                synchronized(notifTrackLock) {
                    val cur = trackPrefs.getStringSet(countedKey, emptySet()) ?: emptySet()
                    trackPrefs.edit().putStringSet(countedKey, cur - action).apply()
                }
            }
        }.start()
    }

    /** Guards the per-notification dedupe read-modify-write on SharedPreferences. */
    private val notifTrackLock = Any()

    /**
     * The notification_id saved at receive-time (OneSignalNotificationServiceExtension).
     * Conversion callsites pass this so a purchase/call-enable/etc. is attributed to
     * the notification the user most recently received.
     */
    fun getLastNotificationId(): Int = try {
        applicationContext.getSharedPreferences("notif_track", Context.MODE_PRIVATE).getInt("last_notif_id", 0)
    } catch (e: Exception) {
        0
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
            if (notifId <= 0 || notifTime <= 0L) return

            // Don't re-record an "open" already counted for this notification.
            val countedActions = prefs.getStringSet("notif_counted_actions_$notifId", emptySet()) ?: emptySet()
            if ("open" in countedActions) return

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

    /**
     * Grab transient audio focus for the ringtone so any media app (YouTube /
     * Spotify / etc.) pauses while Hima is ringing — fixes B150. Idempotent;
     * safe to call from the FCM service for the heads-up phase AND from
     * [playIncomingCallSound] for the activity-phase MediaPlayer.
     *
     * Includes a 45 s auto-release safety net for the case where neither
     * [stopRingtone] nor the accept/decline paths get a chance to run (e.g.
     * the OS auto-cancels the 35 s incoming notification on timeout).
     */
    fun requestRingtoneFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (ringtoneFocusRequest != null) {
            // Already holding focus; just reset the auto-release timer.
            ringtoneFocusAutoReleaseHandler.removeCallbacks(ringtoneFocusAutoRelease)
            ringtoneFocusAutoReleaseHandler.postDelayed(ringtoneFocusAutoRelease, 45_000L)
            return
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // GAIN_TRANSIENT_EXCLUSIVE matches native phone-call ringer behaviour:
        // while we hold focus, the system denies any other app's request (e.g.
        // user tapping Play in Spotify while Hima is ringing). Plain TRANSIENT
        // would let the other app grab focus and play over the ringtone.
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { /* no-op — we don't pause our own ring on focus loss */ }
            .build()
        val result = try { am.requestAudioFocus(req) } catch (e: Exception) {
            Log.e("HimaIncomingCall", "requestRingtoneFocus threw", e)
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            ringtoneFocusRequest = req
            ringtoneFocusAutoReleaseHandler.postDelayed(ringtoneFocusAutoRelease, 45_000L)
            Log.d("HimaIncomingCall", "requestRingtoneFocus: granted")
        } else {
            Log.w("HimaIncomingCall", "requestRingtoneFocus: denied result=$result")
        }
    }

    fun releaseRingtoneFocus() {
        ringtoneFocusAutoReleaseHandler.removeCallbacks(ringtoneFocusAutoRelease)
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: run {
            ringtoneFocusRequest = null
            return
        }
        ringtoneFocusRequest?.let {
            try { am.abandonAudioFocusRequest(it) } catch (e: Exception) {
                Log.e("HimaIncomingCall", "releaseRingtoneFocus threw", e)
            }
        }
        ringtoneFocusRequest = null
    }

    /**
     * Vibrator handle held for the duration of an incoming-call ring. Started
     * in [playIncomingCallSound] alongside the MediaPlayer ringtone so the
     * phone vibrates whether ringer mode is NORMAL or VIBRATE (B028) — the
     * MediaPlayer is silenced by the OS in VIBRATE mode but vibration runs
     * independently. Cancelled in [stopRingtone].
     */
    private var ringtoneVibrator: Vibrator? = null

    private fun startIncomingCallVibration() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        // Respect SILENT mode — user explicitly asked for no buzz.
        if (am.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            Log.d("HimaIncomingCall", "startIncomingCallVibration: ringer SILENT, skip")
            return
        }
        val v: Vibrator? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.e("HimaIncomingCall", "Failed to acquire Vibrator", e)
            null
        }
        if (v == null || !v.hasVibrator()) return
        // Match the channel vibration cadence so foreground+unlocked rings
        // (where the OS notification is suppressed by B030) feel identical to
        // background rings driven by the channel.
        val pattern = longArrayOf(0, 1000, 500, 1000)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = loop
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
            ringtoneVibrator = v
            Log.d("HimaIncomingCall", "startIncomingCallVibration: started ringerMode=${am.ringerMode}")
        } catch (e: Exception) {
            Log.e("HimaIncomingCall", "Vibrator.vibrate threw", e)
        }
    }

    private fun stopIncomingCallVibration() {
        try { ringtoneVibrator?.cancel() } catch (e: Exception) {
            Log.e("HimaIncomingCall", "Vibrator.cancel threw", e)
        }
        ringtoneVibrator = null
    }

    fun playIncomingCallSound() {
        Log.d("HimaIncomingCall", "playIncomingCallSound: begin")
        // Stop any previous ringtone first
        stopRingtone()
        // Grab audio focus so background media (YouTube, Spotify, etc.) pauses
        // while we ring (B150). No-op if FCM service already grabbed it.
        requestRingtoneFocus()
        // B028: also vibrate. The MediaPlayer below uses USAGE_NOTIFICATION_RINGTONE
        // so in VIBRATE ringer mode the OS silences its sound — without an
        // explicit vibration the user gets neither sound NOR buzz. Channel-side
        // rings already vibrate via enableVibration(true) + pattern; this covers
        // the activity-only path (B030 skip-heads-up case).
        startIncomingCallVibration()

        try {
            // Without a headset, use USAGE_NOTIFICATION_RINGTONE + MODE_RINGTONE (ring stream).
            // With a headset, use VOICE_COMMUNICATION + MODE_IN_COMMUNICATION and pin a comm device
            // on API 31+ so OEMs do not blast the phone speaker.
            logAudioStateSnapshot("pre-play")
            val routeInfo = describeIncomingCallAudioRoute()
            val useHeadsetRingPath = routeInfo.hasExternalOutput
            // Clear stale earpiece pin, then either MODE_RINGTONE (speaker/earpiece) or
            // MODE_IN_COMMUNICATION + optional SCO so VOICE_COMMUNICATION routes to BT/wired.
            prepareRingtoneAudioState(useHeadsetRingPath)
            if (useHeadsetRingPath && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pinRingtoneCommunicationDeviceIfPossible()
            }
            val ringtoneUsage = if (useHeadsetRingPath) {
                AudioAttributes.USAGE_VOICE_COMMUNICATION
            } else {
                AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            }
            Log.d(
                "HimaIncomingCall",
                "ringtone routing: headsetPath=$useHeadsetRingPath external=${routeInfo.hasExternalOutput} " +
                    "outputs=${routeInfo.outputDescription} usage=${usageName(ringtoneUsage)} " +
                    "pinnedComm=$ringtoneSetCommDevice scoStarted=$ringtoneWeStartedSco"
            )

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(ringtoneUsage)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(
                applicationContext,
                RingtoneManager.TYPE_RINGTONE
            ) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: android.provider.Settings.System.DEFAULT_RINGTONE_URI
            Log.d("HimaIncomingCall", "ringtone uri=$ringtoneUri")
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(applicationContext, ringtoneUri)
                isLooping = true
                setOnPreparedListener { mp ->
                    Log.d(
                        "HimaIncomingCall",
                        "MediaPlayer onPrepared duration=${runCatching { mp.duration }.getOrDefault(-1)} " +
                            "preferredDevice=${describePreferredDevice(mp)} " +
                            "routedDevice=${describeRoutedDevice(mp)}"
                    )
                    try {
                        if (mediaPlayer === mp) {
                            mp.start()
                            Log.d(
                                "HimaIncomingCall",
                                "MediaPlayer started isPlaying=${runCatching { mp.isPlaying }.getOrDefault(false)} " +
                                    "looping=${runCatching { mp.isLooping }.getOrDefault(false)}"
                            )
                            logAudioStateSnapshot("post-start")
                        } else {
                            Log.w("HimaIncomingCall", "MediaPlayer onPrepared but instance changed; skipping start")
                        }
                    } catch (e: IllegalStateException) {
                        Log.w("MediaPlayer", "start() after release or invalid state", e)
                        stopRingtone()
                    } catch (e: Exception) {
                        Log.e("MediaPlayer", "start() failed", e)
                        stopRingtone()
                    }
                }
                setOnCompletionListener {
                    Log.d(
                        "HimaIncomingCall",
                        "MediaPlayer onCompletion (looping=${runCatching { isLooping }.getOrDefault(false)})"
                    )
                    stopRingtone()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(
                        "HimaIncomingCall",
                        "MediaPlayer onError what=${mediaPlayerErrorName(what)}($what) extra=$extra"
                    )
                    stopRingtone()
                    true
                }
                setOnInfoListener { _, what, extra ->
                    Log.d("HimaIncomingCall", "MediaPlayer onInfo what=$what extra=$extra")
                    false
                }
                prepareAsync() // async is safe
            }
            Log.d("HimaIncomingCall", "playIncomingCallSound: MediaPlayer prepareAsync submitted (looping=true)")
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Error playing ringtone: ${e.message}")
            stopRingtone()
            Log.d("HimaIncomingCall", "playIncomingCallSound: aborted after exception")
        }
    }

    /**
     * Dumps audio mode, ringer mode, and ring/music stream volumes so logs explain why a
     * ringtone was loud, quiet, or silent on a given route. Cheap enough to call twice.
     */
    private fun logAudioStateSnapshot(stage: String) {
        try {
            val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return
            val ringMax = am.getStreamMaxVolume(AudioManager.STREAM_RING)
            val ringVol = am.getStreamVolume(AudioManager.STREAM_RING)
            val musicMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val musicVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val voiceMax = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val voiceVol = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            val btScoOn = am.isBluetoothScoOn
            val btA2dpOn = @Suppress("DEPRECATION") am.isBluetoothA2dpOn
            val wiredOn = @Suppress("DEPRECATION") am.isWiredHeadsetOn
            val speakerOn = am.isSpeakerphoneOn
            val commDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.communicationDevice?.let { "${deviceTypeName(it.type)}#${it.id}" } ?: "null"
            } else {
                "n/a"
            }
            Log.d(
                "HimaIncomingCall",
                "audioState[$stage] mode=${audioModeName(am.mode)} ringer=${ringerModeName(am.ringerMode)} " +
                    "ring=$ringVol/$ringMax music=$musicVol/$musicMax voice=$voiceVol/$voiceMax " +
                    "btSco=$btScoOn btA2dp=$btA2dpOn wired=$wiredOn speakerphone=$speakerOn " +
                    "commDevice=$commDevice"
            )
        } catch (e: Exception) {
            Log.w("HimaIncomingCall", "logAudioStateSnapshot failed: ${e.message}")
        }
    }

    private fun audioModeName(mode: Int): String = when (mode) {
        AudioManager.MODE_NORMAL -> "normal"
        AudioManager.MODE_RINGTONE -> "ringtone"
        AudioManager.MODE_IN_CALL -> "in_call"
        AudioManager.MODE_IN_COMMUNICATION -> "in_communication"
        else -> "mode_$mode"
    }

    private fun ringerModeName(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_NORMAL -> "normal"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        AudioManager.RINGER_MODE_SILENT -> "silent"
        else -> "ringer_$mode"
    }

    private fun describePreferredDevice(mp: MediaPlayer): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mp.preferredDevice?.let { "${deviceTypeName(it.type)}#${it.id}" } ?: "default"
        } else "n/a"
    } catch (e: Throwable) {
        // B-v1110 #5 — catch Throwable, not Exception: getPreferredDevice/
        // getRoutedDevice are missing on some vendor ROMs and throw
        // NoSuchMethodError, which extends Error (not Exception) and so slipped
        // past the old catch and crashed call audio setup. This is a diagnostic
        // string helper, so degrading to an error label is always safe.
        "err:${e.javaClass.simpleName}"
    }

    private fun describeRoutedDevice(mp: MediaPlayer): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mp.routedDevice?.let { "${deviceTypeName(it.type)}#${it.id}" } ?: "unrouted"
        } else "n/a"
    } catch (e: Throwable) {
        // B-v1110 #5 — see describePreferredDevice: NoSuchMethodError on
        // getRoutedDevice() is an Error, so Throwable (not Exception) is required.
        "err:${e.javaClass.simpleName}"
    }

    private fun mediaPlayerErrorName(what: Int): String = when (what) {
        MediaPlayer.MEDIA_ERROR_UNKNOWN -> "unknown"
        MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "server_died"
        else -> "what_$what"
    }

    /**
     * Clears a stale communication-device pin, then sets [AudioManager.MODE_RINGTONE] for the
     * built-in ringer path, or [AudioManager.MODE_IN_COMMUNICATION] (and optionally SCO on API
     * 30 and below) when a headset is connected so [USAGE_VOICE_COMMUNICATION] routes there.
     *
     * Skipped when an actual call is already live ([AudioManager.MODE_IN_CALL] or already
     * [AudioManager.MODE_IN_COMMUNICATION]) so we do not steal routing from an active session.
     */
    private fun prepareRingtoneAudioState(useHeadsetRingPath: Boolean) {
        try {
            val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return

            val currentMode = am.mode
            if (currentMode == AudioManager.MODE_IN_CALL ||
                currentMode == AudioManager.MODE_IN_COMMUNICATION
            ) {
                Log.d(
                    "HimaIncomingCall",
                    "prepareRingtoneAudioState: live call mode=${audioModeName(currentMode)}, leaving route alone"
                )
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val pinned = am.communicationDevice
                if (pinned != null) {
                    val before = "${deviceTypeName(pinned.type)}#${pinned.id}"
                    am.clearCommunicationDevice()
                    ringtoneClearedCommDevice = true
                    Log.d(
                        "HimaIncomingCall",
                        "prepareRingtoneAudioState: cleared stale commDevice=$before"
                    )
                }
            }

            ringtoneSavedAudioMode = currentMode
            ringtoneWeStartedSco = false
            if (useHeadsetRingPath) {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && hasBluetoothAudioSink(am)) {
                    am.startBluetoothSco()
                    ringtoneWeStartedSco = true
                    Log.d(
                        "HimaIncomingCall",
                        "prepareRingtoneAudioState: mode ${audioModeName(currentMode)} -> in_communication startBluetoothSco=true"
                    )
                } else {
                    Log.d(
                        "HimaIncomingCall",
                        "prepareRingtoneAudioState: mode ${audioModeName(currentMode)} -> in_communication sco=${ringtoneWeStartedSco}"
                    )
                }
            } else {
                am.mode = AudioManager.MODE_RINGTONE
                Log.d(
                    "HimaIncomingCall",
                    "prepareRingtoneAudioState: mode ${audioModeName(currentMode)} -> ringtone"
                )
            }
        } catch (e: Exception) {
            Log.w("HimaIncomingCall", "prepareRingtoneAudioState failed: ${e.message}")
        }
    }

    /**
     * True if any Bluetooth-class output is present (A2DP/SCO/BLE headset). Used to decide
     * whether to start SCO on pre-Android-12 devices for ringtone routing.
     */
    private fun hasBluetoothAudioSink(am: AudioManager): Boolean {
        return try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { dev ->
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Prefer pinning a real headset for ringing on API 31+ so OEMs don't send
     * [USAGE_VOICE_COMMUNICATION] to the earpiece when BT is connected.
     */
    private fun pinRingtoneCommunicationDeviceIfPossible() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return
            val device = pickRingtoneCommunicationDevice(am) ?: run {
                Log.w("HimaIncomingCall", "pinRingtoneCommunicationDeviceIfPossible: no suitable device")
                return
            }
            val ok = am.setCommunicationDevice(device)
            if (ok) {
                ringtoneSetCommDevice = true
                Log.d(
                    "HimaIncomingCall",
                    "pinRingtoneCommunicationDeviceIfPossible: set ${deviceTypeName(device.type)}#${device.id}"
                )
            } else {
                Log.w(
                    "HimaIncomingCall",
                    "pinRingtoneCommunicationDeviceIfPossible: setCommunicationDevice failed for " +
                        "${deviceTypeName(device.type)}#${device.id}"
                )
            }
        } catch (e: Exception) {
            Log.w("HimaIncomingCall", "pinRingtoneCommunicationDeviceIfPossible: ${e.message}")
        }
    }

    private fun pickRingtoneCommunicationDevice(am: AudioManager): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val available = try {
            am.availableCommunicationDevices
        } catch (_: Exception) {
            emptyList()
        }
        if (available.isEmpty()) return null
        val preferenceOrder = buildList {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            add(AudioDeviceInfo.TYPE_HEARING_AID)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        }
        for (t in preferenceOrder) {
            available.firstOrNull { it.type == t }?.let { return it }
        }
        return available.firstOrNull()
    }

    private fun revertRingtoneRouting() {
        try {
            val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return
            if (ringtoneSetCommDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
                Log.d("HimaIncomingCall", "revertRingtoneRouting: clearCommunicationDevice")
            }
            ringtoneSetCommDevice = false
            if (ringtoneWeStartedSco) {
                am.stopBluetoothSco()
                Log.d("HimaIncomingCall", "revertRingtoneRouting: stopBluetoothSco")
            }
            ringtoneWeStartedSco = false
        } catch (e: Exception) {
            Log.w("HimaIncomingCall", "revertRingtoneRouting failed: ${e.message}")
            ringtoneSetCommDevice = false
            ringtoneWeStartedSco = false
        }
    }

    /**
     * Restores audio mode to whatever was active before the ringtone, so a normal app session
     * doesn't sit in MODE_RINGTONE forever. No-op if [prepareRingtoneAudioState] didn't change it.
     */
    private fun restoreRingtoneAudioState() {
        val savedMode = ringtoneSavedAudioMode ?: return
        ringtoneSavedAudioMode = null
        val cleared = ringtoneClearedCommDevice
        ringtoneClearedCommDevice = false
        try {
            val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return
            // Don't stomp on an active telephony/voip call. We use MODE_IN_COMMUNICATION for the
            // headset ringtone path ourselves, so we must still restore when current is in_communication.
            val currentMode = am.mode
            if (currentMode == AudioManager.MODE_IN_CALL) {
                Log.d(
                    "HimaIncomingCall",
                    "restoreRingtoneAudioState: call now live (${audioModeName(currentMode)}), keeping mode"
                )
                return
            }
            am.mode = savedMode
            Log.d(
                "HimaIncomingCall",
                "restoreRingtoneAudioState: mode ${audioModeName(currentMode)} -> ${audioModeName(savedMode)} clearedCommDevice=$cleared"
            )
        } catch (e: Exception) {
            Log.w("HimaIncomingCall", "restoreRingtoneAudioState failed: ${e.message}")
        }
    }

    private data class IncomingCallAudioRoute(
        val hasExternalOutput: Boolean,
        val outputDescription: String
    )

    /**
     * Inspects current output devices so [playIncomingCallSound] can pick a usage that won't
     * force-route the ringtone to the loudspeaker when a headset is connected.
     */
    private fun describeIncomingCallAudioRoute(): IncomingCallAudioRoute {
        return try {
            val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return IncomingCallAudioRoute(false, "audio_service_unavailable")
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val descriptions = devices.map { deviceTypeName(it.type) }
            val hasExternal = devices.any { isExternalOutput(it.type) }
            IncomingCallAudioRoute(
                hasExternalOutput = hasExternal,
                outputDescription = descriptions.joinToString(prefix = "[", postfix = "]")
            )
        } catch (e: Exception) {
            Log.w("HimaIncomingCall", "describeIncomingCallAudioRoute failed: ${e.message}")
            IncomingCallAudioRoute(false, "error:${e.javaClass.simpleName}")
        }
    }

    private fun isExternalOutput(type: Int): Boolean {
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            type == AudioDeviceInfo.TYPE_HEARING_AID
        ) return true
        // BLE constants only exist on API 31+; reference them defensively.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                type == AudioDeviceInfo.TYPE_BLE_BROADCAST
            ) return true
        }
        return false
    }

    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt_a2dp"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt_sco"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset"
        AudioDeviceInfo.TYPE_HEARING_AID -> "hearing_aid"
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            "ble_headset"
        } else {
            "type_$type"
        }
    }

    private fun usageName(usage: Int): String = when (usage) {
        AudioAttributes.USAGE_MEDIA -> "media"
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> "ringtone"
        AudioAttributes.USAGE_VOICE_COMMUNICATION -> "voice_comm"
        else -> "usage_$usage"
    }

    /**
     * Plays the gift "send" sound effect. Reworked for B071:
     *  - Uses a dedicated [giftSoundPlayer] so a rapid burst of gifts no
     *    longer tears down the incoming-call ringtone player.
     *  - `prepareAsync()` instead of `prepare()` — synchronous prepare
     *    blocked the UI thread ~50-200ms per gift, which stacked when
     *    users tapped 5-10 gifts back-to-back and starved Agora's
     *    SurfaceView compositing, freezing video on both sides.
     *  - Releases its own MediaPlayer on completion / error so we don't
     *    leak a handle per gift.
     */
    fun playSendGiftSound() {
        try {
            // Tear down the previous gift-sound player (if any) so back-to-back
            // sends don't leak. Does NOT touch the ringtone player anymore.
            giftSoundPlayer?.let { prev ->
                try { prev.release() } catch (_: Exception) {}
            }
            giftSoundPlayer = null

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE) // respects silent mode, matches prior behaviour
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val uri = Uri.parse("android.resource://${packageName}/${R.raw.gift_tune}")
            giftSoundPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(applicationContext, uri)
                setOnPreparedListener { mp ->
                    try { mp.start() } catch (e: Exception) {
                        Log.w("GiftSound", "start() failed: ${e.message}")
                    }
                }
                setOnCompletionListener { mp ->
                    try { mp.release() } catch (_: Exception) {}
                    if (giftSoundPlayer === mp) giftSoundPlayer = null
                }
                setOnErrorListener { mp, what, extra ->
                    Log.w("GiftSound", "MediaPlayer error what=$what extra=$extra")
                    try { mp.release() } catch (_: Exception) {}
                    if (giftSoundPlayer === mp) giftSoundPlayer = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("GiftSound", "playSendGiftSound: ${e.message}", e)
            giftSoundPlayer = null
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
        val wasPlaying = try {
            mediaPlayer?.isPlaying == true
        } catch (_: IllegalStateException) {
            false
        }
        Log.d("HimaIncomingCall", "stopRingtone: begin wasPlaying=$wasPlaying")
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
            // Release ringtone audio focus so paused media (YouTube etc.) can
            // resume — paired with requestRingtoneFocus in playIncomingCallSound
            // and the FCM service for the B150 fix.
            releaseRingtoneFocus()
            // B028: stop the incoming-call vibration paired with the ringtone.
            stopIncomingCallVibration()
            revertRingtoneRouting()
            restoreRingtoneAudioState()
            Log.d("HimaIncomingCall", "stopRingtone: end released")
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


    @Volatile
    private var incomingCallSetAt: Long = 0L

    /** Notification tag for the current CallStyle incoming notification (matches [callId]). */
    @Volatile
    private var lastIncomingCallTag: String? = null

    /**
     * True while the user is inside an Agora audio/video call (any of the four
     * *CallingActivity classes have been onCreate'd but not yet onDestroy'd).
     * Used to drop stray OneSignal / FCM call-style pushes so the device
     * doesn't ring while a call is already in progress.
     */
    @Volatile
    private var isCallActive: Boolean = false

    // B156a — process-wide live-call-activity counter, updated by
    // ActivityLifecycleCallbacks. `currentActivity` only updates in
    // onActivityStarted/Resumed, leaving a ~50-200ms window between user
    // tap and onResume where the previous activity is still "current".
    // FCM-incoming busy-checks could squeeze through that window and
    // launch a second call. Counting from onActivityCreated closes it.
    // AtomicInteger is read by FCM on a background thread.
    private val activeCallActivityCount = AtomicInteger(0)

    fun markCallActive() { isCallActive = true }
    fun markCallEnded() { isCallActive = false }
    fun isInActiveCall(): Boolean =
        isCallActive || activeCallActivityCount.get() > 0

    /**
     * B125 — narrower variant of [isInActiveCall] that returns true only when
     * one of the 4 in-call activities (Male/Female × Audio/Video) is alive.
     * Used by the *connecting* activities to detect "user is trying to start
     * a second call while the first is still going" without falsely triggering
     * on their own ActivityLifecycleCallbacks counter increment.
     */
    fun isInRealCall(): Boolean = isCallActive

    fun setIncomingCall(senderId: Int, callType: String, channelName: String, callId: Int) {
        this.senderId = senderId
        this.callTypeForSplashActivity = callType
        this.channelName = channelName
        this.callIdForSplashActivity = callId
        this.incomingCall = true
        this.incomingCallSetAt = System.currentTimeMillis()
        this.lastIncomingCallTag = callId.toString()
    }

    /**
     * B023 — caller display info is stored alongside the routing fields so
     * MainActivity can re-launch the proper accept screen (avatar + name
     * populated) when the user opens Hima via launcher while a call is
     * still ringing.
     */
    fun setIncomingCallerInfo(name: String?, image: String?) {
        this.incomingCallerName = name
        this.incomingCallerImage = image
    }

    fun getIncomingCallerName(): String = incomingCallerName.orEmpty()
    fun getIncomingCallerImage(): String = incomingCallerImage.orEmpty()

    fun getLastIncomingCallTag(): String? = lastIncomingCallTag

    /**
     * Cancels the CallStyle incoming notification using [lastIncomingCallTag] when set,
     * otherwise legacy `cancel(1)`.
     */
    fun cancelIncomingCallStyleNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val tag = lastIncomingCallTag
        if (tag != null) nm.cancel(tag, INCOMING_CALL_NOTIFICATION_ID)
        else nm.cancel(INCOMING_CALL_NOTIFICATION_ID)
    }

    /**
     * Bulk-cancels every outstanding incoming-call notification in the tray:
     * the FCM `calls_v4` CallStyle path and any OneSignal server-side call
     * push that happened to slip through before the NSE suppressor ran.
     *
     * Chat pushes share the OneSignal default channel so we do NOT cancel by
     * channel wholesale — we only target notifications whose title/body/extras
     * identify them as call pushes. See [looksLikeCallPush].
     */
    fun cancelAllIncomingCallNotifications() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        // Always wipe the legacy CallStyle id + any tagged variant we know about.
        runCatching {
            val tag = lastIncomingCallTag
            if (tag != null) nm.cancel(tag, INCOMING_CALL_NOTIFICATION_ID)
            nm.cancel(INCOMING_CALL_NOTIFICATION_ID)
        }
        // Sweep the active tray for anything else that smells like a call push.
        runCatching {
            nm.activeNotifications?.forEach { sbn ->
                val channel = sbn.notification?.channelId
                if (channel == com.gmwapp.hima.agora.MyFirebaseMessagingService.CALLS_NOTIFICATION_CHANNEL_ID) {
                    nm.cancel(sbn.tag, sbn.id)
                    return@forEach
                }
                if (looksLikeCallPush(sbn.notification)) {
                    nm.cancel(sbn.tag, sbn.id)
                }
            }
        }
    }

    private fun looksLikeCallPush(notif: android.app.Notification?): Boolean {
        if (notif == null) return false
        val extras = notif.extras ?: return false
        val title = (extras.getCharSequence(android.app.Notification.EXTRA_TITLE) ?: "").toString()
        val body = (extras.getCharSequence(android.app.Notification.EXTRA_TEXT) ?: "").toString()
        val haystack = (title + " " + body).lowercase()
        return haystack.contains("video call from") ||
            haystack.contains("audio call from") ||
            haystack.contains("wants to talk to you") ||
            haystack.contains("incoming call")
    }

    fun clearIncomingCall() {
        this.incomingCall = false
        this.incomingCallSetAt = 0L
        this.lastIncomingCallTag = null
        this.incomingCallerName = null
        this.incomingCallerImage = null
    }

    fun isIncomingCall(): Boolean = incomingCall

    /**
     * True while an incoming call is pending/ringing within [maxAgeMs]. Default matches the
     * CallStyle `setTimeoutAfter(35s)` with a small buffer, so a stale flag never permanently
     * blocks new incoming calls.
     */
    fun isIncomingCallFresh(maxAgeMs: Long = 45_000L): Boolean {
        if (!incomingCall) return false
        val age = System.currentTimeMillis() - incomingCallSetAt
        return age in 0..maxAgeMs
    }
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
                // BUG-011: Don't log sensitive attribution data in release builds
                if (BuildConfig.DEBUG) {
                    conversionData?.let {
                        for ((key, value) in it) {
                            Log.d("AppsFlyer", "Conversion data: $key = $value")
                        }
                    } ?: Log.d("AppsFlyer", "Conversion data is null")
                }
            }

            override fun onConversionDataFail(errorMessage: String?) {
                if (BuildConfig.DEBUG) {
                    Log.e("AppsFlyer", "Conversion data failure: $errorMessage")
                }
            }

            override fun onAppOpenAttribution(attributionData: MutableMap<String, String>?) {
                if (BuildConfig.DEBUG) {
                    attributionData?.let {
                        for ((key, value) in it) {
                            Log.d("AppsFlyer", "Attribution data: $key = $value")
                        }
                    } ?: Log.d("AppsFlyer", "Attribution data is null")
                }
            }

            override fun onAttributionFailure(errorMessage: String?) {
                if (BuildConfig.DEBUG) {
                    Log.e("AppsFlyer", "Attribution failure: $errorMessage")
                }
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