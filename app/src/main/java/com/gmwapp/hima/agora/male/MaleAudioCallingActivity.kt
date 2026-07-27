package com.gmwapp.hima.agora.male

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityMaleAudioCallingBinding
import com.gmwapp.hima.media.RtcTokenBuilder2
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.Constants
import android.Manifest
import android.animation.AnimatorListenerAdapter
import android.app.Dialog
import android.content.Intent
import android.content.res.Resources
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceView
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
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.gmwapp.hima.mmp.MmpClient
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.PaymentWebViewActivity
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.activities.RatingActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.adapters.GiftAdapter
import com.gmwapp.hima.agora.FaceDetectVideoFrameObserver
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.audio.CallAudioModerationSession
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import android.telecom.DisconnectCause
import com.gmwapp.hima.agora.GiftBottomSheetFragment
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.agora.services.CallingService
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.AppEventLogger
import com.gmwapp.hima.utils.CallAudioFocusHelper
import com.gmwapp.hima.utils.CallAudioRouter
import com.gmwapp.hima.utils.CallPhoneStateHelper
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.GiftImageViewModel
import com.gmwapp.hima.viewmodels.GiftViewModel
import com.gmwapp.hima.retrofit.responses.GiftData
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import com.gmwapp.hima.retrofit.responses.CallEndReason
import com.gmwapp.hima.retrofit.responses.CallEndedBy
import com.gmwapp.hima.viewmodels.CallDropStatusViewModel
import com.gmwapp.hima.viewmodels.CallStatusViewModel
import com.gmwapp.hima.viewmodels.LudoFcmViewModel
import com.gmwapp.hima.workers.CallUpdateWorker
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import io.agora.rtc2.video.VideoCanvas
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
//import org.vosk.Model
//import org.vosk.Recognizer
//import org.vosk.android.RecognitionListener
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.abs

@AndroidEntryPoint
class MaleAudioCallingActivity : AppCompatActivity() {

    private lateinit var channelName: String
    var receiverId = 0
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()


    private var femaleUserId: String? = null

    private var isSwitchRequestPending = false


    var isClicked: Boolean = false
    var isAudioCallIdReceived: Boolean = false
    private val accountViewModel: AccountViewModel by viewModels()


    lateinit var binding: ActivityMaleAudioCallingBinding
    private val profileViewModel: ProfileViewModel by viewModels()
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()

    private lateinit var giftAdapter: GiftAdapter
    private val giftImageViewModel: GiftImageViewModel by viewModels()
    private val giftViewModel: GiftViewModel by viewModels()
    // Inline quick-gift row state (mirrors GiftBottomSheetFragment's throttle).
    private var lastQuickGiftAt = 0L
    private val quickGiftCooldownMs = 1000L
    private var quickGiftSentGuard = 1

    private val userAvatarViewModel: UserAvatarViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()
    private val callDropStatusViewModel: CallDropStatusViewModel by viewModels()
    private val callStatusViewModel: CallStatusViewModel by viewModels()
    private val isCaller: Boolean by lazy { intent.getBooleanExtra("IS_CALLER", false) }

    /**
     * Last explicit audio-route choice (button tap, BT plug, or save/restore).
     * Kept separately from `isSpeakerOn` because Earpiece and Bluetooth both
     * leave speakerphone off — only `currentAudioRoute` distinguishes them.
     */
    private var currentAudioRoute: com.gmwapp.hima.utils.CallAudioRouter.AudioRoute =
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE
    private val ludoFcmViewModel: LudoFcmViewModel by viewModels()
    // I021 — VM for the low-balance banner's "first 3 packages" prefetch.
    private val walletViewModel: com.gmwapp.hima.viewmodels.WalletViewModel by viewModels()

    private var isSwitchingToAudio = false // ✅ Prevent multiple calls
    private var isSwitchingToVideo = false // ✅ Prevent multiple calls

    // I021 — banner instance + flag flipped when we hand off to WalletActivity
    // so onResume knows to refresh the remaining-time and (optimistically) hide
    // the banner. Banner re-shows on the next sub-60s tick if the recharge
    // didn't extend the timer enough.
    private var lowBalanceBanner: com.gmwapp.hima.utils.LowBalanceBanner? = null
    private var pendingWalletReturn: Boolean = false


    private var remoteSurfaceView: SurfaceView? = null
    private var localPreviewSurface: SurfaceView? = null
    private var localPreviewOffsetX = Float.NaN
    private var localPreviewOffsetY = Float.NaN
    private var localPreviewTouchOffsetX = 0f
    private var localPreviewTouchOffsetY = 0f
    private var localPreviewDragStartX = 0f
    private var localPreviewDragStartY = 0f
    private var isDraggingLocalPreview = false
    private var isRemoteBlurVisible = false
    private var pendingRemoteBlurHide = false

    var switchCallID = 0
    // Audio→video switch needs a fresh call_id from getCallIdforCallSwitch().
    // If the user confirms the switch before that async API returns (or it
    // transiently fails — demohima returned null twice in field logs), switchCallID
    // is still 0. Rather than dead-ending with a bare "Try Again", we park the
    // confirmed switch here and proceed automatically once the id lands.
    private var pendingVideoSwitchSeconds: Int? = null
    private val switchCallIdHandler = Handler(Looper.getMainLooper())
    private var switchCallIdTimeoutRunnable: Runnable? = null
    // Guard so repeated switch attempts don't stack multiple observers on the
    // shared callFemaleUserResponseLiveData.
    private var switchCallIdObserverRegistered = false
    // B069 storm fix: register these once per activity (see female side) so
    // duplicate observers don't stack on shared LiveData and multiply churn.
    private var notificationSentObserverRegistered = false
    private var callSwitchAcceptanceObserverRegistered = false
    private var isVideoCallGoing: Boolean = false
    // B18 (switch-to-video parity): auto-hide the video-mode chrome after 10s idle.
    // Only active while isVideoCallGoing; normal audio mode is unaffected.
    private var videoChromeVisible = true
    private val CHROME_AUTOHIDE_MS = 10_000L
    private val chromeAutoHideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val chromeAutoHideRunnable = Runnable { setVideoChromeVisible(false) }

    private var switchDialog: AlertDialog? = null  // Track current dialog
    private var faceDialog: Dialog? = null

//    private lateinit var model: Model
//    private lateinit var recognizer: Recognizer

    private var appId: String? = null // Will be received from backend
    private val expirationTimeInSeconds = 3600
    private var token: String? = null
    private val uid = 0
    private var videoUid = 0
    private var isJoined = false
    private var agoraEngine: RtcEngine? = null
    private var callModerationCaptureManager: com.gmwapp.hima.utils.CallModerationCaptureManager? = null

    // In-call "on hold" signaling over the Agora data stream — tells the peer
    // when we step away for a cellular / VoIP call so they see a dedicated
    // "‹Name› is on hold" banner. See CallHoldSignal.
    private val holdSignal = com.gmwapp.hima.utils.CallHoldSignal { agoraEngine }

    // B127: real-time RECORD_AUDIO revoke listener; started on join, stopped on teardown.
    private var micWatcher: com.gmwapp.hima.utils.MicPermissionWatcher? = null

    // Local-mic extraction foundation for a future transcription provider. Chunks are currently
    // consumed in memory only: no file, upload, transcript, AI call or warning is created.
    private var audioModerationSession: CallAudioModerationSession? = null

    // Tester report: in-call timer drifted ~60s between the two sides over a
    // few minutes. B141 anchored the math to serverNowMs at each fetch, but
    // BETWEEN fetches both sides relied on local CountDownTimer which loses
    // precision under doze / background throttling / small system jitter,
    // and the FCM-driven refresh path (remainingTimeUpdated) silently breaks
    // if that push is lost. This handler self-paces a re-fetch every 30s as
    // a safety net — drift can't accumulate past one interval, and both
    // sides re-converge on the server's authoritative remaining_time.
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
    companion object {
        private const val TIMER_RESYNC_INTERVAL_MS = 30_000L
    }

    var receiverName = ""

    private var isMuted = false
    // Peer's current mic-mute state (from onUserMuteAudio) so the top video-mode
    // indicator reflects it correctly across an audio<->video switch.
    private var isPeerAudioMuted = false
    private var isSpeakerOn = true

    // B_009 follow-up — speaker toggle responsiveness. AudioManager's
    // clearCommunicationDevice()/setCommunicationDevice() are synchronous binder
    // calls into AudioService that can block the caller for hundreds of ms
    // (seconds on some OEM builds). Running them on the main thread stalled the
    // frame, so the optimistic speaker-icon flip could not be painted until they
    // returned — that is what made the speaker button feel laggy next to mute,
    // which only calls the (fast) muteLocalAudioStream. Single thread so rapid
    // toggles still apply in order.
    private val audioRouteExecutor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor()

