package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.ChatListAdapter
import com.gmwapp.hima.models.ChatConversation
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ChatListActivity : AppCompatActivity() {

    private lateinit var ivBack: ImageView
    private lateinit var tvChatCount: TextView
    private lateinit var rvChatList: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var llEmptyState: LinearLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var chatListAdapter: ChatListAdapter
    private val conversations = ArrayList<ChatConversation>()
    private val db by lazy { Firebase.firestore }
    private var myUserId: String = ""
    
    // Track conversations by threadId for real-time updates
    private val conversationsMap = mutableMapOf<String, ChatConversation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        initializeViews()
        setupUserId()
        setupRecyclerView()
        setupClickListeners()
        loadConversations()
        onBackPressedBtn()

    }

    private fun onBackPressedBtn() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0

                if (messageCameWhenIsAlive == 0) {
                    val intent = Intent(this@ChatListActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    finish()
                }
            }
        })
    }


    private fun initializeViews() {
        ivBack = findViewById(R.id.iv_back)
        tvChatCount = findViewById(R.id.tv_chat_count)
        rvChatList = findViewById(R.id.rv_chat_list)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        llEmptyState = findViewById(R.id.ll_empty_state)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupUserId() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        myUserId = userData?.id?.toString() ?: ""

        if (myUserId.isEmpty()) {
            Log.e("ChatListActivity", "User ID is empty!")
            showEmptyState()
            progressBar.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        chatListAdapter = ChatListAdapter(this, conversations) { conversation ->
            // Open ChatActivity when conversation is clicked
            val intent = Intent(this, ChatActivity::class.java)
            // Convert userId back to Int for ChatActivity
            val userId = conversation.userId.toIntOrNull() ?: -1
            intent.putExtra("USER_ID", userId)
            intent.putExtra("USER_NAME", conversation.userName)
            intent.putExtra("USER_IMAGE", conversation.userImage)
            startActivity(intent)
        }

        rvChatList.apply {
            layoutManager = LinearLayoutManager(this@ChatListActivity)
            adapter = chatListAdapter
        }
    }

    private fun setupClickListeners() {
        ivBack.setOnClickListener {

            val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0

            if (messageCameWhenIsAlive == 0) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            } else {
                finish()
            }
            }

        swipeRefreshLayout.setOnRefreshListener {
            loadConversations()
        }
    }

    private fun loadConversations() {
        if (myUserId.isEmpty()) {
            swipeRefreshLayout.isRefreshing = false
            return
        }

        Log.d("ChatListActivity", "Loading conversations for user: $myUserId")
        progressBar.visibility = View.VISIBLE
        llEmptyState.visibility = View.GONE

        // ✅ FIXED: Changed from .get() to .addSnapshotListener() for real-time updates
        db.collection("chats")
            .addSnapshotListener { documents, error ->
                swipeRefreshLayout.isRefreshing = false
                progressBar.visibility = View.GONE

                if (error != null) {
                    Log.e("ChatListActivity", "Error listening for chat threads: ${error.message}", error)
                    showEmptyState()
                    return@addSnapshotListener
                }

                if (documents == null || documents.isEmpty) {
                    Log.d("ChatListActivity", "No chat threads found.")
                    updateUI(emptyList())
                    return@addSnapshotListener
                }
                Log.d("ChatListActivity", "Found ${documents.size()} chat documents")
                
                // Clear old conversations that are no longer in the list
                val currentThreadIds = documents.map { it.id }.toSet()
                conversationsMap.keys.retainAll(currentThreadIds)

                documents.forEach { document ->
                    val threadId = document.id
                    
                    // Check if this thread involves the current user
                    val userIds = threadId.split("_")
                    if (userIds.contains(myUserId)) {
                        // Get the other user's ID
                        val otherUserId = userIds.firstOrNull { it != myUserId } ?: ""
                        
                            // Check if user is blocked first
                            db.collection("blocked_users")
                                .document(myUserId)
                                .collection("users")
                                .document(otherUserId)
                                .get()
                                .addOnSuccessListener { blockDoc ->
                                    val blockTimestamp = blockDoc.getTimestamp("blockedAt")
                                    
                                    // Get user metadata
                                    db.collection("chats")
                                        .document(threadId)
                                        .get()
                                        .addOnSuccessListener { threadDoc ->
                                            val userName = threadDoc.getString("user_${otherUserId}_name") ?: "User $otherUserId"
                                            val userImage = threadDoc.getString("user_${otherUserId}_image") ?: ""
                                            
                                            // Real-time listener for messages in this thread
                                            db.collection("chats")
                                                .document(threadId)
                                                .collection("messages")
                                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                                .limit(50)
                                                .addSnapshotListener { messagesSnapshot, messageError ->
                                                    if (messageError != null || messagesSnapshot == null) {
                                                        Log.e("ChatListActivity", "Error listening to messages in $threadId", messageError)
                                                        return@addSnapshotListener
                                                    }

                                                    var lastMessage = ""
                                                    var lastMessageTime: Timestamp? = null
                                                    var unreadCount = 0

                                                    if (!messagesSnapshot.isEmpty) {
                                                        // Filter messages sent after block timestamp
                                                        val validMessages = if (blockTimestamp != null) {
                                                            messagesSnapshot.documents.filter { msg ->
                                                                val msgTimestamp = msg.getTimestamp("timestamp")
                                                                val fromId = msg.getString("from") ?: ""
                                                                // Include own messages and messages from blocked user sent BEFORE blocking
                                                                fromId != otherUserId || msgTimestamp == null || msgTimestamp.seconds < blockTimestamp.seconds
                                                            }
                                                        } else {
                                                            messagesSnapshot.documents
                                                        }
                                                        
                                                        if (validMessages.isNotEmpty()) {
                                                            // Get last valid message
                                                            val lastMsgDoc = validMessages[0]
                                                            lastMessage = lastMsgDoc.getString("text") ?: ""
                                                            lastMessageTime = lastMsgDoc.getTimestamp("timestamp")

                                                            // Count unread messages from other user (only those sent before block)
                                                            validMessages.forEach { msg ->
                                                                val fromId = msg.getString("from") ?: ""
                                                                val isRead = msg.getBoolean("isRead") ?: false
                                                                val msgTimestamp = msg.getTimestamp("timestamp")
                                                                
                                                                if (fromId == otherUserId && !isRead) {
                                                                    if (blockTimestamp == null || msgTimestamp == null || msgTimestamp.seconds < blockTimestamp.seconds) {
                                                                        unreadCount++
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Log.d("ChatListActivity", "Thread $threadId - Last: '$lastMessage', Unread: $unreadCount")

                                                    // Create/update conversation
                                                    val conversation = ChatConversation(
                                                        threadId = threadId,
                                                        userId = otherUserId,
                                                        userName = userName,
                                                        userImage = userImage,
                                                        lastMessage = lastMessage,
                                                        lastMessageTime = lastMessageTime,
                                                        unreadCount = unreadCount,
                                                        isOnline = false
                                                    )

                                                    conversationsMap[threadId] = conversation
                                                    
                                                    // Update UI with sorted list
                                                    val sortedConversations = conversationsMap.values
                                                        .sortedByDescending { it.lastMessageTime?.toDate()?.time ?: 0L }
                                                    updateUI(sortedConversations)
                                                }
                                        }
                                }
                            .addOnFailureListener { e ->
                                Log.e("ChatListActivity", "Error loading thread metadata for $threadId", e)
                            }
                    }
                }
            }
    }


    private fun updateUI(conversationsList: List<ChatConversation>) {
        swipeRefreshLayout.isRefreshing = false
        progressBar.visibility = View.GONE

        if (conversationsList.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
            chatListAdapter.updateConversations(conversationsList)
            
            // Update conversation count
            val totalUnread = conversationsList.sumOf { it.unreadCount }
            tvChatCount.text = if (totalUnread > 0) {
                "${conversationsList.size} conversations • $totalUnread unread"
            } else {
                "${conversationsList.size} conversations"
            }
        }
    }

    private fun showEmptyState() {
        rvChatList.visibility = View.GONE
        llEmptyState.visibility = View.VISIBLE
        tvChatCount.text = "No conversations yet"
    }

    private fun hideEmptyState() {
        rvChatList.visibility = View.VISIBLE
        llEmptyState.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        // Refresh conversations when returning to this screen
        loadConversations()
    }
}

