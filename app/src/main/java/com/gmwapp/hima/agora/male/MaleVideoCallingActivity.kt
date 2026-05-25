package com.gmwapp.hima.agora.male

import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioManager
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
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Rational
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
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
import com.gmwapp.hima.agora.GiftBottomSheetFragment
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
        private const val TIMER_RESYNC_INTERVAL_MS = 30_000L
    }

    lateinit var binding: ActivityMaleVideoCallingBinding
    var receiverId = 0

    // Periodic re-fetch of remaining_time so drift between the two clients
    // can't accumulate past 30 s. See MaleAudioCallingActivity for full
    // rationale.
    private val timerResyncHandler = Handler(Looper.getMainLooper())
    private val timerResyncRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed && isJoined) {
                newRemainingTime()
                timerResyncHandler.postDelayed(this, TIMER_RESYNC_INTERVAL_MS)
            }
        }
    }
    private fun startTimerResync() {
        timerResyncHandler.removeCallbacks(timerResyncRunnable)
        timerResyncHandler.postDelayed(timerResyncRunnable, TIMER_RESYNC_INTERVAL_MS)
    }
    private fun stopTimerResync() {
        timerResyncHandler.removeCallbacks(timerResyncRunnable)
    }


    private var isMuted = false
    private var isSpeakerOn = true

    private var audioFocusHelper: CallAudioFocusHelper? = null
    private var audioRouter: CallAudioRouter? = null
    private var phoneStateHelper: CallPhoneStateHelper? = null
    private var btWatcher: com.gmwapp.hima.utils.BluetoothCallWatcher? = null
    // B062 + B064 — auto-end after 30s + show countdown on banner.
    // See MaleAudioCallingActivity for full rationale.
    private val reconnectWatchdog = com.gmwapp.hima.utils.ReconnectWatchdog(
        onTick = { secondsRemaining ->
            binding.reconnectBanner.text = "Reconnecting… ${secondsRemaining}s"
        },
        onTimeout = {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Network lost. Call ended.",
                    Toast.LENGTH_LONG
                ).show()
                leaveChannel(binding.LeaveButton)
            }
        }
    )
    private var mutedByInterrupt = false
    var isClicked : Boolean = false

    // 2026-05-23 v1065 — debounced peer-avatar overlay for FROZEN/FAILED.
    private val mainHandlerForAvatar = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingAvatarShow: Runnable? = null

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


    private val uid = 0
    private var isJoined = false
