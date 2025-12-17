package com.gmwapp.hima.socket

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gmwapp.hima.utils.Config
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class SocketManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: SocketManager? = null
        
        fun getInstance(): SocketManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SocketManager().also { INSTANCE = it }
            }
        }
    }
    
    private var socket: Socket? = null
    private var currentUserId: Int? = null // Store current user ID for joining room after connection
    private val _isConnected = MutableStateFlow<Boolean>(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Synchronization lock to prevent multiple simultaneous connection attempts
    @Volatile
    private var isConnecting = false
    private val connectionLock = Any()
    
    private val _newMessage = MutableStateFlow<ChatMessageSocket?>(null)
    val newMessage: StateFlow<ChatMessageSocket?> = _newMessage.asStateFlow()
    
    private val _messageSent = MutableStateFlow<ChatMessageSocket?>(null)
    val messageSent: StateFlow<ChatMessageSocket?> = _messageSent.asStateFlow()
    
    private val _messageError = MutableStateFlow<String?>(null)
    val messageError: StateFlow<String?> = _messageError.asStateFlow()
    
    private val _chatUpdated = MutableStateFlow<ChatUpdatedEvent?>(null)
    val chatUpdated: StateFlow<ChatUpdatedEvent?> = _chatUpdated.asStateFlow()
    
    private val _userTyping = MutableStateFlow<TypingEvent?>(null)
    val userTyping: StateFlow<TypingEvent?> = _userTyping.asStateFlow()
    
    private val _reactionUpdated = MutableStateFlow<ReactionUpdateEvent?>(null)
    val reactionUpdated: StateFlow<ReactionUpdateEvent?> = _reactionUpdated.asStateFlow()
    
    /**
     * Connect to Socket.IO server using userId
     * After connection, automatically joins user room
     * Thread-safe: prevents multiple simultaneous connection attempts
     */
    fun connect(userId: Int) {
        synchronized(connectionLock) {
            Log.d("SocketIOCheck", "═══════════════════════════════════════")
            Log.d("SocketIOCheck", "🔌 SocketManager.connect() CALLED with userId: $userId")
            Log.d("SocketIOCheck", "═══════════════════════════════════════")
            
            // If already connected and same user, just ensure room is joined
            if (socket?.connected() == true && currentUserId == userId) {
                Log.d("SocketIOCheck", "✅ Already connected for user $userId, ensuring user room is joined...")
                joinUserRoom(userId)
                return
            }
            
            // If connection is already in progress, wait or return
            if (isConnecting) {
                Log.w("SocketIOCheck", "⚠️ Connection already in progress, skipping duplicate call")
                // Update userId in case it changed
                currentUserId = userId
                return
            }
            
            // If socket exists but not connected, disconnect it first
            val hadExistingSocket = socket != null
            socket?.let { existingSocket ->
                if (!existingSocket.connected()) {
                    Log.d("SocketIOCheck", "🔌 Disconnecting existing socket before creating new connection...")
                    try {
                        existingSocket.off() // Remove all listeners
                        existingSocket.disconnect()
                    } catch (e: Exception) {
                        Log.e("SocketIOCheck", "Error disconnecting old socket: ${e.message}")
                    }
                    socket = null // Clear reference
                } else if (currentUserId != userId) {
                    // Different user, need to disconnect and reconnect
                    Log.d("SocketIOCheck", "🔌 User changed from $currentUserId to $userId, reconnecting...")
                    try {
                        existingSocket.off() // Remove all listeners
                        existingSocket.disconnect()
                    } catch (e: Exception) {
                        Log.e("SocketIOCheck", "Error disconnecting socket for user change: ${e.message}")
                    }
                    socket = null // Clear reference
                } else {
                    // Same user and connected, just join room
                    joinUserRoom(userId)
                    return
                }
            }
            
            isConnecting = true
            currentUserId = userId
            
            try {
                Log.d("SocketIOCheck", "🔌 Starting Socket.IO connection...")
                Log.d("SocketIOCheck", "📡 Socket URL: ${Config.SOCKET_URL}")
                Log.d("SocketIOCheck", "📂 Socket Path: ${Config.SOCKET_PATH}")
                Log.d("SocketIOCheck", "👤 User ID: $userId")
                
                val options = IO.Options().apply {
                    path = Config.SOCKET_PATH
                    transports = arrayOf("polling", "websocket")  // Try polling first, then upgrade to websocket
                    reconnection = true
                    reconnectionDelay = 2000  // Increased delay to prevent rapid reconnection loops
                    reconnectionAttempts = 5
                    timeout = 20000
                    // Force new connection if we had an existing socket (to avoid reusing old connection)
                    forceNew = hadExistingSocket
                    // Socket.IO client 2.1.0 automatically handles EIO version
                }
                
                socket = IO.socket(Config.SOCKET_URL, options)
                setupListeners()
                
                Log.d("SocketIOCheck", "🔌 Attempting to connect to Socket.IO: ${Config.SOCKET_URL}${Config.SOCKET_PATH}")
                socket?.connect()
                
                // Log connection attempt after a delay
                Handler(Looper.getMainLooper()).postDelayed({
                    val connected = socket?.connected() == true
                    Log.d("SocketIOCheck", "📊 Connection check after 3s: ${if (connected) "✅ CONNECTED" else "❌ STILL CONNECTING/FAILED"}")
                    if (!connected) {
                        isConnecting = false
                    }
                }, 3000)
                
            } catch (e: Exception) {
                Log.e("SocketIOCheck", "❌ Connection error: ${e.message}", e)
                Log.e("SocketIOCheck", "❌ Exception type: ${e.javaClass.simpleName}")
                Log.e("SocketIOCheck", "❌ Stack trace: ${e.stackTraceToString()}")
                _isConnected.value = false
                isConnecting = false
            }
        }
    }
    
    /**
     * Join user's personal room to receive messages
     * Must be called after connection is established
     */
    private fun joinUserRoom(userId: Int) {
        if (!isConnected()) {
            Log.e("SocketIOCheck", "❌ Cannot join user room: Socket.IO not connected")
            return
        }
        
        try {
            val data = JSONObject().apply {
                put("user_id", userId)
            }
            socket?.emit("join_user", data)
            Log.d("SocketIOCheck", "✅ Joined user room: $userId")
        } catch (e: Exception) {
            Log.e("SocketIOCheck", "Error joining user room: ${e.message}", e)
        }
    }
    
    private fun setupListeners() {
        Log.d("SocketIOCheck", "🔧 setupListeners() called - Socket instance: ${if (socket != null) "✅ Found" else "❌ Null"}")
        socket?.apply {
            // Remove all existing listeners first to prevent duplicates
            off()
            on(Socket.EVENT_CONNECT) {
                synchronized(connectionLock) {
                    Log.d("SocketIOCheck", "✅ Socket.IO CONNECTED successfully!")
                    _isConnected.value = true
                    isConnecting = false
                    // Join user room after connection
                    currentUserId?.let { userId ->
                        Log.d("SocketIOCheck", "✅ Socket.IO CONNECTED - Joining user room...")
                        // Small delay to ensure connection is fully established
                        Handler(Looper.getMainLooper()).postDelayed({
                            joinUserRoom(userId)
                        }, 100)
                    }
                }
            }
            
            on(Socket.EVENT_DISCONNECT) { args ->
                synchronized(connectionLock) {
                    val reason = args.getOrNull(0)?.toString() ?: "Unknown"
                    Log.d("SocketIOCheck", "❌ Socket.IO DISCONNECTED - Reason: $reason")
                    _isConnected.value = false
                    isConnecting = false
                }
            }
            
            on(Socket.EVENT_CONNECT_ERROR) { args ->
                synchronized(connectionLock) {
                    val error = args[0] as? Exception
                    val errorMessage = error?.message ?: args[0]?.toString() ?: "Unknown error"
                    Log.e("SocketIOCheck", "❌ Socket.IO CONNECTION ERROR: $errorMessage")
                    Log.e("SocketIOCheck", "❌ Error type: ${error?.javaClass?.simpleName ?: "Unknown"}")
                    if (error != null) {
                        Log.e("SocketIOCheck", "❌ Stack trace: ${error.stackTraceToString()}")
                    }
                    _isConnected.value = false
                    isConnecting = false
                }
            }
            
            on("reconnect_attempt") {
                Log.d("SocketIOCheck", "🔄 Reconnection attempt...")
            }
            
            on("reconnect_error") { args ->
                val error = args[0] as? Exception
                Log.e("SocketIOCheck", "❌ Reconnection error: ${error?.message}")
            }
            
            on("reconnect_failed") {
                Log.e("SocketIOCheck", "❌ Reconnection FAILED - Max attempts reached")
            }
            
            on("connected") { args ->
                try {
                    val response = args[0] as? JSONObject
                    val status = response?.optBoolean("status", false) ?: false
                    val userId = response?.optInt("user_id", 0) ?: 0
                    val socketId = response?.optString("socket_id", "")
                    Log.d("SocketIOCheck", "✅ User room joined - Status: $status, User ID: $userId, Socket ID: $socketId")
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing connected event: ${e.message}", e)
                }
            }
            
            on("new_message") { args ->
                try {
                    val data = args[0] as? JSONObject
                    val status = data?.optBoolean("status", false) ?: false
                    val messageData = data?.optJSONObject("message")
                    
                    if (status && messageData != null) {
                        val message = ChatMessageSocket(
                            id = messageData.optInt("id", 0),
                            chatId = messageData.optString("chat_id", ""),
                            from = messageData.optString("from", ""),
                            to = messageData.optString("to", ""),
                            message = messageData.optString("message", ""),
                            messageType = messageData.optString("message_type", "text"),
                            attachmentUrl = messageData.optString("attachment_url", null),
                            isRead = messageData.optInt("is_read", 0) == 1,
                            timestamp = messageData.optString("timestamp", ""),
                            fromUserId = messageData.optInt("from_user_id", 0).takeIf { it > 0 },
                            toUserId = messageData.optInt("to_user_id", 0).takeIf { it > 0 }
                        )
                        _newMessage.value = message
                    }
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing new_message: ${e.message}", e)
                }
            }
            
            on("message_sent") { args ->
                try {
                    val response = args[0] as? JSONObject
                    val status = response?.optString("status", "")
                    val messageData = response?.optJSONObject("message")
                    
                    if (status == "success" && messageData != null) {
                        val message = ChatMessageSocket(
                            id = messageData.optInt("id", 0),
                            chatId = messageData.optString("chat_id", ""),
                            from = messageData.optString("from", ""),
                            to = messageData.optString("to", ""),
                            message = messageData.optString("message", ""),
                            messageType = messageData.optString("message_type", "text"),
                            attachmentUrl = messageData.optString("attachment_url", null),
                            isRead = messageData.optInt("is_read", 0) == 1,
                            timestamp = messageData.optString("timestamp", ""),
                            fromUserId = messageData.optInt("from_user_id", 0).takeIf { it > 0 },
                            toUserId = messageData.optInt("to_user_id", 0).takeIf { it > 0 }
                        )
                        _messageSent.value = message
                        Log.d("SocketIOCheck", "✅ Message sent confirmation received - ID: ${message.id}")
                    }
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing message_sent: ${e.message}", e)
                }
            }
            
            on("message_error") { args ->
                try {
                    val errorObj = args[0] as? JSONObject
                    val errorMessage = errorObj?.optString("error", "Unknown error")
                    _messageError.value = errorMessage
                    Log.e("SocketIOCheck", "❌ Message error: $errorMessage")
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing message_error: ${e.message}", e)
                }
            }
            
            on("chat message") { args ->
                try {
                    // Handle chat message event (real-time sync in chat room)
                    val messageObj = args[0] as? JSONObject
                    if (messageObj != null) {
                        // Parse reactions if present
                        val reactionsArray = messageObj.optJSONArray("reactions")
                        val reactionsList = mutableListOf<Map<String, Any>>()
                        if (reactionsArray != null) {
                            for (i in 0 until reactionsArray.length()) {
                                val reactionObj = reactionsArray.getJSONObject(i)
                                reactionsList.add(mapOf(
                                    "user_id" to reactionObj.optInt("user_id", 0),
                                    "reaction_emoji" to reactionObj.optString("reaction_emoji", "")
                                ))
                            }
                        }
                        
                        val message = ChatMessageSocket(
                            id = messageObj.optInt("id", 0),
                            chatId = messageObj.optString("chat_id", ""),
                            from = messageObj.optString("from", ""),
                            to = messageObj.optString("to", ""),
                            message = messageObj.optString("message", ""),
                            messageType = messageObj.optString("message_type", "text"),
                            attachmentUrl = messageObj.optString("attachment_url", null),
                            isRead = messageObj.optInt("is_read", 0) == 1,
                            timestamp = messageObj.optString("timestamp", ""),
                            fromUserId = messageObj.optInt("from_user_id", 0).takeIf { it > 0 },
                            toUserId = messageObj.optInt("to_user_id", 0).takeIf { it > 0 },
                            reactions = if (reactionsList.isNotEmpty()) reactionsList else null
                        )
                        _newMessage.value = message
                        Log.d("SocketIOCheck", "📨 Chat message received: ${message.message}")
                    }
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing chat message: ${e.message}", e)
                }
            }
            
            on("joined_chat") { args ->
                try {
                    val data = args[0] as? JSONObject
                    val chatId = data?.optString("chat_id", "")
                    Log.d("SocketIOCheck", "✅ Joined chat room: $chatId")
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing joined_chat: ${e.message}", e)
                }
            }
            
            on("chat_updated") { args ->
                try {
                    val data = args[0] as? JSONObject
                    val event = ChatUpdatedEvent(
                        chatId = data?.optString("chat_id", "") ?: "",
                        lastMessage = data?.optString("last_message", null),
                        lastMessageTime = data?.optString("last_message_time", null)
                    )
                    _chatUpdated.value = event
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing chat_updated: ${e.message}", e)
                }
            }
            
            on("user_typing") { args ->
                try {
                    val data = args[0] as? JSONObject
                    val event = TypingEvent(
                        chatId = data?.optString("chat_id", "") ?: "",
                        userId = data?.optInt("user_id", 0) ?: 0,
                        isTyping = data?.optBoolean("is_typing", false) ?: false
                    )
                    _userTyping.value = event
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing user_typing: ${e.message}", e)
                }
            }
            
            on("reaction_updated") { args ->
                try {
                    val data = args[0] as? JSONObject
                    val allReactionsArray = data?.optJSONArray("all_reactions")
                    val reactionsList = mutableListOf<Map<String, Any>>()
                    
                    if (allReactionsArray != null) {
                        for (i in 0 until allReactionsArray.length()) {
                            val reactionObj = allReactionsArray.getJSONObject(i)
                            reactionsList.add(mapOf(
                                "user_id" to reactionObj.optInt("user_id", 0),
                                "reaction_emoji" to reactionObj.optString("reaction_emoji", "")
                            ))
                        }
                    }
                    
                    val event = ReactionUpdateEvent(
                        messageId = data?.optInt("message_id", 0) ?: 0,
                        chatId = data?.optString("chat_id", "") ?: "",
                        userId = data?.optInt("user_id", 0) ?: 0,
                        reactionEmoji = data?.optString("reaction_emoji", null),
                        allReactions = reactionsList
                    )
                    _reactionUpdated.value = event
                    Log.d("SocketIOCheck", "✅ Reaction updated received - Message: ${event.messageId}, Reaction: ${event.reactionEmoji}")
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing reaction_updated: ${e.message}", e)
                }
            }
            
            on("reaction_error") { args ->
                try {
                    val errorObj = args[0] as? JSONObject
                    val errorMessage = errorObj?.optString("error", "Unknown error")
                    Log.e("SocketIOCheck", "❌ Reaction error: $errorMessage")
                    _messageError.value = "Reaction error: $errorMessage"
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing reaction_error: ${e.message}", e)
                }
            }
        }
    }
    
    fun disconnect() {
        synchronized(connectionLock) {
            try {
                socket?.off() // Remove all listeners first
                socket?.disconnect()
            } catch (e: Exception) {
                Log.e("SocketIOCheck", "Error during disconnect: ${e.message}")
            }
            socket = null
            _isConnected.value = false
            isConnecting = false
            currentUserId = null
            Log.d("SocketIOCheck", "Socket.IO disconnected")
        }
    }
    
    fun isConnected(): Boolean {
        return socket?.connected() == true
    }
    
    /**
     * Send a message to another user
     * @param fromUserId Current user's ID (sender)
     * @param toUserId Recipient's user ID
     * @param message Message text
     * @param messageType Message type: "text", "image", "file", "audio", "video"
     * @param attachmentUrl Optional attachment URL for media messages
     */
    fun sendMessage(fromUserId: Int, toUserId: Int, message: String, messageType: String = "text", attachmentUrl: String? = null) {
        if (!isConnected()) {
            Log.e("SocketIOCheck", "❌ Cannot send message: Socket.IO not connected")
            _messageError.value = "Socket.IO not connected"
            return
        }
        
        try {
            val data = JSONObject().apply {
                put("from_user_id", fromUserId)
                put("to_user_id", toUserId)
                put("message", message)
                put("message_type", messageType)
                if (attachmentUrl != null) {
                    put("attachment_url", attachmentUrl)
                }
            }
            
            socket?.emit("send_message", data)
            Log.d("SocketIOCheck", "📤 Sent message from $fromUserId to $toUserId: $message")
        } catch (e: Exception) {
            Log.e("SocketIOCheck", "Error sending message: ${e.message}", e)
            _messageError.value = e.message ?: "Unknown error"
        }
    }
    
    /**
     * Generate chat ID from two user IDs (sorted order)
     */
    fun generateChatId(user1Id: Int, user2Id: Int): String {
        val ids = listOf(user1Id, user2Id).sorted()
        return "${ids[0]}_${ids[1]}"
    }
    
    /**
     * Join a specific chat room
     * @param user1Id First user ID
     * @param user2Id Second user ID
     */
    fun joinChatRoom(user1Id: Int, user2Id: Int) {
        if (!isConnected()) {
            Log.e("SocketIOCheck", "❌ Cannot join chat: Socket.IO not connected")
            return
        }
        
        try {
            val chatId = generateChatId(user1Id, user2Id)
            val data = JSONObject().apply {
                put("chat_id", chatId)
            }
            socket?.emit("join_chat", data)
            Log.d("SocketIOCheck", "✅ Joined chat room: $chatId")
        } catch (e: Exception) {
            Log.e("SocketIOCheck", "Error joining chat: ${e.message}", e)
        }
    }
    
    /**
     * Join a specific chat room using chat ID directly
     */
    fun joinChat(chatId: String) {
        if (!isConnected()) {
            Log.e("SocketIOCheck", "❌ Cannot join chat: Socket.IO not connected")
            return
        }
        
        try {
            val data = JSONObject().apply {
                put("chat_id", chatId)
            }
            socket?.emit("join_chat", data)
            Log.d("SocketIOCheck", "✅ Joined chat: $chatId")
        } catch (e: Exception) {
            Log.e("SocketIOCheck", "Error joining chat: ${e.message}", e)
        }
    }
    
    fun leaveChat(chatId: String) {
        if (!isConnected()) {
            Log.w("SocketIOCheck", "⚠️ Cannot leave chat: Socket.IO not connected")
            return
        }
        
        try {
            val data = JSONObject().apply {
                put("chat_id", chatId)
            }
            socket?.emit("leave_chat", data)
            Log.d("SocketIOCheck", "Left chat: $chatId")
        } catch (e: Exception) {
            Log.e("SocketIOCheck", "Error leaving chat: ${e.message}", e)
        }
    }
    
    fun sendTyping(chatId: String, isTyping: Boolean) {
        if (!isConnected()) {
            Log.w("SocketIOCheck", "⚠️ Cannot send typing: Socket.IO not connected")
            return
        }
        
        try {
            val data = JSONObject().apply {
                put("chat_id", chatId)
                put("is_typing", isTyping)
            }
            socket?.emit("typing", data)
        } catch (e: Exception) {
            Log.e("SocketIOCheck", "Error sending typing: ${e.message}", e)
        }
    }
    
    fun updateStatus(status: String) {
        if (!isConnected()) {
            Log.w("SocketIOCheck", "⚠️ Cannot update status: Socket.IO not connected")
            return
        }
        
        try {
            val data = JSONObject().apply {
                put("status", status)
            }
            socket?.emit("update_status", data)
            Log.d("SocketIOCheck", "Status updated: $status")
        } catch (e: Exception) {
            Log.e("SocketIOCheck", "Error updating status: ${e.message}", e)
        }
    }
    
    /**
     * Send a reaction to a message
     * @param userId Current user's ID
     * @param messageId Message ID to react to
     * @param reactionEmoji Emoji reaction (e.g., "👍", "❤️") or null to remove reaction
     */
    fun sendReaction(userId: Int, messageId: Int, reactionEmoji: String?) {
        if (!isConnected()) {
            Log.e("SocketIOCheck", "❌ Cannot send reaction: Socket.IO not connected")
            _messageError.value = "Socket.IO not connected"
            return
        }
        
        try {
            val data = JSONObject().apply {
                put("user_id", userId)
                put("message_id", messageId)
                if (reactionEmoji != null) {
                    put("reaction_emoji", reactionEmoji)
                } else {
                    put("reaction_emoji", JSONObject.NULL)
                }
            }
            
            socket?.emit("send_reaction", data)
            Log.d("SocketIOCheck", "📤 Sent reaction from $userId on message $messageId: $reactionEmoji")
        } catch (e: Exception) {
            Log.e("SocketIOCheck", "Error sending reaction: ${e.message}", e)
            _messageError.value = e.message ?: "Unknown error"
        }
    }
}

// Data classes for Socket.IO events
data class ChatMessageSocket(
    val id: Int,
    val chatId: String,
    val from: String,
    val to: String,
    val message: String,
    val messageType: String,
    val attachmentUrl: String?,
    val isRead: Boolean,
    val timestamp: String,
    val fromUserId: Int?,
    val toUserId: Int?,
    val reactions: List<Map<String, Any>>? = null  // Array of {user_id, reaction_emoji}
)

data class ReactionUpdateEvent(
    val messageId: Int,
    val chatId: String,
    val userId: Int,
    val reactionEmoji: String?,
    val allReactions: List<Map<String, Any>>?  // Array of {user_id, reaction_emoji}
)

data class ChatUpdatedEvent(
    val chatId: String,
    val lastMessage: String?,
    val lastMessageTime: String?
)

data class TypingEvent(
    val chatId: String,
    val userId: Int,
    val isTyping: Boolean
)

