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

        // IPL team chip (top-right of name row). Whole chip tinted with the
        // team's primary color; text color flips on luminance for contrast.
        // FeatureFlags.IPL_ENABLED gates this entirely so the chip never shows
        // while the feature is off, regardless of what the server sends.
        val iplTeamAbbr = femaleUser.ipl_team
        val iplTeam = if (com.gmwapp.hima.utils.FeatureFlags.IPL_ENABLED && !iplTeamAbbr.isNullOrEmpty()) {
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

        // Last message + time. Time always sits in the bottom row, beside the
        // unread badge — never on the name line.
        val lastMessage = femaleUser.last_message
        val hasMessage = lastMessage != null && lastMessage.message.isNotBlank()
        if (hasMessage) {
            holder.binding.tvLastMessage.visibility = View.VISIBLE
            holder.binding.tvLastMessage.text = lastMessage!!.message
            holder.binding.tvLastMessage.setTextColor(
                activity.resources.getColor(R.color.grey_medium, null)
            )
            holder.binding.tvTimeBottom.text = formatChatTime(lastMessage.timestamp)
            holder.binding.tvTimeBottom.visibility = View.VISIBLE
        } else {
            // No previous message — hide the "Tap to call and chat" hint.
            // The whole row is already clickable (opens chat), so no prompt needed.
            holder.binding.tvLastMessage.visibility = View.GONE
            holder.binding.tvTimeBottom.text = ""
            holder.binding.tvTimeBottom.visibility = View.GONE
        }

        val unread = femaleUser.unread_count
        if (unread > 0) {
            holder.binding.tvUnreadCount.visibility = View.VISIBLE
            holder.binding.tvUnreadCount.text = if (unread > 99) "99+" else unread.toString()
        } else {
            holder.binding.tvUnreadCount.visibility = View.GONE
        }

        // Whole row click → open in-house chat with this female.
        holder.binding.main.setOnSingleClickListener {
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

        // Audio & Video call buttons — always visible; show disabled state when
        // the creator isn't accepting that call type so the card layout stays
        // symmetric and users can see what options exist.
        // B095 — also disable when the creator is currently in another call.
        // `is_busy` is derived server-side from active call_attend rows. Old
        // backend builds that don't return the field default to 0 → no
        // behaviour change. New backend builds flip busy creators to
        // disabled so users don't waste taps on rows they can't actually
        // reach right now.
        val isBusy = (femaleUser.is_busy ?: 0) == 1
        val audioEnabled = femaleUser.audio_status == 1 && !isBusy
        val videoEnabled = femaleUser.video_status == 1 && !isBusy
        val disabledTextColor = android.graphics.Color.parseColor("#6B7280")
        val whiteColor = activity.resources.getColor(R.color.white, null)

        holder.binding.btnAudioCall.visibility = View.VISIBLE
        holder.binding.btnVideoCall.visibility = View.VISIBLE

        holder.binding.btnAudioCall.background = activity.resources.getDrawable(
            if (audioEnabled) R.drawable.button_audio_gradient else R.drawable.button_disabled_gradient,
            null
        )
        holder.binding.btnVideoCall.background = activity.resources.getDrawable(
            if (videoEnabled) R.drawable.button_video_gradient else R.drawable.button_disabled_gradient,
            null
        )

        holder.binding.btnAudioCall.isEnabled = audioEnabled
        holder.binding.btnVideoCall.isEnabled = videoEnabled
        holder.binding.btnAudioCall.isClickable = audioEnabled
        holder.binding.btnVideoCall.isClickable = videoEnabled
        holder.binding.btnAudioCall.alpha = if (audioEnabled) 1f else 1f
        holder.binding.btnVideoCall.alpha = if (videoEnabled) 1f else 1f

        // Icons & text — use white on color gradients, muted grey on disabled bg.
        holder.binding.ivAudioIcon.setColorFilter(if (audioEnabled) whiteColor else disabledTextColor)
        holder.binding.ivVideoIcon.setColorFilter(if (videoEnabled) whiteColor else disabledTextColor)
        holder.binding.tvAudioRate.setTextColor(if (audioEnabled) whiteColor else disabledTextColor)
        holder.binding.tvVideoRate.setTextColor(if (videoEnabled) whiteColor else disabledTextColor)
        holder.binding.ivAudioCoin.visibility = if (audioEnabled) View.VISIBLE else View.GONE
        holder.binding.ivVideoCoin.visibility = if (videoEnabled) View.VISIBLE else View.GONE

        holder.binding.tvAudioRate.text = if (audioEnabled)
            "${femaleUser.coin_per_min_audio ?: 10}/min" else "Unavailable"
        holder.binding.tvVideoRate.text = if (videoEnabled)
            "${femaleUser.coin_per_min_video ?: 60}/min" else "Unavailable"

        holder.binding.btnAudioCall.setOnSingleClickListener {
            if (audioEnabled) launchCall(femaleUser, "audio")
        }
        holder.binding.btnVideoCall.setOnSingleClickListener {
            if (videoEnabled) launchCall(femaleUser, "video")
        }
    }

    private fun launchCall(femaleUser: FemaleUsersResponseData, callType: String) {
        val intent = Intent(activity, MaleCallConnectingActivity::class.java).apply {
            putExtra(DConstants.CALL_TYPE, callType)
            putExtra(DConstants.RECEIVER_ID, femaleUser.id)
            putExtra(DConstants.RECEIVER_NAME, femaleUser.name)
            putExtra(DConstants.CALL_ID, 0)
            putExtra(DConstants.IMAGE, femaleUser.image)
            putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
            putExtra(
                DConstants.TEXT,
                activity.getString(R.string.wait_user_hint, femaleUser.name)
            )
        }
        FcmUtils.isUserAvailable = 1
        activity.startActivity(intent)
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
