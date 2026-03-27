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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.RecentCallsAdapter
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.callbacks.NetworkRetryable
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentFavouriteBinding
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
class FavouriteFragment : BaseFragment(), NetworkRetryable {

    @Inject
    lateinit var apiManager: ApiManager

    private lateinit var binding: FragmentFavouriteBinding
    private val recentViewModel: RecentViewModel by viewModels()
    private lateinit var recentCallsAdapter: RecentCallsAdapter
    private var isLoading = false
    private var offset = 0
    private val limit = 10
    private var currentSortType = "recent"  // Default: recent
    private var hasMoreItems = true  // Track if there are more items to load
    
    // Debouncing for search
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val SEARCH_DEBOUNCE_DELAY = 300L // 300ms delay

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentFavouriteBinding.inflate(inflater, container, false)
        initUI()
        observeViewModel()
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
            loadFavouritesList(resetData = true)
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
            isFavouriteMode = true, // Enable favorite mode
            apiManager = apiManager // Pass ApiManager for friend status check
        )
        binding.rvCalls.adapter = recentCallsAdapter

        // Load favourites in onResume only (initUI + onResume both used to fire two requests and duplicate rows)

        // Pagination
        binding.rvCalls.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (!isLoading && hasMoreItems &&
                    layoutManager.findLastCompletelyVisibleItemPosition() == recentCallsAdapter.itemCount - 1
                ) {
                    isLoading = true
                    offset += limit
                    userData.let {
                        recentViewModel.getCallsList(it.id, it.gender, limit, offset, currentSortType, null, 1)
                    }
                }
            }
        })
    }

    private fun loadFavouritesList(resetData: Boolean) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        
        if (resetData) {
            offset = 0
            isLoading = true
            setLoading(true)
            hasMoreItems = true  // Reset hasMoreItems when refreshing
            if (::recentCallsAdapter.isInitialized) {
                recentCallsAdapter.clearData()
            }
        }
        
        // Call with fav=1 to get only favorites
        recentViewModel.getCallsList(userData.id, userData.gender, limit, offset, currentSortType, null, 1)
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
                
                // Check if there are more items to load
                val total = it.total ?: 0
                val currentCount = recentCallsAdapter.itemCount
                hasMoreItems = currentCount < total
                
                Log.d("FavouriteFragment", "Pagination: total=$total, current=$currentCount, hasMore=$hasMoreItems")
            } else if (recentCallsAdapter.itemCount == 0) {
                binding.tlTitle.visibility = View.VISIBLE
                binding.rvCalls.visibility = View.GONE
                hasMoreItems = false  // No more items if empty response
            } else {
                // Empty response but we have some items - reached the end
                hasMoreItems = false
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
            hasMoreItems = false
        })
    }

    private fun setLoading(isLoading: Boolean) {
        val shouldShow = isLoading && !binding.swipeRefreshLayout.isRefreshing
        binding.progressBar.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    override fun onNetworkRetry() {
        loadFavouritesList(resetData = true)
        loadUnreadMessageCount()
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

        Log.d("FavouriteFragment", "Loading unread message count for user: $myUserId")

        // Call API to get chat list
        apiManager.getMyChat(myUserId, null, 100, 0, object : NetworkCallback<MyChatResponse> {
            override fun onResponse(call: Call<MyChatResponse>, response: Response<MyChatResponse>) {
                if (!isAdded) return

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody?.success == true && responseBody.data != null) {
                        val chats = responseBody.data.chats
                        Log.d("FavouriteFragment", "✅ Received ${chats.size} chats from API")

                        // Calculate total unread count
                        val totalUnread = chats.sumOf { it.unreadCount }
                        Log.d("FavouriteFragment", "📊 Total unread count: $totalUnread")
                        updateUnreadBadge(totalUnread)
                    } else {
                        Log.e("FavouriteFragment", "❌ API response unsuccessful or data is null")
                        updateUnreadBadge(0)
                    }
                } else {
                    Log.e("FavouriteFragment", "❌ API call failed: ${response.code()}")
                    updateUnreadBadge(0)
                }
            }

            override fun onFailure(call: Call<MyChatResponse>, t: Throwable) {
                if (!isAdded) return
                Log.e("FavouriteFragment", "❌ Error loading unread count: ${t.message}", t)
                updateUnreadBadge(0)
            }

            override fun onNoNetwork() {
                if (!isAdded) return
                Log.e("FavouriteFragment", "❌ No network connection")
                updateUnreadBadge(0)
            }
        })
    }

    private fun updateUnreadBadge(count: Int) {
        if (!::binding.isInitialized) {
            Log.d("FavouriteFragment", "❌ Binding not initialized")
            return
        }

        Log.d("FavouriteFragment", "📬 Updating unread badge: $count")

        if (count > 0) {
            binding.tvUnreadBadge.visibility = View.VISIBLE
            binding.tvUnreadBadge.text = if (count > 99) "99+" else count.toString()
            Log.d("FavouriteFragment", "✅ Badge visible with count: $count")
        } else {
            binding.tvUnreadBadge.visibility = View.GONE
            Log.d("FavouriteFragment", "⚠️ Badge hidden (no unread)")
        }
    }

    override fun onResume() {
        super.onResume()

        // Always refresh favorites list when returning to this screen
        Log.d("FavouriteFragment", "🔄 onResume - refreshing favourites list")
            loadFavouritesList(resetData = true)

        // Reset flags if they were set
        if (FcmUtils.shouldRefreshCallList == 1) {
            FcmUtils.shouldRefreshCallList = 0
        }

        // Refresh unread count when returning to this screen
        Log.d("FavouriteFragment", "🔄 onResume - reloading unread count")
        loadUnreadMessageCount()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up search handler
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }
}

