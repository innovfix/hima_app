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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*
import android.widget.PopupMenu
import com.google.firebase.Timestamp
import android.view.View

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
    
    // Firestore instance
    private val db by lazy { Firebase.firestore }
    
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        chatAdapter = ChatAdapter(messages)
        rvMessages.apply {
            val layoutManager = LinearLayoutManager(this@ChatActivity)
            // Anchor messages to the bottom so empty space appears above messages
            layoutManager.stackFromEnd = true
            layoutManager.reverseLayout = false
            this.layoutManager = layoutManager
            adapter = chatAdapter

            // When keyboard opens, ensure we keep the latest message visible
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (oldBottom != 0 && bottom < oldBottom) {
                    post {
                        if (chatAdapter.itemCount > 0) scrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            }
        }
    }

    private fun setupUserIds() {
        // Get logged-in user ID
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        myUserId = userData?.id?.toString() ?: ""
        
        // Get peer user ID from intent
        peerUserId = intent.getIntExtra("USER_ID", -1).toString()
        
        // Generate unique thread ID (sorted to ensure same ID regardless of who initiates)
        threadId = listOf(myUserId, peerUserId).sorted().joinToString("_")
        
        Log.d("ChatActivity", "MyUserId: $myUserId, PeerUserId: $peerUserId, ThreadId: $threadId")
        
        if (myUserId.isEmpty() || peerUserId == "-1") {
            Toast.makeText(this, "Error: Invalid user data", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun setupFirestoreListener() {
        Log.d("ChatActivity", "Setting up Firestore listener for threadId: $threadId")
        
        db.collection("chats")
            .document(threadId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatActivity", "❌ Listen failed: ${error.message}", error)
                    
                    // Show error to user
                    val errorMsg = when {
                        error.message?.contains("index") == true -> {
                            "Creating index... Please wait 1-2 minutes and restart app"
                        }
                        else -> "Error loading messages: ${error.message}"
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                
                Log.d("ChatActivity", "Snapshot received - isEmpty: ${snapshot?.isEmpty}, docs: ${snapshot?.documents?.size}")
                
                if (snapshot != null) {
                    messages.clear()
                    
                    if (snapshot.isEmpty) {
                        Log.d("ChatActivity", "No messages in thread yet")
                        chatAdapter.notifyDataSetChanged()
                        return@addSnapshotListener
                    }
                    
                    for (doc in snapshot.documents) {
                        val fromId = doc.getString("from") ?: ""
                        val text = doc.getString("text") ?: ""
                        val timestamp = doc.getTimestamp("timestamp")
                        
                        Log.d("ChatActivity", "Message: from=$fromId, text=$text, timestamp=$timestamp")
                        
                        // Filter messages if peer is blocked
                        if (isPeerBlocked && fromId == peerUserId && blockTimestamp != null && timestamp != null) {
                            if (timestamp.seconds >= blockTimestamp!!.seconds) {
                                Log.d("ChatActivity", "🚫 Filtering blocked message: sent at ${timestamp.seconds}, blocked at ${blockTimestamp!!.seconds}")
                                continue  // Skip messages sent after blocking
                            }
                        }
                        
                        val timeString = if (timestamp != null) {
                            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(timestamp.toDate())
                        } else {
                            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                        }
                        
                        val message = ChatMessage(
                            id = doc.id,
                            message = text,
                            timestamp = timeString,
                            isSentByMe = fromId == myUserId
                        )
                        
                        messages.add(message)
                    }
                    
                    Log.d("ChatActivity", "✅ Loaded ${messages.size} messages, notifying adapter")
                    chatAdapter.notifyDataSetChanged()
                    
                    // Scroll to bottom
                    if (messages.isNotEmpty()) {
                        rvMessages.scrollToPosition(messages.size - 1)
                    }
                    
                    // Collect unread messages from peer
                    collectUnreadMessages(snapshot)
                    
                    // Mark messages as read only if chat is visible
                    if (isChatVisible) {
                        markPendingMessagesAsRead()
                    }
                } else {
                    Log.e("ChatActivity", "Snapshot is null")
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
            .update(activeChatData)
            .addOnSuccessListener {
                Log.d("ChatActivity", "✅ Marked as actively viewing chat: $threadId")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Failed to set active chat status", e)
            }
    }

    private fun startActiveChatHeartbeat() {
        // Refresh every 5 seconds to keep "active" status fresh
        val handler = android.os.Handler(mainLooper)
        val runnable = object : Runnable {
            override fun run() {
                if (isChatVisible) {
                    db.collection("active_chats").document(myUserId)
                        .update("lastUpdated", System.currentTimeMillis())
                        .addOnFailureListener { e ->
                            Log.e("ChatActivity", "⚠️ Failed to refresh active chat heartbeat", e)
                        }
                    handler.postDelayed(this, 5000)
                }
            }
        }
        handler.postDelayed(runnable, 5000)
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
        Log.d("ChatActivity", "📱 Chat destroyed - Cleaned up presence tracking")
    }

    // ==================== BLOCK/UNBLOCK FUNCTIONS ====================
    
    private fun checkIfUserIsBlocked() {
        // Check if I have blocked this peer user
        db.collection("blocked_users")
            .document(myUserId)
            .collection("users")
            .document(peerUserId)
            .get()
            .addOnSuccessListener { doc ->
                isPeerBlocked = doc.exists()
                blockTimestamp = if (isPeerBlocked) doc.getTimestamp("blockedAt") else null
                Log.d("ChatActivity", "Block status loaded: isPeerBlocked=$isPeerBlocked, blockTimestamp=$blockTimestamp")
                
                // Now setup firestore listener with correct block status
                setupFirestoreListener()
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Failed to check block status", e)
                // Proceed anyway with isPeerBlocked = false
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

}



