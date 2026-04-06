package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.IplTeamSelectorAdapter
import com.gmwapp.hima.databinding.BottomSheetSelectIplTeamBinding
import com.gmwapp.hima.models.IplTeam
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BottomSheetSelectIplTeam(
    private val currentTeam: IplTeam? = null,
    private val onTeamSelected: (IplTeam?) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetSelectIplTeamBinding
    private lateinit var adapter: IplTeamSelectorAdapter

    override fun getTheme(): Int = R.style.BaseBottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetSelectIplTeamBinding.inflate(inflater, container, false)
        setupTeamsGrid()
        setupRemoveButton()
        return binding.root
    }

    private fun setupTeamsGrid() {
        adapter = IplTeamSelectorAdapter(
            selectedTeam = currentTeam,
            onTeamSelected = { team ->
                onTeamSelected(team)
                dismiss()
            }
        )

        binding.rvTeams.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvTeams.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val spacing = (8 * resources.displayMetrics.density).toInt()
                outRect.set(spacing, spacing, spacing, spacing)
            }
        })
        binding.rvTeams.adapter = adapter
    }

    private fun setupRemoveButton() {
        binding.btnRemoveTeam.visibility = if (currentTeam != null) View.VISIBLE else View.GONE

        binding.btnRemoveTeam.setOnClickListener {
            onTeamSelected(null)
            dismiss()
        }
    }
}
