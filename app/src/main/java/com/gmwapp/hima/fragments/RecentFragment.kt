package com.gmwapp.hima.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.RandomUserActivity
import com.gmwapp.hima.adapters.RecentCallsAdapter
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentRecentBinding
import com.gmwapp.hima.retrofit.responses.CallsListResponseData
import com.gmwapp.hima.viewmodels.RecentViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecentFragment : BaseFragment() {

    private lateinit var binding: FragmentRecentBinding
    private val recentViewModel: RecentViewModel by viewModels()
    private lateinit var recentCallsAdapter: RecentCallsAdapter
    private var isLoading = false
    private var offset = 0
    private val limit = 10
    private var currentSortType = "recent"  // Default: recent

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecentBinding.inflate(inflater, container, false)
        initUI()
        observeViewModel()
        setupSortOptions()
        return binding.root
    }

    private fun initUI() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        if (userData == null) {
            binding.tlTitle.visibility = View.VISIBLE
            return
        }

        // Setup chat icon click listener
        setupChatIconClickListener()
        
        // Load unread message count
        loadUnreadMessageCount()

        // Swipe to refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadCallsList(currentSortType, resetData = true)
        }

        // Set up RecyclerView
        binding.rvCalls.layoutManager = LinearLayoutManager(requireContext())
        recentCallsAdapter = RecentCallsAdapter(
            requireActivity(),
            ArrayList(),
            object : OnItemSelectionListener<CallsListResponseData> {
                override fun onItemSelected(data: CallsListResponseData) {
                    startMaleCallConnectingActivity(data, "audio")
                }
            },
            object : OnItemSelectionListener<CallsListResponseData> {
                override fun onItemSelected(data: CallsListResponseData) {
                    startMaleCallConnectingActivity(data, "video")
                }
            }
        )
        binding.rvCalls.adapter = recentCallsAdapter

        // Initial call with default type (after adapter is initialized)
        loadCallsList(currentSortType, resetData = true)

        // Pagination
        binding.rvCalls.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (!isLoading &&
                    layoutManager.findLastCompletelyVisibleItemPosition() == recentCallsAdapter.itemCount - 1
                ) {
                    isLoading = true
                    offset += limit
                    userData.let {
                        recentViewModel.getCallsList(it.id, it.gender, limit, offset, currentSortType)
                    }
                }
            }
        })
    }

    private fun loadCallsList(sortType: String, resetData: Boolean) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        
        if (resetData) {
            offset = 0
            isLoading = true
            if (::recentCallsAdapter.isInitialized) {
                recentCallsAdapter.clearData()
            }
        }
        
        recentViewModel.getCallsList(userData.id, userData.gender, limit, offset, sortType)
    }

    private fun observeViewModel() {
        recentViewModel.callsListLiveData.observe(viewLifecycleOwner, Observer {
            binding.swipeRefreshLayout.isRefreshing = false
            isLoading = false

            if (it != null && it.success && it.data != null && it.data.isNotEmpty()) {
                binding.tlTitle.visibility = View.GONE
                binding.rvCalls.visibility = View.VISIBLE
                recentCallsAdapter.addData(it.data)
            } else if (recentCallsAdapter.itemCount == 0) {
                binding.tlTitle.visibility = View.VISIBLE
                binding.rvCalls.visibility = View.GONE
            }
        })
    }

    private fun startMaleCallConnectingActivity(data: CallsListResponseData, callType: String) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        
        // Check if user is female/creator - use FemaleCallConnectingActivity
        val activityClass = if (userData?.gender == DConstants.FEMALE) {
            com.gmwapp.hima.agora.female.FemaleCallConnectingActivity::class.java
        } else {
            MaleCallConnectingActivity::class.java
        }
        
        val intent = Intent(requireContext(), activityClass).apply {
            putExtra(DConstants.CALL_TYPE, callType)
            putExtra(DConstants.RECEIVER_ID, data.id)
            putExtra(DConstants.RECEIVER_NAME, data.name)
            putExtra(DConstants.CALL_ID, 0)
            putExtra(DConstants.IMAGE, data.image)
            putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
            putExtra(DConstants.TEXT, getString(R.string.wait_user_hint, data.name))
        }
        FcmUtils.isUserAvailable=1
        startActivity(intent)
    }

    private fun setupSortOptions() {
        binding.cardSort.setOnClickListener { showSortMenu() }
    }

    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), binding.cardSort)
        popup.menuInflater.inflate(R.menu.menu_recent_sort, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_sort_recent -> {
                    currentSortType = "recent"
                    binding.tvSortLabel.text = "Recent"
                    loadCallsList(currentSortType, resetData = true)
                    true
                }
                R.id.action_sort_talk_time -> {
                    currentSortType = "talk_time"
                    binding.tvSortLabel.text = "Talk Time"
                    loadCallsList(currentSortType, resetData = true)
                    true
                }
                R.id.action_sort_name -> {
                    currentSortType = "a_z"
                    binding.tvSortLabel.text = "A-Z"
                    loadCallsList(currentSortType, resetData = true)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setupChatIconClickListener() {
        binding.cardChat.setOnClickListener {
            // Open ChatListActivity
            val intent = Intent(requireContext(), com.gmwapp.hima.activities.ChatListActivity::class.java)
            startActivity(intent)
        }
    }

    // Track unread counts per thread for real-time updates
    private val unreadCountsMap = mutableMapOf<String, Int>()

    private fun loadUnreadMessageCount() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        val myUserId = userData.id.toString()

        if (myUserId.isEmpty()) return

        // Listen to Firestore for unread message count
        Firebase.firestore.collection("chats")
            .addSnapshotListener { documents, error ->
                if (error != null || documents == null) {
                    updateUnreadBadge(0)
                    return@addSnapshotListener
                }

                if (documents.isEmpty) {
                    updateUnreadBadge(0)
                    return@addSnapshotListener
                }

                // Clear and re-track all threads
                unreadCountsMap.clear()

                documents.forEach { document ->
                    val threadId = document.id
                    val userIds = threadId.split("_")

                    if (userIds.contains(myUserId)) {
                        val otherUserId = userIds.firstOrNull { it != myUserId} ?: ""

                        // Check if other user is blocked
                        Firebase.firestore.collection("blocked_users")
                            .document(myUserId)
                            .collection("users")
                            .document(otherUserId)
                            .get()
                            .addOnSuccessListener { blockDoc ->
                                val blockTimestamp = blockDoc.getTimestamp("blockedAt")
                                
                                // Real-time listener for each thread's unread messages
                                Firebase.firestore.collection("chats")
                                    .document(threadId)
                                    .collection("messages")
                                    .whereEqualTo("from", otherUserId)
                                    .whereEqualTo("isRead", false)
                                    .addSnapshotListener { messages, messageError ->
                                        if (messageError != null || messages == null) {
                                            unreadCountsMap[threadId] = 0
                                        } else {
                                            // Filter out messages sent after block timestamp
                                            val unreadCount = if (blockTimestamp != null) {
                                                messages.documents.count { msg ->
                                                    val msgTimestamp = msg.getTimestamp("timestamp")
                                                    msgTimestamp == null || msgTimestamp.seconds < blockTimestamp.seconds
                                                }
                                            } else {
                                                messages.size()
                                            }
                                            unreadCountsMap[threadId] = unreadCount
                                        }
                                        
                                        // Sum all unread counts
                                        val totalUnread = unreadCountsMap.values.sum()
                                        updateUnreadBadge(totalUnread)
                                    }
                            }
                    }
                }
            }
    }

    private fun updateUnreadBadge(count: Int) {
        if (!::binding.isInitialized) return
        
        if (count > 0) {
            binding.tvUnreadBadge.visibility = View.VISIBLE
            binding.tvUnreadBadge.text = if (count > 99) "99+" else count.toString()
        } else {
            binding.tvUnreadBadge.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()

        if (FcmUtils.isUserAvailable==0){
            loadCallsList(currentSortType, resetData = true)
        }
        
        // Refresh unread count when returning to this screen
        loadUnreadMessageCount()
    }
}
