package com.gmwapp.hima.agora.male

import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityMaleVideoCallingBinding
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.appsflyer.AppsFlyerLib
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.PaymentWebViewActivity
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.utils.AppEventLogger
import com.gmwapp.hima.activities.RatingActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import android.telecom.DisconnectCause
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.retrofit.responses.GiftData
import com.gmwapp.hima.viewmodels.GiftImageViewModel
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.media.RtcTokenBuilder2
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.agora.services.CallingService
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.CallAudioFocusHelper
import com.gmwapp.hima.utils.CallAudioRouter
import com.gmwapp.hima.utils.CallPhoneStateHelper
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import com.gmwapp.hima.retrofit.responses.CallEndReason
import com.gmwapp.hima.retrofit.responses.CallEndedBy
import com.gmwapp.hima.viewmodels.CallDropStatusViewModel
import com.gmwapp.hima.viewmodels.CallStatusViewModel
import com.gmwapp.hima.viewmodels.LudoFcmViewModel
import com.gmwapp.hima.workers.CallUpdateWorker
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.IRtcEngineEventHandler.RtcStats
import io.agora.rtc2.video.VideoCanvas
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import kotlin.math.abs


//import androidx.camera.core.*
//import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
//import androidx.camera.core.ExperimentalGetImage
import com.gmwapp.hima.agora.FaceDetectVideoFrameObserver
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.audio.AudioParams
import org.json.JSONObject
//import org.vosk.Model
//import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


@AndroidEntryPoint
class MaleVideoCallingActivity : AppCompatActivity() {

    companion object {
        private const val TAG_END = "MaleVideoEndFlow"
    }

    lateinit var binding: ActivityMaleVideoCallingBinding
    var receiverId = 0


    private var isMuted = false
    private var isSpeakerOn = true

    private var audioFocusHelper: CallAudioFocusHelper? = null
    private var audioRouter: CallAudioRouter? = null
    private var phoneStateHelper: CallPhoneStateHelper? = null
    private var btWatcher: com.gmwapp.hima.utils.BluetoothCallWatcher? = null
    private var mutedByInterrupt = false
    var isClicked : Boolean = false

    private var videoUid = 0

    private var appId: String? = null // Will be received from backend

    var expirationTimeInSeconds = 3600
    lateinit var channelName : String
    private var token : String? = null
    private var storedVideoRemainingTime: String? = null
    private var storedRemainingTime: String? = null

    private var countDownTimer: CountDownTimer? = null

    private var isSwitchRequestPending = false


    var switchCallID = 0
    var receiverName = ""

    private var switchDialog: AlertDialog? = null  // Track current dialog
    private var faceDialog: Dialog? = null
    private var faceDetectedHandler: Handler? = null
    private var faceDetectedRunnable: Runnable? = null
    private var localPreviewSurface: SurfaceView? = null
    private var isShowingFacePreview = false

    private fun cancelFacePreviewTransition() {
        faceDetectedRunnable?.let { runnable ->
            faceDetectedHandler?.removeCallbacks(runnable)
        }
        faceDetectedRunnable = null
    }

    private var isSwitchingToAudio = false // ✅ Prevent multiple calls
    private var isSwitchingToVideo = false // ✅ Prevent multiple calls

    private val profileViewModel: ProfileViewModel by viewModels()
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private val userAvatarViewModel: UserAvatarViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()
    private val callDropStatusViewModel: CallDropStatusViewModel by viewModels()
    private val callStatusViewModel: CallStatusViewModel by viewModels()
    private val isCaller: Boolean by lazy { intent.getBooleanExtra("IS_CALLER", false) }

    private var currentAudioRoute: com.gmwapp.hima.utils.CallAudioRouter.AudioRoute =
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE
    private val ludoFcmViewModel: LudoFcmViewModel by viewModels()
    private val giftImageViewModel: GiftImageViewModel by viewModels()
    private val giftViewModel: com.gmwapp.hima.viewmodels.GiftViewModel by viewModels()
    private lateinit var giftRailAdapter: com.gmwapp.hima.adapters.GiftRailAdapter
    private var lastSentGiftCount: Int = 0


    private val uid = 0
    private var isJoined = false
//    private var mRtmClient: RtmClient? = null

    private var agoraEngine: RtcEngine? = null

    private var localSurfaceView: SurfaceView? = null

    private var remoteSurfaceView: SurfaceView? = null
    private var localPreviewOffsetX = Float.NaN
    private var localPreviewOffsetY = Float.NaN
    private var localPreviewTouchOffsetX = 0f
    private var localPreviewTouchOffsetY = 0f
    private var localPreviewDragStartX = 0f
    private var localPreviewDragStartY = 0f
    private var isDraggingLocalPreview = false
    private var isRemoteBlurVisible = false
    private var pendingRemoteBlurHide = false
    private var mRtcEngine: RtcEngine? = null

    private var startTime: String = ""
    private var endTime: String = ""
    var callId : Int = 0
    private var pendingLudoAction: String? = null
    private var currentLudoInviteId: String? = null

    private var isAudioCallGoing: Boolean = false

    var isAudioCallIdReceived: Boolean = false

//    private var cameraProvider: ProcessCameraProvider? = null
    private var faceDetector: FaceDetector? = null
//    private var analysisUseCase: ImageAnalysis? = null
//    private var camera: Camera? = null
    private var lastFaceMissingTime = 0L


//    private lateinit var model: Model
//    private lateinit var recognizer: Recognizer

    private val executor = Executors.newSingleThreadExecutor()
    private val accountViewModel: AccountViewModel by viewModels()

    var blockWords: List<String> = emptyList()
    var isBlockWordDetected : Boolean = false




    var maleUserId = 0


    private var isRemoteUserJoined = false
    private var elapsedTime = 0  // Tracks elapsed seconds
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = object : Runnable {
        override fun run() {
            elapsedTime++
            Log.d(
                TAG_END,
                "timeout tick=$elapsedTime isRemoteUserJoined=$isRemoteUserJoined isJoined=$isJoined"
            )

            if (elapsedTime >=10) { // 20 seconds timeout
                if (isRemoteUserJoined==false){
                    Log.d(TAG_END, "timeout fired -> leaveChannel (remote never joined)")
                    Log.d("isUserJoinedTimer","Leave Button")
                    Toast.makeText(this@MaleVideoCallingActivity,"User did not join", Toast.LENGTH_LONG).show()

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

    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (isFinishing) return
            Log.d(
                TAG_END,
                "HB isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined elapsedTime=$elapsedTime isDestroyed=$isDestroyed"
            )
            heartbeatHandler.postDelayed(this, 5000L)
        }
    }

    private fun startHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        heartbeatHandler.post(heartbeatRunnable)
    }

    private fun stopHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }

    fun startTimeoutTracking() {
        Log.d(TAG_END, "startTimeoutTracking called")
        elapsedTime = 0  // Reset counter
        timeoutHandler.post(timeoutRunnable) // Start tracking
    }

