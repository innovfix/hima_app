package com.gmwapp.hima.activities

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        initializeViews()
        setupRecyclerView()
        setupUserIds()
        setupFirestoreListener()
        setupClickListeners()
    }

    private fun initializeViews() {
        rvMessages = findViewById(R.id.rv_messages)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        ivBack = findViewById(R.id.iv_back)
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
                } else {
                    Log.e("ChatActivity", "Snapshot is null")
                }
            }
    }

    private fun setupClickListeners() {
        ivBack.setOnClickListener {
            finish()
        }

        btnSend.setOnClickListener {
            sendMessage()
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
            
            // Create message data for Firestore
            val messageData = hashMapOf(
                "from" to myUserId,
                "to" to peerUserId,
                "text" to messageText,
                "timestamp" to FieldValue.serverTimestamp()
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
    }

}



