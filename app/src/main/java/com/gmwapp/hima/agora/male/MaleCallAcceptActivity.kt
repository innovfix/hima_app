package com.gmwapp.hima.agora.male

import com.gmwapp.hima.utils.showAppToast

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.gmwapp.hima.databinding.ActivityMaleCallAcceptBinding
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

    private var callType: String? = null
    private var receiverId: Int = -1
    private var call_Id: Int = 0
    var callerName = ""
    var callerImage = ""
    private val userAvatarViewModel: UserAvatarViewModel by viewModels()

    private var channelName: String? = null
    var userId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMaleCallAcceptBinding.inflate(layoutInflater)
        setContentView(binding.root)
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

        callerName = intent.getStringExtra("Caller_NAME").toString()
        callerImage = intent.getStringExtra("Caller_Image").toString()

        Log.d("MaleCallAccept_CallerDetails","Image: $callerImage, Name: $callerName")
        call_Id = intent.getIntExtra("CALL_ID", 0)

        // Allow the activity to show when the device is locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
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

        if (BaseApplication.getInstance()?.isAppInForeground() == true && !isLocked) {
            // Only remove notification if app is in foreground and not on lockscreen
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.cancel(1)

            Handler(Looper.getMainLooper()).postDelayed({
                notificationManager?.cancel(1)
            }, 500)

            Handler(Looper.getMainLooper()).postDelayed({
                notificationManager?.cancel(1)
            }, 1000)
        }

        BaseApplication.getInstance()?.clearIncomingCall()
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
                    BaseApplication.getInstance()?.stopRingtone()
                    
                    // Show toast message
                    showAppToast("You don't have enough coins to attend the call. Recharge now!", Toast.LENGTH_LONG)
                    
                    // Redirect to MainActivity
                    val intent = Intent(this@MaleCallAcceptActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                    return@setOnClickListener
                }
                
                // Sufficient coins - proceed with call
                Log.d("MaleCallAccept", "Sufficient coins: $currentCoins. Accepting call.")
                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "accepted")

                if (callType == "audio") {
                    BaseApplication.getInstance()?.stopRingtone()
                    val intent = Intent(this, MaleAudioCallingActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("CALL_ID", call_Id)
                        Log.d("MaleCallAccept_RECEIVER_ID","$receiverId")
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }else{
                    BaseApplication.getInstance()?.stopRingtone()
                    val intent = Intent(this, MaleVideoCallingActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("CALL_ID", call_Id)
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
                }
                
                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "rejected")

                if (isLocked) {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager?.cancel(1)
                    finishAffinity()  // Closes all activities in the task
                    exitProcess(0)    // Force closes the app
                }

                BaseApplication.getInstance()?.stopRingtone()
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

}