    private var audioFocusHelper: CallAudioFocusHelper? = null
    private var audioRouter: CallAudioRouter? = null
    private var phoneStateHelper: CallPhoneStateHelper? = null
    private var btWatcher: com.gmwapp.hima.utils.BluetoothCallWatcher? = null
    private var wiredWatcher: com.gmwapp.hima.utils.WiredHeadsetWatcher? = null
    // B062 + B064 — auto-end after 30s of RECONNECTING/FAILED, and show a
    // live countdown on the reconnect banner so the user knows when the
    // call will give up instead of staring at an indefinite "Reconnecting…"
    // pill. The banner visibility is still owned by CallQualityUi; we only
    // update the text here.
    private val reconnectWatchdog = com.gmwapp.hima.utils.ReconnectWatchdog(
        onTick = { secondsRemaining ->
            binding.reconnectBanner.text =
                peerReconnectingTextOrNull() ?: "Reconnecting… ${secondsRemaining}s"
        },
        // Peer-side reconnect pill: surface "<peer> is reconnecting…" when the
        // PEER's audio has stalled / gone offline (gated by the watchdog's 3.5s
        // debounce so brief jitter never flashes it). Our OWN net loss already
        // shows CallNetLossBanner's red "No internet" banner, so this pill is
        // peer-only — that's why the tablet showed reconnecting but the phone
        // side showed nothing on an audio call.
        onArmedChanged = { armed ->
            runOnUiThread {
                val txt = peerReconnectingTextOrNull()
                if (armed && txt != null) {
                    binding.reconnectBanner.text = txt
                    binding.reconnectBanner.visibility = View.VISIBLE
                } else {
                    binding.reconnectBanner.visibility = View.GONE
                }
            }
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

    /**
     * Text for the peer-reconnecting pill, or null when WE are the down side
     * (own-network loss is shown by CallNetLossBanner, not this pill). Names the
     * peer when known, else falls back to a plain "Reconnecting…".
     */
    private fun peerReconnectingTextOrNull(): String? =
        if (reconnectWatchdog.isPeerDown())
            (if (receiverName.isBlank()) getString(R.string.call_reconnecting)
             else getString(R.string.call_peer_reconnecting, receiverName))
        else null
    private var mutedByInterrupt = false
    // B196 false-positive fix: tracks ONLY a real cellular (SIM) call — the sole
    // interrupt source allowed to surface the "On hold — phone call in progress"
    // banner + peer HOLD signal. Audio-focus interrupts (notifications, the
    // assistant, other apps, VoIP) still mute audio but never set this, so they
    // no longer raise a phantom on-hold banner when there is no phone call.
    private var cellularInterrupt = false

    var maleUserId = 0
    private var storedRemainingTime: String? = null
    private var storedVideoRemainingTime: String? = null

    private var countDownTimer: CountDownTimer? = null
    // B4/TC_006+TC_021: epoch-ms when remaining time first read 0 (0 = not currently zero).
    // Distinguishes a transient zero (rescued by the next positive resync) from a sustained
    // zero (genuine coin-exhaustion → end the call). Reset on any positive read.
    private var zeroRemainingSinceMs = 0L


    private var startTime: String = ""
    private var endTime: String = ""
    // B110: monotonic millis snapshot taken at onUserJoined so the hangup
    // path can compute an accurate durationSeconds for saveCallStatus.
    // Without this, durationSeconds defaulted to null on the backend, the
    // call was recorded with duration=0, and the male's Recent tab classified
    // his own outgoing call as "Missed."
    private var callStartMillis: Long = 0L

    var blockWords: List<String> = emptyList()
    var isBlockWordDetected : Boolean = false

    var callId: Int = 0
    private var pendingLudoAction: String? = null
    private var currentLudoInviteId: String? = null


    private var isRemoteUserJoined = false
    // Fire the "call_started" notification conversion at most once per call session
    // (onUserJoined can re-fire on reconnect, possibly under a newer notification id).
    private var callStartedConversionFired = false

    // CALLER_ACCEPT_RESEND_2026_06_30 — the receiver fires "accepted" to the caller
    // ONCE on tap; a single dropped FCM strands the caller on "Connecting" (then a
    // black screen / "couldn't connect"). As the accepting side (!isCaller), keep
    // re-sending "accepted" every 1.5s until the caller actually joins the channel
    // (onUserJoined) or we hit the cap — so one unlucky dropped push no longer fails
    // the call. Idempotent on the caller (it finishes on the first "accepted").
    // Self-stops on connect/destroy. Connecting screen, billing, teardown untouched.
    private val acceptResendHandler = Handler(Looper.getMainLooper())
    private var acceptResendCount = 0
    private val maxAcceptResends = 5
    private val acceptResendIntervalMs = 1500L
    private val acceptResendRunnable = object : Runnable {
        override fun run() {
            if (isCaller || isRemoteUserJoined || isFinishing || isDestroyed) return
            if (acceptResendCount >= maxAcceptResends) return
            if (receiverId > 0 && !channelName.isNullOrEmpty()) {
                val myId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
                if (myId > 0) {
                    acceptResendCount++
                    fcmNotificationViewModel.sendNotification(
                        senderId = myId,
                        receiverId = receiverId,
                        callType = "audio",
                        channelName = channelName,
                        message = "accepted"
                    )
                    Log.d("CallStatus", "AcceptResend $acceptResendCount/$maxAcceptResends -> peer=$receiverId ch=$channelName")
                }
                acceptResendHandler.postDelayed(this, acceptResendIntervalMs)
            }
        }
    }
    private fun startAcceptResend() {
        if (isCaller) return
        acceptResendHandler.removeCallbacks(acceptResendRunnable)
        acceptResendCount = 0
        acceptResendHandler.postDelayed(acceptResendRunnable, acceptResendIntervalMs)
    }
    private fun stopAcceptResend() {
        acceptResendHandler.removeCallbacks(acceptResendRunnable)
    }
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
                    cancelTimeoutTracking()
                    // B043/B044 — the prior "User did not join" wording read like
                    // the remote user actively refused. By this point the peer
                    // already accepted (otherwise we wouldn't be in the calling
                    // activity); the failure is the Agora session never
                    // materialising — could be either side's network or app
                    // state. Without a server-provided reason field we shouldn't
                    // attribute fault.
                    Toast.makeText(this@MaleAudioCallingActivity,"Couldn't connect — please try again", Toast.LENGTH_LONG).show()
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

    private val REQUESTED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )


    private fun checkSelfPermission(): Boolean {
        return REQUESTED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showMessage(message: String?) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAudioSDKEngine() {
        if (appId == null) {
            Log.e("AgoraToken", "AppId is null, cannot initialize engine")
            showMessage("Failed to initialize call. Please try again.")
            finish()
            return
        }
        // Grab EXCLUSIVE audio focus BEFORE Agora touches the audio HAL so any
        // already-playing media (Spotify / YouTube / etc.) is paused before
        // call audio starts. Previously the focus request was the LAST step in
        // engine setup, so for the brief window between enableAudio() and the
        // focus request, Spotify kept streaming and mixed with call audio
        // (B139). [com.gmwapp.hima.utils.CallAudioFocusHelper] is idempotent
        // — safe even though [setupCallInterruptHandlers] also calls it later.
        setupCallInterruptHandlers()
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId!!
            config.mEventHandler = mRtcEventHandler
            // Wait out any straggling RtcEngine.destroy() from a prior call before
            // creating — prevents the cross-call engine-overlap black-screen race.
            agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.createEngineSafely(config, "MaleAudioCalling")

            // Enable only audio module (Disable video)
            agoraEngine!!.enableAudio()
            // Configure audio profile BEFORE joinChannel to avoid mid-session track reset.
            // B186: SPEECH_STANDARD pinned codec to 32 kHz mono / 18 kbps;
            // on OEMs whose mic captured outside that profile, codec negotiation
            // failed and both sides connected silent. DEFAULT lets Agora pick per
            // the channel profile (COMMUNICATION here).
            agoraEngine!!.setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT, Constants.AUDIO_SCENARIO_DEFAULT)
            audioModerationSession?.dispose()
            audioModerationSession = CallAudioModerationSession(
                context = this,
                callIdProvider = { callId },
                engineProvider = { agoraEngine },
            ).also { it.prepare() }
            // B037: smoothFactor 1 (not 3) so the speak-wave reacts to actual
            // speech bursts instead of a 600ms moving average that hid soft
            // voices entirely; threshold lowered to 30 in onAudioVolumeIndication.
            agoraEngine!!.enableAudioVolumeIndication(200, 1, true)
            // Set the SDK's default audio route + explicit current route so users hear
            // audio in the expected output immediately (also helps Bluetooth/headset).
            agoraEngine!!.setDefaultAudioRoutetoSpeakerphone(true)
            agoraEngine!!.setEnableSpeakerphone(isSpeakerOn)
            Log.d("AgoraTiming", "MaleAudio setupAudioSDKEngine done at ${System.currentTimeMillis()}")

            audioRouter?.release()
            audioRouter = CallAudioRouter(this).also { router ->
                router.init()
                // Reflect plug/unplug events while the call is active so the
                // speaker icon never lies about the real audio route.
                router.setRouteChangeListener { refreshAudioRouteIcon() }
            }
            val btNow = audioRouter?.isBluetoothConnected() == true
            val wiredNow = audioRouter?.isWiredHeadsetConnected() == true
            val initial = when {
                btNow -> com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH
                // A wired headset at call start owns the route — don't show
                // speaker-on just because the saved-state happened to be ON.
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
                // B196 — true second arg flips the on-hold banner visible so
                // the user knows their Hima call is paused while the SIM call
                // is active. Hidden again when the SIM call ends.
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
                    // BT headset just plugged in mid-call → auto-route to it.
                    runOnUiThread {
                        applyAudioRoute(com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH)
                    }
                } else if (currentAudioRoute == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH) {
                    // BT dropped while we were routing to it → fall back to Speaker
                    // so the user isn't left staring at a silent call.
                    runOnUiThread {
                        applyAudioRoute(com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER)
                    }
                }
            }.also { it.register() }
        }
        if (wiredWatcher == null) {
            wiredWatcher = com.gmwapp.hima.utils.WiredHeadsetWatcher(this) { plugged ->
                Log.d(
                    "CallAudioRoute",
                    "Activity.wiredChange plugged=$plugged currentRoute=$currentAudioRoute"
                )
                if (plugged && currentAudioRoute == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER) {
                    // Headphones plugged in while speaker was on — the OS
                    // re-routes audio to the wired output but our speaker
                    // icon would otherwise stay lit (B048). Reflect reality.
                    runOnUiThread {
                        applyAudioRoute(com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE)
                    }
                }
                // On unplug we don't auto-restore speaker: matches native
                // phone/WhatsApp behavior. The user re-taps the icon if
                // they want speaker back.
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
                    // Stop SENDING our mic to the other party.
                    if (!isMuted) agoraEngine?.muteLocalAudioStream(true)
                    // B148: stop PLAYING the remote audio locally — Spotify (resumed mid-call)
                    // mixes with the caller's voice out of the same speaker otherwise.
                    // B001: same effect when a GSM/WhatsApp call interrupts.
                    agoraEngine?.muteAllRemoteAudioStreams(true)
                }
                // B196 false-positive fix: only a real cellular (SIM) call may
                // raise the "On hold — phone call in progress" banner and tell
                // the peer we've stepped away. Audio-focus interrupts mute audio
                // above but must NOT claim a phone call is in progress.
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
                }
            }
            audioModerationSession?.setPaused(isMuted || mutedByInterrupt)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Route the volume rocker to the in-call voice stream so volume up/down
        // actually adjusts call audio while the call screen is up (B149).
        // Default is STREAM_MUSIC, which has no effect on Agora's call audio.
        volumeControlStream = AudioManager.STREAM_VOICE_CALL
        // Grab EXCLUSIVE audio focus FIRST — before Agora setup / joinChannel —
        // so background media (Spotify, YouTube, etc.) pauses immediately and
        // doesn't get a chance to mix with the call audio during the brief
        // engine-init window (B139). setupCallInterruptHandlers' null-check
        // makes this safe to do twice.
        if (audioFocusHelper == null) {
            audioFocusHelper = CallAudioFocusHelper(
                context = this,
                onFocusLost = { muteForInterrupt(true) },
                onFocusGained = { muteForInterrupt(false) }
            ).also { it.request() }
        }
        // Flip in-call flag ASAP so any racing FCM / OneSignal push about to
        // post another ring sees it and backs off. Sweep whatever is already
        // in the tray in case a call push landed a moment before we opened.
        BaseApplication.getInstance()?.markCallActive()
        BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
        enableEdgeToEdge()
        binding = ActivityMaleAudioCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // NET-002/003: "No internet" banner on a REAL device net loss only (not blips).
        com.gmwapp.hima.utils.CallNetLossBanner.attach(this)

        // UI-only ambience: rotating gradient ring + drifting "smoke" glows.
        run {
            val d = resources.displayMetrics.density
            fun loop(a: android.animation.ObjectAnimator, dur: Long) {
                a.duration = dur
                a.repeatCount = android.animation.ObjectAnimator.INFINITE
                a.repeatMode = android.animation.ObjectAnimator.REVERSE
                a.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                a.start()
            }
            fun driftGlow(v: android.view.View, dx: Float, dy: Float, durX: Long, durY: Long) {
                loop(android.animation.ObjectAnimator.ofFloat(v, "translationX", dx), durX)
                loop(android.animation.ObjectAnimator.ofFloat(v, "translationY", dy), durY)
                loop(android.animation.ObjectAnimator.ofFloat(v, "scaleX", 1.28f), durX + 1500)
                loop(android.animation.ObjectAnimator.ofFloat(v, "scaleY", 1.28f), durY + 1500)
                loop(android.animation.ObjectAnimator.ofFloat(v, "alpha", 0.45f, 1f), durX)
            }
            // Avatar rim is now a subtle static brand gradient (matched on both
            // avatars), so no rotation — it sits calm behind the photo.
            driftGlow(binding.glowTl, 85f * d, 95f * d, 6200, 8000)
            driftGlow(binding.glowBr, -82f * d, -90f * d, 7000, 9200)
        }
        // B042: show "Connecting..." instead of stuck 00:00:00 while we wait
        // for the peer to join the Agora channel. startCountdown() overwrites
        // this on its first tick once onUserJoined() fires.
        binding.tvRemainingTime?.text = "Connecting..."

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
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        Glide.with(this)
            .load(R.drawable.gift_png)
            .into(binding.ivGift)


        showGreyScreen()

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        if (userData != null) {
            maleUserId = userData.id
        }

        // I021 — instantiate the low-balance banner. View IDs come from the
        // <include layout="@layout/banner_low_balance"> in the activity layout.
        // The package catalog prefetch is deferred to onUserJoined so we don't
        // waste a network call if the call never actually connects.
        lowBalanceBanner = com.gmwapp.hima.utils.LowBalanceBanner(
            activity = this,
            rootView = findViewById(R.id.low_balance_banner_root),
            chipContainer = findViewById(R.id.chip_container),
            goToWalletTextView = findViewById(R.id.tv_go_to_wallet),
            walletViewModel = walletViewModel,
            userId = maleUserId,
            onLaunchedWallet = { pendingWalletReturn = true }
        )


        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        receiverId = intent.getIntExtra("RECEIVER_ID", -1)
        callId = intent.getIntExtra("CALL_ID", 0)
        // Bug #1 fix (2026-05-25): persist peer id so MyFirebaseMessagingService
        // can match incoming switchToVideo/switchToAudio FCMs (it checks
        // senderId == BaseApplication.getSenderId(); on incoming calls this
        // was set by the FCM handler, but on male-initiated outbound calls
        // nothing was setting it — so the receiver's switch request was
        // silently dropped at line 876 of MyFirebaseMessagingService).
        if (receiverId > 0) BaseApplication.getInstance()?.saveSenderId(receiverId)
        // CALLER_ACCEPT_RESEND_2026_06_30 — if we're the accepting side, keep nudging
        // the caller with "accepted" until they join (no-op for the caller side).
        startAcceptResend()
        Log.d(
            "MaleAudioCallingLog",
            "Channel: $channelName, Receiver: $receiverId, callId : $callId"
        )
        Log.d("AgoraTiming", "MaleAudio onCreate at ${System.currentTimeMillis()}")

        // Use pre-fetched token from connecting/accept screen if available, else fetch from backend
        val intentToken = intent.getStringExtra("AGORA_TOKEN")
        val intentAppId = intent.getStringExtra("AGORA_APP_ID")
        if (!intentToken.isNullOrEmpty() && !intentAppId.isNullOrEmpty()) {
            Log.d("AgoraTiming", "MaleAudio using pre-fetched token at ${System.currentTimeMillis()}")
            token = intentToken
            appId = intentAppId
            if (!checkSelfPermission()) {
                ActivityCompat.requestPermissions(
                    this@MaleAudioCallingActivity,
                    REQUESTED_PERMISSIONS,
                    PERMISSION_REQ_ID
                )
            } else {
                setupAudioSDKEngine()
                joinChannel(binding.JoinButton)
            }
        } else {
            getAgoraTokenFromBackend()
        }

        onAddcoinClicked()
        // B151: 500 ms debounce on the mute toggle so rapid taps can't outrun
        // Agora's muteLocalAudioStream ack and leave the icon out of sync with
        // the actual mic state.
        // U-06: 250ms (not the 500ms default) — the longer window swallowed
        // deliberate mute/unmute taps so the button felt unresponsive (~50% miss).
        binding.btnMuteUnmute.setOnSingleClickListener(debounceMs = 250L) {
            toggleMute()
        }

        // Debounced — the audio-routing chain (Telecom IPC + AudioManager
        // clear/set + Agora SDK + verify read-back) is multi-step and a
        // rapid double-tap would queue redundant work and leave state in
        // an unpredictable mid-flip.
        // U-06: trimmed to 250ms — still debounces the multi-step routing chain
        // against rapid double-taps, but no longer swallows deliberate taps.
        binding.btnSpeaker.setOnSingleClickListener(debounceMs = 250L) {
            onSpeakerButtonClicked()
        }

        endcallBtn()
        onBackPressedBtn()


