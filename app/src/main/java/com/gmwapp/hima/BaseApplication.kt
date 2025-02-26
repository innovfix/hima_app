package com.gmwapp.hima

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Bundle

import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.utils.DPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.zegocloud.uikit.prebuilt.call.core.CallInvitationServiceImpl
import com.zegocloud.uikit.prebuilt.call.core.notification.RingtoneManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    //val ONESIGNAL_APP_ID = "2c7d72ae-8f09-48ea-a3c8-68d9c913c592"
    val ONESIGNAL_APP_ID = "5cd4154a-1ece-4c3b-b6af-e88bafee64cd"
    private lateinit var db: FirebaseFirestore
    private var listenerRegistration: ListenerRegistration? = null
    lateinit var gender : String
    lateinit var userid : String
    private val lifecycleCallbacks: ActivityLifecycleCallbacks =
        object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivity = activity

            }

            override fun onActivityStarted(activity: Activity) {
                currentActivity = activity

            }

            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity

                if (activity is FemaleCallingActivity) {
                    stopRingtone()
                }

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
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }

        }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    companion object {
        private var mInstance: BaseApplication? = null
        private var currentActivity: Activity? = null

        var notificationId: String? = null  // Store notification ID globally


        fun getInstance(): BaseApplication? {
            return mInstance
        }


        fun getCurrentActivity(): Activity? {
            return currentActivity
        }
    }

    override fun onCreate() {
        super.onCreate()
        mInstance = this
        mPreferences = DPreferences(this)
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()
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


        genderCheck()



    }

    private fun genderCheck() {
        val userData = getInstance()?.getPrefs()?.getUserData()

        if (userData?.gender != null && userData.id != null) {
            gender = if (userData.gender == DConstants.MALE) "maleUsers" else "femaleUsers"
            userid = userData.id.toString()

            if (gender == "maleUsers"){
                listenForCallChangesMale(gender,userid)
            }else{
                listenForCallChangesFemale(gender, userid)

            }

        }
    }


    private fun listenForCallChangesFemale(gender: String, maleUserId: String) {
        val callDocRef = db.collection(gender).document(maleUserId)

        Log.d("FirestoreListener", "Listening for changes on: $gender / $maleUserId")

        listenerRegistration = callDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("FirestoreListener", "Error listening to Firestore updates", e)
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                Log.e("FirestoreListener", "Document does not exist or is null")
                return@addSnapshotListener
            }

            val isCalling = snapshot.getBoolean("isCalling") ?: false
            val maleUserId = snapshot.getString("maleUserId")
            val channelName = snapshot.getString("channelName")

            Log.d("FirestoreListener", "isCalling value: $isCalling")

            if (isCalling) {
                Log.d("FirestoreListener", "Incoming call detected")
                playIncomingCallSound()
                navigateToCallAcceptActivity(maleUserId, channelName)
            } else {
                Log.d("FirestoreListener", "Call ended")
                navigateToMainActivityIfNotThere()
                stopRingtone()
            }
        }
    }


    private fun listenForCallChangesMale(gender:String, maleUserId:String) {
        val callDocRef =  db.collection(gender).document(maleUserId)

        listenerRegistration = callDocRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val isCalling = snapshot.getBoolean("isCalling") ?: false
            if (!isCalling) {
                navigateToMainActivityIfNotThere()
            }
        }
    }



    private fun navigateToCallAcceptActivity(maleUserId: String?, channelName: String?) {
        val activity = getCurrentActivity()
        Log.d("HelloRishabh", "Current Activity: $activity")

        if (activity is FemaleCallAcceptActivity) {
            Log.d("HelloRishabh", "Already in FemaleCallAcceptActivity, no need to navigate.")
            return
        }

        if (activity != null && !activity.isFinishing) {
            activity.runOnUiThread {
                val intent = Intent(activity, FemaleCallAcceptActivity::class.java).apply {
                    putExtra("channelName", channelName)
                    putExtra("maleUserId", maleUserId)
                }
                activity.startActivity(intent)
            }
        } else {
            Log.e("HelloRishabh", "No active activity found!")
        }
    }

    private fun navigateToMainActivityIfNotThere() {
        val activity = getCurrentActivity()
        Log.d("ActivityCheck", "Current Activity: $activity")

        if (activity != null && activity !is MainActivity) {
            activity.runOnUiThread {
                stopRingtone()
                val intent = Intent(activity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                activity.startActivity(intent)
            }
        } else {
            Log.d("ActivityCheck", "Already in MainActivity, no action needed.")
        }
    }








    private fun playIncomingCallSound() {
        stopRingtone() // Stop any existing ringtone before playing a new one

        mediaPlayer = MediaPlayer.create(applicationContext, R.raw.rhythm)
        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null // Set to null to avoid using a released player
        }
        mediaPlayer?.start()
    }


    private fun stopRingtone() {
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

}
