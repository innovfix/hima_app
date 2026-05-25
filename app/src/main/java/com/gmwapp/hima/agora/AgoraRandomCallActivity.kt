package com.gmwapp.hima.agora

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ProgressBar
import androidx.lifecycle.Observer

import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityAgoraRandomCallBinding
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AgoraRandomCallActivity : AppCompatActivity() {

    @javax.inject.Inject
    lateinit var apiManager: com.gmwapp.hima.retrofit.ApiManager

    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()
    private lateinit var binding : ActivityAgoraRandomCallBinding
    var callType: String? = null
    var randomFilter: String? = null
    var receiverId: Int = -1
    var userId: Int? = null
    private var callId = 0
    private var callAttempts = 0
    private val maxAttempts = 4
    private val triedUserIds = mutableSetOf<Int>()
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var progressStatus = 0
    private var isRunning = true  // Keeps the loop running

    private var isCallAccepted = false
    private var currentChannelName: String? = null
    private var declinedChannelName = "CallDeclined"

    private var isWaitingForAcceptance = false
    private var prefetchedAgoraToken: String? = null
    private var prefetchedAgoraAppId: String? = null


    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // B067: refuse to start a random Hima call while a SIM call is active.
        // Telephony owns STREAM_VOICE_CALL exclusively in MODE_IN_CALL, so
        // Agora's voice frames would be silently dropped — user can see video
        // but cannot hear audio. Surface a message and bail.
        if (com.gmwapp.hima.utils.CallPhoneStateHelper.isCellularCallBusy(this)) {
            android.widget.Toast.makeText(
                this,
                "You're on a phone call. End it to make a Hima call.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        enableEdgeToEdge()
        binding = ActivityAgoraRandomCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        userData?.id?.let { userId = userData?.id}

        callType = intent.getStringExtra(DConstants.CALL_TYPE)
        randomFilter = intent.getStringExtra("RANDOM_FILTER")?.takeIf {
            it == "all" || it == "new" || it == "star"
        } ?: "all"

        // DND gate — if user has DND on, popup blocks the random call until
        // they disable DND (one tap) or cancel back out to home.
        com.gmwapp.hima.utils.DndCallGate.gate(
            activity = this,
            apiManager = apiManager,
            onProceed = { startRandomCallSetup() },
            onCancel = { finish() }
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("FemaleCallAcceptActivity", "onBackPressed called via Dispatcher")
                if (userId != null && receiverId != -1 && callType != null) {
                    sendCallNotification(userId!!, receiverId,callType!!,declinedChannelName,"callDeclined")
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    val intent = Intent(this@AgoraRandomCallActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                } else {


                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    val intent = Intent(this@AgoraRandomCallActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()

                    Log.e("MaleCallConnectingActivity", "Missing required data: userId=$userId, receiverId=$receiverId, callType=$callType")
                }

            }
        })


    }

    /**
     * Original random-call bootstrap, extracted so [com.gmwapp.hima.utils.DndCallGate]
     * can defer it until the user confirms they want to disable DND (or DND was
     * already off). No side effects fire before this runs.
     */
    private fun startRandomCallSetup() {
        FcmUtils.clearUserBusyStatus()
        getRandomUser()
        initUI()
        observeCallAcceptance()
        observeRandomUser()
        observeUserBusyStatus()
    }

    fun initUI(){
        progressBar = findViewById(R.id.progressBar)
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
            .load(R.drawable.double_arrow_svg)
            .into(binding.ivDoubleArrow)

        startImageSequence()
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

    private fun startImageSequence() {
        // List of image resources
        val images = listOf(
            R.drawable.avatar1,
            R.drawable.avatar2,
            R.drawable.avatar3,
            R.drawable.avatar4,
            R.drawable.avatar5,
            R.drawable.avatar6,

            )

        // Handler to post delayed tasks
        val handler = Handler(Looper.getMainLooper())

        // Function to update image sequence
        val updateImageSequence = object : Runnable {
            var currentImageIndex = 0

            override fun run() {
                if (isFinishing || isDestroyed) {
                    return // Exit if the activity is finishing or destroyed
                }

                // Apply circle crop using Glide
                val requestOptions = RequestOptions().circleCrop()

                // Load the image using Glide with circle crop
                Glide.with(this@AgoraRandomCallActivity)
                    .load(images[currentImageIndex])  // Load the current image resource
                    .apply(requestOptions)  // Apply the circle crop transformation
                    .into(binding.ivLogo)  // Set image into the ImageView

                // Move to the next image
                currentImageIndex = (currentImageIndex + 1) % images.size  // Loop back to the first image after the last one

                // Post the next update with a delay of 1 second
                handler.postDelayed(this, 1000)  // 1 second delay
            }
        }

        // Start the image sequence
        handler.post(updateImageSequence)
    }



    private fun getRandomUser() {
        Log.d("callAttemptDebug","$callAttempts")
        if (isWaitingForAcceptance || callAttempts >= maxAttempts) {
            Log.d("isWaitingForAcceptance", "$isWaitingForAcceptance")
            return

        }
        isWaitingForAcceptance = true


        userId?.let { userId ->
            callType?.let { callType ->
                Log.d("getRandomUser", "Attempt: $callAttempts")
                femaleUsersViewModel.getRandomUser(userId, callType, randomFilter)
            }
        }
    }


    private fun observeRandomUser() {
        femaleUsersViewModel.randomUsersResponseLiveData.removeObservers(this)

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var myname = userData?.name
        var myAvatar = userData?.image
        femaleUsersViewModel.randomUsersResponseLiveData.observe(this, Observer { response ->
            Log.d("RandomUsersResponse", "$response")

            if (response != null && response.success) {
                response.data?.let { data ->
                    if (data.call_id != null && data.call_user_id != null) {
                        callId = data.call_id
                        receiverId = data.call_user_id
                        Log.d("RandomUsersID", "$callId $receiverId")

                        BaseApplication.getInstance()?.saveSenderId(receiverId)

                        Log.d("receiverIds","$receiverId")

                        if (triedUserIds.contains(receiverId)) {
                            Log.d("triedUserIds", "Already tried user $receiverId, waiting before retrying...")
                            isWaitingForAcceptance = false
                            Handler(mainLooper).postDelayed({ retryCall() }, 3000L) // Delay retry by 3 seconds
                            return@Observer
                        }

                        triedUserIds.add(receiverId)

                        Log.d("triedUserList","$triedUserIds")

                        currentChannelName = generateUniqueChannelName(userId!!)
                        prefetchedAgoraToken = null
                        prefetchedAgoraAppId = null
                        prefetchAgoraToken(currentChannelName!!)

                        if (currentChannelName!=null){
                            sendCallNotification(userId!!, receiverId!!, callType!!,currentChannelName!!, "incoming call $callId $myAvatar $myname")
                        }
                        observeNotificationResponse()
                        waitForCallAcceptance()
                    } else {
                        Log.e("RandomCall", "Invalid call data: call_id or call_user_id is null")
                        isWaitingForAcceptance = false

                        retryCall()
                    }
                } ?: run {
                    Log.e("RandomCall", "Response data is null")
                    isWaitingForAcceptance = false

                    retryCall()
                }
            }else{

//                Toast.makeText(this, "${response.message}", Toast.LENGTH_LONG).show()
//                navigateToMainActivity()

                response?.message?.let { message ->
                    if (message.startsWith("Insufficient coins")) {
                        val intent = Intent(this@AgoraRandomCallActivity, WalletActivity::class.java)
                        Toast.makeText(this@AgoraRandomCallActivity, message, Toast.LENGTH_LONG).show()
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d("RandomUsersResponse", "$message")

                        Toast.makeText(this@AgoraRandomCallActivity, message, Toast.LENGTH_LONG).show()
                        navigateToMainActivity()
                    }
                }
            }
        })
    }

    private fun waitForCallAcceptance() {
        val waitTime = when (callAttempts) {
            0 -> 7000L  // First attempt: 7 seconds
            1 -> 14000L // Second attempt: 17 seconds
            2 -> 21000L // Third attempt: 27 seconds
            else -> 28000L // Fourth attempt: 37 seconds
        }

        Log.d("RandomCall", "Waiting for $waitTime ms before checking call status")

        android.os.Handler(mainLooper).postDelayed({
            isWaitingForAcceptance = false

            checkCallStatus()
        }, waitTime)
    }



    private fun checkCallStatus() {
        // If call is accepted, do nothing
        isWaitingForAcceptance = false // Allow next user

        var currentActivity = BaseApplication.getInstance()?.getCurrentActivity()

        if (!isCallAccepted && currentActivity is AgoraRandomCallActivity) {
            declineCall()
            retryCall()
        }



    }

    private fun declineCall() {
        if (userId != null && receiverId != null && callType != null) {
            sendCallNotification(userId!!, receiverId!!, callType!!, declinedChannelName,"callDeclined")
        }
    }

    private fun retryCall() {
        callAttempts++
        if (callAttempts < maxAttempts) {
            Log.d("RandomCall", "Retrying... Attempt $callAttempts")
            Handler(mainLooper).postDelayed({ getRandomUser() }, 3000L) // Add 3 seconds delay before retrying
        } else {
            Log.d("RandomCall", "Max retries reached, stopping calls.")

            // Track this failed cycle on the backend (audio/video, language inferred server-side from user)
            val uid = userId
            val ct = callType
            if (uid != null && uid > 0 && !ct.isNullOrEmpty()) {
                apiManager.trackRandomCallFailure(uid, ct)
            }

            val intent = Intent(this@AgoraRandomCallActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }



    fun sendCallNotification(senderId:Int, receiverId:Int, callType:String,myChannel:String, message:String) {
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = myChannel,
            message = message
        )
        observeNotificationResponse()
        Log.d("ChannelNameDebug", "Sending $message to user $receiverId with channel $myChannel")
    }

    fun observeNotificationResponse() {
        fcmNotificationViewModel.notificationResponseLiveData.removeObservers(this) // add this

        fcmNotificationViewModel.notificationResponseLiveData.observe(this) { response ->
            response?.let {
                if (it.success) {
                    Log.d("FCM_Success", "${it.data_sent?.receiverId}")
                } else {
                    Log.e("FCMNotification", "Failed to send notification")
                }
            }
        }
    }

    fun observeCallAcceptance() {

       FcmUtils.callStatus.removeObservers(this) // 👈 REMOVE existing observers first

        FcmUtils.callStatus.observe(this, Observer { callData ->
            if (callData != null) {  // Check if it's not null before destructuring
                val (status, channelName) = callData
                Log.d("CallStatusDebug", "Received status=$status, channel=$channelName, expected=$currentChannelName, isAccepted=$isCallAccepted")

                if (status == "accepted" && !isCallAccepted && channelName == currentChannelName) {
                    isCallAccepted = true
                    isWaitingForAcceptance = false // Clear waiting
                    FcmUtils.clearCallStatus()  // Clear before moving to AudioCallingActivity

                    var currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
                    if (currentActivity !is MainActivity){
                        var previousSenderId = BaseApplication.getInstance()?.getSenderId()
                        if (previousSenderId==receiverId){

                        Log.d("callTypeData","$callType")
                        if (callType=="audio") {
                            val intent = Intent(this, MaleAudioCallingActivity::class.java).apply {
                                putExtra("CHANNEL_NAME", channelName)
                                putExtra("RECEIVER_ID", receiverId)
                                putExtra("CALL_ID", callId)
                                prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                                prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                                Log.d("RECEIVER_ID","$receiverId")
                            }
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(intent)
                            finish()
                        }else{
                            FcmUtils.clearCallStatus()
                            val intent = Intent(this, MaleVideoCallingActivity::class.java).apply {
                                putExtra("CHANNEL_NAME", channelName)
                                putExtra("RECEIVER_ID", receiverId)
                                putExtra("CALL_ID", callId)
                                prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                                prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                            }
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(intent)
                            finish()
                        }
                    }}
                } else if (status == "rejected") {
                    FcmUtils.clearCallStatus()
                    isWaitingForAcceptance = false

                    retryCall()
                }
            }
        })
    }

    private fun observeUserBusyStatus() {
        FcmUtils.userBusyStatus.observe(this, Observer { busyData ->
            if (busyData != null) {
                Log.d("observeUserBusyStatus", "Random user busy, retrying with another user...")
                
                // Clear the status
                FcmUtils.clearUserBusyStatus()
                
                // Clear waiting state
                isWaitingForAcceptance = false
                
                // Treat it like a rejection - retry with another user
                retryCall()
            }
        })
    }

    private fun prefetchAgoraToken(channelForToken: String) {
        Log.d("AgoraTiming", "RandomCall prefetchAgoraToken started at ${System.currentTimeMillis()}")
        agoraViewModel.agoraTokenLiveData.removeObservers(this)
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            if (response != null && response.success == true && !response.token.isNullOrEmpty()) {
                prefetchedAgoraToken = response.token
                prefetchedAgoraAppId = response.app_id
                Log.d("AgoraTiming", "RandomCall prefetchAgoraToken received at ${System.currentTimeMillis()}")
            }
        }
        agoraViewModel.getAgoraToken(channelForToken, 0, "publisher", 3600)
    }

    private fun navigateToMainActivity() {
        FcmUtils.clearCallStatus()  // Clear any pending call status

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }



    fun generateUniqueChannelName(senderId: Int): String {
        return "${senderId}_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }


}