package com.gmwapp.hima.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BaseApplication.Companion.getInstance
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.IplRoomsActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.adapters.FemaleUserAdapter
import com.gmwapp.hima.agora.AgoraRandomCallActivity
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.callbacks.NetworkRetryable
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.callbacks.Refreshable
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentHomeBinding
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponseData
import com.gmwapp.hima.utils.Helper
import com.gmwapp.hima.utils.PinnedChatsPrefsHelper
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.onesignal.OneSignal
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch


@AndroidEntryPoint
class HomeFragment : BaseFragment(), NetworkRetryable, Refreshable {
    private var isAllFabVisible: Boolean = false
    private var filterType: String = "my_chats" // Default filter is "my_chats" — open on Chats tab
    lateinit var binding: FragmentHomeBinding
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()

    @javax.inject.Inject
    lateinit var myChatsApiManager: com.gmwapp.hima.retrofit.ApiManager

    /** Last my-chats payload (unsorted); re-sorted on pin toggle. */
    private var homeMyChatsRawConversations: List<com.gmwapp.hima.models.ChatConversation> = emptyList()
    private var homeMyChatsAdapter: com.gmwapp.hima.adapters.ChatListAdapter? = null

    // Last-message timestamp (epoch seconds) at the moment a row was tapped.
    // Used to mask stale unread counts returned by my_chat that race mark_read.
    private val readChatLastMsgSeconds = mutableMapOf<String, Long>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(layoutInflater)

