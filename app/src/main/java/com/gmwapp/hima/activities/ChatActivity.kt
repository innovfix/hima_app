package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.ChatAdapter
import com.gmwapp.hima.models.ChatMessage
import com.gmwapp.hima.viewmodels.MessageNotificationViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*
import android.widget.PopupMenu
import com.google.firebase.Timestamp
import android.view.View
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var ivBack: ImageView
    private lateinit var ivUser: CircleImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserStatus: TextView
    private lateinit var vOnlineIndicator: View
    private lateinit var ivMore: ImageView

    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    // Firestore instance - connected to "himadatabase"
    private val db by lazy { FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "himadatabase") }

    // Message Notification ViewModel
    private val messageNotificationViewModel: MessageNotificationViewModel by viewModels()

    // User IDs and thread ID
    private var myUserId: String = ""
    private var peerUserId: String = ""
    private var threadId: String = ""

    // Track if chat is visible to user
    private var isChatVisible = false
    private val pendingMessagesToMarkRead = mutableSetOf<String>()

    // Block/Unblock variables
    private var isPeerBlocked: Boolean = false
    private var blockTimestamp: Timestamp? = null

    // Pagination variables
    private var oldestMessageTimestamp: Timestamp? = null  // Track oldest loaded message for pagination
    private var isLoadingMoreMessages = false  // Prevent multiple simultaneous loads
    private var hasMoreMessages = true  // Track if there are more messages to load
    private var messagesListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private val MESSAGES_PER_PAGE = 10L  // Load 10 messages at a time (Long for Firestore limit)

    // Message queue for sending multiple messages
    private val messageQueue = ConcurrentLinkedQueue<String>()
    private var isSendingMessage = false  // Track if a message is currently being sent
    
    // Cache for peer blocked me status to reduce lag
    private var peerHasBlockedMeCache: Boolean? = null
    private var peerBlockedMeCacheTime: Long = 0
    private val PEER_BLOCKED_CACHE_DURATION = 30000L  // Cache for 30 seconds
    
    
    // Background thread executor for Firestore operations (non-blocking)
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    
    // Cached views for back button optimization
    private var cachedContentView: View? = null
    
    // ✅ PERFORMANCE: Cached date formatters (created once, reused many times)
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    // ✅ PERFORMANCE: Debounce online status updates
    private var lastStatusUpdateTime = 0L
    private val STATUS_UPDATE_THROTTLE = 2000L // Update max once per 2 seconds
    
    // ✅ PERFORMANCE: Throttle layout change listener
    private var lastLayoutChangeTime = 0L
    private val LAYOUT_CHANGE_THROTTLE = 100L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("checkPagiantion", "🏁 ChatActivity onCreate() called")
        setContentView(R.layout.activity_chat)

        // ✅ Restrict screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        initializeViews()
        setupRecyclerView()
        setupUserIds()
        listenToUserOnlineStatus()  // Set up online status listener after user IDs are initialized
        checkIfUserIsBlocked()  // Check block status before setting up listener
        setupClickListeners()
        observeNotificationResponse()
        renderQuickReplyChips()
        Log.d("checkPagiantion", "🏁 ChatActivity onCreate() completed")
    }

    /**
     * Render default Tanglish quick-reply chips above the input bar. Always visible
     * (no gender/API gate) so creators always see something to tap.
     */
    private fun renderQuickReplyChips() {
        val scrollView = findViewById<android.widget.HorizontalScrollView>(R.id.quick_reply_scroll) ?: return
        val container = findViewById<LinearLayout>(R.id.quick_reply_container) ?: return

        val defaults = listOf(
            "Hi da! 👋 Eppadi iruka?",
            "Sollu da, enna pannra? 😊",
            "Pesuvoma? Naan free 💬",
            "Let's chat da! ✨"
        )

        container.removeAllViews()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        defaults.forEach { replyText ->
            val chip = TextView(this).apply {
                text = replyText
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@ChatActivity, R.color.colorAccent))
                typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.poppins_semibold)
                setBackgroundResource(R.drawable.bg_quick_reply_chip)
                setPadding(dp(16), dp(9), dp(16), dp(9))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    etMessage.setText(replyText)
                    etMessage.setSelection(etMessage.text?.length ?: 0)
                    scrollView.visibility = View.GONE
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            container.addView(chip, lp)
        }
        scrollView.visibility = View.VISIBLE
    }

    private fun initializeViews() {
        rvMessages = findViewById(R.id.rv_messages)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        ivBack = findViewById(R.id.iv_back)
        ivUser = findViewById(R.id.iv_user)
        tvUserName = findViewById(R.id.tv_user_name)
        tvUserStatus = findViewById(R.id.tv_user_status)
        vOnlineIndicator = findViewById(R.id.v_online_indicator)
        ivMore = findViewById(R.id.iv_more)

        // Set user data from intent
        val userName = intent.getStringExtra("USER_NAME") ?: "User"
        val userImage = intent.getStringExtra("USER_IMAGE")

        tvUserName.text = userName
        // Status will be loaded from Firebase

        // Load user image
        if (!userImage.isNullOrEmpty()) {
            Glide.with(this)
                .load(userImage)
                .apply(RequestOptions.circleCropTransform())
                .into(ivUser)
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.white)

        // ✅ SIMPLE: Set status bar icons to LIGHT (white) - works on all devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        
        // ✅ OPTIMIZATION: Prevent EditText from requesting focus on its own (reduces lag)
        etMessage.clearFocus()
        
        
        // ✅ Cache contentView for fast back button checks
        cachedContentView = window.decorView.rootView.findViewById(android.R.id.content)
    }

    private fun setupRecyclerView() {
        Log.d("checkPagiantion", "📋 Setting up RecyclerView...")
        chatAdapter = ChatAdapter(messages)
        rvMessages.apply {
            val layoutManager = LinearLayoutManager(this@ChatActivity)
            // Anchor messages to the bottom so empty space appears above messages
            layoutManager.stackFromEnd = true
            layoutManager.reverseLayout = false
            this.layoutManager = layoutManager
            adapter = chatAdapter
            
            // ✅ CRITICAL: Prevent RecyclerView from stealing focus from EditText
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = RecyclerView.FOCUS_BLOCK_DESCENDANTS

            Log.d("checkPagiantion", "📋 RecyclerView setup complete - pagination disabled (last 10 messages only)")

            // ✅ PAGINATION DISABLED - Only showing last 10 messages
            // Scroll listener removed to prevent loading older messages

            // ✅ PERFORMANCE: Throttled layout change listener to prevent excessive calls
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                val now = System.currentTimeMillis()
                if (now - lastLayoutChangeTime < LAYOUT_CHANGE_THROTTLE) return@addOnLayoutChangeListener
                lastLayoutChangeTime = now
                
                if (oldBottom != 0 && bottom < oldBottom) {
                    post {
                        if (chatAdapter.itemCount > 0) scrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            }
        }
        Log.d("checkPagiantion", "📋 RecyclerView setup completed")
    }

    private fun setupUserIds() {
        // Get logged-in user ID
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        myUserId = userData?.id?.toString() ?: ""

        // Get peer user ID from intent
        peerUserId = intent.getIntExtra("USER_ID", -1).toString()

        // Generate unique thread ID (sorted to ensure same ID regardless of who initiates)
        threadId = listOf(myUserId, peerUserId).sorted().joinToString("_")

        Log.d("checkPagiantion", "👤 User IDs setup - myUserId: $myUserId, peerUserId: $peerUserId, threadId: $threadId")
        Log.d("ChatActivity", "MyUserId: $myUserId, PeerUserId: $peerUserId, ThreadId: $threadId")

        if (myUserId.isEmpty() || peerUserId == "-1") {
            Log.e("checkPagiantion", "❌ Invalid user data - finishing activity")
            showAppToast("Error: Invalid user data", Toast.LENGTH_SHORT)
            finish()
        }
    }

    /**
     * ✅ PERFORMANCE: Process messages in background thread to avoid blocking UI
     */
    private fun processMessagesInBackground(
        snapshot: com.google.firebase.firestore.QuerySnapshot,
        isInitialLoad: Boolean
    ) {
        backgroundExecutor.execute {
            val startTime = System.currentTimeMillis()
            Log.d("PERF", "🔥 Processing ${snapshot.documents.size} messages in background thread")
        
        // ✅ Collect messages first
        val tempMessages = mutableListOf<ChatMessage>()

        // Track oldest timestamp for pagination
        var oldestTimestamp: Timestamp? = null

        for (doc in snapshot.documents) {
            val fromId = doc.getString("from") ?: ""
            val text = doc.getString("text") ?: ""
            val timestamp = doc.getTimestamp("timestamp")

            // Update oldest timestamp (messages are DESCENDING, so last doc is oldest)
            if (timestamp != null && (oldestTimestamp == null || timestamp < oldestTimestamp)) {
                oldestTimestamp = timestamp
            }

            Log.d("ChatActivity", "Message: from=$fromId, text=$text, timestamp=$timestamp")

            // Filter messages if peer is blocked
            if (isPeerBlocked && fromId == peerUserId && blockTimestamp != null && timestamp != null) {
                if (timestamp.seconds >= blockTimestamp!!.seconds) {
                    Log.d("ChatActivity", "🚫 Filtering blocked message: sent at ${timestamp.seconds}, blocked at ${blockTimestamp!!.seconds}")
                    continue  // Skip messages sent after blocking
                }
            }

            // Handle timestamp - only use IST for null timestamps (when server timestamp not resolved yet)
            val messageDate = if (timestamp != null) {
                timestamp.toDate()
            } else {
                // If timestamp is null (server timestamp not resolved), use current time in IST
                val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
                Calendar.getInstance(istTimeZone).time
            }
            val timeString = formatMessageTime(messageDate)  // Only time, no date

            val message = ChatMessage(
                id = doc.id,
                message = text,
                timestamp = timeString,
                isSentByMe = fromId == myUserId,
                date = messageDate  // Store Date object for date grouping
            )

            tempMessages.add(message)
        }

        // ✅ FIX: If all messages were filtered out (blocked) and not initial load, keep existing messages
        if (tempMessages.isEmpty() && !isInitialLoad) {
            Log.d("ChatActivity", "⚠️ All messages filtered out (blocked), keeping existing messages")
            isLoadingMoreMessages = false
            return@execute
        }

        // Update oldest message timestamp for pagination (only on initial load)
        if (oldestTimestamp != null && isInitialLoad) {
            oldestMessageTimestamp = oldestTimestamp
            // Check if we got fewer messages than requested - means no more messages
            if (snapshot.documents.size.toLong() < MESSAGES_PER_PAGE) {
                hasMoreMessages = false
                Log.d("ChatActivity", "No more messages to load (got ${snapshot.documents.size} < $MESSAGES_PER_PAGE)")
            }
        }

        // ✅ Reverse to show oldest first (since we queried DESCENDING)
        val reversedMessages = tempMessages.reversed()

        // ✅ Insert date headers between message groups (like WhatsApp)
        val messagesWithHeaders = insertDateHeaders(reversedMessages)

        val processingTime = System.currentTimeMillis() - startTime
        Log.d("PERF", "✅ Background processing completed in ${processingTime}ms")

            // Update UI on main thread
            mainHandler.post {
                // ✅ CRITICAL: Don't update UI if activity is finishing (prevents lag on back press)
                if (isFinishing || isDestroyed) {
                    Log.d("PERF", "⚠️ Skipping UI update - activity is finishing/destroyed")
                    return@post
                }
                updateUIWithProcessedMessages(messagesWithHeaders, tempMessages, isInitialLoad, snapshot)
            }
        }
    }

    /**
     * ✅ PERFORMANCE: Update UI on main thread with processed messages
     */
    private fun updateUIWithProcessedMessages(
        messagesWithHeaders: List<ChatMessage>,
        tempMessages: List<ChatMessage>,
        isInitialLoad: Boolean,
        snapshot: com.google.firebase.firestore.QuerySnapshot
    ) {
        val startTime = System.currentTimeMillis()
        Log.d("PERF", "🔥 Updating UI on main thread")

        // Handle initial load vs real-time updates
        if (isInitialLoad) {
            // Initial load - replace all messages
            val oldSize = messages.size
            messages.clear()
            messages.addAll(messagesWithHeaders)
            
            Log.d("checkPagiantion", "📥 INITIAL LOAD COMPLETE")
            Log.d("checkPagiantion", "📥 Messages loaded: ${messagesWithHeaders.size} (with date headers)")
            Log.d("checkPagiantion", "📥 Actual messages (without headers): ${tempMessages.size}")
            Log.d("checkPagiantion", "📥 Total messages now in list: ${messages.size}")
            Log.d("checkPagiantion", "📥 Oldest message timestamp: $oldestMessageTimestamp")
            Log.d("checkPagiantion", "📥 Has more messages: $hasMoreMessages")
            Log.d("ChatActivity", "✅ Loaded ${messages.size} messages (initial load), notifying adapter")
            
            // ✅ PERFORMANCE: Use notifyItemRangeInserted instead of notifyDataSetChanged
            if (messagesWithHeaders.isNotEmpty()) {
                chatAdapter.notifyItemRangeInserted(0, messagesWithHeaders.size)
            } else {
                chatAdapter.notifyDataSetChanged() // Only when empty
            }

            // Scroll to bottom
            if (messages.isNotEmpty()) {
                rvMessages.scrollToPosition(messages.size - 1)
            }
        } else {
            // Real-time update - add only new messages (newer than what we have)
            // ✅ FIX: Check if snapshot only contains messages we already have (prevent showing old messages)
            val existingIds = messages.map { it.id }.toSet()
            val snapshotIds = snapshot.documents.map { it.id }.toSet()
            
            // Check if snapshot only has existing messages (no new messages)
            // This prevents old messages from reloading when we send a new message
            val hasNewMessages = snapshotIds.any { !existingIds.contains(it) }
            if (!hasNewMessages && messages.isNotEmpty()) {
                // Snapshot only contains messages we already have - skip to prevent showing old messages
                // Only process if there are temp messages to replace
                val hasTempMessages = messages.any { it.id.startsWith("temp_") }
                if (!hasTempMessages) {
                    Log.d("ChatActivity", "⚠️ Snapshot contains only existing messages - skipping update")
                    isLoadingMoreMessages = false
                    return
                }
            }
            
            // Also check for temp messages that need to be replaced

            // ✅ FIX: Also track existing date headers by their text to avoid duplicates
            val existingDateHeaders = messages
                .filter { it.isDateHeader }
                .map { it.dateHeaderText }
                .toSet()

            // Filter for truly new messages (not already in list)
            // For date headers, check by dateHeaderText instead of ID to avoid duplicates
            val newMessages = messagesWithHeaders.filter { msg ->
                if (msg.isDateHeader) {
                    // For date headers, check if this date header text already exists
                    !existingDateHeaders.contains(msg.dateHeaderText)
                } else {
                    // For regular messages, check by ID
                    !existingIds.contains(msg.id)
                }
            }

            // Track which message IDs were used to replace temp messages
            val replacedMessageIds = mutableSetOf<String>()

            // Replace any temp messages with real ones (matching text and sender within 30 seconds)
            for (newMsg in messagesWithHeaders.filter { !it.isDateHeader }) {
                val tempMsgIndex = messages.indexOfFirst { msg ->
                    !msg.isDateHeader &&
                            msg.id.startsWith("temp_") &&
                            msg.message == newMsg.message &&
                            msg.isSentByMe == newMsg.isSentByMe &&
                            msg.date != null && newMsg.date != null &&
                            Math.abs(msg.date!!.time - newMsg.date!!.time) < 30000 // Within 30 seconds
                }

                if (tempMsgIndex != -1) {
                    // Replace temp message with real one
                    messages[tempMsgIndex] = newMsg
                    chatAdapter.notifyItemChanged(tempMsgIndex)
                    replacedMessageIds.add(newMsg.id)
                    Log.d("ChatActivity", "✅ Replaced temp message with real message: ${newMsg.id}")
                }
            }

            // Filter out messages that were used to replace temp messages
            val actuallyNewMessages = newMessages.filter { !replacedMessageIds.contains(it.id) }

            if (actuallyNewMessages.isNotEmpty()) {
                // ✅ FIX: Remove duplicate date header if last message has same date header
                val filteredNewMessages = mutableListOf<ChatMessage>()

                // Check if last message in existing list has a date header
                val lastExistingMessage = messages.lastOrNull()
                val lastExistingDateHeader = if (lastExistingMessage?.isDateHeader == true) {
                    lastExistingMessage.dateHeaderText
                } else {
                    lastExistingMessage?.date?.let { getDateHeaderText(it) }
                }

                // Check first item in new messages (that weren't used to replace temp messages)
                val firstNewItem = actuallyNewMessages.firstOrNull()
                val firstNewDateHeader = if (firstNewItem?.isDateHeader == true) {
                    firstNewItem.dateHeaderText
                } else {
                    firstNewItem?.date?.let { getDateHeaderText(it) }
                }

                // If date headers match, skip duplicate date header in new messages
                // This applies whether the first new item is a header or a message with the same date
                var skipFirstHeader = (lastExistingDateHeader != null &&
                        lastExistingDateHeader == firstNewDateHeader)

                for (message in actuallyNewMessages) {
                    // Skip the first date header if dates match (to avoid duplicate "Today" headers)
                    if (skipFirstHeader && message.isDateHeader && message.dateHeaderText == firstNewDateHeader) {
                        skipFirstHeader = false  // Only skip the first matching header
                        continue
                    }
                    filteredNewMessages.add(message)
                }

                // Append filtered new messages to the end
                val insertPosition = messages.size
                messages.addAll(filteredNewMessages)
                
                // ✅ KEEP ONLY LAST 10 MESSAGES: Trim older messages if we exceed 10 actual messages
                val actualMessageCount = messages.count { !it.isDateHeader }
                if (actualMessageCount > MESSAGES_PER_PAGE.toInt()) {
                    // Count how many items to keep (last 10 messages + their date headers)
                    var messagesToKeep = 0
                    var itemsToKeep = 0
                    // Count from the end backwards
                    for (i in messages.size - 1 downTo 0) {
                        if (!messages[i].isDateHeader) {
                            messagesToKeep++
                            if (messagesToKeep >= MESSAGES_PER_PAGE.toInt()) {
                                itemsToKeep++
                                break
                            }
                        }
                        itemsToKeep++
                    }
                    
                    // Remove older messages from the beginning
                    val itemsToRemove = messages.size - itemsToKeep
                    if (itemsToRemove > 0) {
                        // Remove items from beginning
                        for (i in 0 until itemsToRemove) {
                            messages.removeAt(0)
                        }
                        
                        Log.d("ChatActivity", "✂️ Trimmed $itemsToRemove older items to keep only last ${MESSAGES_PER_PAGE} messages")
                        // ✅ PERFORMANCE: Notify removal of old items
                        chatAdapter.notifyItemRangeRemoved(0, itemsToRemove)
                    }
                }
                
                // ✅ PERFORMANCE: Use notifyItemRangeInserted instead of notifyDataSetChanged
                if (filteredNewMessages.isNotEmpty()) {
                    val finalInsertPosition = messages.size - filteredNewMessages.size
                    chatAdapter.notifyItemRangeInserted(finalInsertPosition, filteredNewMessages.size)
                }

                // Auto-scroll to bottom if user is at bottom
                val layoutManager = rvMessages.layoutManager as? LinearLayoutManager
                val lastVisiblePosition = layoutManager?.findLastVisibleItemPosition() ?: 0
                val totalItemCount = messages.size

                // If user is near bottom (within last 3 items), auto-scroll
                if ((totalItemCount.toLong() - lastVisiblePosition.toLong()) <= 3L) {
                    rvMessages.scrollToPosition(messages.size - 1)
                }

                Log.d("checkPagiantion", "📨 REAL-TIME UPDATE: Added ${filteredNewMessages.size} new messages. Total: ${messages.size} (keeping last ${MESSAGES_PER_PAGE} messages)")
                Log.d("ChatActivity", "✅ Added ${filteredNewMessages.size} new messages. Total: ${messages.size} (keeping last ${MESSAGES_PER_PAGE} messages)")
            }
        }

        // Collect unread messages from snapshot
        collectUnreadMessages(snapshot)

        // Mark messages as read only if chat is visible
        if (isChatVisible) {
            markPendingMessagesAsRead()
        }

        isLoadingMoreMessages = false
        
        val uiUpdateTime = System.currentTimeMillis() - startTime
        Log.d("PERF", "✅ UI update completed in ${uiUpdateTime}ms")
    }

    private fun setupFirestoreListener() {
        Log.d("ChatActivity", "Setting up Firestore listener for threadId: $threadId")
        Log.d("checkPagiantion", "🚀 SETTING UP FIRESTORE LISTENER")
        Log.d("checkPagiantion", "🚀 Thread ID: $threadId")
        Log.d("checkPagiantion", "🚀 Messages per page: $MESSAGES_PER_PAGE")

        // Remove previous listener if exists
        messagesListenerRegistration?.remove()

        // Reset pagination variables - DISABLED (only last 10 messages)
        oldestMessageTimestamp = null
        hasMoreMessages = false  // ✅ Disabled pagination
        isLoadingMoreMessages = false

        Log.d("checkPagiantion", "🚀 Pagination DISABLED - showing only last 10 messages")

        // ✅ OPTIMIZATION: Only load last 10 messages to reduce reads
        // This changes 1000 reads per chat open → 10 reads (100x reduction!)

        messagesListenerRegistration = db.collection("chats")
            .document(threadId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)  // Changed to DESCENDING
            .limit(MESSAGES_PER_PAGE)  // ✅ Only get last 10 messages
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("checkPagiantion", "❌ FIRESTORE LISTENER ERROR: ${error.message}")
                    Log.e("checkPagiantion", "❌ Error details: ${error.stackTraceToString()}")
                    Log.e("ChatActivity", "❌ Listen failed: ${error.message}", error)

                    // Show error to user
                    val errorMsg = when {
                        error.message?.contains("index") == true -> {
                            "Creating index... Please wait 1-2 minutes and restart app"
                        }
                        else -> "Error loading messages: ${error.message}"
                    }
                    showAppToast(errorMsg, Toast.LENGTH_LONG)
                    isLoadingMoreMessages = false
                    return@addSnapshotListener
                }

                Log.d("ChatActivity", "Snapshot received - isEmpty: ${snapshot?.isEmpty}, docs: ${snapshot?.documents?.size}")
                Log.d("checkPagiantion", "📨 SNAPSHOT RECEIVED")
                Log.d("checkPagiantion", "📨 Snapshot isEmpty: ${snapshot?.isEmpty}, document count: ${snapshot?.documents?.size}")

                if (snapshot != null) {
                    // This listener is only for initial load and real-time updates
                    // Check if this is initial load (messages list is empty)
                    val isInitialLoad = messages.isEmpty()

                    Log.d("checkPagiantion", "📨 Is initial load: $isInitialLoad")

                    if (snapshot.isEmpty) {
                        Log.d("ChatActivity", "No messages in thread yet")
                        hasMoreMessages = false
                        // ✅ FIX: Only notify adapter if this is initial load, otherwise keep existing messages
                        if (isInitialLoad) {
                            messages.clear()
                            // ✅ PERFORMANCE: Use notifyDataSetChanged only when list is empty
                            chatAdapter.notifyDataSetChanged()
                        }
                        isLoadingMoreMessages = false
                        return@addSnapshotListener
                    }

                    // ✅ PERFORMANCE: Move heavy processing to background thread
                    processMessagesInBackground(snapshot, isInitialLoad)
                } else {
                    Log.e("ChatActivity", "Snapshot is null")
                    isLoadingMoreMessages = false
                }
            }
    }

    private fun collectUnreadMessages(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        // Clear pending list and recollect from current snapshot
        pendingMessagesToMarkRead.clear()

        // Collect messages from peer that are not yet read
        snapshot.documents.forEach { doc ->
            val fromId = doc.getString("from") ?: ""
            val isRead = doc.getBoolean("isRead") ?: false

            // If message is from the other user and not yet read, add to pending list
            if (fromId == peerUserId && !isRead) {
                pendingMessagesToMarkRead.add(doc.id)
            }
        }
        Log.d("ChatActivity", "Pending unread messages: ${pendingMessagesToMarkRead.size}")
    }

    /**
     * Load more older messages when user scrolls to top
     */
    private fun loadMoreMessages() {
        if (isLoadingMoreMessages || !hasMoreMessages || oldestMessageTimestamp == null) {
            Log.d("checkPagiantion", "⏭️ SKIPPING loadMoreMessages")
            Log.d("checkPagiantion", "⏭️ Reason - isLoading: $isLoadingMoreMessages, hasMore: $hasMoreMessages, oldestTimestamp: $oldestMessageTimestamp")
            Log.d("ChatActivity", "Skipping loadMoreMessages - isLoading: $isLoadingMoreMessages, hasMore: $hasMoreMessages, oldestTimestamp: $oldestMessageTimestamp")
            return
        }

        isLoadingMoreMessages = true
        Log.d("checkPagiantion", "⬆️ LOADING MORE MESSAGES (SCROLL UP)")
        Log.d("checkPagiantion", "⬆️ Current messages count before load: ${messages.size}")
        Log.d("checkPagiantion", "⬆️ Loading messages before timestamp: $oldestMessageTimestamp")
        Log.d("checkPagiantion", "⬆️ Messages per page: $MESSAGES_PER_PAGE")
        Log.d("ChatActivity", "Loading more messages before timestamp: $oldestMessageTimestamp")

        db.collection("chats")
            .document(threadId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .whereLessThan("timestamp", oldestMessageTimestamp!!)
            .limit(MESSAGES_PER_PAGE)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    hasMoreMessages = false
                    isLoadingMoreMessages = false
                    Log.d("checkPagiantion", "❌ NO MORE MESSAGES TO LOAD (empty snapshot)")
                    Log.d("ChatActivity", "No more messages to load")
                    return@addOnSuccessListener
                }

                Log.d("checkPagiantion", "⬆️ Received ${snapshot.documents.size} messages from Firestore")

                val tempMessages = mutableListOf<ChatMessage>()
                var oldestTimestamp: Timestamp? = null

                for (doc in snapshot.documents) {
                    val fromId = doc.getString("from") ?: ""
                    val text = doc.getString("text") ?: ""
                    val timestamp = doc.getTimestamp("timestamp")

                    if (timestamp != null && (oldestTimestamp == null || timestamp < oldestTimestamp)) {
                        oldestTimestamp = timestamp
                    }

                    // Filter messages if peer is blocked
                    if (isPeerBlocked && fromId == peerUserId && blockTimestamp != null && timestamp != null) {
                        if (timestamp.seconds >= blockTimestamp!!.seconds) {
                            continue
                        }
                    }

                    // Handle timestamp - only use IST for null timestamps (when server timestamp not resolved yet)
                    val messageDate = if (timestamp != null) {
                        timestamp.toDate()
                    } else {
                        // If timestamp is null (server timestamp not resolved), use current time in IST
                        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
                        Calendar.getInstance(istTimeZone).time
                    }
                    val timeString = formatMessageTime(messageDate)

                    val message = ChatMessage(
                        id = doc.id,
                        message = text,
                        timestamp = timeString,
                        isSentByMe = fromId == myUserId,
                        date = messageDate
                    )

                    tempMessages.add(message)
                }

                Log.d("checkPagiantion", "⬆️ After filtering blocked messages: ${tempMessages.size} messages")

                if (oldestTimestamp != null) {
                    oldestMessageTimestamp = oldestTimestamp
                    if (snapshot.documents.size.toLong() < MESSAGES_PER_PAGE) {
                        hasMoreMessages = false
                        Log.d("checkPagiantion", "⬆️ No more messages available (got ${snapshot.documents.size} < $MESSAGES_PER_PAGE)")
                        Log.d("ChatActivity", "No more messages to load (got ${snapshot.documents.size} < $MESSAGES_PER_PAGE)")
                    }
                }

                val reversedMessages = tempMessages.reversed()
                val messagesWithHeaders = insertDateHeaders(reversedMessages)

                // Filter out duplicates
                val existingIds = messages.map { it.id }.toSet()
                val newMessages = messagesWithHeaders.filter { !existingIds.contains(it.id) }

                Log.d("checkPagiantion", "⬆️ After date headers: ${messagesWithHeaders.size} items")
                Log.d("checkPagiantion", "⬆️ After duplicate filter: ${newMessages.size} new messages")

                if (newMessages.isNotEmpty()) {
                    // ✅ FIX: Remove duplicate date headers when prepending older messages
                    // When prepending older messages, we want to keep the date header at the TOP (with older messages)
                    // and remove duplicate date headers from existing messages (that are now in the middle)

                    // Save current scroll position BEFORE any modifications
                    val layoutManager = rvMessages.layoutManager as? LinearLayoutManager
                    val currentScrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
                    val currentView = layoutManager?.findViewByPosition(currentScrollPosition)
                    val currentOffset = currentView?.top ?: 0

                    Log.d("checkPagiantion", "⬆️ Scroll position before changes: position=$currentScrollPosition, offset=$currentOffset")

                    // Get date headers from the new messages batch
                    val newDateHeaders = newMessages
                        .filter { it.isDateHeader }
                        .map { it.dateHeaderText }
                        .toSet()

                    // Remove duplicate date headers from EXISTING messages (not from new messages)
                    // This ensures the date header stays at the top with all messages from that date
                    var headersRemovedCount = 0
                    if (newDateHeaders.isNotEmpty()) {
                        headersRemovedCount = messages.count { msg ->
                            msg.isDateHeader && newDateHeaders.contains(msg.dateHeaderText)
                        }
                        messages.removeAll { msg ->
                            msg.isDateHeader && newDateHeaders.contains(msg.dateHeaderText)
                        }
                        Log.d("checkPagiantion", "⬆️ Removed $headersRemovedCount duplicate date headers from existing messages")
                    }

                    // Calculate scroll adjustment: new items added minus headers removed
                    val scrollAdjustment = newMessages.size - headersRemovedCount

                    // Prepend older messages (duplicate headers already removed from existing messages)
                    messages.addAll(0, newMessages)
                    // ✅ PERFORMANCE: Use notifyItemRangeInserted instead of notifyDataSetChanged
                    // Since we're prepending at position 0, notify insertion
                    chatAdapter.notifyItemRangeInserted(0, newMessages.size)

                    Log.d("checkPagiantion", "✅ LOAD MORE COMPLETE")
                    Log.d("checkPagiantion", "✅ Added ${newMessages.size} older messages")
                    Log.d("checkPagiantion", "✅ Removed $headersRemovedCount duplicate headers")
                    Log.d("checkPagiantion", "✅ Net items added: $scrollAdjustment")
                    Log.d("checkPagiantion", "✅ Total messages now: ${messages.size}")
                    Log.d("checkPagiantion", "✅ New oldest timestamp: $oldestMessageTimestamp")
                    Log.d("checkPagiantion", "✅ Has more messages: $hasMoreMessages")

                    // Restore scroll position (account for net change in items)
                    rvMessages.post {
                        if (scrollAdjustment > 0) {
                            val newScrollPosition = currentScrollPosition + scrollAdjustment
                            layoutManager?.scrollToPositionWithOffset(newScrollPosition, currentOffset)
                            Log.d("checkPagiantion", "⬆️ Scroll position restored to: $newScrollPosition (was $currentScrollPosition + $scrollAdjustment)")
                        }
                    }

                    Log.d("ChatActivity", "✅ Loaded ${newMessages.size} older messages. Total: ${messages.size}")
                } else {
                    Log.d("checkPagiantion", "⚠️ No new messages to add (all were duplicates)")
                    Log.d("ChatActivity", "No new messages to add (duplicates filtered)")
                }

                isLoadingMoreMessages = false
            }
            .addOnFailureListener { e ->
                Log.e("checkPagiantion", "❌ ERROR loading more messages: ${e.message}")
                Log.e("ChatActivity", "❌ Error loading more messages", e)
                isLoadingMoreMessages = false
            }
    }

    /**
     * ✅ PERFORMANCE: Batch update messages as read to avoid multiple Firestore writes
     * This is much faster than updating each message individually
     */
    private fun markPendingMessagesAsRead() {
        // Mark all pending messages as read
        if (pendingMessagesToMarkRead.isEmpty()) {
            Log.d("ChatActivity", "No pending messages to mark as read")
            return
        }

        Log.d("ChatActivity", "Marking ${pendingMessagesToMarkRead.size} messages as read")

        // ✅ PERFORMANCE: Use batch write for faster updates (single network call)
        val batch = db.batch()
        val messageIdsToMark = pendingMessagesToMarkRead.toList() // Copy list
        
        messageIdsToMark.forEach { messageId ->
            val messageRef = db.collection("chats")
                .document(threadId)
                .collection("messages")
                .document(messageId)
            batch.update(messageRef, "isRead", true)
        }

        // Clear pending list immediately (before batch completes) to prevent duplicates
        pendingMessagesToMarkRead.clear()

        // Execute batch update (non-blocking, doesn't wait)
        batch.commit()
            .addOnSuccessListener {
                Log.d("ChatActivity", "✅ Marked ${messageIdsToMark.size} messages as read (batch)")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Failed to mark messages as read (batch)", e)
                // Re-add to pending list if batch failed (will retry later)
                pendingMessagesToMarkRead.addAll(messageIdsToMark)
            }
    }

    private fun setupClickListeners() {
        ivBack.setOnClickListener {
            // ✅ Use onBackPressed() for consistent keyboard handling
            onBackPressed()
        }

        btnSend.setOnClickListener {
            sendMessage()
        }

        // Three-dot menu listener for block/unblock
        ivMore.setOnClickListener {
            showOptionsMenu()
        }
    }

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) {
            return
        }

        // ✅ Quick validation checks (lightweight, no network calls)
        if (myUserId.isEmpty() || peerUserId.isEmpty() || peerUserId == "-1") {
            showAppToast("Error: Invalid user data", Toast.LENGTH_SHORT)
            return
        }

        if (isPeerBlocked) {
            showAppToast("Please unblock to send message", Toast.LENGTH_SHORT)
            return
        }

        // ✅ CRITICAL STEP 1: Clear EditText FIRST and regain focus IMMEDIATELY
        // This MUST happen first so user can type next message instantly
        etMessage.setText("")
        
        // ✅ CRITICAL: Request focus IMMEDIATELY and ensure EditText is enabled
        etMessage.isEnabled = true
        etMessage.isFocusable = true
        etMessage.isFocusableInTouchMode = true
        etMessage.requestFocus()
        
        // ✅ INSTANT: Show keyboard immediately (synchronous, no delay)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        
        // ✅ CRITICAL: Ensure focus is maintained (RecyclerView might try to steal it)
        etMessage.post {
            if (!etMessage.hasFocus()) {
                etMessage.requestFocus()
                imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }

        // ✅ STEP 3: Prepare message data (lightweight operations)
        val currentTime = System.currentTimeMillis()
        val currentDate = Date(currentTime)
        val timeString = formatMessageTime(currentDate)
        val tempId = "temp_${currentTime}"
        
        val tempMessage = ChatMessage(
            id = tempId,
            message = messageText,
            timestamp = timeString,
            isSentByMe = true,
            date = currentDate
        )

        // ✅ STEP 4: Add message to UI IMMEDIATELY
        val insertPosition = messages.size
        
        // Fast date header check
        val lastMessage = messages.lastOrNull()
        val lastDateHeader = if (lastMessage?.isDateHeader == true) {
            lastMessage.dateHeaderText
        } else {
            lastMessage?.date?.let { getDateHeaderText(it) }
        }
        val newDateHeader = getDateHeaderText(currentDate)

        if (lastDateHeader != newDateHeader) {
            messages.add(ChatMessage(
                id = "date_header_$tempId",
                message = "",
                timestamp = "",
                isSentByMe = false,
                date = currentDate,
                isDateHeader = true,
                dateHeaderText = newDateHeader
            ))
        }

        messages.add(tempMessage)
        
        // ✅ STEP 5: Update UI IMMEDIATELY (but ensure EditText stays focused)
        val itemCount = messages.size - insertPosition
        
        // ✅ CRITICAL: Restore focus BEFORE notifying adapter (prevents focus loss)
        etMessage.requestFocus()
        imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        
        // Notify adapter (this might cause layout pass, but EditText already has focus)
        chatAdapter.notifyItemRangeInserted(insertPosition, itemCount)
        
        // ✅ STEP 6: Scroll to bottom (non-blocking, use post)
        // Scroll happens in background, EditText already has focus
        rvMessages.post {
            rvMessages.scrollToPosition(messages.size - 1)
            // Ensure focus is maintained after scroll
            etMessage.requestFocus()
        }
        
        // ✅ CRITICAL: Aggressively restore focus after any UI updates
        etMessage.post {
            if (!etMessage.hasFocus()) {
                etMessage.requestFocus()
                imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        
        // ✅ CRITICAL: Final focus check after all operations
        etMessage.postDelayed({
            if (!etMessage.hasFocus()) {
                etMessage.requestFocus()
                imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }, 30)

        // ✅ STEP 7: ALL FIRESTORE OPERATIONS IN BACKGROUND (completely non-blocking)
        // EditText is already ready - user can type next message NOW
        // Firebase sync happens in background without blocking EditText
        backgroundExecutor.execute {
            // Check internet in background (doesn't block UI)
            if (!isInternetAvailable()) {
                mainHandler.post {
                    // ✅ CRITICAL: Don't update UI if activity is finishing
                    if (isFinishing || isDestroyed) return@post
                    
                    showAppToast("No internet connection", Toast.LENGTH_SHORT)
                    // Remove temp message if no internet
                    val tempIndex = messages.indexOfFirst { it.id == tempId }
                    if (tempIndex != -1) {
                        messages.removeAt(tempIndex)
                        chatAdapter.notifyItemRemoved(tempIndex)
                    }
                }
                return@execute
            }

            // Add to queue and process in background (async, non-blocking)
            // This doesn't block EditText - user can continue typing immediately
            messageQueue.offer(messageText)
            processMessageQueue()
        }
        
        // ✅ EditText is NOW ready for immediate input - no waiting for Firebase!
    }

    /**
     * Process messages from the queue one at a time
     * This ensures messages are sent sequentially and allows users to type multiple messages
     */
    private fun processMessageQueue() {
        // If already sending a message, don't start another (queue will be processed when current one finishes)
        if (isSendingMessage) {
            Log.d("ChatActivity", "⏳ Already sending a message, waiting for it to complete. Queue size: ${messageQueue.size}")
            return
        }

        // Poll next message from queue
        val messageText = messageQueue.poll()
        if (messageText == null) {
            Log.d("ChatActivity", "✅ Message queue is empty")
            return
        }

        // Mark that we're sending a message
        isSendingMessage = true
        Log.d("ChatActivity", "🚀 Processing message from queue: '$messageText'. Remaining in queue: ${messageQueue.size}")

        // Check if peer has blocked me before sending
        checkIfPeerBlockedMeAndSendMessage(messageText)
    }

    /**
     * Mark current message sending as complete and process next message in queue
     * This runs in background thread context
     */
    private fun completeMessageSending() {
        isSendingMessage = false
        Log.d("ChatActivity", "✅ Message sending completed. Processing next message in queue...")
        // Process next message in queue (continues in background thread)
        processMessageQueue()
    }

    private fun checkIfPeerBlockedMeAndSendMessage(messageText: String) {
        // ✅ OPTIMIZATION: Use cached result to prevent lag from repeated Firestore queries
        val currentTime = System.currentTimeMillis()
        val cacheValid = peerHasBlockedMeCache != null && 
                         (currentTime - peerBlockedMeCacheTime) < PEER_BLOCKED_CACHE_DURATION
        
        if (cacheValid) {
            // Use cached result (no lag from Firestore query)
            Log.d("ChatActivity", "📦 Using cached peer-blocked-me status: ${peerHasBlockedMeCache}")
            sendMessageToFirestore(messageText)
            return
        }
        
        // Cache expired or doesn't exist - check Firestore and update cache
        // But send message immediately to prevent lag (check happens in background)
        sendMessageToFirestore(messageText)
        
        // Update cache in background (non-blocking)
        db.collection("blocked_users")
            .document(peerUserId)  // Check PEER's blocked list
            .collection("users")
            .document(myUserId)    // Is SENDER (ME) in it?
            .get()
            .addOnSuccessListener { doc ->
                peerHasBlockedMeCache = doc.exists()
                peerBlockedMeCacheTime = currentTime
                if (doc.exists()) {
                    Log.d("ChatActivity", "⚠️ Peer has blocked me (cached for ${PEER_BLOCKED_CACHE_DURATION}ms)")
                } else {
                    Log.d("ChatActivity", "✅ Peer has not blocked me (cached for ${PEER_BLOCKED_CACHE_DURATION}ms)")
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Failed to check if peer blocked me", e)
                // Don't cache on failure - will retry next time
            }
    }

    private fun sendMessageToFirestore(messageText: String) {
        // ✅ UI UPDATE ALREADY DONE IN sendMessage() - This function only handles Firestore operations
        // Find the temp message we already added to UI
        val tempMessage = messages.findLast { 
            !it.isDateHeader && it.message == messageText && it.id.startsWith("temp_")
        }
        val tempId = tempMessage?.id ?: "temp_${System.currentTimeMillis()}"

        // Create message data for Firestore
        val messageData = hashMapOf(
            "from" to myUserId,
            "to" to peerUserId,
            "text" to messageText,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false  // Mark as unread initially
        )

        Log.d("ChatActivity", "Attempting to send message to threadId: $threadId")
        Log.d("ChatActivity", "Message data: $messageData")

        // First, ensure the parent thread document exists with user metadata
        val userName = intent.getStringExtra("USER_NAME") ?: "User"
        val userImage = intent.getStringExtra("USER_IMAGE") ?: ""

        val threadMetadata = hashMapOf(
            "user_${myUserId}_name" to BaseApplication.getInstance()?.getPrefs()?.getUserData()?.name.orEmpty(),
            "user_${myUserId}_image" to BaseApplication.getInstance()?.getPrefs()?.getUserData()?.image.orEmpty(),
            "user_${peerUserId}_name" to userName,
            "user_${peerUserId}_image" to userImage,
            "lastUpdated" to FieldValue.serverTimestamp()
        )

        // Create/update the parent thread document first
        db.collection("chats")
            .document(threadId)
            .set(threadMetadata, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("ChatActivity", "✅ Thread metadata updated")

                // Now send the message
                db.collection("chats")
                    .document(threadId)
                    .collection("messages")
                    .add(messageData)
                    .addOnSuccessListener { documentReference ->
                        Log.d("ChatActivity", "✅ Message sent successfully: ${documentReference.id}")
                        // Temp message will be replaced when listener receives the real message

                        // Check if receiver has blocked sender before sending notification
                        checkIfReceiverBlockedMeAndSendNotification(messageText)
                        
                        // Mark message sending as complete and process next message in queue
                        completeMessageSending()
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatActivity", "❌ Error sending message", e)
                        Log.e("ChatActivity", "Error type: ${e.javaClass.simpleName}")
                        Log.e("ChatActivity", "Error message: ${e.message}")
                        Log.e("ChatActivity", "Error cause: ${e.cause}")

                        // ✅ UI updates must be on main thread
                        mainHandler.post {
                            // ✅ CRITICAL: Don't update UI if activity is finishing
                            if (isFinishing || isDestroyed) return@post
                            
                            // Remove temp message on failure
                            val tempIndex = messages.indexOfFirst { it.id == tempId }
                            if (tempIndex != -1) {
                                var headerRemoved = false
                                var headerIndex = -1
                                
                                // Check if we need to remove date header
                                if (tempIndex > 0 && messages.getOrNull(tempIndex - 1)?.isDateHeader == true) {
                                    if (tempIndex >= messages.size || messages.getOrNull(tempIndex)?.isDateHeader == true) {
                                        headerIndex = tempIndex - 1
                                        headerRemoved = true
                                    }
                                }
                                
                                // Remove items in reverse order to maintain correct indices
                                if (headerRemoved && headerIndex != -1) {
                                    messages.removeAt(tempIndex) // Remove message first
                                    messages.removeAt(headerIndex) // Then remove header
                                    // ✅ PERFORMANCE: Use specific notify methods instead of notifyDataSetChanged
                                    chatAdapter.notifyItemRemoved(tempIndex)
                                    chatAdapter.notifyItemRemoved(headerIndex)
                                } else {
                                    messages.removeAt(tempIndex)
                                    // ✅ PERFORMANCE: Use specific notify methods instead of notifyDataSetChanged
                                    chatAdapter.notifyItemRemoved(tempIndex)
                                }
                            }

                            // Show detailed error to user
                            val errorMsg = when {
                                e.message?.contains("PERMISSION_DENIED") == true ->
                                    "Permission denied. Enable Firestore in Firebase Console"
                                e.message?.contains("NOT_FOUND") == true ->
                                    "Firestore not enabled. Check Firebase Console"
                                e.message?.contains("UNAVAILABLE") == true ->
                                    "No internet connection"
                                else -> "Failed: ${e.message}"
                            }
                            showAppToast(errorMsg, Toast.LENGTH_LONG)
                        }
                        
                        // Mark message sending as complete and process next message in queue
                        completeMessageSending()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Error creating thread metadata", e)

                // ✅ UI updates must be on main thread
                mainHandler.post {
                    // ✅ CRITICAL: Don't update UI if activity is finishing
                    if (isFinishing || isDestroyed) return@post
                    
                    // Remove temp message on failure
                    val tempIndex = messages.indexOfFirst { it.id == tempId }
                    if (tempIndex != -1) {
                        var headerRemoved = false
                        var headerIndex = -1
                        
                        // Check if we need to remove date header
                        if (tempIndex > 0 && messages.getOrNull(tempIndex - 1)?.isDateHeader == true) {
                            if (tempIndex >= messages.size || messages.getOrNull(tempIndex)?.isDateHeader == true) {
                                headerIndex = tempIndex - 1
                                headerRemoved = true
                            }
                        }
                        
                        // Remove items in reverse order to maintain correct indices
                        if (headerRemoved && headerIndex != -1) {
                            messages.removeAt(tempIndex) // Remove message first
                            messages.removeAt(headerIndex) // Then remove header
                            // ✅ PERFORMANCE: Use specific notify methods instead of notifyDataSetChanged
                            chatAdapter.notifyItemRemoved(tempIndex)
                            chatAdapter.notifyItemRemoved(headerIndex)
                        } else {
                            messages.removeAt(tempIndex)
                            // ✅ PERFORMANCE: Use specific notify methods instead of notifyDataSetChanged
                            chatAdapter.notifyItemRemoved(tempIndex)
                        }
                    }

                    showAppToast("Failed to create chat thread", Toast.LENGTH_SHORT)
                }
                
                // Mark message sending as complete and process next message in queue
                completeMessageSending()
            }
    }

    private fun checkIfReceiverBlockedMeAndSendNotification(messageText: String) {
        // Check if the RECEIVER has blocked the SENDER (ME)
        db.collection("blocked_users")
            .document(peerUserId)  // Check RECEIVER's blocked list
            .collection("users")
            .document(myUserId)    // Is SENDER (ME) in it?
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Receiver has blocked me - don't send notification
                    Log.d("ChatActivity", "⚠️ Receiver has blocked me - Skipping notification API call")
                } else {
                    // Not blocked - check if receiver is actively viewing this chat
                    Log.d("ChatActivity", "✅ Not blocked - Checking if receiver is viewing this chat")
                    checkIfReceiverIsViewingChatAndSendNotification(messageText)
                }
            }
            .addOnFailureListener { e ->
                // If check fails, skip notification to be safe
                Log.e("ChatActivity", "❌ Failed to check if receiver blocked me - Skipping notification", e)
            }
    }

    /**
     * ✅ PERFORMANCE: Check if receiver is viewing chat (non-blocking, with timeout)
     */
    private fun checkIfReceiverIsViewingChatAndSendNotification(messageText: String) {
        // ✅ PERFORMANCE: Run in background thread to avoid blocking
        backgroundExecutor.execute {
            // Give a tiny delay to allow receiver's "active" status to sync
            android.os.Handler(mainLooper).postDelayed({
                db.collection("active_chats")
                    .document(peerUserId)
                    .get()
                    .addOnSuccessListener { doc ->
                        val activeThreadId = doc.getString("threadId")
                        val lastUpdatedMillis = doc.getLong("lastUpdated") ?: 0L
                        val currentTime = System.currentTimeMillis()

                        // ✅ Consider user active if last updated within last 15 seconds
                        val isActiveNow = (currentTime - lastUpdatedMillis) < 15_000

                        if (activeThreadId == threadId && isActiveNow) {
                            Log.d("ChatActivity", "👀 Receiver is viewing this chat - skip notification completely")
                            return@addOnSuccessListener
                        }

                        // Receiver not active → now check read status to avoid duplicates
                        checkLastMessageReadStatusAndSendNotification(messageText)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatActivity", "⚠️ Failed to check active chat status - sending notification anyway", e)
                        checkLastMessageReadStatusAndSendNotification(messageText)
                    }
            }, 500)
        }
    }




    private fun sendMessageNotificationApi(messageText: String) {
        try {
            val senderIdInt = myUserId.toIntOrNull()
            val receiverIdInt = peerUserId.toIntOrNull()

            if (senderIdInt == null || receiverIdInt == null) {
                Log.e("ChatActivity", "❌ Invalid user IDs for notification - senderId: $myUserId, receiverId: $peerUserId")
                return
            }

            Log.d("ChatActivity", "📤 Calling send_message_notification API")
            Log.d("ChatActivity", "Sender: $senderIdInt, Receiver: $receiverIdInt, Message: $messageText")

            // Call the message notification API
            messageNotificationViewModel.sendMessageNotification(
                senderId = senderIdInt,
                receiverId = receiverIdInt,
                message = messageText
            )

        } catch (e: Exception) {
            Log.e("ChatActivity", "❌ Exception while sending notification: ${e.message}", e)
        }
    }

    private fun observeNotificationResponse() {
        messageNotificationViewModel.notificationResponseLiveData.observe(this) { response ->
            response?.let {
                if (it.success) {
                    Log.d("ChatActivity", "✅ Message notification sent successfully!")
                    Log.d("ChatActivity", "Response: ${it.message}")
                } else {
                    Log.e("ChatActivity", "❌ Failed to send message notification: ${it.message}")
                }
            }
        }

        messageNotificationViewModel.notificationErrorLiveData.observe(this) { error ->
            error?.let {
                Log.e("ChatActivity", "❌ Message notification error: $it")
            }
        }
    }

    private fun setMyActiveChatStatus() {
        // Mark this user as actively viewing this chat
        val activeChatData = mapOf<String, Any>(
            "threadId" to threadId,
            "lastUpdated" to System.currentTimeMillis()
        )

        db.collection("active_chats")
            .document(myUserId)
            .set(activeChatData, SetOptions.merge())  // ✅ Use set() with merge instead of update()
            .addOnSuccessListener {
                Log.d("ChatActivity", "✅ Marked as actively viewing chat: $threadId")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Failed to set active chat status", e)
            }
    }

    private fun startActiveChatHeartbeat() {
        // ✅ OPTIMIZATION: Changed from 5s to 10s to reduce writes
        // User won't notice difference but saves 50% on write operations
        val handler = android.os.Handler(mainLooper)
        val runnable = object : Runnable {
            override fun run() {
                if (isChatVisible) {
                    db.collection("active_chats").document(myUserId)
                        .set(mapOf("lastUpdated" to System.currentTimeMillis()), SetOptions.merge())  // ✅ Use set() instead of update()
                        .addOnFailureListener { e ->
                            Log.e("ChatActivity", "⚠️ Failed to refresh active chat heartbeat", e)
                        }
                    handler.postDelayed(this, 10000)  // ✅ Changed from 5000 to 10000
                }
            }
        }
        handler.postDelayed(runnable, 10000)  // ✅ Changed from 5000 to 10000
    }


    /**
     * ✅ PERFORMANCE: Clear active chat status non-blocking (don't wait for completion)
     * This prevents lag when pressing back button
     */
    private fun clearMyActiveChatStatus() {
        // ✅ PERFORMANCE: Run in background thread to avoid blocking UI (back button)
        backgroundExecutor.execute {
            // Update last seen timestamp instead of deleting
            val lastSeenTimestamp = System.currentTimeMillis()
            val lastSeenData = hashMapOf(
                "lastSeen" to lastSeenTimestamp,
                "threadId" to ""  // Clear active thread to indicate user left
            )

            val date = java.util.Date(lastSeenTimestamp)
            // ✅ PERFORMANCE: Uses cached formatter
            Log.d("lastseenlog", "💾 Saving MY last seen for user $myUserId: ${dateTimeFormat.format(date)} ($lastSeenTimestamp)")

            // ✅ PERFORMANCE: Don't wait for completion - fire and forget
            db.collection("active_chats")
                .document(myUserId)
                .set(lastSeenData)
                .addOnSuccessListener {
                    Log.d("ChatActivity", "✅ Updated last seen timestamp")
                    Log.d("lastseenlog", "✅ Successfully saved MY last seen to Firebase")
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "❌ Failed to update last seen", e)
                    Log.e("lastseenlog", "❌ Failed to save MY last seen: ${e.message}")
                }
        }
    }

    /**
     * ✅ PERFORMANCE: Check last message read status in background (non-blocking)
     * This query can be slow with lakhs of messages, so we don't wait for it
     */
    private fun checkLastMessageReadStatusAndSendNotification(messageText: String) {
        // ✅ PERFORMANCE: Run in background thread to avoid blocking message send
        // ✅ SAFETY: Wrap in try-catch to prevent crash if executor is shutdown
        try {
            backgroundExecutor.execute {
            db.collection("chats")
                .document(threadId)
                .collection("messages")
                .whereEqualTo("from", myUserId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { documents ->
                    val lastMessageDoc = documents.firstOrNull()
                    val lastMessageIsRead = lastMessageDoc?.getBoolean("isRead") ?: true
                    val lastMessageTimestamp = lastMessageDoc?.getTimestamp("timestamp")

                    val receiverReadRecently = lastMessageIsRead &&
                            lastMessageTimestamp != null &&
                            (System.currentTimeMillis() - lastMessageTimestamp.toDate().time) <= 60_000 // 1 minute window

                    if (receiverReadRecently) {
                        Log.d("ChatActivity", "👀 Receiver recently read previous message – skipping notification")
                    } else {
                        Log.d("ChatActivity", "📩 Previous message unread or older – sending notification")
                        sendMessageNotificationApi(messageText)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "⚠️ Failed to check last message read status, sending notification anyway", e)
                    // Send notification anyway if check fails (better UX)
                    sendMessageNotificationApi(messageText)
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // Executor is shutdown - activity is probably finishing, ignore
            Log.d("ChatActivity", "⚠️ Executor rejected notification check - activity finishing")
        }
    }

    override fun onResume() {
        super.onResume()
        // User is now viewing the chat - mark it as visible
        isChatVisible = true
        Log.d("ChatActivity", "Chat is now visible - marking pending messages as read")
        Log.d("lastseenlog", "📱 My User ID: $myUserId - Resuming chat with user $peerUserId")

        // Mark any pending messages as read
        markPendingMessagesAsRead()

        // User is now actively viewing this chat
        setMyActiveChatStatus()
        startActiveChatHeartbeat() // ✅ keep presence alive every few seconds
        Log.d("ChatActivity", "📱 User resumed - Marked as actively viewing this chat")
    }

    override fun onPause() {
        super.onPause()
        // User is no longer viewing the chat
        isChatVisible = false
        Log.d("ChatActivity", "Chat is no longer visible")
        Log.d("lastseenlog", "📱 My User ID: $myUserId - Pausing chat, updating last seen")

        // User is no longer actively viewing this chat
        clearMyActiveChatStatus()
        Log.d("ChatActivity", "📱 User paused - Cleared active chat status")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up active chat status (non-blocking)
        clearMyActiveChatStatus()
        // Remove Firestore listener
        messagesListenerRegistration?.remove()
        // ✅ PERFORMANCE: Don't wait for executor shutdown - let it finish in background
        // shutdown() blocks until all tasks complete, which can cause lag
        // Instead, shutdown gracefully without waiting
        backgroundExecutor.shutdown()
        // Don't wait for shutdown - allows activity to close immediately
        Log.d("ChatActivity", "📱 Chat destroyed - Cleaned up presence tracking")
    }

    override fun onBackPressed() {
        // ✅ CRITICAL: Stop Firestore listener IMMEDIATELY to prevent UI updates during close
        messagesListenerRegistration?.remove()
        messagesListenerRegistration = null
        
        // ✅ CRITICAL: Cancel all pending UI updates on main thread
        mainHandler.removeCallbacksAndMessages(null)
        
        // ✅ CRITICAL: Cancel all pending view posts (RecyclerView, EditText)
        rvMessages.removeCallbacks(null)
        etMessage.removeCallbacks(null)
        
        // Close keyboard immediately if open
        if (etMessage.hasFocus()) {
            etMessage.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etMessage.windowToken, 0)
        }
        
        // Always finish the activity - don't wait for keyboard animation
        super.onBackPressed()
    }
    

    // ==================== BLOCK/UNBLOCK FUNCTIONS ====================

    private fun checkIfUserIsBlocked() {
        Log.d("checkPagiantion", "🔍 Checking if user is blocked...")
        // Check if I have blocked this peer user
        db.collection("blocked_users")
            .document(myUserId)
            .collection("users")
            .document(peerUserId)
            .get()
            .addOnSuccessListener { doc ->
                isPeerBlocked = doc.exists()
                blockTimestamp = if (isPeerBlocked) doc.getTimestamp("blockedAt") else null
                Log.d("checkPagiantion", "🔍 Block check complete - isPeerBlocked: $isPeerBlocked, blockTimestamp: $blockTimestamp")
                Log.d("checkPagiantion", "🔍 Now calling setupFirestoreListener()...")
                Log.d("ChatActivity", "Block status loaded: isPeerBlocked=$isPeerBlocked, blockTimestamp=$blockTimestamp")

                // Now setup firestore listener with correct block status
                setupFirestoreListener()
            }
            .addOnFailureListener { e ->
                Log.e("checkPagiantion", "❌ Failed to check block status: ${e.message}")
                Log.e("ChatActivity", "❌ Failed to check block status", e)
                // Proceed anyway with isPeerBlocked = false
                Log.d("checkPagiantion", "🔍 Proceeding with setupFirestoreListener() anyway...")
                setupFirestoreListener()
            }
    }

    private fun showOptionsMenu() {
        val popup = PopupMenu(this, ivMore)
        popup.menuInflater.inflate(R.menu.menu_chat, popup.menu)

        // Show block option or unblock option based on current status
        popup.menu.findItem(R.id.action_block)?.isVisible = !isPeerBlocked
        popup.menu.findItem(R.id.action_unblock)?.isVisible = isPeerBlocked

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_block -> {
                    showBlockConfirmationDialog()
                    true
                }
                R.id.action_unblock -> {
                    unblockUser()
                    true
                }
                else -> false
            }
        }
        popup.show()
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

    private fun blockUser() {
        val currentTimestamp = Timestamp.now()
        val userName = intent.getStringExtra("USER_NAME") ?: "User"
        val userImage = intent.getStringExtra("USER_IMAGE") ?: ""

        val blockData = hashMapOf(
            "blockedAt" to currentTimestamp,
            "userName" to userName,
            "userImage" to userImage
        )

        db.collection("blocked_users")
            .document(myUserId)
            .collection("users")
            .document(peerUserId)
            .set(blockData)
            .addOnSuccessListener {
                isPeerBlocked = true
                blockTimestamp = currentTimestamp
                showAppToast("User blocked successfully", Toast.LENGTH_SHORT)
                Log.d("ChatActivity", "✅ User blocked successfully")

                // Refresh Firestore listener to filter blocked user's messages
                setupFirestoreListener()
            }
            .addOnFailureListener { e ->
                showAppToast("Failed to block user", Toast.LENGTH_SHORT)
                Log.e("ChatActivity", "❌ Failed to block user", e)
            }
    }

    private fun unblockUser() {
        // Show confirmation dialog before unblocking
        showUnblockConfirmationDialog()
    }

    private fun showUnblockConfirmationDialog() {
        // Create a custom dialog with beautiful UI
        val dialogView = layoutInflater.inflate(R.layout.dialog_unblock_user_confirmation, null)

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

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_unblock).setOnClickListener {
            performUnblock()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun performUnblock() {
        db.collection("blocked_users")
            .document(myUserId)
            .collection("users")
            .document(peerUserId)
            .delete()
            .addOnSuccessListener {
                isPeerBlocked = false
                blockTimestamp = null
                showAppToast("User unblocked successfully", Toast.LENGTH_SHORT)
                Log.d("ChatActivity", "✅ User unblocked successfully")

                // Refresh Firestore listener to show all messages
                setupFirestoreListener()
            }
            .addOnFailureListener { e ->
                showAppToast("Failed to unblock user", Toast.LENGTH_SHORT)
                Log.e("ChatActivity", "❌ Failed to unblock user", e)
            }
    }

    // ==================== ONLINE STATUS FUNCTIONS ====================

    /**
     * Listen to peer user's online status from Firebase active_chats collection
     * Shows:
     * - "Active recently" if last seen < 60 minutes
     * - "Active X hour(s) ago" if last seen < 24 hours
     * - Hidden if last seen > 24 hours
     */
    private fun listenToUserOnlineStatus() {
        if (peerUserId.isEmpty() || peerUserId == "-1") {
            tvUserStatus.visibility = android.view.View.GONE
            Log.d("lastseenlog", "❌ Invalid peer user ID: '$peerUserId'")
            return
        }

        Log.d("ChatActivity", "🔍 Listening to online status for user $peerUserId")
        Log.d("lastseenlog", "🔍 Started listening for user $peerUserId")

        // Listen to peer user's active_chats document
        db.collection("active_chats")
            .document(peerUserId)
            .addSnapshotListener { documentSnapshot, error ->
                if (error != null) {
                    Log.e("ChatActivity", "❌ Error listening to user status", error)
                    Log.e("lastseenlog", "❌ Error for user $peerUserId: ${error.message}")
                    tvUserStatus.visibility = android.view.View.GONE
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    // Check if user is actively chatting (has lastUpdated) or just has lastSeen
                    val lastUpdated = documentSnapshot.getLong("lastUpdated")
                    val lastSeen = documentSnapshot.getLong("lastSeen")
                    val threadId = documentSnapshot.getString("threadId")

                    // Log raw values
                    Log.d("ChatActivity", "========== PEER USER STATUS ==========")
                    Log.d("ChatActivity", "👤 Peer User ID: $peerUserId")
                    Log.d("ChatActivity", "🔄 lastUpdated: $lastUpdated")
                    Log.d("ChatActivity", "👋 lastSeen: $lastSeen")
                    Log.d("ChatActivity", "💬 threadId: '$threadId'")

                    // Convert to readable dates
                    // ✅ PERFORMANCE: Uses cached formatter
                    if (lastUpdated != null) {
                        val date = java.util.Date(lastUpdated)
                        Log.d("ChatActivity", "📅 lastUpdated (readable): ${dateTimeFormat.format(date)}")
                    }
                    if (lastSeen != null) {
                        val date = java.util.Date(lastSeen)
                        Log.d("ChatActivity", "📅 lastSeen (readable): ${dateTimeFormat.format(date)}")
                    }

                    // Determine which timestamp to use
                    val timestamp = when {
                        // If lastUpdated exists and threadId is not empty, user is actively chatting
                        lastUpdated != null && !threadId.isNullOrEmpty() -> {
                            Log.d("ChatActivity", "✅ Using lastUpdated (user is actively chatting)")
                            lastUpdated
                        }
                        // Otherwise use lastSeen if available
                        lastSeen != null -> {
                            Log.d("ChatActivity", "✅ Using lastSeen (user left the chat)")
                            lastSeen
                        }
                        // Fallback to lastUpdated even if threadId is empty
                        lastUpdated != null -> {
                            Log.d("ChatActivity", "✅ Using lastUpdated (fallback)")
                            lastUpdated
                        }
                        else -> {
                            Log.d("ChatActivity", "❌ No timestamp available")
                            null
                        }
                    }

                    if (timestamp != null) {
                        val now = System.currentTimeMillis()
                        val diffMillis = now - timestamp
                        val diffMinutes = diffMillis / (1000 * 60)
                        val diffHours = diffMillis / (1000 * 60 * 60)

                        Log.d("ChatActivity", "⏰ Current time: $now")
                        Log.d("ChatActivity", "⏰ Selected timestamp: $timestamp")
                        Log.d("ChatActivity", "⏰ Difference: $diffMinutes minutes ($diffHours hours)")
                        Log.d("ChatActivity", "======================================")

                        // Dedicated log for last seen tracking
                        // ✅ PERFORMANCE: Uses cached formatter
                        val date = java.util.Date(timestamp)
                        Log.d("lastseenlog", "User $peerUserId last seen: ${dateTimeFormat.format(date)} ($diffMinutes min ago)")

                        updateOnlineStatusUI(timestamp)
                    } else {
                        tvUserStatus.visibility = android.view.View.GONE
                        Log.d("ChatActivity", "⚠️ No valid timestamp found - hiding status")
                        Log.d("ChatActivity", "======================================")
                    }
                } else {
                    // No active_chats document - user has never been active
                    tvUserStatus.visibility = android.view.View.GONE
                    Log.d("ChatActivity", "========== PEER USER STATUS ==========")
                    Log.d("ChatActivity", "👤 Peer User ID: $peerUserId")
                    Log.d("ChatActivity", "ℹ️ User has no active_chats document")
                    Log.d("ChatActivity", "======================================")
                    Log.w("lastseenlog", "⚠️ User $peerUserId has NO active_chats document - never been active")
                }
            }
    }

    /**
     * Update online status UI based on last seen timestamp
     * @param lastSeenMillis The timestamp when user was last active (in milliseconds)
     * ✅ PERFORMANCE: Debounced to prevent excessive UI updates
     */
    private fun updateOnlineStatusUI(lastSeenMillis: Long) {
        val now = System.currentTimeMillis()
        // ✅ PERFORMANCE: Debounce updates to prevent lag
        if (now - lastStatusUpdateTime < STATUS_UPDATE_THROTTLE) {
            return // Skip this update
        }
        lastStatusUpdateTime = now
        
        val diffMillis = now - lastSeenMillis
        val diffMinutes = diffMillis / (1000 * 60)
        val diffHours = diffMillis / (1000 * 60 * 60)
        val diffDays = diffMillis / (1000 * 60 * 60 * 24)

        Log.d("ChatActivity", "========== UPDATE STATUS UI ==========")
        Log.d("ChatActivity", "⏰ Current time (millis): $now")
        Log.d("ChatActivity", "⏰ Last seen (millis): $lastSeenMillis")
        Log.d("ChatActivity", "⏰ Difference: $diffMinutes minutes | $diffHours hours | $diffDays days")

        when {
            // Within last 60 minutes - show "Active recently"
            diffMinutes < 60 -> {
                tvUserStatus.text = "Active recently"
                tvUserStatus.visibility = android.view.View.VISIBLE
                vOnlineIndicator.visibility = android.view.View.VISIBLE
                Log.d("ChatActivity", "✅ DISPLAYING: 'Active recently' (< 60 min)")
            }

            // Within last 24 hours - show "Active X hour(s) ago"
            diffHours < 24 -> {
                val hoursText = if (diffHours == 1L) "1 hour" else "$diffHours hours"
                tvUserStatus.text = "Active $hoursText ago"
                tvUserStatus.visibility = android.view.View.VISIBLE
                vOnlineIndicator.visibility = android.view.View.VISIBLE
                Log.d("ChatActivity", "✅ DISPLAYING: 'Active $hoursText ago' (< 24 hours)")
            }

            // More than 24 hours - hide status AND dot
            else -> {
                tvUserStatus.visibility = android.view.View.GONE
                vOnlineIndicator.visibility = android.view.View.GONE
                Log.d("ChatActivity", "🚫 HIDING STATUS AND DOT (> 24 hours ago)")
            }
        }
        Log.d("ChatActivity", "======================================")
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false

        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    /**
     * Format message timestamp like WhatsApp:
     * - "Today, 10:30 AM" for today's messages
     * - "Yesterday, 10:30 AM" for yesterday's messages
     * - "31 Oct 2025, 10:30 AM" for older messages
     */
    private fun formatMessageTimestamp(date: Date): String {
        // Use Indian Standard Time (IST) for all date comparisons
        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

        // Get today's calendar in IST and normalize to midnight
        val todayCalendar = Calendar.getInstance(istTimeZone).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Get message calendar in IST and normalize to midnight
        val messageCalendar = Calendar.getInstance(istTimeZone).apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Format time in IST
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
            timeZone = istTimeZone
        }
        val timeString = timeFormat.format(date)

        // Check if message is from today (in IST)
        val isToday = todayCalendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                todayCalendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)

        if (isToday) {
            return "Today, $timeString"
        }

        // Check if message is from yesterday (in IST)
        val yesterdayCalendar = Calendar.getInstance(istTimeZone).apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val isYesterday = yesterdayCalendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                yesterdayCalendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) {
            return "Yesterday, $timeString"
        }

        // For older messages, show date like "31 Oct 2025, 10:30 AM" (in IST)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply {
            timeZone = istTimeZone
        }
        val dateString = dateFormat.format(date)

        return "$dateString, $timeString"
    }

    /**
     * Format message time only (for display in message bubble)
     * Returns: "10:30 AM" or "2:45 PM" (in IST)
     * ✅ PERFORMANCE: Uses cached formatter instead of creating new one each time
     */
    private fun formatMessageTime(date: Date): String {
        return timeFormat.format(date)
    }

    /**
     * Get date header text for a given date
     * Returns: "Today", "Yesterday", or "31 Oct 2025" (in IST)
     */
    private fun getDateHeaderText(date: Date): String {
        // Use Indian Standard Time (IST) for all date comparisons
        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

        // Get today's calendar in IST and normalize to midnight
        val todayCalendar = Calendar.getInstance(istTimeZone).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Get message calendar in IST and normalize to midnight
        val messageCalendar = Calendar.getInstance(istTimeZone).apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Check if message is from today (in IST)
        val isToday = todayCalendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                todayCalendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)

        if (isToday) {
            return "Today"
        }

        // Check if message is from yesterday (in IST)
        val yesterdayCalendar = Calendar.getInstance(istTimeZone).apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val isYesterday = yesterdayCalendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                yesterdayCalendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) {
            return "Yesterday"
        }

        // For older messages, show date like "31 Oct 2025" (in IST)
        // ✅ PERFORMANCE: Uses cached formatter instead of creating new one each time
        val result = dateFormat.format(date)
        return result
    }

    /**
     * Insert date headers between message groups (like WhatsApp)
     * Groups messages by date and inserts date headers
     */
    private fun insertDateHeaders(messages: List<ChatMessage>): MutableList<ChatMessage> {
        val result = mutableListOf<ChatMessage>()

        if (messages.isEmpty()) return result

        var currentDateHeader: String? = null

        for (message in messages) {
            // Use IST timezone for fallback date
            val messageDate = message.date ?: run {
                val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
                Calendar.getInstance(istTimeZone).time
            }
            val dateHeaderText = getDateHeaderText(messageDate)

            // If this message has a different date header than the previous one, insert a date header
            if (dateHeaderText != currentDateHeader) {
                result.add(
                    ChatMessage(
                        id = "date_header_${message.id}",
                        message = "",
                        timestamp = "",
                        isSentByMe = false,
                        date = messageDate,
                        isDateHeader = true,
                        dateHeaderText = dateHeaderText
                    )
                )
                currentDateHeader = dateHeaderText
            }

            // Add the actual message
            result.add(message)
        }

        return result
    }

}