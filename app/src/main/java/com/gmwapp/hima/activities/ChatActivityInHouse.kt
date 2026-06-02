package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.toUserMessage

import com.gmwapp.hima.utils.showAppToast

import android.Manifest
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Observer
import androidx.activity.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.ChatAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.models.ChatMessage
import com.gmwapp.hima.models.ChatMessageApi
import com.gmwapp.hima.models.MessageDeliveryStatus
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ChatAttachmentUploadResponse
import com.gmwapp.hima.retrofit.responses.MarkReadResponse
import com.gmwapp.hima.retrofit.responses.MarkMessagesReadResponse
import com.gmwapp.hima.retrofit.responses.MessageListResponse
import com.gmwapp.hima.retrofit.responses.SendMessageResponse
import com.gmwapp.hima.retrofit.responses.ChatHistoryResponse
import com.gmwapp.hima.retrofit.responses.BlockUserResponse
import com.gmwapp.hima.retrofit.responses.CheckCallAvailabilityResponse
import com.gmwapp.hima.retrofit.responses.FallbackSendMessageResponse
import com.gmwapp.hima.retrofit.responses.FriendRequestResponse
import com.gmwapp.hima.retrofit.responses.RegisterResponse
import retrofit2.Call
import retrofit2.Response
import com.gmwapp.hima.socket.ChatMessageSocket
import com.gmwapp.hima.socket.SocketManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import org.json.JSONObject
import kotlin.math.abs
import com.gmwapp.hima.activities.UserProfileDetailActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.utils.CallUnavailableFeedback
import com.gmwapp.hima.utils.AudioRecorderController
import com.gmwapp.hima.utils.ChatHistoryMemoryCache
import com.gmwapp.hima.utils.ClearedChatsPrefsHelper
import com.gmwapp.hima.utils.DeletedChatsPrefsHelper
import com.gmwapp.hima.utils.ImageCompressor
import com.gmwapp.hima.utils.LastSeenFormatter
import com.gmwapp.hima.utils.LocallyDeletedMessagesStore

@AndroidEntryPoint
class ChatActivityInHouse : AppCompatActivity() {

    companion object {
        /** Filter logcat: `adb logcat -s ChatReopenTrace` */
        private const val CHAT_REOPEN_LOG = "ChatReopenTrace"
        private val CHAT_REOPEN_VERBOSE: Boolean = BuildConfig.DEBUG
        private const val STATE_MARKED_READ_ONCE = "state_marked_read_once"
        private const val STATE_LAST_MARKED_READ_ID = "state_last_marked_read_id"
        // T25: composer draft + reply target preserved across rotation.
        private const val STATE_DRAFT_TEXT = "state_draft_text"
        private const val STATE_REPLY_ID = "state_reply_id"
        private const val MAX_MESSAGE_LENGTH = 2000

        /**
         * Skip the resume-time history reload if a populated list is already on
         * screen and the snapshot is younger than this. Live socket events / FCM
         * pushes drive any net-new lines while the chat is open; the resume reload
         * exists for the case where the activity was paused long enough that the
         * cache is stale.
         */
        private const val RESUME_RELOAD_FRESH_WINDOW_MS = 30_000L
    }

    @Inject
    lateinit var apiManager: ApiManager

    @Inject
    lateinit var historyCache: ChatHistoryMemoryCache

    private val femaleUsersViewModel: com.gmwapp.hima.viewmodels.FemaleUsersViewModel by viewModels()
    private val profileViewModel: com.gmwapp.hima.viewmodels.ProfileViewModel by viewModels()
    /**
     * T1: kept so we can detach before each re-attach in [initPeerHeader]. Without
     * this, switching peer via `onNewIntent` re-subscribes a new observer every
     * time and the previous peer's late response would overwrite the new header.
     */
    private var profileObserver: Observer<RegisterResponse>? = null

    private lateinit var rvMessages: RecyclerView
    private var layoutHistoryError: View? = null
    private var tvHistoryError: TextView? = null
    /** CHAT-082: shimmer skeleton shown during first chat_history call when nothing is cached. */
    private var shimmerLoading: com.facebook.shimmer.ShimmerFrameLayout? = null
    private var btnHistoryRetry: View? = null
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnMic: ImageButton
    private lateinit var ivAttach: ImageView
    private lateinit var ivEmoji: ImageView
    private var emojiPicker: EmojiPickerView? = null
    private var messageInputContainer: View? = null
    private var subscribeLockContainer: View? = null
    private var autopayFailedLockContainer: View? = null
    private var btnSubscribeUnlock: View? = null
    private var btnBuyCoinsUnlock: View? = null
    private var chatEndedBanner: View? = null
    private var chatEndedBannerText: android.widget.TextView? = null
    private val autopayViewModel: com.gmwapp.hima.viewmodels.AutopayViewModel by viewModels()
    private lateinit var ivBack: ImageView
    private lateinit var cvBack: CardView
    private lateinit var ivMore: ImageView
    private lateinit var ivUser: CircleImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserStatus: TextView
    private lateinit var vOnlineIndicator: View
    private lateinit var recordingBar: View
    private lateinit var tvRecordingTimer: TextView
    private lateinit var tvRecordingHint: TextView
    private lateinit var vRecordingDot: View

    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private var layoutReplyPreview: View? = null
    private var tvReplyAuthor: TextView? = null
    private var tvReplySnippet: TextView? = null
    private var ivReplyClose: ImageView? = null
    /** Message the user is replying to (quoted in the next outgoing text). */
    private var pendingReplyTo: ChatMessage? = null
    
    private val socketManager = SocketManager.getInstance()
    
    private var myUserId: Int = 0
    private var peerUserId: Int = 0
    private var chatId: String = ""

    /** Tracks last [SocketManager.isConnected] value for false→true reconnect detection. */
    private var previousSocketConnected: Boolean? = null
    
    private var isChatVisible = false

