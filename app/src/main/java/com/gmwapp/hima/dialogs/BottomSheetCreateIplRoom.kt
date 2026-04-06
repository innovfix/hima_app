package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.BottomSheetCreateIplRoomBinding
import com.gmwapp.hima.models.IplTeam
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class BottomSheetCreateIplRoom(
    private val matchSuggestions: List<String>,
    private val onRoomCreated: (roomName: String, teamA: IplTeam, teamB: IplTeam) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetCreateIplRoomBinding
    private var selectedMatchName: String? = null
    private var selectedTeamA: IplTeam? = null
    private var selectedTeamB: IplTeam? = null

    override fun getTheme(): Int = R.style.BaseBottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetCreateIplRoomBinding.inflate(inflater, container, false)
        setupMatchChips()
        setupTeamSpinners()
        setupCreateButton()
        return binding.root
    }

    private fun setupMatchChips() {
        if (matchSuggestions.isEmpty()) {
            binding.chipGroupMatches.visibility = View.GONE
            return
        }
        for (match in matchSuggestions) {
            val chip = Chip(requireContext()).apply {
                text = match
                isCheckable = true
                setChipBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1A2640")))
                setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))
                chipStrokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2A3A52"))
                chipStrokeWidth = 1f * resources.displayMetrics.density
                chipCornerRadius = 20f * resources.displayMetrics.density
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedMatchName = match
                        // Parse teams from match name
                        val teams = match.split(" vs ")
                        if (teams.size == 2) {
                            selectedTeamA = IplTeam.values().find { it.abbreviation == teams[0] }
                            selectedTeamB = IplTeam.values().find { it.abbreviation == teams[1] }
                            // Update spinners
                            selectedTeamA?.let { teamA ->
                                val posA = IplTeam.values().indexOf(teamA)
                                binding.spinnerTeamA.setSelection(posA)
                            }
                            selectedTeamB?.let { teamB ->
                                val posB = IplTeam.values().indexOf(teamB)
                                binding.spinnerTeamB.setSelection(posB)
                            }
                        }
                    }
                }
            }
            binding.chipGroupMatches.addView(chip)
        }
    }

    private fun setupTeamSpinners() {
        val teamNames = IplTeam.values().map { "${it.abbreviation} - ${it.teamName}" }
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            teamNames
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerTeamA.adapter = spinnerAdapter
        binding.spinnerTeamB.adapter = spinnerAdapter

        // Default selections
        binding.spinnerTeamA.setSelection(0) // MI
        binding.spinnerTeamB.setSelection(1) // CSK
    }

    private fun setupCreateButton() {
        binding.btnCreateRoom.setOnClickListener {
            val teamA = IplTeam.values()[binding.spinnerTeamA.selectedItemPosition]
            val teamB = IplTeam.values()[binding.spinnerTeamB.selectedItemPosition]

            if (teamA == teamB) {
                Toast.makeText(requireContext(), "Please select different teams", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val roomName = selectedMatchName ?: "${teamA.abbreviation} vs ${teamB.abbreviation}"
            onRoomCreated(roomName, teamA, teamB)
            dismiss()
        }
    }
}