        setupStatusBarInsets()
        applyBrandTitleGradient()
        initUI()
        observeLiveUserStatus()
        setupSwipeToRefresh()
        return binding.root
    }

    /** Paint the "Hi ma" brand title with a normal brand pink. */
    private fun applyBrandTitleGradient() {
        val tv = binding.tvLogo
        tv.post {
            val w = tv.paint.measureText(tv.text.toString()).coerceAtLeast(1f)
            val shader = android.graphics.LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(
                    0xFFFF2E9A.toInt(),  // normal brand pink
                    0xFFFF2E9A.toInt()   // same — solid pink, no gradient fade
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            tv.paint.shader = shader
            tv.invalidate()
        }
    }

    private fun setupStatusBarInsets() {
        // Modern WindowInsets API — pads the AppBar by the actual status-bar inset
        // (works with edge-to-edge enabled at the activity level) and the RecyclerView
        // by the bottom inset (so content can scroll under the system nav).
        val contentPaddingTopPx = (8 * resources.displayMetrics.density).toInt()
        val rvBottomPaddingPx = (72 * resources.displayMetrics.density).toInt()

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, windowInsets ->
            val sysBars = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
            view.setPadding(
                view.paddingLeft,
                sysBars.top + contentPaddingTopPx,
                view.paddingRight,
                view.paddingBottom
            )
            windowInsets
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.rvProfiles) { view, windowInsets ->
            val sysBars = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                sysBars.bottom + rvBottomPaddingPx
            )
            windowInsets
        }

        binding.appBarLayout.requestApplyInsets()
        binding.rvProfiles.requestApplyInsets()
    }

    private fun refreshIplBanner() {
        if (!::binding.isInitialized) return
        // FeatureFlags.IPL_ENABLED is an app-side kill-switch that overrides the
        // server flag — when false the banner stays GONE regardless of ipl_rooms_enabled.
        val iplEnabled = com.gmwapp.hima.utils.FeatureFlags.IPL_ENABLED &&
            (BaseApplication.getInstance()?.getPrefs()?.getUserData()?.ipl_rooms_enabled ?: 0) == 1
        binding.cardIplRooms.visibility = if (iplEnabled) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun initUI() {
        binding.clCoins.setOnSingleClickListener {
            val intent = Intent(context, WalletActivity::class.java)
            startActivity(intent)
        }

        // IPL Room Calls banner — male opens room list screen
        binding.cardIplRooms.setOnClickListener {
            startActivity(Intent(requireContext(), com.gmwapp.hima.activities.IplRoomsActivity::class.java))
        }
        refreshIplBanner()

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val language = userData?.language

        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val isTagSet = sharedPreferences.getBoolean("isOneSignalTagSet", false)


        // Subscription is handled centrally in BaseApplication (and at OTP success).
        // All that remains here is setting user-scoped tags and prompting for
        // notification permission — without the logout/optOut churn that used to
        // strand devices in the opted-out state.
        if (userData?.id != null && userData.id > 0) {
            OneSignal.User.addTag("gender", "male")
            language?.let {
                OneSignal.User.addTag("language", it)
                OneSignal.User.addTag("gender_language", "male_$it")
                Log.d("OneSignalTag", "tags set language=$it gender_language=male_$it")
            }

            val notifPrefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val lastAsked = notifPrefs.getLong("notif_permission_last_asked", 0L)
            if (System.currentTimeMillis() - lastAsked >= 24 * 60 * 60 * 1000L) {
                notifPrefs.edit().putLong("notif_permission_last_asked", System.currentTimeMillis()).apply()
                viewLifecycleOwner.lifecycleScope.launch {
                    OneSignal.Notifications.requestPermission(true)
                }
            }
        }










//        // Send the tag only if it hasn't been set before
//        if (!isTagSet) {
//            OneSignal.User.addTag("gender", "male")
//            language?.let {
//                OneSignal.User.addTag("language", it)
//                Log.d("OneSignal", "Language tag added: $it")
//            }
//
//            // Mark the flag so this doesn't happen again
//            sharedPreferences.edit().putBoolean("isOneSignalTagSet", true).apply()
//        } else {
//            Log.d("OneSignaltag", "Tag already set, skipping... ")
//        }
//



//
//        language?.let { OneSignal.User.addTag("language", it)
//            Log.d("OneSignaltag", "Language tag added: $it")
//        }
//
//        OneSignal.User.addTag("gender", "male")
//        Log.d("Gender","Male")



        // Setup filter pills
        setupFilterButtons()

        // If coming from AI onboarding, default to "My Chats" tab
        val showMyChats = activity?.intent?.getBooleanExtra("SHOW_MY_CHATS", false) ?: false
        val fromOnboarding = activity?.intent?.getBooleanExtra("FROM_ONBOARDING", false) ?: false
        if (showMyChats) {
            filterType = "my_chats"
            activity?.intent?.removeExtra("SHOW_MY_CHATS")
        }

        userData?.id?.let {
            if (context?.let { it1 -> isInternetAvailable(it1) } == true) {
                // Chats is now the default tab. Load the appropriate list based
                // on the active filter — chats API for the Chats tab, female
                // users API otherwise. Keep styles in sync after the first
                // render too so the highlighted pill matches the active tab.
                updateFilterButtonStyles()
                if (filterType == "my_chats") {
                    loadMyChats(it)
                    // The server seeds the chat rows asynchronously after AI
                    // onboarding completes. The first my_chat call can land
                    // before the rows exist, returning an empty list. Re-fire
                    // after a short delay so the list fills in without the
                    // user needing to pull-to-refresh manually.
                    if (fromOnboarding) {
                        activity?.intent?.removeExtra("FROM_ONBOARDING")
                        binding.rvProfiles.postDelayed({
                            if (filterType == "my_chats" && isAdded) {
                                loadMyChats(it)
                            }
                        }, 1500L)
                    }
                } else {
                    loadFemaleUsers(it)
                }
            } else {
                binding.tvNointernet.visibility = View.VISIBLE
                setLoading(false)
            }
        }
        userData?.id?.let { profileViewModel.getUsers(it) }

//        profileViewModel.getUserLiveData.observe(viewLifecycleOwner, Observer {
//            it.data?.let { it1 ->
//                BaseApplication.getInstance()?.getPrefs()?.setUserData(it1)
//            }
//            binding.tvCoins.text = it.data?.coins.toString()
//            Log.d("coinsvalue","${it.data?.coins}")
//            Log.d("coinsvalue","${it.data?.name}")
//
//        })

        profileViewModel.getUserLiveData.observe(viewLifecycleOwner, Observer { response ->
            response?.data?.let { userData ->
                BaseApplication.getInstance()?.getPrefs()?.setUserData(userData)
                binding.tvCoins.text = userData.coins.toString()
                Log.d("coinsvalue", "${userData.coins}")
                Log.d("coinsvalue", "${userData.name}")
                // Refresh IPL banner visibility once user data arrives from server
                refreshIplBanner()
                refreshStarTabVisibility()
            } ?: Log.e("HomeFragment", "RegisterResponse is null")
        })




        binding.fabAudio.setOnSingleClickListener {
            val intent = Intent(context, AgoraRandomCallActivity::class.java)
            intent.putExtra(DConstants.CALL_TYPE, "audio")
            intent.putExtra("RANDOM_FILTER", filterType)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }

        binding.fabVideo.setOnSingleClickListener {
            val intent = Intent(context, AgoraRandomCallActivity::class.java)
            intent.putExtra(DConstants.CALL_TYPE, "video")
            intent.putExtra("RANDOM_FILTER", filterType)
            startActivity(intent)
        }

        femaleUsersViewModel.femaleUsersResponseLiveData.observe(viewLifecycleOwner, Observer {


//            if (it?.data != null) {
//                Toast.makeText(activity, it?.message, Toast.LENGTH_SHORT).show()
//            }
//            else if (it.data?.isEmpty() == true) {
//                Toast.makeText(activity, "No Data Found", Toast.LENGTH_SHORT).show()
//            }

//            it.data?.firstOrNull()?.audio_status?.let { audioStatus ->
//                Log.d("responsecheck", "Audio Status: $audioStatus")
//            }

            if (it?.data != null) {
                binding.rvProfiles.layoutManager =
                    LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)

                // Row click is handled inside the adapter (opens ChatActivityInHouse).
                // The audio/video listeners are kept as no-ops for constructor compatibility.
                val noOpListener = object : OnItemSelectionListener<FemaleUsersResponseData> {
                    override fun onItemSelected(data: FemaleUsersResponseData) {}
                }
                val transactionAdapter = activity?.let { context ->
                    FemaleUserAdapter(context, it.data, noOpListener, noOpListener)
                }
                binding.rvProfiles.adapter = transactionAdapter
                // Replay the staggered fall-down animation on each data load.
                binding.rvProfiles.scheduleLayoutAnimation()
            }

            // Stop the swipe-to-refresh loading animation
            binding.swipeRefreshLayout.isRefreshing = false
            setLoading(false)
            refreshMaleHomeNetworkPlaceholder()
        })

        femaleUsersViewModel.femaleUsersErrorLiveData.observe(viewLifecycleOwner, Observer {
            binding.swipeRefreshLayout.isRefreshing = false
            setLoading(false)
            refreshMaleHomeNetworkPlaceholder()
        })

        binding.btnRetryNoNetworkHome.setOnClickListener {
            if (!Helper.checkNetworkConnection()) return@setOnClickListener
            binding.tvNointernet.visibility = View.GONE
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let { loadFemaleUsers(it) }
        }

        BaseApplication.getInstance()?.networkConnectedLiveData?.observe(viewLifecycleOwner) {
            refreshMaleHomeNetworkPlaceholder()
        }
        refreshMaleHomeNetworkPlaceholder()

        initFab()
    }

    private fun refreshMaleHomeNetworkPlaceholder() {
        val online = when (val v = BaseApplication.getInstance()?.networkConnectedLiveData?.value) {
            null -> Helper.checkNetworkConnection()
            else -> v
        }
        val count = binding.rvProfiles.adapter?.itemCount ?: 0
        if (!online && count == 0) {
            binding.tvNointernet.visibility = View.VISIBLE
            binding.rvProfiles.visibility = View.GONE
        } else {
            binding.tvNointernet.visibility = View.GONE
            binding.rvProfiles.visibility = View.VISIBLE
        }
    }

