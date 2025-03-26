package com.gmwapp.hima.activities

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.callbacks.OnButtonClickListener
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityRandomUserBinding
import com.gmwapp.hima.services.CallingService
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.utils.GiftManager
import com.gmwapp.hima.utils.GiftViewModelProvider
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.GiftImageViewModel
import com.gmwapp.hima.viewmodels.GiftViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.widgets.CustomCallView
import com.gmwapp.hima.workers.CallUpdateWorker
//import com.permissionx.guolindev.PermissionX
//import com.permissionx.guolindev.callback.ExplainReasonCallback
//import com.permissionx.guolindev.callback.RequestCallback
//import com.zegocloud.uikit.ZegoUIKit
//import com.zegocloud.uikit.plugin.common.PluginCallbackListener
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallConfig
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
//import com.zegocloud.uikit.prebuilt.call.config.ZegoCallDurationConfig
//import com.zegocloud.uikit.prebuilt.call.core.CallInvitationServiceImpl
//import com.zegocloud.uikit.prebuilt.call.invite.internal.OutgoingCallButtonListener
//import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoCallType
//import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoCallUser
//import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoInvitationCallListener
//import com.zegocloud.uikit.service.defines.ZegoUIKitUser
import dagger.hilt.android.AndroidEntryPoint
//import im.zego.zegoexpress.ZegoExpressEngine
//import im.zego.zegoexpress.callback.IZegoEventHandler
//import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
//import im.zego.zegoexpress.entity.ZegoUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone


