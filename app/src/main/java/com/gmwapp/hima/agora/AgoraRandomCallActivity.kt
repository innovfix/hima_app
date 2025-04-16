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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityAgoraRandomCallBinding
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AgoraRandomCallActivity : AppCompatActivity() {
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private lateinit var binding : ActivityAgoraRandomCallBinding
    var callType: String? = null
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


    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAgoraRandomCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        userData?.id?.let { userId = userData?.id}

        callType = intent.getStringExtra(DConstants.CALL_TYPE)



        getRandomUser()

        initUI()
        observeCallAcceptance()
        observeRandomUser()

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

    fun initUI(){
        progressBar = findViewById(R.id.progressBar)
        startProgressLoop()
        if (callType=="audio"){
            binding.tvTitle.setText("Audio Call")

        }else{
            binding.tvTitle.setText("Video Call")

        }


        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()


        Glide.with(this)
            .load(userData?.image)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivCallerProfile)


        Glide.with(this)
            .asGif()
            .load(R.drawable.double_arrow_gif) // Replace with your GIF file
            .into(binding.ivDoubleArrow)

        startImageSequence()
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
                femaleUsersViewModel.getRandomUser(userId, callType)
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

                Toast.makeText(this, "${response.message}", Toast.LENGTH_LONG).show()
                navigateToMainActivity()
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