    fun cancelTimeoutTracking() {
        val caller = Throwable().stackTrace.getOrNull(2)
            ?.let { "${it.className}.${it.methodName}:${it.lineNumber}" }
            ?: "unknown"
        Log.d(TAG_END, "cancelTimeoutTracking called from $caller")
        timeoutHandler.removeCallbacks(timeoutRunnable) // Stop tracking if call is accepted
        Log.d("isUserJoinedTimer","Cancelled")
    }


    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
        )
    } else {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }


    private fun checkSelfPermission(): Boolean {
        return REQUESTED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
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
        if (appId == null) {
            Log.e("AgoraToken", "AppId is null, cannot initialize engine")
            showMessage("Failed to initialize call. Please try again.")
            Log.d(TAG_END, "finish() from setupVideoSDKEngine.appIdNull")
            finish()
            return
        }
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId!!
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)
            // Enable video and audio modules
            agoraEngine!!.enableVideo()
            agoraEngine!!.enableAudio()
            // Configure audio profile BEFORE joinChannel to avoid mid-session track reset
            agoraEngine!!.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD, Constants.AUDIO_SCENARIO_DEFAULT)
            agoraEngine!!.enableAudioVolumeIndication(200, 3, true)
            // Set the SDK's default audio route + explicit current route so users hear
            // audio in the expected output immediately (also helps Bluetooth/headset).
            agoraEngine!!.setDefaultAudioRoutetoSpeakerphone(true)
            agoraEngine!!.setEnableSpeakerphone(isSpeakerOn)
            Log.d("AgoraTiming", "MaleVideo setupVideoSDKEngine done at ${System.currentTimeMillis()}")

            audioRouter?.release()
            audioRouter = CallAudioRouter(this).also { it.init() }
            val btNow = audioRouter?.isBluetoothConnected() == true
            val initial = when {
                btNow -> com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH
                isSpeakerOn -> com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER
                else -> com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE
            }
            Log.d(
                "CallAudioRoute",
                "Activity.setup initialRoute=$initial btConnected=$btNow isSpeakerOn=$isSpeakerOn"
            )
            applyAudioRoute(initial)

            setupCallInterruptHandlers()
        } catch (e: Exception) {
            showMessage(e.toString())
        }
    }

    private fun setupCallInterruptHandlers() {
        if (audioFocusHelper == null) {
            audioFocusHelper = CallAudioFocusHelper(
                context = this,
                onFocusLost = { muteForInterrupt(true) },
                onFocusGained = { muteForInterrupt(false) }
            ).also { it.request() }
        }
        if (phoneStateHelper == null) {
            phoneStateHelper = CallPhoneStateHelper(
                context = this,
                onCellularCallActive = { muteForInterrupt(true) },
                onCellularCallEnded = { muteForInterrupt(false) }
            ).also { it.register() }
        }
        if (btWatcher == null) {
            btWatcher = com.gmwapp.hima.utils.BluetoothCallWatcher(this) { connected ->
                Log.d(
                    "CallAudioRoute",
                    "Activity.btChange connected=$connected currentRoute=$currentAudioRoute"
                )
                if (connected) {
                    runOnUiThread {
                        applyAudioRoute(com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH)
                    }
                } else if (currentAudioRoute == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH) {
                    runOnUiThread {
                        applyAudioRoute(com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER)
                    }
                }
            }.also { it.register() }
        }
    }

    private fun muteForInterrupt(muted: Boolean) {
        runOnUiThread {
            if (muted) {
                if (!mutedByInterrupt && !isMuted) {
                    mutedByInterrupt = true
                    agoraEngine?.muteLocalAudioStream(true)
                }
            } else {
                if (mutedByInterrupt) {
                    mutedByInterrupt = false
                    if (!isMuted) agoraEngine?.muteLocalAudioStream(false)
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BaseApplication.getInstance()?.markCallActive()
        BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
        enableEdgeToEdge()
        binding = ActivityMaleVideoCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep the call screen visible across lockscreen so users who lock
        // the phone mid-call can resume immediately.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // TEMP: FLAG_SECURE disabled so QA can capture screenshots for testing.
        // Re-enable before release by uncommenting.
        // window.setFlags(
        //     WindowManager.LayoutParams.FLAG_SECURE,
        //     WindowManager.LayoutParams.FLAG_SECURE
        // )
        
        // ✅ Set status bar and navigation bar to black with light icons
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        
        // Make status bar icons light (white) so they're visible on black background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.decorView.systemUiVisibility = 0 // Light icons on dark background
        }
        
        // For Android 11+ use WindowInsetsController for better control
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insetsController = window.insetsController
            if (insetsController != null) {
                insetsController.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                insetsController.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        if (userData != null) {
            maleUserId = userData.id
        }

        showGreyScreen()

        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        receiverId = intent.getIntExtra("RECEIVER_ID", -1)
        callId = intent.getIntExtra("CALL_ID", 0)

        Log.d(
            TAG_END,
            "onCreate channel=$channelName receiverId=$receiverId callId=$callId maleUserId=$maleUserId"
        )
        Log.d("VideoCallingLog", "Channel: $channelName, Receiver: $receiverId, callId:$callId")
        Log.d("AgoraTiming", "MaleVideo onCreate at ${System.currentTimeMillis()}")

        // Use pre-fetched token from connecting/accept screen if available, else fetch from backend
        val intentToken = intent.getStringExtra("AGORA_TOKEN")
        val intentAppId = intent.getStringExtra("AGORA_APP_ID")
        if (!intentToken.isNullOrEmpty() && !intentAppId.isNullOrEmpty()) {
            Log.d("AgoraTiming", "MaleVideo using pre-fetched token at ${System.currentTimeMillis()}")
            token = intentToken
            appId = intentAppId
            if (!checkSelfPermission()) {
                ActivityCompat.requestPermissions(
                    this@MaleVideoCallingActivity,
                    REQUESTED_PERMISSIONS,
                    PERMISSION_REQ_ID
                )
            } else {
                setupVideoSDKEngine()
                joinChannel(binding.JoinButton)
            }
        } else {
            getAgoraTokenFromBackend()
        }

        onAddcoinClicked()
        binding.btnMuteUnmute.setOnClickListener {
            toggleMute()
        }

        binding.btnSpeaker.setOnClickListener {
            onSpeakerButtonClicked()
        }

        binding.btnCameraFlip.setOnClickListener {
            runCatching { agoraEngine?.switchCamera() }
                .onFailure { Log.w("MaleVideoCalling", "switchCamera failed: ${it.message}") }
        }

        endcallBtn()
        onBackPressedBtn()
//        onMenuClicked()

        userAvatarViewModel.getUserAvatar(receiverId)

        avatarObservers()
        userData?.let { setMyAvatar(it.image, it.name) }
        setupIplTeamBadges()

        observeCallSwitchRequest()

        handleCallSwitch()
        setupLocalPreviewDrag()

        getBlockWords()
        if (com.gmwapp.hima.utils.FeatureFlags.LUDO_ENABLED) {
            setupLudoInviteFlow()
        } else {
            binding.ludoButtonCard.visibility = View.GONE
        }
        giftIconClicked()
        startHeartbeat()
    }

    private fun giftIconClicked() {
        val rail = binding.rvGiftRail
        rail.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        giftRailAdapter = com.gmwapp.hima.adapters.GiftRailAdapter(this) { gift ->
            sendGiftFromRail(gift)
        }
        rail.adapter = giftRailAdapter

        giftImageViewModel.giftResponseLiveData.observe(this, Observer { response ->
            response?.data?.let { list -> giftRailAdapter.updateGiftList(list) }
        })
        giftImageViewModel.giftErrorLiveData.observe(this, Observer { msg ->
            msg?.let { Log.e("GiftRail", it) }
        })
        giftImageViewModel.fetchGiftImages()

        observeGiftSendResponse()
        refreshAvailableCoinsForRail()
    }

    private fun sendGiftFromRail(gift: GiftData) {
        val senderId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        profileViewModel.getRemainingTime(senderId, "video", object :
            NetworkCallback<GetRemainingTimeResponse> {
            override fun onResponse(
                call: Call<GetRemainingTimeResponse>,
                response: Response<GetRemainingTimeResponse>
            ) {
                val newTime = response.body()?.data?.remaining_time ?: return
                val leftCoins = railAvailableCoins(newTime)
                giftRailAdapter.setAvailableCoins(leftCoins)
                if (leftCoins >= gift.coins) {
                    lastSentGiftCount = 1
                    giftViewModel.sendGift(senderId, receiverId, gift.id)
                } else {
                    Toast.makeText(
                        this@MaleVideoCallingActivity,
                        "Not enough coins (need ${gift.coins})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {}
            override fun onNoNetwork() {
                Toast.makeText(
                    this@MaleVideoCallingActivity,
                    "No network",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun refreshAvailableCoinsForRail() {
        val senderId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        profileViewModel.getRemainingTime(senderId, "video", object :
            NetworkCallback<GetRemainingTimeResponse> {
            override fun onResponse(
                call: Call<GetRemainingTimeResponse>,
                response: Response<GetRemainingTimeResponse>
            ) {
                val newTime = response.body()?.data?.remaining_time ?: return
                if (::giftRailAdapter.isInitialized) {
                    giftRailAdapter.setAvailableCoins(railAvailableCoins(newTime))
                }
            }

            override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {}
            override fun onNoNetwork() {}
        })
    }

    private fun railAvailableCoins(remainingTime: String): Int {
        val parts = remainingTime.split(":")
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val totalMinutes = minutes + if (seconds >= 30) 1 else 0
        return totalMinutes * 60
    }

    private fun observeGiftSendResponse() {
        giftViewModel.giftResponseLiveData.observe(this) { response ->
            if (response != null && response.success && lastSentGiftCount == 1) {
                Toast.makeText(this, "Gift Sent Successfully!", Toast.LENGTH_SHORT).show()
                lastSentGiftCount = 0
                response.data?.let { sendGiftSentNotification(it.gift_icon) }
                newRemainingTime()
                response.data?.let { animateGift(it.gift_icon) }
                refreshAvailableCoinsForRail()
            }
        }
        giftViewModel.giftErrorLiveData.observe(this) { errorMessage ->
            Log.e("GiftRail", errorMessage)
        }
    }

    fun animateGift(image: String) {
        val giftImage = binding.ivGiftImage
        // Sender: local preview card (the male user). Receiver: remote video container (the female).
        val sender: View = binding.localCardView
        val receiver: View = binding.remoteVideoViewContainer

        giftImage.animate().cancel()
        com.bumptech.glide.Glide.with(this).load(image).into(giftImage)

        giftImage.visibility = View.VISIBLE
        giftImage.alpha = 0f
        giftImage.scaleX = 0.4f
        giftImage.scaleY = 0.4f
        giftImage.translationX = 0f
        giftImage.translationY = 0f

        giftImage.post {
            val originX = giftImage.x
            val originY = giftImage.y

            val giftLoc = IntArray(2); giftImage.getLocationOnScreen(giftLoc)
            val senderLoc = IntArray(2); sender.getLocationOnScreen(senderLoc)
            val receiverLoc = IntArray(2); receiver.getLocationOnScreen(receiverLoc)

            val startX = giftImage.x +
                (senderLoc[0] - giftLoc[0]) +
                (sender.width / 2f - giftImage.width / 2f)
            val startY = giftImage.y +
                (senderLoc[1] - giftLoc[1]) +
                (sender.height / 2f - giftImage.height / 2f)
            val endX = giftImage.x +
                (receiverLoc[0] - giftLoc[0]) +
                (receiver.width / 2f - giftImage.width / 2f)
            val endY = giftImage.y +
                (receiverLoc[1] - giftLoc[1]) +
                (receiver.height / 2f - giftImage.height / 2f) -
                (24f * resources.displayMetrics.density)

            giftImage.x = startX
            giftImage.y = startY

            giftImage.animate()
                .scaleX(0.9f).scaleY(0.9f).alpha(1f)
                .setDuration(180L)
                .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                .withEndAction {
                    giftImage.animate()
                        .x(endX).y(endY)
                        .scaleX(1.2f).scaleY(1.2f)
                        .setDuration(640L)
                        .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                        .withEndAction {
                            giftImage.animate()
                                .scaleX(0.8f).scaleY(0.8f).alpha(0f)
                                .setStartDelay(220L).setDuration(500L)
                                .setInterpolator(android.view.animation.AccelerateInterpolator())
                                .withEndAction {
                                    giftImage.visibility = View.INVISIBLE
                                    giftImage.alpha = 1f
                                    giftImage.scaleX = 1f
                                    giftImage.scaleY = 1f
                                    giftImage.translationX = 0f
                                    giftImage.translationY = 0f
                                    giftImage.x = originX
                                    giftImage.y = originY
                                }
                                .start()
                        }
                        .start()
                }
                .start()
        }
    }

    fun sendGiftSentNotification(giftIcon: String) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val senderId = userData?.id
        if (senderId != null) {
            fcmNotificationViewModel.sendNotification(
                senderId = senderId,
                receiverId = receiverId,
                callType = giftIcon,
                channelName = channelName,
                message = "giftSent"
            )
        }
    }

    private fun getAgoraTokenFromBackend() {
        // Observe token response
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            if (response != null && response.success == true && !response.token.isNullOrEmpty()) {
                token = response.token
                // Get appId from backend response (required)
                appId = response.app_id
                if (appId.isNullOrEmpty()) {
                    Log.e("AgoraToken", "AppId not received from backend")
                    showMessage("Failed to initialize call. Please try again.")
                    Log.d(TAG_END, "finish() from getAgoraTokenFromBackend.appIdEmpty")
                    finish()
                    return@observe
                }
                Log.d("AgoraToken", "Token and AppId received from backend")
                
                // Request permissions if not granted
                if (!checkSelfPermission()) {
                    ActivityCompat.requestPermissions(
                        this@MaleVideoCallingActivity,
                        REQUESTED_PERMISSIONS,
                        PERMISSION_REQ_ID
                    )
                } else {
                    setupVideoSDKEngine()
                    joinChannel(binding.JoinButton) // Automatically join the channel
                }
            } else {
                Log.e("AgoraToken", "Failed to get token: ${response?.message}")
                showMessage("Failed to initialize call. Please try again.")
                Log.d(TAG_END, "finish() from getAgoraTokenFromBackend.tokenFailed")
                finish()
            }
        }

        // Observe errors
        agoraViewModel.agoraTokenErrorLiveData.observe(this) { error ->
            Log.e("AgoraToken", "Error: $error")
            showMessage(error ?: "Failed to initialize call. Please try again.")
            Log.d(TAG_END, "finish() from getAgoraTokenFromBackend.tokenErrorLiveData")
            finish()
        }

        // Request token from ViewModel
        agoraViewModel.getAgoraToken(channelName, uid, "publisher", expirationTimeInSeconds)
    }

    private fun getBlockWords(){
        accountViewModel.getSettings()

        accountViewModel.settingsLiveData.observe(this, Observer { response ->
            if (response?.success == true) {
                response.data?.let { settingsList ->
                    if (settingsList.isNotEmpty()) {
                        val settingsData = settingsList[0]
                        blockWords = settingsData.blockWords
                        Log.d("BlockWords", "$blockWords")
                    }
                }
            }
        })
    }

    private fun initVosk() {
//        executor.execute {
//            try {
//                val modelPath = File(copyAssetToCache("vosk-model-small-en-us-0.15.zip"), "vosk-model-small-en-us-0.15").absolutePath
//                model = Model(modelPath)
//                recognizer = Recognizer(model, 16000.0f)
//            } catch (e: IOException) {
//                Log.e("Vosk", "Model load failed", e)
//            }
//        }
    }

    private fun copyAssetToCache(zipAssetName: String): String {
        val targetDir = File(cacheDir, "vosk-model")
        if (!targetDir.exists()) {
            val inputStream = assets.open(zipAssetName)
            unzip(inputStream, targetDir.absolutePath)
        }
        Log.d("Vosk", "Extracted model to: ${targetDir.absolutePath}, contents: ${targetDir.listFiles()?.joinToString { it.name }}")

        return targetDir.absolutePath
    }

    fun unzip(zipInputStream: InputStream, targetLocation: String) {
        val zis = ZipInputStream(BufferedInputStream(zipInputStream))
        var ze: ZipEntry? = zis.nextEntry

        while (ze != null) {
            val file = File(targetLocation, ze.name)
            if (ze.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                val fout = FileOutputStream(file)
                val buffer = ByteArray(1024)
                var count: Int
                while (zis.read(buffer).also { count = it } != -1) {
                    fout.write(buffer, 0, count)
                }
                fout.close()
            }
            zis.closeEntry()
            ze = zis.nextEntry
        }
        zis.close()
    }

//    private val audioFrameObserver = object : IAudioFrameObserver {
//
//
//        override fun onRecordAudioFrame(
//            channelId: String?,
//            type: Int,
//            samplesPerChannel: Int,
//            bytesPerSample: Int,
//            channels: Int,
//            samplesPerSec: Int,
//            buffer: ByteBuffer?,
//            renderTimeMs: Long,
//            avsync_type: Int
//        ): Boolean {
//            if (buffer == null || !::recognizer.isInitialized) return true
//
//            val pcmData = ByteArray(buffer.remaining())
//            Log.d("VOSK-FINAL", pcmData.size.toString())
//
//            buffer.get(pcmData)
//
//            executor.execute {
//                try {
//                    if (recognizer.acceptWaveForm(pcmData, pcmData.size)) {
//                        val resultJson = recognizer.result  // JSON string like {"text" : "hello"}
//                        val textOnly = JSONObject(resultJson).optString("text", "")
//                        Log.d("VOSK-FINAL-Text", textOnly)  // logs just "hello"
//
//                        runOnUiThread {
//                            val matchedWord = blockWords.firstOrNull { word ->
//                                textOnly.contains(word, ignoreCase = true)
//                            }
//
//                            matchedWord?.let {
//                                isBlockWordDetected = true
//
//                                leaveChannel(binding.LeaveButton)
////
////                                Toast.makeText(
////                                    this@MaleAudioCallingActivity,
////                                    "Blocked word detected: \"$it\"",
////                                    Toast.LENGTH_SHORT
////                                ).show()
//                            }
//                        }
//
//
//
//                    } else {
//                        Log.d("VOSK-PARTIAL", recognizer.partialResult)
//                    }
//                } catch (e: Exception) {
//                    Log.e("VOSK-ERROR", "Error in recognition: ${e.message}")
//                }
//            }
//
//            return true
//        }
//
//        override fun onPlaybackAudioFrame(
//            channelId: String?,
//            type: Int,
//            samplesPerChannel: Int,
//            bytesPerSample: Int,
//            channels: Int,
//            samplesPerSec: Int,
//            buffer: ByteBuffer?,
//            renderTimeMs: Long,
//            avsync_type: Int
//        ): Boolean {
//            return true
//        }
//
//        override fun onMixedAudioFrame(
//            channelId: String?,
//            type: Int,
//            samplesPerChannel: Int,
//            bytesPerSample: Int,
//            channels: Int,
//            samplesPerSec: Int,
//            buffer: ByteBuffer?,
//            renderTimeMs: Long,
//            avsync_type: Int
//        ): Boolean {
//            return true
//        }
//
//        override fun onEarMonitoringAudioFrame(
//            type: Int,
//            samplesPerChannel: Int,
//            bytesPerSample: Int,
//            channels: Int,
//            samplesPerSec: Int,
//            buffer: ByteBuffer?,
//            renderTimeMs: Long,
//            avsync_type: Int
//        ): Boolean {
//            return true
//        }
//
//        override fun onPlaybackAudioFrameBeforeMixing(
//            channelId: String?,
//            uid: Int,
//            type: Int,
//            samplesPerChannel: Int,
//            bytesPerSample: Int,
//            channels: Int,
//            samplesPerSec: Int,
//            buffer: ByteBuffer?,
//            renderTimeMs: Long,
//            avsync_type: Int,
//            rtpTimestamp: Int
//        ): Boolean {
//            return true
//        }
//
//        override fun getObservedAudioFramePosition(): Int {
//            return Constants.POSITION_RECORD       }
//
//        override fun getRecordAudioParams(): AudioParams {
//            return AudioParams(
//                16000, // sample rate (Hz)
//                1,     // mono
//                Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY,
//                1024   // samples per call
//            )        }
//
//        override fun getPlaybackAudioParams(): AudioParams {
//            return AudioParams(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1024)
//        }
//
//        override fun getMixedAudioParams(): AudioParams {
//            return AudioParams(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1024)
//        }
//
//        override fun getEarMonitoringAudioParams(): AudioParams {
//            return AudioParams(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1024)
//        }
//    }


    private fun setMyAvatar(image: String, name: String) {
        binding.tvMaleName.setText(name)
        Glide.with(this)
            .load(image)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivMaleUser)
    }

    private fun setupIplTeamBadges() {
        // IPL badges hidden — no longer shown during calls.
        binding.maleIplBadge.visibility = View.GONE
        binding.maleTeamRing.visibility = View.GONE
        binding.femaleIplBadge.visibility = View.GONE
        binding.femaleTeamRing.visibility = View.GONE
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

                showEndCallConfirmationDialog()
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


    fun startCallingService() {

        Log.d("startCallingService","Service Function call")
        if (CallingService.isRunning) return

        Log.d("startCallingService","Service not returned")

        val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)


        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        Log.d("startCallingService","$visible,  $micGranted")


        if (visible && micGranted) {
            // ✅ Only start microphone FGS from a visible Activity with mic permission granted
            ContextCompat.startForegroundService(this, Intent(this, CallingService::class.java))
            Log.d("startCallingService","Service class called")

        } else {

            // Do NOT start here (would crash on Android 14/15)
            // - If permission missing: request it, then call startCallingService() again.
            // - If not visible: bring Activity to foreground (e.g., from notification action), then start.
        }
    }

    fun stopCallingService() {
        val intent = Intent(this, CallingService::class.java)
        stopService(intent)
    }

    override fun onDestroy() {
        stopHeartbeat()
        Log.d(
            TAG_END,
            "onDestroy isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined elapsedTime=$elapsedTime isFinishing=$isFinishing"
        )
        super.onDestroy()
        BaseApplication.getInstance()?.markCallEnded()
        BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
        HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)

        stopCallingService()
        cancelTimeoutTracking()
        cancelFacePreviewTransition()
        faceDetectedHandler = null
        faceDetectedRunnable = null

        audioFocusHelper?.abandon()
        audioFocusHelper = null
        audioRouter?.release()
        audioRouter = null
        phoneStateHelper?.unregister()
        phoneStateHelper = null
        btWatcher?.unregister()
        btWatcher = null

        // Ensure agoraEngine is not null before using it
        agoraEngine?.let { engine ->
            try {
                engine.stopPreview()
            } catch (e: Exception) {
                Log.e("MaleVideoCalling", "stopPreview in onDestroy", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            try {
                engine.leaveChannel()
            } catch (e: Exception) {
                Log.e("MaleVideoCalling", "leaveChannel in onDestroy", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            Thread {
                try {
                    RtcEngine.destroy()
                } catch (e: Exception) {
                    Log.e("MaleVideoCalling", "RtcEngine.destroy in onDestroy", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
                agoraEngine = null
            }.start()
        }

        if (isRemoteUserJoined==true&&isBlockWordDetected==false){
            val intent = Intent(this@MaleVideoCallingActivity, RatingActivity::class.java)
            intent.putExtra(DConstants.RECEIVER_NAME, receiverName)
            intent.putExtra(DConstants.RECEIVER_ID, receiverId)
            startActivity(intent)
            Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
        }

        if (isRemoteUserJoined==true && isBlockWordDetected==true){
            val intent = Intent(this@MaleVideoCallingActivity, MainActivity::class.java)
            intent.putExtra("blockword", true)
            startActivity(intent)
            Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
        }

    }
    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
           // showMessage("Remote user joined $uid")
            Log.d(
                TAG_END,
                "onUserJoined uid=$uid isRemoteUserJoined=$isRemoteUserJoined isJoined=$isJoined"
            )
            Log.d("AgoraTiming", "MaleVideo onUserJoined at ${System.currentTimeMillis()}")
            startCallingService()
            isRemoteUserJoined= true
            videoUid = uid

            getRemainingTime()

            startTime = dateFormat.format(Date()) // Set call end time in IST

            // Set the remote video view
            runOnUiThread { setupRemoteVideo(uid) }

            if (ContextCompat.checkSelfPermission(this@MaleVideoCallingActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val granted = ContextCompat.checkSelfPermission(this@MaleVideoCallingActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                Log.d("FaceDetection", "CAMERA permission granted: $granted")
                //startFaceDetectionCamera()
                val videoObserver = FaceDetectVideoFrameObserver(this@MaleVideoCallingActivity)
                agoraEngine?.registerVideoFrameObserver(videoObserver)

            } else {
                Log.d("FaceDetection", "CAMERA permission granted: Not granted")

                ActivityCompat.requestPermissions(this@MaleVideoCallingActivity, arrayOf(Manifest.permission.CAMERA), 22)
            }

            val bundle = Bundle().apply {
                putString("user_id", "${maleUserId}")
            }

            FirebaseAnalytics.getInstance(this@MaleVideoCallingActivity).logEvent("call_started", bundle)

            val eventValues = HashMap<String, Any>()
            eventValues["user_id"] = maleUserId    // example duration
            eventValues["call_type"] = "Video"             // example parameter

            AppsFlyerLib.getInstance().logEvent(
                this@MaleVideoCallingActivity,
                "call_started",
                eventValues
            )

            // Log to backend (only Firebase events)
            AppEventLogger.logEvent(
                context = this@MaleVideoCallingActivity,
                eventName = "call_started",
                platform = "firebase",
                userId = maleUserId,
                params = AppEventLogger.bundleToMap(bundle)
            )

            initVosk()

//            agoraEngine?.registerAudioFrameObserver(audioFrameObserver)
        }

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
            Log.d(
                TAG_END,
                "onJoinChannelSuccess uid=$uid channel=$channel isRemoteUserJoined=$isRemoteUserJoined elapsed=$elapsed"
            )
            Log.d("AgoraTiming", "MaleVideo onJoinChannelSuccess at ${System.currentTimeMillis()}")
            startTimeoutTracking()
        }



        override fun onUserOffline(uid: Int, reason: Int) {
          //  showMessage("Remote user offline $uid $reason")
            Log.d(
                TAG_END,
                "onUserOffline uid=$uid reason=$reason isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined"
            )
            stopCountdown()
            updateCallEndDetails()
            runOnUiThread {
                remoteSurfaceView?.let { // ✅ Safe check before accessing
                    it.visibility = View.GONE
                }
            }

            Log.d(TAG_END, "onUserOffline -> startActivity(MainActivity) then finish()")
            val intent = Intent(this@MaleVideoCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            Log.d(TAG_END, "finish() from onUserOffline")
            finish()
        }

        override fun onError(err: Int) {
            Log.d(TAG_END, "onError err=$err isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined")
            super.onError(err)
        }

        override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
            com.gmwapp.hima.utils.CallQualityUi.apply(
                this@MaleVideoCallingActivity,
                binding.ivSignalStrength,
                binding.reconnectBanner,
                rxQuality,
                null
            )
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            Log.d(
                TAG_END,
                "onConnectionStateChanged state=$state reason=$reason isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined"
            )
            com.gmwapp.hima.utils.CallQualityUi.apply(
                this@MaleVideoCallingActivity,
                binding.ivSignalStrength,
                binding.reconnectBanner,
                Constants.QUALITY_UNKNOWN,
                state
            )
            super.onConnectionStateChanged(state, reason)
        }

        override fun onConnectionLost() {
            Log.d(TAG_END, "onConnectionLost isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined")
            super.onConnectionLost()
        }

        override fun onLeaveChannel(stats: RtcStats) {
            Log.d(
                TAG_END,
                "onLeaveChannel totalDuration=${stats.totalDuration} txBytes=${stats.txBytes} rxBytes=${stats.rxBytes}"
            )
            super.onLeaveChannel(stats)
        }

        override fun onRejoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.d(TAG_END, "onRejoinChannelSuccess channel=$channel uid=$uid elapsed=$elapsed")
            super.onRejoinChannelSuccess(channel, uid, elapsed)
        }

        override fun onUserMuteVideo(uid: Int, muted: Boolean) {
            super.onUserMuteVideo(uid, muted)


                runOnUiThread {
                    if (muted){
                        showRemoteBlurState()


                    }else{
                        hideRemoteBlurState()
                        
                        // Re-setup remote video to ensure it's properly rendered when switching from audio to video
                        binding.remoteVideoViewContainer.removeAllViews()
                        remoteSurfaceView = SurfaceView(this@MaleVideoCallingActivity)
                        remoteSurfaceView!!.setZOrderMediaOverlay(false)
                        remoteSurfaceView!!.visibility = View.VISIBLE
                        binding.remoteVideoViewContainer.addView(remoteSurfaceView)
                        agoraEngine?.setupRemoteVideo(
                            VideoCanvas(
                                remoteSurfaceView,
                                VideoCanvas.RENDER_MODE_HIDDEN,
                                uid
                            )
                        )
                        binding.remoteVideoViewContainer.bringToFront()

                    }
                }

        }
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
            DConstants.USER_ID,
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        ).putInt(DConstants.CALL_ID, callId)
            .putString(DConstants.STARTED_TIME, startTime)
            .putBoolean(DConstants.IS_INDIVIDUAL, true)
            .putString(DConstants.ENDED_TIME, endTime).build()

        val oneTimeWorkRequest = OneTimeWorkRequest.Builder(
            CallUpdateWorker::class.java
        ).setInputData(data).setConstraints(constraints).build()
        WorkManager.getInstance(this@MaleVideoCallingActivity)
            .enqueue(oneTimeWorkRequest)


        if (switchCallID != 0) {
            callId = switchCallID
            Log.d("callidCheck","$callId")
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
        binding.remoteVideoViewContainer.visibility = View.VISIBLE
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
        
        binding.localVideoViewContainer.visibility = View.VISIBLE
        binding.localCardView.visibility = View.VISIBLE
        applySavedLocalPreviewPosition()

    }

    private fun setupLocalPreviewDrag() {
        binding.localCardView.setOnTouchListener { view, event ->
            val parent = binding.main
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (parent.width == 0 || parent.height == 0) {
                        return@setOnTouchListener false
                    }
                    localPreviewDragStartX = event.rawX
                    localPreviewDragStartY = event.rawY
                    localPreviewTouchOffsetX = event.rawX - view.x
                    localPreviewTouchOffsetY = event.rawY - view.y
                    isDraggingLocalPreview = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val nextX = event.rawX - localPreviewTouchOffsetX
                    val nextY = event.rawY - localPreviewTouchOffsetY
                    val clampedX = clampLocalPreviewX(nextX)
                    val clampedY = clampLocalPreviewY(nextY)

                    view.x = clampedX
                    view.y = clampedY
                    localPreviewOffsetX = clampedX
                    localPreviewOffsetY = clampedY

                    val dragDistance = abs(event.rawX - localPreviewDragStartX) + abs(event.rawY - localPreviewDragStartY)
                    if (dragDistance > 8f) {
                        isDraggingLocalPreview = true
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isDraggingLocalPreview) {
                        view.performClick()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun clampLocalPreviewX(targetX: Float): Float {
        val parent = binding.main
        val minX = parent.paddingLeft.toFloat()
        val maxX = (parent.width - parent.paddingRight - binding.localCardView.width).toFloat()
        return targetX.coerceIn(minX, maxX.coerceAtLeast(minX))
    }

    private fun clampLocalPreviewY(targetY: Float): Float {
        val parent = binding.main
        val minY = parent.paddingTop.toFloat()
        val maxY = (parent.height - parent.paddingBottom - binding.localCardView.height).toFloat()
        return targetY.coerceIn(minY, maxY.coerceAtLeast(minY))
    }

    private fun applySavedLocalPreviewPosition() {
        binding.localCardView.post {
            if (!localPreviewOffsetX.isNaN() && !localPreviewOffsetY.isNaN()) {
                val clampedX = clampLocalPreviewX(localPreviewOffsetX)
                val clampedY = clampLocalPreviewY(localPreviewOffsetY)
                binding.localCardView.x = clampedX
                binding.localCardView.y = clampedY
                localPreviewOffsetX = clampedX
                localPreviewOffsetY = clampedY
            }
        }
    }

    private fun setupLudoInviteFlow() {
        binding.ludoButtonCard.setOnSingleClickListener {
            if (!isRemoteUserJoined) {
                Toast.makeText(this, "Please wait for the call to connect", Toast.LENGTH_SHORT).show()
                return@setOnSingleClickListener
            }
            showLudoInviteConfirmDialog()
        }

        ludoFcmViewModel.ludoFcmResponseLiveData.observe(this) { response ->
            val action = pendingLudoAction ?: return@observe
            pendingLudoAction = null

            if (!response.status) {
                Toast.makeText(this, response.message, Toast.LENGTH_SHORT).show()
                return@observe
            }

            when (action) {
                "invite" -> {
                    currentLudoInviteId = response.data?.invite_id
                    Toast.makeText(this, "Ludo invite sent", Toast.LENGTH_SHORT).show()
                }
                "accept" -> {
                    val joinUrl = response.data?.join_url ?: buildLudoUrl(response.data?.room_code)
                    if (joinUrl.isNullOrBlank()) {
                        Toast.makeText(this, "Invalid Ludo URL", Toast.LENGTH_SHORT).show()
                    } else {
                        openLudoWebView(joinUrl)
                    }
                }
                "reject" -> Toast.makeText(this, "Ludo invite rejected", Toast.LENGTH_SHORT).show()
            }
        }

        ludoFcmViewModel.ludoFcmErrorLiveData.observe(this) {
            pendingLudoAction = null
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        }

        FcmUtils.ludoEvent.observe(this) { event ->
            if (event == null) return@observe

            when (event.type) {
                "ludo_invite" -> {
                    if (event.fromUserId == receiverId) {
                        showIncomingLudoInviteDialog(event)
                    }
                }
                "ludo_invite_accepted" -> {
                    val joinUrl = event.joinUrl ?: buildLudoUrl(event.roomCode)
                    if (!joinUrl.isNullOrBlank()) {
                        openLudoWebView(joinUrl)
                    }
                }
                "ludo_invite_rejected" -> {
                    Toast.makeText(this, "Ludo invite rejected", Toast.LENGTH_SHORT).show()
                }
                "ludo_invite_expired" -> {
                    Toast.makeText(this, "Ludo invite expired", Toast.LENGTH_SHORT).show()
                }
            }
            FcmUtils.clearLudoEvent()
        }
    }

    private fun showLudoInviteConfirmDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_ludo_send_invite, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(true)

        view.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<TextView>(R.id.btn_send_invite).setOnClickListener {
            dialog.dismiss()
            if (maleUserId <= 0 || receiverId <= 0) {
                Toast.makeText(this, "Unable to send invite", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingLudoAction = "invite"
            ludoFcmViewModel.sendLudoFcm(
                action = "invite",
                fromUserId = maleUserId,
                toUserId = receiverId,
                callId = callId.toString()
            )
        }
        dialog.show()
    }

    private fun showIncomingLudoInviteDialog(event: FcmUtils.LudoEvent) {
        val inviteId = event.inviteId ?: return
        val currentUserId = maleUserId
        if (currentUserId <= 0) return

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_ludo_receive_invite, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(false)

        val name = event.fromUserName?.takeIf { it.isNotBlank() } ?: "Someone"
        view.findViewById<TextView>(R.id.tv_invite_message).text =
            "$name wants to play Ludo with you. Accept the challenge!"

        view.findViewById<TextView>(R.id.btn_decline).setOnClickListener {
            dialog.dismiss()
            pendingLudoAction = "reject"
            ludoFcmViewModel.sendLudoFcm(
                action = "reject",
                fromUserId = currentUserId,
                toUserId = event.fromUserId,
                inviteId = inviteId,
                callId = callId.toString()
            )
        }
        view.findViewById<TextView>(R.id.btn_accept).setOnClickListener {
            dialog.dismiss()
            currentLudoInviteId = inviteId
            pendingLudoAction = "accept"
            ludoFcmViewModel.sendLudoFcm(
                action = "accept",
                fromUserId = currentUserId,
                toUserId = event.fromUserId,
                inviteId = inviteId,
                callId = callId.toString()
            )
        }
        dialog.show()
    }

    private fun buildLudoUrl(roomCode: String?): String? {
        return if (roomCode.isNullOrBlank()) null else "https://demohima.himaapp.in/ludogame?room=$roomCode"
    }

    private fun openLudoWebView(url: String) {
        val intent = PaymentWebViewActivity.createLudoIntent(
            context = this,
            url = url,
            fromUserId = maleUserId,
            toUserId = receiverId,
            callId = callId.toString(),
            inviteId = currentLudoInviteId
        )
        startActivity(intent)
    }

    fun joinChannel(view: View) {
        if (checkSelfPermission()) {
            val options = ChannelMediaOptions()

            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            setupLocalVideo()
            localSurfaceView!!.visibility = View.VISIBLE
            agoraEngine!!.startPreview()
            agoraEngine!!.joinChannel(token, channelName, uid, options)
        } else {
            Toast.makeText(applicationContext, "Permissions was not granted", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun leaveChannel(view: View) {
        Log.d(
            TAG_END,
            "leaveChannel() enter isJoined=$isJoined viewId=${view.id} isRemoteUserJoined=$isRemoteUserJoined"
        )
        if (!isJoined) {
            Log.d(TAG_END, "leaveChannel.notJoined path")
            HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
         //   showMessage("Join a channel first")
            val intent = Intent(this@MaleVideoCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            Log.d(TAG_END, "leaveChannel finishing activity (notJoined)")
            Log.d(TAG_END, "finish() from leaveChannel.notJoined")
            finish()
        } else {
            Log.d(TAG_END, "leaveChannel.joined path")
            stopCountdown()
            try {
                agoraEngine?.stopPreview()
            } catch (e: Exception) {
                Log.e("MaleVideoCalling", "stopPreview in leaveChannel", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            try {
                agoraEngine?.leaveChannel()
            } catch (e: Exception) {
                Log.e("MaleVideoCalling", "leaveChannel", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
         //   showMessage("You left the channel")
            if (remoteSurfaceView != null) remoteSurfaceView!!.visibility = View.GONE
            if (localSurfaceView != null) localSurfaceView!!.visibility = View.GONE
            isJoined = false
            HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
            updateCallEndDetails()

            Handler(Looper.getMainLooper()).postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                val intent = Intent(this@MaleVideoCallingActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                Log.d(TAG_END, "leaveChannel finishing activity (joined delayed)")
                Log.d(TAG_END, "finish() from leaveChannel.joinedDelayed")
                finish()
            }, 50L)
        }
    }

    private  fun getRemainingTime(){
        maleUserId?.let { profileViewModel.getRemainingTime(it,"video", object :
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
                if (!isFinishing && !isDestroyed) {   // ✅ prevents crash after activity closed
                    leaveChannel(binding.LeaveButton)
                }
            }
        }.start()
    }

    private fun stopCountdown() {
        countDownTimer?.cancel() // Cancel the countdown timer
        countDownTimer = null
    }

    fun newRemainingTime(){

        if (isAudioCallGoing){

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
                        Log.d("resumedtag","audiocalltime - $newTime")
                        Log.d("resumedtag","audiocalltime - $storedRemainingTime")

                        if (storedRemainingTime != null) {
                            storedRemainingTime = newTime // Update stored value
                            sendUpdatedTimeNotification(maleUserId,receiverId,"audio","remainingTimeUpdated")
                            stopCountdown()
                            startCountdown(newTime)
                        }
                    }
                }
            })}

        }else{
        maleUserId?.let { profileViewModel.getRemainingTime(it, "video", object :
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
                    Log.d("resumedtag","videocalltime - $storedVideoRemainingTime")


                    if (storedVideoRemainingTime != null) {
                        storedVideoRemainingTime = newTime // Update stored value
                        sendUpdatedTimeNotification(maleUserId,receiverId,"video","remainingTimeUpdated")
                        stopCountdown()
                        startCountdown(newTime)
                    }
                }
            }
        })} }
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






    fun sendUpdatedTimeNotification(senderId:Int, receiverId:Int, callType:String, message:String) {
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


    override fun onStart() {
        super.onStart()
        Log.d(
            TAG_END,
            "onStart isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined elapsedTime=$elapsedTime isFinishing=$isFinishing"
        )
    }

    override fun onResume() {
        super.onResume()
        Log.d(
            TAG_END,
            "onResume isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined elapsedTime=$elapsedTime isFinishing=$isFinishing"
        )
        Log.d("resumedtag","resumed")
        newRemainingTime()
        startCallingService()

        if (isJoined && ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showMessage("Microphone permission was revoked. Ending call.")
            agoraEngine?.leaveChannel()
            Log.d(TAG_END, "finish() from onResume.micRevoked")
            finish()
        }
    }

    override fun onPause() {
        Log.d(
            TAG_END,
            "onPause isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined elapsedTime=$elapsedTime isFinishing=$isFinishing"
        )
        super.onPause()
    }

    override fun onStop() {
        Log.d(
            TAG_END,
            "onStop isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined elapsedTime=$elapsedTime isFinishing=$isFinishing"
        )
        super.onStop()
    }
    private fun onAddcoinClicked(){
        binding.timerContainer.setOnSingleClickListener {
            var intent = Intent(this@MaleVideoCallingActivity, WalletActivity::class.java)
            startActivity(intent)
        }
    }
    private fun toggleMute() {
        isMuted = !isMuted
        agoraEngine?.muteLocalAudioStream(isMuted)  // Mute or unmute audio
        val muteIcon = if (isMuted) R.drawable.ic_call_mic_off else R.drawable.ic_call_mic
        binding.btnMuteUnmute.setImageResource(muteIcon)
    }

    // Function to toggle speaker on/off
    private fun toggleSpeaker() {
        Log.d("CallAudioRoute", "Activity.toggleSpeaker isSpeakerOn=$isSpeakerOn -> ${!isSpeakerOn}")
        applyAudioRoute(
            if (isSpeakerOn) com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE
            else com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER
        )
    }

    private fun onSpeakerButtonClicked() {
        val router = audioRouter
        if (router != null && router.isBluetoothConnected()) {
            com.gmwapp.hima.dialogs.BottomSheetAudioRoute.show(
                supportFragmentManager,
                router
            ) { route -> applyAudioRoute(route) }
        } else {
            toggleSpeaker()
        }
    }

    private fun applyAudioRoute(route: com.gmwapp.hima.utils.CallAudioRouter.AudioRoute) {
        isSpeakerOn = route == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER
        currentAudioRoute = route

        // Telecom-first: Samsung's self-managed CallAudioRouteController
        // overrides AudioManager. Route through Connection API first.
        HimaTelecomManager.setAudioRoute(route)

        agoraEngine?.setEnableSpeakerphone(isSpeakerOn)

        when (route) {
            com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE -> audioRouter?.forceEarpiece()
            com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER -> audioRouter?.forceSpeaker()
            com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH -> audioRouter?.forceBluetooth()
        }

        binding.btnSpeaker.setImageResource(iconForRoute(route))

        Log.d(
            "CallAudioRoute",
            "Activity.applyAudioRoute requested=$route actualAfter=${audioRouter?.currentRoute()} " +
                "isSpeakerOn=$isSpeakerOn btConnected=${audioRouter?.isBluetoothConnected()}"
        )
    }

    private fun iconForRoute(route: com.gmwapp.hima.utils.CallAudioRouter.AudioRoute): Int = when (route) {
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER -> R.drawable.ic_call_speaker_on
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH -> R.drawable.ic_bluetooth_audio
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE -> R.drawable.ic_call_speaker_off
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("KEY_IS_MUTED", isMuted)
        outState.putBoolean("KEY_IS_SPEAKER_ON", isSpeakerOn)
        outState.putString("KEY_AUDIO_ROUTE", currentAudioRoute.name)
        Log.d("CallAudioRoute", "Activity.saveState route=$currentAudioRoute isSpeakerOn=$isSpeakerOn")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        isMuted = savedInstanceState.getBoolean("KEY_IS_MUTED", false)
        val restoredSpeakerOn = savedInstanceState.getBoolean("KEY_IS_SPEAKER_ON", false)
        val restoredRoute = savedInstanceState.getString("KEY_AUDIO_ROUTE")?.let {
            runCatching { com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.valueOf(it) }.getOrNull()
        } ?: if (restoredSpeakerOn) com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER
            else com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE
        Log.d(
            "CallAudioRoute",
            "Activity.restoreState restoredRoute=$restoredRoute restoredSpeaker=$restoredSpeakerOn"
        )
        agoraEngine?.muteLocalAudioStream(isMuted)
        binding.btnMuteUnmute.setImageResource(
            if (isMuted) R.drawable.ic_call_mic_off else R.drawable.ic_call_mic
        )
        applyAudioRoute(restoredRoute)
    }

    private fun endcallBtn() {
        binding.btnEndCall.setOnSingleClickListener {
            showEndCallConfirmationDialog()
        }
    }
    
    private fun showEndCallConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_end_call_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel_end_call)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm_end_call)
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            if (maleUserId > 0 && receiverId > 0 && callId > 0) {
                // Fire-and-forget; call teardown should not be blocked by network.
                callDropStatusViewModel.saveCallDropStatus(
                    userId = maleUserId,
                    receivedUserId = receiverId,
                    callId = callId,
                    callDropStatus = 1
                )
                val endedByRole = if (isCaller) CallEndedBy.CALLER else CallEndedBy.RECEIVER
                Log.d("CallStatus", "MaleVideo.hangup → ended/$endedByRole self=$maleUserId peer=$receiverId callId=$callId isCaller=$isCaller")
                callStatusViewModel.saveCallStatus(
                    userId = maleUserId,
                    receivedUserId = receiverId,
                    callId = callId,
                    endReason = CallEndReason.ENDED,
                    endedBy = endedByRole,
                    endedByUserId = maleUserId,
                )
            } else {
                Log.w(
                    "CallDropStatusAPI",
                    "Skip call_drop_status: userId=$maleUserId receiverId=$receiverId callId=$callId"
                )
            }
            leaveChannel(binding.LeaveButton)
        }
        
        dialog.show()
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
            if (isSwitchRequestPending == false) {
                // Use the call-mode flag instead of comparing drawable.constantState
                // (vector drawables don't share constantState across getDrawable()
                // calls, which used to land here as "Unknown state").
                if (isAudioCallGoing == true) {
                    switchToVideo()
                } else {
                    switchToAudio()
                }
            } else {
                Toast.makeText(this, "Already Request Sent", Toast.LENGTH_SHORT).show()
            }
        }


    }
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

    fun getCallIdforCallSwitch(callType: String) {

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        var userId = userData?.id
        receiverId?.let { it1 ->
            userId?.let {
                femaleUsersViewModel.callFemaleUser(
                    it, it1, callType,1
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
                Log.d(
                    TAG_END,
                    "switchAcceptance observed: switchType=$switchType receiverId=$receiverId this.receiverId=${this.receiverId}"
                )

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

    fun observeCallSwitchRequest() {
        FcmUtils.updatedCallSwitch.observe(this, androidx.lifecycle.Observer { updatedCallSwitch ->
            if (updatedCallSwitch != null) {
                val (switchType, newCallId) = updatedCallSwitch
                Log.d(
                    TAG_END,
                    "switchRequest observed: switchType=$switchType newCallId=$newCallId this.receiverId=$receiverId"
                )

                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                var userid = userData?.id

                if (switchType == "switchToVideo") {
                    if (isAudioCallGoing){
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
                    if (isAudioCallGoing==false){
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

                }}


                FcmUtils.clearCallSwitch()


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
        // Re-show parent container that we hid when switching to video.
        binding.usersContainer.visibility = View.VISIBLE


        runOnUiThread {
            // Stop publishing and capturing camera so bandwidth + camera LED
            // turn off when the user goes to audio mode. Mirror audio-only
            // ChannelMediaOptions so Agora suppresses the camera track.
            agoraEngine?.muteLocalVideoStream(true)
            agoraEngine?.enableLocalVideo(false)
            agoraEngine?.updateChannelMediaOptions(ChannelMediaOptions().apply {
                autoSubscribeAudio = true
                autoSubscribeVideo = false
                publishMicrophoneTrack = true
                publishCameraTrack = false
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            })
            agoraEngine?.stopPreview()
            agoraEngine?.disableVideo()
            Log.d("AgoraTiming", "MaleVideo switched to AUDIO at ${System.currentTimeMillis()}")

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
            binding.btnVideoCall.setImageResource(R.drawable.ic_call_video)

            startTime =
                dateFormat.format(Date()) // Set call end time only if startTime is not empty


        }

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
        // Hide parent avatars container — individual ImageViews being GONE
        // still left a residual rounded shape on the left side after switch.
        binding.usersContainer.visibility = View.GONE


        runOnUiThread {
            // Clear any existing views first
            binding.localVideoViewContainer.removeAllViews()
            binding.remoteVideoViewContainer.removeAllViews()
            
            // Remove background to show video
            binding.main.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // Enable video module
            agoraEngine?.enableVideo()
            
            // Enable local video and start camera
            agoraEngine?.enableLocalVideo(true)
            agoraEngine?.muteLocalVideoStream(false)

            // Set up the local video view
            val localView = SurfaceView(this)
            localView.setZOrderMediaOverlay(true)
            localView.visibility = View.VISIBLE
            binding.localVideoViewContainer.addView(localView)

            // Attach local video feed
            agoraEngine?.setupLocalVideo(VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
            
            // Start local video preview
            agoraEngine?.startPreview()

            // Make video UI visible
            binding.localVideoViewContainer.visibility = View.VISIBLE
            binding.localCardView.visibility = View.VISIBLE
            binding.remoteVideoViewContainer.visibility = View.VISIBLE
            applySavedLocalPreviewPosition()
            
            // Bring video containers to front
            binding.localVideoViewContainer.bringToFront()
            binding.remoteVideoViewContainer.bringToFront()
            binding.localCardView.bringToFront()
            
            Log.d("enableVideoCall", "Local video setup complete from video activity")

            // Setup remote video view - will be properly rendered when remote stream is available
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
            
            Log.d("enableVideoCall", "Video enabled from video call activity for videoUid: $videoUid")
            
            // Retry remote video setup after a delay to ensure remote stream is ready
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d("enableVideoCall", "Retrying remote video setup after delay")
                binding.remoteVideoViewContainer.removeAllViews()
                remoteSurfaceView = SurfaceView(this)
                remoteSurfaceView!!.setZOrderMediaOverlay(false)
                remoteSurfaceView!!.visibility = View.VISIBLE
                binding.remoteVideoViewContainer.addView(remoteSurfaceView)
                agoraEngine?.setupRemoteVideo(
                    VideoCanvas(
                        remoteSurfaceView,
                        VideoCanvas.RENDER_MODE_HIDDEN,
                        videoUid
                    )
                )
                binding.remoteVideoViewContainer.visibility = View.VISIBLE
                binding.remoteVideoViewContainer.bringToFront()
                Log.d("enableVideoCall", "Remote video setup retry completed")
            }, 1500)
            remoteSurfaceView!!.visibility = View.VISIBLE

            startTime =
                dateFormat.format(Date()) // Set call end time only if startTime is not empty

            binding.btnVideoCall.setImageResource(R.drawable.ic_call_audio)



        }
    }

//    private fun startFaceDetectionCamera() {
//        Log.d("FaceDetection", "startFaceDetectionCamera() called")
//
//        val options = FaceDetectorOptions.Builder()
//            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
//            .build()
//
//        faceDetector = FaceDetection.getClient(options)
//
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this@MaleVideoCallingActivity)
//        cameraProviderFuture.addListener({
//            cameraProvider = cameraProviderFuture.get()
//
//            val preview = Preview.Builder().build() // Do not set surface provider
//
//
//
//            analysisUseCase = ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build()
//
//            analysisUseCase?.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
//                processImageProxy(imageProxy)
//            }
//
//            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
//
//            try {
//                cameraProvider?.unbindAll()
//                camera = cameraProvider?.bindToLifecycle(this, cameraSelector, analysisUseCase)
//            } catch (e: Exception) {
//                Log.e("CameraX", "Binding failed", e)
//            }
//        }, ContextCompat.getMainExecutor(this))
//    }
//
//    @androidx.annotation.OptIn(ExperimentalGetImage::class)
//    private fun processImageProxy(imageProxy: ImageProxy) {
//        val mediaImage = imageProxy.image ?: run {
//            imageProxy.close()
//            return
//        }
//
//        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
//
//        Log.d("FaceDetection", "Processing frame...")
//
//        faceDetector?.process(image)
//            ?.addOnSuccessListener { faces ->
//                Log.d("FaceDetection", "Faces detected: ${faces.size}")
//
//                if (faces.isEmpty()) {
//                    showToastOnce("Please show your face")
//                }else{
//                    showToastOnce("face detected")
//
//                }
//                imageProxy.close()
//            }
//            ?.addOnFailureListener {
//                imageProxy.close()
//            }
//    }
//
//    private fun showToastOnce(msg: String) {
//        val now = System.currentTimeMillis()
//        if (now - lastFaceMissingTime > 3000) { // 3 seconds gap
//            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
//            lastFaceMissingTime = now
//        }
//    }

    fun disableVideo(){
        Handler(Looper.getMainLooper()).post {
            if (isFinishing || isDestroyed) {
                return@post
            }

            cancelFacePreviewTransition()
            isShowingFacePreview = false
            
            // Keep local blackscreen hidden; face overlay now handles the full-screen UX.
            binding.blackscreen.visibility=View.GONE
            agoraEngine?.muteLocalVideoStream(true)

            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            val senderId = userData?.id
            if (senderId != null) {
                fcmNotificationViewModel.sendNotification(
                    senderId = senderId,
                    receiverId = receiverId,
                    callType = "calltype",
                    channelName = channelName,
                    message = "greyScreenEnable"
                )
            }

            showNoFaceDetectedDialog()
        }
    }

    fun enableVideo(){
        Handler(Looper.getMainLooper()).post {
            if (isFinishing || isDestroyed) {
                return@post
            }

            val overlayVisible = binding.faceDetectionOverlay.root.visibility == View.VISIBLE
            if (!overlayVisible) {
                Log.d("FaceDetection", "Male: overlay not active, skipping staged preview")
                return@post
            }

            if (isShowingFacePreview) {
                return@post
            }

            cancelFacePreviewTransition()
            isShowingFacePreview = true
            Log.d("FaceDetection", "Male: face detected, resuming call immediately")
            resumeCallAfterFaceDetection()
        }
    }

    private fun showCameraPreviewOnOverlay() {
        try {
            val overlayBinding = binding.faceDetectionOverlay
            val overlay = overlayBinding.root

            overlay.visibility = View.VISIBLE
            overlay.bringToFront()
            overlay.elevation = 9999f
            overlay.translationZ = 9999f

            overlay.setBackgroundColor(android.graphics.Color.BLACK)
            overlayBinding.personOutlineContainer.visibility = View.GONE
            overlayBinding.bottomFacePanel.visibility = View.GONE
            overlayBinding.scanIconHolder.visibility = View.GONE
            overlayBinding.tvFaceNotDetected.text = "Face Detected"
            overlayBinding.tvFaceNotDetected.visibility = View.VISIBLE

            val cameraContainer = overlayBinding.cameraPreviewContainer
            cameraContainer.removeAllViews()
            cameraContainer.setBackgroundColor(android.graphics.Color.BLACK)

            localPreviewSurface = SurfaceView(this)
            localPreviewSurface?.setZOrderOnTop(false)
            localPreviewSurface?.setZOrderMediaOverlay(false)
            localPreviewSurface?.holder?.setFormat(PixelFormat.TRANSLUCENT)

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            cameraContainer.addView(localPreviewSurface, params)
            cameraContainer.visibility = View.VISIBLE
            cameraContainer.bringToFront()

            agoraEngine?.setupLocalVideo(
                VideoCanvas(localPreviewSurface, VideoCanvas.RENDER_MODE_HIDDEN, 0)
            )
            agoraEngine?.startPreview()
        } catch (e: Exception) {
            Log.e("MaleVideoCallingActivity", "Error showing face preview on overlay", e)
            isShowingFacePreview = false
        }
    }

    private fun resumeCallAfterFaceDetection() {
        if (isFinishing || isDestroyed) return

        try {
            binding.blackscreen.visibility = View.GONE
            agoraEngine?.muteAllRemoteAudioStreams(false)
            agoraEngine?.muteLocalVideoStream(false)
            agoraEngine?.muteLocalAudioStream(false)

            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            val senderId = userData?.id
            if (senderId != null) {
                fcmNotificationViewModel.sendNotification(
                    senderId = senderId,
                    receiverId = receiverId,
                    callType = "calltype",
                    channelName = channelName,
                    message = "greyScreenDisable"
                )
            }

            dismissNoFaceDetectedDialog()

            setupLocalVideoInCallView()
        } catch (e: Exception) {
            Log.e("MaleVideoCallingActivity", "Error resuming call after face detection", e)
        }
    }

    private fun setupLocalVideoInCallView() {
        try {
            localPreviewSurface = null
            binding.faceDetectionOverlay.cameraPreviewContainer.removeAllViews()

            binding.localVideoViewContainer.removeAllViews()
            val localView = SurfaceView(this)
            localView.setZOrderMediaOverlay(true)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            binding.localVideoViewContainer.addView(localView, params)

            agoraEngine?.setupLocalVideo(
                VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
            )

            binding.localVideoViewContainer.visibility = View.VISIBLE
            binding.localCardView.visibility = View.VISIBLE
            applySavedLocalPreviewPosition()
        } catch (e: Exception) {
            Log.e("MaleVideoCallingActivity", "Error setting local video in call view", e)
        }
    }


    private fun showNoFaceDetectedDialog() {
        // Show full-screen overlay instead of dialog
        Handler(Looper.getMainLooper()).post {
            // Check if activity is still valid before showing overlay
            if (isFinishing || isDestroyed) {
                return@post
            }
            
            try {
                val overlayBinding = binding.faceDetectionOverlay
                val overlay = overlayBinding.root
                overlay.bringToFront()
                overlay.elevation = 1000f
                overlay.translationZ = 1000f
                overlay.visibility = View.VISIBLE
                binding.localCardView.visibility = View.GONE
                binding.localVideoViewContainer.visibility = View.GONE

                // Always restore no-face UI state on every trigger.
                overlay.setBackgroundColor(android.graphics.Color.parseColor("#66000000"))
                overlayBinding.personOutlineContainer.visibility = View.VISIBLE
                overlayBinding.bottomFacePanel.visibility = View.VISIBLE
                overlayBinding.scanIconHolder.visibility = View.VISIBLE
                overlayBinding.tvFaceNotDetected.text = "Face Not Detected"
                overlayBinding.tvFaceNotDetected.visibility = View.VISIBLE
                overlayBinding.tvFaceNotDetected.bringToFront()
                overlayBinding.personOutlineContainer.bringToFront()
                overlayBinding.bottomFacePanel.bringToFront()
                overlayBinding.scanIconHolder.bringToFront()

                // Face-detection callbacks can fire repeatedly; avoid rebuilding preview each time.
                if (overlayBinding.cameraPreviewContainer.childCount > 0 &&
                    overlayBinding.cameraPreviewContainer.visibility == View.VISIBLE
                ) {
                    return@post
                }

                val cameraContainer = overlayBinding.cameraPreviewContainer
                cameraContainer.removeAllViews()
                cameraContainer.visibility = View.VISIBLE
                cameraContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                localPreviewSurface = SurfaceView(this@MaleVideoCallingActivity).apply {
                    setZOrderOnTop(false)
                    setZOrderMediaOverlay(false)
                    holder?.setFormat(PixelFormat.TRANSLUCENT)
                }

                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                cameraContainer.addView(localPreviewSurface, params)

                agoraEngine?.setupLocalVideo(
                    VideoCanvas(localPreviewSurface, VideoCanvas.RENDER_MODE_HIDDEN, 0)
                )
                agoraEngine?.startPreview()
            } catch (e: Exception) {
                Log.e("MaleVideoCallingActivity", "Cannot show face detection overlay", e)
            }
        }
    }

    private fun dismissNoFaceDetectedDialog() {
        Handler(Looper.getMainLooper()).post {
            if (isFinishing || isDestroyed) {
                return@post
            }
            
            try {
                isShowingFacePreview = false
                cancelFacePreviewTransition()

                val overlayBinding = binding.faceDetectionOverlay
                overlayBinding.cameraPreviewContainer.removeAllViews()
                overlayBinding.cameraPreviewContainer.visibility = View.GONE
                overlayBinding.root.setBackgroundResource(R.drawable.face_detection_gradient_background)
                overlayBinding.personOutlineContainer.visibility = View.VISIBLE
                overlayBinding.bottomFacePanel.visibility = View.VISIBLE
                overlayBinding.scanIconHolder.visibility = View.VISIBLE
                overlayBinding.tvFaceNotDetected.text = "Face Not Detected"
                overlayBinding.root.visibility = View.GONE
                binding.localVideoViewContainer.visibility = View.VISIBLE
                binding.localCardView.visibility = View.VISIBLE
                
                // Process any deferred remote unblur
                if (pendingRemoteBlurHide) {
                    hideRemoteBlurState()
                    pendingRemoteBlurHide = false
                } else {
                    // Restore remote visibility only if no blur state is active
                    if (!isRemoteBlurVisible) {
                        remoteSurfaceView?.visibility = View.VISIBLE
                        binding.remoteVideoViewContainer.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e("MaleVideoCallingActivity", "Error dismissing overlay", e)
            }
        }
    }

    private fun showRemoteBlurState() {
        if (isRemoteBlurVisible) return
        isRemoteBlurVisible = true
        binding.main.setBackgroundResource(R.drawable.call_blur_placeholder_background)
        val overlay = binding.remoteBlurOverlay.root
        val card = binding.remoteBlurOverlay.blurMessageCard
        overlay.bringToFront()
        overlay.animate().cancel()
        card.animate().cancel()
        if (overlay.visibility != View.VISIBLE) {
            overlay.alpha = 0f
            overlay.visibility = View.VISIBLE
        }
        overlay.animate().alpha(1f).setDuration(220).start()
        card.scaleX = 0.96f
        card.scaleY = 0.96f
        card.alpha = 0f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
        remoteSurfaceView?.visibility = View.GONE
        binding.remoteVideoViewContainer.visibility = View.GONE
    }

    private fun hideRemoteBlurState() {
        if (!isRemoteBlurVisible) return
        isRemoteBlurVisible = false
        binding.main.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val overlay = binding.remoteBlurOverlay.root
        val card = binding.remoteBlurOverlay.blurMessageCard
        overlay.animate().cancel()
        card.animate().cancel()
        if (overlay.visibility == View.VISIBLE) {
            card.animate().alpha(0f).scaleX(0.98f).scaleY(0.98f).setDuration(120).start()
            overlay.animate().alpha(0f).setDuration(160).withEndAction {
                overlay.visibility = View.GONE
                overlay.alpha = 1f
            }.start()
        }
        if (remoteSurfaceView == null && videoUid != 0) {
            setupRemoteVideo(videoUid)
        } else {
            remoteSurfaceView?.visibility = View.VISIBLE
            if (remoteSurfaceView?.parent == null) {
                binding.remoteVideoViewContainer.removeAllViews()
                remoteSurfaceView?.let { binding.remoteVideoViewContainer.addView(it) }
                if (videoUid != 0) {
                    agoraEngine?.setupRemoteVideo(
                        VideoCanvas(
                            remoteSurfaceView,
                            VideoCanvas.RENDER_MODE_HIDDEN,
                            videoUid
                        )
                    )
                }
            }
        }
        binding.remoteVideoViewContainer.visibility = View.VISIBLE
        binding.remoteVideoViewContainer.bringToFront()
    }


    fun showGreyScreen(){

        FcmUtils.greyScreenLiveData.observe(this) { msg ->
            if (msg=="greyScreenEnable"){
                showRemoteBlurState()

            }
            if (msg=="greyScreenDisable"){
                val isLocalNoFaceOverlayVisible =
                    binding.faceDetectionOverlay.root.visibility == View.VISIBLE
                if (isLocalNoFaceOverlayVisible) {
                    // Defer remote unblur until local overlay is dismissed
                    pendingRemoteBlurHide = true
                    remoteSurfaceView?.visibility = View.GONE
                    binding.remoteVideoViewContainer.visibility = View.GONE
                } else {
                    hideRemoteBlurState()
                    pendingRemoteBlurHide = false
                }
            }
        }
    }



}



