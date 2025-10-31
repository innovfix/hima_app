package com.gmwapp.hima.activities

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
        Log.d("checkPagiantion", "🏁 ChatActivity onCreate() completed")
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

            Log.d("checkPagiantion", "📋 RecyclerView setup complete - scroll listener will be added")

            // Add scroll listener for pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    // Debug log to verify scroll listener is working
                    if (dy != 0) {
                        Log.d("checkPagiantion", "🔄 Scroll detected - dy: $dy, dx: $dx, isLoadingMore: $isLoadingMoreMessages, hasMore: $hasMoreMessages")
                    }
                    
                    // Check if user scrolled up (dy < 0 means scrolling up)
                    if (dy < 0 && !isLoadingMoreMessages && hasMoreMessages) {
                        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                        layoutManager?.let {
                            // Check if first visible item is visible (user scrolled to top)
                            val firstVisiblePosition = it.findFirstVisibleItemPosition()
                            val firstCompletelyVisiblePosition = it.findFirstCompletelyVisibleItemPosition()
                            val totalItemCount = chatAdapter.itemCount
                            
                            Log.d("checkPagiantion", "🔄 Scrolled up - First visible: $firstVisiblePosition, First completely visible: $firstCompletelyVisiblePosition, Total items: $totalItemCount")
                            
                            // If first visible item is within first 3 items (position 0, 1, or 2), load more
                            // This means user has scrolled up and can see the oldest messages
                            // Handle NO_POSITION (-1) case - only load if position is valid
                            if (firstVisiblePosition >= 0 && firstVisiblePosition <= 2) {
                                Log.d("checkPagiantion", "🔄 Scrolled to top detected - First visible position: $firstVisiblePosition")
                                Log.d("checkPagiantion", "🔄 Current messages count: ${messages.size}, isLoadingMore: $isLoadingMoreMessages, hasMore: $hasMoreMessages")
                                loadMoreMessages()
                            }
                        }
                    }
                }
            })

            Log.d("checkPagiantion", "📋 Scroll listener added successfully")

            // When keyboard opens, ensure we keep the latest message visible
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
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
            Toast.makeText(this, "Error: Invalid user data", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupFirestoreListener() {
        Log.d("ChatActivity", "Setting up Firestore listener for threadId: $threadId")
        Log.d("checkPagiantion", "🚀 SETTING UP FIRESTORE LISTENER")
        Log.d("checkPagiantion", "🚀 Thread ID: $threadId")
        Log.d("checkPagiantion", "🚀 Messages per page: $MESSAGES_PER_PAGE")

        // Remove previous listener if exists
        messagesListenerRegistration?.remove()

        // Reset pagination variables
        oldestMessageTimestamp = null
        hasMoreMessages = true
        isLoadingMoreMessages = false

        Log.d("checkPagiantion", "🚀 Pagination reset - oldestTimestamp: null, hasMore: true, isLoading: false")

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
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
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
                    
                    if (isInitialLoad) {
                        messages.clear()
                    }

                    if (snapshot.isEmpty) {
                        Log.d("ChatActivity", "No messages in thread yet")
                        hasMoreMessages = false
                        chatAdapter.notifyDataSetChanged()
                        isLoadingMoreMessages = false
                        return@addSnapshotListener
                    }

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

                        val messageDate = timestamp?.toDate() ?: Date()
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
                    
                    // Handle initial load vs real-time updates
                    if (isInitialLoad) {
                        // Initial load - replace all messages
                        messages.addAll(messagesWithHeaders)
                        Log.d("checkPagiantion", "📥 INITIAL LOAD COMPLETE")
                        Log.d("checkPagiantion", "📥 Messages loaded: ${messagesWithHeaders.size} (with date headers)")
                        Log.d("checkPagiantion", "📥 Actual messages (without headers): ${tempMessages.size}")
                        Log.d("checkPagiantion", "📥 Total messages now in list: ${messages.size}")
                        Log.d("checkPagiantion", "📥 Oldest message timestamp: $oldestMessageTimestamp")
                        Log.d("checkPagiantion", "📥 Has more messages: $hasMoreMessages")
                        Log.d("ChatActivity", "✅ Loaded ${messages.size} messages (initial load), notifying adapter")
                        chatAdapter.notifyDataSetChanged()

                        // Scroll to bottom
                        if (messages.isNotEmpty()) {
                            rvMessages.scrollToPosition(messages.size - 1)
                        }
                    } else {
                        // Real-time update - add only new messages (newer than what we have)
                        val existingIds = messages.map { it.id }.toSet()
                        val newMessages = messagesWithHeaders.filter { !existingIds.contains(it.id) }
                        
                        if (newMessages.isNotEmpty()) {
                            // Append new messages to the end
                            messages.addAll(newMessages)
                            chatAdapter.notifyDataSetChanged()
                            
                            // Auto-scroll to bottom if user is at bottom
                            val layoutManager = rvMessages.layoutManager as? LinearLayoutManager
                            val lastVisiblePosition = layoutManager?.findLastVisibleItemPosition() ?: 0
                            val totalItemCount = messages.size
                            
                            // If user is near bottom (within last 3 items), auto-scroll
                            if ((totalItemCount.toLong() - lastVisiblePosition.toLong()) <= 3L) {
                                rvMessages.scrollToPosition(messages.size - 1)
                            }
                            
                            Log.d("checkPagiantion", "📨 REAL-TIME UPDATE: Added ${newMessages.size} new messages. Total: ${messages.size}")
                            Log.d("ChatActivity", "✅ Added ${newMessages.size} new messages. Total: ${messages.size}")
                        }
                    }

                    // Collect unread messages from snapshot
                    collectUnreadMessages(snapshot)

                    // Mark messages as read only if chat is visible
                    if (isChatVisible) {
                        markPendingMessagesAsRead()
                    }
                    
                    isLoadingMoreMessages = false
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

                    val messageDate = timestamp?.toDate() ?: Date()
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
                    // Save current scroll position
                    val layoutManager = rvMessages.layoutManager as? LinearLayoutManager
                    val currentScrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
                    val currentView = layoutManager?.findViewByPosition(currentScrollPosition)
                    val currentOffset = currentView?.top ?: 0

                    Log.d("checkPagiantion", "⬆️ Scroll position before prepend: position=$currentScrollPosition, offset=$currentOffset")

                    // Prepend older messages
                    messages.addAll(0, newMessages)
                    chatAdapter.notifyDataSetChanged()

                    Log.d("checkPagiantion", "✅ LOAD MORE COMPLETE")
                    Log.d("checkPagiantion", "✅ Added ${newMessages.size} older messages")
                    Log.d("checkPagiantion", "✅ Total messages now: ${messages.size} (was ${messages.size - newMessages.size})")
                    Log.d("checkPagiantion", "✅ New oldest timestamp: $oldestMessageTimestamp")
                    Log.d("checkPagiantion", "✅ Has more messages: $hasMoreMessages")

                    // Restore scroll position
                    rvMessages.post {
                        if (newMessages.size > 0) {
                            layoutManager?.scrollToPositionWithOffset(currentScrollPosition + newMessages.size, currentOffset)
                            Log.d("checkPagiantion", "⬆️ Scroll position restored to: ${currentScrollPosition + newMessages.size}")
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

    private fun markPendingMessagesAsRead() {
        // Mark all pending messages as read
        if (pendingMessagesToMarkRead.isEmpty()) {
            Log.d("ChatActivity", "No pending messages to mark as read")
            return
        }

        Log.d("ChatActivity", "Marking ${pendingMessagesToMarkRead.size} messages as read")

        pendingMessagesToMarkRead.forEach { messageId ->
            db.collection("chats")
                .document(threadId)
                .collection("messages")
                .document(messageId)
                .update("isRead", true)
                .addOnSuccessListener {
                    Log.d("ChatActivity", "✅ Marked message $messageId as read")
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "❌ Failed to mark message $messageId as read", e)
                }
        }

        // Clear pending list after marking
        pendingMessagesToMarkRead.clear()
    }

    private fun setupClickListeners() {
        ivBack.setOnClickListener {
            finish()
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
        if (messageText.isNotEmpty()) {
            // ✅ CHECK INTERNET FIRST
            if (!isInternetAvailable()) {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
                return  // ❌ DO NOT clear message, user can try again later
            }

            etMessage.setText("")

            // Validate user IDs before sending
            if (myUserId.isEmpty() || peerUserId.isEmpty() || peerUserId == "-1") {
                Log.e("ChatActivity", "Invalid user IDs - myUserId: $myUserId, peerUserId: $peerUserId")
                Toast.makeText(this, "Error: Invalid user data", Toast.LENGTH_SHORT).show()
                return
            }

            // Check if I have blocked this user
            if (isPeerBlocked) {
                Toast.makeText(this, "Please unblock to send message", Toast.LENGTH_SHORT).show()
                return
            }

            // Check if peer has blocked me before sending
            checkIfPeerBlockedMeAndSendMessage(messageText)
        }
    }

    private fun checkIfPeerBlockedMeAndSendMessage(messageText: String) {
        // Check if the PEER has blocked ME (sender)
        // If they blocked me, we still send the message (appears in MY chat)
        // but they won't see it (their listener filters it) and won't get notification
        db.collection("blocked_users")
            .document(peerUserId)  // Check PEER's blocked list
            .collection("users")
            .document(myUserId)    // Is SENDER (ME) in it?
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Peer has blocked me - but we still send the message
                    // They just won't see it or get notified
                    Log.d("ChatActivity", "⚠️ Peer has blocked me - Sending anyway (they won't see it)")
                    sendMessageToFirestore(messageText)
                } else {
                    // Not blocked - proceed with sending normally
                    sendMessageToFirestore(messageText)
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Failed to check if peer blocked me", e)
                // Proceed anyway - try to send
                Toast.makeText(this, "Error checking status, sending anyway", Toast.LENGTH_SHORT).show()
                sendMessageToFirestore(messageText)
            }
    }

    private fun sendMessageToFirestore(messageText: String) {
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
                        //   Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()

                        // Scroll to bottom
                        rvMessages.postDelayed({
                            if (messages.isNotEmpty()) {
                                rvMessages.scrollToPosition(messages.size - 1)
                            }
                        }, 100)

                        // Check if receiver has blocked sender before sending notification
                        checkIfReceiverBlockedMeAndSendNotification(messageText)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatActivity", "❌ Error sending message", e)
                        Log.e("ChatActivity", "Error type: ${e.javaClass.simpleName}")
                        Log.e("ChatActivity", "Error message: ${e.message}")
                        Log.e("ChatActivity", "Error cause: ${e.cause}")

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
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Error creating thread metadata", e)
                Toast.makeText(this, "Failed to create chat thread", Toast.LENGTH_SHORT).show()
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

    private fun checkIfReceiverIsViewingChatAndSendNotification(messageText: String) {
        // Give a tiny delay to allow receiver’s "active" status to sync
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


    private fun clearMyActiveChatStatus() {
        // Update last seen timestamp instead of deleting
        val lastSeenTimestamp = System.currentTimeMillis()
        val lastSeenData = hashMapOf(
            "lastSeen" to lastSeenTimestamp,
            "threadId" to ""  // Clear active thread to indicate user left
        )

        val date = java.util.Date(lastSeenTimestamp)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        Log.d("lastseenlog", "💾 Saving MY last seen for user $myUserId: ${format.format(date)} ($lastSeenTimestamp)")

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

    private fun checkLastMessageReadStatusAndSendNotification(messageText: String) {
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
                sendMessageNotificationApi(messageText)
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
        // Clean up active chat status
        clearMyActiveChatStatus()
        // Remove Firestore listener
        messagesListenerRegistration?.remove()
        Log.d("ChatActivity", "📱 Chat destroyed - Cleaned up presence tracking")
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
                Toast.makeText(this, "User blocked successfully", Toast.LENGTH_SHORT).show()
                Log.d("ChatActivity", "✅ User blocked successfully")

                // Refresh Firestore listener to filter blocked user's messages
                setupFirestoreListener()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to block user", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "User unblocked successfully", Toast.LENGTH_SHORT).show()
                Log.d("ChatActivity", "✅ User unblocked successfully")

                // Refresh Firestore listener to show all messages
                setupFirestoreListener()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to unblock user", Toast.LENGTH_SHORT).show()
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
                    if (lastUpdated != null) {
                        val date = java.util.Date(lastUpdated)
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        Log.d("ChatActivity", "📅 lastUpdated (readable): ${format.format(date)}")
                    }
                    if (lastSeen != null) {
                        val date = java.util.Date(lastSeen)
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        Log.d("ChatActivity", "📅 lastSeen (readable): ${format.format(date)}")
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
                        val date = java.util.Date(timestamp)
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        Log.d("lastseenlog", "User $peerUserId last seen: ${format.format(date)} ($diffMinutes min ago)")

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
     */
    private fun updateOnlineStatusUI(lastSeenMillis: Long) {
        val now = System.currentTimeMillis()
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
     * - "24 Dec 2025, 10:30 AM" for older messages
     */
    private fun formatMessageTimestamp(date: Date): String {
        val calendar = Calendar.getInstance()
        val messageCalendar = Calendar.getInstance().apply { time = date }
        
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val timeString = timeFormat.format(date)
        
        // Check if message is from today
        val isToday = calendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)
        
        if (isToday) {
            return "Today, $timeString"
        }
        
        // Check if message is from yesterday
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = calendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)
        
        if (isYesterday) {
            return "Yesterday, $timeString"
        }
        
        // For older messages, show date like "24 Dec 2025, 10:30 AM"
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateString = dateFormat.format(date)
        
        return "$dateString, $timeString"
    }

    /**
     * Format message time only (for display in message bubble)
     * Returns: "10:30 AM" or "2:45 PM"
     */
    private fun formatMessageTime(date: Date): String {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return timeFormat.format(date)
    }

    /**
     * Get date header text for a given date
     * Returns: "Today", "Yesterday", or "24 Dec 2024"
     */
    private fun getDateHeaderText(date: Date): String {
        val calendar = Calendar.getInstance()
        val messageCalendar = Calendar.getInstance().apply { time = date }
        
        // Check if message is from today
        val isToday = calendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)
        
        if (isToday) {
            return "Today"
        }
        
        // Check if message is from yesterday
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = calendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)
        
        if (isYesterday) {
            return "Yesterday"
        }
        
        // For older messages, show date like "24 Dec 2024"
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return dateFormat.format(date)
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
            val messageDate = message.date ?: Date()
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



