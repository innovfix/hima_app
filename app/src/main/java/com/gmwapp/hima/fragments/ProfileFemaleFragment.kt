package com.gmwapp.hima.fragments

import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.utils.LogCapture
import com.gmwapp.hima.utils.TesterAccess
import com.gmwapp.hima.utils.setOnHold
import com.gmwapp.hima.utils.showAppToast

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.gmwapp.hima.activities.CreatorLevelActivity
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.AccountPrivacyActivity
import com.gmwapp.hima.activities.MyWarningsActivity
import com.gmwapp.hima.activities.EarningsActivity
import com.gmwapp.hima.activities.EditProfileActivity
import com.gmwapp.hima.activities.FemaleTransactionsActivity
import com.gmwapp.hima.activities.FriendsListActivity
import com.gmwapp.hima.activities.HelpAndSupportActivity
import com.gmwapp.hima.activities.StarCreatorApplicationActivity
import com.gmwapp.hima.activities.RefundWebViewActivity
import com.gmwapp.hima.activities.ShareActivity
import com.gmwapp.hima.activities.TermConditionWebViewActivity
import com.gmwapp.hima.callbacks.NetworkRetryable
import com.gmwapp.hima.callbacks.Refreshable
import com.gmwapp.hima.fragments.FriendsTabFragment
import com.gmwapp.hima.databinding.FragmentProfileFemaleBinding
import com.gmwapp.hima.dialogs.BottomSheetLogout
import com.gmwapp.hima.dialogs.BottomSheetSelectIplTeam
import com.gmwapp.hima.models.IplTeam
import com.gmwapp.hima.utils.DndController
import com.gmwapp.hima.utils.UserDataLocalIntentMerge
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.WhatsappLinkViewModel
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFemaleFragment : BaseFragment(), NetworkRetryable, Refreshable {
    lateinit var binding: FragmentProfileFemaleBinding
    private val EDIT_PROFILE_REQUEST_CODE = 1
    private val accountViewModel: AccountViewModel by viewModels()
    private val iplRoomViewModel: com.gmwapp.hima.viewmodels.IplRoomViewModel by viewModels()
    private val whatsappLinkViewModel: WhatsappLinkViewModel by viewModels()

    private val loginViewModel: LoginViewModel by viewModels()
    private lateinit var dndController: DndController
    private var isPanVerified = false
    var whataspplink : String = ""
    lateinit var language : String



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileFemaleBinding.inflate(layoutInflater)
        // 2026-05-22 v17 — force-hide Creator Level card IMMEDIATELY after
        // binding inflation, before any other init. XML already says
        // visibility="gone" but force here in case install/cache weirdness
        // overrides it. Also hidden again in onResume as backstop.
        binding.cvCreatorLevelProfile.visibility = View.GONE
        setupStatusBarInsets()
        initUI()
        whatsapp()
        panVerification()
        return binding.root
    }

    private fun setupStatusBarInsets() {
        // Whole page scrolls now → pad the SCROLL VIEW (not the root) by the
        // status-bar height so scrolling content clips just below the status bar.
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
        if(resultCode == Activity.RESULT_OK && requestCode == EDIT_PROFILE_REQUEST_CODE){
            updateValues()
        }
    }

    private fun updateIplBadge() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val iplEnabled = com.gmwapp.hima.utils.FeatureFlags.IPL_ENABLED &&
            (userData?.ipl_rooms_enabled ?: 0) == 1

        if (!iplEnabled) {
            binding.iplTeamBadge.visibility = View.GONE
            return
        }
        binding.iplTeamBadge.visibility = View.VISIBLE

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
            iplRoomViewModel.updateIplTeam(userId, teamAbbr)
            val updated = userData.copy(ipl_team = selectedTeam?.abbreviation)
            prefs.setUserData(updated)
            updateIplBadge()
        }
        parentFragmentManager.let { bottomSheet.show(it, "IplTeamPicker") }
    }

    private fun updateValues(){
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val prefs = BaseApplication.getInstance()?.getPrefs()
        val supportMail = prefs?.getSettingsData()?.support_mail
        val subject = getString(R.string.delete_account_mail_subject, userData?.mobile, userData?.language)

        val body = ""
        binding.tvSupportMail.setOnSingleClickListener {
            val intent = Intent(Intent.ACTION_VIEW)

            val data = Uri.parse(("mailto:$supportMail?subject=$subject").toString() + "&body=$body")
            intent.setData(data)

            startActivity(intent)
        }
        binding.tvSupportMail.paintFlags =
            binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvSupportMail.text =
            supportMail
        binding.tvName.text = userData?.name
        // Mobile number removed from the profile header (both male & female).
        binding.tvPhone.visibility = android.view.View.GONE
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

    private fun initUI(){

        updateValues()
        updateIplBadge()
        updateBlockBanner()
        iplRoomViewModel.getMatchSuggestions() // Fetch today's matches for team picker

        dndController = DndController(
            this,
            binding.switchDnd,
            binding.tvDndStatus,
            profileViewModel,
            requireCallsDisabledBeforeEnablingDnd = true,
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
                refreshStarCreatorVisibility()
            }
        }

        binding.iplTeamBadge.setOnSingleClickListener {
            showIplTeamPicker()
        }

        dndController.attach()

        val prefs = BaseApplication.getInstance()?.getPrefs()


        binding.clEarnings.setOnSingleClickListener( {
            val intent = Intent(context, EarningsActivity::class.java)
            startActivity(intent)
        })

        binding.clTransactions.setOnSingleClickListener {
            val intent = Intent(context, FemaleTransactionsActivity::class.java)
            startActivity(intent)
        }
        
        // Edit profile via edit icon badge
        binding.ivEditProfile.setOnSingleClickListener( {
            val intent = Intent(context, EditProfileActivity::class.java)
            startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
        })
        
        // Edit profile via avatar container (new design)
        binding.avatarContainer.setOnSingleClickListener {
            val intent = Intent(context, EditProfileActivity::class.java)
            startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
        }
        
        // Edit profile via edit icon badge (new design)
        binding.editIconBadge.setOnSingleClickListener {
            val intent = Intent(context, EditProfileActivity::class.java)
            startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
        }
        
        binding.clAccountPrivacy.setOnSingleClickListener( {
            val intent = Intent(context, AccountPrivacyActivity::class.java)
            startActivity(intent)
        })
        binding.cvLogout.setOnSingleClickListener( {
            val bottomSheet: BottomSheetLogout =
                BottomSheetLogout()
            fragmentManager?.let { it1 ->
                bottomSheet.show(
                    it1,
                    "ProfileFragment"
                )
            }
        })

        binding.clReferEarn.setOnSingleClickListener {
            val intent = Intent(context, ShareActivity::class.java)
            if (isPanVerified){
                startActivity(intent)
            }else{
                requireContext().showAppToast("Please Complete Kyc", Toast.LENGTH_SHORT)

            }
        }

        binding.clMyFriends.setOnSingleClickListener {
            val intent = Intent(context, FriendsListActivity::class.java)
            intent.putExtra("target_tab", FriendsTabFragment.TYPE_CHAT)
            startActivity(intent)
        }

        // Show "Become Star Creator" only if starcreator == 1 and user is not already a star
        // — app-side kill-switch overrides the server `starcreator` flag.
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val showStarCreator = com.gmwapp.hima.utils.FeatureFlags.STAR_CREATOR_ENABLED &&
            (userData?.starcreator ?: 0) == 1 && userData?.star != 1
        binding.clStarCreatorApply.visibility = if (showStarCreator) View.VISIBLE else View.GONE

        binding.clStarCreatorApply.setOnSingleClickListener {
            val intent = Intent(context, StarCreatorApplicationActivity::class.java)
            startActivity(intent)
        }

        // 2026-05-22 v14 — belt-and-braces runtime force-hide. XML already sets
        // visibility="gone" but tester reports the card still showing on
        // some installs (likely device/install-cache weirdness). Force at
        // runtime too so XML override at install can't expose the mock UI.
        binding.cvCreatorLevelProfile.visibility = View.GONE
        binding.cvCreatorLevelProfile.setOnSingleClickListener {
            startActivity(Intent(context, CreatorLevelActivity::class.java))
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

        binding.clWhatsapp.setOnClickListener {
            if (whataspplink.isNotEmpty() && whataspplink!=null){
                openWhatsAppGroup(whataspplink)
            }
        }

        binding.clWarnings.setOnSingleClickListener {
            val intent = Intent(context, MyWarningsActivity::class.java)
            startActivity(intent)
        }

        binding.clHelpSupport.setOnSingleClickListener {
            val intent = Intent(context, HelpAndSupportActivity::class.java)
            startActivity(intent)
        }

        binding.tvAppVersion.text = "Version ${BuildConfig.VERSION_NAME}"
        binding.tvAppVersion.setOnClickListener { TesterAccess.onVersionTap(requireContext()) }
        binding.tvAppVersion.setOnHold(5000L) {
            if (TesterAccess.canUseDebugTools()) LogCapture.shareLogs(requireActivity())
        }

        accountViewModel.getSettings()
        accountViewModel.settingsLiveData.observe(viewLifecycleOwner, Observer {
            if (it!=null && it.success) {
                if (it.data != null) {
                    if (it.data.size > 0) {
                        prefs?.setSettingsData(it.data.get(0))
                        val supportMail = prefs?.getSettingsData()?.support_mail
                        binding.tvSupportMail.text =
                            supportMail
                        val userData = prefs?.getUserData()
                        val subject = getString(R.string.delete_account_mail_subject, userData?.mobile,  userData?.language)

                        val body = ""
                        binding.tvSupportMail.setOnSingleClickListener {
                            val intent = Intent(Intent.ACTION_VIEW)

                            val data = Uri.parse(("mailto:$supportMail?subject=$subject").toString() + "&body=$body")
                            intent.setData(data)

                            startActivity(intent)
                        }
                        binding.tvSupportMail.paintFlags =
                            binding.tvSupportMail.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    }
                }
            }
        })
    }

    fun panVerification(){
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        userData?.let { loginViewModel.login(it.mobile,"0","0") }
        loginViewModel.loginResponseLiveData.observe(viewLifecycleOwner, Observer {
            // ✅ Add null check before accessing properties
            if (it == null) {
                Log.w("ProfileFemaleFragment", "LoginResponse is null")
                return@Observer
            }

            if (it.success) {
                if (!it.data?.pancard_name.isNullOrEmpty()&& !it.data?.pancard_number.isNullOrEmpty()){
                    isPanVerified = true
                }

            }
        })
    }

    fun whatsapp(){

        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()


        language = userData?.language.toString()
        language?.let { whatsappLinkViewModel.fetchLink(it) }

        whatsappLinkViewModel.whatsappResponseLiveData.observe(viewLifecycleOwner) { response ->
            response?.let {
                if (it.success && it.data.isNotEmpty()) {
                    whataspplink = it.data[0].link

                } else {
                    Log.e("VideoError", "Please try again later")
                }
            }
        }
    }

    private fun refreshStarCreatorVisibility() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        // app-side kill-switch overrides the server `starcreator` flag.
        val showStarCreator = com.gmwapp.hima.utils.FeatureFlags.STAR_CREATOR_ENABLED &&
            (userData?.starcreator ?: 0) == 1 && userData?.star != 1
        binding.clStarCreatorApply.visibility = if (showStarCreator) View.VISIBLE else View.GONE
    }

    override fun onNetworkRetry() {
        updateValues()
    }

    private fun openWhatsAppGroup(groupLink: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(groupLink)
            intent.setPackage("com.whatsapp") // Ensures only WhatsApp handles the intent
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
//            showAppToast("WhatsApp is not installed", Toast.LENGTH_SHORT)
        }
    }
    override fun onResume() {
        super.onResume()
            panVerification()
        // 2026-05-22 v17 — force-hide Creator Level card on EVERY onResume.
        // refresh() only runs on pull-to-refresh; XML visibility="gone" should
        // work but tester reports the card still showing. Hide here too so
        // it can never appear regardless of binding/inflation state.
        try {
            binding.cvCreatorLevelProfile.visibility = View.GONE
        } catch (t: Throwable) {
            android.util.Log.w("ProfileFemaleFragment", "Force-hide creator level failed: ${t.message}")
        }
    }

}