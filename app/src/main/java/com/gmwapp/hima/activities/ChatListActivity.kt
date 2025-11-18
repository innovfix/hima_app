package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.ChatListAdapter
import com.gmwapp.hima.models.ChatConversation
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MyChatResponse
import com.google.firebase.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class ChatListActivity : AppCompatActivity() {

    private lateinit var ivBack: ImageView
    private lateinit var tvChatCount: TextView
    private lateinit var rvChatList: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var llEmptyState: LinearLayout
    private lateinit var progressBar: ProgressBar

    @Inject
    lateinit var apiManager: ApiManager

    private lateinit var chatListAdapter: ChatListAdapter
    private val conversations = ArrayList<ChatConversation>()
    private var myUserId: Int = 0
    
    // Date format for parsing timestamps from API (API returns IST timestamps)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    
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

        window.statusBarColor = ContextCompat.getColor(this, R.color.white)

        // ✅ SIMPLE: Set status bar icons to LIGHT (white) - works on all devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
    }

    private fun setupUserId() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        myUserId = userData?.id ?: 0

        if (myUserId == 0) {
            Log.e("ChatListActivity", "User ID is invalid!")
            showEmptyState()
            progressBar.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        chatListAdapter = ChatListAdapter(this, conversations) { conversation ->
            // Open ChatActivityInHouse when conversation is clicked (same as FriendsTabFragment)
            val intent = Intent(this, ChatActivityInHouse::class.java)
            // Convert userId back to Int for ChatActivityInHouse
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
        if (myUserId == 0) {
            swipeRefreshLayout.isRefreshing = false
            showEmptyState()
            return
        }

        Log.d("ChatListActivity", "Loading chat conversations from API for user: $myUserId")
        progressBar.visibility = View.VISIBLE
        llEmptyState.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = true

        // Call API to get chat list
        apiManager.getMyChat(myUserId, object : NetworkCallback<MyChatResponse> {
            override fun onResponse(call: Call<MyChatResponse>, response: Response<MyChatResponse>) {
                swipeRefreshLayout.isRefreshing = false
                progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody?.success == true && responseBody.data != null) {
                        val chats = responseBody.data.chats
                        Log.d("ChatListActivity", "✅ Received ${chats.size} chats from API")

                        // Convert API response to ChatConversation objects
                        val conversations = chats.map { chatItem ->
                            val lastMessage = chatItem.lastMessage
                            val lastMessageTime = if (lastMessage != null) {
                                try {
                                    val date = dateFormat.parse(lastMessage.timestamp)
                                    if (date != null) {
                                        Timestamp(date)
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    Log.e("ChatListActivity", "Error parsing timestamp: ${lastMessage.timestamp}", e)
                                    null
                                }
                            } else {
                                null
                            }

                            ChatConversation(
                                threadId = chatItem.chatId,
                                userId = chatItem.user.id.toString(),
                                userName = chatItem.user.name,
                                userImage = chatItem.user.image ?: "",
                                lastMessage = lastMessage?.message ?: "",
                                lastMessageTime = lastMessageTime,
                                unreadCount = chatItem.unreadCount,
                                isOnline = false
                            )
                        }

                        // Sort by last message time (newest first)
                        val sortedConversations = conversations.sortedByDescending {
                            it.lastMessageTime?.toDate()?.time ?: 0L
                        }

                        Log.d("ChatListActivity", "✅ Converted to ${sortedConversations.size} conversations")
                        updateUI(sortedConversations)
                    } else {
                        Log.e("ChatListActivity", "❌ API response unsuccessful or data is null")
                        updateUI(emptyList())
                    }
                } else {
                    Log.e("ChatListActivity", "❌ API call failed: ${response.code()}")
                    updateUI(emptyList())
                }
            }

            override fun onFailure(call: Call<MyChatResponse>, t: Throwable) {
                swipeRefreshLayout.isRefreshing = false
                progressBar.visibility = View.GONE
                Log.e("ChatListActivity", "❌ Error loading chat conversations: ${t.message}", t)
                updateUI(emptyList())
            }

            override fun onNoNetwork() {
                swipeRefreshLayout.isRefreshing = false
                progressBar.visibility = View.GONE
                Log.e("ChatListActivity", "❌ No network connection")
                updateUI(emptyList())
            }
        })
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

