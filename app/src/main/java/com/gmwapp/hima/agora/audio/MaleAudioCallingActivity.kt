package com.gmwapp.hima.agora.audio

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager

import android.os.CountDownTimer
import android.widget.TextView
import android.os.Bundle
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
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BaseApplication.Companion.getInstance
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.activities.RatingActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityMaleAudioCallingBinding
import com.gmwapp.hima.media.RtcTokenBuilder2
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.workers.CallUpdateWorker
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

@AndroidEntryPoint
class MaleAudioCallingActivity : AppCompatActivity() {

    private var channelName: String? = null
    private var femaleUserId: String? = null
    private var listenerRegistration: ListenerRegistration? = null

    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()

    lateinit var binding: ActivityMaleAudioCallingBinding

    private val appId = "a41e9245489d44a2ac9af9525f1b508c"
    private val appCertificate = "9565a122acba4144926a12214064fd57"
    private val expirationTimeInSeconds = 3600
    private var token: String? = null
    private val uid = 0
    private var isJoined = false
    private var agoraEngine: RtcEngine? = null
    private val profileViewModel: ProfileViewModel by viewModels()
    private var countDownTimer: CountDownTimer? = null

    private var storedRemainingTime: String? = null

    val db = FirebaseFirestore.getInstance()
    private var startTime: String = ""
    private var endTime: String = ""

     var callIdInt : Int = 0

    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    private fun checkSelfPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, REQUESTED_PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED
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


        getRemainingTime()

        getCallId()

        channelName = intent.getStringExtra("channelName")
        Log.d("channelname2", "$channelName")
        femaleUserId = intent.getStringExtra("femaleUserId")
        binding.userid.setText("Female user id $femaleUserId")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("MaleAudioCallingActivity", "onBackPressed called via Dispatcher")
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
            startTime = dateFormat.format(Date()) // Set call start time in IST

        }

        onAddcoinClicked()
    }


    private val dateFormat = SimpleDateFormat("HH:mm:ss").apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST time zone
    }

    private  fun getRemainingTime(){
        val userData = getInstance()?.getPrefs()?.getUserData()
        val maleUserId = userData?.id
        maleUserId?.let { profileViewModel.getRemainingTime(it,"audio", object :
            NetworkCallback<GetRemainingTimeResponse>{
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
                    updateRemainigTime(newTime)
                    if (storedRemainingTime == null) {
                        storedRemainingTime = newTime // Store first-time value
                    }

                    startCountdown(newTime)
                }
            }

        }) }
    }

    private fun onAddcoinClicked(){
        binding.btnAddCoins.setOnSingleClickListener {
            var intent = Intent(this@MaleAudioCallingActivity, WalletActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showExitDialog() {
        val builder = AlertDialog.Builder(this@MaleAudioCallingActivity)
        builder.setTitle("Reject Call")
        builder.setMessage("Do you want to reject the call?")
        builder.setPositiveButton("Yes") { dialog, _ ->
            rejectCall()
            dialog.dismiss()
        }
        builder.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
        builder.setCancelable(false)
        builder.show()
    }

    private fun updateRemainigTime(newTime:String){
        db.collection("femaleUsers").document(femaleUserId!!)
            .update("remainingTime", newTime)
    }

    private fun rejectCall() {
        endTime = dateFormat.format(Date()) // Set call end time in IST


        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val data: Data = Data.Builder().putInt(
            DConstants.USER_ID,
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        ).putInt(DConstants.CALL_ID, callIdInt)
            .putString(DConstants.STARTED_TIME, startTime)
            .putBoolean(DConstants.IS_INDIVIDUAL, true)
            .putString(DConstants.ENDED_TIME, endTime).build()

        val oneTimeWorkRequest = OneTimeWorkRequest.Builder(
            CallUpdateWorker::class.java
        ).setInputData(data).setConstraints(constraints).build()
        WorkManager.getInstance(this@MaleAudioCallingActivity)
            .enqueue(oneTimeWorkRequest)



        val userData = getInstance()?.getPrefs()?.getUserData()
        val maleUserId = userData?.id

        if (maleUserId != null && femaleUserId != null) {

            db.collection("maleUsers").document(maleUserId.toString())
                .update(mapOf("isCalling" to false, "channelName" to null, "femaleUserId" to null, "isConnected" to false,"callType" to null, "callId" to null

                ))

            db.collection("femaleUsers").document(femaleUserId!!)
                .update(mapOf("isCalling" to false, "channelName" to null, "isConnected" to false, "maleUserId" to null, "callType" to null,"remainingTime" to null, "callId" to null
                ))

            stopCountdown()
            finish()
        } else {
            Log.e("MaleAudioCallingActivity", "User ID is null, cannot update Firestore")
        }
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
            showMessage("Joined Channel $channel")
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            stopCountdown()
            showMessage("Remote user left")
            val intent = Intent(this@MaleAudioCallingActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish()
        }
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

            val intent = Intent(this@MaleAudioCallingActivity, MainActivity::class.java).apply {
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
            leaveChannel()
        }

        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()

        Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
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

                val intent = Intent(this@MaleAudioCallingActivity, MainActivity::class.java).apply {
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

    private fun newRemainingTime(){
        val userData = getInstance()?.getPrefs()?.getUserData()
        val maleUserId = userData?.id

        maleUserId?.let { profileViewModel.getRemainingTime(it, "audio", object :
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
                        updateRemainigTime(newTime)
                        stopCountdown()
                        startCountdown(newTime)
                    }
                }
            }
        }) }
    }

    fun getCallId(){
        val userData = getInstance()?.getPrefs()?.getUserData()
        val maleUserId = userData?.id

        val db = FirebaseFirestore.getInstance()
        val callDocRef = db.collection("maleUsers").document(maleUserId.toString())

            listenerRegistration = callDocRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error listening to remainingTime updates", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val callId =snapshot.getLong("callId")?.toInt()


                    if (callId != null) {
                        callIdInt = callId
                        Log.d("callId", "callId: $callIdInt")
                    }
                }
            }

    }




    override fun onResume() {
        super.onResume()
        Log.d("resumedtag","resumed")
        newRemainingTime()
    }



}
