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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.gmwapp.hima.callbacks.Refreshable
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentFavouriteBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.responses.CallsListResponseData
import com.gmwapp.hima.viewmodels.RecentViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FavouriteFragment : BaseFragment(), NetworkRetryable, Refreshable {

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
        // Friends-Gated Chat: when embedded as the "Favourites" tab of the Friends hub,
        // the hub owns the header — hide our own app bar to avoid a double header.
        val embedded = arguments?.getBoolean(ARG_EMBEDDED, false) ?: false
        if (embedded) {
            binding.appBarLayout.visibility = View.GONE
        } else {
            setupStatusBarInsets()
        }
        initUI()
        observeViewModel()
        return binding.root
    }

    companion object {
        private const val ARG_EMBEDDED = "embedded"

        /** Embedded = true hides this fragment's own header (used inside the Friends hub). */
        fun newInstance(embedded: Boolean): FavouriteFragment {
            return FavouriteFragment().apply {
                arguments = Bundle().apply { putBoolean(ARG_EMBEDDED, embedded) }
            }
        }
    }

    private fun setupStatusBarInsets() {
        val basePaddingTop = binding.appBarLayout.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                basePaddingTop + statusBarInset,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.appBarLayout)
    }

    private fun initUI() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        if (userData == null) {
            binding.tlTitle.visibility = View.VISIBLE
            return
        }

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

    /**
     * Called when the user re-taps the Favourite tab in bottom nav.
     * Re-fetches the favourites list.
     */
    override fun refresh() {
        // Guard: see HomeFragment.refresh — MainActivity may fire this after a
        // configuration change (e.g. split-screen) before our view is rebound.
        if (view == null || !::binding.isInitialized) return
        loadFavouritesList(resetData = true)
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
        if (!::binding.isInitialized) return
        val shouldShow = isLoading && !binding.swipeRefreshLayout.isRefreshing
        binding.progressBar.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    override fun onNetworkRetry() {
        loadFavouritesList(resetData = true)
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

    override fun onResume() {
        super.onResume()

        // Always refresh favorites list when returning to this screen
        Log.d("FavouriteFragment", "🔄 onResume - refreshing favourites list")
        loadFavouritesList(resetData = true)

        // Reset flags if they were set
        if (FcmUtils.shouldRefreshCallList == 1) {
            FcmUtils.shouldRefreshCallList = 0
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up search handler
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }
}

