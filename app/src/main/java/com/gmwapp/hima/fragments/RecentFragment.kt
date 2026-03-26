package com.gmwapp.hima.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var currentSearchQuery = ""  // Current search query
    private var currentDaysFilter = 0
    private var currentMissedCount = 0
    private var isProgrammaticChipSelection = false
    private var isTalkTimeDialogOpen = false
    
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

        // Initial call with default type (after adapter is initialized)
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
                    userData.let {
                        val days = if (currentSortType == "talk_time") currentDaysFilter else 0
                        recentViewModel.getCallsList(
                            it.id,
                            it.gender,
                            limit,
                            offset,
                            currentSortType,
                            if (currentSearchQuery.isEmpty()) null else currentSearchQuery,
                            days = days
                        )
                    }
                }
            }
        })
    }

    private fun loadCallsList(sortType: String, resetData: Boolean, searchQuery: String = "") {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        val days = if (sortType == "talk_time") currentDaysFilter else 0
        if (sortType == "talk_time" && days <= 0) {
            binding.swipeRefreshLayout.isRefreshing = false
            isLoading = false
            setLoading(false)
            return
        }

        if (resetData) {
            offset = 0
            isLoading = true
            setLoading(true)
            if (::recentCallsAdapter.isInitialized) {
                recentCallsAdapter.clearData()
            }
        }
        
        val search = searchQuery.trim()
        currentSearchQuery = search
        recentViewModel.getCallsList(
            userData.id,
            userData.gender,
            limit,
            offset,
            sortType,
            if (search.isEmpty()) null else search,
            days = days
        )
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

            if (it != null && it.success && it.data != null && it.data.isNotEmpty()) {
                binding.tlTitle.visibility = View.GONE
                binding.rvCalls.visibility = View.VISIBLE
                recentCallsAdapter.addData(it.data)
            } else if (recentCallsAdapter.itemCount == 0) {
                binding.tlTitle.visibility = View.VISIBLE
                binding.rvCalls.visibility = View.GONE
            }
        })

        recentViewModel.callsListErrorLiveData.observe(viewLifecycleOwner, Observer {
            binding.swipeRefreshLayout.isRefreshing = false
            isLoading = false
            setLoading(false)
            if (recentCallsAdapter.itemCount == 0) {
                binding.tlTitle.visibility = View.VISIBLE
                binding.rvCalls.visibility = View.GONE
            }
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
        binding.chipTalkTime.setOnClickListener {
            if (currentSortType == "talk_time") {
                showTalkTimeDaysDialog(
                    onDaySelected = { selectedDays ->
                        currentDaysFilter = selectedDays
                        recentCallsAdapter.setFilter(currentSortType)
                        // Re-apply filter even when same day is selected again.
                        loadCallsList(currentSortType, resetData = true)
                    },
                    onDismissWithoutSelection = {}
                )
            }
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isProgrammaticChipSelection) return@setOnCheckedStateChangeListener
            val selectedChipId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val sortType = when (checkedIds.firstOrNull()) {
                R.id.chip_missed    -> "missed"
                R.id.chip_talk_time -> "talk_time"
                R.id.chip_a_z       -> "a_z"
                else                -> "recent"
            }
            if (selectedChipId == R.id.chip_talk_time) {
                val previousSortType = currentSortType
                showTalkTimeDaysDialog(
                    onDaySelected = { selectedDays ->
                        val changed = currentSortType != "talk_time" || currentDaysFilter != selectedDays
                        currentSortType = "talk_time"
                        currentDaysFilter = selectedDays
                        recentCallsAdapter.setFilter(currentSortType)
                        if (changed) {
                            loadCallsList(currentSortType, resetData = true)
                        }
                    },
                    onDismissWithoutSelection = {
                        restoreChipSelection(previousSortType)
                    }
                )
                return@setOnCheckedStateChangeListener
            }

            val changed = currentSortType != sortType || currentDaysFilter != 0
            currentSortType = sortType
            currentDaysFilter = 0
            recentCallsAdapter.setFilter(currentSortType)
            if (changed) {
                loadCallsList(currentSortType, resetData = true)
            }
            if (currentSortType == "missed") {
                loadMissedCallCount(seen = 1)
            }
        }
    }

    private fun showTalkTimeDaysDialog(
        onDaySelected: (Int) -> Unit,
        onDismissWithoutSelection: () -> Unit
    ) {
        if (isTalkTimeDialogOpen) return
        isTalkTimeDialogOpen = true

        val dayOptions = arrayOf("Last 7 days", "Last 15 days", "Last 30 days")
        val dayValues = intArrayOf(7, 15, 30)
        var hasSelection = false

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Talk Time Range")
            .setItems(dayOptions) { dialog, which ->
                hasSelection = true
                onDaySelected(dayValues[which])
                dialog.dismiss()
            }
            .setOnCancelListener {
                isTalkTimeDialogOpen = false
                if (!hasSelection) {
                    onDismissWithoutSelection()
                }
            }
            .setOnDismissListener {
                isTalkTimeDialogOpen = false
            }
            .show()
    }

    private fun restoreChipSelection(sortType: String) {
        val chipId = when (sortType) {
            "missed" -> R.id.chip_missed
            "talk_time" -> R.id.chip_talk_time
            "a_z" -> R.id.chip_a_z
            else -> R.id.chip_all
        }
        isProgrammaticChipSelection = true
        binding.chipGroupFilter.check(chipId)
        isProgrammaticChipSelection = false
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
