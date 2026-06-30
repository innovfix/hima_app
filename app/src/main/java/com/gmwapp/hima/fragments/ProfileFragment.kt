package com.gmwapp.hima.fragments

import com.gmwapp.hima.utils.showAppToast

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.activities.CommunityGuidelineActivity
import com.gmwapp.hima.activities.SettingsActivity
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.AccountPrivacyActivity
import com.gmwapp.hima.activities.ManageNotificationsActivity
import com.gmwapp.hima.activities.MyWarningsActivity
import com.gmwapp.hima.activities.EditProfileActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.activities.HelpAndSupportActivity
import com.gmwapp.hima.activities.RefundWebViewActivity
import com.gmwapp.hima.activities.ShareActivity
import com.gmwapp.hima.activities.TermConditionWebViewActivity
import com.gmwapp.hima.activities.TransactionsActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.callbacks.NetworkRetryable
import com.gmwapp.hima.callbacks.Refreshable
import com.gmwapp.hima.databinding.FragmentProfileBinding
import com.gmwapp.hima.dialogs.BottomSheetLogout
import com.gmwapp.hima.dialogs.BottomSheetSelectIplTeam
import com.gmwapp.hima.models.IplTeam
import com.gmwapp.hima.utils.DndController
import com.gmwapp.hima.utils.UserDataLocalIntentMerge
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFragment : BaseFragment(), NetworkRetryable, Refreshable {
    lateinit var binding: FragmentProfileBinding
    private val EDIT_PROFILE_REQUEST_CODE = 1
    private val accountViewModel: AccountViewModel by viewModels()
    private val iplRoomViewModel: com.gmwapp.hima.viewmodels.IplRoomViewModel by viewModels()
    private lateinit var dndController: DndController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(layoutInflater)
        setupStatusBarInsets()
        initUI()
        return binding.root
    }

    private fun setupStatusBarInsets() {
        // The whole page scrolls now, so pad the SCROLL VIEW (not the root) by the
        // status-bar height. With default clipToPadding, the scrolling content then
        // always clips just below the status bar instead of sliding under the clock/
        // signal icons.
        val scroll = binding.profileScroll
        val baseTop = scroll.paddingTop
        val baseBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                baseTop + statusBarInset,
                view.paddingRight,
                baseBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == EDIT_PROFILE_REQUEST_CODE) {
            updateValues()
        }
    }

    private fun updateIplBadge() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        // FeatureFlags.IPL_ENABLED is an app-side kill-switch that overrides the
        // server flag — when false the badge stays hidden and the picker is disabled.
        val iplEnabled = com.gmwapp.hima.utils.FeatureFlags.IPL_ENABLED &&
            (userData?.ipl_rooms_enabled ?: 0) == 1

        // Hide badge entirely if IPL rooms are disabled by admin or the app flag
        if (!iplEnabled) {
            binding.iplTeamBadge.visibility = View.GONE
            return
        }
        binding.iplTeamBadge.visibility = View.VISIBLE

        // Read team from server (userdata.ipl_team), not from local prefs
        val savedTeamAbbr = userData?.ipl_team
        val team = savedTeamAbbr?.let { abbr ->
            IplTeam.values().find { it.abbreviation == abbr }
        }

        if (team != null) {
            binding.iplBadgeTeamDot.visibility = View.VISIBLE
            val dotDrawable = binding.iplBadgeTeamDot.background.mutate() as GradientDrawable
            dotDrawable.setColor(Color.parseColor(team.primaryColor))
            binding.tvIplBadgeTeamName.text = "${team.abbreviation} - ${team.teamName}"
            binding.iplTeamBadge.setBackgroundResource(R.drawable.bg_ipl_team_profile_badge)
        } else {
            binding.iplBadgeTeamDot.visibility = View.GONE
            binding.tvIplBadgeTeamName.text = getString(R.string.choose_team)
            binding.iplTeamBadge.setBackgroundResource(R.drawable.bg_ipl_no_team_badge)
        }
    }

    private fun showIplTeamPicker() {
        // Always fetch fresh match data every time the picker opens
        iplRoomViewModel.getMatchSuggestions()

        // Observe one-time for fresh response
        iplRoomViewModel.matchSuggestionsLiveData.observe(viewLifecycleOwner) { matches ->
            iplRoomViewModel.matchSuggestionsLiveData.removeObservers(viewLifecycleOwner)
            openTeamPickerSheet(matches)
        }
    }

    private fun openTeamPickerSheet(matches: List<com.gmwapp.hima.retrofit.responses.IplMatchData>?) {
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()
        // Read current team from server data, not local prefs
        val currentTeam = userData?.ipl_team?.let { abbr ->
            IplTeam.values().find { it.abbreviation == abbr }
        }

        val playingTeams = if (matches != null && matches.isNotEmpty()) {
            val abbrs = matches.flatMap { listOf(it.teamA, it.teamB) }.distinct()
            IplTeam.values().filter { it.abbreviation in abbrs }.toTypedArray()
        } else null

        if (playingTeams == null || playingTeams.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "No matches available today", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheet = BottomSheetSelectIplTeam(currentTeam, playingTeams) { selectedTeam ->
            val userId = userData?.id ?: return@BottomSheetSelectIplTeam
            val teamAbbr = selectedTeam?.abbreviation ?: ""
            // Save to server
            iplRoomViewModel.updateIplTeam(userId, teamAbbr)
            // Update local userData copy so badge refreshes immediately
            val updated = userData.copy(ipl_team = selectedTeam?.abbreviation)
            prefs.setUserData(updated)
            updateIplBadge()
        }
        parentFragmentManager.let { bottomSheet.show(it, "IplTeamPicker") }
    }

    private fun updateValues() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        binding.tvName.text = userData?.name
        // Mobile number removed from the profile header (both male & female).
        binding.tvPhone.visibility = android.view.View.GONE
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val supportMail = prefs?.getSettingsData()?.support_mail
        val subject = getString(R.string.delete_account_mail_subject, userData?.mobile, userData?.language)

        val body = ""
        binding.tvSupportMail.text = supportMail
        binding.tvSupportMail.paintFlags = binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvSupportMail.setOnSingleClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            val data = Uri.parse("mailto:$supportMail?subject=$subject&body=$body")
            intent.data = data
            startActivity(intent)
        }

        if (userData != null) {
            Glide.with(this)
                .load(userData.image)
                .apply(com.bumptech.glide.request.RequestOptions.circleCropTransform())
                .placeholder(R.drawable.logo)
                .error(R.drawable.logo)
                .into(binding.ivProfile)
        } else {
            Glide.with(this).clear(binding.ivProfile)
        }
    }

    /**
     * Called when the user re-taps the Profile tab in bottom nav.
     * Re-fetches user profile data and IPL match suggestions.
     */
    override fun refresh() {
        // Guard: see HomeFragment.refresh — MainActivity may fire this after a
        // configuration change (e.g. split-screen) before our view is rebound.
        if (view == null || !::binding.isInitialized) return
        val refreshUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        profileViewModel.getUsers(refreshUserId)
        iplRoomViewModel.getMatchSuggestions()
        updateValues()
        updateIplBadge()
        updateBlockBanner()
    }

    private fun updateBlockBanner() {
        if (!::binding.isInitialized) return
        applyBlockBanner(binding.blockBanner)
    }

    private fun initUI() {
        updateValues()
        updateIplBadge()
        updateBlockBanner()
        iplRoomViewModel.getMatchSuggestions() // Fetch today's matches for team picker

        dndController = DndController(
            this,
            binding.switchDnd,
            binding.tvDndStatus,
            profileViewModel,
            cvDnd = binding.cvDnd
        )

        // Refresh user data from server (handles auto-clear of expired ipl_team / dnd)
        val refreshUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
        refreshUserId?.let { profileViewModel.getUsers(it) }
        profileViewModel.getUserLiveData.observe(viewLifecycleOwner) { response ->
            response?.data?.let { fresh ->
                val prev = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                val merged = UserDataLocalIntentMerge.mergePreserveLocalIntent(prev, fresh)
                BaseApplication.getInstance()?.getPrefs()?.setUserData(merged)
                updateIplBadge()
                updateBlockBanner()
                dndController.refresh()
            }
        }

        binding.iplTeamBadge.setOnSingleClickListener {
            showIplTeamPicker()
        }

        dndController.attach()

        val prefs = BaseApplication.getInstance()?.getPrefs()
        
        val userGender = prefs?.getUserData()?.gender

        // Show the My Warnings card for all users on this profile (same card as the female profile)
        binding.clWarnings.visibility = View.VISIBLE

        binding.clManageNotifications.visibility =
            if (userGender?.equals(DConstants.MALE, ignoreCase = true) == true) View.VISIBLE else View.GONE
        binding.clManageNotifications.setOnSingleClickListener {
            startActivity(Intent(context, ManageNotificationsActivity::class.java))
        }

        binding.clWallet.setOnSingleClickListener {
            val intent = Intent(context, WalletActivity::class.java)
            startActivity(intent)
        }
        
        binding.avatarContainer.setOnSingleClickListener {
            if (isInternetAvailable()) {
                val intent = Intent(context, EditProfileActivity::class.java)
                startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
            } else {
                requireContext().showAppToast("No internet connection", Toast.LENGTH_SHORT)
            }
        }
        
        binding.editIconBadge.setOnSingleClickListener {
            if (isInternetAvailable()) {
                val intent = Intent(context, EditProfileActivity::class.java)
                startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
            } else {
                requireContext().showAppToast("No internet connection", Toast.LENGTH_SHORT)
            }
        }

        binding.clTransactions.setOnSingleClickListener {
            val intent = Intent(context, TransactionsActivity::class.java)
            startActivity(intent)
        }

        binding.clAccountPrivacy.setOnSingleClickListener {
            val intent = Intent(context, AccountPrivacyActivity::class.java)
            startActivity(intent)
        }


        binding.clReferEarn.setOnSingleClickListener {
            val intent = Intent(context, ShareActivity::class.java)
            startActivity(intent)
        }

        binding.clWarnings.setOnSingleClickListener {
            val intent = Intent(context, MyWarningsActivity::class.java)
            startActivity(intent)
        }

        binding.clTermsCondition.setOnSingleClickListener {
            val intent = Intent(context, TermConditionWebViewActivity::class.java)

            startActivity(intent)
        }

        binding.clRefund.setOnSingleClickListener {
            val intent = Intent(context, RefundWebViewActivity::class.java)

            startActivity(intent)
        }

        binding.clGuideline.setOnSingleClickListener {
            val intent = Intent(context, CommunityGuidelineActivity::class.java)

            startActivity(intent)
        }

        binding.clHelpSupport.setOnSingleClickListener {
            val intent = Intent(context, HelpAndSupportActivity::class.java)
            startActivity(intent)
        }

        binding.cvSettings.setOnSingleClickListener {
            startActivity(Intent(context, SettingsActivity::class.java))
        }

        binding.cvLogout.setOnSingleClickListener {
            val bottomSheet = BottomSheetLogout()
            fragmentManager?.let {
                bottomSheet.show(it, "ProfileFragment")
            }


        }

        accountViewModel.getSettings()
        accountViewModel.settingsLiveData.observe(viewLifecycleOwner, Observer {
            if (it!=null && it.success && it.data?.isNotEmpty() == true) {
                prefs?.setSettingsData(it.data[0])
                val supportMail = prefs?.getSettingsData()?.support_mail
                binding.tvSupportMail.text = supportMail

                val userData = prefs?.getUserData()
                val subject = getString(R.string.delete_account_mail_subject, userData?.mobile, userData?.language)
                val body = ""

                binding.tvSupportMail.setOnSingleClickListener {
                    val intent = Intent(Intent.ACTION_VIEW)
                    val data = Uri.parse("mailto:$supportMail?subject=$subject&body=$body")
                    intent.data = data
                    startActivity(intent)
                }

                binding.tvSupportMail.paintFlags = binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refreshPremiumCrown()
    }

    private fun refreshPremiumCrown() {
        val ctx = context ?: return
        // Hide for languages without autopay so non-autopay users never see
        // the crown. Existing subscribers (everActive) keep it until their
        // subscription naturally lapses.
        val active = com.gmwapp.hima.utils.SubscriptionStateCache.isActive(ctx)
        val ever = com.gmwapp.hima.utils.SubscriptionStateCache.everActive(ctx)
        val autopayLanguage = com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEnabled(ctx)
        val show = active && (autopayLanguage || ever)
        binding.ivPremiumCrownProfile.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onNetworkRetry() {
        updateValues()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }
}