//    private fun setupSwipeToRefresh() {
//        binding.swipeRefreshLayout.setOnRefreshListener {
//            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
//            userData?.id?.let {
//                if (context?.let { context -> isInternetAvailable(context) } == true) {
//                    loadFemaleUsers(it)
//                    Log.d("refreshing","refreshing")
//                } else {
//                    binding.tvNointernet.visibility = View.VISIBLE
//                    binding.swipeRefreshLayout.isRefreshing = false
//                }
//            }
//        }
//    }


    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            userData?.id?.let {
                if (context?.let { context -> isInternetAvailable(context) } == true) {
                    // Route refresh through the API that matches the active filter.
                    // Pulling down on My Chats used to fire loadFemaleUsers(), which
                    // silently replaced the chats list with the All-users list.
                    if (filterType == "my_chats") {
                        loadMyChats(it)
                    } else {
                        // Clear the existing data
                        femaleUsersViewModel.femaleUsersResponseLiveData.value?.data?.clear()

                        // Notify the adapter that data has been cleared
                        (binding.rvProfiles.adapter as? FemaleUserAdapter)?.notifyDataSetChanged()

                        // Reload the data with current filter
                        loadFemaleUsers(it)
                    }
                    Log.d("refreshing", "refreshing filter=$filterType")
                } else {
                    binding.tvNointernet.visibility = View.VISIBLE
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }


    private fun loadFemaleUsers(userId: Int) {
        setLoading(true)
        val filter = when (filterType) {
            "new"  -> "new"
            "star" -> "star"
            else   -> null
        }
        femaleUsersViewModel.getFemaleUsers(userId, filter)
    }

    /**
     * Called when the user re-taps the Home tab in bottom nav.
     * Re-fetches the female users list with the current filter.
     *
     * Bail out if the fragment's view isn't ready — MainActivity programmatically
     * sets selectedItemId from onCreate, which fires this callback on rotation
     * before the fragment's onCreateView has re-inflated the binding.
     */
    override fun refresh() {
        if (!isAdded || view == null) return
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        loadFemaleUsers(userId)
    }

    private fun setLoading(isLoading: Boolean) {
        val shouldShow = isLoading && !binding.swipeRefreshLayout.isRefreshing
        binding.progressBar.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }
    
    private fun setupFilterButtons() {
        Log.d("FilterButtons", "setupFilterButtons called - initial filterType: $filterType")

        // Set initial state
        updateFilterButtonStyles()
        refreshStarTabVisibility()

        binding.btnFilterMyChats.setOnClickListener { applyFilter("my_chats") }
        binding.btnFilterAll.setOnClickListener { applyFilter("all") }
        binding.btnFilterNew.setOnClickListener { applyFilter("new") }
        binding.btnFilterStar.setOnClickListener { applyFilter("star") }
    }

    private fun refreshStarTabVisibility() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val showStar = (userData?.startab ?: 0) == 1
        binding.btnFilterStar.visibility = if (showStar) View.VISIBLE else View.GONE
    }

    // ── Filter pill helpers ──────────────────────────────────────────────────

    /** Inactive pill — light surface with subtle grey border (white theme). */
    private fun makeGlassPillBg(): android.graphics.drawable.RippleDrawable {
        val density = resources.displayMetrics.density
        val solid = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 14f * density
            setColor(0xFFF5F5F7.toInt())   // light grey fill
            setStroke((1 * density).toInt(), 0xFFE0E0E0.toInt())
        }
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x14000000),
            solid, null
        )
    }

    /** Gradient active pill with the same shorter 14dp radius. */
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

    private fun updateFilterButtonStyles() {
        val whiteText    = resources.getColor(R.color.white, null)
        val inactiveText = 0xFF555566.toInt()   // dark grey — visible on light pill in white theme
        val darkText     = 0xFF1A1A2E.toInt()   // dark navy — for bright gold pill

        val inactiveTint = android.content.res.ColorStateList.valueOf(inactiveText)
        val whiteTint    = android.content.res.ColorStateList.valueOf(whiteText)
        val darkTint     = android.content.res.ColorStateList.valueOf(darkText)

        // Reset all to glass inactive (text + icon use inactive grey)
        listOf(
            binding.btnFilterMyChats,
            binding.btnFilterAll,
            binding.btnFilterNew,
            binding.btnFilterStar
        ).forEach {
            it.background         = makeGlassPillBg()
            it.backgroundTintList = null
            it.setTextColor(inactiveText)
            it.iconTint           = inactiveTint
        }

        // Active tab: hot-pink → deep-purple (matches reference "All" active pill)
        when (filterType) {
            "my_chats" -> binding.btnFilterMyChats.apply {
                background         = makeActivePillBg(0xFFE91E63.toInt(), 0xFF9C27B0.toInt())
                backgroundTintList = null
                setTextColor(whiteText)
                iconTint           = whiteTint
            }
            "all" -> binding.btnFilterAll.apply {
                background         = makeActivePillBg(0xFFE91E63.toInt(), 0xFF9C27B0.toInt())
                backgroundTintList = null
                setTextColor(whiteText)
                iconTint           = whiteTint
            }
            "new" -> binding.btnFilterNew.apply {
                background         = makeActivePillBg(0xFFE91E63.toInt(), 0xFF9C27B0.toInt())
                backgroundTintList = null
                setTextColor(whiteText)
                iconTint           = whiteTint
            }
            "star" -> binding.btnFilterStar.apply {
                background         = makeActivePillBg(0xFFF9D423.toInt(), 0xFFFFB800.toInt())
                backgroundTintList = null
                setTextColor(darkText)   // dark text on gold
                iconTint           = darkTint
            }
        }
    }

    private fun applyFilter(selectedFilter: String) {
        Log.d("FilterButtons", "applyFilter called - current: $filterType, selected: $selectedFilter")
        if (filterType == selectedFilter) {
            Log.d("FilterButtons", "Filter already selected, returning")
            return
        }
        filterType = selectedFilter
        updateFilterButtonStyles()
                        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                        userData?.id?.let { userId ->
                            if (context?.let { it1 -> isInternetAvailable(it1) } == true) {
                                if (selectedFilter == "my_chats") {
                                    loadMyChats(userId)
                                } else {
                                    // Clear existing data
                                    femaleUsersViewModel.femaleUsersResponseLiveData.value?.data?.clear()
                                    (binding.rvProfiles.adapter as? FemaleUserAdapter)?.notifyDataSetChanged()
                                    // Reload with new filter
                                    loadFemaleUsers(userId)
                                }
                            }
                        }
    }

    private fun sortMyChatsPinnedFirst(
        raw: List<com.gmwapp.hima.models.ChatConversation>,
        ctx: Context
    ): List<com.gmwapp.hima.models.ChatConversation> {
        val refreshed = raw.map {
            it.copy(isPinned = PinnedChatsPrefsHelper.isPinned(ctx, it.userId))
        }
        val base = refreshed.sortedByDescending { it.lastMessageTime?.seconds ?: 0 }
        val (pinned, unpinned) = base.partition { it.isPinned }
        val order = PinnedChatsPrefsHelper.getPinnedIds(ctx)
        val sortedPinned = pinned.sortedBy { conv ->
            val i = order.indexOf(conv.userId)
            if (i >= 0) i else Int.MAX_VALUE
        }
        return sortedPinned + unpinned
    }

    private fun loadMyChats(userId: Int) {
        setLoading(true)
        myChatsApiManager.getMyChat(userId, null, 100, 0, object : com.gmwapp.hima.retrofit.callbacks.NetworkCallback<com.gmwapp.hima.retrofit.responses.MyChatResponse> {
            override fun onResponse(
                call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.MyChatResponse>,
                response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.MyChatResponse>
            ) {
                setLoading(false)
                binding.swipeRefreshLayout.isRefreshing = false
                val chats = response.body()?.data?.chats ?: emptyList()
                val activityCtx = activity ?: return
                val mapped = chats.mapNotNull { item ->
                    try {
                        val ts = try {
                            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            val date = item.lastMessage?.timestamp?.let { fmt.parse(it) }
                            date?.let { com.google.firebase.Timestamp(it) }
                        } catch (_: Exception) { null }
                        val readUpTo = readChatLastMsgSeconds[item.user.id.toString()]
                        val incomingSec = ts?.seconds ?: 0L
                        val effectiveUnread = if (readUpTo != null && incomingSec <= readUpTo) 0 else item.unreadCount
                        com.gmwapp.hima.models.ChatConversation(
                            threadId = item.chatId,
                            userId = item.user.id.toString(),
                            userName = item.user.name,
                            userImage = item.user.image ?: "",
                            lastMessage = item.lastMessage?.message ?: "",
                            lastMessageType = item.lastMessage?.messageType ?: "text",
                            lastMessageTime = ts,
                            unreadCount = effectiveUnread,
                            // status == 1 from the backend means the creator is
                            // currently active — mirror the "All" tab's green dot.
                            isOnline = (item.user.status ?: 0) == 1,
                            audioStatus = item.user.audioStatus ?: 1,
                            videoStatus = item.user.videoStatus ?: 1,
                            coinPerMinAudio = item.user.coinPerMinAudio ?: 10,
                            coinPerMinVideo = item.user.coinPerMinVideo ?: 60,
                            language = item.user.language,
                            isPinned = PinnedChatsPrefsHelper.isPinned(activityCtx, item.user.id.toString())
                        )
                    } catch (_: Exception) { null }
                }
                homeMyChatsRawConversations = mapped
                val conversations = sortMyChatsPinnedFirst(mapped, activityCtx)

                lateinit var chatListAdapter: com.gmwapp.hima.adapters.ChatListAdapter
                chatListAdapter = com.gmwapp.hima.adapters.ChatListAdapter(
                    activityCtx,
                    ArrayList(conversations),
                    { conv ->
                        conv.lastMessageTime?.seconds?.let { readChatLastMsgSeconds[conv.userId] = it }
                        chatListAdapter.markConversationAsRead(conv.userId)
                        val intent = Intent(activityCtx, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                            putExtra("USER_ID", conv.userId.toIntOrNull() ?: -1)
                            putExtra("USER_NAME", conv.userName)
                            putExtra("USER_IMAGE", conv.userImage)
                        }
                        startActivity(intent)
                    },
                    myChatsApiManager,
                    onPinToggled = {
                        activity?.let { ctx ->
                            val sorted = sortMyChatsPinnedFirst(homeMyChatsRawConversations, ctx)
                            homeMyChatsAdapter?.updateConversations(ArrayList(sorted))
                        }
                    }
                )
                homeMyChatsAdapter = chatListAdapter
                binding.rvProfiles.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
                binding.rvProfiles.adapter = chatListAdapter
                binding.rvProfiles.visibility = View.VISIBLE
                // Replay staggered fall-down animation on chats load.
                binding.rvProfiles.scheduleLayoutAnimation()
            }

            override fun onFailure(call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.MyChatResponse>, t: Throwable) {
                setLoading(false)
                binding.swipeRefreshLayout.isRefreshing = false
            }

            override fun onNoNetwork() {
                setLoading(false)
                binding.swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    fun initFab() {
        // Start collapsed: audio + video hidden, random visible with label.
        binding.fabAudio.visibility = View.GONE
        binding.fabVideo.visibility = View.GONE
        applyRandomCollapsedStyle()

        // Restore last drag position BEFORE starting animations so the bob
        // baseline uses the persisted offset.
        restoreFabPosition()
        installFabDrag()
        startFabAnimations()

        binding.fabRandom.setOnSingleClickListener {
            if (!isAllFabVisible) {
                showDimBackground()
                binding.fabAudio.visibility = View.VISIBLE
                binding.fabVideo.visibility = View.VISIBLE

                // Expanded: random becomes a white close pill (icon-only).
                applyRandomExpandedStyle()
                isAllFabVisible = true
            } else {
                collapseFabs()
            }
        }

        // Tap the dimmed background to auto-close the expanded FAB stack.
        binding.dimBackground.setOnClickListener {
            if (isAllFabVisible) collapseFabs()
        }
    }

    /** Collapse the audio/video FABs back into the Random pill. */
    private fun collapseFabs() {
        binding.fabAudio.visibility = View.GONE
        binding.fabVideo.visibility = View.GONE
        binding.tvAudio1.visibility = View.GONE
        binding.tvAudio2.visibility = View.GONE
        binding.tvVideo1.visibility = View.GONE
        binding.tvVideo2.visibility = View.GONE
        binding.ivCoinAudio.visibility = View.GONE
        binding.ivCoinVideo.visibility = View.GONE

        hideDimBackground()
        applyRandomCollapsedStyle()
        isAllFabVisible = false
    }

    /** Subtle bob + pulse loop on the 3 FABs. Staggered so they never move in sync. */
    private val fabAnimators = mutableListOf<android.animation.Animator>()

    /** User-set drag offset applied on top of the bob translation. Persists across recreations. */
    private var fabOffsetX = 0f
    private var fabOffsetY = 0f

    private fun startFabAnimations() {
        stopFabAnimations()
        val density = resources.displayMetrics.density
        val bobOffset = -6f * density   // 6dp lift on the up-stroke

        // (view, startDelayMs, peakScale)
        val targets = listOf(
            Triple(binding.fabRandom, 0L, 1.04f),
            Triple(binding.fabAudio, 250L, 1.06f),
            Triple(binding.fabVideo, 500L, 1.06f)
        )

        targets.forEach { (view, delay, peak) ->
            view.translationX = fabOffsetX
            view.translationY = fabOffsetY

            // Bob is a ValueAnimator so we can add the bob offset on top of the
            // drag baseline (fabOffsetY) every frame — keeps drag + bob coherent.
            val bob = ValueAnimator.ofFloat(0f, bobOffset, 0f).apply {
                duration = 2400L
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = delay
                addUpdateListener {
                    view.translationY = fabOffsetY + (it.animatedValue as Float)
                }
            }
            val pulseX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, peak, 1f).apply {
                duration = 1800L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = delay
            }
            val pulseY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, peak, 1f).apply {
                duration = 1800L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = delay
            }
            bob.start(); pulseX.start(); pulseY.start()
            fabAnimators += listOf(bob, pulseX, pulseY)
        }
    }

    private fun stopFabAnimations() {
        fabAnimators.forEach { it.cancel() }
        fabAnimators.clear()
    }

    /** Restore the user's previous drag position before the FABs are laid out. */
    private fun restoreFabPosition() {
        val prefs = requireContext().getSharedPreferences("fab_pos", Context.MODE_PRIVATE)
        fabOffsetX = prefs.getFloat("dx", 0f)
        fabOffsetY = prefs.getFloat("dy", 0f)
        binding.fabRandom.translationX = fabOffsetX
        binding.fabAudio.translationX = fabOffsetX
        binding.fabVideo.translationX = fabOffsetX
        // translationY is owned by the bob updater — it reads fabOffsetY each frame.
    }

    private fun saveFabPosition() {
        requireContext().getSharedPreferences("fab_pos", Context.MODE_PRIVATE)
            .edit()
            .putFloat("dx", fabOffsetX)
            .putFloat("dy", fabOffsetY)
            .apply()
    }

    /**
     * Drag-to-move on the Random FAB. The whole stack (Random + Audio + Video)
     * follows by the same delta so their relative positions stay intact.
     * A tap (movement < touchSlop) still triggers the OnClickListener via performClick().
     */
    private fun installFabDrag() {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop.toFloat()
        var startRawX = 0f
        var startRawY = 0f
        var startOffsetX = 0f
        var startOffsetY = 0f
        var dragging = false

        binding.fabRandom.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startOffsetX = fabOffsetX
                    startOffsetY = fabOffsetY
                    dragging = false
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (!dragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        fabOffsetX = startOffsetX + dx
                        fabOffsetY = startOffsetY + dy
                        applyFabOffset()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        v.performClick()
                    } else {
                        clampFabOffset()
                        applyFabOffset()
                        saveFabPosition()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        clampFabOffset()
                        applyFabOffset()
                        saveFabPosition()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun applyFabOffset() {
        binding.fabRandom.translationX = fabOffsetX
        binding.fabAudio.translationX = fabOffsetX
        binding.fabVideo.translationX = fabOffsetX
        // translationY is written each frame by the bob updater (fabOffsetY + bob).
    }

    /** Keep the dragged stack inside the visible area with a 16dp safety pad. */
    private fun clampFabOffset() {
        val root = binding.root
        val random = binding.fabRandom
        if (root.width == 0 || root.height == 0 || random.width == 0) return
        val density = resources.displayMetrics.density
        val pad = 16f * density

        val rLeft = random.left.toFloat()
        val rTop = random.top.toFloat()
        val w = random.width.toFloat()
        val h = random.height.toFloat()

        // Horizontal: keep random fully on-screen.
        val minDx = pad - rLeft
        val maxDx = (root.width - pad) - rLeft - w
        if (minDx <= maxDx) fabOffsetX = fabOffsetX.coerceIn(minDx, maxDx)

        // Vertical: keep the would-be topmost FAB (audio at 184dp bottom-margin
        // + 56dp height = 240dp from the bottom edge) below the safe-area top.
        // We compute the top from XML constants rather than reading audio.top,
        // because audio is GONE while collapsed and its position is unreliable.
        val stackTopExpected = root.height - (240f * density)
        val minDy = pad - stackTopExpected
        val maxDy = (root.height - pad) - rTop - h
        if (minDy <= maxDy) fabOffsetY = fabOffsetY.coerceIn(minDy, maxDy)
    }

    override fun onDestroyView() {
        stopFabAnimations()
        super.onDestroyView()
    }

    /** Random pill — collapsed state: blue gradient bg, white icon + label visible. */
    private fun applyRandomCollapsedStyle() {
        binding.fabRandomInner.setBackgroundResource(R.drawable.bg_fab_gradient_random)
        binding.ivFabRandom.setImageResource(R.drawable.random)
        binding.ivFabRandom.imageTintList =
            android.content.res.ColorStateList.valueOf(resources.getColor(R.color.white, null))
        binding.tvFabRandom.visibility = View.VISIBLE
        binding.tvFabRandom.setTextColor(resources.getColor(R.color.white, null))
    }

    /** Random pill — expanded state: white bg, black close icon, label hidden. */
    private fun applyRandomExpandedStyle() {
        binding.fabRandomInner.setBackgroundResource(R.drawable.bg_fab_gradient_white)
        binding.ivFabRandom.setImageResource(R.drawable.ic_close)
        binding.ivFabRandom.imageTintList =
            android.content.res.ColorStateList.valueOf(resources.getColor(R.color.black, null))
        binding.tvFabRandom.visibility = View.GONE
    }

    private fun showDimBackground() {
        binding.dimBackground.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(400).start()
        }
    }

    private fun hideDimBackground() {
        binding.dimBackground.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                binding.dimBackground.visibility = View.GONE
            }.start()
    }

    // Check for Internet Connection
    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    override fun onResume() {
        super.onResume()
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.id?.let { profileViewModel.getUsers(it) }
        observeCoins()

        if (FcmUtils.isUserAvailable==0){
            // Respect the active filter on resume — don't silently swap the
            // chats list with the creators list when the user is on Chats.
            userData?.id?.let { uid ->
                if (filterType == "my_chats") loadMyChats(uid) else loadFemaleUsers(uid)
            }
        }

        // Sync selected filter button styles when resuming
        updateFilterButtonStyles()

        checkFemaleStatus()

        // Realtime chat-list update for the my_chats filter.
        if (filterType == "my_chats") {
            registerHomeChatListRefreshReceiver()
            startHomeCollectingSocketNewMessage()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterHomeChatListRefreshReceiver()
    }

    /** Receiver for [ACTION_CHAT_LIST_REFRESH] while the my_chats filter is active. */
    private var homeChatListRefreshReceiver: android.content.BroadcastReceiver? = null
    private var homeChatListRefreshReceiverRegistered: Boolean = false

    private fun registerHomeChatListRefreshReceiver() {
        if (homeChatListRefreshReceiverRegistered) return
        val ctx = context ?: return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                if (!isAdded || intent == null) return
                if (intent.action != com.gmwapp.hima.onesignal.OneSignalNotificationServiceExtension.ACTION_CHAT_LIST_REFRESH) return
                // Only act while the my_chats filter is the visible list.
                if (filterType != "my_chats") return
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
                applyHomeIncoming(peerId, text, type)
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
        homeChatListRefreshReceiver = receiver
        homeChatListRefreshReceiverRegistered = true
    }

    private fun unregisterHomeChatListRefreshReceiver() {
        if (!homeChatListRefreshReceiverRegistered) return
        val ctx = context ?: return
        val receiver = homeChatListRefreshReceiver ?: return
        runCatching { ctx.unregisterReceiver(receiver) }
        homeChatListRefreshReceiver = null
        homeChatListRefreshReceiverRegistered = false
    }

    private fun applyHomeIncoming(peerId: Int, text: String, type: String) {
        val adapter = homeMyChatsAdapter ?: return
        val ts = com.google.firebase.Timestamp.now()
        val suppressUnread = com.gmwapp.hima.utils.ActiveChatTracker.isActiveFor(context, peerId)
        val handled = adapter.applyIncomingMessage(
            peerUserId = peerId.toString(),
            lastMessageText = text,
            lastMessageType = type,
            lastMessageTime = ts,
            suppressUnreadIncrement = suppressUnread
        )
        if (!handled) {
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let { loadMyChats(it) }
        }
    }

    private fun startHomeCollectingSocketNewMessage() {
        val owner = viewLifecycleOwner
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                com.gmwapp.hima.socket.SocketManager.getInstance().newMessage.collect { msg ->
                    if (!isAdded || filterType != "my_chats") return@collect
                    val mySelfId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
                    val peerId = msg.fromUserId ?: return@collect
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
                    applyHomeIncoming(peerId, previewText, previewType)
                }
            }
        }
    }

    fun observeCoins() {
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
            profileViewModel.getUsers(it)
        }
    }

    // Public method to refresh coins from external calls (like after claiming free coins)
    fun refreshCoinsBalance() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.id?.let { 
            // Show premium coin flying animation first
            showPremiumCoinFlyingAnimation()
            
            // Refresh API after animation starts
            Handler(Looper.getMainLooper()).postDelayed({
                profileViewModel.getUsers(it)
                Log.d("HomeFragment", "Refreshing coins balance after claim")
            }, 500) // Delay API call slightly for better UX
        }
    }

    override fun onNetworkRetry() {
        binding.tvNointernet.visibility = View.GONE
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let { loadFemaleUsers(it) }
    }

    private fun showPremiumCoinFlyingAnimation() {
        context?.let { ctx ->
            // Get the root view to add the flying coin
            val rootView = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
            rootView?.let { root ->
                
                // Create a coin ImageView
                val flyingCoin = ImageView(ctx).apply {
                    setImageResource(R.drawable.coin_d)
                    layoutParams = ViewGroup.LayoutParams(80, 80) // Larger coin for visibility
                }
                
                // Add coin to root view
                root.addView(flyingCoin)
                
                // Get screen center position (where dialog was)
                val screenWidth = root.width
                val screenHeight = root.height
                val startX = (screenWidth / 2 - 40).toFloat() // Center X minus half coin width
                val startY = (screenHeight / 2 - 40).toFloat() // Center Y minus half coin height
                
                // Get target position (cl_coins location)
                val targetLocation = IntArray(2)
                binding.clCoins.getLocationOnScreen(targetLocation)
                val endX = targetLocation[0].toFloat()
                val endY = targetLocation[1].toFloat()
                
                // Set initial position
                flyingCoin.x = startX
                flyingCoin.y = startY
                flyingCoin.alpha = 0f
                flyingCoin.scaleX = 0.5f
                flyingCoin.scaleY = 0.5f
                
                // Create curved path animation (bezier curve)
                val controlX = (startX + endX) / 2
                val controlY = startY - 200 // Arc upward
                
                // Create animator set for smooth animation
                val animatorSet = AnimatorSet()
                
                // Fade in
                val fadeIn = ObjectAnimator.ofFloat(flyingCoin, "alpha", 0f, 1f).apply {
                    duration = 200
                }
                
                // Scale up
                val scaleUpX = ObjectAnimator.ofFloat(flyingCoin, "scaleX", 0.5f, 1.2f).apply {
                    duration = 300
                }
                val scaleUpY = ObjectAnimator.ofFloat(flyingCoin, "scaleY", 0.5f, 1.2f).apply {
                    duration = 300
                }
                
                // Move along curved path
                val pathX = ObjectAnimator.ofFloat(flyingCoin, "x", startX, controlX, endX).apply {
                    duration = 800
                    interpolator = AccelerateDecelerateInterpolator()
                }
                
                val pathY = ObjectAnimator.ofFloat(flyingCoin, "y", startY, controlY, endY).apply {
                    duration = 800
                    interpolator = AccelerateDecelerateInterpolator()
                }
                
                // Scale down at end
                val scaleDownX = ObjectAnimator.ofFloat(flyingCoin, "scaleX", 1.2f, 0.3f).apply {
                    duration = 300
                    startDelay = 500
                }
                val scaleDownY = ObjectAnimator.ofFloat(flyingCoin, "scaleY", 1.2f, 0.3f).apply {
                    duration = 300
                    startDelay = 500
                }
                
                // Fade out at end
                val fadeOut = ObjectAnimator.ofFloat(flyingCoin, "alpha", 1f, 0f).apply {
                    duration = 200
                    startDelay = 600
                }
                
                // Rotation for dynamic effect
                val rotation = ObjectAnimator.ofFloat(flyingCoin, "rotation", 0f, 720f).apply {
                    duration = 800
                }
                
                // Play all animations together
                animatorSet.playTogether(fadeIn, scaleUpX, scaleUpY, pathX, pathY, scaleDownX, scaleDownY, fadeOut, rotation)
                
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // Remove flying coin from view
                        root.removeView(flyingCoin)
                        
                        // Animate the cl_coins card
                        showCoinsAddedAnimation()
                    }
                })
                
                animatorSet.start()
            }
        }
    }

    private fun showCoinsAddedAnimation() {
        // Animate the entire coins card with pulse
        val pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.coins_pulse)
        binding.clCoins.startAnimation(pulseAnimation)
        
        // Animate the coin icon separately for extra effect
        val coinAnimation = AnimationUtils.loadAnimation(context, R.anim.coin_scale_bounce)
        binding.ivCoin.startAnimation(coinAnimation)
    }


    fun checkFemaleStatus(){
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        val prefs = requireContext().getSharedPreferences("my_app_prefs", Context.MODE_PRIVATE)
        val femaleuserId = prefs.getString("notification_user_id", null)
        Log.d("notification_user_id","$femaleuserId")
        if (femaleuserId != null) {
            userData?.id?.let { profileViewModel.getUsersStatus(femaleuserId) }

        }else{
            return
        }

    }

    fun observeLiveUserStatus(){ profileViewModel.getUserLiveStatus.observe(viewLifecycleOwner, Observer { response ->

        val prefs = requireContext().getSharedPreferences("my_app_prefs", Context.MODE_PRIVATE)
        val femaleUserId = prefs.getString("notification_user_id", null)
        Log.d("femaleUserId_Notification","$femaleUserId")
        if (femaleUserId != null) {

        response?.data?.let { userData ->

            prefs.edit().remove("notification_user_id").apply()


            var audioStatus = userData.audio_status
            var videoStatus = userData.video_status

            val context = requireContext()
            val intent = Intent(context, MaleCallConnectingActivity::class.java)

            when {
                audioStatus == 1 -> {
                    intent.putExtra(DConstants.CALL_TYPE, "audio")
                }
                videoStatus == 1 -> {
                    intent.putExtra(DConstants.CALL_TYPE, "video")
                }
                else -> {
                    Log.d("HomeFragment", "No call available for this user")
                    intent.putExtra(DConstants.CALL_TYPE, "audio")
                }
            }



            intent.putExtra(DConstants.RECEIVER_ID, response.data.id)
            intent.putExtra(DConstants.RECEIVER_NAME, response.data.name)
            intent.putExtra(DConstants.CALL_ID, 0)
            intent.putExtra(DConstants.IMAGE, response.data.image)
            intent.putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
            intent.putExtra(
                DConstants.TEXT,
                getString(R.string.wait_user_hint, response.data.name)
            )
            FcmUtils.isUserAvailable=1
            startActivity(intent)

        } ?: Log.e("HomeFragment", "RegisterResponse is null")
    }})}


}
