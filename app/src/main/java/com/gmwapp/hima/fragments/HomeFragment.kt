package com.gmwapp.hima.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.onesignal.OneSignal
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@AndroidEntryPoint
class HomeFragment : BaseFragment(), NetworkRetryable, Refreshable {
    private var isAllFabVisible: Boolean = false
    private var filterType: String = "all" // Default filter is "all"
    lateinit var binding: FragmentHomeBinding
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()

    @javax.inject.Inject
    lateinit var myChatsApiManager: com.gmwapp.hima.retrofit.ApiManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(layoutInflater)

        setupStatusBarInsets()
        initUI()
        observeLiveUserStatus()
        setupSwipeToRefresh()
        return binding.root
    }

    private fun setupStatusBarInsets() {
        binding.appBarLayout.setOnApplyWindowInsetsListener { view, insets ->
            val statusBarHeight = insets.systemWindowInsetTop
            // Add status bar height to the existing bottom padding (8dp content + status bar)
            val contentPaddingTop = (8 * resources.displayMetrics.density).toInt()
            view.setPadding(
                view.paddingLeft,
                statusBarHeight + contentPaddingTop,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        // Request insets to trigger the listener
        binding.appBarLayout.requestApplyInsets()
    }

    private fun refreshIplBanner() {
        if (!::binding.isInitialized) return
        val iplEnabled = (BaseApplication.getInstance()?.getPrefs()?.getUserData()?.ipl_rooms_enabled ?: 0) == 1
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


        CoroutineScope(Dispatchers.Main).launch {
            delay(2000) // wait to ensure OneSignal is initialized fully

            // 1. FULL RESET before login
            OneSignal.logout()
            OneSignal.User.pushSubscription.optOut()

            // 2. Fetch user ID
            val userId = getInstance()?.getPrefs()?.getUserData()?.id.toString()

            if (!userId.isNullOrEmpty() && userId != "null") {
                Log.d("OneSignalFix", "Attempting clean login with userId: $userId")

                // 3. Force fresh login
                OneSignal.login(userId)

                // 4. Re-subscribe and assign external ID
                OneSignal.User.pushSubscription.optIn()

                // 5. Prompt notification permission (Android 13+) — once per day only
                val notifPrefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                val lastAsked = notifPrefs.getLong("notif_permission_last_asked", 0L)
                if (System.currentTimeMillis() - lastAsked >= 24 * 60 * 60 * 1000L) {
                    notifPrefs.edit().putLong("notif_permission_last_asked", System.currentTimeMillis()).apply()
                    OneSignal.Notifications.requestPermission(true)
                }

                OneSignal.User.addTag("gender", "male")
                language?.let {
                    OneSignal.User.addTag("language", it)
                    Log.d("OneSignalTag", "Language tag added: $it")
                }

                language?.let {
                    OneSignal.User.addTag("gender_language", "male_$it")
                    Log.d("OneSignalTag", "male_$it")

                }

                // 6. Debug logs to confirm status
                delay(1000)
                Log.d("OneSignalFix", "externalId: ${OneSignal.User.externalId}")
                Log.d("OneSignalFix", "pushToken: ${OneSignal.User.pushSubscription.token}")
                Log.d("OneSignalFix", "optedIn: ${OneSignal.User.pushSubscription.optedIn}")
            } else {
                Log.e("OneSignalFix", "Invalid user ID: $userId")
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
        if (showMyChats) {
            filterType = "my_chats"
            activity?.intent?.removeExtra("SHOW_MY_CHATS")
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let { uid ->
                loadMyChats(uid)
                updateFilterButtonStyles()
            }
        }
        
        userData?.id?.let {
            if (context?.let { it1 -> isInternetAvailable(it1) } == true) {
                loadFemaleUsers(it)
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
                    // Clear the existing data
                    femaleUsersViewModel.femaleUsersResponseLiveData.value?.data?.clear()

                    // Notify the adapter that data has been cleared
                    (binding.rvProfiles.adapter as? FemaleUserAdapter)?.notifyDataSetChanged()

                    // Reload the data with current filter
                    loadFemaleUsers(it)
                    Log.d("refreshing", "refreshing")
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
     */
    override fun refresh() {
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

        binding.btnFilterMyChats.setOnClickListener { applyFilter("my_chats") }
        binding.btnFilterAll.setOnClickListener { applyFilter("all") }
        binding.btnFilterNew.setOnClickListener { applyFilter("new") }
        binding.btnFilterStar.setOnClickListener { applyFilter("star") }
    }

    private fun updateFilterButtonStyles() {
        val strokeWidthPx = (1 * resources.displayMetrics.density).toInt()
        val pinkColor   = resources.getColorStateList(R.color.colorAccent, null)
        val goldColor   = android.content.res.ColorStateList.valueOf(0xFFFFC107.toInt())
        val purpleColor = android.content.res.ColorStateList.valueOf(0xFF9C27B0.toInt())
        val whiteColor  = resources.getColorStateList(R.color.white, null)
        val greyColor   = resources.getColor(R.color.grey_medium, null)
        val whiteText   = resources.getColor(R.color.white, null)
        val borderColor = resources.getColorStateList(R.color.light_grey, null)

        // Reset all to unselected state first
        listOf(binding.btnFilterMyChats, binding.btnFilterAll, binding.btnFilterNew, binding.btnFilterStar).forEach {
            it.backgroundTintList = whiteColor
            it.setTextColor(greyColor)
            it.strokeWidth = strokeWidthPx
            it.strokeColor = borderColor
        }

        // Highlight selected
        when (filterType) {
            "my_chats" -> binding.btnFilterMyChats.apply {
                backgroundTintList = purpleColor
                setTextColor(whiteText)
                strokeWidth = 0
            }
            "all" -> binding.btnFilterAll.apply {
                backgroundTintList = pinkColor
                setTextColor(whiteText)
                strokeWidth = 0
            }
            "new" -> binding.btnFilterNew.apply {
                backgroundTintList = pinkColor
                setTextColor(whiteText)
                strokeWidth = 0
            }
            "star" -> binding.btnFilterStar.apply {
                backgroundTintList = goldColor
                setTextColor(whiteText)
                strokeWidth = 0
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
                val conversations = chats.mapNotNull { item ->
                    try {
                        val ts = try {
                            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            val date = item.lastMessage?.timestamp?.let { fmt.parse(it) }
                            date?.let { com.google.firebase.Timestamp(it) }
                        } catch (_: Exception) { null }
                        com.gmwapp.hima.models.ChatConversation(
                            threadId = item.chatId,
                            userId = item.user.id.toString(),
                            userName = item.user.name,
                            userImage = item.user.image ?: "",
                            lastMessage = item.lastMessage?.message ?: "",
                            lastMessageTime = ts,
                            unreadCount = item.unreadCount,
                            isOnline = false
                        )
                    } catch (_: Exception) { null }
                }.sortedByDescending { it.lastMessageTime?.seconds ?: 0 }

                val activityCtx = activity ?: return
                val adapter = com.gmwapp.hima.adapters.ChatListAdapter(activityCtx, ArrayList(conversations)) { conv ->
                    val intent = Intent(activityCtx, com.gmwapp.hima.activities.ChatActivityInHouse::class.java).apply {
                        putExtra("USER_ID", conv.userId.toIntOrNull() ?: -1)
                        putExtra("USER_NAME", conv.userName)
                        putExtra("USER_IMAGE", conv.userImage)
                    }
                    startActivity(intent)
                }
                binding.rvProfiles.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
                binding.rvProfiles.adapter = adapter
                binding.rvProfiles.visibility = View.VISIBLE
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
        binding.fabRandom.extend()
        binding.fabAudio.hide()
        binding.fabVideo.hide()
        
        // Animations removed as per user request
        
        binding.fabRandom.setOnSingleClickListener {
            if (!isAllFabVisible) {
                showDimBackground()
                binding.fabAudio.show()
                binding.fabVideo.show()
                // Don't show text labels or coin icons
                // binding.tvAudio1.visibility = View.VISIBLE
                // binding.tvAudio2.visibility = View.VISIBLE
                // binding.tvVideo1.visibility = View.VISIBLE
                // binding.tvVideo2.visibility = View.VISIBLE
                // binding.ivCoinAudio.visibility = View.VISIBLE
                // binding.ivCoinVideo.visibility = View.VISIBLE

                // Change the bg color to white when expanded
                binding.fabRandom.backgroundTintList = resources.getColorStateList(R.color.white)

                // Change the icon tint to black
                binding.fabRandom.setIconTintResource(R.color.black)
                
                // Change text color to black
                binding.fabRandom.setTextColor(resources.getColor(R.color.black, null))

                // Change the icon to close when expanded
                binding.fabRandom.setIconResource(R.drawable.ic_close)

                binding.fabRandom.shrink()
                isAllFabVisible = true
            } else {
                binding.fabAudio.hide()
                binding.fabVideo.hide()
                binding.tvAudio1.visibility = View.GONE
                binding.tvAudio2.visibility = View.GONE
                binding.tvVideo1.visibility = View.GONE
                binding.tvVideo2.visibility = View.GONE
                binding.ivCoinAudio.visibility = View.GONE
                binding.ivCoinVideo.visibility = View.GONE

                hideDimBackground()

                // Reset the bg color to blue when collapsed
                binding.fabRandom.backgroundTintList = resources.getColorStateList(R.color.blue)

                // Reset the icon tint to white
                binding.fabRandom.setIconTintResource(R.color.white)
                
                // Reset text color to white
                binding.fabRandom.setTextColor(resources.getColor(R.color.white, null))

                // Change the icon to random when collapsed
                binding.fabRandom.setIconResource(R.drawable.random)
                binding.fabRandom.extend()
                
                isAllFabVisible = false
            }
        }
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
            userData?.let { loadFemaleUsers(it.id) }
        }
        
        // Sync selected filter button styles when resuming
        updateFilterButtonStyles()

        checkFemaleStatus()

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
