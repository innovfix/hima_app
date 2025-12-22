package com.gmwapp.hima.activities

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Observer
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.ChatAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.models.ChatMessage
import com.gmwapp.hima.models.ChatMessageApi
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MarkReadResponse
import com.gmwapp.hima.retrofit.responses.MarkMessagesReadResponse
import com.gmwapp.hima.retrofit.responses.MessageListResponse
import com.gmwapp.hima.retrofit.responses.SendMessageResponse
import com.gmwapp.hima.retrofit.responses.ChatHistoryResponse
import com.gmwapp.hima.retrofit.responses.BlockUserResponse
import com.gmwapp.hima.retrofit.responses.CheckCallAvailabilityResponse
import com.gmwapp.hima.retrofit.responses.FallbackSendMessageResponse
import com.gmwapp.hima.retrofit.responses.RegisterResponse
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
import android.content.Intent
import org.json.JSONObject

@AndroidEntryPoint
class ChatActivityInHouse : AppCompatActivity() {

    @Inject
    lateinit var apiManager: ApiManager

    private val femaleUsersViewModel: com.gmwapp.hima.viewmodels.FemaleUsersViewModel by viewModels()

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var ivBack: ImageView
    private lateinit var ivMore: ImageView
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
    
    // Store last online status from API
    private var lastOnlineStatus: String? = null
    
    // Track if user is blocked
    private var iHaveBlockedThisUser: Boolean = false
    
    // Call buttons
    private lateinit var callButtonsContainer: View
    private lateinit var cvAudioCall: com.google.android.material.card.MaterialCardView
    private lateinit var cvVideoCall: com.google.android.material.card.MaterialCardView
    private lateinit var ivAudioCall: ImageView
    private lateinit var ivVideoCall: ImageView
    private var peerUserGender: String? = null
    private var isPeerUserOnline: Boolean = false
    private var peerAudioStatus: Int? = null  // 0 or 1
    private var peerVideoStatus: Int? = null   // 0 or 1
    private var isCallBlocked: Boolean = false  // true if male user is blocked by female user (blocked = 1 or 2)
    
    // Pagination variables
    private var currentOffset = 0
    private var isLoadingMore = false
    private var hasMoreMessages = true
    private val MESSAGES_PER_PAGE = 10
    
    // API returns timestamps in IST format: "2025-11-18 19:10:31"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initializeViews()
        setupRecyclerView()
        setupUserIds()
        setupClickListeners()
        connectSocket()
        loadMessages()
        observeSocketEvents()
        setupCallStatusObservers()
        setupCallButtons()
        setupCallButtonListeners()
        
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
        ivMore = findViewById(R.id.iv_more)
        ivUser = findViewById(R.id.iv_user)
        tvUserName = findViewById(R.id.tv_user_name)
        tvUserStatus = findViewById(R.id.tv_user_status)
        vOnlineIndicator = findViewById(R.id.v_online_indicator)
        
        // Initialize call buttons
        callButtonsContainer = findViewById(R.id.call_buttons_container)
        cvAudioCall = findViewById(R.id.cv_audio_call)
        ivAudioCall = findViewById(R.id.iv_audio_call)
        cvVideoCall = findViewById(R.id.cv_video_call)
        ivVideoCall = findViewById(R.id.iv_video_call)

        val userName = intent.getStringExtra("USER_NAME") ?: "User"
        val userImage = intent.getStringExtra("USER_IMAGE")

        tvUserName.text = extractNameOnly(userName)

        if (!userImage.isNullOrEmpty()) {
            Glide.with(this)
                .load(userImage)
                .apply(RequestOptions.circleCropTransform())
                .into(ivUser)
        }

        // Configure UI based on user gender
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val isMaleUser = userData?.gender?.equals(DConstants.MALE, ignoreCase = true) == true
        
