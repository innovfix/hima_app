package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.BottomSheetCreateIplRoomBinding
import com.gmwapp.hima.models.IplTeam
import com.gmwapp.hima.retrofit.responses.IplMatchData
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BottomSheetCreateIplRoom(
    private val matchSuggestions: List<IplMatchData>,
    private val onRoomCreated: (roomName: String, teamA: IplTeam, teamB: IplTeam, selectedTeam: IplTeam) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetCreateIplRoomBinding
    private var currentMatch: IplMatchData? = null
    private var teamA: IplTeam? = null
    private var teamB: IplTeam? = null
    private var selectedTeam: IplTeam? = null

    override fun getTheme(): Int = R.style.BaseBottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetCreateIplRoomBinding.inflate(inflater, container, false)

        if (matchSuggestions.isEmpty()) {
            binding.tvMatchValue.text = "No match live right now"
            binding.btnCreateRoom.isEnabled = false
            binding.btnCreateRoom.alpha = 0.5f
            return binding.root
        }

        // Use the first (currently live) match
        currentMatch = matchSuggestions.first()
        teamA = IplTeam.values().find { it.abbreviation == currentMatch!!.teamA }
        teamB = IplTeam.values().find { it.abbreviation == currentMatch!!.teamB }
        binding.tvMatchValue.text = "${currentMatch!!.teamA} vs ${currentMatch!!.teamB}"

        // TC_016: Do NOT auto-select a team. The user must tap one of the chips
        // explicitly. Previously the saved IPL team was auto-selected which made
        // QA see "Even when no team selected, one gets chosen automatically".
        selectedTeam = null

        renderTeamChips()
        updateCreateButtonState()

        binding.btnCreateRoom.setOnClickListener {
            val sel = selectedTeam
            val a = teamA
            val b = teamB
            if (sel == null || a == null || b == null) {
                Toast.makeText(requireContext(), "Please select your team first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val customName = binding.etRoomName.text?.toString()?.trim()
            if (customName.isNullOrEmpty()) {
                binding.etRoomName.error = "Please enter a room name"
                binding.etRoomName.requestFocus()
                return@setOnClickListener
            }
            onRoomCreated(customName, a, b, sel)
            dismiss()
        }

        return binding.root
    }

    private fun renderTeamChips() {
        binding.llTeamChips.removeAllViews()
        val teams = listOfNotNull(teamA, teamB)
        for (team in teams) {
            binding.llTeamChips.addView(createTeamChip(team))
        }
    }

    private fun createTeamChip(team: IplTeam): View {
        val isSelected = (team == selectedTeam)
        val badgeReserve = dp(10)

        val outer = FrameLayout(requireContext()).apply {
            val olp = LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT)
            olp.marginEnd = dp(14)
            layoutParams = olp
            setPadding(badgeReserve, badgeReserve, badgeReserve, badgeReserve)
            clipChildren = false
            clipToPadding = false
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(14), dp(10), dp(14))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor("#1A2640"))
            if (isSelected) setStroke(dp(2), Color.parseColor("#4CAF50"))
            else setStroke(dp(1), Color.parseColor("#2A3A52"))
        }

        // Centered color circle with abbreviation
        val circleSize = dp(48)
        val circleWrap = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            try { typeface = resources.getFont(R.font.poppins_bold) } catch (_: Exception) {}
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER }
        }
        circleWrap.addView(abbrText)
        card.addView(circleWrap)

        val label = TextView(requireContext()).apply {
            text = team.abbreviation
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            try { typeface = resources.getFont(R.font.poppins_semibold) } catch (_: Exception) {}
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            gravity = android.view.Gravity.CENTER
        }
        card.addView(label)
        outer.addView(card)

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
                text = "\u2713"
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
            // Save to backend so badge updates everywhere
            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            val uid = userData?.id ?: 0
            if (uid > 0) {
                val updated = userData!!.copy(ipl_team = team.abbreviation)
                BaseApplication.getInstance()?.getPrefs()?.setUserData(updated)
            }
            renderTeamChips()
            updateCreateButtonState()
        }

        return outer
    }

    private fun updateCreateButtonState() {
        val enabled = selectedTeam != null
        binding.btnCreateRoom.isEnabled = enabled
        binding.btnCreateRoom.alpha = if (enabled) 1f else 0.5f
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
