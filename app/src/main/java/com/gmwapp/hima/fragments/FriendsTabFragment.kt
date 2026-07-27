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
    
    // T18/T19: parse server timestamps in the device's local timezone so a user
    // outside India sees consistent dates/times in the chat list.
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
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
        private const val AUTO_REFRESH_INTERVAL = 30_000L // 30 seconds

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
            // Hub "Friends" tab (TYPE_CHAT_FRIENDS): always open at the TOP when the
            // tab is (re)entered. Switching away and back used to leave the user
            // scrolled halfway down; scroll to 0 so it reads from the start. Scoped
            // to this tab so the main chat list keeps its position as expected.
            if (tabType == TYPE_CHAT_FRIENDS) binding.rvFriends.scrollToPosition(0)
            // Realtime: subscribe to push-driven list refresh + socket new_message
            // so a new push or live socket event reorders / increments the row
            // without waiting for the 30s poll.
            registerChatListRefreshReceiver()
            startCollectingSocketNewMessage()
        } else {
            // Friend / Requests / Sent tabs: refresh on resume, and always show the
            // list from the TOP when the tab is (re)entered. Returning from another
            // tab used to leave the user scrolled halfway down; scroll to 0 so it
            // reads from the start. (No 30s auto-refresh on these tabs — see
            // startAutoRefresh — so the list is never reordered under the user.)
            loadData()
            binding.rvFriends.scrollToPosition(0)
        }

        // Restart auto-refresh
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        // Stop auto-refresh when not visible
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        unregisterChatListRefreshReceiver()
        Log.d("FriendsTab", "⏸️ Stopped auto-refresh")
    }

    /**
     * Receives [ACTION_CHAT_LIST_REFRESH] from the OneSignal NSE / foreground
     * listener for every type=message push. Updates the visible list row
     * in-place; if the peer isn't in the list yet (new conversation), falls
     * back to a full API reload.
     */
    private var chatListRefreshReceiver: android.content.BroadcastReceiver? = null
    private var chatListRefreshReceiverRegistered: Boolean = false

    private fun registerChatListRefreshReceiver() {
        if (chatListRefreshReceiverRegistered) return
        val ctx = context ?: return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                if (!isAdded || intent == null) return
                if (intent.action != com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_LIST_REFRESH) return
                val peerId = intent.getIntExtra(
                    com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.EXTRA_PEER_ID,
                    -1
                )
                if (peerId <= 0) return
                val text = intent.getStringExtra(
                    com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.EXTRA_LAST_MESSAGE
                ).orEmpty()
                val type = intent.getStringExtra(
                    com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.EXTRA_MESSAGE_TYPE
                ) ?: "text"
                val msgId = intent.getLongExtra(
                    com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.EXTRA_MESSAGE_ID,
                    0L
                ).takeIf { it > 0L }
                applyChatListIncomingFromBroadcast(peerId, text, type, msgId)
            }
        }
        val filter = android.content.IntentFilter(
            com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_LIST_REFRESH
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(
                receiver,
                filter,
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(receiver, filter)
        }
        chatListRefreshReceiver = receiver
        chatListRefreshReceiverRegistered = true
    }

    private fun unregisterChatListRefreshReceiver() {
        if (!chatListRefreshReceiverRegistered) return
        val ctx = context ?: return
        val receiver = chatListRefreshReceiver ?: return
        runCatching { ctx.unregisterReceiver(receiver) }
        chatListRefreshReceiver = null
        chatListRefreshReceiverRegistered = false
    }

    private fun applyChatListIncomingFromBroadcast(peerId: Int, text: String, type: String, messageId: Long? = null) {
        if (!::chatAdapter.isInitialized) return
        val now = com.google.firebase.Timestamp.now()
        val suppressUnread = com.gmwapp.hima.utils.ActiveChatTracker.isActiveFor(context, peerId)
        // CM_006-live: make the card badge update live from the push (no extra
        // socket/connection needed). If the push carries a message id we dedup on
        // it (so a connected socket can't double-count). If there's no id, only
        // force the bump when the socket is DOWN — then the socket can't also
        // count it, so there's no double-count either. When the socket is up and
        // owns counting, we leave the count to it (unchanged behaviour).
        val socketDown = !com.gmwapp.hima.socket.SocketManager.getInstance().isConnected()
        val handled = chatAdapter.applyIncomingMessage(
            peerUserId = peerId.toString(),
            lastMessageText = text,
            lastMessageType = type,
            lastMessageTime = now,
            suppressUnreadIncrement = suppressUnread,
            incomingMessageId = messageId,
            forceCountUnread = (messageId == null && socketDown)
        )
        if (!handled) {
            // New peer not yet in the list — pull a fresh page so the row appears.
            loadChatConversations()
        }
    }

    /**
     * Collects [com.gmwapp.hima.socket.SocketManager.newMessage] for the lifetime
     * of `viewLifecycleOwner` (auto-cancels in `onDestroyView`). Repeats on every
     * `RESUMED` so we don't keep collecting after the tab pauses.
     */
    private fun startCollectingSocketNewMessage() {
        val owner = viewLifecycleOwner
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                com.gmwapp.hima.socket.SocketManager.getInstance().newMessage.collect { msg ->
                    if (!isAdded || !::chatAdapter.isInitialized) return@collect
                    val mySelfId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
                    val peerId = msg.fromUserId ?: return@collect
                    // Skip own outgoing echoes — the thread already handles those.
                    if (peerId == mySelfId) return@collect
                    val previewType = msg.messageType.lowercase().ifBlank { "text" }
                    val previewText = msg.message.ifBlank {
                        when (previewType) {
                            "image" -> "📷 Photo"
                            "audio" -> "🎤 Voice message"
                            "video" -> "📹 Video"
                            "file" -> "📎 File"
                            else -> ""
                        }
                    }
                    val ts = com.google.firebase.Timestamp.now()
                    val suppressUnread = com.gmwapp.hima.utils.ActiveChatTracker.isActiveFor(context, peerId)
                    val handled = chatAdapter.applyIncomingMessage(
                        peerUserId = peerId.toString(),
                        lastMessageText = previewText,
                        lastMessageType = previewType,
                        lastMessageTime = ts,
                        suppressUnreadIncrement = suppressUnread,
                        incomingMessageId = msg.id
                    )
                    if (!handled) {
                        loadChatConversations()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up handler
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        Log.d("FriendsTab", "🗑️ Cleaned up auto-refresh handler")
    }

    // T35: removed deprecated `setUserVisibleHint`. ViewPager2 + FragmentStateAdapter
    // already drive `onResume()`/`onPause()` per tab visibility, and the existing
    // [onResume] override calls `loadData()` / `loadChatConversations()` when the
    // tab actually becomes visible, so the old hint hook was redundant.

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

                // QA bug #8 — sink blocked friends to the bottom, mirroring the chat
                // list. FINAL stable partition over the server order: normal rows keep
                // their exact position, blocked rows (either direction) move to the end
                // preserving their relative order. With an un-upgraded backend every
                // flag is false, so this is a no-op and ordering is unchanged. Applied
                // before checkChatHistoryForFriends() so its per-row notifyItemChanged
                // indices resolve against the final positions.
                if (tabType == TYPE_FRIENDS) {
                    sinkBlockedFriendsToBottom()
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
                markRequestsSeen(response.data)
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
                    // Re-fetch the bottom-nav Chat badge (incl. pending friend-requests) so it
                    // updates right after accept/reject instead of waiting for onResume. Uses the
                    // same delay as loadData so it reads post-commit (server drops the
                    // friend_tabs_counts cache on the mutation, so this returns the fresh count).
                    (activity as? com.gmwapp.hima.activities.MainActivity)?.refreshChatUnreadBadge()
                }, 500)

                // Reload the sibling Friends tab so the just-accepted friend floats to the top.
                // Retry with backoff: my_chat/friends may not include the new friendship within
                // the first 500ms, and the 30s auto-refresh that used to catch this was removed —
                // so a single refresh can fire before the server commits and the friend never
                // floats (the reported bug). Each pass re-fetches + re-sorts; once the friend
                // appears, the local accept timestamp floats them to #1. Cheap + self-limiting
                // (only after an accept action).
                listOf(500L, 2000L, 4500L).forEach { d ->
                    binding.root.postDelayed({
                        if (!isAdded) return@postDelayed
                        (parentFragment as? FriendsHubFragment)?.refreshFriendsTab()
                        (parentFragment as? CreatorChatFragment)?.refreshFriendsTab()
                    }, d)
                }
            }
        })

        // Observe errors
        friendRequestViewModel.friendRequestErrorLiveData.observe(viewLifecycleOwner, Observer { error ->
            binding.swipeRefresh.isRefreshing = false
            // Single-shot consume. friendRequestErrorLiveData is a plain MutableLiveData that is
            // never reset, so its LAST error string replays to a fresh observer every time this
            // tab's view is re-created (tab re-select / onResume / rotation). That surfaced a
            // phantom "Failed to send friend request" toast on the Requests screen when the user
            // had done nothing — the reported bug. Ignore the null we post back and null it out
            // after showing so a one-off transient error can't linger as a recurring toast.
            if (error.isNullOrBlank()) return@Observer
            requireContext().showAppToast(error, Toast.LENGTH_SHORT)
            friendRequestViewModel.friendRequestErrorLiveData.value = null
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

    /**
     * B_010: the user is looking at the Requests list, so everything in it counts as seen.
     * Advance the watermark to the newest id here and drop the nav badge — it used to count
     * every pending request forever, so it never cleared however often she opened this tab.
     *
     * Three guards, all load-bearing — each one is a way to silence a request the user
     * never actually saw, which would be worse than the bug being fixed:
     *  - only the received-requests tab. This observer belongs to a fragment-scoped
     *    view-model, but the fragment class backs every tab, so tabType is what makes
     *    "she saw the requests" true rather than merely likely.
     *  - only while this tab is actually on screen. ViewPager2's FragmentStateAdapter
     *    builds the adjacent page ahead of time and caps it at STARTED, so today nothing
     *    loads offscreen — but that is a property of where loadData() happens to be
     *    called from, not a guarantee. isResumed states the requirement outright so a
     *    later load-on-create cannot quietly clear the badge from the next tab over.
     *  - only an unfiltered list. A search shows a subset; treating that as "seen all"
     *    would silence requests she never laid eyes on.
     */
    private fun markRequestsSeen(
        data: List<com.gmwapp.hima.retrofit.responses.ReceivedFriendRequestData>
    ) {
        if (tabType != TYPE_THEIR_REQUESTS) return
        if (!isResumed) return
        if (currentSearchQuery.isNotBlank()) return
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        val newest = data.maxOfOrNull { it.request_id } ?: return

        val moved = com.gmwapp.hima.utils.RequestsSeenPrefs.markSeen(requireContext(), userId, newest)
        if (!moved) return

        // Watermark moved, so the badge is now stale by definition — refresh both genders'
        // paths rather than wait for the next onResume to notice.
        (activity as? com.gmwapp.hima.activities.MainActivity)?.let {
            it.refreshFriendsRequestCountBadge()
            it.refreshChatUnreadBadge()
        }
    }

    private fun updateEmptyState() {
        if (isChatListTab() && chatConversations.isEmpty() && currentSearchQuery.isNotBlank()) {
            binding.emptyStateTitle.text = getString(R.string.chat_search_no_results_title)
            binding.emptyStateSubtitle.text = getString(
                R.string.chat_search_no_results_desc,
                currentSearchQuery
            )
            binding.emptyState.visibility = View.VISIBLE
            binding.rvFriends.visibility = View.GONE
            return
        }

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
            TYPE_THEIR_REQUESTS -> {
                binding.emptyStateTitle.text = getString(R.string.chat_requests_empty_title)
                binding.emptyStateSubtitle.text = getString(R.string.chat_requests_empty_subtitle)
            }
            TYPE_MY_REQUESTS -> {
                binding.emptyStateTitle.text = getString(R.string.chat_sent_empty_title)
                binding.emptyStateSubtitle.text = getString(R.string.chat_sent_empty_subtitle)
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
                intent.putExtra("COIN_PER_MIN_AUDIO", conversation.coinPerMinAudio)
                intent.putExtra("COIN_PER_MIN_VIDEO", conversation.coinPerMinVideo)
                // T36: SINGLE_TOP + CLEAR_TOP so opening another chat from the
                // list reuses the existing ChatActivityInHouse via onNewIntent
                // instead of stacking a second instance.
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
                // T36: reuse existing ChatActivityInHouse instead of stacking.
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
                        // Remove accepted friendship — status=2 deletes the row server-side.
                        removeFriendFromList(friend)
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

        // Float the just-accepted friend to the top of the Friends list. Both the male
        // (FriendsHubFragment) and female (CreatorChatFragment) Friends tabs render via
        // TYPE_CHAT_FRIENDS, and a new friend has no chat messages yet — so without a
        // recorded accept time they'd sink into the alphabetical "no messages" bucket
        // instead of appearing first. sortChatConversationsForCurrentTab reads this.
        context?.let {
            com.gmwapp.hima.utils.RecentlyAcceptedFriendsPrefsHelper
                .recordAccepted(it, userData.id, friend.friend_id)
        }

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

    private fun removeFriendFromList(friend: FriendData) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        friendRequestViewModel.sendFriendRequest(
            senderId = userData.id,
            receiverId = friend.friend_id,
            status = 2
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
        // B080 — `audioStatus` / `videoStatus` are creator-only fields. Male
        // users never set them, so the server returns 0 → chat list shows
        // "Unavailable" for every male. When the viewer is female the other
        // party is male and there are no toggles to respect — force-enable.
        val currentUserIsFemale = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
            ?.equals(DConstants.FEMALE, ignoreCase = true) == true
        // B080 + B097 — resolve once so isOnline can also check availability.
        // See HomeFragment.loadMyChats for full rationale.
        // FEMALE_3_REJECT_BLOCK — if she auto-blocked this male (3 rejects/5min),
        // force BOTH to 0 so his call buttons grey out for the 60-min cooldown.
        val rejectBlocked = u.callBlocked == true
        val audioAvail = if (rejectBlocked) 0 else if (currentUserIsFemale) 1 else (u.audioStatus ?: 1)
        val videoAvail = if (rejectBlocked) 0 else if (currentUserIsFemale) 1 else (u.videoStatus ?: 1)
        // Clear chat: blank the list preview + unread when the newest message is at/before the
        // cleared watermark (keep the row). A newer message restores it — WhatsApp-style.
        val ctx = context
        val myId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        val clearedUpto = if (ctx != null && myId > 0)
            com.gmwapp.hima.utils.ClearedChatsPrefsHelper.getClearedUpto(ctx, myId, u.id) else 0L
        val isCleared = clearedUpto > 0L && (lastMessageTime?.toDate()?.time ?: 0L) <= clearedUpto
        // B_028 — if the thread's last message was "deleted for me" (device-local
        // hide, never sent to the server), the my_chat API still returns it as the
        // last message, so blank the preview here too. Ids match: the chat screen
        // stores ChatMessage.id = apiMsg.id.toString(), same id space as LastMessage.id.
        // Self-healing: a newer message changes lastMessage.id, which won't be in the
        // deleted set, so the preview restores automatically.
        val lastMsgId = lastMessage?.id?.toString().orEmpty()
        val isLastLocallyDeleted = ctx != null && myId > 0 && lastMsgId.isNotEmpty() &&
            com.gmwapp.hima.utils.LocallyDeletedMessagesStore.isLocallyDeleted(ctx, myId, u.id, lastMsgId)
        // B_028 follow-up — for a single "Delete for me" (NOT a whole clear-chat), don't
        // collapse the row to "No messages yet". Surface the PREVIOUS still-visible message
        // from the in-memory chat snapshot (WhatsApp-style). Null → no snapshot, keep blank.
        val deletedFallback = if (isLastLocallyDeleted && !isCleared && ctx != null)
            com.gmwapp.hima.utils.ChatPreviewFallback.previousVisiblePreview(ctx, myId, u.id) else null
        return ChatConversation(
            threadId = chatItem.chatId,
            userId = u.id.toString(),
            userName = u.name,
            userImage = u.image ?: "",
            lastMessage = when {
                isCleared -> ""
                deletedFallback != null -> deletedFallback.first
                isLastLocallyDeleted -> ""
                else -> lastMessage?.message ?: ""
            },
            lastMessageType = when {
                isCleared -> "text"
                deletedFallback != null -> deletedFallback.second
                isLastLocallyDeleted -> "text"
                else -> lastMessage?.messageType ?: "text"
            },
            lastMessageTime = lastMessageTime,
            unreadCount = if (isCleared) 0 else chatItem.unreadCount,
            // B097 — green dot now requires recent activity AND at least one
            // call mode available, mirroring FemaleUserAdapter semantics.
            isOnline = u.status == 1 && (audioAvail == 1 || videoAvail == 1),
            audioStatus = audioAvail,
            videoStatus = videoAvail,
            coinPerMinAudio = u.coinPerMinAudio ?: 10,
            coinPerMinVideo = u.coinPerMinVideo ?: 60,
            language = u.language,
            isPinned = if (isAdded) {
                PinnedChatsPrefsHelper.isPinned(requireContext(), u.id.toString())
            } else {
                false
            },
            // TC_022: keep blocked conversations in the list with a visible indicator.
            isBlocked = chatItem.iHaveBlockedThisUser == true,
            peerBlockedMe = chatItem.peerBlockedMe == true
        )
    }

    private fun sortChatConversationsForCurrentTab(
        conversations: List<ChatConversation>
    ): List<ChatConversation> {
        if (!isAdded) return conversations
        val ctx = requireContext()
        // WhatsApp-style "Delete chat": hide conversations the user deleted until a newer
        // message arrives (its timestamp beats the deleted-upto watermark).
        val myId = com.gmwapp.hima.BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        val visible = conversations.filterNot { conv ->
            val peerId = conv.userId.toIntOrNull() ?: return@filterNot false
            val lastMillis = conv.lastMessageTime?.toDate()?.time ?: 0L
            com.gmwapp.hima.utils.DeletedChatsPrefsHelper.isHidden(ctx, myId, peerId, lastMillis)
        }
        val (pinnedConv, unpinned) = visible.partition { it.isPinned }
        val order = PinnedChatsPrefsHelper.getPinnedIds(ctx)
        val sortedPinned = pinnedConv.sortedBy { conv ->
            val i = order.indexOf(conv.userId)
            if (i >= 0) i else Int.MAX_VALUE
        }

        val sortedUnpinned = if (tabType == TYPE_CHAT_FRIENDS) {
            // Effective ordering time = the later of (last message time, the moment this
            // friend was accepted on this device). A freshly accepted friend has no
            // messages, so folding in the local accept timestamp floats them to the very
            // top until real chat activity takes over. Friends with neither a message nor
            // a recorded accept keep the old alphabetical ("no messages") ordering.
            val effectiveMillis = unpinned.associateWith { conv ->
                val msg = conv.lastMessageTime?.toDate()?.time ?: 0L
                val accepted = conv.userId.toIntOrNull()?.let {
                    com.gmwapp.hima.utils.RecentlyAcceptedFriendsPrefsHelper
                        .getAcceptedMillis(ctx, myId, it)
                } ?: 0L
                // Diagnostic (debug builds only): if a just-accepted friend never floats,
                // this reveals whether the accept timestamp is being read at all. A log line
                // for the friend ⇒ the id match works and the friend is present (so any
                // failure is upstream — server didn't return them yet). NO line after
                // accepting ⇒ id mismatch or the record wasn't written.
                if (com.gmwapp.hima.BuildConfig.DEBUG && accepted > 0L) {
                    Log.d("FriendsTab", "float-check id=${conv.userId} name=${conv.userName} msg=$msg accepted=$accepted")
                }
                maxOf(msg, accepted)
            }
            val (active, idle) = unpinned.partition { (effectiveMillis[it] ?: 0L) > 0L }
            val sortedActive = active.sortedByDescending { effectiveMillis[it] ?: 0L }
            val sortedIdle = idle.sortedBy { it.userName.lowercase(Locale.getDefault()) }
            sortedActive + sortedIdle
        } else {
            unpinned.sortedByDescending { it.lastMessageTime?.toDate()?.time ?: 0L }
        }

        // QA bug #8 — blocked conversations (either direction) sink to the very
        // bottom of the list. Applied as a FINAL stable partition over the already
        // pinned/active/idle-ordered result, so every existing ordering rule is
        // preserved WITHIN each group; only the blocked rows move down, keeping
        // their relative order. partition() is stable, so no row is reshuffled.
        val ordered = sortedPinned + sortedUnpinned
        val (blockedRows, normalRows) = ordered.partition { it.isBlocked || it.peerBlockedMe }
        return normalRows + blockedRows
    }

    private fun updateChatUI(conversationsList: List<ChatConversation>) {
        if (!isAdded) return
        if (conversationsList.isEmpty()) {
            lastLoadedChatConversations = emptyList()
        }

        Log.d("FriendsTab", "📊 updateChatUI called with ${conversationsList.size} conversations")
        // T14: don't dump usernames/userIds in release logcat.
        if (com.gmwapp.hima.BuildConfig.DEBUG) {
            Log.d("FriendsTab", "Conversations: ${conversationsList.map { "${it.userName} (${it.userId})" }}")
        }
        
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
        // No timed auto-refresh on the Friend / Requests lists (owner request) or on
        // the hub Friends tab (TYPE_CHAT_FRIENDS) — they refresh on resume only, plus
        // realtime push/socket for the chat one, so the list isn't reloaded/reordered
        // under the user every 30s. Other chat lists (main chat, General) keep the
        // 30s poll as a fallback when realtime misses.
        if (!isChatListTab() || tabType == TYPE_CHAT_FRIENDS) return
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL)
        Log.d("FriendsTab", "✅ Started auto-refresh every ${AUTO_REFRESH_INTERVAL / 1000} seconds")
    }
    
    /**
     * Check if chat history exists for each friend in the Friends tab
     * This checks if a Firestore chat thread document exists (lightweight check)
     */
    /**
     * QA bug #8 — reorder [friendsList] in place so blocked friends (either I blocked
     * them, or they blocked me) sit at the very bottom, matching the chat-list behavior.
     * Stable partition: the server's ordering is preserved within each group, so this is
     * a pure no-op when nothing is blocked (or when the backend hasn't started returning
     * the block flags yet).
     */
    private fun sinkBlockedFriendsToBottom() {
        val (blocked, normal) = friendsList.partition { it.isBlocked || it.peerBlockedMe }
        if (blocked.isEmpty()) return
        friendsList.clear()
        friendsList.addAll(normal)
        friendsList.addAll(blocked)
    }

    private fun checkChatHistoryForFriends() {
        if (friendsList.isEmpty()) return
        
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val myUserId = userData?.id?.toString() ?: return
        
        Log.d("FriendsTab", "🔍 Checking chat history for ${friendsList.size} friends")
        
        friendsList.forEach { friend ->
            val friendId = friend.friend_id.toString()
            
            // Generate thread ID (same logic as ChatActivityInHouse)
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

                    // T38: refresh just the matching row instead of rebuilding the
                    // whole list per Firestore callback (one probe per friend ×
                    // N friends = N full rebinds otherwise).
                    if (::adapter.isInitialized) {
                        val idx = friendsList.indexOfFirst { it.friend_id == friend.friend_id }
                        if (idx in 0 until adapter.itemCount) {
                            adapter.notifyItemChanged(idx)
                        }
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
    /**
     * Public reload used by the parent hub after an OUT-OF-TAB mutation: accepting a
     * friend request on the Requests tab must float the new friend to the TOP of the
     * sibling Friends tab immediately. Previously the Friends tab stayed frozen on its
     * previously-loaded list (its own onResume didn't re-fire on the ViewPager swap and
     * the 30s poll hadn't run), so the just-accepted friend didn't appear. Guarded so it
     * no-ops when this child's view isn't ready (offscreen/destroyed — recreation then
     * loads it fresh anyway).
     */
    fun reloadTabFromParent() {
        if (!isAdded || view == null) return
        if (isChatListTab()) loadChatConversations() else loadData()
    }

    private fun refreshParentTabCounts() {
        // Inside MainActivity the parent is FriendsHubFragment (male Friends tab) or
        // CreatorChatFragment (female Chat tab). Refresh whichever hosts us so the
        // Requests/Sent counts drop immediately on accept/reject/remove.
        (parentFragment as? FriendsHubFragment)?.refreshCounts()
        (parentFragment as? CreatorChatFragment)?.refreshCounts()

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
                    Log.d("FriendsTab", "🔄 Tab count refresh handled by FriendsHubFragment")
                }
            }
        }
    }
}
