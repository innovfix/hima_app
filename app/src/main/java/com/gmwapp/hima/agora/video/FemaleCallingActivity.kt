package com.gmwapp.hima.agora.video

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.os.CountDownTimer

import android.util.Log
import android.view.SurfaceView
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
import com.gmwapp.hima.BaseApplication.Companion.getInstance
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.databinding.ActivityFemaleCallingBinding
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
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtm.RtmClient
import retrofit2.Call
import retrofit2.Response

@AndroidEntryPoint
class FemaleCallingActivity : AppCompatActivity() {
    private var channelName: String? = null
    private var maleUserId: String? = null


    private val profileViewModel: ProfileViewModel by viewModels()


    private var listenerRegistration: ListenerRegistration? = null
    private var countDownTimer: CountDownTimer? = null



    lateinit var binding: ActivityFemaleCallingBinding

    private val appId = "a41e9245489d44a2ac9af9525f1b508c"

    var appCertificate = "9565a122acba4144926a12214064fd57"
    var expirationTimeInSeconds = 3600
    private var token : String? = null
    private val uid = 0
    private var isJoined = false
    private var mRtmClient: RtmClient? = null

    private var agoraEngine: RtcEngine? = null

    private var localSurfaceView: SurfaceView? = null

    private var remoteSurfaceView: SurfaceView? = null
    private var mRtcEngine: RtcEngine? = null

    lateinit var femaleUserId: String



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
        binding = ActivityFemaleCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val userData = getInstance()?.getPrefs()?.getUserData()
        femaleUserId = userData?.id.toString()

        channelName = intent.getStringExtra("channelName")
        maleUserId = intent.getStringExtra("maleUserId")

        var maleid = intent.getStringExtra("maleUserId")?.toIntOrNull()



        listenRemainingTime()


        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("FemaleCallAcceptActivity", "onBackPressed called via Dispatcher")
                showExitDialog()
            }
        })



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
        val builder = AlertDialog.Builder(this@FemaleCallingActivity)
        builder.setTitle("Reject Call")
        builder.setMessage("Do you want to reject the call?")

        builder.setPositiveButton("Yes") { dialog, _ ->
            rejectCall()
            dialog.dismiss()
        }

        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }

        builder.setCancelable(false)
        builder.show()
    }

    private fun rejectCall() {



        val userData = getInstance()?.getPrefs()?.getUserData()
        val userid = userData?.id

        if (maleUserId != null && userid !=null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("maleUsers").document(maleUserId!!)
                .update(mapOf(
                    "isCalling" to false,
                    "channelName" to null,
                    "femaleUserId" to null,
                    "isConnected" to false

                ))
                .addOnSuccessListener {
                    Log.d("FirestoreUpdate", "isCalling set to false successfully")
                  //  finish() // Close activity after rejection
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreUpdate", "Failed to update Firestore: ", e)
                }


            db.collection("femaleUsers").document(userid.toString())
                .update(
                    mapOf(
                        "isCalling" to false,
                        "channelName" to null,
                        "isConnected" to false,
                        "maleUserId" to null,
                        "remainingTime" to null
                    )
                )
                .addOnSuccessListener {
                    Log.d("FirestoreUpdate", "isCalling set to false and channelName set to null successfully")
                    stopCountdown()

                    finish() // Close activity after rejection
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreUpdate", "Failed to update Firestore: ", e)
                }




        } else {
            Log.e("FemaleCallAcceptActivity", "callerUserId is null, cannot update Firestore")
        }
    }


    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            showMessage("Remote user joined $uid")

            // Set the remote video view
            runOnUiThread { setupRemoteVideo(uid) }
        }

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
            showMessage("Joined Channel $channel")
        }

        override fun onUserOffline(uid: Int, reason: Int) {

            stopCountdown()

            val intent = Intent(this@FemaleCallingActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish()
            showMessage("Remote user offline $uid $reason")
            runOnUiThread { remoteSurfaceView!!.visibility = View.GONE }

            agoraEngine!!.leaveChannel()
            showMessage("You left the channel")
            if (remoteSurfaceView != null) remoteSurfaceView!!.visibility = View.GONE
            if (localSurfaceView != null) localSurfaceView!!.visibility = View.GONE
            isJoined = false



        }
    }

    private fun setupRemoteVideo(uid: Int) {
        remoteSurfaceView = SurfaceView(baseContext)
        remoteSurfaceView!!.setZOrderMediaOverlay(true)
        binding.remoteVideoViewContainer.addView(remoteSurfaceView)
        agoraEngine!!.setupRemoteVideo(
            VideoCanvas(
                remoteSurfaceView,
                VideoCanvas.RENDER_MODE_FIT,
                uid
            )
        )
        remoteSurfaceView!!.visibility = View.VISIBLE
    }

    private fun setupLocalVideo() {
        localSurfaceView = SurfaceView(baseContext)
        binding.localVideoViewContainer.addView(localSurfaceView)
        agoraEngine!!.setupLocalVideo(
            VideoCanvas(
                localSurfaceView,
                VideoCanvas.RENDER_MODE_HIDDEN,
                0
            )
        )
    }

    fun joinChannel(view: View) {
        if (checkSelfPermission()) {
            // Ensure Agora is properly initialized before joining
            if (agoraEngine == null) {
                setupVideoSDKEngine()
            }

            val options = ChannelMediaOptions()
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            setupLocalVideo()
            localSurfaceView!!.visibility = View.VISIBLE
            agoraEngine!!.startPreview()
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

            // Clear video views
            remoteSurfaceView?.visibility = View.GONE
            localSurfaceView?.visibility = View.GONE
            isJoined = false

            // Destroy the Agora engine instance completely
            RtcEngine.destroy()
            agoraEngine = null

            stopCountdown()

            rejectCall()

            val intent = Intent(this@FemaleCallingActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish()

        }
    }


    override fun onDestroy() {
        super.onDestroy()

        // 🛑 Remove Firestore listener to prevent memory leaks
        listenerRegistration?.remove()
        listenerRegistration = null

        // 🛑 Check if agoraEngine is null before using it
        agoraEngine?.apply {
            stopPreview()
            leaveChannel()
        }

        // Destroy Agora engine safely in a background thread
        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()

        Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
        Log.d("MaleCallingActivitydestory", "onDestroy called - Activity is being fully destroyed")

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

                val intent = Intent(this@FemaleCallingActivity, MainActivity::class.java).apply {
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