//    private var mRtmClient: RtmClient? = null

    private var agoraEngine: RtcEngine? = null

    // B127: real-time RECORD_AUDIO revoke listener; started on join, stopped on teardown.
    private var micWatcher: com.gmwapp.hima.utils.MicPermissionWatcher? = null
    // B176: tracks whether we muted local video for background/lock so onResume can restore it.
    private var videoMutedForBackground = false

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
    // B110: monotonic millis snapshot taken at onUserJoined so the hangup
    // path can compute an accurate durationSeconds for saveCallStatus.
    // Without this, durationSeconds defaulted to null on the backend, the
    // call was recorded with duration=0, and the male's Recent tab classified
    // his own outgoing call as "Missed."
    private var callStartMillis: Long = 0L
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
    // I021 — VM for the low-balance banner's "first 3 packages" prefetch.
    private val walletViewModel: com.gmwapp.hima.viewmodels.WalletViewModel by viewModels()

    // I021 — banner instance + one-shot flag flipped before hand-off to
    // WalletActivity so onResume can refresh the timer and hide the banner.
    private var lowBalanceBanner: com.gmwapp.hima.utils.LowBalanceBanner? = null
    private var pendingWalletReturn: Boolean = false

    // Tester report: creator's broken camera triggers a 30s grace flow. We
    // mirror the banner here so the caller sees the reason; latched so the
    // disconnect handler shows the right dialog.
    private var cameraUnavailableNotice: com.gmwapp.hima.utils.CameraUnavailableNotice? = null
    private var cameraUnavailableLatched: Boolean = false

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

            if (elapsedTime >= 20) { // B042: bumped 10 → 20 seconds. Slow networks
                // / OEM-throttled FCM regularly take 12-15 s for the peer to actually
                // join Agora after accepting; the old 10 s window false-fired
                // "User did not join" before the connection finished establishing.
                if (isRemoteUserJoined==false){
                    Log.d(TAG_END, "timeout fired -> leaveChannel (remote never joined)")
                    Log.d("isUserJoinedTimer","Leave Button")
                    // B043/B044 — see MaleAudioCallingActivity for the rationale
                    // on dropping the user-blaming wording.
                    Toast.makeText(this@MaleVideoCallingActivity,"Couldn't connect — please try again", Toast.LENGTH_LONG).show()

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
        // Grab EXCLUSIVE audio focus BEFORE Agora touches the audio HAL so
        // Spotify / YouTube / etc. pause before call audio starts (B139).
        // Idempotent — safe even though setupCallInterruptHandlers below
        // calls it again as part of engine wiring.
        setupCallInterruptHandlers()
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId!!
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)
            // Enable video and audio modules
            agoraEngine!!.enableVideo()
            agoraEngine!!.enableAudio()
            // Configure audio profile BEFORE joinChannel to avoid mid-session track reset.
            // B186: SPEECH_STANDARD pinned codec to 32 kHz mono / 18 kbps;
            // on OEMs whose mic captured outside that profile, codec negotiation
            // failed and both sides connected silent. DEFAULT lets Agora pick per
            // the channel profile (COMMUNICATION here).
            agoraEngine!!.setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT, Constants.AUDIO_SCENARIO_DEFAULT)
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
                // B196 — second arg flips the on-hold banner visible/hidden.
                onCellularCallActive = { muteForInterrupt(true, showOnHoldBanner = true) },
                onCellularCallEnded = { muteForInterrupt(false, showOnHoldBanner = true) }
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

    /**
     * @param showOnHoldBanner B196 — flips the on-hold banner visible/hidden
     *   when the cellular phone-state path triggers a mute/unmute.
     */
    private fun muteForInterrupt(muted: Boolean, showOnHoldBanner: Boolean = false) {
        runOnUiThread {
            if (muted) {
                if (!mutedByInterrupt) {
                    mutedByInterrupt = true
                    if (!isMuted) agoraEngine?.muteLocalAudioStream(true)
                    // B148: stop PLAYING the remote audio locally — Spotify (resumed mid-call)
                    // mixes with the caller's voice out of the same speaker otherwise.
                    // B001: also mute remote video so we stop pulling bandwidth during the interrupt.
                    agoraEngine?.muteAllRemoteAudioStreams(true)
                    agoraEngine?.muteAllRemoteVideoStreams(true)
                }
                if (showOnHoldBanner) {
                    runCatching { binding.onHoldBanner.visibility = View.VISIBLE }
                }
            } else {
                if (mutedByInterrupt) {
                    mutedByInterrupt = false
                    if (!isMuted) agoraEngine?.muteLocalAudioStream(false)
                    agoraEngine?.muteAllRemoteAudioStreams(false)
                    agoraEngine?.muteAllRemoteVideoStreams(false)
                }
                if (showOnHoldBanner) {
                    runCatching { binding.onHoldBanner.visibility = View.GONE }
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Route the volume rocker to the in-call voice stream so volume up/down
        // adjusts call audio while the call screen is up (B149). Default is
        // STREAM_MUSIC, which has no effect on Agora's call audio.
        volumeControlStream = AudioManager.STREAM_VOICE_CALL
        // Grab EXCLUSIVE audio focus FIRST — before Agora setup / joinChannel —
        // so background media (Spotify, YouTube, etc.) pauses immediately and
        // doesn't mix with call audio during the engine-init window (B139).
        if (audioFocusHelper == null) {
            audioFocusHelper = CallAudioFocusHelper(
                context = this,
                onFocusLost = { muteForInterrupt(true) },
                onFocusGained = { muteForInterrupt(false) }
            ).also { it.request() }
        }
        BaseApplication.getInstance()?.markCallActive()
        BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
        enableEdgeToEdge()
        binding = ActivityMaleVideoCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // B042: show "Connecting..." instead of stuck 00:00:00 while we wait
        // for the peer to join the Agora channel. startCountdown() overwrites
        // this on its first tick once onUserJoined() fires.
        binding.tvRemainingTime?.text = "Connecting..."
        // B043 + B058: keep the container visible so the persistent peer-avatar
        // skeleton sibling (iv_remote_avatar_skeleton) is visible underneath.
        // The container itself is transparent — the skeleton handles all the
        // "no remote video frames" states (initial connect, FAILED/FROZEN
        // reattach, mid-call switch, mute→unmute window), and the SurfaceView
        // (added later) draws opaquely on top when frames are actually rendering.
        binding.remoteVideoViewContainer.visibility = View.VISIBLE

        // Keep the call screen visible across lockscreen so users who lock
        // the phone mid-call can resume immediately.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // ✅ Restrict screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
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

        // I021 — see MaleAudioCallingActivity for rationale. Banner is wired
        // here; package prefetch is deferred to onUserJoined.
        lowBalanceBanner = com.gmwapp.hima.utils.LowBalanceBanner(
            activity = this,
            rootView = findViewById(R.id.low_balance_banner_root),
            chipContainer = findViewById(R.id.chip_container),
            goToWalletTextView = findViewById(R.id.tv_go_to_wallet),
            walletViewModel = walletViewModel,
            userId = maleUserId,
            onLaunchedWallet = { pendingWalletReturn = true }
        )

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
        // B151: debounce mute + speaker so rapid taps can't desync the icon
        // from Agora's mute / AudioManager comm-device state.
        binding.btnMuteUnmute.setOnSingleClickListener {
            toggleMute()
        }

        binding.btnSpeaker.setOnSingleClickListener {
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
        observeCameraUnavailable()

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
        binding.giftButtonCard.setOnClickListener {
            val bottomSheet = GiftBottomSheetFragment("video", receiverId)
            bottomSheet.show(supportFragmentManager, "BottomSheetGift")
        }
    }

    fun animateGift(image: String) {
        val giftImage = binding.ivGiftImage
        // B071 — cancel any in-flight gift animation so its `withEndAction`
        // chain doesn't leave the view in a stuck state when a new gift
        // arrives before the previous one finished animating.
        giftImage.animate().cancel()
        giftImage.alpha = 1f
        giftImage.visibility = View.VISIBLE
        // B203 — `iv_gift_image` is declared in XML BEFORE blackscreen /
        // remoteBlurOverlay / faceDetectionOverlay, so any of those being
        // visible would render over the gift. bringToFront re-orders the
        // view in its parent's draw list, and a high elevation handles
        // API ≥21 z-ordering for siblings that also use elevation.
        giftImage.bringToFront()
        giftImage.elevation = 32f
        (giftImage.parent as? View)?.requestLayout()

        BaseApplication.getInstance()?.playSendGiftSound()
        com.bumptech.glide.Glide.with(this)
            .load(image)
            .into(giftImage)

        giftImage.post {
            val startX = giftImage.x
            val startY = giftImage.y

            val remoteContainer = binding.remoteVideoViewContainer
            val giftLocation = IntArray(2)
            val remoteLocation = IntArray(2)
            giftImage.getLocationOnScreen(giftLocation)
            remoteContainer.getLocationOnScreen(remoteLocation)

            val targetX = giftImage.x + (remoteLocation[0] - giftLocation[0]) + (remoteContainer.width / 2f - giftImage.width / 2f)
            val targetY = giftImage.y + (remoteLocation[1] - giftLocation[1]) + (remoteContainer.height / 2f - giftImage.height / 2f)

            giftImage.animate()
                .x(targetX)
                .y(targetY)
                .setDuration(2000)
                .withEndAction {
                    giftImage.animate()
                        .alpha(0f)
                        .setDuration(1000)
                        .withEndAction {
                            giftImage.visibility = View.INVISIBLE
                            giftImage.x = startX
                            giftImage.y = startY
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
        binding.tvMaleName.setText(com.gmwapp.hima.utils.DisplayName.clean(name))
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

                binding.tvFemaleName.setText(com.gmwapp.hima.utils.DisplayName.clean(response.data?.name))

                // B043 + B058: load the caller's avatar into the persistent
                // skeleton ImageView that sits BEHIND remote_video_view_container.
                // Because it's a sibling (not a child of the container), no
                // removeAllViews() can destroy it — so during initial connect,
                // FAILED/FROZEN reattach, mid-call audio↔video switch, and the
                // brief mute→unmute window, the user sees the peer avatar
                // instead of a blank screen.
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this).load(imageUrl).into(binding.ivRemoteAvatarSkeleton)
                }
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

    // I022 — wired headset hook / BT AVRCP play-pause = single-press end on
    // the active-call screen, matching native phone / WhatsApp parity.
    // MEDIA_PLAY_PAUSE covers BT headsets that map the button to the media
    // key instead of HEADSETHOOK. Bypasses the confirmation dialog so the
    // user can end with the phone in their pocket — the visible End button
    // still routes through the dialog.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            leaveChannel(binding.LeaveButton)
            return true
        }
        return super.onKeyDown(keyCode, event)
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
            val allGranted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                setupVideoSDKEngine()
                joinChannel(binding.JoinButton) // Automatically join the channel
            } else {
                // B173 — denying once = exit. Re-calling requestPermissions
                // after a denial used to silently spin: Android 11+ enters
                // "permanently denied" after the second decline and starts
                // returning DENIED without a dialog, so the activity sat on
                // a non-functional video call screen indefinitely. Show a
                // brief explanation and finish so the user lands back where
                // they came from.
                Toast.makeText(
                    this,
                    "Camera and microphone access are required for a video call. " +
                        "Enable them in Settings to try again.",
                    Toast.LENGTH_LONG
                ).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        Log.d(TAG_END, "finish() from permission-denied path")
                        finish()
                    }
                }, 1500)
            }
        }
    }


    fun startCallingService() {

        Log.d("startCallingService","Service Function call")
        if (CallingService.isRunning) return

        Log.d("startCallingService","Service not returned")

        // B137 — STARTED state is the earliest legal point to start a
        // foreground service on Android 14/15. Previously we waited until
        // RESUMED which delayed the session-in-progress notification.
        val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)


        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        Log.d("startCallingService","$visible,  $micGranted")


        if (visible && micGranted) {
            // B033 — tell CallingService which class to deep-link back to.
            CallingService.callerActivityClassName = this::class.java.name
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

    /**
     * B061 — when the user presses Home during an active video call, slide
     * the call into Picture-in-Picture instead of just backgrounding the
     * activity. Matches WhatsApp / Google Meet / Telegram behaviour.
     *
     * Skipped when the call hasn't fully connected yet (no remote video to
     * show in PIP) or when we're in the middle of finishing — in both cases
     * letting the activity background normally is the right move.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        tryEnterPip()
    }

    private fun tryEnterPip() {
        if (isFinishing || isDestroyed) return
        if (!isRemoteUserJoined) return
        try {
            val aspect = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspect)
                .build()
            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Log.w("PipMode", "enterPictureInPictureMode failed: ${e.message}")
        }
    }

    /**
     * Toggle chrome: in PIP we want only the remote video; on return to
     * fullscreen we restore all call controls.
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val chromeVisibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        // Hide everything that isn't the remote video. View IDs come straight
        // from activity_male_video_calling.xml — any new chrome added there
        // should also be added below.
        runCatching { binding.localCardView.visibility = chromeVisibility }
        runCatching { binding.timerContainer.visibility = chromeVisibility }
        runCatching { binding.btnMenu.visibility = chromeVisibility }
        runCatching { binding.usersContainer.visibility = chromeVisibility }
        runCatching { binding.controlsContainer.visibility = chromeVisibility }
        runCatching { binding.giftButtonCard.visibility = chromeVisibility }
    }

    override fun onDestroy() {
        stopHeartbeat()
        Log.d(
            TAG_END,
            "onDestroy isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined elapsedTime=$elapsedTime isFinishing=$isFinishing"
        )
        super.onDestroy()
        // B181 backstop — covers system-killed activities that bypass leaveChannel.
        FcmUtils.isUserAvailable = 0
        // B082 backstop — close lingering switch-call dialog.
        switchDialog?.dismiss()
        switchDialog = null
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
        reconnectWatchdog.cancel()

        // B143: deterministic teardown — disable audio+video, leave channel, then block on
        // RtcEngine.destroy() so the mic/camera are released before this activity finishes.
        stopMicRevokeWatcher()
        agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.releaseEngineSync(
            agoraEngine, "MaleVideoCalling", hasVideo = true
        )

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
            // 2026-05-22 — Contact event for Meta + Firebase.
            com.gmwapp.hima.utils.HimaAnalytics.logContact(this@MaleVideoCallingActivity, contentType = "video_call")
            startCallingService()
            isRemoteUserJoined= true
            videoUid = uid

            getRemainingTime()
            // I021 — load the package catalog now so the banner's chips are
            // populated by the time the timer drops below 60s.
            runOnUiThread { lowBalanceBanner?.prefetch() }
            // Safety-net 30s re-fetch — see MaleAudioCallingActivity.
            startTimerResync()

            startTime = dateFormat.format(Date()) // Set call end time in IST
            callStartMillis = System.currentTimeMillis() // B110: duration baseline

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
            // B186 — defensive unmute on join. See MaleAudioCallingActivity
            // onJoinChannelSuccess for full rationale.
            mutedByInterrupt = false
            if (!isMuted) agoraEngine?.muteLocalAudioStream(false)
            agoraEngine?.muteAllRemoteAudioStreams(false)
            // B185 — pre-bind a remote canvas with uid=0 the moment the
            // local user joins, BEFORE the remote user is announced via
            // onUserJoined. setupRemoteVideo(uid=0) tells Agora to attach
            // the first remote stream that arrives to this canvas, so the
            // SDK can start decoding/rendering as soon as packets land
            // instead of waiting for the round-trip:
            //   remote-join FCM → onUserJoined → runOnUiThread → addView →
            //   setupRemoteVideo.
            // onUserJoined still re-binds with the real uid (cheap), but by
            // then frames are already flowing.
            runOnUiThread {
                if (remoteSurfaceView == null && !isFinishing && !isDestroyed) {
                    val pre = SurfaceView(baseContext).apply {
                        setZOrderMediaOverlay(false)
                        visibility = View.VISIBLE
                    }
                    remoteSurfaceView = pre
                    binding.remoteVideoViewContainer.removeAllViews()
                    binding.remoteVideoViewContainer.addView(pre)
                    binding.remoteVideoViewContainer.visibility = View.VISIBLE
                    agoraEngine?.setupRemoteVideo(
                        VideoCanvas(pre, VideoCanvas.RENDER_MODE_HIDDEN, 0)
                    )
                    Log.d("AgoraTiming", "MaleVideo pre-bound remote canvas (uid=0) at ${System.currentTimeMillis()}")
                }
            }
            startTimeoutTracking()
            startMicRevokeWatcher()
        }



        override fun onUserOffline(uid: Int, reason: Int) {
          //  showMessage("Remote user offline $uid $reason")
            Log.d(
                TAG_END,
                "onUserOffline uid=$uid reason=$reason isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined"
            )
            stopCountdown()
            updateCallEndDetails()

            // Snapshot the latch on the Agora worker thread, then dispatch
            // ALL UI work to the main thread. Avoids racing with the dialog
            // flow that resets the latch inside showCameraUnavailablePeerDialogIfNeeded.
            val cameraGraceActive = cameraUnavailableLatched
            runOnUiThread {
                remoteSurfaceView?.let { // ✅ Safe check before accessing
                    it.visibility = View.GONE
                }
                if (cameraGraceActive) {
                    // Tester report: creator with broken camera disconnected
                    // before the 30s grace timer expired. Show the reason
                    // dialog and let its OK button drive leaveChannel.
                    cameraUnavailableNotice?.cancel()
                    cameraUnavailableNotice = null
                    showCameraUnavailablePeerDialogIfNeeded()
                } else {
                    Log.d(TAG_END, "onUserOffline -> startActivity(MainActivity) then finish()")
                    val intent = Intent(this@MaleVideoCallingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    Log.d(TAG_END, "finish() from onUserOffline")
                    finish()
                }
            }
        }

        override fun onError(err: Int) {
            Log.d(TAG_END, "onError err=$err isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined")
            super.onError(err)
        }

        override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
            // I006 — pass the WORSE of the two directions. See
            // MaleAudioCallingActivity for full rationale.
            com.gmwapp.hima.utils.CallQualityUi.apply(
                this@MaleVideoCallingActivity,
                binding.ivSignalStrength,
                binding.reconnectBanner,
                maxOf(txQuality, rxQuality),
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
            // B062 — auto-end on prolonged reconnect.
            reconnectWatchdog.armOrCancel(state)
            // 2026-05-22 v16 — when our connection comes back, check if peer
            // ended the call while we were offline. See MaleAudioCallingActivity.
            if (state == Constants.CONNECTION_STATE_CONNECTED && callId > 0) {
                com.gmwapp.hima.utils.CallAliveChecker.checkAndEndIfDead(callId) {
                    if (!isFinishing && !isDestroyed) {
                        leaveChannel(binding.LeaveButton)
                    }
                }
            }
            // 2026-05-22 v23 — REMOVED peer-avatar-on-own-reconnect logic at
            // user request. Tier-2/3 users have frequent brief network blips
            // and the avatar overlay made every blip feel like a major
            // disconnect. Let the video tile freeze naturally instead; the
            // small "Reconnecting…" banner above is sufficient signal.
            super.onConnectionStateChanged(state, reason)
        }

        // I024 — detect PEER-side network drops. See MaleAudioCallingActivity
        // for full rationale.
        override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteAudioStateChanged(uid, state, reason, elapsed)
            if (reason == Constants.REMOTE_AUDIO_REASON_REMOTE_MUTED) return
            runOnUiThread {
                when (state) {
                    Constants.REMOTE_AUDIO_STATE_FROZEN,
                    Constants.REMOTE_AUDIO_STATE_FAILED ->
                        reconnectWatchdog.peerStreamStalled(stalled = true)
                    Constants.REMOTE_AUDIO_STATE_DECODING,
                    Constants.REMOTE_AUDIO_STATE_STARTING ->
                        reconnectWatchdog.peerStreamStalled(stalled = false)
                }
                // 2026-05-23 v1072 — banner is DISABLED entirely. Don't toggle
                // visibility from peer-stream state either (was bypassing the
                // CallQualityUi banner-disable fix).
            }
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
            // B188 — after a network blip the channel rejoins but the remote
            // video canvas binding is stale (Agora's subscriber thread re-
            // attaches the stream to engine-internal state on rejoin, but
            // our SurfaceView ref doesn't carry over). Local preview works
            // because it's tied to the local capture pipeline, not the
            // channel subscriber. Re-bind the remote canvas if we already
            // know the remote uid.
            if (isRemoteUserJoined && videoUid != 0) {
                runOnUiThread { setupRemoteVideo(videoUid) }
            }
        }

        // B188 — last-resort recovery. Agora reports remote video stream
        // state transitions explicitly: STARTING(1) → DECODING(2) is the
        // happy path; FROZEN(3) means frames have stalled, FAILED(4) means
        // the subscriber thread gave up. On FAILED (and on FROZEN that
        // doesn't auto-recover within a few seconds) Agora's own renderer
        // is wedged and won't recover until we re-bind the canvas.
        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
            Log.d(TAG_END, "onRemoteVideoStateChanged uid=$uid state=$state reason=$reason")
            if (uid != videoUid) return
            // 2026-05-23 v1065 — debounce FROZEN/FAILED so tier-2/3 brief blips
            // don't flash the avatar overlay. STARTING (pre-first-frame) and
            // DECODING (recovered) still fire instantly. FROZEN/FAILED only
            // trigger after 8s of sustained stall; a recovery (DECODING) in
            // that window cancels the pending show.
            runOnUiThread {
                when (state) {
                    Constants.REMOTE_VIDEO_STATE_DECODING -> {
                        pendingAvatarShow?.let { mainHandlerForAvatar.removeCallbacks(it); pendingAvatarShow = null }
                        hideRemoteAvatarSkeleton()
                    }
                    Constants.REMOTE_VIDEO_STATE_STARTING -> showRemoteAvatarSkeleton()
                    Constants.REMOTE_VIDEO_STATE_FROZEN,
                    Constants.REMOTE_VIDEO_STATE_FAILED -> {
                        if (pendingAvatarShow == null) {
                            val run = Runnable {
                                showRemoteAvatarSkeleton()
                                pendingAvatarShow = null
                            }
                            pendingAvatarShow = run
                            mainHandlerForAvatar.postDelayed(run, 8_000L)
                        }
                    }
                }
            }
            // REMOTE_VIDEO_STATE_FAILED == 4
            if (state == Constants.REMOTE_VIDEO_STATE_FAILED) {
                runOnUiThread { setupRemoteVideo(uid) }
            }
        }

        override fun onUserMuteVideo(uid: Int, muted: Boolean) {
            super.onUserMuteVideo(uid, muted)


                runOnUiThread {
                    if (muted){
                        showRemoteBlurState()
                        // B058 — also show the skeleton; the blur overlay covers
                        // most of the screen but the skeleton makes sure no raw
                        // SurfaceView hole-punch artefact is visible underneath.
                        showRemoteAvatarSkeleton()


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

        // B055 — surface a "Peer is muted" pill below the top bar when the
        // remote participant mutes their mic. Without this, silence during
        // a video call looked indistinguishable from a connection problem.
        override fun onUserMuteAudio(uid: Int, muted: Boolean) {
            super.onUserMuteAudio(uid, muted)
            runOnUiThread {
                binding.remoteMicMutedPill.visibility = if (muted) View.VISIBLE else View.GONE
                // Perumal 2026-05-22: also drive the visible badge for peer mute.
                updateMuteBadge(peerMuted = muted)
            }
        }
    }

    // Perumal 2026-05-22: tracks peer mute state so self-mute toggle can OR with it.
    private var isPeerMutedBadge = false

    private fun updateMuteBadge(peerMuted: Boolean? = null, selfMutedOverride: Boolean? = null) {
        if (peerMuted != null) isPeerMutedBadge = peerMuted
        val showBadge = (selfMutedOverride ?: isMuted) || isPeerMutedBadge
        binding.ivRemoteMicMuted.visibility = if (showBadge) View.VISIBLE else View.INVISIBLE
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss").apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST time zone
    }

    fun updateCallEndDetails(){


        if (startTime.isNotEmpty()) {
            endTime = dateFormat.format(Date()) // Set call end time only if startTime is not empty
        }

        // See MaleAudioCallingActivity.updateCallEndDetails for rationale.
        com.gmwapp.hima.utils.CallEndUpdater.enqueueIfFresh(
            context = this@MaleVideoCallingActivity,
            userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0,
            callId = callId,
            startedTime = startTime,
            endedTime = endTime,
            isIndividual = true
        )

        // 2026-05-23 v26 — Spend Credits removed per marketing. Fire 2min_call
        // when video call duration >= 120s.
        if (callId > 0 && startTime.isNotEmpty()) {
            try {
                val sdf = dateFormat
                val durationSec = ((sdf.parse(endTime)?.time ?: 0L) - (sdf.parse(startTime)?.time ?: 0L)) / 1000
                if (durationSec >= 120) {
                    com.gmwapp.hima.utils.HimaAnalytics.log2MinCall(
                        ctx = this,
                        callId = callId,
                        contentType = "video_call",
                        durationSec = durationSec,
                    )
                }
            } catch (t: Throwable) {
                Log.w("HimaAnalytics", "MaleVideo 2min_call estimate failed: ${t.message}")
            }
        }


        if (switchCallID != 0) {
            callId = switchCallID
            Log.d("callidCheck","$callId")
        }
    }


    private fun setupRemoteVideo(uid: Int) {
        // B188 — re-binding for recovery (called from onRejoinChannelSuccess
        // and onRemoteVideoStateChanged) used to stack SurfaceViews in the
        // container while the engine canvas pointed to the latest one. The
        // old views remained attached with possibly-destroyed Surfaces,
        // and on some devices the engine renderer occasionally fell back
        // to one of them → frames written to a dead canvas → frozen feed.
        // Detach the previous SurfaceView and clear Agora's canvas binding
        // BEFORE creating the new view so the subscriber thread re-acquires
        // a clean target.
        binding.remoteVideoViewContainer.removeAllViews()
        agoraEngine?.setupRemoteVideo(
            VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, uid)
        )
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
        // B124: forward touches from the SurfaceView (which lives on a
        // separate compositor layer due to setZOrderMediaOverlay) into the
        // CardView's drag listener. Without this, Redmi/MIUI users can't
        // drag the local preview because the touch never reaches the CardView.
        localSurfaceView!!.setOnTouchListener(localPreviewTouchListener)

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

    /**
     * B124: drag handler. Always operates on [binding.localCardView] regardless
     * of which view received the touch — so we can attach the same listener to
     * the inner FrameLayout / SurfaceView and still drag the outer CardView.
     * Redmi/MIUI's input dispatcher routes touches on a SurfaceView with
     * setZOrderMediaOverlay(true) through a separate window-manager layer that
     * bypasses the CardView's OnTouchListener, so forwarding from the inner
     * views is what actually fixes the "can't move local bubble" bug.
     */
    private val localPreviewTouchListener = View.OnTouchListener { _, event ->
        val card = binding.localCardView
        val parent = binding.main
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (parent.width == 0 || parent.height == 0) return@OnTouchListener false
                localPreviewDragStartX = event.rawX
                localPreviewDragStartY = event.rawY
                localPreviewTouchOffsetX = event.rawX - card.x
                localPreviewTouchOffsetY = event.rawY - card.y
                isDraggingLocalPreview = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val clampedX = clampLocalPreviewX(event.rawX - localPreviewTouchOffsetX)
                val clampedY = clampLocalPreviewY(event.rawY - localPreviewTouchOffsetY)
                card.x = clampedX
                card.y = clampedY
                localPreviewOffsetX = clampedX
                localPreviewOffsetY = clampedY

                val dragDistance = abs(event.rawX - localPreviewDragStartX) +
                    abs(event.rawY - localPreviewDragStartY)
                if (dragDistance > 8f) isDraggingLocalPreview = true
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDraggingLocalPreview) card.performClick()
                true
            }

            else -> false
        }
    }

    private fun setupLocalPreviewDrag() {
        // Attach to both the CardView AND the FrameLayout container — the
        // container catches touches on Redmi where the inner SurfaceView's
        // separate compositor layer eats them. setupLocalVideo also attaches
        // the listener to the SurfaceView once it's created.
        binding.localCardView.setOnTouchListener(localPreviewTouchListener)
        binding.localVideoViewContainer.setOnTouchListener(localPreviewTouchListener)
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

        // Server-driven force-end observer — see MaleAudio counterpart for
        // the full B184 follow-up rationale. Hangs up if backend signals
        // that the male's coins are exhausted for this call.
        FcmUtils.forceEndCall.observe(this) { signal ->
            if (signal == null) return@observe
            val (signalCallId, reason) = signal
            if (signalCallId == callId) {
                Log.d("ForceEndCall", "Honoring server force-end callId=$signalCallId reason=$reason")
                FcmUtils.clearForceEndCall()
                if (!isFinishing && !isDestroyed) {
                    leaveChannel(binding.LeaveButton)
                }
            }
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
        // B128: log the live permission state at the camera-open boundary so
        // QA can confirm "Only this time" lifecycle in logcat. Permissions are
        // NEVER cached locally — every check goes through ContextCompat.
        // Android's "Only this time" stays alive across multiple call
        // activities within the same process by design; if the OS does revoke
        // it (extended background), the check below catches it and we
        // re-request instead of silently toast-and-stall.
        val camGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(
            "CameraPermission",
            "MaleVideo.joinChannel: cam=$camGranted mic=$micGranted"
        )
        if (checkSelfPermission()) {
            val options = ChannelMediaOptions()

            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER

            // Defensive symmetry with the female-side fix: a caller with a
            // broken camera would crash the same way. Skip camera setup, join
            // audio-only, peer sees the avatar skeleton (B058).
            val cameraOk = com.gmwapp.hima.utils.CameraAvailability.isCameraAvailable(this)
            if (cameraOk) {
                setupLocalVideo()
                localSurfaceView!!.visibility = View.VISIBLE
                agoraEngine!!.startPreview()
            } else {
                Log.w("CameraFallback", "MaleVideo.joinChannel: camera unavailable, joining audio-only")
                agoraEngine!!.enableLocalVideo(false)
                agoraEngine!!.muteLocalVideoStream(true)
                binding.localCardView.visibility = View.GONE
                showMessage(getString(R.string.call_no_camera_fallback))
            }

            agoraEngine!!.joinChannel(token, channelName, uid, options)
        } else {
            // Permission revoked between onCreate and here — usually the OS
            // auto-revoked an "Only this time" grant after extended background.
            // Re-prompt instead of just toasting "denied" so the user can
            // grant again without leaving the call screen.
            Log.w("CameraPermission", "MaleVideo.joinChannel: permission missing — re-requesting")
            ActivityCompat.requestPermissions(
                this@MaleVideoCallingActivity,
                REQUESTED_PERMISSIONS,
                PERMISSION_REQ_ID
            )
        }
    }

    fun leaveChannel(view: View) {
        Log.d(
            TAG_END,
            "leaveChannel() enter isJoined=$isJoined viewId=${view.id} isRemoteUserJoined=$isRemoteUserJoined"
        )
        // 2026-05-22 — instant peer-hangup propagation. Fire-and-forget FCM
        // push so the peer's app disconnects within seconds instead of waiting
        // ~25s for Agora onUserOffline. Background thread + 3s timeout — never
        // blocks teardown. Skipped if no callId/receiverId (e.g., not yet joined).
        FcmUtils.notifyPeerOfHangup(receiverId, callId)
        // B181 — clear the "user is busy" guard before navigating back so
        // fragments' onResume can refresh creator availability.
        FcmUtils.isUserAvailable = 0
        // B082 — close any switch-call dialog before tearing down so it
        // doesn't linger over the next screen as a phantom popup.
        switchDialog?.dismiss()
        switchDialog = null
        FcmUtils.clearCallSwitch()
        stopTimerResync()
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
            // B143: release mic/camera synchronously here too — onDestroy may run several seconds
            // later (or be killed) and we want hardware freed at the moment the user hangs up.
            stopMicRevokeWatcher()
            agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.releaseEngineSync(
                agoraEngine, "MaleVideoCalling", hasVideo = true
            )
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

    private fun getRemainingTime(attempt: Int = 0) {
        val maxRetries = 3
        maleUserId?.let { profileViewModel.getRemainingTime(it,"video", object :
            NetworkCallback<GetRemainingTimeResponse> {
            override fun onNoNetwork() {
                Log.w("RemainingTime", "no network on attempt $attempt — retry in 3s")
                if (attempt < maxRetries) {
                    Handler(Looper.getMainLooper()).postDelayed(
                        { getRemainingTime(attempt + 1) }, 3_000L
                    )
                }
            }

            override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                Log.w("RemainingTime", "failure on attempt $attempt: ${t.message} — retry in 3s")
                if (attempt < maxRetries) {
                    Handler(Looper.getMainLooper()).postDelayed(
                        { getRemainingTime(attempt + 1) }, 3_000L
                    )
                }
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

                    startCountdown(newTime, data.ends_at_ms, data.server_now_ms)
                }
            }

        }) }
    }

    fun startCountdown(remainingTime: String, endsAtMs: Long? = null, serverNowMs: Long? = null) {
        // Cancel any previous CountDownTimer before scheduling a new one.
        // get_remaining_time can fire multiple times per call (initial fetch +
        // refresh on remainingTimeUpdated push); without this, every refresh
        // stacks another timer and the displayed text flickers between two
        // values as each timer's onTick stomps the other.
        countDownTimer?.cancel()

        // B141: prefer the server-anchored absolute end timestamp when
        // available — both sides (male + female) compute remaining against
        // the same epoch ms, so their displays show the same value at the
        // same wall-clock instant. Fall back to the legacy "MM:SS" duration
        // string when the server hasn't deployed the v2 response yet.
        val totalMillis = if (endsAtMs != null && endsAtMs > 0L) {
            // When the server's own "now" is in the response, anchor against
            // it so the math is purely server-side and the displayed timer is
            // unaffected by client clock drift (emulator clocks, wrong-TZ
            // phones, devices that haven't NTP-synced). Falling back to the
            // device clock keeps backwards compat with older builds where the
            // caller doesn't pass serverNowMs.
            val anchor = serverNowMs ?: System.currentTimeMillis()
            (endsAtMs - anchor).coerceAtLeast(0L)
        } else {
            val timeParts = remainingTime.split(":").map { it.toIntOrNull() ?: 0 }
            val mins = timeParts.getOrElse(0) { 0 }
            val secs = timeParts.getOrElse(1) { 0 }
            (mins * 60 + secs) * 1000L
        }

        countDownTimer =  object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / 3600000
                val minutes = (millisUntilFinished % 3600000) / 60000
                val secs = (millisUntilFinished % 60000) / 1000

                binding.tvRemainingTime?.text = String.format("%02d:%02d:%02d", hours, minutes, secs)
                Log.d("timechanging","${String.format("%02d:%02d:%02d", hours, minutes, secs)}")

                // I021 — see MaleAudioCallingActivity for rationale.
                lowBalanceBanner?.maybeShow(millisUntilFinished)
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

                        // Always (re)start countdown — gating on stored != null
                        // meant a failed first getRemainingTime left the timer
                        // permanently stopped and the call had no auto-hangup
                        // at 00:00 (pairs with B184 fix).
                        storedRemainingTime = newTime
                        sendUpdatedTimeNotification(maleUserId,receiverId,"audio","remainingTimeUpdated")
                        stopCountdown()
                        startCountdown(newTime, data.ends_at_ms, data.server_now_ms)
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


                    // See audio branch above — drop the null gate so countdown
                    // can recover if initial getRemainingTime failed.
                    storedVideoRemainingTime = newTime
                    sendUpdatedTimeNotification(maleUserId,receiverId,"video","remainingTimeUpdated")
                    stopCountdown()
                    startCountdown(newTime, data.ends_at_ms, data.server_now_ms)
                }
            }
        })} }
    }




    private fun getAudioRemainingTime() {
        maleUserId?.let {
            profileViewModel.getRemainingTime(it, "audio", object :
                NetworkCallback<GetRemainingTimeResponse> {
                override fun onNoNetwork() {
                    // Ignore: remaining-time is a non-critical refresh; throwing here
                // (the original Kotlin `TODO()`) was killing the call activity on
                // any network blip — same root cause as B184.
                Log.w("RemainingTime", "callback ignored — call continues")
                }

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                    // Ignore: remaining-time is a non-critical refresh; throwing here
                // (the original Kotlin `TODO()`) was killing the call activity on
                // any network blip — same root cause as B184.
                Log.w("RemainingTime", "callback ignored — call continues")
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
                        startCountdown(newTime, data.ends_at_ms, data.server_now_ms)
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
                    // Ignore: remaining-time is a non-critical refresh; throwing here
                // (the original Kotlin `TODO()`) was killing the call activity on
                // any network blip — same root cause as B184.
                Log.w("RemainingTime", "callback ignored — call continues")
                }

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                    // Ignore: remaining-time is a non-critical refresh; throwing here
                // (the original Kotlin `TODO()`) was killing the call activity on
                // any network blip — same root cause as B184.
                Log.w("RemainingTime", "callback ignored — call continues")
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
                        startCountdown(newTime, data.ends_at_ms, data.server_now_ms)
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
        // B137 — fire the foreground service as soon as activity is visible.
        startCallingService()
    }

    override fun onResume() {
        super.onResume()
        Log.d(
            TAG_END,
            "onResume isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined elapsedTime=$elapsedTime isFinishing=$isFinishing"
        )
        Log.d("resumedtag","resumed")
        // B189 — when the user unlocks mid-call, setShowWhenLocked alone
        // still leaves the lockscreen above us on insecure-keyguard devices
        // (swipe-to-unlock). Asking the keyguard to dismiss on every resume
        // shaves the visible "delay" Laxmi reported — on secure keyguards
        // (PIN/pattern/fingerprint) it's a no-op since the user must
        // authenticate, but the call screen pops back instantly the moment
        // they do.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val km = getSystemService(android.content.Context.KEYGUARD_SERVICE)
                    as? android.app.KeyguardManager
                km?.requestDismissKeyguard(this, null)
            } catch (e: Exception) {
                Log.w(TAG_END, "requestDismissKeyguard failed: ${e.message}")
            }
        }
        // B162 — recover from a stuck interrupt-mute when focus loss/regain
        // didn't pair (see MaleAudioCallingActivity.onResume for full notes).
        audioFocusHelper?.request()
        if (mutedByInterrupt && audioFocusHelper?.hasFocus() == true) {
            Log.d("B162", "MaleVideo onResume: clearing stuck interrupt mute (focus held)")
            mutedByInterrupt = false
            agoraEngine?.muteAllRemoteAudioStreams(false)
            if (!isMuted) agoraEngine?.muteLocalAudioStream(false)
        }
        newRemainingTime()
        startCallingService()

        // I021 — see MaleAudioCallingActivity.onResume for rationale.
        if (pendingWalletReturn) {
            pendingWalletReturn = false
            lowBalanceBanner?.hide()
        }

        if (isJoined && ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showMessage("Microphone permission was revoked. Ending call.")
            agoraEngine?.leaveChannel()
            Log.d(TAG_END, "finish() from onResume.micRevoked")
            finish()
            return
        }

        // B176: counterpart to onPause/onStop — bring local video back when user returns.
        resumeLocalVideoAfterBackground()
    }

    override fun onPause() {
        Log.d(
            TAG_END,
            "onPause isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined elapsedTime=$elapsedTime isFinishing=$isFinishing"
        )
        // B176: screen-off / app backgrounded must stop pushing camera frames to the peer.
        // setShowWhenLocked(true) keeps this activity alive on the lock screen, so without this
        // the remote side would keep receiving video while the local user thinks the call is paused.
        pauseLocalVideoForBackground()
        super.onPause()
    }

    override fun onStop() {
        Log.d(
            TAG_END,
            "onStop isJoined=$isJoined isRemoteUserJoined=$isRemoteUserJoined elapsedTime=$elapsedTime isFinishing=$isFinishing"
        )
        // B176: release the camera hardware itself when the activity is no longer visible.
        try {
            agoraEngine?.stopPreview()
        } catch (e: Exception) {
            Log.e("MaleVideoCalling", "stopPreview in onStop", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        super.onStop()
    }

    private fun pauseLocalVideoForBackground() {
        if (!isJoined) return
        try {
            agoraEngine?.muteLocalVideoStream(true)
            videoMutedForBackground = true
        } catch (e: Exception) {
            Log.e("MaleVideoCalling", "muteLocalVideoStream(true) in pauseLocalVideoForBackground", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun resumeLocalVideoAfterBackground() {
        if (!isJoined || !videoMutedForBackground) return
        try {
            agoraEngine?.startPreview()
            agoraEngine?.muteLocalVideoStream(false)
            videoMutedForBackground = false
        } catch (e: Exception) {
            Log.e("MaleVideoCalling", "resumeLocalVideoAfterBackground", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun startMicRevokeWatcher() {
        if (micWatcher != null) return
        val watcher = com.gmwapp.hima.utils.MicPermissionWatcher(this) {
            if (isFinishing || isDestroyed) return@MicPermissionWatcher
            showMessage("Microphone permission was revoked. Ending call.")
            Log.d(TAG_END, "finish() from MicPermissionWatcher")
            try {
                agoraEngine?.leaveChannel()
            } catch (_: Exception) { }
            finish()
        }
        watcher.start()
        micWatcher = watcher
    }

    private fun stopMicRevokeWatcher() {
        micWatcher?.stop()
        micWatcher = null
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
        val muteIcon = if (isMuted) R.drawable.mute_img else R.drawable.unmute_img
        binding.btnMuteUnmute.setImageResource(muteIcon)
        // Perumal 2026-05-22: reflect self-mute on the visible top-center badge too.
        updateMuteBadge(selfMutedOverride = isMuted)
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
                router,
                currentAudioRoute
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
        // Agora's worker thread may write isSpeakerphoneOn after we return.
        // Verify once after the worker has flushed and re-apply if it raced.
        audioRouter?.verifyAndReapply(route)
    }

    private fun iconForRoute(route: com.gmwapp.hima.utils.CallAudioRouter.AudioRoute): Int = when (route) {
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER -> R.drawable.speakeron_img
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH -> R.drawable.ic_bluetooth_audio
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE -> R.drawable.speakeroff_img
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
            if (isMuted) R.drawable.mute_img else R.drawable.unmute_img
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
                // B110: compute actual call duration from the onUserJoined
                // baseline so the backend records a non-zero duration and the
                // Recent tab doesn't classify this completed call as "missed."
                val durationSec = if (callStartMillis > 0L) {
                    ((System.currentTimeMillis() - callStartMillis) / 1000L).toInt().coerceAtLeast(0)
                } else 0
                Log.d("CallStatus", "MaleVideo.hangup → ended/$endedByRole self=$maleUserId peer=$receiverId callId=$callId isCaller=$isCaller durationSec=$durationSec")
                callStatusViewModel.saveCallStatus(
                    userId = maleUserId,
                    receivedUserId = receiverId,
                    callId = callId,
                    endReason = CallEndReason.ENDED,
                    endedBy = endedByRole,
                    endedByUserId = maleUserId,
                    durationSeconds = durationSec,
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
        // B151: debounce so a rapid double-tap can't fire two opposite
        // switchTo*() calls before the server replies.
        binding.btnVideoCall.setOnSingleClickListener {
            if (isSwitchRequestPending) {
                Toast.makeText(this, "Already Request Sent", Toast.LENGTH_SHORT).show()
                return@setOnSingleClickListener
            }
            // B142 — decide direction from the call's actual mode flag, not
            // from Drawable.constantState equality.
            if (isAudioCallGoing) switchToVideo() else switchToAudio()
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
            // B082 — drop late switch payloads once the activity is finishing.
            if (isFinishing || isDestroyed) {
                FcmUtils.clearCallSwitch()
                return@Observer
            }
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

    /**
     * Tester report: when the creator has a broken camera, her side now
     * connects audio-only and sends a "cameraUnavailable" FCM. We observe
     * that signal and mirror the 30s countdown banner so the caller knows
     * the call is ending and why. The actual disconnect happens on the
     * creator side; we just sync the UI and show the explanation dialog
     * when onUserOffline fires.
     */
    private fun observeCameraUnavailable() {
        FcmUtils.cameraUnavailableStatus.observe(this) { signaledChannel ->
            if (signaledChannel.isNullOrEmpty()) return@observe
            // Scope the signal to THIS call — FCM may arrive while a prior
            // channel's listener is still attached.
            if (signaledChannel != channelName) {
                Log.d("CameraFallback", "Ignoring camera-unavailable for other channel: $signaledChannel vs $channelName")
                return@observe
            }
            // Idempotent — duplicate FCM deliveries don't re-arm the banner.
            if (cameraUnavailableLatched) return@observe
            cameraUnavailableLatched = true

            val banner = findViewById<android.widget.TextView>(R.id.camera_unavailable_banner)
            if (banner != null) {
                cameraUnavailableNotice = com.gmwapp.hima.utils.CameraUnavailableNotice(
                    context = this,
                    banner = banner,
                    bannerCopyRes = R.string.call_camera_unavailable_peer_banner,
                    onTimeout = {
                        // No leaveChannel here — the creator side owns the
                        // actual disconnect. We just show the reason dialog
                        // if the natural disconnect hasn't happened yet by
                        // the time the countdown completes.
                        showCameraUnavailablePeerDialogIfNeeded()
                    }
                ).also { it.start() }
            }
            // Clear the signal so we don't re-fire if the activity restarts.
            FcmUtils.clearCameraUnavailable()
        }
    }

    /**
     * Show the "her camera was unavailable" dialog before navigating away.
     * Called from the grace-timer onTimeout AND from onUserOffline (in case
     * the creator side leaves earlier than 30s). Idempotent.
     */
    private fun showCameraUnavailablePeerDialogIfNeeded() {
        if (!cameraUnavailableLatched) return
        if (isFinishing || isDestroyed) return
        cameraUnavailableLatched = false
        try {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.call_camera_unavailable_peer_dialog_title)
                .setMessage(R.string.call_camera_unavailable_peer_dialog_body)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { d, _ ->
                    d.dismiss()
                    if (!isFinishing && !isDestroyed) leaveChannel(binding.LeaveButton)
                }
                .show()
        } catch (e: Exception) {
            Log.w("CameraFallback", "Peer dialog show failed; leaving channel", e)
            if (!isFinishing && !isDestroyed) leaveChannel(binding.LeaveButton)
        }
    }

    fun observeCallSwitchRequest() {
        // B069 — observer attach timestamp; payloads older than this are
        // leftovers from a prior call and must not pop a dialog now.
        val callSwitchObserverStartedAtMs = System.currentTimeMillis()
        FcmUtils.updatedCallSwitch.observe(this, androidx.lifecycle.Observer { updatedCallSwitch ->
            // B082 — don't pop the switch-call dialog if the call has ended.
            if (isFinishing || isDestroyed) {
                FcmUtils.clearCallSwitch()
                return@Observer
            }
            // B069 — drop stale payloads (posted before this observer).
            val postedAt = FcmUtils.callSwitchPostedAt()
            if (postedAt == 0L || postedAt < callSwitchObserverStartedAtMs) {
                Log.d(
                    "B069",
                    "Dropping stale switch payload postedAt=$postedAt observerStart=$callSwitchObserverStartedAtMs"
                )
                FcmUtils.clearCallSwitch()
                return@Observer
            }
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
                    // B069 follow-up — outside-tap dismiss = implicit decline.
                    var respondedVideo = false
                    switchDialog = AlertDialog.Builder(this)
                        .setTitle("Switch to Video Call ?")
                        .setMessage("$receiverName requested for video call")
                        .setPositiveButton("Confirm") { _, _ ->
                            val remainingTime = binding.tvRemainingTime?.text.toString()
                            val timeParts = remainingTime.split(":").map { it.toInt() }
                            if (timeParts.size == 3) {
                                val hours = timeParts[0]
                                val minutes = timeParts[1]
                                val seconds = timeParts[2]
                                val totalSeconds = (hours * 3600) + (minutes * 60) + seconds
                                if (totalSeconds > 360) {
                                    if (userid != null && switchCallID != 0) {
                                        respondedVideo = true
                                        Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
                                        sendCallAcceptNotification(userid, receiverId, "video", "VideoAccepted")
                                        FcmUtils.clearCallSwitch()
                                        Log.d("NewCallID", "$newCallId")
                                        stopCountdown()
                                        isSwitchingToVideo = false
                                        enableVideoCall()
                                    }
                                } else {
                                    respondedVideo = true
                                    Toast.makeText(this, "$receiverName don't have enough coins", Toast.LENGTH_SHORT).show()
                                    FcmUtils.clearCallSwitch()
                                }
                            }
                        }
                        .setNegativeButton("Decline") { d, _ ->
                            respondedVideo = true
                            userid?.let {
                                sendCallAcceptNotification(it, receiverId, "video", "SwitchDeclined")
                            }
                            d.dismiss()
                            FcmUtils.clearCallSwitch()
                        }
                        .create().apply {
                            setOnDismissListener {
                                if (!respondedVideo && !isFinishing && !isDestroyed) {
                                    userid?.let { uid ->
                                        sendCallAcceptNotification(uid, receiverId, "video", "SwitchDeclined")
                                    }
                                    FcmUtils.clearCallSwitch()
                                }
                                switchDialog = null
                            }
                            show()
                        }

                }}

                if (switchType=="switchToAudio"){
                    if (isAudioCallGoing==false){
                    switchCallID = newCallId

                    switchDialog?.dismiss()

                    var respondedAudio = false
                    // B068 — modal. Outside-tap and back can't dismiss; only
                    // Confirm/Decline close the dialog. setOnDismissListener
                    // below remains as a backstop for activity teardown.
                    switchDialog = AlertDialog.Builder(this)
                        .setTitle("Switch to audio Call ?")
                        .setMessage("$receiverName requested for audio call")
                        .setCancelable(false)
                        .setPositiveButton("Confirm") { _, _ ->
                            respondedAudio = true
                            if (userid != null && switchCallID != 0) {
                                Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
                                sendCallAcceptNotification(userid, receiverId, "audio", "AudioAccepted")
                                FcmUtils.clearCallSwitch()
                                Log.d("NewCallID", "$newCallId")
                                stopCountdown()
                                isSwitchingToAudio = false
                                enableAudioCall()
                            }
                        }
                        .setNegativeButton("Decline") { d, _ ->
                            respondedAudio = true
                            userid?.let {
                                sendCallAcceptNotification(it, receiverId, "audio", "SwitchDeclined")
                            }
                            d.dismiss()
                            FcmUtils.clearCallSwitch()
                        }
                        .create().apply {
                            setOnDismissListener {
                                if (!respondedAudio && !isFinishing && !isDestroyed) {
                                    userid?.let { uid ->
                                        sendCallAcceptNotification(uid, receiverId, "audio", "SwitchDeclined")
                                    }
                                    FcmUtils.clearCallSwitch()
                                }
                                switchDialog = null
                            }
                            show()
                        }

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
        // B060 — keep the top-bar label honest after a mid-call switch.
        binding.tvCallType.setText(R.string.call_type_audio)
        // B058 — hide remote video skeleton when switching to audio mode;
        // audio UI uses users_container avatars instead.
        hideRemoteAvatarSkeleton()

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
                // 2026-05-22 v18 — preserve mute state across switch
                publishMicrophoneTrack = !isMuted
                publishCameraTrack = false
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            })
            // 2026-05-22 v19 — belt & suspenders: also enforce device-level mute.
            agoraEngine?.muteLocalAudioStream(isMuted)
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
            binding.btnVideoCall.setImageResource(R.drawable.videocall_img)

            // B191 — enableVideoCall() also flipped these for video mode but
            // enableAudioCall() forgot to reverse them, leaving video-only
            // controls overlaid on the audio UI ("mixed states"). Hide the
            // camera-flip button + face-detection overlay, and restore an
            // opaque background so the avatar/name layer renders cleanly
            // (enableVideoCall set main background to TRANSPARENT so video
            // could show through).
            binding.btnCameraFlip.visibility = View.GONE
            binding.faceDetectionOverlay.root.visibility = View.GONE
            binding.main.setBackgroundColor(android.graphics.Color.BLACK)

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
        // B060 — keep the top-bar label honest after a mid-call switch.
        binding.tvCallType.setText(R.string.call_type_video)
        // B058 — re-show skeleton until the new video stream starts decoding;
        // hidden again by onRemoteVideoStateChanged(DECODING).
        showRemoteAvatarSkeleton()
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
            // B191 — restore the camera-flip button hidden by a prior
            // audio downgrade. Face-detection overlay manages its own
            // visibility once face detection resumes.
            binding.btnCameraFlip.visibility = View.VISIBLE
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

            binding.btnVideoCall.setImageResource(R.drawable.audiocall_img)



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

    // 2026-05-23 v1066 — full-screen peer-avatar overlay DISABLED everywhere
    // per user request. The overlay was firing on every brief network blip on
    // tier-2/3 networks (own-connection reconnect + peer-stream FROZEN/FAILED
    // + peer mute video), making the UX feel constantly broken. Function kept
    // as no-op so existing call sites compile. hideRemoteAvatarSkeleton stays
    // active so any pre-existing visible skeleton gets cleared on first call.
    private fun showRemoteAvatarSkeleton() {
        // intentionally no-op
        return
    }

    private fun hideRemoteAvatarSkeleton() {
        if (binding.ivRemoteAvatarSkeleton.visibility != View.GONE) {
            binding.ivRemoteAvatarSkeleton.visibility = View.GONE
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



