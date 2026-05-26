package com.gmwapp.hima.socket

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gmwapp.hima.utils.Config
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Synchronization lock to prevent multiple simultaneous connection attempts
    @Volatile
    private var isConnecting = false
    private val connectionLock = Any()

    /**
     * T5 watchdog: if neither EVENT_CONNECT nor EVENT_CONNECT_ERROR / EVENT_DISCONNECT
     * arrives within this window, reset [isConnecting] and tear the socket down so the
     * next [connect] call can start fresh instead of becoming a no-op forever.
     */
    private val connectWatchdogRunnable = Runnable {
        synchronized(connectionLock) {
            if (isConnecting) {
                Log.w("SocketIOCheck", "⏰ connect watchdog fired — resetting isConnecting and disconnecting")
                isConnecting = false
                try {
                    socket?.off()
                    socket?.disconnect()
                } catch (_: Exception) {}
                socket = null
                _isConnected.value = false
            }
        }
    }
    private val connectWatchdogMs = 15_000L
    
    // Event streams are SharedFlow (not StateFlow) — StateFlow conflates and de-duplicates
    // by equality, which silently dropped messages when the server echoed the same payload
    // twice or two replies arrived back-to-back. SharedFlow with replay=0 emits every event
    // exactly once in normal use; the larger buffer absorbs reconnect bursts.
    private fun <T> eventFlow(): MutableSharedFlow<T> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _newMessage = eventFlow<ChatMessageSocket>()
    val newMessage: SharedFlow<ChatMessageSocket> = _newMessage.asSharedFlow()

    private val _messageSent = eventFlow<ChatMessageSocket>()
    val messageSent: SharedFlow<ChatMessageSocket> = _messageSent.asSharedFlow()

    private val _messageError = eventFlow<String>()
    val messageError: SharedFlow<String> = _messageError.asSharedFlow()

    private val _chatUpdated = eventFlow<ChatUpdatedEvent>()
    val chatUpdated: SharedFlow<ChatUpdatedEvent> = _chatUpdated.asSharedFlow()

    private val _userTyping = eventFlow<TypingEvent>()
    val userTyping: SharedFlow<TypingEvent> = _userTyping.asSharedFlow()

    private val _reactionUpdated = eventFlow<ReactionUpdateEvent>()
    val reactionUpdated: SharedFlow<ReactionUpdateEvent> = _reactionUpdated.asSharedFlow()

    /**
     * Emits the server-confirmed message id whenever a delete-for-everyone event
     * lands (either triggered by this user or by the peer). The UI flips the
     * matching row to the "This message was deleted" tombstone.
     */
    private val _chatMessageDeleted = eventFlow<String>()
    val chatMessageDeleted: SharedFlow<String> = _chatMessageDeleted.asSharedFlow()
    
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
            
            // If connection is already in progress, decide based on whether the
            // userId changed. T5: if it did (account switch), force a clean restart
            // — otherwise the in-flight handshake's EVENT_CONNECT joins the new
            // user's room on the *old* socket identity.
            if (isConnecting) {
                if (currentUserId != null && currentUserId != userId) {
                    Log.w("SocketIOCheck", "⚠️ connect() racing account switch ${currentUserId} → $userId — forcing restart")
                    isConnecting = false
                    mainHandler.removeCallbacks(connectWatchdogRunnable)
                    try {
                        socket?.off()
                        socket?.disconnect()
                    } catch (_: Exception) {}
                    socket = null
                    _isConnected.value = false
                    // fall through to fresh connect below
                } else {
                    Log.w("SocketIOCheck", "⚠️ Connection already in progress, skipping duplicate call")
                    currentUserId = userId
                    return
                }
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
                    // T42: with Int.MAX_VALUE the `reconnect_failed` handler effectively
                    // never fires, so the watchdog/recovery branch is dead. Cap at 30
                    // attempts (~60s of retries) so a stalled link surfaces and
                    // we re-arm a fresh socket instead of looping forever.
                    reconnectionAttempts = 30
                    timeout = 20000
                    // Force new connection if we had an existing socket (to avoid reusing old connection)
                    forceNew = hadExistingSocket
                    // Socket.IO client 2.1.0 automatically handles EIO version
                }
                
                socket = IO.socket(Config.SOCKET_URL, options)
                setupListeners()

                Log.d("SocketIOCheck", "🔌 Attempting to connect to Socket.IO: ${Config.SOCKET_URL}${Config.SOCKET_PATH}")
                // T5: arm watchdog so a stalled connect doesn't strand isConnecting=true forever.
                mainHandler.removeCallbacks(connectWatchdogRunnable)
                mainHandler.postDelayed(connectWatchdogRunnable, connectWatchdogMs)
                socket?.connect()
                
                // Log connection attempt after a delay
                mainHandler.postDelayed({
                    val connected = socket?.connected() == true
                    Log.d("SocketIOCheck", "📊 Connection check after 3s: ${if (connected) "✅ CONNECTED" else "❌ STILL CONNECTING/FAILED"}")
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
                    mainHandler.removeCallbacks(connectWatchdogRunnable)
                    Log.d("SocketIOCheck", "✅ Socket.IO CONNECTED successfully!")
                    _isConnected.value = true
                    isConnecting = false
                    val userId = currentUserId
                    if (userId == null || userId <= 0) {
                        // T5: account was wiped (logout/clear) before this CONNECT landed —
                        // disconnect immediately rather than joining the wrong user room.
                        Log.w("SocketIOCheck", "⚠️ EVENT_CONNECT but currentUserId=null — disconnecting")
                        try {
                            socket?.off()
                            socket?.disconnect()
                        } catch (_: Exception) {}
                        socket = null
                        _isConnected.value = false
                        return@synchronized
                    }
                    Log.d("SocketIOCheck", "✅ Socket.IO CONNECTED - Joining user room...")
                    mainHandler.postDelayed({
                        joinUserRoom(userId)
                    }, 100)
                }
            }

            on(Socket.EVENT_DISCONNECT) { args ->
                synchronized(connectionLock) {
                    mainHandler.removeCallbacks(connectWatchdogRunnable)
                    val reason = args.getOrNull(0)?.toString() ?: "Unknown"
                    Log.d("SocketIOCheck", "❌ Socket.IO DISCONNECTED - Reason: $reason")
                    _isConnected.value = false
                    isConnecting = false
                }
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                synchronized(connectionLock) {
                    mainHandler.removeCallbacks(connectWatchdogRunnable)
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
                val userId = currentUserId
                if (userId != null) {
                    mainHandler.postDelayed({
                        if (!isConnected()) {
                            Log.d("SocketIOCheck", "🔄 Restarting Socket.IO after reconnect_failed")
                            disconnect()
                            connect(userId)
                        }
                    }, 3000)
                }
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
                        // T4: parse id as Long. T6: forward server's `is_deleted` flag.
                        val message = ChatMessageSocket(
                            id = messageData.optLong("id", 0L),
                            chatId = messageData.optString("chat_id", ""),
                            from = messageData.optString("from", ""),
                            to = messageData.optString("to", ""),
                            message = messageData.optString("message", ""),
                            messageType = messageData.optString("message_type", "text"),
                            attachmentUrl = messageData.optString("attachment_url", null),
                            isRead = messageData.optInt("is_read", 0) == 1,
                            timestamp = messageData.optString("timestamp", ""),
                            fromUserId = messageData.optInt("from_user_id", 0).takeIf { it > 0 },
                            toUserId = messageData.optInt("to_user_id", 0).takeIf { it > 0 },
                            isDeleted = messageData.optInt("is_deleted", 0) == 1,
                            audioDurationMs = if (messageData.isNull("audio_duration_ms")) null
                                else messageData.optLong("audio_duration_ms", 0L).takeIf { it > 0 }
                        )
                        Log.d(
                            "RealtimeChat",
                            "socket new_message RX id=${message.id} from=${message.fromUserId} to=${message.toUserId} chatId=${message.chatId} event=new_message"
                        )
                        _newMessage.tryEmit(message)
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
                            id = messageData.optLong("id", 0L),
                            chatId = messageData.optString("chat_id", ""),
                            from = messageData.optString("from", ""),
                            to = messageData.optString("to", ""),
                            message = messageData.optString("message", ""),
                            messageType = messageData.optString("message_type", "text"),
                            attachmentUrl = messageData.optString("attachment_url", null),
                            isRead = messageData.optInt("is_read", 0) == 1,
                            timestamp = messageData.optString("timestamp", ""),
                            fromUserId = messageData.optInt("from_user_id", 0).takeIf { it > 0 },
                            toUserId = messageData.optInt("to_user_id", 0).takeIf { it > 0 },
                            isDeleted = messageData.optInt("is_deleted", 0) == 1,
                            audioDurationMs = if (messageData.isNull("audio_duration_ms")) null
                                else messageData.optLong("audio_duration_ms", 0L).takeIf { it > 0 }
                        )
                        _messageSent.tryEmit(message)
                        Log.d("SocketIOCheck", "✅ Message sent confirmation received - ID: ${message.id}")
                    }
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing message_sent: ${e.message}", e)
                }
            }
            
            on("message_error") { args ->
                try {
                    val errorObj = args[0] as? JSONObject
                    val errorMessage = errorObj?.optString("error", "Unknown error") ?: "Unknown error"
                    _messageError.tryEmit(errorMessage)
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
                            id = messageObj.optLong("id", 0L),
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
                            reactions = if (reactionsList.isNotEmpty()) reactionsList else null,
                            isDeleted = messageObj.optInt("is_deleted", 0) == 1,
                            audioDurationMs = if (messageObj.isNull("audio_duration_ms")) null
                                else messageObj.optLong("audio_duration_ms", 0L).takeIf { it > 0 }
                        )
                        Log.d(
                            "RealtimeChat",
                            "socket new_message RX id=${message.id} from=${message.fromUserId} to=${message.toUserId} chatId=${message.chatId} event=chat_message"
                        )
                        _newMessage.tryEmit(message)
                        // T14: don't expose chat bodies in production logcat.
                        if (com.gmwapp.hima.BuildConfig.DEBUG) {
                            Log.d("SocketIOCheck", "📨 Chat message received: ${message.message}")
                        } else {
                            Log.d("SocketIOCheck", "📨 Chat message received id=${message.id} type=${message.messageType} len=${message.message.length}")
                        }
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
                    _chatUpdated.tryEmit(event)
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
                    _userTyping.tryEmit(event)
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
                    
                    // T4: parse message_id as Long.
                    val event = ReactionUpdateEvent(
                        messageId = data?.optLong("message_id", 0L) ?: 0L,
                        chatId = data?.optString("chat_id", "") ?: "",
                        userId = data?.optInt("user_id", 0) ?: 0,
                        reactionEmoji = data?.optString("reaction_emoji", null),
                        allReactions = reactionsList
                    )
                    _reactionUpdated.tryEmit(event)
                    Log.d("SocketIOCheck", "✅ Reaction updated received - Message: ${event.messageId}, Reaction: ${event.reactionEmoji}")
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing reaction_updated: ${e.message}", e)
                }
            }
            
            on("reaction_error") { args ->
                try {
                    val errorObj = args[0] as? JSONObject
                    val errorMessage = errorObj?.optString("error", "Unknown error") ?: "Unknown error"
                    Log.e("SocketIOCheck", "❌ Reaction error: $errorMessage")
                    _messageError.tryEmit("Reaction error: $errorMessage")
                } catch (e: Exception) {
                    Log.e("SocketIOCheck", "Error parsing reaction_error: ${e.message}", e)
                }
            }

            on("message_deleted") { args ->
                try {
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val id = when {
                        payload.has("message_id") && !payload.isNull("message_id") ->
                            payload.opt("message_id")?.toString().orEmpty()
                        else -> ""
                    }
                    if (id.isNotEmpty()) {
                        _chatMessageDeleted.tryEmit(id)
                        Log.d("ChatDelete", "Socket message_deleted received id=$id")
                    }
                } catch (e: Exception) {
                    Log.e("ChatDelete", "Error parsing message_deleted: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Emit a delete-for-everyone request for [messageId]. Returns false if the
     * socket isn't connected so the caller can fall back to the REST endpoint.
     * Server broadcasts `message_deleted` to both rooms on success.
     */
    fun deleteMessage(fromUserId: Int, toUserId: Int, messageId: String): Boolean {
        if (!isConnected()) {
            Log.w("ChatDelete", "deleteMessage: socket not connected — caller should use REST")
            return false
        }
        return try {
            val data = JSONObject().apply {
                put("from_user_id", fromUserId)
                put("to_user_id", toUserId)
                put("message_id", messageId)
            }
            socket?.emit("delete_message", data)
            Log.d("ChatDelete", "📤 delete_message emitted id=$messageId from=$fromUserId to=$toUserId")
            true
        } catch (e: Exception) {
            Log.e("ChatDelete", "deleteMessage emit failed: ${e.message}", e)
            false
        }
    }
    
    fun disconnect() {
        synchronized(connectionLock) {
            mainHandler.removeCallbacks(connectWatchdogRunnable)
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
    fun sendMessage(
        fromUserId: Int,
        toUserId: Int,
        message: String,
        messageType: String = "text",
        attachmentUrl: String? = null,
        audioDurationMs: Long? = null
    ) {
        if (!isConnected()) {
            Log.e("SocketIOCheck", "❌ Cannot send message: Socket.IO not connected")
            _messageError.tryEmit("Socket.IO not connected")
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
                // CHAT-034: include duration only for audio rows with a real value.
                if (messageType.equals("audio", ignoreCase = true) &&
                    audioDurationMs != null && audioDurationMs > 0
                ) {
                    put("audio_duration_ms", audioDurationMs)
                }
            }

            socket?.emit("send_message", data)
            if (com.gmwapp.hima.BuildConfig.DEBUG) {
                Log.d("SocketIOCheck", "📤 Sent message from $fromUserId to $toUserId: $message")
            } else {
                Log.d("SocketIOCheck", "📤 Sent from=$fromUserId to=$toUserId type=$messageType len=${message.length}")
            }
        } catch (e: Exception) {
            Log.e("SocketIOCheck", "Error sending message: ${e.message}", e)
            _messageError.tryEmit(e.message ?: "Unknown error")
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
            _messageError.tryEmit("Socket.IO not connected")
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
            _messageError.tryEmit(e.message ?: "Unknown error")
        }
    }
}

// Data classes for Socket.IO events
// T4: ids must be Long — Int silently truncates snowflake-style backend ids and
// breaks dedup, reactions, and delete once any id exceeds Int.MAX_VALUE.
data class ChatMessageSocket(
    val id: Long,
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
    val reactions: List<Map<String, Any>>? = null,  // Array of {user_id, reaction_emoji}
    // T6: server may signal a tombstone in the socket payload; carry it through so
    // a user with no API refresh in flight still sees the deleted-bubble state.
    val isDeleted: Boolean = false,
    // CHAT-034: sender-measured voice-note length. Null for non-audio and for
    // pre-migration audio rows.
    val audioDurationMs: Long? = null
)

data class ReactionUpdateEvent(
    val messageId: Long,
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

