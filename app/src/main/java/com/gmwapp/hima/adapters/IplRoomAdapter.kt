package com.gmwapp.hima.adapters

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.AdapterIplRoomBinding
import com.gmwapp.hima.models.IplRoom

class IplRoomAdapter(
    val activity: Activity,
    private var rooms: MutableList<IplRoom>,
    private val onJoinListener: OnItemSelectionListener<IplRoom>
) : RecyclerView.Adapter<IplRoomAdapter.RoomViewHolder>() {

    inner class RoomViewHolder(val binding: AdapterIplRoomBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(room: IplRoom) {
            // Team A circle
            val teamABg = binding.viewTeamABg.background.mutate() as GradientDrawable
            teamABg.setColor(Color.parseColor(room.teamA.primaryColor))
            binding.tvTeamAAbbr.text = room.teamA.abbreviation

            // Team B circle
            val teamBBg = binding.viewTeamBBg.background.mutate() as GradientDrawable
            teamBBg.setColor(Color.parseColor(room.teamB.primaryColor))
            binding.tvTeamBAbbr.text = room.teamB.abbreviation

            // Room name
            binding.tvRoomName.text = room.name

            // Creator info
            binding.tvRoomInfo.text = activity.getString(R.string.created_by, room.creatorName)

            // Member count in badge row
            binding.tvMemberCount.text = "${room.memberCount}/${room.maxMembers} joined"

            // LIVE badge
            binding.tvLiveBadge.visibility = if (room.isLive) View.VISIBLE else View.GONE

            // Join button — always enabled since users enter as listeners first
            binding.btnJoin.isEnabled = true
            binding.btnJoin.text = activity.getString(R.string.join_room)
            binding.btnJoin.setBackgroundResource(R.drawable.bg_fantasy_join_btn)
            binding.btnJoin.setTextColor(
                ContextCompat.getColor(activity, R.color.white)
            )
            binding.btnJoin.alpha = 1.0f

            binding.btnJoin.setOnClickListener {
                onJoinListener.onItemSelected(room)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val binding = AdapterIplRoomBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RoomViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(rooms[position])
    }

    override fun getItemCount(): Int = rooms.size

    fun updateRooms(newRooms: List<IplRoom>) {
        rooms.clear()
        rooms.addAll(newRooms)
        notifyDataSetChanged()
    }
}
