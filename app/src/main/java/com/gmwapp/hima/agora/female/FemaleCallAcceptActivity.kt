package com.gmwapp.hima.agora.female

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.CallChannel
import com.gmwapp.hima.agora.MyFirebaseMessagingService
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import android.telecom.DisconnectCause
import com.gmwapp.hima.databinding.ActivityFemaleCallAcceptBinding
import com.gmwapp.hima.retrofit.responses.CallEndReason
import com.gmwapp.hima.retrofit.responses.CallEndedBy
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.viewmodels.CallStatusViewModel
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FemaleCallAcceptActivity : AppCompatActivity() {
    companion object {
        // FORCE_CLOSE_REJECT regression guard (2026-07-08) — identity of the
        // CURRENT ring instance. When a duplicate incoming surface recreates this
        // screen, the newer onCreate overwrites this BEFORE the older instance's
        // onDestroy runs, so onDestroy force-close-rejects ONLY while it is still
        // the live instance (liveInstance === this). A superseded duplicate can
        // therefore never reject the call the newer instance is actively ringing.
        @Volatile
        private var liveInstance: FemaleCallAcceptActivity? = null
    }

    private lateinit var binding: ActivityFemaleCallAcceptBinding
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private val agoraViewModel: AgoraViewModel by viewModels()
    private val callStatusViewModel: CallStatusViewModel by viewModels()

    private var callType: String? = null
    private var receiverId: Int = -1
    private var call_Id: Int = 0
    var callerName = ""
    var callerImage = ""
    private val userAvatarViewModel: UserAvatarViewModel by viewModels()

    private var channelName: String? = null
    var userId: Int? = null
    private var prefetchedAgoraToken: String? = null
    private var prefetchedAgoraAppId: String? = null

    // B10/TC_009: gate the call-screen launch on the "accepted" relay so the
    // creator never enters a blank, caller-gone call. The backend's
    // ACCEPTED_DEAD_BLOCK returns success=false / error="call_already_ended"
    // (8s-grace-validated, so it won't eat a legit just-connected accept).
    // Fail-OPEN: launch anyway on success / any other outcome / timeout, so a
    // slow or failing relay never blocks a legitimate accept.
    private val acceptGateHandler = Handler(Looper.getMainLooper())
    private var acceptInFlight = false
    private var acceptLaunchHandled = false
    private var pendingChannel: String? = null
    private var pendingReceiver: Int = -1
    private var pendingCallType: String? = null
    private val ACCEPT_GATE_TIMEOUT_MS = 2000L

    // C-04/B6: instant tap feedback on Answer/Decline. The buttons grey out the
    // moment they're tapped (no double-fire, no "did it register?" pause). The
    // "Connecting…" hint is DELAYED ~200ms (Option B): a fast accept opens the
    // call screen before it ever shows, so quick calls get no flicker; only a
    // genuinely-waiting accept surfaces the hint. Pink→purple brand gradient
    // (#C471ED→#FF6B9D, matches profile_gradient_border / new-UI accent).
    private val connectingHintHandler = Handler(Looper.getMainLooper())
    private val CONNECTING_HINT_DELAY_MS = 200L
    // Latches on the first Accept/Decline tap. Touch taps are already blocked by
    // the disabled buttons, but performClick() (headset hook / notification
    // accept) bypasses isEnabled — this guard hard-stops that re-entry too.
    private var callButtonsLocked = false

    // ── TC-HMA-002: callee-side liveness poll ───────────────────────────────
    // The caller's cancel/hang-up reaches this ring banner only via a
    // best-effort FCM "callDeclined"/"callEnded" relay. When that push is
    // dropped/delayed (Doze, battery optimization, stale token, flaky link),
    // the incoming banner would otherwise ring forever — the creator has to
    // dismiss a ghost call by hand. Mirror of the caller-side poll in
    // MaleCallConnectingActivity.startAlivePolling(): while still RINGING (not
    // yet accepted), ping the authoritative server state every few seconds and
    // tear the banner down the instant the backend reports the call ended.
    // One PK lookup per tick (cheap); checkConnectingDead only fires on an
    // ACTIVE end (end_reason set), so a legitimately-ringing call is never cut.
    private val aliveHandler = Handler(Looper.getMainLooper())
    private val aliveIntervalMs = 2500L
    // @Volatile: also written by markRemoteEnded() from the FCM background thread
    // (callEnded/callDeclined teardown); read on the main thread in onDestroy and
    // the alive-poll, so the cross-thread write must be visible without a barrier.
    @Volatile
    private var peerEndedHandled = false
    private val alivePollRunnable = object : Runnable {
        override fun run() {
            // peerEndedHandled mirrors the male side's isRunning=false latch: once a
            // teardown has begun, never poll or repost again — don't lean on
            // isFinishing flipping, which can lag a tick behind finish().
            if (peerEndedHandled || isFinishing || isDestroyed ||
                acceptInFlight || acceptLaunchHandled) return
            // No usable call id → checkConnectingDead can't query, so polling is
            // pointless. Bail without rescheduling instead of spinning a no-op
            // tick every 2.5s for the whole ring.
            if (call_Id <= 0) return
            com.gmwapp.hima.utils.CallAliveChecker.checkConnectingDead(call_Id) {
                if (!peerEndedHandled && !isFinishing && !isDestroyed &&
                    !acceptInFlight && !acceptLaunchHandled) {
                    Log.d("CreatorCallDiag", "FAccept.alivePoll -> backend says call ended, dismissing ring banner callId=$call_Id")
                    exitBecausePeerEnded()
                }
            }
            aliveHandler.postDelayed(this, aliveIntervalMs)
        }
    }

    private fun startAlivePolling() {
        // Nothing to poll without a call id; the runnable would only no-op + repost.
        if (call_Id <= 0) return
        // removeCallbacks before postDelayed makes this idempotent in BOTH
        // directions (safe to call again after a stop), matching the male side.
        aliveHandler.removeCallbacks(alivePollRunnable)
        aliveHandler.postDelayed(alivePollRunnable, aliveIntervalMs)
    }

    private fun stopAlivePolling() {
        aliveHandler.removeCallbacks(alivePollRunnable)
    }

    /**
     * The caller already ended the call server-side (cancel / not-answered) and
     * we learned it from the liveness poll rather than the FCM relay. Tear the
     * ring banner down the same way a dropped "callDeclined" push would — but do
     * NOT post a "rejected" status, because the peer already stamped the
     * terminal state (re-posting would double-stamp the call row). Idempotent.
     */
    private fun exitBecausePeerEnded() {
        if (peerEndedHandled || acceptInFlight || acceptLaunchHandled) return
        peerEndedHandled = true
        stopAlivePolling()
        try { HimaTelecomManager.endActiveCall(DisconnectCause.REMOTE) } catch (_: Throwable) {}
        BaseApplication.getInstance()?.stopRingtone()
        BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
        BaseApplication.getInstance()?.clearIncomingCall()
        finish()
    }

    /**
     * FORCE_CLOSE_REJECT regression guard (2026-07-08) — a REMOTE end (the caller
     * cancelled before she answered, arriving as a callEnded/callDeclined push)
     * tears this ring screen down with a bare finish(). In onDestroy that is
     * indistinguishable from a genuine user swipe-away, so the force-close-reject
     * there would wrongly stamp the call "rejected" and re-notify the caller — for
     * a call the peer already ended server-side. The FCM teardown paths call this
     * FIRST so onDestroy's `!peerEndedHandled` guard suppresses that reject.
     * Idempotent; only flips the same latch exitBecausePeerEnded() uses.
     */
    fun markRemoteEnded() { peerEndedHandled = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // B024: this activity is the call UI; any system heads-up banner is
        // now redundant. Wipe ALL incoming-call notifications (FCM + OneSignal
        // paths) as the FIRST thing we do — before setContentView, Glide,
        // viewmodels, etc. — so the banner+full-screen overlap window shrinks
        // to roughly the FSI->process-start latency instead of ~300ms of
        // onCreate setup. Cleared early on every entry, including cold-start.
        BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
        // FORCE_CLOSE_REJECT regression guard (2026-07-08) — claim "live instance"
        // as early as possible so a duplicate surface that recreates this screen
        // supersedes us, and our onDestroy won't reject the newer instance's call.
        liveInstance = this
        // Route the volume rocker to STREAM_RING while this activity is on
        // screen so volume up/down adjusts the incoming ringtone (B027).
        // Without this the default STREAM_MUSIC is targeted and the rocker
        // appears to do nothing while the phone is ringing.
        volumeControlStream = AudioManager.STREAM_RING
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Log.d(
            "HimaIncomingCall",
            "FemaleCallAcceptActivity.onCreate flags=${intent.flags} action=${intent.action} keyguardLocked=${km.isKeyguardLocked}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        enableEdgeToEdge()
        binding = ActivityFemaleCallAcceptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.id?.let { userId = userData?.id}

        callType = intent.getStringExtra("CALL_TYPE")
        receiverId = intent.getIntExtra("SENDER_ID", -1)
        channelName = intent.getStringExtra("CHANNEL_NAME")

        callerName = intent.getStringExtra("Caller_NAME").orEmpty()
        callerImage = intent.getStringExtra("Caller_Image").orEmpty()

        Log.d("callerdeatails","$callerImage")
        Log.d("callerdeatails","$callerName")
        call_Id = intent.getIntExtra("CALL_ID", 0)
        Log.d(
            "VideoCallFlow",
            "FemaleAccept.onCreate channel=$channelName callId=$call_Id senderId=$receiverId " +
                "callType=$callType userId=$userId"
        )

        // Pre-request RECORD_AUDIO so permission dialog won't block call start on accept.
        // READ_PHONE_STATE is OPTIONAL — it only powers the "on hold" banner when a SIM
        // call interrupts the Hima call (CallPhoneStateHelper); denying it never blocks
        // the call. Requested together so the user isn't prompted again mid-call.
        val acceptPerms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            acceptPerms.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            acceptPerms.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (acceptPerms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, acceptPerms.toTypedArray(), 100)
        }

        // Pre-fetch Agora token while the user decides to accept/reject
        if (CallChannel.isJoinable(channelName)) {
            prefetchAgoraToken(channelName!!)
        }

        // Start pulse animations for the avatar rings
        startPulseAnimations()

        if (callType=="audio"){
            binding.calltype.setText("Incoming Voice Call")
            binding.callTypeIcon.setImageResource(R.drawable.ic_mic)
        }else{
            binding.calltype.setText("Incoming Video Call")
            binding.callTypeIcon.setImageResource(R.drawable.ic_videocam)
        }


        val pendingTag = BaseApplication.getInstance()?.getLastIncomingCallTag()
        val expectedTag = if (call_Id != 0) call_Id.toString() else null
        val alreadyHandled = BaseApplication.getInstance()?.isIncomingCall() != true ||
            (expectedTag != null && pendingTag != null && pendingTag != expectedTag)
        if (alreadyHandled) {
            Log.d(
                "HimaIncomingCall",
                "FemaleCallAcceptActivity: stale launch (pendingTag=$pendingTag expected=$expectedTag) -> finish"
            )
            BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
            finish()
            return
        }

        // Activity now owns the call presentation; cancel the heads-up so the
        // OS channel ringtone stops before MediaPlayer takes over the loop —
        // otherwise both play in parallel on locked phones (B147 fix).
        BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
        if (BaseApplication.getInstance()?.isRingtonePlaying() == false) {
            BaseApplication.getInstance()?.playIncomingCallSound()
        }




        binding.callerName.setText(callerName.trimEnd { it.isDigit() })
        Glide.with(this)
            .load(callerImage)
            .apply(RequestOptions.circleCropTransform())
            .into(binding.ivLogo)





        Log.d("callType","from notification $callType")

        userAvatarViewModel.getUserAvatar(receiverId)

        avatarObservers()

        Log.d("CallID","$call_Id")

        // TC-HMA-002: this is a genuine live ring (passed the stale-launch guard
        // above), so begin polling the authoritative call state. If the caller
        // cancels and the "callDeclined"/"callEnded" push is dropped, this
        // dismisses the banner automatically instead of ringing forever.
        startAlivePolling()



        // B10/TC_009: single gate observer. Only acts while an accept is in
        // flight (acceptInFlight) and only once (acceptLaunchHandled). The
        // "accepted" relay's 200-body lands here; real network failures go to
        // the error LiveData instead (→ fail-open timer). Registered once.
        fcmNotificationViewModel.notificationResponseLiveData.observe(this) { resp ->
            if (!acceptInFlight || acceptLaunchHandled || resp == null) return@observe
            val callAlreadyEnded = !resp.success &&
                (resp.error == "call_already_ended" ||
                    resp.message.contains("already ended", ignoreCase = true))
            acceptLaunchHandled = true
            acceptInFlight = false
            acceptGateHandler.removeCallbacksAndMessages(null)
            if (callAlreadyEnded) {
                Log.d("CreatorCallDiag", "FAccept gate: call_already_ended → abort launch callId=$call_Id")
                BaseApplication.getInstance()?.clearIncomingCall()
                Toast.makeText(this, "This call has already ended", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                launchCallScreen()
            }
        }

        binding.accpet.setOnClickListener {
            if (callButtonsLocked) return@setOnClickListener
            // B_002 — an OFFLINE device must never enter a call it can never join.
            // Agora can't connect, onJoinChannelSuccess never fires, and the only
            // safety timer (startTimeoutTracking) is armed INSIDE that callback — so
            // nothing ends the call and it sits "connected" with no audio forever.
            // Block at the source, BEFORE any state changes: no lockCallButtons, no
            // stopRingtone, no markRingAccepted — the ring stays fully intact so she
            // can retry or decline, and the caller just sees a normal timeout.
            // isOnline() is lenient + fail-open, so it can only block a genuinely
            // absent network (see NetworkUtils).
            if (!com.gmwapp.hima.utils.NetworkUtils.isOnline(this)) {
                Log.d("CreatorCallDiag", "FAccept.click BLOCKED: device offline callId=$call_Id")
                Toast.makeText(this, getString(R.string.call_accept_no_network), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (receiverId != -1 && CallChannel.isJoinable(channelName) && !callType.isNullOrEmpty()) {
                Log.d(
                    "CreatorCallDiag",
                    "FAccept.click sender(female)=$userId receiver(male)=$receiverId " +
                        "channel=$channelName callId=$call_Id callType=$callType"
                )
                Log.d(
                    "VideoCallFlow",
                    "FemaleAccept.acceptClick channel=$channelName callId=$call_Id callType=$callType " +
                        "tokenPrefetched=${!prefetchedAgoraToken.isNullOrEmpty()} appIdPrefetched=${!prefetchedAgoraAppId.isNullOrEmpty()}"
                )

                // C-04/B6: acknowledge the tap in THIS frame — grey out both
                // buttons (also blocks a double-tap) and arm the delayed
                // "Connecting…" hint. launchCallScreen() cancels the hint, so a
                // fast accept never flashes it.
                lockCallButtons()
                showConnectingHintDelayed()

                // B10/TC_009: silence the ring immediately (responsiveness) but
                // DEFER the call-screen launch until the "accepted" relay tells
                // us the call isn't already dead. The gate observer above and the
                // fail-open timer below decide; markActive happens only on launch.
                BaseApplication.getInstance()?.stopRingtone()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()

                // TC-HMA-002: stop the liveness poll now — the call is going
                // live, so a "dead" reading during accept must not tear it down.
                stopAlivePolling()

                pendingChannel = channelName
                pendingReceiver = receiverId
                pendingCallType = callType
                acceptInFlight = true
                acceptLaunchHandled = false
                // C-10 fix: record that THIS caller's ring is accepted, so a stale
                // "callDeclined" racing in (caller ring-timeout firing at the same
                // instant) is ignored instead of tearing down the accepted call.
                BaseApplication.getInstance()?.markRingAccepted(receiverId)
                acceptGateHandler.removeCallbacksAndMessages(null)
                acceptGateHandler.postDelayed({
                    if (!acceptLaunchHandled) {
                        acceptLaunchHandled = true
                        acceptInFlight = false
                        Log.d("CreatorCallDiag", "FAccept gate: relay timeout → fail-open launch callId=$call_Id")
                        launchCallScreen()
                    }
                }, ACCEPT_GATE_TIMEOUT_MS)

                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "accepted")
            } else {
                // Channel-less push (no real "<id>_<ts>" channel) — never join the
                // shared "default_channel" room (black screen + other callers'
                // audio while the caller waits alone). Tell her and tear down.
                Log.w(
                    "VideoCallFlow",
                    "FemaleAccept: refusing accept, unusable channel='$channelName' callId=$call_Id receiver=$receiverId"
                )
                Toast.makeText(
                    this,
                    "Couldn't connect this call. Please ask them to call again.",
                    Toast.LENGTH_LONG
                ).show()
                // TC-HMA-002: deliberate exit — stop the poll like every other
                // teardown path, rather than leaving it to onDestroy.
                stopAlivePolling()
                HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
                BaseApplication.getInstance()?.stopRingtone()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                BaseApplication.getInstance()?.clearIncomingCall()
                finish()
            }
        }
        binding.reject.setOnClickListener {
            if (callButtonsLocked) return@setOnClickListener
            if (receiverId != -1 && !channelName.isNullOrEmpty() && !callType.isNullOrEmpty()) {
                // C-04/B6: instant feedback — grey out the buttons and flip the
                // status to "Ending…" the moment Decline is tapped. Teardown
                // below is synchronous/fast, so no delay is needed here.
                lockCallButtons()
                showEndingState()

                // TC-HMA-002: she's actively declining; stop the liveness poll so
                // it can't race the manual reject teardown below.
                stopAlivePolling()
                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "rejected")
                userId?.let { selfId ->
                    Log.d("CallStatus", "FemaleAccept.reject → rejected/receiver self=$selfId peer=$receiverId callId=$call_Id")
                    callStatusViewModel.saveCallStatus(
                        userId = selfId,
                        receivedUserId = receiverId,
                        callId = call_Id,
                        endReason = CallEndReason.REJECTED,
                        endedBy = CallEndedBy.RECEIVER,
                        endedByUserId = selfId,
                        durationSeconds = 0,
                    )
                    // REJECT_FALSE_MISS_2026_07_09: durable backstop so the reject
                    // survives offline / app death (idempotent alongside the post above).
                    com.gmwapp.hima.workers.CallStatusWorker.enqueueReject(applicationContext, selfId, receiverId, call_Id)
                }

                HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                BaseApplication.getInstance()?.stopRingtone()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                BaseApplication.getInstance()?.clearIncomingCall()
                // Re-check the keyguard NOW (not the onCreate snapshot) so an
                // unlock-after-ring is routed correctly.
                val lockedNow = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
                if (lockedNow) {
                    // Device is locked: just dismiss the call screen back to the
                    // lockscreen. Do NOT open MainActivity over the keyguard, and
                    // NEVER kill the app. The old finishAffinity()+exitProcess(0)
                    // here force-closed the entire app whenever a call was declined
                    // while the device was locked (and the FSI shows over the
                    // keyguard, so this fired even when the user thought they were
                    // "in the app").
                    finish()
                } else {
                    val intent = Intent(this@FemaleCallAcceptActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }

            }
        }


        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
//
//                if (receiverId != -1 && !channelName.isNullOrEmpty() && !callType.isNullOrEmpty()) {
//                    sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "rejected")
//
//                    BaseApplication.getInstance()?.stopRingtone()
//                    val intent = Intent(this@FemaleCallAcceptActivity, MainActivity::class.java)
//                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
//                    startActivity(intent)
//                    finish()
//
//                }

            }
        })

        // B021: When the Accept button on the notification was tapped while the
        // app was killed, the FCM-service launched this activity with
        // AUTO_ACCEPT=true. Post to the main queue so all observer/view wiring
        // above finishes first, then perform the same click the user would do.
        maybeAutoAccept(intent)
    }

    /**
     * Re-entry path for AUTO_ACCEPT — singleTop launchMode means a notification
     * tap on Accept while this activity is already in the stack will arrive via
     * onNewIntent, not onCreate. Handle it here too so cold-start and warm-start
     * notification-Accept paths converge on [binding.accpet]'s click handler.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        adoptUpgradedChannel(intent)
        maybeAutoAccept(intent)
    }

    /**
     * B-lockcall channel upgrade: the OneSignal ring can create this screen with
     * the non-joinable `default_channel` placeholder (OneSignal pushes carry no
     * Agora channel — see [CallChannel]). The FCM channel relay then re-launches
     * this singleTop activity with the REAL joinable channel. Adopt it so Accept
     * joins the correct Agora channel instead of refusing with "unusable channel".
     */
    private fun adoptUpgradedChannel(intent: Intent) {
        val newChannel = intent.getStringExtra("CHANNEL_NAME")
        // Only upgrade: a real channel replacing an unusable placeholder. Never
        // downgrade a channel we already consider joinable.
        if (!CallChannel.isJoinable(newChannel) || CallChannel.isJoinable(channelName)) return
        // Only adopt when the upgrade is for THIS call (the FCM guard already
        // enforces callId match; re-check here so channelName and call_Id can
        // never drift out of sync). If a mismatched CALL_ID somehow arrives,
        // keep our channel/call_Id rather than grafting a foreign channel.
        val newCallId = intent.getIntExtra("CALL_ID", 0)
        if (call_Id != 0 && newCallId != 0 && newCallId != call_Id) {
            Log.w("HimaIncomingCall", "FemaleCallAcceptActivity.onNewIntent: callId mismatch (have=$call_Id new=$newCallId) — ignoring channel upgrade")
            return
        }
        channelName = newChannel
        if (newCallId != 0) call_Id = newCallId
        intent.getStringExtra("CALL_TYPE")?.takeIf { it.isNotBlank() }?.let { callType = it }
        intent.getIntExtra("SENDER_ID", -1).takeIf { it != -1 }?.let { receiverId = it }
        intent.getStringExtra("Caller_NAME")?.takeIf { it.isNotBlank() }?.let { callerName = it }
        intent.getStringExtra("Caller_Image")?.takeIf { it.isNotBlank() }?.let { callerImage = it }
        Log.d("HimaIncomingCall", "FemaleCallAcceptActivity.onNewIntent: channel upgraded → $channelName")
        // Prefetch the Agora token for the real channel now so Accept is instant.
        prefetchAgoraToken(newChannel)
    }

    private fun maybeAutoAccept(intent: Intent?) {
        val auto = intent?.getBooleanExtra("AUTO_ACCEPT", false) == true
        if (!auto) return
        // B022: this activity now owns the call lifecycle (and will start
        // CallingService as the in-call FGS once accepted), so the warm-up
        // service can shut down to free a foreground-service slot.
        com.gmwapp.hima.agora.FcmCallService.stop(this)
        // B022: don't fire performClick() immediately — wait briefly for the
        // Agora token prefetch (kicked off in onCreate) to land so the calling
        // activity receives it via the intent extras and can skip its own
        // backend round-trip. Without this, cold-start accept duplicates the
        // token fetch and the call can ring-out before joinChannel finishes.
        val startMs = System.currentTimeMillis()
        val maxWaitMs = 1500L
        val pollHandler = Handler(Looper.getMainLooper())
        val poll = object : Runnable {
            override fun run() {
                val ready = !prefetchedAgoraToken.isNullOrEmpty()
                val timedOut = System.currentTimeMillis() - startMs >= maxWaitMs
                if (ready || timedOut) {
                    Log.d(
                        "HimaIncomingCall",
                        "FemaleCallAcceptActivity: AUTO_ACCEPT firing tokenReady=$ready timedOut=$timedOut waitedMs=${System.currentTimeMillis() - startMs}"
                    )
                    binding.accpet.performClick()
                } else {
                    pollHandler.postDelayed(this, 100L)
                }
            }
        }
        pollHandler.post(poll)
    }

    private fun avatarObservers() {
        userAvatarViewModel.userAvatarLiveData.observe(this) { response ->
            Log.d("userAvatarLiveData", "Image URL: $response")

            if (response != null && response.success) {
                val imageUrl = response.data?.image
                callerName = response.data?.name.toString()
                Log.d("UserAvatar", "Image URL: $imageUrl")

                binding.callerName.setText(callerName.trimEnd { it.isDigit() })
                // Load the avatar image into an ImageView using Glide or Picasso
                // Glide.with(this).load(imageUrl).into(binding.ivMaleUser)
                Glide.with(this)
                    .load(imageUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.ivLogo)

            }
        }

        userAvatarViewModel.userAvatarErrorLiveData.observe(this) { errorMessage ->
            Log.e("UserAvatarError", errorMessage)
        }
    }

    fun sendCallNotification(senderId:Int, receiverId:Int, callType:String,channelName:String,message:String  ) {
        fcmNotificationViewModel.sendNotification(
            senderId = senderId,
            receiverId = receiverId,
            callType = callType,
            channelName =channelName ,
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

    // ── C-04/B6: Answer/Decline instant-feedback helpers ────────────────────
    /** Grey out + disable both call buttons. A disabled View won't dispatch its
     *  click, so this also hard-blocks an accidental double Accept/Decline. */
    private fun lockCallButtons() {
        callButtonsLocked = true
        binding.accpet.isEnabled = false
        binding.reject.isEnabled = false
        binding.accpet.alpha = 0.35f
        binding.reject.alpha = 0.35f
    }

    /** Paint [callStatusText] with the new-UI pink→purple brand gradient. */
    private fun applyGradientStatus(text: String) {
        val tv = binding.callStatusText
        tv.text = text
        val width = tv.paint.measureText(text).coerceAtLeast(1f)
        tv.paint.shader = android.graphics.LinearGradient(
            0f, 0f, width, 0f,
            intArrayOf(0xFFC471ED.toInt(), 0xFFFF6B9D.toInt()),
            null, android.graphics.Shader.TileMode.CLAMP
        )
        tv.invalidate()
    }

    /** Option B: show "Connecting…" only if the accept hasn't opened the call
     *  screen within ~200ms — fast accepts never flash it. */
    private fun showConnectingHintDelayed() {
        connectingHintHandler.removeCallbacksAndMessages(null)
        connectingHintHandler.postDelayed({
            if (!isFinishing && !isDestroyed) applyGradientStatus("Connecting…")
        }, CONNECTING_HINT_DELAY_MS)
    }

    /** Decline path: immediate solid-colour "Ending…" (no gradient shader). */
    private fun showEndingState() {
        val tv = binding.callStatusText
        tv.paint.shader = null
        tv.text = "Ending…"
        tv.setTextColor(android.graphics.Color.parseColor("#FF7A6B"))
    }

    // B10/TC_009: launch the in-call activity. Reached only after the accept
    // gate clears (relay said the call is live, or fail-open timeout). markActive
    // is done here — never on an aborted accept.
    private fun launchCallScreen() {
        if (isFinishing || isDestroyed) return
        // C-04/B6: the call screen is opening — kill any pending "Connecting…"
        // hint so a fast accept transitions straight to the call with no flash.
        connectingHintHandler.removeCallbacksAndMessages(null)
        val channel = pendingChannel
        val type = pendingCallType
        val receiver = pendingReceiver
        if (channel.isNullOrEmpty() || channel == CallChannel.DEFAULT_SENTINEL ||
            type.isNullOrEmpty() || receiver == -1) {
            Log.w("CreatorCallDiag", "FAccept launchCallScreen: missing/unusable pending args → abort callId=$call_Id")
            finish()
            return
        }
        HimaTelecomManager.markActive()
        BaseApplication.getInstance()?.clearIncomingCall()
        val target = if (type == "audio") FemaleAudioCallingActivity::class.java
                     else FemaleVideoCallingActivity::class.java
        val intent = Intent(this, target).apply {
            putExtra("CHANNEL_NAME", channel)
            putExtra("RECEIVER_ID", receiver)
            putExtra("CALL_ID", call_Id)
            prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
            prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
            // C-05: hand the caller's name/image we already have to the call
            // screen so it shows the avatar instantly instead of a blank white
            // circle while getUserAvatar() round-trips.
            putExtra("Caller_NAME", callerName)
            putExtra("Caller_Image", callerImage)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun prefetchAgoraToken(channelForToken: String) {
        Log.d("AgoraTiming", "FemaleCallAccept prefetchAgoraToken started at ${System.currentTimeMillis()}")
        Log.d("VideoCallFlow", "FemaleAccept.prefetchToken.start channel=$channelForToken callId=$call_Id")
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            Log.d(
                "VideoCallFlow",
                "FemaleAccept.prefetchToken.response success=${response?.success} " +
                    "tokenPresent=${!response?.token.isNullOrEmpty()} appIdPresent=${!response?.app_id.isNullOrEmpty()}"
            )
            if (response != null && response.success == true && !response.token.isNullOrEmpty()) {
                prefetchedAgoraToken = response.token
                prefetchedAgoraAppId = response.app_id
                Log.d("AgoraTiming", "FemaleCallAccept prefetchAgoraToken received at ${System.currentTimeMillis()}")
            }
        }
        agoraViewModel.getAgoraToken(channelForToken, 0, "publisher", 3600)
    }

    // Holds the Accept/Decline "rock" animators so they can be cancelled on teardown.
    private val buttonShakeAnimators = mutableListOf<android.animation.ObjectAnimator>()

    private fun startPulseAnimations() {
        try {
            // Load pulse animation
            val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)

            // Start animation on outer ring with delay
            Handler(Looper.getMainLooper()).postDelayed({
                binding.pulseRingOuter.startAnimation(pulseAnimation)
            }, 0)

            // Start animation on middle ring with delay for staggered effect
            Handler(Looper.getMainLooper()).postDelayed({
                binding.pulseRingMiddle.startAnimation(pulseAnimation)
            }, 500)

            // Both call buttons gently rock to draw the eye while ringing (mockup-approved).
            startButtonShake(binding.accpet)
            startButtonShake(binding.reject)
        } catch (e: Exception) {
            Log.e("PulseAnimation", "Error starting pulse animations: ${e.message}")
        }
    }

    /** Gentle attention "rock": a decaying ±14° swing with a slight scale, looping every 1.3s. */
    private fun startButtonShake(view: android.view.View) {
        val rot = android.animation.PropertyValuesHolder.ofKeyframe(
            android.view.View.ROTATION,
            android.animation.Keyframe.ofFloat(0f, 0f),
            android.animation.Keyframe.ofFloat(0.15f, -14f),
            android.animation.Keyframe.ofFloat(0.30f, 12f),
            android.animation.Keyframe.ofFloat(0.45f, -9f),
            android.animation.Keyframe.ofFloat(0.60f, 7f),
            android.animation.Keyframe.ofFloat(0.75f, -3f),
            android.animation.Keyframe.ofFloat(1f, 0f)
        )
        val sx = android.animation.PropertyValuesHolder.ofKeyframe(
            android.view.View.SCALE_X,
            android.animation.Keyframe.ofFloat(0f, 1f),
            android.animation.Keyframe.ofFloat(0.22f, 1.06f),
            android.animation.Keyframe.ofFloat(0.6f, 1f),
            android.animation.Keyframe.ofFloat(1f, 1f)
        )
        val sy = android.animation.PropertyValuesHolder.ofKeyframe(
            android.view.View.SCALE_Y,
            android.animation.Keyframe.ofFloat(0f, 1f),
            android.animation.Keyframe.ofFloat(0.22f, 1.06f),
            android.animation.Keyframe.ofFloat(0.6f, 1f),
            android.animation.Keyframe.ofFloat(1f, 1f)
        )
        val anim = android.animation.ObjectAnimator.ofPropertyValuesHolder(view, rot, sx, sy).apply {
            duration = 1300
            repeatCount = android.animation.ObjectAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
        buttonShakeAnimators.add(anim)
    }

    /**
     * One-press silence for the incoming ringtone — matches native phone-call
     * behaviour. Consumes volume up/down while ringing so we stop the channel
     * sound + MediaPlayer instead of just nudging STREAM_RING by one notch.
     * The call screen stays up; user can still Accept/Decline.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val ringing = BaseApplication.getInstance()?.isRingtonePlaying() == true
            if (ringing) {
                BaseApplication.getInstance()?.stopRingtone()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                return true
            }
        }
        // I022 — wired headset hook / BT AVRCP play-pause = single-press
        // accept on the incoming-call screen, matching native phone /
        // WhatsApp parity. MEDIA_PLAY_PAUSE covers BT headsets that map
        // their button to the media key instead of HEADSETHOOK.
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            binding.accpet.performClick()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // C-04/B6: drop any pending "Connecting…" hint so it can't fire post-teardown.
        connectingHintHandler.removeCallbacksAndMessages(null)
        // TC_003 ghost-block fix: clear the "incoming call" freshness flag on any
        // teardown that didn't already clear it — e.g. the launchCallScreen abort
        // at :438 (missing pending args), a swipe-away, or a system kill. Left set,
        // isIncomingCallFresh() stays true ~45s and the single-call guard silently
        // auto-rejects the creator's NEXT caller, so she never rings. Guard on
        // isFinishing so a config-change recreate (rotation) does NOT drop a still-
        // live ring. Accept/reject/launch paths already clear it earlier
        // (:224/:292/:301/:442); this is the catch-all for every other exit.
        if (isFinishing) {
            // Only clear if the still-armed incoming call is OURS. Mirror the
            // stale-launch guard at :165-168: if a NEWER call has re-armed the
            // flag with a different tag (pendingTag != our call_Id), leave it —
            // clearing would wipe the newer call's live ring. Otherwise clear so
            // a stuck flag can't blind the creator's next caller.
            val pendingTag = BaseApplication.getInstance()?.getLastIncomingCallTag()
            val ourTag = if (call_Id != 0) call_Id.toString() else null
            if (pendingTag == null || ourTag == null || pendingTag == ourTag) {
                // FORCE_CLOSE_REJECT_2026_07_07 — she left the ring WITHOUT
                // accepting or declining (task swipe / system finish). Treat it as
                // a Decline so the server row is stamped rejected (clears pending so
                // the ring can't be resurrected on reopen) and the caller stops
                // ringing. Secondary catch to FcmCallService.onTaskRemoved.
                // callButtonsLocked is set by BOTH Accept and Decline taps, so this
                // fires only for a genuine no-action exit; acceptInFlight/
                // acceptLaunchHandled/peerEndedHandled add belt-and-braces so a real
                // accept or peer-end never mis-rejects.
                // liveInstance === this: a duplicate surface that recreated this
                // screen has NOT superseded us, so this is a genuine abandon — not
                // us being torn down while a newer instance rings the same call.
                if (liveInstance === this &&
                    !callButtonsLocked && !peerEndedHandled &&
                    !acceptInFlight && !acceptLaunchHandled &&
                    call_Id > 0 && receiverId != -1) {
                    val selfId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
                    Log.d("CallStatus", "FemaleAccept.onDestroy no-action exit → force-close reject callId=$call_Id")
                    BaseApplication.getInstance()?.rejectCallOnAppClose(
                        selfId, receiverId, call_Id, callType, channelName
                    )
                }
                BaseApplication.getInstance()?.clearIncomingCall()
            }
        }
        // B10/TC_009: drop any pending accept-gate fail-open so it can't fire
        // against a destroyed activity.
        acceptGateHandler.removeCallbacksAndMessages(null)
        // TC-HMA-002: stop the liveness poll so it can't fire against a
        // destroyed activity.
        stopAlivePolling()
        // Stop animations
        try {
            binding.pulseRingOuter.clearAnimation()
            binding.pulseRingMiddle.clearAnimation()
            buttonShakeAnimators.forEach { it.cancel() }
            buttonShakeAnimators.clear()
        } catch (e: Exception) {
            Log.e("PulseAnimation", "Error clearing animations: ${e.message}")
        }
        // FORCE_CLOSE_REJECT regression guard (2026-07-08) — only relinquish the
        // "live instance" claim if we still hold it; a newer instance that took
        // over must keep its claim so ITS onDestroy can still act.
        if (liveInstance === this) liveInstance = null
    }

}