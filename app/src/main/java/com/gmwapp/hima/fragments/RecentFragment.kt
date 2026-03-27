package com.gmwapp.hima.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.activityViewModels
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
import com.gmwapp.hima.callbacks.NetworkRetryable
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentRecentBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallsListResponseData
import com.gmwapp.hima.retrofit.responses.MyChatResponse
import com.gmwapp.hima.utils.Helper
import com.gmwapp.hima.viewmodels.RecentViewModel
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@AndroidEntryPoint
class RecentFragment : BaseFragment(), NetworkRetryable {

    @Inject
    lateinit var apiManager: ApiManager

    private lateinit var binding: FragmentRecentBinding
    /** Activity scope: list survives bottom-nav tab switches so we don't clear + race on every visit. */
    private val recentViewModel: RecentViewModel by activityViewModels()
    private lateinit var recentCallsAdapter: RecentCallsAdapter
    /** When true, next successful calls-list response replaces the first page (clear then add). */
    private var pendingFullListReplace = false
    private var isLoading = false
    private var offset = 0
    private val limit = 10
    private var currentSortType = "recent"  // Default: recent
    private var currentSearchQuery = ""  // Current search query
    private var currentMissedCount = 0
    /** True from dispatching getCallsList until success/error/null response handled. */
    private var listRequestInFlight = false

