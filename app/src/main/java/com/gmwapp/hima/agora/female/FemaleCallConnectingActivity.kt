package com.gmwapp.hima.agora.female

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityFemaleCallConnectingBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallEndReason
import com.gmwapp.hima.retrofit.responses.CallEndedBy
import com.gmwapp.hima.retrofit.responses.RegisterResponse
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.viewmodels.CallStatusViewModel
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject


@AndroidEntryPoint
class FemaleCallConnectingActivity : AppCompatActivity() {
    private lateinit var binding : ActivityFemaleCallConnectingBinding
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()
    private val callStatusViewModel: CallStatusViewModel by viewModels()
    var callType: String? = null
    var receiverId: Int = -1
    var receiverImg : String? = null
    var receiverName : String? = null
    var userId: Int? = null
    private var callId = 0
    private var channelName: String = ""  // Store channel name to ensure consistency
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    // 2026-05-22 v20 — wire callRejectCount from female side so unanswered/cancelled
    // calls also count toward the 3-strike block. Previously only fired when male
    // explicitly tapped RED reject button, which never happens when male is
    // offline / app killed / notification dismissed — the most common case.
    private val accountViewModel: AccountViewModel by viewModels()
    @Inject
    lateinit var apiManager: ApiManager
    private var prefetchedAgoraToken: String? = null
    private var prefetchedAgoraAppId: String? = null
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var progressStatus = 0
    private var isRunning = true  // Keeps the loop running
    private val designOnly = false  // Toggle: true = UI only (no API/FCM), false = full flow

    // The receiver's FCM token is briefly stale ('0'/absent) right after call_male_user's
    // OneSignal push wakes their app — it re-registers a couple of seconds later
    // (BaseApplication cold-start sync). A "no FCM token"/404 from sendNotification is
    // therefore transient on the first attempt, so retry the ring a few times before
    // surfacing failure instead of forcing the user to redial 2-3 times.
    private var ringNotifRetryCount = 0
    private var lastRingNotifMessage: String? = null
    private val maxRingNotifRetries = 3
    private val ringNotifRetryDelayMs = 2000L