        if (isMaleUser) {
            // Hide back button and align avatar from start for male users
            ivBack.visibility = View.GONE
            val layoutParams = ivUser.layoutParams as android.view.ViewGroup.MarginLayoutParams
            layoutParams.marginStart = 0
            ivUser.layoutParams = layoutParams
        } else {
            // Keep back button visible and avatar with margin for female users
            ivBack.visibility = View.VISIBLE
            val layoutParams = ivUser.layoutParams as android.view.ViewGroup.MarginLayoutParams
            // Convert 12dp to pixels
            val marginInPx = (12 * resources.displayMetrics.density).toInt()
            layoutParams.marginStart = marginInPx
            ivUser.layoutParams = layoutParams
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
        chatAdapter = ChatAdapter(
            messages = messages,
            enableReactions = true,
            myUserId = myUserId,
            onReactionChanged = { message, reactionEmoji -> handleReactionUpdate(message, reactionEmoji) },
            onReactionClick = { message, emoji -> showReactionDetails(message, emoji) }
        )
        rvMessages.apply {
            // WhatsApp-style layout: messages anchored to bottom, newest at bottom
            // Messages are sorted oldest first (index 0 = oldest, index N = newest)
            // With stackFromEnd=true, RecyclerView shows the last item (newest) at bottom
            // With reverseLayout=false, it keeps the order: oldest at top, newest at bottom
            val layoutManager = LinearLayoutManager(this@ChatActivityInHouse).apply {
                stackFromEnd = true  // Anchor to bottom - shows last item (index N = newest) at bottom
                reverseLayout = false  // Don't reverse - keeps chronological order
            }
            this.layoutManager = layoutManager
            adapter = chatAdapter
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = RecyclerView.FOCUS_BLOCK_DESCENDANTS
            
            // Add scroll listener for pagination (load older messages when scrolling up)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                    if (layoutManager != null) {
                        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                        
                        // Load more when user scrolls to top (older messages)
                        // Trigger when user is near the top (within first 5 items) and scrolling up
                        if (firstVisiblePosition <= 5 && dy < 0 && !isLoadingMore && hasMoreMessages) {
                            Log.d("ChatPagination", "🔄 Scroll detected - Loading more messages. First visible: $firstVisiblePosition")
                            loadMoreMessages()
                        }
                    }
                }
            })
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

    private fun handleReactionUpdate(message: ChatMessage, reactionEmoji: String?) {
        if (message.isDateHeader) return
        
        val messageId = message.id.toIntOrNull() ?: return
        
        // Send reaction to server via Socket.IO (with API fallback)
        if (socketManager.isConnected()) {
            socketManager.sendReaction(myUserId, messageId, reactionEmoji)
            Log.d("ChatReactions", "📤 Sending reaction via Socket.IO - Message: $messageId, Reaction: $reactionEmoji")
        } else {
            // Fallback to API
            apiManager.addMessageReaction(
                userId = myUserId,
                messageId = messageId,
                reactionEmoji = reactionEmoji,
                object : NetworkCallback<com.gmwapp.hima.retrofit.responses.AddReactionResponse> {
                    override fun onResponse(
                        call: Call<com.gmwapp.hima.retrofit.responses.AddReactionResponse>,
                        response: Response<com.gmwapp.hima.retrofit.responses.AddReactionResponse>
                    ) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            val data = response.body()?.data
                            if (data != null) {
                                // Update local message with reactions
                                updateMessageReactions(messageId.toString(), data.allReactions)
                            }
                        }
                    }

                    override fun onFailure(
                        call: Call<com.gmwapp.hima.retrofit.responses.AddReactionResponse>,
                        t: Throwable
                    ) {
                        Log.e("ChatReactions", "Failed to send reaction via API: ${t.message}")
                    }

                    override fun onNoNetwork() {
                        Log.e("ChatReactions", "No network connection")
                    }
                }
            )
            Log.w("ChatReactions", "⚠️ Socket.IO not connected - Using API fallback")
        }
    }
    
    private fun updateMessageReactions(messageId: String, reactions: List<com.gmwapp.hima.models.MessageReaction>?) {
        val messageIndex = messages.indexOfFirst { it.id == messageId }
        if (messageIndex != -1) {
            val reactionsMap = reactions?.associate { it.userId to it.reactionEmoji } ?: emptyMap()
            messages[messageIndex].reactions = reactionsMap
            chatAdapter.notifyItemChanged(messageIndex)
        }
    }

    private fun setupClickListeners() {
        ivBack.setOnClickListener {
            onBackPressed()
        }

        ivMore.setOnClickListener {
            showOptionsMenu()
        }

        btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun connectSocket() {
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        Log.d("SocketIOCheck", "🔌 Connecting Socket.IO for ChatActivityInHouse")
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        
        // Get user ID and connect Socket.IO
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id
        
        if (userId != null && userId > 0) {
            Log.d("SocketIOCheck", "🔌 Connecting Socket.IO with User ID: $userId")
            
            // If already connected, join chat room immediately
            if (socketManager.isConnected() && myUserId > 0 && peerUserId > 0) {
                Log.d("SocketIOCheck", "✅ Socket.IO already connected - Joining chat room immediately: $chatId")
                socketManager.joinChatRoom(myUserId, peerUserId)
            } else {
                // Connect and wait for connection event to join room (handled in observeSocketEvents)
                socketManager.connect(userId)
            }
        } else {
            Log.e("SocketIOCheck", "❌ No user ID available - Cannot connect Socket.IO")
        }
    }
    
    private fun updateSocketStatusUI(isConnected: Boolean) {
        // Don't update UI based on Socket.IO status anymore
        // UI will be updated based on last_online_status from API
    }
    
    private fun updateOnlineStatusFromAPI(lastOnlineStatus: String?) {
        this.lastOnlineStatus = lastOnlineStatus
        mainHandler.post {
            // Show status only if it's not null or empty
            if (!lastOnlineStatus.isNullOrEmpty()) {
                tvUserStatus.text = lastOnlineStatus
                vOnlineIndicator.visibility = View.VISIBLE
            } else {
                // Hide status if null or empty
                tvUserStatus.text = ""
                tvUserStatus.visibility = View.GONE
                vOnlineIndicator.visibility = View.GONE
            }
            
            // Removed: Update call buttons state based on online status
            // Buttons are now controlled only by check_call_availability API response
        }
    }

    private fun observeSocketEvents() {
        lifecycleScope.launch {
            socketManager.isConnected.collectLatest { connected ->
                if (connected) {
                    Log.d("SocketIOCheck", "✅ Socket.IO CONNECTED - joining chat room immediately: $chatId")
                    // Join chat room immediately when connected
                    if (myUserId > 0 && peerUserId > 0) {
                        socketManager.joinChatRoom(myUserId, peerUserId)
                        Log.d("SocketIOCheck", "✅ Joined chat room: $chatId")
                    } else {
                        // Fallback to chatId if user IDs not set
                        socketManager.joinChat(chatId)
                    }
                } else {
                    Log.d("SocketIOCheck", "❌ Socket.IO DISCONNECTED")
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
                        // Message already shown optimistically, just update status if needed
                        logSocketIOStatus() // Log updated status
                    }
                }
            }
        }

        lifecycleScope.launch {
            socketManager.messageError.collectLatest { error ->
                error?.let {
                    Log.e("SocketIOCheck", "Message error: $it")
                }
            }
        }

        lifecycleScope.launch {
            socketManager.reactionUpdated.collectLatest { reactionUpdate ->
                reactionUpdate?.let {
                    if (it.chatId == chatId) {
                        handleIncomingReaction(it)
                    }
                }
            }
        }
    }
    
    private fun handleIncomingReaction(reactionUpdate: com.gmwapp.hima.socket.ReactionUpdateEvent) {
        val messageId = reactionUpdate.messageId.toString()
        val messageIndex = messages.indexOfFirst { it.id == messageId }
        
        if (messageIndex != -1) {
            // Convert all_reactions to Map<userId, emoji>
            val reactionsMap = mutableMapOf<Int, String>()
            reactionUpdate.allReactions?.forEach { reactionMapData ->
                val userId = (reactionMapData["user_id"] as? Number)?.toInt() ?: return@forEach
                val emoji = reactionMapData["reaction_emoji"] as? String ?: return@forEach
                reactionsMap[userId] = emoji
            }
            
            messages[messageIndex].reactions = reactionsMap
            chatAdapter.notifyItemChanged(messageIndex)
            Log.d("ChatReactions", "✅ Updated reaction for message $messageId: $reactionsMap")
        }
    }
    
    private fun showReactionDetails(message: ChatMessage, clickedEmoji: String) {
        val reactions = message.reactions
        if (reactions.isEmpty()) return
        
        // Get peer user info from intent
        val peerUserName = extractNameOnly(intent.getStringExtra("USER_NAME") ?: "User")
        val peerUserImage = intent.getStringExtra("USER_IMAGE") ?: ""
        
        // Show bottom sheet
        val bottomSheet = com.gmwapp.hima.dialogs.BottomSheetReactionDetails.newInstance(
            message = message,
            myUserId = myUserId,
            peerUserId = peerUserId,
            peerUserName = peerUserName,
            peerUserImage = peerUserImage,
            onReactionRemove = { msg, emoji ->
                // Remove reaction when user taps "Tap to remove"
                handleReactionUpdate(msg, null)
            },
            onAddReaction = { msg ->
                // Show reaction popup when user taps "Add Reaction" button
                val messageIndex = messages.indexOfFirst { it.id == msg.id }
                if (messageIndex != -1) {
                    val viewHolder = rvMessages.findViewHolderForAdapterPosition(messageIndex)
                    if (viewHolder != null && viewHolder.itemView != null) {
                        val tvMessage = viewHolder.itemView.findViewById<TextView>(R.id.tv_message)
                        if (tvMessage != null) {
                            // Use the adapter's method to show reaction popup
                            chatAdapter.showReactionPopupForPosition(tvMessage, messageIndex)
                        }
                    }
                }
            }
        )
        
        bottomSheet.show(supportFragmentManager, "BottomSheetReactionDetails")
    }

    private fun loadMessages() {
        // Reset pagination state
        currentOffset = 0
        hasMoreMessages = true
        isLoadingMore = false
        
        Log.d("ChatPagination", "═══════════════════════════════════════")
        Log.d("ChatPagination", "🔄 INITIAL LOAD - Requesting chat history")
        Log.d("ChatPagination", "User ID: $myUserId, Receiver ID: $peerUserId")
        Log.d("ChatPagination", "Limit: $MESSAGES_PER_PAGE, Offset: $currentOffset")
        Log.d("ChatPagination", "═══════════════════════════════════════")
        
        apiManager.getChatHistory(
            userId = myUserId,
            receiverId = peerUserId,
            limit = MESSAGES_PER_PAGE,
            offset = currentOffset,
            object : NetworkCallback<ChatHistoryResponse> {
                override fun onResponse(call: Call<ChatHistoryResponse>, response: Response<ChatHistoryResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        
                        // Log complete API response with chathisoryapi tag
                        Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                        Log.d("chathisoryapi", "📥 COMPLETE CHAT HISTORY API RESPONSE")
                        Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                        Log.d("chathisoryapi", "HTTP Status Code: ${response.code()}")
                        Log.d("chathisoryapi", "Response Headers: ${response.headers()}")
                        Log.d("chathisoryapi", "Request URL: ${call.request().url}")
                        Log.d("chathisoryapi", "Request Method: ${call.request().method}")
                        
                        if (responseBody != null) {
                            Log.d("chathisoryapi", "Response Success: ${responseBody.success}")
                            Log.d("chathisoryapi", "Response Message: ${responseBody.message}")
                            
                            if (responseBody.data != null) {
                                val data = responseBody.data
                                Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                                Log.d("chathisoryapi", "📊 RESPONSE DATA:")
                                Log.d("chathisoryapi", "Chat ID: ${data.chatId}")
                                Log.d("chathisoryapi", "User ID: ${data.userId}")
                                Log.d("chathisoryapi", "Receiver ID: ${data.receiverId}")
                                Log.d("chathisoryapi", "Total Messages: ${data.totalMessages}")
                                Log.d("chathisoryapi", "Returned Messages: ${data.returnedMessages}")
                                Log.d("chathisoryapi", "Limit: ${data.limit}")
                                Log.d("chathisoryapi", "Offset: ${data.offset}")
                                Log.d("chathisoryapi", "Has More: ${data.hasMore}")
                                Log.d("chathisoryapi", "Last Online: ${data.lastOnline}")
                                Log.d("chathisoryapi", "Last Online Status: ${data.lastOnlineStatus}")
                                Log.d("chathisoryapi", "I Have Blocked This User: ${data.iHaveBlockedThisUser}")
                                Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                                
                                val apiMessages = data.messages
                                Log.d("chathisoryapi", "📨 MESSAGES COUNT: ${apiMessages.size}")
                                Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                                
                                // Log each message with complete details
                                apiMessages.forEachIndexed { index, msg ->
                                    Log.d("chathisoryapi", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    Log.d("chathisoryapi", "Message #${index + 1}:")
                                    Log.d("chathisoryapi", "  ID: ${msg.id}")
                                    Log.d("chathisoryapi", "  Chat ID: ${msg.chatId}")
                                    Log.d("chathisoryapi", "  From User ID: ${msg.fromUserId}")
                                    Log.d("chathisoryapi", "  From: ${msg.from}")
                                    Log.d("chathisoryapi", "  To User ID: ${msg.toUserId}")
                                    Log.d("chathisoryapi", "  To: ${msg.to}")
                                    Log.d("chathisoryapi", "  Message: ${msg.message}")
                                    Log.d("chathisoryapi", "  Message Type: ${msg.messageType}")
                                    Log.d("chathisoryapi", "  Attachment URL: ${msg.attachmentUrl ?: "null"}")
                                    Log.d("chathisoryapi", "  Is Read: ${msg.isRead}")
                                    Log.d("chathisoryapi", "  Timestamp: ${msg.timestamp}")
                                    Log.d("chathisoryapi", "  Created At: ${msg.createdAt ?: "null"}")
                                    Log.d("chathisoryapi", "  ⭐ Using for display: ${msg.createdAt ?: msg.timestamp}")
                                }
                                Log.d("chathisoryapi", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                                Log.d("chathisoryapi", "✅ END OF CHAT HISTORY API RESPONSE")
                                Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                            } else {
                                Log.d("chathisoryapi", "⚠️ Response data is null")
                            }
                        } else {
                            Log.d("chathisoryapi", "⚠️ Response body is null")
                        }
                        
                        if (responseBody?.success == true && responseBody.data != null) {
                            val data = responseBody.data
                            val apiMessages = data.messages
                            
                            // Update blocked status (for UI display purposes)
                            iHaveBlockedThisUser = data.iHaveBlockedThisUser
                            
                            Log.d("ChatPagination", "═══════════════════════════════════════")
                            Log.d("ChatPagination", "✅ INITIAL LOAD RESPONSE:")
                            Log.d("ChatPagination", "Total Messages: ${data.totalMessages}")
                            Log.d("ChatPagination", "Returned Messages: ${data.returnedMessages}")
                            Log.d("ChatPagination", "Has More: ${data.hasMore}")
                            Log.d("ChatPagination", "Offset: ${data.offset}")
                            Log.d("ChatPagination", "Limit: ${data.limit}")
                            Log.d("ChatPagination", "Messages received: ${apiMessages.size}")
                            Log.d("ChatPagination", "User blocked: $iHaveBlockedThisUser")
                            Log.d("ChatPagination", "═══════════════════════════════════════")
                            
                            // Log ALL messages from API response in order
                            Log.d("ChatPagination", "📋 API RESPONSE MESSAGES (Original Order from API):")
                            apiMessages.forEachIndexed { index, msg ->
                                Log.d("ChatPagination", "  [$index] ID=${msg.id}, ChatID=${msg.chatId}, From=${msg.fromUserId} (${msg.from}), To=${msg.toUserId} (${msg.to}), Type=${msg.messageType}, Text='${msg.message.take(50)}...', Timestamp=${msg.timestamp}, Read=${msg.isRead}")
                            }
                            Log.d("ChatPagination", "═══════════════════════════════════════")
                            
                            val convertedMessages = apiMessages.map { apiMsg ->
                                convertApiMessageToChatMessage(apiMsg)
                            }
                            
                            // Sort messages by date (oldest first) - WhatsApp style
                            // This ensures: index 0 = oldest message, index N = newest message
                            // With stackFromEnd=true, newest (index N) will appear at bottom, oldest (index 0) at top
                            val sortedMessages = convertedMessages.sortedBy { it.date?.time ?: 0L }
                            
                            messages.clear()
                            messages.addAll(sortedMessages)
                            
                            // Reactions are now loaded from API response, no need to apply stored reactions
                            
                            // Add header at top (position 0) and at date boundaries
                            updateTopHeader(messages)
                            
                            chatAdapter.notifyDataSetChanged()
                            
                            // Log message order for debugging - show ALL sorted messages
                            Log.d("ChatPagination", "═══════════════════════════════════════")
                            Log.d("ChatPagination", "📋 SORTED MESSAGES (After sorting - should be oldest to newest):")
                            sortedMessages.forEachIndexed { index, msg ->
                                val msgId = if (msg.id.startsWith("temp_")) msg.id else msg.id
                                Log.d("ChatPagination", "  [$index] ID=$msgId, Text='${msg.message.take(40)}...', Timestamp=${msg.timestamp}, Date=${msg.date?.time}")
                            }
                            Log.d("ChatPagination", "═══════════════════════════════════════")
                            
                            if (sortedMessages.isNotEmpty()) {
                                Log.d("ChatPagination", "Message order check:")
                                Log.d("ChatPagination", "  First (index 0 - should be OLDEST): '${sortedMessages.first().message.take(30)}...' at ${sortedMessages.first().timestamp}, Date=${sortedMessages.first().date?.time}")
                                Log.d("ChatPagination", "  Last (index ${sortedMessages.size-1} - should be NEWEST): '${sortedMessages.last().message.take(30)}...' at ${sortedMessages.last().timestamp}, Date=${sortedMessages.last().date?.time}")
                            }
                            
                            // Update pagination state
                            currentOffset = data.offset + data.returnedMessages
                            hasMoreMessages = data.hasMore
                            
                            // Scroll to bottom (newest message) - WhatsApp style
                            if (messages.isNotEmpty()) {
                                rvMessages.post {
                                    rvMessages.scrollToPosition(messages.size - 1)
                                    Log.d("ChatPagination", "Scrolled to bottom, showing latest ${messages.size} messages")
                                }
                            }
                            
                            // Update online status from API response
                            updateOnlineStatusFromAPI(data.lastOnlineStatus)
                            
                            // Mark messages as read using the new API with last message id
                            if (apiMessages.isNotEmpty()) {
                                // Get the last message id (newest message) from API messages
                                val lastMessageId = apiMessages.maxByOrNull { it.id }?.id
                                if (lastMessageId != null) {
                                    markMessagesAsReadWithLastMessageId(lastMessageId)
                                }
                            }
                            
                            // Mark messages as read (old method - keeping for backward compatibility)
                            if (isChatVisible) {
                                markMessagesAsRead()
                            }
                            
                            Log.d("ChatPagination", "✅ Initial load complete. Displaying ${messages.size} messages")
                            Log.d("ChatPagination", "Next offset for pagination: $currentOffset, Has more: $hasMoreMessages")
                        }
                    } else {
                        Log.e("ChatPagination", "❌ Error loading messages: ${response.code()}")
                        Log.e("chathisoryapi", "❌ ERROR: HTTP ${response.code()}")
                        Log.e("chathisoryapi", "Error Body: ${response.errorBody()?.string()}")
                        Toast.makeText(this@ChatActivityInHouse, "Failed to load messages", Toast.LENGTH_SHORT).show()
                    }
                    isLoadingMore = false
                }

                override fun onFailure(call: Call<ChatHistoryResponse>, t: Throwable) {
                    Log.e("ChatPagination", "❌ Error loading messages: ${t.message}", t)
                    Log.e("chathisoryapi", "❌ NETWORK ERROR: ${t.message}")
                    Log.e("chathisoryapi", "Request URL: ${call.request().url}")
                    Toast.makeText(this@ChatActivityInHouse, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    isLoadingMore = false
                }

                override fun onNoNetwork() {
                    Log.e("ChatPagination", "❌ No network connection")
                    Log.e("chathisoryapi", "❌ NO NETWORK CONNECTION")
                    Toast.makeText(this@ChatActivityInHouse, "No internet connection", Toast.LENGTH_SHORT).show()
                    isLoadingMore = false
                }
            }
        )
    }
    
    private fun loadMoreMessages() {
        if (isLoadingMore || !hasMoreMessages) {
            Log.d("ChatPagination", "⏸️ Skipping load more - isLoadingMore: $isLoadingMore, hasMoreMessages: $hasMoreMessages")
            return
        }
        
        isLoadingMore = true
        
        Log.d("ChatPagination", "═══════════════════════════════════════")
        Log.d("ChatPagination", "📜 LOADING MORE MESSAGES (Pagination)")
        Log.d("ChatPagination", "Current Offset: $currentOffset")
        Log.d("ChatPagination", "Current Messages Count: ${messages.size}")
        Log.d("ChatPagination", "Limit: $MESSAGES_PER_PAGE")
        Log.d("ChatPagination", "═══════════════════════════════════════")
        
        // Save current scroll position to restore after loading (WhatsApp style)
        val layoutManager = rvMessages.layoutManager as? LinearLayoutManager
        val currentFirstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
        val currentFirstVisibleView = layoutManager?.findViewByPosition(currentFirstVisiblePosition)
        val offset = currentFirstVisibleView?.top ?: 0
        
        Log.d("ChatPagination", "Current scroll position - First visible: $currentFirstVisiblePosition, Offset: $offset")
        
        apiManager.getChatHistory(
            userId = myUserId,
            receiverId = peerUserId,
            limit = MESSAGES_PER_PAGE,
            offset = currentOffset,
            object : NetworkCallback<ChatHistoryResponse> {
                override fun onResponse(call: Call<ChatHistoryResponse>, response: Response<ChatHistoryResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        
                        // Log complete API response with chathisoryapi tag (for pagination)
                        Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                        Log.d("chathisoryapi", "📥 PAGINATION - CHAT HISTORY API RESPONSE")
                        Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                        Log.d("chathisoryapi", "HTTP Status Code: ${response.code()}")
                        Log.d("chathisoryapi", "Request URL: ${call.request().url}")
                        
                        if (responseBody != null) {
                            Log.d("chathisoryapi", "Response Success: ${responseBody.success}")
                            Log.d("chathisoryapi", "Response Message: ${responseBody.message}")
                            
                            if (responseBody.data != null) {
                                val data = responseBody.data
                                Log.d("chathisoryapi", "Chat ID: ${data.chatId}")
                                Log.d("chathisoryapi", "Total Messages: ${data.totalMessages}")
                                Log.d("chathisoryapi", "Returned Messages: ${data.returnedMessages}")
                                Log.d("chathisoryapi", "Limit: ${data.limit}, Offset: ${data.offset}")
                                Log.d("chathisoryapi", "Has More: ${data.hasMore}")
                                
                                val apiMessages = data.messages
                                Log.d("chathisoryapi", "📨 MESSAGES COUNT: ${apiMessages.size}")
                                
                                // Log each message with complete details
                                apiMessages.forEachIndexed { index, msg ->
                                    Log.d("chathisoryapi", "Message #${index + 1}: ID=${msg.id}, From=${msg.fromUserId}, To=${msg.toUserId}, Text='${msg.message}', Timestamp=${msg.timestamp}, Created At=${msg.createdAt ?: "null"}, ⭐ Using: ${msg.createdAt ?: msg.timestamp}")
                                }
                                Log.d("chathisoryapi", "═══════════════════════════════════════════════════════════")
                            }
                        }
                        
                        if (responseBody?.success == true && responseBody.data != null) {
                            val data = responseBody.data
                            val apiMessages = data.messages
                            
                            Log.d("ChatPagination", "✅ PAGINATION RESPONSE:")
                            Log.d("ChatPagination", "Total Messages: ${data.totalMessages}")
                            Log.d("ChatPagination", "Returned Messages: ${data.returnedMessages}")
                            Log.d("ChatPagination", "Has More: ${data.hasMore}")
                            Log.d("ChatPagination", "Offset: ${data.offset}")
                            Log.d("ChatPagination", "Messages received: ${apiMessages.size}")
                            
                            // Log first few message IDs and timestamps
                            apiMessages.take(3).forEachIndexed { index, msg ->
                                Log.d("ChatPagination", "Older Message[$index]: ID=${msg.id}, Text='${msg.message.take(20)}...', Timestamp=${msg.timestamp}")
                            }
                            
                            if (apiMessages.isNotEmpty()) {
                                val convertedMessages = apiMessages.map { apiMsg ->
                                    convertApiMessageToChatMessage(apiMsg)
                                }
                                
                                // Sort older messages by date (oldest first)
                                // This ensures chronological order: oldest first, newest last
                                val sortedOlderMessages = convertedMessages.sortedBy { it.date?.time ?: 0L }
                                
                                // Get the oldest message timestamp from existing messages
                                val oldestExistingTimestamp = messages.firstOrNull()?.date?.time ?: Long.MAX_VALUE
                                
                                // Filter out any messages that are newer than our oldest existing message
                                // (This prevents duplicates if API returns overlapping data)
                                val filteredOlderMessages = sortedOlderMessages.filter { msg ->
                                    val msgTime = msg.date?.time ?: 0L
                                    msgTime < oldestExistingTimestamp
                                }
                                
                                if (filteredOlderMessages.isNotEmpty()) {
                                    // Insert older messages at the beginning (oldest first)
                                    val oldSize = messages.size
                                    messages.addAll(0, filteredOlderMessages)
                                    
                                    // Update headers - this will remove old header and add new one at top
                                    updateTopHeader(messages)
                                    
                                    // Notify adapter of all changes
                                    chatAdapter.notifyDataSetChanged()
                                    
                                    // Restore scroll position to prevent jumping (WhatsApp style)
                                    // The position shifts by the number of items we added
                                    val newSize = messages.size
                                    val addedCount = newSize - oldSize
                                    rvMessages.post {
                                        layoutManager?.scrollToPositionWithOffset(
                                            currentFirstVisiblePosition + addedCount,
                                            offset
                                        )
                                        Log.d("ChatPagination", "Restored scroll position - New position: ${currentFirstVisiblePosition + addedCount}")
                                    }
                                    
                                    // Update pagination state
                                    currentOffset = data.offset + data.returnedMessages
                                    hasMoreMessages = data.hasMore
                                    
                                    Log.d("ChatPagination", "✅ Loaded ${filteredOlderMessages.size} older messages")
                                    Log.d("ChatPagination", "Total messages now: ${messages.size} (was $oldSize)")
                                    Log.d("ChatPagination", "Next offset: $currentOffset, Has more: $hasMoreMessages")
                                } else {
                                    Log.d("ChatPagination", "⚠️ All messages filtered (duplicates or newer than existing)")
                                    hasMoreMessages = false
                                }
                            } else {
                                hasMoreMessages = false
                                Log.d("ChatPagination", "✅ No more messages to load (empty response)")
                            }
                        }
                    } else {
                        Log.e("ChatPagination", "❌ Error loading more messages: ${response.code()}")
                        Log.e("chathisoryapi", "❌ PAGINATION ERROR: HTTP ${response.code()}")
                        Log.e("chathisoryapi", "Error Body: ${response.errorBody()?.string()}")
                    }
                    isLoadingMore = false
                }

                override fun onFailure(call: Call<ChatHistoryResponse>, t: Throwable) {
                    Log.e("ChatPagination", "❌ Error loading more messages: ${t.message}", t)
                    Log.e("chathisoryapi", "❌ PAGINATION NETWORK ERROR: ${t.message}")
                    Log.e("chathisoryapi", "Request URL: ${call.request().url}")
                    isLoadingMore = false
                }

                override fun onNoNetwork() {
                    Log.e("ChatPagination", "❌ No network connection")
                    Log.e("chathisoryapi", "❌ PAGINATION - NO NETWORK CONNECTION")
                    Toast.makeText(this@ChatActivityInHouse, "No internet connection", Toast.LENGTH_SHORT).show()
                    isLoadingMore = false
                }
            }
        )
    }

    private fun sendMessage() {
        // Check if user is blocked
        if (iHaveBlockedThisUser) {
            Toast.makeText(this, "Please unblock to send message", Toast.LENGTH_SHORT).show()
            return
        }
        
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) {
            return
        }

        // Clear input immediately
        etMessage.setText("")
        etMessage.requestFocus()

        // Show message optimistically (WhatsApp style - add to bottom)
        val currentTime = Date()
        val tempMessage = ChatMessage(
            id = "temp_${System.currentTimeMillis()}",
            message = messageText,
            timestamp = timeFormat.format(currentTime),
            isSentByMe = true,
            date = currentTime
        )
        
        // Add to end (newest at bottom)
        messages.add(tempMessage)
        
        // Update headers (will add boundary header if date changed)
        updateTopHeader(messages)
        chatAdapter.notifyDataSetChanged()
        
        // Smooth scroll to bottom to show new message
        rvMessages.post {
            rvMessages.smoothScrollToPosition(messages.size - 1)
        }

        // Try Socket.IO first, fallback to API
        val tempMessageId = tempMessage.id
        if (socketManager.isConnected()) {
            messageSendMethod[tempMessageId] = "socket"
            // ⭐ Updated to use new signature: sendMessage(fromUserId, toUserId, message, messageType, attachmentUrl)
            socketManager.sendMessage(myUserId, peerUserId, messageText, "text")
            Log.d("SocketIOCheck", "🚀 Sending via SOCKET.IO - From: $myUserId, To: $peerUserId, Message: '$messageText'")
        } else {
            messageSendMethod[tempMessageId] = "api"
            Log.w("SocketIOCheck", "⚠️ Socket.IO NOT CONNECTED - Using fallback API")
            // Fallback to API
            sendMessageViaAPI(messageText)
        }
    }

    private fun sendMessageViaAPI(messageText: String) {
        apiManager.fallbackSendMessage(
            fromUserId = myUserId,
            toUserId = peerUserId,
            message = messageText,
            object : NetworkCallback<FallbackSendMessageResponse> {
                override fun onResponse(call: Call<FallbackSendMessageResponse>, response: Response<FallbackSendMessageResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true && responseBody.data?.message != null) {
                            // Replace temp message with real one
                            val fallbackMessage = responseBody.data.message
                            val realMessage = convertFallbackMessageToChatMessage(fallbackMessage)
                            val tempIndex = messages.indexOfFirst { it.id.startsWith("temp_") && it.message == messageText }
                            if (tempIndex != -1) {
                                val tempId = messages[tempIndex].id
                                val existingReactions = messages[tempIndex].reactions
                                messages[tempIndex] = realMessage
                                messages[tempIndex].reactions = existingReactions
                                messageSendMethod[realMessage.id] = "api"
                                
                                // Update headers (will add boundary header if date changed)
                                updateTopHeader(messages)
                                chatAdapter.notifyDataSetChanged()
                            }
                            Log.d("SocketIOCheck", "✅ Message sent via fallback API - ID: ${fallbackMessage.id}")
                        }
                    } else {
                        // Remove temp message on error
                        val tempIndex = messages.indexOfFirst { it.id.startsWith("temp_") && it.message == messageText }
                        if (tempIndex != -1) {
                            messages.removeAt(tempIndex)
                            chatAdapter.notifyItemRemoved(tempIndex)
                        }
                        Log.e("SocketIOCheck", "Failed to send message via fallback API: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<FallbackSendMessageResponse>, t: Throwable) {
                    // Remove temp message on error
                    val tempIndex = messages.indexOfFirst { it.id.startsWith("temp_") && it.message == messageText }
                    if (tempIndex != -1) {
                        messages.removeAt(tempIndex)
                        chatAdapter.notifyItemRemoved(tempIndex)
                    }
                    Log.e("SocketIOCheck", "Failed to send message via fallback API: ${t.message}", t)
                }

                override fun onNoNetwork() {
                    val tempIndex = messages.indexOfFirst { it.id.startsWith("temp_") && it.message == messageText }
                    if (tempIndex != -1) {
                        messages.removeAt(tempIndex)
                        chatAdapter.notifyItemRemoved(tempIndex)
                    }
                    Log.e("SocketIOCheck", "No internet connection")
                }
            }
        )
    }

    private fun handleNewMessage(socketMessage: ChatMessageSocket) {
        // Check if message is from this chat
        if (socketMessage.chatId != chatId) {
            return
        }

        val realMessageId = socketMessage.id.toString()
        val isSentByMe = socketMessage.fromUserId == myUserId
        
        // Check if we already have this message (by ID)
        val existingMessageIndex = messages.indexOfFirst { it.id == realMessageId }
        
        if (existingMessageIndex != -1) {
            // Message already exists, skip
            return
        }
        
        // Check if there's a temp message that should be replaced
        // This happens when we send a message and it comes back via Socket.IO
        val tempMessageIndex = messages.indexOfFirst { 
            it.id.startsWith("temp_") && 
            it.message == socketMessage.message && 
            it.isSentByMe == isSentByMe
        }
        
        val chatMessage = convertSocketMessageToChatMessage(socketMessage)
        
        if (tempMessageIndex != -1) {
            // Replace temp message with real one
            val tempId = messages[tempMessageIndex].id
            val existingReactions = messages[tempMessageIndex].reactions
            messages[tempMessageIndex] = chatMessage
            messages[tempMessageIndex].reactions = existingReactions
            
            // Update headers (will add boundary header if date changed)
            updateTopHeader(messages)
            chatAdapter.notifyDataSetChanged()
            
            messageSendMethod[realMessageId] = "socket"
            Log.d("ChatActivityInHouse", "Replaced temp message with real message ID: $realMessageId")
        } else {
            // New incoming message - add to end (newest at bottom - WhatsApp style)
            messages.add(chatMessage)
            
            // Update headers (will add boundary header if date changed)
            updateTopHeader(messages)
            chatAdapter.notifyDataSetChanged()
            
            // Smooth scroll to bottom to show new message
            rvMessages.post {
                rvMessages.smoothScrollToPosition(messages.size - 1)
            }
        }

        // Mark as read if chat is visible
        if (isChatVisible) {
            markMessagesAsRead()
        }
    }

    private fun convertApiMessageToChatMessage(apiMsg: ChatMessageApi): ChatMessage {
        // Use created_at for accurate message time, fallback to timestamp if created_at is null
        val timestampString = apiMsg.createdAt ?: apiMsg.timestamp
        val timestamp = parseTimestamp(timestampString)
        val isSentByMe = apiMsg.fromUserId == myUserId
        
        // Convert reactions from API to Map<userId, emoji>
        val reactionsMap = apiMsg.reactions?.associate { it.userId to it.reactionEmoji } ?: emptyMap()
        
        Log.d("ChatTimeFix", "Message ID: ${apiMsg.id}, Using: ${if (apiMsg.createdAt != null) "created_at" else "timestamp"}, Value: $timestampString")
        
        return ChatMessage(
            id = apiMsg.id.toString(),
            message = apiMsg.message,
            timestamp = timeFormat.format(timestamp),
            isSentByMe = isSentByMe,
            date = timestamp,
            reactions = reactionsMap
        )
    }

    private fun convertSocketMessageToChatMessage(socketMsg: ChatMessageSocket): ChatMessage {
        val timestamp = parseTimestamp(socketMsg.timestamp)
        val isSentByMe = socketMsg.fromUserId == myUserId
        
        // Convert reactions from Socket to Map<userId, emoji>
        val reactionsMap = mutableMapOf<Int, String>()
        socketMsg.reactions?.forEach { reactionMap ->
            val userId = (reactionMap["user_id"] as? Number)?.toInt() ?: return@forEach
            val emoji = reactionMap["reaction_emoji"] as? String ?: return@forEach
            reactionsMap[userId] = emoji
        }
        
        return ChatMessage(
            id = socketMsg.id.toString(),
            message = socketMsg.message,
            timestamp = timeFormat.format(timestamp),
            isSentByMe = isSentByMe,
            date = timestamp,
            reactions = reactionsMap
        )
    }

    private fun convertFallbackMessageToChatMessage(fallbackMsg: com.gmwapp.hima.retrofit.responses.FallbackMessage): ChatMessage {
        // Use created_at for accurate message time, fallback to timestamp if created_at is null
        val timestampString = fallbackMsg.createdAt ?: fallbackMsg.timestamp
        val timestamp = parseTimestamp(timestampString)
        val isSentByMe = fallbackMsg.fromUserId == myUserId
        
        // Fallback messages don't include reactions, use empty map
        val reactionsMap = emptyMap<Int, String>()
        
        Log.d("ChatTimeFix", "Fallback Message ID: ${fallbackMsg.id}, Using: ${if (fallbackMsg.createdAt != null) "created_at" else "timestamp"}, Value: $timestampString")
        
        return ChatMessage(
            id = fallbackMsg.id.toString(),
            message = fallbackMsg.message,
            timestamp = timeFormat.format(timestamp),
            isSentByMe = isSentByMe,
            date = timestamp,
            reactions = reactionsMap
        )
    }

    private fun parseTimestamp(timestampString: String): Date {
        return try {
            val parsed = dateFormat.parse(timestampString)
            if (parsed == null) {
                Log.e("ChatPagination", "⚠️ Failed to parse timestamp: '$timestampString' - returned null, using current time")
                Date()
            } else {
                parsed
            }
        } catch (e: Exception) {
            Log.e("ChatPagination", "❌ Error parsing timestamp: '$timestampString' - ${e.message}, using current time")
            Date()
        }
    }

    // Helper function to check if two dates are on the same day (using IST timezone)
    private fun isSameDay(date1: Date?, date2: Date?): Boolean {
        if (date1 == null || date2 == null) return false
        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val cal1 = Calendar.getInstance(istTimeZone).apply { time = date1 }
        val cal2 = Calendar.getInstance(istTimeZone).apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    // Helper function to check if yesterday (using IST timezone)
    private fun isYesterday(today: Calendar, messageDate: Calendar): Boolean {
        val yesterday = today.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return yesterday.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
               yesterday.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR)
    }

    // Helper function to format date header (using IST timezone)
    private fun getDateHeaderText(date: Date): String {
        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val today = Calendar.getInstance(istTimeZone)
        val messageDate = Calendar.getInstance(istTimeZone).apply { time = date }
        
        return when {
            isSameDay(today.time, date) -> "Today"
            isYesterday(today, messageDate) -> "Yesterday"
            else -> {
                val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                dateFormat.timeZone = istTimeZone
                dateFormat.format(date)
            }
        }
    }

    // Function to add/update header at the top of messages and at date boundaries
    private fun updateTopHeader(messages: MutableList<ChatMessage>) {
        // Remove all existing headers first
        messages.removeAll { it.isDateHeader }
        
        if (messages.isEmpty()) return
        
        // Get the first actual message (not header)
        val firstMessage = messages.firstOrNull()
        
        if (firstMessage != null && firstMessage.date != null) {
            // Add header at position 0 (top)
            val header = ChatMessage(
                id = "header_top_${firstMessage.date!!.time}",
                message = "",
                timestamp = "",
                isSentByMe = false,
                date = firstMessage.date,
                isDateHeader = true,
                dateHeaderText = getDateHeaderText(firstMessage.date!!)
            )
            messages.add(0, header)
        }
        
        // Now add headers at date boundaries (where dates change)
        var i = 1 // Start from 1 (skip the top header)
        while (i < messages.size) {
            val currentMessage = messages[i]
            val nextMessage = messages.getOrNull(i + 1)
            
            // Skip if current is a header
            if (currentMessage.isDateHeader) {
                i++
                continue
            }
            
            val currentDate = currentMessage.date
            val nextDate = nextMessage?.date
            
            // If dates are different, insert header for the NEXT date AFTER current message
            if (currentDate != null && nextDate != null && !isSameDay(currentDate, nextDate)) {
                // Add header for the NEXT date (not current date)
                val header = ChatMessage(
                    id = "header_${nextDate.time}",
                    message = "",
                    timestamp = "",
                    isSentByMe = false,
                    date = nextDate,
                    isDateHeader = true,
                    dateHeaderText = getDateHeaderText(nextDate)
                )
                messages.add(i + 1, header)
                i += 2 // Skip both message and header
            } else {
                i++
            }
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

    private fun markMessagesAsReadWithLastMessageId(lastMessageId: Int) {
        apiManager.markMessagesRead(
            userId = myUserId,
            receiverId = peerUserId,
            lastMessageId = lastMessageId,
            object : NetworkCallback<MarkMessagesReadResponse> {
                override fun onResponse(call: Call<MarkMessagesReadResponse>, response: Response<MarkMessagesReadResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true) {
                            Log.d("ChatActivityInHouse", "Messages marked as read successfully. Last message ID: $lastMessageId")
                            responseBody.data?.let { data ->
                                Log.d("ChatActivityInHouse", "Messages marked read: ${data.messagesMarkedRead}, Remaining unread: ${data.remainingUnreadCount}")
                            }
                        } else {
                            Log.e("ChatActivityInHouse", "Error marking messages as read: ${responseBody?.message}")
                        }
                    } else {
                        Log.e("ChatActivityInHouse", "Error marking messages as read: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<MarkMessagesReadResponse>, t: Throwable) {
                    Log.e("ChatActivityInHouse", "Error marking messages as read: ${t.message}")
                }

                override fun onNoNetwork() {
                    // Silent fail for read status
                }
            }
        )
    }

    /**
     * Helper method to mark messages as read using the last message ID from loaded messages
     * This is called when user enters or leaves the activity
     */
    private fun markMessagesAsReadIfAvailable() {
        if (messages.isNotEmpty() && myUserId > 0 && peerUserId > 0) {
            // Get the last message id (newest message) from the messages list
            val lastMessage = messages.maxByOrNull { 
                // Extract numeric ID from message ID (handle both "temp_" prefixed and regular IDs)
                val idStr = it.id.replace("temp_", "")
                idStr.toIntOrNull() ?: 0
            }
            lastMessage?.let { msg ->
                val messageId = msg.id.replace("temp_", "").toIntOrNull()
                if (messageId != null && messageId > 0) {
                    Log.d("ChatActivityInHouse", "Marking messages as read before leaving activity. Last message ID: $messageId")
                    markMessagesAsReadWithLastMessageId(messageId)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isChatVisible = true
        
        // Mark messages as read using the new API with last message id if messages are loaded
        markMessagesAsReadIfAvailable()
        
        // Also call old method for backward compatibility
        markMessagesAsRead()
        
        // Check Socket.IO connection status and reconnect if needed
        val isConnected = socketManager.isConnected()
        Log.d("SocketIOCheck", "onResume - Socket.IO status: ${if (isConnected) "✅ CONNECTED" else "❌ DISCONNECTED"}")
        
        if (!isConnected) {
            // Reconnect Socket.IO if not connected (e.g., if activity was paused and resumed)
            Log.d("SocketIOCheck", "🔄 Reconnecting Socket.IO in onResume...")
            connectSocket()
        } else {
            // Update online status and join chat room immediately if already connected
            socketManager.updateStatus("online")
            if (myUserId > 0 && peerUserId > 0) {
                Log.d("SocketIOCheck", "✅ Socket.IO connected in onResume - Joining chat room immediately: $chatId")
                socketManager.joinChatRoom(myUserId, peerUserId)
            }
            Log.d("SocketIOCheck", "✅ Socket.IO is WORKING - Messages will be sent via Socket.IO")
        }
        
        // Log status report
        logSocketIOStatus()
    }

    override fun onPause() {
        super.onPause()
        isChatVisible = false
        
        // Mark messages as read when user leaves the activity (e.g., pressing home, switching apps, etc.)
        markMessagesAsReadIfAvailable()
        
        // Keep Socket.IO connected - do not disconnect on pause
    }

    override fun onDestroy() {
        super.onDestroy()
        
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        Log.d("SocketIOCheck", "🔌 Disconnecting Socket.IO - Leaving ChatActivityInHouse")
        Log.d("SocketIOCheck", "═══════════════════════════════════════")
        
        // Leave chat room
        socketManager.leaveChat(chatId)
        
        // Disconnect Socket.IO only on destroy
        socketManager.disconnect()
        
        Log.d("SocketIOCheck", "✅ Socket.IO disconnected")
    }

    override fun onBackPressed() {
        // Mark messages as read when user presses back button
        markMessagesAsReadIfAvailable()
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

    private fun showOptionsMenu() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val isFemaleUser = userData?.gender?.equals(DConstants.FEMALE, ignoreCase = true) == true

        if (isFemaleUser) {
            // Show custom popup with toggles for female users
            showCustomMenuWithToggles()
        } else {
            // Show regular menu for non-female users
            showRegularMenu()
        }
    }

    private fun showCustomMenuWithToggles() {
        val popupView = layoutInflater.inflate(R.layout.popup_call_settings_menu, null)
        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Set background and elevation
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.drawable.dialog_holo_light_frame))
        popupWindow.elevation = 8f
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true

        val switchAudio = popupView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_audio)
        val switchVideo = popupView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_video)
        val itemBlockUser = popupView.findViewById<TextView>(R.id.item_block_user)

        // Get current user data
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        
        // Temporarily disable listeners to prevent API calls while setting initial state
        switchAudio?.setOnCheckedChangeListener(null)
        switchVideo?.setOnCheckedChangeListener(null)
        
        // Set initial switch states
        switchAudio?.isChecked = userData?.audio_status == 1
        switchVideo?.isChecked = userData?.video_status == 1

        // Audio switch listener
        switchAudio?.setOnCheckedChangeListener { _, isChecked ->
            if (userData != null) {
                Log.d("ChatActivityInHouse", "📞 Updating audio status: ${if (isChecked) "ON" else "OFF"}")
                femaleUsersViewModel.updateCallStatus(
                    userId = userData.id,
                    callType = DConstants.AUDIO,
                    status = if (isChecked) 1 else 0
                )
            }
        }

        // Video switch listener
        switchVideo?.setOnCheckedChangeListener { _, isChecked ->
            if (userData != null) {
                Log.d("ChatActivityInHouse", "📹 Updating video status: ${if (isChecked) "ON" else "OFF"}")
                femaleUsersViewModel.updateCallStatus(
                    userId = userData.id,
                    callType = DConstants.VIDEO,
                    status = if (isChecked) 1 else 0
                )
            }
        }

        // Block user click listener - show block or unblock based on status
        if (iHaveBlockedThisUser) {
            itemBlockUser?.text = "Unblock User"
            itemBlockUser?.setOnClickListener {
                popupWindow.dismiss()
                showUnblockConfirmationDialog()
            }
        } else {
            itemBlockUser?.text = "Block User"
            itemBlockUser?.setOnClickListener {
                popupWindow.dismiss()
                showBlockConfirmationDialog()
            }
        }

        // Measure popup to get its width
        popupView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth

        // Calculate popup position (below the three-dot icon, aligned to the right)
        val location = IntArray(2)
        ivMore.getLocationOnScreen(location)
        val screenWidth = resources.displayMetrics.widthPixels
        val x = (location[0] + ivMore.width - popupWidth).coerceAtLeast(16) // Align to right edge, with margin
        val y = location[1] + ivMore.height + 8 // Add small gap below icon

        // Show popup
        popupWindow.showAtLocation(ivMore, android.view.Gravity.NO_GRAVITY, x, y)
    }

    private fun showRegularMenu() {
        val popup = PopupMenu(this, ivMore)
        popup.menuInflater.inflate(R.menu.menu_chat, popup.menu)

        // Show block option or unblock option based on current status
        popup.menu.findItem(R.id.action_block)?.isVisible = !iHaveBlockedThisUser
        popup.menu.findItem(R.id.action_unblock)?.isVisible = iHaveBlockedThisUser

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_block -> {
                    showBlockConfirmationDialog()
                    true
                }
                R.id.action_unblock -> {
                    showUnblockConfirmationDialog()
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

    private fun showUnblockConfirmationDialog() {
        // Create a custom dialog with beautiful UI (reusing the same layout)
        val dialogView = layoutInflater.inflate(R.layout.dialog_block_user_confirmation, null)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Make dialog background transparent
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Find TextViews by traversing the view hierarchy
        fun findTextViews(parent: android.view.ViewGroup): Pair<android.widget.TextView?, android.widget.TextView?> {
            var titleTextView: android.widget.TextView? = null
            var messageTextView: android.widget.TextView? = null
            
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is android.widget.TextView) {
                    if (titleTextView == null) {
                        titleTextView = child
                    } else if (messageTextView == null) {
                        messageTextView = child
                        break
                    }
                } else if (child is android.view.ViewGroup) {
                    val result = findTextViews(child)
                    if (result.first != null && titleTextView == null) titleTextView = result.first
                    if (result.second != null && messageTextView == null) messageTextView = result.second
                    if (titleTextView != null && messageTextView != null) break
                }
            }
            return Pair(titleTextView, messageTextView)
        }
        
        val (titleTextView, messageTextView) = findTextViews(dialogView as android.view.ViewGroup)
        val btnBlock = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_block)
        
        // Update title and message text
        titleTextView?.text = "Unblock User?"
        messageTextView?.text = "Once you unblock this user, you'll be able to send and receive messages from them again."
        btnBlock?.text = "Unblock"

        // Find the ImageView (icon) and change its background to dark pink
        fun findImageView(parent: android.view.ViewGroup): android.widget.ImageView? {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is android.widget.ImageView) {
                    return child
                } else if (child is android.view.ViewGroup) {
                    val result = findImageView(child)
                    if (result != null) return result
                }
            }
            return null
        }
        
        val iconImageView = findImageView(dialogView as android.view.ViewGroup)
        
        // Change icon circle background to dark pink
        iconImageView?.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(R.color.pink, theme)
        )
        
        // Change button background to lighter pink
        btnBlock?.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(R.color.pink, theme)
        )

        // Set button listeners
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        btnBlock?.setOnClickListener {
            unblockUser()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun blockUser() {
        Log.d("ChatActivityInHouse", "📤 Blocking user: $peerUserId")
        
        apiManager.blockChatUser(
            userId = myUserId,
            blockedUserId = peerUserId,
            object : NetworkCallback<BlockUserResponse> {
                override fun onResponse(call: Call<BlockUserResponse>, response: Response<BlockUserResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true) {
                            Toast.makeText(this@ChatActivityInHouse, responseBody.message, Toast.LENGTH_SHORT).show()
                            Log.d("ChatActivityInHouse", "✅ User blocked successfully")
                            // Reload chat history to update blocked status
                            loadMessages()
                        } else {
                            Toast.makeText(this@ChatActivityInHouse, "Failed to block user", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@ChatActivityInHouse, "Failed to block user: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<BlockUserResponse>, t: Throwable) {
                    Log.e("ChatActivityInHouse", "❌ Error blocking user: ${t.message}", t)
                    Toast.makeText(this@ChatActivityInHouse, "Failed to block user: ${t.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onNoNetwork() {
                    Toast.makeText(this@ChatActivityInHouse, "No internet connection", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun unblockUser() {
        Log.d("ChatActivityInHouse", "📤 Unblocking user: $peerUserId")
        
        apiManager.unblockChatUser(
            userId = myUserId,
            blockedUserId = peerUserId,
            object : NetworkCallback<BlockUserResponse> {
                override fun onResponse(call: Call<BlockUserResponse>, response: Response<BlockUserResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true) {
                            Toast.makeText(this@ChatActivityInHouse, responseBody.message, Toast.LENGTH_SHORT).show()
                            Log.d("ChatActivityInHouse", "✅ User unblocked successfully")
                            // Reload chat history to update blocked status
                            loadMessages()
                        } else {
                            Toast.makeText(this@ChatActivityInHouse, "Failed to unblock user", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@ChatActivityInHouse, "Failed to unblock user: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<BlockUserResponse>, t: Throwable) {
                    Log.e("ChatActivityInHouse", "❌ Error unblocking user: ${t.message}", t)
                    Toast.makeText(this@ChatActivityInHouse, "Failed to unblock user: ${t.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onNoNetwork() {
                    Toast.makeText(this@ChatActivityInHouse, "No internet connection", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun setupCallStatusObservers() {
        // Observe call status update response
        femaleUsersViewModel.updateCallStatusResponseLiveData.observe(this, Observer { response ->
            if (response != null && response.success && response.data != null) {
                // Update user data in preferences
                BaseApplication.getInstance()?.getPrefs()?.setUserData(response.data)
                Log.d("ChatActivityInHouse", "✅ Call status updated successfully")
                Toast.makeText(this, "Call status updated", Toast.LENGTH_SHORT).show()
            }
        })

        // Observe call status update errors
        femaleUsersViewModel.updateCallStatusErrorLiveData.observe(this, Observer { error ->
            if (error != null) {
                Log.e("ChatActivityInHouse", "❌ Error updating call status: $error")
                when (error) {
                    DConstants.NO_NETWORK -> {
                        Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, "Failed to update call status", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // ==================== CALL BUTTONS FUNCTIONS ====================

    private fun setupCallButtons() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val isMaleUser = userData?.gender?.equals(DConstants.MALE, ignoreCase = true) == true
        
        if (isMaleUser && peerUserId > 0 && myUserId > 0) {
            checkCallAvailability()
        } else {
            callButtonsContainer.visibility = View.GONE
        }
    }

    private fun checkCallAvailability() {
        apiManager.checkCallAvailability(
            maleUserId = myUserId,
            femaleUserId = peerUserId,
            object : NetworkCallback<CheckCallAvailabilityResponse> {
                override fun onResponse(
                    call: Call<CheckCallAvailabilityResponse>,
                    response: Response<CheckCallAvailabilityResponse>
                ) {
                    if (response.isSuccessful) {
                        val responseData = response.body()?.data
                        if (responseData != null) {
                            isCallBlocked = responseData.is_blocked
                            peerAudioStatus = responseData.audio_status
                            peerVideoStatus = responseData.video_status
                            
                            Log.d("ChatActivityInHouse", "Call availability - Blocked: $isCallBlocked, Audio: $peerAudioStatus, Video: $peerVideoStatus")
                            
                            mainHandler.post {
                                callButtonsContainer.visibility = View.VISIBLE
                                // Initialize buttons as disabled (gray) first
                                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this@ChatActivityInHouse, R.color.light_grey))
                                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this@ChatActivityInHouse, R.color.light_grey))
                                ivAudioCall.setColorFilter(ContextCompat.getColor(this@ChatActivityInHouse, R.color.grey_medium))
                                ivVideoCall.setColorFilter(ContextCompat.getColor(this@ChatActivityInHouse, R.color.grey_medium))
                                cvAudioCall.isEnabled = false
                                cvVideoCall.isEnabled = false
                                // Update buttons based on API response (is_blocked, audio_status, video_status)
                                updateCallButtonsState()
                            }
                        } else {
                            Log.e("ChatActivityInHouse", "Call availability response data is null")
                            callButtonsContainer.visibility = View.GONE
                        }
                    } else {
                        Log.e("ChatActivityInHouse", "Failed to check call availability: ${response.code()}")
                        callButtonsContainer.visibility = View.GONE
                    }
                }

                override fun onFailure(call: Call<CheckCallAvailabilityResponse>, t: Throwable) {
                    Log.e("ChatActivityInHouse", "Failed to check call availability: ${t.message}")
                    callButtonsContainer.visibility = View.GONE
                }

                override fun onNoNetwork() {
                    callButtonsContainer.visibility = View.GONE
                }
            }
        )
    }

    private fun updateCallButtonsState() {
        Log.d("CallButtons", "Updating call buttons state. Blocked: $isCallBlocked, Audio: $peerAudioStatus, Video: $peerVideoStatus")
        
        // If blocked (is_blocked = true), disable both buttons
        if (isCallBlocked) {
            Log.d("CallButtons", "User is BLOCKED - disabling both audio and video buttons")
            mainHandler.post {
                // DISABLED - Gray for both buttons
                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_grey))
                ivAudioCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvAudioCall.isEnabled = false
                
                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_grey))
                ivVideoCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvVideoCall.isEnabled = false
            }
            return
        }
        
        // Check individual audio and video status (0 = disabled, 1 = enabled)
        val isAudioEnabled = peerAudioStatus == 1
        val isVideoEnabled = peerVideoStatus == 1
        
        Log.d("CallButtons", "Final status - Audio: $isAudioEnabled ($peerAudioStatus), Video: $isVideoEnabled ($peerVideoStatus)")
        
        mainHandler.post {
            // Audio button state - enabled only if audio is enabled (status = 1)
            if (isAudioEnabled) {
                // ENABLED - Purple
                Log.d("CallButtons", "Setting audio button to ENABLED (purple)")
                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
                ivAudioCall.setColorFilter(ContextCompat.getColor(this, R.color.white))
                cvAudioCall.isEnabled = true
            } else {
                // DISABLED - Gray
                Log.d("CallButtons", "Setting audio button to DISABLED (gray) - AudioEnabled: $isAudioEnabled")
                cvAudioCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_grey))
                ivAudioCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvAudioCall.isEnabled = false
            }
            
            // Video button state - enabled only if video is enabled (status = 1)
            if (isVideoEnabled) {
                // ENABLED - Green
                Log.d("CallButtons", "Setting video button to ENABLED (green)")
                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.green))
                ivVideoCall.setColorFilter(ContextCompat.getColor(this, R.color.white))
                cvVideoCall.isEnabled = true
            } else {
                // DISABLED - Gray
                Log.d("CallButtons", "Setting video button to DISABLED (gray) - VideoEnabled: $isVideoEnabled")
                cvVideoCall.setCardBackgroundColor(ContextCompat.getColor(this, R.color.light_grey))
                ivVideoCall.setColorFilter(ContextCompat.getColor(this, R.color.grey_medium))
                cvVideoCall.isEnabled = false
            }
        }
    }

    private fun setupCallButtonListeners() {
        cvAudioCall.setOnClickListener {
            when {
                isCallBlocked -> {
                    Toast.makeText(this, "You are blocked by this user", Toast.LENGTH_SHORT).show()
                }
                peerAudioStatus != 1 -> {
                    Toast.makeText(this, "Audio calls are disabled for this user", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    initiateCall("audio")
                }
            }
        }
        
        cvVideoCall.setOnClickListener {
            when {
                isCallBlocked -> {
                    Toast.makeText(this, "You are blocked by this user", Toast.LENGTH_SHORT).show()
                }
                peerVideoStatus != 1 -> {
                    Toast.makeText(this, "Video calls are disabled for this user", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    initiateCall("video")
                }
            }
        }
    }

    private fun initiateCall(callType: String) {
        val userName = extractNameOnly(intent.getStringExtra("USER_NAME") ?: "User")
        val userImage = intent.getStringExtra("USER_IMAGE") ?: ""
        
        val intent = Intent(this, com.gmwapp.hima.agora.male.MaleCallConnectingActivity::class.java).apply {
            putExtra(DConstants.CALL_TYPE, callType)
            putExtra(DConstants.RECEIVER_ID, peerUserId)
            putExtra(DConstants.IMAGE, userImage)
            putExtra(DConstants.RECEIVER_NAME, userName)
            putExtra("FROM_CHAT", true)
            putExtra("CHAT_PEER_USER_ID", peerUserId)
        }
        startActivity(intent)
    }

    /**
     * Extracts the name part from username by removing trailing numbers
     * Examples: "Joy22" -> "Joy", "ZNKAK467" -> "ZNKAK"
     */
    private fun extractNameOnly(username: String): String {
        if (username.isEmpty()) return username
        
        // Remove trailing digits
        return username.replace(Regex("\\d+$"), "").trim()
    }
}

