package com.gmwapp.hima.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var ivBack: ImageView
    private lateinit var ivMore: ImageView
    private lateinit var ivUser: CircleImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserStatus: TextView
    
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    
    // Firestore instance
    private val db by lazy { Firebase.firestore }
    
    // User IDs and thread ID
    private var myUserId: String = ""
    private var peerUserId: String = ""
    private var threadId: String = ""
    
    // Track if chat is visible to user
    private var isChatVisible = false
    private val pendingMessagesToMarkRead = mutableSetOf<String>()
    
    // Track if peer user is blocked
    private var isPeerBlocked = false
    private var blockTimestamp: com.google.firebase.Timestamp? = null
    
    // Listener registration to properly remove when needed
    private var messagesListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        initializeViews()
        setupRecyclerView()
        setupUserIds()
        setupClickListeners()
        // Load block status FIRST, then setup listener
        checkIfUserIsBlockedAndSetupListener()
    }

    private fun initializeViews() {
        rvMessages = findViewById(R.id.rv_messages)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        ivBack = findViewById(R.id.iv_back)
        ivMore = findViewById(R.id.iv_more)
        ivUser = findViewById(R.id.iv_user)
        tvUserName = findViewById(R.id.tv_user_name)
        tvUserStatus = findViewById(R.id.tv_user_status)
        
        // Set user data from intent
        val userName = intent.getStringExtra("USER_NAME") ?: "User"
        val userImage = intent.getStringExtra("USER_IMAGE")
        
        tvUserName.text = userName
        tvUserStatus.text = "Online"
        
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
        
        // Store user metadata in thread document for chat list
        storeUserMetadata()
    }
    
    private fun storeUserMetadata() {
        val myName = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.name ?: ""
        val myImage = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.image ?: ""
        val peerName = intent.getStringExtra("USER_NAME") ?: ""
        val peerImage = intent.getStringExtra("USER_IMAGE") ?: ""
        
        // Store both users' metadata in the thread document
        val metadata = hashMapOf(
            "user_${myUserId}_name" to myName,
            "user_${myUserId}_image" to myImage,
            "user_${peerUserId}_name" to peerName,
            "user_${peerUserId}_image" to peerImage,
            "lastUpdated" to FieldValue.serverTimestamp()
        )
        
        db.collection("chats")
            .document(threadId)
            .set(metadata, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("ChatActivity", "✅ User metadata stored successfully")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Failed to store user metadata", e)
            }
    }
    
    private fun setupFirestoreListener() {
        // Remove existing listener if any
        messagesListenerRegistration?.remove()
        
        Log.d("ChatActivity", "Setting up Firestore listener for threadId: $threadId")
        Log.d("ChatActivity", "Block status - isPeerBlocked: $isPeerBlocked, blockTimestamp: $blockTimestamp")
        
        messagesListenerRegistration = db.collection("chats")
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
                        
                        // Skip NEW messages from blocked user (sent AFTER block timestamp)
                        if (isPeerBlocked && fromId == peerUserId && blockTimestamp != null && timestamp != null) {
                            if (timestamp.seconds >= blockTimestamp!!.seconds) {
                                Log.d("ChatActivity", "Skipping NEW message from blocked user sent at: $timestamp (blocked at: $blockTimestamp)")
                                continue
                            }
                        }
                        
                        Log.d("ChatActivity", "Message: from=$fromId, text=$text, timestamp=$timestamp")
                        
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
        
        ivMore.setOnClickListener { view ->
            showOptionsMenu(view)
        }
    }
    
    private fun showOptionsMenu(view: android.view.View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.menu_chat, popupMenu.menu)
        
        // Show/hide block/unblock based on current status
        popupMenu.menu.findItem(R.id.action_block)?.isVisible = !isPeerBlocked
        popupMenu.menu.findItem(R.id.action_unblock)?.isVisible = isPeerBlocked
        
        popupMenu.setOnMenuItemClickListener { menuItem ->
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
        
        popupMenu.show()
    }
    
    private fun showBlockConfirmationDialog() {
        val userName = intent.getStringExtra("USER_NAME") ?: "this user"
        
        // Inflate custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_block_confirmation, null)
        
        // Create dialog with custom view
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Make dialog background transparent to show rounded corners
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Update message with user name
        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        tvMessage.text = "Are you sure you want to block $userName?"
        
        // Setup button click listeners
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_block).setOnClickListener {
            dialog.dismiss()
            blockUser()
        }
        
        dialog.show()
    }
    
    private fun checkIfUserIsBlockedAndSetupListener() {
        db.collection("blocked_users")
            .document(myUserId)
            .collection("users")
            .document(peerUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    isPeerBlocked = true
                    blockTimestamp = document.getTimestamp("blockedAt")
                    Log.d("ChatActivity", "✅ User $peerUserId is blocked at: $blockTimestamp")
                } else {
                    isPeerBlocked = false
                    blockTimestamp = null
                    Log.d("ChatActivity", "✅ User $peerUserId is NOT blocked")
                }
                
                // NOW setup the listener after we know the block status
                setupFirestoreListener()
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ Failed to check block status", e)
                isPeerBlocked = false
                blockTimestamp = null
                
                // Still setup listener even if check failed
                setupFirestoreListener()
            }
    }
    
    private fun blockUser() {
        val currentTimestamp = com.google.firebase.Timestamp.now()
        
        db.collection("blocked_users")
            .document(myUserId)
            .collection("users")
            .document(peerUserId)
            .set(
                hashMapOf(
                    "blockedAt" to currentTimestamp,
                    "userName" to (intent.getStringExtra("USER_NAME") ?: ""),
                    "userImage" to (intent.getStringExtra("USER_IMAGE") ?: "")
                )
            )
            .addOnSuccessListener {
                isPeerBlocked = true
                blockTimestamp = currentTimestamp
                Toast.makeText(this, "User blocked successfully", Toast.LENGTH_SHORT).show()
                Log.d("ChatActivity", "✅ User $peerUserId blocked at $currentTimestamp")
                Log.d("ChatActivity", "Existing messages will remain visible. New messages will be blocked.")
                
                // DON'T remove existing messages - they stay visible
                // The listener will automatically filter NEW messages sent after this timestamp
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to block user", Toast.LENGTH_SHORT).show()
                Log.e("ChatActivity", "❌ Failed to block user", e)
            }
    }
    
    private fun unblockUser() {
        db.collection("blocked_users")
            .document(myUserId)
            .collection("users")
            .document(peerUserId)
            .delete()
            .addOnSuccessListener {
                isPeerBlocked = false
                blockTimestamp = null
                Toast.makeText(this, "User unblocked successfully", Toast.LENGTH_SHORT).show()
                Log.d("ChatActivity", "✅ User $peerUserId unblocked")
                
                // Refresh listener to show all messages again (including those sent during block)
                setupFirestoreListener()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to unblock user", Toast.LENGTH_SHORT).show()
                Log.e("ChatActivity", "❌ Failed to unblock user", e)
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
            
            // Check if YOU have blocked THEM before sending
            db.collection("blocked_users")
                .document(myUserId)  // Check YOUR blocked list
                .collection("users")
                .document(peerUserId)  // Is PEER in it?
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        // You have blocked this user - don't let you send message
                        Log.d("ChatActivity", "⚠️ Cannot send - You have blocked $peerUserId")
                        Toast.makeText(this, "Cannot send message to blocked user", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    
                    // You haven't blocked them - proceed to send message
                    // (Even if they blocked you, message will send but they won't see it)
                    sendMessageToFirestore(messageText)
                }
                .addOnFailureListener {
                    // If check fails, still try to send (better UX)
                    sendMessageToFirestore(messageText)
                }
        }
    }
    
    private fun sendMessageToFirestore(messageText: String) {
        // Create message data for Firestore
        val messageData = hashMapOf(
            "from" to myUserId,
            "to" to peerUserId,
            "text" to messageText,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false
        )
        
        Log.d("ChatActivity", "Attempting to send message to threadId: $threadId")
        Log.d("ChatActivity", "Message data: $messageData")
        
        // Send to Firestore
        db.collection("chats")
            .document(threadId)
            .collection("messages")
            .add(messageData)
            .addOnSuccessListener { documentReference ->
                Log.d("ChatActivity", "✅ Message sent successfully: ${documentReference.id}")
                Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
                // Scroll to bottom
                rvMessages.postDelayed({
                    if (messages.isNotEmpty()) {
                        rvMessages.scrollToPosition(messages.size - 1)
                    }
                }, 100)
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

    override fun onResume() {
        super.onResume()
        // User is now viewing the chat - mark it as visible
        isChatVisible = true
        Log.d("ChatActivity", "Chat is now visible - marking pending messages as read")
        // Mark any pending messages as read
        markPendingMessagesAsRead()
    }

    override fun onPause() {
        super.onPause()
        // User is no longer viewing the chat
        isChatVisible = false
        Log.d("ChatActivity", "Chat is no longer visible")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up listener when activity is destroyed
        messagesListenerRegistration?.remove()
    }

}