    /**
     * Catch-up fallback when a Socket.IO `new_message` event is missed.
     * The OneSignal NSE broadcasts this when a chat push arrives for the peer
     * whose thread is currently open; we respond by replaying `loadMessages()`,
     * which de-duplicates against the current in-memory window.
     */
    private val chatRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val incomingPeer = intent?.getIntExtra("peer_id", -1) ?: -1
            if (incomingPeer == peerUserId && isChatVisible && myUserId > 0) {
                if (isInitialHistoryLoading) {
                    pendingPostInitialReload = true
                    Log.d("RealtimeChat", "push-refresh peer=$incomingPeer deferred until initial history completes")
                    return
                }
                Log.d("RealtimeChat", "push-refresh broadcast peer=$incomingPeer — replaying loadMessages()")
                loadMessages()
            } else {
                Log.d(
                    "RealtimeChat",
                    "push-refresh broadcast ignored peer=$incomingPeer (current=$peerUserId visible=$isChatVisible myUserId=$myUserId)"
                )
            }
        }
    }
    private var chatRefreshReceiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Delayed status log from [onCreate]; removed in [onDestroy] to avoid posting after activity is gone. */
    private val logSocketStatusAfterDelay = Runnable { logSocketIOStatus() }

    /** False after [onDestroy] / while finishing — use before touching views from async callbacks. */
    private fun isUiSafe(): Boolean {
        if (isFinishing) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            !isDestroyed
        } else {
            true
        }
    }
    
    // Track message sending method
    private val messageSendMethod = mutableMapOf<String, String>() // messageId -> "socket" or "api"

    private data class PendingOutgoingPayload(
        val message: String,
        val messageType: String,
        val attachmentUrl: String?,
        // CHAT-034: keep the recorded duration around so re-sends after a
        // socket reconnect or REST fallback also carry it to the server.
        val audioDurationMs: Long? = null
    )

    private val pendingOutgoingByTempId = LinkedHashMap<String, PendingOutgoingPayload>()
    
    // Store last online status from API
    private var lastOnlineStatus: String? = null
    
    // Track if user is blocked
    private var iHaveBlockedThisUser: Boolean = false
    /** True when the OTHER side blocked the current user — server-sourced from
     *  chat_history.this_user_has_blocked_me. Triggers the dimmed-call /
     *  banner / send-intercept UI on this side. Symmetric with
     *  [iHaveBlockedThisUser]. */
    private var peerHasBlockedMe: Boolean = false
    private var tvBlockedBanner: android.widget.TextView? = null

    // T-CHAT-021: persistent blocked-state banner shown above the composer
    // when the current user has blocked this peer. Mirrors [iHaveBlockedThisUser].
    private var llBlockedBanner: LinearLayout? = null

    /** Peer display name (for add-friend banner / toasts). */
    private var peerName: String = ""

    // Blocks the profile-API avatar fetch from clobbering an avatar that intent extras
    // or ChatNotificationStore already loaded correctly.
    private var headerImageLoaded: Boolean = false

    private var isFriendWithPeer: Boolean = false
    private var isAddFriendBannerDismissedThisSession: Boolean = false

    private var bannerAddFriend: com.google.android.material.card.MaterialCardView? = null
    private var tvBannerAddFriendTitle: TextView? = null
    private var btnBannerNotNow: TextView? = null
    private var btnBannerAcceptFriend: com.google.android.material.button.MaterialButton? = null
    private var isFriendRequestInFlight = false
    private var btnNewMessages: com.google.android.material.button.MaterialButton? = null
    private var unseenIncomingCount = 0
    
    // Call buttons
    private lateinit var callButtonsContainer: View
    private lateinit var cvAudioCall: com.google.android.material.card.MaterialCardView
    private lateinit var cvVideoCall: com.google.android.material.card.MaterialCardView
    private lateinit var ivAudioCall: ImageView
    private lateinit var ivVideoCall: ImageView

    // Per-user call rate banner shown above the messages list
    private var cvRateBanner: com.google.android.material.card.MaterialCardView? = null
    private var tvRateBanner: TextView? = null
    // Cached call rates so the audio/video click handlers can run the
    // coin-balance gate without re-reading intent extras each time.
    // Fall-backs match the rate-banner defaults (10 audio, 60 video).
    private var perMinAudioRate: Int = 10
    private var perMinVideoRate: Int = 60
    private var peerUserGender: String? = null
    private var isPeerUserOnline: Boolean = false
    private var peerAudioStatus: Int? = null  // 0 or 1
    private var peerVideoStatus: Int? = null   // 0 or 1
    private var isCallBlocked: Boolean = false  // true if male user is blocked by female user (blocked = 1 or 2)
    private var callStatusAudioSwitch: com.google.android.material.switchmaterial.SwitchMaterial? = null
    private var callStatusVideoSwitch: com.google.android.material.switchmaterial.SwitchMaterial? = null
    private var isApplyingCallStatusToggleState = false
    
    // Pagination variables
    private var currentOffset = 0
    private var isLoadingMore = false
    private var hasMoreMessages = true
    // CHAT smoothness: 40/page (was 10) → ~4× fewer pagination round-trips, so
    // scrolling up stalls far less often. WhatsApp loads ~50 from a local DB;
    // we still fetch from the server but a bigger page closes most of the gap.
    // 40 < the backend's 100 limit cap; payload is ~20KB (negligible vs RTT).
    private val MESSAGES_PER_PAGE = 40

    // CHAT-084 follow-up: when a tapped reply's original isn't in the loaded
    // window (only 10 load at a time), page older messages in and retry the
    // scroll. These track the in-flight retry across pagination callbacks.
    private var pendingReplyScrollMessageId: String? = null
    private var pendingReplyScrollAttempts = 0
    private val MAX_REPLY_SCROLL_PAGES = 25

    /** Latest wins for overlapping [getChatHistory] calls so an older response cannot replace a newer list. */
    private val historyLoadRequestId = AtomicInteger(0)

    /** In-flight Retrofit calls for chat history — cancelled on destroy or when superseded. */
    private var currentHistoryCall: Call<ChatHistoryResponse>? = null
    private var currentMoreCall: Call<ChatHistoryResponse>? = null
    /**
     * CHAT-082 / CHAT-030: every reset of this flag to `false` is treated as a
     * terminal outcome of the first chat_history load — the shimmer skeleton
     * always vanishes regardless of which exit path (success / error / rate
     * limit / no-network / lifecycle teardown) we took. Avoids the
     * spinner-stuck class of bugs by binding visibility to the source of
     * truth instead of every call site remembering to also call
     * `hideChatLoadingSkeleton()`.
     */
    private var isInitialHistoryLoading: Boolean = false
        set(value) {
            field = value
            if (!value) hideChatLoadingSkeleton()
        }
    private var pendingPostInitialReload = false
    private val paginationLoadRequestId = AtomicInteger(0)
    /** Prevents duplicate mark-read on both [onBackPressed] and [onPause] in one exit. */
    private var markedReadOnce = false
    private var lastMarkedReadMessageId: String? = null
    /** One silent retry after a transient network failure (per [loadMessages] chain). */
    private var historySilentRetryUsed = false

    private val retryHistoryRunnable = Runnable {
        if (!isUiSafe()) return@Runnable
        if (messages.isEmpty()) {
            Log.d(CHAT_REOPEN_LOG, "history RETRY firing peer=$peerUserId (silent retry)")
            loadMessages(isSilentRetry = true)
        } else {
            Log.d(CHAT_REOPEN_LOG, "history RETRY skipped peer=$peerUserId (messages already loaded)")
        }
    }

    /** Cancels a delayed initial history fetch when a new [loadMessages] supersedes it. */
    private var pendingThrottleHistoryRunnable: Runnable? = null

    private var activityCreatedAtElapsed: Long = 0L

    /**
     * Skip one history reload on the first [onResume] after [onCreate] ([loadMessages] already runs there).
     * Later resumes (returning from another screen / app background) refresh history so messages reappear.
     */
    private var suppressNextResumeHistoryReload = true
    private val audioRecorderController by lazy { AudioRecorderController(cacheDir) }
    private var recordingPulseAnimator: ObjectAnimator? = null
    private var recordingStartY: Float = 0f
    private var recordingStartX: Float = 0f
    private var isRecording: Boolean = false
    private var clearingTextDuringRecording = false
    private var cancelRecordingOnRelease = false
    private var recordingStartedAtMs: Long = 0L
    private val cancelRecordingThresholdPx by lazy { 72f * resources.displayMetrics.density }
    private val activeAttachmentTempIds = mutableSetOf<String>()
    private val activeAttachmentCalls = mutableMapOf<String, Call<*>>()
    private val activeTextSendCalls = mutableListOf<Call<*>>()
    
    // T19: explicit local timezone — server sends timestamps in device wall-clock
    // (yyyy-MM-dd HH:mm:ss with no Z), so we parse/format in the device's locale.
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    private val recordingTicker = object : Runnable {
        override fun run() {
            if (!audioRecorderController.isRecording()) return
            val elapsed = (SystemClock.elapsedRealtime() - recordingStartedAtMs).coerceAtLeast(0L)
            tvRecordingTimer.text = formatElapsedTime(elapsed)
            mainHandler.postDelayed(this, 100L)
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            handlePickedImage(selectedUri)
        }
    }

    private val imagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchPhotoPicker()
        } else {
            showAppToast("Image permission is required to pick a photo", Toast.LENGTH_SHORT)
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showAppToast("Hold the mic again to record a voice note", Toast.LENGTH_SHORT)
        } else {
            showAppToast("Microphone permission is required to record audio", Toast.LENGTH_SHORT)
        }
    }

    private val audioCallEnablePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            ?: return@registerForActivityResult
        if (granted) {
            femaleUsersViewModel.updateCallStatus(userData.id, DConstants.AUDIO, 1)
            isApplyingCallStatusToggleState = true
            callStatusAudioSwitch?.isChecked = true
            isApplyingCallStatusToggleState = false
        } else {
            startActivity(Intent(this, GrantPermissionsActivity::class.java))
        }
    }

    /**
     * CHAT-047: result from [FullscreenImageActivity]. The viewer can return
     * RESULT_OK with an action extra (Reply / React) — we route that back
     * into the in-thread flow by finding the message by id and calling the
     * existing handlers, so the viewer stays a dumb display surface.
     */
    private val fullscreenImageResultLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val action = data.getStringExtra(FullscreenImageActivity.EXTRA_RESULT_ACTION)
            ?: return@registerForActivityResult
        val msgId = data.getStringExtra(FullscreenImageActivity.EXTRA_MESSAGE_ID)
            ?: return@registerForActivityResult
        val idx = messages.indexOfFirst { !it.isDateHeader && it.id == msgId }
        if (idx < 0) return@registerForActivityResult
        val msg = messages[idx]
        when (action) {
            FullscreenImageActivity.ACTION_REPLY -> beginReplyTo(msg)
            FullscreenImageActivity.ACTION_REACT -> {
                rvMessages.post {
                    val holder = rvMessages.findViewHolderForAdapterPosition(idx)
                    val anchor = holder?.itemView ?: rvMessages
                    chatAdapter.showReactionPopupForPosition(anchor, idx)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_chat)

        markedReadOnce = savedInstanceState?.getBoolean(STATE_MARKED_READ_ONCE) ?: false
        lastMarkedReadMessageId = savedInstanceState?.getString(STATE_LAST_MARKED_READ_ID)

        initializeViews()
        if (!setupUserIds()) return
        setupRecyclerView()
        // Defensive: bind the cache to the current signed-in user. If the previous
        // login on this device wasn't fully torn down, this drops any stale entries
        // before we read them below.
        if (myUserId > 0) {
            historyCache.setOwner(myUserId)
        }
        initPeerHeader()
        initAddFriendBanner()
        setupClickListeners()
        setupComposer()
        // T-CHAT-021: seed blocked-state from the local prefs cache so the
        // banner + disabled composer render immediately on entry, without
        // waiting for chat-history to come back with `iHaveBlockedThisUser`.
        iHaveBlockedThisUser = com.gmwapp.hima.utils.BlockedPeersPrefsHelper
            .isBlocked(this, peerUserId.toString())
        applyBlockedUiState()
        applySubscriptionGate()
        connectSocket()
        suppressNextResumeHistoryReload = true
        activityCreatedAtElapsed = SystemClock.elapsedRealtime()
        Log.d(
            CHAT_REOPEN_LOG,
            "LIFECYCLE onCreate peer=$peerUserId chatId=$chatId taskId=$taskId instance=${hashCode()} " +
                "cacheAvailable=${historyCache.hasSnapshot(peerUserId)} rateLimitRemainMs=${historyCache.cooldownRemainMs(peerUserId)}"
        )
        loadMessages()
        observeSocketEvents()
        setupCallStatusObservers()
        setupCallButtons()
        setupCallButtonListeners()

        // T25: restore composer state — draft text immediately; reply target after
        // [loadMessages] succeeds (the original message is needed for `beginReplyTo`).
        // CHAT-108: prefer the savedInstanceState bundle (fresher; covers active
        // unsaved typing through a config change); fall back to the persistent
        // SharedPrefs draft so cold restarts / chat switches also restore.
        val bundleDraft = savedInstanceState?.getString(STATE_DRAFT_TEXT)
        val draftToApply = if (!bundleDraft.isNullOrEmpty()) {
            bundleDraft
        } else if (peerUserId > 0) {
            com.gmwapp.hima.utils.ChatDraftStore.get(this, peerUserId)
        } else ""
        if (draftToApply.isNotEmpty()) {
            etMessage.setText(draftToApply)
            etMessage.setSelection(draftToApply.length.coerceAtMost(etMessage.text?.length ?: 0))
        }
        pendingRestoreReplyId = savedInstanceState?.getString(STATE_REPLY_ID)

        // Log initial status (runnable cleared in onDestroy)
        mainHandler.postDelayed(logSocketStatusAfterDelay, 3000)
    }

    /** T25: deferred reply target restoration; applied once history fills the list. */
    private var pendingRestoreReplyId: String? = null

    private fun maybeApplyPendingRestoreReply() {
        val id = pendingRestoreReplyId ?: return
        val msg = messages.firstOrNull { !it.isDateHeader && it.id == id } ?: return
        beginReplyTo(msg)
        pendingRestoreReplyId = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val oldPeerId = peerUserId
        setIntent(intent)
        if (!setupUserIds()) return
        if (oldPeerId == peerUserId) {
            // T3: same peer — still re-render the header so a push that arrived
            // with updated USER_NAME / USER_IMAGE replaces stale extras. Skip the
            // expensive socket/history work which is already correct.
            initPeerHeader()
            return
        }

        resetConversationStateForPeerChange()
        if (myUserId > 0) {
            historyCache.setOwner(myUserId)
        }
        initPeerHeader()
        initAddFriendBanner()
        connectSocket()
        loadMessages()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_MARKED_READ_ONCE, markedReadOnce)
        outState.putString(STATE_LAST_MARKED_READ_ID, lastMarkedReadMessageId)
        // T25: persist the in-progress composer + reply target so a rotation
        // (or backgrounded process restore) doesn't drop the user's draft.
        outState.putString(STATE_DRAFT_TEXT, etMessage.text?.toString())
        outState.putString(STATE_REPLY_ID, pendingReplyTo?.id)
        super.onSaveInstanceState(outState)
    }

    private fun initializeViews() {
        rvMessages = findViewById(R.id.rv_messages)
        layoutReplyPreview = findViewById(R.id.layout_reply_preview)
        tvReplyAuthor = findViewById(R.id.tv_reply_author)
        tvReplySnippet = findViewById(R.id.tv_reply_snippet)
        ivReplyClose = findViewById(R.id.iv_reply_close)
        ivReplyClose?.setOnClickListener {
            pendingReplyTo = null
            updateReplyPreviewUi()
        }
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        btnMic = findViewById(R.id.btn_mic)
        ivAttach = findViewById(R.id.iv_attach)
        ivEmoji = findViewById(R.id.iv_emoji)
        emojiPicker = findViewById(R.id.emoji_picker)
        llBlockedBanner = findViewById(R.id.ll_blocked_banner)
        tvBlockedBanner = findViewById(R.id.tv_blocked_banner)
        messageInputContainer = findViewById(R.id.message_input_container)
        subscribeLockContainer = findViewById(R.id.subscribe_lock_container)
        autopayFailedLockContainer = findViewById(R.id.autopay_failed_lock_container)
        chatEndedBanner = findViewById(R.id.ll_chat_ended_banner)
        chatEndedBannerText = findViewById(R.id.tv_chat_ended_banner_text)
        btnSubscribeUnlock = findViewById(R.id.btn_subscribe_unlock)
        btnSubscribeUnlock?.setOnClickListener { showTrialOfferSheet() }
        btnBuyCoinsUnlock = findViewById(R.id.btn_buy_coins_unlock)
        btnBuyCoinsUnlock?.setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
        }
        observeAutopayPushEvents()
        ivBack = findViewById(R.id.iv_back)
        cvBack = findViewById(R.id.cv_back)
        ivMore = findViewById(R.id.iv_more)
        ivUser = findViewById(R.id.iv_user)
        tvUserName = findViewById(R.id.tv_user_name)
        tvUserStatus = findViewById(R.id.tv_user_status)
        vOnlineIndicator = findViewById(R.id.v_online_indicator)
        recordingBar = findViewById(R.id.recording_bar)
        tvRecordingTimer = findViewById(R.id.tv_recording_timer)
        tvRecordingHint = findViewById(R.id.tv_recording_hint)
        vRecordingDot = findViewById(R.id.v_recording_dot)
        btnNewMessages = findViewById(R.id.btn_new_messages)
        btnNewMessages?.setOnClickListener {
            scrollToBottomAndClearNewMessagePill()
        }
        
        // Initialize call buttons
        callButtonsContainer = findViewById(R.id.call_buttons_container)
        cvAudioCall = findViewById(R.id.cv_audio_call)
        ivAudioCall = findViewById(R.id.iv_audio_call)
        cvVideoCall = findViewById(R.id.cv_video_call)
        ivVideoCall = findViewById(R.id.iv_video_call)

        // Per-user call rate banner (shown when home opens chat with rate extras)
        cvRateBanner = findViewById(R.id.cv_rate_banner)
        tvRateBanner = findViewById(R.id.tv_rate_banner)
        val audioRate = intent.getIntExtra("COIN_PER_MIN_AUDIO", -1)
        val videoRate = intent.getIntExtra("COIN_PER_MIN_VIDEO", -1)
        perMinAudioRate = if (audioRate > 0) audioRate else 10
        perMinVideoRate = if (videoRate > 0) videoRate else 60
        if (audioRate > 0 || videoRate > 0) {
            tvRateBanner?.text = getString(
                R.string.rate_per_min_audio_video,
                perMinAudioRate,
                perMinVideoRate
            )
            cvRateBanner?.visibility = View.VISIBLE
        } else {
            cvRateBanner?.visibility = View.GONE
        }

        // Header (name + avatar) populated in [initPeerHeader], called after
        // [setupUserIds] so peerUserId is available for the store / profile-API fallback.

        // Configure UI based on user gender
        // Always show back button for all users with consistent avatar margin
        cvBack.visibility = View.VISIBLE
        val layoutParams = ivUser.layoutParams as android.view.ViewGroup.MarginLayoutParams
        val marginInPx = (12 * resources.displayMetrics.density).toInt()
        layoutParams.marginStart = marginInPx
        ivUser.layoutParams = layoutParams

        // App is light-only — force a white status bar with dark icons regardless
        // of the system uiMode. Removes the conditional dark-mode handling that
        // T17 introduced.
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        WindowInsetsControllerCompat(window, findViewById(R.id.main)).isAppearanceLightStatusBars = true

        bannerAddFriend = findViewById(R.id.banner_add_friend)
        tvBannerAddFriendTitle = findViewById(R.id.tv_banner_add_friend_title)
        btnBannerNotNow = findViewById(R.id.btn_banner_not_now)
        btnBannerAcceptFriend = findViewById(R.id.btn_banner_accept_friend)

        layoutHistoryError = findViewById(R.id.layout_history_error)
        tvHistoryError = findViewById(R.id.tv_history_error)
        btnHistoryRetry = findViewById(R.id.btn_history_retry)
        btnHistoryRetry?.setOnClickListener {
            Log.d(CHAT_REOPEN_LOG, "UI USER_RETRY tap peer=$peerUserId fromState=EMPTY_STATE")
            hideHistoryErrorUi("USER_RETRY")
            loadMessages(userRetry = true)
        }
        shimmerLoading = findViewById(R.id.shimmer_chat_loading)
    }

    private fun hideHistoryErrorUi(reason: String) {
        layoutHistoryError?.visibility = View.GONE
        if (CHAT_REOPEN_VERBOSE) {
            Log.d(CHAT_REOPEN_LOG, "UI EMPTY_STATE hidden peer=$peerUserId reason=$reason")
        }
    }

    private fun showHistoryErrorUi(reason: String, code: Int, userMessage: String? = null) {
        if (!isUiSafe()) return
        layoutHistoryError?.visibility = View.VISIBLE
        tvHistoryError?.text = userMessage ?: getString(R.string.chat_history_error_generic)
        Log.w(CHAT_REOPEN_LOG, "UI EMPTY_STATE shown peer=$peerUserId reason=$reason code=$code")
    }

    /**
     * CHAT-082: show the shimmer skeleton when the first chat_history call is
     * in flight and there's nothing already on screen. Caller is responsible
     * for guarding on `messages.isEmpty()` since the cache-hydrate path can
     * fill the list before this fires.
     */
    private fun showChatLoadingSkeleton() {
        val v = shimmerLoading ?: return
        if (v.visibility != View.VISIBLE) {
            v.visibility = View.VISIBLE
            v.startShimmer()
        }
    }

    /**
     * CHAT-082 / CHAT-030: hide the shimmer skeleton on every terminal
     * outcome of the initial history load (success, error, no-network, cache
     * hydrate). Idempotent.
     */
    private fun hideChatLoadingSkeleton() {
        val v = shimmerLoading ?: return
        if (v.visibility != View.GONE) {
            v.stopShimmer()
            v.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            messages = messages,
            enableReactions = true,
            myUserId = myUserId,
            onReactionChanged = { message, reactionEmoji -> handleReactionUpdate(message, reactionEmoji) },
            onReactionClick = { message, emoji -> showReactionDetails(message, emoji) },
            onMessageLongPress = { anchor, msg, pos -> showChatMessageContextMenu(anchor, msg, pos) },
            onReplyQuoteTap = { msg -> scrollToInlineReplyOriginal(msg) },
            onImageBubbleTap = { msg -> openFullscreenImageViewer(msg) }
        )
        rvMessages.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            // WhatsApp-style layout: messages anchored to bottom, newest at bottom
            // Messages are sorted oldest first (index 0 = oldest, index N = newest)
            // With stackFromEnd=true, RecyclerView shows the last item (newest) at bottom
            // With reverseLayout=false, it keeps the order: oldest at top, newest at bottom
            val layoutManager = LinearLayoutManager(this@ChatActivityInHouse).apply {
                stackFromEnd = true  // Anchor to bottom - shows last item (index N = newest) at bottom
                reverseLayout = false  // Don't reverse - keeps chronological order
            }
            this.layoutManager = layoutManager
            adapter = chatAdapter
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = RecyclerView.FOCUS_BLOCK_DESCENDANTS
            
            // Add scroll listener for pagination (load older messages when scrolling up)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                    if (layoutManager != null) {
                        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                        
                        // Load more when user scrolls toward the top (older messages).
                        // Prefetch earlier (within 12 items, was 5) so the next page is
                        // usually already in by the time the user reaches the top — kills
                        // the "scroll up, stop, wait for load" stutter.
                        if (firstVisiblePosition <= 12 && dy < 0 && !isLoadingMore && hasMoreMessages) {
                            Log.d("ChatPagination", "🔄 Scroll detected - Loading more messages. First visible: $firstVisiblePosition")
                            loadMoreMessages()
                        }
                        if (isRecyclerNearBottom()) {
                            clearNewMessagePill()
                        }
                    }
                }
            })
        }
        attachSwipeToReply()
    }

    private fun attachSwipeToReply() {
        // CHAT-091: allow BOTH directions in the constructor so we can route
        // sent rows to START (left-swipe — bubble has room to slide away from
        // the right edge) and received rows to END (right-swipe — bubble
        // slides toward the center, away from the left edge). The previous
        // END-only handler silently fired for sent bubbles too, but the row
        // could not visually translate so the user never saw any motion.
        val replyIcon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_reply)
        val replyIconSizePx = (24f * resources.displayMetrics.density).toInt()
        val replyIconPaddingPx = (16f * resources.displayMetrics.density).toInt()
        val replyIconTint = androidx.core.content.ContextCompat.getColor(this, R.color.colorAccent)

        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.START or ItemTouchHelper.END
            ) {
                // CHAT-091 follow-up v4: fire the reply once per gesture when the
                // drag passes the trigger distance, NOT on swipe-complete. Reset
                // when the row springs back to rest.
                private var replyFiredThisGesture = false

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ) = false

                override fun getSwipeDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    val pos = viewHolder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return 0
                    val msg = messages.getOrNull(pos)
                    if (msg == null || msg.isDateHeader || msg.isDeleted || isPendingMessage(msg)) return 0
                    // CHAT-091: sent bubbles swipe LEFT (START), received swipe RIGHT (END).
                    return if (msg.isSentByMe) ItemTouchHelper.START else ItemTouchHelper.END
                }

                // CHAT-091 follow-up v4: the row must NEVER complete the swipe —
                // a completed swipe is a "dismiss", which left the bubble
                // animated off-screen (only the reply icon remained, the
                // message appeared to vanish). Returning a threshold > 1 and a
                // huge escape velocity guarantees ItemTouchHelper always springs
                // the row back to rest, so the bubble can't disappear. The reply
                // is triggered mid-drag in onChildDraw instead.
                override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 10f

                override fun getSwipeEscapeVelocity(defaultValue: Float): Float =
                    defaultValue * 100f

                // Never fires now (threshold is unreachable) — kept because
                // SimpleCallback declares it abstract.
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                /**
                 * CHAT-091: WhatsApp-style fade-in reply-icon hint anchored to
                 * the row edge **opposite** the swipe direction. Also the place
                 * we trigger the reply: once |dX| passes ~30% of the row width
                 * the reply preview opens (once per gesture); the row then
                 * springs back on release without ever dismissing.
                 */
                override fun onChildDraw(
                    c: android.graphics.Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                        val pos = viewHolder.bindingAdapterPosition
                        val msg = if (pos != RecyclerView.NO_POSITION) messages.getOrNull(pos) else null
                        if (msg != null && !msg.isDateHeader && !msg.isDeleted && !isPendingMessage(msg)) {
                            val itemView = viewHolder.itemView

                            // Draw the fade-in reply icon.
                            if (replyIcon != null) {
                                val iconThreshold = itemView.width / 4f
                                val progress = (kotlin.math.abs(dX) / iconThreshold).coerceIn(0f, 1f)
                                val centerY = itemView.top + itemView.height / 2
                                val iconTop = centerY - replyIconSizePx / 2
                                val iconLeft = if (msg.isSentByMe) {
                                    itemView.right - replyIconPaddingPx - replyIconSizePx
                                } else {
                                    itemView.left + replyIconPaddingPx
                                }
                                replyIcon.setBounds(
                                    iconLeft, iconTop,
                                    iconLeft + replyIconSizePx, iconTop + replyIconSizePx
                                )
                                replyIcon.alpha = (progress * 255f).toInt()
                                androidx.core.graphics.drawable.DrawableCompat.setTint(replyIcon, replyIconTint)
                                replyIcon.draw(c)
                            }

                            // Trigger the reply once when the drag passes 30% of
                            // the row width, while the finger is still down.
                            val triggerDist = itemView.width * 0.30f
                            if (!replyFiredThisGesture &&
                                isCurrentlyActive &&
                                kotlin.math.abs(dX) >= triggerDist
                            ) {
                                replyFiredThisGesture = true
                                beginReplyToWithoutKeyboard(msg)
                                viewHolder.itemView.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.LONG_PRESS
                                )
                            }
                            // Reset for the next gesture once the row is back at rest.
                            if (kotlin.math.abs(dX) < 1f) {
                                replyFiredThisGesture = false
                            }
                        }
                    }
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                }
            }
        ).attachToRecyclerView(rvMessages)
    }

    private fun updateReplyPreviewUi() {
        if (!isUiSafe()) return
        val ref = pendingReplyTo
        val layout = layoutReplyPreview ?: return
        if (ref == null) {
            layout.visibility = View.GONE
            tvReplyAuthor?.text = ""
            tvReplySnippet?.text = ""
            tvReplySnippet?.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            return
        }
        layout.visibility = View.VISIBLE
        tvReplyAuthor?.text = if (ref.isSentByMe) {
            getString(R.string.chat_reply_you)
        } else {
            peerName
        }
        // CHAT-090: voice notes get a small mic compound drawable + duration
        // ("Voice note · 0:14") instead of the generic "🎤 Voice message"
        // placeholder, so the user can tell which voice note they're replying to.
        val isAudio = ref.messageType.equals("audio", ignoreCase = true)
        val snippet = tvReplySnippet
        if (snippet != null && isAudio) {
            val durTxt = com.gmwapp.hima.utils.formatAudioReplyDuration(ref.audioDurationMs)
            snippet.text = if (durTxt.isNotEmpty()) {
                getString(R.string.chat_reply_voice_with_duration, durTxt)
            } else {
                getString(R.string.chat_reply_voice_no_duration)
            }
            val mic = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_mic)?.mutate()
            mic?.setTint(androidx.core.content.ContextCompat.getColor(this, R.color.colorAccent))
            snippet.setCompoundDrawablesRelativeWithIntrinsicBounds(mic, null, null, null)
            snippet.compoundDrawablePadding = (4f * resources.displayMetrics.density).toInt()
        } else {
            snippet?.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            snippet?.text = buildReplySnippetText(ref)
        }
    }

    private fun buildReplySnippetText(msg: ChatMessage): String = when (msg.messageType.lowercase()) {
        "image" -> getString(R.string.chat_preview_photo)
        // CHAT-090: persist the prefix + duration so the peer's in-bubble reply
        // quote can detect "this snippet refers to a voice note" without having
        // the original message's audioDurationMs in scope. Falls back to the
        // bare emoji when duration is unknown.
        "audio" -> {
            val durTxt = com.gmwapp.hima.utils.formatAudioReplyDuration(msg.audioDurationMs)
            if (durTxt.isNotEmpty()) {
                "${com.gmwapp.hima.utils.AUDIO_REPLY_SNIPPET_PREFIX}$durTxt"
            } else {
                getString(R.string.chat_preview_voice)
            }
        }
        else -> msg.message.trim().replace("\n", " ").take(160)
    }

    private fun buildReplyHeaderLine(ref: ChatMessage): String {
        val author = if (ref.isSentByMe) getString(R.string.chat_reply_you) else peerName
        val snippet = buildReplySnippetText(ref)
        return getString(R.string.chat_reply_header_line, author, snippet)
    }

    private fun isPendingMessage(message: ChatMessage): Boolean =
        message.id.startsWith("temp_") ||
            message.deliveryStatus == MessageDeliveryStatus.SENDING

    private fun parseInlineReplySnippet(raw: String): String? {
        val firstLineEnd = raw.indexOf('\n')
        if (firstLineEnd <= 0) return null
        val header = raw.substring(0, firstLineEnd)
        val separator = header.indexOf(": ")
        if (separator <= 0) return null
        return header.substring(separator + 2).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * CHAT-084: tap on a reply bubble's quote strip — scroll the list to the
     * original message and briefly flash its row so the user can tell which
     * message is being referenced.
     *
     * The inline-reply payload only carries author + snippet (no original
     * message id), so we match by snippet. To avoid picking the wrong "ok" /
     * "yes" we scan **backwards** from the reply's own position — a reply can
     * only refer to a message that came before it.
     */
    private fun scrollToInlineReplyOriginal(message: ChatMessage) {
        val snippet = parseInlineReplySnippet(message.message) ?: return
        // A fresh tap on a different reply resets the paging-retry counter.
        if (pendingReplyScrollMessageId != message.id) {
            pendingReplyScrollAttempts = 0
        }
        val replyIndex = messages.indexOfFirst { it.id == message.id }
        if (replyIndex < 0) {
            clearPendingReplyScroll()
            android.widget.Toast.makeText(
                this,
                R.string.chat_reply_original_not_loaded,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        // CHAT-084 follow-up: media-type replies carry placeholder snippets
        // ("🎤 0:14", "📷 Photo", "Voice message" etc.) — the original media
        // bubble has an empty `message` field, so text-contains never matches
        // and the user always saw "Original message not loaded". Detect the
        // media-type snippets up front and match by messageType instead.
        val targetMessageType: String? = detectReplyTargetMessageType(snippet)

        var targetIndex = -1
        for (i in (replyIndex - 1) downTo 0) {
            val c = messages[i]
            if (c.isDateHeader || c.isDeleted) continue
            val matched = if (targetMessageType != null) {
                c.messageType.equals(targetMessageType, ignoreCase = true)
            } else {
                c.message.contains(snippet, ignoreCase = true)
            }
            if (matched) {
                targetIndex = i
                break
            }
        }
        if (targetIndex == -1) {
            // Original isn't in the loaded window yet. Only 10 messages load at
            // a time, so older originals are commonly just not paged in. Load
            // older pages and retry (bounded) before giving up — WhatsApp does
            // the same "jump to quoted message" load.
            if (hasMoreMessages && !isLoadingMore &&
                pendingReplyScrollAttempts < MAX_REPLY_SCROLL_PAGES
            ) {
                pendingReplyScrollMessageId = message.id
                pendingReplyScrollAttempts++
                loadMoreMessages()
                return
            }
            clearPendingReplyScroll()
            android.widget.Toast.makeText(
                this,
                R.string.chat_reply_original_not_loaded,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        clearPendingReplyScroll()
        rvMessages.smoothScrollToPosition(targetIndex)
        scheduleReplyFlash(targetIndex)
    }

    private fun clearPendingReplyScroll() {
        pendingReplyScrollMessageId = null
        pendingReplyScrollAttempts = 0
    }

    /**
     * Called after each pagination page lands. If a reply-tap is waiting for its
     * original to be paged in, re-run the search now that more messages exist.
     */
    private fun maybeRetryPendingReplyScroll() {
        val id = pendingReplyScrollMessageId ?: return
        val msg = messages.firstOrNull { !it.isDateHeader && it.id == id }
        if (msg == null) {
            // The reply row itself fell out of the window — give up cleanly.
            clearPendingReplyScroll()
            return
        }
        scrollToInlineReplyOriginal(msg)
    }

    /**
     * CHAT-084 follow-up: classify a reply-quote snippet as a media-type
     * placeholder so [scrollToInlineReplyOriginal] can match against
     * messageType instead of message body (which is empty for media bubbles).
     * Returns the messageType to match, or null if the snippet is plain text
     * and should fall back to text-contains matching.
     */
    private fun detectReplyTargetMessageType(snippet: String): String? {
        val s = snippet.trim()
        if (s.startsWith(com.gmwapp.hima.utils.AUDIO_REPLY_SNIPPET_PREFIX) ||
            s.equals("Voice note", ignoreCase = true) ||
            s.startsWith("Voice note ", ignoreCase = true) ||
            s == getString(R.string.chat_preview_voice) ||
            s == getString(R.string.chat_reply_voice_no_duration)
        ) {
            return "audio"
        }
        if (s == getString(R.string.chat_preview_photo) ||
            s.startsWith("📷", ignoreCase = true) ||
            s.equals("Photo", ignoreCase = true)
        ) {
            return "image"
        }
        return null
    }

    /**
     * CHAT-084: run the flash as soon as the target row is on screen and the
     * RecyclerView has settled. Three paths covered:
     *  1) Target already visible — animate immediately.
     *  2) Scroll in flight — listen for SCROLL_STATE_IDLE, then animate.
     *  3) Safety timeout — if neither fires within 1500ms (e.g. row was already
     *     visible so smoothScroll was a no-op and never produced an IDLE event),
     *     try one more lookup and animate if found.
     */
    private fun scheduleReplyFlash(index: Int) {
        val immediate = rvMessages.findViewHolderForAdapterPosition(index)?.itemView
        if (immediate != null) {
            animateReplyFlash(immediate)
            return
        }
        val listener = object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(
                rv: androidx.recyclerview.widget.RecyclerView,
                newState: Int
            ) {
                if (newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) return
                rv.removeOnScrollListener(this)
                rv.findViewHolderForAdapterPosition(index)?.itemView?.let { animateReplyFlash(it) }
            }
        }
        rvMessages.addOnScrollListener(listener)
        rvMessages.postDelayed({
            rvMessages.removeOnScrollListener(listener)
            if (replyFlashAnimator?.isRunning != true) {
                rvMessages.findViewHolderForAdapterPosition(index)?.itemView
                    ?.let { animateReplyFlash(it) }
            }
        }, 1500L)
    }

    private var replyFlashAnimator: android.animation.ValueAnimator? = null

    /**
     * CHAT-084: 1000ms flash on a row — 200ms fade-in (alpha 0→64/255), 500ms
     * hold, 300ms fade-out. Uses [View.foreground] so we don't touch the
     * bubble drawable and don't need per-holder layout changes. Alpha caps at
     * 64 (~25%) so the bubble text stays readable through the tint.
     */
    private fun animateReplyFlash(row: android.view.View) {
        replyFlashAnimator?.cancel()
        val flashColor = androidx.core.content.ContextCompat.getColor(this, R.color.chat_reply_flash)
        val fg = android.graphics.drawable.ColorDrawable(flashColor).apply { alpha = 0 }
        row.foreground = fg
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            addUpdateListener { v ->
                val frac = v.animatedValue as Float
                val intensity = when {
                    frac < 0.2f -> frac / 0.2f
                    frac < 0.7f -> 1f
                    else -> (1f - frac) / 0.3f
                }
                fg.alpha = (intensity * 64f).toInt().coerceIn(0, 64)
                row.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    row.foreground = null
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    row.foreground = null
                }
            })
        }
        replyFlashAnimator = anim
        anim.start()
    }

    private fun beginReplyTo(message: ChatMessage) {
        if (message.isDateHeader || message.isDeleted) return
        if (isPendingMessage(message)) return
        pendingReplyTo = message
        updateReplyPreviewUi()
        etMessage.requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * CHAT-091 follow-up v3: swipe-triggered reply that does NOT auto-open
     * the IME. The reply preview opens (small bar at the bottom) but the
     * keyboard stays closed, so the RecyclerView doesn't lose ~300dp of
     * height to the IME and the swiped message remains visible. User taps
     * the message input when they want to start typing — same UX as iOS
     * WhatsApp and the simplest way to make the "messages disappeared
     * after swipe" complaint go away across every device timing.
     */
    private fun beginReplyToWithoutKeyboard(message: ChatMessage) {
        if (message.isDateHeader || message.isDeleted) return
        if (isPendingMessage(message)) return
        pendingReplyTo = message
        updateReplyPreviewUi()
    }

    /**
     * CHAT-091 follow-up v4: scroll the swiped message into the visible
     * area after a swipe-to-reply. Simpler approach than v2/v3 — just call
     * smoothScrollToPosition with the bottom of the visible area as the
     * target, which forces the LayoutManager to bring the row into view
     * regardless of stackFromEnd anchoring.
     *
     * Earlier versions used scrollToPositionWithOffset with a 30%-height
     * offset, but on some devices the LayoutManager kept re-anchoring to
     * the bottom after our scroll ran, leaving the swiped row off the top.
     * smoothScrollToPosition gives the LayoutManager an explicit "make
     * this row visible" command that survives the anchor pass.
     *
     * Captures the message ID (not the index) so a concurrent socket
     * insert / delete doesn't make us scroll to the wrong row.
     */
    private fun keepSwipedMessageVisible(swipedPos: Int) {
        if (swipedPos < 0 || swipedPos !in messages.indices) return
        val swipedId = messages[swipedPos].id
        Log.d("ChatSwipeReply", "keepSwipedMessageVisible swipedPos=$swipedPos swipedId=$swipedId messages=${messages.size}")

        fun doScroll() {
            if (!isUiSafe()) return
            val idx = messages.indexOfFirst { it.id == swipedId }
            if (idx < 0) {
                Log.d("ChatSwipeReply", "doScroll: swipedId=$swipedId not found in messages")
                return
            }
            // smoothScrollToPosition forces the LayoutManager to bring the
            // row into view — survives stackFromEnd's bottom-anchor logic.
            rvMessages.smoothScrollToPosition(idx)
            Log.d("ChatSwipeReply", "doScroll: smoothScroll to idx=$idx")
        }

        // Two-stage: first immediate post (catches the reply-preview layout pass),
        // then a 400ms postDelayed (catches any later re-anchoring).
        rvMessages.post { doScroll() }
        rvMessages.postDelayed({ doScroll() }, 400L)
    }

    private fun showChatMessageContextMenu(anchor: View, message: ChatMessage, position: Int) {
        if (!isUiSafe()) return
        if (message.isDateHeader) return
        // Once deleted there's nothing to reply to, react to, or re-delete.
        if (message.isDeleted) return
        // Bug 10: FAILED outgoing bubbles get a context menu (Retry / Delete);
        // still skip SENDING optimistic rows that haven't either succeeded
        // or failed yet.
        val isFailed = message.isSentByMe &&
            message.deliveryStatus == MessageDeliveryStatus.FAILED
        if (isPendingMessage(message) && !isFailed) return
        val popup = PopupMenu(this, anchor, Gravity.END)
        menuInflater.inflate(R.menu.menu_chat_message, popup.menu)
        // Retry — only for FAILED outgoing bubbles.
        popup.menu.findItem(R.id.action_retry_send)?.isVisible = isFailed
        // Reply / React don't make sense for a message that didn't reach
        // the server.
        popup.menu.findItem(R.id.action_reply)?.isVisible = !isFailed
        popup.menu.findItem(R.id.action_reaction)?.isVisible = !isFailed
        // Delete-for-everyone is only offered to the sender of the message
        // and only AFTER the server has acked it (delete needs a real id).
        popup.menu.findItem(R.id.action_delete)?.isVisible = message.isSentByMe && !isFailed
        // Delete-for-me works for any non-pending message (sent or received).
        // For FAILED rows, "delete" means "abandon this attempt" — just
        // drop the temp from the list, no server call.
        popup.menu.findItem(R.id.action_delete_for_me)?.isVisible = !isPendingMessage(message) || isFailed
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_retry_send -> {
                    retryFailedMessage(message.id)
                    true
                }
                R.id.action_reply -> {
                    beginReplyTo(message)
                    true
                }
                R.id.action_reaction -> {
                    chatAdapter.showReactionPopupForPosition(anchor, position)
                    true
                }
                R.id.action_delete -> {
                    confirmDeleteMessage(message)
                    true
                }
                R.id.action_delete_for_me -> {
                    if (isFailed) {
                        // Drop the failed temp row entirely; nothing to
                        // tell the server about.
                        removeTempMessage(message.id)
                    } else {
                        confirmDeleteForMe(message)
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * CHAT-138: local-only hide. Server is never told; peer's view is unaffected.
     * Persisted in [LocallyDeletedMessagesStore] so the hide survives chat reloads.
     */
    private fun confirmDeleteForMe(message: ChatMessage) {
        if (!isUiSafe()) return
        if (message.isDateHeader) return
        if (isPendingMessage(message)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_delete_for_me_title)
            .setMessage(R.string.chat_delete_for_me_message)
            .setPositiveButton(R.string.chat_delete_confirm) { _, _ ->
                performDeleteForMe(message)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performDeleteForMe(message: ChatMessage) {
        if (!isUiSafe()) return
        LocallyDeletedMessagesStore.add(this, myUserId, peerUserId, message.id)
        // Reply-target safety: if the row I'm about to remove is the active
        // reply anchor, clear the composer state so the reply preview doesn't
        // point at a vanished message.
        if (pendingReplyTo?.id == message.id) {
            pendingReplyTo = null
            updateReplyPreviewUi()
        }
        val index = messages.indexOfFirst { it.id == message.id && !it.isDateHeader }
        if (index != -1) {
            messages.removeAt(index)
            chatAdapter.notifyItemRemoved(index)
        }
    }

    private fun confirmDeleteMessage(message: ChatMessage) {
        if (!isUiSafe()) return
        // Defensive: the menu item is already hidden for received rows, but guard here too.
        if (!message.isSentByMe || message.isDeleted) return
        if (isPendingMessage(message)) {
            showAppToast(getString(R.string.chat_wait_until_sent_before_deleting), Toast.LENGTH_SHORT)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_delete_title)
            .setMessage(R.string.chat_delete_message)
            .setPositiveButton(R.string.chat_delete_confirm) { _, _ ->
                performDeleteForEveryone(message)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Optimistically flips the bubble to a tombstone, then tries to propagate the delete
     * via Socket.IO; if the socket is down it falls back to REST. On REST failure the
     * local state is rolled back so the user can retry. `onNoNetwork` deliberately keeps
     * the tombstone on-screen — once the server ships the feature, a reconciliation pass
     * on reconnect/reload can surface any rows the server rejected.
     */
    private fun performDeleteForEveryone(message: ChatMessage) {
        if (!isUiSafe()) return
        if (!message.isSentByMe) return
        if (message.isDeleted) return
        if (isPendingMessage(message)) {
            showAppToast(getString(R.string.chat_wait_until_sent_before_deleting), Toast.LENGTH_SHORT)
            return
        }

        val idForLog = message.id
        // Keep the ORIGINAL so the ack/error/timeout paths can roll the tombstone
        // back if the server never actually deleted it.
        pendingDeleteOriginals[message.id] = message
        markMessageDeletedLocally(message)
        if (pendingReplyTo?.id == message.id) {
            pendingReplyTo = null
            updateReplyPreviewUi()
        }

        // BUG (delete-for-everyone consistency): a successful socket *emit* is NOT proof
        // the server persisted the delete or notified the peer — the old code trusted it
        // and returned, so a dropped/rejected delete left the peer still showing the
        // message while the sender showed a tombstone. Now we emit and WAIT for the
        // server's `message_delete_ack`; if it doesn't arrive in time (or `delete_error`
        // fires), we fall back to REST (which persists via Laravel). The tombstone is only
        // permanent once the server confirms.
        val emitted = socketManager.deleteMessage(myUserId, peerUserId, message.id)
        Log.d("ChatDelete", "performDeleteForEveryone id=$idForLog via=socket emitted=$emitted")
        if (emitted) {
            scheduleDeleteAckTimeout(message)
        } else {
            deleteForEveryoneViaRest(message)
        }
    }

    /** Fires the REST delete (persists via Laravel) and reconciles the tombstone on the result. */
    private fun deleteForEveryoneViaRest(original: ChatMessage) {
        val id = original.id
        apiManager.deleteChatMessage(
            myUserId,
            peerUserId,
            id,
            object : NetworkCallback<com.gmwapp.hima.retrofit.responses.SimpleAckResponse> {
                override fun onResponse(
                    call: Call<com.gmwapp.hima.retrofit.responses.SimpleAckResponse>,
                    response: Response<com.gmwapp.hima.retrofit.responses.SimpleAckResponse>
                ) {
                    val ok = response.isSuccessful && (response.body()?.success != false)
                    Log.d("ChatDelete", "deleteForEveryone id=$id via=rest ok=$ok http=${response.code()}")
                    pendingDeleteOriginals.remove(id)
                    if (ok) {
                        if (isUiSafe()) showAppToast(getString(R.string.chat_message_deleted_toast), Toast.LENGTH_SHORT)
                    } else {
                        rollbackDeleteOnFailure(original)
                    }
                }

                override fun onFailure(
                    call: Call<com.gmwapp.hima.retrofit.responses.SimpleAckResponse>,
                    t: Throwable
                ) {
                    Log.w("ChatDelete", "deleteForEveryone id=$id via=rest FAILED: ${t.message}")
                    pendingDeleteOriginals.remove(id)
                    rollbackDeleteOnFailure(original)
                }

                override fun onNoNetwork() {
                    // Offline: keep the tombstone and the pending original so a later
                    // reconnect/reload can reconcile. Don't roll back — the user's intent
                    // is recorded locally.
                    Log.w("ChatDelete", "deleteForEveryone id=$id via=rest NO_NETWORK — tombstone stays")
                }
            }
        )
    }

    private fun scheduleDeleteAckTimeout(original: ChatMessage) {
        cancelDeleteAckTimeout(original.id)
        val r = Runnable {
            pendingDeleteTimeouts.remove(original.id)
            Log.w("ChatDelete", "no delete ack for id=${original.id} within ${DELETE_ACK_TIMEOUT_MS}ms — REST fallback")
            // REST delete is idempotent server-side, so a slow-but-eventual socket ack
            // racing this is harmless (the row is already is_deleted=1).
            deleteForEveryoneViaRest(original)
        }
        pendingDeleteTimeouts[original.id] = r
        mainHandler.postDelayed(r, DELETE_ACK_TIMEOUT_MS)
    }

    private fun cancelDeleteAckTimeout(messageId: String) {
        pendingDeleteTimeouts.remove(messageId)?.let { mainHandler.removeCallbacks(it) }
    }

    private fun markMessageDeletedLocally(message: ChatMessage) {
        val index = messages.indexOfFirst { it.id == message.id && !it.isDateHeader }
        if (index == -1) return
        val current = messages[index]
        if (current.isDeleted) return
        messages[index] = current.copy(
            isDeleted = true,
            reactions = emptyMap(),
            attachmentUrl = null
        )
        chatAdapter.notifyItemChanged(index)
    }

    private fun rollbackDeleteOnFailure(message: ChatMessage) {
        if (!isUiSafe()) return
        val index = messages.indexOfFirst { it.id == message.id && !it.isDateHeader }
        if (index != -1 && messages[index].isDeleted) {
            messages[index] = message
            chatAdapter.notifyItemChanged(index)
        }
        showAppToast(getString(R.string.chat_delete_failed), Toast.LENGTH_SHORT)
    }

    private fun setupUserIds(): Boolean {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        myUserId = userData?.id ?: 0
        peerUserId = intent.getIntExtra("USER_ID", -1)

        if (myUserId == 0 || peerUserId == -1) {
            showAppToast("Error: Invalid user data", Toast.LENGTH_SHORT)
            finish()
            return false
        }

        // Generate chat ID: smaller_id_larger_id
        chatId = if (myUserId < peerUserId) {
            "${myUserId}_${peerUserId}"
        } else {
            "${peerUserId}_${myUserId}"
        }

        Log.d("ChatActivityInHouse", "MyUserId: $myUserId, PeerUserId: $peerUserId, ChatId: $chatId")

        // T11: write to prefs as well so the NSE sees the active peer even in a separate process.
        com.gmwapp.hima.utils.ActiveChatTracker.setActive(this, peerUserId)
        // Opening this conversation clears any stacked chat-notification lines
        // for this peer (WhatsApp parity) and removes the tray entry so it
        // doesn't linger behind the now-open chat.
        com.gmwapp.hima.utils.ChatNotificationStore.clear(this, peerUserId)
        androidx.core.app.NotificationManagerCompat.from(this)
            .cancel(com.gmwapp.hima.utils.ChatNotifications.notifIdFor(peerUserId))
        return true
    }

    /**
     * Populate [tvUserName] and [ivUser] with a best-effort answer in three layers:
     *   1. Intent extras (cheapest — already in-process when the chat opens from FriendsTab / Home).
     *   2. [ChatNotificationStore] — last-known name/image captured by the OneSignal NSE.
     *      Covers the "tap push" path where extras can be blank because the payload from
     *      [BaseApplication] click handler / [ChatNotifications] PendingIntent is incomplete.
     *   3. `get_user` API — final fallback when neither above produced a name or avatar.
     *
     * Runs after [setupUserIds] so [peerUserId] is populated before the store/API lookups.
     */
    private fun initPeerHeader() {
        val extrasName = intent.getStringExtra("USER_NAME")
            ?.let { extractNameOnly(it) }
            ?.takeIf { it.isNotBlank() && it != "User" }
        val extrasImage = intent.getStringExtra("USER_IMAGE")?.takeIf { it.isNotBlank() }

        var resolvedName = extrasName
        var resolvedImage = extrasImage

        if ((resolvedName == null || resolvedImage == null) && peerUserId > 0) {
            val (storeName, storeImage) =
                com.gmwapp.hima.utils.ChatNotificationStore.getMeta(this, peerUserId)
            if (resolvedName == null) {
                val cleaned = extractNameOnly(storeName).takeIf { it.isNotBlank() && it != "User" }
                if (cleaned != null) resolvedName = cleaned
            }
            if (resolvedImage == null && !storeImage.isNullOrBlank()) {
                resolvedImage = storeImage
            }
        }

        peerName = resolvedName ?: "User"
        tvUserName.text = peerName

        if (!resolvedImage.isNullOrBlank()) {
            headerImageLoaded = true
            Glide.with(this)
                .load(resolvedImage)
                .apply(RequestOptions.circleCropTransform())
                .into(ivUser)
        }

        val needsPeerProfileFetch = resolvedName == null || resolvedImage == null
        if (needsPeerProfileFetch && peerUserId > 0) {
            // T1: detach the old observer before re-subscribing so a stale response
            // for the previous peer can't overwrite the freshly-set header on a
            // peer switch via `onNewIntent`.
            profileObserver?.let { profileViewModel.getUserLiveData.removeObserver(it) }
            val observer = Observer<RegisterResponse> { response ->
                val data = response?.data ?: return@Observer
                val fetchedName = extractNameOnly(data.name)
                    .takeIf { it.isNotBlank() && it != "User" }
                if (fetchedName != null && (peerName.isBlank() || peerName == "User")) {
                    peerName = fetchedName
                    tvUserName.text = fetchedName
                }
                val fetchedImage = data.image.takeIf { it.isNotBlank() }
                if (fetchedImage != null && !headerImageLoaded) {
                    headerImageLoaded = true
                    Glide.with(this)
                        .load(fetchedImage)
                        .apply(RequestOptions.circleCropTransform())
                        .into(ivUser)
                }
            }
            profileObserver = observer
            profileViewModel.getUserLiveData.observe(this, observer)
            profileViewModel.getUsers(peerUserId)
        }
    }

    private fun initAddFriendBanner() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val isMaleUser = userData?.gender?.equals(DConstants.MALE, ignoreCase = true) == true
        if (isMaleUser) {
            bannerAddFriend?.visibility = View.GONE
            return
        }
        tvBannerAddFriendTitle?.text = getString(R.string.chat_add_friend_banner_title, peerName)
        btnBannerNotNow?.setOnClickListener {
            isAddFriendBannerDismissedThisSession = true
            bannerAddFriend?.visibility = View.GONE
        }
        btnBannerAcceptFriend?.setOnClickListener { acceptAsFriend() }
        refreshFriendState()
    }

    private fun refreshFriendState() {
        val me = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        val peer = peerUserId.takeIf { it > 0 } ?: return
        apiManager.checkFriendRequest(peer, me, me, object : NetworkCallback<FriendRequestResponse> {
            override fun onResponse(call: Call<FriendRequestResponse>, response: Response<FriendRequestResponse>) {
                val body = response.body()
                isFriendWithPeer = response.isSuccessful &&
                    body?.success == true &&
                    body.message == "You are friends"
                updateAddFriendUi()
            }

            override fun onFailure(call: Call<FriendRequestResponse>, t: Throwable) {
                Log.w("ChatFriends", "refreshFriendState failed: ${t.message}")
            }

            override fun onNoNetwork() {
                Log.w("ChatFriends", "refreshFriendState skipped: no network")
            }
        })
    }

    private fun updateAddFriendUi() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val isMaleUser = userData?.gender?.equals(DConstants.MALE, ignoreCase = true) == true
        if (isMaleUser) {
            bannerAddFriend?.visibility = View.GONE
            return
        }
        val showBannerAndMenu = !isFriendWithPeer
        bannerAddFriend?.visibility =
            if (showBannerAndMenu && !isAddFriendBannerDismissedThisSession) View.VISIBLE else View.GONE
    }

    private fun acceptAsFriend() {
        if (isFriendRequestInFlight) return
        val me = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        val peer = peerUserId.takeIf { it > 0 } ?: return
        isFriendRequestInFlight = true
        btnBannerAcceptFriend?.isEnabled = false
        apiManager.sendFriendRequest(peer, me, 1, object : NetworkCallback<FriendRequestResponse> {
            override fun onResponse(call: Call<FriendRequestResponse>, response: Response<FriendRequestResponse>) {
                isFriendRequestInFlight = false
                btnBannerAcceptFriend?.isEnabled = true
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    isFriendWithPeer = true
                    showAppToast(getString(R.string.chat_add_friend_success, peerName), Toast.LENGTH_SHORT)
                } else {
                    showAppToast(getString(R.string.chat_add_friend_failure), Toast.LENGTH_SHORT)
                }
                updateAddFriendUi()
            }

            override fun onFailure(call: Call<FriendRequestResponse>, t: Throwable) {
                isFriendRequestInFlight = false
                btnBannerAcceptFriend?.isEnabled = true
                showAppToast(getString(R.string.chat_add_friend_failure), Toast.LENGTH_SHORT)
            }

            override fun onNoNetwork() {
                isFriendRequestInFlight = false
                btnBannerAcceptFriend?.isEnabled = true
                showAppToast(getString(R.string.chat_add_friend_failure), Toast.LENGTH_SHORT)
            }
        })
    }

    private fun handleReactionUpdate(message: ChatMessage, reactionEmoji: String?) {
        if (message.isDateHeader) return
        // T23: don't allow reactions on optimistic temp rows — server doesn't know
        // about the temp id yet, so the call would 404.
        if (message.id.startsWith("temp_")) return

        // T23: parse as Long (paired with T4) so snowflake ids round-trip correctly,
        // then narrow to Int for the existing socket/api signatures.
        val messageIdLong = message.id.toLongOrNull() ?: return
        val messageId = messageIdLong.toInt()
        
        // Send reaction to server via Socket.IO (with API fallback)
        if (socketManager.isConnected()) {
            socketManager.sendReaction(myUserId, messageId, reactionEmoji)
            Log.d("ChatReactions", "📤 Sending reaction via Socket.IO - Message: $messageId, Reaction: $reactionEmoji")
        } else {
            // Fallback to API
            apiManager.addMessageReaction(
                userId = myUserId,
                messageId = messageId,
                reactionEmoji = reactionEmoji,
                object : NetworkCallback<com.gmwapp.hima.retrofit.responses.AddReactionResponse> {
                    override fun onResponse(
                        call: Call<com.gmwapp.hima.retrofit.responses.AddReactionResponse>,
                        response: Response<com.gmwapp.hima.retrofit.responses.AddReactionResponse>
                    ) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            val data = response.body()?.data
                            if (data != null) {
                                // Update local message with reactions
                                updateMessageReactions(messageId.toString(), data.allReactions)
                            }
                        }
                    }

                    override fun onFailure(
                        call: Call<com.gmwapp.hima.retrofit.responses.AddReactionResponse>,
                        t: Throwable
                    ) {
                        Log.e("ChatReactions", "Failed to send reaction via API: ${t.message}")
                    }

                    override fun onNoNetwork() {
                        Log.e("ChatReactions", "No network connection")
                    }
                }
            )
            Log.w("ChatReactions", "⚠️ Socket.IO not connected - Using API fallback")
        }
    }
    
    private fun updateMessageReactions(messageId: String, reactions: List<com.gmwapp.hima.models.MessageReaction>?) {
        val messageIndex = messages.indexOfFirst { it.id == messageId }
        if (messageIndex != -1) {
            val reactionsMap = reactions?.associate { it.userId to it.reactionEmoji } ?: emptyMap()
            // M18: ChatMessage.reactions is val — copy() instead of mutating in place.
            messages[messageIndex] = messages[messageIndex].copy(reactions = reactionsMap)
            chatAdapter.notifyItemChanged(messageIndex)
        }
    }

    private fun setupClickListeners() {
        cvBack.setOnClickListener {
            onBackPressed()
        }

        ivMore.setOnClickListener {
            showOptionsMenu()
        }

        // CHAT-099: tap anywhere on the header user strip (avatar + name +
        // online status) opens the profile — not just the small 42dp avatar.
        findViewById<View>(R.id.ll_header_user)?.setOnClickListener {
            openUserProfile()
        }
    }

    /**
     * T-CHAT-021: drive the persistent blocked-state UI from
     * [iHaveBlockedThisUser]. Renders the banner above the composer and
     * disables every send affordance so the user can't compose a message
     * that will only fail at send time. Also persists the state into the
     * local prefs cache and broadcasts a list refresh so the chat list
     * Blocked badge updates without an app restart.
     */
    private fun applyBlockedUiState() {
        // EITHER direction blocked → disable composer + show banner. Banner
        // copy + input hint differ so the user knows which way the block goes
        // (their own to undo, or the peer's, which they can't undo).
        val blocked = iHaveBlockedThisUser || peerHasBlockedMe
        llBlockedBanner?.visibility = if (blocked) View.VISIBLE else View.GONE
        tvBlockedBanner?.setText(
            when {
                iHaveBlockedThisUser -> R.string.chat_blocked_banner
                peerHasBlockedMe -> R.string.chat_blocked_by_peer_banner
                else -> R.string.chat_blocked_banner
            }
        )

        // Composer affordances — keep them visible but inert so the layout
        // doesn't reflow on every block/unblock. The banner above explains why.
        etMessage.isEnabled = !blocked
        etMessage.isFocusable = !blocked
        etMessage.isFocusableInTouchMode = !blocked
        etMessage.hint = when {
            iHaveBlockedThisUser -> getString(R.string.chat_blocked_input_hint)
            peerHasBlockedMe -> getString(R.string.chat_blocked_by_peer_input_hint)
            else -> getString(R.string.chat_input_hint)
        }
        if (blocked) {
            etMessage.setText("")
            // Drop the keyboard if it was open over the now-disabled field.
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(etMessage.windowToken, 0)
            // ...and the emoji panel — you can't compose into a blocked thread.
            hideEmojiPicker(showKeyboard = false)
        }
        btnSend.isEnabled = !blocked
        btnMic.isEnabled = !blocked
        ivAttach.isEnabled = !blocked
        val composerAlpha = if (blocked) 0.5f else 1.0f
        etMessage.alpha = composerAlpha
        btnSend.alpha = composerAlpha
        btnMic.alpha = composerAlpha
        ivAttach.alpha = composerAlpha
        if (::ivEmoji.isInitialized) {
            ivEmoji.isEnabled = !blocked
            ivEmoji.alpha = composerAlpha
        }

        // Dim the call buttons in the header so the blocked side gets a
        // visual signal (matches WhatsApp). The click listeners themselves
        // already toast — the alpha just makes the disabled state legible.
        // Only dim if call cards are inflated (they may not be on the
        // female-side layout variant).
        if (::cvAudioCall.isInitialized) cvAudioCall.alpha = if (blocked) 0.4f else 1.0f
        if (::cvVideoCall.isInitialized) cvVideoCall.alpha = if (blocked) 0.4f else 1.0f

        // Block-aware presence: hide the online/last-seen indicator the
        // instant the block toggle lands (rather than waiting on the
        // chat_history reload below to come back with last_online=null).
        // Symmetric on unblock — restoring presence then waits on the
        // chat_history response so we don't surface a stale local value.
        if (blocked) {
            tvUserStatus.text = ""
            tvUserStatus.visibility = View.GONE
            vOnlineIndicator.visibility = View.GONE
        }

        // Mirror into local cache + tell every chat-list listener to re-bind.
        // Only mirror the I-blocked-them direction into BlockedPeersPrefsHelper
        // — the peer-blocked-me state isn't ours to persist (it'd lie about
        // who initiated the block if the user later checks the prefs cache).
        if (peerUserId > 0) {
            com.gmwapp.hima.utils.BlockedPeersPrefsHelper
                .setBlocked(this, peerUserId.toString(), iHaveBlockedThisUser)
            val refresh = android.content.Intent(
                com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_LIST_REFRESH
            ).setPackage(packageName)
            sendBroadcast(refresh)
        }
    }

    private fun setupComposer() {
        btnSend.setOnClickListener {
            sendMessage()
        }

        ivAttach.setOnClickListener {
            showAttachmentBottomSheet()
        }

        setupEmojiPicker()

        btnMic.setOnTouchListener { _, event ->
            handleMicTouch(event)
        }

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (isRecording && !clearingTextDuringRecording && !s.isNullOrEmpty()) {
                    clearingTextDuringRecording = true
                    s.clear()
                    clearingTextDuringRecording = false
                    return
                }
                updateComposerActionState()
                // Bug 8: emit typing=true while user is actively typing, then
                // auto-emit typing=false after 2.5s of inactivity. Empty
                // composer clears immediately. Server fans out `user_typing`
                // to the peer in this chat room so their header shows
                // "Typing..." in real time.
                emitComposerTyping(!s.isNullOrEmpty())
            }
        })

        updateComposerActionState()
    }

    /**
     * TC_CH_003: in-app emoji picker. The composer smiley toggles the panel and
     * picking an emoji inserts it at the cursor. The picker and the soft
     * keyboard are mutually exclusive — opening one dismisses the other — so the
     * composer never fights itself for the bottom of the screen.
     */
    private fun setupEmojiPicker() {
        val picker = emojiPicker ?: return

        picker.setOnEmojiPickedListener { item ->
            insertIntoComposer(item.emoji)
        }

        ivEmoji.setOnClickListener {
            if (picker.visibility == View.VISIBLE) {
                hideEmojiPicker(showKeyboard = true)
            } else {
                showEmojiPicker()
            }
        }

        // Tapping the input to type should always reclaim the bottom area.
        etMessage.setOnClickListener {
            if (picker.visibility == View.VISIBLE) hideEmojiPicker(showKeyboard = true)
        }
    }

    private fun showEmojiPicker() {
        val picker = emojiPicker ?: return
        if (!etMessage.isEnabled) return
        // Drop the keyboard first so the panel doesn't stack above it.
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(etMessage.windowToken, 0)
        etMessage.requestFocus()
        picker.visibility = View.VISIBLE
    }

    private fun hideEmojiPicker(showKeyboard: Boolean) {
        val picker = emojiPicker ?: return
        if (picker.visibility != View.VISIBLE) return
        picker.visibility = View.GONE
        if (showKeyboard) {
            etMessage.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun insertIntoComposer(emoji: String) {
        val editable = etMessage.text ?: return
        val start = etMessage.selectionStart.coerceIn(0, editable.length)
        val end = etMessage.selectionEnd.coerceIn(0, editable.length)
        editable.replace(minOf(start, end), maxOf(start, end), emoji)
    }

    private var isCurrentlyEmittingTyping = false
    private var typingStopRunnable: Runnable? = null

    private fun emitComposerTyping(hasText: Boolean) {
        if (myUserId <= 0 || peerUserId <= 0 || chatId.isBlank()) return
        if (iHaveBlockedThisUser || peerHasBlockedMe) return
        typingStopRunnable?.let { mainHandler.removeCallbacks(it) }
        typingStopRunnable = null
        if (hasText) {
            if (!isCurrentlyEmittingTyping) {
                socketManager.sendTyping(chatId, true)
                isCurrentlyEmittingTyping = true
            }
            // Auto-stop 2.5s after the last keystroke — covers the case
            // where the user stops typing without sending.
            val stop = Runnable {
                if (isCurrentlyEmittingTyping) {
                    socketManager.sendTyping(chatId, false)
                    isCurrentlyEmittingTyping = false
                }
            }
            typingStopRunnable = stop
            mainHandler.postDelayed(stop, 2500L)
        } else {
            if (isCurrentlyEmittingTyping) {
                socketManager.sendTyping(chatId, false)
                isCurrentlyEmittingTyping = false
            }
        }
    }

    private fun openUserProfile() {
        val intent = Intent(this, UserProfileDetailActivity::class.java).apply {
            putExtra(DConstants.USER_ID, peerUserId)
            putExtra("USER_NAME", intent.getStringExtra("USER_NAME") ?: "User")
            putExtra("USER_IMAGE", intent.getStringExtra("USER_IMAGE") ?: "")
            putExtra("USER_LANGUAGE", intent.getStringExtra("USER_LANGUAGE") ?: "")
            putExtra("USER_INTERESTS", intent.getStringExtra("USER_INTERESTS") ?: "")
            putExtra("USER_ABOUT", intent.getStringExtra("USER_ABOUT") ?: "")
            putExtra("USER_AGE", 0)
            putExtra("AUDIO_STATUS", peerAudioStatus ?: 0)
            putExtra("VIDEO_STATUS", peerVideoStatus ?: 0)
        }
        startActivity(intent)
    }

    /**
     * CHAT-047: launch the WhatsApp-style image viewer for an image bubble.
     * Sender label is "You" for outgoing messages and the peer name otherwise;
     * the timestamp is formatted into the same human shape the WhatsApp header
     * uses ("22 May, 4:34 pm") so the chrome reads natural.
     */
    private fun openFullscreenImageViewer(message: ChatMessage) {
        val url = message.attachmentUrl?.takeIf { it.isNotBlank() } ?: return
        val senderLabel = if (message.isSentByMe) {
            getString(R.string.chat_reply_you)
        } else {
            peerName.takeIf { it.isNotBlank() } ?: getString(R.string.chat_reply_you)
        }
        val avatar = if (message.isSentByMe) {
            ""
        } else {
            intent.getStringExtra("USER_IMAGE").orEmpty()
        }
        val tsLabel = message.date?.let { dt ->
            java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()).format(dt)
        } ?: message.timestamp
        fullscreenImageResultLauncher.launch(
            FullscreenImageActivity.intent(
                context = this,
                imageUrl = url,
                peerName = senderLabel,
                peerAvatar = avatar,
                timestamp = tsLabel,
                messageId = message.id,
            )
        )
    }

    private fun updateComposerActionState() {
        if (isRecording) {
            btnMic.visibility = View.VISIBLE
            btnSend.visibility = View.GONE
            return
        }
        val hasText = etMessage.text?.toString()?.trim().orEmpty().isNotEmpty()
        btnSend.visibility = if (hasText) View.VISIBLE else View.GONE
        btnMic.visibility = if (hasText) View.GONE else View.VISIBLE
    }

    private fun showAttachmentBottomSheet() {
        if (!canSendMediaPayload()) return

        val sheet = BottomSheetDialog(this)
        val contentView = LayoutInflater.from(this).inflate(R.layout.bottomsheet_chat_attach, null)
        sheet.setContentView(contentView)

        contentView.findViewById<View>(R.id.row_photo)?.setOnClickListener {
            sheet.dismiss()
            requestPhotoAccessAndOpenPicker()
        }
        contentView.findViewById<View>(R.id.row_camera)?.setOnClickListener {
            sheet.dismiss()
            showAppToast(getString(R.string.chat_camera_coming_soon_toast), Toast.LENGTH_SHORT)
        }
        sheet.show()
    }

    private fun requestPhotoAccessAndOpenPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launchPhotoPicker()
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchPhotoPicker()
        } else {
            imagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun launchPhotoPicker() {
        imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /** T44: guard so a rapid second pick doesn't spawn two optimistic rows / uploads. */
    private var isPreparingImage = false

    private fun handlePickedImage(uri: Uri) {
        if (!canSendMediaPayload()) return
        if (isPreparingImage) return
        isPreparingImage = true

        lifecycleScope.launch {
            try {
                val compressedFile = withContext(Dispatchers.IO) {
                    ImageCompressor.compress(this@ChatActivityInHouse, uri)
                }
                val tempId = addOptimisticMediaMessage(
                    messageType = "image",
                    localAttachmentUrl = compressedFile.toURI().toString()
                )
                uploadAndSendAttachment(tempId, compressedFile, "image")
            } catch (e: Exception) {
                Log.e("ChatMedia", "Image prepare failed: ${e.message}", e)
                showAppToast(e.toUserMessage("Couldn't prepare image"), Toast.LENGTH_SHORT)
            } finally {
                isPreparingImage = false
            }
        }
    }

    private fun canSendMediaPayload(): Boolean {
        if (iHaveBlockedThisUser) {
            showAppToast("Please unblock to send message", Toast.LENGTH_SHORT)
            return false
        }
        if (myUserId <= 0 || peerUserId <= 0) {
            showAppToast("Chat isn't ready yet. Please try again.", Toast.LENGTH_SHORT)
            return false
        }
        return true
    }

    private fun handleMicTouch(event: MotionEvent): Boolean {
        if (etMessage.text?.toString()?.trim().orEmpty().isNotEmpty()) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!canSendMediaPayload()) return true
                // T10: a second ACTION_DOWN while we're already recording would
                // call AudioRecorderController.start() (which begins with cancel())
                // and silently throw away the in-progress clip. Ignore it.
                if (audioRecorderController.isRecording()) return true
                if (!hasRecordAudioPermission()) {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return true
                }
                startAudioRecording(event.rawX, event.rawY)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!audioRecorderController.isRecording()) return true
                val dx = abs(event.rawX - recordingStartX)
                val dy = recordingStartY - event.rawY
                cancelRecordingOnRelease = dy > cancelRecordingThresholdPx &&
                    dx < cancelRecordingThresholdPx / 2f
                tvRecordingHint.text = if (cancelRecordingOnRelease) {
                    getString(R.string.chat_recording_release_cancel)
                } else {
                    getString(R.string.chat_recording_slide_cancel)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!audioRecorderController.isRecording()) return true
                if (cancelRecordingOnRelease) {
                    cancelAudioRecording(showToast = true)
                } else {
                    stopAudioRecordingAndSend()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (audioRecorderController.isRecording()) {
                    cancelAudioRecording(showToast = true)
                }
                return true
            }
        }
        return false
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startAudioRecording(touchStartX: Float, touchStartY: Float) {
        try {
            audioRecorderController.start()
            isRecording = true
            recordingStartX = touchStartX
            recordingStartY = touchStartY
            cancelRecordingOnRelease = false
            recordingStartedAtMs = SystemClock.elapsedRealtime()
            tvRecordingTimer.text = getString(R.string.chat_recording_timer_zero)
            tvRecordingHint.text = getString(R.string.chat_recording_slide_cancel)
            setRecordingUiVisible(true)
            mainHandler.removeCallbacks(recordingTicker)
            mainHandler.post(recordingTicker)
        } catch (e: Exception) {
            Log.e("ChatMedia", "Audio recording start failed: ${e.message}", e)
            isRecording = false
            setRecordingUiVisible(false)
            showAppToast(getString(R.string.chat_recording_start_failed), Toast.LENGTH_SHORT)
        }
    }

    private fun stopAudioRecordingAndSend() {
        try {
            val recordingResult = audioRecorderController.stop()
            isRecording = false
            setRecordingUiVisible(false)
            if (recordingResult.durationMs < 1000L) {
                recordingResult.file.delete()
                // CHAT-040 / T45: hint the gesture instead of just saying "too short"
                // — short taps are a common discoverability fail on a hold-to-record
                // affordance.
                showAppToast(getString(R.string.chat_mic_hold_to_record), Toast.LENGTH_SHORT)
                return
            }

            val tempId = addOptimisticMediaMessage(
                messageType = "audio",
                localAttachmentUrl = recordingResult.file.toURI().toString(),
                audioDurationMs = recordingResult.durationMs
            )
            uploadAndSendAttachment(tempId, recordingResult.file, "audio")
        } catch (e: Exception) {
            Log.e("ChatMedia", "Audio recording stop failed: ${e.message}", e)
            isRecording = false
            setRecordingUiVisible(false)
            // CHAT-040: a really fast tap (~100-200ms) makes MediaRecorder.stop()
            // throw because no audio frames were captured yet. The duration-check
            // above never runs in that case — the controller throws straight
            // through to this catch. Treat sub-second elapsed time as a tap and
            // show the friendly Hold-to-record hint instead of the generic
            // "Couldn't save voice note" toast, which reads like an app bug.
            val elapsed = if (recordingStartedAtMs > 0L) {
                SystemClock.elapsedRealtime() - recordingStartedAtMs
            } else {
                Long.MAX_VALUE
            }
            val msgRes = if (elapsed < 1000L) {
                R.string.chat_mic_hold_to_record
            } else {
                R.string.chat_voice_save_failed
            }
            showAppToast(getString(msgRes), Toast.LENGTH_SHORT)
        }
    }

    private fun cancelAudioRecording(showToast: Boolean) {
        // CHAT-040: a parent that intercepts the touch fires ACTION_CANCEL after
        // a casual tap, which previously surfaced "Recording canceled" — same
        // misleading error a single tap on the mic used to produce. If the
        // touch lasted less than a second, treat it as a tap and show the
        // friendly hint instead.
        val durationMs = if (recordingStartedAtMs > 0L) {
            SystemClock.elapsedRealtime() - recordingStartedAtMs
        } else {
            Long.MAX_VALUE
        }
        audioRecorderController.cancel()
        isRecording = false
        setRecordingUiVisible(false)
        if (showToast) {
            val msgRes = if (durationMs < 1000L) {
                R.string.chat_mic_hold_to_record
            } else {
                R.string.chat_recording_canceled
            }
            showAppToast(getString(msgRes), Toast.LENGTH_SHORT)
        }
    }

    private fun setRecordingUiVisible(isVisible: Boolean) {
        recordingBar.visibility = if (isVisible) View.VISIBLE else View.GONE
        etMessage.isEnabled = !isVisible
        etMessage.isFocusable = !isVisible
        etMessage.isFocusableInTouchMode = !isVisible
        if (isVisible) {
            startRecordingPulse()
        } else {
            stopRecordingPulse()
            mainHandler.removeCallbacks(recordingTicker)
            tvRecordingTimer.text = getString(R.string.chat_recording_timer_zero)
            tvRecordingHint.text = getString(R.string.chat_recording_slide_cancel)
        }
        updateComposerActionState()
    }

    private fun startRecordingPulse() {
        recordingPulseAnimator?.cancel()
        recordingPulseAnimator = ObjectAnimator.ofFloat(vRecordingDot, View.ALPHA, 1f, 0.25f).apply {
            duration = 450L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun stopRecordingPulse() {
        recordingPulseAnimator?.cancel()
        recordingPulseAnimator = null
        vRecordingDot.alpha = 1f
    }

    private fun addOptimisticMediaMessage(
        messageType: String,
        localAttachmentUrl: String,
        audioDurationMs: Long = 0L
    ): String {
        val currentTime = Date()
        val tempId = newTempMessageId()
        val tempMessage = ChatMessage(
            id = tempId,
            message = "",
            timestamp = timeFormat.format(currentTime),
            isSentByMe = true,
            date = currentTime,
            messageType = messageType,
            attachmentUrl = localAttachmentUrl,
            audioDurationMs = audioDurationMs,
            deliveryStatus = MessageDeliveryStatus.SENDING
        )

        appendMessageWithOptionalDateHeader(tempMessage)
        rememberPendingOutgoing(
            tempId = tempId,
            message = "",
            messageType = messageType,
            attachmentUrl = localAttachmentUrl,
            audioDurationMs = audioDurationMs.takeIf { it > 0 }
        )
        // Bug 10: schedule timeout — applies to attachment sends too. Upload
        // + server ack must arrive within SENDING_TIMEOUT_MS or the bubble
        // flips to FAILED and the user can retry.
        scheduleSendingTimeout(tempId)
        rvMessages.post {
            rvMessages.smoothScrollToPosition(messages.size - 1)
        }
        return tempId
    }

    private fun updateTempMessage(tempId: String, updater: (ChatMessage) -> ChatMessage) {
        val index = messages.indexOfFirst { it.id == tempId }
        if (index == -1) return
        messages[index] = updater(messages[index])
        chatAdapter.notifyItemChanged(index)
    }

    private fun removeTempMessage(tempId: String) {
        val index = messages.indexOfFirst { it.id == tempId }
        if (index == -1) return
        messages.removeAt(index)
        pendingOutgoingByTempId.remove(tempId)
        messageSendMethod.remove(tempId)
        updateTopHeader(messages)
        chatAdapter.notifyDataSetChanged()
    }

    private fun rememberPendingOutgoing(
        tempId: String,
        message: String,
        messageType: String,
        attachmentUrl: String? = null,
        audioDurationMs: Long? = null
    ) {
        pendingOutgoingByTempId[tempId] = PendingOutgoingPayload(
            message = message,
            messageType = messageType,
            attachmentUrl = attachmentUrl,
            audioDurationMs = audioDurationMs
        )
    }

    private fun replaceTempMessage(tempId: String, realMessage: ChatMessage, method: String): Boolean {
        val tempIndex = messages.indexOfFirst { it.id == tempId }
        if (tempIndex == -1) return false

        val tempMessage = messages[tempIndex]
        val existingReactions = tempMessage.reactions
        // M18: ChatMessage.reactions is val — copy() instead of mutating in place.
        val replacement = realMessage.copy(reactions = existingReactions)
        messages[tempIndex] = replacement
        pendingOutgoingByTempId.remove(tempId)
        messageSendMethod.remove(tempId)
        messageSendMethod[realMessage.id] = method
        // Bug 10: ack arrived — cancel the stuck-SENDING timeout.
        cancelSendingTimeout(tempId)

        // BUG-03: the temp→confirmed swap sits at the SAME index with identical
        // visible content — only the id and the tick (SENDING → SENT) change. A
        // full rebuild + notifyDataSetChanged() rebinds every visible row, which
        // is the second "blink" the user sees. Repaint just this one bubble so
        // the send is flicker-free. Fall back to the structural rebuild only when
        // the swap could actually change list structure:
        //   • a confirmed twin already sits elsewhere (socket-echo race) — let the
        //     rebuild's dedup collapse it, else we'd show the message twice; or
        //   • the server timestamp lands on a different calendar day than the
        //     optimistic temp (its date header needs recomputing).
        val duplicateElsewhere = messages.withIndex().any { (i, m) ->
            i != tempIndex && !m.isDateHeader && m.id == replacement.id
        }
        val dayChanged = tempMessage.date != null && replacement.date != null &&
            !isSameDay(tempMessage.date, replacement.date)
        if (duplicateElsewhere || dayChanged) {
            rebuildMessagesWithHeaders(messages.filterNot { it.isDateHeader })
            chatAdapter.notifyDataSetChanged()
        } else {
            chatAdapter.notifyItemChanged(tempIndex)
        }
        return true
    }

    /**
     * Bug 10: send failed (offline, server reject, or socket no-ack timeout).
     * Keep the temp bubble visible with FAILED state so the user knows
     * exactly which message didn't go through and can retry. Only remove
     * the temp row on success — never on failure.
     */
    private fun failPendingOutgoing(tempId: String, userMessage: String) {
        val idx = messages.indexOfFirst { it.id == tempId }
        if (idx >= 0) {
            val cur = messages[idx]
            if (cur.deliveryStatus != MessageDeliveryStatus.FAILED) {
                messages[idx] = cur.copy(deliveryStatus = MessageDeliveryStatus.FAILED)
                chatAdapter.notifyItemChanged(idx)
            }
        }
        cancelSendingTimeout(tempId)
        if (userMessage.isNotBlank()) {
            showAppToast(userMessage, Toast.LENGTH_SHORT)
        }
    }

    /**
     * Bug 10: per-temp SENDING timeout. Socket emit is fire-and-forget so
     * if the socket dies between emit and server processing we never hear
     * back — the bubble would otherwise stay on SENDING forever. After
     * [SENDING_TIMEOUT_MS] without an ack (message_sent socket event or
     * REST onResponse), we flip the bubble to FAILED so the user knows
     * and can retry.
     */
    private val sendingTimeoutRunnables = mutableMapOf<String, Runnable>()
    private val SENDING_TIMEOUT_MS = 15_000L

    // Delete-for-everyone ack tracking: original message (for rollback) + ack-timeout
    // runnable, keyed by message id. See performDeleteForEveryone().
    private val pendingDeleteOriginals = mutableMapOf<String, ChatMessage>()
    private val pendingDeleteTimeouts = mutableMapOf<String, Runnable>()
    private val DELETE_ACK_TIMEOUT_MS = 4_000L

    private fun scheduleSendingTimeout(tempId: String) {
        cancelSendingTimeout(tempId)
        val r = Runnable {
            sendingTimeoutRunnables.remove(tempId)
            val idx = messages.indexOfFirst { it.id == tempId }
            if (idx >= 0 && messages[idx].deliveryStatus == MessageDeliveryStatus.SENDING) {
                Log.w("ChatSendRetry", "tempId=$tempId stuck on SENDING — marking FAILED")
                failPendingOutgoing(tempId, "")
            }
        }
        sendingTimeoutRunnables[tempId] = r
        mainHandler.postDelayed(r, SENDING_TIMEOUT_MS)
    }

    private fun cancelSendingTimeout(tempId: String) {
        sendingTimeoutRunnables.remove(tempId)?.let { mainHandler.removeCallbacks(it) }
    }

    /**
     * Bug 10: resend a FAILED message. Swaps it back to SENDING, schedules
     * the timeout, and re-runs the same send path (socket-first, REST
     * fallback). Invoked from the long-press "Retry" menu and from the
     * network-reconnect auto-retry.
     */
    private fun retryFailedMessage(tempId: String) {
        val idx = messages.indexOfFirst { it.id == tempId }
        if (idx < 0) return
        val msg = messages[idx]
        if (msg.deliveryStatus != MessageDeliveryStatus.FAILED) return
        val payload = pendingOutgoingByTempId[tempId]
        if (payload == null) {
            Log.w("ChatSendRetry", "tempId=$tempId has no pending payload — dropping")
            return
        }

        messages[idx] = msg.copy(deliveryStatus = MessageDeliveryStatus.SENDING)
        chatAdapter.notifyItemChanged(idx)
        scheduleSendingTimeout(tempId)

        when (payload.messageType) {
            "text" -> {
                if (socketManager.isConnected()) {
                    messageSendMethod[tempId] = "socket"
                    socketManager.sendMessage(myUserId, peerUserId, payload.message, "text")
                } else {
                    messageSendMethod[tempId] = "api"
                    sendMessageViaAPI(tempId, payload.message)
                }
            }
            "image", "audio" -> {
                val url = payload.attachmentUrl
                if (url.isNullOrBlank()) {
                    failPendingOutgoing(tempId, "Couldn't retry attachment")
                    return
                }
                if (socketManager.isConnected()) {
                    messageSendMethod[tempId] = "socket"
                    socketManager.sendMessage(
                        fromUserId = myUserId,
                        toUserId = peerUserId,
                        message = "",
                        messageType = payload.messageType,
                        attachmentUrl = url,
                        audioDurationMs = payload.audioDurationMs
                    )
                } else {
                    messageSendMethod[tempId] = "api"
                    sendMediaViaFallbackAPI(tempId, payload.messageType, url)
                }
            }
        }
    }

    /**
     * Bug 10: re-send every FAILED message in the current chat. Called
     * from the network-reconnect listener when the device comes back
     * online so pending messages auto-sync without the user having to
     * manually retry each one.
     */
    private fun retryAllFailedMessages() {
        val failed = messages.filter {
            it.isSentByMe && !it.isDateHeader && it.deliveryStatus == MessageDeliveryStatus.FAILED
        }.map { it.id }
        if (failed.isEmpty()) return
        Log.d("ChatSendRetry", "auto-retry on reconnect — ${failed.size} failed messages")
        failed.forEach { retryFailedMessage(it) }
    }

    private fun failPendingOutgoingByMessage(messageText: String, userMessage: String) {
        val tempId = pendingOutgoingByTempId.entries.firstOrNull { (_, payload) ->
            payload.messageType == "text" && payload.message == messageText
        }?.key ?: messages.firstOrNull {
            it.id.startsWith("temp_") && it.isSentByMe && it.message == messageText
        }?.id

        if (tempId != null) {
            failPendingOutgoing(tempId, userMessage)
        } else {
            showAppToast(userMessage, Toast.LENGTH_SHORT)
        }
    }

    private fun uploadAndSendAttachment(tempId: String, file: File, messageType: String) {
        val uploadCall = apiManager.uploadChatAttachment(
            userId = myUserId,
            toUserId = peerUserId,
            messageType = messageType,
            file = file,
            callback = object : NetworkCallback<ChatAttachmentUploadResponse> {
                override fun onResponse(
                    call: Call<ChatAttachmentUploadResponse>,
                    response: Response<ChatAttachmentUploadResponse>
                ) {
                    activeAttachmentTempIds.remove(tempId)
                    activeAttachmentCalls.remove(tempId)
                    val remoteUrl = response.body()?.data?.url
                    val success = response.isSuccessful && response.body()?.success == true &&
                        !remoteUrl.isNullOrBlank()
                    if (!success) {
                        removeTempMessage(tempId)
                        val body = response.body()
                        val minVer = body?.data?.requiredMinVersion
                        val baseMsg = body?.message ?: "Couldn't upload attachment"
                        showAppToast(
                            if (minVer != null) "$baseMsg (min version: $minVer)" else baseMsg,
                            Toast.LENGTH_SHORT
                        )
                        file.delete()
                        return
                    }

                    updateTempMessage(tempId) { current ->
                        current.copy(attachmentUrl = remoteUrl)
                    }
                    // CHAT-034: preserve the recorded duration across the
                    // localUrl → remoteUrl swap so the pending payload and
                    // any downstream re-send / fallback still ship it.
                    val durationToShip = pendingOutgoingByTempId[tempId]?.audioDurationMs
                    rememberPendingOutgoing(
                        tempId = tempId,
                        message = "",
                        messageType = messageType,
                        attachmentUrl = remoteUrl,
                        audioDurationMs = durationToShip
                    )

                    if (!socketManager.isConnected()) {
                        sendMediaViaFallbackAPI(tempId, messageType, remoteUrl!!)
                        file.delete()
                        return
                    }

                    messageSendMethod[tempId] = "socket"
                    socketManager.sendMessage(
                        fromUserId = myUserId,
                        toUserId = peerUserId,
                        message = "",
                        messageType = messageType,
                        attachmentUrl = remoteUrl,
                        audioDurationMs = durationToShip
                    )
                    // Push is sent server-side (socket saveMessage always pushes);
                    // the old client-side push here double-notified the peer.
                    file.delete()
                }

                override fun onFailure(call: Call<ChatAttachmentUploadResponse>, t: Throwable) {
                    activeAttachmentTempIds.remove(tempId)
                    activeAttachmentCalls.remove(tempId)
                    if (call.isCanceled) {
                        file.delete()
                        return
                    }
                    removeTempMessage(tempId)
                    showAppToast("Couldn't upload attachment", Toast.LENGTH_SHORT)
                    file.delete()
                    Log.e("ChatMedia", "Attachment upload failed: ${t.message}", t)
                }

                override fun onNoNetwork() {
                    activeAttachmentTempIds.remove(tempId)
                    activeAttachmentCalls.remove(tempId)
                    removeTempMessage(tempId)
                    showAppToast(DConstants.NO_NETWORK, Toast.LENGTH_SHORT)
                    file.delete()
                }
            }
        )
        if (uploadCall != null) {
            activeAttachmentTempIds.add(tempId)
            activeAttachmentCalls[tempId] = uploadCall
        }
    }

    /**
     * When the socket is down after a successful upload, send the media message via REST
     * using the same fields as socket (`message_type`, `attachment_url`).
     */
    private fun sendMediaViaFallbackAPI(tempId: String, messageType: String, attachmentUrl: String) {
        messageSendMethod[tempId] = "api"
        // CHAT-034: forward the recorded duration so the bubble can render the
        // length once the chat history reloads — same as the socket path does.
        val durationToShip = pendingOutgoingByTempId[tempId]?.audioDurationMs
        val apiCall = apiManager.fallbackSendMessage(
            fromUserId = myUserId,
            toUserId = peerUserId,
            message = "",
            messageType = messageType,
            attachmentUrl = attachmentUrl,
            audioDurationMs = durationToShip,
            callback = object : NetworkCallback<FallbackSendMessageResponse> {
                override fun onResponse(
                    call: Call<FallbackSendMessageResponse>,
                    response: Response<FallbackSendMessageResponse>
                ) {
                    activeTextSendCalls.remove(call)
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true && responseBody.data?.message != null) {
                            val fallbackMessage = responseBody.data.message
                            val realMessage = convertFallbackMessageToChatMessage(fallbackMessage)
                            val replaced = replaceTempMessage(tempId, realMessage, "api")
                            if (!replaced) {
                                Log.v("SocketIOCheck", "Fallback API returned after socket already replaced tempId=$tempId")
                                return
                            }
                            Log.d(
                                "SocketIOCheck",
                                "✅ Media sent via fallback API - ID: ${fallbackMessage.id}"
                            )
                            // fallback_send_message pushes server-side; the old
                            // client-side push here double-notified the peer.
                        } else {
                            removeTempMessage(tempId)
                            showAppToast(
                                responseBody?.message ?: "Couldn't send attachment",
                                Toast.LENGTH_SHORT
                            )
                        }
                    } else {
                        removeTempMessage(tempId)
                        showAppToast("Couldn't send attachment", Toast.LENGTH_SHORT)
                        Log.e(
                            "SocketIOCheck",
                            "Failed to send media via fallback API: ${response.code()}"
                        )
                    }
                }

                override fun onFailure(call: Call<FallbackSendMessageResponse>, t: Throwable) {
                    activeTextSendCalls.remove(call)
                    if (call.isCanceled) return
                    removeTempMessage(tempId)
                    showAppToast("Couldn't send attachment", Toast.LENGTH_SHORT)
                    Log.e(
                        "SocketIOCheck",
                        "Failed to send media via fallback API: ${t.message}",
                        t
                    )
                }

                override fun onNoNetwork() {
                    removeTempMessage(tempId)
                    showAppToast(DConstants.NO_NETWORK, Toast.LENGTH_SHORT)
                    Log.e("SocketIOCheck", "No internet connection (media fallback)")
                }
            }
        )
        apiCall?.let { activeTextSendCalls.add(it) }
    }

    private fun formatElapsedTime(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun connectSocket() {
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        Log.d("SocketIOCheck", "🔌 Connecting Socket.IO for ChatActivityInHouse")
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        
        // Get user ID and connect Socket.IO
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id
        
        if (userId != null && userId > 0) {
            Log.d("SocketIOCheck", "🔌 Connecting Socket.IO with User ID: $userId")
            
            // If already connected, join chat room immediately
            if (socketManager.isConnected() && myUserId > 0 && peerUserId > 0) {
                Log.d("SocketIOCheck", "✅ Socket.IO already connected - Joining chat room immediately: $chatId")
                socketManager.joinChatRoom(myUserId, peerUserId)
            } else {
                // Connect and wait for connection event to join room (handled in observeSocketEvents)
                socketManager.connect(userId)
            }
        } else {
            Log.e("SocketIOCheck", "❌ No user ID available - Cannot connect Socket.IO")
        }
    }
    
    private fun updateSocketStatusUI(isConnected: Boolean) {
        // Don't update UI based on Socket.IO status anymore
        // UI will be updated based on last_online_status from API
    }
    
    /**
     * CHAT-095: format the chat-history `last_online` timestamp (now
     * sourced from users.last_active_at server-side) into one of 7 display
     * states. Always sets the status TextView VISIBLE — the previous code
     * left it stuck on GONE once the empty branch fired, which is why the
     * "no last seen at all" complaint kept coming back.
     */
    private fun updateOnlineStatusFromAPI(lastOnlineTimestamp: String?, legacyStatus: String?) {
        this.lastOnlineStatus = lastOnlineTimestamp ?: legacyStatus
        mainHandler.post {
            if (!isUiSafe()) return@post
            // Block-aware presence: completely hide the indicator when either
            // party has blocked the other. The server also nulls these fields
            // when blocked, but the local cache may still carry a stale
            // timestamp on a fresh block toggle — this client-side guard
            // makes the hide happen instantly regardless of server timing.
            if (iHaveBlockedThisUser || peerHasBlockedMe) {
                tvUserStatus.text = ""
                tvUserStatus.visibility = View.GONE
                vOnlineIndicator.visibility = View.GONE
                return@post
            }
            val display = if (!lastOnlineTimestamp.isNullOrBlank()) {
                // Bug-1: presence is "chat-open only". The REST last_online is
                // sourced from users.last_active_at, which is bumped by ANY
                // authenticated request / a still-connected background socket —
                // i.e. it means "used the app recently", NOT "in this chat now".
                // So it may never render the green "Online": the green state is
                // owned exclusively by live socket room-presence (applyLivePresence).
                // A <60s snapshot is downgraded to a grey "Last seen recently".
                val snapshot = LastSeenFormatter.format(lastOnlineTimestamp)
                if (snapshot.isOnline) {
                    LastSeenFormatter.Display(text = "Last seen recently", isOnline = false)
                } else {
                    snapshot
                }
            } else if (!legacyStatus.isNullOrBlank()) {
                // Pre-migration users / older server response. Strip the
                // redundant "active " prefix and render as a single-line
                // last-seen string in the muted color (no green dot).
                LastSeenFormatter.Display(
                    text = "Last seen ${legacyStatus.trim().removePrefix("active ").trim()}",
                    isOnline = false
                )
            } else {
                LastSeenFormatter.Display(text = "Offline", isOnline = false)
            }

            tvUserStatus.text = display.text
            tvUserStatus.visibility = View.VISIBLE
            val color = if (display.isOnline) {
                ContextCompat.getColor(this, R.color.online_green)
            } else {
                ContextCompat.getColor(this, R.color.grey_medium)
            }
            tvUserStatus.setTextColor(color)
            vOnlineIndicator.visibility = if (display.isOnline) View.VISIBLE else View.GONE
            // Removed: Update call buttons state based on online status
            // Buttons are now controlled only by check_call_availability API response
        }
    }

    /**
     * Real-time presence push from socket. When peer joins this chat
     * (`user_joined_chat`) → "Online" + green dot. When peer leaves or
     * disconnects (`user_left_chat`) → "Last seen just now" + grey. The
     * snapshot path [updateOnlineStatusFromAPI] still owns the initial
     * render from chat_history; this just keeps it fresh after that.
     */
    private var peerTypingClearRunnable: Runnable? = null

    /**
     * Bug 8: render the peer-typing state in the chat header. When typing
     * is active, "Typing..." replaces the Online/Last-seen text and the
     * green dot is shown. On stop, restore the snapshot from the latest
     * chat_history fetch. Auto-clear after 5s so we don't get stuck if the
     * peer's stop-typing emit is dropped on the wire.
     */
    private fun applyPeerTypingIndicator(isTyping: Boolean) {
        mainHandler.post {
            if (!isUiSafe()) return@post
            // Suppress when blocked — Bug 3 already hides the header status
            // entirely; do not surface "Typing..." for blocked peers.
            if (iHaveBlockedThisUser || peerHasBlockedMe) return@post

            peerTypingClearRunnable?.let { mainHandler.removeCallbacks(it) }
            peerTypingClearRunnable = null

            if (isTyping) {
                tvUserStatus.text = "Typing..."
                tvUserStatus.visibility = View.VISIBLE
                tvUserStatus.setTextColor(ContextCompat.getColor(this, R.color.online_green))
                vOnlineIndicator.visibility = View.VISIBLE
                val clearer = Runnable { applyPeerTypingIndicator(false) }
                peerTypingClearRunnable = clearer
                mainHandler.postDelayed(clearer, 5000L)
            } else {
                // Re-render whatever the last-known presence snapshot was.
                updateOnlineStatusFromAPI(lastOnlineStatus, null)
            }
        }
    }

    private fun applyLivePresence(online: Boolean) {
        mainHandler.post {
            if (!isUiSafe()) return@post
            // Block-aware presence: ignore the live socket presence event
            // entirely when blocked in either direction. The peer's join /
            // leave must not surface as "Online" or "Last seen just now".
            if (iHaveBlockedThisUser || peerHasBlockedMe) {
                tvUserStatus.text = ""
                tvUserStatus.visibility = View.GONE
                vOnlineIndicator.visibility = View.GONE
                return@post
            }
            if (online) {
                tvUserStatus.text = "Online"
                tvUserStatus.visibility = View.VISIBLE
                tvUserStatus.setTextColor(ContextCompat.getColor(this, R.color.online_green))
                vOnlineIndicator.visibility = View.VISIBLE
            } else {
                tvUserStatus.text = "Last seen just now"
                tvUserStatus.visibility = View.VISIBLE
                tvUserStatus.setTextColor(ContextCompat.getColor(this, R.color.grey_medium))
                vOnlineIndicator.visibility = View.GONE
            }
        }
    }

    private fun observeSocketEvents() {
        lifecycleScope.launch {
            socketManager.isConnected.collectLatest { connected ->
                val prev = previousSocketConnected
                previousSocketConnected = connected
                if (connected) {
                    Log.d("SocketIOCheck", "✅ Socket.IO CONNECTED - joining chat room immediately: $chatId")
                    // Join chat room immediately when connected
                    if (myUserId > 0 && peerUserId > 0) {
                        socketManager.joinChatRoom(myUserId, peerUserId)
                        Log.d("SocketIOCheck", "✅ Joined chat room: $chatId")
                    } else {
                        // Fallback to chatId if user IDs not set
                        socketManager.joinChat(chatId)
                    }
                    // Catch up history after a disconnect (missed socket events while offline)
                    if (prev == false && chatId.isNotEmpty() && myUserId > 0 && peerUserId > 0) {
                        Log.d("SocketIOCheck", "🔄 Socket reconnected — refreshing chat history")
                        loadMessages()
                    }
                } else {
                    Log.d("SocketIOCheck", "❌ Socket.IO DISCONNECTED")
                }
            }
        }

        // Use collect (not collectLatest) on SharedFlow event streams — collectLatest
        // cancels the previous block when a new event arrives, which can drop UI work
        // mid-flight when messages land back-to-back.
        lifecycleScope.launch {
            socketManager.newMessage.collect { message ->
                val matches = isSocketMessageForThisChat(message)
                Log.d(
                    "RealtimeChat",
                    "activity newMessage.collect id=${message.id} from=${message.fromUserId} to=${message.toUserId} chatId=${message.chatId} matchesThread=$matches"
                )
                if (matches) {
                    handleNewMessage(message)
                }
            }
        }

        lifecycleScope.launch {
            socketManager.messageSent.collect { sock ->
                if (!isSocketMessageForThisChat(sock)) return@collect
                if (sock.fromUserId != myUserId) return@collect
                val messageId = sock.id.toString()
                Log.d("SocketIOCheck", "✅ Message sent via SOCKET.IO - ID: $messageId")
                if (!isUiSafe()) return@collect
                messageSendMethod[messageId] = "socket"
                if (messages.none { it.id == messageId }) {
                    handleNewMessage(sock)
                }
                val idx = messages.indexOfFirst { m -> m.isSentByMe && m.id == messageId }
                if (idx != -1) {
                    // CHAT-025: server-ack means SENT (single tick), not
                    // DELIVERED. DELIVERED is reserved for an explicit
                    // peer-device delivery signal; without one we'd jump
                    // straight from no-tick to double-tick on every send.
                    val nextStatus = if (sock.isRead) {
                        MessageDeliveryStatus.READ
                    } else {
                        MessageDeliveryStatus.SENT
                    }
                    messages[idx] = messages[idx].copy(deliveryStatus = nextStatus)
                    chatAdapter.notifyItemChanged(idx)
                }
                logSocketIOStatus()
            }
        }

        lifecycleScope.launch {
            socketManager.messageError.collect { error ->
                Log.e("SocketIOCheck", "Message error: $error")
                if (!isUiSafe() || error.startsWith("Reaction error:")) return@collect
                // T9: server doesn't echo a client_message_id yet, so we can't
                // map a `message_error` back to a specific in-flight temp. To
                // avoid mutating the wrong bubble when multiple sends are
                // pending, only act when there is exactly ONE pending socket
                // send. Otherwise surface a generic toast and leave the temps
                // alone — `messageSent` / `newMessage` will still reconcile
                // them when the socket recovers.
                val socketPendings = pendingOutgoingByTempId.entries.filter { (tempId, _) ->
                    messageSendMethod[tempId] == "socket"
                }
                if (socketPendings.size != 1) {
                    if (socketPendings.size > 1) {
                        showAppToast("Send failed", Toast.LENGTH_SHORT)
                        Log.w(
                            "SocketIOCheck",
                            "messageError ambiguous — ${socketPendings.size} pending socket sends; not mutating temps"
                        )
                    }
                    return@collect
                }
                val socketPending = socketPendings.first()
                val tempId = socketPending.key
                val payload = socketPending.value
                if (payload.messageType == "text") {
                    messageSendMethod[tempId] = "api"
                    sendMessageViaAPI(tempId, payload.message)
                } else {
                    // Attachments: same REST-fallback pattern as text. The
                    // upload step already succeeded (attachmentUrl is in the
                    // pending payload), so we just need to ship the message
                    // via fallback_send_message — exactly what the initial
                    // socket-disconnected path does at uploadAndSendAttachment
                    // line ~2077. Previously this branch just toasted
                    // "Couldn't send attachment" and dropped the message even
                    // though the upload had succeeded and a perfectly good
                    // REST fallback was sitting right there. Only give up if
                    // the URL is somehow missing (shouldn't happen, but
                    // belt-and-suspenders).
                    val remoteUrl = payload.attachmentUrl
                    if (!remoteUrl.isNullOrBlank()) {
                        messageSendMethod[tempId] = "api"
                        sendMediaViaFallbackAPI(tempId, payload.messageType, remoteUrl)
                    } else {
                        failPendingOutgoing(tempId, "Couldn't send attachment")
                    }
                }
            }
        }

        lifecycleScope.launch {
            socketManager.reactionUpdated.collect { reactionUpdate ->
                if (reactionUpdate.chatId == chatId) {
                    handleIncomingReaction(reactionUpdate)
                }
            }
        }

        // CHAT-095 follow-up: real-time peer presence. When the peer joins
        // this chat their socket fires `user_joined_chat` → flip header to
        // "Online". When they leave / disconnect → "Last seen just now".
        // Falls back to the snapshot value from chat_history if the event
        // never arrives.
        lifecycleScope.launch {
            socketManager.presenceUpdates.collect { event ->
                if (!isUiSafe()) return@collect
                if (event.userId != peerUserId) return@collect
                if (event.chatId.isNotEmpty() && event.chatId != chatId) return@collect
                applyLivePresence(event.online)
            }
        }

        // Bug 8: peer typing indicator. Server fans out `user_typing` to
        // everyone in the chat room when the other side's composer fires.
        // Show "Typing..." in the header in place of Online/Last seen; auto
        // clear after 5s in case the stop event is dropped on the wire.
        lifecycleScope.launch {
            socketManager.userTyping.collect { event ->
                if (!isUiSafe()) return@collect
                if (event.userId != peerUserId) return@collect
                if (event.chatId.isNotEmpty() && event.chatId != chatId) return@collect
                applyPeerTypingIndicator(event.isTyping)
            }
        }

        // Bug 10: auto-retry FAILED messages the moment the network comes
        // back. Observes BaseApplication's networkConnectedLiveData so we
        // pick up reconnects from any source (Wi-Fi handoff, mobile data
        // toggle, airplane-mode off). The retry routes through the same
        // socket-first / REST-fallback path the original send used.
        var lastNetState: Boolean? = null
        BaseApplication.getInstance()?.networkConnectedLiveData?.observe(this) { online ->
            val wasOffline = lastNetState == false
            lastNetState = online
            if (online == true && wasOffline) {
                Log.d("ChatSendRetry", "network reconnected — auto-retrying any FAILED messages")
                retryAllFailedMessages()
            }
        }

        lifecycleScope.launch {
            socketManager.chatMessageDeleted.collect { deletedId ->
                if (deletedId.isEmpty()) return@collect
                val idx = messages.indexOfFirst { it.id == deletedId && !it.isDateHeader }
                if (idx == -1) {
                    Log.d("ChatDelete", "Ignoring message_deleted — id=$deletedId not in current window")
                    return@collect
                }
                val existing = messages[idx]
                if (existing.isDeleted) return@collect
                messages[idx] = existing.copy(
                    isDeleted = true,
                    reactions = emptyMap(),
                    attachmentUrl = null
                )
                if (pendingReplyTo?.id == deletedId) {
                    pendingReplyTo = null
                    updateReplyPreviewUi()
                }
                chatAdapter.notifyItemChanged(idx)
                Log.d("ChatDelete", "✅ Applied remote tombstone for id=$deletedId idx=$idx")
            }
        }

        // Delete-for-everyone consistency: server confirmed it persisted + broadcast our
        // delete. Cancel the ack-timeout and stop tracking — the tombstone is now final.
        lifecycleScope.launch {
            socketManager.messageDeleteAck.collect { ackId ->
                if (ackId.isEmpty()) return@collect
                cancelDeleteAckTimeout(ackId)
                pendingDeleteOriginals.remove(ackId)
                Log.d("ChatDelete", "server ack delete id=$ackId — tombstone confirmed")
            }
        }

        // Server rejected/failed our socket delete → fall back to REST so the delete
        // actually persists (and the peer eventually gets it) instead of sticking only
        // on the sender. If REST also fails, deleteForEveryoneViaRest rolls the tombstone back.
        lifecycleScope.launch {
            socketManager.messageDeleteError.collect { (errId, err) ->
                if (errId.isEmpty()) return@collect
                cancelDeleteAckTimeout(errId)
                Log.w("ChatDelete", "server delete_error id=$errId err=$err — REST fallback")
                val original = pendingDeleteOriginals[errId]
                if (original != null) deleteForEveryoneViaRest(original)
            }
        }

        // Read-receipt: peer marked our messages as read → flip bubbles to ✓✓-blue.
        lifecycleScope.launch {
            socketManager.messagesRead.collect { event ->
                if (!isUiSafe()) return@collect
                if (event.chatId.isNotEmpty() && event.chatId != chatId) return@collect
                if (event.messageIds.isEmpty() && event.lastMessageId == null) return@collect
                val targetIds = event.messageIds.map { it.toString() }.toHashSet()
                val cutoff = event.lastMessageId
                var changed = false
                messages.forEachIndexed { idx, msg ->
                    if (msg.isDateHeader || !msg.isSentByMe || msg.isDeleted) return@forEachIndexed
                    if (msg.deliveryStatus == MessageDeliveryStatus.READ) return@forEachIndexed
                    val numericId = msg.id.toLongOrNull()
                    val matchesIdList = msg.id in targetIds
                    val matchesCutoff = cutoff != null && numericId != null && numericId <= cutoff
                    if (matchesIdList || matchesCutoff) {
                        messages[idx] = msg.copy(deliveryStatus = MessageDeliveryStatus.READ)
                        chatAdapter.notifyItemChanged(idx)
                        changed = true
                    }
                }
                if (changed) Log.d("ChatReadReceipt", "✓✓ flipped bubbles to READ for chat=${event.chatId}")
            }
        }
    }

    private fun handleIncomingReaction(reactionUpdate: com.gmwapp.hima.socket.ReactionUpdateEvent) {
        val messageId = reactionUpdate.messageId.toString()
        val messageIndex = messages.indexOfFirst { it.id == messageId }
        
        if (messageIndex != -1) {
            // Convert all_reactions to Map<userId, emoji>
            val reactionsMap = mutableMapOf<Int, String>()
            reactionUpdate.allReactions?.forEach { reactionMapData ->
                val userId = (reactionMapData["user_id"] as? Number)?.toInt() ?: return@forEach
                val emoji = reactionMapData["reaction_emoji"] as? String ?: return@forEach
                reactionsMap[userId] = emoji
            }
            
            // M18: ChatMessage.reactions is val — copy() instead of mutating in place.
            messages[messageIndex] = messages[messageIndex].copy(reactions = reactionsMap)
            chatAdapter.notifyItemChanged(messageIndex)
            Log.d("ChatReactions", "✅ Updated reaction for message $messageId: $reactionsMap")
        }
    }
    
    private fun showReactionDetails(message: ChatMessage, clickedEmoji: String) {
        val reactions = message.reactions
        if (reactions.isEmpty()) return
        
        // Get peer user info from intent
        val peerUserName = extractNameOnly(intent.getStringExtra("USER_NAME") ?: "User")
        val peerUserImage = intent.getStringExtra("USER_IMAGE") ?: ""
        
        // Show bottom sheet
        val bottomSheet = com.gmwapp.hima.dialogs.BottomSheetReactionDetails.newInstance(
            message = message,
            myUserId = myUserId,
            peerUserId = peerUserId,
            peerUserName = peerUserName,
            peerUserImage = peerUserImage,
            onReactionRemove = { msg, emoji ->
                // Remove reaction when user taps "Tap to remove"
                handleReactionUpdate(msg, null)
            },
            onAddReaction = { msg ->
                // Show reaction popup when user taps "Add Reaction" button
                val messageIndex = messages.indexOfFirst { it.id == msg.id }
                if (messageIndex != -1) {
                    val viewHolder = rvMessages.findViewHolderForAdapterPosition(messageIndex)
                    if (viewHolder != null) {
                        val tvMessage = viewHolder.itemView.findViewById<TextView>(R.id.tv_message)
                        if (tvMessage != null) {
                            // Use the adapter's method to show reaction popup
                            chatAdapter.showReactionPopupForPosition(tvMessage, messageIndex)
                        }
                    }
                }
            }
        )
        
        bottomSheet.show(supportFragmentManager, "BottomSheetReactionDetails")
    }

    private fun loadMessages(isSilentRetry: Boolean = false, userRetry: Boolean = false) {
        mainHandler.removeCallbacks(retryHistoryRunnable)
        pendingThrottleHistoryRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingThrottleHistoryRunnable = null

        val hadInFlightHistory = currentHistoryCall != null
        currentHistoryCall?.cancel()
        val requestId = historyLoadRequestId.incrementAndGet()

        if (!isSilentRetry) historySilentRetryUsed = false
        if (userRetry) {
            historyCache.clearRateLimit(peerUserId, "USER_RETRY")
        }

        hideHistoryErrorUi("NEW_LOAD_STARTED")

        val cacheAvailable = historyCache.hasSnapshot(peerUserId)
        val rateRemain = historyCache.cooldownRemainMs(peerUserId)
        Log.d(
            CHAT_REOPEN_LOG,
            "HISTORY START req=$requestId peer=$peerUserId chatId=$chatId hadInFlightToCancel=$hadInFlightHistory " +
                "isSilentRetry=$isSilentRetry cacheAvailable=$cacheAvailable rateLimitActive=${rateRemain > 0} " +
                "rateLimitRemainMs=$rateRemain userRetry=$userRetry instance=${hashCode()}"
        )

        // Reset pagination state
        currentOffset = 0
        hasMoreMessages = true
        isLoadingMore = false
        isInitialHistoryLoading = true

        if (messages.isEmpty()) {
            val snap = historyCache.getSnapshot(peerUserId)
            if (snap != null) {
                val ageMs = historyCache.snapshotAgeMs(peerUserId)
                val before = messages.size
                messages.clear()
                messages.addAll(snap)
                updateTopHeader(messages)
                chatAdapter.notifyDataSetChanged()
                if (CHAT_REOPEN_VERBOSE) {
                    Log.d(
                        CHAT_REOPEN_LOG,
                        "UI ADAPTER notify peer=$peerUserId before=$before after=${messages.size} reason=CACHE_HYDRATE"
                    )
                }
                hideHistoryErrorUi("CACHE_HYDRATE")
                rvMessages.post {
                    if (messages.isNotEmpty()) {
                        rvMessages.scrollToPosition(messages.size - 1)
                        Log.d(
                            CHAT_REOPEN_LOG,
                            "UI SCROLL to=${messages.size - 1} reason=CACHE_HYDRATE peer=$peerUserId"
                        )
                    }
                }
                Log.d(CHAT_REOPEN_LOG, "CACHE HIT peer=$peerUserId count=${snap.size} ageMs=$ageMs")
            } else {
                Log.d(CHAT_REOPEN_LOG, "CACHE MISS peer=$peerUserId")
            }
        }

        if (historyCache.shouldSkipFetch(peerUserId)) {
            if (messages.isNotEmpty()) {
                isInitialHistoryLoading = false
                Log.d(
                    CHAT_REOPEN_LOG,
                    "RATE_LIMIT SKIP peer=$peerUserId cooldownRemainMs=${historyCache.cooldownRemainMs(peerUserId)} usedCache=true"
                )
                return
            }
            isInitialHistoryLoading = false
            showHistoryErrorUi("NO_CACHE_AND_RATE_LIMITED", -1)
            Log.w(
                CHAT_REOPEN_LOG,
                "RATE_LIMIT SKIP peer=$peerUserId cooldownRemainMs=${historyCache.cooldownRemainMs(peerUserId)} usedCache=false"
            )
            return
        }

        Log.d("ChatPagination", "═══════════════════════════════════════")
        Log.d("ChatPagination", "🔄 INITIAL LOAD - Requesting chat history (requestId=$requestId)")
        Log.d("ChatPagination", "User ID: $myUserId, Receiver ID: $peerUserId")
        Log.d("ChatPagination", "Limit: $MESSAGES_PER_PAGE, Offset: $currentOffset")
        Log.d("ChatPagination", "═══════════════════════════════════════")

        // CHAT-082: cache miss path → show shimmer until the network response
        // lands. With a cache hit, messages is already non-empty so the
        // skeleton doesn't flash.
        if (messages.isEmpty()) showChatLoadingSkeleton()

        val delayMs = if (messages.isNotEmpty()) historyCache.suggestedDelayMs() else 0L
        if (delayMs > 0L) {
            Log.d(CHAT_REOPEN_LOG, "THROTTLE DELAY peer=$peerUserId delayMs=$delayMs")
        }
        val scheduledAt = SystemClock.elapsedRealtime()
        val throttleRunnable = Runnable {
            pendingThrottleHistoryRunnable = null
            val actualDelay = SystemClock.elapsedRealtime() - scheduledAt
            if (delayMs > 0L && CHAT_REOPEN_VERBOSE) {
                Log.d(
                    CHAT_REOPEN_LOG,
                    "THROTTLE FIRE peer=$peerUserId expectedDelayMs=$delayMs actualDelayMs=$actualDelay"
                )
            }
            if (!isUiSafe()) {
                isInitialHistoryLoading = false
                if (CHAT_REOPEN_VERBOSE) {
                    Log.w(CHAT_REOPEN_LOG, "LIFECYCLE UI_UNSAFE skip event=throttle_history peer=$peerUserId")
                }
                return@Runnable
            }
            if (requestId != historyLoadRequestId.get()) return@Runnable
            enqueueHistoryNetworkRequest(requestId)
        }
        pendingThrottleHistoryRunnable = throttleRunnable
        if (delayMs > 0L) {
            mainHandler.postDelayed(throttleRunnable, delayMs)
        } else {
            throttleRunnable.run()
        }
    }

    private fun enqueueHistoryNetworkRequest(requestId: Int) {
        val fetchStartMs = SystemClock.elapsedRealtime()
        historyCache.recordFetchStarted()

        val historyCall = apiManager.getChatHistoryCancellable(
            userId = myUserId,
            receiverId = peerUserId,
            limit = MESSAGES_PER_PAGE,
            offset = currentOffset,
            object : NetworkCallback<ChatHistoryResponse> {
                override fun onResponse(call: Call<ChatHistoryResponse>, response: Response<ChatHistoryResponse>) {
                    val elapsedMs = SystemClock.elapsedRealtime() - fetchStartMs
                    if (requestId != historyLoadRequestId.get()) {
                        Log.d("ChatPagination", "Ignoring stale chat history response (requestId=$requestId, latest=${historyLoadRequestId.get()})")
                        Log.d(CHAT_REOPEN_LOG, "history STALE response dropped req=$requestId latest=${historyLoadRequestId.get()} peer=$peerUserId")
                        return
                    }
                    if (!isUiSafe()) {
                        Log.d("ChatPagination", "Ignoring chat history response — activity not safe")
                        isInitialHistoryLoading = false
                        if (CHAT_REOPEN_VERBOSE) {
                            Log.w(CHAT_REOPEN_LOG, "LIFECYCLE UI_UNSAFE skip event=history_onResponse peer=$peerUserId")
                        }
                        return
                    }
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        val retryAfter = response.headers()["Retry-After"]
                        Log.d(
                            CHAT_REOPEN_LOG,
                            "HISTORY RESPONSE req=$requestId peer=$peerUserId http=${response.code()} success=${responseBody?.success} " +
                                "elapsedMs=$elapsedMs retryAfterHeader=$retryAfter url=${call.request().url}"
                        )

                        // Verbose chat-history dump (message bodies, names, attachment urls).
                        // PII-heavy — debug builds only. Release smoke verifies no payloads in logcat.
                        if (BuildConfig.DEBUG) {
                            Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                            Log.d("chathisoryapi", "📥 COMPLETE CHAT HISTORY API RESPONSE")
                            Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                            Log.d("chathisoryapi", "HTTP Status Code: ${response.code()}")
                            Log.d("chathisoryapi", "Response Headers: ${response.headers()}")
                            Log.d("chathisoryapi", "Request URL: ${call.request().url}")
                            Log.d("chathisoryapi", "Request Method: ${call.request().method}")

                            if (responseBody != null) {
                                Log.d("chathisoryapi", "Response Success: ${responseBody.success}")
                                Log.d("chathisoryapi", "Response Message: ${responseBody.message}")

                                if (responseBody.data != null) {
                                    val data = responseBody.data
                                    Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                                    Log.d("chathisoryapi", "📊 RESPONSE DATA:")
                                    Log.d("chathisoryapi", "Chat ID: ${data.chatId}")
                                    Log.d("chathisoryapi", "User ID: ${data.userId}")
                                    Log.d("chathisoryapi", "Receiver ID: ${data.receiverId}")
                                    Log.d("chathisoryapi", "Total Messages: ${data.totalMessages}")
                                    Log.d("chathisoryapi", "Returned Messages: ${data.returnedMessages}")
                                    Log.d("chathisoryapi", "Limit: ${data.limit}")
                                    Log.d("chathisoryapi", "Offset: ${data.offset}")
                                    Log.d("chathisoryapi", "Has More: ${data.hasMore}")
                                    Log.d("chathisoryapi", "Last Online: ${data.lastOnline}")
                                    Log.d("chathisoryapi", "Last Online Status: ${data.lastOnlineStatus}")
                                    Log.d("chathisoryapi", "I Have Blocked This User: ${data.iHaveBlockedThisUser}")
                                    Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")

                                    val apiMessages = data.messages
                                    Log.d("chathisoryapi", "📨 MESSAGES COUNT: ${apiMessages.size}")
                                    Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")

                                    apiMessages.forEachIndexed { index, msg ->
                                        Log.d("chathisoryapi", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                        Log.d("chathisoryapi", "Message #${index + 1}:")
                                        Log.d("chathisoryapi", "  ID: ${msg.id}")
                                        Log.d("chathisoryapi", "  Chat ID: ${msg.chatId}")
                                        Log.d("chathisoryapi", "  From User ID: ${msg.fromUserId}")
                                        Log.d("chathisoryapi", "  From: ${msg.from}")
                                        Log.d("chathisoryapi", "  To User ID: ${msg.toUserId}")
                                        Log.d("chathisoryapi", "  To: ${msg.to}")
                                        Log.d("chathisoryapi", "  Message: ${msg.message}")
                                        Log.d("chathisoryapi", "  Message Type: ${msg.messageType}")
                                        Log.d("chathisoryapi", "  Attachment URL: ${msg.attachmentUrl ?: "null"}")
                                        Log.d("chathisoryapi", "  Is Read: ${msg.isRead}")
                                        Log.d("chathisoryapi", "  Timestamp: ${msg.timestamp}")
                                        Log.d("chathisoryapi", "  Created At: ${msg.createdAt ?: "null"}")
                                        Log.d("chathisoryapi", "  ⭐ Using for display: ${msg.createdAt ?: msg.timestamp}")
                                    }
                                    Log.d("chathisoryapi", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                                    Log.d("chathisoryapi", "✅ END OF CHAT HISTORY API RESPONSE")
                                    Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                                } else {
                                    Log.d("chathisoryapi", "⚠️ Response data is null")
                                }
                            } else {
                                Log.d("chathisoryapi", "⚠️ Response body is null")
                            }
                        }
                        
                        if (responseBody?.success == true && responseBody.data != null) {
                            val data = responseBody.data
                            val apiMessages = data.messages
                            
                            // Update blocked status (for UI display purposes)
                            iHaveBlockedThisUser = data.iHaveBlockedThisUser
                            // Peer-blocked-me direction (server-sourced).
                            // Triggers the dimmed-call + banner +
                            // send-intercept UI symmetric with I-blocked-them.
                            peerHasBlockedMe = data.thisUserHasBlockedMe
                            // T-CHAT-021: persist + refresh banner / composer
                            // / chat-list badge from the authoritative server flag.
                            applyBlockedUiState()
                            
                            Log.d("ChatPagination", "═══════════════════════════════════════")
                            Log.d("ChatPagination", "✅ INITIAL LOAD RESPONSE:")
                            Log.d("ChatPagination", "Total Messages: ${data.totalMessages}")
                            Log.d("ChatPagination", "Returned Messages: ${data.returnedMessages}")
                            Log.d("ChatPagination", "Has More: ${data.hasMore}")
                            Log.d("ChatPagination", "Offset: ${data.offset}")
                            Log.d("ChatPagination", "Limit: ${data.limit}")
                            Log.d("ChatPagination", "Messages received: ${apiMessages.size}")
                            Log.d("ChatPagination", "User blocked: $iHaveBlockedThisUser")
                            Log.d("ChatPagination", "═══════════════════════════════════════")
                            
                            // Log ALL messages from API response in order
                            Log.d("ChatPagination", "📋 API RESPONSE MESSAGES (Original Order from API):")
                            apiMessages.forEachIndexed { index, msg ->
                                Log.d("ChatPagination", "  [$index] ID=${msg.id}, ChatID=${msg.chatId}, From=${msg.fromUserId} (${msg.from}), To=${msg.toUserId} (${msg.to}), Type=${msg.messageType}, Text='${msg.message.take(50)}...', Timestamp=${msg.timestamp}, Read=${msg.isRead}")
                            }
                            Log.d("ChatPagination", "═══════════════════════════════════════")
                            
                            val convertedMessages = apiMessages.map { apiMsg ->
                                convertApiMessageToChatMessage(apiMsg)
                            }
                            
                            // Sort messages by date (oldest first) - WhatsApp style
                            // This ensures: index 0 = oldest message, index N = newest message
                            // With stackFromEnd=true, newest (index N) will appear at bottom, oldest (index 0) at top
                            val sortedMessages = convertedMessages.sortedBy { it.date?.time ?: 0L }

                            mergeServerMessagesPreservingPending(sortedMessages)
                            
                            chatAdapter.notifyDataSetChanged()
                            
                            // Log message order for debugging - show ALL sorted messages
                            Log.d("ChatPagination", "═══════════════════════════════════════")
                            Log.d("ChatPagination", "📋 SORTED MESSAGES (After sorting - should be oldest to newest):")
                            sortedMessages.forEachIndexed { index, msg ->
                                val msgId = if (msg.id.startsWith("temp_")) msg.id else msg.id
                                Log.d("ChatPagination", "  [$index] ID=$msgId, Text='${msg.message.take(40)}...', Timestamp=${msg.timestamp}, Date=${msg.date?.time}")
                            }
                            Log.d("ChatPagination", "═══════════════════════════════════════")
                            
                            if (sortedMessages.isNotEmpty()) {
                                Log.d("ChatPagination", "Message order check:")
                                Log.d("ChatPagination", "  First (index 0 - should be OLDEST): '${sortedMessages.first().message.take(30)}...' at ${sortedMessages.first().timestamp}, Date=${sortedMessages.first().date?.time}")
                                Log.d("ChatPagination", "  Last (index ${sortedMessages.size-1} - should be NEWEST): '${sortedMessages.last().message.take(30)}...' at ${sortedMessages.last().timestamp}, Date=${sortedMessages.last().date?.time}")
                            }
                            
                            // Update pagination state. After C1 the visible list may
                            // include older pages we've already paginated in; if we
                            // reset currentOffset to (offset + returnedMessages) the
                            // next loadMoreMessages() would refetch already-shown
                            // pages. Use the count of distinct server-confirmed
                            // messages currently in the list as the next offset.
                            val confirmedCount = messages
                                .count { !it.isDateHeader && !it.id.startsWith("temp_") }
                            currentOffset = maxOf(
                                confirmedCount,
                                data.offset + data.returnedMessages
                            )
                            hasMoreMessages = data.hasMore
                            
                            // Scroll to bottom (newest message) - WhatsApp style
                            if (messages.isNotEmpty()) {
                                rvMessages.post {
                                    rvMessages.scrollToPosition(messages.size - 1)
                                    Log.d("ChatPagination", "Scrolled to bottom, showing latest ${messages.size} messages")
                                }
                            }
                            
                            // CHAT-095: pass the raw `last_online` timestamp now (sourced
                            // from users.last_active_at server-side) so the header can
                            // render WhatsApp-style absolute timing. Falls back to the
                            // legacy buckets only if the timestamp is missing.
                            updateOnlineStatusFromAPI(data.lastOnline, data.lastOnlineStatus)
                            
                            // Mark messages as read using the new API with last message id
                            if (apiMessages.isNotEmpty()) {
                                // Get the last message id (newest message) from API messages
                                val lastMessageId = apiMessages
                                    .filter { it.fromUserId != myUserId }
                                    .maxByOrNull { it.id }
                                    ?.id
                                if (lastMessageId != null) {
                                    markMessagesAsReadWithLastMessageId(lastMessageId)
                                }
                            }
                            
                            // Mark messages as read (old method - keeping for backward compatibility)
                            if (isChatVisible) {
                                markMessagesAsRead()
                            }
                            
                            Log.d("ChatPagination", "✅ Initial load complete. Displaying ${messages.size} messages")
                            Log.d("ChatPagination", "Next offset for pagination: $currentOffset, Has more: $hasMoreMessages")
                            historyCache.putSnapshot(peerUserId, messages.toList())
                            historyCache.clearRateLimit(peerUserId, "SUCCESS")
                            hideHistoryErrorUi("MESSAGES_LOADED")
                            // T25: now that history is in the list, restore reply target if rotated mid-reply.
                            maybeApplyPendingRestoreReply()
                            Log.d(
                                CHAT_REOPEN_LOG,
                                "HISTORY LOADED req=$requestId peer=$peerUserId msgCount=${messages.size} hasMore=$hasMoreMessages fromCache=false elapsedMs=$elapsedMs"
                            )
                            isInitialHistoryLoading = false
                            runPendingPostInitialReloadIfNeeded()
                        } else {
                            isInitialHistoryLoading = false
                            if (messages.isEmpty()) {
                                showHistoryErrorUi("API_ERROR", response.code())
                                showAppToast("Failed to load messages", Toast.LENGTH_SHORT)
                            }
                        }
                    } else {
                        val code = response.code()
                        val errSnippet = try {
                            response.errorBody()?.string()?.take(120) ?: ""
                        } catch (_: Exception) {
                            ""
                        }
                        Log.e("ChatPagination", "❌ Error loading messages: $code")
                        Log.e("chathisoryapi", "❌ ERROR: HTTP $code")
                        Log.e("chathisoryapi", "Error Body: $errSnippet")
                        isInitialHistoryLoading = false
                        Log.e(
                            CHAT_REOPEN_LOG,
                            "HISTORY HTTP_ERROR req=$requestId code=$code peer=$peerUserId msgsInUi=${messages.size} retryUsed=$historySilentRetryUsed " +
                                "elapsedMs=$elapsedMs errorBody=$errSnippet"
                        )
                        val isTransient = code == 429 || code == 408 || code in 500..599
                        if (code == 429) {
                            val retryAfterHeader = response.headers()["Retry-After"]
                            val serverCooldownMs = ChatHistoryMemoryCache.parseRetryAfterMs(retryAfterHeader)
                            val cooldownMs = serverCooldownMs ?: ChatHistoryMemoryCache.DEFAULT_COOLDOWN_MS
                            val source = if (serverCooldownMs != null) "server(Retry-After=$retryAfterHeader)" else "default"
                            historyCache.recordRateLimit(peerUserId, cooldownMs, source)
                        }
                        if (isTransient && !historySilentRetryUsed && messages.isEmpty()) {
                            val delayMs = when (code) {
                                429 -> historyCache.cooldownRemainMs(peerUserId).coerceAtLeast(500L)
                                else -> 1500L
                            }
                            historySilentRetryUsed = true
                            mainHandler.removeCallbacks(retryHistoryRunnable)
                            mainHandler.postDelayed(retryHistoryRunnable, delayMs)
                            Log.d(
                                CHAT_REOPEN_LOG,
                                "history RETRY scheduled peer=$peerUserId code=$code delayMs=$delayMs reason=TRANSIENT_HTTP"
                            )
                        } else if (messages.isEmpty()) {
                            val msg = if (isTransient) getString(R.string.chat_history_error_generic) else "Failed to load messages"
                            Log.w(CHAT_REOPEN_LOG, "history GIVE_UP peer=$peerUserId code=$code retryUsed=$historySilentRetryUsed shown=\"$msg\"")
                            showHistoryErrorUi("HTTP_ERROR", code, msg)
                            if (!isTransient) {
                                showAppToast(msg, Toast.LENGTH_SHORT)
                            }
                        }
                    }
                    isLoadingMore = false
                }

                override fun onFailure(call: Call<ChatHistoryResponse>, t: Throwable) {
                    val elapsedMs = SystemClock.elapsedRealtime() - fetchStartMs
                    if (requestId != historyLoadRequestId.get()) {
                        Log.d("ChatPagination", "Ignoring stale chat history failure (requestId=$requestId)")
                        return
                    }
                    if (call.isCanceled) {
                        Log.d("ChatPagination", "History load cancelled")
                        Log.d(CHAT_REOPEN_LOG, "history CANCELLED req=$requestId peer=$peerUserId (expected if user left chat)")
                        isInitialHistoryLoading = false
                        return
                    }
                    if (!isUiSafe()) {
                        isInitialHistoryLoading = false
                        if (CHAT_REOPEN_VERBOSE) {
                            Log.w(CHAT_REOPEN_LOG, "LIFECYCLE UI_UNSAFE skip event=history_onFailure peer=$peerUserId")
                        }
                        return
                    }
                    isInitialHistoryLoading = false
                    val errClass = t.javaClass.simpleName
                    Log.e("ChatPagination", "❌ Error loading messages: ${t.message}", t)
                    Log.e(
                        CHAT_REOPEN_LOG,
                        "HISTORY FAILURE req=$requestId peer=$peerUserId elapsedMs=$elapsedMs errClass=$errClass errMsg=${t.message} " +
                            "isCanceled=${call.isCanceled} retry=${!historySilentRetryUsed && messages.isEmpty()}"
                    )
                    Log.e("chathisoryapi", "❌ NETWORK ERROR: ${t.message}")
                    Log.e("chathisoryapi", "Request URL: ${call.request().url}")
                    if (messages.isEmpty() && !historySilentRetryUsed) {
                        historySilentRetryUsed = true
                        mainHandler.removeCallbacks(retryHistoryRunnable)
                        mainHandler.postDelayed(retryHistoryRunnable, 1500L)
                        Log.d(CHAT_REOPEN_LOG, "history RETRY scheduled peer=$peerUserId code=-1 delayMs=1500 reason=NETWORK_FAILURE")
                    } else if (messages.isEmpty()) {
                        showHistoryErrorUi("NETWORK_FAILURE", -1, t.message)
                    }
                    isLoadingMore = false
                }

                override fun onNoNetwork() {
                    if (requestId != historyLoadRequestId.get()) {
                        Log.d("ChatPagination", "Ignoring stale chat history no-network (requestId=$requestId)")
                        return
                    }
                    if (!isUiSafe()) {
                        isInitialHistoryLoading = false
                        if (CHAT_REOPEN_VERBOSE) {
                            Log.w(CHAT_REOPEN_LOG, "LIFECYCLE UI_UNSAFE skip event=history_onNoNetwork peer=$peerUserId")
                        }
                        return
                    }
                    isInitialHistoryLoading = false
                    Log.e("ChatPagination", "❌ No network connection")
                    Log.e("chathisoryapi", "❌ NO NETWORK CONNECTION")
                    if (messages.isEmpty()) {
                        showHistoryErrorUi("NO_NETWORK", -1, getString(R.string.no_internet_connection))
                    } else {
                        showAppToast("No internet connection", Toast.LENGTH_SHORT)
                    }
                    isLoadingMore = false
                }
            }
        )
        if (historyCall == null) {
            isInitialHistoryLoading = false
            Log.w(CHAT_REOPEN_LOG, "HISTORY NO_NETWORK (getChatHistory returned null) req=$requestId peer=$peerUserId")
            if (messages.isEmpty()) {
                showHistoryErrorUi("NO_NETWORK", -1)
            }
        } else {
            Log.d(CHAT_REOPEN_LOG, "HISTORY ENQUEUED req=$requestId peer=$peerUserId url=${historyCall.request().url}")
        }
        currentHistoryCall = historyCall
    }
    
    private fun loadMoreMessages() {
        if (isLoadingMore || !hasMoreMessages) {
            Log.d("ChatPagination", "⏸️ Skipping load more - isLoadingMore: $isLoadingMore, hasMoreMessages: $hasMoreMessages")
            return
        }
        if (historyCache.shouldSkipFetch(peerUserId)) {
            Log.d(
                CHAT_REOPEN_LOG,
                "PAGINATION RATE_LIMIT SKIP peer=$peerUserId cooldownRemainMs=${historyCache.cooldownRemainMs(peerUserId)}"
            )
            return
        }
        
        isLoadingMore = true
        
        Log.d("ChatPagination", "═══════════════════════════════════════")
        Log.d("ChatPagination", "📜 LOADING MORE MESSAGES (Pagination)")
        Log.d("ChatPagination", "Current Offset: $currentOffset")
        Log.d("ChatPagination", "Current Messages Count: ${messages.size}")
        Log.d("ChatPagination", "Limit: $MESSAGES_PER_PAGE")
        Log.d("ChatPagination", "═══════════════════════════════════════")
        
        // Save current scroll position to restore after loading (WhatsApp style)
        val layoutManager = rvMessages.layoutManager as? LinearLayoutManager
        val currentFirstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
        val currentFirstVisibleView = layoutManager?.findViewByPosition(currentFirstVisiblePosition)
        val offset = currentFirstVisibleView?.top ?: 0
        
        Log.d("ChatPagination", "Current scroll position - First visible: $currentFirstVisiblePosition, Offset: $offset")

        val pageFetchStartMs = SystemClock.elapsedRealtime()
        historyCache.recordFetchStarted()
        val pageRequestId = paginationLoadRequestId.incrementAndGet()
        Log.d(
            CHAT_REOPEN_LOG,
            "PAGINATION START req=$pageRequestId peer=$peerUserId offset=$currentOffset limit=$MESSAGES_PER_PAGE instance=${hashCode()}"
        )
        currentMoreCall?.cancel()

        val moreCall = apiManager.getChatHistoryCancellable(
            userId = myUserId,
            receiverId = peerUserId,
            limit = MESSAGES_PER_PAGE,
            offset = currentOffset,
            object : NetworkCallback<ChatHistoryResponse> {
                override fun onResponse(call: Call<ChatHistoryResponse>, response: Response<ChatHistoryResponse>) {
                    val elapsedMs = SystemClock.elapsedRealtime() - pageFetchStartMs
                    if (pageRequestId != paginationLoadRequestId.get()) {
                        Log.d("ChatPagination", "Ignoring stale pagination response (pageRequestId=$pageRequestId)")
                        Log.d(CHAT_REOPEN_LOG, "PAGINATION STALE req=$pageRequestId latest=${paginationLoadRequestId.get()} peer=$peerUserId")
                        return
                    }
                    if (response.isSuccessful) {
                        Log.d(
                            CHAT_REOPEN_LOG,
                            "PAGINATION RESPONSE req=$pageRequestId peer=$peerUserId http=${response.code()} success=${response.body()?.success} " +
                                "elapsedMs=$elapsedMs url=${call.request().url}"
                        )
                        if (!isUiSafe()) {
                            Log.d("ChatPagination", "Ignoring pagination response — activity not safe")
                            isLoadingMore = false
                            return
                        }
                        val responseBody = response.body()
                        
                        // Verbose pagination dump (message bodies, urls). Debug builds only.
                        if (BuildConfig.DEBUG) {
                            Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                            Log.d("chathisoryapi", "📥 PAGINATION - CHAT HISTORY API RESPONSE")
                            Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                            Log.d("chathisoryapi", "HTTP Status Code: ${response.code()}")
                            Log.d("chathisoryapi", "Request URL: ${call.request().url}")

                            if (responseBody != null) {
                                Log.d("chathisoryapi", "Response Success: ${responseBody.success}")
                                Log.d("chathisoryapi", "Response Message: ${responseBody.message}")

                                if (responseBody.data != null) {
                                    val data = responseBody.data
                                    Log.d("chathisoryapi", "Chat ID: ${data.chatId}")
                                    Log.d("chathisoryapi", "Total Messages: ${data.totalMessages}")
                                    Log.d("chathisoryapi", "Returned Messages: ${data.returnedMessages}")
                                    Log.d("chathisoryapi", "Limit: ${data.limit}, Offset: ${data.offset}")
                                    Log.d("chathisoryapi", "Has More: ${data.hasMore}")

                                    val apiMessages = data.messages
                                    Log.d("chathisoryapi", "📨 MESSAGES COUNT: ${apiMessages.size}")

                                    apiMessages.forEachIndexed { index, msg ->
                                        Log.d("chathisoryapi", "Message #${index + 1}: ID=${msg.id}, From=${msg.fromUserId}, To=${msg.toUserId}, Text='${msg.message}', Timestamp=${msg.timestamp}, Created At=${msg.createdAt ?: "null"}, ⭐ Using: ${msg.createdAt ?: msg.timestamp}")
                                    }
                                    Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                                }
                            }
                        }
                        
                        if (responseBody?.success == true && responseBody.data != null) {
                            val data = responseBody.data
                            val apiMessages = data.messages
                            
                            Log.d("ChatPagination", "✅ PAGINATION RESPONSE:")
                            Log.d("ChatPagination", "Total Messages: ${data.totalMessages}")
                            Log.d("ChatPagination", "Returned Messages: ${data.returnedMessages}")
                            Log.d("ChatPagination", "Has More: ${data.hasMore}")
                            Log.d("ChatPagination", "Offset: ${data.offset}")
                            Log.d("ChatPagination", "Messages received: ${apiMessages.size}")
                            
                            // Log first few message IDs and timestamps
                            apiMessages.take(3).forEachIndexed { index, msg ->
                                Log.d("ChatPagination", "Older Message[$index]: ID=${msg.id}, Text='${msg.message.take(20)}...', Timestamp=${msg.timestamp}")
                            }
                            
                            if (apiMessages.isNotEmpty()) {
                                val convertedMessages = apiMessages.map { apiMsg ->
                                    convertApiMessageToChatMessage(apiMsg)
                                }
                                
                                // Sort older messages by date (oldest first)
                                // This ensures chronological order: oldest first, newest last
                                val sortedOlderMessages = convertedMessages.sortedBy { it.date?.time ?: 0L }
                                
                                val existingNonHeaders = messages.filterNot { it.isDateHeader }
                                val existingIds = existingNonHeaders.map { it.id }.toSet()
                                val oldestExistingTimestamp = existingNonHeaders
                                    .minOfOrNull { it.date?.time ?: Long.MAX_VALUE }
                                    ?: Long.MAX_VALUE
                                
                                // Filter out any messages that are newer than our oldest existing message
                                // (This prevents duplicates if API returns overlapping data)
                                val filteredOlderMessages = sortedOlderMessages.filter { msg ->
                                    val msgTime = msg.date?.time ?: 0L
                                    msg.id !in existingIds && msgTime < oldestExistingTimestamp
                                }
                                
                                if (filteredOlderMessages.isNotEmpty()) {
                                    val oldList = ArrayList(messages)
                                    val oldSize = oldList.size
                                    rebuildMessagesWithHeaders(existingNonHeaders + filteredOlderMessages)
                                    val newSize = messages.size
                                    val addedCount = newSize - oldSize

                                    // Smooth WhatsApp-style prepend: if the rebuild
                                    // only added rows at the FRONT (the existing tail
                                    // is byte-for-byte unchanged), use
                                    // notifyItemRangeInserted so RecyclerView keeps the
                                    // visible content anchored — no full rebind, no
                                    // scroll jump. If a date header reshuffled mid-list
                                    // (tail changed), fall back to the old
                                    // notifyDataSetChanged + manual scroll restore so
                                    // correctness is never at risk.
                                    val pureFrontPrepend = addedCount > 0 &&
                                        newSize >= addedCount &&
                                        messages.subList(addedCount, newSize) == oldList
                                    if (pureFrontPrepend) {
                                        chatAdapter.notifyItemRangeInserted(0, addedCount)
                                        // LinearLayoutManager auto-anchors the existing
                                        // first-visible row, so no manual scroll restore.
                                    } else {
                                        chatAdapter.notifyDataSetChanged()
                                        rvMessages.post {
                                            layoutManager?.scrollToPositionWithOffset(
                                                currentFirstVisiblePosition + addedCount,
                                                offset
                                            )
                                            Log.d("ChatPagination", "Restored scroll position - New position: ${currentFirstVisiblePosition + addedCount}")
                                        }
                                    }

                                    // Update pagination state
                                    currentOffset = data.offset + data.returnedMessages
                                    hasMoreMessages = data.hasMore
                                    
                                    Log.d("ChatPagination", "✅ Loaded ${filteredOlderMessages.size} older messages")
                                    Log.d("ChatPagination", "Total messages now: ${messages.size} (was $oldSize)")
                                    Log.d("ChatPagination", "Next offset: $currentOffset, Has more: $hasMoreMessages")
                                    historyCache.putSnapshot(peerUserId, messages.toList())
                                    Log.d(
                                        CHAT_REOPEN_LOG,
                                        "PAGINATION LOADED req=$pageRequestId peer=$peerUserId newCount=${filteredOlderMessages.size} " +
                                            "totalCount=${messages.size} hasMore=$hasMoreMessages elapsedMs=$elapsedMs"
                                    )
                                } else {
                                    Log.d("ChatPagination", "⚠️ All messages filtered (duplicates or newer than existing)")
                                    currentOffset = data.offset + data.returnedMessages
                                    hasMoreMessages = data.hasMore
                                }
                            } else {
                                hasMoreMessages = false
                                Log.d("ChatPagination", "✅ No more messages to load (empty response)")
                            }
                        }
                    } else {
                        val code = response.code()
                        Log.e("ChatPagination", "❌ Error loading more messages: $code")
                        Log.e("chathisoryapi", "❌ PAGINATION ERROR: HTTP $code")
                        Log.e("chathisoryapi", "Error Body: ${response.errorBody()?.string()}")
                        Log.e(
                            CHAT_REOPEN_LOG,
                            "PAGINATION HTTP_ERROR req=$pageRequestId peer=$peerUserId code=$code elapsedMs=$elapsedMs"
                        )
                        if (code == 429) {
                            val retryAfterHeader = response.headers()["Retry-After"]
                            val serverCooldownMs = ChatHistoryMemoryCache.parseRetryAfterMs(retryAfterHeader)
                            val cooldownMs = serverCooldownMs ?: ChatHistoryMemoryCache.DEFAULT_COOLDOWN_MS
                            val source = if (serverCooldownMs != null) {
                                "server(Retry-After=$retryAfterHeader)"
                            } else {
                                "default"
                            }
                            historyCache.recordRateLimit(peerUserId, cooldownMs, source)
                        }
                    }
                    isLoadingMore = false
                    // CHAT-084 follow-up: a reply-tap may be waiting for its
                    // original to be paged in — retry the scroll now that this
                    // page has landed. Posted so the adapter's notifyDataSetChanged
                    // + scroll-restore settle first.
                    rvMessages.post { maybeRetryPendingReplyScroll() }
                }

                override fun onFailure(call: Call<ChatHistoryResponse>, t: Throwable) {
                    val failElapsed = SystemClock.elapsedRealtime() - pageFetchStartMs
                    if (pageRequestId != paginationLoadRequestId.get()) return
                    if (call.isCanceled) {
                        Log.d(CHAT_REOPEN_LOG, "PAGINATION CANCELLED req=$pageRequestId peer=$peerUserId")
                        isLoadingMore = false
                        return
                    }
                    if (!isUiSafe()) {
                        isLoadingMore = false
                        return
                    }
                    Log.e("ChatPagination", "❌ Error loading more messages: ${t.message}", t)
                    Log.e("chathisoryapi", "❌ PAGINATION NETWORK ERROR: ${t.message}")
                    Log.e("chathisoryapi", "Request URL: ${call.request().url}")
                    Log.e(
                        CHAT_REOPEN_LOG,
                        "PAGINATION FAILURE req=$pageRequestId peer=$peerUserId errClass=${t.javaClass.simpleName} errMsg=${t.message} " +
                            "isCanceled=${call.isCanceled} elapsedMs=$failElapsed"
                    )
                    isLoadingMore = false
                }

                override fun onNoNetwork() {
                    if (pageRequestId != paginationLoadRequestId.get()) return
                    if (!isUiSafe()) {
                        isLoadingMore = false
                        return
                    }
                    Log.e("ChatPagination", "❌ No network connection")
                    Log.e("chathisoryapi", "❌ PAGINATION - NO NETWORK CONNECTION")
                    showAppToast("No internet connection", Toast.LENGTH_SHORT)
                    isLoadingMore = false
                }
            }
        )
        if (moreCall == null) {
            isLoadingMore = false
            Log.w(CHAT_REOPEN_LOG, "PAGINATION NO_NETWORK req=$pageRequestId peer=$peerUserId")
        } else {
            Log.d(CHAT_REOPEN_LOG, "PAGINATION ENQUEUED req=$pageRequestId peer=$peerUserId url=${moreCall.request().url}")
        }
        currentMoreCall = moreCall
    }

    private fun sendMessage() {
        if (!canSendMediaPayload()) return

        // Peer-blocked-me guard. The composer is already disabled when this
        // flag is true (applyBlockedUiState), so btnSend won't fire — but
        // sendMessage is also reachable via the IME send action and any
        // future callsite. Toast explicitly so the user understands why
        // nothing happens even if some path bypasses the disabled button.
        if (peerHasBlockedMe) {
            showAppToast(
                getString(R.string.chat_blocked_by_peer_toast),
                Toast.LENGTH_SHORT,
            )
            return
        }

        // Bug 8: stop emitting typing the moment we hand off a send. The
        // composer will clear in a moment anyway, but emitting an explicit
        // stop keeps the peer's "Typing..." indicator from lingering past
        // the message arrival.
        if (isCurrentlyEmittingTyping && chatId.isNotBlank()) {
            socketManager.sendTyping(chatId, false)
            isCurrentlyEmittingTyping = false
            typingStopRunnable?.let { mainHandler.removeCallbacks(it) }
            typingStopRunnable = null
        }

        val typed = etMessage.text.toString().trim()
        if (typed.isEmpty()) {
            // T24: still clear whitespace-only input so it doesn't linger after tap.
            etMessage.setText("")
            return
        }
        val replyRef = pendingReplyTo
        val bodyToSend = if (replyRef != null) {
            "${buildReplyHeaderLine(replyRef)}\n$typed"
        } else {
            typed
        }
        // T8: enforce the limit on the actual outgoing body (which may include the
        // reply header), not just the typed portion — otherwise a long quote +
        // typed text can blow past the backend cap and silently fail.
        if (bodyToSend.length > MAX_MESSAGE_LENGTH) {
            showAppToast(getString(R.string.chat_message_too_long, MAX_MESSAGE_LENGTH), Toast.LENGTH_SHORT)
            return
        }
        pendingReplyTo = null
        updateReplyPreviewUi()

        // Clear input immediately
        etMessage.setText("")
        etMessage.requestFocus()
        // CHAT-108: the typed text was just shipped — drop the persisted draft
        // so the next chat open / list-row preview doesn't resurrect it.
        if (peerUserId > 0) com.gmwapp.hima.utils.ChatDraftStore.clear(this, peerUserId)

        // Show message optimistically (WhatsApp style - add to bottom)
        val currentTime = Date()
        val tempMessage = ChatMessage(
            id = newTempMessageId(),
            message = bodyToSend,
            timestamp = timeFormat.format(currentTime),
            isSentByMe = true,
            date = currentTime,
            deliveryStatus = MessageDeliveryStatus.SENDING
        )
        
        appendMessageWithOptionalDateHeader(tempMessage)

        // Smooth scroll to bottom to show new message
        rvMessages.post {
            rvMessages.smoothScrollToPosition(messages.size - 1)
        }

        // Try Socket.IO first, fallback to API
        val tempMessageId = tempMessage.id
        rememberPendingOutgoing(tempMessageId, bodyToSend, "text")
        // Bug 10: if no ack arrives within SENDING_TIMEOUT_MS, flip to FAILED.
        scheduleSendingTimeout(tempMessageId)
        if (socketManager.isConnected()) {
            messageSendMethod[tempMessageId] = "socket"
            // ⭐ Updated to use new signature: sendMessage(fromUserId, toUserId, message, messageType, attachmentUrl)
            socketManager.sendMessage(myUserId, peerUserId, bodyToSend, "text")
            // Push is sent server-side for every message (socket saveMessage
            // always pushes; the NSE suppresses the heads-up when this chat is
            // open). The old client-side push here double-notified the peer.
            if (BuildConfig.DEBUG) {
                Log.d("SocketIOCheck", "🚀 Sending via SOCKET.IO - From: $myUserId, To: $peerUserId, Message: '$bodyToSend'")
            } else {
                Log.d("SocketIOCheck", "🚀 Sending via SOCKET.IO from=$myUserId to=$peerUserId len=${bodyToSend.length}")
            }
        } else {
            messageSendMethod[tempMessageId] = "api"
            Log.w("SocketIOCheck", "⚠️ Socket.IO NOT CONNECTED - Using fallback API")
            // Fallback to API
            sendMessageViaAPI(tempMessageId, bodyToSend)
        }
    }

    // firePeerMessageNotification / notificationBodyForType removed: the server
    // pushes once for every message on BOTH paths — the socket server's
    // saveMessage always pushes, and the REST fallback_send_message pushes too —
    // so the client-side push was a pure duplicate that double-notified the peer.

    private fun sendMessageViaAPI(tempId: String, messageText: String) {
        val apiCall = apiManager.fallbackSendMessage(
            fromUserId = myUserId,
            toUserId = peerUserId,
            message = messageText,
            callback = object : NetworkCallback<FallbackSendMessageResponse> {
                override fun onResponse(call: Call<FallbackSendMessageResponse>, response: Response<FallbackSendMessageResponse>) {
                    activeTextSendCalls.remove(call)
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true && responseBody.data?.message != null) {
                            // Replace temp message with real one
                            val fallbackMessage = responseBody.data.message
                            val realMessage = convertFallbackMessageToChatMessage(fallbackMessage)
                            val replaced = replaceTempMessage(tempId, realMessage, "api")
                            if (!replaced) {
                                Log.v("SocketIOCheck", "Fallback API returned after socket already replaced tempId=$tempId")
                                return
                            }
                            Log.d("SocketIOCheck", "Message sent via fallback API - ID: ${fallbackMessage.id}")
                            // fallback_send_message pushes server-side; the old
                            // client-side push here double-notified the peer.
                        } else {
                            failPendingOutgoing(
                                tempId,
                                responseBody?.message ?: "Couldn't send message"
                            )
                        }
                    } else {
                        failPendingOutgoing(tempId, "Couldn't send message")
                        Log.e("SocketIOCheck", "Failed to send message via fallback API: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<FallbackSendMessageResponse>, t: Throwable) {
                    activeTextSendCalls.remove(call)
                    if (call.isCanceled) return
                    failPendingOutgoing(tempId, "Couldn't send message")
                    Log.e("SocketIOCheck", "Failed to send message via fallback API: ${t.message}", t)
                }

                override fun onNoNetwork() {
                    failPendingOutgoing(tempId, DConstants.NO_NETWORK)
                    Log.e("SocketIOCheck", "No internet connection")
                }
            }
        )
        apiCall?.let { activeTextSendCalls.add(it) }
    }

    /**
     * Accept socket payloads when [chatId] matches or when participant IDs match this thread
     * (backend may emit a slightly different [ChatMessageSocket.chatId] string).
     */
    private fun isSocketMessageForThisChat(socketMessage: ChatMessageSocket): Boolean {
        if (socketMessage.chatId == chatId) return true
        val from = socketMessage.fromUserId ?: return false
        val to = socketMessage.toUserId ?: return false
        if (myUserId <= 0 || peerUserId <= 0) return false
        return (from == myUserId && to == peerUserId) ||
            (from == peerUserId && to == myUserId)
    }

    private fun handleNewMessage(socketMessage: ChatMessageSocket) {
        // Check if message is from this chat
        if (!isSocketMessageForThisChat(socketMessage)) {
            return
        }

        val realMessageId = socketMessage.id.toString()
        val isSentByMe = socketMessage.fromUserId == myUserId
        if (iHaveBlockedThisUser && !isSentByMe) {
            Log.d("RealtimeChat", "dropping inbound message because peer is blocked")
            return
        }

        // Check if we already have this message (by ID)
        val existingMessageIndex = messages.indexOfFirst { it.id == realMessageId }

        Log.d(
            "RealtimeChat",
            "handleNewMessage id=$realMessageId existingIdx=$existingMessageIndex isSentByMe=$isSentByMe"
        )
        
        if (existingMessageIndex != -1) {
            val existing = messages[existingMessageIndex]
            if (existing.isSentByMe &&
                socketMessage.isRead &&
                existing.deliveryStatus != MessageDeliveryStatus.READ
            ) {
                messages[existingMessageIndex] =
                    existing.copy(deliveryStatus = MessageDeliveryStatus.READ)
                chatAdapter.notifyItemChanged(existingMessageIndex)
            }
            return
        }
        
        // Check if there's a temp message that should be replaced
        // This happens when we send a message and it comes back via Socket.IO
        val tempMessageIndex = if (isSentByMe) {
            findPendingTempIndexForSocket(socketMessage)
        } else {
            -1
        }
        
        var chatMessage = convertSocketMessageToChatMessage(socketMessage)
        if (tempMessageIndex != -1 && isSentByMe) {
            chatMessage = chatMessage.copy(
                deliveryStatus = if (socketMessage.isRead) {
                    MessageDeliveryStatus.READ
                } else {
                    MessageDeliveryStatus.SENT
                }
            )
        }

        if (tempMessageIndex != -1) {
            // Replace temp message with real one
            val tempId = messages[tempMessageIndex].id
            replaceTempMessage(tempId, chatMessage, "socket")
            messageSendMethod[realMessageId] = "socket"
            Log.d("ChatActivityInHouse", "Replaced temp message with real message ID: $realMessageId")
        } else {
            val wasNearBottom = isRecyclerNearBottom()
            insertMessageChronologically(chatMessage)

            if (isSentByMe || wasNearBottom) {
                rvMessages.post {
                    rvMessages.smoothScrollToPosition(messages.size - 1)
                }
            } else {
                unseenIncomingCount += 1
                updateNewMessagePill()
            }
        }

        // Mark as read if chat is visible
        if (isChatVisible) {
            markMessagesAsRead()
        }
    }

    private fun convertApiMessageToChatMessage(apiMsg: ChatMessageApi): ChatMessage {
        // Use created_at for accurate message time, fallback to timestamp if created_at is null
        val timestampString = apiMsg.createdAt ?: apiMsg.timestamp
        val timestamp = parseTimestamp(timestampString)
        val isSentByMe = apiMsg.fromUserId == myUserId
        val isDeleted = (apiMsg.isDeleted ?: 0) == 1

        // Deleted rows carry no reactions / attachments on the client — the tombstone
        // is a blank slate regardless of what the backend echoes back.
        val reactionsMap = if (isDeleted) {
            emptyMap()
        } else {
            apiMsg.reactions?.associate { it.userId to it.reactionEmoji } ?: emptyMap()
        }

        Log.d("ChatTimeFix", "Message ID: ${apiMsg.id}, Using: ${if (apiMsg.createdAt != null) "created_at" else "timestamp"}, Value: $timestampString")

        // CHAT-025: outgoing-but-unread messages are SENT (single tick), not
        // DELIVERED — skipping straight to double-tick hides genuine delivery
        // failures and breaks parity with every other messaging app.
        // The !isSentByMe branch is a no-op visually (adapter ignores
        // deliveryStatus for inbound rows) but kept for completeness.
        val deliveryStatus = when {
            !isSentByMe -> MessageDeliveryStatus.DELIVERED
            apiMsg.isRead -> MessageDeliveryStatus.READ
            else -> MessageDeliveryStatus.SENT
        }
        return ChatMessage(
            id = apiMsg.id.toString(),
            message = apiMsg.message,
            timestamp = timeFormat.format(timestamp),
            isSentByMe = isSentByMe,
            date = timestamp,
            reactions = reactionsMap,
            messageType = apiMsg.messageType,
            attachmentUrl = if (isDeleted) null else apiMsg.attachmentUrl,
            // CHAT-034: surface stored duration from chat_history so the bubble
            // can render the length without a MediaPlayer.prepare() round-trip.
            audioDurationMs = apiMsg.audioDurationMs ?: 0L,
            deliveryStatus = deliveryStatus,
            isDeleted = isDeleted
        )
    }

    private fun convertSocketMessageToChatMessage(socketMsg: ChatMessageSocket): ChatMessage {
        val timestamp = parseTimestamp(socketMsg.timestamp)
        val isSentByMe = socketMsg.fromUserId == myUserId
        
        // Convert reactions from Socket to Map<userId, emoji>
        val reactionsMap = mutableMapOf<Int, String>()
        socketMsg.reactions?.forEach { reactionMap ->
            val userId = (reactionMap["user_id"] as? Number)?.toInt() ?: return@forEach
            val emoji = reactionMap["reaction_emoji"] as? String ?: return@forEach
            reactionsMap[userId] = emoji
        }
        
        // CHAT-025: socket-echoed outgoing message is SENT (single tick),
        // not DELIVERED. See convertApiMessageToChatMessage for rationale.
        val deliveryStatus = when {
            !isSentByMe -> MessageDeliveryStatus.DELIVERED
            socketMsg.isRead -> MessageDeliveryStatus.READ
            else -> MessageDeliveryStatus.SENT
        }
        return ChatMessage(
            id = socketMsg.id.toString(),
            message = socketMsg.message,
            timestamp = timeFormat.format(timestamp),
            isSentByMe = isSentByMe,
            date = timestamp,
            reactions = reactionsMap,
            messageType = socketMsg.messageType,
            attachmentUrl = socketMsg.attachmentUrl,
            // CHAT-034: pass through server-stored duration; 0L keeps the
            // existing "--:--" fallback when the server didn't (yet) record it.
            audioDurationMs = socketMsg.audioDurationMs ?: 0L,
            deliveryStatus = deliveryStatus,
            // T6: carry through the server's tombstone flag so a socket-only delivery
            // (no API refresh in flight) renders the deleted-bubble state immediately.
            isDeleted = socketMsg.isDeleted
        )
    }

    private fun convertFallbackMessageToChatMessage(fallbackMsg: com.gmwapp.hima.retrofit.responses.FallbackMessage): ChatMessage {
        // Use created_at for accurate message time, fallback to timestamp if created_at is null
        val timestampString = fallbackMsg.createdAt ?: fallbackMsg.timestamp
        val timestamp = parseTimestamp(timestampString)
        val isSentByMe = fallbackMsg.fromUserId == myUserId
        
        // Fallback messages don't include reactions, use empty map
        val reactionsMap = emptyMap<Int, String>()
        
        Log.d("ChatTimeFix", "Fallback Message ID: ${fallbackMsg.id}, Using: ${if (fallbackMsg.createdAt != null) "created_at" else "timestamp"}, Value: $timestampString")
        
        // CHAT-025: REST-fallback outgoing message is SENT (single tick),
        // not DELIVERED. See convertApiMessageToChatMessage for rationale.
        val deliveryStatus = when {
            !isSentByMe -> MessageDeliveryStatus.DELIVERED
            fallbackMsg.isRead -> MessageDeliveryStatus.READ
            else -> MessageDeliveryStatus.SENT
        }
        return ChatMessage(
            id = fallbackMsg.id.toString(),
            message = fallbackMsg.message,
            timestamp = timeFormat.format(timestamp),
            isSentByMe = isSentByMe,
            date = timestamp,
            reactions = reactionsMap,
            messageType = fallbackMsg.messageType,
            attachmentUrl = fallbackMsg.attachmentUrl,
            deliveryStatus = deliveryStatus
        )
    }

    private fun parseTimestamp(timestampString: String): Date {
        return try {
            val parsed = dateFormat.parse(timestampString)
            if (parsed == null) {
                Log.e("ChatPagination", "⚠️ Failed to parse timestamp: '$timestampString' - returned null, using current time")
                Date()
            } else {
                parsed
            }
        } catch (e: Exception) {
            Log.e("ChatPagination", "❌ Error parsing timestamp: '$timestampString' - ${e.message}, using current time")
            Date()
        }
    }

    // T18: use the device's local timezone instead of hardcoding IST so a user
    // outside Asia/Kolkata sees consistent "Today"/"Yesterday" headers and the
    // chat-list timestamps line up with the thread headers.
    private fun isSameDay(date1: Date?, date2: Date?): Boolean {
        if (date1 == null || date2 == null) return false
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(today: Calendar, messageDate: Calendar): Boolean {
        val yesterday = today.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return yesterday.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
               yesterday.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR)
    }

    private fun getDateHeaderText(date: Date): String {
        val today = Calendar.getInstance()
        val messageDate = Calendar.getInstance().apply { time = date }

        return when {
            isSameDay(today.time, date) -> getString(R.string.chat_date_today)
            isYesterday(today, messageDate) -> getString(R.string.chat_date_yesterday)
            else -> {
                val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                dateFormat.format(date)
            }
        }
    }

    /** Last real message in the list (skips date headers). Used for incremental bottom appends. */
    private fun lastNonHeaderMessage(): ChatMessage? {
        var i = messages.size - 1
        while (i >= 0) {
            if (!messages[i].isDateHeader) return messages[i]
            i--
        }
        return null
    }

    private fun sortedChatMessages(source: List<ChatMessage>): List<ChatMessage> {
        return source
            .filterNot { it.isDateHeader }
            .sortedWith(
                compareBy<ChatMessage> { it.date?.time ?: Long.MAX_VALUE }
                    .thenBy { it.id.removePrefix("temp_").toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.id }
            )
    }

    private fun rebuildMessagesWithHeaders(source: List<ChatMessage>) {
        messages.clear()
        // CHAT-138: drop anything the user "Deleted for me" on this device.
        val filtered = filterOutClearedBeforeWatermark(filterOutLocallyDeleted(source))
        // CHAT-030: drop orphan SENDING temps whose server-confirmed twin is
        // already in the list. Stops the bubble spinner from sticking while
        // the chat list (which reads server state) already shows delivered.
        val deduped = dropAbsorbedSendingTemps(sortedChatMessages(filtered))
        messages.addAll(deduped)
        updateTopHeader(messages)
    }

    /**
     * CHAT-030: reconciliation safety net for the optimistic-temp lifecycle.
     *
     * Strict content-match in [findPendingTempIndexForSocket] /
     * [pendingPayloadMatchesMessage] can miss when the server normalises the
     * message body (e.g. trimming, smart-quote substitution, CRLF→LF). When
     * the match misses, the server-confirmed row is inserted as a new entry
     * and the temp_ row stays in SENDING — visible only as a stuck spinner
     * while the chat-list screen (which reads server state) shows the
     * message as delivered. This pass runs on every list rebuild: for each
     * SENDING temp_ owned by the local user, if a confirmed (non-temp) row
     * with the same messageType + body/attachmentUrl already exists, drop
     * the temp and release its pending bookkeeping.
     */
    private fun dropAbsorbedSendingTemps(list: List<ChatMessage>): List<ChatMessage> {
        val hasOrphanCandidate = list.any { m ->
            m.isSentByMe && !m.isDateHeader &&
                m.id.startsWith("temp_") &&
                m.deliveryStatus == MessageDeliveryStatus.SENDING
        }
        if (!hasOrphanCandidate) return list

        val confirmedKeys = HashSet<String>()
        list.forEach { m ->
            if (m.isSentByMe && !m.isDateHeader && !m.id.startsWith("temp_")) {
                confirmedReconcileKey(m)?.let { confirmedKeys.add(it) }
            }
        }
        if (confirmedKeys.isEmpty()) return list

        val survivors = ArrayList<ChatMessage>(list.size)
        list.forEach { m ->
            val isOrphan = m.isSentByMe && !m.isDateHeader &&
                m.id.startsWith("temp_") &&
                m.deliveryStatus == MessageDeliveryStatus.SENDING
            if (isOrphan) {
                val key = confirmedReconcileKey(m)
                if (key != null && confirmedKeys.contains(key)) {
                    pendingOutgoingByTempId.remove(m.id)
                    messageSendMethod.remove(m.id)
                    Log.d(
                        "ChatDelivery",
                        "CHAT-030 dropped orphan SENDING temp ${m.id} — server-confirmed twin already in list"
                    )
                    return@forEach
                }
            }
            survivors.add(m)
        }
        return survivors
    }

    /**
     * CHAT-030: reconciliation key. Trim text bodies so server-side whitespace
     * normalisation doesn't keep an orphan alive. Media rows match on the
     * resolved remote URL — the temp's url is set to the remote one by
     * [uploadAndSendAttachment] before the socket emit, so this lines up.
     */
    private fun confirmedReconcileKey(m: ChatMessage): String? {
        val type = m.messageType.lowercase()
        return when (type) {
            "image", "audio" -> {
                val url = m.attachmentUrl
                if (url.isNullOrBlank()) null else "$type::$url"
            }
            else -> "$type::${m.message.trim()}"
        }
    }

    /**
     * CHAT-138: removes messages whose IDs are in this user's local
     * "Delete for me" set for the current peer. Date headers always pass.
     */
    private fun filterOutLocallyDeleted(source: List<ChatMessage>): List<ChatMessage> {
        if (myUserId <= 0 || peerUserId <= 0) return source
        val deletedSet = LocallyDeletedMessagesStore.getAll(this, myUserId, peerUserId)
        if (deletedSet.isEmpty()) return source
        return source.filter { it.isDateHeader || !deletedSet.contains(it.id) }
    }

    private fun pendingPayloadMatchesMessage(
        pending: ChatMessage,
        confirmed: ChatMessage
    ): Boolean {
        if (!pending.isSentByMe || !confirmed.isSentByMe) return false
        if (pending.messageType != confirmed.messageType) return false
        return when (pending.messageType.lowercase()) {
            "image", "audio" -> {
                val pendingRemote = pendingOutgoingByTempId[pending.id]?.attachmentUrl
                !pendingRemote.isNullOrBlank() && pendingRemote == confirmed.attachmentUrl
            }
            else -> pending.message == confirmed.message
        }
    }

    /**
     * Folds a fresh server page into the live list **without** wiping previously
     * loaded older pages or pending optimistic temps.
     *
     * The old behavior — `merged = serverMessages.toMutableList()` plus pending —
     * truncated the list to the latest 10 messages on every refresh (resume,
     * push-triggered reload, etc.), so a user who scrolled up to load 50 messages
     * would lose pages 2..N as soon as the activity refreshed (C1).
     *
     * The fix: keep every existing non-header server-confirmed message that is
     * NOT in the new page, then add the new page on top. Pending temps still get
     * reconciled exactly as before — matched server messages absorb them, the
     * rest tag along until the server confirms.
     */
    private fun mergeServerMessagesPreservingPending(serverMessages: List<ChatMessage>) {
        val newServerIds = serverMessages.asSequence().map { it.id }.toHashSet()
        val existingNonHeader = messages.filterNot { it.isDateHeader }
        val existingTemps = existingNonHeader
            .filter { it.id.startsWith("temp_") && pendingOutgoingByTempId.containsKey(it.id) }
        val existingConfirmed = existingNonHeader
            .filter { !it.id.startsWith("temp_") && it.id !in newServerIds }

        // Seed: prior pages we already loaded + the fresh page. Order will be sorted below.
        val merged = mutableListOf<ChatMessage>()
        merged.addAll(existingConfirmed)
        merged.addAll(serverMessages)

        // Reconcile pending optimistic sends against the new page. Anything matched
        // is absorbed (server copy wins); unmatched temps stay in the list.
        val matchedServerIndexes = mutableSetOf<Int>()
        existingTemps.forEach { pending ->
            val serverIndex = serverMessages.indexOfFirst { confirmed ->
                val index = serverMessages.indexOf(confirmed)
                index !in matchedServerIndexes && pendingPayloadMatchesMessage(pending, confirmed)
            }
            if (serverIndex == -1) {
                merged.add(pending)
            } else {
                matchedServerIndexes.add(serverIndex)
                pendingOutgoingByTempId.remove(pending.id)
                messageSendMethod.remove(pending.id)
            }
        }

        // Dedupe by id (a message could exist in both `existingConfirmed` and the
        // server page if a previous page overlapped — defensive even though the
        // `id !in newServerIds` filter above should already cover it). Then sort
        // by date asc, with numeric-id as the tiebreaker for same-millisecond rows.
        val dedupedById = LinkedHashMap<String, ChatMessage>(merged.size)
        merged.forEach { m ->
            val existing = dedupedById[m.id]
            if (existing == null || existing.id.startsWith("temp_")) {
                dedupedById[m.id] = m
            }
        }
        val sorted = dedupedById.values.sortedWith(
            compareBy<ChatMessage> { it.date?.time ?: 0L }
                .thenBy { it.id.toLongOrNull() ?: Long.MAX_VALUE }
        )

        // T7: when this user has blocked the peer, the API still returns the
        // peer's incoming messages. Drop them so a blocked thread shows only
        // the user's own outgoing history (parity with the socket-side filter).
        val finalList = if (iHaveBlockedThisUser) sorted.filter { it.isSentByMe } else sorted
        rebuildMessagesWithHeaders(finalList)
    }

    private fun findPendingTempIndexForSocket(socketMessage: ChatMessageSocket): Int {
        val tempId = pendingOutgoingByTempId.entries.firstOrNull { (_, pending) ->
            pending.messageType == socketMessage.messageType &&
                when (socketMessage.messageType.lowercase()) {
                    "image", "audio" -> pending.attachmentUrl == socketMessage.attachmentUrl
                    else -> pending.message == socketMessage.message
                }
        }?.key ?: return -1
        return messages.indexOfFirst { it.id == tempId }
    }

    private fun insertMessageChronologically(message: ChatMessage) {
        val merged = messages.filterNot { it.isDateHeader }.toMutableList()
        val existingIndex = merged.indexOfFirst { it.id == message.id }
        if (existingIndex != -1) {
            merged[existingIndex] = message
        } else {
            merged.add(message)
        }
        rebuildMessagesWithHeaders(merged)
        chatAdapter.notifyDataSetChanged()
    }

    private fun newTempMessageId(): String =
        "temp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

    private fun isRecyclerNearBottom(): Boolean {
        if (messages.isEmpty()) return true
        val layoutManager = rvMessages.layoutManager as? LinearLayoutManager ?: return true
        val lastIndex = messages.size - 1
        // CHAT-119: "near bottom" must mean the very last row's bottom edge is
        // on screen — not just that any pixel of the last 2 rows is poking out.
        // The old `lastVisible >= size - 2` returned true while the user was
        // reading the second-to-last bubble, so any incoming message yanked
        // them out of place instead of surfacing the "N new messages" pill.
        val lastFullyVisible = layoutManager.findLastCompletelyVisibleItemPosition()
        if (lastFullyVisible == lastIndex) return true
        // Layout-in-flight fallback: if the absolute last row is partly visible
        // AND RecyclerView reports no content below the viewport, we are at the
        // bottom for all practical purposes.
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (lastVisible == RecyclerView.NO_POSITION) return true
        return lastVisible == lastIndex && !rvMessages.canScrollVertically(1)
    }

    private fun updateNewMessagePill() {
        val count = unseenIncomingCount.coerceAtLeast(0)
        val pill = btnNewMessages ?: return
        if (count == 0) {
            pill.visibility = View.GONE
            return
        }
        pill.text = resources.getQuantityString(R.plurals.chat_new_messages, count, count)
        pill.visibility = View.VISIBLE
    }

    private fun clearNewMessagePill() {
        unseenIncomingCount = 0
        updateNewMessagePill()
    }

    private fun scrollToBottomAndClearNewMessagePill() {
        if (messages.isNotEmpty()) {
            rvMessages.smoothScrollToPosition(messages.size - 1)
        }
        clearNewMessagePill()
    }

    private fun runPendingPostInitialReloadIfNeeded() {
        if (!pendingPostInitialReload) return
        pendingPostInitialReload = false
        if (isUiSafe() && myUserId > 0 && peerUserId > 0) {
            Log.d("RealtimeChat", "running deferred push-refresh reload after initial history")
            loadMessages()
        }
    }

    private fun cancelInFlightSendsForBlock() {
        val pendingIds = pendingOutgoingByTempId.keys.toList()
        activeTextSendCalls.forEach { it.cancel() }
        activeTextSendCalls.clear()
        activeAttachmentCalls.values.forEach { it.cancel() }
        activeAttachmentCalls.clear()
        activeAttachmentTempIds.clear()
        pendingIds.forEach { removeTempMessage(it) }
        if (pendingIds.isNotEmpty()) {
            showAppToast(getString(R.string.chat_send_canceled), Toast.LENGTH_SHORT)
        }
    }

    private fun resetConversationStateForPeerChange() {
        currentHistoryCall?.cancel()
        currentMoreCall?.cancel()
        pendingThrottleHistoryRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingThrottleHistoryRunnable = null
        pendingPostInitialReload = false
        isInitialHistoryLoading = false
        isLoadingMore = false
        hasMoreMessages = true
        currentOffset = 0
        pendingReplyTo = null
        updateReplyPreviewUi()
        pendingOutgoingByTempId.clear()
        messageSendMethod.clear()
        // T2: peer-switch must also drop session-scoped flags so chat B does not
        // inherit chat A's read/retry/banner state.
        markedReadOnce = false
        lastMarkedReadMessageId = null
        historySilentRetryUsed = false
        suppressNextResumeHistoryReload = true
        isAddFriendBannerDismissedThisSession = false
        activeAttachmentTempIds.clear()
        messages.clear()
        clearNewMessagePill()
        if (::chatAdapter.isInitialized) {
            chatAdapter.notifyDataSetChanged()
        }
        // T-CHAT-021: re-seed blocked-state from prefs for the new peer so the
        // banner / composer can't carry over from chat A into chat B.
        iHaveBlockedThisUser = if (peerUserId > 0) {
            com.gmwapp.hima.utils.BlockedPeersPrefsHelper
                .isBlocked(this, peerUserId.toString())
        } else {
            false
        }
        applyBlockedUiState()
    }

    private fun latestReceivedMessageId(): String? =
        messages
            .asSequence()
            .filterNot { it.isDateHeader }
            .filterNot { it.isSentByMe }
            .mapNotNull { it.id.toLongOrNull()?.takeIf { id -> id > 0L } }
            .maxOrNull()
            ?.toString()

    /**
     * Append one message at the bottom with at most one new date header (no full list rebuild).
     * For bulk loads use [updateTopHeader] + range/full notify instead.
     */
    private fun appendMessageWithOptionalDateHeader(newMsg: ChatMessage) {
        // CHAT-138: if the user already deleted this messageId for themselves
        // on this device, don't let it come back via a socket replay or chat
        // history pagination. Date headers and pending optimistic rows skip.
        if (!newMsg.isDateHeader && !isPendingMessage(newMsg) && newMsg.id.isNotBlank() &&
            myUserId > 0 && peerUserId > 0 &&
            LocallyDeletedMessagesStore.isLocallyDeleted(this, myUserId, peerUserId, newMsg.id)
        ) {
            return
        }
        val prev = lastNonHeaderMessage()
        if (prev == null) {
            if (newMsg.date != null) {
                val header = ChatMessage(
                    id = "header_top_${newMsg.date!!.time}_${newMsg.id}",
                    message = "",
                    timestamp = "",
                    isSentByMe = false,
                    date = newMsg.date,
                    isDateHeader = true,
                    dateHeaderText = getDateHeaderText(newMsg.date!!)
                )
                messages.add(header)
                messages.add(newMsg)
                chatAdapter.notifyItemRangeInserted(messages.size - 2, 2)
            } else {
                messages.add(newMsg)
                chatAdapter.notifyItemInserted(messages.size - 1)
            }
            return
        }
        val needBoundary =
            newMsg.date != null &&
                prev.date != null &&
                !isSameDay(prev.date, newMsg.date)
        if (needBoundary) {
            val header = ChatMessage(
                id = "header_${newMsg.date!!.time}_${newMsg.id}",
                message = "",
                timestamp = "",
                isSentByMe = false,
                date = newMsg.date,
                isDateHeader = true,
                dateHeaderText = getDateHeaderText(newMsg.date!!)
            )
            messages.add(header)
            messages.add(newMsg)
            chatAdapter.notifyItemRangeInserted(messages.size - 2, 2)
        } else {
            messages.add(newMsg)
            chatAdapter.notifyItemInserted(messages.size - 1)
        }
    }

    // Function to add/update header at the top of messages and at date boundaries
    private fun updateTopHeader(messages: MutableList<ChatMessage>) {
        // Remove all existing headers first
        messages.removeAll { it.isDateHeader }
        
        if (messages.isEmpty()) return
        
        // Get the first actual message (not header)
        val firstMessage = messages.firstOrNull()
        
        if (firstMessage != null && firstMessage.date != null) {
            // Add header at position 0 (top)
            val header = ChatMessage(
                id = "header_top_${firstMessage.date!!.time}",
                message = "",
                timestamp = "",
                isSentByMe = false,
                date = firstMessage.date,
                isDateHeader = true,
                dateHeaderText = getDateHeaderText(firstMessage.date!!)
            )
            messages.add(0, header)
        }
        
        // Now add headers at date boundaries (where dates change)
        var i = 1 // Start from 1 (skip the top header)
        while (i < messages.size) {
            val currentMessage = messages[i]
            val nextMessage = messages.getOrNull(i + 1)
            
            // Skip if current is a header
            if (currentMessage.isDateHeader) {
                i++
                continue
            }
            
            val currentDate = currentMessage.date
            val nextDate = nextMessage?.date
            
            // If dates are different, insert header for the NEXT date AFTER current message
            if (currentDate != null && nextDate != null && !isSameDay(currentDate, nextDate)) {
                // Add header for the NEXT date (not current date)
                val header = ChatMessage(
                    id = "header_${nextDate.time}",
                    message = "",
                    timestamp = "",
                    isSentByMe = false,
                    date = nextDate,
                    isDateHeader = true,
                    dateHeaderText = getDateHeaderText(nextDate)
                )
                messages.add(i + 1, header)
                i += 2 // Skip both message and header
            } else {
                i++
            }
        }
    }

    /** T20: cooldown so a burst of incoming messages doesn't spawn one mark-read call each. */
    private var lastMarkedChatReadAt: Long = 0L
    private val markChatReadCooldownMs = 2_000L

    private fun markMessagesAsRead() {
        // T20: skip if the same call fired within the cooldown window.
        val now = SystemClock.elapsedRealtime()
        if (now - lastMarkedChatReadAt < markChatReadCooldownMs) {
            return
        }
        lastMarkedChatReadAt = now
        apiManager.markRead(
            userId = myUserId,
            chatId = chatId,
            object : NetworkCallback<MarkReadResponse> {
                override fun onResponse(call: Call<MarkReadResponse>, response: Response<MarkReadResponse>) {
                    if (response.isSuccessful) {
                        Log.d("ChatActivityInHouse", "Messages marked as read")
                    } else {
                        Log.e("ChatActivityInHouse", "Error marking as read: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<MarkReadResponse>, t: Throwable) {
                    Log.e("ChatActivityInHouse", "Error marking as read: ${t.message}")
                }

                override fun onNoNetwork() {
                    // Silent fail for read status
                }
            }
        )
    }

    private fun markMessagesAsReadWithLastMessageId(lastMessageId: Long) {
        // T20: skip when the server already knows about this id (or a newer one).
        val alreadyMarked = lastMarkedReadMessageId?.toLongOrNull()
        if (alreadyMarked != null && lastMessageId <= alreadyMarked) {
            return
        }
        lastMarkedReadMessageId = lastMessageId.toString()
        apiManager.markMessagesRead(
            userId = myUserId,
            receiverId = peerUserId,
            lastMessageId = lastMessageId,
            object : NetworkCallback<MarkMessagesReadResponse> {
                override fun onResponse(call: Call<MarkMessagesReadResponse>, response: Response<MarkMessagesReadResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true) {
                            Log.d("ChatActivityInHouse", "Messages marked as read successfully. Last message ID: $lastMessageId")
                            responseBody.data?.let { data ->
                                Log.d("ChatActivityInHouse", "Messages marked read: ${data.messagesMarkedRead}, Remaining unread: ${data.remainingUnreadCount}")
                            }
                        } else {
                            Log.e("ChatActivityInHouse", "Error marking messages as read: ${responseBody?.message}")
                        }
                    } else {
                        Log.e("ChatActivityInHouse", "Error marking messages as read: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<MarkMessagesReadResponse>, t: Throwable) {
                    Log.e("ChatActivityInHouse", "Error marking messages as read: ${t.message}")
                }

                override fun onNoNetwork() {
                    // Silent fail for read status
                }
            }
        )
    }

    /**
     * Helper method to mark messages as read using the last message ID from loaded messages
     * This is called when user enters or leaves the activity
     */
    private fun markMessagesAsReadIfAvailable() {
        if (messages.isNotEmpty() && myUserId > 0 && peerUserId > 0) {
            // T4: messages with snowflake ids overflow Int — use Long.
            val lastRealMessage = messages
                .asSequence()
                .filterNot { it.isDateHeader }
                .filterNot { it.isSentByMe }
                .mapNotNull { msg -> msg.id.toLongOrNull()?.takeIf { it > 0L } }
                .maxOrNull()
            if (lastRealMessage != null) {
                if (lastMarkedReadMessageId == lastRealMessage.toString()) return
                Log.d(
                    "ChatActivityInHouse",
                    "Marking messages as read before leaving activity. Last real message ID: $lastRealMessage"
                )
                markMessagesAsReadWithLastMessageId(lastRealMessage)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isChatVisible = true
        // T11: prefs-backed setter so the NSE (possibly cross-process) reads it.
        com.gmwapp.hima.utils.ActiveChatTracker.setActive(this, peerUserId)

        // BUG-10: re-sync the I-blocked-them state from the local prefs cache on
        // every resume. Block / Unblock done from the peer's profile screen
        // updates BlockedPeersPrefsHelper + broadcasts a list refresh, but the
        // chat-detail's onResume history reload is skipped by the C1 fresh-cache
        // optimization — so without this re-seed the locked banner + disabled
        // composer would stay stale after unblocking (or blocking) from the
        // profile. peerHasBlockedMe stays server-driven via the history path.
        if (peerUserId > 0) {
            val blockedNow = com.gmwapp.hima.utils.BlockedPeersPrefsHelper
                .isBlocked(this, peerUserId.toString())
            if (blockedNow != iHaveBlockedThisUser) {
                iHaveBlockedThisUser = blockedNow
                applyBlockedUiState()
            }
        }

        if (!chatRefreshReceiverRegistered) {
            val filter = IntentFilter(
                com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_REFRESH
            )
            ContextCompat.registerReceiver(
                this,
                chatRefreshReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            chatRefreshReceiverRegistered = true
        }
        val elapsedSinceCreate = SystemClock.elapsedRealtime() - activityCreatedAtElapsed
        Log.d(
            CHAT_REOPEN_LOG,
            "LIFECYCLE onResume peer=$peerUserId suppressNextResume=$suppressNextResumeHistoryReload " +
                "willCallLoadMessages=${!suppressNextResumeHistoryReload} elapsedSinceCreateMs=$elapsedSinceCreate instance=${hashCode()}"
        )

        // Returning to this conversation clears the MessagingStyle stack for this peer
        // so lines dismissed by the user (or arriving while the chat was briefly paused)
        // don't reappear on the next push.
        if (peerUserId > 0) {
            com.gmwapp.hima.utils.ChatNotificationStore.clear(this, peerUserId)
            androidx.core.app.NotificationManagerCompat.from(this)
                .cancel(com.gmwapp.hima.utils.ChatNotifications.notifIdFor(peerUserId))
        }

        // Re-check subscription: user may be returning from AutopayCheckoutActivity,
        // OR may have just revoked the UPI mandate from inside GPay/PhonePe and
        // come back to the app. The backend's subscription_status endpoint
        // reconciles against Cashfree on every call (60s cache), so this single
        // hit catches UPI-side cancels even when no webhook has arrived yet.
        // Cached gate applies first for instant feedback; observer at
        // observeAutopayPushEvents() re-applies the gate when the response lands.
        applySubscriptionGate()
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let { uid ->
            autopayViewModel.subscriptionStatus(uid)
        }



        // Mark messages as read using the new API with last message id if messages are loaded
        markMessagesAsReadIfAvailable()
        
        // Also call old method for backward compatibility
        markMessagesAsRead()
        
        // Check Socket.IO connection status and reconnect if needed
        val isConnected = socketManager.isConnected()
        Log.d("SocketIOCheck", "onResume - Socket.IO status: ${if (isConnected) "✅ CONNECTED" else "❌ DISCONNECTED"}")
        
        if (!isConnected) {
            // Reconnect Socket.IO if not connected (e.g., if activity was paused and resumed)
            Log.d("SocketIOCheck", "🔄 Reconnecting Socket.IO in onResume...")
            connectSocket()
        } else {
            // Update online status and join chat room immediately if already connected
            socketManager.updateStatus("online")
            if (myUserId > 0 && peerUserId > 0) {
                Log.d("SocketIOCheck", "✅ Socket.IO connected in onResume - Joining chat room immediately: $chatId")
                socketManager.joinChatRoom(myUserId, peerUserId)
            }
            Log.d("SocketIOCheck", "✅ Socket.IO is WORKING - Messages will be sent via Socket.IO")
        }
        
        // Log status report
        logSocketIOStatus()

        // Re-fetch history when returning to this screen (fixes missing messages after tab / background).
        // Skip once: onCreate already called loadMessages(); first onResume would duplicate the request.
        if (suppressNextResumeHistoryReload) {
            suppressNextResumeHistoryReload = false
            Log.d("ChatPagination", "Skipping duplicate history reload (first resume after onCreate)")
            Log.d(CHAT_REOPEN_LOG, "onResume SKIP extra loadMessages (first resume after onCreate) peer=$peerUserId")
        } else if (messages.any { !it.isDateHeader } &&
            historyCache.snapshotAgeMs(peerUserId) in 0L..RESUME_RELOAD_FRESH_WINDOW_MS
        ) {
            // C1: If we already have a populated list AND the cache is fresh (<30s),
            // skip the resume reload entirely. The push-broadcast path or socket
            // events will pick up anything new without truncating older pages.
            Log.d(
                CHAT_REOPEN_LOG,
                "onResume SKIP loadMessages — list populated and cache age=" +
                    "${historyCache.snapshotAgeMs(peerUserId)}ms < ${RESUME_RELOAD_FRESH_WINDOW_MS}ms peer=$peerUserId"
            )
        } else {
            Log.d("ChatPagination", "onResume — refreshing chat history from server")
            Log.d(CHAT_REOPEN_LOG, "onResume TRIGGER loadMessages() peer=$peerUserId (returning to chat)")
            loadMessages()
        }
    }

    override fun onPause() {
        super.onPause()
        isChatVisible = false
        // T11: prefs-backed clear so cross-process readers also see "no chat open".
        com.gmwapp.hima.utils.ActiveChatTracker.clear(this)

        // Bug 8: leaving the screen must clear our typing state on the peer's
        // side, otherwise their "Typing..." sticks for 5s until the auto-clear
        // fires. Cheap & idempotent.
        if (isCurrentlyEmittingTyping && chatId.isNotBlank()) {
            runCatching { socketManager.sendTyping(chatId, false) }
            isCurrentlyEmittingTyping = false
        }
        typingStopRunnable?.let { mainHandler.removeCallbacks(it) }
        typingStopRunnable = null

        // Bug-1: presence is "chat-open only". Leaving this screen or
        // backgrounding the app must tell the peer we're no longer in the chat
        // so their header flips from green "Online" to grey "Last seen just now"
        // in real time. Previously this only happened in onDestroy, so a paused/
        // backgrounded (but not destroyed) chat kept the peer showing us Online.
        // onResume re-joins + re-announces "online" when we come back.
        runCatching {
            socketManager.updateStatus("offline")
            socketManager.leaveChat(chatId)
        }.onFailure { Log.w("Presence", "onPause offline emit failed: ${it.message}") }

        // CHAT-108: persist composer draft so it survives switching chats,
        // backgrounding, and cold restarts (savedInstanceState only covers
        // rotations / short process-recovery). Blank text auto-removes the
        // entry via ChatDraftStore.save's eviction path.
        if (peerUserId > 0) {
            com.gmwapp.hima.utils.ChatDraftStore.save(
                this, peerUserId, etMessage.text?.toString()
            )
        }

        if (chatRefreshReceiverRegistered) {
            runCatching { unregisterReceiver(chatRefreshReceiver) }
                .onFailure { Log.w("RealtimeChat", "unregisterReceiver failed: ${it.message}") }
            chatRefreshReceiverRegistered = false
        }
        val elapsedSinceCreate = SystemClock.elapsedRealtime() - activityCreatedAtElapsed
        Log.d(
            CHAT_REOPEN_LOG,
            "LIFECYCLE onPause peer=$peerUserId instance=${hashCode()} elapsedSinceCreateMs=$elapsedSinceCreate " +
                "hadInFlightHistory=${currentHistoryCall != null}"
        )

        if (audioRecorderController.isRecording()) {
            cancelAudioRecording(showToast = true)
        }
        if (::chatAdapter.isInitialized) {
            chatAdapter.release()
        }

        // Fire both mark-read endpoints on exit so the inbox badge clears reliably
        // on the next `my_chat` refresh. markMessagesAsReadIfAvailable() covers the
        // `mark_messages_read` path (by last message id); markMessagesAsRead()
        // covers the `chats/mark-read` path (by chat id) — the latter also works
        // when the in-memory messages list is empty.
        markReadOnExit()

        // Keep Socket.IO connected - do not disconnect on pause
    }

    /** Single exit path so [onBackPressed] + [onPause] do not each fire mark-read APIs twice. */
    private fun markReadOnExit() {
        val latestReceivedId = latestReceivedMessageId()
        if (latestReceivedId == null || latestReceivedId == lastMarkedReadMessageId) return
        if (markedReadOnce && latestReceivedId == lastMarkedReadMessageId) return
        markedReadOnce = true
        markMessagesAsReadIfAvailable()
        markMessagesAsRead()
    }

    override fun onDestroy() {
        super.onDestroy()

        val hadPendingThrottle = pendingThrottleHistoryRunnable != null
        pendingThrottleHistoryRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingThrottleHistoryRunnable = null
        Log.d(
            CHAT_REOPEN_LOG,
            "LIFECYCLE onDestroy peer=$peerUserId cancelHistory=${currentHistoryCall != null} cancelMore=${currentMoreCall != null} " +
                "pendingThrottle=$hadPendingThrottle instance=${hashCode()}"
        )
        mainHandler.removeCallbacks(retryHistoryRunnable)
        currentHistoryCall?.cancel()
        currentMoreCall?.cancel()
        // T12: cancel any in-flight attachment uploads / sends so their callbacks
        // don't run on a destroyed view tree.
        activeAttachmentCalls.values.forEach { runCatching { it.cancel() } }
        activeAttachmentCalls.clear()
        activeTextSendCalls.forEach { runCatching { it.cancel() } }
        activeTextSendCalls.clear()
        // Delete-for-everyone ack timeouts: drop pending callbacks so they don't fire
        // a REST fallback against a destroyed view tree.
        pendingDeleteTimeouts.values.forEach { mainHandler.removeCallbacks(it) }
        pendingDeleteTimeouts.clear()
        pendingDeleteOriginals.clear()
        isInitialHistoryLoading = false

        mainHandler.removeCallbacks(logSocketStatusAfterDelay)
        mainHandler.removeCallbacks(recordingTicker)
        stopRecordingPulse()
        audioRecorderController.release()
        chatAdapter.release()
        
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        Log.d("SocketIOCheck", "👋 Leaving chat room (socket stays connected for app session)")
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        
        // T1: detach our profile observer so a delayed LiveData emission can't fire on a destroyed activity.
        profileObserver?.let { profileViewModel.getUserLiveData.removeObserver(it) }
        profileObserver = null

        // Leave this chat room only — do not disconnect the global socket here.
        // Disconnecting on every close caused reconnect races when opening chat repeatedly (blank UI / failed loads).
        socketManager.leaveChat(chatId)
    }

    override fun onBackPressed() {
        // TC_CH_003: an open emoji panel swallows the first back press, like the
        // soft keyboard would, instead of leaving the chat.
        if (emojiPicker?.visibility == View.VISIBLE) {
            hideEmojiPicker(showKeyboard = false)
            return
        }
        Log.d(CHAT_REOPEN_LOG, "onBackPressed peer=$peerUserId")
        markReadOnExit()
        super.onBackPressed()
    }
    
    /**
     * Helper method to check how a message was sent
     * Call this method with message ID to see if it was sent via Socket.IO or API
     */
    private fun getMessageSendMethod(messageId: String): String {
        return messageSendMethod[messageId] ?: "unknown"
    }
    
    /**
     * Log Socket.IO connection status and message sending statistics
     */
    private fun logSocketIOStatus() {
        val isConnected = socketManager.isConnected()
        val socketMessages = messageSendMethod.values.count { it == "socket" }
        val apiMessages = messageSendMethod.values.count { it == "api" }
        
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        Log.d("SocketIOCheck", "📊 SOCKET.IO STATUS REPORT")
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        Log.d("SocketIOCheck", "Connection Status: ${if (isConnected) "✅ CONNECTED" else "❌ DISCONNECTED"}")
        Log.d("SocketIOCheck", "Socket.IO URL: ${com.gmwapp.hima.utils.Config.SOCKET_URL}")
        Log.d("SocketIOCheck", "Chat ID: $chatId")
        Log.d("SocketIOCheck", "Messages sent via Socket.IO: $socketMessages")
        Log.d("SocketIOCheck", "Messages sent via API: $apiMessages")
        Log.d("SocketIOCheck", "Total messages tracked: ${messageSendMethod.size}")
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
    }

    private fun showOptionsMenu() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val isFemaleUser = userData?.gender?.equals(DConstants.FEMALE, ignoreCase = true) == true

        if (isFemaleUser) {
            // Show custom popup with toggles for female users
            showCustomMenuWithToggles()
        } else {
            // Show regular menu for non-female users
            showRegularMenu()
        }
    }

    private fun showCustomMenuWithToggles() {
        val popupView = layoutInflater.inflate(R.layout.popup_call_settings_menu, null)
        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Set background and elevation
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.drawable.dialog_holo_light_frame))
        popupWindow.elevation = 8f
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true

        val itemBlockUser = popupView.findViewById<TextView>(R.id.item_block_user)
        // Audio/Video toggles were removed from this overflow menu (managed in
        // Profile's call-availability section). Keep references nulled so the
        // permission-launcher callbacks don't try to flip a missing switch.
        callStatusAudioSwitch = null
        callStatusVideoSwitch = null

        // Block user click listener - show block or unblock based on status
        if (iHaveBlockedThisUser) {
            itemBlockUser?.text = getString(R.string.chat_unblock_user)
            itemBlockUser?.setOnClickListener {
                popupWindow.dismiss()
                showUnblockConfirmationDialog()
            }
        } else {
            itemBlockUser?.text = getString(R.string.chat_block_user)
            itemBlockUser?.setOnClickListener {
                popupWindow.dismiss()
                showBlockConfirmationDialog()
            }
        }

        val itemAcceptFriend = popupView.findViewById<TextView>(R.id.item_accept_as_friend)
        val dividerBeforeBlockUser = popupView.findViewById<View>(R.id.divider_before_block_user)
        val showAcceptFriend = !isFriendWithPeer
        itemAcceptFriend?.visibility = if (showAcceptFriend) View.VISIBLE else View.GONE
        dividerBeforeBlockUser?.visibility = if (showAcceptFriend) View.VISIBLE else View.GONE
        itemAcceptFriend?.setOnClickListener {
            popupWindow.dismiss()
            acceptAsFriend()
        }

        // TC_CH_004: Clear chat / Delete chat (same actions as the male menu path)
        popupView.findViewById<TextView>(R.id.item_clear_chat)?.setOnClickListener {
            popupWindow.dismiss()
            showClearChatConfirmation()
        }
        popupView.findViewById<TextView>(R.id.item_delete_chat)?.setOnClickListener {
            popupWindow.dismiss()
            showDeleteChatConfirmation()
        }

        // Measure popup to get its width
        popupView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth

        // Calculate popup position (below the three-dot icon, aligned to the right)
        val location = IntArray(2)
        ivMore.getLocationOnScreen(location)
        val screenWidth = resources.displayMetrics.widthPixels
        val x = (location[0] + ivMore.width - popupWidth).coerceAtLeast(16) // Align to right edge, with margin
        val y = location[1] + ivMore.height + 8 // Add small gap below icon

        // Show popup
        popupWindow.showAtLocation(ivMore, android.view.Gravity.NO_GRAVITY, x, y)
        popupWindow.setOnDismissListener {
            callStatusAudioSwitch = null
            callStatusVideoSwitch = null
        }
    }

    private fun syncCallStatusTogglesFromPrefs() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        isApplyingCallStatusToggleState = true
        callStatusAudioSwitch?.isChecked = userData.audio_status == 1
        callStatusVideoSwitch?.isChecked = userData.video_status == 1
        isApplyingCallStatusToggleState = false
    }

    private fun showRegularMenu() {
        val popup = PopupMenu(this, ivMore)
        popup.menuInflater.inflate(R.menu.menu_chat, popup.menu)

        // Show block option or unblock option based on current status
        popup.menu.findItem(R.id.action_accept_as_friend)?.isVisible = false
        popup.menu.findItem(R.id.action_block)?.isVisible = !iHaveBlockedThisUser
        popup.menu.findItem(R.id.action_unblock)?.isVisible = iHaveBlockedThisUser

        // TC_CH_004: tint "Delete chat" red so the destructive action reads as such
        // in the stock PopupMenu (which has no per-item colour attribute).
        popup.menu.findItem(R.id.action_delete_chat)?.let { item ->
            val span = android.text.SpannableString(item.title)
            span.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#DC2626")),
                0, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            item.title = span
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_accept_as_friend -> {
                    acceptAsFriend()
                    true
                }
                R.id.action_block -> {
                    showBlockConfirmationDialog()
                    true
                }
                R.id.action_unblock -> {
                    showUnblockConfirmationDialog()
                    true
                }
                R.id.action_clear_chat -> {
                    showClearChatConfirmation()
                    true
                }
                R.id.action_delete_chat -> {
                    showDeleteChatConfirmation()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC_CH_004: Clear chat / Delete chat (device-local, WhatsApp-style)
    // ─────────────────────────────────────────────────────────────────────────

    private fun showClearChatConfirmation() {
        showChatActionConfirmation(
            iconRes = R.drawable.ic_close_circle,
            accentColorHex = "#ff1383",
            titleRes = R.string.chat_clearchat_title,
            messageRes = R.string.chat_clearchat_message,
            confirmTextRes = R.string.chat_clearchat_confirm
        ) { clearChatLocally() }
    }

    private fun showDeleteChatConfirmation() {
        showChatActionConfirmation(
            iconRes = R.drawable.delete,
            accentColorHex = "#DC2626",
            titleRes = R.string.chat_deletechat_title,
            messageRes = R.string.chat_deletechat_message,
            confirmTextRes = R.string.chat_deletechat_confirm
        ) { deleteChatLocally() }
    }

    /** Reusable confirm dialog for the chat overflow's destructive actions. */
    private fun showChatActionConfirmation(
        iconRes: Int,
        accentColorHex: String,
        titleRes: Int,
        messageRes: Int,
        confirmTextRes: Int,
        onConfirm: () -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_chat_action_confirmation, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val color = android.graphics.Color.parseColor(accentColorHex)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.iv_icon)
        ivIcon.setImageResource(iconRes)
        ContextCompat.getDrawable(this, R.drawable.circle_bg_accent)?.mutate()?.let { bg ->
            bg.setTint(color)
            ivIcon.background = bg
        }

        dialogView.findViewById<TextView>(R.id.tv_title).setText(titleRes)
        dialogView.findViewById<TextView>(R.id.tv_message).setText(messageRes)

        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm)
        btnConfirm.setText(confirmTextRes)
        btnConfirm.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Empties this thread on-device. Records a clear watermark (newest message id
     * present), drops the in-memory list + cache snapshot, and refreshes the chat
     * list so the row preview blanks. The thread stays in the list; history is
     * filtered on every later load so it stays empty until a NEWER message arrives.
     * Local-only — the peer keeps their copy.
     */
    private fun clearChatLocally() {
        val watermark = newestNumericMessageId()
        if (peerUserId > 0 && watermark > 0L) {
            ClearedChatsPrefsHelper.setCleared(this, peerUserId.toString(), watermark)
        }
        messages.clear()
        chatAdapter.notifyDataSetChanged()
        updateTopHeader(messages)
        // Drop the in-memory snapshot so a same-session reopen can't rehydrate
        // pre-clear history (a process restart already starts from an empty cache).
        if (peerUserId > 0) historyCache.putSnapshot(peerUserId, emptyList())
        broadcastChatListRefresh()
        showAppToast(getString(R.string.chat_clearchat_toast), Toast.LENGTH_SHORT)
    }

    /**
     * Removes this thread from the chat list on-device (WhatsApp-style) and closes
     * back to the list. Records a delete watermark (thread reappears only when a
     * newer message arrives) plus a clear watermark (a reappeared thread opens
     * empty). Local-only — the peer + server are untouched.
     */
    private fun deleteChatLocally() {
        val watermark = newestNumericMessageId()
        if (peerUserId > 0) {
            DeletedChatsPrefsHelper.setDeleted(this, peerUserId.toString(), watermark)
            if (watermark > 0L) {
                ClearedChatsPrefsHelper.setCleared(this, peerUserId.toString(), watermark)
            }
            historyCache.putSnapshot(peerUserId, emptyList())
        }
        broadcastChatListRefresh()
        showAppToast(getString(R.string.chat_deletechat_toast), Toast.LENGTH_SHORT)
        finish()
    }

    /** Newest server-confirmed (numeric-id) message currently in the list, or 0. */
    private fun newestNumericMessageId(): Long =
        messages.asSequence().mapNotNull { it.id.toLongOrNull() }.maxOrNull() ?: 0L

    private fun broadcastChatListRefresh() {
        val refresh = android.content.Intent(
            com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_LIST_REFRESH
        ).setPackage(packageName)
        sendBroadcast(refresh)
    }

    /**
     * TC_CH_004: drop history at or below the local "Clear chat" watermark for this
     * peer. Clear is local-only (the server re-sends everything), so every list
     * rebuild filters server/cache rows against the persisted watermark. Date headers
     * and pending/temp rows (non-numeric ids) always pass — they're never stale.
     */
    private fun filterOutClearedBeforeWatermark(source: List<ChatMessage>): List<ChatMessage> {
        if (peerUserId <= 0) return source
        val watermark = ClearedChatsPrefsHelper.watermark(this, peerUserId.toString())
        if (watermark <= 0L) return source
        return source.filter { msg ->
            if (msg.isDateHeader) return@filter true
            val idLong = msg.id.toLongOrNull() ?: return@filter true
            idLong > watermark
        }
    }

    private fun showBlockConfirmationDialog() {
        // Create a custom dialog with beautiful UI
        val dialogView = layoutInflater.inflate(R.layout.dialog_block_user_confirmation, null)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Make dialog background transparent
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set button listeners
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_block).setOnClickListener {
            blockUser()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showUnblockConfirmationDialog() {
        // Create a custom dialog with beautiful UI (reusing the same layout)
        val dialogView = layoutInflater.inflate(R.layout.dialog_block_user_confirmation, null)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Make dialog background transparent
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Find TextViews by traversing the view hierarchy
        fun findTextViews(parent: android.view.ViewGroup): Pair<android.widget.TextView?, android.widget.TextView?> {
            var titleTextView: android.widget.TextView? = null
            var messageTextView: android.widget.TextView? = null
            
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is android.widget.TextView) {
                    if (titleTextView == null) {
                        titleTextView = child
                    } else if (messageTextView == null) {
                        messageTextView = child
                        break
                    }
                } else if (child is android.view.ViewGroup) {
                    val result = findTextViews(child)
                    if (result.first != null && titleTextView == null) titleTextView = result.first
                    if (result.second != null && messageTextView == null) messageTextView = result.second
                    if (titleTextView != null && messageTextView != null) break
                }
            }
            return Pair(titleTextView, messageTextView)
        }
        
        val (titleTextView, messageTextView) = findTextViews(dialogView as android.view.ViewGroup)
        val btnBlock = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_block)
        
        // Update title and message text
        titleTextView?.text = getString(R.string.chat_unblock_user_title)
        messageTextView?.text = getString(R.string.chat_unblock_user_message)
        btnBlock?.text = getString(R.string.chat_unblock_action)

        // Find the ImageView (icon) and change its background to dark pink
        fun findImageView(parent: android.view.ViewGroup): android.widget.ImageView? {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is android.widget.ImageView) {
                    return child
                } else if (child is android.view.ViewGroup) {
                    val result = findImageView(child)
                    if (result != null) return result
                }
            }
            return null
        }
        
        val iconImageView = findImageView(dialogView as android.view.ViewGroup)
        
        // Change icon circle background to dark pink
        iconImageView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(R.color.pink, theme)
        )
        
        // Change button background to lighter pink
        btnBlock?.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(R.color.pink, theme)
        )

        // Set button listeners
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        btnBlock?.setOnClickListener {
            unblockUser()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun blockUser() {
        Log.d("ChatActivityInHouse", "📤 Blocking user: $peerUserId")
        
        apiManager.blockChatUser(
            userId = myUserId,
            blockedUserId = peerUserId,
            object : NetworkCallback<BlockUserResponse> {
                override fun onResponse(call: Call<BlockUserResponse>, response: Response<BlockUserResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true) {
                            iHaveBlockedThisUser = true
                            cancelInFlightSendsForBlock()
                            applyBlockedUiState()
                            showAppToast(responseBody.message, Toast.LENGTH_SHORT)
                            Log.d("ChatActivityInHouse", "✅ User blocked successfully")
                            // Reload chat history to update blocked status
                            loadMessages()
                        } else {
                            showAppToast("Failed to block user", Toast.LENGTH_SHORT)
                        }
                    } else {
                        showAppToast("Failed to block user: ${response.code()}", Toast.LENGTH_SHORT)
                    }
                }

                override fun onFailure(call: Call<BlockUserResponse>, t: Throwable) {
                    Log.e("ChatActivityInHouse", "❌ Error blocking user: ${t.message}", t)
                    showAppToast(t.toUserMessage(), Toast.LENGTH_SHORT)
                }

                override fun onNoNetwork() {
                    showAppToast("No internet connection", Toast.LENGTH_SHORT)
                }
            }
        )
    }

    private fun unblockUser() {
        Log.d("ChatActivityInHouse", "📤 Unblocking user: $peerUserId")
        
        apiManager.unblockChatUser(
            userId = myUserId,
            blockedUserId = peerUserId,
            object : NetworkCallback<BlockUserResponse> {
                override fun onResponse(call: Call<BlockUserResponse>, response: Response<BlockUserResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true) {
                            iHaveBlockedThisUser = false
                            applyBlockedUiState()
                            showAppToast(responseBody.message, Toast.LENGTH_SHORT)
                            Log.d("ChatActivityInHouse", "✅ User unblocked successfully")
                            // Reload chat history to update blocked status
                            loadMessages()
                        } else {
                            showAppToast("Failed to unblock user", Toast.LENGTH_SHORT)
                        }
                    } else {
                        showAppToast("Failed to unblock user: ${response.code()}", Toast.LENGTH_SHORT)
                    }
                }

                override fun onFailure(call: Call<BlockUserResponse>, t: Throwable) {
                    Log.e("ChatActivityInHouse", "❌ Error unblocking user: ${t.message}", t)
                    showAppToast(t.toUserMessage(), Toast.LENGTH_SHORT)
                }

                override fun onNoNetwork() {
                    showAppToast("No internet connection", Toast.LENGTH_SHORT)
                }
            }
        )
    }

    private fun setupCallStatusObservers() {
        // Observe call status update response
        femaleUsersViewModel.updateCallStatusResponseLiveData.observe(this, Observer { response ->
            if (response != null && response.success && response.data != null) {
                // Update user data in preferences
                BaseApplication.getInstance()?.getPrefs()?.setUserData(response.data)
                syncCallStatusTogglesFromPrefs()
                Log.d("ChatActivityInHouse", "✅ Call status updated successfully")
                showAppToast("Call status updated", Toast.LENGTH_SHORT)
            } else if (response != null) {
                // Revert toggles when API rejects update (toggle should be ON/OFF only on success).
                syncCallStatusTogglesFromPrefs()
                response.message.takeIf { it.isNotBlank() }?.let { message ->
                    showAppToast(message, Toast.LENGTH_SHORT)
                }
            }
        })

        // Observe call status update errors
        femaleUsersViewModel.updateCallStatusErrorLiveData.observe(this, Observer { error ->
            if (error != null) {
                syncCallStatusTogglesFromPrefs()
                Log.e("ChatActivityInHouse", "❌ Error updating call status: $error")
                when (error) {
                    DConstants.NO_NETWORK -> {
                        showAppToast("No internet connection", Toast.LENGTH_SHORT)
                    }
                    else -> {
                        showAppToast("Failed to update call status", Toast.LENGTH_SHORT)
                    }
                }
            }
        })
    }

    // ==================== CALL BUTTONS FUNCTIONS ====================

    private fun setupCallButtons() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val isMaleUser = userData?.gender?.equals(DConstants.MALE, ignoreCase = true) == true
        
        if (isMaleUser && peerUserId > 0 && myUserId > 0) {
            checkCallAvailability()
        } else {
            callButtonsContainer.visibility = View.GONE
        }
    }

    private fun checkCallAvailability() {
        apiManager.checkCallAvailability(
            maleUserId = myUserId,
            femaleUserId = peerUserId,
            object : NetworkCallback<CheckCallAvailabilityResponse> {
                override fun onResponse(
                    call: Call<CheckCallAvailabilityResponse>,
                    response: Response<CheckCallAvailabilityResponse>
                ) {
                    if (response.isSuccessful) {
                        val responseData = response.body()?.data
                        if (responseData != null) {
                            isCallBlocked = responseData.is_blocked
                            peerAudioStatus = responseData.audio_status
                            peerVideoStatus = responseData.video_status
                            
                            Log.d("ChatActivityInHouse", "Call availability - Blocked: $isCallBlocked, Audio: $peerAudioStatus, Video: $peerVideoStatus")
                            
                            mainHandler.post {
                                if (!isUiSafe()) return@post
                                callButtonsContainer.visibility = View.VISIBLE
                                // Initialize buttons as disabled (gray) first
                                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this@ChatActivityInHouse, R.color.light_grey))
                                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this@ChatActivityInHouse, R.color.light_grey))
                                ivAudioCall.setColorFilter(ContextCompat.getColor(this@ChatActivityInHouse, R.color.grey_medium))
                                ivVideoCall.setColorFilter(ContextCompat.getColor(this@ChatActivityInHouse, R.color.grey_medium))
                                cvAudioCall.isEnabled = false
                                cvVideoCall.isEnabled = false
                                // Update buttons based on API response (is_blocked, audio_status, video_status)
                                updateCallButtonsState()
                            }
                        } else {
                            Log.e("ChatActivityInHouse", "Call availability response data is null")
                            callButtonsContainer.visibility = View.GONE
                        }
                    } else {
                        Log.e("ChatActivityInHouse", "Failed to check call availability: ${response.code()}")
                        callButtonsContainer.visibility = View.GONE
                    }
                }

                override fun onFailure(call: Call<CheckCallAvailabilityResponse>, t: Throwable) {
                    Log.e("ChatActivityInHouse", "Failed to check call availability: ${t.message}")
                    callButtonsContainer.visibility = View.GONE
                }

                override fun onNoNetwork() {
                    callButtonsContainer.visibility = View.GONE
                }
            }
        )
    }

    private fun updateCallButtonsState() {
        Log.d("CallButtons", "Updating call buttons state. Blocked: $isCallBlocked, Audio: $peerAudioStatus, Video: $peerVideoStatus")
        
        // If blocked (is_blocked = true), disable both buttons
        if (isCallBlocked) {
            Log.d("CallButtons", "User is BLOCKED - disabling both audio and video buttons")
            mainHandler.post {
                if (!isUiSafe()) return@post
                // DISABLED - Gray for both buttons
                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_grey))
                ivAudioCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvAudioCall.isEnabled = false
                
                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_grey))
                ivVideoCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvVideoCall.isEnabled = false
            }
            return
        }
        
        // Check individual audio and video status (0 = disabled, 1 = enabled)
        val isAudioEnabled = peerAudioStatus == 1
        val isVideoEnabled = peerVideoStatus == 1
        
        Log.d("CallButtons", "Final status - Audio: $isAudioEnabled ($peerAudioStatus), Video: $isVideoEnabled ($peerVideoStatus)")
        
        mainHandler.post {
            if (!isUiSafe()) return@post
            // Audio button state - enabled only if audio is enabled (status = 1)
            if (isAudioEnabled) {
                // ENABLED - Purple
                Log.d("CallButtons", "Setting audio button to ENABLED (purple)")
                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
                ivAudioCall.setColorFilter(ContextCompat.getColor(this, R.color.white))
                cvAudioCall.isEnabled = true
            } else {
                // DISABLED - Gray
                Log.d("CallButtons", "Setting audio button to DISABLED (gray) - AudioEnabled: $isAudioEnabled")
                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_grey))
                ivAudioCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvAudioCall.isEnabled = false
            }
            
            // Video button state - enabled only if video is enabled (status = 1)
            if (isVideoEnabled) {
                // ENABLED - Green
                Log.d("CallButtons", "Setting video button to ENABLED (green)")
                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.green))
                ivVideoCall.setColorFilter(ContextCompat.getColor(this, R.color.white))
                cvVideoCall.isEnabled = true
            } else {
                // DISABLED - Gray
                Log.d("CallButtons", "Setting video button to DISABLED (gray) - VideoEnabled: $isVideoEnabled")
                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_grey))
                ivVideoCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvVideoCall.isEnabled = false
            }
        }
    }

    private fun setupCallButtonListeners() {
        cvAudioCall.setOnClickListener {
            when {
                // Peer-blocked-me (chat-side block, server-sourced) → toast
                // explicit. Beats the isCallBlocked branch because that
                // covers the calls-side blocked_users table separately and
                // its message ("You are blocked by this user") is the
                // same — but the chat-side block is checked first so the
                // toast text source-of-truth stays consistent.
                peerHasBlockedMe -> {
                    showAppToast(
                        getString(R.string.chat_blocked_by_peer_toast),
                        Toast.LENGTH_SHORT,
                    )
                }
                iHaveBlockedThisUser -> {
                    showAppToast(
                        getString(R.string.chat_blocked_input_hint),
                        Toast.LENGTH_SHORT,
                    )
                }
                isCallBlocked -> {
                    showAppToast("You are blocked by this user", Toast.LENGTH_SHORT)
                }
                peerAudioStatus != 1 -> {
                    CallUnavailableFeedback.show(
                        this,
                        findViewById(android.R.id.content),
                        forAudio = true
                    )
                }
                !hasEnoughCoinsForCall(perMinAudioRate) -> {
                    showTrialOfferSheet()
                }
                else -> {
                    initiateCall("audio")
                }
            }
        }

        cvVideoCall.setOnClickListener {
            when {
                peerHasBlockedMe -> {
                    showAppToast(
                        getString(R.string.chat_blocked_by_peer_toast),
                        Toast.LENGTH_SHORT,
                    )
                }
                iHaveBlockedThisUser -> {
                    showAppToast(
                        getString(R.string.chat_blocked_input_hint),
                        Toast.LENGTH_SHORT,
                    )
                }
                isCallBlocked -> {
                    showAppToast("You are blocked by this user", Toast.LENGTH_SHORT)
                }
                peerVideoStatus != 1 -> {
                    CallUnavailableFeedback.show(
                        this,
                        findViewById(android.R.id.content),
                        forAudio = false
                    )
                }
                !hasEnoughCoinsForCall(perMinVideoRate) -> {
                    showTrialOfferSheet()
                }
                else -> {
                    initiateCall("video")
                }
            }
        }
    }

    private fun hasEnoughCoinsForCall(perMinRate: Int): Boolean {
        val coins = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.coins ?: 0
        return coins >= perMinRate
    }

    private fun initiateCall(callType: String) {
        val userName = extractNameOnly(intent.getStringExtra("USER_NAME") ?: "User")
        val userImage = intent.getStringExtra("USER_IMAGE") ?: ""
        
        val intent = Intent(this, com.gmwapp.hima.agora.male.MaleCallConnectingActivity::class.java).apply {
            putExtra(DConstants.CALL_TYPE, callType)
            putExtra(DConstants.RECEIVER_ID, peerUserId)
            putExtra(DConstants.IMAGE, userImage)
            putExtra(DConstants.RECEIVER_NAME, userName)
            putExtra("FROM_CHAT", true)
            putExtra("CHAT_PEER_USER_ID", peerUserId)
        }
        startActivity(intent)
    }

    /**
     * Extracts the name part from username by removing trailing numbers
     * Examples: "Joy22" -> "Joy", "ZNKAK467" -> "ZNKAK"
     */
    private fun extractNameOnly(username: String): String {
        if (username.isEmpty()) return username

        // Remove trailing digits
        return username.replace(Regex("\\d+$"), "").trim()
    }

    private fun isSubscriptionActive(): Boolean =
        com.gmwapp.hima.utils.SubscriptionStateCache.isActive(this)

    private fun wasEverSubscribed(): Boolean =
        com.gmwapp.hima.utils.SubscriptionStateCache.everActive(this)

    /**
     * Three mutually-exclusive bottom-bar states (mirrors ChatActivity).
     *   active             -> message input visible
     *   lapsed (everActive) -> autopay-failed lock with "Buy Coins" CTA
     *   never-subscribed   -> subscribe-to-unlock lock
     */
    private fun applySubscriptionGate() {
        // Females are creators (recipients) — they earn from chat, never pay.
        // The autopay gate applies to males only. Skip the gate entirely for any non-male user.
        val gender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
        if (!gender.equals("male", ignoreCase = true)) {
            messageInputContainer?.visibility = View.VISIBLE
            subscribeLockContainer?.visibility = View.GONE
            autopayFailedLockContainer?.visibility = View.GONE
            return
        }
        // Languages where admin disabled autopay never see the chat lock.
        // Existing subscribers (everActive) keep the lock states so a mid-flight
        // admin flip doesn't strip the autopay-failed lock from a lapsed user.
        val autopayLanguage = com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEnabled(this)
        val ever = wasEverSubscribed()
        if (!autopayLanguage && !ever) {
            messageInputContainer?.visibility = View.VISIBLE
            subscribeLockContainer?.visibility = View.GONE
            autopayFailedLockContainer?.visibility = View.GONE
            return
        }
        // Per-language re-enable prevention: when admin disabled
        // re-subscription for this language, lapsed users see the
        // autopay-failed lock (Buy Coins CTA) instead of the Subscribe lock.
        // Lapsed in re-sub-enabled languages, and fresh users in any
        // autopay-enabled language, get the regular Subscribe lock.
        val reSubEnabled = com.gmwapp.hima.utils.LanguageFeatureCache.isReSubscriptionEnabled(this)
        val lapsedAndBlocked = ever && !isSubscriptionActive() && !reSubEnabled
        when {
            isSubscriptionActive() -> {
                messageInputContainer?.visibility = View.VISIBLE
                subscribeLockContainer?.visibility = View.GONE
                autopayFailedLockContainer?.visibility = View.GONE
            }
            lapsedAndBlocked -> {
                messageInputContainer?.visibility = View.GONE
                subscribeLockContainer?.visibility = View.GONE
                autopayFailedLockContainer?.visibility = View.VISIBLE
            }
            else -> {
                // Both fresh users and lapsed-but-allowed users see the same
                // Subscribe lock. The Subscribe CTA's onClick uses
                // UserSegment.isNewUser() to decide PLAN_TRIAL_NEW (₹1) vs
                // PLAN_DIRECT_OLD (₹299), so re-subscribers automatically
                // pay full price without a second free trial.
                messageInputContainer?.visibility = View.GONE
                subscribeLockContainer?.visibility = View.VISIBLE
                autopayFailedLockContainer?.visibility = View.GONE
            }
        }
    }

    /**
     * Foreground reaction to OneSignal subscription_status push (autopay
     * failed/cancelled). Mirrors ChatActivity.observeAutopayPushEvents.
     */
    private fun observeAutopayPushEvents() {
        com.gmwapp.hima.utils.SubscriptionStateCache.pushEvent.observe(this) { event ->
            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return@observe
            autopayViewModel.subscriptionStatus(userId)
            showChatEndedBanner(event)
        }
        autopayViewModel.statusLiveData.observe(this) { resp ->
            val data = resp?.data ?: return@observe
            com.gmwapp.hima.utils.SubscriptionStateCache.update(data)
            applySubscriptionGate()
        }
        // Re-gate when admin flips the language flag while chat is open.
        com.gmwapp.hima.utils.LanguageFeatureCache.updates.observe(this) {
            applySubscriptionGate()
        }
    }

    private fun showChatEndedBanner(
        event: com.gmwapp.hima.utils.SubscriptionStateCache.PushEvent
    ) {
        val banner = chatEndedBanner ?: return
        chatEndedBannerText?.text = when (event) {
            com.gmwapp.hima.utils.SubscriptionStateCache.PushEvent.FAILED ->
                "Chat ended — bank declined the autopay renewal"
            com.gmwapp.hima.utils.SubscriptionStateCache.PushEvent.CANCELLED ->
                "Chat ended — autopay was cancelled"
        }
        banner.visibility = View.VISIBLE
        banner.postDelayed({ banner.visibility = View.GONE }, 5000L)
    }

    private fun showTrialOfferSheet() {
        // Defensive: chat lock is hidden for non-autopay languages already,
        // but if anything reaches here (e.g. an old observer) still guard.
        if (!com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEnabled(this) &&
            !wasEverSubscribed()) return
        // For lapsed users on languages where admin blocked re-subscription,
        // suppress the subscribe sheet entirely. They shouldn't have reached
        // here (subscribe_lock is hidden in that case) but the chat-list
        // tap path can race the cache; this is the belt-and-braces guard.
        val ever = wasEverSubscribed()
        val active = isSubscriptionActive()
        val reSubEnabled = com.gmwapp.hima.utils.LanguageFeatureCache.isReSubscriptionEnabled(this)
        if (ever && !active && !reSubEnabled) return
        // Trial sheet (with explainer video) for everyone who has never had
        // an autopay mandate; banner sheet only for lapsed/cancelled. The
        // ever_active gate is the single source of truth — Users who already
        // saw the pitch don't need the video re-shown.
        if (!com.gmwapp.hima.utils.SubscriptionStateCache.everActive(this)) {
            val sheet = com.gmwapp.hima.dialogs.BottomSheetTrialOffer.newInstance()
            sheet.setOnTryNowClickListener {
                startActivity(AutopayCheckoutActivity.intentFor(
                    this, AutopayCheckoutActivity.PLAN_TRIAL_NEW
                ))
            }
            sheet.show(supportFragmentManager, com.gmwapp.hima.dialogs.BottomSheetTrialOffer.TAG)
        } else {
            val sheet = com.gmwapp.hima.dialogs.BottomSheetOldUserSubscribe.newInstance(
                bannerOnly = true,
                title = "Subscribe to unlock unlimited chats"
            )
            sheet.setOnSubscribeClickListener {
                startActivity(AutopayCheckoutActivity.intentFor(
                    this, AutopayCheckoutActivity.PLAN_DIRECT_OLD
                ))
            }
            sheet.show(supportFragmentManager, com.gmwapp.hima.dialogs.BottomSheetOldUserSubscribe.TAG)
        }
    }
}
