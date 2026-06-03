package com.gmwapp.hima.agora.male

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.animation.AnimationUtils
import android.widget.Toast
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
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import android.telecom.DisconnectCause
import com.gmwapp.hima.databinding.ActivityMaleCallAcceptBinding
import com.gmwapp.hima.retrofit.responses.CallEndReason
import com.gmwapp.hima.retrofit.responses.CallEndedBy
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.viewmodels.CallStatusViewModel
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import com.gmwapp.hima.viewmodels.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlin.system.exitProcess

@AndroidEntryPoint
class MaleCallAcceptActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMaleCallAcceptBinding
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
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
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Log.d(
            "HimaIncomingCall",
            "MaleCallAcceptActivity.onCreate flags=${intent.flags} action=${intent.action} keyguardLocked=${km.isKeyguardLocked}"
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
        binding = ActivityMaleCallAcceptBinding.inflate(layoutInflater)
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

        Log.d("MaleCallAccept_CallerDetails","Image: $callerImage, Name: $callerName")
        call_Id = intent.getIntExtra("CALL_ID", 0)
        Log.d(
            "VideoCallFlow",
            "MaleAccept.onCreate channel=$channelName callId=$call_Id senderId=$receiverId " +
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
                "MaleCallAcceptActivity: stale launch (pendingTag=$pendingTag expected=$expectedTag) -> finish"
            )
            BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
            finish()
            return
        }

        if (BaseApplication.getInstance()?.isRingtonePlaying() == false) {
            BaseApplication.getInstance()?.playIncomingCallSound()
        }

        binding.callerName.setText(callerName.trimEnd { it.isDigit() })
        Glide.with(this)
            .load(callerImage)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivLogo)

        Log.d("MaleCallAccept_CallType","from notification $callType")

        userAvatarViewModel.getUserAvatar(receiverId)
        avatarObservers()
        observeCallRejectCount()

        Log.d("MaleCallAccept_CallID","$call_Id")

        binding.accpet.setOnClickListener {
            if (receiverId != -1 && !channelName.isNullOrEmpty() && !callType.isNullOrEmpty()) {
                // Check if male has enough coins (minimum 10 coins required)
                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                val currentCoins = userData?.coins ?: 0
                
                if (currentCoins < 10) {
                    // Insufficient coins - decline the call and show message
                    Log.d("MaleCallAccept", "Insufficient coins: $currentCoins. Required: 10")
                    
                    // Send rejection notification to female
                    sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "rejected")
                    
                    // Stop ringtone
                    HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                    BaseApplication.getInstance()?.stopRingtone()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()

                    // Show toast message
                    Toast.makeText(
                        this,
                        "You don't have enough coins to attend the call. Recharge now!",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Redirect to MainActivity
                    val intent = Intent(this@MaleCallAcceptActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                    return@setOnClickListener
                }
                
                // Sufficient coins - proceed with call
                Log.d("MaleCallAccept", "Sufficient coins: $currentCoins. Accepting call.")
                Log.d(
                    "VideoCallFlow",
                    "MaleAccept.acceptClick channel=$channelName callId=$call_Id callType=$callType " +
                        "tokenPrefetched=${!prefetchedAgoraToken.isNullOrEmpty()} appIdPrefetched=${!prefetchedAgoraAppId.isNullOrEmpty()}"
                )
                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "accepted")

                if (callType == "audio") {
                    BaseApplication.getInstance()?.stopRingtone()
                    HimaTelecomManager.markActive()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    val intent = Intent(this, MaleAudioCallingActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("CALL_ID", call_Id)
                        prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                        prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                        Log.d("MaleCallAccept_RECEIVER_ID","$receiverId")
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }else{
                    BaseApplication.getInstance()?.stopRingtone()
                    HimaTelecomManager.markActive()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    val intent = Intent(this, MaleVideoCallingActivity::class.java).apply {
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
                // Call reject count API
                userId?.let { maleUserId ->
                    accountViewModel.callRejectCount(maleUserId, receiverId)
                    Log.d("CallStatus", "MaleAccept.reject → rejected/receiver self=$maleUserId peer=$receiverId callId=$call_Id")
                    callStatusViewModel.saveCallStatus(
                        userId = maleUserId,
                        receivedUserId = receiverId,
                        callId = call_Id,
                        endReason = CallEndReason.REJECTED,
                        endedBy = CallEndedBy.RECEIVER,
                        endedByUserId = maleUserId,
                        durationSeconds = 0,
                    )
                }

                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "rejected")

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
                val intent = Intent(this@MaleCallAcceptActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - prevent back press during incoming call
            }
        })
    }

    private fun avatarObservers() {
        userAvatarViewModel.userAvatarLiveData.observe(this) { response ->
            Log.d("MaleCallAccept_Avatar", "Image URL: $response")

            if (response != null && response.success) {
                val imageUrl = response.data?.image
                callerName = response.data?.name.toString()
                Log.d("MaleCallAccept_UserAvatar", "Image URL: $imageUrl")

                binding.callerName.setText(callerName.trimEnd { it.isDigit() })
                Glide.with(this)
                    .load(imageUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.ivLogo)
            }
        }

        userAvatarViewModel.userAvatarErrorLiveData.observe(this) { errorMessage ->
            Log.e("MaleCallAccept_AvatarError", errorMessage)
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
                    Log.d("MaleCallAccept_FCM", "Notification sent successfully!")
                } else {
                    Log.e("MaleCallAccept_FCM", "Failed to send notification")
                }
            }
        }
    }

    fun observeCallRejectCount() {
        accountViewModel.callRejectCountLiveData.observe(this) { response ->
            response?.let {
                if (it.success) {
                    Log.d("MaleCallAccept_RejectCount", "Call reject count recorded: ${it.data?.rejecting_count}")
                } else {
                    Log.e("MaleCallAccept_RejectCount", "Failed to record reject count: ${it.message}")
                }
            }
        }

        accountViewModel.callRejectCountErrorLiveData.observe(this) { error ->
            error?.let {
                Log.e("MaleCallAccept_RejectCount", "Error: $it")
            }
        }
    }

    private fun prefetchAgoraToken(channelForToken: String) {
        Log.d("AgoraTiming", "MaleCallAccept prefetchAgoraToken started at ${System.currentTimeMillis()}")
        Log.d("VideoCallFlow", "MaleAccept.prefetchToken.start channel=$channelForToken callId=$call_Id")
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            Log.d(
                "VideoCallFlow",
                "MaleAccept.prefetchToken.response success=${response?.success} " +
                    "tokenPresent=${!response?.token.isNullOrEmpty()} appIdPresent=${!response?.app_id.isNullOrEmpty()}"
            )
            if (response != null && response.success == true && !response.token.isNullOrEmpty()) {
                prefetchedAgoraToken = response.token
                prefetchedAgoraAppId = response.app_id
                Log.d("AgoraTiming", "MaleCallAccept prefetchAgoraToken received at ${System.currentTimeMillis()}")
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
            Log.e("MaleCallAccept_Anim", "Error starting pulse animations: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop animations
        try {
            binding.pulseRingOuter.clearAnimation()
            binding.pulseRingMiddle.clearAnimation()
        } catch (e: Exception) {
            Log.e("MaleCallAccept_Anim", "Error clearing animations: ${e.message}")
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
        // Wired headset hook / BT AVRCP play-pause = single-press accept on the
        // incoming-call screen, matching native phone / WhatsApp parity.
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            binding.accpet.performClick()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

}

