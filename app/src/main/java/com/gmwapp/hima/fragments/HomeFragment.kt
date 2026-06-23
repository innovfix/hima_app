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
import com.gmwapp.hima.dialogs.BottomSheetTrialOffer
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
            // Subscribed (any state) → open wallet directly; Wallet hides the banner on its own.
            // Never had autopay (and not subscribed) → trial sheet, but only if
            // the user's language has autopay enabled per admin config. Otherwise
            // the trial surface stays hidden and the coin tap just opens Wallet.
            //
            // Cold-start race: SubscriptionStateCache.everActive defaults to
            // false until APIs land, so !everActive is true on cold start →
            // trial sheet shows optimistically. LanguageFeatureCache.isAutopayEligible()
            // handles the language-side cold start internally (cache OR static
            // whitelist fallback), so the same single helper is used by
            // HomeFragment and AutopayCheckoutActivity — gates can't disagree.
            val ctx = requireContext()
            val isSubscribed = SubscriptionStateCache.isActive(ctx)
            val autopayEligible = com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEligible(ctx)
            val cachesPopulated = com.gmwapp.hima.utils.LanguageFeatureCache.isPopulated(ctx) &&
                SubscriptionStateCache.isPopulated()
            if (!isSubscribed && autopayEligible &&
                (!SubscriptionStateCache.everActive(ctx) || !cachesPopulated)
            ) {
                val sheet = BottomSheetTrialOffer()
                sheet.setOnTryNowClickListener {
                    startActivity(com.gmwapp.hima.activities.AutopayCheckoutActivity.intentFor(
                        requireContext(),
                        com.gmwapp.hima.activities.AutopayCheckoutActivity.PLAN_TRIAL_NEW
                    ))
                }
                sheet.show(parentFragmentManager, "trial_offer")
            } else {
                startActivity(android.content.Intent(requireContext(), WalletActivity::class.java))
            }
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
                // Autopay UX: new users with no subscription history display zero coins
                // until they take action; matches refreshCoinsDisplayFromCache.
                val displayCoins =
                    if (UserSegment.isNewUser(requireContext()) && !SubscriptionStateCache.isActive(requireContext())) 0
                    else userData.coins
                binding.tvCoins.text = displayCoins.toString()
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

            if (it?.data != null) {
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

    private fun showTrialOfferSheet() {
        if (!isAdded) return
        val ctx = context ?: return
        // Trial offer is autopay-only. For non-autopay languages, send the
        // user to Wallet to buy coin packs instead — that's the legacy
        // pre-autopay coin-only flow this language is supposed to use.
        if (!com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEnabled(ctx)) {
            startActivity(Intent(ctx, WalletActivity::class.java))
            return
        }
        val existing = childFragmentManager.findFragmentByTag(BottomSheetTrialOffer.TAG)
        if (existing is BottomSheetTrialOffer && existing.isAdded) return
        BottomSheetTrialOffer.newInstance().apply {
            setOnTryNowClickListener {
                val c = context ?: return@setOnTryNowClickListener
                startActivity(com.gmwapp.hima.activities.AutopayCheckoutActivity.intentFor(
                    c, com.gmwapp.hima.activities.AutopayCheckoutActivity.PLAN_TRIAL_NEW
                ))
            }
        }.show(childFragmentManager, BottomSheetTrialOffer.TAG)
    }

    private fun showInsufficientFundsSheet() {
        if (!isAdded) return
        val ctx = context ?: return

        // Non-autopay language → no subscribe sheet ever. Route to Wallet
        // for coin top-up (legacy flow).
        if (!com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEnabled(ctx)) {
            startActivity(Intent(ctx, WalletActivity::class.java))
            return
        }

        // Per-language re-enable prevention: when admin has disabled
        // re-subscription for this language (LanguageFeatureCache says
        // re_subscription_enabled=false), lapsed users (everActive &&
        // !isActive) are routed to Wallet for coin-pack purchase instead of
        // the subscribe sheet. When the toggle is ON (default) lapsed users
        // see BottomSheetOldUserSubscribe and re-subscribe via
        // AutopayCheckoutActivity → PLAN_DIRECT_OLD (₹299 direct charge).
        if (SubscriptionStateCache.everActive(ctx)
            && !SubscriptionStateCache.isActive(ctx)
            && !com.gmwapp.hima.utils.LanguageFeatureCache.isReSubscriptionEnabled(ctx)
        ) {
            startActivity(Intent(ctx, WalletActivity::class.java))
            return
        }

        val tag = com.gmwapp.hima.dialogs.BottomSheetOldUserSubscribe.TAG
        val existing = childFragmentManager.findFragmentByTag(tag)
        if (existing is com.gmwapp.hima.dialogs.BottomSheetOldUserSubscribe && existing.isAdded) return
        // Insufficient-talktime is a mid-call-flow popup; the user wants to
        // call right now, so we keep the coin grid (low-friction recharge)
        // but still surface the right subscribe price. Never-ever-active →
        // ₹1 trial; lapsed/cancelled → ₹299 direct. The ₹1 path skips the
        // explainer video here on purpose — the video is already shown at
        // the primary entry points (chat lock, top-right coin pill, wallet),
        // and a video gate would block users in their moment of urgency.
        val isNeverSubscribed = !SubscriptionStateCache.everActive(ctx)
        val planType = if (isNeverSubscribed)
            com.gmwapp.hima.activities.AutopayCheckoutActivity.PLAN_TRIAL_NEW
        else
            com.gmwapp.hima.activities.AutopayCheckoutActivity.PLAN_DIRECT_OLD
        val subscribeButtonText = if (isNeverSubscribed) "₹1 only" else "₹299 only"
        com.gmwapp.hima.dialogs.BottomSheetOldUserSubscribe.newInstance(
            bannerOnly = false,
            title = "You have insufficient talktime balance to make this call. Please recharge",
            subscribeButtonText = subscribeButtonText
        ).apply {
            setOnSubscribeClickListener {
                val c = context ?: return@setOnSubscribeClickListener
                startActivity(com.gmwapp.hima.activities.AutopayCheckoutActivity.intentFor(
                    c, planType
                ))
            }
        }.show(childFragmentManager, tag)
    }

    private fun refreshCoinsDisplayFromCache() {
        if (!isAdded || !::binding.isInitialized) return
        val cached = BaseApplication.getInstance()?.getPrefs()?.getUserData() ?: return
        // Daily-claim coins are credited server-side via /daily_claim;
        // users.coins reflects the new total directly, no local bonus.
        val displayCoins = if (UserSegment.isNewUser(requireContext()) && !SubscriptionStateCache.isActive(requireContext())) 0
                           else (cached.coins ?: 0)
        binding.tvCoins.text = displayCoins.toString()
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
                binding.tvCoins.text = total.toString()
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
    }

    private fun refreshStarTabVisibility() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val showStar = (userData?.startab ?: 0) == 1
        binding.btnFilterStar.visibility = if (showStar) View.VISIBLE else View.GONE
    }

    private fun updateFilterButtonStyles() {
        val strokeWidthPx = (1 * resources.displayMetrics.density).toInt()
        val pinkColor   = resources.getColorStateList(R.color.colorAccent, null)
        val goldColor   = android.content.res.ColorStateList.valueOf(0xFFFFC107.toInt())
        val chatsFreeGreen = android.content.res.ColorStateList.valueOf(0xFF10B981.toInt())
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
                backgroundTintList = chatsFreeGreen
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
                        // B080 + B097 — resolve once so isOnline can also check
                        // availability. For female viewers, the other party is
                        // male and there are no audio/video toggles to honor —
                        // both forced to 1. For male viewers, the server values
                        // (creator's toggles) flow through unchanged.
                        val audioAvail = if (currentUserIsFemale) 1 else (item.user.audioStatus ?: 1)
                        val videoAvail = if (currentUserIsFemale) 1 else (item.user.videoStatus ?: 1)
                        com.gmwapp.hima.models.ChatConversation(
                            threadId = item.chatId,
                            userId = item.user.id.toString(),
                            userName = item.user.name,
                            userImage = item.user.image ?: "",
                            lastMessage = item.lastMessage?.message ?: "",
                            lastMessageType = item.lastMessage?.messageType ?: "text",
                            lastMessageTime = ts,
                            unreadCount = effectiveUnread,
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
