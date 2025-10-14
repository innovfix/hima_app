package com.gmwapp.hima.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.activities.ChatActivity
import com.gmwapp.hima.activities.UserProfileDetailActivity
import com.gmwapp.hima.adapters.FriendsAdapter
import com.gmwapp.hima.adapters.ChatListAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentFriendsTabBinding
import com.gmwapp.hima.models.ChatConversation
import com.gmwapp.hima.retrofit.responses.FriendData
import com.gmwapp.hima.retrofit.responses.toFriendData
import com.gmwapp.hima.retrofit.responses.ReceivedFriendRequestsResponse
import com.gmwapp.hima.viewmodels.FriendRequestViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FriendsTabFragment : Fragment() {

    private lateinit var binding: FragmentFriendsTabBinding
    private lateinit var adapter: FriendsAdapter
    private lateinit var chatAdapter: ChatListAdapter
    private var friendsList = ArrayList<FriendData>()
    private var chatConversations = ArrayList<ChatConversation>()
    private var tabType: Int = TYPE_FRIENDS
    private val friendRequestViewModel: FriendRequestViewModel by viewModels()
    private var requestIdMap = mutableMapOf<Int, Int>() // Maps friend_id to request_id
    private val db by lazy { Firebase.firestore }
    private val conversationsMap = mutableMapOf<String, ChatConversation>()

    companion object {
        const val TYPE_CHAT = 0
        const val TYPE_FRIENDS = 1
        const val TYPE_MY_REQUESTS = 2
        const val TYPE_THEIR_REQUESTS = 3
        private const val ARG_TYPE = "type"

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
        setupSwipeRefresh()
        setupObservers()
        
        // Initially show RecyclerView (even if empty)
        binding.rvFriends.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        
        // For chat tab, load data immediately
        if (tabType == TYPE_CHAT) {
            Log.d("FriendsTab", "💬 Chat tab - loading conversations immediately")
            loadChatConversations()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("FriendsTab", "▶️ onResume - tabType: $tabType")
        // Call API/Load data when entering this tab
        if (tabType != TYPE_CHAT) {
            loadData()
        }
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        Log.d("FriendsTab", "👁️ setUserVisibleHint: $isVisibleToUser, isResumed: $isResumed, tabType: $tabType")
        // Call API/Load data when tab becomes visible
        if (isVisibleToUser && isResumed) {
            if (tabType == TYPE_CHAT) {
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
                Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                
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
                
                // Give backend time to process, then reload fresh data
                binding.root.postDelayed({
                    loadData()
                }, 500)
            }
        })

        // Observe errors
        friendRequestViewModel.friendRequestErrorLiveData.observe(viewLifecycleOwner, Observer { error ->
            binding.swipeRefresh.isRefreshing = false
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        })
    }

    private fun loadData() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        
        Log.d("FriendsTab", "🔄 loadData called for tabType: $tabType")
        binding.swipeRefresh.isRefreshing = true
        
        when (tabType) {
            TYPE_CHAT -> {
                Log.d("FriendsTab", "📞 Loading chat data...")
                loadChatConversations()
            }
            TYPE_FRIENDS -> {
                // Clear old data to force fresh fetch
                friendRequestViewModel.friendsListLiveData.value = null
                friendRequestViewModel.getFriendsList(userData.id)
            }
            TYPE_MY_REQUESTS -> {
                // Clear old data to force fresh fetch
                friendRequestViewModel.myFriendRequestsLiveData.value = null
                friendRequestViewModel.getMyFriendRequests(userData.id)
            }
            TYPE_THEIR_REQUESTS -> {
                // Clear old data to force fresh fetch
                friendRequestViewModel.receivedFriendRequestsLiveData.value = null
                friendRequestViewModel.getReceivedFriendRequests(userData.id)
            }
        }
    }

    private fun updateEmptyState() {
        val isEmpty = if (tabType == TYPE_CHAT) {
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
        chatAdapter = ChatListAdapter(requireActivity(), chatConversations) { conversation ->
            val intent = Intent(context, ChatActivity::class.java)
            val userId = conversation.userId.toIntOrNull() ?: -1
            intent.putExtra("USER_ID", userId)
            intent.putExtra("USER_NAME", conversation.userName)
            intent.putExtra("USER_IMAGE", conversation.userImage)
            startActivity(intent)
        }
        Log.d("FriendsTab", "✅ Chat adapter created")
        
        // Setup Friends adapter
        adapter = FriendsAdapter(
            requireActivity(),
            friendsList,
            tabType,
            onChatClick = { friend ->
                // Open chat
                val intent = Intent(requireContext(), ChatActivity::class.java)
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
                        Toast.makeText(context, "Removed ${friend.name}", Toast.LENGTH_SHORT).show()
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
        if (tabType == TYPE_CHAT) {
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
        val myUserId = userData?.id?.toString() ?: ""
        
        Log.d("FriendsTab", "🔵 loadChatConversations called. MyUserId: $myUserId")
        
        if (myUserId.isEmpty()) {
            Log.e("FriendsTab", "❌ User ID is empty!")
            binding.swipeRefresh.isRefreshing = false
            updateChatUI(emptyList())
            return
        }
        
        Log.d("FriendsTab", "📱 Loading chat conversations for user: $myUserId")
        binding.swipeRefresh.isRefreshing = true
        
        // Load conversations from Firebase - EXACTLY like ChatListActivity
        db.collection("chats")
            .addSnapshotListener { documents, error ->
                if (!isAdded) return@addSnapshotListener
                
                binding.swipeRefresh.isRefreshing = false
                
                if (error != null) {
                    Log.e("FriendsTab", "❌ Error: ${error.message}", error)
                    updateChatUI(emptyList())
                    return@addSnapshotListener
                }
                
                if (documents == null || documents.isEmpty) {
                    Log.d("FriendsTab", "⚠️ No chat threads found")
                    updateChatUI(emptyList())
                    return@addSnapshotListener
                }
                
                Log.d("FriendsTab", "✅ Found ${documents.size()} chat documents")
                
                // Clear old conversations
                val currentThreadIds = documents.map { it.id }.toSet()
                conversationsMap.keys.retainAll(currentThreadIds)
                
                // Process each chat thread
                for (document in documents) {
                    val threadId = document.id
                    
                    // Parse participants from thread ID (format: "userId1_userId2")
                    val participants = threadId.split("_")
                    
                    Log.d("FriendsTab", "Thread $threadId - parsed participants: $participants")
                    
                    if (participants.size != 2 || !participants.contains(myUserId)) {
                        Log.d("FriendsTab", "Skipping $threadId - not my conversation")
                        continue
                    }
                    
                    val otherUserId = participants.firstOrNull { it != myUserId }
                    if (otherUserId == null) {
                        Log.d("FriendsTab", "Skipping $threadId - no other user")
                        continue
                    }
                    
                    Log.d("FriendsTab", "✅ Processing $threadId with user $otherUserId")
                    
                    // Get user metadata
                    db.collection("chats")
                        .document(threadId)
                        .get()
                        .addOnSuccessListener { threadDoc ->
                            if (!isAdded) return@addOnSuccessListener
                            
                            val userName = threadDoc.getString("user_${otherUserId}_name") ?: "User $otherUserId"
                            val userImage = threadDoc.getString("user_${otherUserId}_image") ?: ""
                            
                            Log.d("FriendsTab", "Got metadata for $otherUserId: $userName")
                            
                            // Real-time listener for messages
                            db.collection("chats")
                                .document(threadId)
                                .collection("messages")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .limit(50)
                                .addSnapshotListener { messagesSnapshot, messageError ->
                                    if (!isAdded) {
                                        return@addSnapshotListener
                                    }
                                    
                                    if (messageError != null) {
                                        Log.e("FriendsTab", "❌ Message error for $threadId: ${messageError.message}")
                                        return@addSnapshotListener
                                    }
                                    
                                    if (messagesSnapshot == null || messagesSnapshot.isEmpty) {
                                        Log.d("FriendsTab", "⚠️ No messages in $threadId")
                                        // Still create conversation but with empty message
                                        val conversation = ChatConversation(
                                            threadId = threadId,
                                            userId = otherUserId,
                                            userName = userName,
                                            userImage = userImage,
                                            lastMessage = "",
                                            lastMessageTime = null,
                                            unreadCount = 0,
                                            isOnline = false
                                        )
                                        conversationsMap[threadId] = conversation
                                        
                                        val sortedConversations = conversationsMap.values
                                            .sortedByDescending { it.lastMessageTime?.toDate()?.time ?: 0L }
                                        updateChatUI(sortedConversations)
                                        return@addSnapshotListener
                                    }
                                    
                                    var lastMessage = ""
                                    var lastMessageTime: Timestamp? = null
                                    var unreadCount = 0
                                    
                                    Log.d("FriendsTab", "📨 Processing ${messagesSnapshot.size()} messages for $threadId")
                                    
                                    for (msgDoc in messagesSnapshot.documents) {
                                        val fromId = msgDoc.getString("from") ?: ""
                                        val text = msgDoc.getString("text") ?: ""
                                        val timestamp = msgDoc.getTimestamp("timestamp")
                                        val isRead = msgDoc.getBoolean("isRead") ?: false
                                        
                                        Log.d("FriendsTab", "Msg: from=$fromId, text='$text', isRead=$isRead")
                                        
                                        // Get the first (most recent) message
                                        if (lastMessage.isEmpty() && text.isNotEmpty()) {
                                            lastMessage = text
                                            lastMessageTime = timestamp
                                            Log.d("FriendsTab", "✅ Last message set: '$lastMessage'")
                                        }
                                        
                                        // Count unread messages from other user
                                        if (fromId == otherUserId && !isRead) {
                                            unreadCount++
                                        }
                                    }
                                    
                                    Log.d("FriendsTab", "📊 Thread $threadId FINAL: lastMsg='$lastMessage', unread=$unreadCount")
                                    
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
                                    
                                    // Update UI with sorted list (REAL-TIME!)
                                    val sortedConversations = conversationsMap.values
                                        .sortedByDescending { it.lastMessageTime?.toDate()?.time ?: 0L }
                                    updateChatUI(sortedConversations)
                                    
                                    Log.d("FriendsTab", "🔄 UI updated with ${sortedConversations.size} conversations")
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("FriendsTab", "Error loading metadata for $threadId", e)
                        }
                }
            }
    }
    
    private fun updateChatUI(conversationsList: List<ChatConversation>) {
        if (!isAdded) return
        
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
}

