package com.gmwapp.hima.agora.female

import android.Manifest
import android.app.Dialog
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Outline
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.PaymentWebViewActivity
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.activities.RatingActivity
import com.gmwapp.hima.agora.FaceDetectVideoFrameObserver
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import android.telecom.DisconnectCause
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityFemaleVideoCallingBinding
import com.gmwapp.hima.databinding.ActivityMaleVideoCallingBinding
import com.gmwapp.hima.media.RtcTokenBuilder2
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.retrofit.responses.FemaleCallAttendResponse
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.retrofit.responses.IcebreakerQuestionsResponse
import com.gmwapp.hima.agora.services.CallingService
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.CallAudioFocusHelper
import com.gmwapp.hima.utils.CallAudioRouter
import com.gmwapp.hima.utils.CallPhoneStateHelper
import com.gmwapp.hima.viewmodels.AccountViewModel
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
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.audio.AudioParams
import io.agora.rtc2.video.VideoCanvas
import com.google.gson.JsonElement
import org.json.JSONObject
//import org.vosk.Model
//import org.vosk.Recognizer
import retrofit2.Call
import retrofit2.Response
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.abs

@AndroidEntryPoint
class FemaleVideoCallingActivity : AppCompatActivity() {

    lateinit var binding: ActivityFemaleVideoCallingBinding
    var receiverId = 0

    // Tester report: creator with broken camera crashed on video accept.
    // The flow now: skip camera setup (via CameraAvailability), connect
    // audio-only, show a 30s countdown banner so both sides know what's
    // happening, then disconnect with a clear dialog.
    private var cameraUnavailableNotice: com.gmwapp.hima.utils.CameraUnavailableNotice? = null
    // Latched while the camera-unavailable grace flow is active. Tells
    // leaveChannel / onDestroy paths to skip the normal "remote user left"
    // dialog (we've already shown our own reason).
    private var cameraUnavailableActive: Boolean = false


