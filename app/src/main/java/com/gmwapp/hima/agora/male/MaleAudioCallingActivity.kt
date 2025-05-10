package com.gmwapp.hima.agora.male

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityMaleAudioCallingBinding
import com.gmwapp.hima.media.RtcTokenBuilder2
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.Constants
import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.res.Resources
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.activities.RatingActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.adapters.GiftAdapter
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.GiftBottomSheetFragment
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.agora.services.CallingService
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.GiftImageViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import com.gmwapp.hima.workers.CallUpdateWorker
import dagger.hilt.android.AndroidEntryPoint
import io.agora.rtc2.video.VideoCanvas
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

@AndroidEntryPoint
class MaleAudioCallingActivity : AppCompatActivity() {

    private lateinit var channelName: String
    var receiverId = 0
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()


    private var femaleUserId: String? = null

    private var isSwitchRequestPending = false


    var isClicked: Boolean = false
    var isAudioCallIdReceived: Boolean = false

    lateinit var binding: ActivityMaleAudioCallingBinding
    private val profileViewModel: ProfileViewModel by viewModels()
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()

    private lateinit var giftAdapter: GiftAdapter
    private val giftImageViewModel: GiftImageViewModel by viewModels()

    private val userAvatarViewModel: UserAvatarViewModel by viewModels()

    private var isSwitchingToAudio = false // ✅ Prevent multiple calls
    private var isSwitchingToVideo = false // ✅ Prevent multiple calls


    private var remoteSurfaceView: SurfaceView? = null

    var switchCallID = 0
    private var isVideoCallGoing: Boolean = false

    private var switchDialog: AlertDialog? = null  // Track current dialog


    private val appId = "a41e9245489d44a2ac9af9525f1b508c"
    private val appCertificate = "9565a122acba4144926a12214064fd57"
    private val expirationTimeInSeconds = 3600
    private var token: String? = null
    private val uid = 0
    private var videoUid = 0
    private var isJoined = false
    private var agoraEngine: RtcEngine? = null

    var receiverName = ""

    private var isMuted = false
    private var isSpeakerOn = true

    var maleUserId = 0
    private var storedRemainingTime: String? = null
    private var storedVideoRemainingTime: String? = null

    private var countDownTimer: CountDownTimer? = null


    private var startTime: String = ""
    private var endTime: String = ""


    var callId: Int = 0


    private var isRemoteUserJoined = false
    private var elapsedTime = 0  // Tracks elapsed seconds
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = object : Runnable {
        override fun run() {
            elapsedTime++
            Log.d("CallTimeoutTracking", "Seconds passed: $elapsedTime")

            if (elapsedTime >=10) { // 20 seconds timeout
                if (isRemoteUserJoined==false){
                    Log.d("isUserJoinedTimer","Leave Button")
                    cancelTimeoutTracking()
                    Toast.makeText(this@MaleAudioCallingActivity,"User did not join", Toast.LENGTH_LONG).show()
                    leaveChannel(binding.LeaveButton)
                }else{
                    cancelTimeoutTracking()
                }
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
        Log.d("isUserJoinedTimer","Cancelled")
    }


    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    private fun checkSelfPermission(): Boolean {
        return !(ContextCompat.checkSelfPermission(
            this,
            REQUESTED_PERMISSIONS[0]
        ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    REQUESTED_PERMISSIONS[1]
                ) != PackageManager.PERMISSION_GRANTED)
    }

    private fun showMessage(message: String?) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAudioSDKEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)

            // Enable only audio module (Disable video)
            agoraEngine!!.enableAudio()
        } catch (e: Exception) {
            showMessage(e.toString())
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMaleAudioCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        Glide.with(this)
            .load(R.drawable.gift_png)
            .into(binding.ivGift)

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        if (userData != null) {
            maleUserId = userData.id
        }


        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        receiverId = intent.getIntExtra("RECEIVER_ID", -1)
        callId = intent.getIntExtra("CALL_ID", 0)
        Log.d(
            "MaleAudioCallingLog",
            "Channel: $channelName, Receiver: $receiverId, callId : $callId"
        )


        val tokenBuilder = RtcTokenBuilder2()
        val timestamp = (System.currentTimeMillis() / 1000 + expirationTimeInSeconds).toInt()

        token = tokenBuilder.buildTokenWithUid(
            appId, appCertificate,
            channelName, uid, RtcTokenBuilder2.Role.ROLE_PUBLISHER, timestamp, timestamp
        )

        if (!checkSelfPermission()) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
        } else {
            setupAudioSDKEngine()
            joinChannel(binding.JoinButton)
        }

