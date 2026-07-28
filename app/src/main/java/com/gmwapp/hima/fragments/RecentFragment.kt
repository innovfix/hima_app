package com.gmwapp.hima.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.gmwapp.hima.callbacks.Refreshable
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentRecentBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.retrofit.responses.CallsListResponseData
import com.gmwapp.hima.viewmodels.RecentViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import com.gmwapp.hima.utils.applyHimaRefreshColors

@AndroidEntryPoint
class RecentFragment : BaseFragment(), Refreshable {

    private lateinit var binding: FragmentRecentBinding
    private val recentViewModel: RecentViewModel by viewModels()
    private lateinit var recentCallsAdapter: RecentCallsAdapter
    private var isLoading = false
    // B_017 — set for a silent (deferred, post-call) reset-load: the observer then
    // diff-replaces the list in place instead of appending onto a cleared list, so
    // the second refresh doesn't blank+refill (no visible flicker).
    private var pendingSilentReplace = false
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
    // TC_018: the deferred post-call refresh (B107/B109). Held so a new onResume
    // (or teardown) can cancel a still-pending one instead of stacking another
    // full list-reset on top — prevents the offset race + repeated flicker.
    private var deferredRefreshRunnable: Runnable? = null
    private val SEARCH_DEBOUNCE_DELAY = 300L // 300ms delay

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecentBinding.inflate(inflater, container, false)
        setupStatusBarInsets()
        initUI()
        observeViewModel()
        setupFilterChips()
        setupSwipeBetweenPills()
        return binding.root
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

        // Setup search listener
        setupSearchListener()

        // Swipe to refresh
        binding.swipeRefreshLayout.applyHimaRefreshColors()
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

    /**
     * Called when the user re-taps the Recent tab in bottom nav.
     * Re-fetches the calls list with the current sort/search filters.
     */
    override fun refresh() {
        // Guard: see HomeFragment.refresh — MainActivity may fire this after a
        // configuration change (e.g. split-screen) before our view is rebound.
        if (view == null || !::binding.isInitialized) return
        loadCallsList(currentSortType, resetData = true, searchQuery = currentSearchQuery)
    }

    private fun loadCallsList(sortType: String, resetData: Boolean, searchQuery: String = "", silent: Boolean = false) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        val days = if (sortType == "talk_time") currentDaysFilter else 0
        if (sortType == "talk_time" && days <= 0) {
            binding.swipeRefreshLayout.isRefreshing = false
            isLoading = false
            setLoading(false)
            return
        }

