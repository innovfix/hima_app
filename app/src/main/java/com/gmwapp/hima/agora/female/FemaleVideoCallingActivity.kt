package com.gmwapp.hima.agora.female

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Outline
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityFemaleVideoCallingBinding
import com.gmwapp.hima.databinding.ActivityMaleVideoCallingBinding
import com.gmwapp.hima.media.RtcTokenBuilder2
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FemaleCallAttendResponse
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import com.gmwapp.hima.workers.CallUpdateWorker
import dagger.hilt.android.AndroidEntryPoint
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

@AndroidEntryPoint
class FemaleVideoCallingActivity : AppCompatActivity() {

    lateinit var binding: ActivityFemaleVideoCallingBinding
    var receiverId = 0


    private var startTime: String = ""
    private var endTime: String = ""
    private var isMuted = false
    private var isSpeakerOn = true
    var isClicked : Boolean = false

    private val appId = "a41e9245489d44a2ac9af9525f1b508c"

    var appCertificate = "9565a122acba4144926a12214064fd57"
    var expirationTimeInSeconds = 3600
    lateinit var channelName : String
    private var token : String? = null

    private val profileViewModel: ProfileViewModel by viewModels()
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()

    private val userAvatarViewModel: UserAvatarViewModel by viewModels()

    private var storedVideoRemainingTime: String? = null
    private var storedRemainingTime: String? = null

    private var countDownTimer: CountDownTimer? = null

    var receiverName = ""

    private var isAudioCallGoing : Boolean = false
    var isAudioCallIdReceived: Boolean = false


    private var isSwitchingToAudio = false // ✅ Prevent multiple calls
    private var isSwitchingToVideo = false // ✅ Prevent multiple calls


    var switchCallID =0

    private val uid = 0
    private var videoUid = 0

    var call_Id: Int = 0

    private var isJoined = false
  //  private var mRtmClient: RtmClient? = null

    private var agoraEngine: RtcEngine? = null

    private var localSurfaceView: SurfaceView? = null

    private var remoteSurfaceView: SurfaceView? = null
    private var mRtcEngine: RtcEngine? = null


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
                    Toast.makeText(this@FemaleVideoCallingActivity,"User did not join", Toast.LENGTH_LONG).show()

