package com.gmwapp.hima

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gmwapp.hima.BaseApplication.Companion.getInstance
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.databinding.ActivityFemaleAudioCallingBinding
import com.gmwapp.hima.media.RtcTokenBuilder2
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtm.RtmClient

class FemaleAudioCallingActivity : AppCompatActivity() {

    private var channelName: String? = null
    private var maleUserId: String? = null
    private var listenerRegistration: ListenerRegistration? = null

    lateinit var binding: ActivityFemaleAudioCallingBinding

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
                    "femaleUserId" to null, "isConnected" to false, "callType" to null
                ))

            db.collection("femaleUsers").document(userId.toString())
                .update(mapOf(
                    "isCalling" to false, "channelName" to null,
                    "isConnected" to false, "maleUserId" to null, "callType" to null
                ))
                .addOnSuccessListener { finish() }
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
            leaveChannel(binding.LeaveButton)
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
        agoraEngine?.apply {
            stopPreview()
            leaveChannel()
        }

        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()
    }
}
