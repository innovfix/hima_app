package com.gmwapp.hima.agora.female

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityFemaleVideoCallingBinding
import com.gmwapp.hima.databinding.ActivityMaleVideoCallingBinding
import com.gmwapp.hima.media.RtcTokenBuilder2
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.ProfileViewModel
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

    private var storedRemainingTime: String? = null
    private var countDownTimer: CountDownTimer? = null

    private val uid = 0
    var call_Id: Int = 0

    private var isJoined = false
  //  private var mRtmClient: RtmClient? = null

    private var agoraEngine: RtcEngine? = null

    private var localSurfaceView: SurfaceView? = null

    private var remoteSurfaceView: SurfaceView? = null
    private var mRtcEngine: RtcEngine? = null



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

        onBackPressedBtn()
        onMenuClicked()

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
            getRemainingTime()
            startTime = dateFormat.format(Date()) // Set call end time in IST

            runOnUiThread { setupRemoteVideo(uid) }
        }

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true

            Log.d("JoinedSuccessFully","$channel")
            showMessage("Joined Channel $channel")

        }

        override fun onUserOffline(uid: Int, reason: Int) {
            showMessage("Remote user offline $uid $reason")

            updateCallEndDetails()
            stopCountdown()
            runOnUiThread { remoteSurfaceView!!.visibility = View.GONE }
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
                    if (storedRemainingTime == null) {
                        storedRemainingTime = newTime // Store first-time value
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
                    if (storedRemainingTime != null && newTime > storedRemainingTime!!) {
                        storedRemainingTime = newTime // Update stored value
                        stopCountdown()
                        startCountdown(newTime)
                    }
                }
            }
        }) }
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

    private fun onMenuClicked(){
        binding.btnMenu.setOnSingleClickListener {
            if (!isClicked) {
                binding.layoutButtons.visibility = View.VISIBLE

                isClicked = true


            } else {
                binding.layoutButtons.visibility = View.INVISIBLE

                isClicked = false
            }
        }}

}