                    cancelTimeoutTracking()
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
    private val REQUESTED_PERMISSIONS = arrayOf<String>(
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

    fun showMessage(message: String?) {
        runOnUiThread {
            Toast.makeText(
                applicationContext,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun setupVideoSDKEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)
            // By default, the video module is disabled, call enableVideo to enable it.
            agoraEngine!!.enableVideo()
        } catch (e: Exception) {
            showMessage(e.toString())
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFemaleVideoCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        receiverId = intent.getIntExtra("RECEIVER_ID", -1)
        call_Id = intent.getIntExtra("CALL_ID", 0)

        Log.d("VideoCallingLog", "Channel: $channelName, Receiver: $receiverId, callId : $call_Id")


        val tokenBuilder = RtcTokenBuilder2()
        val timestamp = (System.currentTimeMillis() / 1000 + expirationTimeInSeconds).toInt()

        println("UID token")
        val result = tokenBuilder.buildTokenWithUid(
            appId, appCertificate,
            channelName, uid, RtcTokenBuilder2.Role.ROLE_PUBLISHER, timestamp, timestamp
        )



        token = result


        // Request permissions if not granted
        if (!checkSelfPermission()) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
        } else {

            setupVideoSDKEngine()


            if (agoraEngine != null) {
                Log.d("AgoraCheck", "Agora is initialized.")
            } else {
                Log.e("AgoraCheck", "Agora is NULL! Check initialization.")
            }

            joinChannel(binding.JoinButton) // Automatically join the channel
        }

        observeRemainingTimeUpdated()
        binding.btnMuteUnmute.setOnClickListener {
            toggleMute()
        }

        binding.btnSpeaker.setOnClickListener {
            toggleSpeaker()
        }

        endcallBtn()


        onBackPressedBtn()
        onMenuClicked()

        userAvatarViewModel.getUserAvatar(receiverId)
        avatarObservers()

        handleCallSwitch()
        observeCallSwitchRequest()


        userData?.let { setMyAvatar(it.image, it.name) }

    }


    private fun setMyAvatar(image: String, name: String) {
        binding.tvFemaleName.setText(name)
        Glide.with(this)
            .load(image)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivFemaleUser)

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
                    .into(binding.ivMaleUser)

                binding.tvMaleName.setText(response.data?.name)
            }
        }

        userAvatarViewModel.userAvatarErrorLiveData.observe(this) { errorMessage ->
            Log.e("UserAvatarError", errorMessage)
        }
    }


    private fun onBackPressedBtn() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leaveChannel(binding.LeaveButton)

            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupVideoSDKEngine()
                joinChannel(binding.JoinButton) // Automatically join the channel
            } else {
                ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancelTimeoutTracking()
        // Ensure agoraEngine is not null before using it
        agoraEngine?.let {
            it.stopPreview()
            it.leaveChannel()

            Thread {
                RtcEngine.destroy()
                agoraEngine = null
            }.start()
        }
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            showMessage("Remote user joined $uid")
            isRemoteUserJoined=true
            getRemainingTime()
            startTime = dateFormat.format(Date()) // Set call end time in IST

            videoUid = uid
            runOnUiThread { setupRemoteVideo(uid) }

            femaleUsersViewModel.femaleCallAttend(receiverId,
                call_Id,
                startTime,
                object : NetworkCallback<FemaleCallAttendResponse> {
                    override fun onResponse(
                        call: Call<FemaleCallAttendResponse>,
                        response: Response<FemaleCallAttendResponse>
                    ) {
                    }

                    override fun onFailure(
                        call: Call<FemaleCallAttendResponse>, t: Throwable
                    ) {
                    }

                    override fun onNoNetwork() {
                    }
                })


        }

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true

            startTimeoutTracking()
            Log.d("JoinedSuccessFully","$channel")
            showMessage("Joined Channel $channel")

        }

        override fun onUserOffline(uid: Int, reason: Int) {
            showMessage("Remote user offline $uid $reason")

            updateCallEndDetails()
            stopCountdown()
            runOnUiThread {
                remoteSurfaceView?.let { // ✅ Safe check before accessing
                    it.visibility = View.GONE
                }
            }
            val intent = Intent(this@FemaleVideoCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    private fun setupRemoteVideo(uid: Int) {
        remoteSurfaceView = SurfaceView(baseContext)
        remoteSurfaceView!!.setZOrderMediaOverlay(false)
        binding.remoteVideoViewContainer.addView(remoteSurfaceView)
        agoraEngine!!.setupRemoteVideo(
            VideoCanvas(
                remoteSurfaceView,
                VideoCanvas.RENDER_MODE_HIDDEN,
                uid
            )
        )
        remoteSurfaceView!!.visibility = View.VISIBLE
    }

    private fun setupLocalVideo() {
        localSurfaceView = SurfaceView(baseContext)
        binding.localVideoViewContainer.addView(localSurfaceView)
        localSurfaceView!!.setZOrderMediaOverlay(true)

        agoraEngine!!.setupLocalVideo(
            VideoCanvas(
                localSurfaceView,
                VideoCanvas.RENDER_MODE_HIDDEN,
                0
            )
        )



    }
    private val dateFormat = SimpleDateFormat("HH:mm:ss").apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST time zone
    }

    fun updateCallEndDetails(){


        if (startTime.isNotEmpty()) {
            endTime = dateFormat.format(Date()) // Set call end time only if startTime is not empty
        }
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val data: Data = Data.Builder().putInt(
            DConstants.USER_ID,receiverId

        ).putInt(DConstants.CALL_ID, call_Id)
            .putString(DConstants.STARTED_TIME, startTime)
            .putBoolean(DConstants.IS_INDIVIDUAL, true)
            .putString(DConstants.ENDED_TIME, endTime).build()

        val oneTimeWorkRequest = OneTimeWorkRequest.Builder(
            CallUpdateWorker::class.java
        ).setInputData(data).setConstraints(constraints).build()
        WorkManager.getInstance(this@FemaleVideoCallingActivity)
            .enqueue(oneTimeWorkRequest)


        if (switchCallID!=0){
            call_Id = switchCallID
            Log.d("switchCallIDAfterUpdate","$switchCallID")
            Log.d("switchCallIDAfterUpdate","$call_Id")
        }

    }

    fun joinChannel(view: View) {
        if (checkSelfPermission()) {
            val options = ChannelMediaOptions()

            Log.d("AgoraDebug", "Joining channel: $channelName, Token: $token")

            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            setupLocalVideo()
            localSurfaceView!!.visibility = View.VISIBLE
            agoraEngine!!.startPreview()
            val result = agoraEngine!!.joinChannel(token, channelName, uid, options)
            if (result == 0) {
                Log.d("AgorajoinChannel", "joinChannel: Success $result")
                localSurfaceView!!.visibility = View.VISIBLE
                var startpreview = agoraEngine!!.startPreview()
                Log.d("startpreview", "$startpreview")

            } else {
                Log.e("AgorajoinChannel", "joinChannel failed with error code: $result")
                showMessage("Join channel failed: $result")

            }} else {
            Toast.makeText(applicationContext, "Permissions was not granted", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun leaveChannel(view: View) {
        if (!isJoined) {
            showMessage("Join a channel first")
            val intent = Intent(this@FemaleVideoCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        } else {
            agoraEngine!!.leaveChannel()
            showMessage("You left the channel")
            if (remoteSurfaceView != null) remoteSurfaceView!!.visibility = View.GONE
            if (localSurfaceView != null) localSurfaceView!!.visibility = View.GONE
            isJoined = false
            stopCountdown()

            updateCallEndDetails()
            val intent = Intent(this@FemaleVideoCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    private  fun getRemainingTime(){
        receiverId?.let { profileViewModel.getRemainingTime(it,"video", object :
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
                    if (storedVideoRemainingTime == null) {
                        storedVideoRemainingTime = newTime // Store first-time value
                    }

                    startCountdown(newTime)
                }
            }

        }) }
    }

    fun startCountdown(remainingTime: String) {
        // Convert "MM:SS" format to milliseconds
        val timeParts = remainingTime.split(":").map { it.toInt() }
        val minutes = timeParts[0]
        val seconds = timeParts[1]
        val totalMillis = (minutes * 60 + seconds) * 1000L

        countDownTimer =  object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / 3600000
                val minutes = (millisUntilFinished % 3600000) / 60000
                val secs = (millisUntilFinished % 60000) / 1000

                binding.tvRemainingTime?.text = String.format("%02d:%02d:%02d", hours, minutes, secs)
                Log.d("timechanging","${String.format("%02d:%02d:%02d", hours, minutes, secs)}")

            }

            override fun onFinish() {
                binding.tvRemainingTime?.text = "00:00:00" // When countdown finishes
                leaveChannel(binding.LeaveButton)
            }
        }.start()
    }

    private fun stopCountdown() {
        countDownTimer?.cancel() // Cancel the countdown timer
        countDownTimer = null
    }


    private fun newRemainingTime(){

        if (isAudioCallGoing){

            receiverId?.let { profileViewModel.getRemainingTime(it, "audio", object :
                NetworkCallback<GetRemainingTimeResponse> {
                override fun onNoNetwork() {}

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {}

                override fun onResponse(
                    call: Call<GetRemainingTimeResponse>,
                    response: Response<GetRemainingTimeResponse>
                ) {
                    response.body()?.data?.let { data ->
                        val newTime = data.remaining_time
                        if (storedRemainingTime != null) {
                            storedRemainingTime = newTime // Update stored value
                            stopCountdown()
                            startCountdown(newTime)
                        }
                    }
                }
            }) }
        }else{

        receiverId?.let { profileViewModel.getRemainingTime(it, "video", object :
            NetworkCallback<GetRemainingTimeResponse> {
            override fun onNoNetwork() {}

            override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {}

            override fun onResponse(
                call: Call<GetRemainingTimeResponse>,
                response: Response<GetRemainingTimeResponse>
            ) {
                response.body()?.data?.let { data ->
                    val newTime = data.remaining_time

                    if (storedVideoRemainingTime != null) {
                        storedVideoRemainingTime = newTime // Update stored value
                        stopCountdown()
                        startCountdown(newTime)
                    }
                }
            }
        }) }}
    }




    private fun getAudioRemainingTime() {
        receiverId?.let {
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
        receiverId?.let {
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


    fun observeRemainingTimeUpdated() {
        FcmUtils.updatedTime.observe(this, androidx.lifecycle.Observer { updatedTime ->
            if (updatedTime != null) {

                if (updatedTime=="remainingTimeUpdated"){
                    newRemainingTime()
                    FcmUtils.clearRemainingTime()
                }

            }
        })
    }

    override fun onResume() {
        super.onResume()
        Log.d("resumedtag","resumed")
        newRemainingTime()
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



    private fun handleCallSwitch() {

        binding.btnVideoCall.setOnClickListener {
            val currentDrawable = binding.btnVideoCall.drawable
            val audioDrawable = ContextCompat.getDrawable(this, R.drawable.audiocall_img)
            val videoDrawable = ContextCompat.getDrawable(this, R.drawable.videocall_img)

            if (currentDrawable != null && audioDrawable != null && currentDrawable.constantState == audioDrawable.constantState) {
                // If button image is AUDIO, switch
                switchToAudio()
            } else if (currentDrawable != null && videoDrawable != null && currentDrawable.constantState == videoDrawable.constantState) {
                // If button image is VIDEO, switch
                switchToVideo()
            } else {
                Toast.makeText(this, "Error: Unknown state", Toast.LENGTH_SHORT).show()
            }
        }


    }

    private fun switchToVideo() {

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var userid = userData?.id


        getCallIdforCallSwitch("video")

        val remainingTime = binding.tvRemainingTime?.text.toString().trim()
        if (remainingTime.isEmpty() || !remainingTime.contains(":")) {
            Log.e("switchToVideo", "Invalid remaining time format: $remainingTime")
            Toast.makeText(this, "Error: Invalid remaining time", Toast.LENGTH_SHORT).show()
            return
        }

        val timeParts = remainingTime.split(":").mapNotNull {
            it.toIntOrNull() // ✅ Safely parse integers, avoid crash
        }

        if (timeParts.size == 3) {  // Ensure we have HH:MM:SS format
            val hours = timeParts[0]
            val minutes = timeParts[1]
            val seconds = timeParts[2]

            val totalSeconds = (hours * 3600) + (minutes * 60) + seconds


            AlertDialog.Builder(this)
                .setTitle("Want to Switch to Video Call?")
                .setPositiveButton("Yes") { _, _ ->
                    // Show toast message
                    if (totalSeconds>360){
                        if (switchCallID==0){
                            Toast.makeText(this, "Try Again", Toast.LENGTH_SHORT).show()

                        }else{
                            if (userid != null) {
                                Log.d("SwitchCallIdWhileSending","$switchCallID")
                                sendSwitchCallRequestNotification(userid, receiverId, "video", "switchToVideo $switchCallID")
                            }
                            Toast.makeText(this, "Video call request sent", Toast.LENGTH_SHORT).show()
                        }

                    }else{
                        Toast.makeText(this, "$receiverName don’t have enough coins", Toast.LENGTH_SHORT).show()

                    }


                }
                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()


        }

    }

    fun sendSwitchCallRequestNotification(senderId:Int, receiverId:Int, callType:String, message:String) {
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = channelName,
            message = message
        )
        observeCallSwitchAcceptance()
        isSwitchingToAudio = false
        isSwitchingToVideo = false

        Log.d("SwitchCallIdAfterSending","$switchCallID")

    }

    fun observeCallSwitchAcceptance() {
        FcmUtils.updatedCallSwitch.observe(this, androidx.lifecycle.Observer { updatedCallSwitch ->
            if (updatedCallSwitch != null) {
                val (switchType, receiverId) = updatedCallSwitch

                Log.d("SwitchCallIdAfterAcceptance","$switchCallID")

                if (switchType=="VideoAccepted" && receiverId==this.receiverId){

                    val remainingTime = binding.tvRemainingTime?.text.toString() // Get the current countdown time
                    val timeParts = remainingTime.split(":").map { it.toInt() }


                    if (timeParts.size == 3) {  // Ensure we have HH:MM:SS format
                        val hours = timeParts[0]
                        val minutes = timeParts[1]
                        val seconds = timeParts[2]

                        val totalSeconds = (hours * 3600) + (minutes * 60) + seconds

                        if (totalSeconds>360){
                            Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
                            stopCountdown()
                            FcmUtils.clearCallSwitch()
                            enableVideoCall()
                        }else{
                            Toast.makeText(this, "You don't have enough coins for video call", Toast.LENGTH_SHORT).show()
                            FcmUtils.clearCallSwitch()
                            updateCallEndDetails()

                        }
                    }


                }

                if (switchType == "AudioAccepted" && receiverId == this.receiverId) {

                    Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
                    stopCountdown()
                    FcmUtils.clearCallSwitch()
                    enableAudioCall()
                }

            }
        })
    }


    fun getCallIdforCallSwitch(callType: String){

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        var userId = userData?.id
        receiverId?.let { it1 ->
            userId?.let {
                femaleUsersViewModel.callFemaleUser(
                    it1, it,callType
                )
            }
            callIdObserver()
        }
    }

    private fun callIdObserver() {
        femaleUsersViewModel.callFemaleUserResponseLiveData.observe(this){
            if (it != null && it.success) {
                switchCallID = it.data?.call_id ?: 0

                isAudioCallIdReceived = true

                Log.d("switchCallIdObserver", "$switchCallID")

            }
        }
    }








    fun observeCallSwitchRequest() {
        FcmUtils.updatedCallSwitch.observe(this, androidx.lifecycle.Observer { updatedCallSwitch ->
            if (updatedCallSwitch != null) {
                val (switchType, newCallId) = updatedCallSwitch

                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                var userid = userData?.id

                if (switchType=="switchToVideo"){
                    switchCallID = newCallId

                    AlertDialog.Builder(this)
                        .setTitle("Switch to Video Call ?")
                        .setMessage("$receiverName requested for video call")
                        .setPositiveButton("Confirm") { _, _ ->


                            val remainingTime = binding.tvRemainingTime?.text.toString() // Get the current countdown time
                            val timeParts = remainingTime.split(":").map { it.toInt() }


                            if (timeParts.size == 3) {  // Ensure we have HH:MM:SS format
                                val hours = timeParts[0]
                                val minutes = timeParts[1]
                                val seconds = timeParts[2]

                                val totalSeconds = (hours * 3600) + (minutes * 60) + seconds


                                if (totalSeconds>360){
                                    if (userid != null && switchCallID !=0) {
                                        Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()

                                        sendCallAcceptNotification(userid,receiverId,"video","VideoAccepted")
                                        FcmUtils.clearCallSwitch()
                                        Log.d("NewCallID","$newCallId")
                                        stopCountdown()
                                        isSwitchingToVideo = false

                                        enableVideoCall()
                                    }
                                }else{
                                    Toast.makeText(this, "$receiverName don't have enough coins", Toast.LENGTH_SHORT).show()
                                    FcmUtils.clearCallSwitch()

                                }



                            }



                        }
                        .setNegativeButton("Decline") { dialog, _ ->
                            // Dismiss dialog if No is clicked
                            dialog.dismiss()
                            FcmUtils.clearCallSwitch()

                        }
                        .show()

                }

                if (switchType=="switchToAudio"){
                    switchCallID = newCallId

                    AlertDialog.Builder(this)
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
                            dialog.dismiss()
                            FcmUtils.clearCallSwitch()

                        }
                        .show()

                }


                FcmUtils.clearCallSwitch()


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
        isAudioCallGoing = false
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

            femaleUsersViewModel.femaleCallAttend(receiverId,
                switchCallID,
                startTime,
                object : NetworkCallback<FemaleCallAttendResponse> {
                    override fun onResponse(
                        call: Call<FemaleCallAttendResponse>,
                        response: Response<FemaleCallAttendResponse>
                    ) {
                        Log.d("femaleCallAttend","${response.body()}")
                        Log.d("femaleCallAttend","${switchCallID}")
                    }

                    override fun onFailure(
                        call: Call<FemaleCallAttendResponse>, t: Throwable
                    ) {
                    }

                    override fun onNoNetwork() {
                    }
                })

        }
    }



    fun sendCallAcceptNotification(senderId:Int, receiverId:Int, callType:String, message:String) {
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = channelName,
            message = message
        )
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
        isAudioCallGoing = true

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

            femaleUsersViewModel.femaleCallAttend(receiverId,
                switchCallID,
                startTime,
                object : NetworkCallback<FemaleCallAttendResponse> {
                    override fun onResponse(
                        call: Call<FemaleCallAttendResponse>,
                        response: Response<FemaleCallAttendResponse>
                    ) {
                        Log.d("femaleCallAttend","${response.body()}")
                        Log.d("femaleCallAttend","${switchCallID}")
                    }

                    override fun onFailure(
                        call: Call<FemaleCallAttendResponse>, t: Throwable
                    ) {
                    }

                    override fun onNoNetwork() {
                    }
                })

        }

    }

    private fun switchToAudio() {


        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var userid = userData?.id
        isAudioCallIdReceived = false
        getCallIdforCallSwitch("audio")

        AlertDialog.Builder(this)
            .setTitle("Want to Switch to Audio Call?")
            .setPositiveButton("Yes") { _, _ ->
                if (isAudioCallIdReceived == false) {
                    Toast.makeText(this, "Try Again", Toast.LENGTH_SHORT).show()

                } else {
                    if (userid != null) {
                        sendSwitchCallRequestNotification(
                            userid,
                            receiverId,
                            "audio",
                            "switchToAudio $switchCallID"
                        )
                    }
                    Toast.makeText(this, "Audio call request sent", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()


    }



}