    // Debouncing for search
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val SEARCH_DEBOUNCE_DELAY = 300L // 300ms delay

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecentBinding.inflate(inflater, container, false)
        initUI()
        observeViewModel()
        setupFilterChips()
        return binding.root
    }


    private fun initUI() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        if (userData == null) {
            binding.tlTitle.visibility = View.VISIBLE
            return
        }

        binding.btnRetryNoNetwork.setOnClickListener {
            if (!Helper.checkNetworkConnection()) return@setOnClickListener
            onNetworkRetry()
        }

        BaseApplication.getInstance()?.networkConnectedLiveData?.observe(viewLifecycleOwner) {
            refreshRecentPlaceholderState()
        }

        // Setup chat icon click listener
        setupChatIconClickListener()
        
        // Load unread message count from API
        loadUnreadMessageCount()
        
        // Setup search listener
        setupSearchListener()

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
            },
            isFavouriteMode = false // Not favorite mode for RecentFragment
        )
        binding.rvCalls.adapter = recentCallsAdapter

        // Initial load (LiveData may only hold the last page chunk — always refresh for a full list)
        recentCallsAdapter.setFilter(currentSortType)
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
                    listRequestInFlight = true
                    userData.let {
                        recentViewModel.getCallsList(it.id, it.gender, limit, offset, currentSortType, if (currentSearchQuery.isEmpty()) null else currentSearchQuery)
                    }
                }
            }
        })

        refreshRecentPlaceholderState()
    }

    private fun loadCallsList(sortType: String, resetData: Boolean, searchQuery: String = "") {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        listRequestInFlight = true

        if (resetData) {
            offset = 0
            isLoading = true
            setLoading(true)
            pendingFullListReplace = true
        }

        val search = searchQuery.trim()
        currentSearchQuery = search
        recentViewModel.getCallsList(userData.id, userData.gender, limit, offset, sortType, if (search.isEmpty()) null else search)
    }

    private fun isNetworkConnectedNow(): Boolean {
        return when (val v = BaseApplication.getInstance()?.networkConnectedLiveData?.value) {
            null -> Helper.checkNetworkConnection()
            else -> v
        }
    }

    private fun refreshRecentPlaceholderState() {
        if (!::binding.isInitialized || !::recentCallsAdapter.isInitialized) return

        val online = isNetworkConnectedNow()
        val empty = recentCallsAdapter.itemCount == 0
        val loading =
            listRequestInFlight || binding.swipeRefreshLayout.isRefreshing || (binding.progressBar.visibility == View.VISIBLE)

        if (!online && empty) {
            binding.layoutNoInternet.visibility = View.VISIBLE
            binding.tlTitle.visibility = View.GONE
            binding.rvCalls.visibility = View.GONE
            return
        }

        binding.layoutNoInternet.visibility = View.GONE

        if (!empty) {
            binding.tlTitle.visibility = View.GONE
            binding.rvCalls.visibility = View.VISIBLE
            return
        }

        if (loading) {
            binding.tlTitle.visibility = View.GONE
            binding.rvCalls.visibility = View.GONE
        } else {
            binding.tlTitle.visibility = View.VISIBLE
            binding.rvCalls.visibility = View.GONE
        }
    }
    
    private fun setupSearchListener() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Cancel previous search runnable
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                
                // Create new search runnable
                val searchQuery = s?.toString()?.trim() ?: ""
                searchRunnable = Runnable {
                    loadCallsList(currentSortType, resetData = true, searchQuery = searchQuery)
                }
                
                // Post delayed search with debouncing
                searchHandler.postDelayed(searchRunnable!!, SEARCH_DEBOUNCE_DELAY)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeViewModel() {
        recentViewModel.callsListLiveData.observe(viewLifecycleOwner, Observer {
            binding.swipeRefreshLayout.isRefreshing = false
            isLoading = false
            setLoading(false)
            listRequestInFlight = false

            val response = it
            if (response == null) {
                pendingFullListReplace = false
                refreshRecentPlaceholderState()
                return@Observer
            }

            if (response.success && response.data != null) {
                if (response.data.isNotEmpty()) {
                    if (pendingFullListReplace) {
                        recentCallsAdapter.clearData()
                        pendingFullListReplace = false
                    }
                    recentCallsAdapter.addData(response.data)
                } else {
                    if (pendingFullListReplace) {
                        recentCallsAdapter.clearData()
                        pendingFullListReplace = false
                    }
                }
            } else {
                pendingFullListReplace = false
            }
            refreshRecentPlaceholderState()
        })

        recentViewModel.callsListErrorLiveData.observe(viewLifecycleOwner, Observer {
            binding.swipeRefreshLayout.isRefreshing = false
            isLoading = false
            setLoading(false)
            pendingFullListReplace = false
            listRequestInFlight = false
            refreshRecentPlaceholderState()
        })

        recentViewModel.missedCallCountLiveData.observe(viewLifecycleOwner, Observer { count ->
            Log.d("missed_call_data", "ui_count=${count ?: 0}")
            updateMissedChipCount(count ?: 0)
            // When Missed tab is opened (seen=1 flow), refresh bottom nav badge in MainActivity
            // so it reflects latest value immediately.
            if (currentSortType == "missed") {
                (activity as? com.gmwapp.hima.activities.MainActivity)?.refreshRecentMissedCountBadge()
            }
        })

        recentViewModel.missedCallCountErrorLiveData.observe(viewLifecycleOwner, Observer { error ->
            Log.e("missed_call_data", "ui_error=$error")
        })
    }

    private fun setLoading(isLoading: Boolean) {
        val shouldShow = isLoading && !binding.swipeRefreshLayout.isRefreshing
        binding.progressBar.visibility = if (shouldShow) View.VISIBLE else View.GONE
        refreshRecentPlaceholderState()
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

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val sortType = when (checkedIds.firstOrNull()) {
                R.id.chip_missed    -> "missed"
                R.id.chip_talk_time -> "talk_time"
                R.id.chip_a_z       -> "a_z"
                else                -> "recent"
            }
            if (sortType != currentSortType) {
                currentSortType = sortType
                recentCallsAdapter.setFilter(currentSortType)
                loadCallsList(currentSortType, resetData = true)
                if (currentSortType == "missed") {
                    loadMissedCallCount(seen = 1)
                }
            }
        }
    }

    private fun loadMissedCallCount(seen: Int = 0) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        recentViewModel.getMissedCallCount(userData.id, seen)
    }

    private fun updateMissedChipCount(count: Int) {
        currentMissedCount = count.coerceAtLeast(0)
        binding.chipMissed.text = if (currentMissedCount > 0) {
            "Missed ($currentMissedCount)"
        } else {
            "Missed"
        }
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
        apiManager.getMyChat(myUserId, null, 100, 0, object : NetworkCallback<MyChatResponse> {
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

    override fun onNetworkRetry() {
        loadCallsList(currentSortType, resetData = true, searchQuery = currentSearchQuery)
        loadUnreadMessageCount()
        loadMissedCallCount(seen = 0)
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
        loadMissedCallCount(seen = 0)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up search handler
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        // Clean up listeners when fragment is destroyed
//        unreadCountsMap.clear()
    }
}
