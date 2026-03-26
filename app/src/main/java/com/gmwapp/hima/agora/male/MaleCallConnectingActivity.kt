package com.gmwapp.hima.agora.male

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityMaleCallConnectingBinding
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.job
import kotlinx.coroutines.launch


@AndroidEntryPoint
class MaleCallConnectingActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMaleCallConnectingBinding
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    var callType: String? = null
    var receiverId: Int = -1
    var receiverImg : String? = null
    var receiverName : String? = null
    var userId: Int? = null
    private var callId = 0
    private var fromChat: Boolean = false
    private var chatPeerUserId: Int = -1
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var progressStatus = 0
    private var isRunning = true  // Keeps the loop running

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
        binding =ActivityMaleCallConnectingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        FcmUtils.isUserAvailable=1

        Log.d("FcmUtils.isUserAvailable","${FcmUtils.isUserAvailable}")

        // Read intent extras immediately (outside coroutine) so they're available for onBackPressed
        callType = intent.getStringExtra(DConstants.CALL_TYPE)
        receiverId = intent.getIntExtra(DConstants.RECEIVER_ID, -1)
        receiverImg = intent.getStringExtra(DConstants.IMAGE)
        receiverName = intent.getStringExtra(DConstants.RECEIVER_NAME)
        fromChat = intent.getBooleanExtra("FROM_CHAT", false)
        chatPeerUserId = intent.getIntExtra("CHAT_PEER_USER_ID", -1)
        
        Log.d("MaleCallConnecting", "fromChat=$fromChat, chatPeerUserId=$chatPeerUserId, receiverId=$receiverId")

        lifecycleScope.launch {
             FcmUtils.clearCallStatus()
             FcmUtils.clearUserBusyStatus()

             Log.d("callStatusValueLog", "${FcmUtils.callStatus.value}")
             val callStatusValue = FcmUtils.callStatus.value
             if (callStatusValue?.first == "accepted") {

               //  Toast.makeText(this, "Try again", Toast.LENGTH_LONG).show()
                 Log.d("NavigationDebug", "Redirecting to MainActivity due to call accepted.")

                 val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
                 intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                 startActivity(intent)
                 finish()
             }


             val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

             userData?.id?.let { userId = userData?.id }

             getCallId()


             initUI()
             observeCallAcceptance()
             observeUserBusyStatus()

         }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("MaleCallConnecting", "onBackPressed called - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                
                if (userId != null && receiverId != -1 && callType != null) {
                    sendCallNotification(userId!!, receiverId, callType!!, "callDeclined")
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    if (fromChat && chatPeerUserId != -1) {
                        // Return to ChatActivityInHouse if call was initiated from chat
                        Log.d("NavigationDebug", "Returning to ChatActivityInHouse - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                        val intent = Intent(this@MaleCallConnectingActivity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                            putExtra("USER_ID", chatPeerUserId)
                            putExtra("USER_NAME", receiverName ?: "")
                            putExtra("USER_IMAGE", receiverImg ?: "")
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d("NavigationDebug", "Going to MainActivity - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                        val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    if (fromChat && chatPeerUserId != -1) {
                        // Return to ChatActivityInHouse if call was initiated from chat
                        Log.d("NavigationDebug", "Returning to ChatActivityInHouse (no userId) - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                        val intent = Intent(this@MaleCallConnectingActivity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                            putExtra("USER_ID", chatPeerUserId)
                            putExtra("USER_NAME", receiverName ?: "")
                            putExtra("USER_IMAGE", receiverImg ?: "")
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d("NavigationDebug", "Going to MainActivity (no userId) - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                        val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                        finish()
                    }

                    Log.e(
                        "MaleCallConnectingActivity",
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
        // Simple fade in for title
        binding.tvTitle.alpha = 0f
        binding.tvTitle.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        
        // Subtle connecting dots animation
        startConnectingDotsAnimation()
    }
    
    private fun startConnectingDotsAnimation() {
        try {
            val dot1 = findViewById<android.view.View>(R.id.dot1)
            val dot2 = findViewById<android.view.View>(R.id.dot2)
            val dot3 = findViewById<android.view.View>(R.id.dot3)
            
            val animateDots = object : Runnable {
                var step = 0
                override fun run() {
                    when (step % 3) {
                        0 -> {
                            dot1?.alpha = 1.0f
                            dot2?.alpha = 0.5f
                            dot3?.alpha = 0.3f
                        }
                        1 -> {
                            dot1?.alpha = 0.3f
                            dot2?.alpha = 1.0f
                            dot3?.alpha = 0.5f
                        }
                        2 -> {
                            dot1?.alpha = 0.5f
                            dot2?.alpha = 0.3f
                            dot3?.alpha = 1.0f
                        }
                    }
                    step++
                    if (isRunning) {
                        handler.postDelayed(this, 500)
                    }
                }
            }
            handler.post(animateDots)
        } catch (e: Exception) {
            Log.e("Animation", "Connecting dots not found: ${e.message}")
        }
    }


    private fun startProgressLoop() {
        Thread {
            while (isRunning) {
                progressStatus = 0  // Reset progress

                while (progressStatus < 100 && isRunning) {
                    progressStatus += 1  // Increase progress
                    handler.post { progressBar.progress = progressStatus }

                    Log.d("progressStatus","$progressStatus")
                    try {
                        Thread.sleep(200)  // Smooth animation delay
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }

                // Reset to 0 and repeat the loop
                if (isRunning) {
                    handler.post { progressBar.progress = 0 }
                }
            }
        }.start()

    }

        fun getCallId(){
        receiverId?.let { it1 ->
            userId?.let {
                femaleUsersViewModel.callFemaleUser(
                    it, it1, callType.toString(),0
                )
            }
            callIdObserver()
        }
    }

    private fun callIdObserver(){
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var myname = userData?.name
        var myAvatar = userData?.image
        femaleUsersViewModel.callFemaleUserResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                callId = it.data?.call_id ?: 0

                Log.d("callid","$callId")
                val audioStatus = it.data?.audio_status
                val videoStatus = it.data?.video_status

                Log.d("callid", "$callId")

                // ✅ Check callType against corresponding status
                val shouldSendNotification = when (callType) {
                    "audio" -> audioStatus != 0
                    "video" -> videoStatus != 0
                    else -> false
                }

                if (!shouldSendNotification) {
                    Log.d("Notification", "Not sending notification because callType=$callType has status=0")
                    Toast.makeText(
                        this@MaleCallConnectingActivity, "User is offline", Toast.LENGTH_LONG
                    ).show()
                    navigateToMain()
                    return@Observer
                }

                if (userId != null && receiverId != -1 && callType != null) {
                    sendCallNotification(userId!!, receiverId,callType!!,"incoming call $callId $myAvatar $myname")
                    startTimeoutTracking()



                } else {
                    Log.e("MaleCallConnectingActivity", "Missing required data: userId=$userId, receiverId=$receiverId, callType=$callType")
                }


            } else {

                it?.message?.let { message ->
                    if (message.startsWith("Insufficient coins")) {
                        val intent = Intent(this@MaleCallConnectingActivity, WalletActivity::class.java)
                        Toast.makeText(this@MaleCallConnectingActivity, message, Toast.LENGTH_LONG).show()
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@MaleCallConnectingActivity, message, Toast.LENGTH_LONG).show()
                        // Return to ChatActivityInHouse if call was initiated from chat
                        if (fromChat && chatPeerUserId != -1) {
                            val intent = Intent(this@MaleCallConnectingActivity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                                putExtra("USER_ID", chatPeerUserId)
                                putExtra("USER_NAME", receiverName ?: "")
                                putExtra("USER_IMAGE", receiverImg ?: "")
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent)
                        }
                        finish()
                    }
                }

            }
        })
    }

    private fun disconnectCall() {
        var currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
        if (currentActivity is MaleCallConnectingActivity){
            if (userId != null && receiverId != -1 && callType != null) {
                sendCallNotification(userId!!, receiverId, callType!!, "callDeclined")
            }
            cancelTimeoutTracking()
            FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

            Log.d("MaleCallConnecting", "disconnectCall - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
            
            if (fromChat && chatPeerUserId != -1) {
                // Return to ChatActivityInHouse if call was initiated from chat
                Log.d("NavigationDebug", "Returning to ChatActivityInHouse due to timeout from chat")
                val intent = Intent(this@MaleCallConnectingActivity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                    putExtra("USER_ID", chatPeerUserId)
                    putExtra("USER_NAME", receiverName ?: "")
                    putExtra("USER_IMAGE", receiverImg ?: "")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            } else {
                Log.d("NavigationDebug", "Redirecting to MainActivity due to timeout.")
                val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }
        }
    }

    fun sendCallNotification(senderId:Int, receiverId:Int, callType:String, message:String) {
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = generateUniqueChannelName(senderId),
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

    fun observeCallAcceptance() {
        FcmUtils.callStatus.observe(this, Observer { callData ->
            if (callData != null) {  // Check if it's not null before destructuring
                val (status, channelName) = callData
                Log.d("callStatusData","$status")

                if (status == "accepted") {
                    FcmUtils.clearCallStatus()  // Clear before moving to AudioCallingActivity

                    var currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
                    if (currentActivity !is MainActivity){

                    Log.d("callTypeData","$callType")
                    if (callType=="audio") {
                        cancelTimeoutTracking()

                        val intent = Intent(this, MaleAudioCallingActivity::class.java).apply {
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("RECEIVER_ID", receiverId)
                            putExtra("CALL_ID", callId)
                            Log.d("RECEIVER_ID","$receiverId")
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                        finish()
                    }else{
                        cancelTimeoutTracking()

                        FcmUtils.clearCallStatus()
                        val intent = Intent(this, MaleVideoCallingActivity::class.java).apply {
                                putExtra("CHANNEL_NAME", channelName)
                                putExtra("RECEIVER_ID", receiverId)
                                 putExtra("CALL_ID", callId)

                        }
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(intent)
                            finish()
                    }
                    }
                } else if (status == "rejected") {
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    cancelTimeoutTracking()
                    
                    Log.d("MaleCallConnecting", "Call rejected - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                    
                    if (fromChat && chatPeerUserId != -1) {
                        // Return to ChatActivityInHouse if call was initiated from chat
                        Log.d("NavigationDebug", "Returning to ChatActivityInHouse due to call rejected from chat")
                        val intent = Intent(this, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                            putExtra("USER_ID", chatPeerUserId)
                            putExtra("USER_NAME", receiverName ?: "")
                            putExtra("USER_IMAGE", receiverImg ?: "")
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d("NavigationDebug", "Redirecting to MainActivity due to call rejected")
                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                        Log.d("wentToMain","$status")
                        finish()
                    }
                }
            }
        })
    }


    fun generateUniqueChannelName(senderId: Int): String {
        val timestamp = System.currentTimeMillis() // Get current timestamp in milliseconds
        Log.d("ChannelnameCheck","${senderId}_$timestamp")
        return "${senderId}_$timestamp"
    }

    private fun observeUserBusyStatus() {
        FcmUtils.userBusyStatus.observe(this, Observer { busyData ->
            if (busyData != null) {
                val (callTypeFromBusy, userName) = busyData
                
                Log.d("MaleCallConnecting", "User busy: $userName, callType: $callTypeFromBusy")
                
                // Stop all ongoing operations
                isRunning = false
                cancelTimeoutTracking()
                
                // Update UI to show busy message
                binding.tlWaitTitle.text = "${receiverName?.trimEnd { it.isDigit() }} just got busy!"
                binding.tvProgressText.text = "Finding another match..."
                
                // Clear the status
                FcmUtils.clearUserBusyStatus()
                
                // Wait 1.5 seconds then redirect to random call
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        val randomCallIntent = Intent(
                            this@MaleCallConnectingActivity, 
                            com.gmwapp.hima.agora.AgoraRandomCallActivity::class.java
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(DConstants.CALL_TYPE, callType) // Use same call type
                        }
                        startActivity(randomCallIntent)
                        finish()
                    }
                }, 1500) // 1.5 seconds delay
            }
        })
    }

        override fun onDestroy() {
            super.onDestroy()
            isRunning = false
            cancelTimeoutTracking()

        }

    fun navigateToMain(){
        isRunning = false
        FcmUtils.clearCallStatus()  // Clear before moving to MainActivity
        FcmUtils.isUserAvailable = 0
        cancelTimeoutTracking()
        Log.d("GoinginMain", "${FcmUtils.isUserAvailable}")
        Log.d("MaleCallConnecting", "navigateToMain - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")

        if (fromChat && chatPeerUserId != -1) {
            // Return to ChatActivityInHouse if call was initiated from chat
            Log.d("NavigationDebug", "Returning to ChatActivityInHouse from navigateToMain")
            val intent = Intent(this, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                putExtra("USER_ID", chatPeerUserId)
                putExtra("USER_NAME", receiverName ?: "")
                putExtra("USER_IMAGE", receiverImg ?: "")
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
        } else {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }


}