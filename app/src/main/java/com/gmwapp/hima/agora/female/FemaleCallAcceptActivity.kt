package com.gmwapp.hima.agora.female

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.MyFirebaseMessagingService
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import android.telecom.DisconnectCause
import com.gmwapp.hima.databinding.ActivityFemaleCallAcceptBinding
import com.gmwapp.hima.retrofit.responses.CallEndReason
import com.gmwapp.hima.retrofit.responses.CallEndedBy
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.viewmodels.CallStatusViewModel
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlin.system.exitProcess

@AndroidEntryPoint
class FemaleCallAcceptActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFemaleCallAcceptBinding
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()
    private val callStatusViewModel: CallStatusViewModel by viewModels()

    private var callType: String? = null
    private var receiverId: Int = -1
    private var call_Id: Int = 0
    var callerName = ""
    var callerImage = ""
    private val userAvatarViewModel: UserAvatarViewModel by viewModels()

    private var channelName: String? = null
    var userId: Int? = null
    private var prefetchedAgoraToken: String? = null
    private var prefetchedAgoraAppId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Route the volume rocker to STREAM_RING while this activity is on
        // screen so volume up/down adjusts the incoming ringtone (B027).
        // Without this the default STREAM_MUSIC is targeted and the rocker
        // appears to do nothing while the phone is ringing.
        volumeControlStream = AudioManager.STREAM_RING
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Log.d(
            "HimaIncomingCall",
            "FemaleCallAcceptActivity.onCreate flags=${intent.flags} action=${intent.action} keyguardLocked=${km.isKeyguardLocked}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        enableEdgeToEdge()
        binding = ActivityFemaleCallAcceptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.id?.let { userId = userData?.id}

        callType = intent.getStringExtra("CALL_TYPE")
        receiverId = intent.getIntExtra("SENDER_ID", -1)
        channelName = intent.getStringExtra("CHANNEL_NAME")

        callerName = intent.getStringExtra("Caller_NAME").orEmpty()
        callerImage = intent.getStringExtra("Caller_Image").orEmpty()

        Log.d("callerdeatails","$callerImage")
        Log.d("callerdeatails","$callerName")
        call_Id = intent.getIntExtra("CALL_ID", 0)

        // Pre-request RECORD_AUDIO so permission dialog won't block call start on accept
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        // Pre-fetch Agora token while the user decides to accept/reject
        if (!channelName.isNullOrEmpty()) {
            prefetchAgoraToken(channelName!!)
        }

        // Start pulse animations for the avatar rings
        startPulseAnimations()

        if (callType=="audio"){
            binding.calltype.setText("Incoming Voice Call")
            binding.callTypeIcon.setImageResource(R.drawable.ic_mic)
        }else{
            binding.calltype.setText("Incoming Video Call")
            binding.callTypeIcon.setImageResource(R.drawable.ic_videocam)
        }


        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked // Check if device is locked

        val pendingTag = BaseApplication.getInstance()?.getLastIncomingCallTag()
        val expectedTag = if (call_Id != 0) call_Id.toString() else null
        val alreadyHandled = BaseApplication.getInstance()?.isIncomingCall() != true ||
            (expectedTag != null && pendingTag != null && pendingTag != expectedTag)
        if (alreadyHandled) {
            Log.d(
                "HimaIncomingCall",
                "FemaleCallAcceptActivity: stale launch (pendingTag=$pendingTag expected=$expectedTag) -> finish"
            )
            BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
            finish()
            return
        }

        // Activity now owns the call presentation; cancel the heads-up so the
        // OS channel ringtone stops before MediaPlayer takes over the loop —
        // otherwise both play in parallel on locked phones (B147 fix).
        BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
        if (BaseApplication.getInstance()?.isRingtonePlaying() == false) {
            BaseApplication.getInstance()?.playIncomingCallSound()
        }




        binding.callerName.setText(callerName.trimEnd { it.isDigit() })
        Glide.with(this)
            .load(callerImage)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivLogo)





        Log.d("callType","from notification $callType")

        userAvatarViewModel.getUserAvatar(receiverId)

        avatarObservers()

        Log.d("CallID","$call_Id")



        binding.accpet.setOnClickListener {

            if (receiverId != -1 && !channelName.isNullOrEmpty() && !callType.isNullOrEmpty()) {
                Log.d(
                    "CreatorCallDiag",
                    "FAccept.click sender(female)=$userId receiver(male)=$receiverId " +
                        "channel=$channelName callId=$call_Id callType=$callType"
                )
                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "accepted")

                if (callType == "audio") {
                    BaseApplication.getInstance()?.stopRingtone()
                    HimaTelecomManager.markActive()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    val intent = Intent(this, FemaleAudioCallingActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("CALL_ID", call_Id)
                        prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                        prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                        Log.d("RECEIVER_ID","$receiverId")
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }else{
                    BaseApplication.getInstance()?.stopRingtone()
                    HimaTelecomManager.markActive()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    val intent = Intent(this, FemaleVideoCallingActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("CALL_ID", call_Id)
                        prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                        prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }
            }
        }
        binding.reject.setOnClickListener {

            if (receiverId != -1 && !channelName.isNullOrEmpty() && !callType.isNullOrEmpty()) {
                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "rejected")
                userId?.let { selfId ->
                    Log.d("CallStatus", "FemaleAccept.reject → rejected/receiver self=$selfId peer=$receiverId callId=$call_Id")
                    callStatusViewModel.saveCallStatus(
                        userId = selfId,
                        receivedUserId = receiverId,
                        callId = call_Id,
                        endReason = CallEndReason.REJECTED,
                        endedBy = CallEndedBy.RECEIVER,
                        endedByUserId = selfId,
                        durationSeconds = 0,
                    )
                }

                if (isLocked) {
                    HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                    BaseApplication.getInstance()?.stopRingtone()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    finishAffinity()  // Closes all activities in the task
                    exitProcess(0)    // Force closes the app
                }


                HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                BaseApplication.getInstance()?.stopRingtone()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                BaseApplication.getInstance()?.clearIncomingCall()
                val intent = Intent(this@FemaleCallAcceptActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()

            }
        }


        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
//
//                if (receiverId != -1 && !channelName.isNullOrEmpty() && !callType.isNullOrEmpty()) {
//                    sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "rejected")
//
//                    BaseApplication.getInstance()?.stopRingtone()
//                    val intent = Intent(this@FemaleCallAcceptActivity, MainActivity::class.java)
//                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
//                    startActivity(intent)
//                    finish()
//
//                }

            }
        })

        // B021: When the Accept button on the notification was tapped while the
        // app was killed, the FCM-service launched this activity with
        // AUTO_ACCEPT=true. Post to the main queue so all observer/view wiring
        // above finishes first, then perform the same click the user would do.
        maybeAutoAccept(intent)
    }

    /**
     * Re-entry path for AUTO_ACCEPT — singleTop launchMode means a notification
     * tap on Accept while this activity is already in the stack will arrive via
     * onNewIntent, not onCreate. Handle it here too so cold-start and warm-start
     * notification-Accept paths converge on [binding.accpet]'s click handler.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeAutoAccept(intent)
    }

    private fun maybeAutoAccept(intent: Intent?) {
        val auto = intent?.getBooleanExtra("AUTO_ACCEPT", false) == true
        if (!auto) return
        // B022: this activity now owns the call lifecycle (and will start
        // CallingService as the in-call FGS once accepted), so the warm-up
        // service can shut down to free a foreground-service slot.
        com.gmwapp.hima.agora.FcmCallService.stop(this)
        // B022: don't fire performClick() immediately — wait briefly for the
        // Agora token prefetch (kicked off in onCreate) to land so the calling
        // activity receives it via the intent extras and can skip its own
        // backend round-trip. Without this, cold-start accept duplicates the
        // token fetch and the call can ring-out before joinChannel finishes.
        val startMs = System.currentTimeMillis()
        val maxWaitMs = 1500L
        val pollHandler = Handler(Looper.getMainLooper())
        val poll = object : Runnable {
            override fun run() {
                val ready = !prefetchedAgoraToken.isNullOrEmpty()
                val timedOut = System.currentTimeMillis() - startMs >= maxWaitMs
                if (ready || timedOut) {
                    Log.d(
                        "HimaIncomingCall",
                        "FemaleCallAcceptActivity: AUTO_ACCEPT firing tokenReady=$ready timedOut=$timedOut waitedMs=${System.currentTimeMillis() - startMs}"
                    )
                    binding.accpet.performClick()
                } else {
                    pollHandler.postDelayed(this, 100L)
                }
            }
        }
        pollHandler.post(poll)
    }

    private fun avatarObservers() {
        userAvatarViewModel.userAvatarLiveData.observe(this) { response ->
            Log.d("userAvatarLiveData", "Image URL: $response")

            if (response != null && response.success) {
                val imageUrl = response.data?.image
                callerName = response.data?.name.toString()
                Log.d("UserAvatar", "Image URL: $imageUrl")

                binding.callerName.setText(callerName.trimEnd { it.isDigit() })
                // Load the avatar image into an ImageView using Glide or Picasso
                // Glide.with(this).load(imageUrl).into(binding.ivMaleUser)
                Glide.with(this)
                    .load(imageUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.ivLogo)

            }
        }

        userAvatarViewModel.userAvatarErrorLiveData.observe(this) { errorMessage ->
            Log.e("UserAvatarError", errorMessage)
        }
    }

    fun sendCallNotification(senderId:Int, receiverId:Int, callType:String,channelName:String,message:String  ) {
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName =channelName ,
            message = message
        )
        observeNotificationResponse()
    }

    fun observeNotificationResponse() {
        fcmNotificationViewModel.notificationResponseLiveData.observe(this) { response ->
            response?.let {
                if (it.success) {
                    Log.d("FCMNotification", "Notification sent successfully!")
                } else {
                    Log.e("FCMNotification", "Failed to send notification")
                }
            }
        }
    }

    private fun prefetchAgoraToken(channelForToken: String) {
        Log.d("AgoraTiming", "FemaleCallAccept prefetchAgoraToken started at ${System.currentTimeMillis()}")
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            if (response != null && response.success == true && !response.token.isNullOrEmpty()) {
                prefetchedAgoraToken = response.token
                prefetchedAgoraAppId = response.app_id
                Log.d("AgoraTiming", "FemaleCallAccept prefetchAgoraToken received at ${System.currentTimeMillis()}")
            }
        }
        agoraViewModel.getAgoraToken(channelForToken, 0, "publisher", 3600)
    }

    private fun startPulseAnimations() {
        try {
            // Load pulse animation
            val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
            
            // Start animation on outer ring with delay
            Handler(Looper.getMainLooper()).postDelayed({
                binding.pulseRingOuter.startAnimation(pulseAnimation)
            }, 0)
            
            // Start animation on middle ring with delay for staggered effect
            Handler(Looper.getMainLooper()).postDelayed({
                binding.pulseRingMiddle.startAnimation(pulseAnimation)
            }, 500)
        } catch (e: Exception) {
            Log.e("PulseAnimation", "Error starting pulse animations: ${e.message}")
        }
    }

    /**
     * One-press silence for the incoming ringtone — matches native phone-call
     * behaviour. Consumes volume up/down while ringing so we stop the channel
     * sound + MediaPlayer instead of just nudging STREAM_RING by one notch.
     * The call screen stays up; user can still Accept/Decline.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val ringing = BaseApplication.getInstance()?.isRingtonePlaying() == true
            if (ringing) {
                BaseApplication.getInstance()?.stopRingtone()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop animations
        try {
            binding.pulseRingOuter.clearAnimation()
            binding.pulseRingMiddle.clearAnimation()
        } catch (e: Exception) {
            Log.e("PulseAnimation", "Error clearing animations: ${e.message}")
        }
    }

}