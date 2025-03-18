package com.gmwapp.hima.agora

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.lifecycle.Observer

import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AgoraRandomCallActivity : AppCompatActivity() {
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    var callType: String? = null
    var receiverId: Int = -1
    var userId: Int? = null
    private var callId = 0
    private var callAttempts = 0
    private val maxAttempts = 4
    private val triedUserIds = mutableSetOf<Int>()

    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_agora_random_call)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        userData?.id?.let { userId = userData?.id}

        callType = intent.getStringExtra(DConstants.CALL_TYPE)


        getRandomUser()

        observeCallAcceptance()
        observeRandomUser()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("FemaleCallAcceptActivity", "onBackPressed called via Dispatcher")
                if (userId != null && receiverId != -1 && callType != null) {
                    sendCallNotification(userId!!, receiverId,callType!!,"callDeclined")
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


    private fun getRandomUser() {
        if (callAttempts >= maxAttempts) {
            Log.d("RandomCall", "Max attempts reached. Stopping calls.")
            return

        }

        userId?.let { userId ->
            callType?.let { callType ->
                Log.d("getRandomUser", "Attempt: $callAttempts")
                femaleUsersViewModel.getRandomUser(userId, callType)
            }
        }
    }


    private fun observeRandomUser() {
        femaleUsersViewModel.randomUsersResponseLiveData.removeObservers(this)

        femaleUsersViewModel.randomUsersResponseLiveData.observe(this, Observer { response ->
            Log.d("RandomUsersResponse", "$response")

            if (response != null && response.success) {
                response.data?.let { data ->
                    if (data.call_id != null && data.call_user_id != null) {
                        callId = data.call_id
                        receiverId = data.call_user_id
                        Log.d("RandomUsersID", "$callId $receiverId")

                        BaseApplication.getInstance()?.saveSenderId(receiverId)

                        if (triedUserIds.contains(receiverId)) {
                            Log.d("AgoraRandomCall", "Already tried user $receiverId, waiting before retrying...")
                            Handler(mainLooper).postDelayed({ retryCall() }, 3000L) // Delay retry by 3 seconds
                            return@Observer
                        }

                        triedUserIds.add(receiverId)

                        sendCallNotification(userId!!, receiverId!!, callType!!, "incoming call $callId")
                        observeNotificationResponse()
                        waitForCallAcceptance()
                    } else {
                        Log.e("RandomCall", "Invalid call data: call_id or call_user_id is null")
                        retryCall()
                    }
                } ?: run {
                    Log.e("RandomCall", "Response data is null")
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
            checkCallStatus()
        }, waitTime)
    }



    private fun checkCallStatus() {
        // If call is accepted, do nothing
        var currentActivity = BaseApplication.getInstance()?.getCurrentActivity()

        if (currentActivity is AgoraRandomCallActivity) {
            declineCall()
            retryCall()
        }


    }

    private fun declineCall() {
        if (userId != null && receiverId != null && callType != null) {
            sendCallNotification(userId!!, receiverId!!, callType!!, "callDeclined")
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

                if (status == "accepted") {
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
        val timestamp = System.currentTimeMillis() // Get current timestamp in milliseconds
        return "${senderId}_$timestamp"
    }

}