//        onMenuClicked()
        avatarObservers()
        userAvatarViewModel.getUserAvatar(receiverId)

        userData?.let { setMyAvatar(it.image, it.name) }
        setupIplTeamBadges()

        handleCallSwitch()

        observeCallSwitchRequest()
        setupLocalPreviewDrag()

        giftIconClicked()
        setupQuickGifts()
        getBlockWords()
        if (com.gmwapp.hima.utils.FeatureFlags.LUDO_ENABLED) {
            setupLudoInviteFlow()
        } else {
            // Feature disabled — hide the in-call entry point; setupLudoInviteFlow()
            // also registers the FcmUtils.ludoEvent observer, so skipping it means
            // stray Ludo pushes can't surface the receive-invite dialog either.
            binding.ludoButtonCard.visibility = View.GONE
        }
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
        applyPlayLudoVisibility(
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.play_ludo ?: false
        )

        profileViewModel.getUserLiveData.observe(this) { response ->
            val fresh = response?.data ?: return@observe
            // B075 — match the female-side ludo refresh: preserve the user's
            // toggle / DND intent so a mid-call refresh can't clobber it.
            BaseApplication.getInstance()?.getPrefs()?.setUserDataPreservingLocalIntent(fresh)
            applyPlayLudoVisibility(fresh.play_ludo ?: false)
        }
        if (maleUserId != 0) {
            profileViewModel.getUsers(maleUserId)
        }

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

        // Server-driven force-end observer. Backend pushes `callEndedNoCoins`
        // FCM when the male's coins run out during an active call; hangs us
        // up immediately if the signal matches our current call, closing the
        // gap where a stuck client countdown could let the male over-talk
        // past his balance (B184 follow-up).
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

    private fun applyPlayLudoVisibility(enabled: Boolean) {
        binding.ludoButtonCard.visibility = if (enabled) View.VISIBLE else View.GONE
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

    // Agora token fetch is single-shot and the prod backend (DB-overloaded
    // himaapp.in) intermittently returns slow / blank app_id / a transient error.
    // The old code finish()ed the call on the FIRST miss — the user saw the call
    // "connect" then go black and auto-disconnect within ~1s. Retry a few times
    // with a short backoff before giving up so one token blip no longer drops the
    // call. Shared shape across all four calling activities.
    private var agoraTokenAttempts = 0
    private val maxAgoraTokenAttempts = 3
    private val agoraTokenRetryDelayMs = 600L
    private fun retryOrFailAgoraToken(reason: String) {
        if (isFinishing || isDestroyed) return
        if (agoraTokenAttempts < maxAgoraTokenAttempts) {
            agoraTokenAttempts++
            Log.w("AgoraToken", "token fetch failed ($reason) — retry $agoraTokenAttempts/$maxAgoraTokenAttempts in ${agoraTokenRetryDelayMs}ms")
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    agoraViewModel.getAgoraToken(channelName, uid, "publisher", expirationTimeInSeconds)
                }
            }, agoraTokenRetryDelayMs)
        } else {
            Log.e("AgoraToken", "token fetch failed ($reason) — exhausted $maxAgoraTokenAttempts retries, ending call")
            showMessage("Failed to initialize call. Please try again.")
            finish()
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
                    retryOrFailAgoraToken("appId-empty")
                    return@observe
                }
                agoraTokenAttempts = 0  // success — reset for any future re-fetch
                Log.d("AgoraToken", "Token and AppId received from backend")
                
                // Request permissions if not granted
                if (!checkSelfPermission()) {
                    ActivityCompat.requestPermissions(
                        this@MaleAudioCallingActivity,
                        REQUESTED_PERMISSIONS,
                        PERMISSION_REQ_ID
                    )
                } else {
                    setupAudioSDKEngine()
                    joinChannel(binding.JoinButton)
                }
            } else {
                Log.e("AgoraToken", "Failed to get token: ${response?.message}")
                retryOrFailAgoraToken("token-failed")
            }
        }

        // Observe errors
        agoraViewModel.agoraTokenErrorLiveData.observe(this) { error ->
            Log.e("AgoraToken", "Error: $error")
            retryOrFailAgoraToken("token-error")
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
//                                leaveChannel(binding.LeaveButton)
//
////                                Toast.makeText(
////                                   this@MaleAudioCallingActivity,
////                                   "\"$it\"",
////                                 Toast.LENGTH_SHORT
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


    private fun giftIconClicked(){
        // Gift button click listener (works for both the card and the icon)
        binding.giftButtonCard.setOnClickListener {
            if(isVideoCallGoing==true){
                val bottomSheet = GiftBottomSheetFragment("video",receiverId)
                bottomSheet.show(supportFragmentManager, "BottomSheetGift")
            }else{
                val bottomSheet = GiftBottomSheetFragment("audio",receiverId)
                bottomSheet.show(supportFragmentManager, "BottomSheetGift")
            }
        }
    }

    /**
     * Inline "Tap to send" quick-gift row on the connected audio screen.
     * Shows the first 4 gifts from the shared catalog and sends one instantly
     * on tap, reusing the same coin-check + send + animate pipeline as
     * GiftBottomSheetFragment (so behaviour and throttling stay identical).
     */
    private fun setupQuickGifts() {
        val cards = listOf(
            binding.giftCard1, binding.giftCard2, binding.giftCard3, binding.giftCard4
        )
        val icons = listOf(
            binding.ivQuickGift1, binding.ivQuickGift2, binding.ivQuickGift3, binding.ivQuickGift4
        )
        val coinLabels = listOf(
            binding.tvQuickGiftCoins1, binding.tvQuickGiftCoins2,
            binding.tvQuickGiftCoins3, binding.tvQuickGiftCoins4
        )

        fun bind(gifts: List<GiftData>) {
            for (i in cards.indices) {
                val gift = gifts.getOrNull(i)
                if (gift == null) {
                    cards[i].visibility = View.GONE
                    continue
                }
                cards[i].visibility = View.VISIBLE
                coinLabels[i].text = gift.coins.toString()
                // Fallback to the bundled gift icon so a card never renders blank
                // if the remote icon URL is missing/unreachable (e.g. a server
                // whose storage/gifts file 404s).
                Glide.with(this)
                    .load(gift.gift_icon)
                    .placeholder(R.drawable.gift_png)
                    .error(R.drawable.gift_png)
                    .into(icons[i])
                cards[i].setOnClickListener { sendQuickGift(gift) }
            }
        }

        val cached = com.gmwapp.hima.utils.GiftManager.getCachedGifts()
        if (cached.isNotEmpty()) bind(cached)
        com.gmwapp.hima.utils.GiftManager.cachedGiftsLiveData.observe(this) { list ->
            if (!list.isNullOrEmpty()) bind(list)
        }
        // Cold cache (call opened before MainActivity prefetched) — fetch now and
        // warm the shared cache so the row fills in.
        giftImageViewModel.giftResponseLiveData.observe(this) { response ->
            response?.data?.let { list ->
                if (list.isNotEmpty()) {
                    bind(list)
                    com.gmwapp.hima.utils.GiftManager.updateGifts(list)
                }
            }
        }
        if (cached.isEmpty()) giftImageViewModel.fetchGiftImages()

        // One observer for inline sends — animate + notify once per success.
        giftViewModel.giftResponseLiveData.observe(this) { response ->
            if (response != null && response.success && quickGiftSentGuard == 1) {
                quickGiftSentGuard++
                response.data?.let {
                    sendGiftSentNotification(it.gift_icon)
                    animateGift(it.gift_icon)
                }
                newRemainingTime()
                Toast.makeText(this, "Gift Sent Successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendQuickGift(gift: GiftData) {
        // Throttle rapid taps (B071) — same 1s window as the bottom sheet.
        val now = System.currentTimeMillis()
        if (now - lastQuickGiftAt < quickGiftCooldownMs) return
        lastQuickGiftAt = now

        val maleUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        profileViewModel.getRemainingTime(maleUserId, "audio", object :
            NetworkCallback<GetRemainingTimeResponse> {
            override fun onNoNetwork() {}
            override fun onFailure(call: retrofit2.Call<GetRemainingTimeResponse>, t: Throwable) {}
            override fun onResponse(
                call: retrofit2.Call<GetRemainingTimeResponse>,
                response: retrofit2.Response<GetRemainingTimeResponse>
            ) {
                val remaining = response.body()?.data?.remaining_time ?: return
                val availableCoins = calculateAvailableQuickGiftCoins(remaining)
                if (availableCoins >= gift.coins) {
                    quickGiftSentGuard = 1
                    giftViewModel.sendGift(maleUserId, receiverId, gift.id)
                } else {
                    Toast.makeText(
                        this@MaleAudioCallingActivity,
                        "You don't have enough coins to send this gift!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    /** Audio call: 10 coins per minute, round up at >=30s (matches GiftBottomSheetFragment). */
    private fun calculateAvailableQuickGiftCoins(remainingTime: String): Int {
        val parts = remainingTime.split(":")
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val totalMinutes = minutes + if (seconds >= 30) 1 else 0
        return totalMinutes * 10
    }




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
    // key instead of HEADSETHOOK. We go straight to leaveChannel (no
    // confirmation dialog) so the user can end with the phone in their
    // pocket — the visible End button still routes through the dialog.
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                setupAudioSDKEngine()
                joinChannel(binding.JoinButton) // Automatically join the channel
            } else {
                showMessage("Microphone permission is required for audio calls")
                finish()
            }
        }
    }

    fun startCallingService() {

        Log.d("startCallingService","Service Function call")
        if (CallingService.isRunning) return

        Log.d("startCallingService","Service not returned")

        // B137 — Android 14/15 lets a foreground service start while the
        // activity is in the visible STARTED state, not only RESUMED. Using
        // RESUMED forced us to wait until onResume; STARTED lets us fire
        // from onStart instead, shaving ~100–300ms off the time before the
        // session-in-progress notification appears in the tray.
        val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)


        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        Log.d("startCallingService","$visible,  $micGranted")


        if (visible && micGranted) {
            // B033 — tell CallingService which class to deep-link back to
            // when the user taps the session notification.
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

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
            Log.d("AgoraTiming", "MaleAudio onJoinChannelSuccess at ${System.currentTimeMillis()}")
            // B186 — defensive unmute. If a transient focus loss / interrupt
            // fired during setup and left mutedByInterrupt=true with no
            // matching gain to clear it, the channel would be joined with
            // both streams muted ("call connected, no voice either side").
            // At this point the channel IS connected and the user expects to
            // hear and be heard; reset interrupt state and force-unmute
            // everything except the user-controlled local mute (isMuted).
            mutedByInterrupt = false
            if (!isMuted) agoraEngine?.muteLocalAudioStream(false)
            agoraEngine?.muteAllRemoteAudioStreams(false)
            audioModerationSession?.setPaused(isMuted)
            // Open the reliable data stream used for peer "on hold" signaling.
            holdSignal.onChannelJoined()
            startTimeoutTracking()
            startMicRevokeWatcher()
        }

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
            // I006 — pass the WORSE of the two directions. Agora's quality
            // constants are ordered low-to-high = good-to-bad, so maxOf
            // picks the worse one. Watching only rx (download) missed cases
            // where our upload was struggling and the peer couldn't hear us.
            com.gmwapp.hima.utils.CallQualityUi.apply(
                this@MaleAudioCallingActivity,
                binding.ivSignalStrength,
                binding.reconnectBanner,
                maxOf(txQuality, rxQuality),
                null
            )
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            com.gmwapp.hima.utils.CallQualityUi.apply(
                this@MaleAudioCallingActivity,
                binding.ivSignalStrength,
                binding.reconnectBanner,
                Constants.QUALITY_UNKNOWN,
                state
            )
            // B062 — arm the auto-end timer on RECONNECTING/FAILED, cancel
            // on CONNECTED/DISCONNECTED. Without this Agora's retry loop
            // ran indefinitely while the user stared at the banner.
            reconnectWatchdog.armOrCancel(state)
            // 2026-05-22 v16 — when our connection comes back, check if peer
            // ended the call while we were offline. Avoids the cosmetic
            // 5-30s lag waiting for Agora's lazy onUserOffline delivery.
            if (state == Constants.CONNECTION_STATE_CONNECTED && callId > 0) {
                com.gmwapp.hima.utils.CallAliveChecker.checkAndEndIfDead(callId) {
                    if (!isFinishing && !isDestroyed) {
                        leaveChannel(binding.LeaveButton)
                    }
                }
            }
        }

        // I024 — detect PEER-side network drops. onConnectionStateChanged
        // only fires when OUR connection has issues; when the peer's network
        // dies we just stop receiving their audio while Agora cheerfully
        // tells us we're still connected. FROZEN/FAILED on the remote audio
        // stream is the SDK's signal that the peer side has stalled. We
        // arm the same watchdog (different reason) so the user sees the
        // existing Reconnecting… banner + countdown + 30s auto-end instead
        // of talking into silence until Agora finally gives up.
        override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteAudioStateChanged(uid, state, reason, elapsed)
            // Skip mute-driven freezes — peer explicitly muting is a normal
            // user action, not a network outage. Existing B055 pill handles
            // that path.
            if (reason == Constants.REMOTE_AUDIO_REASON_REMOTE_MUTED) return
            runOnUiThread {
                when (state) {
                    Constants.REMOTE_AUDIO_STATE_FROZEN,
                    Constants.REMOTE_AUDIO_STATE_FAILED ->
                        reconnectWatchdog.peerStreamStalled(stalled = true)
                    Constants.REMOTE_AUDIO_STATE_DECODING,
                    Constants.REMOTE_AUDIO_STATE_STARTING ->
                        reconnectWatchdog.peerStreamStalled(stalled = false)
                    // REMOTE_AUDIO_STATE_STOPPED — explicit mute, leave alone.
                }
                // Banner reflects "is either source stalled". Driving it off
                // the watchdog's combined state keeps the peer-stream
                // recovery from prematurely hiding the banner while OUR
                // connection is still RECONNECTING.
                // 2026-05-23 v1072 — banner DISABLED. See MaleVideo for rationale.
            }
        }

        override fun onSnapshotTaken(uid: Int, filePath: String?, width: Int, height: Int, errCode: Int) {
            callModerationCaptureManager?.onSnapshotTaken(filePath, width, height, errCode)
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            // Peer left — clear any stale "on hold" banner (no UNHOLD arrives if
            // they dropped abruptly while on hold).
            runOnUiThread { runCatching { binding.peerOnHoldBanner.visibility = View.GONE } }

            // B-CALL RC#3: reason=1 means the peer's connection TIMED OUT (they may
            // rejoin) — not a voluntary leave. Don't tear the call down on a transient
            // drop; arm the reconnect watchdog's HARD-OFFLINE window (NET-004: ~15s,
            // shorter than the frozen-audio grace because Agora already waited ~20s
            // before firing this). Its onTimeout ends the call if the peer never
            // returns; onUserJoined / stream-resume clears it on rejoin. Fail-safe:
            // the watchdog ALWAYS ends the call, so this can't hang a call.
            // reason 0 (voluntary leave) / 2 (kicked) still end immediately below.
            if (reason == 1 && isRemoteUserJoined) {
                Log.w("CallReconnect", "MaleAudio onUserOffline reason=1 — hard-offline grace, not ending")
                reconnectWatchdog.peerOffline(gone = true)
                return
            }

            updateCallEndDetails()
            stopCountdown()
          //  showMessage("Remote user left")

            val intent = Intent(this@MaleAudioCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()

        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            // TC-NET-005: peer connected → begin per-side liveness heartbeats.
            com.gmwapp.hima.utils.CallHeartbeat.start(callId)
          //  showMessage("Remote user joined $uid")
            isRemoteUserJoined = true
            // NET-004: peer is back on the channel — clear any hard-offline grace.
            reconnectWatchdog.peerOffline(gone = false)
            stopAcceptResend() // CALLER_ACCEPT_RESEND — caller is here, stop nudging
            Log.d("AgoraTiming", "MaleAudio onUserJoined at ${System.currentTimeMillis()}")
            Log.d("videoUid", "$uid")
            videoUid = uid
            startTime = dateFormat.format(Date()) // Set call end time in IST
            callStartMillis = System.currentTimeMillis() // B110: duration baseline
            // 2026-05-22 — Contact event (Meta + Firebase). Fires on the first
            // remote join of THIS call session — once per call, not per app open.
            com.gmwapp.hima.utils.HimaAnalytics.logContact(this@MaleAudioCallingActivity, contentType = "audio_call")
            startCallingService()
            getRemainingTime()
            audioModerationSession?.startAfterPeerConnected(initiallyPaused = isMuted || mutedByInterrupt)
            // I021 — load the package catalog now so the banner's chips are
            // populated by the time the timer drops below 60s.
            runOnUiThread { lowBalanceBanner?.prefetch() }
            // Safety-net 30s re-fetch — see TIMER_RESYNC_INTERVAL_MS rationale.
            startTimerResync()

            val bundle = Bundle().apply {
                putString("user_id", "${maleUserId}")
            }

            FirebaseAnalytics.getInstance(this@MaleAudioCallingActivity).logEvent("call_started", bundle)

            MmpClient.trackEvent(
                eventName = "call_started",
                params = mapOf("user_id" to maleUserId, "call_type" to "Audio"),
                customerUserId = "$maleUserId"
            )

            // Log to backend (only Firebase events)
            AppEventLogger.logEvent(
                context = this@MaleAudioCallingActivity,
                eventName = "call_started",
                platform = "firebase",
                userId = maleUserId,
                params = AppEventLogger.bundleToMap(bundle)
            )

            // Adjust (mirrors alongside Firebase + MMP + backend).
            com.gmwapp.hima.mmp.AdjustTracker.trackEvent(
                "call_started",
                params = mapOf("user_id" to maleUserId, "call_type" to "Audio")
            )

            // Notification conversion: caller actually connected a call (engagement).
            if (!callStartedConversionFired) {
                callStartedConversionFired = true
                com.gmwapp.hima.BaseApplication.getInstance()?.let { app ->
                    app.trackNotificationConversion(app.getLastNotificationId(), "call_started")
                }
            }




        }

        override fun onUserMuteVideo(uid: Int, muted: Boolean) {
            super.onUserMuteVideo(uid, muted)

            if (isVideoCallGoing){
                runOnUiThread {
                    if (muted){
                        showRemoteBlurState()


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
                    }
                }
            }
        }


        override fun onUserMuteAudio(uid: Int, muted: Boolean) {
            // This is triggered when remote user (with uid) mutes/unmutes their mic
            Log.d("userMuted", if (muted) "User is muted" else "User is not muted")
            isPeerAudioMuted = muted
            // Renders femaleMute (audio mode) or iv_peer_mute_top (video mode).
            updateMuteIndicators()
        }



        // Add these variables at the top of the class (after other class variables)
        private var lastSpeakingStateChangeTime = 0L
        private val SPEAKING_STATE_DEBOUNCE_MS = 500L
        private var lastMaleSpeakingState = false
        private var lastFemaleSpeakingState = false

        // Then replace the entire onAudioVolumeIndication function with this:
        override fun onAudioVolumeIndication(
            speakers: Array<IRtcEngineEventHandler.AudioVolumeInfo>,
            totalVolume: Int
        ) {
            var isLocalSpeaking = false
            var isRemoteSpeaking = false

            for (speaker in speakers) {
                val uid = speaker.uid
                val volume = speaker.volume

                if (uid == 0 && volume > 30) {
                    isLocalSpeaking = true
                } else if (uid != 0 && volume > 30) {
                    isRemoteSpeaking = true
                }
            }

            val currentTime = System.currentTimeMillis()

            // Only update if state changed AND enough time has passed
            if (isLocalSpeaking != lastMaleSpeakingState || isRemoteSpeaking != lastFemaleSpeakingState) {
                if (currentTime - lastSpeakingStateChangeTime > SPEAKING_STATE_DEBOUNCE_MS) {
                    lastSpeakingStateChangeTime = currentTime
                    lastMaleSpeakingState = isLocalSpeaking
                    lastFemaleSpeakingState = isRemoteSpeaking

                    runOnUiThread {
                        if (!isVideoCallGoing) {
                            // Male avatar
                            if (isLocalSpeaking) {
                                if (!binding.maleWave.isAnimating) {
                                    binding.maleWave.alpha = 1f
                                    binding.maleWave.visibility = View.VISIBLE
                                    binding.maleWave.playAnimation()
                                }
                            } else {
                                if (binding.maleWave.isAnimating) {
                                    binding.maleWave.repeatCount = 0
                                }
                            }

                            // Female avatar
                            if (isRemoteSpeaking) {
                                if (!binding.femaleWave.isAnimating) {
                                    binding.femaleWave.alpha = 1f
                                    binding.femaleWave.visibility = View.VISIBLE
                                    binding.femaleWave.playAnimation()
                                }
                            } else {
                                if (binding.femaleWave.isAnimating) {
                                    binding.femaleWave.repeatCount = 0
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    fun joinChannel(view: View) {
        if (checkSelfPermission()) {
            if (agoraEngine == null) setupAudioSDKEngine()

            val options = ChannelMediaOptions()
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            options.autoSubscribeAudio = true
            options.autoSubscribeVideo = false
            options.publishMicrophoneTrack = true
            options.publishCameraTrack = false

            Log.d("AgoraTiming", "MaleAudio joinChannel at ${System.currentTimeMillis()}")
            agoraEngine!!.joinChannel(token, channelName, uid, options)
            Log.d("AgoraTag", "Joined channel: $channelName with token: $token")

        } else {
            Toast.makeText(applicationContext, "Permissions were not granted", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss").apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST time zone
    }

    fun leaveChannel(view: View) {
        // 2026-05-22 — instant peer-hangup FCM (fire-and-forget). See
        // MaleVideoCallingActivity for full rationale.
        FcmUtils.notifyPeerOfHangup(receiverId, callId)
        // B181 — clear the "user is busy" guard BEFORE we navigate back to
        // MainActivity. The fragments' onResume() checks this flag to decide
        // whether to refresh creator availability; if we wait for onDestroy
        // it can land after the next onResume and leave stale "in-call" data
        // (greyed call buttons) on screen.
        FcmUtils.isUserAvailable = 0
        // B082 — close any switch-call dialog before tearing down so it
        // doesn't linger over the next screen as a phantom popup.
        switchDialog?.dismiss()
        switchDialog = null
        FcmUtils.clearCallSwitch()
        // Stop the 30s timer-resync handler so it doesn't fire newRemainingTime
        // after the channel has already been left.
        stopTimerResync()
        // Bug #5B fix (2026-05-25): ALWAYS attempt Agora teardown regardless of
        // isJoined. Previously the `!isJoined` branch skipped releaseEngineSync,
        // so when a low-balance early disconnect (or any path that flipped
        // isJoined=false before leaveChannel ran) fired, the Agora channel
        // stayed live in the background — both peers could keep talking for
        // free until the SDK GC'd the engine ~30s later. releaseEngineSync is
        // idempotent (handles already-left channels gracefully), so the extra
        // call on the !isJoined path is safe.
        stopCountdown()
        stopMicRevokeWatcher()
        audioModerationSession?.finishCall(callId)
        audioModerationSession?.dispose()
        audioModerationSession = null
        try {
            agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.releaseEngineSync(
                agoraEngine, "MaleAudioCalling", hasVideo = false
            )
        } catch (t: Throwable) {
            Log.w("MaleAudioCalling", "leaveChannel teardown threw (safe): ${t.message}")
        }
        val wasJoined = isJoined
        isJoined = false
        HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
        if (wasJoined) {
            updateCallEndDetails()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val intent = Intent(this@MaleAudioCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }, 50L)
    }

    fun updateCallEndDetails() {

        // Must run before the switchCallID promotion at the tail of this method:
        // finishCall() only matches the leg it was started for, so once callId
        // has moved on the video leg would never be finalised.
        callModerationCaptureManager?.finishCall(callId)

        if (startTime.isNotEmpty()) {
            endTime = dateFormat.format(Date()) // Set call end time only if startTime is not empty
        }

        // Tester report: 2-3 identical transactions per call. The fix lives
        // in CallEndUpdater — it dedupes by callId so multiple lifecycle
        // paths (onUserOffline + leaveChannel + late FCM observers) can't
        // each enqueue a separate worker for the same call.
        com.gmwapp.hima.utils.CallEndUpdater.enqueueIfFresh(
            context = this@MaleAudioCallingActivity,
            userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0,
            callId = callId,
            startedTime = startTime,
            endedTime = endTime,
            isIndividual = true
        )

        // 2026-07-27 — two_min_new_male is now VIDEO-only and fires once per
        // male on his first 2-minute video call, so audio calls no longer emit it.

        if (switchCallID != 0) {
            callId = switchCallID
            Log.d("callidCheck","$callId")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRouteExecutor.shutdown() // B_009 follow-up: stop the audio-route worker
        callModerationCaptureManager?.dispose()
        chromeAutoHideHandler.removeCallbacks(chromeAutoHideRunnable) // B18: stop auto-hide timer
        stopAcceptResend() // CALLER_ACCEPT_RESEND — clean up any pending nudges
        com.gmwapp.hima.utils.CallHeartbeat.stop() // TC-NET-005: end liveness heartbeats
        // B181 backstop — covers system-killed activities that bypass leaveChannel.
        FcmUtils.isUserAvailable = 0
        // B082 backstop — close lingering switch-call dialog.
        switchDialog?.dismiss()
        switchDialog = null
        // Cancel any pending video-switch wait so its timeout can't fire post-teardown.
        switchCallIdTimeoutRunnable?.let { switchCallIdHandler.removeCallbacks(it) }
        switchCallIdTimeoutRunnable = null
        pendingVideoSwitchSeconds = null
        BaseApplication.getInstance()?.markCallEnded()
        // GHOST_CALL_TTL_2026_07_03 — the no-arg markCallEnded() above only flips
        // isCallActive; stamp THIS call_id as ended too so the 5-min recently-ended
        // window restarts from actual hang-up (not from answer). Without this, a
        // call that ran >5 min lets its call_id expire mid-call, and a late/retried
        // duplicate "incoming call" push for it could slip past wasCallRecentlyEnded
        // and ghost-ring after the call ended. markCallEnded(id) no-ops when id<=0.
        BaseApplication.getInstance()?.markCallEnded(callId)
        BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
        HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
        stopCountdown()
        cancelTimeoutTracking()
        stopCallingService()

        audioFocusHelper?.abandon()
        audioFocusHelper = null
        audioRouter?.release()
        audioRouter = null
        phoneStateHelper?.unregister()
        phoneStateHelper = null
        btWatcher?.unregister()
        btWatcher = null
        wiredWatcher?.unregister()
        wiredWatcher = null
        reconnectWatchdog.cancel()

        // B143: deterministic teardown — disable audio, leave channel, then block on destroy.
        stopMicRevokeWatcher()
        audioModerationSession?.finishCall(callId)
        audioModerationSession?.dispose()
        audioModerationSession = null
        agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.releaseEngineSync(
            agoraEngine, "MaleAudioCalling", hasVideo = false
        )

        if (isRemoteUserJoined==true && isBlockWordDetected==false){
            val intent = Intent(this@MaleAudioCallingActivity, RatingActivity::class.java)
            intent.putExtra(DConstants.RECEIVER_NAME, receiverName)
            intent.putExtra(DConstants.RECEIVER_ID, receiverId)
            startActivity(intent)
            Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
        }

        if (isRemoteUserJoined==true && isBlockWordDetected==true){
            val intent = Intent(this@MaleAudioCallingActivity, MainActivity::class.java)
            intent.putExtra("blockword", true)
            startActivity(intent)
            Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
        }



    }

    private fun getRemainingTime(attempt: Int = 0) {
        val maxRetries = 3
        maleUserId?.let {
            profileViewModel.getRemainingTime(it, "audio", object :
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
                        Log.d("newtime","$newTime")

                        if (storedRemainingTime == null) {
                            storedRemainingTime = newTime // Store first-time value
                        }

                        startCountdown(newTime, data.ends_at_ms, data.server_now_ms)
                    }
                }

            })
        }
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
            // Bug #5A fix (2026-05-25): handle both HH:MM:SS (server format) and
            // MM:SS (legacy). Old code took parts[0] as minutes regardless,
            // so "00:02:00" (2 minutes) was parsed as 0 min + 2 sec = 2 sec
            // and CountDownTimer fired onFinish almost immediately, ending
            // low-balance calls way before the user's actual budget ran out.
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
            lowBalanceBanner?.maybeShow(0L)
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

        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / 3600000
                val minutes = (millisUntilFinished % 3600000) / 60000
                val secs = (millisUntilFinished % 60000) / 1000

                binding.tvRemainingTime?.text =
                    String.format("%02d:%02d:%02d", hours, minutes, secs)
                Log.d("timechanging", "${String.format("%02d:%02d:%02d", hours, minutes, secs)}")

                // I021 — reveal the low-balance banner on the first sub-60s
                // tick. The banner is idempotent so it's safe to call every
                // tick; once shown it stays visible until the activity ends
                // or the user returns from a successful recharge (handled in
                // onResume via hide()).
                lowBalanceBanner?.maybeShow(millisUntilFinished)
            }

            override fun onFinish() {
                binding.tvRemainingTime?.text = "00:00:00" // When countdown finishes
                leaveChannel(binding.LeaveButton)
            }
        }.start()
    }

    private fun stopCountdown() {
        if (countDownTimer != null) {
            countDownTimer?.cancel() // Cancel the countdown timer
            countDownTimer = null
            Log.d("Countdown", "Countdown timer stopped successfully.")
        } else {
            Log.d("Countdown", "Countdown timer was already null.")
        }
    }


     fun newRemainingTime() {

        if (isVideoCallGoing) {
            maleUserId?.let {
                profileViewModel.getRemainingTime(it, "video", object :
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
                            Log.d("resumedtag","storedVideoRemainingTime - $storedVideoRemainingTime")

                            // v1111 — collapsed two back-to-back blocks that
                            // both ran on every response (the first set the
                            // var not-null, causing the second to also run).
                            // Net effect was one running timer (stopCountdown
                            // cancelled the first), one wasted notification
                            // roundtrip, and one extra startCountdown call.
                            storedVideoRemainingTime = newTime
                            sendUpdatedTimeNotification(
                                maleUserId,
                                receiverId,
                                "audio",
                                "remainingTimeUpdated"
                            )
                            stopCountdown()
                            startCountdown(newTime, data.ends_at_ms, data.server_now_ms)


                        }
                    }
                })
            }

        } else {

            maleUserId?.let {
                profileViewModel.getRemainingTime(it, "audio", object :
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

                            // Always (re)start countdown — gating on stored != null
                            // meant a failed first getRemainingTime left the timer
                            // permanently stopped and the call had no auto-hangup
                            // at 00:00 (pairs with B184 fix).
                            storedRemainingTime = newTime
                            sendUpdatedTimeNotification(
                                maleUserId,
                                receiverId,
                                "audio",
                                "remainingTimeUpdated"
                            )
                            stopCountdown()
                            startCountdown(newTime, data.ends_at_ms, data.server_now_ms)
                        }
                    }
                })
            }


        }


    }

    fun sendUpdatedTimeNotification(
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
        // B137 — fire the foreground service as soon as the activity is
        // visible (STARTED state), not just when it's resumed. The session
        // notification appears in the tray ~100-300ms sooner this way.
        startCallingService()
    }

    override fun onResume() {
        super.onResume()
        Log.d("resumedtag", "resumed")
        // B162 — if a permanent AUDIOFOCUS_LOSS left us in muted-by-interrupt
        // state and the matching GAIN never came back (some apps grab focus
        // and never return it), the receiver's voice would stay muted with
        // no recovery path. Activity resume = user is actively on the call
        // surface = they expect audio. Re-request focus (idempotent) and if
        // we hold it, defensively clear the interrupt mute. If the interrupt
        // is genuinely still active, the helper will re-fire onFocusLost
        // and re-mute within milliseconds.
        audioFocusHelper?.request()
        if (mutedByInterrupt && audioFocusHelper?.hasFocus() == true) {
            Log.d("B162", "MaleAudio onResume: clearing stuck interrupt mute (focus held)")
            mutedByInterrupt = false
            agoraEngine?.muteAllRemoteAudioStreams(false)
            if (!isMuted) agoraEngine?.muteLocalAudioStream(false)
            audioModerationSession?.setPaused(isMuted)
        }
        newRemainingTime()
        startCallingService()

        // I021 — if we just came back from WalletActivity (either through the
        // banner's chip tap or the "Go to wallet" link), hide the banner.
        // newRemainingTime() above has already re-fetched the server's
        // remaining-time and restarted the countdown; the next sub-60s tick
        // will re-show the banner if the recharge wasn't enough to push past
        // the threshold. Flag is one-shot.
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
            finish()
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
            var intent = Intent(this@MaleAudioCallingActivity, WalletActivity::class.java)
            startActivity(intent)
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        agoraEngine?.muteLocalAudioStream(isMuted)  // Mute or unmute audio
        audioModerationSession?.setPaused(isMuted || mutedByInterrupt)
        val muteIcon = if (isMuted) R.drawable.mute_img else R.drawable.unmute_img
        binding.btnMuteUnmute.setImageResource(muteIcon)
        // B054 — flip the self-avatar mute badge so the user sees the same
        // indicator on their own avatar that the peer already sees on theirs.
        updateMuteIndicators()
    }

    /**
     * Single source of truth for the mute badges. In AUDIO mode the per-avatar
     * center badges (maleMute = self, femaleMute = peer) are used. In VIDEO mode
     * (including an audio→video switch) we mirror the dedicated video screen:
     * self-mute on the local preview PiP (iv_self_mic_muted) and peer-mute
     * top-center (iv_remote_mic_muted), so placement is identical in all cases.
     */
    private fun updateMuteIndicators() {
        runOnUiThread {
            if (isVideoCallGoing) {
                // Video mode (incl. audio→video switch): match the dedicated video
                // screen — self-mute on the local preview PiP, peer-mute top-center.
                // Retired the old top-row (ll_video_mute_top) so placement is identical
                // in all cases. bringToFront keeps the preview badge above the surface.
                binding.maleMute.visibility = View.INVISIBLE
                binding.femaleMute.visibility = View.INVISIBLE
                binding.llVideoMuteTop.visibility = View.GONE
                binding.ivSelfMicMuted.visibility = if (isMuted) View.VISIBLE else View.GONE
                binding.ivRemoteMicMuted.visibility = if (isPeerAudioMuted) View.VISIBLE else View.GONE
                if (isMuted) binding.ivSelfMicMuted.bringToFront()
            } else {
                binding.llVideoMuteTop.visibility = View.GONE
                binding.ivSelfMicMuted.visibility = View.GONE
                binding.ivRemoteMicMuted.visibility = View.GONE
                binding.maleMute.visibility = if (isMuted) View.VISIBLE else View.INVISIBLE
                binding.femaleMute.visibility = if (isPeerAudioMuted) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    // Function to toggle speaker on/off
    private fun toggleSpeaker() {
        Log.d("CallAudioRoute", "Activity.toggleSpeaker isSpeakerOn=$isSpeakerOn -> ${!isSpeakerOn}")
        applyAudioRoute(
            if (isSpeakerOn) com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE
            else com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER
        )
    }

    /**
     * Show the 3-way audio-route picker when a Bluetooth headset is connected;
     * otherwise fall back to the binary speaker toggle. Called from the speaker
     * button click listener so the user only sees the extra chooser when it's
     * actually useful.
     */
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
        // 0) OPTIMISTIC UI. Flip the icon and intent state immediately so the
        //    tap feels instant. The actual routing work below talks to the
        //    Telecom service + AudioFlinger via IPC and can take 50–300ms;
        //    waiting for it before updating the icon is what made the toggle
        //    feel laggy. If the OS later rejects the route (e.g. wired
        //    headset on Android <12) the reconciliation block at the bottom
        //    snaps the icon back and surfaces a toast.
        isSpeakerOn = route == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER
        currentAudioRoute = route
        binding.btnSpeaker.setImageResource(iconForRoute(route))

        // 1) TELECOM-FIRST. On self-managed connections (Samsung One UI /
        //    Android 16 hits this) the system Telecom service owns the
        //    baseline audio route and silently overrides AudioManager. Route
        //    through the Connection API first so Samsung's CallAudioRouteController
        //    respects the choice; AudioManager fallback below handles the
        //    non-Telecom path.
        HimaTelecomManager.setAudioRoute(route)

        // 2) Tell Agora. Agora's own routing flips Android's
        //    `isSpeakerphoneOn` flag; when that runs AFTER our
        //    `setCommunicationDevice` call it silently clears the explicit
        //    communication device we just set and lets Android fall back to
        //    the default route — which is usually Bluetooth when connected.
        agoraEngine?.setEnableSpeakerphone(route == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER)

        // 3) Force the explicit communication device — OFF the main thread.
        //    clearCommunicationDevice()/setCommunicationDevice() are synchronous
        //    binder calls into AudioService and can block for hundreds of ms
        //    (seconds on some OEM builds). Running them inline held the main thread
        //    so the optimistic icon flip above could not be painted until they
        //    returned — that was the "speaker feels laggy" complaint. Last write
        //    wins when no Telecom connection is active. Each force*() returns
        //    whether the OS actually applied the route; we must not lie about state.
        //    forceSpeaker may fail on wired-headset + Android <12.
        //    forceEarpiece may fail on OEMs that need an explicit clear (the
        //    router does that now, but verify anyway).
        audioRouteExecutor.execute {
            val applied = when (route) {
                com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE ->
                    audioRouter?.forceEarpiece() ?: false
                com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER ->
                    audioRouter?.forceSpeaker() ?: false
                com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH ->
                    audioRouter?.forceBluetooth() ?: false
            }
            val actualRoute = if (applied) route else (audioRouter?.currentRoute() ?: route)

            runOnUiThread {
                // 4) Reconcile the optimistic icon flip with what the OS actually did.
                //    Only roll back when the OS rejected the request — the happy path
                //    needs no further work because we already pre-flipped the icon.
                if (!applied) {
                    if (route == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER) {
                        Toast.makeText(
                            this,
                            "Unplug headphones to use the speaker.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else if (route == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE &&
                        actualRoute == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER
                    ) {
                        // User tapped speaker-off but the OS kept routing to speaker.
                        Toast.makeText(
                            this,
                            "Couldn't switch off the speaker. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    isSpeakerOn = actualRoute == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER
                    currentAudioRoute = actualRoute
                    binding.btnSpeaker.setImageResource(iconForRoute(actualRoute))
                }
                Log.d(
                    "CallAudioRoute",
                    "Activity.applyAudioRoute requested=$route applied=$applied effective=$currentAudioRoute " +
                        "isSpeakerOn=$isSpeakerOn " +
                        "btConnected=${audioRouter?.isBluetoothConnected()} " +
                        "wiredConnected=${audioRouter?.isWiredHeadsetConnected()}"
                )
                // Agora's worker thread may write isSpeakerphoneOn after we return.
                // Verify once after the worker has flushed and re-apply if it raced.
                audioRouter?.verifyAndReapply(currentAudioRoute)
            }
        }
    }

    private fun iconForRoute(route: com.gmwapp.hima.utils.CallAudioRouter.AudioRoute): Int = when (route) {
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER -> R.drawable.speakeron_img
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.BLUETOOTH -> R.drawable.ic_bluetooth_audio
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE -> R.drawable.speakeroff_img
    }

    /**
     * Re-syncs the speaker icon with the OS-reported audio route. Called from
     * the router's device-callback when a wired/BT headset is plugged or
     * unplugged mid-call. The icon must follow hardware state, not the last
     * button tap.
     */
    private fun refreshAudioRouteIcon() {
        val router = audioRouter ?: return
        val actual = router.currentRoute()
        if (actual != currentAudioRoute) {
            Log.d(
                "CallAudioRoute",
                "Activity.refreshIcon route changed in hardware: $currentAudioRoute -> $actual " +
                    "wiredConnected=${router.isWiredHeadsetConnected()} btConnected=${router.isBluetoothConnected()}"
            )
            currentAudioRoute = actual
            isSpeakerOn = actual == com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.SPEAKER
            binding.btnSpeaker.setImageResource(iconForRoute(actual))
        }
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
        // B054 — keep the self mute badge in sync with restored mute state
        // (center badge in audio, top badge in video).
        updateMuteIndicators()
        // applyAudioRoute re-runs Agora + router + icon in one atomic call so
        // there's no half-applied state after restore.
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
                Log.d("CallStatus", "MaleAudio.hangup → ended/$endedByRole self=$maleUserId peer=$receiverId callId=$callId isCaller=$isCaller durationSec=$durationSec")
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
    
    private fun showSwitchVideoDialog(totalSeconds: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_switch_video, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val btnNo = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dialog_no)
        val btnYes = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dialog_yes)
        
        btnNo.setOnClickListener {
            dialog.dismiss()
        }
        
        btnYes.setOnClickListener {
            dialog.dismiss()
            // Show toast message
            if (totalSeconds > 360) {
                proceedOrAwaitVideoSwitch(totalSeconds)
            } else {
                Toast.makeText(
                    this,
                    "You don't have enough coins",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        dialog.show()
    }

    /**
     * Drive the audio→video switch once the user has confirmed. If the fresh
     * call_id is already in (switchCallID != 0) we send the request immediately.
     * Otherwise — the call_id request is still in flight, or a previous attempt
     * transiently failed (null response) — we re-request it, show a connecting
     * state, and proceed automatically when [callIdObserver] receives it. Only
     * after an 8s timeout (or a hard API failure) do we surface a real error,
     * instead of the old bare "Try Again" dead-end.
     */
    private fun proceedOrAwaitVideoSwitch(totalSeconds: Int) {
        if (switchCallID != 0) {
            sendSwitchCallRequestNotification(
                maleUserId,
                receiverId,
                "video",
                "switchToVideo $switchCallID"
            )
            Toast.makeText(this, "Video session request sent", Toast.LENGTH_SHORT).show()
            return
        }

        // call_id not ready yet — park the switch, re-request, and wait briefly.
        pendingVideoSwitchSeconds = totalSeconds
        Toast.makeText(this, "Preparing video session…", Toast.LENGTH_SHORT).show()
        getCallIdforCallSwitch("video")

        switchCallIdTimeoutRunnable?.let { switchCallIdHandler.removeCallbacks(it) }
        val timeout = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            if (pendingVideoSwitchSeconds != null && switchCallID == 0) {
                pendingVideoSwitchSeconds = null
                Toast.makeText(
                    this,
                    "Couldn't start video session. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        switchCallIdTimeoutRunnable = timeout
        switchCallIdHandler.postDelayed(timeout, 8000)
    }

    private fun showIncomingSwitchVideoRequest(userid: Int?, requesterName: String): AlertDialog {
        val dialogView = layoutInflater.inflate(R.layout.dialog_switch_video, null)
        // B068 — modal. Outside-tap and back can't dismiss this dialog; only
        // Accept/Decline buttons close it. The setOnDismissListener below
        // stays as a safety net for activity teardown (it self-guards via
        // !isFinishing && !isDestroyed).
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        tvMessage.text = "${com.gmwapp.hima.utils.DisplayName.clean(requesterName)} requested for video session"

        val btnNo = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dialog_no)
        val btnYes = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dialog_yes)

        btnNo.text = "Decline"
        btnYes.text = "Accept"

        // B069 follow-up — outside-tap = implicit decline (see female-side fix).
        var responded = false

        btnNo.setOnClickListener {
            responded = true
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

        btnYes.setOnClickListener {
            val remainingTime = binding.tvRemainingTime?.text.toString()
            // v1106 — same defensive parse as switchToVideo() — countdown text
            // can be "Connecting..." before timer initializes.
            val timeParts = try {
                remainingTime.split(":").map { it.toInt() }
            } catch (e: NumberFormatException) {
                Log.w("MaleAudio", "btnYes: remainingTime='$remainingTime' not parseable — aborting")
                return@setOnClickListener
            }

            if (timeParts.size == 3) {
                val hours = timeParts[0]
                val minutes = timeParts[1]
                val seconds = timeParts[2]
                val totalSeconds = (hours * 3600) + (minutes * 60) + seconds

                if (totalSeconds > 360) {
                    if (userid != null && switchCallID != 0) {
                        responded = true
                        Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
                        sendCallAcceptNotification(
                            userid,
                            receiverId,
                            "video",
                            "VideoAccepted"
                        )
                        FcmUtils.clearCallSwitch()
                        stopCountdown()
                        isSwitchingToVideo = false
                        enableVideoCall()
                    }
                } else {
                    responded = true
                    Toast.makeText(
                        this,
                        "$requesterName don't have enough coins",
                        Toast.LENGTH_SHORT
                    ).show()
                    FcmUtils.clearCallSwitch()
                }
            }
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (!responded && !isFinishing && !isDestroyed) {
                userid?.let {
                    sendCallAcceptNotification(
                        it,
                        receiverId,
                        "video",
                        "SwitchDeclined"
                    )
                }
                FcmUtils.clearCallSwitch()
            }
            switchDialog = null
        }
        dialog.show()
        return dialog
    }

    private fun handleCallSwitch() {
        // B151: debounce the audio↔video switch button so rapid double-taps
        // don't queue two opposite switchTo*() calls before the first server
        // ack lands. The isSwitchRequestPending guard below also helps but it
        // only flips after the dialog appears.
        binding.btnVideoCall.setOnSingleClickListener {
            if (isSwitchRequestPending) {
                Toast.makeText(this, "Already Request Sent", Toast.LENGTH_SHORT).show()
                return@setOnSingleClickListener
            }
            // B142 — decide direction from the call's actual mode flag, not
            // from Drawable.constantState equality. Vector/ContextCompat
            // drawables return fresh constantState instances and the
            // comparison silently fell into the "Unknown state" branch.
            if (isVideoCallGoing) switchToAudio() else switchToVideo()
        }
    }

    //Switch to video



    private fun switchToVideo() {

         getCallIdforCallSwitch("video")

                val remainingTime =
                    binding.tvRemainingTime?.text.toString() // Get the current countdown time
                // v1106 (2026-05-29) — guard against UI text like "Connecting..." being
                // parsed as Int. 4 users on v1105 hit NumberFormatException when they
                // tapped switch-to-video before the call countdown finished initializing
                // (tv_remaining_time was still showing "Connecting..."). Defensive parse.
                val timeParts = try {
                    remainingTime.split(":").map { it.toInt() }
                } catch (e: NumberFormatException) {
                    Log.w("MaleAudio", "switchToVideo: remainingTime='$remainingTime' not yet HH:MM:SS — skipping switch")
                    return
                }

                if (timeParts.size == 3) {  // Ensure we have HH:MM:SS format
                    val hours = timeParts[0]
                    val minutes = timeParts[1]
                    val seconds = timeParts[2]

                    val totalSeconds = (hours * 3600) + (minutes * 60) + seconds


                    showSwitchVideoDialog(totalSeconds)
                }



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
            // B082 — drop late switch payloads once the activity is finishing.
            if (isFinishing || isDestroyed) {
                FcmUtils.clearCallSwitch()
                return@Observer
            }
            if (updatedCallSwitch != null) {
                val (switchType, receiverId) = updatedCallSwitch

                Log.d("CallswitchID", "$switchCallID")

                if (switchType == "VideoAccepted" && receiverId == this.receiverId) {
                    isSwitchRequestPending=false

                    val remainingTime =
                        binding.tvRemainingTime?.text.toString() // Get the current countdown time
                    // v1106 — same defensive parse pattern.
                    val timeParts = try {
                        remainingTime.split(":").map { it.toInt() }
                    } catch (e: NumberFormatException) {
                        Log.w("MaleAudio", "VideoAccepted observer: remainingTime='$remainingTime' not parseable — skipping")
                        return@Observer
                    }


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

    private fun enableVideoCall() {

        Log.d("isSwitchingToVideo","$isSwitchingToVideo")

        if (isSwitchingToVideo) {
            Log.d("enableAudioCall", "Already switching to video, skipping duplicate call")
            return
        }

        isSwitchingToVideo = true // ✅ Set flag to prevent duplicate calls

        // ✅ Set status bar and navigation bar to black when switching to video
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        
        // Make status bar icons light (white) so they're visible on black background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.decorView.systemUiVisibility = 0
        }
        
        // For Android 11+ use WindowInsetsController for better control
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insetsController = window.insetsController
            if (insetsController != null) {
                insetsController.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                insetsController.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            }
        }

        FcmUtils.clearCallSwitch()
        updateCallEndDetails()
        isVideoCallGoing = true
        // B18: now in video mode — tap toggles chrome; start the 10s idle auto-hide
        // (parity with the direct video-call screen). The root's existing touch
        // listener returns false, so onClick still registers.
        binding.main.setOnClickListener { if (isVideoCallGoing) toggleVideoChrome() }
        videoChromeVisible = true
        armVideoChromeAutoHide()
        applyGiftCardSizing(compact = true) // video: smaller gift cards
        // B060 — keep the top-bar label honest after a mid-call switch.
        binding.tvCallType.setText(R.string.call_type_video)
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
        // Hide the parent avatars container too — the surrounding CardView
        // / FrameLayout still showed a residual rounded shape on the left
        // (female side) after switching to video.
        binding.usersContainer.visibility = View.GONE
        // Move the mute indicators to the top icon-only badges for video mode.
        updateMuteIndicators()


        runOnUiThread {
            // Enable video module
            agoraEngine?.enableVideo()

            // Defensive symmetry with FemaleAudio.switchToVideo — caller with
            // a broken camera would crash on startPreview otherwise.
            val cameraOk = com.gmwapp.hima.utils.CameraAvailability.isCameraAvailable(this)

            // Critical: the original joinChannel used audio-only ChannelMediaOptions
            // (publishCameraTrack = false, autoSubscribeVideo = false). Those options
            // persist unless we explicitly flip them here, so the camera track never
            // reaches the peer even after enableVideo(). Update them before setting up
            // the local surface so the track is publishing by the time the canvas binds.
            agoraEngine?.enableLocalVideo(cameraOk)
            agoraEngine?.muteLocalVideoStream(!cameraOk)
            agoraEngine?.updateChannelMediaOptions(ChannelMediaOptions().apply {
                autoSubscribeAudio = true
                autoSubscribeVideo = true
                // 2026-05-22 v18 — preserve current mute state across the
                // audio→video switch. Was unconditionally true, which silently
                // unmuted users who had pressed mute before switching.
                publishMicrophoneTrack = !isMuted
                publishCameraTrack = cameraOk
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            })
            // 2026-05-22 v19 — belt & suspenders: also enforce device-level mute.
            agoraEngine?.muteLocalAudioStream(isMuted)
            if (cameraOk) {
                agoraEngine?.startPreview()
                Log.d("AgoraTiming", "MaleAudio switched to VIDEO at ${System.currentTimeMillis()}")

                // Set up the local video view
                val localContainer = binding.localVideoViewContainer
                val localView = SurfaceView(this)
                // Insert the surface at index 0 (BELOW iv_self_mic_muted). A below-window
                // SurfaceView punches a transparent hole where it draws; adding it on top
                // of the badge would erase the self-mute icon on the preview (the exact
                // audio→video-switch bug). Then re-attach the badge ON TOP (a prior
                // switch-to-audio removeAllViews may have detached it).
                localContainer.addView(localView, 0)
                localView.setZOrderMediaOverlay(true)
                (binding.ivSelfMicMuted.parent as? android.view.ViewGroup)?.removeView(binding.ivSelfMicMuted)
                localContainer.addView(binding.ivSelfMicMuted)

                // Attach local video feed
                agoraEngine?.setupLocalVideo(VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0))

                // Make video UI visible
                binding.localVideoViewContainer.visibility = View.VISIBLE
                binding.localCardView.visibility = View.VISIBLE
                applySavedLocalPreviewPosition()

                // The camera is live from here on, so this leg needs the same
                // moderation coverage as a call that started in video. Anchored
                // after updateCallEndDetails() above, which has already promoted
                // callId to switchCallID — the video leg's id, which is what the
                // snapshot must be attributed to. Only wired on the cameraOk
                // path: no camera, no frames to capture.
                if (callModerationCaptureManager == null) {
                    callModerationCaptureManager = com.gmwapp.hima.utils.CallModerationCaptureManager(
                        context = this@MaleAudioCallingActivity,
                        callIdProvider = { callId },
                        engineProvider = { agoraEngine },
                    )
                }
                callModerationCaptureManager?.startAfterPeerConnected()
            } else {
                Log.w("CameraFallback", "MaleAudio.switchToVideo: camera unavailable, skipping local preview")
                binding.localCardView.visibility = View.GONE
                binding.localVideoViewContainer.visibility = View.GONE
                showMessage(getString(R.string.call_no_camera_fallback))
            }
            binding.remoteVideoViewContainer.visibility = View.VISIBLE

            // Notify remote user to switch to video (if required)

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
            remoteSurfaceView!!.visibility = View.VISIBLE

            binding.main.setBackgroundResource(R.drawable.d_call_screen_background)
            remoteSurfaceView?.visibility = View.VISIBLE
            binding.remoteVideoViewContainer.visibility = View.VISIBLE

            startTime =
                dateFormat.format(Date()) // Set call end time only if startTime is not empty

            binding.btnVideoCall.setImageResource(R.drawable.audiocall_img)
            binding.layoutButtons.visibility = View.GONE
            binding.ivGift.visibility=View.GONE


            if (ContextCompat.checkSelfPermission(this@MaleAudioCallingActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val granted = ContextCompat.checkSelfPermission(this@MaleAudioCallingActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                Log.d("FaceDetection", "CAMERA permission granted: $granted")
                //startFaceDetectionCamera()
                val videoObserver = FaceDetectVideoFrameObserver(this@MaleAudioCallingActivity)
                agoraEngine?.registerVideoFrameObserver(videoObserver)

            } else {
                Log.d("FaceDetection", "CAMERA permission granted: Not granted")

                ActivityCompat.requestPermissions(this@MaleAudioCallingActivity, arrayOf(Manifest.permission.CAMERA), 22)
            }


        }
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
            // Register the observer only once — getCallIdforCallSwitch can be
            // called repeatedly (initial tap + retry), and re-observing would
            // stack duplicate observers on the shared LiveData.
            if (!switchCallIdObserverRegistered) {
                switchCallIdObserverRegistered = true
                callIdObserver()
            }
        }
    }

    private fun callIdObserver() {
        femaleUsersViewModel.callFemaleUserResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                switchCallID = it.data?.call_id ?: 0

                isAudioCallIdReceived = true
                Log.d("switchCallID", "$switchCallID")

                // A confirmed video switch was waiting on this id — proceed now.
                if (pendingVideoSwitchSeconds != null && switchCallID != 0) {
                    pendingVideoSwitchSeconds = null
                    switchCallIdTimeoutRunnable?.let { r -> switchCallIdHandler.removeCallbacks(r) }
                    if (!isFinishing && !isDestroyed) {
                        sendSwitchCallRequestNotification(
                            maleUserId,
                            receiverId,
                            "video",
                            "switchToVideo $switchCallID"
                        )
                        Toast.makeText(this, "Video session request sent", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (pendingVideoSwitchSeconds != null) {
                // The call-id request hard-failed (null / success=false) while a
                // switch was waiting — surface a real error instead of stalling.
                pendingVideoSwitchSeconds = null
                switchCallIdTimeoutRunnable?.let { r -> switchCallIdHandler.removeCallbacks(r) }
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this,
                        "Couldn't start video session. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }


    fun observeCallSwitchRequest() {
        // B069 — capture observer attach time so we can ignore any switch
        // payload posted before this call's activity existed (stale from
        // a previous call; LiveData re-fires its current value on attach).
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

                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                var userid = userData?.id

                if (switchType == "switchToVideo") {
                    if (isVideoCallGoing==false){
                    switchCallID = newCallId
                    switchDialog?.dismiss()

                    switchDialog = showIncomingSwitchVideoRequest(userid, receiverName)

                }}

                if (switchType=="switchToAudio"){
                    if (isVideoCallGoing){
                    switchCallID = newCallId

                    switchDialog?.dismiss()

                    // B069 follow-up — outside-tap dismiss = implicit decline.
                    var responded = false
                    switchDialog = AlertDialog.Builder(this)
                        .setTitle("Switch to audio Call ?")
                        .setMessage("$receiverName requested for audio call")
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
                                sendCallAcceptNotification(
                                    it,
                                    receiverId,
                                    "audio",
                                    "SwitchDeclined"
                                )
                            }
                            d.dismiss()
                            FcmUtils.clearCallSwitch()
                        }
                        .create().apply {
                            setOnDismissListener {
                                if (!responded && !isFinishing && !isDestroyed) {
                                    userid?.let { uid ->
                                        sendCallAcceptNotification(
                                            uid,
                                            receiverId,
                                            "audio",
                                            "SwitchDeclined"
                                        )
                                    }
                                    FcmUtils.clearCallSwitch()
                                }
                                switchDialog = null
                            }
                            show()
                        }

                }


                FcmUtils.clearCallSwitch()


            }



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


    private fun onMenuClicked() {
        binding.btnMenu.setOnSingleClickListener {
            if (!isClicked) {
                binding.layoutButtons.visibility = View.VISIBLE
                binding.ivMaleUser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd = 14.dpToPx()
                }

                binding.maleWave.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                  //  leftMargin = 0.dpToPx()
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
                    binding.maleWave.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                      //  leftMargin = 5.dpToPx()
                    }
                }
            }
            false // Return false to allow other touch events
        }

    }

    fun Int.dpToPx() = (this * Resources.getSystem().displayMetrics.density).toInt()

    // ===== B18 (switch-to-video parity): auto-hide video-mode chrome after 10s idle.
    //       No-op outside video mode, so normal audio calls are unaffected. =====
    /** Fade the video-mode chrome (top bar + controls + gift row) in/out.
     *  INVISIBLE (not GONE) so nothing reflows. */
    private fun setVideoChromeVisible(visible: Boolean) {
        if (!isVideoCallGoing || !::binding.isInitialized) return
        videoChromeVisible = visible
        listOf(binding.topBar, binding.controlsContainer, binding.quickGiftSection).forEach { v ->
            v.animate().cancel()
            if (visible) {
                v.visibility = View.VISIBLE
                v.animate().alpha(1f).setDuration(280).start()
            } else {
                v.animate().alpha(0f).setDuration(280).withEndAction {
                    if (!videoChromeVisible) v.visibility = View.INVISIBLE
                }.start()
            }
        }
        if (visible) armVideoChromeAutoHide()
        else chromeAutoHideHandler.removeCallbacks(chromeAutoHideRunnable)
    }

    private fun armVideoChromeAutoHide() {
        chromeAutoHideHandler.removeCallbacks(chromeAutoHideRunnable)
        chromeAutoHideHandler.postDelayed(chromeAutoHideRunnable, CHROME_AUTOHIDE_MS)
    }

    private fun toggleVideoChrome() = setVideoChromeVisible(!videoChromeVisible)

    /** Cancel auto-hide and force chrome visible — used when leaving video mode. */
    private fun showVideoChromeAndCancelAutoHide() {
        chromeAutoHideHandler.removeCallbacks(chromeAutoHideRunnable)
        videoChromeVisible = true
        if (!::binding.isInitialized) return
        listOf(binding.topBar, binding.controlsContainer, binding.quickGiftSection).forEach { v ->
            v.animate().cancel()
            v.alpha = 1f
            v.visibility = View.VISIBLE
        }
    }

    /** Any touch keeps visible chrome alive by restarting the 10s countdown. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (isVideoCallGoing && videoChromeVisible) armVideoChromeAutoHide()
    }

    /**
     * Compact the inline gift cards for video (icon 36dp, padding 10/8dp) vs the
     * larger audio default (icon 44dp, padding 14/10dp). This row lives on the
     * audio screen, so shrink it only in video mode and restore it on switch-back.
     */
    private fun applyGiftCardSizing(compact: Boolean) {
        if (!::binding.isInitialized) return
        val d = resources.displayMetrics.density
        val icon = ((if (compact) 36 else 44) * d).toInt()
        val padT = ((if (compact) 10 else 14) * d).toInt()
        val padB = ((if (compact) 8 else 10) * d).toInt()
        // Video → smoke-glass card (translucent over the feed); audio → opaque dark card.
        val cardBg = if (compact) R.drawable.bg_quick_gift_card_glass else R.drawable.bg_quick_gift_card
        listOf(binding.ivQuickGift1, binding.ivQuickGift2, binding.ivQuickGift3, binding.ivQuickGift4).forEach {
            val lp = it.layoutParams; lp.width = icon; lp.height = icon; it.layoutParams = lp
        }
        listOf(binding.giftCard1, binding.giftCard2, binding.giftCard3, binding.giftCard4).forEach {
            it.setBackgroundResource(cardBg)              // set bg first — it can reset padding
            it.setPadding(it.paddingLeft, padT, it.paddingRight, padB)
        }
    }


    // Switch to audio

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

    private fun enableAudioCall() {

        if (isSwitchingToAudio) {
            Log.d("enableAudioCall", "Already switching to audio, skipping duplicate call")
            return
        }

        isSwitchingToAudio = true // ✅ Set flag to prevent duplicate calls

        Log.d("enableAudioCall","$1")
        stopCountdown()

        FcmUtils.clearCallSwitch()
        isVideoCallGoing = false
        // Switching back to audio: the "Host's video is blurred" overlay is a
        // video-only signal. If it was showing during the video portion it would
        // otherwise stay stuck over the audio screen (the show sites are gated by
        // isVideoCallGoing, but nothing hid it on the way down). Clear it here.
        pendingRemoteBlurHide = false
        hideRemoteBlurState()
        // B18: back to audio mode — stop the auto-hide timer and restore controls.
        showVideoChromeAndCancelAutoHide()
        applyGiftCardSizing(compact = false) // back to the larger audio gift cards
        // B060 — keep the top-bar label honest after a mid-call switch.
        binding.tvCallType.setText(R.string.call_type_audio)

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
        // Move mute indicators back to the per-avatar center badges.
        updateMuteIndicators()


        runOnUiThread {
            // Stop publishing and capturing camera, and mirror the audio-only
            // ChannelMediaOptions from the original joinChannel so bandwidth +
            // camera LED stop when the user goes back to audio mode.
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
            Log.d("AgoraTiming", "MaleAudio switched back to AUDIO at ${System.currentTimeMillis()}")

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

            startTime =
                dateFormat.format(Date()) // Set call end time only if startTime is not empty


        }

    }




    private fun getAudioRemainingTime() {
        maleUserId?.let {
            profileViewModel.getRemainingTime(it, "audio", object :
                NetworkCallback<GetRemainingTimeResponse> {
                override fun onNoNetwork() {
                    // Ignore: remaining-time is a non-critical refresh; throwing
                    // here (the original Kotlin `TODO()`) was killing the call
                    // activity on any network blip — same root cause as B184.
                    Log.w("RemainingTime", "callback ignored — call continues")
                }

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                    // Ignore: remaining-time is a non-critical refresh; throwing
                    // here (the original Kotlin `TODO()`) was killing the call
                    // activity on any network blip — same root cause as B184.
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
                    // Ignore: remaining-time is a non-critical refresh; throwing
                    // here (the original Kotlin `TODO()`) was killing the call
                    // activity on any network blip — same root cause as B184.
                    Log.w("RemainingTime", "callback ignored — call continues")
                }

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                    // Ignore: remaining-time is a non-critical refresh; throwing
                    // here (the original Kotlin `TODO()`) was killing the call
                    // activity on any network blip — same root cause as B184.
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


    fun animateGift(image: String) {
        // Cinematic gift moment — shared GiftCinema overlay. Audio call uses
        // the full-quality sequence; the gift flies to the creator avatar.
        BaseApplication.getInstance()?.playSendGiftSound()
        com.gmwapp.hima.widgets.GiftCinema.send(
            activity = this,
            giftUrl = image,
            recipientView = binding.ivFemaleUser,
            lite = false
        )
    }


    fun sendGiftSentNotification(giftIcon : String) {

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val senderId = userData?.id
        if (senderId != null) {
            fcmNotificationViewModel.sendNotification(
                senderId = senderId,
                receiverId = receiverId,
                callType = "$giftIcon",
                channelName = channelName,
                message = "giftSent"
            )
        }
    }

    fun disableVideo(){
        // Keep local blackscreen hidden; face overlay now handles the full-screen UX.
        binding.blackscreen.visibility=View.GONE
        // While local no-face overlay is active, keep remote feed hidden.
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

    fun enableVideo(){
        binding.blackscreen.visibility=View.GONE
        agoraEngine?.muteAllRemoteAudioStreams(false)
        agoraEngine?.muteLocalVideoStream(false)
        // Respect the user's mute: this face-detection auto-resume must NOT re-open a
        // mic the user muted (was unconditional). Matches the !isMuted guard used at
        // every other unmute site. Fixes "muted user still heard by peer".
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

        dismissNoFaceDetectedDialog()
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

                val cameraContainer = overlayBinding.cameraPreviewContainer
                cameraContainer.removeAllViews()
                cameraContainer.visibility = View.VISIBLE
                cameraContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                localPreviewSurface = SurfaceView(this@MaleAudioCallingActivity).apply {
                    setZOrderOnTop(false)
                    setZOrderMediaOverlay(false)
                    holder?.setFormat(PixelFormat.TRANSLUCENT)
                }

                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                cameraContainer.addView(localPreviewSurface, params)

                // Keep guide/text layers above camera preview.
                overlayBinding.tvFaceNotDetected.bringToFront()
                overlayBinding.personOutlineContainer.bringToFront()
                overlayBinding.bottomFacePanel.bringToFront()
                overlayBinding.scanIconHolder.bringToFront()

                agoraEngine?.setupLocalVideo(
                    VideoCanvas(localPreviewSurface, VideoCanvas.RENDER_MODE_HIDDEN, 0)
                )
                agoraEngine?.startPreview()
            } catch (e: Exception) {
                Log.e("MaleAudioCallingActivity", "Cannot show face detection overlay", e)
            }
        }
    }

    private fun dismissNoFaceDetectedDialog() {
        Handler(Looper.getMainLooper()).post {
            if (isFinishing || isDestroyed) {
                return@post
            }
            
            try {
                val overlayBinding = binding.faceDetectionOverlay
                overlayBinding.cameraPreviewContainer.removeAllViews()
                overlayBinding.cameraPreviewContainer.visibility = View.GONE
                overlayBinding.root.setBackgroundResource(R.drawable.face_detection_gradient_background)
                overlayBinding.personOutlineContainer.visibility = View.VISIBLE
                overlayBinding.bottomFacePanel.visibility = View.VISIBLE
                overlayBinding.scanIconHolder.visibility = View.VISIBLE
                overlayBinding.tvFaceNotDetected.text = "Face Not Detected"
                overlayBinding.root.visibility = View.GONE

                if (isVideoCallGoing) {
                    setupLocalVideoInCallView()
                    
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
                } else {
                    binding.localVideoViewContainer.visibility = View.GONE
                    binding.localCardView.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("MaleAudioCallingActivity", "Error dismissing overlay", e)
            }
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
            // removeAllViews above detached the self-mute badge (an XML child of this
            // container); re-attach it ON TOP of the surface so it stays visible on the
            // preview after a face-detection re-setup. It keeps its XML layout params.
            (binding.ivSelfMicMuted.parent as? android.view.ViewGroup)?.removeView(binding.ivSelfMicMuted)
            binding.localVideoViewContainer.addView(binding.ivSelfMicMuted)

            agoraEngine?.setupLocalVideo(
                VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
            )

            binding.localVideoViewContainer.visibility = View.VISIBLE
            binding.localCardView.visibility = View.VISIBLE
            applySavedLocalPreviewPosition()
        } catch (e: Exception) {
            Log.e("MaleAudioCallingActivity", "Error setting local video in call view", e)
        }
    }


    private fun showRemoteBlurState() {
        // "Host's video is blurred" is a video-only signal — never show it on an
        // audio call, whatever path called this (belt-and-braces alongside the
        // isVideoCallGoing guards at the call sites and the hide on switch-to-audio).
        if (!isVideoCallGoing) return
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
        binding.main.setBackgroundResource(R.drawable.d_call_screen_background)
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
            // Remote-blur ("Host's video is blurred") is a video-only face-detection
            // signal. On a pure audio call it must never show — guards against a stale
            // sticky greyScreenEnable replaying into a fresh audio call, and against a
            // stray/late FCM bleeding in mid-call. Mirrors the isVideoCallGoing guard
            // already used on the onUserMuteVideo path.
            if (!isVideoCallGoing) return@observe
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
