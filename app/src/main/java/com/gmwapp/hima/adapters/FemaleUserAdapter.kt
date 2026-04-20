package com.gmwapp.hima.adapters

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.ChatActivityInHouse
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.AdapterFemaleUserBinding
import com.gmwapp.hima.models.IplTeam
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponseData
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.utils.setOnSingleClickListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class FemaleUserAdapter(
    val activity: Activity,
    private var femaleUsers: List<FemaleUsersResponseData>,
    // Listeners kept to preserve constructor compatibility with HomeFragment.
    // No longer used by the chat-style row, but left in case any caller still needs them.
    val onAudioListener: OnItemSelectionListener<FemaleUsersResponseData>,
    val onVideoListener: OnItemSelectionListener<FemaleUsersResponseData>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ItemHolder(
            AdapterFemaleUserBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holderParent: RecyclerView.ViewHolder, position: Int) {
        val holder = holderParent as ItemHolder
        val femaleUser = femaleUsers[position]

        Glide.with(activity).load(femaleUser.image).into(holder.binding.ivProfile)

        // Online indicator: green dot if she's available for audio or video.
        val isOnline = femaleUser.audio_status == 1 || femaleUser.video_status == 1
        holder.binding.onlineIndicator.visibility = if (isOnline) View.VISIBLE else View.GONE

        // NEW badge — verified within last 3 days.
        val daysSinceVerified = if (!femaleUser.verified_datetime.isNullOrBlank()) {
            getDayDifferenceLabel(femaleUser.verified_datetime)
        } else {
            null
        }
        holder.binding.newUser.visibility =
            if (daysSinceVerified != null && daysSinceVerified < 3) View.VISIBLE else View.GONE

        // STAR badge.
        holder.binding.starBadge.visibility =
            if (femaleUser.is_star == 1 || femaleUser.star == 1) View.VISIBLE else View.GONE

        // Name (strip digits like the old layout did).
        val nameWithoutNumbers = femaleUser.name.replace(Regex("[0-9]"), "")
        holder.binding.tvName.text = nameWithoutNumbers

        // IPL team chip (top-right of name row). The whole chip is tinted with
        // the team's primary color so dark teams (MI, GT, KKR) stay visible.
        // Text color flips between white and black based on luminance for contrast.
        val iplTeamAbbr = femaleUser.ipl_team
        val iplTeam = if (!iplTeamAbbr.isNullOrEmpty()) {
            IplTeam.values().find { it.abbreviation == iplTeamAbbr }
        } else null
        if (iplTeam != null) {
            val teamColor = android.graphics.Color.parseColor(iplTeam.primaryColor)
            holder.binding.iplTeamBadgeCard.visibility = View.VISIBLE
            holder.binding.iplTeamBadgeCard.setCardBackgroundColor(teamColor)
            holder.binding.tvIplTeamAbbr.text = iplTeam.abbreviation
            val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(teamColor)
            val textColor = if (luminance > 0.55) android.graphics.Color.BLACK
            else android.graphics.Color.WHITE
            holder.binding.tvIplTeamAbbr.setTextColor(textColor)
        } else {
            holder.binding.iplTeamBadgeCard.visibility = View.GONE
        }

        // Last message / time row is hidden — no longer displayed on home screen.
        // Kept code commented for easy restoration if needed later.
        // val lastMessage = femaleUser.last_message
        // val hasMessage = lastMessage != null && lastMessage.message.isNotBlank()
        // if (hasMessage) { ... } else { ... }

        val unread = femaleUser.unread_count
        if (unread > 0) {
            holder.binding.tvUnreadCount.visibility = View.VISIBLE
            holder.binding.tvUnreadCount.text = if (unread > 99) "99+" else unread.toString()
        } else {
            holder.binding.tvUnreadCount.visibility = View.GONE
        }

        // Whole card click (profile) → open in-house chat with this female.
        val openChat: () -> Unit = {
            val intent = Intent(activity, ChatActivityInHouse::class.java).apply {
                putExtra("USER_ID", femaleUser.id)
                putExtra("USER_NAME", femaleUser.name)
                putExtra("USER_IMAGE", femaleUser.image)
                putExtra("AUDIO_STATUS", femaleUser.audio_status)
                putExtra("VIDEO_STATUS", femaleUser.video_status)
                putExtra("COIN_PER_MIN_AUDIO", femaleUser.coin_per_min_audio ?: 10)
                putExtra("COIN_PER_MIN_VIDEO", femaleUser.coin_per_min_video ?: 60)
                putExtra("USER_LANGUAGE", femaleUser.language)
                putExtra("USER_INTERESTS", femaleUser.interests)
                putExtra("USER_ABOUT", femaleUser.describe_yourself)
            }
            activity.startActivity(intent)
        }

        holder.binding.main.setOnSingleClickListener { openChat() }
        holder.binding.btnChat.setOnSingleClickListener { openChat() }

        // Audio call button — grey when unavailable (light grey bg, white icon/text still visible)
        val audioAvailable = femaleUser.audio_status == 1
        val audioColor = if (audioAvailable) {
            androidx.core.content.ContextCompat.getColor(activity, R.color.colorAccent)
        } else {
            android.graphics.Color.parseColor("#C4C4C4")
        }
        holder.binding.btnAudioCall.setCardBackgroundColor(audioColor)
        holder.binding.btnAudioCall.isEnabled = audioAvailable
        holder.binding.btnAudioCall.alpha = 1.0f
        holder.binding.btnAudioCall.setOnSingleClickListener {
            if (audioAvailable) onAudioListener.onItemSelected(femaleUser)
        }

        // Video call button — grey when unavailable
        val videoAvailable = femaleUser.video_status == 1
        val videoColor = if (videoAvailable) {
            android.graphics.Color.parseColor("#43A047")
        } else {
            android.graphics.Color.parseColor("#C4C4C4")
        }
        holder.binding.btnVideoCall.setCardBackgroundColor(videoColor)
        holder.binding.btnVideoCall.isEnabled = videoAvailable
        holder.binding.btnVideoCall.alpha = 1.0f
        holder.binding.btnVideoCall.setOnSingleClickListener {
            if (videoAvailable) onVideoListener.onItemSelected(femaleUser)
        }
    }

    override fun getItemCount(): Int = femaleUsers.size

    private fun getDayDifferenceLabel(createdAt: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val createdDateOnly = createdAt.substring(0, 10)
            val createdDate: Date = format.parse(createdDateOnly) ?: return 3
            val todayDate: Date = format.parse(format.format(Date())) ?: return 3
            val diffMillis = todayDate.time - createdDate.time
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)
            if (diffDays >= 0) diffDays else 10
        } catch (_: Exception) {
            10
        }
    }

    /**
     * Format like WhatsApp: "10:30 AM" if today, "Yesterday" if yesterday,
     * weekday name within the last week, else "MMM dd".
     */
    private fun formatChatTime(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = parser.parse(timestamp) ?: return ""

            val now = Calendar.getInstance()
            val msgCal = Calendar.getInstance().apply { time = date }

            val sameDay = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)

            val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val isYesterday = yesterdayCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                    yesterdayCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)

            val diffDays = TimeUnit.MILLISECONDS.toDays(now.timeInMillis - msgCal.timeInMillis)

            when {
                sameDay -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
                isYesterday -> "Yesterday"
                diffDays in 2..6 -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
                else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {
            ""
        }
    }

    internal class ItemHolder(val binding: AdapterFemaleUserBinding) :
        RecyclerView.ViewHolder(binding.root)
}
