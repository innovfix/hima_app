package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.IplRoomCallActivity
import com.gmwapp.hima.databinding.BottomSheetJoinIplRoomBinding
import com.gmwapp.hima.models.IplTeam
import com.gmwapp.hima.viewmodels.IplRoomViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BottomSheetJoinIplRoom : BottomSheetDialogFragment() {

    private val TAG = "JoinIplRoom"
    private lateinit var binding: BottomSheetJoinIplRoomBinding
    private val viewModel: IplRoomViewModel by viewModels()

    private var selectedTeam: IplTeam? = null
    private var availableTeams: List<IplTeam> = emptyList()

    private val userId: Int
        get() = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0

    override fun getTheme(): Int = R.style.BaseBottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetJoinIplRoomBinding.inflate(inflater, container, false)
        observeViewModel()
        setupListeners()

        // Pre-fill if user already has a team selected
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.ipl_team?.let { abbr ->
            selectedTeam = IplTeam.values().find { it.abbreviation == abbr }
        }
        updateJoinButtonsState()

        viewModel.getMatchSuggestions()
        return binding.root
    }

    private fun observeViewModel() {
        viewModel.matchSuggestionsLiveData.observe(viewLifecycleOwner) { matches ->
            if (matches != null && matches.isNotEmpty()) {
                val abbrs = matches.flatMap { listOf(it.teamA, it.teamB) }.distinct()
                availableTeams = IplTeam.values().filter { it.abbreviation in abbrs }
                renderTeamChips()
            } else {
                availableTeams = emptyList()
                binding.llTeamChips.removeAllViews()
                val emptyText = TextView(requireContext()).apply {
                    text = "No matches available right now"
                    setTextColor(Color.parseColor("#80FFFFFF"))
                    textSize = 13f
                }
                binding.llTeamChips.addView(emptyText)
            }
        }

        viewModel.joinByCodeLiveData.observe(viewLifecycleOwner) { response ->
            binding.btnJoinWithCode.isEnabled = selectedTeam != null
            if (response?.success == true && response.data != null) {
                launchRoomCall(
                    response.data.roomId, response.data.roomName ?: "IPL Room",
                    response.data.teamA ?: "MI", response.data.teamB ?: "CSK"
                )
                dismiss()
            } else {
                val msg = response?.message ?: "Failed to join room"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.joinRandomLiveData.observe(viewLifecycleOwner) { response ->
            binding.btnJoinRandom.isEnabled = selectedTeam != null
            if (response?.success == true && response.data != null) {
                launchRoomCall(
                    response.data.roomId, response.data.roomName ?: "IPL Room",
                    response.data.teamA ?: "MI", response.data.teamB ?: "CSK"
                )
                dismiss()
            } else {
                val msg = response?.message ?: "No rooms available right now"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.errorLiveData.observe(viewLifecycleOwner) { error ->
            updateJoinButtonsState()
            if (!error.isNullOrEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderTeamChips() {
        binding.llTeamChips.removeAllViews()
        for (team in availableTeams) {
            val chip = createTeamChip(team)
            binding.llTeamChips.addView(chip)
        }
    }

    private fun createTeamChip(team: IplTeam): View {
        val isSelected = (team == selectedTeam)

        // Outer frame with margin reserved for the checkmark badge that sits OUTSIDE
        // the chip card border (top-right). Padding here = badge half-size, so the
        // chip card sits inside with room for the badge to overlap its top-right corner.
        val badgeReserve = dp(10)
        val outer = FrameLayout(requireContext()).apply {
            val olp = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
            olp.marginEnd = dp(12)
            layoutParams = olp
            setPadding(badgeReserve, badgeReserve, badgeReserve, badgeReserve)
            clipChildren = false
            clipToPadding = false
            isClickable = true
            isFocusable = true
        }

        // Card background — fills inside outer's padding area, so its border is fully visible
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(12), dp(10), dp(12))
            val clp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = clp
        }
        val bg = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor("#1A2640"))
            if (isSelected) {
                setStroke(dp(2), Color.parseColor("#4CAF50"))
            } else {
                setStroke(dp(1), Color.parseColor("#2A3A52"))
            }
        }
        card.background = bg

        // Centered circle with team color and abbreviation inside
        val circleSize = dp(44)
        val circleWrap = FrameLayout(requireContext()).apply {
            val cwlp = LinearLayout.LayoutParams(circleSize, circleSize)
            cwlp.gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = cwlp
        }

        val circle = View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(circleSize, circleSize)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(team.primaryColor))
            }
        }
        circleWrap.addView(circle)

        val abbrText = TextView(requireContext()).apply {
            text = team.abbreviation
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            try { typeface = resources.getFont(R.font.poppins_bold) } catch (_: Exception) {}
            val tlp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            tlp.gravity = android.view.Gravity.CENTER
            layoutParams = tlp
        }
        circleWrap.addView(abbrText)
        card.addView(circleWrap)

        // Team name label
        val label = TextView(requireContext()).apply {
            text = team.abbreviation
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            try { typeface = resources.getFont(R.font.poppins_semibold) } catch (_: Exception) {}
            val llp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            llp.topMargin = dp(6)
            layoutParams = llp
            gravity = android.view.Gravity.CENTER
        }
        card.addView(label)

        outer.addView(card)

        // Checkmark badge — sits at the top-right of OUTER (which extends 10dp beyond
        // the chip card on all sides), so the badge overlaps the chip's corner from
        // outside without breaking the green border.
        if (isSelected) {
            val checkSize = dp(22)
            val checkBg = View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(checkSize, checkSize).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#4CAF50"))
                    setStroke(dp(2), Color.parseColor("#0F172A"))
                }
            }
            outer.addView(checkBg)

            val checkText = TextView(requireContext()).apply {
                text = "\u2713" // ✓
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                try { typeface = resources.getFont(R.font.poppins_bold) } catch (_: Exception) {}
                gravity = android.view.Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(checkSize, checkSize).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                }
            }
            outer.addView(checkText)
        }

        outer.setOnClickListener {
            selectedTeam = team
            val uid = userId
            if (uid > 0) {
                viewModel.updateIplTeam(uid, team.abbreviation)
                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                if (userData != null) {
                    val updated = userData.copy(ipl_team = team.abbreviation)
                    BaseApplication.getInstance()?.getPrefs()?.setUserData(updated)
                }
            }
            renderTeamChips()
            updateJoinButtonsState()
        }

        return outer
    }

    private fun updateJoinButtonsState() {
        val enabled = selectedTeam != null
        binding.btnJoinRandom.isEnabled = enabled
        binding.btnJoinRandom.alpha = if (enabled) 1f else 0.5f
        binding.btnJoinWithCode.isEnabled = enabled
        binding.btnJoinWithCode.alpha = if (enabled) 1f else 0.5f
    }

    private fun setupListeners() {
        binding.btnJoinRandom.setOnClickListener {
            if (selectedTeam == null) {
                Toast.makeText(requireContext(), "Please select your team first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "Join Random clicked, userId=$userId")
            binding.btnJoinRandom.isEnabled = false
            viewModel.joinRoomRandom(userId)
        }

        binding.btnJoinWithCode.setOnClickListener {
            if (selectedTeam == null) {
                Toast.makeText(requireContext(), "Please select your team first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val code = binding.etInviteCode.text?.toString()?.trim()?.uppercase() ?: ""
            if (code.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter an invite code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "Join with code clicked, userId=$userId, code=$code")
            binding.btnJoinWithCode.isEnabled = false
            viewModel.joinRoomByCode(userId, code)
        }
    }

    private fun launchRoomCall(roomId: Int, roomName: String, teamA: String, teamB: String) {
        val intent = Intent(requireContext(), IplRoomCallActivity::class.java).apply {
            putExtra("room_id", roomId)
            putExtra("room_name", roomName)
            putExtra("team_a", teamA)
            putExtra("team_b", teamB)
        }
        startActivity(intent)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