        onAddcoinClicked()
        binding.btnMuteUnmute.setOnClickListener {
            toggleMute()
        }

        binding.btnSpeaker.setOnClickListener {
            toggleSpeaker()
        }

        endcallBtn()
        onBackPressedBtn()


        onMenuClicked()
        avatarObservers()
        userAvatarViewModel.getUserAvatar(receiverId)

        userData?.let { setMyAvatar(it.image, it.name) }

        handleCallSwitch()

        observeCallSwitchRequest()

        giftIconClicked()
    }

    private fun giftIconClicked(){
        binding.ivGift.setOnClickListener{
           // showGiftBottomSheet()
            if(isVideoCallGoing==true){
                val bottomSheet = GiftBottomSheetFragment("video",receiverId)
             //   bottomSheet.show(supportFragmentManager, "BottomSheetSelectPayment")
            }else{
                val bottomSheet = GiftBottomSheetFragment("audio",receiverId)
                bottomSheet.show(supportFragmentManager, "BottomSheetSelectPayment")
            }

        }
    }




    private fun setMyAvatar(image: String, name: String) {
        binding.tvMaleName.setText(name)
        Glide.with(this)
            .load(image)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivMaleUser)

    }

    private fun avatarObservers() {
        userAvatarViewModel.userAvatarLiveData.observe(this) { response ->
            Log.d("userAvatarLiveData", "Image URL: $response")

            if (response != null && response.success) {
                val imageUrl = response.data?.image
                receiverName = response.data?.name.toString()

                Log.d("UserAvatar", "Image URL: $imageUrl")

                // Load the avatar image into an ImageView using Glide or Picasso
                // Glide.with(this).load(imageUrl).into(binding.ivMaleUser)
                Glide.with(this)
                    .load(imageUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.ivFemaleUser)

                binding.tvFemaleName.setText(response.data?.name)
            }
        }

        userAvatarViewModel.userAvatarErrorLiveData.observe(this) { errorMessage ->
            Log.e("UserAvatarError", errorMessage)
        }
    }

    private fun onBackPressedBtn() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {

                showExitDialog()
            }
        })
    }

    private fun showExitDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.exit_dialog_layout)

        // Set dialog width to match the screen width
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),  // 90% of screen width
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnNo = dialog.findViewById<Button>(R.id.btnNo)
        val btnYes = dialog.findViewById<Button>(R.id.btnYes)

        btnNo.setOnClickListener { dialog.dismiss() }
        btnYes.setOnClickListener {
            dialog.dismiss()
            leaveChannel(binding.LeaveButton)
        }

        dialog.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupAudioSDKEngine()
                joinChannel(binding.JoinButton) // Automatically join the channel
            } else {
                ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
            }
        }
    }

    fun startCallingService() {
        val intent = Intent(this, CallingService::class.java)
        startService(intent)
    }

    fun stopCallingService() {
        val intent = Intent(this, CallingService::class.java)
        stopService(intent)
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
         //   showMessage("Joined Channel $channel")
            startTimeoutTracking()
        }

        override fun onUserOffline(uid: Int, reason: Int) {

            updateCallEndDetails()
            stopCountdown()
          //  showMessage("Remote user left")

            val intent = Intent(this@MaleAudioCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()

        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
          //  showMessage("Remote user joined $uid")
            isRemoteUserJoined = true
            Log.d("videoUid", "$uid")
            videoUid = uid
            startTime = dateFormat.format(Date()) // Set call end time in IST
            startCallingService()
            getRemainingTime()
        }
    }

    fun joinChannel(view: View) {
        if (checkSelfPermission()) {
            if (agoraEngine == null) setupAudioSDKEngine()

            val options = ChannelMediaOptions()
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            options.autoSubscribeAudio = true
            options.autoSubscribeVideo = true  // ✅ Ensure video is OFF

            agoraEngine!!.joinChannel(token, channelName, uid, options)
            Log.d("AgoraTag", "Joined channel: $channelName with token: $token")

        } else {
            Toast.makeText(applicationContext, "Permissions were not granted", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss").apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST time zone
    }

    fun leaveChannel(view: View) {
        if (!isJoined) {
          //  showMessage("Join a channel first")
            val intent = Intent(this@MaleAudioCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        } else {
            stopCountdown()
            agoraEngine?.leaveChannel()
          //  showMessage("You left the channel")
            isJoined = false

            RtcEngine.destroy()
            agoraEngine = null

            updateCallEndDetails()


            val intent = Intent(this@MaleAudioCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

    }

    fun updateCallEndDetails() {

        if (startTime.isNotEmpty()) {
            endTime = dateFormat.format(Date()) // Set call end time only if startTime is not empty
        }

        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val data: Data = Data.Builder().putInt(
            DConstants.USER_ID,
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        ).putInt(DConstants.CALL_ID, callId)
            .putString(DConstants.STARTED_TIME, startTime)
            .putBoolean(DConstants.IS_INDIVIDUAL, true)
            .putString(DConstants.ENDED_TIME, endTime).build()

        val oneTimeWorkRequest = OneTimeWorkRequest.Builder(
            CallUpdateWorker::class.java
        ).setInputData(data).setConstraints(constraints).build()
        WorkManager.getInstance(this@MaleAudioCallingActivity)
            .enqueue(oneTimeWorkRequest)

        if (switchCallID != 0) {
            callId = switchCallID
            Log.d("callidCheck","$callId")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
        agoraEngine?.apply {
            leaveChannel()
        }
        cancelTimeoutTracking()
        stopCallingService()

        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()

        if (isRemoteUserJoined==true){
            val intent = Intent(this@MaleAudioCallingActivity, RatingActivity::class.java)
            intent.putExtra(DConstants.RECEIVER_NAME, receiverName)
            intent.putExtra(DConstants.RECEIVER_ID, receiverId)
            startActivity(intent)
            Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
        }


    }

    private fun getRemainingTime() {
        maleUserId?.let {
            profileViewModel.getRemainingTime(it, "audio", object :
                NetworkCallback<GetRemainingTimeResponse> {
                override fun onNoNetwork() {
                    TODO("Not yet implemented")
                }

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                    TODO("Not yet implemented")
                }

                override fun onResponse(
                    call: Call<GetRemainingTimeResponse>,
                    response: Response<GetRemainingTimeResponse>
                ) {
                    response.body()?.data?.let { data ->
                        val newTime = data.remaining_time
                        Log.d("newtime","$newTime")

                        if (storedRemainingTime == null) {
                            storedRemainingTime = newTime // Store first-time value
                        }

                        startCountdown(newTime)
                    }
                }

            })
        }
    }

    fun startCountdown(remainingTime: String) {
        // Convert "MM:SS" format to milliseconds
        val timeParts = remainingTime.split(":").map { it.toInt() }
        val minutes = timeParts[0]
        val seconds = timeParts[1]
        val totalMillis = (minutes * 60 + seconds) * 1000L

        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / 3600000
                val minutes = (millisUntilFinished % 3600000) / 60000
                val secs = (millisUntilFinished % 60000) / 1000

                binding.tvRemainingTime?.text =
                    String.format("%02d:%02d:%02d", hours, minutes, secs)
                Log.d("timechanging", "${String.format("%02d:%02d:%02d", hours, minutes, secs)}")

            }

            override fun onFinish() {
                binding.tvRemainingTime?.text = "00:00:00" // When countdown finishes
                leaveChannel(binding.LeaveButton)
            }
        }.start()
    }

    private fun stopCountdown() {
        if (countDownTimer != null) {
            countDownTimer?.cancel() // Cancel the countdown timer
            countDownTimer = null
            Log.d("Countdown", "Countdown timer stopped successfully.")
        } else {
            Log.d("Countdown", "Countdown timer was already null.")
        }
    }


     fun newRemainingTime() {

        if (isVideoCallGoing) {
            maleUserId?.let {
                profileViewModel.getRemainingTime(it, "video", object :
                    NetworkCallback<GetRemainingTimeResponse> {
                    override fun onNoNetwork() {}

                    override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {}

                    override fun onResponse(
                        call: Call<GetRemainingTimeResponse>,
                        response: Response<GetRemainingTimeResponse>
                    ) {
                        response.body()?.data?.let { data ->
                            val newTime = data.remaining_time
                            Log.d("resumedtag","videocalltime - $newTime")
                            Log.d("resumedtag","storedVideoRemainingTime - $storedVideoRemainingTime")

                            if (storedVideoRemainingTime == null) {
                                storedVideoRemainingTime = newTime // Store first-time value
                                sendUpdatedTimeNotification(
                                    maleUserId,
                                    receiverId,
                                    "audio",
                                    "remainingTimeUpdated"
                                )
                                stopCountdown()
                                startCountdown(newTime)

                            }

                            if (storedVideoRemainingTime != null) {
                                storedVideoRemainingTime = newTime // Update stored value
                                sendUpdatedTimeNotification(
                                    maleUserId,
                                    receiverId,
                                    "audio",
                                    "remainingTimeUpdated"
                                )
                                stopCountdown()
                                startCountdown(newTime)
                            }


                        }
                    }
                })
            }

        } else {

            maleUserId?.let {
                profileViewModel.getRemainingTime(it, "audio", object :
                    NetworkCallback<GetRemainingTimeResponse> {
                    override fun onNoNetwork() {}

                    override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {}

                    override fun onResponse(
                        call: Call<GetRemainingTimeResponse>,
                        response: Response<GetRemainingTimeResponse>
                    ) {
                        response.body()?.data?.let { data ->
                            val newTime = data.remaining_time
                            Log.d("resumedtag","audiocalltime - $newTime")

                            if (storedRemainingTime != null) {
                                storedRemainingTime = newTime // Update stored value
                                sendUpdatedTimeNotification(
                                    maleUserId,
                                    receiverId,
                                    "audio",
                                    "remainingTimeUpdated"
                                )
                                stopCountdown()
                                startCountdown(newTime)
                            }
                        }
                    }
                })
            }


        }


    }

    fun sendUpdatedTimeNotification(
        senderId: Int,
        receiverId: Int,
        callType: String,
        message: String
    ) {
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = channelName,
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


    override fun onResume() {
        super.onResume()
        Log.d("resumedtag", "resumed")
        newRemainingTime()
    }

    private fun onAddcoinClicked() {
        binding.timerContainer.setOnSingleClickListener {
            var intent = Intent(this@MaleAudioCallingActivity, WalletActivity::class.java)
            startActivity(intent)
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        agoraEngine?.muteLocalAudioStream(isMuted)  // Mute or unmute audio
        val muteIcon = if (isMuted) R.drawable.mute_img else R.drawable.unmute_img
        binding.btnMuteUnmute.setImageResource(muteIcon)
    }

    // Function to toggle speaker on/off
    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        agoraEngine?.setEnableSpeakerphone(isSpeakerOn)  // Enable or disable speakerphone
        val speakerIcon = if (isSpeakerOn) R.drawable.speakeron_img else R.drawable.speakeroff_img
        binding.btnSpeaker.setImageResource(speakerIcon)
    }

    private fun endcallBtn() {
      binding.btnEndCall.setOnSingleClickListener {
          leaveChannel(binding.LeaveButton)

      }
    }

    private fun handleCallSwitch() {

        binding.btnVideoCall.setOnClickListener {
            val currentDrawable = binding.btnVideoCall.drawable
            val audioDrawable = ContextCompat.getDrawable(this, R.drawable.audiocall_img)
            val videoDrawable = ContextCompat.getDrawable(this, R.drawable.videocall_img)

            if (isSwitchRequestPending == false) {
                if (currentDrawable != null && audioDrawable != null && currentDrawable.constantState == audioDrawable.constantState) {
                    // If button image is AUDIO, switch to VIDEO
                    switchToAudio()
                } else if (currentDrawable != null && videoDrawable != null && currentDrawable.constantState == videoDrawable.constantState) {
                    // If button image is VIDEO, switch to AUDIO
                    switchToVideo()
                } else {
                    Toast.makeText(this, "Error: Unknown state", Toast.LENGTH_SHORT).show()
                }
            }else{
                Toast.makeText(this,"Already Request Sent", Toast.LENGTH_SHORT).show()
            }
        }

    }

    //Switch to video



    private fun switchToVideo() {

         getCallIdforCallSwitch("video")

                val remainingTime =
                    binding.tvRemainingTime?.text.toString() // Get the current countdown time
                val timeParts = remainingTime.split(":").map { it.toInt() }

                if (timeParts.size == 3) {  // Ensure we have HH:MM:SS format
                    val hours = timeParts[0]
                    val minutes = timeParts[1]
                    val seconds = timeParts[2]

                    val totalSeconds = (hours * 3600) + (minutes * 60) + seconds


                    AlertDialog.Builder(this)
                        .setTitle("Want to Switch to Video Call?")
                        .setPositiveButton("Yes") { _, _ ->
                            // Show toast message
                            if (totalSeconds > 360) {
                                if (switchCallID == 0) {
                                    Toast.makeText(this, "Try Again", Toast.LENGTH_SHORT).show()

                                } else {
                                    sendSwitchCallRequestNotification(
                                        maleUserId,
                                        receiverId,
                                        "video",
                                        "switchToVideo $switchCallID"
                                    )
                                    Toast.makeText(
                                        this,
                                        "Video call request sent",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }

                            } else {
                                Toast.makeText(
                                    this,
                                    "You don’t have enough coins",
                                    Toast.LENGTH_SHORT
                                )
                                    .show()

                            }


                        }
                        .setNegativeButton("No") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }



    }


    fun sendSwitchCallRequestNotification(
        senderId: Int,
        receiverId: Int,
        callType: String,
        message: String
    ) {
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = channelName,
            message = message
        )
        observeSwitchCallNotificationSent()
        isSwitchingToAudio = false
        isSwitchingToVideo = false

    }

    fun observeSwitchCallNotificationSent(){
        fcmNotificationViewModel.notificationResponseLiveData.observe(this) { response ->
            response?.let {
                if (it.success) {
                    Log.d("FCMNotification", "Notification sent successfully!")
                    var message = it.data_sent?.message?: ""
                    if (message.startsWith("switchToVideo") || message.startsWith("switchToAudio")) {

                        isSwitchRequestPending= true
                        observeCallSwitchAcceptance()

                    }

                } else {
                    Log.e("FCMNotification", "Failed to send notification")
                }
            }
        }
    }


    fun observeCallSwitchAcceptance() {
        FcmUtils.updatedCallSwitch.observe(this, androidx.lifecycle.Observer { updatedCallSwitch ->
            if (updatedCallSwitch != null) {
                val (switchType, receiverId) = updatedCallSwitch

                Log.d("CallswitchID", "$switchCallID")

                if (switchType == "VideoAccepted" && receiverId == this.receiverId) {
                    isSwitchRequestPending=false

                    val remainingTime =
                        binding.tvRemainingTime?.text.toString() // Get the current countdown time
                    val timeParts = remainingTime.split(":").map { it.toInt() }


                    if (timeParts.size == 3) {  // Ensure we have HH:MM:SS format
                        val hours = timeParts[0]
                        val minutes = timeParts[1]
                        val seconds = timeParts[2]

                        val totalSeconds = (hours * 3600) + (minutes * 60) + seconds

                        if (totalSeconds > 360) {
                            Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
                            stopCountdown()
                            FcmUtils.clearCallSwitch()
                            enableVideoCall()
                        } else {
                            Toast.makeText(
                                this,
                                "You don't have enough coins for video call",
                                Toast.LENGTH_SHORT
                            ).show()
                            FcmUtils.clearCallSwitch()
                            updateCallEndDetails()

                        }
                    }


                }

                if (switchType == "AudioAccepted" && receiverId == this.receiverId) {

                    isSwitchRequestPending=false

                    Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
                            stopCountdown()
                            FcmUtils.clearCallSwitch()
                            enableAudioCall()
                        }


                if (switchType == "SwitchDeclined" && receiverId == this.receiverId) {

                    isSwitchRequestPending=false
                    FcmUtils.clearCallSwitch()
                    Toast.makeText(this, "Request is rejected", Toast.LENGTH_SHORT).show()
                }


            }



        })
    }

    private fun enableVideoCall() {

        Log.d("isSwitchingToVideo","$isSwitchingToVideo")


        if (isSwitchingToVideo) {
            Log.d("enableAudioCall", "Already switching to video, skipping duplicate call")
            return
        }

        isSwitchingToVideo = true // ✅ Set flag to prevent duplicate calls

        FcmUtils.clearCallSwitch()
        updateCallEndDetails()
        isVideoCallGoing = true
        storedVideoRemainingTime = null  // Reset stored time
        storedRemainingTime = null
        Handler(Looper.getMainLooper()).postDelayed({
            stopCountdown()
            getVideoRemainingTime()  // ✅ Get fresh time after resetting
        }, 1000)

        binding.ivFemaleUser.visibility = View.GONE
        binding.ivMaleUser.visibility = View.GONE
        binding.tvFemaleName.visibility = View.GONE
        binding.tvMaleName.visibility = View.GONE


        runOnUiThread {
            // Enable video module
            agoraEngine?.enableVideo()

            // Set up the local video view
            val localContainer = binding.localVideoViewContainer
            val localView = SurfaceView(this)
            localView.setZOrderMediaOverlay(true)
            localContainer.addView(localView)

            // Attach local video feed
            agoraEngine?.setupLocalVideo(VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0))

            // Make video UI visible
            binding.localVideoViewContainer.visibility = View.VISIBLE
            binding.localCardView.visibility = View.VISIBLE
            binding.remoteVideoViewContainer.visibility = View.VISIBLE

            // Notify remote user to switch to video (if required)

            remoteSurfaceView = SurfaceView(this)
            remoteSurfaceView!!.setZOrderMediaOverlay(false)
            binding.remoteVideoViewContainer.addView(remoteSurfaceView)
            agoraEngine!!.setupRemoteVideo(
                VideoCanvas(
                    remoteSurfaceView,
                    VideoCanvas.RENDER_MODE_HIDDEN,
                    videoUid

                )
            )
            remoteSurfaceView!!.visibility = View.VISIBLE

            startTime =
                dateFormat.format(Date()) // Set call end time only if startTime is not empty

            binding.btnVideoCall.setImageResource(R.drawable.audiocall_img)
            binding.btnVideoCall.visibility= View.GONE



        }
    }

    fun getCallIdforCallSwitch(callType: String) {

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        var userId = userData?.id
        receiverId?.let { it1 ->
            userId?.let {
                femaleUsersViewModel.callFemaleUser(
                    it, it1, callType
                )
            }
            callIdObserver()
        }
    }

    private fun callIdObserver() {
        femaleUsersViewModel.callFemaleUserResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                switchCallID = it.data?.call_id ?: 0

                isAudioCallIdReceived = true
                Log.d("switchCallID", "$switchCallID")

            }
        })
    }


    fun observeCallSwitchRequest() {
        FcmUtils.updatedCallSwitch.observe(this, androidx.lifecycle.Observer { updatedCallSwitch ->
            if (updatedCallSwitch != null) {
                val (switchType, newCallId) = updatedCallSwitch

                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                var userid = userData?.id

                if (switchType == "switchToVideo") {
                    if (isVideoCallGoing==false){
                    switchCallID = newCallId
                    switchDialog?.dismiss()

                    switchDialog = AlertDialog.Builder(this)
                        .setTitle("Switch to Video Call ?")
                        .setMessage("$receiverName requested for video call")
                        .setPositiveButton("Confirm") { _, _ ->


                            val remainingTime =
                                binding.tvRemainingTime?.text.toString() // Get the current countdown time
                            val timeParts = remainingTime.split(":").map { it.toInt() }


                            if (timeParts.size == 3) {  // Ensure we have HH:MM:SS format
                                val hours = timeParts[0]
                                val minutes = timeParts[1]
                                val seconds = timeParts[2]

                                val totalSeconds = (hours * 3600) + (minutes * 60) + seconds


                                if (totalSeconds > 360) {
                                    if (userid != null && switchCallID != 0) {
                                        Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()

                                        sendCallAcceptNotification(
                                            userid,
                                            receiverId,
                                            "video",
                                            "VideoAccepted"
                                        )
                                        FcmUtils.clearCallSwitch()
                                        Log.d("NewCallID", "$newCallId")
                                        stopCountdown()
                                        isSwitchingToVideo = false
                                        enableVideoCall()
                                    }
                                } else {
                                    Toast.makeText(
                                        this,
                                        "$receiverName don't have enough coins",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    FcmUtils.clearCallSwitch()

                                }


                            }


                        }
                        .setNegativeButton("Decline") { dialog, _ ->
                            // Dismiss dialog if No is clicked

                            userid?.let {
                                sendCallAcceptNotification(
                                    it,
                                    receiverId,
                                    "video",
                                    "SwitchDeclined"
                                )
                            }
                            dialog.dismiss()
                            FcmUtils.clearCallSwitch()


                        }
                        .setOnDismissListener { switchDialog = null }  // Reset when dismissed

                        .show()

                }}

                if (switchType=="switchToAudio"){
                    if (isVideoCallGoing){
                    switchCallID = newCallId

                    switchDialog?.dismiss()

                    switchDialog = AlertDialog.Builder(this)
                        .setTitle("Switch to audio Call ?")
                        .setMessage("$receiverName requested for audio call")
                        .setPositiveButton("Confirm") { _, _ ->

                            if (userid != null && switchCallID !=0) {
                                Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()

                                sendCallAcceptNotification(userid,receiverId,"audio","AudioAccepted")
                                FcmUtils.clearCallSwitch()
                                Log.d("NewCallID","$newCallId")
                                stopCountdown()
                                isSwitchingToAudio = false

                                enableAudioCall()
                            }

                        }
                        .setNegativeButton("Decline") { dialog, _ ->
                            // Dismiss dialog if No is clicked
                            userid?.let {
                                sendCallAcceptNotification(
                                    it,
                                    receiverId,
                                    "audio",
                                    "SwitchDeclined"
                                )
                            }
                            dialog.dismiss()
                            FcmUtils.clearCallSwitch()

                        }
                        .setOnDismissListener { switchDialog = null }  // Reset when dismissed

                        .show()

                }


                FcmUtils.clearCallSwitch()


            }



            }






        })
    }

    fun sendCallAcceptNotification(
        senderId: Int,
        receiverId: Int,
        callType: String,
        message: String
    ) {
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = channelName,
            message = message
        )
    }


    private fun onMenuClicked() {
        binding.btnMenu.setOnSingleClickListener {
            if (!isClicked) {
                binding.layoutButtons.visibility = View.VISIBLE
                binding.ivMaleUser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd = 14.dpToPx()
                }
                isClicked = true


            } else {
                binding.layoutButtons.visibility = View.INVISIBLE
                binding.ivMaleUser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd = 0
                }
                isClicked = false
            }
        }


        binding.main.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) { // Detect touch down event
                val screenWidth = binding.main.width
                val clickX = event.x  // Get X position relative to `main`

                if (clickX < screenWidth * 0.75) { // Clicked outside the rightmost 20%
                    isClicked = false
                    binding.layoutButtons.visibility = View.INVISIBLE
                    binding.ivMaleUser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        marginEnd = 0
                    }
                }
            }
            false // Return false to allow other touch events
        }

    }

    fun Int.dpToPx() = (this * Resources.getSystem().displayMetrics.density).toInt()


    // Switch to audio

    private fun switchToAudio() {

                isAudioCallIdReceived = false
                getCallIdforCallSwitch("audio")

                AlertDialog.Builder(this)
                    .setTitle("Want to Switch to Audio Call?")
                    .setPositiveButton("Yes") { _, _ ->
                        if (isAudioCallIdReceived == false) {
                            Toast.makeText(this, "Try Again", Toast.LENGTH_SHORT).show()

                        } else {
                            sendSwitchCallRequestNotification(
                                maleUserId,
                                receiverId,
                                "audio",
                                "switchToAudio $switchCallID"
                            )
                            Toast.makeText(this, "Audio call request sent", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()


    }

    private fun enableAudioCall() {

        if (isSwitchingToAudio) {
            Log.d("enableAudioCall", "Already switching to audio, skipping duplicate call")
            return
        }

        isSwitchingToAudio = true // ✅ Set flag to prevent duplicate calls

        Log.d("enableAudioCall","$1")
        stopCountdown()

        FcmUtils.clearCallSwitch()
        isVideoCallGoing = false

        updateCallEndDetails()
        storedVideoRemainingTime = null  // Reset stored time
        storedRemainingTime = null
        Handler(Looper.getMainLooper()).postDelayed({
            stopCountdown()
            getAudioRemainingTime() // ✅ Get fresh time after resetting
        }, 1000)
        binding.ivFemaleUser.visibility = View.VISIBLE
        binding.ivMaleUser.visibility = View.VISIBLE
        binding.tvFemaleName.visibility = View.VISIBLE
        binding.tvMaleName.visibility = View.VISIBLE


        runOnUiThread {
            // Enable video module
            agoraEngine?.disableVideo()

            // Hide local video view
            binding.localVideoViewContainer.removeAllViews()
            binding.localVideoViewContainer.visibility = View.GONE
            binding.localCardView.visibility = View.GONE

            // Hide remote video view
            binding.remoteVideoViewContainer.removeAllViews()
            binding.remoteVideoViewContainer.visibility = View.GONE

            // Reset video surfaces
            remoteSurfaceView = null

            // **Update button to reflect audio call**
            binding.btnVideoCall.setImageResource(R.drawable.videocall_img)

            startTime =
                dateFormat.format(Date()) // Set call end time only if startTime is not empty


        }

    }




    private fun getAudioRemainingTime() {
        maleUserId?.let {
            profileViewModel.getRemainingTime(it, "audio", object :
                NetworkCallback<GetRemainingTimeResponse> {
                override fun onNoNetwork() {
                    TODO("Not yet implemented")
                }

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                    TODO("Not yet implemented")
                }

                override fun onResponse(
                    call: Call<GetRemainingTimeResponse>,
                    response: Response<GetRemainingTimeResponse>
                ) {
                    response.body()?.data?.let { data ->
                        val newTime = data.remaining_time
                        Log.d("newtime","$newTime")

                        stopCountdown()
                        storedRemainingTime = newTime // Store first-time value
                        startCountdown(newTime)
                    }
                }

            })
        }
    }



    private fun getVideoRemainingTime() {
        maleUserId?.let {
            profileViewModel.getRemainingTime(it, "video", object :
                NetworkCallback<GetRemainingTimeResponse> {
                override fun onNoNetwork() {
                    TODO("Not yet implemented")
                }

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                    TODO("Not yet implemented")
                }

                override fun onResponse(
                    call: Call<GetRemainingTimeResponse>,
                    response: Response<GetRemainingTimeResponse>
                ) {
                    response.body()?.data?.let { data ->
                        val newTime = data.remaining_time
                        Log.d("newtime","$newTime")

                        stopCountdown()
                        storedVideoRemainingTime = newTime // Store first-time value
                        startCountdown(newTime)
                    }
                }

            })
        }
    }


    fun animateGift(image: String) {
        val giftImage = binding.ivGiftImage
        val femaleImage = binding.ivFemaleUser

        // Reset visibility and alpha
        giftImage.alpha = 1f
        giftImage.visibility = View.VISIBLE

        BaseApplication.getInstance()?.playSendGiftSound()
        Glide.with(this)
            .load(image)
            .into(giftImage)

        giftImage.post {
            val startX = giftImage.x
            val startY = giftImage.y

            val femaleCenterX = femaleImage.x + femaleImage.width / 2 - giftImage.width / 2
            val femaleCenterY = femaleImage.y + femaleImage.height / 2 - giftImage.height / 2

            // First: animate movement only
            giftImage.animate()
                .x(femaleCenterX)
                .y(femaleCenterY)
                .setDuration(2000)
                .withEndAction {
                    // Then: fade out
                    giftImage.animate()
                        .alpha(0f)
                        .setDuration(1000)
                        .withEndAction {
                            giftImage.visibility = View.INVISIBLE
                            // Reset position if needed
                            giftImage.x = startX
                            giftImage.y = startY
                        }
                        .start()
                }
                .start()
        }
    }

    fun sendGiftSentNotification(giftIcon : String) {

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val senderId = userData?.id
        if (senderId != null) {
            fcmNotificationViewModel.sendNotification(
                senderId = senderId,
                receiverId = receiverId,
                callType = "$giftIcon",
                channelName = channelName,
                message = "giftSent"
            )
        }
    }







}