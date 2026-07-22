package com.gmwapp.hima.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BaseApplication.Companion.getInstance
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.IplRoomsActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.utils.SubscriptionStateCache
import com.gmwapp.hima.utils.UserSegment
import com.gmwapp.hima.viewmodels.AutopayViewModel
import com.gmwapp.hima.viewmodels.DailyClaimViewModel
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
    private var filterType: String = "all" // Default filter is "all" — open on creators list so users see who to call
    // When filterType == "interest", this holds the selected interest name (e.g. "Music").
    // The backend filters the (already-cached) online-female list by this interest.
    private var selectedInterest: String? = null

    // Top-5 interest pills (data-driven): interest name (sent to the API) + selected
    // fill colour. Button views are resolved fresh from `binding` via interestButton()
    // so we never cache a stale view across view re-creation.
    private val INTEREST_PILLS: List<Pair<String, Int>> = listOf(
        "Music"  to 0xFF9C1DF9.toInt(),
        "Love"   to 0xFFFF2D6B.toInt(),
        "Movies" to 0xFF2388F2.toInt(),
        "Foodie" to 0xFFF5820D.toInt(),
        "Travel" to 0xFF06B6D4.toInt(),
    )

    // Left→right on-screen order of the filter pills, used for swipe-to-adjacent-pile
    // navigation. Matches the layout order (interest pills: Music, Movies, Foodie, Love, Travel).
    private val FILTER_ORDER: List<String> = listOf(
        "my_chats", "all", "new", "star", "Music", "Movies", "Foodie", "Love", "Travel"
    )

    private fun interestButton(name: String): com.google.android.material.button.MaterialButton? = when (name) {
        "Music"  -> binding.btnFilterMusic
        "Love"   -> binding.btnFilterLove
        "Movies" -> binding.btnFilterMovies
        "Foodie" -> binding.btnFilterFoodie
        "Travel" -> binding.btnFilterTravel
        else     -> null
    }
    lateinit var binding: FragmentHomeBinding
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private val autopayViewModel: AutopayViewModel by viewModels()
    private val dailyClaimViewModel: DailyClaimViewModel by viewModels()
    private var dailyCoinsDialog: androidx.appcompat.app.AlertDialog? = null

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
        // FeatureFlags.IPL_ENABLED is an app-side kill-switch that overrides the
        // server flag — when false the banner stays GONE regardless of ipl_rooms_enabled.
        val iplEnabled = com.gmwapp.hima.utils.FeatureFlags.IPL_ENABLED &&
            (BaseApplication.getInstance()?.getPrefs()?.getUserData()?.ipl_rooms_enabled ?: 0) == 1
        binding.cardIplRooms.visibility = if (iplEnabled) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun initUI() {
        binding.clCoins.setOnSingleClickListener {
            // The old ₹1 trial bottom-sheet is retired; the welcome-gift banner is
            // the only autopay upsell now. Tapping coins always opens Wallet.
            startActivity(android.content.Intent(requireContext(), WalletActivity::class.java))
        }

        setupSubscriptionObservers()

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
                // Use call-fix branch's preserving setter (keeps locally-set intent flags)
                BaseApplication.getInstance()?.getPrefs()?.setUserDataPreservingLocalIntent(userData)
                // Bug #10 — re-apply the account-blocked banner now that fresh server
                // data has landed. onResume() evaluated it against the stale cached
                // UserData.blocked (this getUsers() refresh runs async, after that), so
                // without this the banner only appeared on the NEXT launch.
                applyDismissableBlockBanner(
                    binding.blockBannerHome.root,
                    binding.blockBannerHome.btnCloseBanner
                )
                // Autopay UX: new users with no subscription history display zero coins
                // until they take action; matches refreshCoinsDisplayFromCache.
                val displayCoins =
                    if (UserSegment.isNewUser(requireContext()) && !SubscriptionStateCache.isActive(requireContext())) 0
                    else userData.coins
                com.gmwapp.hima.utils.CoinAnimUtil.animateTo(binding.tvCoins, displayCoins ?: 0)
                Log.d("coinsvalue", "${userData.coins}")
                Log.d("coinsvalue", "${userData.name}")
                // Refresh IPL banner visibility once user data arrives from server
                refreshIplBanner()
                refreshStarTabVisibility()
            } ?: Log.e("HomeFragment", "RegisterResponse is null")
        })




        // B066 — guard against launching a second call activity while one is
        // already alive. Symptom of the missing guard: tapping the random
        // button mid-call (or with the call screen still backgrounded) appears
        // to do "nothing" because Telecom/Agora silently reject the new flow.
        binding.fabAudio.setOnSingleClickListener {
            if (BaseApplication.getInstance()?.isInActiveCall() == true) {
                Toast.makeText(context, "Already in a call", Toast.LENGTH_SHORT).show()
                return@setOnSingleClickListener
            }
            val intent = Intent(context, AgoraRandomCallActivity::class.java)
            intent.putExtra(DConstants.CALL_TYPE, "audio")
            intent.putExtra("RANDOM_FILTER", filterType)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }

        binding.fabVideo.setOnSingleClickListener {
            if (BaseApplication.getInstance()?.isInActiveCall() == true) {
                Toast.makeText(context, "Already in a call", Toast.LENGTH_SHORT).show()
                return@setOnSingleClickListener
            }
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

            // B_036 — only paint the creators list when a creators tab is active. rvProfiles
            // is shared with the Chats list (loadMyChats), so a stray or cached female-users
            // response landing while the user is on "Chats" must NOT overwrite it — otherwise
            // the Chats chip stays highlighted while the creator list shows underneath.
            if (it?.data != null && filterType != "my_chats") {
                binding.rvProfiles.layoutManager =
                    LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)

                // Row click is handled inside the adapter (opens ChatActivityInHouse).
                // The audio/video listeners are kept as no-ops for constructor compatibility.
                val noOpListener = object : OnItemSelectionListener<FemaleUsersResponseData> {
                    override fun onItemSelected(data: FemaleUsersResponseData) {}
                }
                val transactionAdapter = activity?.let { context ->
                    FemaleUserAdapter(
                        context,
                        it.data,
                        object : OnItemSelectionListener<FemaleUsersResponseData> {
                            override fun onItemSelected(data: FemaleUsersResponseData) {
                                handleCallClick(data, DConstants.AUDIO)
                            }
                        },
                        object : OnItemSelectionListener<FemaleUsersResponseData> {
                            override fun onItemSelected(data: FemaleUsersResponseData) {
                                handleCallClick(data, DConstants.VIDEO)
                            }
                        }
                    )
                }
                binding.rvProfiles.adapter = transactionAdapter
            }

            // Stop the swipe-to-refresh loading animation
            binding.swipeRefreshLayout.isRefreshing = false
            setLoading(false)
            refreshMaleHomeNetworkPlaceholder()
        })

        femaleUsersViewModel.femaleUsersErrorLiveData.observe(viewLifecycleOwner, Observer { errorMessage ->
            binding.swipeRefreshLayout.isRefreshing = false
            setLoading(false)
            refreshMaleHomeNetworkPlaceholder()
            // B102 — surface load failures so the user knows the tap "didn't
            // take" and can retry. Previously this observer silently hid the
            // spinner and the UI looked unchanged from a successful load.
            if (!errorMessage.isNullOrBlank() && isAdded) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Couldn't load creators — pull to refresh", Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun handleCallClick(data: FemaleUsersResponseData, callType: String) {
        val ctx = context ?: return
        if (UserSegment.isNewUser(ctx)) {
            if (isSubscriptionActive()) {
                startCallActivity(data, callType)
            } else {
                showTrialOfferSheet()
            }
            return
        }
        if (hasEnoughCoinsForCall(data, callType)) {
            startCallActivity(data, callType)
        } else {
            showInsufficientFundsSheet()
        }
    }

    private fun hasEnoughCoinsForCall(data: FemaleUsersResponseData, callType: String): Boolean {
        val coins = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.coins ?: 0
        val required = if (callType == DConstants.VIDEO) {
            data.coin_per_min_video ?: 60
        } else {
            data.coin_per_min_audio ?: 10
        }
        return coins >= required
    }

    // The ₹1 trial bottom-sheet is retired. A new/unsubscribed user who taps a
    // creator's call button is sent to Wallet (the welcome-gift banner there is
    // the autopay upsell). Kept as a function so handleCallClick's gate + return
    // stay intact — no call ever starts for an unsubscribed new user.
    private fun showTrialOfferSheet() {
        if (!isAdded) return
        val ctx = context ?: return
        startActivity(Intent(ctx, WalletActivity::class.java))
    }

    // Insufficient talktime on a call tap now routes straight to Wallet — the
    // welcome-offer / ₹1-trial card and the coin top-up both live there, so the
    // old BottomSheetOldUserSubscribe recharge+subscribe sheet is retired
    // (mirrors showTrialOfferSheet). No call ever starts without coins.
    private fun showInsufficientFundsSheet() {
        if (!isAdded) return
        val ctx = context ?: return
        startActivity(Intent(ctx, WalletActivity::class.java))
    }

    private fun refreshCoinsDisplayFromCache() {
        if (!isAdded || !::binding.isInitialized) return
        val cached = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        // Daily-claim coins are credited server-side via /daily_claim;
        // users.coins reflects the new total directly, no local bonus.
        val displayCoins = if (UserSegment.isNewUser(requireContext()) && !SubscriptionStateCache.isActive(requireContext())) 0
                           else (cached.coins ?: 0)
        com.gmwapp.hima.utils.CoinAnimUtil.animateTo(binding.tvCoins, displayCoins)
    }

    private fun refreshPremiumCrown() {
        if (!::binding.isInitialized) return
        // Crown is an autopay status reflection. Hide for languages without
        // autopay so non-autopay users never see it. Existing subscribers
        // (everActive) keep it until their subscription naturally lapses,
        // so a mid-flight admin flip doesn't strip their visible status.
        val ctx = requireContext()
        val autopayLanguage = com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEnabled(ctx)
        val everActive = SubscriptionStateCache.everActive(ctx)
        val show = isSubscriptionActive() && (autopayLanguage || everActive)
        binding.ivPremiumCrownHome.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Kick off the two API calls whose responses (via observers in
     * setupSubscriptionObservers) decide whether to show the daily-claim
     * dialog or the autopay-failed dialog. Called on resume + on the
     * midnight handler tick.
     */
    private fun maybeShowDailyCoinsDialog() {
        if (!isAdded) return
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        val userId = userData.id
        if (userId <= 0) return
        autopayViewModel.subscriptionStatus(userId)
        dailyClaimViewModel.dailyClaimStatus(userId)
        // Refresh the per-language admin flag alongside subscription status
        // so non-autopay-language users get their gating updated on every
        // foreground without an extra round-trip elsewhere.
        com.gmwapp.hima.utils.LanguageFeatureCache.refresh(
            requireContext(), myChatsApiManager, userId, userData.language
        )
    }

    companion object {
        private var autopayDialogShownThisSession = false
        private var welcomeGiftShownThisSession = false
        // Interest-pill discovery nudge plays at most once per app launch (resets on
        // cold start / process death), and stops permanently once the user taps any
        // interest pill (persisted in prefs).
        private var interestNudgePlayedThisProcess = false

        /**
         * Reset once-per-session dialog flags on logout so a second account on
         * the same device (same process) is re-evaluated for the welcome-gift /
         * autopay-failed dialogs instead of inheriting the previous user's
         * "already shown" state.
         */
        @JvmStatic
        fun resetSessionDialogFlags() {
            autopayDialogShownThisSession = false
            welcomeGiftShownThisSession = false
            interestNudgePlayedThisProcess = false
        }
    }

    private val midnightHandler = Handler(Looper.getMainLooper())
    private val midnightRunnable: Runnable = Runnable {
        if (isAdded) {
            maybeShowDailyCoinsDialog()
            scheduleMidnightRefresh()
        }
    }

    private fun msUntilMidnight(): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis - System.currentTimeMillis()
    }

    private fun scheduleMidnightRefresh() {
        midnightHandler.removeCallbacks(midnightRunnable)
        midnightHandler.postDelayed(midnightRunnable, msUntilMidnight())
    }

    private fun wasEverSubscribed(): Boolean =
        SubscriptionStateCache.everActive(requireContext())

    private fun showDailyCoinsDialog() {
        // Guard against stacking — observer can fire repeatedly (resume + midnight).
        if (dailyCoinsDialog?.isShowing == true) return
        val view = layoutInflater.inflate(R.layout.dialog_daily_coins, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.5f)
        val btn = view.findViewById<View>(R.id.btnAwesome)
        btn.setOnClickListener {
            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            if (userId == null) {
                dialog.dismiss()
                return@setOnClickListener
            }
            // Disable to prevent double-tap; re-enables on dialog dismiss.
            btn.isEnabled = false
            dailyClaimViewModel.dailyClaim(userId)
            // Dismissal happens in the claim observer (success or failure).
        }
        dialog.setOnDismissListener {
            btn.isEnabled = true
            refreshCoinsDisplayFromCache()
        }
        dailyCoinsDialog = dialog
        dialog.show()
    }

    private fun showAutopayFailedDialog(status: String?) {
        val view = layoutInflater.inflate(R.layout.dialog_autopay_failed, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.5f)

        val titleView = view.findViewById<android.widget.TextView>(R.id.tvAutopayFailedTitle)
        val subtitleView = view.findViewById<android.widget.TextView>(R.id.tvAutopayFailedSubtitle)
        when (status?.lowercase()) {
            "failed" -> {
                titleView.text = "Autopay Renewal Failed"
                subtitleView.text =
                    "Your bank declined the autopay renewal.\n" +
                    "Please check your balance or update your payment method."
            }
            "cancelled" -> {
                titleView.text = "Autopay Cancelled"
                subtitleView.text =
                    "You cancelled your autopay.\n" +
                    "Subscribe again to unlock chat."
            }
            else -> Unit
        }

        view.findViewById<View>(R.id.btnBuyCoins).setOnClickListener {
            dialog.dismiss()
            startActivity(android.content.Intent(requireContext(), WalletActivity::class.java))
        }
        view.findViewById<View>(R.id.tvDismiss).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun isSubscriptionActive(): Boolean =
        SubscriptionStateCache.isActive(requireContext())

    /**
     * Wires the AutopayViewModel and DailyClaimViewModel observers.
     * Called once from initUI; the actual API requests are kicked off
     * from maybeShowDailyCoinsDialog (onResume + midnight handler).
     */
    private fun setupSubscriptionObservers() {
        // Subscription status → cache + UI refresh + autopay-failed dialog.
        autopayViewModel.statusLiveData.observe(viewLifecycleOwner) { resp ->
            val data = resp?.data ?: return@observe
            SubscriptionStateCache.update(data)
            refreshPremiumCrown()
            refreshCoinsDisplayFromCache()
            // Suppress the autopay-failed dialog when the user is on a
            // non-autopay language and has never been a subscriber. Prevents
            // a stale "ever_active" flag (or test data) from popping a
            // dialog that doesn't apply.
            val ctx = requireContext()
            val autopayLanguage = com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEnabled(ctx)
            if (!data.is_active && data.ever_active &&
                !autopayDialogShownThisSession && (autopayLanguage || data.ever_active)) {
                autopayDialogShownThisSession = true
                if (isAdded) showAutopayFailedDialog(data.status)
            }

            // Welcome-gift (₹1 trial) banner — now that real subscription state
            // has landed, show it once per session to eligible never-active male
            // users on autopay languages (full gate in WelcomeGiftPromo). Flag is
            // only set when eligible, so a state change later in the session can
            // still trigger it.
            if (!welcomeGiftShownThisSession &&
                com.gmwapp.hima.utils.WelcomeGiftPromo.isEligible(ctx)) {
                welcomeGiftShownThisSession = true
                (activity as? com.gmwapp.hima.activities.MainActivity)?.let { mainAct ->
                    binding.root.post { if (isAdded) mainAct.showWelcomeGiftDialog() }
                }
            }
        }

        // Daily-claim status → show the claim dialog when the server says we can.
        // Daily-claim is a subscriber perk; suppress for non-autopay-language
        // users who never subscribed (defensive — server should not say
        // can_claim for them, but we guard anyway).
        dailyClaimViewModel.statusLiveData.observe(viewLifecycleOwner) { resp ->
            val data = resp?.data ?: return@observe
            val ctx = requireContext()
            val autopayLanguage = com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEnabled(ctx)
            val everActive = SubscriptionStateCache.everActive(ctx)
            if (data.can_claim && isAdded && (autopayLanguage || everActive)) {
                binding.root.post { if (isAdded) showDailyCoinsDialog() }
            }
        }

        // Re-gate UI when admin flips the language flag while the app is open.
        com.gmwapp.hima.utils.LanguageFeatureCache.updates.observe(viewLifecycleOwner) {
            refreshPremiumCrown()
            refreshCoinsDisplayFromCache()
        }

        // Daily-claim result → dismiss + refresh.
        dailyClaimViewModel.claimLiveData.observe(viewLifecycleOwner) { resp ->
            val total = resp?.data?.total_coins
            if (total != null && ::binding.isInitialized) {
                com.gmwapp.hima.utils.CoinAnimUtil.animateTo(binding.tvCoins, total)
                // Keep the cached UserData fresh so other screens see the new total.
                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                    profileViewModel.getUsers(it)
                }
            }
            dailyCoinsDialog?.dismiss()
            dailyCoinsDialog = null
        }

        // Errors are silent — the UI just doesn't show the dialog this resume.
        autopayViewModel.errorLiveData.observe(viewLifecycleOwner) { msg ->
            Log.w("Autopay", "subscription_status: $msg")
        }
        dailyClaimViewModel.errorLiveData.observe(viewLifecycleOwner) { msg ->
            Log.w("DailyClaim", msg)
            // Re-enable the dialog button if it's still showing.
            dailyCoinsDialog?.findViewById<View>(R.id.btnAwesome)?.isEnabled = true
        }
    }

    private fun startCallActivity(data: FemaleUsersResponseData, callType: String) {
        val intent = Intent(requireContext(), MaleCallConnectingActivity::class.java).apply {
            putExtra(DConstants.CALL_TYPE, callType)
            putExtra(DConstants.RECEIVER_ID, data.id)
            putExtra(DConstants.RECEIVER_NAME, data.name)
            putExtra(DConstants.CALL_ID, 0)
            putExtra(DConstants.IMAGE, data.image)
            putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
            putExtra(DConstants.TEXT, "Connecting to ${data.name}…")
        }
        startActivity(intent)
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
        // Interest pills reuse the shared (non-"new") base list, filtered by interest.
        if (filterType == "interest" && selectedInterest != null) {
            Log.d("filterpassing", "female_users_list -> user_id=$userId, filter=null, interest=$selectedInterest (filterType=$filterType)")
            femaleUsersViewModel.getFemaleUsers(userId, null, selectedInterest)
            return
        }
        val filter = when (filterType) {
            "new"  -> "new"
            "star" -> "star"
            else   -> null
        }
        Log.d("filterpassing", "female_users_list -> user_id=$userId, filter=$filter, interest=null (filterType=$filterType)")
        femaleUsersViewModel.getFemaleUsers(userId, filter)
    }

    /**
     * Called when the user re-taps the Home tab in bottom nav.
     * Re-fetches the female users list with the current filter.
     */
    override fun refresh() {
        // Guard: MainActivity programmatically selects R.id.home in onCreate, which fires
        // onNavigationItemSelected -> refresh() on the restored fragment. On a configuration
        // change (e.g. enter/exit split-screen) the fragment is restored before its
        // onCreateView has run, so `binding` hasn't been set yet. Bail out and let the
        // user re-trigger refresh after the view is bound.
        if (view == null || !::binding.isInitialized) return
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        loadFemaleUsers(userId)
    }

    private fun setLoading(isLoading: Boolean) {
        if (!::binding.isInitialized) return
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
        // Interest pills — each filters the list to online females who picked that interest.
        INTEREST_PILLS.forEach { (interest, _) ->
            interestButton(interest)?.setOnClickListener { applyInterestFilter(interest) }
        }

        // One-time discovery nudge so users notice the new interest pills off-screen.
        maybeShowInterestPillNudge()

        // Swipe left/right anywhere on the list to move to the next/previous filter pile.
        setupSwipeBetweenFilters()
    }

    // ---- Swipe-between-piles (FILTER_ORDER) ----------------------------------
    // Observe-only horizontal-fling detector on the list. It NEVER consumes touch
    // events, so vertical scroll, item taps, and pull-to-refresh are untouched.
    // A decisive horizontal fling selects the adjacent visible pile via the exact
    // same applyFilter()/applyInterestFilter() path as a tap.
    private fun setupSwipeBetweenFilters() {
        if (!::binding.isInitialized) return
        // Called once per view creation (single call site in setupFilterButtons), so the
        // listener is attached to the current rvProfiles instance — survives view recreation.
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
                        goToAdjacentFilter(if (dx < 0) 1 else -1)
                        return true
                    }
                    return false
                }
            })

        binding.rvProfiles.addOnItemTouchListener(object :
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
    }

    // Visible pile keys in on-screen order (Star is hidden unless startab == 1).
    private fun visibleFilterKeys(): List<String> {
        val starOn = (BaseApplication.getInstance()?.getPrefs()?.getUserData()?.startab ?: 0) == 1
        return FILTER_ORDER.filter { it != "star" || starOn }
    }

    private fun currentFilterKey(): String =
        if (filterType == "interest") (selectedInterest ?: "all") else filterType

    private fun goToAdjacentFilter(dir: Int) {
        if (!::binding.isInitialized) return
        val keys = visibleFilterKeys()
        val cur = keys.indexOf(currentFilterKey())
        if (cur < 0) return
        val next = cur + dir
        if (next !in keys.indices) return // stop at the ends (no wrap-around)
        val key = keys[next]
        animateListSwap(dir)
        if (INTEREST_PILLS.any { it.first == key }) applyInterestFilter(key) else applyFilter(key)
        scrollPillIntoView(key)
    }

    // Keep the newly-active pill visible/centered in the horizontal pill bar.
    private fun scrollPillIntoView(key: String) {
        if (!::binding.isInitialized) return
        val btn = when (key) {
            "my_chats" -> binding.btnFilterMyChats
            "all"      -> binding.btnFilterAll
            "new"      -> binding.btnFilterNew
            "star"     -> binding.btnFilterStar
            else       -> interestButton(key)
        } ?: return
        val scroll = binding.filterPillsScroll
        scroll.post {
            val target = (btn.left - (scroll.width - btn.width) / 2).coerceAtLeast(0)
            scroll.smoothScrollTo(target, 0)
        }
    }

    // Subtle slide so the swipe reads as a page change (content reloads via the API).
    private fun animateListSwap(dir: Int) {
        if (!::binding.isInitialized) return
        val rv = binding.rvProfiles
        if (rv.width == 0) return
        rv.translationX = (rv.width * 0.06f) * (if (dir > 0) 1f else -1f)
        rv.animate().translationX(0f).setDuration(220).start()
    }

    /**
     * First launch after the interest-pills update: gently auto-scroll the filter
     * row to reveal the colourful interest pills, pause, then spring back to All —
     * so users discover there's more to scroll. Runs once (prefs flag), and only
     * when the pills are actually off-screen (skips on wide screens).
     */
    private fun maybeShowInterestPillNudge() {
        if (!::binding.isInitialized) return
        // At most once per app launch (not on every return to Home in a session).
        if (interestNudgePlayedThisProcess) return
        val ctx = context ?: return
        val sp = ctx.getSharedPreferences("home_ui_prefs", android.content.Context.MODE_PRIVATE)
        // Stop nudging permanently once the user has tapped any interest pill.
        if (sp.getBoolean("interest_pill_used", false)) return

        val scroll = binding.filterPillsScroll
        scroll.postDelayed({
            if (!isAdded || !::binding.isInitialized) return@postDelayed
            val music = binding.btnFilterMusic
            val viewportRight = scroll.scrollX + scroll.width
            // Already fully visible (wide screen) → no nudge needed.
            if (music.right <= viewportRight) return@postDelayed
            interestNudgePlayedThisProcess = true

            val density = resources.displayMetrics.density
            val target = (music.left - 64 * density).toInt().coerceAtLeast(0)
            scroll.smoothScrollTo(target, 0)
            scroll.postDelayed({
                if (!isAdded || !::binding.isInitialized) return@postDelayed
                scroll.smoothScrollTo(0, 0)
            }, 1200L)
        }, 700L)
    }

    private fun refreshStarTabVisibility() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val showStar = (userData?.startab ?: 0) == 1
        binding.btnFilterStar.visibility = if (showStar) View.VISIBLE else View.GONE
    }

    private fun updateFilterButtonStyles() {
        val ctx = requireContext()
        val greyColor = resources.getColor(R.color.grey_medium, null)
        val whiteText = resources.getColor(R.color.white, null)

        // All filter pills share ONE look: white pill when unselected, purple→pink
        // gradient when selected — uniform across Chats / All / New / Star / interests.
        val allPills = listOf(
            binding.btnFilterMyChats, binding.btnFilterAll, binding.btnFilterNew, binding.btnFilterStar
        ) + INTEREST_PILLS.mapNotNull { interestButton(it.first) }

        allPills.forEach {
            it.backgroundTintList = null
            it.strokeWidth = 0
            it.background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_chip_white)
            it.setTextColor(greyColor)
            it.iconTint = android.content.res.ColorStateList.valueOf(greyColor)
        }

        val selected = when (filterType) {
            "my_chats" -> binding.btnFilterMyChats
            "all"      -> binding.btnFilterAll
            "new"      -> binding.btnFilterNew
            "star"     -> binding.btnFilterStar
            "interest" -> selectedInterest?.let { interestButton(it) }
            else       -> null
        }
        selected?.apply {
            backgroundTintList = null
            strokeWidth = 0
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_chip_gradient)
            setTextColor(whiteText)
            iconTint = android.content.res.ColorStateList.valueOf(whiteText)
        }
    }

    private fun applyFilter(selectedFilter: String) {
        Log.d("FilterButtons", "applyFilter called - current: $filterType, selected: $selectedFilter")
        if (filterType == selectedFilter) {
            Log.d("FilterButtons", "Filter already selected, returning")
            return
        }

        // B102 — check preconditions BEFORE flipping pill state. Previously
        // we updated filterType + visual highlight first, then silently
        // skipped the load if userData was null or there was no internet,
        // leaving the pill looking selected but the list unchanged (the
        // "toggle not working" symptom). Now: if we can't load, we tell
        // the user and leave the previous pill state intact so a retry tap
        // on the same pill still works.
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id
        val ctx = context
        if (userId == null) {
            Log.w("FilterButtons", "applyFilter: userData not ready — ignoring tap")
            ctx?.let { Toast.makeText(it, "Please wait a moment and try again", Toast.LENGTH_SHORT).show() }
            return
        }
        if (ctx == null || !isInternetAvailable(ctx)) {
            Log.w("FilterButtons", "applyFilter: offline — ignoring tap")
            ctx?.let { Toast.makeText(it, "No internet — check connection and retry", Toast.LENGTH_SHORT).show() }
            return
        }

        // Preconditions OK — commit the state change and fire the load.
        filterType = selectedFilter
        // Switching to a non-interest pill clears any active interest selection.
        selectedInterest = null
        updateFilterButtonStyles()
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

    /**
     * Interest-pill tap. Mirrors [applyFilter]'s precondition guards, then sets
     * filterType="interest" + the chosen interest and reloads. The backend filters
     * the (already-cached) online-female list by this interest — no extra DB query.
     */
    private fun applyInterestFilter(interest: String) {
        if (filterType == "interest" && selectedInterest == interest) return

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id
        val ctx = context
        if (userId == null) {
            ctx?.let { Toast.makeText(it, "Please wait a moment and try again", Toast.LENGTH_SHORT).show() }
            return
        }
        if (ctx == null || !isInternetAvailable(ctx)) {
            ctx?.let { Toast.makeText(it, "No internet — check connection and retry", Toast.LENGTH_SHORT).show() }
            return
        }

        filterType = "interest"
        selectedInterest = interest
        // User engaged an interest pill → stop the discovery nudge permanently.
        ctx.getSharedPreferences("home_ui_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("interest_pill_used", true).apply()
        updateFilterButtonStyles()
        // Clear existing data, then reload with the interest filter.
        femaleUsersViewModel.femaleUsersResponseLiveData.value?.data?.clear()
        (binding.rvProfiles.adapter as? FemaleUserAdapter)?.notifyDataSetChanged()
        loadFemaleUsers(userId)
    }

    private fun sortMyChatsPinnedFirst(
        raw: List<com.gmwapp.hima.models.ChatConversation>,
        ctx: Context
    ): List<com.gmwapp.hima.models.ChatConversation> {
        // WhatsApp-style "Delete chat": hide conversations the user deleted, unless a newer
        // message has arrived since (its timestamp beats the deleted-upto watermark).
        val myId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        val visible = raw.filterNot { conv ->
            val peerId = conv.userId.toIntOrNull() ?: return@filterNot false
            val lastMillis = (conv.lastMessageTime?.seconds ?: 0L) * 1000L
            com.gmwapp.hima.utils.DeletedChatsPrefsHelper.isHidden(ctx, myId, peerId, lastMillis)
        }
        val refreshed = visible.map {
            it.copy(isPinned = PinnedChatsPrefsHelper.isPinned(ctx, it.userId))
        }
        val base = refreshed.sortedByDescending { it.lastMessageTime?.seconds ?: 0 }
        val (pinned, unpinned) = base.partition { it.isPinned }
        val order = PinnedChatsPrefsHelper.getPinnedIds(ctx)
        val sortedPinned = pinned.sortedBy { conv ->
            val i = order.indexOf(conv.userId)
            if (i >= 0) i else Int.MAX_VALUE
        }
        // QA bug #8 — blocked conversations (either direction) sink to the bottom.
        // Final stable partition over the pinned+time-ordered list: normal rows keep
        // their exact order, blocked rows move to the end in their relative order.
        val (blockedRows, normalRows) = (sortedPinned + unpinned)
            .partition { it.isBlocked || it.peerBlockedMe }
        return normalRows + blockedRows
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
                // B080 — `audioStatus` / `videoStatus` are creator-only fields
                // (set by FemaleHomeFragment toggles). Male users never set
                // them so the server returns 0, which the chat-list adapter
                // renders as "Unavailable". When the viewer is female, the
                // other party is male — there are no call-availability toggles
                // to honor, so force-enable both call types.
                val currentUserIsFemale = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
                    ?.equals(DConstants.FEMALE, ignoreCase = true) == true
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
                        // Clear chat: if this peer's newest message is at/before the cleared
                        // watermark, blank the list preview + unread (keep the row). A newer
                        // message beyond the watermark restores the preview — WhatsApp-style.
                        val clearedUpto = com.gmwapp.hima.utils.ClearedChatsPrefsHelper.getClearedUpto(activityCtx, userId, item.user.id)
                        val isCleared = clearedUpto > 0L && (incomingSec * 1000L) <= clearedUpto
                        // B_028 — blank the preview when the thread's last message was
                        // "deleted for me" (device-local hide the server doesn't know
                        // about). Ids match the chat screen's ChatMessage.id space; a
                        // newer message restores the preview automatically.
                        val lastMsgId = item.lastMessage?.id?.toString().orEmpty()
                        val isLastLocallyDeleted = lastMsgId.isNotEmpty() &&
                            com.gmwapp.hima.utils.LocallyDeletedMessagesStore.isLocallyDeleted(activityCtx, userId, item.user.id, lastMsgId)
                        val hideLastPreview = isCleared || isLastLocallyDeleted
                        // B080 + B097 — resolve once so isOnline can also check
                        // availability. For female viewers, the other party is
                        // male and there are no audio/video toggles to honor —
                        // both forced to 1. For male viewers, the server values
                        // (creator's toggles) flow through unchanged.
                        // FEMALE_3_REJECT_BLOCK — but if she auto-blocked this male
                        // (3 rejects/5min), force BOTH to 0 so his call buttons grey
                        // out for the 60-min cooldown (overrides the force-enable).
                        val rejectBlocked = item.user.callBlocked == true
                        val audioAvail = if (rejectBlocked) 0 else if (currentUserIsFemale) 1 else (item.user.audioStatus ?: 1)
                        val videoAvail = if (rejectBlocked) 0 else if (currentUserIsFemale) 1 else (item.user.videoStatus ?: 1)
                        com.gmwapp.hima.models.ChatConversation(
                            threadId = item.chatId,
                            userId = item.user.id.toString(),
                            userName = item.user.name,
                            userImage = item.user.image ?: "",
                            lastMessage = if (hideLastPreview) "" else item.lastMessage?.message ?: "",
                            lastMessageType = if (hideLastPreview) "text" else item.lastMessage?.messageType ?: "text",
                            lastMessageTime = ts,
                            unreadCount = if (isCleared) 0 else effectiveUnread,
                            // B097 — green dot now requires BOTH recent activity
                            // (status==1) AND at least one call mode available.
                            // Previously a creator with both toggles off but
                            // status==1 still got the dot, making her look
                            // reachable when tapping anything said "Unavailable".
                            // Mirrors FemaleUserAdapter's stricter "online means
                            // callable" semantics.
                            isOnline = (item.user.status ?: 0) == 1 &&
                                (audioAvail == 1 || videoAvail == 1),
                            audioStatus = audioAvail,
                            videoStatus = videoAvail,
                            coinPerMinAudio = item.user.coinPerMinAudio ?: 10,
                            coinPerMinVideo = item.user.coinPerMinVideo ?: 60,
                            language = item.user.language,
                            isPinned = PinnedChatsPrefsHelper.isPinned(activityCtx, item.user.id.toString()),
                            isBlocked = item.iHaveBlockedThisUser == true,
                            // Show a "you can't message" marker when the creator blocked me.
                            peerBlockedMe = item.peerBlockedMe == true
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
        
        // B066 — short debounce (150ms) so users can rapidly tap-to-expand and
        // tap-to-collapse without the second tap getting swallowed by the
        // default 500ms guard. The Random FAB is a pure UI toggle, not an
        // activity launcher, so 500ms is overkill here.
        binding.fabRandom.setOnSingleClickListener(debounceMs = 150L) {
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
                collapseFabMenu()
            }
        }

        // Tapping the dimmed backdrop closes the Random menu. The dim View is
        // clickable/focusable in the layout so it also swallows taps meant for
        // the cards behind it — while the menu is open those cards must not be
        // interactable; only the audio/video FABs (drawn above the dim) respond.
        binding.dimBackground.setOnClickListener {
            if (isAllFabVisible) collapseFabMenu()
        }
    }

    private fun collapseFabMenu() {
        binding.fabAudio.hide()
        binding.fabVideo.hide()
        binding.tvAudio1.visibility = View.GONE
        binding.tvAudio2.visibility = View.GONE
        binding.tvVideo1.visibility = View.GONE
        binding.tvVideo2.visibility = View.GONE
        binding.ivCoinAudio.visibility = View.GONE
        binding.ivCoinVideo.visibility = View.GONE

        hideDimBackground()

        // Reset the bg color to the resting brand pink when collapsed. Must match
        // the XML default (app:backgroundTint="@color/colorAccent" in fragment_home.xml)
        // so the Random FAB has a single consistent resting color — otherwise the
        // runtime blue never persists and reverts to pink on view recreation (B_051).
        binding.fabRandom.backgroundTintList = resources.getColorStateList(R.color.colorAccent)

        // Reset the icon tint to white
        binding.fabRandom.setIconTintResource(R.color.white)

        // Reset text color to white
        binding.fabRandom.setTextColor(resources.getColor(R.color.white, null))

        // Change the icon to random when collapsed
        binding.fabRandom.setIconResource(R.drawable.random)
        binding.fabRandom.extend()

        isAllFabVisible = false
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
        // Bug #10 — app-open account-blocked banner (dismissable, reappears each launch)
        applyDismissableBlockBanner(
            binding.blockBannerHome.root,
            binding.blockBannerHome.btnCloseBanner
        )
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

        refreshCoinsDisplayFromCache()
        refreshPremiumCrown()
        maybeShowDailyCoinsDialog()
        scheduleMidnightRefresh()

        // Sync selected filter button styles when resuming
        updateFilterButtonStyles()

        checkFemaleStatus()

        // Realtime chat-list update for the my_chats filter.
        if (filterType == "my_chats") {
            registerHomeChatListRefreshReceiver()
            startHomeCollectingSocketNewMessage()
        }

        // B065 — refresh creator availability when network returns mid-screen.
        registerNetworkRestoreListener()
    }

    override fun onPause() {
        super.onPause()
        // Call-fix branch cleanup
        unregisterHomeChatListRefreshReceiver()
        unregisterNetworkRestoreListener()
        // Autopay branch cleanup — stop the midnight-refresh handler
        midnightHandler.removeCallbacks(midnightRunnable)
    }

    // B065 — without this, killing the network while the user is sitting on
    // the Home screen left the creator list stale until the user manually
    // pulled to refresh or navigated away and back. Register a default
    // network callback for the lifetime of the fragment being resumed; on
    // a real offline → online transition (NOT on initial registration when
    // we're already online), kick the existing loadFemaleUsers() path so
    // creator availability statuses come back fresh.
    private var networkRestoreCallback: ConnectivityManager.NetworkCallback? = null
    private var wasOnline = true

    private fun registerNetworkRestoreListener() {
        if (networkRestoreCallback != null) return
        val ctx = context ?: return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        // Seed wasOnline from the current state so the first onAvailable
        // (which fires immediately if we're already online) doesn't cause
        // a spurious refresh on top of the onResume refresh.
        wasOnline = isInternetAvailable(ctx)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!wasOnline) {
                    wasOnline = true
                    view?.post {
                        if (!isAdded) return@post
                        val uid = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
                            ?: return@post
                        Log.d("HomeFragment", "Network restored — refreshing creator list")
                        if (filterType == "my_chats") loadMyChats(uid) else loadFemaleUsers(uid)
                    }
                }
            }
            override fun onLost(network: Network) {
                wasOnline = false
            }
        }
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, cb)
            networkRestoreCallback = cb
        } catch (e: Exception) {
            Log.w("HomeFragment", "registerNetworkCallback failed: ${e.message}")
        }
    }

    private fun unregisterNetworkRestoreListener() {
        val cb = networkRestoreCallback ?: return
        try {
            val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        } catch (e: Exception) {
            Log.w("HomeFragment", "unregisterNetworkCallback failed: ${e.message}")
        } finally {
            networkRestoreCallback = null
        }
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

    // [messageId] is non-null only for Socket.IO deliveries; the OneSignal push
    // broadcast passes null so it never bumps the unread count (the socket path owns
    // counting). See ChatListAdapter.applyIncomingMessage for the rationale.
    private fun applyHomeIncoming(peerId: Int, text: String, type: String, messageId: Long? = null) {
        val adapter = homeMyChatsAdapter ?: return
        val ts = com.google.firebase.Timestamp.now()
        val suppressUnread = com.gmwapp.hima.utils.ActiveChatTracker.isActiveFor(context, peerId)
        val handled = adapter.applyIncomingMessage(
            peerUserId = peerId.toString(),
            lastMessageText = text,
            lastMessageType = type,
            lastMessageTime = ts,
            suppressUnreadIncrement = suppressUnread,
            incomingMessageId = messageId
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
                    applyHomeIncoming(peerId, previewText, previewType, msg.id)
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

            // Bug #9: the call type the online notification was sent for. Read it,
            // then clear both keys so a later normal launch can't reuse a stale type.
            val notifCallType = prefs.getString("notification_call_type", null)?.lowercase()
            prefs.edit()
                .remove("notification_user_id")
                .remove("notification_call_type")
                .apply()


            var audioStatus = userData.audio_status
            var videoStatus = userData.video_status

            val context = requireContext()
            val intent = Intent(context, MaleCallConnectingActivity::class.java)

            val chosenCallType = when {
                // Honor the type the notification advertised, when that type is
                // available — so a "ready for video calls" tap starts video even when
                // both toggles are on (previously audio-first always won).
                notifCallType == "video" && videoStatus == 1 -> "video"
                notifCallType == "audio" && audioStatus == 1 -> "audio"
                // Fallback precedence when no/unavailable notification type.
                audioStatus == 1 -> "audio"
                videoStatus == 1 -> "video"
                else -> {
                    Log.d("HomeFragment", "No call available for this user")
                    "audio"
                }
            }
            intent.putExtra(DConstants.CALL_TYPE, chosenCallType)



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
