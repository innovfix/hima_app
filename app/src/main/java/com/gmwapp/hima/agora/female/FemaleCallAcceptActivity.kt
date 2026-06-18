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
import android.widget.Toast
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

    // B10/TC_009: gate the call-screen launch on the "accepted" relay so the
    // creator never enters a blank, caller-gone call. The backend's
    // ACCEPTED_DEAD_BLOCK returns success=false / error="call_already_ended"
    // (8s-grace-validated, so it won't eat a legit just-connected accept).
    // Fail-OPEN: launch anyway on success / any other outcome / timeout, so a
    // slow or failing relay never blocks a legitimate accept.
    private val acceptGateHandler = Handler(Looper.getMainLooper())
    private var acceptInFlight = false
    private var acceptLaunchHandled = false
    private var pendingChannel: String? = null
    private var pendingReceiver: Int = -1
    private var pendingCallType: String? = null
    private val ACCEPT_GATE_TIMEOUT_MS = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // B024: this activity is the call UI; any system heads-up banner is
        // now redundant. Wipe ALL incoming-call notifications (FCM + OneSignal
        // paths) as the FIRST thing we do — before setContentView, Glide,
        // viewmodels, etc. — so the banner+full-screen overlap window shrinks
        // to roughly the FSI->process-start latency instead of ~300ms of
        // onCreate setup. Cleared early on every entry, including cold-start.
        BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
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
        Log.d(
            "VideoCallFlow",
            "FemaleAccept.onCreate channel=$channelName callId=$call_Id senderId=$receiverId " +
                "callType=$callType userId=$userId"
        )

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



        // B10/TC_009: single gate observer. Only acts while an accept is in
        // flight (acceptInFlight) and only once (acceptLaunchHandled). The
        // "accepted" relay's 200-body lands here; real network failures go to
        // the error LiveData instead (→ fail-open timer). Registered once.
        fcmNotificationViewModel.notificationResponseLiveData.observe(this) { resp ->
            if (!acceptInFlight || acceptLaunchHandled || resp == null) return@observe
            val callAlreadyEnded = !resp.success &&
                (resp.error == "call_already_ended" ||
                    resp.message.contains("already ended", ignoreCase = true))
            acceptLaunchHandled = true
            acceptInFlight = false
            acceptGateHandler.removeCallbacksAndMessages(null)
            if (callAlreadyEnded) {
                Log.d("CreatorCallDiag", "FAccept gate: call_already_ended → abort launch callId=$call_Id")
                BaseApplication.getInstance()?.clearIncomingCall()
                Toast.makeText(this, "This call has already ended", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                launchCallScreen()
            }
        }

        binding.accpet.setOnClickListener {

            if (receiverId != -1 && !channelName.isNullOrEmpty() && !callType.isNullOrEmpty()) {
                Log.d(
                    "CreatorCallDiag",
                    "FAccept.click sender(female)=$userId receiver(male)=$receiverId " +
                        "channel=$channelName callId=$call_Id callType=$callType"
                )
                Log.d(
                    "VideoCallFlow",
                    "FemaleAccept.acceptClick channel=$channelName callId=$call_Id callType=$callType " +
                        "tokenPrefetched=${!prefetchedAgoraToken.isNullOrEmpty()} appIdPrefetched=${!prefetchedAgoraAppId.isNullOrEmpty()}"
                )

                // B10/TC_009: silence the ring immediately (responsiveness) but
                // DEFER the call-screen launch until the "accepted" relay tells
                // us the call isn't already dead. The gate observer above and the
                // fail-open timer below decide; markActive happens only on launch.
                BaseApplication.getInstance()?.stopRingtone()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()

                pendingChannel = channelName
                pendingReceiver = receiverId
                pendingCallType = callType
                acceptInFlight = true
                acceptLaunchHandled = false
                acceptGateHandler.removeCallbacksAndMessages(null)
                acceptGateHandler.postDelayed({
                    if (!acceptLaunchHandled) {
                        acceptLaunchHandled = true
                        acceptInFlight = false
                        Log.d("CreatorCallDiag", "FAccept gate: relay timeout → fail-open launch callId=$call_Id")
                        launchCallScreen()
                    }
                }, ACCEPT_GATE_TIMEOUT_MS)

                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "accepted")
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

    // B10/TC_009: launch the in-call activity. Reached only after the accept
    // gate clears (relay said the call is live, or fail-open timeout). markActive
    // is done here — never on an aborted accept.
    private fun launchCallScreen() {
        if (isFinishing || isDestroyed) return
        val channel = pendingChannel
        val type = pendingCallType
        val receiver = pendingReceiver
        if (channel.isNullOrEmpty() || type.isNullOrEmpty() || receiver == -1) {
            Log.w("CreatorCallDiag", "FAccept launchCallScreen: missing pending args → abort callId=$call_Id")
            finish()
            return
        }
        HimaTelecomManager.markActive()
        BaseApplication.getInstance()?.clearIncomingCall()
        val target = if (type == "audio") FemaleAudioCallingActivity::class.java
                     else FemaleVideoCallingActivity::class.java
        val intent = Intent(this, target).apply {
            putExtra("CHANNEL_NAME", channel)
            putExtra("RECEIVER_ID", receiver)
            putExtra("CALL_ID", call_Id)
            prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
            prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun prefetchAgoraToken(channelForToken: String) {
        Log.d("AgoraTiming", "FemaleCallAccept prefetchAgoraToken started at ${System.currentTimeMillis()}")
        Log.d("VideoCallFlow", "FemaleAccept.prefetchToken.start channel=$channelForToken callId=$call_Id")
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            Log.d(
                "VideoCallFlow",
                "FemaleAccept.prefetchToken.response success=${response?.success} " +
                    "tokenPresent=${!response?.token.isNullOrEmpty()} appIdPresent=${!response?.app_id.isNullOrEmpty()}"
            )
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
        // I022 — wired headset hook / BT AVRCP play-pause = single-press
        // accept on the incoming-call screen, matching native phone /
        // WhatsApp parity. MEDIA_PLAY_PAUSE covers BT headsets that map
        // their button to the media key instead of HEADSETHOOK.
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            binding.accpet.performClick()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // TC_003 ghost-block fix: clear the "incoming call" freshness flag on any
        // teardown that didn't already clear it — e.g. the launchCallScreen abort
        // at :438 (missing pending args), a swipe-away, or a system kill. Left set,
        // isIncomingCallFresh() stays true ~45s and the single-call guard silently
        // auto-rejects the creator's NEXT caller, so she never rings. Guard on
        // isFinishing so a config-change recreate (rotation) does NOT drop a still-
        // live ring. Accept/reject/launch paths already clear it earlier
        // (:224/:292/:301/:442); this is the catch-all for every other exit.
        if (isFinishing) {
            // Only clear if the still-armed incoming call is OURS. Mirror the
            // stale-launch guard at :165-168: if a NEWER call has re-armed the
            // flag with a different tag (pendingTag != our call_Id), leave it —
            // clearing would wipe the newer call's live ring. Otherwise clear so
            // a stuck flag can't blind the creator's next caller.
            val pendingTag = BaseApplication.getInstance()?.getLastIncomingCallTag()
            val ourTag = if (call_Id != 0) call_Id.toString() else null
            if (pendingTag == null || ourTag == null || pendingTag == ourTag) {
                BaseApplication.getInstance()?.clearIncomingCall()
            }
        }
        // B10/TC_009: drop any pending accept-gate fail-open so it can't fire
        // against a destroyed activity.
        acceptGateHandler.removeCallbacksAndMessages(null)
        // Stop animations
        try {
            binding.pulseRingOuter.clearAnimation()
            binding.pulseRingMiddle.clearAnimation()
        } catch (e: Exception) {
            Log.e("PulseAnimation", "Error clearing animations: ${e.message}")
        }
    }

}