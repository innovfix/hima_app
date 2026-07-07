package com.gmwapp.hima.agora.female

import android.Manifest
import android.animation.AnimatorListenerAdapter
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.PaymentWebViewActivity
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import android.telecom.DisconnectCause
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.databinding.ActivityFemaleAudioCallingBinding
import com.gmwapp.hima.databinding.ActivityMaleAudioCallingBinding
import com.gmwapp.hima.media.RtcTokenBuilder2
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.IcebreakerQuestionsResponse
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.google.firebase.crashlytics.FirebaseCrashlytics
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

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.activities.RatingActivity
import com.gmwapp.hima.activities.EarningsHonourActivity
import com.gmwapp.hima.agora.FaceDetectVideoFrameObserver
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.retrofit.responses.FemaleCallAttendResponse
import com.gmwapp.hima.agora.services.CallingService
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.utils.CallAudioFocusHelper
import com.gmwapp.hima.utils.CallAudioRouter
import com.gmwapp.hima.utils.CallPhoneStateHelper
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import com.gmwapp.hima.retrofit.responses.CallEndReason
import com.gmwapp.hima.retrofit.responses.CallEndedBy
import com.gmwapp.hima.viewmodels.CallDropStatusViewModel
import com.gmwapp.hima.viewmodels.CallStatusViewModel
import com.gmwapp.hima.viewmodels.LudoFcmViewModel
import com.gmwapp.hima.workers.CallUpdateWorker
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.audio.AudioParams
import io.agora.rtc2.video.VideoCanvas
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
import kotlin.math.abs

@AndroidEntryPoint
class FemaleAudioCallingActivity : AppCompatActivity() {
    private lateinit var channelName: String
    var receiverId = 0

    //check
    private lateinit var binding: ActivityFemaleAudioCallingBinding
    private var appId: String? = null // Will be received from backend
    private val expirationTimeInSeconds = 3600
    private var token: String? = null
    private val uid = 0
    private var videoUid = 0
    private var isJoined = false
    private var agoraEngine: RtcEngine? = null

    // In-call "on hold" signaling over the Agora data stream — tells the peer
    // when we step away for a cellular / VoIP call so they see a dedicated
    // "‹Name› is on hold" banner. See CallHoldSignal.
    private val holdSignal = com.gmwapp.hima.utils.CallHoldSignal { agoraEngine }

    // B127: real-time RECORD_AUDIO revoke listener; started on join, stopped on teardown.
    private var micWatcher: com.gmwapp.hima.utils.MicPermissionWatcher? = null

    // Periodic re-fetch of remaining_time. The FCM "remainingTimeUpdated"
    // push from the caller's side can be lost (DND, doze, network) and
    // letting the local CountDownTimer drift between fetches surfaced a
    // ~60 s skew across the two phones. Resyncing every 30 s caps drift
    // tightly. See MaleAudioCallingActivity for fuller rationale.
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

    /**
     * F1 Call Duration Bonus — (re)anchor the on-screen popups to a single call leg of
     * [callType], starting the milestone clock NOW. Called at first connect and again at
     * each audio↔video switch, because the server bills every switch as a SEPARATE call
     * row (its own type + its own started_time reset to the switch instant). Anchoring the
     * popups the same way keeps them honest: no stale audio amount lingers over a video
     * leg, and no payout flashes for a milestone the server won't credit.
     * Display only — never credits money. [showIntro]=false suppresses the T+10s intro on
     * the switched leg (it already played on the first leg).
     */
    private fun anchorBonusForLeg(callType: String, showIntro: Boolean) = runOnUiThread {
        // View work must run on the UI thread: the initial call comes from Agora's
        // onUserJoined (worker thread); the switch calls are already on the UI thread
        // (runOnUiThread executes inline there, preserving order).
        bonusStartMillis = System.currentTimeMillis()
        com.gmwapp.hima.utils.CallBonusViews.clear(binding.bonusOverlay) // drop any in-flight popup from the old leg
        val bonusCfg = BaseApplication.getInstance()?.getPrefs()?.getSettingsData()?.call_bonus
        val lead = bonusCfg?.teaser_lead_seconds ?: 60
        val presenter = com.gmwapp.hima.utils.CallBonusPresenter(
            onIntro = { com.gmwapp.hima.utils.CallBonusViews.showIntro(binding.bonusOverlay) },
            onTeaser = { tier -> com.gmwapp.hima.utils.CallBonusViews.showTeaser(binding.bonusOverlay, tier, lead) },
            onPayout = { tier -> com.gmwapp.hima.utils.CallBonusViews.showPayout(binding.bonusOverlay, tier) }
        )
        callBonusPresenter = if (presenter.configure(bonusCfg, callType)) {
            if (!showIntro) presenter.skipIntro()
            // Video leg shows the self-preview (top-right) — drop the intro/teaser below it so the
            // toast never overlaps her own preview (parity with FemaleVideo). Audio leg → reset to top.
            val topDp = if (callType == "video") (205 * resources.displayMetrics.density).toInt() else 0
            listOf(binding.bonusOverlay.bonusIntro, binding.bonusOverlay.bonusTeaser).forEach { v ->
                (v.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.topMargin = topDp
                    v.layoutParams = lp
                }
            }
            presenter
        } else null
        // Start (or restart, on an audio<->video switch re-anchor) the independent
        // bonus clock only when bonuses actually apply to this leg; otherwise stop it.
        if (callBonusPresenter != null) startBonusTicker() else stopBonusTicker()
    }
    private companion object {
        private const val TIMER_RESYNC_INTERVAL_MS = 30_000L
    }
    private val profileViewModel: ProfileViewModel by viewModels()
    private val userAvatarViewModel: UserAvatarViewModel by viewModels()
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()
    private val callDropStatusViewModel: CallDropStatusViewModel by viewModels()
    private val callStatusViewModel: CallStatusViewModel by viewModels()
    private val isCaller: Boolean by lazy { intent.getBooleanExtra("IS_CALLER", false) }

    private var currentAudioRoute: com.gmwapp.hima.utils.CallAudioRouter.AudioRoute =
        com.gmwapp.hima.utils.CallAudioRouter.AudioRoute.EARPIECE
    private val ludoFcmViewModel: LudoFcmViewModel by viewModels()

    private var isVideoCallGoing : Boolean = false
    // B18 (switch-to-video parity): auto-hide the video-mode chrome after 10s idle.
    // Only active while isVideoCallGoing; normal audio mode is unaffected.
    private var videoChromeVisible = true
    // Whether the female-only icebreaker hint button is active for this call;
    // when true it rides the video-mode chrome auto-hide instead of being pinned.
    private var icebreakerActive = false
    private val CHROME_AUTOHIDE_MS = 10_000L
    private val chromeAutoHideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val chromeAutoHideRunnable = Runnable { setVideoChromeVisible(false) }
    private var remoteSurfaceView: SurfaceView? = null
    private var isRemoteBlurVisible = false
    private var pendingRemoteBlurHide = false
    private var localPreviewSurface: SurfaceView? = null
    private var localPreviewOffsetX = Float.NaN
    private var localPreviewOffsetY = Float.NaN
    private var localPreviewTouchOffsetX = 0f
    private var localPreviewTouchOffsetY = 0f
    private var localPreviewDragStartX = 0f
    private var localPreviewDragStartY = 0f
    private var isDraggingLocalPreview = false
    var isAudioCallIdReceived: Boolean = false