    private var elapsedTime = 0  // Tracks elapsed seconds
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = object : Runnable {
        override fun run() {
            elapsedTime++
            Log.d("CallTimeoutTracker", "Seconds passed: $elapsedTime")

            if (elapsedTime >= 40) { // 40 seconds timeout — Oplus/Realme ROMs defer the
                                     // Telecom ringer UI up to ~15s after the FCM lands;
                                     // a 20s caller-side cutoff was firing before the
                                     // receiver's phone visibly rang and the user could tap.
                disconnectCall()
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
        stopAlivePolling() // every terminal path (accept/reject/cancel) cancels the timeout
    }

    // ── Caller-side liveness poll (symmetric with MaleCallConnectingActivity) ──
    // The callee's decline reaches us only via a best-effort FCM relay; when it's
    // dropped/delayed we'd dangle on "Connecting…" for the full 40s timeout even
    // though the backend already stamped the call ended. Poll the authoritative
    // state (one PK lookup per tick) and bail out the instant it reports dead.
    private val aliveHandler = Handler(Looper.getMainLooper())
    private val aliveIntervalMs = 2500L
    private var peerEndedHandled = false
    private val alivePollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || isFinishing || isDestroyed) return
            if (!designOnly && callId > 0) {
                com.gmwapp.hima.utils.CallAliveChecker.checkConnectingDead(callId) {
                    if (isRunning && !isFinishing && !isDestroyed) {
                        Log.d("CallStatus", "FemaleConnecting.alivePoll -> backend says call ended, disconnecting caller")
                        exitBecausePeerEnded()
                    }
                }
            }
            aliveHandler.postDelayed(this, aliveIntervalMs)
        }
    }

    private fun startAlivePolling() {
        aliveHandler.removeCallbacks(alivePollRunnable)
        aliveHandler.postDelayed(alivePollRunnable, aliveIntervalMs)
    }

    private fun stopAlivePolling() {
        aliveHandler.removeCallbacks(alivePollRunnable)
    }

    /**
     * Peer ended the call server-side and we learned it from the liveness poll
     * rather than the FCM relay. Mirror the "rejected" observer branch: tear
     * down Telecom, clear status, navigate to Main — without re-posting
     * callStatus (the peer already posted the terminal state).
     */
    private fun exitBecausePeerEnded() {
        if (peerEndedHandled) return
        peerEndedHandled = true
        isRunning = false
        cancelTimeoutTracking()
        stopAlivePolling()
        com.gmwapp.hima.agora.telecom.HimaTelecomManager.endActiveCall(
            android.telecom.DisconnectCause.REJECTED
        )
        FcmUtils.clearCallStatus()
        FcmUtils.shouldRefreshCallList = 1
        val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // B067: refuse to start a Hima call while a SIM call is active.
        // Android's telephony stack holds an exclusive lock on STREAM_VOICE_CALL
        // in MODE_IN_CALL — Agora video frames still render but voice frames
        // have nowhere to go, so the user "can see but can't hear." Block up
        // front and surface the same message WhatsApp/Telegram show.
        if (com.gmwapp.hima.utils.CallPhoneStateHelper.isCellularCallBusy(this)) {
            android.widget.Toast.makeText(
                this,
                "You're on a phone call. End it to make a Hima call.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        // B125 — refuse to start a second Hima call while one is already in
        // progress (in-call activity alive). Without this, the previous
        // video call screen would linger behind the new connecting screen
        // and the new audio call's mic path would be hijacked by the
        // existing Agora session.
        if (BaseApplication.getInstance()?.isInRealCall() == true) {
            android.widget.Toast.makeText(
                this,
                "You're already in a call. End it first.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        enableEdgeToEdge()
        binding = ActivityFemaleCallConnectingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // B014 — match the connecting screen's white background instead of
        // hardcoding black. Dark status-bar icons stay legible against white.
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // In design-only mode, avoid touching global call state
        if (!designOnly) {
            FcmUtils.isUserAvailable = 1
            Log.d("FcmUtils.isUserAvailable","${FcmUtils.isUserAvailable}")
        }

        // Read intent extras early for both modes
        callType = intent.getStringExtra(DConstants.CALL_TYPE)
        receiverId = intent.getIntExtra(DConstants.RECEIVER_ID, -1)
        receiverImg = intent.getStringExtra(DConstants.IMAGE)
        receiverName = intent.getStringExtra(DConstants.RECEIVER_NAME)

        if (designOnly) {
            // UI-only preview: no API/FCM, just show connecting animation briefly
            initUI()
            // Simulate preview for 2 seconds, then return to main
            handler.postDelayed({
                Toast.makeText(this, "Design preview only", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }, 2000)
        } else {
            lifecycleScope.launch {
                FcmUtils.clearCallStatus()

                Log.d("callStatusValueLog", "${FcmUtils.callStatus.value}")
                val callStatusValue = FcmUtils.callStatus.value
                if (callStatusValue?.first == "accepted") {
                    Log.d("NavigationDebug", "Redirecting to MainActivity due to call accepted.")
                    val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }

                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                userData?.id?.let { userId = userData?.id }

                getCallId()
                initUI()
                observeCallAcceptance()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("FemaleCallConnectingActivity", "onBackPressed called via Dispatcher")

                // Tear down the self-managed Telecom outgoing connection placed in
                // registerOutgoingWithTelecom(). Without this, a user-cancel (back-press)
                // while still DIALING strands the connection — Telecom then blocks EVERY
                // subsequent outgoing call with the system error "Cannot place a call as
                // there is already another call connecting" until the app is killed. The
                // timeout (disconnectCall) and reject paths already do this; the manual
                // cancel path was the missing one. Cover all branches by calling here.
                com.gmwapp.hima.agora.telecom.HimaTelecomManager.endActiveCall(
                    android.telecom.DisconnectCause.LOCAL
                )

                if (!designOnly && userId != null && receiverId != -1 && callType != null) {
                    sendCallNotification(userId!!, receiverId, callType!!, "callDeclined")
                    Log.d("CallStatus", "FemaleConnecting.cancel → not_answered/caller self=$userId peer=$receiverId callId=$callId")
                    // 2026-05-22 v20 — count this as a reject toward the 3-strike block.
                    // male_user_id = receiverId (the male who didn't answer), female_user_id = userId
                    accountViewModel.callRejectCount(receiverId, userId!!)
                    callStatusViewModel.saveCallStatus(
                        userId = userId!!,
                        receivedUserId = receiverId,
                        callId = callId,
                        endReason = CallEndReason.NOT_ANSWERED,
                        endedBy = CallEndedBy.CALLER,
                        endedByUserId = userId,
                        durationSeconds = 0,
                    )
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    Log.d("NavigationDebug", "Redirecting to MainActivity due to back pressed when user id is not null")

                    val intent =
                        Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                } else {


                    if (!designOnly) {
                        FcmUtils.clearCallStatus()  // Clear before moving to MainActivity
                    }

                    Log.d("NavigationDebug", "Redirecting to MainActivity due to back pressed when user id is null")

                    val intent =
                        Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()

                    Log.e(
                        "FemaleCallConnectingActivity",
                        "Missing required data: userId=$userId, receiverId=$receiverId, callType=$callType"
                    )
                }

            }
        })

    }

    fun initUI(){
        progressBar = findViewById(R.id.progressBar)
        if (receiverName != null) {
            val displayName = receiverName!!.trimEnd { it.isDigit() }
            binding.tlWaitTitle.setText("Connecting with $displayName")
        }
        startProgressLoop()
        if (callType=="audio"){
            binding.tvTitle.setText("Audio Session")

        }else{
            binding.tvTitle.setText("Video Session")

        }


        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()


        Glide.with(this)
            .load(userData?.image)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivCallerProfile)

        // Placeholder + error fallback so the receiver avatar circle isn't blank
        // when the missed-call OneSignal payload didn't carry an image URL and
        // we have nothing cached for this peer either. Matches the silhouette
        // used by the chat-list / creator-notification adapters.
        Glide.with(this)
            .load(receiverImg)
            .apply(RequestOptions.circleCropTransform())
            .placeholder(R.drawable.small_profile)
            .error(R.drawable.small_profile)
            .into(binding.ivLogo)

        // Missed-call OneSignal payloads from the server typically don't carry an
        // avatar URL and ChatNotificationStore may not have one cached either
        // (e.g. user hasn't received a chat push from this peer in this session).
        // Fall back to the userdetails endpoint so we can replace the silhouette
        // placeholder with the real photo as soon as the network responds.
        if (receiverImg.isNullOrBlank() && receiverId > 0) {
            fetchReceiverImage(receiverId)
        }

        Glide.with(this)
            .load(R.drawable.double_arrow_svg)
            .into(binding.ivDoubleArrow)
            
        startSimpleAnimations()
        
        // Cancel button click
        binding.tvCancel.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * Pulls the receiver's profile (image) from the userdetails endpoint when the
     * intent didn't carry one — typical for missed-call notification taps where
     * the OneSignal payload had no avatar field. On success the local
     * [receiverImg] is updated and Glide is reloaded so the silhouette
     * placeholder is replaced by the real photo. Failures are silent — the
     * placeholder simply stays.
     */
    private fun fetchReceiverImage(peerId: Int) {
        apiManager.getUser(peerId, object : NetworkCallback<RegisterResponse> {
            override fun onResponse(
                call: Call<RegisterResponse>,
                response: Response<RegisterResponse>
            ) {
                if (isFinishing || isDestroyed) return
                if (!response.isSuccessful) return
                val fetched = response.body()?.data?.image.orEmpty()
                if (fetched.isBlank()) return
                receiverImg = fetched
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Glide.with(this@FemaleCallConnectingActivity)
                        .load(fetched)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.small_profile)
                        .error(R.drawable.small_profile)
                        .into(binding.ivLogo)
                }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                Log.w("FemaleCallConnecting", "fetchReceiverImage failed: ${t.message}")
            }

            override fun onNoNetwork() {
                Log.w("FemaleCallConnecting", "fetchReceiverImage skipped: no network")
            }
        })
    }


    private fun startSimpleAnimations() {
        // Animate the connecting dots
        val fadeIn = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 600
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        binding.dot1.startAnimation(fadeIn)
        
        val fadeIn2 = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 600
            startOffset = 200
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.dot2.startAnimation(fadeIn2)
        
        val fadeIn3 = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 600
            startOffset = 400
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.dot3.startAnimation(fadeIn3)

        // Animate the wave ring with subtle pulse
        binding.waveRing1.apply {
            scaleX = 0.9f
            scaleY = 0.9f
            animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .alpha(0.2f)
                .setDuration(1500)
                .withEndAction {
                    if (isRunning) {
                        scaleX = 0.9f
                        scaleY = 0.9f
                        alpha = 0.4f
                        startSimpleAnimations()
                    }
                }
                .start()
        }
        
        // Animate double arrow with gentle bounce
        binding.ivDoubleArrow.apply {
            translationY = 0f
            animate()
                .translationY(-15f)
                .setDuration(800)
                .withEndAction {
                    if (isRunning) {
                        animate()
                            .translationY(0f)
                            .setDuration(800)
                            .withEndAction {
                                if (isRunning) {
                                    postDelayed({ 
                                        if (isRunning) {
                                            startSimpleAnimations()
                                        }
                                    }, 100)
                                }
                            }
                            .start()
                    }
                }
                .start()
        }
    }

    private fun startProgressLoop() {
        startTimeoutTracking()
        // Fallback for a dropped FCM relay — see startAlivePolling(): poll the
        // authoritative call state so a callee decline disconnects the caller in
        // ~seconds instead of waiting out the 40s timeout. Guards designOnly/callId.
        startAlivePolling()
        handler.post(object : Runnable {
            override fun run() {
                if (progressStatus < 100 && isRunning) {
                    progressStatus += 2
                    progressBar.progress = progressStatus
                    handler.postDelayed(this, 200)
                } else if (progressStatus >= 100 && isRunning) {
                    progressStatus = 0 // Reset to 0 after reaching 100
                    handler.postDelayed(this, 200)
                }
            }
        })
    }

    fun getCallId() {
        if (designOnly) return // UI-only: skip API
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val myId = userData?.id
        // Guard: missed-call-tap with a missing/synthetic sender used to fire
        // callMaleUser(myId, -1, ...) and silently spin. Bail out visibly.
        if (myId == null || receiverId <= 0 || callType.isNullOrBlank()) {
            Log.e(
                "FemaleCallConnectingActivity",
                "getCallId aborted: myId=$myId receiverId=$receiverId callType=$callType"
            )
            Toast.makeText(
                this,
                "Couldn't start the call. Please try again from Recent.",
                Toast.LENGTH_LONG
            ).show()
            abortToMain()
            return
        }
        Log.d("callMaleUserApi", "$myId $receiverId")
        femaleUsersViewModel.callMaleUser(myId, receiverId, callType!!, 0)
        callIdObserver()
    }

    private fun callIdObserver() {
        if (designOnly) return // UI-only: skip observers
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val myAvatar = userData?.image
        val myname = userData?.name

        femaleUsersViewModel.callMaleUserResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                callId = it.data?.call_id ?: 0
                channelName = "channel_$callId"  // Set channel name based on call ID
                Log.d("CallID", "CallID: $callId, ChannelName: $channelName")

                // B201 (female side) used to gate on the male receiver's
                // audio_status / video_status, mirroring the male->female
                // direction. But those fields are the FEMALE opt-in toggle
                // (the s_audio / s_video switches in fragment_female_home);
                // males have no UI to flip them, and register() never seeds
                // them, so the column stays 0 for every male in production.
                // The gate therefore blocked F→M to every male, every time,
                // surfacing as "User is unavailable right now" right after
                // the chat-list call button. /api/auth/call_male_user
                // already rejects the call upstream when the male is
                // deleted, blocked by the caller, or currently on another
                // call (UserCalls with no ended_time today) — so if we got
                // success=true back, the male IS reachable per backend
                // rules. Don't second-guess the server on a column that
                // doesn't mean what this check thinks it means.
                //
                // NOTE for Perumal: your 7a0758a8 commit re-added this gate
                // to surface missed-call callback failures. The intent is
                // good but the mechanism (audio_status on male) is broken
                // because males have no UI to set that column — it stays 0
                // and re-blocks all F→M calls. Detect failure via timeout or
                // a new backend field instead.

                if (callId != 0) {
                    prefetchAgoraToken(channelName)
                    val ringMessage = "incoming call $callId $myAvatar $myname"
                    lastRingNotifMessage = ringMessage
                    ringNotifRetryCount = 0
                    sendCallNotification(userId!!, receiverId, callType!!, ringMessage)
                    observeNotificationResponse()
                    // I039 — register outgoing call with Telecom so a SIM / WhatsApp
                    // call mid-Hima triggers the system second-call UI.
                    registerOutgoingWithTelecom()
                } else {
                    Toast.makeText(
                        this@FemaleCallConnectingActivity,
                        "Couldn't start the call. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    abortToMain()
                }
            } else {
                val message = it?.message?.takeIf { msg -> msg.isNotBlank() }
                    ?: "Couldn't reach this user right now. Please try again."
                Toast.makeText(
                    this@FemaleCallConnectingActivity,
                    message,
                    Toast.LENGTH_LONG
                ).show()
                abortToMain()
            }
        })

        // Without this, network failures / HTTP errors leave the connecting
        // screen spinning forever — exactly the missed-call-tap bug.
        femaleUsersViewModel.callMaleUserErrorLiveData.observe(this, Observer { err ->
            if (err.isNullOrBlank()) return@Observer
            Toast.makeText(
                this@FemaleCallConnectingActivity,
                err,
                Toast.LENGTH_LONG
            ).show()
            abortToMain()
        })
    }

    /**
     * Stop the connecting UI and return to MainActivity. Used by every
     * failure path in [callIdObserver] so the user never gets stranded on a
     * spinner when the male-call API rejects the request.
     */
    private fun abortToMain() {
        isRunning = false
        cancelTimeoutTracking()
        com.gmwapp.hima.agora.telecom.HimaTelecomManager.endActiveCall(
            android.telecom.DisconnectCause.LOCAL
        )
        FcmUtils.clearCallStatus()
        FcmUtils.isUserAvailable = 0
        val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private var unreachableHandled = false

    fun observeNotificationResponse() {
        fcmNotificationViewModel.notificationResponseLiveData.observe(this) { response ->
            response?.let {
                if (it.success) {
                    Log.d("FCMNotification", "Notification sent successfully to male user!")
                    lastRingNotifMessage = null  // ring delivered — stop any pending retry
                } else {
                    Log.e("FCMNotification", "Failed to send notification: ${it.message}")
                    // Fix 2 — receiver unreachable (no deliverable FCM token). End the
                    // caller's "Connecting" now instead of waiting out the 40s timeout.
                    // disconnectCall() has no currentActivity guard on this side, so the
                    // single-shot unreachableHandled flag keeps it from re-firing on the
                    // callDeclined echo. Narrow message match means transient/malformed
                    // responses (reported as success=true) never trip it.
                    if (!unreachableHandled && it.message.contains("FCM token", ignoreCase = true)) {
                        unreachableHandled = true
                        Toast.makeText(this, "User is not available right now", Toast.LENGTH_LONG).show()
                        disconnectCall()
                    }
                }
            }
        }
        
        fcmNotificationViewModel.notificationErrorLiveData.observe(this) { error ->
            error?.let {
                Log.e("FCMNotification", "Notification error: $it")
                if (shouldRetryRingNotification(it)) {
                    ringNotifRetryCount++
                    Log.d(
                        "FCMNotification",
                        "Receiver token not ready — retry $ringNotifRetryCount/$maxRingNotifRetries in ${ringNotifRetryDelayMs}ms"
                    )
                    handler.postDelayed({
                        val uid = userId
                        val ct = callType
                        if (isRunning && !isFinishing && !isDestroyed &&
                            lastRingNotifMessage != null && uid != null && ct != null) {
                            sendCallNotification(uid, receiverId, ct, lastRingNotifMessage!!)
                        }
                    }, ringNotifRetryDelayMs)
                } else {
                    // Retries exhausted — the receiver is genuinely unreachable
                    // (logged out / no deliverable FCM token, backend 404
                    // "Receiver does not have an FCM token"). Show the SAME
                    // friendly message as the success=false path above instead of
                    // dumping the raw 404 JSON, and end the dangling "Connecting"
                    // screen. Mirrors the male-side connecting twin.
                    if (!unreachableHandled &&
                        (it.contains("FCM token", ignoreCase = true) || it.contains("404"))) {
                        unreachableHandled = true
                        Toast.makeText(this, "User is not available right now", Toast.LENGTH_LONG).show()
                        disconnectCall()
                    } else {
                        Toast.makeText(this, "Couldn't connect. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * The receiver's FCM token is briefly missing right after the OneSignal call push
     * cold-starts their app (it re-registers within ~2s). Treat a "no FCM token" / 404
     * from sendNotification as transient and retry the ring a few times — but only while
     * we're still actively connecting (isRunning && !isFinishing), only for the ring
     * (lastRingNotifMessage != null, never the callDeclined teardown send), and only up
     * to the cap so a genuinely tokenless receiver still fails fast enough.
     */
    private fun shouldRetryRingNotification(error: String): Boolean {
        val tokenNotReady = error.contains("FCM token", ignoreCase = true) ||
            error.contains("Error: 404", ignoreCase = true)
        return isRunning && !isFinishing &&
            tokenNotReady &&
            lastRingNotifMessage != null &&
            ringNotifRetryCount < maxRingNotifRetries
    }

    /**
     * I039 — symmetric to HimaTelecomManager.tryAddIncomingCall on the receive side.
     */
    private fun registerOutgoingWithTelecom() {
        if (designOnly) return
        val ct = callType ?: return
        val extras = android.os.Bundle().apply {
            putString(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_CALL_TYPE, ct)
            putInt(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_SENDER_ID, receiverId)
            putString(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_CHANNEL_NAME, channelName)
            putInt(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_CALL_ID, callId)
            putString(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_CALLER_NAME, receiverName ?: "Hima call")
            putString(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_CALLER_IMAGE, "")
            putString(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_RECEIVER_GENDER, "female")
        }
        com.gmwapp.hima.agora.telecom.HimaTelecomManager.tryPlaceOutgoingCall(this, extras)
    }

    fun sendCallNotification(senderId: Int, receiverId: Int, callType: String, message: String) {
        if (designOnly) return // UI-only: skip FCM
        Log.d("FemaleCallConnect", "Sending notification with channelName: $channelName")
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = channelName,  // Use stored channel name instead of hardcoded
            message = message
        )
    }

    private fun prefetchAgoraToken(channelForToken: String) {
        Log.d("AgoraTiming", "FemaleConnecting prefetchAgoraToken started at ${System.currentTimeMillis()}")
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            if (response != null && response.success == true && !response.token.isNullOrEmpty()) {
                prefetchedAgoraToken = response.token
                prefetchedAgoraAppId = response.app_id
                Log.d("AgoraTiming", "FemaleConnecting prefetchAgoraToken received at ${System.currentTimeMillis()}")
            }
        }
        agoraViewModel.getAgoraToken(channelForToken, 0, "publisher", 3600)
    }

    fun observeCallAcceptance() {
        if (designOnly) return // UI-only: skip observers
        FcmUtils.callStatus.observe(this, androidx.lifecycle.Observer { callStatus ->
            if (callStatus != null) {
                val (status, receiverIdStr) = callStatus
                val receiverIdFromStatus = receiverIdStr.toIntOrNull() ?: -1
                
                if (status == "accepted" && receiverIdFromStatus == this.receiverId) {
                    cancelTimeoutTracking()
                    isRunning = false
                    FcmUtils.clearCallStatus()
                    // I039 — transition outgoing Telecom connection DIALING → ACTIVE.
                    com.gmwapp.hima.agora.telecom.HimaTelecomManager.markActive()

                    Log.d("FemaleCallConnect", "Male accepted! Joining channel: $channelName")
                    
                    // Navigate to the appropriate calling activity
                    val intent = if (callType == "audio") {
                        Intent(this@FemaleCallConnectingActivity, FemaleAudioCallingActivity::class.java)
                    } else {
                        Intent(this@FemaleCallConnectingActivity, FemaleVideoCallingActivity::class.java)
                    }
                    
                    intent.putExtra("CHANNEL_NAME", channelName)  // Use stored channel name
                    intent.putExtra("RECEIVER_ID", this.receiverId)
                    intent.putExtra("CALL_ID", callId)
                    intent.putExtra("IS_CALLER", true)
                    prefetchedAgoraToken?.let { intent.putExtra("AGORA_TOKEN", it) }
                    prefetchedAgoraAppId?.let { intent.putExtra("AGORA_APP_ID", it) }
                    
                    startActivity(intent)
                    finish()
                } else if ((status == "declined" || status == "rejected") && receiverIdFromStatus == this.receiverId) {
                    cancelTimeoutTracking()
                    isRunning = false
                    FcmUtils.clearCallStatus()
                    // I039 — tear down outgoing Telecom connection so the system doesn't
                    // keep treating us as in-call after a rejection.
                    com.gmwapp.hima.agora.telecom.HimaTelecomManager.endActiveCall(
                        android.telecom.DisconnectCause.REJECTED
                    )
                    FcmUtils.shouldRefreshCallList = 1
                    Log.d("CallStatus", "FemaleConnecting.peerRejected (observed, no post — peer already posted) self=$userId peer=$receiverId callId=$callId status=$status")
                    Toast.makeText(
                        this@FemaleCallConnectingActivity,
                        "${receiverName?.trimEnd { it.isDigit() }} is busy",
                        Toast.LENGTH_SHORT
                    ).show()
                    val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                } else if (status == "cancelled" && receiverIdFromStatus == this.receiverId) {
                    cancelTimeoutTracking()
                    isRunning = false
                    FcmUtils.clearCallStatus()
                    com.gmwapp.hima.agora.telecom.HimaTelecomManager.endActiveCall(
                        android.telecom.DisconnectCause.CANCELED
                    )
                    val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }
            }
        })
    }

    private fun disconnectCall() {
        // Guard symmetric with the male side (MaleCallConnectingActivity.disconnectCall):
        // only tear down while this connecting screen is actually foreground. Without it,
        // a late notification-response (Fix 2) or timeout callback arriving after the call
        // was already accepted — and we transitioned to FemaleAudioCallingActivity — would
        // endActiveCall(LOCAL) + navigate to MainActivity and kill a LIVE call. Hardens the
        // pre-existing timeout/back-press callers too, not just the new Fix 2 caller.
        if (BaseApplication.getInstance()?.getCurrentActivity() !is FemaleCallConnectingActivity) return
        isRunning = false
        cancelTimeoutTracking()
        // I039 — local cancel (timeout or user back-pressed). Mirror the male side.
        com.gmwapp.hima.agora.telecom.HimaTelecomManager.endActiveCall(
            android.telecom.DisconnectCause.LOCAL
        )
     //   Toast.makeText(this, "$receiverName is not responding", Toast.LENGTH_SHORT).show()
        if (!designOnly && userId != null && callType != null) {
            sendCallNotification(userId!!, receiverId, callType!!, "callDeclined")
            Log.d("CallStatus", "FemaleConnecting.timeout → not_answered/receiver self=$userId peer=$receiverId callId=$callId")
            // 2026-05-22 v20 — count timeout as a reject toward the 3-strike block.
            // This is the main path: female calls, male doesn't pick up in 40s,
            // disconnectCall() fires. Without this, the male's reject_count never
            // increments unless he's actively pressing the RED reject button.
            if (receiverId != -1) {
                accountViewModel.callRejectCount(receiverId, userId!!)
            }
            callStatusViewModel.saveCallStatus(
                userId = userId!!,
                receivedUserId = receiverId,
                callId = callId,
                endReason = CallEndReason.NOT_ANSWERED,
                endedBy = CallEndedBy.RECEIVER,
                endedByUserId = receiverId,
                durationSeconds = 0,
            )
            FcmUtils.clearCallStatus()
        }
        val intent = Intent(this@FemaleCallConnectingActivity, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // B156a — clear the synchronous busy flag set at the click handler.
        // If onDestroy fires because we successfully transitioned to the
        // FemaleAudio/VideoCallingActivity, that activity already has
        // isInActiveCall()==true + currentActivity covering the busy gate.
        // If it fires because the user cancelled/backed out, leaving the
        // flag at 1 would auto-reject every legitimate incoming call until
        // the next real call ends. Mirrors MaleCallConnectingActivity:645.
        FcmUtils.isUserAvailable = 0
        isRunning = false
        cancelTimeoutTracking()
        handler.removeCallbacksAndMessages(null)
    }
}

