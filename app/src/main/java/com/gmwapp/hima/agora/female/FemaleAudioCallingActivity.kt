package com.gmwapp.hima.agora.female

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.databinding.ActivityFemaleAudioCallingBinding
import com.gmwapp.hima.databinding.ActivityMaleAudioCallingBinding
import com.gmwapp.hima.media.RtcTokenBuilder2
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.viewmodels.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rte.Observer
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.workers.CallUpdateWorker

@AndroidEntryPoint
class FemaleAudioCallingActivity : AppCompatActivity() {
    private lateinit var channelName: String
    var receiverId = 0

    //check
    private lateinit var binding: ActivityFemaleAudioCallingBinding
    private val appId = "a41e9245489d44a2ac9af9525f1b508c"
    private val appCertificate = "9565a122acba4144926a12214064fd57"
    private val expirationTimeInSeconds = 3600
    private var token: String? = null
    private val uid = 0
    private var isJoined = false
    private var agoraEngine: RtcEngine? = null
    private val profileViewModel: ProfileViewModel by viewModels()

    private var isMuted = false
    private var isSpeakerOn = true
    private var storedRemainingTime: String? = null
    private var countDownTimer: CountDownTimer? = null

    private var startTime: String = ""
    private var endTime: String = ""

    var call_Id: Int = 0

    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    private fun checkSelfPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            REQUESTED_PERMISSIONS[0]
        ) == PackageManager.PERMISSION_GRANTED
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
        binding = ActivityFemaleAudioCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        receiverId = intent.getIntExtra("RECEIVER_ID", -1)
        call_Id = intent.getIntExtra("CALL_ID", 0)

        Log.d("FemaleAudioCallingCheck", "Channel: $channelName, Receiver: $receiverId, callID : $call_Id")
        Log.d("FemaleAudioCallingCheck", "$call_Id")


        binding.userid.setText("Male userID - $receiverId")
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


        observeRemainingTimeUpdated()
        binding.muteUnmute.setOnClickListener {
            toggleMute()
        }

        binding.speaker.setOnClickListener {
            toggleSpeaker()
        }

        onBackPressedBtn()
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
                setupAudioSDKEngine()
                joinChannel(binding.JoinButton) // Automatically join the channel
            } else {
                ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
            }
        }
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
            showMessage("Joined Channel $channel")
            startTime = dateFormat.format(Date()) // Set call end time in IST

        }

        override fun onUserOffline(uid: Int, reason: Int) {

            updateCallEndDetails()
            stopCountdown()
            showMessage("Remote user left")


            val intent = Intent(this@FemaleAudioCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        override fun onUserJoined(uid: Int, elapsed: Int) {
            showMessage("Remote user joined $uid")

           getRemainingTime()
        }
    }


    private val dateFormat = SimpleDateFormat("HH:mm:ss").apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST time zone
    }

    fun joinChannel(view: View) {
        if (checkSelfPermission()) {
            if (agoraEngine == null) setupAudioSDKEngine()

            val options = ChannelMediaOptions()
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            options.autoSubscribeAudio = true
            options.autoSubscribeVideo = false  // ✅ Ensure video is OFF

            agoraEngine!!.joinChannel(token, channelName, uid, options)
            Log.d("AgoraTag", "Joined channel: $channelName with token: $token")

        } else {
            Toast.makeText(applicationContext, "Permissions were not granted", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun leaveChannel(view: View) {
        if (!isJoined) {
            showMessage("Join a channel first")
        } else {
            agoraEngine?.leaveChannel()
            showMessage("You left the channel")
            isJoined = false

            RtcEngine.destroy()
            agoraEngine = null


            updateCallEndDetails()

            stopCountdown()
            val intent = Intent(this@FemaleAudioCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

    }

    fun updateCallEndDetails(){

        endTime = dateFormat.format(Date()) // Set call end time in IST
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
        WorkManager.getInstance(this@FemaleAudioCallingActivity)
            .enqueue(oneTimeWorkRequest)

    }

    private  fun getRemainingTime(){
        receiverId?.let { profileViewModel.getRemainingTime(it,"audio", object :
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

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
        agoraEngine?.apply {
            leaveChannel()
        }

        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()

        Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
    }

    private fun newRemainingTime(){

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
        val muteIcon = if (isMuted) R.drawable.mute else R.drawable.unmute
        binding.muteUnmute.setImageResource(muteIcon)
    }

    // Function to toggle speaker on/off
    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        agoraEngine?.setEnableSpeakerphone(isSpeakerOn)  // Enable or disable speakerphone
        val speakerIcon = if (isSpeakerOn) R.drawable.speaker_on else R.drawable.speaker_off
        binding.speaker.setImageResource(speakerIcon)
    }
}