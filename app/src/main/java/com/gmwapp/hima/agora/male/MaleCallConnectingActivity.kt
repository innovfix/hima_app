package com.gmwapp.hima.agora.male

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.gmwapp.hima.utils.DndCallGate
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityMaleCallConnectingBinding
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallEndReason
import com.gmwapp.hima.retrofit.responses.CallEndedBy
import com.gmwapp.hima.retrofit.responses.RegisterResponse
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.viewmodels.CallStatusViewModel
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject


@AndroidEntryPoint
class MaleCallConnectingActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMaleCallConnectingBinding
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()
    private val callStatusViewModel: CallStatusViewModel by viewModels()
    var callType: String? = null
    var receiverId: Int = -1
    var receiverImg : String? = null
    var receiverName : String? = null
    var userId: Int? = null
    private var callId = 0
    private var fromChat: Boolean = false
    private var chatPeerUserId: Int = -1
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    @Inject
    lateinit var apiManager: ApiManager
    private var currentCallChannelName: String? = null
    private var prefetchedAgoraToken: String? = null
    private var prefetchedAgoraAppId: String? = null
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var progressStatus = 0
    private var isRunning = true  // Keeps the loop running

    // The receiver's FCM token is briefly stale ('0'/absent) right after the OneSignal
    // call push wakes their app — it re-registers a couple of seconds later
    // (BaseApplication cold-start sync). A "no FCM token"/404 from sendNotification is
    // therefore transient on the first attempt, so retry the ring a few times before
    // giving up, instead of silently spinning to the 40s timeout.
    private var ringNotifRetryCount = 0
    private var lastRingNotifMessage: String? = null
    private var notifObserversRegistered = false
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
                Log.d("CreatorCallDiag", "MConn.timeoutFired elapsed=$elapsedTime -> disconnect (no answer)")
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
        stopAlivePolling() // every terminal path (accept/reject/busy/cancel) cancels the timeout
    }

    // ── Caller-side liveness poll ───────────────────────────────────────────
    // The callee's decline reaches us only via a best-effort FCM "rejected"
    // relay. When that push is dropped/delayed (Doze, battery optimization,
    // stale token, flaky link) we'd otherwise sit on "Connecting…" for the full
    // 40s timeout even though the backend already stamped the call ended. Poll
    // the authoritative server state every few seconds while ringing; bail out
    // the instant it reports dead. One primary-key lookup per tick (cheap).
    private val aliveHandler = Handler(Looper.getMainLooper())
    private val aliveIntervalMs = 2500L
    private var peerEndedHandled = false
    private val alivePollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || isFinishing || isDestroyed) return
            if (callId > 0) {
                com.gmwapp.hima.utils.CallAliveChecker.checkConnectingDead(callId) {
                    if (isRunning && !isFinishing && !isDestroyed) {
                        Log.d("CreatorCallDiag", "MConn.alivePoll -> backend says call ended, disconnecting caller")
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
     * The peer already ended the call server-side (decline / not-answered) and
     * we learned it from the liveness poll rather than the FCM relay. Mirror the
     * "rejected" observer branch exactly: tear down Telecom, clear status, and
     * navigate out — but do NOT re-post callStatus, because the peer already
     * posted the terminal state (re-posting would double-stamp the call row).
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
        if (fromChat && chatPeerUserId != -1) {
            val intent = Intent(this@MaleCallConnectingActivity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                putExtra("USER_ID", chatPeerUserId)
                putExtra("USER_NAME", receiverName ?: "")
                putExtra("USER_IMAGE", receiverImg ?: "")
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
        } else {
            val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
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
        // B125 — refuse to start a second Hima call while one is already
        // running. The first call's in-call activity sets isCallActive=true
        // in its onCreate (and clears it in onDestroy); if it's still alive
        // here, the user attempted to initiate a 2nd call via Random Call
        // FAB / Recent / etc. Without this gate, two Agora channels would
        // join concurrently, the previous video activity would stay on
        // screen, and the 2nd call's audio path would lose its mic.
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
        binding =ActivityMaleCallConnectingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // B014 — the connecting layout has a white background (bg_white).
        // Hardcoding the status bar to black created the jarring "completely
        // black bar over a white screen" look. Match it to the page bg and
        // ask the OS to render the status-bar icons in dark colors so they
        // stay legible against white.
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

        // Read intent extras immediately (outside coroutine) so they're
        // available for onBackPressed AND for the DND-cancel navigation below.
        callType = intent.getStringExtra(DConstants.CALL_TYPE)
        receiverId = intent.getIntExtra(DConstants.RECEIVER_ID, -1)
        receiverImg = intent.getStringExtra(DConstants.IMAGE)
        receiverName = intent.getStringExtra(DConstants.RECEIVER_NAME)
        fromChat = intent.getBooleanExtra("FROM_CHAT", false)
        chatPeerUserId = intent.getIntExtra("CHAT_PEER_USER_ID", -1)

        Log.d("MaleCallConnecting", "fromChat=$fromChat, chatPeerUserId=$chatPeerUserId, receiverId=$receiverId")

        // Pre-flight DND gate. If the user has DND on, show the blocking
        // dialog before any call API fires or any FCM/Telecom side effects
        // run. The dialog disables DND server-side and then calls onProceed;
        // Cancel routes back to the entry point with zero side effects.
        DndCallGate.gate(
            activity = this,
            apiManager = apiManager,
            onProceed = { startCallSetup() },
            onCancel = { abortDueToDnd() }
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("MaleCallConnecting", "onBackPressed called - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")

                // Tear down the self-managed Telecom outgoing connection placed in
                // registerOutgoingWithTelecom(). Without this, a user-cancel (back-press)
                // while still DIALING strands the connection — Telecom then blocks EVERY
                // subsequent outgoing call with the system error "Cannot place a call as
                // there is already another call connecting" until the app is killed. The
                // timeout (disconnectCall) and reject paths already do this; the manual
                // cancel path was the missing one. Cover both branches below by calling here.
                com.gmwapp.hima.agora.telecom.HimaTelecomManager.endActiveCall(
                    android.telecom.DisconnectCause.LOCAL
                )

                if (userId != null && receiverId != -1 && callType != null) {
                    sendCallNotification(userId!!, receiverId, callType!!, "callDeclined")
                    Log.d("CallStatus", "MaleConnecting.cancel → not_answered/caller self=$userId peer=$receiverId callId=$callId")
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

                    if (fromChat && chatPeerUserId != -1) {
                        // Return to ChatActivityInHouse if call was initiated from chat
                        Log.d("NavigationDebug", "Returning to ChatActivityInHouse - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                        val intent = Intent(this@MaleCallConnectingActivity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                            putExtra("USER_ID", chatPeerUserId)
                            putExtra("USER_NAME", receiverName ?: "")
                            putExtra("USER_IMAGE", receiverImg ?: "")
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d("NavigationDebug", "Going to MainActivity - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                        val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

                    if (fromChat && chatPeerUserId != -1) {
                        // Return to ChatActivityInHouse if call was initiated from chat
                        Log.d("NavigationDebug", "Returning to ChatActivityInHouse (no userId) - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                        val intent = Intent(this@MaleCallConnectingActivity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                            putExtra("USER_ID", chatPeerUserId)
                            putExtra("USER_NAME", receiverName ?: "")
                            putExtra("USER_IMAGE", receiverImg ?: "")
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d("NavigationDebug", "Going to MainActivity (no userId) - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                        val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                        finish()
                    }

                    Log.e(
                        "MaleCallConnectingActivity",
                        "Missing required data: userId=$userId, receiverId=$receiverId, callType=$callType"
                    )
                }

            }
        })

    }

    /**
     * Runs the original call-setup pipeline (marks user busy, requests mic
     * permission, places the call via the appropriate ViewModel API, observes
     * acceptance / busy / FCM). Extracted from [onCreate] so the [DndCallGate]
     * can defer it until the user has either confirmed they want to turn off
     * DND, or DND was off to begin with.
     */
    private fun startCallSetup() {
        FcmUtils.isUserAvailable = 1
        Log.d("FcmUtils.isUserAvailable", "${FcmUtils.isUserAvailable}")

        // RECORD_AUDIO is required for the call. READ_PHONE_STATE is OPTIONAL and
        // only powers the "on hold" banner when a SIM call interrupts the Hima
        // call (CallPhoneStateHelper) — request it together so the user isn't
        // prompted again mid-call; denying it never blocks the call.
        val callPerms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callPerms.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            callPerms.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (callPerms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, callPerms.toTypedArray(), 100)
        }

        lifecycleScope.launch {
            FcmUtils.clearCallStatus()
            FcmUtils.clearUserBusyStatus()

            Log.d("callStatusValueLog", "${FcmUtils.callStatus.value}")
            val callStatusValue = FcmUtils.callStatus.value
            if (callStatusValue?.first == "accepted") {
                Log.d("NavigationDebug", "Redirecting to MainActivity due to call accepted.")
                val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }

            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            userData?.id?.let { userId = userData?.id }

            getCallId()
            initUI()
            observeCallAcceptance()
            observeUserBusyStatus()
        }
    }

    /**
     * User tapped Cancel on the DND-blocked dialog. We haven't made any call
     * API yet (no UserCalls row, no FCM, no Telecom registration), so just
     * route them back to the screen they came from with zero teardown.
     */
    private fun abortDueToDnd() {
        Log.d("MaleCallConnecting", "abortDueToDnd fromChat=$fromChat chatPeerUserId=$chatPeerUserId")
        if (fromChat && chatPeerUserId != -1) {
            val intent = Intent(this, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                putExtra("USER_ID", chatPeerUserId)
                putExtra("USER_NAME", receiverName ?: "")
                putExtra("USER_IMAGE", receiverImg ?: "")
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        finish()
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
    
    private fun startSimpleAnimations() {
        // Simple fade in for title
        binding.tvTitle.alpha = 0f
        binding.tvTitle.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        
        // Subtle connecting dots animation
        startConnectingDotsAnimation()
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
                    Glide.with(this@MaleCallConnectingActivity)
                        .load(fetched)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.small_profile)
                        .error(R.drawable.small_profile)
                        .into(binding.ivLogo)
                }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                Log.w("MaleCallConnecting", "fetchReceiverImage failed: ${t.message}")
            }

            override fun onNoNetwork() {
                Log.w("MaleCallConnecting", "fetchReceiverImage skipped: no network")
            }
        })
    }

    private fun startConnectingDotsAnimation() {
        try {
            val dot1 = findViewById<android.view.View>(R.id.dot1)
            val dot2 = findViewById<android.view.View>(R.id.dot2)
            val dot3 = findViewById<android.view.View>(R.id.dot3)
            
            val animateDots = object : Runnable {
                var step = 0
                override fun run() {
                    when (step % 3) {
                        0 -> {
                            dot1?.alpha = 1.0f
                            dot2?.alpha = 0.5f
                            dot3?.alpha = 0.3f
                        }
                        1 -> {
                            dot1?.alpha = 0.3f
                            dot2?.alpha = 1.0f
                            dot3?.alpha = 0.5f
                        }
                        2 -> {
                            dot1?.alpha = 0.5f
                            dot2?.alpha = 0.3f
                            dot3?.alpha = 1.0f
                        }
                    }
                    step++
                    if (isRunning) {
                        handler.postDelayed(this, 500)
                    }
                }
            }
            handler.post(animateDots)
        } catch (e: Exception) {
            Log.e("Animation", "Connecting dots not found: ${e.message}")
        }
    }


    private fun startProgressLoop() {
        Thread {
            while (isRunning) {
                progressStatus = 0  // Reset progress

                while (progressStatus < 100 && isRunning) {
                    progressStatus += 1  // Increase progress
                    handler.post { progressBar.progress = progressStatus }

                    Log.d("progressStatus","$progressStatus")
                    try {
                        Thread.sleep(200)  // Smooth animation delay
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }

                // Reset to 0 and repeat the loop
                if (isRunning) {
                    handler.post { progressBar.progress = 0 }
                }
            }
        }.start()

    }

        fun getCallId(){
        receiverId?.let { it1 ->
            userId?.let {
                femaleUsersViewModel.callFemaleUser(
                    it, it1, callType.toString(),0
                )
            }
            callIdObserver()
        }
    }

    private fun callIdObserver(){
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        var myname = userData?.name
        var myAvatar = userData?.image
        femaleUsersViewModel.callFemaleUserResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                callId = it.data?.call_id ?: 0

                Log.d("callid","$callId")
                val audioStatus = it.data?.audio_status
                val videoStatus = it.data?.video_status

                Log.d("callid", "$callId")

                // B201 — fail-CLOSED gate. Previously this used `!= 0`, but
                // `audioStatus` / `videoStatus` are nullable: `null != 0` is
                // true, so any code path that triggered callFemaleUser with a
                // response payload missing the status field (e.g. the avatar-
                // tap bypass Ranjini reported) silently skipped the DND check
                // and the call went through anyway. Requiring `== 1` treats
                // both `0` (DND on / unavailable) and `null` (status unknown)
                // as "do not place the call".
                val shouldSendNotification = when (callType) {
                    "audio" -> audioStatus == 1
                    "video" -> videoStatus == 1
                    else -> false
                }

                if (!shouldSendNotification) {
                    Log.d(
                        "Notification",
                        "Blocking call: callType=$callType audioStatus=$audioStatus videoStatus=$videoStatus"
                    )
                    Toast.makeText(
                        this@MaleCallConnectingActivity,
                        "Creator is unavailable right now",
                        Toast.LENGTH_LONG
                    ).show()
                    navigateToMain()
                    return@Observer
                }

                if (userId != null && receiverId != -1 && callType != null) {
                    val ringMessage = "incoming call $callId $myAvatar $myname"
                    lastRingNotifMessage = ringMessage
                    ringNotifRetryCount = 0
                    sendCallNotification(userId!!, receiverId, callType!!, ringMessage)
                    startTimeoutTracking()
                    // Fallback for a dropped "rejected" FCM relay: poll the
                    // authoritative call state so a callee decline disconnects
                    // the caller in ~seconds instead of waiting out the 40s timeout.
                    startAlivePolling()
                    // I039 — register the outgoing call with Telecom so a SIM / WhatsApp
                    // call arriving mid-Hima-call triggers the system second-call UI
                    // (Hold & Answer / End & Answer) instead of ringing on top of the
                    // Agora audio with no coordination.
                    registerOutgoingWithTelecom()

                } else {
                    Log.e("MaleCallConnectingActivity", "Missing required data: userId=$userId, receiverId=$receiverId, callType=$callType")
                }


            } else {

                val safeCoinStatus = it?.coin_status ?: 1
                it?.message?.let { message ->
                    if (message.startsWith("Insufficient coins")) {
                        if (safeCoinStatus == 0) {
                            val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java).apply {
                                putExtra("show_paywall_insufficient", true)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            val intent = Intent(this@MaleCallConnectingActivity, com.gmwapp.hima.activities.WalletActivity::class.java)
                            Toast.makeText(this@MaleCallConnectingActivity, message, Toast.LENGTH_LONG).show()
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        Toast.makeText(this@MaleCallConnectingActivity, message, Toast.LENGTH_LONG).show()
                        // Return to ChatActivityInHouse if call was initiated from chat
                        if (fromChat && chatPeerUserId != -1) {
                            val intent = Intent(this@MaleCallConnectingActivity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                                putExtra("USER_ID", chatPeerUserId)
                                putExtra("USER_NAME", receiverName ?: "")
                                putExtra("USER_IMAGE", receiverImg ?: "")
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent)
                        }
                        finish()
                    }
                }

            }
        })
    }

    private fun disconnectCall() {
        var currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
        if (currentActivity is MaleCallConnectingActivity){
            // I039 — local cancel (timeout or user back-pressed). Tear down the Telecom
            // outgoing connection alongside the FCM decline so we don't leak a self-
            // managed call that the system would otherwise treat as still in progress.
            com.gmwapp.hima.agora.telecom.HimaTelecomManager.endActiveCall(
                android.telecom.DisconnectCause.LOCAL
            )
            if (userId != null && receiverId != -1 && callType != null) {
                sendCallNotification(userId!!, receiverId, callType!!, "callDeclined")
                Log.d("CallStatus", "MaleConnecting.timeout → not_answered/receiver self=$userId peer=$receiverId callId=$callId")
                callStatusViewModel.saveCallStatus(
                    userId = userId!!,
                    receivedUserId = receiverId,
                    callId = callId,
                    endReason = CallEndReason.NOT_ANSWERED,
                    endedBy = CallEndedBy.RECEIVER,
                    endedByUserId = receiverId,
                    durationSeconds = 0,
                )
            }
            cancelTimeoutTracking()
            FcmUtils.clearCallStatus()  // Clear before moving to MainActivity

            Log.d("MaleCallConnecting", "disconnectCall - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
            
            if (fromChat && chatPeerUserId != -1) {
                // Return to ChatActivityInHouse if call was initiated from chat
                Log.d("NavigationDebug", "Returning to ChatActivityInHouse due to timeout from chat")
                val intent = Intent(this@MaleCallConnectingActivity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                    putExtra("USER_ID", chatPeerUserId)
                    putExtra("USER_NAME", receiverName ?: "")
                    putExtra("USER_IMAGE", receiverImg ?: "")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            } else {
                Log.d("NavigationDebug", "Redirecting to MainActivity due to timeout.")
                val intent = Intent(this@MaleCallConnectingActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }
        }
    }

    /**
     * I039 — symmetric to HimaTelecomManager.tryAddIncomingCall on the receive side.
     * Bundles up the same EXTRA_* keys HimaConnection expects so the outgoing
     * connection identifies the peer correctly in the system second-call UI.
     */
    private fun registerOutgoingWithTelecom() {
        val ch = currentCallChannelName ?: return
        val ct = callType ?: return
        val extras = Bundle().apply {
            putString(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_CALL_TYPE, ct)
            putInt(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_SENDER_ID, receiverId)
            putString(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_CHANNEL_NAME, ch)
            putInt(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_CALL_ID, callId)
            putString(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_CALLER_NAME, receiverName ?: "Hima call")
            putString(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_CALLER_IMAGE, receiverImg ?: "")
            putString(com.gmwapp.hima.agora.telecom.HimaConnection.EXTRA_RECEIVER_GENDER, "male")
        }
        com.gmwapp.hima.agora.telecom.HimaTelecomManager.tryPlaceOutgoingCall(this, extras)
    }

    fun sendCallNotification(senderId:Int, receiverId:Int, callType:String, message:String) {
        if (currentCallChannelName == null) {
            currentCallChannelName = generateUniqueChannelName(senderId)
            prefetchAgoraToken(currentCallChannelName!!)
        }
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName = currentCallChannelName!!,
            message = message
        )
        observeNotificationResponse()
    }

    private fun prefetchAgoraToken(channelName: String) {
        Log.d("AgoraTiming", "prefetchAgoraToken started at ${System.currentTimeMillis()}")
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            if (response != null && response.success == true && !response.token.isNullOrEmpty()) {
                prefetchedAgoraToken = response.token
                prefetchedAgoraAppId = response.app_id
                Log.d("AgoraTiming", "prefetchAgoraToken received at ${System.currentTimeMillis()}")
            }
        }
        agoraViewModel.getAgoraToken(channelName, 0, "publisher", 3600)
    }

    private var unreachableHandled = false

    fun observeNotificationResponse() {
        // sendCallNotification calls this on every send (ring + declines); register the
        // LiveData observers only once so a retry can't stack duplicate observers (which
        // would multiply into runaway re-sends).
        if (notifObserversRegistered) return
        notifObserversRegistered = true

        fcmNotificationViewModel.notificationResponseLiveData.observe(this) { response ->
            response?.let {
                if (it.success) {
                    Log.d("FCMNotification", "Notification sent successfully!")
                    lastRingNotifMessage = null  // ring delivered — stop any pending retry
                } else {
                    Log.e("FCMNotification", "Failed to send notification: ${it.message}")
                    // Fix 2 — the receiver has no deliverable FCM token (logged out /
                    // backend stored "0"), so the ring was never delivered. Don't leave
                    // the caller dangling on "Connecting" for the full 40s timeout: tear
                    // the call down now via the same path the timeout uses. Matched
                    // narrowly on the backend's "...does not have an FCM token" message;
                    // the response deserializer reports transient/malformed responses as
                    // success=true, so this cannot false-trigger and kill a live call.
                    if (!unreachableHandled && it.message.contains("FCM token", ignoreCase = true)) {
                        unreachableHandled = true
                        Toast.makeText(
                            this@MaleCallConnectingActivity,
                            "User is not available right now",
                            Toast.LENGTH_LONG
                        ).show()
                        disconnectCall()
                    }
                }
            }
        }

        // The receiver's FCM token is briefly missing right after the OneSignal call push
        // cold-starts their app (it re-registers within ~2s). A "no FCM token"/404 here is
        // transient, so retry the ring a few times instead of letting the call silently
        // spin to the 40s timeout. Mirrors the female-side connecting screen.
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
                }
                // else: keep the historical male-side behavior — log only, no toast; the
                // 40s timeout (disconnectCall) handles a genuinely unreachable receiver.
            }
        }
    }

    /** See the female-side twin: retry only the ring, only while connecting, only up to the cap. */
    private fun shouldRetryRingNotification(error: String): Boolean {
        val tokenNotReady = error.contains("FCM token", ignoreCase = true) ||
            error.contains("Error: 404", ignoreCase = true)
        return isRunning && !isFinishing &&
            tokenNotReady &&
            lastRingNotifMessage != null &&
            ringNotifRetryCount < maxRingNotifRetries
    }

    fun observeCallAcceptance() {
        Log.d("CreatorCallDiag", "MConn.observer.attached userId=$userId receiverId=$receiverId callType=$callType")
        FcmUtils.callStatus.observe(this, Observer { callData ->
            if (callData != null) {  // Check if it's not null before destructuring
                val (status, channelName) = callData
                Log.d("callStatusData","$status")
                val cur = BaseApplication.getInstance()?.getCurrentActivity()?.javaClass?.simpleName
                Log.d(
                    "CreatorCallDiag",
                    "MConn.observer.fire status=$status channel=$channelName currentActivity=$cur"
                )

                // B2 / TC_003 — cross-listen guard. During a concurrent call
                // collision the losing caller's connecting screen would otherwise
                // consume the WINNER's "accepted" relay (a different Agora channel)
                // and fake-connect into the wrong room — the "auto-connect" flash —
                // or wrongly abort on a foreign "rejected". Only act on a status
                // update whose channel matches THIS call's. currentCallChannelName is
                // always set (in sendCallNotification) before any legitimate relay
                // for our own call can arrive, so a null/mismatched channel here is
                // always a foreign or stale signal and must be ignored.
                if (channelName != currentCallChannelName) {
                    Log.d(
                        "CreatorCallDiag",
                        "MConn.observer.ignore foreign/stale channel=$channelName own=$currentCallChannelName status=$status"
                    )
                    return@Observer
                }

                if (status == "accepted") {
                    FcmUtils.clearCallStatus()  // Clear before moving to AudioCallingActivity
                    // I039 — transition the Telecom outgoing connection from DIALING → ACTIVE
                    // so the system tracks this Hima call for hold/switch coordination.
                    com.gmwapp.hima.agora.telecom.HimaTelecomManager.markActive()

                    var currentActivity = BaseApplication.getInstance()?.getCurrentActivity()
                    if (currentActivity !is MainActivity){

                    Log.d("callTypeData","$callType")
                    if (callType=="audio") {
                        cancelTimeoutTracking()

                        val intent = Intent(this, MaleAudioCallingActivity::class.java).apply {
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("RECEIVER_ID", receiverId)
                            putExtra("CALL_ID", callId)
                            putExtra("IS_CALLER", true)
                            prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                            prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                            Log.d("RECEIVER_ID","$receiverId")
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                        finish()
                    }else{
                        cancelTimeoutTracking()

                        FcmUtils.clearCallStatus()
                        val intent = Intent(this, MaleVideoCallingActivity::class.java).apply {
                                putExtra("CHANNEL_NAME", channelName)
                                putExtra("RECEIVER_ID", receiverId)
                                putExtra("CALL_ID", callId)
                                putExtra("IS_CALLER", true)
                                prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                                prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                        }
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(intent)
                            finish()
                    }
                    }
                } else if (status == "rejected") {
                    FcmUtils.clearCallStatus()  // Clear before moving to MainActivity
                    // I039 — clean up the Telecom outgoing connection so the system stops
                    // treating us as busy. Without this, a subsequent quick-retry outgoing
                    // call would find the previous self-managed account still "active".
                    com.gmwapp.hima.agora.telecom.HimaTelecomManager.endActiveCall(
                        android.telecom.DisconnectCause.REJECTED
                    )

                    cancelTimeoutTracking()

                    Log.d("CallStatus", "MaleConnecting.peerRejected (observed, no post — peer already posted) self=$userId peer=$receiverId callId=$callId")
                    Log.d("MaleCallConnecting", "Call rejected - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")
                    
                    if (fromChat && chatPeerUserId != -1) {
                        // Return to ChatActivityInHouse if call was initiated from chat
                        Log.d("NavigationDebug", "Returning to ChatActivityInHouse due to call rejected from chat")
                        val intent = Intent(this, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                            putExtra("USER_ID", chatPeerUserId)
                            putExtra("USER_NAME", receiverName ?: "")
                            putExtra("USER_IMAGE", receiverImg ?: "")
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d("NavigationDebug", "Redirecting to MainActivity due to call rejected")
                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                        Log.d("wentToMain","$status")
                        finish()
                    }
                }
            }
        })
    }


    fun generateUniqueChannelName(senderId: Int): String {
        val timestamp = System.currentTimeMillis() // Get current timestamp in milliseconds
        Log.d("ChannelnameCheck","${senderId}_$timestamp")
        return "${senderId}_$timestamp"
    }

    private fun observeUserBusyStatus() {
        FcmUtils.userBusyStatus.observe(this, Observer { busyData ->
            if (busyData != null) {
                val (callTypeFromBusy, userName) = busyData
                
                Log.d("MaleCallConnecting", "User busy: $userName, callType: $callTypeFromBusy")
                
                // Stop all ongoing operations
                isRunning = false
                cancelTimeoutTracking()
                
                // Update UI to show busy message
                binding.tlWaitTitle.text = "${receiverName?.trimEnd { it.isDigit() }} just got busy!"
                binding.tvProgressText.text = "Finding another match..."
                
                // Clear the status
                FcmUtils.clearUserBusyStatus()
                
                // Wait 1.5 seconds then redirect to random call
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        val randomCallIntent = Intent(
                            this@MaleCallConnectingActivity, 
                            com.gmwapp.hima.agora.AgoraRandomCallActivity::class.java
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(DConstants.CALL_TYPE, callType) // Use same call type
                        }
                        startActivity(randomCallIntent)
                        finish()
                    }
                }, 1500) // 1.5 seconds delay
            }
        })
    }

        override fun onDestroy() {
            super.onDestroy()
            isRunning = false
            cancelTimeoutTracking()
            handler.removeCallbacksAndMessages(null)  // cancel any pending ring-notif retry

        }

    fun navigateToMain(){
        isRunning = false
        FcmUtils.clearCallStatus()  // Clear before moving to MainActivity
        FcmUtils.isUserAvailable = 0
        cancelTimeoutTracking()
        Log.d("GoinginMain", "${FcmUtils.isUserAvailable}")
        Log.d("MaleCallConnecting", "navigateToMain - fromChat=$fromChat, chatPeerUserId=$chatPeerUserId")

        if (fromChat && chatPeerUserId != -1) {
            // Return to ChatActivityInHouse if call was initiated from chat
            Log.d("NavigationDebug", "Returning to ChatActivityInHouse from navigateToMain")
            val intent = Intent(this, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                putExtra("USER_ID", chatPeerUserId)
                putExtra("USER_NAME", receiverName ?: "")
                putExtra("USER_IMAGE", receiverImg ?: "")
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
        } else {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }


}