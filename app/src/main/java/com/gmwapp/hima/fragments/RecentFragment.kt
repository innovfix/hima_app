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
import android.widget.ImageView
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
import com.gmwapp.hima.retrofit.responses.CallsListResponseData
import com.gmwapp.hima.viewmodels.RecentViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecentFragment : BaseFragment(), Refreshable {

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
        setupStatusBarInsets()
        initUI()
        observeViewModel()
        setupFilterChips()
        animateEntrance()
        return binding.root
    }

    /** Professional staggered entrance animation when Recent opens. */
    private fun animateEntrance() {
        // Run only once per app launch — skip on subsequent tab switches.
        if (hasPlayedEntrance) return
        hasPlayedEntrance = true

        val d = resources.displayMetrics.density

        // 1) AppBar: slide down from -40dp + fade in.
        binding.appBarLayout.apply {
            alpha = 0f
            translationY = -40f * d
            animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(100L)
                .setDuration(700L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                .start()
        }

        // 2) Title + subtitle: gentle scale-in.
        listOf<View?>(binding.tvRecentCalls).forEach { v ->
            v ?: return@forEach
            v.alpha = 0f
            v.scaleX = 0.85f
            v.scaleY = 0.85f
            v.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(380L)
                .setDuration(600L)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
                .start()
        }

        // 3) Filter pills: staggered slide-in from left.
        listOf(
            binding.chipAll,
            binding.chipMissed,
            binding.chipTalkTime,
            binding.chipAZ
        ).forEachIndexed { idx, pill ->
            pill.alpha = 0f
            pill.translationX = -80f * d
            pill.animate()
                .alpha(1f)
                .translationX(0f)
                .setStartDelay(550L + idx * 130L)
                .setDuration(550L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f))
                .start()
        }

        // 4) Search bar: fade + slight slide-up (last).
        binding.cardSearch.apply {
            alpha = 0f
            translationY = 24f * d
            animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(1100L)
                .setDuration(550L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f))
                .start()
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

        // Defer initial data load by one frame so the fragment transition animation
        // can finish smoothly first — eliminates the brief stutter on tab switch.
        recentCallsAdapter.setFilter(currentSortType)
        binding.root.post {
            loadCallsList(currentSortType, resetData = true)
        }

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
        loadCallsList(currentSortType, resetData = true, searchQuery = currentSearchQuery)
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
                val wasEmpty = recentCallsAdapter.itemCount == 0
                recentCallsAdapter.addData(it.data)
                // Replay the fall-down layout animation only on initial/full reload
                // so it matches the Home tab's first-load behaviour.
                if (wasEmpty) binding.rvCalls.scheduleLayoutAnimation()
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
        // Apply initial styling (All is selected by default).
        updateChipStyles()

        binding.chipAll.setOnClickListener { selectChip("recent") }
        binding.chipMissed.setOnClickListener { selectChip("missed") }
        binding.chipAZ.setOnClickListener { selectChip("a_z") }
        binding.chipTalkTime.setOnClickListener {
            // Always open the days-picker dialog when Talk Time is tapped.
            val previousSortType = currentSortType
            showTalkTimeDaysDialog(
                onDaySelected = { selectedDays ->
                    val changed = currentSortType != "talk_time" || currentDaysFilter != selectedDays
                    currentSortType = "talk_time"
                    currentDaysFilter = selectedDays
                    updateChipStyles()
                    if (!::recentCallsAdapter.isInitialized) return@showTalkTimeDaysDialog
                    recentCallsAdapter.setFilter(currentSortType)
                    if (changed) {
                        loadCallsList(currentSortType, resetData = true)
                    }
                },
                onDismissWithoutSelection = {
                    restoreChipSelection(previousSortType)
                }
            )
        }
    }

    /** Apply the same sort type and update the pill styles. */
    private fun selectChip(sortType: String) {
        if (isProgrammaticChipSelection) return
        val changed = currentSortType != sortType || currentDaysFilter != 0
        currentSortType = sortType
        currentDaysFilter = 0
        updateChipStyles()
        if (!::recentCallsAdapter.isInitialized) return
        recentCallsAdapter.setFilter(currentSortType)
        if (changed) {
            loadCallsList(currentSortType, resetData = true)
        }
        if (currentSortType == "missed") {
            loadMissedCallCount(seen = 1)
        }
    }

    /** Glass inactive pill — light grey fill + thin border (matches Home). */
    private fun makeGlassPillBg(): android.graphics.drawable.RippleDrawable {
        val density = resources.displayMetrics.density
        val solid = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 14f * density
            setColor(0xFFF5F5F7.toInt())
            setStroke((1 * density).toInt(), 0xFFE0E0E0.toInt())
        }
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x14000000),
            solid, null
        )
    }

    /** Active pill — pink → purple gradient (matches Home active pill). */
    private fun makeActivePillBg(
        startColor: Int, endColor: Int
    ): android.graphics.drawable.RippleDrawable {
        val density = resources.displayMetrics.density
        val grad = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(startColor, endColor)
        ).apply { cornerRadius = 14f * density }
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33FFFFFF),
            grad, null
        )
    }

    /** Style each filter pill — pink→purple gradient when active (matches Home). */
    private fun updateChipStyles() {
        val whiteText    = resources.getColor(R.color.white, null)
        val inactiveText = 0xFF555566.toInt()
        val whiteTint    = android.content.res.ColorStateList.valueOf(whiteText)
        val darkTint     = android.content.res.ColorStateList.valueOf(inactiveText)

        val all  = binding.chipAll
        val miss = binding.chipMissed
        val talk = binding.chipTalkTime
        val az   = binding.chipAZ

        listOf(all, miss, talk, az).forEach {
            it.background         = makeGlassPillBg()
            it.backgroundTintList = null
            it.setTextColor(inactiveText)
            it.iconTint           = darkTint
        }

        val selected = when (currentSortType) {
            "missed"    -> miss
            "talk_time" -> talk
            "a_z"       -> az
            else        -> all
        }
        selected.apply {
            background         = makeActivePillBg(0xFFE91E63.toInt(), 0xFF9C27B0.toInt())
            backgroundTintList = null
            setTextColor(whiteText)
            iconTint           = whiteTint
        }
    }

    private fun showTalkTimeDaysDialog(
        onDaySelected: (Int) -> Unit,
        onDismissWithoutSelection: () -> Unit
    ) {
        if (isTalkTimeDialogOpen) return
        isTalkTimeDialogOpen = true

        val view = layoutInflater.inflate(R.layout.dialog_talk_time_range, null)
        val dialog = android.app.AlertDialog.Builder(requireContext()).setView(view).create()
        // Transparent window so our custom rounded background shows through.
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        // 88% screen width for a comfortable modal width.
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val option7  = view.findViewById<View>(R.id.option_7)
        val option15 = view.findViewById<View>(R.id.option_15)
        val option30 = view.findViewById<View>(R.id.option_30)
        val check7   = view.findViewById<ImageView>(R.id.check_7)
        val check15  = view.findViewById<ImageView>(R.id.check_15)
        val check30  = view.findViewById<ImageView>(R.id.check_30)
        val btnCancel = view.findViewById<View>(R.id.btn_cancel)

        // Highlight the currently-selected option if Talk Time was already active.
        fun applySelection(days: Int) {
            check7.setImageResource(if (days == 7) R.drawable.ic_dialog_radio_on else R.drawable.ic_dialog_radio_off)
            check15.setImageResource(if (days == 15) R.drawable.ic_dialog_radio_on else R.drawable.ic_dialog_radio_off)
            check30.setImageResource(if (days == 30) R.drawable.ic_dialog_radio_on else R.drawable.ic_dialog_radio_off)
        }
        if (currentSortType == "talk_time" && currentDaysFilter > 0) {
            applySelection(currentDaysFilter)
        }

        var hasSelection = false
        fun pick(days: Int) {
            hasSelection = true
            applySelection(days)
            view.postDelayed({
                onDaySelected(days)
                dialog.dismiss()
            }, 120)
        }

        option7.setOnClickListener  { pick(7)  }
        option15.setOnClickListener { pick(15) }
        option30.setOnClickListener { pick(30) }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.setOnCancelListener {
            isTalkTimeDialogOpen = false
            if (!hasSelection) onDismissWithoutSelection()
        }
        dialog.setOnDismissListener {
            isTalkTimeDialogOpen = false
            if (!hasSelection) onDismissWithoutSelection()
        }
        dialog.show()
    }

    private fun restoreChipSelection(sortType: String) {
        isProgrammaticChipSelection = true
        currentSortType = sortType
        updateChipStyles()
        isProgrammaticChipSelection = false
    }

    private fun loadMissedCallCount(seen: Int = 0) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        recentViewModel.getMissedCallCount(userData.id, seen)
    }

    private fun updateMissedChipCount(count: Int) {
        currentMissedCount = count.coerceAtLeast(0)
        val chip = binding.chipMissed
        val ctx = chip.context

        if (currentMissedCount > 0) {
            // Highlight the unread missed-call count in red so users notice it.
            val label = "Missed "
            val badge = "($currentMissedCount)"
            val red = ContextCompat.getColor(ctx, R.color.chat_recording_red)
            val span = SpannableString(label + badge).apply {
                setSpan(ForegroundColorSpan(red), label.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(android.graphics.Typeface.BOLD), label.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            chip.text = span
        } else {
            chip.text = "Missed"
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

        loadMissedCallCount(seen = 0)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up search handler
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        // Clean up listeners when fragment is destroyed
//        unreadCountsMap.clear()
    }

    companion object {
        // Survives fragment recreation within the same process, so the entrance
        // animation plays only on the first open per app launch.
        private var hasPlayedEntrance = false
    }
}
