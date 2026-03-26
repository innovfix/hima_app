package com.gmwapp.hima.agora.female

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityFemaleCallConnectingBinding
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class FemaleCallConnectingActivity : AppCompatActivity() {
    private lateinit var binding : ActivityFemaleCallConnectingBinding
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    var callType: String? = null
    var receiverId: Int = -1
    var receiverImg : String? = null
    var receiverName : String? = null
    var userId: Int? = null
    private var callId = 0
    private var channelName: String = ""  // Store channel name to ensure consistency
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var progressStatus = 0
    private var isRunning = true  // Keeps the loop running
    private val designOnly = false  // Toggle: true = UI only (no API/FCM), false = full flow

    private var elapsedTime = 0  // Tracks elapsed seconds
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = object : Runnable {
        override fun run() {
            elapsedTime++
            Log.d("CallTimeoutTracker", "Seconds passed: $elapsedTime")

            if (elapsedTime >= 20) { // 20 seconds timeout
                disconnectCall()
            } else {
                timeoutHandler.postDelayed(this, 1000) // Update every second
            }
        }
    }

    fun startTimeoutTracking() {
        elapsedTime = 0  // Reset counter
        timeoutHandler.post(timeoutRunnable) // Start tracking
    }

    fun cancelTimeoutTracking() {
        timeoutHandler.removeCallbacks(timeoutRunnable) // Stop tracking if call is accepted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFemaleCallConnectingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // In design-only mode, avoid touching global call state
        if (!designOnly) {
            FcmUtils.isUserAvailable = 1
            Log.d("FcmUtils.isUserAvailable","${FcmUtils.isUserAvailable}")
        }

        // Read intent extras early for both modes
        callType = intent.getStringExtra(DConstants.CALL_TYPE)
        receiverId = intent.getIntExtra(DConstants.RECEIVER_ID, -1)
        receiverImg = intent.getStringExtra(DConstants.IMAGE)
        receiverName = intent.getStringExtra(DConstants.RECEIVER_NAME)

        if (designOnly) {
            // UI-only preview: no API/FCM, just show connecting animation briefly
            initUI()
            // Simulate preview for 2 seconds, then return to main
            handler.postDelayed({
                Toast.makeText(this, "Design preview only", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }, 2000)
        } else {
            lifecycleScope.launch {
                FcmUtils.clearCallStatus()

                Log.d("callStatusValueLog", "${FcmUtils.callStatus.value}")
                val callStatusValue = FcmUtils.callStatus.value
                if (callStatusValue?.first == "accepted") {
                    Log.d("NavigationDebug", "Redirecting to MainActivity due to call accepted.")
                    val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }

                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                userData?.id?.let { userId = userData?.id }

                getCallId()
                initUI()
                observeCallAcceptance()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("FemaleCallConnectingActivity", "onBackPressed called via Dispatcher")
                if (!designOnly && userId != null && receiverId != -1 && callType != null) {
                    sendCallNotification(userId!!, receiverId, callType!!, "callDeclined")
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    Log.d("NavigationDebug", "Redirecting to MainActivity due to back pressed when user id is not null")

                    val intent =
                        Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                } else {


                    if (!designOnly) {
                        FcmUtils.clearCallStatus()  // Clear before moving to MainActivity
                    }

                    Log.d("NavigationDebug", "Redirecting to MainActivity due to back pressed when user id is null")

                    val intent =
                        Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()

                    Log.e(
                        "FemaleCallConnectingActivity",
                        "Missing required data: userId=$userId, receiverId=$receiverId, callType=$callType"
                    )
                }

            }
        })

    }

    fun initUI(){
        progressBar = findViewById(R.id.progressBar)
        if (receiverName != null) {
            val displayName = receiverName!!.trimEnd { it.isDigit() }
            binding.tlWaitTitle.setText("Connecting with $displayName")
        }
        startProgressLoop()
        if (callType=="audio"){
            binding.tvTitle.setText("Audio Session")

        }else{
            binding.tvTitle.setText("Video Session")

        }


        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()


        Glide.with(this)
            .load(userData?.image)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivCallerProfile)

        Glide.with(this)
            .load(receiverImg)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivLogo)

        Glide.with(this)
            .load(R.drawable.double_arrow_svg)
            .into(binding.ivDoubleArrow)
            
        startSimpleAnimations()
        
        // Cancel button click
        binding.tvCancel.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }


    private fun startSimpleAnimations() {
        // Animate the connecting dots
        val fadeIn = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 600
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        binding.dot1.startAnimation(fadeIn)
        
        val fadeIn2 = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 600
            startOffset = 200
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.dot2.startAnimation(fadeIn2)
        
        val fadeIn3 = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 600
            startOffset = 400
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.dot3.startAnimation(fadeIn3)

        // Animate the wave ring with subtle pulse
        binding.waveRing1.apply {
            scaleX = 0.9f
            scaleY = 0.9f
            animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .alpha(0.2f)
                .setDuration(1500)
                .withEndAction {
                    if (isRunning) {
                        scaleX = 0.9f
                        scaleY = 0.9f
                        alpha = 0.4f
                        startSimpleAnimations()
                    }
                }
                .start()
        }
        
        // Animate double arrow with gentle bounce
        binding.ivDoubleArrow.apply {
            translationY = 0f
            animate()
                .translationY(-15f)
                .setDuration(800)
                .withEndAction {
                    if (isRunning) {
                        animate()
                            .translationY(0f)
                            .setDuration(800)
                            .withEndAction {
                                if (isRunning) {
                                    postDelayed({ 
                                        if (isRunning) {
                                            startSimpleAnimations()
                                        }
                                    }, 100)
                                }
                            }
                            .start()
                    }
                }
                .start()
        }
    }

    private fun startProgressLoop() {
        startTimeoutTracking()
        handler.post(object : Runnable {
            override fun run() {
                if (progressStatus < 100 && isRunning) {
                    progressStatus += 2
                    progressBar.progress = progressStatus
                    handler.postDelayed(this, 200)
                } else if (progressStatus >= 100 && isRunning) {
                    progressStatus = 0 // Reset to 0 after reaching 100
                    handler.postDelayed(this, 200)
                }
            }
        })
    }

    fun getCallId() {
        if (designOnly) return // UI-only: skip API
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var userId = userData?.id
        receiverId?.let { receiverId ->
            userId?.let { userId ->
                // For female calling male, use callMaleUser API
                Log.d("callMaleUserApi","$userId $receiverId")
                femaleUsersViewModel.callMaleUser(userId, receiverId, callType!!, 0)
            }
            callIdObserver()
        }
    }

    private fun callIdObserver() {
        if (designOnly) return // UI-only: skip observers
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val myAvatar = userData?.image
        val myname = userData?.name
        
        femaleUsersViewModel.callMaleUserResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                callId = it.data?.call_id ?: 0
                channelName = "channel_$callId"  // Set channel name based on call ID
                Log.d("CallID", "CallID: $callId, ChannelName: $channelName")
                if (callId != 0) {
                    sendCallNotification(userId!!, receiverId, callType!!, "incoming call $callId $myAvatar $myname")
                    observeNotificationResponse()
                }
            }
        })
    }

    fun observeNotificationResponse() {
        fcmNotificationViewModel.notificationResponseLiveData.observe(this) { response ->
            response?.let {
                if (it.success) {
                    Log.d("FCMNotification", "Notification sent successfully to male user!")
                } else {
                    Log.e("FCMNotification", "Failed to send notification: ${it.message}")
                }
            }
        }
        
        fcmNotificationViewModel.notificationErrorLiveData.observe(this) { error ->
            error?.let {
                Log.e("FCMNotification", "Notification error: $it")
                Toast.makeText(this, "Failed to connect: $it", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sendCallNotification(senderId: Int, receiverId: Int, callType: String, message: String) {
        if (designOnly) return // UI-only: skip FCM
        Log.d("FemaleCallConnect", "Sending notification with channelName: $channelName")
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = channelName,  // Use stored channel name instead of hardcoded
            message = message
        )
    }

    fun observeCallAcceptance() {
        if (designOnly) return // UI-only: skip observers
        FcmUtils.callStatus.observe(this, androidx.lifecycle.Observer { callStatus ->
            if (callStatus != null) {
                val (status, receiverIdStr) = callStatus
                val receiverIdFromStatus = receiverIdStr.toIntOrNull() ?: -1
                
                if (status == "accepted" && receiverIdFromStatus == this.receiverId) {
                    cancelTimeoutTracking()
                    isRunning = false
                    FcmUtils.clearCallStatus()
                    
                    Log.d("FemaleCallConnect", "Male accepted! Joining channel: $channelName")
                    
                    // Navigate to the appropriate calling activity
                    val intent = if (callType == "audio") {
                        Intent(this@FemaleCallConnectingActivity, FemaleAudioCallingActivity::class.java)
                    } else {
                        Intent(this@FemaleCallConnectingActivity, FemaleVideoCallingActivity::class.java)
                    }
                    
                    intent.putExtra("CHANNEL_NAME", channelName)  // Use stored channel name
                    intent.putExtra("RECEIVER_ID", this.receiverId)
                    intent.putExtra("CALL_ID", callId)
                    
                    startActivity(intent)
                    finish()
                } else if ((status == "declined" || status == "rejected") && receiverIdFromStatus == this.receiverId) {
                    cancelTimeoutTracking()
                    isRunning = false
                    FcmUtils.clearCallStatus()
                    FcmUtils.shouldRefreshCallList = 1
                    Toast.makeText(
                        this@FemaleCallConnectingActivity,
                        "${receiverName?.trimEnd { it.isDigit() }} is busy",
                        Toast.LENGTH_SHORT
                    ).show()
                    val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                } else if (status == "cancelled" && receiverIdFromStatus == this.receiverId) {
                    cancelTimeoutTracking()
                    isRunning = false
                    FcmUtils.clearCallStatus()
                    val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }
            }
        })
    }

    private fun disconnectCall() {
        isRunning = false
        cancelTimeoutTracking()
     //   Toast.makeText(this, "$receiverName is not responding", Toast.LENGTH_SHORT).show()
        if (!designOnly && userId != null && callType != null) {
            sendCallNotification(userId!!, receiverId, callType!!, "callDeclined")
            FcmUtils.clearCallStatus()
        }
        val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cancelTimeoutTracking()
        handler.removeCallbacksAndMessages(null)
    }
}

