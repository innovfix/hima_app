package com.gmwapp.hima.fragments

import com.gmwapp.hima.utils.PinnedChatsPrefsHelper
import com.gmwapp.hima.utils.showAppToast

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.ChatActivityInHouse
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.activities.UserProfileDetailActivity
import com.gmwapp.hima.adapters.FriendsAdapter
import com.gmwapp.hima.adapters.ChatListAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentFriendsTabBinding
import com.gmwapp.hima.models.ChatConversation
import com.gmwapp.hima.retrofit.responses.FriendData
import com.gmwapp.hima.retrofit.responses.toFriendData
import com.gmwapp.hima.retrofit.responses.ReceivedFriendRequestsResponse
import com.gmwapp.hima.retrofit.responses.ChatItem
import com.gmwapp.hima.retrofit.responses.MyChatResponse
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.viewmodels.FriendRequestViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class FriendsTabFragment : Fragment() {

    @Inject
    lateinit var apiManager: ApiManager

    private lateinit var binding: FragmentFriendsTabBinding
    private lateinit var adapter: FriendsAdapter
    private lateinit var chatAdapter: ChatListAdapter
    private var friendsList = ArrayList<FriendData>()
    private var chatConversations = ArrayList<ChatConversation>()
    /** Unsorted list from last my_chat API response; re-sorted when pin toggles. */
    private var lastLoadedChatConversations: List<ChatConversation> = emptyList()
    private var tabType: Int = TYPE_FRIENDS
    private val friendRequestViewModel: FriendRequestViewModel by viewModels()
    private var requestIdMap = mutableMapOf<Int, Int>() // Maps friend_id to request_id
    private val db by lazy { FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "himadatabase") }
    private val conversationsMap = mutableMapOf<String, ChatConversation>()
    private var currentSearchQuery: String = ""
    
    // Date format for parsing timestamps from API (API returns IST timestamps)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    
    // Auto-refresh handler
    private val autoRefreshHandler = Handler(Looper.getMainLooper())
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            if (isAdded) {
                Log.d("FriendsTab", "🔄 Auto-refreshing friends list...")
                loadData()
                autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL)
            }
        }
    }

    companion object {
        const val TYPE_CHAT = 0
        const val TYPE_FRIENDS = 1
        const val TYPE_MY_REQUESTS = 2
        const val TYPE_THEIR_REQUESTS = 3
        /** Creator Chat tab — Friends sub-list (POST my_chat/friends). */
        const val TYPE_CHAT_FRIENDS = 4
        /** Creator Chat tab — General sub-list (POST my_chat/general). */
        const val TYPE_CHAT_GENERAL = 5
        private const val ARG_TYPE = "type"
        private const val AUTO_REFRESH_INTERVAL = 30_000000L // 30 seconds

        fun newInstance(type: Int): FriendsTabFragment {
            val fragment = FriendsTabFragment()
            val args = Bundle()
            args.putInt(ARG_TYPE, type)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabType = arguments?.getInt(ARG_TYPE, TYPE_FRIENDS) ?: TYPE_FRIENDS
    }

    private fun isChatListTab(): Boolean =
        tabType == TYPE_CHAT || tabType == TYPE_CHAT_FRIENDS || tabType == TYPE_CHAT_GENERAL

    /**
     * Public method called from ChatListActivity to perform search
     */
    fun performSearch(query: String) {
        currentSearchQuery = query
        Log.d("FriendsTab", "🔍 performSearch called with query: '$query', tabType: $tabType")
        loadData()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFriendsTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d("FriendsTab", "🎬 onViewCreated - tabType: $tabType")
        
        setupRecyclerView()
        if (requireActivity() is MainActivity && parentFragment !is CreatorChatFragment) {
            val basePaddingTop = binding.rvFriends.paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(binding.rvFriends) { view, insets ->
                val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(
                    view.paddingLeft,
                    basePaddingTop + statusBarInset,
                    view.paddingRight,
                    view.paddingBottom
                )
                insets
            }
            ViewCompat.requestApplyInsets(binding.rvFriends)
        }
        setupSwipeRefresh()
        setupObservers()
        
        // Initially show RecyclerView (even if empty)
        binding.rvFriends.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        
        // For chat tab(s), load data immediately
        if (isChatListTab()) {
            Log.d("FriendsTab", "💬 Chat tab - loading conversations immediately")
            loadChatConversations()
        }

        // Start auto-refresh
        startAutoRefresh()
    }

    override fun onResume() {
        super.onResume()
        Log.d("FriendsTab", "▶️ onResume - tabType: $tabType")
        // Call API/Load data when entering this tab
        if (isChatListTab()) {
            // Refresh chat conversations from API
            Log.d("FriendsTab", "💬 Chat tab resumed - refreshing chat conversations")
            loadChatConversations()
        } else {
            loadData()
        }
        
        // Restart auto-refresh
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        // Stop auto-refresh when not visible
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        Log.d("FriendsTab", "⏸️ Stopped auto-refresh")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up handler
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        Log.d("FriendsTab", "🗑️ Cleaned up auto-refresh handler")
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        Log.d("FriendsTab", "👁️ setUserVisibleHint: $isVisibleToUser, isResumed: $isResumed, tabType: $tabType")
        // Call API/Load data when tab becomes visible
        if (isVisibleToUser && isResumed) {
            if (isChatListTab()) {
                Log.d("FriendsTab", "💬 Chat tab visible - loading conversations")
                loadChatConversations()
            } else {
                loadData()
            }
        }
    }

    private fun setupObservers() {
        // Observe friends list
        friendRequestViewModel.friendsListLiveData.observe(viewLifecycleOwner, Observer { response ->
            binding.swipeRefresh.isRefreshing = false
            
            if (response == null) return@Observer
            
            friendsList.clear()
            requestIdMap.clear()
            
            if (response.success && response.data != null) {
                response.data.forEach { friendData ->
                    friendsList.add(friendData.toFriendData())
                    requestIdMap[friendData.friend_data.id] = friendData.request_id
                }
                
                // Check chat history for Friends tab
                if (tabType == TYPE_FRIENDS) {
                    checkChatHistoryForFriends()
                }
            }
            
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
            updateEmptyState()
        })
        
        // Observe my friend requests
        friendRequestViewModel.myFriendRequestsLiveData.observe(viewLifecycleOwner, Observer { response ->
            binding.swipeRefresh.isRefreshing = false
            
            if (response == null) return@Observer
            
            friendsList.clear()
            requestIdMap.clear()
            
            if (response.success && response.data != null) {
                response.data.forEach { requestData ->
                    friendsList.add(requestData.toFriendData())
                    requestIdMap[requestData.receiver_data.id] = requestData.request_id
                }
            }
            
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
            updateEmptyState()
        })

        // Observe received friend requests
        friendRequestViewModel.receivedFriendRequestsLiveData.observe(viewLifecycleOwner, Observer { response ->
            binding.swipeRefresh.isRefreshing = false
            
            if (response == null) return@Observer
            
            friendsList.clear()
            requestIdMap.clear()
            
            if (response.success && response.data != null) {
                response.data.forEach { requestData ->
                    friendsList.add(requestData.toFriendData())
                    requestIdMap[requestData.sender_data.id] = requestData.request_id
                }
            }
            
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
            updateEmptyState()
        })

        // Observe accept/reject response
        friendRequestViewModel.sendFriendRequestLiveData.observe(viewLifecycleOwner, Observer { response ->
            if (response != null && response.success) {
                requireContext().showAppToast(response.message, Toast.LENGTH_SHORT)
                
                // Clear the LiveData to prevent re-triggering with old data
                friendRequestViewModel.sendFriendRequestLiveData.value = null
                
                // Clear the UI list immediately so old data doesn't show
                friendsList.clear()
                requestIdMap.clear()
                if (::adapter.isInitialized) {
                    adapter.notifyDataSetChanged()
                }
                
                // Show loading state
                binding.swipeRefresh.isRefreshing = true
                
                // Refresh tab counts in parent activity
                refreshParentTabCounts()
                
                // Give backend time to process, then reload fresh data
                binding.root.postDelayed({
                    loadData()
                }, 500)
            }
        })

        // Observe errors
        friendRequestViewModel.friendRequestErrorLiveData.observe(viewLifecycleOwner, Observer { error ->
            binding.swipeRefresh.isRefreshing = false
            requireContext().showAppToast(error, Toast.LENGTH_SHORT)
        })
    }

    private fun loadData() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        
        Log.d("FriendsTab", "🔄 loadData called for tabType: $tabType, search: '$currentSearchQuery'")
        binding.swipeRefresh.isRefreshing = true
        
        // Convert empty string to null for API
        val searchParam = if (currentSearchQuery.isEmpty()) null else currentSearchQuery
        
        when (tabType) {
            TYPE_CHAT, TYPE_CHAT_FRIENDS, TYPE_CHAT_GENERAL -> {
                Log.d("FriendsTab", "📞 Loading chat data...")
                loadChatConversations()
            }
            TYPE_FRIENDS -> {
                // Clear old data to force fresh fetch
                friendRequestViewModel.friendsListLiveData.value = null
                friendRequestViewModel.getFriendsList(userData.id, searchParam)
            }
            TYPE_MY_REQUESTS -> {
                // Clear old data to force fresh fetch
                friendRequestViewModel.myFriendRequestsLiveData.value = null
                friendRequestViewModel.getMyFriendRequests(userData.id, searchParam)
            }
            TYPE_THEIR_REQUESTS -> {
                // Clear old data to force fresh fetch
                friendRequestViewModel.receivedFriendRequestsLiveData.value = null
                friendRequestViewModel.getReceivedFriendRequests(userData.id, searchParam)
            }
        }
    }

    private fun updateEmptyState() {
        when (tabType) {
            TYPE_CHAT -> {
                binding.emptyStateTitle.text = getString(R.string.chat_empty_state_title)
                binding.emptyStateSubtitle.text = getString(R.string.chat_empty_state_subtitle)
            }
            TYPE_CHAT_FRIENDS -> {
                binding.emptyStateTitle.text = getString(R.string.chat_friends_empty_state_title)
                binding.emptyStateSubtitle.text = getString(R.string.chat_friends_empty_state_subtitle)
            }
            TYPE_CHAT_GENERAL -> {
                binding.emptyStateTitle.text = getString(R.string.chat_general_empty_state_title)
                binding.emptyStateSubtitle.text = getString(R.string.chat_general_empty_state_subtitle)
            }
            else -> {
                binding.emptyStateTitle.text = getString(R.string.friends_empty_state_title)
                binding.emptyStateSubtitle.text = getString(R.string.friends_empty_state_subtitle)
            }
        }

        val isEmpty = if (isChatListTab()) {
            chatConversations.isEmpty()
        } else {
            friendsList.isEmpty()
        }

        Log.d("FriendsTab", "📊 updateEmptyState - tabType: $tabType, isEmpty: $isEmpty, chatSize: ${chatConversations.size}, friendsSize: ${friendsList.size}")

        if (isEmpty) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvFriends.visibility = View.GONE
            Log.d("FriendsTab", "👁️ Showing empty state")
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvFriends.visibility = View.VISIBLE
            Log.d("FriendsTab", "👁️ Showing RecyclerView with data")
        }
    }

    private fun setupRecyclerView() {
        Log.d("FriendsTab", "🎯 setupRecyclerView called for tabType: $tabType")
        
        // Setup Chat adapter
        chatAdapter = ChatListAdapter(
            requireActivity(),
            chatConversations,
            onItemClick = { conversation ->
                // Optimistically clear the badge on tap — server mark-read + next
                // onResume refetch will keep the state consistent.
                chatAdapter.markConversationAsRead(conversation.userId)
                val intent = Intent(context, ChatActivityInHouse::class.java)
                val userId = conversation.userId.toIntOrNull() ?: -1
                intent.putExtra("USER_ID", userId)
                intent.putExtra("USER_NAME", conversation.userName)
                intent.putExtra("USER_IMAGE", conversation.userImage)
                startActivity(intent)
            },
            apiManager = apiManager,
            onPinToggled = {
                if (isAdded && isChatListTab()) {
                    val refreshed = lastLoadedChatConversations.map { conv ->
                        conv.copy(isPinned = PinnedChatsPrefsHelper.isPinned(requireContext(), conv.userId))
                    }
                    updateChatUI(sortChatConversationsForCurrentTab(refreshed))
                }
            }
        )
        Log.d("FriendsTab", "✅ Chat adapter created")
        
        // Setup Friends adapter
        adapter = FriendsAdapter(
            requireActivity(),
            friendsList,
            tabType,
            onChatClick = { friend ->
                // Same peer may appear in the Chat tab list — clear badge if present.
                chatAdapter.markConversationAsRead(friend.friend_id.toString())
                val intent = Intent(requireContext(), ChatActivityInHouse::class.java)
                intent.putExtra("USER_ID", friend.friend_id)
                intent.putExtra("USER_NAME", friend.name)
                intent.putExtra("USER_IMAGE", friend.image)
                startActivity(intent)
            },
            onAcceptClick = { friend ->
                // Accept friend request by calling API with status=1
                acceptFriendRequest(friend)
            },
            onRemoveClick = { friend ->
                // Remove friend or reject request
                when (tabType) {
                    TYPE_MY_REQUESTS -> {
                        // Cancel friend request by calling API with status=2
                        cancelFriendRequest(friend)
                    }
                    TYPE_THEIR_REQUESTS -> {
                        // Reject friend request by calling API with status=2
                        rejectFriendRequest(friend)
                    }
                    else -> {
                        requireContext().showAppToast("Removed ${friend.name}", Toast.LENGTH_SHORT)
                        // TODO: Call API to remove friend
                        removeFriend(friend)
                    }
                }
            },
            onProfileClick = { friend ->
                // Open profile detail
                val intent = Intent(requireContext(), UserProfileDetailActivity::class.java)
                intent.putExtra(DConstants.USER_ID, friend.friend_id)
                intent.putExtra("USER_NAME", friend.name)
                intent.putExtra("USER_IMAGE", friend.image)
                intent.putExtra("USER_LANGUAGE", friend.language)
                intent.putExtra("USER_INTERESTS", "")
                intent.putExtra("USER_ABOUT", "")
                intent.putExtra("USER_AGE", 0)
                intent.putExtra("AUDIO_STATUS", friend.audio_status)
                intent.putExtra("VIDEO_STATUS", friend.video_status)
                startActivity(intent)
            }
        )

        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        
        // Set appropriate adapter based on tab type
        if (isChatListTab()) {
            Log.d("FriendsTab", "✅ Setting chat adapter to RecyclerView")
            binding.rvFriends.adapter = chatAdapter
        } else {
            Log.d("FriendsTab", "✅ Setting friends adapter to RecyclerView")
        binding.rvFriends.adapter = adapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }
    }

    private fun cancelFriendRequest(friend: FriendData) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        
        friendRequestViewModel.sendFriendRequest(
            senderId = userData.id,
            receiverId = friend.friend_id,
            status = 2
        )
    }

    private fun acceptFriendRequest(friend: FriendData) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        
        friendRequestViewModel.sendFriendRequest(
            senderId = friend.friend_id,
            receiverId = userData.id,
            status = 1
        )
    }

    private fun rejectFriendRequest(friend: FriendData) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        
        friendRequestViewModel.sendFriendRequest(
            senderId = friend.friend_id,
            receiverId = userData.id,
            status = 3
        )
    }

    private fun loadChatConversations() {
        if (!isAdded) {
            Log.e("FriendsTab", "❌ Fragment not added, skipping")
            return
        }
        
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val myUserId = userData?.id ?: 0
        
        Log.d("FriendsTab", "🔵 loadChatConversations called. MyUserId: $myUserId, Search: '$currentSearchQuery'")
        
        if (myUserId == 0) {
            Log.e("FriendsTab", "❌ User ID is invalid!")
            binding.swipeRefresh.isRefreshing = false
            updateChatUI(emptyList())
            return
        }
        
        Log.d("FriendsTab", "📱 Loading chat conversations from API for user: $myUserId")
        binding.swipeRefresh.isRefreshing = true
        
        // Pass search query to API (null if empty)
        val searchParam = if (currentSearchQuery.isEmpty()) null else currentSearchQuery

        val callback = object : NetworkCallback<MyChatResponse> {
            override fun onResponse(call: Call<MyChatResponse>, response: Response<MyChatResponse>) {
                if (!isAdded) return
                binding.swipeRefresh.isRefreshing = false
                if (response.isSuccessful) {
                    processMyChatResponse(response.body())
                } else {
                    Log.e("FriendsTab", "❌ API call failed: ${response.code()}")
                    updateChatUI(emptyList())
                }
            }

            override fun onFailure(call: Call<MyChatResponse>, t: Throwable) {
                if (!isAdded) return
                binding.swipeRefresh.isRefreshing = false
                Log.e("FriendsTab", "❌ Error loading chat conversations: ${t.message}", t)
                updateChatUI(emptyList())
            }

            override fun onNoNetwork() {
                if (!isAdded) return
                binding.swipeRefresh.isRefreshing = false
                Log.e("FriendsTab", "❌ No network connection")
                updateChatUI(emptyList())
            }
        }

        when (tabType) {
            TYPE_CHAT_FRIENDS -> apiManager.getMyChatFriends(myUserId, searchParam, 100, 0, callback)
            TYPE_CHAT_GENERAL -> apiManager.getMyChatGeneral(myUserId, searchParam, 100, 0, callback)
            else -> apiManager.getMyChat(myUserId, searchParam, 100, 0, callback)
        }
    }

    private fun processMyChatResponse(responseBody: MyChatResponse?) {
        if (!isAdded) return
        if (responseBody?.success == true && responseBody.data != null) {
            val chats = responseBody.data.chats
            Log.d("FriendsTab", "✅ Received ${chats.size} chats from API")
            val conversations = chats.map { mapChatItemToConversation(it) }
            lastLoadedChatConversations = conversations
            val sorted = sortChatConversationsForCurrentTab(conversations)
            Log.d("FriendsTab", "✅ Converted to ${sorted.size} conversations")
            updateChatUI(sorted)
        } else {
            Log.e("FriendsTab", "❌ API response unsuccessful or data is null")
            updateChatUI(emptyList())
        }
    }

    private fun mapChatItemToConversation(chatItem: ChatItem): ChatConversation {
        val lastMessage = chatItem.lastMessage
        val lastMessageTime = if (lastMessage != null) {
            try {
                val date = dateFormat.parse(lastMessage.timestamp)
                if (date != null) Timestamp(date) else null
            } catch (e: Exception) {
                Log.e("FriendsTab", "Error parsing timestamp: ${lastMessage.timestamp}", e)
                null
            }
        } else {
            null
        }
        val u = chatItem.user
        return ChatConversation(
            threadId = chatItem.chatId,
            userId = u.id.toString(),
            userName = u.name,
            userImage = u.image ?: "",
            lastMessage = lastMessage?.message ?: "",
            lastMessageType = lastMessage?.messageType ?: "text",
            lastMessageTime = lastMessageTime,
            unreadCount = chatItem.unreadCount,
            isOnline = false,
            audioStatus = u.audioStatus ?: 1,
            videoStatus = u.videoStatus ?: 1,
            coinPerMinAudio = u.coinPerMinAudio ?: 10,
            coinPerMinVideo = u.coinPerMinVideo ?: 60,
            language = u.language,
            isPinned = if (isAdded) {
                PinnedChatsPrefsHelper.isPinned(requireContext(), u.id.toString())
            } else {
                false
            }
        )
    }

    private fun sortChatConversationsForCurrentTab(
        conversations: List<ChatConversation>
    ): List<ChatConversation> {
        if (!isAdded) return conversations
        val ctx = requireContext()
        val (pinnedConv, unpinned) = conversations.partition { it.isPinned }
        val order = PinnedChatsPrefsHelper.getPinnedIds(ctx)
        val sortedPinned = pinnedConv.sortedBy { conv ->
            val i = order.indexOf(conv.userId)
            if (i >= 0) i else Int.MAX_VALUE
        }

        val sortedUnpinned = if (tabType == TYPE_CHAT_FRIENDS) {
            val withTime = unpinned.filter { it.lastMessageTime != null }
                .sortedByDescending { it.lastMessageTime?.toDate()?.time ?: 0L }
            val withoutTime = unpinned.filter { it.lastMessageTime == null }
                .sortedBy { it.userName.lowercase(Locale.getDefault()) }
            withTime + withoutTime
        } else {
            unpinned.sortedByDescending { it.lastMessageTime?.toDate()?.time ?: 0L }
        }

        return sortedPinned + sortedUnpinned
    }
    
    private fun updateChatUI(conversationsList: List<ChatConversation>) {
        if (!isAdded) return
        if (conversationsList.isEmpty()) {
            lastLoadedChatConversations = emptyList()
        }

        Log.d("FriendsTab", "📊 updateChatUI called with ${conversationsList.size} conversations")
        Log.d("FriendsTab", "Conversations: ${conversationsList.map { "${it.userName} (${it.userId})" }}")
        
        // Use the adapter's updateConversations method like ChatListActivity does
        if (::chatAdapter.isInitialized) {
            Log.d("FriendsTab", "🔄 Updating chatAdapter with ${conversationsList.size} conversations")
            chatAdapter.updateConversations(conversationsList)
        } else {
            Log.e("FriendsTab", "❌ chatAdapter not initialized!")
        }
        
        updateEmptyState()
    }

    private fun loadMockData() {
        // TODO: Replace with actual API call
        friendsList.clear()

        when (tabType) {
            TYPE_CHAT -> {
                // Mock chat data - All users including friends and recent chats
                friendsList.addAll(
                    listOf(
                        FriendData(
                            id = 1,
                            friend_id = 101,
                            name = "Priya Sharma",
                            image = "https://via.placeholder.com/150",
                            language = "Hindi",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        ),
                        FriendData(
                            id = 2,
                            friend_id = 102,
                            name = "Ananya Reddy",
                            image = "https://via.placeholder.com/150",
                            language = "Telugu",
                            audio_status = 1,
                            video_status = 0,
                            is_online = false,
                            last_seen = "2 hours ago"
                        ),
                        FriendData(
                            id = 3,
                            friend_id = 103,
                            name = "Sneha Patel",
                            image = "https://via.placeholder.com/150",
                            language = "Gujarati",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        ),
                        FriendData(
                            id = 8,
                            friend_id = 108,
                            name = "Neha Sharma",
                            image = "https://via.placeholder.com/150",
                            language = "Punjabi",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        ),
                        FriendData(
                            id = 9,
                            friend_id = 109,
                            name = "Anjali Verma",
                            image = "https://via.placeholder.com/150",
                            language = "Hindi",
                            audio_status = 1,
                            video_status = 0,
                            is_online = false,
                            last_seen = "5 mins ago"
                        )
                    )
                )
            }
            TYPE_FRIENDS -> {
                // Mock friends data
                friendsList.addAll(
                    listOf(
                        FriendData(
                            id = 1,
                            friend_id = 101,
                            name = "Priya Sharma",
                            image = "https://via.placeholder.com/150",
                            language = "Hindi",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        ),
                        FriendData(
                            id = 2,
                            friend_id = 102,
                            name = "Ananya Reddy",
                            image = "https://via.placeholder.com/150",
                            language = "Telugu",
                            audio_status = 1,
                            video_status = 0,
                            is_online = false,
                            last_seen = "2 hours ago"
                        ),
                        FriendData(
                            id = 3,
                            friend_id = 103,
                            name = "Sneha Patel",
                            image = "https://via.placeholder.com/150",
                            language = "Gujarati",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        )
                    )
                )
            }
            TYPE_MY_REQUESTS -> {
                // Mock "My Requests" - requests I sent
                friendsList.addAll(
                    listOf(
                        FriendData(
                            id = 6,
                            friend_id = 106,
                            name = "Kavya Nair",
                            image = "https://via.placeholder.com/150",
                            language = "Malayalam",
                            audio_status = 1,
                            video_status = 1,
                            is_online = false,
                            last_seen = "5 hours ago"
                        ),
                        FriendData(
                            id = 7,
                            friend_id = 107,
                            name = "Meera Kapoor",
                            image = "https://via.placeholder.com/150",
                            language = "Hindi",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        )
                    )
                )
            }
            TYPE_THEIR_REQUESTS -> {
                // Mock "Their Requests" - requests I received
                friendsList.addAll(
                    listOf(
                        FriendData(
                            id = 4,
                            friend_id = 104,
                            name = "Divya Kumar",
                            image = "https://via.placeholder.com/150",
                            language = "Tamil",
                            audio_status = 1,
                            video_status = 1,
                            is_online = false,
                            last_seen = "1 day ago"
                        ),
                        FriendData(
                            id = 5,
                            friend_id = 105,
                            name = "Riya Singh",
                            image = "https://via.placeholder.com/150",
                            language = "Hindi",
                            audio_status = 1,
                            video_status = 0,
                            is_online = false,
                            last_seen = "3 days ago"
                        )
                    )
                )
            }
        }

        adapter.notifyDataSetChanged()
        binding.swipeRefresh.isRefreshing = false

        // Show/hide empty state
        if (friendsList.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvFriends.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvFriends.visibility = View.VISIBLE
        }
    }

    private fun removeFriend(friend: FriendData) {
        friendsList.remove(friend)
        adapter.notifyDataSetChanged()

        // Show empty state if list is empty
        if (friendsList.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvFriends.visibility = View.GONE
        }
    }

    private fun startAutoRefresh() {
        // Start periodic refresh
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL)
        Log.d("FriendsTab", "✅ Started auto-refresh every ${AUTO_REFRESH_INTERVAL / 1000} seconds")
    }
    
    /**
     * Check if chat history exists for each friend in the Friends tab
     * This checks if a Firestore chat thread document exists (lightweight check)
     */
    private fun checkChatHistoryForFriends() {
        if (friendsList.isEmpty()) return
        
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val myUserId = userData?.id?.toString() ?: return
        
        Log.d("FriendsTab", "🔍 Checking chat history for ${friendsList.size} friends")
        
        friendsList.forEach { friend ->
            val friendId = friend.friend_id.toString()
            
            // Generate thread ID (same logic as ChatActivity)
            val threadId = if (myUserId.toInt() < friendId.toInt()) {
                "${myUserId}_${friendId}"
            } else {
                "${friendId}_${myUserId}"
            }
            
            // Check if chat thread exists and has messages
            db.collection("chats")
                .document(threadId)
                .collection("messages")
                .limit(1)
                .get()
                .addOnSuccessListener { messagesSnapshot ->
                    if (!isAdded) return@addOnSuccessListener
                    
                    // Set hasChatHistory based on whether messages exist
                    friend.hasChatHistory = !messagesSnapshot.isEmpty
                    
                    Log.d("FriendsTab", "💬 ${friend.name}: hasChatHistory = ${friend.hasChatHistory}")
                    
                    // Update UI
                    if (::adapter.isInitialized) {
                        adapter.notifyDataSetChanged()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FriendsTab", "❌ Error checking chat for ${friend.name}: ${e.message}")
                    friend.hasChatHistory = false
                }
        }
    }
    
    /**
     * Refresh tab counts in parent activity (works for both FriendsListActivity and ChatListActivity)
     */
    private fun refreshParentTabCounts() {
        activity?.let { parentActivity ->
            when (parentActivity) {
                is com.gmwapp.hima.activities.FriendsListActivity -> {
                    parentActivity.refreshTabCounts()
                    Log.d("FriendsTab", "🔄 Refreshing tab counts in FriendsListActivity")
                }
                is com.gmwapp.hima.activities.ChatListActivity -> {
                    parentActivity.refreshTabCounts()
                    Log.d("FriendsTab", "🔄 Refreshing tab counts in ChatListActivity")
                }
                else -> {
                    Log.w("FriendsTab", "⚠️ Parent activity doesn't support tab count refresh")
                }
            }
        }
    }
}

