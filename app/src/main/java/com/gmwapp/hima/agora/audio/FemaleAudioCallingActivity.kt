package com.gmwapp.hima.agora.audio

import android.Manifest
import android.app.AlertDialog
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
import com.gmwapp.hima.BaseApplication.Companion.getInstance
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.databinding.ActivityFemaleAudioCallingBinding
import com.gmwapp.hima.media.RtcTokenBuilder2
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtm.RtmClient
import retrofit2.Call
import retrofit2.Response

@AndroidEntryPoint
class FemaleAudioCallingActivity : AppCompatActivity() {

    private var channelName: String? = null
    private var maleUserId: String? = null
    private var listenerRegistration: ListenerRegistration? = null

    lateinit var binding: ActivityFemaleAudioCallingBinding
    private val profileViewModel: ProfileViewModel by viewModels()

    private var countDownTimer: CountDownTimer? = null

    val db = FirebaseFirestore.getInstance()


    private val appId = "a41e9245489d44a2ac9af9525f1b508c"
    private val appCertificate = "9565a122acba4144926a12214064fd57"
    private val expirationTimeInSeconds = 3600
    private var token: String? = null
    private val uid = 0
    private var isJoined = false
    private var mRtmClient: RtmClient? = null
    private var agoraEngine: RtcEngine? = null

    lateinit var femaleUserId: String

    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)

    private fun checkSelfPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, REQUESTED_PERMISSIONS[0]
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

        val userData = getInstance()?.getPrefs()?.getUserData()
        femaleUserId = userData?.id.toString()

        channelName = intent.getStringExtra("channelName")
        maleUserId = intent.getStringExtra("maleUserId")


        listenRemainingTime()

        binding.userid.setText("Male user id - $maleUserId")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })

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
    }

    private fun listenRemainingTime() {
        val db = FirebaseFirestore.getInstance()
        val callDocRef = db.collection("femaleUsers").document(femaleUserId)

        listenerRegistration = callDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("Firestore", "Error listening to remainingTime updates", error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val remainingTime = snapshot.getString("remainingTime")
                Log.d("Firestore", "Remaining Time Updated: $remainingTime")

                if (remainingTime != null) {
                    stopCountdown()
                    startCountdown(remainingTime)
                }
            }
        }
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reject Call")
            .setMessage("Do you want to reject the call?")
            .setPositiveButton("Yes") { dialog, _ ->
                rejectCall()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun rejectCall() {
        val userData = getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id

        if (maleUserId != null && userId != null) {
            val db = FirebaseFirestore.getInstance()

            db.collection("maleUsers").document(maleUserId!!)
                .update(mapOf(
                    "isCalling" to false, "channelName" to null,
                    "femaleUserId" to null, "isConnected" to false, "callType" to null,"callId" to null
                ))

            db.collection("femaleUsers").document(userId.toString())
                .update(mapOf(
                    "isCalling" to false, "channelName" to null,
                    "isConnected" to false, "maleUserId" to null, "callType" to null, "remainingTime" to null,"callId" to null
                ))
                .addOnSuccessListener {
                    stopCountdown()

                    finish()
                }
        }
    }

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            showMessage("Remote user joined: $uid")
        }

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
            showMessage("Joined Channel: $channel")
        }

        override fun onUserOffline(uid: Int, reason: Int) {

            stopCountdown()

            val intent = Intent(this@FemaleAudioCallingActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish()
        }
    }

    fun joinChannel(view: View) {
        if (checkSelfPermission()) {
            if (agoraEngine == null) {
                setupAudioSDKEngine()
            }

            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                autoSubscribeAudio = true
                autoSubscribeVideo = false
            }

            Log.d("AgoraTag", "Joined audio-only channel: $channelName with token: $token")
            Log.d("AgoraTag", "Joined audio-only channel: $uid with token: ")

            agoraEngine!!.joinChannel(token, channelName, uid, options)
            Log.d("AgoraTag", "Joined audio-only channel: $channelName with token: $token")

        } else {
            Toast.makeText(applicationContext, "Permissions were not granted", Toast.LENGTH_SHORT).show()
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

            stopCountdown()

            rejectCall()

            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
        listenerRegistration = null

        agoraEngine?.apply {
            stopPreview()
            leaveChannel()
        }

        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()
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
            }

            override fun onFinish() {
                binding.tvRemainingTime?.text = "00:00:00" // When countdown finishes
                rejectCall()

                val intent = Intent(this@FemaleAudioCallingActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                finish()
            }
        }.start()

}

    private fun stopCountdown() {
        countDownTimer?.cancel() // Cancel the countdown timer
        countDownTimer = null
    }

}
