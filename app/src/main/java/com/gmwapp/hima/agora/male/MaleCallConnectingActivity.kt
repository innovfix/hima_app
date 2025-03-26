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
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityMaleCallConnectingBinding
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MaleCallConnectingActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMaleCallConnectingBinding
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    var callType: String? = null
    var receiverId: Int = -1
    var receiverImg : String? = null
    var userId: Int? = null
    private var callId = 0
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var progressStatus = 0
    private var isRunning = true  // Keeps the loop running

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

        FcmUtils.clearCallStatus()
        val callStatusValue = FcmUtils.callStatus.value
        if (callStatusValue?.first == "accepted") {

            Toast.makeText(this, "Try again", Toast.LENGTH_LONG).show()

            val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }


        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        userData?.id?.let { userId = userData?.id}

         callType = intent.getStringExtra(DConstants.CALL_TYPE)
         receiverId = intent.getIntExtra(DConstants.RECEIVER_ID, -1)
         receiverImg = intent.getStringExtra(DConstants.IMAGE)

        getCallId()


        initUI()
        observeCallAcceptance()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("FemaleCallAcceptActivity", "onBackPressed called via Dispatcher")
                if (userId != null && receiverId != -1 && callType != null) {
                    sendCallNotification(userId!!, receiverId,callType!!,"callDeclined")
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                } else {


                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
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
            .load(receiverImg)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivLogo)

        Glide.with(this)
            .asGif()
            .load(R.drawable.double_arrow_gif) // Replace with your GIF file
            .into(binding.ivDoubleArrow)
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

        fun getCallId(){
        receiverId?.let { it1 ->
            userId?.let {
                femaleUsersViewModel.callFemaleUser(
                    it, it1, callType.toString()
                )
            }
            callIdObserver()
        }
    }

    private fun callIdObserver(){
        femaleUsersViewModel.callFemaleUserResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                callId = it.data?.call_id ?: 0

                Log.d("callid","$callId")


                if (userId != null && receiverId != -1 && callType != null) {
                    sendCallNotification(userId!!, receiverId,callType!!,"incoming call $callId")

                    Handler(Looper.getMainLooper()).postDelayed({
                        disconnectCall()
                    }, 20000) // 20 seconds delay

                } else {
                    Log.e("MaleCallConnectingActivity", "Missing required data: userId=$userId, receiverId=$receiverId, callType=$callType")
                }


            } else {
                Toast.makeText(
                    this@MaleCallConnectingActivity, it?.message, Toast.LENGTH_LONG
                ).show()
                finish()
            }
        })
    }

    private fun disconnectCall() {
        var currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
        if (currentActivity is MaleCallConnectingActivity){
        sendCallNotification(userId!!, receiverId,callType!!,"callDeclined")
        FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

        val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }}

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
                    }
                } else if (status == "rejected") {
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }
            }
        })
    }


    fun generateUniqueChannelName(senderId: Int): String {
        val timestamp = System.currentTimeMillis() // Get current timestamp in milliseconds
        return "${senderId}_$timestamp"
    }

        override fun onDestroy() {
            super.onDestroy()
            isRunning = false  // Stop the loop to prevent memory leaks
        }


}