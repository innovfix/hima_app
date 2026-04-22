package com.gmwapp.hima.fragments

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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.activities.CommunityGuidelineActivity
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
    private var isPanVerified = false
    var whataspplink : String = ""
    lateinit var language : String



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileFemaleBinding.inflate(layoutInflater)
        initUI()
        whatsapp()
        panVerification()
        return binding.root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK && requestCode == EDIT_PROFILE_REQUEST_CODE){
            updateValues()
        }
    }

    private fun updateIplBadge() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val iplEnabled = (userData?.ipl_rooms_enabled ?: 0) == 1

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
        Glide.with(this).load(userData?.image).
        apply(RequestOptions.circleCropTransform()).into(binding.ivProfile)
    }

    /**
     * Called when the user re-taps the Profile tab in bottom nav.
     * Re-fetches user profile data and IPL match suggestions.
     */
    override fun refresh() {
        val refreshUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        profileViewModel.getUsers(refreshUserId)
        iplRoomViewModel.getMatchSuggestions()
        updateValues()
        updateIplBadge()
    }

    private fun initUI(){

        updateValues()
        updateIplBadge()
        iplRoomViewModel.getMatchSuggestions() // Fetch today's matches for team picker

        // Refresh user data from server (handles auto-clear of expired ipl_team / dnd)
        val refreshUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
        refreshUserId?.let { profileViewModel.getUsers(it) }
        profileViewModel.getUserLiveData.observe(viewLifecycleOwner) { response ->
            response?.data?.let { fresh ->
                BaseApplication.getInstance()?.getPrefs()?.setUserData(fresh)
                updateIplBadge()
                refreshDndUi()
                refreshStarCreatorVisibility()
                // KYC state derived here instead of a separate login() call.
                isPanVerified = !fresh.pancard_name.isNullOrEmpty()
                    && !fresh.pancard_number.isNullOrEmpty()
            }
        }

        binding.iplTeamBadge.setOnSingleClickListener {
            showIplTeamPicker()
        }

        // DND setup
        refreshDndUi()
        binding.switchDnd.setOnClickListener {
            if (binding.switchDnd.isChecked) {
                binding.switchDnd.isChecked = false
                showDndDurationPicker()
            } else {
                callToggleDndApi(enabled = 0, durationHours = 0)
            }
        }

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
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val showStarCreator = (userData?.starcreator ?: 0) == 1 && userData?.star != 1
        binding.clStarCreatorApply.visibility = if (showStarCreator) View.VISIBLE else View.GONE

        binding.clStarCreatorApply.setOnSingleClickListener {
            val intent = Intent(context, StarCreatorApplicationActivity::class.java)
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
        // KYC status is now derived from profileViewModel.getUserLiveData (see
        // initUI). Keeping this function as a no-op shim to avoid churning
        // callers until the next cleanup commit.
        val cached = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        isPanVerified = !cached?.pancard_name.isNullOrEmpty()
            && !cached?.pancard_number.isNullOrEmpty()
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
        val showStarCreator = (userData?.starcreator ?: 0) == 1 && userData?.star != 1
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
    }

    // ================= DND =================

    private fun refreshDndUi() {
        if (!::binding.isInitialized) return
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val enabled = (userData?.dnd_enabled ?: 0) == 1
        val until = userData?.dnd_until

        binding.switchDnd.setOnCheckedChangeListener(null)
        binding.switchDnd.isChecked = enabled

        binding.switchDnd.setOnClickListener {
            if (binding.switchDnd.isChecked) {
                binding.switchDnd.isChecked = false
                showDndDurationPicker()
            } else {
                callToggleDndApi(enabled = 0, durationHours = 0)
            }
        }

        binding.tvDndStatus.text = "Do Not Disturb"
    }

    private fun formatDndUntil(iso: String): String {
        return try {
            val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val date = sdfIn.parse(iso) ?: return iso
            val sdfOut = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
            sdfOut.format(date)
        } catch (e: Exception) {
            iso
        }
    }

    private fun showDndDurationPicker() {
        val view = layoutInflater.inflate(R.layout.dialog_dnd_duration, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTransparent)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dnd_1h).setOnClickListener {
            dialog.dismiss(); callToggleDndApi(enabled = 1, durationHours = 1)
        }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dnd_3h).setOnClickListener {
            dialog.dismiss(); callToggleDndApi(enabled = 1, durationHours = 3)
        }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dnd_24h).setOnClickListener {
            dialog.dismiss(); callToggleDndApi(enabled = 1, durationHours = 24)
        }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dnd_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun callToggleDndApi(enabled: Int, durationHours: Int) {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        profileViewModel.toggleDnd(userId, enabled, durationHours, object : com.gmwapp.hima.retrofit.callbacks.NetworkCallback<com.gmwapp.hima.retrofit.responses.ToggleDndResponse> {
            override fun onResponse(call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.ToggleDndResponse>, response: retrofit2.Response<com.gmwapp.hima.retrofit.responses.ToggleDndResponse>) {
                val body = response.body()
                if (body?.success == true) {
                    val current = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                    if (current != null) {
                        val updated = current.copy(
                            dnd_enabled = body.data?.dnd_enabled ?: 0,
                            dnd_until = body.data?.dnd_until
                        )
                        BaseApplication.getInstance()?.getPrefs()?.setUserData(updated)
                    }
                    activity?.runOnUiThread {
                        refreshDndUi()
                        Toast.makeText(requireContext(), body.message ?: "Updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onFailure(call: retrofit2.Call<com.gmwapp.hima.retrofit.responses.ToggleDndResponse>, t: Throwable) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to update DND", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNoNetwork() {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "No internet", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}