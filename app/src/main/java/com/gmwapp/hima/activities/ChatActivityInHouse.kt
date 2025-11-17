package com.gmwapp.hima.activities

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.ChatAdapter
import com.gmwapp.hima.models.ChatMessage
import com.gmwapp.hima.models.ChatMessageApi
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MarkReadResponse
import com.gmwapp.hima.retrofit.responses.MessageListResponse
import com.gmwapp.hima.retrofit.responses.SendMessageResponse
import retrofit2.Call
import retrofit2.Response
import com.gmwapp.hima.socket.ChatMessageSocket
import com.gmwapp.hima.socket.SocketManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ChatActivityInHouse : AppCompatActivity() {

    @Inject
    lateinit var apiManager: ApiManager

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var ivBack: ImageView
    private lateinit var ivUser: CircleImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserStatus: TextView
    private lateinit var vOnlineIndicator: View

    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    
    private val socketManager = SocketManager.getInstance()
    
    private var myUserId: Int = 0
    private var peerUserId: Int = 0
    private var chatId: String = ""
    
    private var isChatVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Track message sending method
    private val messageSendMethod = mutableMapOf<String, String>() // messageId -> "socket" or "api"
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Restrict screenshots
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        initializeViews()
        setupRecyclerView()
        setupUserIds()
        setupClickListeners()
        connectSocket()
        loadMessages()
        observeSocketEvents()
        
        // Log initial status
        mainHandler.postDelayed({
            logSocketIOStatus()
        }, 3000)
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

        val userName = intent.getStringExtra("USER_NAME") ?: "User"
        val userImage = intent.getStringExtra("USER_IMAGE")

        tvUserName.text = userName

        if (!userImage.isNullOrEmpty()) {
            Glide.with(this)
                .load(userImage)
                .apply(RequestOptions.circleCropTransform())
                .into(ivUser)
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.white)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivityInHouse).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = RecyclerView.FOCUS_BLOCK_DESCENDANTS
        }
    }

    private fun setupUserIds() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        myUserId = userData?.id ?: 0
        peerUserId = intent.getIntExtra("USER_ID", -1)

        // Generate chat ID: smaller_id_larger_id
        chatId = if (myUserId < peerUserId) {
            "${myUserId}_${peerUserId}"
        } else {
            "${peerUserId}_${myUserId}"
        }

        Log.d("ChatActivityInHouse", "MyUserId: $myUserId, PeerUserId: $peerUserId, ChatId: $chatId")

        if (myUserId == 0 || peerUserId == -1) {
            Toast.makeText(this, "Error: Invalid user data", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupClickListeners() {
        ivBack.setOnClickListener {
            onBackPressed()
        }

        btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun connectSocket() {
        // Socket.IO is already connected globally in BaseApplication
        // Just check the connection status and update UI
        val isConnected = socketManager.isConnected()
        Log.d("SocketIOCheck", "📊 Checking Socket.IO connection status: ${if (isConnected) "✅ CONNECTED" else "❌ DISCONNECTED"}")
        
        // If not connected, try to reconnect (in case connection was lost)
        if (!isConnected) {
            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            val userId = userData?.id
            if (userId != null && userId > 0) {
                Log.d("SocketIOCheck", "🔄 Attempting to reconnect Socket.IO with User ID: $userId")
                socketManager.connect(userId)
            } else {
                Log.e("SocketIOCheck", "❌ No user ID available - Cannot reconnect Socket.IO")
            }
        }
        
        // Join chat room when socket is connected
        if (isConnected && myUserId > 0 && peerUserId > 0) {
            socketManager.joinChatRoom(myUserId, peerUserId)
            Log.d("SocketIOCheck", "✅ Joined chat room: $chatId")
        }
        
        // Update UI after a short delay
        mainHandler.postDelayed({
            val connected = socketManager.isConnected()
            Log.d("SocketIOCheck", "Socket.IO connection status: ${if (connected) "✅ CONNECTED" else "❌ DISCONNECTED"}")
            updateSocketStatusUI(connected)
        }, 2000)
    }
    
    private fun updateSocketStatusUI(isConnected: Boolean) {
        mainHandler.post {
            if (isConnected) {
                tvUserStatus.text = "Online • Socket.IO Connected"
                tvUserStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                vOnlineIndicator.visibility = View.VISIBLE
            } else {
                tvUserStatus.text = "Socket.IO Disconnected"
                tvUserStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                vOnlineIndicator.visibility = View.GONE
            }
        }
    }

    private fun observeSocketEvents() {
        lifecycleScope.launch {
            socketManager.isConnected.collectLatest { connected ->
                if (connected) {
                    Log.d("SocketIOCheck", "✅ Socket.IO CONNECTED - joining chat room: $chatId")
                    updateSocketStatusUI(true)
                    socketManager.joinChat(chatId)
                } else {
                    Log.d("SocketIOCheck", "❌ Socket.IO DISCONNECTED")
                    updateSocketStatusUI(false)
                }
            }
        }

        lifecycleScope.launch {
            socketManager.newMessage.collectLatest { message ->
                message?.let {
                    if (it.chatId == chatId) {
                        handleNewMessage(it)
                    }
                }
            }
        }

        lifecycleScope.launch {
            socketManager.messageSent.collectLatest { message ->
                message?.let {
                    if (it.chatId == chatId) {
                        val messageId = it.id.toString()
                        messageSendMethod[messageId] = "socket"
                        Log.d("SocketIOCheck", "✅ Message sent via SOCKET.IO - ID: $messageId")
                        Toast.makeText(this@ChatActivityInHouse, "✅ Sent via Socket.IO", Toast.LENGTH_SHORT).show()
                        // Message already shown optimistically, just update status if needed
                        logSocketIOStatus() // Log updated status
                    }
                }
            }
        }

        lifecycleScope.launch {
            socketManager.messageError.collectLatest { error ->
                error?.let {
                    Toast.makeText(this@ChatActivityInHouse, it, Toast.LENGTH_SHORT).show()
                    Log.e("SocketIOCheck", "Message error: $it")
                }
            }
        }
    }

    private fun loadMessages() {
        apiManager.getMessages(
            userId = myUserId,
            chatId = chatId,
            page = 1,
            limit = 50,
            object : NetworkCallback<MessageListResponse> {
                override fun onResponse(call: Call<MessageListResponse>, response: Response<MessageListResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.status == true && responseBody.data != null) {
                            val apiMessages = responseBody.data.messages
                            val convertedMessages = apiMessages.map { apiMsg ->
                                convertApiMessageToChatMessage(apiMsg)
                            }
                            
                            messages.clear()
                            messages.addAll(convertedMessages)
                            chatAdapter.notifyDataSetChanged()
                            
                            if (messages.isNotEmpty()) {
                                rvMessages.scrollToPosition(messages.size - 1)
                            }
                            
                            // Mark messages as read
                            if (isChatVisible) {
                                markMessagesAsRead()
                            }
                        }
                    } else {
                        Log.e("ChatActivityInHouse", "Error loading messages: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<MessageListResponse>, t: Throwable) {
                    Log.e("ChatActivityInHouse", "Error loading messages: ${t.message}", t)
                }

                override fun onNoNetwork() {
                    Toast.makeText(this@ChatActivityInHouse, "No internet connection", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) {
            return
        }

        // Clear input immediately
        etMessage.setText("")
        etMessage.requestFocus()

        // Show message optimistically
        val tempMessage = ChatMessage(
            id = "temp_${System.currentTimeMillis()}",
            message = messageText,
            timestamp = timeFormat.format(Date()),
            isSentByMe = true,
            date = Date()
        )
        
        messages.add(tempMessage)
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)

        // Try Socket.IO first, fallback to API
        val tempMessageId = tempMessage.id
        if (socketManager.isConnected()) {
            messageSendMethod[tempMessageId] = "socket"
            // ⭐ Updated to use new signature: sendMessage(fromUserId, toUserId, message, messageType, attachmentUrl)
            socketManager.sendMessage(myUserId, peerUserId, messageText, "text")
            Log.d("SocketIOCheck", "🚀 Sending via SOCKET.IO - From: $myUserId, To: $peerUserId, Message: '$messageText'")
            Toast.makeText(this, "🚀 Sending via Socket.IO...", Toast.LENGTH_SHORT).show()
        } else {
            messageSendMethod[tempMessageId] = "api"
            Log.w("SocketIOCheck", "⚠️ Socket.IO NOT CONNECTED - Using API fallback")
            Toast.makeText(this, "⚠️ Socket.IO offline - Using API", Toast.LENGTH_SHORT).show()
            // Fallback to API
            sendMessageViaAPI(messageText)
        }
    }

    private fun sendMessageViaAPI(messageText: String) {
        apiManager.sendMessage(
            userId = myUserId,
            toUserId = peerUserId,
            message = messageText,
            messageType = "text",
            object : NetworkCallback<SendMessageResponse> {
                override fun onResponse(call: Call<SendMessageResponse>, response: Response<SendMessageResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.status == true && responseBody.data != null) {
                            // Replace temp message with real one
                            val realMessage = convertApiMessageToChatMessage(responseBody.data)
                            val tempIndex = messages.indexOfFirst { it.id.startsWith("temp_") && it.message == messageText }
                            if (tempIndex != -1) {
                                val tempId = messages[tempIndex].id
                                messages[tempIndex] = realMessage
                                messageSendMethod[realMessage.id] = "api"
                                chatAdapter.notifyItemChanged(tempIndex)
                            }
                            Log.d("SocketIOCheck", "✅ Message sent via API - ID: ${responseBody.data.id}")
                            Toast.makeText(this@ChatActivityInHouse, "✅ Sent via API", Toast.LENGTH_SHORT).show()
                            logSocketIOStatus() // Log updated status
                        }
                    } else {
                        // Remove temp message on error
                        val tempIndex = messages.indexOfFirst { it.id.startsWith("temp_") && it.message == messageText }
                        if (tempIndex != -1) {
                            messages.removeAt(tempIndex)
                            chatAdapter.notifyItemRemoved(tempIndex)
                        }
                        Toast.makeText(this@ChatActivityInHouse, "Failed to send message: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<SendMessageResponse>, t: Throwable) {
                    // Remove temp message on error
                    val tempIndex = messages.indexOfFirst { it.id.startsWith("temp_") && it.message == messageText }
                    if (tempIndex != -1) {
                        messages.removeAt(tempIndex)
                        chatAdapter.notifyItemRemoved(tempIndex)
                    }
                    Toast.makeText(this@ChatActivityInHouse, "Failed to send message: ${t.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onNoNetwork() {
                    val tempIndex = messages.indexOfFirst { it.id.startsWith("temp_") && it.message == messageText }
                    if (tempIndex != -1) {
                        messages.removeAt(tempIndex)
                        chatAdapter.notifyItemRemoved(tempIndex)
                    }
                    Toast.makeText(this@ChatActivityInHouse, "No internet connection", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun handleNewMessage(socketMessage: ChatMessageSocket) {
        // Check if message is from this chat
        if (socketMessage.chatId != chatId) {
            return
        }

        // Check if we already have this message
        val existingMessage = messages.find { 
            it.id == socketMessage.id.toString() || 
            (it.message == socketMessage.message && 
             it.isSentByMe == (socketMessage.fromUserId == myUserId))
        }

        if (existingMessage == null) {
            val chatMessage = convertSocketMessageToChatMessage(socketMessage)
            messages.add(chatMessage)
            chatAdapter.notifyItemInserted(messages.size - 1)
            rvMessages.scrollToPosition(messages.size - 1)

            // Mark as read if chat is visible
            if (isChatVisible) {
                markMessagesAsRead()
            }
        }
    }

    private fun convertApiMessageToChatMessage(apiMsg: ChatMessageApi): ChatMessage {
        val timestamp = parseTimestamp(apiMsg.timestamp)
        val isSentByMe = apiMsg.fromUserId == myUserId
        
        return ChatMessage(
            id = apiMsg.id.toString(),
            message = apiMsg.message,
            timestamp = timeFormat.format(timestamp),
            isSentByMe = isSentByMe,
            date = timestamp
        )
    }

    private fun convertSocketMessageToChatMessage(socketMsg: ChatMessageSocket): ChatMessage {
        val timestamp = parseTimestamp(socketMsg.timestamp)
        val isSentByMe = socketMsg.fromUserId == myUserId
        
        return ChatMessage(
            id = socketMsg.id.toString(),
            message = socketMsg.message,
            timestamp = timeFormat.format(timestamp),
            isSentByMe = isSentByMe,
            date = timestamp
        )
    }

    private fun parseTimestamp(timestampString: String): Date {
        return try {
            dateFormat.parse(timestampString) ?: Date()
        } catch (e: Exception) {
            Log.e("ChatActivityInHouse", "Error parsing timestamp: ${e.message}")
            Date()
        }
    }

    private fun markMessagesAsRead() {
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

    override fun onResume() {
        super.onResume()
        isChatVisible = true
        markMessagesAsRead()
        
        // Update online status
        socketManager.updateStatus("online")
        
        // Check and display Socket.IO connection status
        val isConnected = socketManager.isConnected()
        Log.d("SocketIOCheck", "onResume - Socket.IO status: ${if (isConnected) "✅ CONNECTED" else "❌ DISCONNECTED"}")
        updateSocketStatusUI(isConnected)
        
        // Log connection info
        if (isConnected) {
            Log.d("SocketIOCheck", "✅ Socket.IO is WORKING - Messages will be sent via Socket.IO")
        } else {
            Log.w("SocketIOCheck", "⚠️ Socket.IO is NOT WORKING - Messages will use API fallback")
        }
        
        // Log status report
        logSocketIOStatus()
    }

    override fun onPause() {
        super.onPause()
        isChatVisible = false
        
        // Update online status
        socketManager.updateStatus("offline")
    }

    override fun onDestroy() {
        super.onDestroy()
        socketManager.leaveChat(chatId)
        // Don't disconnect Socket.IO here - it's a singleton used app-wide
    }

    override fun onBackPressed() {
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
}

