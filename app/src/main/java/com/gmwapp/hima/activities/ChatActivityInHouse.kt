package com.gmwapp.hima.activities

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
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.gmwapp.hima.utils.LocallyDeletedMessagesStore
import com.gmwapp.hima.utils.ImageCompressor

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
    // Crashlytics initPeerHeader$lambda: getUserLiveData posts response.body() which is
    // null on a non-2xx/empty /getUsers response. Observer must be typed nullable, else
    // Kotlin's generated non-null param check throws before the body's null-guard runs.
    private var profileObserver: Observer<RegisterResponse?>? = null

    private lateinit var rvMessages: RecyclerView
    private var layoutHistoryError: View? = null
    private var tvHistoryError: TextView? = null
    private var btnHistoryRetry: View? = null
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnMic: ImageButton
    private lateinit var btnEmoji: TextView
    private lateinit var emojiGrid: GridView
    private var messageInputContainer: View? = null
    private var accountBlockedChatStrip: View? = null
    private var subscribeLockContainer: View? = null
    private var autopayFailedLockContainer: View? = null
    // Friends-Gated Chat: friendship lock (friends-mode composer lock) + last gate result.
    private var friendshipLockContainer: View? = null
    private var tvFriendLockTitle: TextView? = null
    private var tvFriendLockSubtitle: TextView? = null
    private var btnFriendLockPrimary: View? = null
    private var tvFriendLockPrimaryText: TextView? = null
    private var btnFriendLockSecondary: View? = null
    private var tvFriendLockSecondaryText: TextView? = null
    private var lastChatGate: com.gmwapp.hima.retrofit.responses.ChatGateStatusResponse? = null
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
        val audioDurationMs: Long? = null
    )

    private val pendingOutgoingByTempId = LinkedHashMap<String, PendingOutgoingPayload>()
    
    // Store last online status from API
    private var lastOnlineStatus: String? = null
    
    // Track if user is blocked
    private var iHaveBlockedThisUser: Boolean = false

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
    private var tvAudioRateTop: TextView? = null
    private var tvVideoRateTop: TextView? = null
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
    private var peerCallBlocked: Boolean = false  // FEMALE_3_REJECT_BLOCK — female auto-blocked this male (3 rejects/5min), 60-min cooldown
    private var callStatusAudioSwitch: com.google.android.material.switchmaterial.SwitchMaterial? = null
    private var callStatusVideoSwitch: com.google.android.material.switchmaterial.SwitchMaterial? = null
    private var isApplyingCallStatusToggleState = false
    
    // Pagination variables
    private var currentOffset = 0
    private var isLoadingMore = false
    private var hasMoreMessages = true
    private val MESSAGES_PER_PAGE = 10

    // CHAT-084 reply-quote tap → scroll-to-original + flash. Paging-retry state
    // for when the original isn't in the loaded window yet.
    private var pendingReplyScrollMessageId: String? = null
    private var pendingReplyScrollAttempts = 0
    private val MAX_REPLY_SCROLL_PAGES = 5
    private var replyFlashAnimator: android.animation.ValueAnimator? = null
    private var replyFlashScrollListener: androidx.recyclerview.widget.RecyclerView.OnScrollListener? = null
    private var replyFlashTimeoutRunnable: Runnable? = null

    /** Latest wins for overlapping [getChatHistory] calls so an older response cannot replace a newer list. */
    private val historyLoadRequestId = AtomicInteger(0)

    /** In-flight Retrofit calls for chat history — cancelled on destroy or when superseded. */
    private var currentHistoryCall: Call<ChatHistoryResponse>? = null
    private var currentMoreCall: Call<ChatHistoryResponse>? = null
    // C-30: driving skeleton off this flag means every load-termination site
    // (16 of them) auto-hides the skeleton without touching each one — the
    // setter fires on any `isInitialHistoryLoading = false`.
    private var isInitialHistoryLoading = false
        set(value) {
            field = value
            if (!value) hideChatSkeleton()
        }
    private var chatSkeleton: View? = null
    private var chatSkeletonAnim: android.animation.Animator? = null
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
        // Block screenshots / screen recording for the entire chat thread (text +
        // inline image thumbnails). Must be set before setContentView so the window
        // is flagged secure from first frame.
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
        applyComposerGate()
        refreshChatGate()
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
        savedInstanceState?.getString(STATE_DRAFT_TEXT)?.let { draft ->
            if (draft.isNotEmpty()) {
                etMessage.setText(draft)
                etMessage.setSelection(draft.length.coerceAtMost(etMessage.text?.length ?: 0))
            }
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
        // Friends-Gated Chat: lock the composer immediately for the new peer
        // (gate was just reset to null), then fetch the real gate for this peer.
        applyComposerGate()
        refreshChatGate()
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
        btnEmoji = findViewById(R.id.btn_emoji)
        emojiGrid = findViewById(R.id.emoji_grid)
        messageInputContainer = findViewById(R.id.message_input_container)
        accountBlockedChatStrip = findViewById(R.id.account_blocked_chat_strip)
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
        tvAudioRateTop = findViewById(R.id.tv_audio_rate_top)
        tvVideoRateTop = findViewById(R.id.tv_video_rate_top)
        val audioRate = intent.getIntExtra("COIN_PER_MIN_AUDIO", -1)
        val videoRate = intent.getIntExtra("COIN_PER_MIN_VIDEO", -1)
        perMinAudioRate = if (audioRate > 0) audioRate else 10
        perMinVideoRate = if (videoRate > 0) videoRate else 60
        // Per-minute rates now render inline under the top-bar call buttons;
        // the standalone rate banner is retired.
        tvAudioRateTop?.text = "${perMinAudioRate}/min"
        tvVideoRateTop?.text = "${perMinVideoRate}/min"
        cvRateBanner?.visibility = View.GONE

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
        // Edge-to-edge: the wallpaper (on R.id.main) fills the whole screen incl. the
        // bottom gesture strip (WhatsApp-style). Keep the TOP clean — the white top-bar
        // card extends behind the status bar, and we pad its content down by the status
        // inset; the transparent input bar is padded up by the nav inset so the field
        // clears the gesture bar while the wallpaper still shows behind/below it.
        val root = findViewById<View>(R.id.main)
        val topBarContent = findViewById<View>(R.id.top_bar)
        val inputContainer = findViewById<View>(R.id.message_input_container)
        val baseTopPad = topBarContent.paddingTop
        val baseInputBottomPad = inputContainer.paddingBottom
        // Bottom-pinned lock bars that REPLACE the input row when the chat is
        // gated (never-subscribed subscribe CTA, autopay-failed lock, friends
        // gate). Like the composer they sit on constraintBottom_toBottomOf=parent,
        // but they weren't getting the nav-bar inset — so on 3-button-nav phones
        // the system bar covered the "Subscribe to unlock…" pill. Pad each by the
        // nav gap too (no IME here — these bars have no text field). Views are GONE
        // by default; padding is harmless until they're shown.
        val bottomLockBars = listOf(
            R.id.subscribe_lock_container,
            R.id.autopay_failed_lock_container,
            R.id.friendship_lock_container
        ).mapNotNull { findViewById<View>(it) }
        val baseLockBottomPads = bottomLockBars.associateWith { it.paddingBottom }
        // Single source of truth for the bar/keyboard padding. Reused by both the
        // resting inset pass and the per-frame keyboard-animation callback so the
        // composer lift stays identical whether the keyboard is settled or mid-slide.
        //
        // Keyboard (IME) inset: targetSdk 35 forces edge-to-edge, so the legacy
        // adjustResize no longer shrinks the window for the keyboard on its own —
        // the composer would sit UNDER the keyboard. We lift it ourselves so the
        // whole input row (attach • field • mic/send) rides ABOVE the keyboard,
        // WhatsApp-style, and the message list resizes above it. When the keyboard
        // is up ime.bottom already includes the nav-bar height, so max() avoids
        // double-counting; keyboard down → falls back to the nav-bar gap.
        val applyInsetPadding = fun(insets: androidx.core.view.WindowInsetsCompat) {
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            topBarContent.setPadding(
                topBarContent.paddingLeft, baseTopPad + bars.top,
                topBarContent.paddingRight, topBarContent.paddingBottom
            )
            inputContainer.setPadding(
                inputContainer.paddingLeft, inputContainer.paddingTop,
                inputContainer.paddingRight, baseInputBottomPad + maxOf(bars.bottom, ime.bottom)
            )
            bottomLockBars.forEach { bar ->
                val basePad = baseLockBottomPads[bar] ?: bar.paddingBottom
                bar.setPadding(bar.paddingLeft, bar.paddingTop, bar.paddingRight, basePad + bars.bottom)
            }
        }
        // API<30 (Android 10 and below) never reports the ime() inset, and
        // installing the WindowInsetsAnimationCompat callback there hijacks the
        // window's inset flow so the legacy adjustResize stops shrinking for the
        // keyboard — leaving the composer stranded UNDER it (waterdrop-notch
        // budget phones on Android 10/11). So only run the manual lift on
        // API 30+, where ime() insets are real; below that we leave the window
        // alone and let the manifest's adjustResize do its native job.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                // DISPATCH_MODE_STOP defers this resting pass until the keyboard
                // animation ends, so it only sets the final settled state; the
                // per-frame lift is driven by the animation callback below.
                applyInsetPadding(insets)
                insets
            }
            // WhatsApp-style glide: track the keyboard frame-by-frame while it animates
            // in/out instead of snapping to the final height in one jump.
            androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback(
                root,
                object : androidx.core.view.WindowInsetsAnimationCompat.Callback(
                    androidx.core.view.WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP
                ) {
                    override fun onProgress(
                        insets: androidx.core.view.WindowInsetsCompat,
                        runningAnimations: MutableList<androidx.core.view.WindowInsetsAnimationCompat>
                    ): androidx.core.view.WindowInsetsCompat {
                        applyInsetPadding(insets)
                        return insets
                    }
                }
            )
            androidx.core.view.ViewCompat.requestApplyInsets(root)
        }

        bannerAddFriend = findViewById(R.id.banner_add_friend)
        tvBannerAddFriendTitle = findViewById(R.id.tv_banner_add_friend_title)
        btnBannerNotNow = findViewById(R.id.btn_banner_not_now)
        btnBannerAcceptFriend = findViewById(R.id.btn_banner_accept_friend)

        // Friends-Gated Chat: friendship lock views (code-driven states).
        friendshipLockContainer = findViewById(R.id.friendship_lock_container)
        tvFriendLockTitle = findViewById(R.id.tv_friend_lock_title)
        tvFriendLockSubtitle = findViewById(R.id.tv_friend_lock_subtitle)
        btnFriendLockPrimary = findViewById(R.id.btn_friend_lock_primary)
        tvFriendLockPrimaryText = findViewById(R.id.tv_friend_lock_primary_text)
        btnFriendLockSecondary = findViewById(R.id.btn_friend_lock_secondary)
        tvFriendLockSecondaryText = findViewById(R.id.tv_friend_lock_secondary_text)

        layoutHistoryError = findViewById(R.id.layout_history_error)
        chatSkeleton = findViewById(R.id.layout_chat_skeleton)
        tvHistoryError = findViewById(R.id.tv_history_error)
        btnHistoryRetry = findViewById(R.id.btn_history_retry)
        btnHistoryRetry?.setOnClickListener {
            Log.d(CHAT_REOPEN_LOG, "UI USER_RETRY tap peer=$peerUserId fromState=EMPTY_STATE")
            hideHistoryErrorUi("USER_RETRY")
            loadMessages(userRetry = true)
        }
    }

    /**
     * C-30: show the shimmer skeleton over the (empty) message area on a cold
     * open so it isn't a bare blank while history loads. Only shown when there's
     * genuinely nothing to display yet — a warm revisit hydrates real messages
     * from cache and never calls this. A gentle alpha pulse stands in for a
     * shimmer (no extra library). Auto-hidden by the isInitialHistoryLoading
     * setter the moment the load ends.
     */
    private fun showChatSkeleton() {
        val sk = chatSkeleton ?: return
        if (!messages.isEmpty()) return
        if (sk.visibility == View.VISIBLE) return
        sk.visibility = View.VISIBLE
        chatSkeletonAnim?.cancel()
        chatSkeletonAnim = android.animation.ObjectAnimator.ofFloat(sk, "alpha", 1f, 0.4f).apply {
            duration = 650L
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            start()
        }
    }

    private fun hideChatSkeleton() {
        val sk = chatSkeleton ?: return
        chatSkeletonAnim?.cancel()
        chatSkeletonAnim = null
        sk.alpha = 1f
        if (sk.visibility != View.GONE) sk.visibility = View.GONE
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
                        
                        // Load more when user scrolls to top (older messages)
                        // Trigger when user is near the top (within first 5 items) and scrolling up
                        if (firstVisiblePosition <= 5 && dy < 0 && !isLoadingMore && hasMoreMessages) {
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
        // CHAT-091: route sent rows to START (left-swipe) and received rows to
        // END (right-swipe) so each bubble has room to slide; draw a fade-in
        // reply icon on the edge opposite the swipe direction.
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

                // CHAT-091 follow-up v4: the row must NEVER complete the swipe — a
                // completed swipe is a "dismiss", which left the bubble animated
                // off-screen (the message appeared to vanish). Threshold > 1 and a
                // huge escape velocity guarantee ItemTouchHelper always springs the
                // row back to rest. The reply is triggered mid-drag in onChildDraw.
                override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 10f

                override fun getSwipeEscapeVelocity(defaultValue: Float): Float =
                    defaultValue * 100f

                // Never fires now (threshold is unreachable) — kept because
                // SimpleCallback declares it abstract.
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

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

                            // Trigger the reply once when the drag passes 30% of the
                            // row width, while the finger is still down.
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
            return
        }
        layout.visibility = View.VISIBLE
        tvReplyAuthor?.text = if (ref.isSentByMe) {
            getString(R.string.chat_reply_you)
        } else {
            peerName
        }
        tvReplySnippet?.text = buildReplySnippetText(ref)
    }

    private fun buildReplySnippetText(msg: ChatMessage): String = when (msg.messageType.lowercase()) {
        "image" -> getString(R.string.chat_preview_photo)
        "audio" -> getString(R.string.chat_preview_voice)
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
     * CHAT-084: tap a reply bubble's quote strip → smooth-scroll the list to the
     * original message and briefly flash its row (WhatsApp-style) so the user
     * can tell which message is being referenced.
     *
     * The inline-reply payload only carries author + snippet (no original id), so
     * we match by snippet, scanning BACKWARDS from the reply's own position (a
     * reply can only refer to something earlier — avoids matching a later "ok").
     * Media replies carry placeholder snippets ("📷 Photo" / "🎤 Voice message")
     * and the original media bubble has an empty body, so we match those by
     * messageType instead. If the original isn't paged in yet, load older pages
     * and retry (bounded) before giving up — same as WhatsApp's jump-to-quoted.
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
                this, R.string.chat_reply_original_not_loaded, android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

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
            // Original not in the loaded window yet — page older messages in and
            // retry (bounded) before giving up.
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
                this, R.string.chat_reply_original_not_loaded, android.widget.Toast.LENGTH_SHORT
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
            clearPendingReplyScroll()
            return
        }
        scrollToInlineReplyOriginal(msg)
    }

    /**
     * Classify a reply-quote snippet as a media placeholder so the scan matches
     * by messageType (media bubbles have an empty body). Mirrors the static
     * snippets [buildReplySnippetText] produces. Null → plain text → text match.
     */
    private fun detectReplyTargetMessageType(snippet: String): String? {
        val s = snippet.trim()
        if (s == getString(R.string.chat_preview_voice) ||
            s.startsWith("🎤", ignoreCase = true) ||
            s.equals("Voice message", ignoreCase = true)
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
     * Run the flash once the target row is on screen and the list settles.
     *  1) already visible → animate now; 2) scroll in flight → wait for IDLE;
     *  3) safety timeout (1500ms) in case smoothScroll was a no-op.
     */
    private fun scheduleReplyFlash(index: Int) {
        rvMessages.findViewHolderForAdapterPosition(index)?.itemView?.let {
            animateReplyFlash(it)
            return
        }
        // Tear down any prior pending-flash wiring before scheduling a new one.
        replyFlashScrollListener?.let { rvMessages.removeOnScrollListener(it) }
        replyFlashTimeoutRunnable?.let { rvMessages.removeCallbacks(it) }
        val listener = object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(
                rv: androidx.recyclerview.widget.RecyclerView, newState: Int
            ) {
                if (newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) return
                rv.removeOnScrollListener(this)
                replyFlashScrollListener = null
                rv.findViewHolderForAdapterPosition(index)?.itemView?.let { animateReplyFlash(it) }
            }
        }
        replyFlashScrollListener = listener
        rvMessages.addOnScrollListener(listener)
        val timeout = Runnable {
            replyFlashScrollListener?.let { rvMessages.removeOnScrollListener(it) }
            replyFlashScrollListener = null
            replyFlashTimeoutRunnable = null
            if (replyFlashAnimator?.isRunning != true) {
                rvMessages.findViewHolderForAdapterPosition(index)?.itemView
                    ?.let { animateReplyFlash(it) }
            }
        }
        replyFlashTimeoutRunnable = timeout
        rvMessages.postDelayed(timeout, 1500L)
    }

    /**
     * 1000ms flash on a row — 200ms fade-in (alpha 0→64/255), 500ms hold, 300ms
     * fade-out. Uses [View.foreground] so the bubble drawable is untouched; alpha
     * caps at 64 (~25%) so the text stays readable through the tint.
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
                override fun onAnimationEnd(animation: android.animation.Animator) { row.foreground = null }
                override fun onAnimationCancel(animation: android.animation.Animator) { row.foreground = null }
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

    // CHAT-091 v3: swipe-to-reply sets the reply target WITHOUT popping the
    // keyboard (a keyboard after every swipe would cover the conversation).
    private fun beginReplyToWithoutKeyboard(message: ChatMessage) {
        if (message.isDateHeader || message.isDeleted) return
        if (isPendingMessage(message)) return
        pendingReplyTo = message
        updateReplyPreviewUi()
    }

    private fun showChatMessageContextMenu(anchor: View, message: ChatMessage, position: Int) {
        if (!isUiSafe()) return
        if (message.isDateHeader) return
        // Once deleted there's nothing to reply to, react to, or re-delete.
        if (message.isDeleted) return
        if (isPendingMessage(message)) return
        val popup = PopupMenu(this, anchor, Gravity.END)
        menuInflater.inflate(R.menu.menu_chat_message, popup.menu)
        // Delete-for-everyone is only offered to the SENDER of the message.
        popup.menu.findItem(R.id.action_delete)?.isVisible = message.isSentByMe
        // CHAT-138: "Delete for me" is a local-only hide, offered on ANY message
        // (sent or received) that has reached the menu (pending/deleted already
        // early-returned above).
        popup.menu.findItem(R.id.action_delete_for_me)?.isVisible = true
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
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
                    confirmDeleteForMe(message)
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
        // Reply-target safety: if the row I'm about to remove is the active reply
        // anchor, clear the composer state so the preview doesn't point at a gone row.
        if (pendingReplyTo?.id == message.id) {
            pendingReplyTo = null
            updateReplyPreviewUi()
        }
        // Rebuild from the current rows: filterOutLocallyDeleted (inside
        // rebuildMessagesWithHeaders) drops the row we just hid, and the rebuild
        // re-derives date headers so none are left orphaned over an empty day.
        rebuildMessagesWithHeaders(messages.filterNot { it.isDateHeader })
        chatAdapter.notifyDataSetChanged()
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
        markMessageDeletedLocally(message)
        if (pendingReplyTo?.id == message.id) {
            pendingReplyTo = null
            updateReplyPreviewUi()
        }

        val delivered = socketManager.deleteMessage(myUserId, peerUserId, message.id)
        Log.d("ChatDelete", "performDeleteForEveryone id=$idForLog via=socket delivered=$delivered")
        if (delivered) {
            showAppToast(getString(R.string.chat_message_deleted_toast), Toast.LENGTH_SHORT)
            return
        }

        apiManager.deleteChatMessage(
            myUserId,
            peerUserId,
            message.id,
            object : NetworkCallback<com.gmwapp.hima.retrofit.responses.SimpleAckResponse> {
                override fun onResponse(
                    call: Call<com.gmwapp.hima.retrofit.responses.SimpleAckResponse>,
                    response: Response<com.gmwapp.hima.retrofit.responses.SimpleAckResponse>
                ) {
                    val ok = response.isSuccessful &&
                        (response.body()?.success != false)
                    Log.d("ChatDelete", "performDeleteForEveryone id=$idForLog via=rest ok=$ok http=${response.code()}")
                    if (ok) {
                        if (isUiSafe()) {
                            showAppToast(getString(R.string.chat_message_deleted_toast), Toast.LENGTH_SHORT)
                        }
                    } else {
                        rollbackDeleteOnFailure(message)
                    }
                }

                override fun onFailure(
                    call: Call<com.gmwapp.hima.retrofit.responses.SimpleAckResponse>,
                    t: Throwable
                ) {
                    Log.w("ChatDelete", "performDeleteForEveryone id=$idForLog via=rest FAILED: ${t.message}")
                    rollbackDeleteOnFailure(message)
                }

                override fun onNoNetwork() {
                    // Leave the tombstone in place so the user sees their intent was
                    // accepted locally; the backend will not learn about it until the
                    // socket reconnects — acceptable for MVP.
                    Log.w("ChatDelete", "performDeleteForEveryone id=$idForLog via=rest NO_NETWORK — tombstone stays")
                }
            }
        )
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
            val observer = Observer<RegisterResponse?> { response ->
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
        // Friends-Gated Chat: the friendship lock (friendship_lock_container) now owns the
        // not-friends CTA, so the legacy add-friend banner is retired to avoid duplicate UI.
        bannerAddFriend?.visibility = View.GONE
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

        // Profile icon click - open UserProfileDetailActivity
        ivUser.setOnClickListener {
            openUserProfile()
        }
    }

    private fun setupComposer() {
        btnSend.setOnClickListener {
            sendMessage()
        }

        setupEmojiPanel()

        btnEmoji.setOnClickListener {
            toggleEmojiPanel()
        }

        // Tapping the input to type dismisses the emoji panel (keyboard takes over).
        etMessage.setOnClickListener {
            if (emojiGrid.visibility == View.VISIBLE) hideEmojiPanel()
        }

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
            }
        })

        updateComposerActionState()
    }

    private fun openUserProfile() {
        val intent = Intent(this, UserProfileDetailActivity::class.java).apply {
            putExtra(DConstants.USER_ID, peerUserId)
            // Use the resolved header name (structured/API/store), NOT the raw intent
            // extra — on the notification-tap path that extra can be the poisoned
            // "<name> sent you a message" title, which would carry into the profile.
            putExtra("USER_NAME", peerName.takeIf { it.isNotBlank() && it != "User" }
                ?: intent.getStringExtra("USER_NAME") ?: "User")
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
            // "You" header — show the logged-in user's own avatar, not a blank
            // placeholder. Same self-image source the call/profile screens use.
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.image.orEmpty()
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
        // Image + audio attachments removed (requirement): text-only composer.
        // Send is always visible; mic stays permanently hidden. (Emoji button is
        // always visible and independent of text state.)
        btnMic.visibility = View.GONE
        btnSend.visibility = View.VISIBLE
    }

    // ---- Emoji picker (lightweight in-app grid; replaces the old image attach slot) ----

    private val emojiList: List<String> by lazy {
        listOf(
            "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😊",
            "🙂", "😉", "😌", "😍", "🥰", "😘", "😗", "😋",
            "😜", "🤪", "😎", "🤩", "🥳", "😏", "😔", "😴",
            "🤗", "🤔", "🤭", "🤫", "😐", "😶", "🙄", "😬",
            "😳", "🥺", "😢", "😭", "😤", "😠", "😡", "🥶",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "💖", "💗", "💕", "💞", "💯", "🔥", "✨", "🌟",
            "👍", "👎", "👏", "🙏", "🤝", "💪", "🙌", "👌",
            "🎉", "🎊", "🌸", "🌹", "🌻", "🎁", "☕", "🍕"
        )
    }

    private fun setupEmojiPanel() {
        emojiGrid.adapter = ArrayAdapter(this, R.layout.item_emoji, emojiList)
        emojiGrid.setOnItemClickListener { _, _, position, _ ->
            insertEmoji(emojiList[position])
        }
    }

    private fun toggleEmojiPanel() {
        if (emojiGrid.visibility == View.VISIBLE) hideEmojiPanel() else showEmojiPanel()
    }

    private fun showEmojiPanel() {
        // Hide the soft keyboard so the panel occupies that space cleanly.
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(etMessage.windowToken, 0)
        etMessage.requestFocus()
        emojiGrid.visibility = View.VISIBLE
        btnEmoji.text = getString(R.string.chat_keyboard_glyph)
    }

    private fun hideEmojiPanel() {
        emojiGrid.visibility = View.GONE
        btnEmoji.text = getString(R.string.chat_emoji_glyph)
    }

    /** Inserts [emoji] at the current cursor position (or replaces the selection). */
    private fun insertEmoji(emoji: String) {
        val editable = etMessage.text ?: return
        val start = etMessage.selectionStart.coerceIn(0, editable.length)
        val end = etMessage.selectionEnd.coerceIn(0, editable.length)
        editable.replace(minOf(start, end), maxOf(start, end), emoji)
        etMessage.setSelection((minOf(start, end) + emoji.length).coerceAtMost(etMessage.text.length))
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
                showAppToast(e.message ?: "Couldn't prepare image", Toast.LENGTH_SHORT)
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
            showAppToast("Couldn't start recording", Toast.LENGTH_SHORT)
        }
    }

    private fun stopAudioRecordingAndSend() {
        try {
            val recordingResult = audioRecorderController.stop()
            isRecording = false
            setRecordingUiVisible(false)
            if (recordingResult.durationMs < 1000L) {
                recordingResult.file.delete()
                // B111: the original short "Hold to record" LENGTH_SHORT toast
                // flashed for ~2 s at the bottom of the screen and testers
                // missed it — they reported "voice note not sending, popup not
                // clearly visible." Use a longer, more instructive message so
                // the user understands WHY the voice note didn't send and how
                // to fix it.
                showAppToast(
                    "Press and hold the mic to record a voice note. Release to send, slide up to cancel.",
                    Toast.LENGTH_LONG
                )
                return
            }

            val tempId = addOptimisticMediaMessage(
                messageType = "audio",
                localAttachmentUrl = recordingResult.file.toURI().toString(),
                audioDurationMs = recordingResult.durationMs
            )
            // TC_015: carry the measured duration through upload→send so it's persisted
            // server-side and echoed back, instead of being lost on the round-trip.
            uploadAndSendAttachment(tempId, recordingResult.file, "audio", audioDurationMs = recordingResult.durationMs)
        } catch (e: Exception) {
            Log.e("ChatMedia", "Audio recording stop failed: ${e.message}", e)
            isRecording = false
            setRecordingUiVisible(false)
            showAppToast("Couldn't save voice note", Toast.LENGTH_SHORT)
        }
    }

    private fun cancelAudioRecording(showToast: Boolean) {
        audioRecorderController.cancel()
        isRecording = false
        setRecordingUiVisible(false)
        if (showToast) {
            showAppToast("Recording canceled", Toast.LENGTH_SHORT)
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
        // TC_015: remember the duration too so the socket-error REST fallback still sends it.
        rememberPendingOutgoing(tempId, "", messageType, localAttachmentUrl, audioDurationMs.takeIf { it > 0 })
        // B-v1110 #8 (sibling) — bounds-guard the attachment-send scroll too.
        rvMessages.post {
            val lastPos = messages.size - 1
            if (lastPos >= 0) {
                rvMessages.smoothScrollToPosition(lastPos)
            }
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

        val existing = messages[tempIndex]
        // TC_015: don't lose the optimistic voice-note duration if the echoed real
        // message arrives without one (e.g. before the server persists/returns it).
        val preservedDuration = when {
            realMessage.audioDurationMs > 0L -> realMessage.audioDurationMs
            realMessage.messageType == "audio" -> existing.audioDurationMs
            else -> 0L
        }
        // M18: ChatMessage.reactions is val — copy() instead of mutating in place.
        messages[tempIndex] = realMessage.copy(
            reactions = existing.reactions,
            audioDurationMs = preservedDuration
        )
        pendingOutgoingByTempId.remove(tempId)
        messageSendMethod.remove(tempId)
        messageSendMethod[realMessage.id] = method
        rebuildMessagesWithHeaders(messages.filterNot { it.isDateHeader })
        chatAdapter.notifyDataSetChanged()
        return true
    }

    private fun failPendingOutgoing(tempId: String, userMessage: String) {
        removeTempMessage(tempId)
        showAppToast(userMessage, Toast.LENGTH_SHORT)
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

    private fun uploadAndSendAttachment(tempId: String, file: File, messageType: String, attempt: Int = 1, audioDurationMs: Long? = null) {
        // TC_016: attachment uploads failed intermittently (transient timeout / 5xx) with
        // no retry, surfacing "Couldn't send attachment". Retry a couple of times with a
        // short backoff before giving up; only the FINAL attempt removes the temp + deletes
        // the file, so the optimistic bubble stays put while we retry.
        val maxAttempts = 3
        val retryUpload = retry@{
            if (attempt >= maxAttempts) return@retry false
            activeAttachmentTempIds.remove(tempId)
            activeAttachmentCalls.remove(tempId)
            rvMessages.postDelayed(
                { if (!isFinishing && !isDestroyed) uploadAndSendAttachment(tempId, file, messageType, attempt + 1, audioDurationMs) },
                1500L * attempt
            )
            Log.w("ChatMedia", "TC_016: retrying attachment upload, attempt ${attempt + 1}/$maxAttempts")
            true
        }
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
                        // Retry transient server errors (5xx); permanent failures (4xx,
                        // version gate) fall through and surface the message immediately.
                        if (response.code() >= 500 && retryUpload()) return
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
                    rememberPendingOutgoing(tempId, "", messageType, remoteUrl, audioDurationMs)

                    if (!socketManager.isConnected()) {
                        sendMediaViaFallbackAPI(tempId, messageType, remoteUrl!!, audioDurationMs)
                        file.delete()
                        return
                    }

                    messageSendMethod[tempId] = "socket"
                    socketManager.sendMessage(
                        myUserId,
                        peerUserId,
                        "",
                        messageType,
                        remoteUrl,
                        audioDurationMs
                    )
                    file.delete()
                }

                override fun onFailure(call: Call<ChatAttachmentUploadResponse>, t: Throwable) {
                    activeAttachmentTempIds.remove(tempId)
                    activeAttachmentCalls.remove(tempId)
                    if (call.isCanceled) {
                        file.delete()
                        return
                    }
                    // Transient network error (timeout/reset) — retry before giving up.
                    if (retryUpload()) return
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
    private fun sendMediaViaFallbackAPI(tempId: String, messageType: String, attachmentUrl: String, audioDurationMs: Long? = null) {
        messageSendMethod[tempId] = "api"
        val apiCall = apiManager.fallbackSendMessage(
            fromUserId = myUserId,
            toUserId = peerUserId,
            message = "",
            messageType = messageType,
            attachmentUrl = attachmentUrl,
            audioDurationMs = audioDurationMs,
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
                        } else {
                            removeTempMessage(tempId)
                            showAppToast(
                                responseBody?.message ?: "Couldn't send attachment",
                                Toast.LENGTH_SHORT
                            )
                            // Friends-gated chat: same as the text path — re-fetch the gate so
                            // the composer locks with the Add-Friend / Subscribe CTA.
                            val gateCode = responseBody?.code
                            if (gateCode == "FRIENDS_REQUIRED" || gateCode == "AUTOPAY_REQUIRED" || gateCode == "ACCOUNT_BLOCKED") {
                                refreshChatGate()
                            }
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
    
    private fun updateOnlineStatusFromAPI(lastOnlineStatus: String?) {
        this.lastOnlineStatus = lastOnlineStatus
        mainHandler.post {
            if (!isUiSafe()) return@post
            // Show status only if it's not null or empty
            if (!lastOnlineStatus.isNullOrEmpty()) {
                tvUserStatus.text = formatLastOnlineStatus(lastOnlineStatus)
                // CM_005: explicitly re-show — the empty branch now parks it at
                // INVISIBLE (not GONE), so it must be flipped back to VISIBLE here.
                tvUserStatus.visibility = View.VISIBLE
                vOnlineIndicator.visibility = View.VISIBLE
            } else {
                // CM_005: keep the status line's height reserved when there's no
                // status yet — use INVISIBLE, not GONE, so the name+status column
                // height stays constant and the username never reflows/shifts
                // down when the status later loads.
                tvUserStatus.text = ""
                tvUserStatus.visibility = View.INVISIBLE
                vOnlineIndicator.visibility = View.GONE
            }

            // Removed: Update call buttons state based on online status
            // Buttons are now controlled only by check_call_availability API response
        }
    }

    /**
     * Backend sends strings like "active 6 hours ago" / "active 5 minutes ago" /
     * "Online" / "Just now". Strip the redundant leading "active " so the header
     * just reads "6 hours ago" — `Online` / `Just now` are passed through.
     */
    private fun formatLastOnlineStatus(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("active ", ignoreCase = true) ->
                trimmed.substring("active ".length).trim()
            else -> trimmed
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
                    val nextStatus = if (sock.isRead) {
                        MessageDeliveryStatus.READ
                    } else {
                        MessageDeliveryStatus.DELIVERED
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
                // Friends-gated chat rejection (server throws "FRIENDS_REQUIRED: chat is
                // locked" / "AUTOPAY_REQUIRED: ..."). The REST fallback enforces the SAME gate,
                // so retrying it is pointless. Re-fetch the gate so the composer locks and shows
                // the Add-Friend / Subscribe CTA instead of leaving an open input box.
                val isAccountBlocked = error.startsWith("ACCOUNT_BLOCKED")
                val isGateError = error.startsWith("FRIENDS_REQUIRED") || error.startsWith("AUTOPAY_REQUIRED") || isAccountBlocked
                if (isGateError) refreshChatGate()
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
                if (isGateError) {
                    // Mark this message failed with the friendly reason; do NOT retry over REST.
                    val msg = when {
                        isAccountBlocked -> "Your account has been blocked."
                        error.startsWith("AUTOPAY_REQUIRED") -> "Subscribe to start chatting."
                        else -> "You can chat once you are friends."
                    }
                    failPendingOutgoing(tempId, msg)
                    return@collect
                }
                if (payload.messageType == "text") {
                    messageSendMethod[tempId] = "api"
                    sendMessageViaAPI(tempId, payload.message)
                } else {
                    // TC_016: media parity with the text path above. The file is
                    // already uploaded (we hold its remote URL), so a dropped
                    // socket emit recovers via the same REST fallback instead of
                    // failing with "Couldn't send attachment". Only retry when we
                    // actually have the URL; otherwise there is nothing to resend.
                    val mediaUrl = payload.attachmentUrl
                    if (!mediaUrl.isNullOrBlank()) {
                        sendMediaViaFallbackAPI(tempId, payload.messageType, mediaUrl, payload.audioDurationMs)
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

        lifecycleScope.launch {
            socketManager.messagesRead.collect { ev ->
                if (!isUiSafe()) return@collect
                // The reader is my chat peer → flip my sent bubbles to READ (blue tick)
                // for everything up to the id they've read, live, no reload needed.
                // Snapshot peerUserId to avoid a mutation race with onNewIntent peer-switch.
                val currentPeer = peerUserId
                if (ev.readerId == currentPeer) {
                    markSentMessagesReadUpTo(ev.lastMessageId)
                }
            }
        }
    }

    /** Flip my own sent messages (id <= [lastMessageId]) to READ for live blue ticks. */
    private fun markSentMessagesReadUpTo(lastMessageId: Long) {
        var changed = false
        for (i in messages.indices) {
            val m = messages[i]
            if (m.isDateHeader || !m.isSentByMe || m.deliveryStatus == MessageDeliveryStatus.READ) continue
            val idLong = m.id.toLongOrNull() ?: continue
            if (idLong <= lastMessageId) {
                messages[i] = m.copy(deliveryStatus = MessageDeliveryStatus.READ)
                changed = true
            }
        }
        if (changed) chatAdapter.notifyDataSetChanged()
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
                // CHAT-138: the cached snapshot may predate a "Delete for me" done
                // earlier this session — re-filter so a hidden message can't return.
                rebuildMessagesWithHeaders(snap.filterNot { it.isDateHeader })
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
                // C-30: no cached snapshot to hydrate → the area would be a bare
                // blank until the fetch returns. Show the skeleton instead. The
                // isInitialHistoryLoading setter hides it when the load ends.
                showChatSkeleton()
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
                            // Re-evaluate the composer: if I've blocked this peer, lock it
                            // with an Unblock CTA instead of leaving the input box open.
                            applyComposerGate()

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
                            // TC_017: hide any messages the user cleared via "block + delete chat".
                            val sortedMessages = dropClearedMessages(
                                convertedMessages.sortedBy { it.date?.time ?: 0L }
                            )

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
                            
                            // Update online status from API response
                            updateOnlineStatusFromAPI(data.lastOnlineStatus)
                            
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
                                // TC_017: also drop any messages cleared via "block + delete chat"
                                // so pagination (scroll-up) can't resurrect them.
                                val sortedOlderMessages = dropClearedMessages(
                                    convertedMessages.sortedBy { it.date?.time ?: 0L }
                                )
                                
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
                                    val oldSize = messages.size
                                    rebuildMessagesWithHeaders(existingNonHeaders + filteredOlderMessages)
                                    chatAdapter.notifyDataSetChanged()
                                    
                                    // Restore scroll position to prevent jumping (WhatsApp style)
                                    // The position shifts by the number of items we added
                                    val newSize = messages.size
                                    val addedCount = newSize - oldSize
                                    rvMessages.post {
                                        layoutManager?.scrollToPositionWithOffset(
                                            currentFirstVisiblePosition + addedCount,
                                            offset
                                        )
                                        Log.d("ChatPagination", "Restored scroll position - New position: ${currentFirstVisiblePosition + addedCount}")
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
                    // CHAT-084: a reply-tap may be waiting for its original to be
                    // paged in — retry the jump now that this page has landed.
                    // Only on a successful page; on HTTP error don't retry (it would
                    // loop loadMoreMessages and bypass the 429 cooldown) — clear it.
                    if (response.isSuccessful) {
                        rvMessages.post { maybeRetryPendingReplyScroll() }
                    } else {
                        clearPendingReplyScroll()
                    }
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
        
        val typed = etMessage.text.toString().trim()
        if (typed.isEmpty()) {
            // T24: still clear whitespace-only input so it doesn't linger after tap.
            etMessage.setText("")
            return
        }
        // Notification conversion: user is actively engaging (sending a chat message).
        // Fired here (once per notification, via per-action dedupe) so it covers both
        // the socket and API-fallback send paths without double-counting.
        BaseApplication.getInstance()?.let { app ->
            app.trackNotificationConversion(app.getLastNotificationId(), "message_sent")
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
        // B-v1110 #8 — guard against an empty list (size-1 == -1 → "Invalid target
        // position"). The post{} runs async, so the list could be emptied first.
        rvMessages.post {
            val lastPos = messages.size - 1
            if (lastPos >= 0) {
                rvMessages.smoothScrollToPosition(lastPos)
            }
        }

        // Try Socket.IO first, fallback to API
        val tempMessageId = tempMessage.id
        rememberPendingOutgoing(tempMessageId, bodyToSend, "text")
        if (socketManager.isConnected()) {
            messageSendMethod[tempMessageId] = "socket"
            // ⭐ Updated to use new signature: sendMessage(fromUserId, toUserId, message, messageType, attachmentUrl)
            socketManager.sendMessage(myUserId, peerUserId, bodyToSend, "text")
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
                        } else {
                            // Friends-gated chat: server returns success=false + code
                            // FRIENDS_REQUIRED/AUTOPAY_REQUIRED (HTTP 200). Surface the friendly
                            // reason and re-fetch the gate so the composer locks with the
                            // Add-Friend / Subscribe CTA — not a generic "Couldn't send" toast.
                            val gateCode = responseBody?.code
                            failPendingOutgoing(
                                tempId,
                                responseBody?.message ?: "Couldn't send message"
                            )
                            if (gateCode == "FRIENDS_REQUIRED" || gateCode == "AUTOPAY_REQUIRED" || gateCode == "ACCOUNT_BLOCKED") {
                                refreshChatGate()
                            }
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
            // TC_017: a socket backlog replay (reconnect / late delivery) can re-deliver a
            // message that was already cleared via "block + delete chat". Honour the same
            // cleared-upto watermark the history path (dropClearedMessages) uses, so cleared
            // messages can't reappear through the realtime path a few minutes later.
            val clearedUpto = ClearedChatsPrefsHelper.getClearedUpto(this, myUserId, peerUserId)
            val msgTime = chatMessage.date?.time
            if (clearedUpto > 0L && msgTime != null && msgTime <= clearedUpto) {
                Log.d("RealtimeChat", "TC_017: dropping socket message at/before cleared watermark")
                return
            }
            val wasNearBottom = isRecyclerNearBottom()
            insertMessageChronologically(chatMessage)

            if (isSentByMe || wasNearBottom) {
                // B-v1110 #8 (sibling) — bounds-guard the incoming-message scroll too.
                rvMessages.post {
                    val lastPos = messages.size - 1
                    if (lastPos >= 0) {
                        rvMessages.smoothScrollToPosition(lastPos)
                    }
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

        val deliveryStatus = when {
            !isSentByMe -> MessageDeliveryStatus.DELIVERED
            apiMsg.isRead -> MessageDeliveryStatus.READ
            else -> MessageDeliveryStatus.DELIVERED
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
            deliveryStatus = deliveryStatus,
            isDeleted = isDeleted,
            // TC_015: real stored duration if present; 0 => adapter resolves from the file.
            audioDurationMs = apiMsg.audioDurationMs ?: 0L
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
        
        val deliveryStatus = when {
            !isSentByMe -> MessageDeliveryStatus.DELIVERED
            socketMsg.isRead -> MessageDeliveryStatus.READ
            else -> MessageDeliveryStatus.DELIVERED
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
            deliveryStatus = deliveryStatus,
            // T6: carry through the server's tombstone flag so a socket-only delivery
            // (no API refresh in flight) renders the deleted-bubble state immediately.
            isDeleted = socketMsg.isDeleted,
            // TC_015: real stored duration if the server echoed it; 0 => resolve from file.
            audioDurationMs = socketMsg.audioDurationMs ?: 0L
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
        
        val deliveryStatus = when {
            !isSentByMe -> MessageDeliveryStatus.DELIVERED
            fallbackMsg.isRead -> MessageDeliveryStatus.READ
            else -> MessageDeliveryStatus.DELIVERED
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
            deliveryStatus = deliveryStatus,
            audioDurationMs = fallbackMsg.audioDurationMs ?: 0L
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
        messages.addAll(sortedChatMessages(filterOutLocallyDeleted(source)))
        updateTopHeader(messages)
    }

    /**
     * CHAT-138: remove rows whose id is in this peer's "Delete for me" set.
     * Date headers always pass.
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
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (lastVisible == RecyclerView.NO_POSITION) return true
        return lastVisible >= messages.size - 2
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
        // Friends-Gated Chat: the gate is per-conversation. Drop the previous
        // peer's gate so peer B never inherits peer A's "unlocked" composer
        // (a real friends-gate bypass when switching peers via onNewIntent).
        // onNewIntent re-applies the (now-null) gate immediately, then refreshes.
        lastChatGate = null
        isFriendWithPeer = false
        // Block state is also per-conversation: reset it so peer B never inherits
        // peer A's "You blocked this user" composer lock when switching via onNewIntent.
        iHaveBlockedThisUser = false
        activeAttachmentTempIds.clear()
        messages.clear()
        clearNewMessagePill()
        if (::chatAdapter.isInitialized) {
            chatAdapter.notifyDataSetChanged()
        }
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
        // CHAT-138: if the user already deleted this messageId for themselves on this
        // device, don't let it come back via a socket replay or history pagination.
        // Date headers and pending optimistic rows skip the check.
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
        // The legacy `chats/mark-read` route (by chat_id) was never deployed
        // server-side — it always 404'd, so messages that arrived while the thread
        // was open never got cleared and the inbox badge kept counting them.
        // Delegate to the working `mark_messages_read` path (by last message id),
        // still throttled so a burst of incoming messages doesn't spam the endpoint.
        val now = SystemClock.elapsedRealtime()
        if (now - lastMarkedChatReadAt < markChatReadCooldownMs) {
            return
        }
        lastMarkedChatReadAt = now
        markMessagesAsReadIfAvailable()
    }

    private fun markMessagesAsReadWithLastMessageId(lastMessageId: Long) {
        // T20: skip when the server already knows about this id (or a newer one).
        val alreadyMarked = lastMarkedReadMessageId?.toLongOrNull()
        if (alreadyMarked != null && lastMessageId <= alreadyMarked) {
            return
        }
        lastMarkedReadMessageId = lastMessageId.toString()
        // Real-time blue ticks: tell the sender immediately over the socket (the REST
        // call below still persists is_read; this is just the live signal so the
        // sender's ticks flip without waiting for them to reload history).
        socketManager.markRead(myUserId, peerUserId, lastMessageId)
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
        applyComposerGate()
        refreshChatGate()
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

        // Mark read on exit so the inbox badge clears reliably on the next
        // `my_chat` refresh. Both calls below resolve to the same working
        // `mark_messages_read` path (by last message id); the id-equality guard
        // means the second is a cheap no-op.
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

        // C-30: stop the skeleton pulse so its animator can't outlive the activity.
        chatSkeletonAnim?.cancel()
        chatSkeletonAnim = null

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
        // CHAT-084: stop the reply-flash so its animator/listener/timeout can't
        // fire on a destroyed view tree (recycled row tint bleed / leak).
        replyFlashAnimator?.cancel()
        replyFlashAnimator = null
        replyFlashScrollListener?.let { rvMessages.removeOnScrollListener(it) }
        replyFlashScrollListener = null
        replyFlashTimeoutRunnable?.let { rvMessages.removeCallbacks(it) }
        replyFlashTimeoutRunnable = null
        // T12: cancel any in-flight attachment uploads / sends so their callbacks
        // don't run on a destroyed view tree.
        activeAttachmentCalls.values.forEach { runCatching { it.cancel() } }
        activeAttachmentCalls.clear()
        activeTextSendCalls.forEach { runCatching { it.cancel() } }
        activeTextSendCalls.clear()
        isInitialHistoryLoading = false

        mainHandler.removeCallbacks(logSocketStatusAfterDelay)
        mainHandler.removeCallbacks(recordingTicker)
        stopRecordingPulse()
        audioRecorderController.release()
        // B-v1110 #7 — onPause guards this with ::chatAdapter.isInitialized but
        // onDestroy did not; an early teardown (activity destroyed before the
        // adapter is created) crashed with UninitializedPropertyAccessException.
        if (::chatAdapter.isInitialized) {
            chatAdapter.release()
        }

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
        // If the emoji panel is open, back just closes it (don't leave the chat).
        if (::emojiGrid.isInitialized && emojiGrid.visibility == View.VISIBLE) {
            hideEmojiPanel()
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

        // Clear / Delete chat — parity with the male overflow menu (showRegularMenu).
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

        // Tint "Delete chat" red so the destructive action reads as such
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
        androidx.core.content.ContextCompat.getDrawable(this, R.drawable.circle_bg_accent)?.mutate()?.let { bg ->
            bg.setTint(color)
            ivIcon.background = bg
        }

        dialogView.findViewById<android.widget.TextView>(R.id.tv_title).setText(titleRes)
        dialogView.findViewById<android.widget.TextView>(R.id.tv_message).setText(messageRes)

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

    private fun deleteChatLocally() {
        // Newest currently-loaded message time (fallback now) is the watermark for both:
        //  - ClearedChatsPrefsHelper: hides these messages inside the conversation so a
        //    server re-fetch on reopen can't resurrect them.
        //  - DeletedChatsPrefsHelper: removes the conversation from the chat list until a
        //    NEW message (beyond this watermark) arrives — WhatsApp-style.
        // Without these the delete was purely in-memory and reverted on the next list load.
        val deletedUpto = messages.asSequence()
            .filterNot { it.isDateHeader }
            .mapNotNull { it.date?.time }
            .maxOrNull() ?: System.currentTimeMillis()
        ClearedChatsPrefsHelper.setClearedUpto(this, myUserId, peerUserId, deletedUpto)
        DeletedChatsPrefsHelper.setDeletedUpto(this, myUserId, peerUserId, deletedUpto)
        messages.clear()
        chatAdapter.notifyDataSetChanged()
        runCatching { historyCache.putSnapshot(peerUserId, emptyList()) }
        showAppToast(getString(R.string.chat_deletechat_toast), Toast.LENGTH_SHORT)
        finish()
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

        // B099 follow-up — WhatsApp-style optional "also delete chat" alongside
        // the block action. Unchecked = current behaviour (chat-block only).
        // Checked = block + clear local chat list so the conversation no longer
        // shows on this device.
        val alsoDeleteChat = dialogView.findViewById<android.widget.CheckBox>(R.id.cb_also_delete_chat)

        // Set button listeners
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_block).setOnClickListener {
            val deleteAlso = alsoDeleteChat?.isChecked == true
            blockUser()
            if (deleteAlso) {
                clearChatLocally()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Clears the local chat list (in-memory + history cache) for the current
     * peer. Used by the "Also delete chat" option in the block dialog so the
     * user doesn't keep seeing the conversation history on this device after
     * blocking.
     *
     * Doesn't touch the server — the other side still has the chat, and so
     * does our own server-side history (the user explicitly chose "clear for
     * me", not "delete for everyone"). The empty snapshot in the cache means
     * a same-session re-open won't restore the cleared messages; a fresh app
     * launch may re-fetch from server, but at that point the peer is blocked
     * and no new messages can arrive anyway.
     */
    private fun clearChatLocally() {
        val sizeBefore = messages.size
        // B_002/B_003/B_008: "block + also delete chat" clears the MESSAGES but must KEEP
        // the conversation row in the chat list as an empty thread (with the blocked badge,
        // per TC_022) — a blocked peer can never send a new message, so setting the Deleted
        // watermark here would remove the row forever (that was the reported defect).
        // Cover EVERYTHING up to now (a blocked peer can't send anymore, so now is a safe
        // hard watermark) and record ONLY the Cleared watermark:
        //  - ClearedChatsPrefsHelper → empties the messages inside the conversation
        // We deliberately do NOT set DeletedChatsPrefsHelper here (that is only for the
        // standalone "Delete chat" action in deleteChatLocally()).
        val deletedUpto = System.currentTimeMillis()
        ClearedChatsPrefsHelper.setClearedUpto(this, myUserId, peerUserId, deletedUpto)
        messages.clear()
        chatAdapter.notifyDataSetChanged()
        runCatching { historyCache.putSnapshot(peerUserId, emptyList()) }
        Log.d(
            "ChatActivityInHouse",
            "🧹 Block+delete chat for peer=$peerUserId messagesBefore=$sizeBefore deletedUpto=$deletedUpto"
        )
        showAppToast(getString(R.string.chat_clearchat_toast), Toast.LENGTH_SHORT)
    }

    /**
     * TC_017: drop messages cleared via "block + also delete chat". Anything at or before
     * the persisted cleared-upto watermark for this peer is hidden on this device, so a
     * server re-fetch / pagination cannot resurrect it. Messages with no parseable date
     * are kept (can't be confidently classified as cleared).
     */
    private fun dropClearedMessages(serverMessages: List<ChatMessage>): List<ChatMessage> {
        val clearedUpto = ClearedChatsPrefsHelper.getClearedUpto(this, myUserId, peerUserId)
        if (clearedUpto <= 0L) return serverMessages
        return serverMessages.filter { msg ->
            val t = msg.date?.time
            t == null || t > clearedUpto
        }
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

        // The shared layout carries the "Also delete chat for me" checkbox for the
        // BLOCK flow. Unblock never reads it, so hide it here so it doesn't show on
        // the Unblock dialog.
        dialogView.findViewById<android.widget.CheckBox>(R.id.cb_also_delete_chat)?.visibility =
            android.view.View.GONE

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
                            // Lock the composer immediately — don't wait for the history
                            // round-trip, or the input box stays usable in the gap.
                            applyComposerGate()
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
                    showAppToast("Failed to block user: ${t.message}", Toast.LENGTH_SHORT)
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
                            // BUG-004 / WhatsApp-style: if this chat was block+deleted, bring
                            // the row back into the list on unblock — but leave the Cleared
                            // watermark so it comes back EMPTY (old messages stay deleted).
                            DeletedChatsPrefsHelper.clearForPeer(this@ChatActivityInHouse, myUserId, peerUserId)
                            // Unlock the composer immediately so the user isn't left
                            // staring at the Unblock lock until history returns.
                            applyComposerGate()
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
                    showAppToast("Failed to unblock user: ${t.message}", Toast.LENGTH_SHORT)
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
                // Notification conversion: creator turned calls ON (audio or video).
                if (response.data.audio_status == 1 || response.data.video_status == 1) {
                    val app = BaseApplication.getInstance()
                    app?.trackNotificationConversion(app.getLastNotificationId(), "call_enable")
                }
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
        val isFemaleUser = userData?.gender?.equals(DConstants.FEMALE, ignoreCase = true) == true

        // Females don't get a video-call entry point — hide the whole video column,
        // leaving Audio only (mirrors the Recent Calls screen). Males keep both.
        if (isFemaleUser) {
            findViewById<View>(R.id.ll_video_call_column)?.visibility = View.GONE
        }

        // Both males and female creators get the in-chat call buttons. checkCallAvailability()
        // returns is_blocked + the peer's audio/video status; updateCallButtonsState() already
        // skips the peer-status gate for a female caller (males have no UI to set those flags),
        // so both buttons enable correctly for her.
        if ((isMaleUser || isFemaleUser) && peerUserId > 0 && myUserId > 0) {
            checkCallAvailability()
        } else {
            callButtonsContainer.visibility = View.GONE
        }
    }

    private fun checkCallAvailability() {
        // The endpoint hard-validates that male_user_id is actually male and
        // female_user_id female. When the viewer is the female creator, SHE is the
        // female_user_id and the male peer is the male_user_id — so swap the args by
        // role, otherwise the backend rejects with success=false/null data and the
        // call buttons are silently hidden for every creator.
        val isCurrentUserFemale = BaseApplication.getInstance()?.getPrefs()
            ?.getUserData()?.gender?.equals(DConstants.FEMALE, ignoreCase = true) == true
        val maleId = if (isCurrentUserFemale) peerUserId else myUserId
        val femaleId = if (isCurrentUserFemale) myUserId else peerUserId
        apiManager.checkCallAvailability(
            maleUserId = maleId,
            femaleUserId = femaleId,
            object : NetworkCallback<CheckCallAvailabilityResponse> {
                override fun onResponse(
                    call: Call<CheckCallAvailabilityResponse>,
                    response: Response<CheckCallAvailabilityResponse>
                ) {
                    if (response.isSuccessful) {
                        val responseData = response.body()?.data
                        if (responseData != null) {
                            isCallBlocked = responseData.is_blocked
                            peerCallBlocked = responseData.call_blocked
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
        
        // If blocked (is_blocked = true) OR auto-blocked by 3 rejects (call_blocked),
        // disable both buttons. FEMALE_3_REJECT_BLOCK greys them for the 60-min cooldown.
        if (isCallBlocked || peerCallBlocked) {
            Log.d("CallButtons", "User is BLOCKED (is_blocked=$isCallBlocked call_blocked=$peerCallBlocked) - disabling both audio and video buttons")
            mainHandler.post {
                if (!isUiSafe()) return@post
                // DISABLED - Gray for both buttons
                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.chat_call_grey_bg))
                ivAudioCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvAudioCall.isEnabled = false
                tvAudioRateTop?.setTextColor(ContextCompat.getColor(this, R.color.grey_medium))
                
                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.chat_call_grey_bg))
                ivVideoCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvVideoCall.isEnabled = false
                tvVideoRateTop?.setTextColor(ContextCompat.getColor(this, R.color.grey_medium))
            }
            return
        }
        
        // Check individual audio and video status (0 = disabled, 1 = enabled).
        // audio_status / video_status are the FEMALE creator's opt-in toggles
        // (set via fragment_female_home.xml s_audio / s_video switches).
        // Males have no UI to flip them and register() never seeds them, so
        // the column stays 0 for every male in production. When the viewer
        // is female calling a male, ignoring this gate is correct — there's
        // no male signal here to respect. Mirrors B080 in FriendsTabFragment.
        val isCurrentUserFemale = BaseApplication.getInstance()?.getPrefs()
            ?.getUserData()?.gender?.equals(DConstants.FEMALE, ignoreCase = true) == true
        val isAudioEnabled = isCurrentUserFemale || peerAudioStatus == 1
        val isVideoEnabled = isCurrentUserFemale || peerVideoStatus == 1
        
        Log.d("CallButtons", "Final status - Audio: $isAudioEnabled ($peerAudioStatus), Video: $isVideoEnabled ($peerVideoStatus)")
        
        mainHandler.post {
            if (!isUiSafe()) return@post
            // Audio button state - enabled only if audio is enabled (status = 1)
            if (isAudioEnabled) {
                // ENABLED - Purple
                Log.d("CallButtons", "Setting audio button to ENABLED (purple)")
                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.chat_call_audio_bg))
                ivAudioCall.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent))
                cvAudioCall.isEnabled = true
                tvAudioRateTop?.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
            } else {
                // DISABLED - Gray
                Log.d("CallButtons", "Setting audio button to DISABLED (gray) - AudioEnabled: $isAudioEnabled")
                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.chat_call_grey_bg))
                ivAudioCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvAudioCall.isEnabled = false
                tvAudioRateTop?.setTextColor(ContextCompat.getColor(this, R.color.grey_medium))
            }
            
            // Video button state - enabled only if video is enabled (status = 1)
            if (isVideoEnabled) {
                // ENABLED - Green
                Log.d("CallButtons", "Setting video button to ENABLED (green)")
                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.chat_call_video_bg))
                ivVideoCall.setColorFilter(ContextCompat.getColor(this, R.color.purple))
                cvVideoCall.isEnabled = true
                tvVideoRateTop?.setTextColor(ContextCompat.getColor(this, R.color.purple))
            } else {
                // DISABLED - Gray
                Log.d("CallButtons", "Setting video button to DISABLED (gray) - VideoEnabled: $isVideoEnabled")
                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.chat_call_grey_bg))
                ivVideoCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvVideoCall.isEnabled = false
                tvVideoRateTop?.setTextColor(ContextCompat.getColor(this, R.color.grey_medium))
            }
        }
    }

    private fun setupCallButtonListeners() {
        // See updateCallButtonsState — when the viewer is female, the male's
        // audio_status / video_status fields don't mean what this check
        // assumes (males have no UI to flip them), so skip the gate.
        val isCurrentUserFemale = BaseApplication.getInstance()?.getPrefs()
            ?.getUserData()?.gender?.equals(DConstants.FEMALE, ignoreCase = true) == true

        cvAudioCall.setOnClickListener {
            when {
                isCallBlocked -> {
                    showAppToast("You are blocked by this user", Toast.LENGTH_SHORT)
                }
                peerCallBlocked -> {
                    showAppToast("You can't call this user right now. Please try again later.", Toast.LENGTH_SHORT)
                }
                !isCurrentUserFemale && peerAudioStatus != 1 -> {
                    CallUnavailableFeedback.show(
                        this,
                        findViewById(android.R.id.content),
                        forAudio = true
                    )
                }
                !isCurrentUserFemale && !hasEnoughCoinsForCall(perMinAudioRate) -> {
                    showTrialOfferSheet()
                }
                else -> {
                    initiateCall("audio")
                }
            }
        }

        cvVideoCall.setOnClickListener {
            when {
                isCallBlocked -> {
                    showAppToast("You are blocked by this user", Toast.LENGTH_SHORT)
                }
                peerCallBlocked -> {
                    showAppToast("You can't call this user right now. Please try again later.", Toast.LENGTH_SHORT)
                }
                !isCurrentUserFemale && peerVideoStatus != 1 -> {
                    CallUnavailableFeedback.show(
                        this,
                        findViewById(android.R.id.content),
                        forAudio = false
                    )
                }
                !isCurrentUserFemale && !hasEnoughCoinsForCall(perMinVideoRate) -> {
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

        val isCurrentUserFemale = BaseApplication.getInstance()?.getPrefs()
            ?.getUserData()?.gender?.equals(DConstants.FEMALE, ignoreCase = true) == true

        val intent = if (isCurrentUserFemale) {
            // Female creator -> male user. Use FemaleCallConnectingActivity with the same
            // extras Recent/Favourite already pass for a female-initiated call
            // (RecentFragment.startMaleCallConnectingActivity), so the flow is identical.
            Intent(this, com.gmwapp.hima.agora.female.FemaleCallConnectingActivity::class.java).apply {
                putExtra(DConstants.CALL_TYPE, callType)
                putExtra(DConstants.RECEIVER_ID, peerUserId)
                putExtra(DConstants.RECEIVER_NAME, userName)
                putExtra(DConstants.CALL_ID, 0)
                putExtra(DConstants.IMAGE, userImage)
                putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
                putExtra(DConstants.TEXT, getString(R.string.wait_user_hint, userName))
                putExtra("FROM_CHAT", true)
                putExtra("CHAT_PEER_USER_ID", peerUserId)
            }
        } else {
            // Male path — unchanged.
            Intent(this, com.gmwapp.hima.agora.male.MaleCallConnectingActivity::class.java).apply {
                putExtra(DConstants.CALL_TYPE, callType)
                putExtra(DConstants.RECEIVER_ID, peerUserId)
                putExtra(DConstants.IMAGE, userImage)
                putExtra(DConstants.RECEIVER_NAME, userName)
                putExtra("FROM_CHAT", true)
                putExtra("CHAT_PEER_USER_ID", peerUserId)
            }
        }
        if (isCurrentUserFemale) {
            com.gmwapp.hima.agora.FcmUtils.isUserAvailable = 1
        }
        startActivity(intent)
    }

    /**
     * Extracts the name part from username by removing trailing numbers
     * Examples: "Joy22" -> "Joy", "ZNKAK467" -> "ZNKAK"
     */
    private fun extractNameOnly(username: String): String {
        if (username.isEmpty()) return username
        return com.gmwapp.hima.utils.PeerNameUtils.sanitizePeerName(username)
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

    private fun isOwnAccountBlocked(): Boolean =
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.blocked == true ||
            lastChatGate?.reason == "account_blocked"

    /** Bug #10 — lock the composer + show the suspension strip when the signed-in
     *  user's own account is admin-suspended. The backend already rejects the send;
     *  this makes the state visible instead of a silent "couldn't send" toast. */
    private fun lockComposerForAccountBlocked() {
        accountBlockedChatStrip?.visibility = View.VISIBLE
        accountBlockedChatStrip?.setOnClickListener {
            startActivity(Intent(this, HelpAndSupportActivity::class.java))
        }
        messageInputContainer?.visibility = View.VISIBLE
        friendshipLockContainer?.visibility = View.GONE
        subscribeLockContainer?.visibility = View.GONE
        autopayFailedLockContainer?.visibility = View.GONE
        etMessage.isEnabled = false
        etMessage.hint = getString(R.string.chat_blocked_composer_hint)
        btnSend.isEnabled = false; btnSend.alpha = 0.4f
        btnMic.isEnabled = false; btnMic.alpha = 0.4f
        btnEmoji.isEnabled = false; btnEmoji.alpha = 0.4f
        if (::emojiGrid.isInitialized) hideEmojiPanel()
    }

    /** Revert the account-blocked composer lock (admin un-blocked + gate refreshed). */
    private fun clearAccountBlockedComposerLock() {
        accountBlockedChatStrip?.visibility = View.GONE
        if (!etMessage.isEnabled) {
            etMessage.isEnabled = true
            etMessage.hint = getString(R.string.chat_input_hint)
            btnSend.isEnabled = true; btnSend.alpha = 1f
            btnMic.isEnabled = true; btnMic.alpha = 1f
            btnEmoji.isEnabled = true; btnEmoji.alpha = 1f
        }
    }

    /**
     * Friends-Gated Chat — top-level composer gate. Chooses between the autopay
     * Subscribe/BuyCoins lock (autopay-language males) and the friendship lock
     * (females + autopay-OFF males), driven by [lastChatGate] from chat_gate_status.
     * Autopay-mode (and the pre-load state) defer to [applySubscriptionGate];
     * friends-mode owns the composer here.
     */
    private fun applyComposerGate() {
        // Bug #10 — account-level admin suspension locks the composer above every
        // other gate (mirrors chatSendDecision / the socket gate).
        if (isOwnAccountBlocked()) { lockComposerForAccountBlocked(); return }
        clearAccountBlockedComposerLock()
        // If THIS user has blocked the peer, lock the composer with an Unblock CTA —
        // they can't message someone they've blocked (the socket rejects it anyway),
        // so a visible input box was misleading. Takes precedence over every gate
        // state and applies to both genders (females are otherwise never gated).
        if (iHaveBlockedThisUser) {
            subscribeLockContainer?.visibility = View.GONE
            autopayFailedLockContainer?.visibility = View.GONE
            bannerAddFriend?.visibility = View.GONE
            messageInputContainer?.visibility = View.GONE
            friendshipLockContainer?.visibility = View.VISIBLE
            renderIBlockedLock()
            return
        }
        val gate = lastChatGate
        if (gate == null) {
            val gender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
            val isMale = gender?.equals(DConstants.MALE, ignoreCase = true) == true
            val isFemale = gender?.equals(DConstants.FEMALE, ignoreCase = true) == true
            when {
                isMale -> {
                    // Male — defer to subscription gate while friends-gate API loads.
                    friendshipLockContainer?.visibility = View.GONE
                    applySubscriptionGate()
                }
                isFemale -> {
                    // Female users are recipients, never gated by friendship or autopay.
                    // Show their composer immediately so they don't see a blank bar while
                    // the gate API is in-flight or unreachable.
                    messageInputContainer?.visibility = View.VISIBLE
                    friendshipLockContainer?.visibility = View.GONE
                }
                else -> {
                    // Gender unknown — fail-safe: hide composer to prevent gate bypass.
                    messageInputContainer?.visibility = View.GONE
                    friendshipLockContainer?.visibility = View.GONE
                }
            }
            return
        }
        // Blocked: the peer has blocked this user (chat_gate_status returns
        // mode/reason "blocked"). Takes precedence over autopay/friends — lock the
        // composer and show a clear "you're blocked" indication instead of a stray
        // "send friend request" CTA (they may still be friends on record).
        if (gate.mode == "blocked" || gate.reason == "blocked") {
            subscribeLockContainer?.visibility = View.GONE
            autopayFailedLockContainer?.visibility = View.GONE
            bannerAddFriend?.visibility = View.GONE
            messageInputContainer?.visibility = View.GONE
            friendshipLockContainer?.visibility = View.VISIBLE
            renderBlockedLock()
            return
        }
        if (gate.mode == "autopay") {
            friendshipLockContainer?.visibility = View.GONE
            applySubscriptionGate()
            return
        }
        // Friends mode: autopay locks never apply; friendship governs the composer.
        subscribeLockContainer?.visibility = View.GONE
        autopayFailedLockContainer?.visibility = View.GONE
        bannerAddFriend?.visibility = View.GONE
        if (gate.unlocked) {
            friendshipLockContainer?.visibility = View.GONE
            messageInputContainer?.visibility = View.VISIBLE
        } else {
            messageInputContainer?.visibility = View.GONE
            friendshipLockContainer?.visibility = View.VISIBLE
            renderFriendshipLock(gate)
        }
    }

    /** Render the friendship lock per the friend action (received / pending / send). */
    private fun renderFriendshipLock(gate: com.gmwapp.hima.retrofit.responses.ChatGateStatusResponse) {
        val name = peerName.ifBlank { "this user" }
        setFriendLockButtonsEnabled(true)
        // Restore button visibility in case a prior blocked-lock render hid them.
        btnFriendLockPrimary?.visibility = View.VISIBLE
        when (gate.action) {
            "accept_request" -> {
                tvFriendLockTitle?.text = "$name wants to be friends"
                tvFriendLockSubtitle?.text = "Accept to start chatting."
                tvFriendLockPrimaryText?.text = "Accept"
                tvFriendLockSecondaryText?.text = "Decline"
                btnFriendLockSecondary?.visibility = View.VISIBLE
                btnFriendLockPrimary?.setOnClickListener { doFriendAction(1) }
                btnFriendLockSecondary?.setOnClickListener { doFriendAction(2) }
            }
            "request_sent" -> {
                tvFriendLockTitle?.text = "Friend request sent"
                tvFriendLockSubtitle?.text = "You can chat once $name accepts."
                tvFriendLockPrimaryText?.text = "Request sent"
                btnFriendLockPrimary?.isClickable = false
                btnFriendLockPrimary?.alpha = 0.5f
                btnFriendLockPrimary?.setOnClickListener(null)
                tvFriendLockSecondaryText?.text = "View profile"
                btnFriendLockSecondary?.visibility = View.VISIBLE
                btnFriendLockSecondary?.setOnClickListener { openUserProfile() }
            }
            else -> { // send_friend_request (default)
                tvFriendLockTitle?.text = "Want to chat with $name?"
                tvFriendLockSubtitle?.text = "Send a friend request to start chatting."
                tvFriendLockPrimaryText?.text = "Send friend request"
                btnFriendLockPrimary?.setOnClickListener { doFriendAction(0) }
                tvFriendLockSecondaryText?.text = "View profile"
                btnFriendLockSecondary?.visibility = View.VISIBLE
                btnFriendLockSecondary?.setOnClickListener { openUserProfile() }
            }
        }
    }

    /**
     * "I blocked them" lock: this user blocked the peer. Reuses the friendship-lock
     * container, shows an unblock prompt, and turns the primary button into Unblock.
     */
    private fun renderIBlockedLock() {
        val name = peerName.ifBlank { "this user" }
        tvFriendLockTitle?.text = getString(R.string.chat_i_blocked_lock_title, name)
        tvFriendLockSubtitle?.text = getString(R.string.chat_i_blocked_lock_subtitle)
        btnFriendLockSecondary?.visibility = View.GONE
        btnFriendLockPrimary?.visibility = View.VISIBLE
        tvFriendLockPrimaryText?.text = getString(R.string.chat_unblock_action)
        btnFriendLockPrimary?.isClickable = true
        btnFriendLockPrimary?.alpha = 1f
        btnFriendLockPrimary?.setOnClickListener { showUnblockConfirmationDialog() }
    }

    /**
     * Blocked lock: the peer has blocked this user. Reuses the friendship-lock
     * container but shows a block indication and hides the friend-request buttons —
     * there is no action the blocked user can take here.
     */
    private fun renderBlockedLock() {
        val name = peerName.ifBlank { "this user" }
        // Clear any stale listeners from a prior lock state before hiding the buttons,
        // so a reused container can't fire an old action if it's shown again.
        btnFriendLockPrimary?.setOnClickListener(null)
        btnFriendLockSecondary?.setOnClickListener(null)
        tvFriendLockTitle?.text = getString(R.string.chat_blocked_lock_title)
        tvFriendLockSubtitle?.text = getString(R.string.chat_blocked_lock_subtitle, name)
        btnFriendLockPrimary?.visibility = View.GONE
        btnFriendLockSecondary?.visibility = View.GONE
    }

    private fun setFriendLockButtonsEnabled(enabled: Boolean) {
        btnFriendLockPrimary?.isClickable = enabled
        btnFriendLockPrimary?.alpha = if (enabled) 1f else 0.5f
        btnFriendLockSecondary?.isClickable = enabled
    }

    /**
     * Friend action from the lock. status: 0 = I send a request (me->peer),
     * 1 = accept the peer's request, 2 = decline it. Re-reads the gate afterward so the
     * composer/lock updates immediately.
     */
    private fun doFriendAction(status: Int) {
        if (isFriendRequestInFlight) return
        val me = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        val peer = peerUserId.takeIf { it > 0 } ?: return
        val senderId = if (status == 0) me else peer
        val receiverId = if (status == 0) peer else me
        isFriendRequestInFlight = true
        setFriendLockButtonsEnabled(false)
        apiManager.sendFriendRequest(senderId, receiverId, status, object : NetworkCallback<FriendRequestResponse> {
            override fun onResponse(call: Call<FriendRequestResponse>, response: Response<FriendRequestResponse>) {
                isFriendRequestInFlight = false
                if (!isUiSafe()) return
                val ok = response.isSuccessful && response.body()?.success == true
                val msg = when {
                    !ok -> getString(R.string.chat_add_friend_failure)
                    status == 0 -> "Friend request sent"
                    status == 1 -> getString(R.string.chat_add_friend_success, peerName)
                    else -> "Request declined"
                }
                showAppToast(msg, Toast.LENGTH_SHORT)
                refreshChatGate()
            }

            override fun onFailure(call: Call<FriendRequestResponse>, t: Throwable) {
                isFriendRequestInFlight = false
                if (!isUiSafe()) return
                setFriendLockButtonsEnabled(true)
                showAppToast(getString(R.string.chat_add_friend_failure), Toast.LENGTH_SHORT)
            }

            override fun onNoNetwork() {
                isFriendRequestInFlight = false
                if (!isUiSafe()) return
                setFriendLockButtonsEnabled(true)
                showAppToast(getString(R.string.chat_add_friend_failure), Toast.LENGTH_SHORT)
            }
        })
    }

    /** Friends-Gated Chat — read the per-conversation gate, then (re)apply the composer state. */
    private fun refreshChatGate() {
        val me = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        val peer = peerUserId.takeIf { it > 0 } ?: return
        apiManager.chatGateStatus(me, peer, object : NetworkCallback<com.gmwapp.hima.retrofit.responses.ChatGateStatusResponse> {
            override fun onResponse(
                call: Call<com.gmwapp.hima.retrofit.responses.ChatGateStatusResponse>,
                response: Response<com.gmwapp.hima.retrofit.responses.ChatGateStatusResponse>
            ) {
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    lastChatGate = body
                    isFriendWithPeer = body.friendStatus == "friends"
                }
                if (!isUiSafe()) return
                applyComposerGate()
            }

            override fun onFailure(call: Call<com.gmwapp.hima.retrofit.responses.ChatGateStatusResponse>, t: Throwable) {
                Log.w("ChatFriends", "refreshChatGate failed: ${t.message}")
                if (!isUiSafe()) return
                applyComposerGate()
            }

            override fun onNoNetwork() {
                if (!isUiSafe()) return
                applyComposerGate()
            }
        })
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
            applyComposerGate()
        }
        // Re-gate when admin flips the language flag while chat is open.
        com.gmwapp.hima.utils.LanguageFeatureCache.updates.observe(this) {
            applyComposerGate()
        }
        // Real-time rejection: when the peer rejects this user's friend request while
        // the chat screen is open, silently re-fetch the gate so the male can re-request.
        // Clear after handling so re-entry doesn't replay the stale value.
        com.gmwapp.hima.agora.FcmUtils.friendRequestRejectedLiveData.observe(this) { peerId ->
            if (peerId != null && peerId == peerUserId) {
                com.gmwapp.hima.agora.FcmUtils.friendRequestRejectedLiveData.postValue(null)
                showAppToast("Your request was declined. You can send a new one.", android.widget.Toast.LENGTH_SHORT)
                refreshChatGate()
            }
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
        // Never-active users: the ₹1 trial bottom-sheet is retired — send them to
        // Wallet, where the welcome-gift banner is the autopay (₹1) upsell.
        // Lapsed/cancelled users keep the ₹299 re-subscribe sheet so a locked
        // composer still has a way to unlock.
        if (!com.gmwapp.hima.utils.SubscriptionStateCache.everActive(this)) {
            startActivity(android.content.Intent(this, WalletActivity::class.java))
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