        // B_017 — a silent reset keeps the current rows on screen and diff-replaces
        // them when the response lands (no clear, no spinner). Used by the deferred
        // post-call refresh so the second refresh doesn't flicker.
        pendingSilentReplace = silent && resetData
        if (resetData) {
            offset = 0
            isLoading = true
            if (!silent) {
                setLoading(true)
                if (::recentCallsAdapter.isInitialized) {
                    recentCallsAdapter.clearData()
                }
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
                // B_017 — a silent (deferred, post-call) refresh replaces the rows in
                // place via DiffUtil so the list never blanks; a normal reset already
                // cleared the list, so it appends as before.
                if (pendingSilentReplace) {
                    recentCallsAdapter.setData(it.data)
                } else {
                    recentCallsAdapter.addData(it.data)
                }
            } else if (recentCallsAdapter.itemCount == 0) {
                binding.tlTitle.visibility = View.VISIBLE
                binding.rvCalls.visibility = View.GONE
            }
            // A silent refresh that returned empty deliberately leaves the existing
            // rows untouched (don't blank on a transient empty post-call response).
            pendingSilentReplace = false
        })

        recentViewModel.callsListErrorLiveData.observe(viewLifecycleOwner, Observer {
            binding.swipeRefreshLayout.isRefreshing = false
            isLoading = false
            setLoading(false)
            pendingSilentReplace = false
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
        if (!::binding.isInitialized) return
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
        binding.chipAll.setOnSingleClickListener { onPillSelected("recent") }
        binding.chipMissed.setOnSingleClickListener { onPillSelected("missed") }
        binding.chipTalkTime.setOnSingleClickListener { onPillSelected("talk_time") }
        binding.chipAZ.setOnSingleClickListener { onPillSelected("a_z") }
        styleChips(currentSortType)
    }

    /** Ordered filter pills, left→right, for swipe navigation. */
    private val pillOrder = listOf("recent", "missed", "talk_time", "a_z")

    /**
     * Swipe horizontally across the call list to move between filter pills — same
     * effect as tapping. Matches the Home page: swipe RIGHT→LEFT = next pill
     * (All→Missed→…), LEFT→RIGHT = previous. Observational only, so vertical scroll
     * is untouched.
     */
    private fun setupSwipeBetweenPills() {
        if (!::binding.isInitialized) return
        val ctx = context ?: return
        val detector = android.view.GestureDetector(ctx,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: android.view.MotionEvent?, e2: android.view.MotionEvent,
                    velocityX: Float, velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    // Decisive horizontal fling only — must clearly beat the vertical
                    // component so we never fight list scroll or pull-to-refresh.
                    if (kotlin.math.abs(dx) > 80f &&
                        kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2f &&
                        kotlin.math.abs(velocityX) > 800f
                    ) {
                        goToAdjacentPill(if (dx < 0) 1 else -1) // match Home: swipe right→left = next
                        return true
                    }
                    return false
                }
            })
        binding.rvCalls.addOnItemTouchListener(object :
            androidx.recyclerview.widget.RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(
                rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent
            ): Boolean {
                detector.onTouchEvent(e)
                return false // never steal — purely observational
            }
            override fun onTouchEvent(
                rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent
            ) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
        // B_039 — the RecyclerView listener above only sees touches while the list is
        // shown; on an empty tab rv_calls is GONE and the "No Data Found" state is up,
        // so swipes over that area were never detected → you couldn't swipe into OR
        // out of an empty tab. Feed the SAME detector from swipeRefreshLayout, which is
        // match_parent and always present; when rv_calls is GONE its touches (and the
        // non-consuming empty-state view's) fall through to it. Returns false so
        // pull-to-refresh and normal list touches are never affected.
        binding.swipeRefreshLayout.setOnTouchListener { _, e ->
            detector.onTouchEvent(e)
            false
        }
    }

    /** Move to the pill `dir` steps away (no wrap-around) and reveal it. */
    private fun goToAdjacentPill(dir: Int) {
        val cur = pillOrder.indexOf(currentSortType)
        if (cur < 0) return
        val next = cur + dir
        if (next !in pillOrder.indices) return
        onPillSelected(pillOrder[next])
        scrollPillIntoView(next)
    }

    /** Scroll the pill row so the newly-selected pill is fully visible. */
    private fun scrollPillIntoView(index: Int) {
        val pill = binding.llFilterPills.getChildAt(index) ?: return
        val hsv = binding.llFilterPills.parent as? android.widget.HorizontalScrollView ?: return
        hsv.post {
            val offset = (32 * resources.displayMetrics.density).toInt()
            hsv.smoothScrollTo((pill.left - offset).coerceAtLeast(0), 0)
        }
    }

    /** Single-selection handler for the custom filter pills. */
    private fun onPillSelected(sortType: String) {
        if (sortType == "talk_time") {
            // Tapping Talk Time always opens the range picker.
            showTalkTimeDaysDialog(
                onDaySelected = { selectedDays ->
                    val changed = currentSortType != "talk_time" || currentDaysFilter != selectedDays
                    currentSortType = "talk_time"
                    currentDaysFilter = selectedDays
                    styleChips(currentSortType)
                    if (!::recentCallsAdapter.isInitialized) return@showTalkTimeDaysDialog
                    recentCallsAdapter.setFilter(currentSortType)
                    if (changed) {
                        // B_027 — keep the active search filter when switching tabs.
                        loadCallsList(currentSortType, resetData = true, searchQuery = currentSearchQuery)
                    }
                },
                // Cancelled — leave selection where it was.
                onDismissWithoutSelection = { styleChips(currentSortType) }
            )
            return
        }

        val changed = currentSortType != sortType || currentDaysFilter != 0
        currentSortType = sortType
        currentDaysFilter = 0
        styleChips(currentSortType)
        if (!::recentCallsAdapter.isInitialized) return
        recentCallsAdapter.setFilter(currentSortType)
        if (changed) {
            // B_027 — keep the active search filter when switching tabs.
            loadCallsList(currentSortType, resetData = true, searchQuery = currentSearchQuery)
        }
        if (currentSortType == "missed") {
            loadMissedCallCount(seen = 1)
        }
    }

    /** Paint the selected pill with the gradient; others stay white/outlined. */
    private fun styleChips(selected: String) {
        val items = listOf(
            Triple(binding.chipAll, binding.tvChipAll, "recent"),
            Triple(binding.chipMissed, binding.tvChipMissed, "missed"),
            Triple(binding.chipTalkTime, binding.tvChipTalkTime, "talk_time"),
            Triple(binding.chipAZ, binding.tvChipAZ, "a_z"),
        )
        val density = resources.displayMetrics.density
        val grey = ContextCompat.getColor(requireContext(), R.color.grey_medium)
        items.forEach { (card, tv, sort) ->
            val inner = card.getChildAt(0)
            // B_029 — the pill icon is the first child of the inner row. Its tint was
            // never updated on selection, so it stayed grey_medium against the dark
            // purple-pink gradient and was hard to see. Flip it white when selected
            // (matching the text) and back to grey when not, for clear contrast.
            val icon = (inner as? android.view.ViewGroup)?.getChildAt(0) as? android.widget.ImageView
            if (sort == selected) {
                inner.setBackgroundResource(R.drawable.bg_chip_gradient)
                card.strokeWidth = 0
                tv.setTextColor(android.graphics.Color.WHITE)
                icon?.setColorFilter(android.graphics.Color.WHITE)
            } else {
                inner.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                card.strokeWidth = (1.2f * density).toInt()
                tv.setTextColor(grey)
                icon?.setColorFilter(grey)
            }
        }
        // Keep the Missed badge text/colour in sync with the selection.
        applyMissedText(selected == "missed")
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
        styleChips(sortType)
    }

    private fun loadMissedCallCount(seen: Int = 0) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        recentViewModel.getMissedCallCount(userData.id, seen)
    }

    private fun updateMissedChipCount(count: Int) {
        currentMissedCount = count.coerceAtLeast(0)
        applyMissedText(currentSortType == "missed")
    }

    /** Render the Missed pill label (with the red unread badge), base colour matched to selection. */
    private fun applyMissedText(selected: Boolean) {
        val tv = binding.tvChipMissed
        val base = if (selected) android.graphics.Color.WHITE
                   else ContextCompat.getColor(requireContext(), R.color.grey_medium)
        if (currentMissedCount > 0) {
            val label = "Missed "
            val badge = "($currentMissedCount)"
            val red = ContextCompat.getColor(requireContext(), R.color.chat_recording_red)
            val span = SpannableString(label + badge).apply {
                setSpan(ForegroundColorSpan(red), label.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(android.graphics.Typeface.BOLD), label.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            tv.text = span
        } else {
            tv.text = "Missed"
        }
        tv.setTextColor(base)
    }

    override fun onResume() {
        super.onResume()

        // Skip if a load is already in flight (e.g. initUI's first-open page-0 fetch) so
        // we don't fire a second reset-load that races the first — the root of the
        // duplicate-row-on-first-open bug (two page-0 responses appended back-to-back).
        // Same "skip if in-flight" guard the deferred refresh below uses; the in-flight
        // load already delivers fresh data. We fix it here rather than de-duping in the
        // adapter, because the recent list is one-row-per-call and its `id` is the peer
        // user id, so de-duping by id would wrongly merge legitimate repeat calls.
        if (FcmUtils.isUserAvailable==0 && !isLoading){
            loadCallsList(currentSortType, resetData = true)
        }

        if (FcmUtils.shouldRefreshCallList == 1) {
            Log.d("RecentFragment", "Call rejected detected, refreshing call list")
            loadCallsList(currentSortType, resetData = true)
            FcmUtils.shouldRefreshCallList = 0  // Reset flag after refresh
        }

        loadMissedCallCount(seen = 0)

        // B107 + B109 — CallUpdateWorker writes ended_time via WorkManager
        // async, so when a user finishes a call and immediately opens Recent
        // (or types the creator's name in search) the worker often hasn't
        // fired yet → the just-ended call is missing from the response.
        // Schedule a second refresh ~1.5s after onResume to catch the
        // worker's write. Pass currentSearchQuery so an active search filter
        // is preserved on re-query (B109): without this the deferred refresh
        // would wipe the user's "san" search filter back to the unfiltered
        // list. Idempotent: if the worker already fired, just re-fetches.
        // Cancel any still-pending deferred refresh from a previous resume so
        // rapid tab-switches don't stack multiple list-resets (TC_018).
        deferredRefreshRunnable?.let { searchHandler.removeCallbacks(it) }
        deferredRefreshRunnable = Runnable {
            // Skip if a load is already in flight — the in-flight one will
            // deliver fresh data; a second reset here only races on `offset`.
            if (isAdded && !isDetached && !isLoading) {
                loadCallsList(
                    currentSortType,
                    resetData = true,
                    searchQuery = currentSearchQuery,
                    // B_017 — silent in-place replace so this second (post-call)
                    // refresh updates durations without the visible blank+refill.
                    silent = true
                )
            }
        }
        searchHandler.postDelayed(deferredRefreshRunnable!!, 1500L)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up search handler
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        deferredRefreshRunnable?.let { searchHandler.removeCallbacks(it) }
        // Clean up listeners when fragment is destroyed
//        unreadCountsMap.clear()
    }
}
