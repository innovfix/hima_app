package com.gmwapp.hima.agora.male

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
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.animation.AnimationUtils
import android.widget.Toast
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
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.agora.CallChannel
import com.gmwapp.hima.agora.telecom.HimaTelecomManager
import android.telecom.DisconnectCause
import com.gmwapp.hima.databinding.ActivityMaleCallAcceptBinding
import com.gmwapp.hima.retrofit.responses.CallEndReason
import com.gmwapp.hima.retrofit.responses.CallEndedBy
import com.gmwapp.hima.viewmodels.AgoraViewModel
import com.gmwapp.hima.viewmodels.CallStatusViewModel
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import com.gmwapp.hima.viewmodels.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MaleCallAcceptActivity : AppCompatActivity() {
    companion object {
        // FORCE_CLOSE_REJECT parity (male port of FemaleCallAcceptActivity) —
        // identity of the CURRENT ring instance. When a duplicate incoming
        // surface recreates this screen, the newer onCreate overwrites this
        // BEFORE the older instance's onDestroy runs, so onDestroy force-close-
        // rejects ONLY while it is still the live instance (liveInstance === this).
        // A superseded duplicate can therefore never reject the call the newer
        // instance is actively ringing.
        @Volatile
        private var liveInstance: MaleCallAcceptActivity? = null
    }

    private lateinit var binding: ActivityMaleCallAcceptBinding
    private val fcmNotificationViewModel: FcmNotificationViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
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

    // ── TC-HMA-002 (male port): callee-side liveness poll ───────────────────
    // 1:1 port of the mechanism already shipping on FemaleCallAcceptActivity —
    // NOT a new implementation. When a CREATOR (female) initiates and cancels,
    // this MALE ring banner is the receiver, and the caller's cancel reaches it
    // only via a best-effort FCM "callDeclined"/"callEnded" relay. When that push
    // is dropped/delayed (Doze, battery optimization, stale token) the banner
    // would ring forever — the exact ghost-ring seen only on creator-initiated
    // calls, because a male-initiated call rings a FEMALE screen that already has
    // this poll. While still RINGING (not yet accepted/declined), ping the
    // authoritative server state every few seconds and tear the banner down the
    // instant the backend reports the call ended. One PK lookup per tick (cheap);
    // checkConnectingDead only fires on an ACTIVE end (end_reason set), so a
    // legitimately-ringing call is never cut.
    private val aliveHandler = Handler(Looper.getMainLooper())
    private val aliveIntervalMs = 2500L
    private var peerEndedHandled = false
    private var terminalStarted = false // accept/decline tapped → stop self-heal
    private val alivePollRunnable = object : Runnable {
        override fun run() {
            if (peerEndedHandled || terminalStarted || isFinishing || isDestroyed) return
            // No usable call id → checkConnectingDead can't query; bail without rescheduling.
            if (call_Id <= 0) return
            // RING_PEER_GONE_2026_07_24 — receiver ring heartbeat (twin of female side):
            // if this phone is OEM-killed on a ring swipe, the beats stop and the server
            // frees the CALLER in ~15s instead of the 45s fallback. Server ignores it
            // unless ring_peer_gone_reap is enabled.
            com.gmwapp.hima.utils.CallAliveChecker.sendRingHeartbeat(call_Id)
            com.gmwapp.hima.utils.CallAliveChecker.checkConnectingDead(call_Id) {
                if (!peerEndedHandled && !terminalStarted && !isFinishing && !isDestroyed) {
                    Log.d("CreatorCallDiag", "MAccept.alivePoll -> backend says call ended, dismissing ring banner callId=$call_Id")
                    exitBecausePeerEnded()
                }
            }
            aliveHandler.postDelayed(this, aliveIntervalMs)
        }
    }

    private fun startAlivePolling() {
        if (call_Id <= 0) return
        // RING_PEER_GONE_2026_07_24 — beat ONCE immediately so the ':recv' key exists within
        // the first second. Without this the first beat is 2.5s out, and a receiver killed
        // in that window never registers, so the server can't reap → caller stuck.
        com.gmwapp.hima.utils.CallAliveChecker.sendRingHeartbeat(call_Id)
        // removeCallbacks before postDelayed makes this idempotent in both directions.
        aliveHandler.removeCallbacks(alivePollRunnable)
        aliveHandler.postDelayed(alivePollRunnable, aliveIntervalMs)
        // Arm the hard ring cap alongside the poll (idempotent).
        ringTimeoutHandler.removeCallbacks(ringTimeoutRunnable)
        ringTimeoutHandler.postDelayed(ringTimeoutRunnable, ringTimeoutMs)
    }

    private fun stopAlivePolling() {
        aliveHandler.removeCallbacks(alivePollRunnable)
        ringTimeoutHandler.removeCallbacks(ringTimeoutRunnable)
    }

    // TC-HMA-002b (2026-07-18): callee-side HARD ring cap — male port of the fix in
    // FemaleCallAcceptActivity. The alive-poll above tears the ring down only when
    // the backend reports the call ENDED with a non-null end_reason. When the CALLER
    // loses network mid-ring it can send neither the "callDeclined" push NOR its ring
    // heartbeats, so check_call_alive flips alive=false with reason=null (the 30s
    // age-guard) — which the poll deliberately ignores, so a slow-but-legit ring
    // isn't cut at 30s. The result was a ring screen that stayed up and answerable
    // indefinitely (the 45s sound watchdog only silences audio; it never closed this
    // Activity). This backstop finishes the ring screen at 45s — safely AFTER the
    // caller's own 40s give-up, so it can never cut a legitimate ring. No status is
    // posted (a timeout is not a decline); teardown reuses the proven peer-ended path.
    private val ringTimeoutHandler = Handler(Looper.getMainLooper())
    private val ringTimeoutMs = 45_000L
    private val ringTimeoutRunnable = Runnable {
        if (peerEndedHandled || terminalStarted || isFinishing || isDestroyed) return@Runnable
        Log.d(
            "CreatorCallDiag",
            "MAccept.ringTimeout -> ring exceeded ${ringTimeoutMs}ms with no answer/cancel; dismissing stuck ring callId=$call_Id"
        )
        exitBecausePeerEnded()
    }

    /**
     * The caller already ended the call server-side (cancel / not-answered) and we
     * learned it from the liveness poll rather than the FCM relay. Tear the ring
     * banner down the same way a dropped "callDeclined" push would — but do NOT post
     * a "rejected" status (the caller already stamped the terminal state). Idempotent.
     */
    private fun exitBecausePeerEnded() {
        if (peerEndedHandled || terminalStarted) return
        peerEndedHandled = true
        stopAlivePolling()
        try { HimaTelecomManager.endActiveCall(DisconnectCause.REMOTE) } catch (_: Throwable) {}
        BaseApplication.getInstance()?.stopRingtone()
        BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
        BaseApplication.getInstance()?.clearIncomingCall()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // B024: this activity is the call UI; any system heads-up banner is
        // now redundant. Wipe ALL incoming-call notifications (FCM + OneSignal
        // paths) as the FIRST thing we do — before setContentView, Glide,
        // viewmodels, etc. — so the banner+full-screen overlap window shrinks
        // to roughly the FSI->process-start latency instead of ~300ms of
        // onCreate setup. Cleared early on every entry, including cold-start.
        BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
        // FORCE_CLOSE_REJECT parity — claim "live instance" as early as possible
        // so a duplicate surface that recreates this screen supersedes us, and our
        // onDestroy won't reject the newer instance's call. Mirrors FemaleCallAccept.
        liveInstance = this
        // Route the volume rocker to STREAM_RING while this activity is on
        // screen so volume up/down adjusts the incoming ringtone (B027).
        // Without this the default STREAM_MUSIC is targeted and the rocker
        // appears to do nothing while the phone is ringing.
        volumeControlStream = AudioManager.STREAM_RING
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Log.d(
            "HimaIncomingCall",
            "MaleCallAcceptActivity.onCreate flags=${intent.flags} action=${intent.action} keyguardLocked=${km.isKeyguardLocked}"
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
        binding = ActivityMaleCallAcceptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.id?.let { userId = userData?.id}

        // TC_HL_05 — default callType to "audio" when the FCM extra is
        // missing so the icon/label branch below doesn't fall through to
        // the Video header on an audio call. The downstream Accept handler
        // also uses this to pick MaleAudioCallingActivity vs Video.
        callType = intent.getStringExtra("CALL_TYPE")?.takeIf { it.isNotBlank() } ?: "audio"
        receiverId = intent.getIntExtra("SENDER_ID", -1)
        channelName = intent.getStringExtra("CHANNEL_NAME")

        // TC_HL_05 — fallback caller name so the banner never renders blank
        // during the userAvatarViewModel lag window. avatarObservers() will
        // overwrite this once the backend responds.
        // Sanitize: the OneSignal ring path can seed this from the notification title
        // ("X is calling you"), which otherwise flashes on the ring for a split second
        // before getUserAvatar returns the real name. Strip that boilerplate.
        callerName = com.gmwapp.hima.utils.PeerNameUtils
            .sanitizeCallerName(intent.getStringExtra("Caller_NAME"))
            .takeIf { it.isNotBlank() } ?: "Hima Caller"
        callerImage = intent.getStringExtra("Caller_Image").orEmpty()

        Log.d("MaleCallAccept_CallerDetails","Image: $callerImage, Name: $callerName")
        call_Id = intent.getIntExtra("CALL_ID", 0)
        Log.d(
            "VideoCallFlow",
            "MaleAccept.onCreate channel=$channelName callId=$call_Id senderId=$receiverId " +
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

        // Pre-fetch Agora token while the user decides to accept/reject.
        // Only for a real, joinable channel — never the "default_channel"
        // sentinel (a channel-less push) which would just waste a token fetch.
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
                "MaleCallAcceptActivity: stale launch (pendingTag=$pendingTag expected=$expectedTag) -> finish"
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

        binding.callerName.setText(com.gmwapp.hima.utils.DisplayName.clean(callerName))
        // TC_HL_05 — guard against empty image URL so Glide doesn't render
        // an empty white circle, which made the avatar banner look missing.
        // Falls back to avatar1 (existing drawable verified in res/drawable/).
        if (callerImage.isNotBlank()) {
            Glide.with(this)
                .load(callerImage)
                .apply(RequestOptions.circleCropTransform().placeholder(R.drawable.avatar1))
                .into(binding.ivLogo)
        } else {
            binding.ivLogo.setImageResource(R.drawable.avatar1)
        }

        Log.d("MaleCallAccept_CallType","from notification $callType")

        userAvatarViewModel.getUserAvatar(receiverId)
        avatarObservers()
        observeCallRejectCount()

        Log.d("MaleCallAccept_CallID","$call_Id")

        // TC-HMA-002 (male port): start the callee-side liveness poll now that the
        // ring banner is up, so a dropped/delayed caller-cancel push can't leave it
        // ringing forever. Stopped on accept/decline and in onDestroy.
        startAlivePolling()

        binding.accpet.setOnClickListener {
            // B_002 — an OFFLINE device must never enter a call it can never join
            // (Agora can't connect, onJoinChannelSuccess never fires, so the only
            // safety timer never arms and the call hangs "connected" forever).
            // Block BEFORE any state change (terminalStarted / stopAlivePolling) so
            // the ring stays intact and he can retry or decline. isOnline() is
            // lenient + fail-open — see NetworkUtils. Mirrors FemaleCallAcceptActivity.
            if (!com.gmwapp.hima.utils.NetworkUtils.isOnline(this)) {
                Toast.makeText(this, getString(R.string.call_accept_no_network), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            terminalStarted = true      // user is answering — stop the self-heal poll
            stopAlivePolling()
            if (receiverId != -1 && CallChannel.isJoinable(channelName) && !callType.isNullOrEmpty()) {
                // Check if male has enough coins (minimum 10 coins required)
                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                val currentCoins = userData?.coins ?: 0
                
                if (currentCoins < 10) {
                    // TC_HL_05 — Insufficient coins: route to the payment page
                    // (WalletActivity) instead of silently dropping to MainActivity.
                    // The female peer is informed via the "rejected" FCM so her
                    // ringing UI tears down, but the male sees the recharge screen
                    // so he can top up and call back.
                    Log.d("MaleCallAccept", "Insufficient coins: $currentCoins. Routing to WalletActivity.")

                    sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "rejected")

                    HimaTelecomManager.endActiveCall(DisconnectCause.REJECTED)
                    BaseApplication.getInstance()?.stopRingtone()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()

                    Toast.makeText(
                        this,
                        "You don't have enough coins to attend the call. Recharge to call back.",
                        Toast.LENGTH_LONG
                    ).show()

                    val walletIntent = Intent(this@MaleCallAcceptActivity, WalletActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("REASON", "insufficient_coins_incoming_call")
                        putExtra("MIN_COINS_REQUIRED", 10)
                    }
                    startActivity(walletIntent)
                    finish()
                    return@setOnClickListener
                }
                
                // Sufficient coins - proceed with call
                Log.d("MaleCallAccept", "Sufficient coins: $currentCoins. Accepting call.")
                // FORCE_CLOSE_REJECT_2026_07_07 — mark THIS caller's ring accepted so
                // FcmCallService.onTaskRemoved's wasRingAcceptedFor() guard skips the
                // reject if the app is swiped in the brief window before
                // clearIncomingCall() stops the service. Mirrors FemaleCallAccept:365
                // and the two CallActionReceiver accept branches; set only after the
                // coins gate passes (never on the insufficient-coins reject above).
                if (receiverId > 0) BaseApplication.getInstance()?.markRingAccepted(receiverId)
                Log.d(
                    "VideoCallFlow",
                    "MaleAccept.acceptClick channel=$channelName callId=$call_Id callType=$callType " +
                        "tokenPrefetched=${!prefetchedAgoraToken.isNullOrEmpty()} appIdPrefetched=${!prefetchedAgoraAppId.isNullOrEmpty()}"
                )
                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "accepted")

                if (callType == "audio") {
                    BaseApplication.getInstance()?.stopRingtone()
                    HimaTelecomManager.markActive()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    val intent = Intent(this, MaleAudioCallingActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("CALL_ID", call_Id)
                        prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                        prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                        Log.d("MaleCallAccept_RECEIVER_ID","$receiverId")
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }else{
                    BaseApplication.getInstance()?.stopRingtone()
                    HimaTelecomManager.markActive()
                    BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                    BaseApplication.getInstance()?.clearIncomingCall()
                    val intent = Intent(this, MaleVideoCallingActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("RECEIVER_ID", receiverId)
                        putExtra("CALL_ID", call_Id)
                        prefetchedAgoraToken?.let { putExtra("AGORA_TOKEN", it) }
                        prefetchedAgoraAppId?.let { putExtra("AGORA_APP_ID", it) }
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }
            } else {
                // Channel-less push (no real "<id>_<ts>" channel) — never join the
                // shared "default_channel" room (black screen + other callers'
                // audio while the caller waits alone). Tell him and tear down.
                Log.w(
                    "VideoCallFlow",
                    "MaleAccept: refusing accept, unusable channel='$channelName' callId=$call_Id receiver=$receiverId"
                )
                Toast.makeText(
                    this,
                    "Couldn't connect this call. Please ask them to call again.",
                    Toast.LENGTH_LONG
                ).show()
                // UNUSABLE_CHANNEL_LOOP_FIX_2026_07_24 — this attempt can't join, but the
                // caller must still be told or her screen hangs on "Connecting…" forever
                // and the ring keeps re-appearing (the reported loop). Relay "rejected"
                // (same signal as the Decline button) so her connecting UI tears down, and
                // mark this call busy-rejected so a re-delivered push can't re-ring it.
                if (receiverId > 0) {
                    runCatching {
                        sendCallNotification(userId ?: 0, receiverId, callType ?: "audio", channelName ?: "", "rejected")
                    }
                    if (call_Id > 0) BaseApplication.getInstance()?.markCallBusyRejected(call_Id)
                }
                HimaTelecomManager.endActiveCall(DisconnectCause.LOCAL)
                BaseApplication.getInstance()?.stopRingtone()
                BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
                BaseApplication.getInstance()?.clearIncomingCall()
                finish()
            }
        }

        binding.reject.setOnClickListener {
            terminalStarted = true      // user is declining — stop the self-heal poll
            stopAlivePolling()
            if (receiverId != -1 && !channelName.isNullOrEmpty() && !callType.isNullOrEmpty()) {
                // Call reject count API
                userId?.let { maleUserId ->
                    accountViewModel.callRejectCount(maleUserId, receiverId)
                    Log.d("CallStatus", "MaleAccept.reject → rejected/receiver self=$maleUserId peer=$receiverId callId=$call_Id")
                    callStatusViewModel.saveCallStatus(
                        userId = maleUserId,
                        receivedUserId = receiverId,
                        callId = call_Id,
                        endReason = CallEndReason.REJECTED,
                        endedBy = CallEndedBy.RECEIVER,
                        endedByUserId = maleUserId,
                        durationSeconds = 0,
                    )
                    // Match the female receiver path: retain the terminal reject
                    // across an app kill or short network loss. Unique WorkManager
                    // work collapses duplicate enqueue attempts for this call.
                    com.gmwapp.hima.workers.CallStatusWorker.enqueueReject(
                        applicationContext,
                        maleUserId,
                        receiverId,
                        call_Id
                    )
                }

                sendCallNotification(userId!!, receiverId, callType!!, channelName!!, "rejected")

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
                    val intent = Intent(this@MaleCallAcceptActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - prevent back press during incoming call
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
            Log.w("HimaIncomingCall", "MaleCallAcceptActivity.onNewIntent: callId mismatch (have=$call_Id new=$newCallId) — ignoring channel upgrade")
            return
        }
        channelName = newChannel
        if (newCallId != 0) call_Id = newCallId
        intent.getStringExtra("CALL_TYPE")?.takeIf { it.isNotBlank() }?.let { callType = it }
        intent.getIntExtra("SENDER_ID", -1).takeIf { it != -1 }?.let { receiverId = it }
        com.gmwapp.hima.utils.PeerNameUtils.sanitizeCallerName(intent.getStringExtra("Caller_NAME"))
            .takeIf { it.isNotBlank() }?.let { callerName = it }
        intent.getStringExtra("Caller_Image")?.takeIf { it.isNotBlank() }?.let { callerImage = it }
        Log.d("HimaIncomingCall", "MaleCallAcceptActivity.onNewIntent: channel upgraded → $channelName")
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
                        "MaleCallAcceptActivity: AUTO_ACCEPT firing tokenReady=$ready timedOut=$timedOut waitedMs=${System.currentTimeMillis() - startMs}"
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
            Log.d("MaleCallAccept_Avatar", "Image URL: $response")

            if (response != null && response.success) {
                val imageUrl = response.data?.image
                callerName = response.data?.name.toString()
                Log.d("MaleCallAccept_UserAvatar", "Image URL: $imageUrl")

                binding.callerName.setText(com.gmwapp.hima.utils.DisplayName.clean(callerName))
                Glide.with(this)
                    .load(imageUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.ivLogo)
            }
        }

        userAvatarViewModel.userAvatarErrorLiveData.observe(this) { errorMessage ->
            Log.e("MaleCallAccept_AvatarError", errorMessage)
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
                    Log.d("MaleCallAccept_FCM", "Notification sent successfully!")
                } else {
                    Log.e("MaleCallAccept_FCM", "Failed to send notification")
                }
            }
        }
    }

    fun observeCallRejectCount() {
        accountViewModel.callRejectCountLiveData.observe(this) { response ->
            response?.let {
                if (it.success) {
                    Log.d("MaleCallAccept_RejectCount", "Call reject count recorded: ${it.data?.rejecting_count}")
                } else {
                    Log.e("MaleCallAccept_RejectCount", "Failed to record reject count: ${it.message}")
                }
            }
        }

        accountViewModel.callRejectCountErrorLiveData.observe(this) { error ->
            error?.let {
                Log.e("MaleCallAccept_RejectCount", "Error: $it")
            }
        }
    }

    private fun prefetchAgoraToken(channelForToken: String) {
        Log.d("AgoraTiming", "MaleCallAccept prefetchAgoraToken started at ${System.currentTimeMillis()}")
        Log.d("VideoCallFlow", "MaleAccept.prefetchToken.start channel=$channelForToken callId=$call_Id")
        agoraViewModel.agoraTokenLiveData.observe(this) { response ->
            Log.d(
                "VideoCallFlow",
                "MaleAccept.prefetchToken.response success=${response?.success} " +
                    "tokenPresent=${!response?.token.isNullOrEmpty()} appIdPresent=${!response?.app_id.isNullOrEmpty()}"
            )
            if (response != null && response.success == true && !response.token.isNullOrEmpty()) {
                prefetchedAgoraToken = response.token
                prefetchedAgoraAppId = response.app_id
                Log.d("AgoraTiming", "MaleCallAccept prefetchAgoraToken received at ${System.currentTimeMillis()}")
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
            Log.e("MaleCallAccept_Anim", "Error starting pulse animations: ${e.message}")
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

    /**
     * B_007 (male port of FemaleCallAcceptActivity.onUserLeaveHint) — user pressed
     * Home / Recents while this ring is still unanswered. The foreground+unlocked
     * FCM path skipped the tray banner (B030), so once this activity is
     * backgrounded nothing is left in the notification bar and the incoming call is
     * unreachable until the app is reopened. Re-post the silent CallStyle heads-up
     * so the call stays reachable — tap to return, or Accept/Decline from the banner.
     *
     * Guarded to the pristine ringing state: terminalStarted is set the instant
     * Accept OR Decline is tapped, so this never re-posts once the user has acted;
     * peerEndedHandled covers a peer-cancel. Auto-cancelled on return by onResume
     * and by every teardown path's cancelIncomingCallStyleNotification sweep.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (terminalStarted || peerEndedHandled || isFinishing || isDestroyed) return
        if (call_Id <= 0) return
        if (BaseApplication.getInstance()?.isIncomingCall() != true) return
        Log.d("HimaIncomingCall", "MaleAccept.onUserLeaveHint -> re-posting ring banner callId=$call_Id (B_007)")
        com.gmwapp.hima.utils.CallNotifications.repostIncomingForBackground(
            this,
            com.gmwapp.hima.utils.CallNotifications.IncomingPayload(
                isMale = true,
                callType = callType,
                senderId = receiverId,
                callId = call_Id,
                channelName = channelName.orEmpty(),
                callerName = callerName,
                callerImage = callerImage,
            )
        )
    }

    // DUAL_SURFACE_HIDE_2026_07_23 — male twin of the female fix. The full-screen ring is
    // the primary UI; a heads-up popup that races in ~1s later (second push provider
    // delivering the same ring) slips past onCreate/onResume's one-shot cancel. Re-sweep
    // over the first ~2.5s so a late popup is hidden almost immediately. The sweep only
    // matches incoming-call channels, so it can never touch a live in-call notification.
    private val popupHideHandler = Handler(Looper.getMainLooper())
    private val popupHideSweepDelays = longArrayOf(0L, 350L, 800L, 1400L, 2200L)
    private fun startPopupHideSweeps() {
        popupHideHandler.removeCallbacksAndMessages(null)
        for (d in popupHideSweepDelays) {
            popupHideHandler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    BaseApplication.getInstance()?.cancelAllIncomingCallNotifications()
                }
            }, d)
        }
    }

    override fun onResume() {
        super.onResume()
        // B_007: back in the foreground, this activity owns the call presentation
        // again — clear any tray banner re-posted by onUserLeaveHint. Idempotent.
        BaseApplication.getInstance()?.cancelIncomingCallStyleNotification()
        // Catch a popup that arrives a beat AFTER this cancel (cross-provider race).
        startPopupHideSweeps()
    }

    override fun onPause() {
        super.onPause()
        // Stop sweeping the instant we leave the foreground so a scheduled sweep can't
        // wipe the banner onUserLeaveHint deliberately re-posts for the background case.
        popupHideHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        // DUAL_SURFACE_HIDE_2026_07_23 — stop any pending popup-hide sweeps.
        popupHideHandler.removeCallbacksAndMessages(null)
        // FORCE_CLOSE_REJECT parity (male port of FemaleCallAcceptActivity:877) —
        // he left the ring WITHOUT accepting or declining (task swipe / system
        // finish). Treat it as a Decline so the server row is stamped rejected
        // (clears pending so the ring can't be resurrected on reopen) and the
        // caller (female creator) stops ringing. This is the second layer to
        // FcmCallService.onTaskRemoved — and the ONLY layer when the ring is shown
        // with the app foreground+unlocked, where FcmCallService is never started
        // (B030 skip), so without this the MALE ring lingered while the female
        // caller's screen dropped. Guard on isFinishing so a config-change recreate
        // (rotation) does NOT drop a still-live ring.
        if (isFinishing) {
            // Only act if the still-armed incoming call is OURS. If a NEWER call has
            // re-armed the flag with a different tag (pendingTag != our call_Id),
            // leave it — rejecting/clearing would wipe the newer call's live ring.
            val pendingTag = BaseApplication.getInstance()?.getLastIncomingCallTag()
            val ourTag = if (call_Id != 0) call_Id.toString() else null
            if (pendingTag == null || ourTag == null || pendingTag == ourTag) {
                // terminalStarted is set by BOTH Accept and Decline taps (before any
                // teardown), so this fires only for a genuine no-action exit;
                // peerEndedHandled suppresses it when the caller already cancelled
                // (peer-end / ring-timeout already stamped the terminal state).
                // liveInstance === this: a duplicate surface that recreated this
                // screen has NOT superseded us, so this is a genuine abandon.
                if (liveInstance === this &&
                    !terminalStarted && !peerEndedHandled &&
                    call_Id > 0 && receiverId != -1) {
                    val selfId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
                    Log.d("CallStatus", "MaleAccept.onDestroy no-action exit → force-close reject callId=$call_Id")
                    BaseApplication.getInstance()?.rejectCallOnAppClose(
                        selfId, receiverId, call_Id, callType, channelName
                    )
                    // RING_TELECOM_LEAK_2026_07_23 — tear the self-managed Telecom
                    // connection down HERE too. This onDestroy consumes the task-removal
                    // claim, so FcmCallService.onTaskRemoved logs "NOT rejecting" and used
                    // to skip its telecom sweep entirely, leaving the connection
                    // registered. The next incoming FCM then saw maleOnAnotherAppCall==true
                    // and auto-replied "userBusy" WITHOUT ringing — the caller's next call
                    // died ~1s in with "This call has already ended". Mirrors the manual
                    // Decline path, which has always ended the connection. Id-matched, so
                    // it can never disconnect a newer call's connection.
                    com.gmwapp.hima.agora.telecom.HimaTelecomManager.endIncomingCallIfMatches(
                        senderId = receiverId,
                        callId = call_Id,
                        reason = android.telecom.DisconnectCause.REJECTED
                    )
                }
                BaseApplication.getInstance()?.clearIncomingCall()
            }
        }
        stopAlivePolling() // TC-HMA-002 (male port): never leak the ring poll past teardown
        // Stop animations
        try {
            binding.pulseRingOuter.clearAnimation()
            binding.pulseRingMiddle.clearAnimation()
            buttonShakeAnimators.forEach { it.cancel() }
            buttonShakeAnimators.clear()
        } catch (e: Exception) {
            Log.e("MaleCallAccept_Anim", "Error clearing animations: ${e.message}")
        }
        // FORCE_CLOSE_REJECT parity — only relinquish the "live instance" claim if
        // we still hold it; a newer instance that took over must keep its claim so
        // ITS onDestroy can still act.
        if (liveInstance === this) liveInstance = null
    }

}