@AndroidEntryPoint
class RandomUserActivity : BaseActivity(), OnButtonClickListener {
    //    private var timer: CountDownTimer? = null;
//    private var isReceiverDetailsAvailable: Boolean = false
//    private val CALL_PERMISSIONS_REQUEST_CODE = 1
 //  lateinit var binding: ActivityRandomUserBinding
//    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
//    private val GiftImageViewModel: GiftImageViewModel by viewModels()
//    private var usersCount: Int = 0
//    private val profileViewModel: ProfileViewModel by viewModels()
//
//    private var userId: String = ""
//    private var callUserId: String = ""
//    private var callUserName: String = ""
//    private var startTime: String = ""
//    private var endTime: String = ""
//    var targetUserId: String? = null
//    private lateinit var sharedPreferences: SharedPreferences
//    private var isPermissionDenied: Boolean = false
//
//
//
//    private val dateFormat = SimpleDateFormat("HH:mm:ss").apply {
//        timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST time zone
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityRandomUserBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//        enableEdgeToEdge()
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        // Initialize SharedPreferences in onCreate
//        sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
//        isPermissionDenied = sharedPreferences.getBoolean("isOneSignalTagSet", false)
//
//
//
//
//
//        initUI()
//        askPermissions()
//        onBackPressedDispatcher.addCallback(this) {
//            stopCall()
//            finish()
//        }
//    }
//
//    private fun checkOverlayPermission() {
//
//        if (isPermissionDenied==true){
//            initializeCall(false)
//        }else{
//
//            try {
//                PermissionX.init(this).permissions(Manifest.permission.SYSTEM_ALERT_WINDOW)
//                    .onExplainRequestReason(ExplainReasonCallback { scope, deniedList ->
//                        try {
//                            val message =
//                                "We need your consent for the following permissions in order to use the offline call function properly"
//                            scope.showRequestReasonDialog(deniedList, message, "Allow", "Deny")
//                        } catch (e: Exception) {
//                        }
//                    }).request(RequestCallback { allGranted, grantedList, deniedList ->
//                        try {
//                            if (allGranted) {
//                                initializeCall(false)
//                            } else {
//                                sharedPreferences.edit().putBoolean("isOneSignalTagSet", true).apply()
//                                initializeCall(false)
//                            }
//                        } catch (e: Exception) {
//                        }
//                    })
//            } catch (e: Exception) {
//            }
//        }
//
//
//
//
//    }
//
//    fun askPermissions() {
//        val permissionNeeded =
//            arrayOf("android.permission.RECORD_AUDIO", "android.permission.CAMERA")
//
//        if (ContextCompat.checkSelfPermission(
//                this, "android.permission.CAMERA"
//            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
//                this, "android.permission.RECORD_AUDIO"
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            requestPermissions(permissionNeeded, CALL_PERMISSIONS_REQUEST_CODE)
//        } else {
//            checkOverlayPermission()
//        }
//    }
//
//    private fun initializeCall(cancelled: Boolean) {
//        val instance = BaseApplication.getInstance()
//        if (isReceiverDetailsAvailable) {
//            instance?.setReceiverDetailsAvailable(true)
//            if (cancelled) {
//                stopCall()
//                finish()
//            } else {
//                val receiverId = intent.getIntExtra(DConstants.RECEIVER_ID, 0)
//                val receiverName = intent.getStringExtra(DConstants.RECEIVER_NAME)
//                Log.d("receivername","$receiverName")
//                callType = intent.getStringExtra(DConstants.CALL_TYPE)
//                instance?.setCallType(callType)
//                val userData = instance?.getPrefs()?.getUserData()
//                userData?.id?.let {
//                    femaleUsersViewModel.callFemaleUser(
//                        it, receiverId, callType.toString()
//                    )
//                }
//
//                femaleUsersViewModel.callFemaleUserResponseLiveData.observe(this, Observer {
//
//                    GiftImageViewModel.fetchGiftImages()
//
//                    Log.d("typeofcall","${it.data?.audio_status}")
//                    Log.d("typeofcall","${it.data?.video_status}")
//
//                    if (it != null && it.success) {
//                        val callId = it.data?.call_id
//                        Log.d("CallIdInActivity","$callId")
//                        val balanceTime = it.data?.balance_time
//                        if (callId != null) {
//                            setupCall(
//                                receiverId.toString(),
//                                receiverName.toString(),
//                                callType.toString(),
//                                balanceTime,
//                                callId
//                            )
//                            addRoomStateChangedListener(callId)
//                        }
//                    } else {
//                        Toast.makeText(
//                            this@RandomUserActivity, it?.message, Toast.LENGTH_LONG
//                        ).show()
//                        stopCall()
//                        finish()
//                    }
//                })
//
//            }
//        } else {
//            instance?.setReceiverDetailsAvailable(false)
//
//            if (usersCount < 4) {
//                usersCount++
//                val userData = instance?.getPrefs()?.getUserData()
//                val callType = intent.getStringExtra(DConstants.CALL_TYPE)
//                userData?.let {
//                    callType?.let { it1 -> femaleUsersViewModel.getRandomUser(it.id, it1) }
//                }
//            } else {
//                stopCall()
//                finish()
//            }
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        if (BaseApplication.getInstance()?.getRoomId() != null) {
//            moveTaskToBack(true)
//        }
//        CallInvitationServiceImpl.getInstance().hideIncomingCallDialog()
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<String>, grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        when (requestCode) {
//            CALL_PERMISSIONS_REQUEST_CODE -> if (grantResults.isNotEmpty()) {
//                val permissionToCamera = grantResults[0] == PackageManager.PERMISSION_GRANTED
//                val permissionToRecord = grantResults[1] == PackageManager.PERMISSION_GRANTED
//                if (permissionToCamera && permissionToRecord) {
//                    checkOverlayPermission()
//                } else {
//                    finish()
//                    val intent = Intent(this, GrantPermissionsActivity::class.java)
//                    startActivity(intent)
//                }
//            }
//        }
//    }
//
//    override fun onButtonClick() {
//        getRemainingTime()
//    }
//
//    private fun initUI() {
//
//
//        Glide.with(this)
//            .asGif()
//            .load(R.drawable.double_arrow_gif) // Replace with your GIF file
//            .into(binding.ivDoubleArrow)
//
//        val giftViewModel = ViewModelProvider(this)[GiftViewModel::class.java]
//
//        GiftViewModelProvider.init(giftViewModel)
//
//        // Observe API response
//        giftViewModel.giftResponseLiveData.observe(this) { response ->
//            if (response != null) {
//                Toast.makeText(this, "Gift sent successfully!", Toast.LENGTH_SHORT).show()
//                getRemainingTime()
//
//                Log.d("GiftAPI", "Response: $response")
//            }
//        }
//
//        // Observe API failure
//        giftViewModel.giftErrorLiveData.observe(this) { error ->
//            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
//            Log.e("GiftAPI", "Error: $error")
//        }
//
//        GiftImageViewModel.giftResponseLiveData.observe(this) { response ->
//            response?.let {
//                if (it.success && it.data.isNotEmpty()) {
//                    GiftManager.updateGifts(it.data)
//                    Log.d("GiftManager", "Gifts updated successfully.")
//                } else {
//                    Log.e("GiftManager", "Failed to load gifts")
//                }
//            }
//        }
//
//
//
//
//
//        isReceiverDetailsAvailable =
//            intent.getBooleanExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, false)
//        progress()
//
//        val callType = intent.getStringExtra(DConstants.CALL_TYPE)
//        val image = intent.getStringExtra(DConstants.IMAGE)
//        val text = intent.getStringExtra(DConstants.TEXT)
//
//
//
//        if (callType == "audio") {
//            binding.tvTitle.text = "Audio call"
//        } else {
//            binding.tvTitle.text = "Video call"
//        }
//
//        val prefs = BaseApplication.getInstance()?.getPrefs()
//        val userData = prefs?.getUserData()
//        val profileImage = userData?.image
//
//        val requestOptions = RequestOptions().circleCrop()
//        Glide.with(this).load(profileImage).apply(requestOptions).into(binding.ivCallerProfile)
//
//        if (image != null) {
//            val requestOptions = RequestOptions().circleCrop()
//            Glide.with(this).load(image).apply(requestOptions).into(binding.ivLogo)
//        }
//        if (text != null) {
//            // binding.tvWaitHint.text = text
//        }
//
////        binding.btnCancel.setOnClickListener({
////            stopCall()
////            finish()
////        })
//
//        femaleUsersViewModel.randomUsersResponseLiveData.observe(this, Observer {
//            GiftImageViewModel.fetchGiftImages()
//
//            startImageSequence()
//            if (it != null && it.success) {
//                val data = it.data
//                data?.call_id?.let { it1 ->
//
//                    Log.d("randomfemalecalling","${data.user_name}")
//                    setupCall(
//                        data.call_user_id.toString(),
//                        data.call_user_name.toString(),
//                        callType.toString(),
//                        data.balance_time,
//                        it1,
//                    )
//                }
//                data?.call_id?.let { it1 -> addRoomStateChangedListener(it1) }
//                Log.d("balanceTime", "${data?.balance_time}")
//                Log.d("StarTime", "${data?.balance_time}")
//                Log.d("EndTime", "${data?.balance_time}")
//
//
//            } else {
//                Toast.makeText(
//                    this@RandomUserActivity, it?.message, Toast.LENGTH_LONG
//                ).show()
//                stopCall()
//                finish()
//            }
//        })
//        femaleUsersViewModel.randomUsersErrorLiveData.observe(this, Observer {
//            showErrorMessage(it)
//            stopCall()
//            finish()
//        })
//    }
//
//    private fun progress() {
//        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
//
//        // Set the ProgressBar max to 100
//        val max = (if (isReceiverDetailsAvailable) 30 else 60) * 1000L
//        progressBar.max = max.toInt()
//
//        // Timer for 30 seconds
//        timer = null;
//        timer = object : CountDownTimer(max, 500) { // 30000ms = 30s, 300ms interval
//            override fun onTick(millisUntilFinished: Long) {
//                // Calculate the progress
//                val progress = (max - millisUntilFinished).toInt()
//                progressBar.progress = progress
//            }
//
//            override fun onFinish() {
//                // Complete progress when the timer finishes
//                progressBar.progress = max.toInt()
//                timer?.cancel()
//                try {
//                    CallInvitationServiceImpl.getInstance().cancelInvitation { result -> }
//                } catch (e: Exception) {
//                }
//                initializeCall(true)
//            }
//        }
//        timer?.start()
//    }
//
//    private fun addRoomStateChangedListener(callId: Int) {
//        BaseApplication.getInstance()?.setCallId(callId)
//        ZegoUIKitPrebuiltCallService.events.invitationEvents.outgoingCallButtonListener =
//            object : OutgoingCallButtonListener {
//                override fun onOutgoingCallCancelButtonPressed() {
//                    stopCall()
//                    finish()
//                }
//            }
//
//
//
//        ZegoUIKitPrebuiltCallService.events.invitationEvents.invitationListener =
//            object : ZegoInvitationCallListener {
//                override fun onIncomingCallReceived(
//                    callID: String?,
//                    caller: ZegoCallUser?,
//                    callType: ZegoCallType?,
//                    callees: MutableList<ZegoCallUser>?
//                ) {
//                }
//
//                override fun onIncomingCallCanceled(callID: String?, caller: ZegoCallUser?) {
//                    Log.d("InvitationEvent", "Incoming call canceled. CallID: $callID, Caller: ${caller?.name}")
//
//                    stopCall()
//                    finish()
//                }
//
//                override fun onIncomingCallTimeout(callID: String?, caller: ZegoCallUser?) {
//                    Log.d("InvitationEvent", "Incoming call timeout. CallID: $callID")
//
//                    stopCall()
//                    finish()
//                }
//
//                override fun onOutgoingCallAccepted(callID: String?, callee: ZegoCallUser?) {
//                    Log.d("InvitationEvent", "Call accepted $callID")
//
//                }
//
//
//                override fun onOutgoingCallRejectedCauseBusy(
//                    callID: String?, callee: ZegoCallUser?
//                ) {
//                    ZegoUIKitPrebuiltCallService.endCall()
//                    initializeCall(true)
//                }
//
//                override fun onOutgoingCallDeclined(callID: String?, callee: ZegoCallUser?) {
//                    Log.d("CallEvent", "Outgoing call declined. CallID: $callID, Callee: ${callee?.name}")
//
//                    ZegoUIKitPrebuiltCallService.endCall()
//                    initializeCall(true)
//                }
//
//                override fun onOutgoingCallTimeout(
//                    callID: String?, callees: MutableList<ZegoCallUser>?
//                ) {
//                    Log.d("CallEvent", "Outgoing call timeout. CallID: $callID")
//
//                    ZegoUIKitPrebuiltCallService.endCall()
//                    initializeCall(true)
//                }
//
//            }
//
//
//
//
//        ZegoUIKit.addRoomStateChangedListener { room, reason, _, _ ->
//            when (reason) {
//                ZegoRoomStateChangedReason.LOGINED -> {
//                    Log.d("RoomStateChangedCheck", "Login reason: $reason")
//                    if(CallInvitationServiceImpl.getInstance().callInvitationData.type == 1) {
//                        activateWakeLock()
//                    }
//                    startService(Intent(this@RandomUserActivity, CallingService::class.java))
//                    timer?.cancel();
//                    lastActiveTime = System.currentTimeMillis()
//                    roomID = room
//                    Log.d("RoomStateChangedCheck", "Login room: $roomID")
//
//                    BaseApplication.getInstance()?.setRoomId(roomID)
//                    userId = BaseApplication.getInstance()?.getPrefs()
//                        ?.getUserData()?.id.toString() // Set user_id
//                    callUserId = targetUserId.toString() // Set call_user_id
//                    startTime = dateFormat.format(Date()) // Set call start time in IST
//                    BaseApplication.getInstance()?.setStartTime(startTime)
//
//                }
//
//                ZegoRoomStateChangedReason.LOGOUT -> {
//                    Log.d("RoomStateChangedCheck", "Logout reason: $reason")
//
//                    releaseWakeLock()
//                    lifecycleScope.launch {
//                        stopService(Intent(this@RandomUserActivity, CallingService::class.java))
//                        lastActiveTime = null
//                        delay(500)
//
//                        if (roomID != null) {
//                            roomID = null
//                            BaseApplication.getInstance()?.setRoomId(null)
//                            BaseApplication.getInstance()?.setCallId(null)
//                            endTime = dateFormat.format(Date()) // Set call end time in IST
//                            Log.d("RoomStateChangedCheck", "Login room: $roomID")
//                            Log.d("RoomStateChangedCheck", "Start time: $startTime")
//                            Log.d("RoomStateChangedCheck", "EndTime: $endTime")
//                            Log.d("RoomStateChangedCheck", "isReceiverDetailsAvailable: $isReceiverDetailsAvailable")
//                            Log.d("RoomStateChangedCheck", "isReceiverDetailsAvailable: $callId")
//
//                            val constraints =
//                                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
//                                    .build()
//                            val data: Data = Data.Builder().putInt(
//                                DConstants.USER_ID,
//                                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
//                            ).putInt(DConstants.CALL_ID, callId)
//                                .putString(DConstants.STARTED_TIME, startTime)
//                                .putBoolean(DConstants.IS_INDIVIDUAL, isReceiverDetailsAvailable)
//                                .putString(DConstants.ENDED_TIME, endTime).build()
//
//                            Log.d("CallIDValue","$callId")
//                            val oneTimeWorkRequest = OneTimeWorkRequest.Builder(
//                                CallUpdateWorker::class.java
//                            ).setInputData(data).setConstraints(constraints).build()
//                            WorkManager.getInstance(this@RandomUserActivity)
//                                .enqueue(oneTimeWorkRequest)
//                            finish()
//                            val receiverId = intent.getIntExtra(DConstants.RECEIVER_ID, 0)
//                            val intent = Intent(this@RandomUserActivity, RatingActivity::class.java)
//                            intent.putExtra(DConstants.RECEIVER_NAME, callUserName)
//                            intent.putExtra(DConstants.RECEIVER_ID, receiverId)
//                            startActivity(intent)
//                        }
//                    }
//                }
//
//                else ->  {Log.d("RoomStateChanged", "Unhandled reason: $reason")
//                }
//            }
//        }
//    }
//
//
//
//    private fun stopCall() {
//        try {
//            try {
//                CallInvitationServiceImpl.getInstance().cancelInvitation { result -> }
//            } catch (e: Exception) {
//            }
//            ZegoUIKitPrebuiltCallService.endCall()
//        } catch (e: Exception) {
//        }
//    }
//
//    private fun setupCall(
//        receiverId: String?, receiverName: String, type: String, balanceTime: String?, callId: Int
//    ) {
//        val prefs = BaseApplication.getInstance()?.getPrefs()
//        val userData = prefs?.getUserData()
//        if (userData != null) {
//            this.balanceTime = balanceTime
//            setupZegoUIKit(userData.id, userData.name)
//        }
//        when (type) {
//            "audio" -> receiverId?.let { StartVoiceCall(it, receiverName, callId) }
//            "video" -> receiverId?.let { StartVideoCall(it, receiverName, callId) }
//            else -> Toast.makeText(this, "Invalid call type", Toast.LENGTH_SHORT).show()
//        }
//
//    }
//
//    private fun StartVoiceCall(targetUserId: String, targetName: String, callId: Int) {
//        binding.voiceCallButton.setIsVideoCall(false)
//
//
//        binding.voiceCallButton.resourceID = "zego_call"
//        val user = ZegoUIKitUser(targetUserId, targetName)
//        val instance = BaseApplication.getInstance()
//        user.avatar = instance?.getPrefs()?.getUserData()?.image
//        binding.voiceCallButton.setCustomData(callId.toString())
//        binding.voiceCallButton.setInvitees(listOf(user))
//        binding.voiceCallButton.setTimeout(if (isReceiverDetailsAvailable) 30 else 15)
//        callUserName = targetName
//        callUserId = targetUserId
//        instance?.setCallUserId(callUserId)
//        instance?.setCallUserName(callUserName)
//        lifecycleScope.launch {
//            if (instance?.isCalled() == false || instance?.isCalled() == null) {
//                delay(4000)
//                instance?.setCalled(true)
//            }
//            binding.voiceCallButton.performClick()
//        }
//
//    }
//
//    private fun StartVideoCall(targetUserId: String, targetName: String, callId: Int) {
//        callUserName = targetName
//        callUserId = targetUserId
//        binding.voiceCallButton.setIsVideoCall(true)
//        binding.voiceCallButton.resourceID = "zego_call"
//        val user = ZegoUIKitUser(targetUserId, targetName)
//        val instance = BaseApplication.getInstance()
//        user.avatar = instance?.getPrefs()?.getUserData()?.image
//        binding.voiceCallButton.setCustomData(callId.toString())
//        binding.voiceCallButton.setInvitees(listOf(user))
//        binding.voiceCallButton.setTimeout(if (isReceiverDetailsAvailable) 30 else 15)
//        lifecycleScope.launch {
//            if (instance?.isCalled() == false || instance?.isCalled() == null) {
//                delay(4000)
//                instance?.setCalled(true)
//            }
//            binding.voiceCallButton.performClick()
//        }
//    }
//
//    private fun startImageSequence() {
//        // List of image resources
//        val images = listOf(
//            R.drawable.avatar1,
//            R.drawable.avatar2,
//            R.drawable.avatar3,
//            R.drawable.avatar4,
//            R.drawable.avatar5,
//            R.drawable.avatar6,
//
//        )
//
//        // Handler to post delayed tasks
//        val handler = Handler(Looper.getMainLooper())
//
//        // Function to update image sequence
//        val updateImageSequence = object : Runnable {
//            var currentImageIndex = 0
//
//            override fun run() {
//                if (isFinishing || isDestroyed) {
//                    return // Exit if the activity is finishing or destroyed
//                }
//
//                // Apply circle crop using Glide
//                val requestOptions = RequestOptions().circleCrop()
//
//                // Load the image using Glide with circle crop
//                Glide.with(this@RandomUserActivity)
//                    .load(images[currentImageIndex])  // Load the current image resource
//                    .apply(requestOptions)  // Apply the circle crop transformation
//                    .into(binding.ivLogo)  // Set image into the ImageView
//
//                // Move to the next image
//                currentImageIndex = (currentImageIndex + 1) % images.size  // Loop back to the first image after the last one
//
//                // Post the next update with a delay of 1 second
//                handler.postDelayed(this, 1000)  // 1 second delay
//            }
//        }
//
//        // Start the image sequence
//        handler.post(updateImageSequence)
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        Log.d("destroying","destroyed")
//        intent.replaceExtras(null) // Clears all extras
//
//    }
    override fun onButtonClick() {
        TODO("Not yet implemented")
    }


}
