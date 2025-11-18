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
import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.RandomUserActivity
import com.gmwapp.hima.adapters.RecentCallsAdapter
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentRecentBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallsListResponseData
import com.gmwapp.hima.retrofit.responses.MyChatResponse
import com.gmwapp.hima.viewmodels.RecentViewModel
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@AndroidEntryPoint
class RecentFragment : BaseFragment() {

    @Inject
    lateinit var apiManager: ApiManager

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
        
        // Load unread message count from API
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

    private fun loadUnreadMessageCount() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        val myUserId = userData.id

        if (myUserId == 0) {
            updateUnreadBadge(0)
            return
        }

        Log.d("RecentFragment", "Loading unread message count for user: $myUserId")

        // Call API to get chat list
        apiManager.getMyChat(myUserId, object : NetworkCallback<MyChatResponse> {
            override fun onResponse(call: Call<MyChatResponse>, response: Response<MyChatResponse>) {
                if (!isAdded) return

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody?.success == true && responseBody.data != null) {
                        val chats = responseBody.data.chats
                        Log.d("RecentFragment", "✅ Received ${chats.size} chats from API")

                        // Calculate total unread count
                        val totalUnread = chats.sumOf { it.unreadCount }
                        Log.d("RecentFragment", "📊 Total unread count: $totalUnread")
                        updateUnreadBadge(totalUnread)
                    } else {
                        Log.e("RecentFragment", "❌ API response unsuccessful or data is null")
                        updateUnreadBadge(0)
                    }
                } else {
                    Log.e("RecentFragment", "❌ API call failed: ${response.code()}")
                    updateUnreadBadge(0)
                }
            }

            override fun onFailure(call: Call<MyChatResponse>, t: Throwable) {
                if (!isAdded) return
                Log.e("RecentFragment", "❌ Error loading unread count: ${t.message}", t)
                updateUnreadBadge(0)
            }

            override fun onNoNetwork() {
                if (!isAdded) return
                Log.e("RecentFragment", "❌ No network connection")
                updateUnreadBadge(0)
            }
        })
    }

    private fun updateUnreadBadge(count: Int) {
        if (!::binding.isInitialized) {
            Log.d("RecentFragment", "❌ Binding not initialized")
            return
        }

        Log.d("RecentFragment", "📬 Updating unread badge: $count")

        if (count > 0) {
            binding.tvUnreadBadge.visibility = View.VISIBLE
            binding.tvUnreadBadge.text = if (count > 99) "99+" else count.toString()
            Log.d("RecentFragment", "✅ Badge visible with count: $count")
        } else {
            binding.tvUnreadBadge.visibility = View.GONE
            Log.d("RecentFragment", "⚠️ Badge hidden (no unread)")
        }
    }


    override fun onResume() {
        super.onResume()

        if (FcmUtils.isUserAvailable==0){
            loadCallsList(currentSortType, resetData = true)
        }

        if (FcmUtils.shouldRefreshCallList == 1) {
            Log.d("RecentFragment", "Call rejected detected, refreshing call list")
            loadCallsList(currentSortType, resetData = true)
            FcmUtils.shouldRefreshCallList = 0  // Reset flag after refresh
        }

        // Refresh unread count when returning to this screen
        Log.d("RecentFragment", "🔄 onResume - reloading unread count")
        loadUnreadMessageCount()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up listeners when fragment is destroyed
//        unreadCountsMap.clear()
    }
}