    private var isSwitchingToAudio = false // ✅ Prevent multiple calls
    private var isSwitchingToVideo = false // ✅ Prevent multiple calls

    // B081 — when this creator accepts an audio→video switch for a single
    // call, the server (incorrectly) flips her global `video_status` to 1,
    // which makes random users start sending her video calls. We snapshot
    // the user's pre-switch `video_status` here and restore it explicitly
    // on call end so her global availability isn't silently changed by an
    // individual call's media-type negotiation.
    private var savedVideoStatusBeforeSwitch: Int? = null

    var isClicked : Boolean = false

    var switchCallID =0
    // Mirrors the MaleAudioCallingActivity fix: video switch needs a fresh
    // call_id from getCallIdforCallSwitch(); if the user confirms before it
    // arrives (or a transient failure), park the switch and proceed when it
    // lands instead of dead-ending with a bare "Try Again".
    private var pendingVideoSwitchSeconds: Int? = null
    private var pendingVideoSwitchUserId: Int? = null
    private val switchCallIdHandler = Handler(Looper.getMainLooper())
    private var switchCallIdTimeoutRunnable: Runnable? = null
    private var switchCallIdObserverRegistered = false
    // B069 storm fix: these observers were re-attached on every switch send,
    // stacking duplicate observers on shared LiveData and multiplying the
    // clear↔observe churn. Register each at most once per activity.
    private var notificationSentObserverRegistered = false
    private var callSwitchAcceptanceObserverRegistered = false
    var receiverName = ""

    private var isMuted = false
    // Peer's current mic-mute state (from onUserMuteAudio) so the top video-mode
    // indicator reflects it correctly across an audio<->video switch.
    private var isPeerAudioMuted = false
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
    // B196 false-positive fix: tracks ONLY a real cellular (SIM) call — the sole
    // interrupt source allowed to surface the "On hold — phone call in progress"
    // banner + peer HOLD signal. Audio-focus interrupts (notifications, the
    // assistant, other apps, VoIP) still mute audio but never set this, so they
    // no longer raise a phantom on-hold banner when there is no phone call.
    private var cellularInterrupt = false
    private var storedRemainingTime: String? = null
    private var storedVideoRemainingTime: String? = null
    private var countDownTimer: CountDownTimer? = null
    // B4/TC_006+TC_021: epoch-ms when remaining time first read 0 (0 = not currently zero).
    // Distinguishes a transient zero (rescued by the next positive resync) from a sustained
    // zero (genuine coin-exhaustion → end the call). Reset on any positive read.
    private var zeroRemainingSinceMs = 0L

    private var startTime: String = ""
    // Bug #4 fix (2026-05-25): also capture a monotonic millis snapshot so we
    // can compute duration with sub-second accuracy and avoid the wall-clock
    // drift that made male-vs-female call durations diverge (male side uses
    // System.currentTimeMillis(), female was using SimpleDateFormat HH:mm:ss
    // strings which truncated by up to 999ms on each end).
    private var callStartMillis: Long = 0L
    private var endTime: String = ""

    // F1 Call Duration Bonus — drives the display-only milestone popups.
    private var callBonusPresenter: com.gmwapp.hima.utils.CallBonusPresenter? = null
    private var bonusInitDone = false
    // Own anchor (callStartMillis resets on reconnect; this stays fixed at first join).
    private var bonusStartMillis: Long = 0L
    // F1: make admin bonus-config edits reflect on THIS call. onUserJoined kicks off a
    // fresh getSettings() and defers the popup anchor until that live config is cached
    // (or a short fallback fires) — so a change saved in the panel shows on the very next
    // call instead of only after the app re-fetches settings on some other screen.
    private var bonusPeerJoined = false
    private var bonusAnchoredFirstLeg = false
    private val bonusAnchorFallbackHandler = Handler(Looper.getMainLooper())
    // How long to wait for the fresh settings fetch before anchoring with cached config.
    // The first teaser window is 60s wide, so a couple seconds' skew is invisible; this
    // only guards against a slow/failed network swallowing the popups entirely.
    private val BONUS_SETTINGS_WAIT_MS = 2000L
    // Dedicated 1s ticker for the bonus popups. The remaining-time CountDownTimer
    // used to be the only driver, but it is cancelled/recreated on every
    // get_remaining_time refresh and stalls on a transient "00:00:00" — a stall
    // spanning a teaser's 60s window silently swallowed the "coming up" popup
    // while the milestone payout (open-ended >= check) still fired. This ticker
    // is anchored to bonusStartMillis and runs independently so the popups never
    // miss their window. UI-thread Handler because onTeaser/onPayout touch views.
    private val bonusTickHandler = Handler(Looper.getMainLooper())
    private val bonusTickRunnable = object : Runnable {
        override fun run() {
            callBonusPresenter?.let { p ->
                if (bonusStartMillis > 0L) {
                    p.onTick((System.currentTimeMillis() - bonusStartMillis) / 1000L)
                }
            }
            bonusTickHandler.postDelayed(this, 1000L)
        }
    }
    private fun startBonusTicker() {
        bonusTickHandler.removeCallbacks(bonusTickRunnable)
        bonusTickHandler.post(bonusTickRunnable)
    }
    private fun stopBonusTicker() {
        bonusTickHandler.removeCallbacks(bonusTickRunnable)
    }

    /**
     * Anchor the first-leg bonus popups exactly once — from the settings observer the
     * moment fresh config is cached, or from the fallback timer if the network is slow.
     * Cancels the pending fallback so the two paths can never double-anchor, and no-ops
     * if the screen is tearing down (the fallback could fire after finish()).
     */
    private fun anchorBonusFirstLegOnce(callType: String) {
        if (bonusAnchoredFirstLeg || isFinishing || isDestroyed) return
        bonusAnchoredFirstLeg = true
        bonusAnchorFallbackHandler.removeCallbacksAndMessages(null)
        anchorBonusForLeg(callType, showIntro = true)
    }
    private var isSwitchRequestPending = false

//    private lateinit var model: Model
//    private lateinit var recognizer: Recognizer

    private val executor = Executors.newSingleThreadExecutor()
    private val accountViewModel: AccountViewModel by viewModels()

    var blockWords: List<String> = emptyList()

    var call_Id: Int = 0
    private var pendingLudoAction: String? = null
    private var currentLudoInviteId: String? = null

    private var switchDialog: AlertDialog? = null  // Track current dialog
    private var faceDialog: Dialog? = null

    var isBlockWordDetected : Boolean = false


    private var isRemoteUserJoined = false

