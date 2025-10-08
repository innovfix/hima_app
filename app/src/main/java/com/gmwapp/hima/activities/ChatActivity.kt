package com.gmwapp.hima.activities

import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.ChatAdapter
import com.gmwapp.hima.models.ChatMessage
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        // Ensure the window resizes when the keyboard appears so input and messages stay visible
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        // Apply window insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Apply top inset to root so header doesn't collide with status bar
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

            // Combine nav bar + IME bottom inset so input container sits above both
            val bottomInset = systemBars.bottom + imeInsets.bottom

            val inputContainer = findViewById<LinearLayout>(R.id.message_input_container)
            val rv = findViewById<RecyclerView>(R.id.rv_messages)

            inputContainer?.setPadding(
                inputContainer.paddingLeft,
                inputContainer.paddingTop,
                inputContainer.paddingRight,
                bottomInset
            )

            rv?.setPadding(
                rv.paddingLeft,
                rv.paddingTop,
                rv.paddingRight,
                rv.paddingBottom + bottomInset
            )

            // If keyboard (IME) is visible, ensure the latest message is visible
            if (imeInsets.bottom > 0) {
                rv?.post {
                    val count = rv.adapter?.itemCount ?: 0
                    if (count > 0) rv.scrollToPosition(count - 1)
                }
            }

            insets
        }
        
        initializeViews()
        setupRecyclerView()
        loadSampleMessages()
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
        
        // Set user data from intent or use defaults
        val userName = intent.getStringExtra("user_name") ?: "Sarah Johnson"
        val userStatus = intent.getStringExtra("user_status") ?: "Online"
        
        tvUserName.text = userName
        tvUserStatus.text = userStatus
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

    private fun loadSampleMessages() {
        // Add sample conversation to demonstrate the design
        val sampleMessages = listOf(
            ChatMessage("1", "Hey! How are you doing?", "10:30 AM", false),
            ChatMessage("2", "I'm doing great! Thanks for asking 😊", "10:31 AM", true),
            ChatMessage("3", "That's wonderful to hear!", "10:31 AM", false),
            ChatMessage("4", "Are you free for a call later?", "10:32 AM", false),
            ChatMessage("5", "Yes, absolutely! What time works for you?", "10:32 AM", true),
            ChatMessage("6", "How about 3 PM?", "10:33 AM", false),
            ChatMessage("7", "Perfect! I'll be ready at 3 PM. Looking forward to it! 🎉", "10:33 AM", true)
        )
        
        messages.addAll(sampleMessages)
        chatAdapter.notifyDataSetChanged()
        
        // Scroll to bottom
        rvMessages.scrollToPosition(messages.size - 1)
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
            val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val newMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                message = messageText,
                timestamp = currentTime,
                isSentByMe = true
            )
            
            chatAdapter.addMessage(newMessage)
            etMessage.setText("")
            
            // Scroll to bottom
            rvMessages.scrollToPosition(messages.size - 1)
            
            // Simulate a reply after 2 seconds (for demo purposes)
            rvMessages.postDelayed({
                simulateReply()
            }, 2000)
        }
    }

    private fun simulateReply() {
        val replies = listOf(
            "That sounds great!",
            "I agree with you!",
            "Tell me more about that.",
            "Interesting! 🤔",
            "Absolutely!",
            "I understand what you mean."
        )
        
        val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val reply = ChatMessage(
            id = UUID.randomUUID().toString(),
            message = replies.random(),
            timestamp = currentTime,
            isSentByMe = false
        )
        
        chatAdapter.addMessage(reply)
        rvMessages.scrollToPosition(messages.size - 1)
    }
}