    private var startTime: String = ""
    private var endTime: String = ""
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
        // TC_007: hide-only on disarm. We never SHOW the banner from here (the
        // own-network reconnect banner stays disabled per v1067); the only
        // shower is the 8s remote-video-freeze runnable. But when the watchdog
        // clears we must guarantee the pill goes away even if no fresh remote
        // video DECODING callback arrives to call hideRemoteAvatarSkeleton().
        onArmedChanged = { armed ->
            if (!armed) runOnUiThread { binding.reconnectBanner.visibility = View.GONE }
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
    // B196 false-positive fix: tracks ONLY a real cellular (SIM) call — the sole
    // interrupt source allowed to surface the "On hold — phone call in progress"
    // banner + peer HOLD signal. Audio-focus interrupts (notifications, the
    // assistant, other apps, VoIP) still mute audio but never set this, so they
    // no longer raise a phantom on-hold banner when there is no phone call.
    private var cellularInterrupt = false
    var isClicked : Boolean = false

    // 2026-05-23 v1065 — debounced peer-avatar overlay for FROZEN/FAILED.
    private val mainHandlerForAvatar = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingAvatarShow: Runnable? = null

    private var appId: String? = null // Will be received from backend

    var expirationTimeInSeconds = 3600
    lateinit var channelName : String
    private var token : String? = null

    private val profileViewModel: ProfileViewModel by viewModels()
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()

    private val userAvatarViewModel: UserAvatarViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()
    private val callDropStatusViewModel: CallDropStatusViewModel by viewModels()
    private val callStatusViewModel: CallStatusViewModel by viewModels()
    private val isCaller: Boolean by lazy { intent.getBooleanExtra("IS_CALLER", false) }

    private var currentAudioRoute: com.gmwapp.hima.utils.CallAudioRouter.AudioRoute =
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE
    private val ludoFcmViewModel: LudoFcmViewModel by viewModels()

    private var storedVideoRemainingTime: String? = null
    private var storedRemainingTime: String? = null

    private var countDownTimer: CountDownTimer? = null
    // B4/TC_006+TC_021: epoch-ms when remaining time first read 0 (0 = not currently zero).
    // Distinguishes a transient zero (rescued by the next positive resync) from a sustained
    // zero (genuine coin-exhaustion → end the call). Reset on any positive read.
    private var zeroRemainingSinceMs = 0L
    private var switchDialog: AlertDialog? = null  // Track current dialog
    private var faceDialog: Dialog? = null
    private var faceDetectedHandler: Handler? = null
    private var faceDetectedRunnable: Runnable? = null
    private var localPreviewSurface: SurfaceView? = null
    private var isShowingFacePreview = false  // Flag to prevent early dismissal

    private fun cancelFacePreviewTransition() {
        faceDetectedRunnable?.let { runnable ->
            faceDetectedHandler?.removeCallbacks(runnable)
        }
        faceDetectedRunnable = null
    }


//    private lateinit var model: Model
//    private lateinit var recognizer: Recognizer

    private val executor = Executors.newSingleThreadExecutor()
    private val accountViewModel: AccountViewModel by viewModels()

    var blockWords: List<String> = emptyList()
    var isBlockWordDetected : Boolean = false


    var receiverName = ""

    private var isAudioCallGoing : Boolean = false
    var isAudioCallIdReceived: Boolean = false


    private var isSwitchRequestPending = false

    private var isSwitchingToAudio = false // ✅ Prevent multiple calls
    private var isSwitchingToVideo = false // ✅ Prevent multiple calls


    var switchCallID =0
    // B069 storm fix: register these once per activity so duplicate observers
    // don't stack on shared LiveData and multiply the clear↔observe churn.
    private var notificationSentObserverRegistered = false
    private var callSwitchAcceptanceObserverRegistered = false

    private val uid = 0
    // @Volatile: read/written on both the Agora IRtcEngineEventHandler worker
    // thread (onRemoteVideoStateChanged/onUserJoined) and the UI thread, so it
    // needs a happens-before guarantee to avoid a stale-read data race [TC_007].
    @Volatile private var videoUid = 0

    var call_Id: Int = 0
    private var pendingLudoAction: String? = null
    private var currentLudoInviteId: String? = null

    private var isJoined = false
  //  private var mRtmClient: RtmClient? = null

    private var agoraEngine: RtcEngine? = null

    // In-call "on hold" signaling over the Agora data stream — tells the peer
    // when we step away for a cellular / VoIP call so they see a dedicated
    // "‹Name› is on hold" banner. See CallHoldSignal.
    private val holdSignal = com.gmwapp.hima.utils.CallHoldSignal { agoraEngine }

    // B127: real-time RECORD_AUDIO revoke listener; started on join, stopped on teardown.
    private var micWatcher: com.gmwapp.hima.utils.MicPermissionWatcher? = null
    // B176: tracks whether we muted local video for background/lock so onResume can restore it.
    private var videoMutedForBackground = false

    // Periodic re-fetch of remaining_time. See MaleAudioCallingActivity for
    // full rationale (FCM push can be lost → local CountDownTimer drifts).
    private val timerResyncHandler = android.os.Handler(android.os.Looper.getMainLooper())
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
    private companion object {
        private const val TIMER_RESYNC_INTERVAL_MS = 30_000L
    }

    private var localSurfaceView: SurfaceView? = null

    // Locked-accept video fix: when a video call is answered from a SECURE
    // lockscreen we no longer defer the whole join (that left the caller stuck on
    // "connecting" past his 20s watchdog while she entered her pattern, and she
    // landed on a blue/empty screen). Instead we join the channel IMMEDIATELY as
    // audio-only — exactly like an audio call, which connects fine over the
    // lockscreen — and defer ONLY the camera (binding SurfaceViews to a
    // keyguard-occluded window is what renders black). True while that post-unlock
    // camera publish is still pending. See joinChannel / publishVideoAfterUnlock.
    private var pendingVideoPublishAfterUnlock = false

    // @Volatile: see videoUid — touched from the Agora callback thread and the
    // UI thread; the recovery guard in onRemoteVideoStateChanged reads it [TC_007].
    @Volatile private var remoteSurfaceView: SurfaceView? = null
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


    private var isRemoteUserJoined = false
    private var elapsedTime = 0  // Tracks elapsed seconds
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = object : Runnable {
        override fun run() {
            elapsedTime++
            Log.d("CallTimeoutTracking", "Seconds passed: $elapsedTime")

            if (elapsedTime >= 20) { // B042: bumped 10 → 20 seconds. Slow networks
                // / OEM-throttled FCM regularly take 12-15 s for the peer to actually
                // join Agora after accepting; the old 10 s window false-fired
                // "User did not join" before the connection finished establishing.
                if (isRemoteUserJoined==false){
                    Log.d("isUserJoinedTimer","Leave Button")
                    // B043/B044 — see MaleAudioCallingActivity for the rationale
                    // on dropping the user-blaming wording.
                    Toast.makeText(this@FemaleVideoCallingActivity,"Couldn't connect — please try again", Toast.LENGTH_LONG).show()

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
            // Wait out any straggling RtcEngine.destroy() from a prior call before
            // creating — prevents the "black screen after several video calls" race.
            agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.createEngineSafely(config, "FemaleVideoCalling")
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
            Log.d("AgoraTiming", "FemaleVideo setupVideoSDKEngine done at ${System.currentTimeMillis()}")

            audioRouter?.release()
            audioRouter = CallAudioRouter(this).also { it.init() }
            val btNow = audioRouter?.isBluetoothConnected() == true
            val wiredNow = audioRouter?.isWiredHeadsetConnected() == true
            // B154: when wired earphones are plugged in, default to EARPIECE
            // so the system routes audio through the wired output instead of
            // forcing speaker — otherwise the creator hears nothing in her
            // earphones. Mirrors the male-side B048 fix.
            val initial = when {
                btNow -> com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH
                wiredNow -> com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE
                isSpeakerOn -> com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER
                else -> com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE
            }
            Log.d(
                "CallAudioRoute",
                "Activity.setup initialRoute=$initial btConnected=$btNow wiredConnected=$wiredNow isSpeakerOn=$isSpeakerOn"
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
                onCellularCallActive = { muteForInterrupt(true, fromCellular = true) },
                onCellularCallEnded = { muteForInterrupt(false, fromCellular = true) }
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
     * Mutes/unmutes our audio (and remote streams) when an external call
     * interrupts the Hima call, and signals the peer so BOTH sides see an
     * "on hold" banner. Shows our own banner (B196) and sends the HOLD/UNHOLD
     * data-stream signal for ALL interrupt sources — cellular
     * (CallPhoneStateHelper) and VoIP / other-app audio-focus loss
     * (CallAudioFocusHelper) — not just SIM calls.
     */
    private fun muteForInterrupt(muted: Boolean, fromCellular: Boolean = false) {
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
                // B196 false-positive fix: only a real cellular (SIM) call may
                // raise the "On hold — phone call in progress" banner and tell
                // the peer we've stepped away. Audio-focus interrupts mute above
                // but must NOT claim a phone call is in progress.
                if (fromCellular) {
                    cellularInterrupt = true
                    holdSignal.sendHold(true)
                    runCatching { binding.onHoldBanner.visibility = View.VISIBLE }
                }
            } else {
                // Clear the banner + peer signal only when the cellular call
                // itself ends — never on an unrelated audio-focus regain.
                if (fromCellular && cellularInterrupt) {
                    cellularInterrupt = false
                    holdSignal.sendHold(false)
                    runCatching { binding.onHoldBanner.visibility = View.GONE }
                }
                if (mutedByInterrupt) {
                    mutedByInterrupt = false
                    if (!isMuted) agoraEngine?.muteLocalAudioStream(false)
                    agoraEngine?.muteAllRemoteAudioStreams(false)
                    agoraEngine?.muteAllRemoteVideoStreams(false)
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
        binding = ActivityFemaleVideoCallingBinding.inflate(layoutInflater)
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

        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        receiverId = intent.getIntExtra("RECEIVER_ID", -1)
        call_Id = intent.getIntExtra("CALL_ID", 0)

        Log.d("VideoCallingLog", "Channel: $channelName, Receiver: $receiverId, callId : $call_Id")
        Log.d("AgoraTiming", "FemaleVideo onCreate at ${System.currentTimeMillis()}")

        // Use pre-fetched token from connecting/accept screen if available, else fetch from backend
        val intentToken = intent.getStringExtra("AGORA_TOKEN")
        val intentAppId = intent.getStringExtra("AGORA_APP_ID")
        if (!intentToken.isNullOrEmpty() && !intentAppId.isNullOrEmpty()) {
            Log.d("AgoraTiming", "FemaleVideo using pre-fetched token at ${System.currentTimeMillis()}")
            token = intentToken
            appId = intentAppId
            if (!checkSelfPermission()) {
                ActivityCompat.requestPermissions(
                    this@FemaleVideoCallingActivity,
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

        observeRemainingTimeUpdated()
        observeGiftReceived()
        observeForceEndCall()
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
                .onFailure { Log.w("FemaleVideoCalling", "switchCamera failed: ${it.message}") }
        }

        endcallBtn()

        showGreyScreen()

        onBackPressedBtn()
//        onMenuClicked()

        userAvatarViewModel.getUserAvatar(receiverId)
        avatarObservers()

        handleCallSwitch()
        observeCallSwitchRequest()
        setupLocalPreviewDrag()


        userData?.let { setMyAvatar(it.image, it.name) }
        setupIplTeamBadges()

        getBlockWords()
        setupIcebreakerIfFemale()
        if (com.gmwapp.hima.utils.FeatureFlags.LUDO_ENABLED) {
            setupLudoInviteFlow()
        } else {
            binding.ludoButtonCard.visibility = View.GONE
        }
    }

    private fun setupIcebreakerIfFemale() {
        // 2026-05-22: icebreaker feature hidden until ready. See FemaleAudioCallingActivity.
        binding.icebreakerHintButton.visibility = View.GONE
        return
        @Suppress("UNREACHABLE_CODE")
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        if (!userData.gender.equals("female", ignoreCase = true)) {
            binding.icebreakerHintButton.visibility = View.GONE
            return
        }
        binding.icebreakerHintButton.visibility = View.VISIBLE
        binding.icebreakerHintButton.setOnSingleClickListener {
            requestAndShowIcebreakerQuestions(userData.id)
        }
    }

    private fun requestAndShowIcebreakerQuestions(userId: Int) {
        profileViewModel.getIcebreakerQuestions(
            userId = userId,
            callback = object : NetworkCallback<IcebreakerQuestionsResponse> {
                override fun onResponse(
                    call: Call<IcebreakerQuestionsResponse>,
                    response: Response<IcebreakerQuestionsResponse>
                ) {
                    val body = response.body()
                    if (body?.success == true) {
                        val questions = parseIcebreakerQuestions(body.data)
                        if (questions.isEmpty()) {
                            Toast.makeText(
                                this@FemaleVideoCallingActivity,
                                "No icebreaker questions available",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            showIcebreakerDialog(questions)
                        }
                    } else {
                        Toast.makeText(
                            this@FemaleVideoCallingActivity,
                            body?.message ?: "Unable to load questions",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<IcebreakerQuestionsResponse>, t: Throwable) {
                    Log.e("IcebreakerQuestions", "API failed: ${t.message}")
                    Toast.makeText(
                        this@FemaleVideoCallingActivity,
                        "Failed to load questions",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onNoNetwork() {
                    Toast.makeText(
                        this@FemaleVideoCallingActivity,
                        "No internet connection",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun showIcebreakerDialog(questions: List<String>) {
        val message = buildString {
            questions.forEachIndexed { index, question ->
                append("\u2022 ")
                append(question)
                if (index != questions.lastIndex) append("\n\n")
            }
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_icebreaker_questions, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_icebreaker_dialog_message)
        val btnClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btn_close_icebreaker_dialog
        )
        tvMessage.text = message
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun parseIcebreakerQuestions(data: JsonElement?): List<String> {
        if (data == null || data.isJsonNull) return emptyList()
        val result = mutableListOf<String>()

        fun addQuestion(raw: String?) {
            val cleaned = raw?.trim().orEmpty()
            if (cleaned.isNotEmpty()) {
                result.add(cleaned)
            }
        }

        fun parseElement(element: JsonElement?) {
            if (element == null || element.isJsonNull) return

            when {
                element.isJsonPrimitive -> addQuestion(element.asString)

                element.isJsonArray -> {
                    element.asJsonArray.forEach { parseElement(it) }
                }

                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val candidateKeys = listOf("question", "text", "title", "prompt")
                    var consumed = false

                    for (key in candidateKeys) {
                        if (obj.has(key)) {
                            parseElement(obj.get(key))
                            consumed = true
                        }
                    }

                    if (!consumed) {
                        if (obj.has("questions")) {
                            parseElement(obj.get("questions"))
                        } else if (obj.has("data")) {
                            parseElement(obj.get("data"))
                        }
                    }
                }
            }
        }

        parseElement(data)
        return result.distinct()
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
                    finish()
                    return@observe
                }
                Log.d("AgoraToken", "Token and AppId received from backend")
                
                // Request permissions if not granted
                if (!checkSelfPermission()) {
                    ActivityCompat.requestPermissions(
                        this@FemaleVideoCallingActivity,
                        REQUESTED_PERMISSIONS,
                        PERMISSION_REQ_ID
                    )
                } else {
                    setupVideoSDKEngine()
                    
                    if (agoraEngine != null) {
                        Log.d("AgoraCheck", "Agora is initialized.")
                    } else {
                        Log.e("AgoraCheck", "Agora is NULL! Check initialization.")
                    }
                    
                    joinChannel(binding.JoinButton) // Automatically join the channel
                }
            } else {
                Log.e("AgoraToken", "Failed to get token: ${response?.message}")
                showMessage("Failed to initialize call. Please try again.")
                finish()
            }
        }

        // Observe errors
        agoraViewModel.agoraTokenErrorLiveData.observe(this) { error ->
            Log.e("AgoraToken", "Error: $error")
            showMessage(error ?: "Failed to initialize call. Please try again.")
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
//                                val prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE)
//                                prefs.edit().putBoolean("blockword_detected", isBlockWordDetected).apply()
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


    fun startCallingService() {

        Log.d("startCallingService","Service Function call")
        if (CallingService.isRunning) return

        Log.d("startCallingService","Service not returned")

        // B137 — STARTED is the earliest legal state for FGS start on
        // Android 14/15; firing from onStart shaves ~100-300ms off the
        // time before the session notification appears.
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
     * B061 — slide the call into Picture-in-Picture when the user presses
     * Home during an active video call, instead of just backgrounding the
     * activity. See [com.gmwapp.hima.agora.male.MaleVideoCallingActivity]
     * for the full rationale; the implementation here mirrors it.
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

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val chromeVisibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        // View IDs come from activity_female_video_calling.xml — keep in
        // sync with the layout if new chrome is added.
        runCatching { binding.localCardView.visibility = chromeVisibility }
        runCatching { binding.timerContainer.visibility = chromeVisibility }
        runCatching { binding.btnMenu.visibility = chromeVisibility }
        runCatching { binding.usersContainer.visibility = chromeVisibility }
        runCatching { binding.controlsContainer.visibility = chromeVisibility }
    }

    private fun setMyAvatar(image: String, name: String) {
        binding.tvFemaleName.setText(com.gmwapp.hima.utils.DisplayName.clean(name))
        Glide.with(this)
            .load(image)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivFemaleUser)
    }

    private fun setupIplTeamBadges() {
        if (!com.gmwapp.hima.utils.FeatureFlags.IPL_ENABLED) {
            binding.femaleIplBadge.visibility = View.GONE
            binding.femaleTeamRing.visibility = View.GONE
            binding.maleIplBadge.visibility = View.GONE
            binding.maleTeamRing.visibility = View.GONE
            return
        }
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val savedTeamName = prefs?.getSelectedIplTeam()
        val team = savedTeamName?.let { name ->
            com.gmwapp.hima.models.IplTeam.values().find { it.name == name }
        }
        if (team != null) {
            binding.femaleIplBadge.visibility = View.VISIBLE
            binding.tvFemaleIplTeam.text = team.abbreviation
            val femaleDot = binding.femaleIplDot.background.mutate() as android.graphics.drawable.GradientDrawable
            femaleDot.setColor(android.graphics.Color.parseColor(team.primaryColor))
            binding.femaleTeamRing.visibility = View.VISIBLE
            val ringDrawable = binding.femaleTeamRing.background.mutate() as android.graphics.drawable.GradientDrawable
            ringDrawable.setColor(android.graphics.Color.parseColor(team.primaryColor))
        }
        val demoTeam = com.gmwapp.hima.models.IplTeam.values().random()
        binding.maleIplBadge.visibility = View.VISIBLE
        binding.tvMaleIplTeam.text = demoTeam.abbreviation
        val maleDot = binding.maleIplDot.background.mutate() as android.graphics.drawable.GradientDrawable
        maleDot.setColor(android.graphics.Color.parseColor(demoTeam.primaryColor))
        binding.maleTeamRing.visibility = View.VISIBLE
        val maleRing = binding.maleTeamRing.background.mutate() as android.graphics.drawable.GradientDrawable
        maleRing.setColor(android.graphics.Color.parseColor(demoTeam.primaryColor))
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

                binding.tvMaleName.setText(com.gmwapp.hima.utils.DisplayName.clean(response.data?.name))

                // B043 + B058: load the caller's avatar into the persistent
                // skeleton ImageView that sits BEHIND remote_video_view_container.
                // Because it's a sibling (not a child of the container), no
                // removeAllViews() can destroy it — so during initial connect,
                // FAILED/FROZEN reattach, mid-call audio↔video switch, and the
                // brief mute→unmute window, the creator sees the peer avatar
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
                // B173 — denying once = exit. Re-requesting after denial used
                // to silently spin on Android 11+ ("permanently denied" state)
                // leaving the user stuck on a non-functional video screen.
                Toast.makeText(
                    this,
                    "Camera and microphone access are required for a video call. " +
                        "Enable them in Settings to try again.",
                    Toast.LENGTH_LONG
                ).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) finish()
                }, 1500)
            }
        }
    }

    override fun onDestroy() {
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
        
        // Clean up face detection handlers
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
        // TC_007: cancel the pending 8s avatar/banner show so it can't fire
        // after teardown and mutate this dead activity's views.
        pendingAvatarShow?.let { mainHandlerForAvatar.removeCallbacks(it) }
        pendingAvatarShow = null

        // B143: deterministic teardown — disable audio+video, leave channel, then block on
        // RtcEngine.destroy() so the mic/camera are released before this activity finishes.
        stopMicRevokeWatcher()
        agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.releaseEngineSync(
            agoraEngine, "FemaleVideoCalling", hasVideo = true
        )
        if (isRemoteUserJoined==true){
            val intent = Intent(this@FemaleVideoCallingActivity, RatingActivity::class.java)
            intent.putExtra(DConstants.RECEIVER_NAME, receiverName)
            intent.putExtra(DConstants.RECEIVER_ID, receiverId)
            intent.putExtra(DConstants.CALL_ID, call_Id)
            intent.putExtra(DConstants.CALL_TYPE, DConstants.VIDEO)
            startActivity(intent)
            Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
            }

    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        // Peer "on hold" signal — show/hide the dedicated banner when the other
        // party steps away for a cellular / VoIP call (and clears it on resume).
        override fun onStreamMessage(uid: Int, streamId: Int, data: ByteArray?) {
            super.onStreamMessage(uid, streamId, data)
            val onHold = com.gmwapp.hima.utils.CallHoldSignal.parse(data) ?: return
            runOnUiThread {
                binding.peerOnHoldBanner.text =
                    getString(R.string.call_peer_on_hold, receiverName)
                binding.peerOnHoldBanner.visibility = if (onHold) View.VISIBLE else View.GONE
            }
        }

        override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
            // I006 — pass the WORSE of the two directions. See
            // MaleAudioCallingActivity for full rationale.
            com.gmwapp.hima.utils.CallQualityUi.apply(
                this@FemaleVideoCallingActivity,
                binding.ivSignalStrength,
                binding.reconnectBanner,
                maxOf(txQuality, rxQuality),
                null
            )
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            com.gmwapp.hima.utils.CallQualityUi.apply(
                this@FemaleVideoCallingActivity,
                binding.ivSignalStrength,
                binding.reconnectBanner,
                Constants.QUALITY_UNKNOWN,
                state
            )
            // B062 — auto-end on prolonged reconnect.
            reconnectWatchdog.armOrCancel(state)
            // 2026-05-22 v16 — when our connection comes back, check if peer
            // ended the call while we were offline. See MaleAudioCallingActivity.
            if (state == Constants.CONNECTION_STATE_CONNECTED && call_Id > 0) {
                com.gmwapp.hima.utils.CallAliveChecker.checkAndEndIfDead(call_Id) {
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
                // 2026-05-23 v1072 — banner DISABLED. See MaleVideo for rationale.
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
         //   showMessage("Remote user joined $uid")
            Log.d("AgoraTiming", "FemaleVideo onUserJoined at ${System.currentTimeMillis()}")
            isRemoteUserJoined=true
            getRemainingTime()
            startTimerResync()
            startTime = dateFormat.format(Date()) // Set call end time in IST

            startCallingService()
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


            if (ContextCompat.checkSelfPermission(this@FemaleVideoCallingActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val granted = ContextCompat.checkSelfPermission(this@FemaleVideoCallingActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                Log.d("FaceDetection", "CAMERA permission granted: $granted")
                //startFaceDetectionCamera()
                val videoObserver = FaceDetectVideoFrameObserver(this@FemaleVideoCallingActivity)
                agoraEngine?.registerVideoFrameObserver(videoObserver)

            } else {
                Log.d("FaceDetection", "CAMERA permission granted: Not granted")

                ActivityCompat.requestPermissions(this@FemaleVideoCallingActivity, arrayOf(Manifest.permission.CAMERA), 22)
            }


            initVosk()

//            agoraEngine?.registerAudioFrameObserver(audioFrameObserver)

        }

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
            Log.d("AgoraTiming", "FemaleVideo onJoinChannelSuccess at ${System.currentTimeMillis()}")
            // B186 — defensive unmute on join. See MaleAudioCallingActivity
            // onJoinChannelSuccess for full rationale.
            mutedByInterrupt = false
            if (!isMuted) agoraEngine?.muteLocalAudioStream(false)
            agoraEngine?.muteAllRemoteAudioStreams(false)
            // Open the reliable data stream used for peer "on hold" signaling.
            holdSignal.onChannelJoined()
            // B185 — pre-bind remote canvas with uid=0 so Agora can attach
            // the first remote stream the moment it arrives, without
            // waiting for our onUserJoined → setupRemoteVideo main-thread
            // hop. onUserJoined still re-binds with the real uid below.
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
                    Log.d("AgoraTiming", "FemaleVideo pre-bound remote canvas (uid=0) at ${System.currentTimeMillis()}")
                }
            }
            startTimeoutTracking()
            startMicRevokeWatcher()
            Log.d("JoinedSuccessFully","$channel")
        //    showMessage("Joined Channel $channel")

        }

        override fun onUserOffline(uid: Int, reason: Int) {
          //  showMessage("Remote user offline $uid $reason")
            // Peer left — clear any stale "on hold" banner (no UNHOLD arrives if
            // they dropped abruptly while on hold).
            runOnUiThread { runCatching { binding.peerOnHoldBanner.visibility = View.GONE } }

            // B-CALL RC#3: reason=1 = peer connection TIMED OUT (may rejoin). Arm the
            // reconnect watchdog instead of ending; stream-resume / onRejoinChannelSuccess
            // clears it on rejoin. Fail-safe: watchdog ends the call after ~30s if the
            // peer never returns. reason 0 (voluntary leave) / 2 (kicked) end below.
            if (reason == 1 && isRemoteUserJoined) {
                Log.w("CallReconnect", "FemaleVideo onUserOffline reason=1 — reconnect grace, not ending")
                reconnectWatchdog.peerStreamStalled(stalled = true)
                return
            }

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

        // B188 — re-bind remote canvas after a transient network rejoin.
        // Local preview keeps working because it's tied to local capture
        // (independent of channel subscriber), but the remote SurfaceView's
        // Agora binding doesn't carry over the rejoin → frozen remote feed.
        override fun onRejoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            super.onRejoinChannelSuccess(channel, uid, elapsed)
            if (videoUid != 0) {
                runOnUiThread { setupRemoteVideo(videoUid) }
            }
        }

        // B188 — last-resort recovery for a wedged remote subscriber.
        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
            Log.d(
                "VideoCallFlow",
                "FemaleVideo.onRemoteVideoStateChanged uid=$uid state=$state reason=$reason"
            )
            // TC_007: marshal the entire handler onto the main thread. Agora
            // delivers this on an internal worker thread, but the body reads View
            // state (remoteVideoViewContainer.childCount) and rebinds the canvas —
            // both must happen on the main thread. One runOnUiThread preserves the
            // original top-to-bottom ordering exactly (recovery → videoUid guard →
            // avatar state → FAILED rebind).
            runOnUiThread {
                // B13/TC_007 recovery path: if the remote is starting/decoding but the
                // canvas was never created (missed onUserJoined / recreation), bind it
                // now. Runs BEFORE the videoUid guard below because this is exactly the
                // case where videoUid may be stale/unset. Acts ONLY when the surface is
                // genuinely MISSING — must not override the GONE state owned by the
                // mute-blur / no-face-overlay logic (which hides without removing).
                if ((state == Constants.REMOTE_VIDEO_STATE_STARTING ||
                        state == Constants.REMOTE_VIDEO_STATE_DECODING) &&
                    (binding.remoteVideoViewContainer.childCount == 0 || remoteSurfaceView == null)
                ) {
                    videoUid = uid
                    setupRemoteVideo(uid)
                }
                if (uid != videoUid) return@runOnUiThread
                // 2026-05-23 v1065 — debounce FROZEN/FAILED so tier-2/3 brief blips
                // don't flash the avatar overlay. See MaleVideoCallingActivity for rationale.
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
                                // TC_007: after 8s of sustained freeze/failure, surface
                                // the "Reconnecting…" pill over the avatar. Driven by the
                                // remote-VIDEO-state path — NOT CallQualityUi's connection
                                // banner (which stays disabled per v1067) — so it appears
                                // only on a genuine sustained stall, never on brief blips.
                                binding.reconnectBanner.visibility = View.VISIBLE
                                pendingAvatarShow = null
                            }
                            pendingAvatarShow = run
                            mainHandlerForAvatar.postDelayed(run, 8_000L)
                        }
                    }
                }
                if (state == Constants.REMOTE_VIDEO_STATE_FAILED) {
                    setupRemoteVideo(uid)
                }
            }
        }

        override fun onUserMuteVideo(uid: Int, muted: Boolean) {
            super.onUserMuteVideo(uid, muted)

                runOnUiThread {
                    if (muted){
                        showRemoteBlurState()
                        // B058 — see MaleVideoCallingActivity for rationale.
                        showRemoteAvatarSkeleton()
                    }else{
                        val isLocalNoFaceOverlayVisible =
                            binding.faceDetectionOverlay.root.visibility == View.VISIBLE
                        if (isLocalNoFaceOverlayVisible) {
                            pendingRemoteBlurHide = true
                            remoteSurfaceView?.visibility = View.GONE
                            binding.remoteVideoViewContainer.visibility = View.GONE
                            return@runOnUiThread
                        }
                        hideRemoteBlurState()
                        binding.remoteVideoViewContainer.bringToFront()
                    }
                }
            }

        // B055 — see MaleVideoCallingActivity for rationale.
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

    private fun setupRemoteVideo(uid: Int) {
        // B188 — re-binding for recovery (rejoin / remote-video state
        // FAILED) used to stack SurfaceViews in the container. Detach the
        // previous SurfaceView and clear Agora's canvas binding BEFORE
        // creating the new view so the subscriber thread re-acquires a
        // clean target. See MaleVideoCallingActivity setupRemoteVideo for
        // full rationale.
        //
        // v1106 (2026-05-29) — guard against null agoraEngine. setupRemoteVideo
        // can fire from a delayed onUserJoined callback after the user has
        // already hung up and agoraEngine has been released to null. 1 user
        // hit NPE on `agoraEngine!!` on v1105.
        val engine = agoraEngine ?: run {
            Log.w("FemaleVideo", "setupRemoteVideo: agoraEngine is null (already released?) — skipping")
            return
        }
        if (isFinishing || isDestroyed) return
        binding.remoteVideoViewContainer.removeAllViews()
        engine.setupRemoteVideo(
            VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, uid)
        )
        // Rebuild on a fresh view so a stale/dead surface never lingers.
        binding.remoteVideoViewContainer.removeAllViews()
        val surface = SurfaceView(this)
        surface.setZOrderMediaOverlay(false)
        remoteSurfaceView = surface
        binding.remoteVideoViewContainer.addView(surface)
        binding.remoteVideoViewContainer.visibility = View.VISIBLE

        // Bind the Agora canvas ONLY once the Surface actually exists. The old code
        // bound synchronously right after addView(), racing Android's async surface
        // creation: if the surface wasn't ready the renderer attached to nothing and
        // — with no retry path — the remote view stayed black for the whole call
        // while the timer kept running. surfaceCreated() guarantees a live surface,
        // and re-fires on resume so backgrounding recovers too.
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d("VideoCallFlow", "FemaleVideo.remoteSurfaceCreated uid=$uid -> bind canvas")
                agoraEngine?.setupRemoteVideo(
                    VideoCanvas(surface, VideoCanvas.RENDER_MODE_HIDDEN, uid)
                )
                surface.visibility = View.VISIBLE
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
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
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
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
            if (currentUserId <= 0 || receiverId <= 0) {
                Toast.makeText(this, "Unable to send invite", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingLudoAction = "invite"
            ludoFcmViewModel.sendLudoFcm(
                action = "invite",
                fromUserId = currentUserId,
                toUserId = receiverId,
                callId = call_Id.toString()
            )
        }
        dialog.show()
    }

    private fun showIncomingLudoInviteDialog(event: FcmUtils.LudoEvent) {
        val inviteId = event.inviteId ?: return
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
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
                callId = call_Id.toString()
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
                callId = call_Id.toString()
            )
        }
        dialog.show()
    }

    private fun buildLudoUrl(roomCode: String?): String? {
        return if (roomCode.isNullOrBlank()) null else "https://demohima.himaapp.in/ludogame?room=$roomCode"
    }

    private fun openLudoWebView(url: String) {
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        val intent = PaymentWebViewActivity.createLudoIntent(
            context = this,
            url = url,
            fromUserId = currentUserId,
            toUserId = receiverId,
            callId = call_Id.toString(),
            inviteId = currentLudoInviteId
        )
        startActivity(intent)
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
            context = this@FemaleVideoCallingActivity,
            userId = receiverId,
            callId = call_Id,
            startedTime = startTime,
            endedTime = endTime,
            isIndividual = true
        )


        if (switchCallID!=0){
            call_Id = switchCallID
            Log.d("switchCallIDAfterUpdate","$switchCallID")
            Log.d("switchCallIDAfterUpdate","$call_Id")
        }

    }

    fun joinChannel(view: View) {
        // Locked-accept handling. Earlier we deferred the ENTIRE join until the
        // keyguard was dismissed — but a lockscreen pattern entry routinely takes
        // longer than the caller-side 20s "connecting" watchdog, so the caller
        // gave up before she ever joined (caller stuck "connecting", creator left
        // on a blue/empty screen). Audio calls never had this problem because they
        // join immediately over the lockscreen. So we do the same below: join NOW
        // (audio-only) so the caller connects right away, and defer ONLY the camera
        // — binding the camera/remote SurfaceViews to a keyguard-occluded window is
        // the part that renders black. The camera is published after unlock via
        // publishVideoAfterUnlock().
        val keyguardLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(android.content.Context.KEYGUARD_SERVICE)
                as? android.app.KeyguardManager)?.isKeyguardLocked == true
        } else false
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
            "FemaleVideo.joinChannel: cam=$camGranted mic=$micGranted"
        )
        if (checkSelfPermission()) {
            val options = ChannelMediaOptions()
            // B13/TC_007 — front-camera availability gates the local-preview restart
            // after join() succeeds. Restored after a refactor dropped this decl.
            val cameraOk = com.gmwapp.hima.utils.CameraAvailability.isCameraAvailable(this)

            Log.d("AgoraDebug", "Joining channel: $channelName, Token: $token")

            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            // Make remote subscription explicit — other paths flip these to false
            // when switching to audio; don't inherit a stale "don't subscribe" state.
            options.autoSubscribeVideo = true
            options.autoSubscribeAudio = true

            if (keyguardLocked) {
                // Audio-only join over the lockscreen: do NOT open the camera or
                // bind the local preview yet (an occluded window renders black and
                // can fail to open the camera on some OEMs). The mic still
                // publishes, so the caller's onUserJoined fires and he connects
                // instantly. The camera is attached + published the moment she
                // unlocks, in publishVideoAfterUnlock().
                options.publishCameraTrack = false
                agoraEngine?.enableLocalVideo(false)
                pendingVideoPublishAfterUnlock = true
                // Prompt the unlock so video can attach. Primary signal is the
                // dismiss callback; onWindowFocusChanged / onResume are backups.
                try {
                    (getSystemService(android.content.Context.KEYGUARD_SERVICE)
                        as? android.app.KeyguardManager)?.requestDismissKeyguard(
                        this,
                        object : android.app.KeyguardManager.KeyguardDismissCallback() {
                            override fun onDismissSucceeded() {
                                runOnUiThread { publishVideoAfterUnlock() }
                            }
                        }
                    )
                } catch (_: Exception) {}
                Log.d("FemaleVideoCalling", "joinChannel: keyguard locked — joining audio-only, camera deferred to unlock")
            } else {
                pendingVideoPublishAfterUnlock = false
                setupLocalVideo()
                localSurfaceView!!.visibility = View.VISIBLE
                agoraEngine!!.startPreview()
            }

            val result = agoraEngine!!.joinChannel(token, channelName, uid, options)
            if (result == 0) {
                Log.d("AgorajoinChannel", "joinChannel: Success $result")
                if (!keyguardLocked && cameraOk) {
                    localSurfaceView!!.visibility = View.VISIBLE
                    var startpreview = agoraEngine!!.startPreview()
                    Log.d("startpreview", "$startpreview")
                }

            } else {
                Log.e("AgorajoinChannel", "joinChannel failed with error code: $result")
                showMessage("Join channel failed: $result")

            }} else {
            // Permission revoked between onCreate and here — usually the OS
            // auto-revoked an "Only this time" grant after extended background.
            // Re-prompt instead of just toasting "denied" so the user can grant
            // again without leaving the call screen.
            Log.w("CameraPermission", "FemaleVideo.joinChannel: permission missing — re-requesting")
            ActivityCompat.requestPermissions(
                this@FemaleVideoCallingActivity,
                REQUESTED_PERMISSIONS,
                PERMISSION_REQ_ID
            )
        }
    }

    fun leaveChannel(view: View) {
        // 2026-05-22 — instant peer-hangup FCM (fire-and-forget). See
        // MaleVideoCallingActivity for full rationale.
        FcmUtils.notifyPeerOfHangup(receiverId, call_Id)
        // B181 — clear the "user is busy" guard before navigating back so
        // fragments' onResume can refresh creator/availability data.
        FcmUtils.isUserAvailable = 0
        // B082 — close any switch-call dialog (Accept audio/video request)
        // before the activity tears down, so it doesn't linger over the
        // next screen as a phantom popup.
        switchDialog?.dismiss()
        switchDialog = null
        FcmUtils.clearCallSwitch()
        // Cancel the camera-unavailable countdown if it was running (peer
        // may have hung up before our grace timer expired).
        cameraUnavailableNotice?.cancel()
        cameraUnavailableNotice = null
        stopTimerResync()
        // Bug #5B fix (2026-05-25): always teardown Agora regardless of isJoined.
        // releaseEngineSync is idempotent. See MaleAudioCallingActivity twin
        // for full comment.
        stopCountdown()
        stopMicRevokeWatcher()
        try {
            agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.releaseEngineSync(
                agoraEngine, "FemaleVideoCalling", hasVideo = true
            )
        } catch (t: Throwable) {
            Log.w("FemaleVideoCalling", "leaveChannel teardown threw (safe): ${t.message}")
        }
        if (remoteSurfaceView != null) remoteSurfaceView!!.visibility = View.GONE
        if (localSurfaceView != null) localSurfaceView!!.visibility = View.GONE
        val wasJoined = isJoined
        isJoined = false
        HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
        if (wasJoined) {
            updateCallEndDetails()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val intent = Intent(this@FemaleVideoCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }, 50L)
    }

    private fun getRemainingTime(attempt: Int = 0) {
        val maxRetries = 3
        receiverId?.let { profileViewModel.getRemainingTime(it,"video", object :
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
            // Bug #5A fix — see MaleAudioCallingActivity twin for full comment.
            val timeParts = remainingTime.split(":").map { it.toIntOrNull() ?: 0 }
            val totalSeconds = when (timeParts.size) {
                3 -> timeParts[0] * 3600L + timeParts[1] * 60L + timeParts[2]
                2 -> timeParts[0] * 60L + timeParts[1]
                1 -> timeParts[0].toLong()
                else -> 0L
            }
            (totalSeconds * 1000L).coerceAtLeast(0L)
        }

        // B4/TC_006+TC_021: a freshly-fetched remaining time of 0 (server clock
        // skew, a mid-debit read, or a stale "00:00:00") must NOT instantly tear
        // down a CONNECTED call. CountDownTimer(0,..) fires onFinish immediately ->
        // leaveChannel, dropping the call for BOTH sides (seconds after accept, or
        // on the first 30s resync). Coin-exhaustion stays enforced by the server's
        // callEndedNoCoins force-end FCM; a real "time's up" comes from a POSITIVE
        // timer ticking to zero, never from startCountdown being seeded with 0.
        if (totalMillis <= 0L) {
            binding.tvRemainingTime?.text = "00:00:00"
            // B4/TC_006+TC_021 (refined): a SINGLE zero can be transient (clock skew /
            // mid-debit read) and must NOT drop a funded call. But a SUSTAINED zero is
            // genuine coin-exhaustion and must end the call — this backend emits no
            // callEndedNoCoins force-end. Track when zero first appeared (cleared on any
            // positive resync below); end once it persists ~one resync interval. The 30s
            // timer-resync guarantees re-evaluation.
            val now = System.currentTimeMillis()
            if (zeroRemainingSinceMs == 0L) zeroRemainingSinceMs = now
            if (now - zeroRemainingSinceMs >= 25_000L && !isFinishing && !isDestroyed) {
                Log.w("RemainingTime", "remaining stayed 0 for ${now - zeroRemainingSinceMs}ms — ending call (exhaustion)")
                zeroRemainingSinceMs = 0L
                leaveChannel(binding.LeaveButton)
            } else {
                Log.w("RemainingTime", "transient zero remaining — skip auto-leave; will end if it persists")
            }
            return
        }
        // Positive remaining — clear the sustained-zero tracker (a transient zero recovered).
        zeroRemainingSinceMs = 0L

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
                        // Always (re)start countdown — gating on stored != null
                        // meant a failed first getRemainingTime left the timer
                        // permanently stopped and the call had no auto-hangup
                        // at 00:00 (pairs with B184 fix).
                        storedRemainingTime = newTime
                        stopCountdown()
                        startCountdown(newTime, data.ends_at_ms, data.server_now_ms)
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

                    // See audio branch above — drop the null gate so countdown
                    // can recover if initial getRemainingTime failed.
                    storedVideoRemainingTime = newTime
                    stopCountdown()
                    startCountdown(newTime, data.ends_at_ms, data.server_now_ms)
                }
            }
        }) }}
    }




    private fun getAudioRemainingTime() {
        receiverId?.let {
            profileViewModel.getRemainingTime(it, "audio", object :
                NetworkCallback<GetRemainingTimeResponse> {
                override fun onNoNetwork() {
                    // Ignore: remaining-time is a non-critical refresh; throwing here
                // (the original Kotlin `TODO()`) was killing the call activity on
                // any network blip — root cause of B184 immediate disconnect.
                Log.w("RemainingTime", "callback ignored — call continues")
                }

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                    // Ignore: remaining-time is a non-critical refresh; throwing here
                // (the original Kotlin `TODO()`) was killing the call activity on
                // any network blip — root cause of B184 immediate disconnect.
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
        receiverId?.let {
            profileViewModel.getRemainingTime(it, "video", object :
                NetworkCallback<GetRemainingTimeResponse> {
                override fun onNoNetwork() {
                    // Ignore: remaining-time is a non-critical refresh; throwing here
                // (the original Kotlin `TODO()`) was killing the call activity on
                // any network blip — root cause of B184 immediate disconnect.
                Log.w("RemainingTime", "callback ignored — call continues")
                }

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                    // Ignore: remaining-time is a non-critical refresh; throwing here
                // (the original Kotlin `TODO()`) was killing the call activity on
                // any network blip — root cause of B184 immediate disconnect.
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

    /**
     * Server-driven force-end observer — see counterpart in
     * [FemaleAudioCallingActivity.observeForceEndCall] for the full B184
     * follow-up rationale.
     */
    private fun observeForceEndCall() {
        FcmUtils.forceEndCall.observe(this, androidx.lifecycle.Observer { signal ->
            if (signal == null) return@Observer
            val (signalCallId, reason) = signal
            if (signalCallId == call_Id) {
                Log.d("ForceEndCall", "Honoring server force-end callId=$signalCallId reason=$reason")
                FcmUtils.clearForceEndCall()
                if (!isFinishing && !isDestroyed) {
                    leaveChannel(binding.LeaveButton)
                }
            }
        })
    }

    fun observeGiftReceived() {
        FcmUtils.giftReceived.observe(this, androidx.lifecycle.Observer { giftReceived ->
            if (giftReceived != null) {
                animateGift(giftReceived)
            }
            FcmUtils.cleargiftReceived()
        })
    }

    fun animateGift(image: String) {
        val giftImage = binding.ivGiftImage
        val femaleImage = binding.ivFemaleUser

        Toast.makeText(this, "Gift Received", Toast.LENGTH_SHORT).show()

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

        Glide.with(this)
            .load(image)
            .into(giftImage)

        giftImage.post {
            val startX = giftImage.translationX
            val startY = giftImage.translationY

            val giftLocation = IntArray(2)
            val femaleLocation = IntArray(2)
            giftImage.getLocationOnScreen(giftLocation)
            femaleImage.getLocationOnScreen(femaleLocation)

            val femaleCenterX = (femaleLocation[0] - giftLocation[0] + (femaleImage.width / 2f - giftImage.width / 2f))
            val femaleCenterY = (femaleLocation[1] - giftLocation[1] + (femaleImage.height / 2f - giftImage.height / 2f))

            giftImage.animate()
                .translationX(femaleCenterX)
                .translationY(femaleCenterY)
                .setDuration(2000)
                .withEndAction {
                    giftImage.animate()
                        .alpha(0f)
                        .setDuration(1000)
                        .withEndAction {
                            giftImage.visibility = View.INVISIBLE
                            giftImage.translationX = startX
                            giftImage.translationY = startY
                        }
                        .start()
                }
                .start()
        }
    }

    override fun onStart() {
        super.onStart()
        // B137 — fire FGS at STARTED (earliest legal) so session notification
        // appears sooner.
        startCallingService()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Backup signal (see joinChannel): publish the deferred camera once the
        // window regains focus after unlock.
        if (hasFocus) publishVideoAfterUnlock()
    }

    /**
     * Attach + publish the local camera once the keyguard is gone, for a video
     * call answered from a SECURE lockscreen. The channel join already happened
     * (audio-only) the instant she answered, so the caller is already connected;
     * this only upgrades our side to video. Safe to call repeatedly — it consumes
     * [pendingVideoPublishAfterUnlock], so only the first run after unlock
     * publishes; while still locked it no-ops and waits for the next trigger.
     */
    private fun publishVideoAfterUnlock() {
        if (!pendingVideoPublishAfterUnlock) return
        // Dismiss callback / onResume can fire after teardown; never touch a dead engine.
        if (isFinishing || isDestroyed) {
            pendingVideoPublishAfterUnlock = false
            return
        }
        val keyguard = getSystemService(android.content.Context.KEYGUARD_SERVICE)
            as? android.app.KeyguardManager
        // Still locked → wait for the next trigger (dismiss cb / focus / resume).
        if (keyguard != null && keyguard.isKeyguardLocked) return
        pendingVideoPublishAfterUnlock = false
        if (!checkSelfPermission()) return
        // Broken/unavailable camera: stay audio-only (matches enableVideoCall's
        // CameraAvailability fallback) rather than crash on camera open.
        val cameraOk = com.gmwapp.hima.utils.CameraAvailability.isCameraAvailable(this)
        if (!cameraOk) {
            Log.w("FemaleVideoCalling", "publishVideoAfterUnlock: camera unavailable — staying audio-only")
            return
        }
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            Log.d("FemaleVideoCalling", "keyguard dismissed — attaching + publishing camera")
            agoraEngine?.enableLocalVideo(true)
            agoraEngine?.muteLocalVideoStream(false)
            if (localSurfaceView == null) setupLocalVideo()
            localSurfaceView?.visibility = View.VISIBLE
            agoraEngine?.startPreview()
            // Start publishing the camera now that the window is visible. Mirrors
            // the publishCameraTrack pattern used by enableVideoCall / the
            // audio→video upgrade elsewhere in this codebase.
            agoraEngine?.updateChannelMediaOptions(ChannelMediaOptions().apply {
                publishCameraTrack = true
                autoSubscribeVideo = true
                autoSubscribeAudio = true
            })
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("resumedtag","resumed")
        // B189 — request keyguard dismiss every resume so the call screen
        // pops back instantly after a lock/unlock cycle (insecure keyguard
        // dismisses without auth; secure keyguard still requires it but the
        // activity returns the instant the user authenticates).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val km = getSystemService(android.content.Context.KEYGUARD_SERVICE)
                    as? android.app.KeyguardManager
                km?.requestDismissKeyguard(this, null)
            } catch (e: Exception) {
                Log.w("FemaleVideoCalling", "requestDismissKeyguard failed: ${e.message}")
            }
        }
        // Locked-accept video fix: third trigger for the deferred camera publish,
        // in case neither the dismiss callback nor onWindowFocusChanged fired.
        // No-op unless a publish is pending AND the keyguard is now gone.
        publishVideoAfterUnlock()
        // B162 — recover from a stuck interrupt-mute (focus loss without
        // matching regain). User on call surface = audio expected.
        audioFocusHelper?.request()
        if (mutedByInterrupt && audioFocusHelper?.hasFocus() == true) {
            Log.d("B162", "FemaleVideo onResume: clearing stuck interrupt mute (focus held)")
            mutedByInterrupt = false
            agoraEngine?.muteAllRemoteAudioStreams(false)
            if (!isMuted) agoraEngine?.muteLocalAudioStream(false)
        }
        newRemainingTime()
        startCallingService()

        if (isJoined && ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showMessage("Microphone permission was revoked. Ending call.")
            agoraEngine?.leaveChannel()
            finish()
            return
        }

        // B176: counterpart to onPause/onStop — bring local video back when user returns.
        resumeLocalVideoAfterBackground()
    }

    override fun onPause() {
        // B176: screen-off / app backgrounded must stop pushing camera frames to the peer.
        // Without this, the remote side keeps receiving video while the local user thinks the
        // call is paused.
        pauseLocalVideoForBackground()
        super.onPause()
    }

    override fun onStop() {
        // B176: release the camera hardware itself when the activity is no longer visible.
        try {
            agoraEngine?.stopPreview()
        } catch (e: Exception) {
            Log.e("FemaleVideoCalling", "stopPreview in onStop", e)
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
            Log.e("FemaleVideoCalling", "muteLocalVideoStream(true) in pauseLocalVideoForBackground", e)
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
            Log.e("FemaleVideoCalling", "resumeLocalVideoAfterBackground", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun startMicRevokeWatcher() {
        if (micWatcher != null) return
        val watcher = com.gmwapp.hima.utils.MicPermissionWatcher(this) {
            if (isFinishing || isDestroyed) return@MicPermissionWatcher
            showMessage("Microphone permission was revoked. Ending call.")
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

    private fun onAddcoinClicked() {
        binding.timerContainer.setOnSingleClickListener {
            // Timer container is now clickable and visible
            // You can add navigation to wallet activity if needed
            // val intent = Intent(this@FemaleVideoCallingActivity, WalletActivity::class.java)
            // startActivity(intent)
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        agoraEngine?.muteLocalAudioStream(isMuted)  // Mute or unmute audio
        val muteIcon = if (isMuted) R.drawable.mute_img else R.drawable.unmute_img
        binding.btnMuteUnmute.setImageResource(muteIcon)
        // 2026-05-22 v14: stopped toggling binding.femaleMute here. That icon
        // sits inside users_container which (despite being intended for
        // audio-mode UI) is shown VISIBLE during regular video calls by
        // onPictureInPictureModeChanged — so the femaleMute badge was
        // appearing in the middle-left of the screen, looking misaligned.
        // The top-center iv_remote_mic_muted badge (set below via
        // updateMuteBadge) is the single source of truth for mute indication
        // in video calls now — visible to both self and peer via the same view.
        binding.femaleMute.visibility = View.GONE
        // Perumal 2026-05-22: reflect self-mute on the visible top-center badge.
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
            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
            if (userId > 0 && receiverId > 0 && call_Id > 0) {
                // Fire-and-forget; call teardown should not be blocked by network.
                callDropStatusViewModel.saveCallDropStatus(
                    userId = userId,
                    receivedUserId = receiverId,
                    callId = call_Id,
                    callDropStatus = 1
                )
                val endedByRole = if (isCaller) CallEndedBy.CALLER else CallEndedBy.RECEIVER
                Log.d("CallStatus", "FemaleVideo.hangup → ended/$endedByRole self=$userId peer=$receiverId callId=$call_Id isCaller=$isCaller")
                callStatusViewModel.saveCallStatus(
                    userId = userId,
                    receivedUserId = receiverId,
                    callId = call_Id,
                    endReason = CallEndReason.ENDED,
                    endedBy = endedByRole,
                    endedByUserId = userId,
                )
            } else {
                Log.w(
                    "CallDropStatusAPI",
                    "Skip call_drop_status: userId=$userId receiverId=$receiverId callId=$call_Id"
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

    /**
     * Reveal the camera-unavailable banner with a 30-second countdown, send
     * the matching FCM to the caller so his screen mirrors the message, and
     * schedule a clean disconnect at the end of the grace window.
     * Idempotent — guarded by cameraUnavailableActive.
     */
    private fun startCameraUnavailableGrace() {
        if (cameraUnavailableActive) return
        cameraUnavailableActive = true

        // Notify the caller so his banner can mirror the countdown.
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val femaleId = userData?.id
        if (femaleId != null && receiverId > 0 && !channelName.isNullOrEmpty()) {
            fcmNotificationViewModel.sendNotification(
                senderId = femaleId,
                receiverId = receiverId,
                callType = "video",
                channelName = channelName,
                message = "cameraUnavailable"
            )
        } else {
            Log.w("CameraFallback", "Skipping FCM (femaleId=$femaleId receiverId=$receiverId channel=$channelName)")
        }

        val banner = findViewById<android.widget.TextView>(R.id.camera_unavailable_banner)
        if (banner == null) {
            Log.e("CameraFallback", "camera_unavailable_banner view not found — falling back to immediate end")
            showCameraUnavailableDialogAndEnd()
            return
        }
        cameraUnavailableNotice = com.gmwapp.hima.utils.CameraUnavailableNotice(
            context = this,
            banner = banner,
            bannerCopyRes = R.string.call_camera_unavailable_self_banner,
            onTimeout = { showCameraUnavailableDialogAndEnd() }
        ).also { it.start() }
    }

    /**
     * Show the end-of-call reason dialog and trigger the existing leave path.
     * Activity guards (isFinishing/isDestroyed) keep this safe if the user
     * already navigated away during the grace window.
     */
    private fun showCameraUnavailableDialogAndEnd() {
        if (isFinishing || isDestroyed) return
        try {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.call_camera_unavailable_self_dialog_title)
                .setMessage(R.string.call_camera_unavailable_self_dialog_body)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { d, _ ->
                    d.dismiss()
                    leaveChannel(binding.LeaveButton)
                }
                .show()
        } catch (e: Exception) {
            Log.w("CameraFallback", "Dialog show failed; leaving channel directly", e)
            leaveChannel(binding.LeaveButton)
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
        observeSwitchCallNotificationSent()
        isSwitchingToAudio = false
        isSwitchingToVideo = false

        Log.d("SwitchCallIdAfterSending","$switchCallID")

    }


    fun observeSwitchCallNotificationSent(){
        if (notificationSentObserverRegistered) return
        notificationSentObserverRegistered = true
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
        if (callSwitchAcceptanceObserverRegistered) return
        callSwitchAcceptanceObserverRegistered = true
        FcmUtils.updatedCallSwitch.observe(this, androidx.lifecycle.Observer { updatedCallSwitch ->
            // B082 — drop late switch FCMs once the activity is finishing.
            if (isFinishing || isDestroyed) {
                FcmUtils.clearCallSwitch()
                return@Observer
            }
            if (updatedCallSwitch != null) {
                val (switchType, receiverId) = updatedCallSwitch

                Log.d("SwitchCallIdAfterAcceptance","$switchCallID")

                if (switchType=="VideoAccepted" && receiverId==this.receiverId){

                    isSwitchRequestPending=false

                    val remainingTime = binding.tvRemainingTime?.text.toString() // Get the current countdown time
                    // B-v1110 #1 (sibling) — guard "Connecting…" parse; empty list skips the size==3 block.
                    val timeParts = remainingTime.split(":").mapNotNull { it.trim().toIntOrNull() }


                    if (timeParts.size == 3) {  // Ensure we have HH:MM:SS format
                        val hours = timeParts[0]
                        val minutes = timeParts[1]
                        val seconds = timeParts[2]

                        val totalSeconds = (hours * 3600) + (minutes * 60) + seconds

                        if (totalSeconds>360){
                          //  Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
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
                    isSwitchRequestPending=false

                  //  Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
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


    fun getCallIdforCallSwitch(callType: String){

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        var userId = userData?.id
        receiverId?.let { it1 ->
            userId?.let {
                femaleUsersViewModel.callFemaleUser(
                    it1, it,callType,1
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
        // B069 — observer attach timestamp; payloads older than this are
        // leftovers from a prior call and must not pop a dialog now.
        val callSwitchObserverStartedAtMs = System.currentTimeMillis()
        FcmUtils.updatedCallSwitch.observe(this, androidx.lifecycle.Observer { updatedCallSwitch ->
            // B082 — guard against the switch-call dialog being raised after
            // the call has already ended (late FCM during the finish window).
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

                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                var userid = userData?.id

                if (switchType=="switchToVideo"){
                    if (isAudioCallGoing){
                    switchCallID = newCallId

                    switchDialog?.dismiss()

                    // B069 follow-up — track explicit response so outside-tap = decline.
                    var responded = false
                    switchDialog = AlertDialog.Builder(this)
                        .setTitle("Switch to Video Call ?")
                        .setMessage("$receiverName requested for video call")
                        .setPositiveButton("Confirm") { _, _ ->
                            val remainingTime = binding.tvRemainingTime?.text.toString()
                            // B-v1110 #1 (sibling) — guard "Connecting…" parse; empty list skips the size==3 block.
                            val timeParts = remainingTime.split(":").mapNotNull { it.trim().toIntOrNull() }
                            if (timeParts.size == 3) {
                                val hours = timeParts[0]
                                val minutes = timeParts[1]
                                val seconds = timeParts[2]
                                val totalSeconds = (hours * 3600) + (minutes * 60) + seconds
                                if (totalSeconds > 360) {
                                    if (userid != null && switchCallID != 0) {
                                        responded = true
                                        Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
                                        sendCallAcceptNotification(userid, receiverId, "video", "VideoAccepted")
                                        FcmUtils.clearCallSwitch()
                                        Log.d("NewCallID", "$newCallId")
                                        stopCountdown()
                                        isSwitchingToVideo = false
                                        enableVideoCall()
                                    }
                                } else {
                                    responded = true
                                    Toast.makeText(this, "$receiverName don't have enough coins", Toast.LENGTH_SHORT).show()
                                    FcmUtils.clearCallSwitch()
                                }
                            }
                        }
                        .setNegativeButton("Decline") { d, _ ->
                            responded = true
                            userid?.let {
                                sendCallAcceptNotification(it, receiverId, "video", "SwitchDeclined")
                            }
                            d.dismiss()
                            FcmUtils.clearCallSwitch()
                        }
                        .create().apply {
                            setOnDismissListener {
                                if (!responded && !isFinishing && !isDestroyed) {
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

                    var responded = false
                    // B068 — modal. Outside-tap and back can't dismiss; only
                    // Confirm/Decline close the dialog. setOnDismissListener
                    // below remains as a backstop for activity teardown.
                    switchDialog = AlertDialog.Builder(this)
                        .setTitle("Switch to audio Call ?")
                        .setMessage("$receiverName requested for audio call")
                        .setCancelable(false)
                        .setPositiveButton("Confirm") { _, _ ->
                            responded = true
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
                            responded = true
                            userid?.let {
                                sendCallAcceptNotification(it, receiverId, "audio", "SwitchDeclined")
                            }
                            d.dismiss()
                            FcmUtils.clearCallSwitch()
                        }
                        .create().apply {
                            setOnDismissListener {
                                if (!responded && !isFinishing && !isDestroyed) {
                                    userid?.let { uid ->
                                        sendCallAcceptNotification(uid, receiverId, "audio", "SwitchDeclined")
                                    }
                                    FcmUtils.clearCallSwitch()
                                }
                                switchDialog = null
                            }
                            show()
                        }

                }


                FcmUtils.clearCallSwitch()


            }}
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


        runOnUiThread {
            // Clear any existing views first
            binding.localVideoViewContainer.removeAllViews()
            binding.remoteVideoViewContainer.removeAllViews()
            
            // Remove background to show video
            binding.main.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // Enable video module
            agoraEngine?.enableVideo()

            // Tester report: creator with broken camera crashed when audio
            // upgraded to video. Same fallback as the initial join — keep
            // video disabled on this side and let the peer's avatar
            // skeleton (B058) carry the visual. See CameraAvailability.
            val cameraOk = com.gmwapp.hima.utils.CameraAvailability.isCameraAvailable(this)
            if (cameraOk) {
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
            } else {
                Log.w("CameraFallback", "FemaleVideo.enableVideoCall: camera unavailable, video upgrade silent on our side")
                agoraEngine?.enableLocalVideo(false)
                agoraEngine?.muteLocalVideoStream(true)
                binding.localCardView.visibility = View.GONE
                binding.localVideoViewContainer.visibility = View.GONE
                showMessage(getString(R.string.call_no_camera_fallback))
            }

            binding.remoteVideoViewContainer.visibility = View.VISIBLE
            // B191 — restore the camera-flip button hidden by a prior
            // audio downgrade. Face-detection overlay manages its own
            // visibility once face detection resumes.
            binding.btnCameraFlip.visibility = if (cameraOk) View.VISIBLE else View.GONE
            if (cameraOk) applySavedLocalPreviewPosition()

            // Bring video containers to front
            binding.localVideoViewContainer.bringToFront()
            binding.remoteVideoViewContainer.bringToFront()
            binding.localCardView.bringToFront()
            
            Log.d("enableVideoCall", "Local video setup complete from video activity")

            // Setup remote video view - will be properly rendered when remote stream is available
            remoteSurfaceView = SurfaceView(this)
            remoteSurfaceView!!.setZOrderMediaOverlay(false)
            remoteSurfaceView!!.visibility = View.VISIBLE
            binding.remoteVideoViewContainer.addView(remoteSurfaceView)
            agoraEngine!!.setupRemoteVideo(
                VideoCanvas(
                    remoteSurfaceView,
                    VideoCanvas.RENDER_MODE_HIDDEN,
                    videoUid
                )
            )
            binding.remoteVideoViewContainer.visibility = View.VISIBLE
            binding.remoteVideoViewContainer.bringToFront()
            
            Log.d("enableVideoCall", "Video enabled from video call activity for videoUid: $videoUid")

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


        runOnUiThread {
            // Stop publishing and capturing camera so bandwidth + camera LED
            // turn off when the user goes to audio mode. Mirror audio-only
            // ChannelMediaOptions so Agora suppresses the camera track.
            agoraEngine?.muteLocalVideoStream(true)
            agoraEngine?.enableLocalVideo(false)
            agoraEngine?.updateChannelMediaOptions(ChannelMediaOptions().apply {
                autoSubscribeAudio = true
                autoSubscribeVideo = false
                // 2026-05-22 v18 — preserve mute state across video→audio switch
                publishMicrophoneTrack = !isMuted
                publishCameraTrack = false
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            })
            // 2026-05-22 v19 — belt & suspenders: also enforce device-level mute.
            agoraEngine?.muteLocalAudioStream(isMuted)
            agoraEngine?.stopPreview()
            agoraEngine?.disableVideo()
            Log.d("AgoraTiming", "FemaleVideo switched to AUDIO at ${System.currentTimeMillis()}")

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

            // B191 — enableVideoCall() flipped these for video mode but
            // enableAudioCall() forgot to reverse them. See
            // MaleVideoCallingActivity.enableAudioCall for full rationale.
            binding.btnCameraFlip.visibility = View.GONE
            binding.faceDetectionOverlay.root.visibility = View.GONE
            binding.main.setBackgroundColor(android.graphics.Color.BLACK)

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


    fun disableVideo(){
        Handler(Looper.getMainLooper()).post {
            if (isFinishing || isDestroyed) {
                return@post
            }

            // Any stale delayed transition should be cleared when face is lost again.
            cancelFacePreviewTransition()
            isShowingFacePreview = false
            
            // Keep local blackscreen hidden; face overlay now handles the full-screen UX.
            binding.blackscreen.visibility=View.GONE
            // While user is in no-face flow, prevent remote feed from bleeding through overlay.
            remoteSurfaceView?.visibility = View.GONE
            binding.remoteVideoViewContainer.visibility = View.GONE
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
                Log.d("FaceDetection", "Overlay not active, skipping staged face preview")
                return@post
            }

            // Avoid duplicate transitions while we are already resuming.
            if (isShowingFacePreview) {
                return@post
            }

            cancelFacePreviewTransition()
            isShowingFacePreview = true
            Log.d("FaceDetection", "Face detected - resuming call immediately")
            resumeCallAfterFaceDetection()
        }
    }
    
    private fun showCameraPreviewOnOverlay() {
        try {
            val overlayBinding = binding.faceDetectionOverlay
            val overlay = overlayBinding.root
            
            // FORCE overlay to be visible and on top
            overlay.visibility = View.VISIBLE
            overlay.bringToFront()
            overlay.elevation = 9999f
            overlay.translationZ = 9999f
            
            Log.d("FaceDetection", "Showing camera on purple overlay - STAYING HERE for 3 seconds")
            
            // Remove purple background completely - make it BLACK so camera shows
            overlay.setBackgroundColor(android.graphics.Color.BLACK)
            
            // Hide all UI elements except the text
            overlayBinding.personOutlineContainer.visibility = View.GONE
            overlayBinding.bottomFacePanel.visibility = View.GONE
            overlayBinding.scanIconHolder.visibility = View.GONE
            overlayBinding.tvFaceNotDetected.text = "Face Detected"
            overlayBinding.tvFaceNotDetected.visibility = View.VISIBLE
            
            // Show camera preview on the overlay (full screen)
            val cameraContainer = overlayBinding.cameraPreviewContainer
            cameraContainer.removeAllViews()
            cameraContainer.setBackgroundColor(android.graphics.Color.BLACK)
            
            // Create SurfaceView for camera
            localPreviewSurface = SurfaceView(this)
            localPreviewSurface?.setZOrderOnTop(false)
            localPreviewSurface?.setZOrderMediaOverlay(false)
            localPreviewSurface?.holder?.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            
            // Add to container with match parent for full screen
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            cameraContainer.addView(localPreviewSurface, layoutParams)
            
            // Make sure camera is visible and on top
            cameraContainer.visibility = View.VISIBLE
            cameraContainer.bringToFront()
            
            // Setup local video on the preview surface
            agoraEngine?.setupLocalVideo(
                VideoCanvas(
                    localPreviewSurface,
                    VideoCanvas.RENDER_MODE_HIDDEN,
                    0
                )
            )
            
            // Make sure preview is started
            agoraEngine?.startPreview()
            
            Log.d("FaceDetection", "Camera preview setup complete - overlay should show face for 3 seconds now")
            
        } catch (e: Exception) {
            Log.e("FemaleVideoCallingActivity", "Error showing camera preview", e)
            isShowingFacePreview = false
        }
    }
    
    private fun resumeCallAfterFaceDetection() {
        if (isFinishing || isDestroyed) {
            return
        }
        
        try {
            Log.d("FaceDetection", "Resuming call after face detection")
            
            // Unmute streams
            binding.blackscreen.visibility = View.GONE
            agoraEngine?.muteAllRemoteAudioStreams(false)
            agoraEngine?.muteLocalVideoStream(false)
            // Respect the user's mute: face-detection auto-resume must NOT re-open a
            // mic the user muted (was unconditional). Fixes "muted user still heard".
            if (!isMuted) agoraEngine?.muteLocalAudioStream(false)

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

            // First dismiss overlay, then setup local video in call view
            dismissNoFaceDetectedDialog()
            
            // Setup local video in normal call view immediately
            setupLocalVideoInCallView()
            
        } catch (e: Exception) {
            Log.e("FemaleVideoCallingActivity", "Error resuming call", e)
        }
    }
    
    private fun setupLocalVideoInCallView() {
        try {
            Log.d("FaceDetection", "Setting up local video in call view")
            
            // Clean up preview surface
            localPreviewSurface = null
            val overlayBinding = binding.faceDetectionOverlay
            overlayBinding.cameraPreviewContainer.removeAllViews()
            
            // Re-setup local video in the main call view
            binding.localVideoViewContainer.removeAllViews()
            val localView = SurfaceView(this)
            localView.setZOrderMediaOverlay(true)
            
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            binding.localVideoViewContainer.addView(localView, layoutParams)
            
            agoraEngine?.setupLocalVideo(
                VideoCanvas(
                    localView,
                    VideoCanvas.RENDER_MODE_HIDDEN,
                    0
                )
            )
            
            binding.localVideoViewContainer.visibility = View.VISIBLE
            binding.localCardView.visibility = View.VISIBLE
            applySavedLocalPreviewPosition()
            
            Log.d("FaceDetection", "Local video setup complete in call view")
            
        } catch (e: Exception) {
            Log.e("FemaleVideoCallingActivity", "Error setting up local video", e)
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
                remoteSurfaceView?.visibility = View.GONE
                binding.remoteVideoViewContainer.visibility = View.GONE

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

                localPreviewSurface = SurfaceView(this@FemaleVideoCallingActivity).apply {
                    setZOrderOnTop(false)
                    setZOrderMediaOverlay(false)
                    holder?.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                }

                val layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                cameraContainer.addView(localPreviewSurface, layoutParams)

                // Force local camera path for overlay preview.
                agoraEngine?.enableVideo()
                agoraEngine?.enableLocalVideo(true)
                agoraEngine?.muteLocalVideoStream(false)
                agoraEngine?.setupLocalVideo(
                    VideoCanvas(
                        localPreviewSurface,
                        VideoCanvas.RENDER_MODE_HIDDEN,
                        0
                    )
                )
                agoraEngine?.startPreview()
            } catch (e: Exception) {
                Log.e("FemaleVideoCallingActivity", "Cannot show face detection overlay", e)
            }
        }
    }

    private fun dismissNoFaceDetectedDialog() {
        Handler(Looper.getMainLooper()).post {
            if (isFinishing || isDestroyed) {
                return@post
            }
            
            try {
                Log.d("FaceDetection", "Dismissing face detection overlay NOW")
                
                // Reset flag
                isShowingFacePreview = false
                
                // Cancel any pending auto-dismiss
                cancelFacePreviewTransition()
                
                val overlayBinding = binding.faceDetectionOverlay
                
                // Clean up camera preview
                overlayBinding.cameraPreviewContainer.removeAllViews()
                overlayBinding.cameraPreviewContainer.visibility = View.GONE
                
                // Restore original overlay appearance
                overlayBinding.root.setBackgroundResource(R.drawable.face_detection_gradient_background)
                overlayBinding.personOutlineContainer.visibility = View.VISIBLE
                overlayBinding.bottomFacePanel.visibility = View.VISIBLE
                overlayBinding.scanIconHolder.visibility = View.VISIBLE
                overlayBinding.tvFaceNotDetected.text = "Face Not Detected"
                
                // Hide overlay
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
                Log.e("FemaleVideoCallingActivity", "Error dismissing overlay", e)
            }
        }
    }

    // 2026-05-23 v1066 — full-screen peer-avatar overlay DISABLED everywhere
    // per user request. See MaleVideoCallingActivity for rationale.
    private fun showRemoteAvatarSkeleton() {
        // Avatar overlay disabled per product decision (2026-06-26): the peer avatar
        // flashed as a "ghost" on every brief remote-video restart/mute (STARTING is
        // instant). Keep it GONE always — frozen/failed still shows the Reconnecting
        // pill, peer-mute still blurs; the tile just stays black instead of flashing.
        if (binding.ivRemoteAvatarSkeleton.visibility != View.GONE) {
            binding.ivRemoteAvatarSkeleton.visibility = View.GONE
        }
    }

    private fun hideRemoteAvatarSkeleton() {
        if (binding.ivRemoteAvatarSkeleton.visibility != View.GONE) {
            binding.ivRemoteAvatarSkeleton.visibility = View.GONE
        }
        // TC_007: clear the "Reconnecting…" pill once the remote video recovers,
        // BUT only if the watchdog isn't still counting down an own-network /
        // peer-stream stall. A transient remote-video DECODING while we're still
        // reconnecting would otherwise erase the live countdown the watchdog owns
        // (and the call is still about to auto-end). When the watchdog finally
        // disarms, its onArmedChanged hides the pill.
        if (!reconnectWatchdog.isArmed() && binding.reconnectBanner.visibility != View.GONE) {
            binding.reconnectBanner.visibility = View.GONE
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
        remoteSurfaceView?.visibility = View.VISIBLE
        binding.remoteVideoViewContainer.visibility = View.VISIBLE
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