    // CALLER_ACCEPT_RESEND_2026_06_30 — the receiver fires "accepted" to the caller
    // ONCE on tap; a single dropped FCM strands the caller on "Connecting" (then a
    // black screen / "couldn't connect"). As the accepting side (!isCaller), keep
    // re-sending "accepted" every 1.5s until the caller actually joins the channel
    // (onUserJoined) or we hit the cap. Idempotent on the caller (it finishes on the
    // first "accepted"). Self-stops on connect/destroy. Connecting/billing untouched.
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
                    // B043/B044 — see MaleAudioCallingActivity for the rationale
                    // on dropping the user-blaming wording.
                    Toast.makeText(this@FemaleAudioCallingActivity,"Couldn't connect — please try again", Toast.LENGTH_LONG).show()

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
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
        )
    } else {
        arrayOf(
            Manifest.permission.RECORD_AUDIO
        )
    }


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
            // creating — prevents the cross-call engine-overlap black-screen race.
            agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.createEngineSafely(config, "FemaleAudioCalling")

            // Enable only audio module (Disable video)
            agoraEngine!!.enableAudio()
            // Configure audio profile BEFORE joinChannel to avoid mid-session track reset.
            // B186: SPEECH_STANDARD pinned codec to 32 kHz mono / 18 kbps;
            // on OEMs whose mic captured outside that profile, codec negotiation
            // failed and both sides connected silent. DEFAULT lets Agora pick per
            // the channel profile (COMMUNICATION here).
            agoraEngine!!.setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT, Constants.AUDIO_SCENARIO_DEFAULT)
            // B037: smoothFactor 1 (not 3) so the speak-wave reacts to actual
            // speech bursts instead of a 600ms moving average that hid soft
            // voices entirely; threshold lowered to 30 in onAudioVolumeIndication.
            agoraEngine!!.enableAudioVolumeIndication(200, 1, true)
            // Set the SDK's default audio route + explicit current route so users hear
            // audio in the expected output immediately (also helps Bluetooth/headset).
            agoraEngine!!.setDefaultAudioRoutetoSpeakerphone(true)
            agoraEngine!!.setEnableSpeakerphone(isSpeakerOn)
            Log.d("AgoraTiming", "FemaleAudio setupAudioSDKEngine done at ${System.currentTimeMillis()}")

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
                    // B001: same effect when a GSM/WhatsApp call interrupts.
                    agoraEngine?.muteAllRemoteAudioStreams(true)
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
        binding = ActivityFemaleAudioCallingBinding.inflate(layoutInflater)
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

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        receiverId = intent.getIntExtra("RECEIVER_ID", -1)
        // C-05: paint the caller's name/avatar we were handed IMMEDIATELY so the
        // screen isn't a blank white circle while getUserAvatar() below refreshes
        // it from the server (avatarObservers just overwrites with the same data).
        intent.getStringExtra("Caller_NAME")?.takeIf { it.isNotBlank() }?.let {
            binding.tvMaleName.text = com.gmwapp.hima.utils.DisplayName.clean(it)
        }
        intent.getStringExtra("Caller_Image")?.takeIf { it.isNotBlank() }?.let {
            Glide.with(this).load(it)
                .apply(RequestOptions.circleCropTransform())
                .into(binding.ivMaleUser)
        }
        // CALLER_ACCEPT_RESEND_2026_06_30 — if we're the accepting side, keep nudging
        // the caller with "accepted" until they join (no-op for the caller side).
        startAcceptResend()
        call_Id = intent.getIntExtra("CALL_ID", 0)
        // Bug #1 fix (2026-05-25): persist peer id so MyFirebaseMessagingService
        // can match incoming switchToVideo/switchToAudio FCMs. See twin fix in
        // MaleAudioCallingActivity for the full root-cause comment.
        if (receiverId > 0) BaseApplication.getInstance()?.saveSenderId(receiverId)

        Log.d("FemaleAudioCallingCheck", "Channel: $channelName, Receiver: $receiverId, callID : $call_Id")
        Log.d("FemaleAudioCallingCheck", "$call_Id")
        Log.d("AgoraTiming", "FemaleAudio onCreate at ${System.currentTimeMillis()}")

        // Use pre-fetched token from connecting/accept screen if available, else fetch from backend
        val intentToken = intent.getStringExtra("AGORA_TOKEN")
        val intentAppId = intent.getStringExtra("AGORA_APP_ID")
        if (!intentToken.isNullOrEmpty() && !intentAppId.isNullOrEmpty()) {
            Log.d("AgoraTiming", "FemaleAudio using pre-fetched token at ${System.currentTimeMillis()}")
            token = intentToken
            appId = intentAppId
            if (!checkSelfPermission()) {
                ActivityCompat.requestPermissions(
                    this@FemaleAudioCallingActivity,
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


        showGreyScreen()
        observeRemainingTimeUpdated()
        observeGiftReceived()
        observeForceEndCall()
        observeCallSwitchRequest()

        onAddcoinClicked()
        // B151: debounce mute + speaker so rapid taps can't desync the icon
        // from Agora's mute / AudioManager comm-device state.
        // U-06/TC-HMA-003: 250ms (not the 500ms default) — these are pure-UI
        // toggles; 500ms swallowed deliberate mute/unmute & speaker taps so the
        // button felt unresponsive / "disabled" (~50% miss). See B066 precedent.
        binding.btnMuteUnmute.setOnSingleClickListener(debounceMs = 250L) {
            toggleMute()
        }

        binding.btnSpeaker.setOnSingleClickListener(debounceMs = 250L) {
            onSpeakerButtonClicked()
        }

        endcallBtn()

//        onMenuClicked()
        onBackPressedBtn()
        userAvatarViewModel.getUserAvatar(receiverId)
        avatarObservers()

        handleCallSwitch()
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

    // Icebreaker: female-only hint button during a call, gated by admin toggles
    // (master + audio) delivered via cached settings. On tap, fetch 5 rotating
    // questions from the server and show them in the dialog.
    private fun setupIcebreakerIfFemale(
        settings: com.gmwapp.hima.retrofit.responses.SettingsResponseData? =
            BaseApplication.getInstance()?.getPrefs()?.getSettingsData()
    ) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        if (!userData.gender.equals("female", ignoreCase = true)) {
            icebreakerActive = false
            binding.icebreakerHintButton.visibility = View.GONE
            return
        }
        val enabled = (settings?.icebreaker_enabled ?: 0) == 1 &&
            (settings?.icebreaker_audio_enabled ?: 1) == 1
        if (!enabled) {
            icebreakerActive = false
            binding.icebreakerHintButton.visibility = View.GONE
            return
        }
        icebreakerActive = true
        binding.icebreakerHintButton.setOnSingleClickListener {
            requestAndShowIcebreakerQuestions(userData.id)
        }
        // In video mode the button rides the 10s chrome auto-hide, so honour the
        // current chrome state instead of force-showing. This setup re-runs when
        // fresh settings arrive from the server, which on the caller side can land
        // AFTER the auto-hide already fired — force-setting VISIBLE there left the
        // button orphaned on-screen forever (why it "never disappeared" on
        // creator->user calls but was fine the other way). In plain audio mode
        // there is no auto-hide, so the button simply stays visible.
        if (isVideoCallGoing && !videoChromeVisible) {
            binding.icebreakerHintButton.visibility = View.INVISIBLE
        } else {
            binding.icebreakerHintButton.alpha = 1f
            binding.icebreakerHintButton.visibility = View.VISIBLE
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
                    val questions = body?.data?.map { it.question }?.filter { it.isNotBlank() } ?: emptyList()
                    if (body?.success == true && questions.isNotEmpty()) {
                        showIcebreakerDialog(questions)
                    } else {
                        Toast.makeText(
                            this@FemaleAudioCallingActivity,
                            body?.message ?: "No icebreaker questions available",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<IcebreakerQuestionsResponse>, t: Throwable) {
                    Log.e("IcebreakerQuestions", "API failed: ${t.message}")
                    Toast.makeText(
                        this@FemaleAudioCallingActivity,
                        "Failed to load questions",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onNoNetwork() {
                    Toast.makeText(
                        this@FemaleAudioCallingActivity,
                        "No internet connection",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun showIcebreakerDialog(questions: List<String>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_icebreaker_questions, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val container =
            dialogView.findViewById<android.widget.LinearLayout>(R.id.ll_icebreaker_questions)
        questions.forEachIndexed { index, question ->
            val row = layoutInflater.inflate(R.layout.item_icebreaker_question, container, false)
            row.findViewById<TextView>(R.id.tv_q_number).text = (index + 1).toString()
            row.findViewById<TextView>(R.id.tv_q_text).text = question
            container.addView(row)
        }

        val btnClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btn_close_icebreaker_dialog
        )
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun setupLudoInviteFlow() {
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        applyPlayLudoVisibility(
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.play_ludo ?: false
        )

        profileViewModel.getUserLiveData.observe(this) { response ->
            val fresh = response?.data ?: return@observe
            // B075 — only need play_ludo here; preserve the user's audio/video toggle
            // intent so a mid-call refresh doesn't silently flip her availability when
            // she returns to FemaleHomeFragment after the call.
            BaseApplication.getInstance()?.getPrefs()?.setUserDataPreservingLocalIntent(fresh)
            applyPlayLudoVisibility(fresh.play_ludo ?: false)
        }
        if (currentUserId != 0) {
            profileViewModel.getUsers(currentUserId)
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
        val currentUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_ludo_send_invite, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
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
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
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
                        this@FemaleAudioCallingActivity,
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
                        // Persist fresh settings so all cached reads on this screen stay current.
                        BaseApplication.getInstance()?.getPrefs()?.setSettingsData(settingsData)
                        // F1: peer already here → anchor the bonus popups now that LIVE
                        // config is cached, so admin edits reflect on this call not the next.
                        if (bonusPeerJoined) anchorBonusFirstLegOnce("audio")
                        // Fresh settings arrived — re-evaluate the icebreaker button so it
                        // appears even if the cache was stale when onCreate first ran.
                        setupIcebreakerIfFemale(settingsData)
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
        binding.tvFemaleName.setText(com.gmwapp.hima.utils.DisplayName.clean(name))
        Glide.with(this)
            .load(image)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivFemaleUser)
    }

    private fun setupIplTeamBadges() {
        // App-side kill-switch: when IPL is disabled, force both badges GONE
        // and skip the picker wiring entirely.
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
            // Show female (self) team badge + ring
            binding.femaleIplBadge.visibility = View.VISIBLE
            binding.tvFemaleIplTeam.text = team.abbreviation
            val femaleDot = binding.femaleIplDot.background.mutate() as android.graphics.drawable.GradientDrawable
            femaleDot.setColor(android.graphics.Color.parseColor(team.primaryColor))
            binding.femaleTeamRing.visibility = View.VISIBLE
            val ringDrawable = binding.femaleTeamRing.background.mutate() as android.graphics.drawable.GradientDrawable
            ringDrawable.setColor(android.graphics.Color.parseColor(team.primaryColor))
        }

        // Demo: Show a random team for the male caller
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

        // B137 — STARTED is the earliest legal state for FGS start on
        // Android 14/15; using it lets us fire from onStart instead of
        // onResume so the session-in-progress notification appears sooner.
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

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            isJoined = true
            Log.d("AgoraTiming", "FemaleAudio onJoinChannelSuccess at ${System.currentTimeMillis()}")
            // B186 — defensive unmute on join. See MaleAudioCallingActivity
            // onJoinChannelSuccess for full rationale.
            mutedByInterrupt = false
            if (!isMuted) agoraEngine?.muteLocalAudioStream(false)
            agoraEngine?.muteAllRemoteAudioStreams(false)
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
            // I006 — pass the WORSE of the two directions. See
            // MaleAudioCallingActivity for full rationale.
            com.gmwapp.hima.utils.CallQualityUi.apply(
                this@FemaleAudioCallingActivity,
                binding.ivSignalStrength,
                binding.reconnectBanner,
                maxOf(txQuality, rxQuality),
                null
            )
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            com.gmwapp.hima.utils.CallQualityUi.apply(
                this@FemaleAudioCallingActivity,
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
        }

        // I024 — detect PEER-side network drops. See MaleAudioCallingActivity
        // for full rationale.
        override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteAudioStateChanged(uid, state, reason, elapsed)
            // TC-HMA-001: drive the peer-mute indicator from this RELIABLE signal —
            // it reports an already-muted peer on subscribe, which the deprecated
            // onUserMuteAudio misses when the user joins ALREADY muted. Mute/unmute
            // is intentional silence, so update the badge and return WITHOUT arming
            // the reconnect watchdog below.
            if (reason == Constants.REMOTE_AUDIO_REASON_REMOTE_MUTED) {
                isPeerAudioMuted = true
                updateMuteIndicators()
                return
            }
            if (reason == Constants.REMOTE_AUDIO_REASON_REMOTE_UNMUTED) {
                isPeerAudioMuted = false
                updateMuteIndicators()
                return
            }
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

        override fun onUserOffline(uid: Int, reason: Int) {
            // Peer left — clear any stale "on hold" banner (no UNHOLD arrives if
            // they dropped abruptly while on hold).
            runOnUiThread { runCatching { binding.peerOnHoldBanner.visibility = View.GONE } }

            // B-CALL RC#3: reason=1 = peer connection TIMED OUT (may rejoin). Arm the
            // watchdog's HARD-OFFLINE window (NET-004: ~15s — Agora already waited
            // ~20s before firing this) instead of ending; onUserJoined / stream-resume
            // clears it on rejoin. Fail-safe: the watchdog ends the call if the peer
            // never returns. reason 0 (voluntary leave) / 2 (kicked) end immediately below.
            if (reason == 1 && isRemoteUserJoined) {
                Log.w("CallReconnect", "FemaleAudio onUserOffline reason=1 — hard-offline grace, not ending")
                reconnectWatchdog.peerOffline(gone = true)
                return
            }

            updateCallEndDetails()
            stopCountdown()
           // showMessage("Remote user left")


            val intent = Intent(this@FemaleAudioCallingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        override fun onUserJoined(uid: Int, elapsed: Int) {
            // TC-NET-005: peer connected → begin per-side liveness heartbeats.
            com.gmwapp.hima.utils.CallHeartbeat.start(call_Id)
         //   showMessage("Remote user joined $uid")
            Log.d("AgoraTiming", "FemaleAudio onUserJoined at ${System.currentTimeMillis()}")
            startTime = dateFormat.format(Date()) // Set call end time in IST
            // Bug #4 fix — snapshot monotonic millis at the same instant.
            callStartMillis = System.currentTimeMillis()
            isRemoteUserJoined= true
            // NET-004: peer is back on the channel — clear any hard-offline grace.
            reconnectWatchdog.peerOffline(gone = false)
            stopAcceptResend() // CALLER_ACCEPT_RESEND — caller is here, stop nudging
            videoUid = uid
            startCallingService()
            getRemainingTime()
            startTimerResync()

            // F1 Call Duration Bonus — pull the LATEST config before popups start so admin
            // edits reflect on THIS call, then anchor once (guarded so reconnect doesn't
            // replay the intro). The anchor fires from the settings observer when fresh
            // config lands, or from the fallback timer if the network is slow/failed (using
            // cached config — a slow fetch can't swallow the popups). Marshaled to the UI
            // thread (this is a worker-thread callback) so it can't race the observer.
            if (!bonusInitDone) {
                bonusInitDone = true
                runOnUiThread {
                    bonusPeerJoined = true
                    accountViewModel.getSettings()
                    bonusAnchorFallbackHandler.postDelayed(
                        { anchorBonusFirstLegOnce("audio") }, BONUS_SETTINGS_WAIT_MS
                    )
                }
            }


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



            initVosk()

//            agoraEngine?.registerAudioFrameObserver(audioFrameObserver)

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
            // Renders maleMute (audio mode) or iv_peer_mute_top (video mode).
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
            options.autoSubscribeVideo = false
            options.publishMicrophoneTrack = true
            options.publishCameraTrack = false

            Log.d("AgoraTiming", "FemaleAudio joinChannel at ${System.currentTimeMillis()}")
            agoraEngine!!.joinChannel(token, channelName, uid, options)
            Log.d("AgoraTag", "Joined channel: $channelName with token: $token")

        } else {
            Toast.makeText(applicationContext, "Permissions were not granted", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun leaveChannel(view: View) {
        // 2026-05-22 — instant peer-hangup FCM (fire-and-forget). See
        // MaleVideoCallingActivity for full rationale.
        FcmUtils.notifyPeerOfHangup(receiverId, call_Id)
        // B181 — clear the "user is busy" guard before navigating back so
        // fragments' onResume can refresh creator/availability data.
        FcmUtils.isUserAvailable = 0
        // B082 — close any switch-call dialog (Accept video request) before
        // the activity tears down. Without this the dialog window lingers
        // on top of RatingActivity as the "wrong VIDEO popup" Yuvanesh saw
        // after an audio call ended with an unanswered switch request.
        switchDialog?.dismiss()
        switchDialog = null
        // Also drain any in-flight switch payload so a stale FCM that lands
        // during the 50ms finish delay can't fire the observer again.
        FcmUtils.clearCallSwitch()
        stopTimerResync()
        // B081 — restore the creator's pre-switch video_status if this call
        // upgraded audio→video. The server flips video_status to 1 during
        // the per-call upgrade; without this restore she'd silently become
        // "accepting video calls" globally for everyone after the call ends.
        restoreGlobalVideoStatusIfSwitched()
        // Bug #5B fix (2026-05-25): always teardown Agora regardless of isJoined
        // (see MaleAudioCallingActivity twin for full comment). releaseEngineSync
        // is idempotent so it's safe to call even when !isJoined.
        stopCountdown()
        stopBonusTicker() // F1: freeze the bonus clock the instant the call ends (no late payout flash)
        stopMicRevokeWatcher()
        try {
            agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.releaseEngineSync(
                agoraEngine, "FemaleAudioCalling", hasVideo = false
            )
        } catch (t: Throwable) {
            Log.w("FemaleAudioCalling", "leaveChannel teardown threw (safe): ${t.message}")
        }
        val wasJoined = isJoined
        isJoined = false
        HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
        if (wasJoined) {
            updateCallEndDetails()
        }
        Handler(Looper.getMainLooper()).postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                val intent = Intent(this@FemaleAudioCallingActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                Log.d("blockword","$isBlockWordDetected")
                startActivity(intent)
                finish()
            }, 50L)
    }

    fun updateCallEndDetails(){

        if (startTime.isNotEmpty()) {
            endTime = dateFormat.format(Date()) // Set call end time only if startTime is not empty
        }

        // See MaleAudioCallingActivity.updateCallEndDetails for rationale —
        // dedupe same-side duplicate enqueues so triple-billing doesn't
        // happen when multiple lifecycle paths (onUserOffline + leaveChannel
        // + late FCM observers) all fire end-of-call within milliseconds.
        com.gmwapp.hima.utils.CallEndUpdater.enqueueIfFresh(
            context = this@FemaleAudioCallingActivity,
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

    /**
     * Fetches male's remaining call time so the on-screen countdown can start
     * and auto-hangup at 00:00. The original code crashed on network failure
     * (TODO() throw — B184). Now we log + schedule a single retry 3s later
     * so a brief network blip at pickup doesn't permanently kill the timer.
     *
     * If the timer never starts, male keeps talking past his coin allowance
     * and the server's duration-based deduction can't cap properly — that's
     * the bigger business risk this retry guards against.
     */
    private fun getRemainingTime(attempt: Int = 0) {
        val maxRetries = 3
        receiverId?.let { profileViewModel.getRemainingTime(it,"audio", object :
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

                    startCountdown(newTime, data.ends_at_ms, data.server_now_ms); callBonusPresenter?.setRemainingToday(data.bonus_remaining_today)
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

                // F1 Call Duration Bonus popups are now driven by the dedicated
                // bonusTickRunnable (started in anchorBonusForLeg), not by this
                // remaining-time timer — it stalls on refresh/transient-zero and
                // was swallowing the "coming up" teaser window. See startBonusTicker().
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
        chromeAutoHideHandler.removeCallbacks(chromeAutoHideRunnable) // B18: stop auto-hide timer
        stopBonusTicker() // F1: stop the independent bonus clock so it can't leak past the call
        bonusAnchorFallbackHandler.removeCallbacksAndMessages(null) // F1: cancel any pending anchor
        com.gmwapp.hima.utils.CallBonusViews.clear(binding.bonusOverlay) // F1: drop any bonus popups
        stopAcceptResend() // CALLER_ACCEPT_RESEND — clean up any pending nudges
        com.gmwapp.hima.utils.CallHeartbeat.stop() // TC-NET-005: end liveness heartbeats
        // B181 backstop — covers system-killed activities that bypass leaveChannel.
        FcmUtils.isUserAvailable = 0
        // B082 backstop — close lingering switch-call dialog so it doesn't
        // float over RatingActivity / next screen as a phantom video popup.
        switchDialog?.dismiss()
        switchDialog = null
        // Cancel any pending video-switch wait so its timeout can't fire post-teardown.
        switchCallIdTimeoutRunnable?.let { switchCallIdHandler.removeCallbacks(it) }
        switchCallIdTimeoutRunnable = null
        pendingVideoSwitchSeconds = null
        pendingVideoSwitchUserId = null
        // B081 backstop — restore pre-switch video_status if leaveChannel
        // never ran (system-killed activity).
        restoreGlobalVideoStatusIfSwitched()
        BaseApplication.getInstance()?.markCallEnded()
        // GHOST_CALL_TTL_2026_07_03 — the no-arg markCallEnded() above only flips
        // isCallActive; stamp THIS call_id as ended too so the 5-min recently-ended
        // window restarts from actual hang-up (not from answer). Without this, a
        // call that ran >5 min lets its call_id expire mid-call, and a late/retried
        // duplicate "incoming call" push for it could slip past wasCallRecentlyEnded
        // and ghost-ring after the call ended. markCallEnded(id) no-ops when id<=0.
        BaseApplication.getInstance()?.markCallEnded(call_Id)
        BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
        HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
        cancelTimeoutTracking()
        stopCallingService()
        stopCountdown()
        audioFocusHelper?.abandon()
        audioFocusHelper = null
        audioRouter?.release()
        audioRouter = null
        phoneStateHelper?.unregister()
        phoneStateHelper = null
        btWatcher?.unregister()
        btWatcher = null
        reconnectWatchdog.cancel()

        // B143: deterministic teardown — disable audio, leave channel, then block on destroy.
        stopMicRevokeWatcher()
        agoraEngine = com.gmwapp.hima.utils.AgoraTeardownHelper.releaseEngineSync(
            agoraEngine, "FemaleAudioCalling", hasVideo = false
        )

        if (isRemoteUserJoined==true){
            // B1: route through the earnings honour screen only when the bonus feature
            // applies to THIS call type (master + audio/video switch + tiers) — the same
            // gate the in-call popups use, so a bonus-off type no longer pops the sheet.
            // It forwards these extras to RatingActivity; else go direct to rating.
            val callType = if (isVideoCallGoing) DConstants.VIDEO else DConstants.AUDIO
            val bonusOn = com.gmwapp.hima.utils.CallBonusPresenter.isEnabledForType(
                BaseApplication.getInstance()?.getPrefs()?.getSettingsData()?.call_bonus, callType
            )
            val target = if (bonusOn) EarningsHonourActivity::class.java else RatingActivity::class.java
            val intent = Intent(this@FemaleAudioCallingActivity, target)
            intent.putExtra(DConstants.RECEIVER_NAME, receiverName)
            intent.putExtra(DConstants.RECEIVER_ID, receiverId)
            intent.putExtra(DConstants.CALL_ID, call_Id)
            intent.putExtra(DConstants.CALL_TYPE, callType)
            startActivity(intent)
            Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
        }


        Log.d("Lifecycle", "onDestroy() called. Firestore listener removed.")
    }

    private fun newRemainingTime(){

        if (isVideoCallGoing){

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
                        Log.d("resumedtag","videocalltime - $newTime")
                        if (storedVideoRemainingTime == null) {
                            storedVideoRemainingTime = newTime // Store first-time value
                            startCountdown(newTime, data.ends_at_ms, data.server_now_ms); callBonusPresenter?.setRemainingToday(data.bonus_remaining_today)

                        }

                        if (storedVideoRemainingTime != null) {
                            storedVideoRemainingTime = newTime // Update stored value
                            stopCountdown()
                            startCountdown(newTime, data.ends_at_ms, data.server_now_ms); callBonusPresenter?.setRemainingToday(data.bonus_remaining_today)
                        }



                    }
                }
            }) }

        }else{
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
                    Log.d("resumedtag","auidocalltime - $newTime")

                    // Always (re)start the countdown — previously gated on
                    // `storedRemainingTime != null`, which meant if the initial
                    // getRemainingTime() failed at pickup the countdown could
                    // never start later via FCM refresh, leaving the call with
                    // no auto-hangup at 00:00 and male over-talking past his
                    // coin allowance (paired with B184 fix).
                    storedRemainingTime = newTime
                    stopCountdown()
                    startCountdown(newTime, data.ends_at_ms, data.server_now_ms); callBonusPresenter?.setRemainingToday(data.bonus_remaining_today)
                }
            }
        }) }}
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
     * Server-driven force-end observer. Backend pushes `callEndedNoCoins`
     * FCM when the male's coins run out during an active call; this hangs
     * us up immediately if the signal matches our current call, closing
     * the gap where a stuck client countdown could let the male over-talk
     * past his balance (B184 follow-up).
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
        // Cinematic gift moment — shared GiftCinema overlay (receiver side).
        BaseApplication.getInstance()?.playSendGiftSound()
        com.gmwapp.hima.widgets.GiftCinema.send(
            activity = this,
            giftUrl = image,
            recipientView = binding.ivFemaleUser,
            lite = false
        )
    }


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
            if (isVideoCallGoing) switchToAudio() else switchToVideo()
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


                showSwitchVideoDialog(totalSeconds, userid)


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
            // B082 — bail if the call has already ended; a late switch
            // payload arriving in the finish window must not act on this
            // activity (it can fire enableVideoCall/enableAudioCall which
            // surface their own dialogs).
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
                            Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
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


    fun getCallIdforCallSwitch(callType: String){

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        var userId = userData?.id
        receiverId?.let { it1 ->
            userId?.let {
                femaleUsersViewModel.callFemaleUser(
                    it1, it,callType,1
                )
            }
            // Register once — getCallIdforCallSwitch can be called again on retry.
            if (!switchCallIdObserverRegistered) {
                switchCallIdObserverRegistered = true
                callIdObserver()
            }
        }
    }

    private fun callIdObserver() {
        femaleUsersViewModel.callFemaleUserResponseLiveData.observe(this){
            if (it != null && it.success) {
                switchCallID = it.data?.call_id ?: 0

                isAudioCallIdReceived = true

                Log.d("switchCallIdObserver", "$switchCallID")

                // A confirmed video switch was waiting on this id — send it now.
                val pendingSecs = pendingVideoSwitchSeconds
                if (pendingSecs != null && switchCallID != 0) {
                    val uid = pendingVideoSwitchUserId
                    pendingVideoSwitchSeconds = null
                    pendingVideoSwitchUserId = null
                    switchCallIdTimeoutRunnable?.let { r -> switchCallIdHandler.removeCallbacks(r) }
                    if (!isFinishing && !isDestroyed) {
                        if (uid != null) {
                            sendSwitchCallRequestNotification(uid, receiverId, "video", "switchToVideo $switchCallID")
                        }
                        Toast.makeText(this, "Video session request sent", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (pendingVideoSwitchSeconds != null) {
                // Hard failure (null / success=false) while a switch was waiting.
                pendingVideoSwitchSeconds = null
                pendingVideoSwitchUserId = null
                switchCallIdTimeoutRunnable?.let { r -> switchCallIdHandler.removeCallbacks(r) }
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this,
                        "Couldn't start video session. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }








    fun observeCallSwitchRequest() {
        // B069 — capture the moment this observer activated so we can drop
        // any switch payload that was posted BEFORE this call's activity
        // existed (i.e. left over from a previous call). LiveData re-fires
        // its current value to every fresh observer on attach; without this
        // guard the "Accept video call?" dialog would pop the instant a
        // brand-new audio call started.
        val callSwitchObserverStartedAtMs = System.currentTimeMillis()
        FcmUtils.updatedCallSwitch.observe(this, androidx.lifecycle.Observer { updatedCallSwitch ->
            // B082 — a late switchToVideo FCM landing during the 50ms gap
            // between leaveChannel() and finish() would fire here and pop
            // a "video call" dialog over RatingActivity. Bail when the
            // activity is on its way out.
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
                    if (isVideoCallGoing==false){
                    switchCallID = newCallId

                    switchDialog?.dismiss()

                   switchDialog = showIncomingSwitchVideoRequest(userid, receiverName)

                }
}


                if (switchType=="switchToAudio"){
                    if (isVideoCallGoing){
                    switchCallID = newCallId

                    switchDialog?.dismiss()

                    // B069 follow-up — track explicit response so an
                    // outside-tap dismiss fires an implicit decline below.
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
                                // B069 follow-up — outside-tap/back = implicit decline.
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

        // B081 — snapshot the user's pre-switch video availability so we
        // can restore it in leaveChannel/onDestroy. Capture only once per
        // call (savedVideoStatusBeforeSwitch != null already guards re-entry).
        if (savedVideoStatusBeforeSwitch == null) {
            savedVideoStatusBeforeSwitch =
                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.video_status ?: 0
            Log.d(
                "B081",
                "snapshot video_status=$savedVideoStatusBeforeSwitch before audio→video switch"
            )
        }

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
        // Hide the parent avatars container too — otherwise the per-avatar
        // femaleMute/maleMute badges keep floating mid-screen over the remote
        // video (they're children of users_container). Mirrors MaleAudio.
        binding.usersContainer.visibility = View.GONE
        // Move the mute indicators to the top icon-only badges for video mode.
        updateMuteIndicators()


        runOnUiThread {
            // Enable video module
            agoraEngine?.enableVideo()

            // Tester report: creator with broken camera crashed when audio
            // upgraded to video. Detect that case BEFORE startPreview /
            // setupLocalVideo. publishCameraTrack stays FALSE on her side
            // (we still autoSubscribeVideo so she can SEE the peer); peer
            // sees her avatar via the B058 skeleton.
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
                // 2026-05-22 v18 — preserve mute state across audio→video switch
                publishMicrophoneTrack = !isMuted
                publishCameraTrack = cameraOk
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            })
            // 2026-05-22 v19 — belt & suspenders: also enforce device-level mute.
            // updateChannelMediaOptions alone was still letting peers hear the
            // caller in some cases (publishMicrophoneTrack flag race during
            // audio→video transition).
            agoraEngine?.muteLocalAudioStream(isMuted)
            if (cameraOk) {
                agoraEngine?.startPreview()
                Log.d("AgoraTiming", "FemaleAudio switched to VIDEO at ${System.currentTimeMillis()}")

                // Set up the local video view
                val localContainer = binding.localVideoViewContainer
                val localView = SurfaceView(this)
                localView.setZOrderMediaOverlay(true)
                localContainer.addView(localView)

                // Attach local video feed
                agoraEngine?.setupLocalVideo(VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0))

                // Make video UI visible
                binding.localVideoViewContainer.visibility = View.VISIBLE
                binding.localCardView.visibility = View.VISIBLE
                applySavedLocalPreviewPosition()
            } else {
                Log.w("CameraFallback", "FemaleAudio.switchToVideo: camera unavailable, skipping local preview")
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

            startTime =
                dateFormat.format(Date()) // Set call end time only if startTime is not empty

            // F1 — this is a NEW video leg: the server bills it as a separate row against the
            // VIDEO tiers, clocked from this same instant. Re-anchor the popups to match
            // (fresh clock + video tier table, no intro replay) so they stop showing stale
            // audio amounts and never flash a payout the server won't credit.
            anchorBonusForLeg("video", showIntro = false)

            // Hide video switch button and its container during video call
            binding.btnVideoCall.setImageResource(R.drawable.audiocall_img)
            binding.btnVideoCall.visibility= View.GONE
            binding.layoutButtons.visibility= View.GONE

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


            if (ContextCompat.checkSelfPermission(this@FemaleAudioCallingActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val granted = ContextCompat.checkSelfPermission(this@FemaleAudioCallingActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                Log.d("FaceDetection", "CAMERA permission granted: $granted")
                //startFaceDetectionCamera()
                val videoObserver = FaceDetectVideoFrameObserver(this@FemaleAudioCallingActivity)
                agoraEngine?.registerVideoFrameObserver(videoObserver)

            } else {
                Log.d("FaceDetection", "CAMERA permission granted: Not granted")

                ActivityCompat.requestPermissions(this@FemaleAudioCallingActivity, arrayOf(Manifest.permission.CAMERA), 22)
            }

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

    override fun onStart() {
        super.onStart()
        // B137 — fire FGS as soon as activity is visible (STARTED), not just
        // RESUMED, so the session notification appears sooner.
        startCallingService()
    }

    override fun onResume() {
        super.onResume()
        Log.d("resumedtag","resumed")
        // B162 — recover from a stuck interrupt-mute. If a permanent
        // AUDIOFOCUS_LOSS left mutedByInterrupt=true and the matching GAIN
        // never came back, the receiver's voice stays muted with no recovery
        // path. Activity resume = user is on the call = audio expected.
        audioFocusHelper?.request()
        if (mutedByInterrupt && audioFocusHelper?.hasFocus() == true) {
            Log.d("B162", "FemaleAudio onResume: clearing stuck interrupt mute (focus held)")
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

    /**
     * B081 — if this call upgraded from audio to video, push the user's
     * original `video_status` back to the server so the per-call upgrade
     * doesn't leak into her global availability. Idempotent: clears the
     * snapshot after firing so a subsequent leaveChannel/onDestroy in the
     * same activity lifecycle can't re-send the request.
     *
     * Called from both leaveChannel (synchronous, before navigation) and
     * onDestroy (backstop for system-killed activities).
     */
    private fun restoreGlobalVideoStatusIfSwitched() {
        val original = savedVideoStatusBeforeSwitch ?: return
        val uid = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        savedVideoStatusBeforeSwitch = null
        Log.d("B081", "restoring video_status=$original after audio→video call ended (uid=$uid)")
        femaleUsersViewModel.updateCallStatus(uid, DConstants.VIDEO, original)
    }

    private fun onAddcoinClicked() {
        binding.timerContainer.setOnSingleClickListener {
            // Timer container is now clickable and visible
            // You can add navigation to wallet activity if needed
            // val intent = Intent(this@FemaleAudioCallingActivity, WalletActivity::class.java)
            // startActivity(intent)
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        agoraEngine?.muteLocalAudioStream(isMuted)  // Mute or unmute audio
        val muteIcon = if (isMuted) R.drawable.mute_img else R.drawable.unmute_img
        binding.btnMuteUnmute.setImageResource(muteIcon)
        // B054 — flip the self mute badge so the creator sees the same
        // indicator the peer already sees.
        updateMuteIndicators()
    }

    /**
     * Single source of truth for the mute badges. In AUDIO mode the per-avatar
     * center badges (femaleMute = self, maleMute = peer) are used. In VIDEO mode
     * the avatars are hidden, so we show small icon-only badges at the TOP
     * (iv_self_mute_top / iv_peer_mute_top) instead of letting the center badges
     * float over the remote video at the old avatar positions.
     */
    private fun updateMuteIndicators() {
        runOnUiThread {
            if (isVideoCallGoing) {
                binding.femaleMute.visibility = View.INVISIBLE
                binding.maleMute.visibility = View.INVISIBLE
                binding.ivSelfMuteTop.visibility = if (isMuted) View.VISIBLE else View.GONE
                binding.ivPeerMuteTop.visibility = if (isPeerAudioMuted) View.VISIBLE else View.GONE
                binding.llVideoMuteTop.visibility = View.VISIBLE
            } else {
                binding.llVideoMuteTop.visibility = View.GONE
                binding.femaleMute.visibility = if (isMuted) View.VISIBLE else View.INVISIBLE
                binding.maleMute.visibility = if (isPeerAudioMuted) View.VISIBLE else View.INVISIBLE
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
        // B054 — keep the self mute badge in sync with restored mute state
        // (center badge in audio, top badge in video).
        updateMuteIndicators()
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
                Log.d("CallStatus", "FemaleAudio.hangup → ended/$endedByRole self=$userId peer=$receiverId callId=$call_Id isCaller=$isCaller")
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
    
    private fun showSwitchVideoDialog(totalSeconds: Int, userid: Int?) {
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
            if (totalSeconds > 360) {
                proceedOrAwaitVideoSwitch(totalSeconds, userid)
            } else {
                Toast.makeText(this, "$receiverName don't have enough coins", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }

    /**
     * Twin of MaleAudioCallingActivity.proceedOrAwaitVideoSwitch. If the fresh
     * call_id is ready, send the switch request now; otherwise re-request it,
     * show a connecting state, and proceed automatically when [callIdObserver]
     * receives it — surfacing a real error only after an 8s timeout or a hard
     * failure, instead of the old bare "Try Again".
     */
    private fun proceedOrAwaitVideoSwitch(totalSeconds: Int, userid: Int?) {
        if (switchCallID != 0) {
            if (userid != null) {
                Log.d("SwitchCallIdWhileSending", "$switchCallID")
                sendSwitchCallRequestNotification(userid, receiverId, "video", "switchToVideo $switchCallID")
            }
            Toast.makeText(this, "Video session request sent", Toast.LENGTH_SHORT).show()
            return
        }

        pendingVideoSwitchSeconds = totalSeconds
        pendingVideoSwitchUserId = userid
        Toast.makeText(this, "Preparing video session…", Toast.LENGTH_SHORT).show()
        getCallIdforCallSwitch("video")

        switchCallIdTimeoutRunnable?.let { switchCallIdHandler.removeCallbacks(it) }
        val timeout = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            if (pendingVideoSwitchSeconds != null && switchCallID == 0) {
                pendingVideoSwitchSeconds = null
                pendingVideoSwitchUserId = null
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
        tvMessage.text = "$requesterName requested for video session"

        val btnNo = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dialog_no)
        val btnYes = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dialog_yes)

        btnNo.text = "Decline"
        btnYes.text = "Accept"

        // B069 follow-up — track whether the user explicitly chose. If they
        // dismiss via outside-tap / back, the dismiss listener below treats
        // it as a decline so the requester isn't left with a hanging
        // "request pending" UI.
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
            // B069 follow-up — outside-tap / back-button dismiss with no
            // explicit choice = implicit decline. Suppressed when the
            // activity itself is going away (leaveChannel/onDestroy dismiss
            // the dialog; sending FCM in that window would race the call's
            // teardown and the requester is already on his way out).
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

    private fun onMenuClicked(){
        binding.btnMenu.setOnSingleClickListener {
            if (!isClicked){
                binding.layoutButtons.visibility = View.VISIBLE
                binding.ivMaleUser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd =  14.dpToPx()
                }
                binding.maleWave.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                 //   leftMargin = 8.dpToPx()
                }
                isClicked = true



            }else{
                binding.layoutButtons.visibility = View.INVISIBLE
                binding.ivMaleUser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd =  0
                }
                isClicked= false
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
                     //   leftMargin = 17.dpToPx()
                    }
                }
            }
            false // Return false to allow other touch events
        }

    }
    fun Int.dpToPx() = (this * Resources.getSystem().displayMetrics.density).toInt()

    // ===== B18 (switch-to-video parity): auto-hide video-mode chrome after 10s idle.
    //       No-op outside video mode, so normal audio calls are unaffected. =====
    /** Fade the video-mode chrome (top bar + controls) in/out. INVISIBLE (not GONE). */
    private fun setVideoChromeVisible(visible: Boolean) {
        if (!isVideoCallGoing || !::binding.isInitialized) return
        videoChromeVisible = visible
        // Include the icebreaker hint button only when it's active for this call,
        // so a hidden/disabled button isn't force-shown when chrome re-appears.
        val chrome = mutableListOf<View>(binding.topBar, binding.controlsContainer)
        if (icebreakerActive) chrome.add(binding.icebreakerHintButton)
        chrome.forEach { v ->
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
        listOf(binding.topBar, binding.controlsContainer).forEach { v ->
            v.animate().cancel()
            v.alpha = 1f
            v.visibility = View.VISIBLE
        }
        // Returning to audio mode: un-orphan the icebreaker button (plain audio
        // has no auto-hide, so it should be visible again if active for this call).
        if (icebreakerActive) {
            binding.icebreakerHintButton.animate().cancel()
            binding.icebreakerHintButton.alpha = 1f
            binding.icebreakerHintButton.visibility = View.VISIBLE
        }
    }

    /** Any touch keeps visible chrome alive by restarting the 10s countdown. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (isVideoCallGoing && videoChromeVisible) armVideoChromeAutoHide()
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
        // B18: back to audio mode — stop the auto-hide timer and restore controls.
        showVideoChromeAndCancelAutoHide()
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
        // Re-show the avatars container hidden when switching to video, and
        // move the mute indicators back to the per-avatar center badges.
        binding.usersContainer.visibility = View.VISIBLE
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
            Log.d("AgoraTiming", "FemaleAudio switched back to AUDIO at ${System.currentTimeMillis()}")

            // Hide local video view
            binding.localVideoViewContainer.removeAllViews()
            binding.localVideoViewContainer.visibility = View.GONE
            binding.localCardView.visibility = View.GONE

            // Hide remote video view
            binding.remoteVideoViewContainer.removeAllViews()
            binding.remoteVideoViewContainer.visibility = View.GONE

            // Reset video surfaces
            remoteSurfaceView = null

            // **Update button to reflect audio call and show it again**
            binding.btnVideoCall.setImageResource(R.drawable.videocall_img)
            binding.btnVideoCall.visibility= View.VISIBLE
            binding.layoutButtons.visibility= View.VISIBLE

            startTime =
                dateFormat.format(Date()) // Set call end time only if startTime is not empty

            // F1 — switched back to a NEW audio leg (server bills it as a separate audio row
            // clocked from here). Re-anchor the popups to the audio tiers + fresh clock.
            anchorBonusForLeg("audio", showIntro = false)

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
                        startCountdown(newTime, data.ends_at_ms, data.server_now_ms); callBonusPresenter?.setRemainingToday(data.bonus_remaining_today)
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
                        startCountdown(newTime, data.ends_at_ms, data.server_now_ms); callBonusPresenter?.setRemainingToday(data.bonus_remaining_today)
                    }
                }

            })
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
        // every other unmute site. Fixes "muted female still heard by male".
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

                localPreviewSurface = SurfaceView(this@FemaleAudioCallingActivity).apply {
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
                Log.e("FemaleAudioCallingActivity", "Cannot show face detection overlay", e)
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
                Log.e("FemaleAudioCallingActivity", "Error dismissing overlay", e)
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

            agoraEngine?.setupLocalVideo(
                VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
            )

            binding.localVideoViewContainer.visibility = View.VISIBLE
            binding.localCardView.visibility = View.VISIBLE
            applySavedLocalPreviewPosition()
        } catch (e: Exception) {
            Log.e("FemaleAudioCallingActivity", "Error setting local video in call view", e)
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
