package com.gmwapp.hima.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.models.IplTeam

class IplTeamSelectorAdapter(
    private val teams: Array<IplTeam> = IplTeam.values(),
    private var selectedTeam: IplTeam? = null,
    private val onTeamSelected: (IplTeam) -> Unit
) : RecyclerView.Adapter<IplTeamSelectorAdapter.TeamViewHolder>() {

    inner class TeamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: LinearLayout = itemView.findViewById(R.id.team_item_root)
        val colorCircle: View = itemView.findViewById(R.id.team_color_circle)
        val tvAbbreviation: TextView = itemView.findViewById(R.id.tv_team_abbreviation)
        val tvTeamName: TextView = itemView.findViewById(R.id.tv_team_name)
        val ivCheck: ImageView = itemView.findViewById(R.id.iv_selected_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ipl_team_selector, parent, false)
        return TeamViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
        val team = teams[position]
        val isSelected = team == selectedTeam

        // Set team color circle
        val circleDrawable = holder.colorCircle.background.mutate() as GradientDrawable
        circleDrawable.setColor(Color.parseColor(team.primaryColor))

        // Set text
        holder.tvAbbreviation.text = team.abbreviation
        holder.tvTeamName.text = team.teamName

        // Selected state
        if (isSelected) {
            holder.root.setBackgroundResource(R.drawable.bg_ipl_team_grid_item_selected)
            holder.ivCheck.visibility = View.VISIBLE
        } else {
            holder.root.setBackgroundResource(R.drawable.bg_ipl_team_grid_item)
            holder.ivCheck.visibility = View.GONE
        }

        holder.root.setOnClickListener {
            val previousSelected = selectedTeam
            selectedTeam = team
            onTeamSelected(team)

            // Refresh old and new selection
            if (previousSelected != null) {
                val oldPos = teams.indexOf(previousSelected)
                if (oldPos >= 0) notifyItemChanged(oldPos)
            }
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = teams.size

    fun clearSelection() {
        val oldPos = selectedTeam?.let { teams.indexOf(it) }
        selectedTeam = null
        if (oldPos != null && oldPos >= 0) notifyItemChanged(oldPos)
    }
}
