package com.gmwapp.hima.agora.female

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
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
import com.gmwapp.hima.databinding.ActivityFemaleCallAcceptBinding
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlin.system.exitProcess

@AndroidEntryPoint
class FemaleCallAcceptActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFemaleCallAcceptBinding
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()

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
        binding = ActivityFemaleCallAcceptBinding.inflate(layoutInflater)
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

        Log.d("callerdeatails","$callerImage")
        Log.d("callerdeatails","$callerName")
        call_Id = intent.getIntExtra("CALL_ID", 0)

        // Allow the activity to show when the device is locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        }

        if (callType=="audio"){
            binding.calltype.setText("Incoming Voice Session")

            binding.accpet.setImageResource(R.drawable.audio_accept_gif)

        }else{
            binding.accpet.setImageResource(R.drawable.accept_videocall_gif)

            binding.calltype.setText("Incoming Video Session")

        }


        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked // Check if device is locked

        if (BaseApplication.getInstance()?.isAppInForeground() == true && !isLocked) {
            // Sirf agar app foreground me hai aur lockscreen pe nahi tabhi notification remove karo
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.cancel(1)

            Handler(Looper.getMainLooper()).postDelayed({
                notificationManager?.cancel(1)
            }, 500)

            Handler(Looper.getMainLooper()).postDelayed({
                notificationManager?.cancel(1)
            }, 1000)

            Glide.with(this)
                .asGif()
                .load(R.drawable.endcall_gif) // Replace with your GIF file
                .into(binding.reject)

            if (callType=="audio"){
                binding.calltype.setText("Incoming Voice Session")

                Glide.with(this)
                    .asGif()
                    .load(R.drawable.audio_accept_gif) // Replace with your GIF file
                    .into(binding.accpet)
            }else{
                Glide.with(this)
                    .asGif()
                    .load(R.drawable.accept_videocall_gif) // Replace with your GIF file
                    .into(binding.accpet)
                binding.calltype.setText("Incoming Video Session")

            }
        }


        BaseApplication.getInstance()?.clearIncomingCall()
        if (BaseApplication.getInstance()?.isRingtonePlaying() == false) {
            BaseApplication.getInstance()?.playIncomingCallSound()
        }




        binding.callerName.setText(callerName)
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
                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "accepted")

                if (callType == "audio") {
                    BaseApplication.getInstance()?.stopRingtone()
                    val intent = Intent(this, FemaleAudioCallingActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("CALL_ID", call_Id)
                        Log.d("RECEIVER_ID","$receiverId")

                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }else{
                    BaseApplication.getInstance()?.stopRingtone()
                    val intent = Intent(this, FemaleVideoCallingActivity::class.java).apply {
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
                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "rejected")

                if (isLocked) {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager?.cancel(1)
                    finishAffinity()  // Closes all activities in the task
                    exitProcess(0)    // Force closes the app
                }


                BaseApplication.getInstance()?.stopRingtone()
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
    }

    private fun avatarObservers() {
        userAvatarViewModel.userAvatarLiveData.observe(this) { response ->
            Log.d("userAvatarLiveData", "Image URL: $response")

            if (response != null && response.success) {
                val imageUrl = response.data?.image
                callerName = response.data?.name.toString()
                Log.d("UserAvatar", "Image URL: $imageUrl")

                binding.callerName.setText(callerName)
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


}