package com.gmwapp.hima.adapters

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.UserProfileDetailActivity
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.AdapterCoinBinding
import com.gmwapp.hima.databinding.AdapterRecentCallsBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.responses.CallsListResponse
import com.gmwapp.hima.retrofit.responses.CallsListResponseData
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponseData
import com.gmwapp.hima.retrofit.responses.TransactionsResponseData
import com.gmwapp.hima.utils.CallUnavailableFeedback
import com.gmwapp.hima.utils.setOnSingleClickListener
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone


class RecentCallsAdapter(
    val activity: Activity,
    private val callList: ArrayList<CallsListResponseData>,
    val onAudioListener: OnItemSelectionListener<CallsListResponseData>,
    val onVideoListener: OnItemSelectionListener<CallsListResponseData>,
    val isFavouriteMode: Boolean = false,
    val apiManager: ApiManager? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var currentFilter: String = "recent"

    fun setFilter(filter: String) {
        currentFilter = filter
        notifyDataSetChanged()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemHolder = ItemHolder(
            AdapterRecentCallsBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        return itemHolder
    }

    override fun onBindViewHolder(holderParent: RecyclerView.ViewHolder, position: Int) {
        val holder: ItemHolder = holderParent as ItemHolder
        val call: CallsListResponseData = callList[position]
        Glide.with(activity).load(call.image).apply(
            RequestOptions().circleCrop()
        ).into(holder.binding.ivImage)

        holder.binding.ivAudioCircle.setOnClickListener(null)
        holder.binding.ivVideoCircle.setOnClickListener(null)
        // Reset Views before applying new data
        holder.binding.ivAudioCircle.visibility = View.GONE
        holder.binding.ivVideoCircle.visibility = View.GONE
        holder.binding.ivAudio.visibility = View.GONE
        holder.binding.ivVideo.visibility = View.GONE
        holder.binding.tvAmount.visibility = View.GONE

        // Reset button colors
        holder.binding.ivAudioCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
        holder.binding.ivVideoCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
        
        holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
        holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))

        holder.binding.ivAudio.isEnabled = false
        holder.binding.ivVideo.isEnabled = false



        // Remove numbers from name - show only alphabets
        val nameWithoutNumbers = call.name.replace(Regex("[0-9]"), "")
        holder.binding.tvName.text = nameWithoutNumbers
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        if (userData?.gender == DConstants.MALE) {
            // Male users calling female users
            holder.binding.ivAudioCircle.visibility = View.VISIBLE
            holder.binding.ivVideoCircle.visibility = View.VISIBLE
            holder.binding.ivAudio.visibility = View.VISIBLE
            holder.binding.ivVideo.visibility = View.VISIBLE
            holder.binding.tvAmount.visibility = View.GONE

            if (call.audio_status == 0) {
                holder.binding.ivAudioCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
                holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
                holder.binding.ivAudio.isEnabled = false
                holder.binding.ivAudio.setImageResource(R.drawable.ic_phone_modern)
                holder.binding.ivAudioCircle.setOnSingleClickListener {
                    CallUnavailableFeedback.show(activity, holder.binding.root, forAudio = true)
                }
            }else{
                holder.binding.ivAudioCircle.setOnSingleClickListener{
                    onAudioListener.onItemSelected(call)
                }
                holder.binding.ivAudioCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.colorAccent))
                holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.white))
                holder.binding.ivAudio.isEnabled = true
                holder.binding.ivAudio.setImageResource(R.drawable.ic_phone_modern)
            }
            if (call.video_status == 0) {
                holder.binding.ivVideoCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
                holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
                holder.binding.ivVideo.isEnabled = false
                holder.binding.ivVideo.setImageResource(R.drawable.ic_video_modern)
                holder.binding.ivVideoCircle.setOnSingleClickListener {
                    CallUnavailableFeedback.show(activity, holder.binding.root, forAudio = false)
                }
            }else{
                holder.binding.ivVideoCircle.setOnSingleClickListener{ onVideoListener.onItemSelected(call) }
                holder.binding.ivVideoCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.green))
                holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.white))
                holder.binding.ivVideo.isEnabled = true
                holder.binding.ivVideo.setImageResource(R.drawable.ic_video_modern)
            }
            // Chat button always enabled
            holder.binding.ivChatCircle.setOnSingleClickListener {
                val intent = android.content.Intent(activity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java)
                intent.putExtra("USER_ID", call.id)
                intent.putExtra("USER_NAME", call.name)
                intent.putExtra("USER_IMAGE", call.image)
                activity.startActivity(intent)
            }
        } else {
            // Female/Creator users - show BOTH call buttons AND earnings
            holder.binding.ivAudioCircle.visibility = View.VISIBLE
            holder.binding.ivVideoCircle.visibility = View.VISIBLE
            holder.binding.ivAudio.visibility = View.VISIBLE
            holder.binding.ivVideo.visibility = View.VISIBLE
            holder.binding.tvAmount.visibility = View.VISIBLE // Show earnings
            holder.binding.tvAmount.text = activity.getString(R.string.rupee_text, call.income)

            // ✅ Audio call button - check status for creators
            if (call.audio_status == 0) {
                holder.binding.ivAudioCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
                holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
                holder.binding.ivAudio.isEnabled = false
                holder.binding.ivAudio.setImageResource(R.drawable.ic_phone_modern)
            } else {
                holder.binding.ivAudioCircle.setOnSingleClickListener{
                    onAudioListener.onItemSelected(call)
                }
                holder.binding.ivAudioCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.colorAccent))
                holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.white))
                holder.binding.ivAudio.isEnabled = true
                holder.binding.ivAudio.setImageResource(R.drawable.ic_phone_modern)
            }

            // ✅ Video call button - check status for creators
            if (call.video_status == 0) {
                holder.binding.ivVideoCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
                holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
                holder.binding.ivVideo.isEnabled = false
                holder.binding.ivVideo.setImageResource(R.drawable.ic_video_modern)
            } else {
                holder.binding.ivVideoCircle.setOnSingleClickListener{ onVideoListener.onItemSelected(call) }
                holder.binding.ivVideoCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.green))
                holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.white))
                holder.binding.ivVideo.isEnabled = true
                holder.binding.ivVideo.setImageResource(R.drawable.ic_video_modern)
            }
            holder.binding.ivVideoCircle.visibility= View.GONE

            // Chat button (design only)
            holder.binding.ivChatCircle.setOnSingleClickListener {
                val intent = android.content.Intent(activity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java)
                intent.putExtra("USER_ID", call.id)
                intent.putExtra("USER_NAME", call.name)
                intent.putExtra("USER_IMAGE", call.image)
                activity.startActivity(intent)
            }
        }
        // Hide duration and time for favorites mode
        if (isFavouriteMode) {
            holder.binding.main.setOnClickListener(null)
            holder.binding.cardChatNow.visibility = View.GONE
            holder.binding.cardChatNow.setOnClickListener(null)
            holder.binding.tvTime.visibility = View.GONE
            holder.binding.tvDuration.visibility = View.GONE
            holder.binding.llDuration.visibility = View.GONE
            
            // Show "Chat Now" text instead of language
            holder.binding.tvTime.visibility = View.VISIBLE
            holder.binding.tvTime.text = activity.getString(R.string.chat_now)
            holder.binding.tvTime.setBackgroundResource(R.drawable.language_tag_background)
            holder.binding.tvTime.setTextColor(ContextCompat.getColor(activity, R.color.colorAccent))
            holder.binding.tvTime.textSize = 10f
            val padding = (8 * activity.resources.displayMetrics.density).toInt()
            holder.binding.tvTime.setPadding(padding, padding / 2, padding, padding / 2)
            
            // Make "Chat Now" clickable - open chat directly (no friend gating).
            holder.binding.tvTime.setOnSingleClickListener {
                openChatActivity(call)
            }
        } else {
            holder.binding.tvTime.visibility = View.VISIBLE
            holder.binding.tvDuration.visibility = View.VISIBLE
            holder.binding.llDuration.visibility = View.VISIBLE
            holder.binding.tvTime.text = call.started_time ?: ""
            val rawDuration = call.duration?.trim().orEmpty()
            val isMissedRow = rawDuration.isEmpty() || parseDuration(rawDuration) <= 0
            // Missed calls come back from the backend with an empty or 0 duration,
            // which left the clock-icon row visually empty. Show "Missed call"
            // instead. For connected calls, roll 60+ minutes into hours so
            // "1265 min 29 sec" renders as "21 hr 5 min 29 sec".
            holder.binding.tvDuration.text = if (isMissedRow) {
                activity.getString(R.string.missed_call_label)
            } else {
                formatDuration(rawDuration)
            }
            // CALL-102: missed rows use the dialer-convention red phone-with-
            // down-left-arrow icon (intrinsic red — clear tint so colorAccent
            // doesn't override). Connected rows reset to the grey-tinted clock
            // so a recycled holder doesn't carry the missed look over.
            if (isMissedRow) {
                holder.binding.ivDurationIcon.setImageResource(R.drawable.ic_missed_call)
                holder.binding.ivDurationIcon.imageTintList = null
            } else {
                holder.binding.ivDurationIcon.setImageResource(R.drawable.ic_clock)
                holder.binding.ivDurationIcon.imageTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(activity, R.color.colorAccent)
                    )
            }
            // Remove click listener for non-favorite mode
            holder.binding.tvTime.setOnClickListener(null)

            holder.binding.cardChatNow.visibility = View.GONE
            holder.binding.cardChatNow.setOnClickListener(null)
            holder.binding.main.setOnSingleClickListener {
                openChatActivity(call)
            }
        }

        Log.d("RecentCallUserName","${call.name}")


        val currentDate : String ? = call.date

        // Show the date if it's different from the last displayed date
        if (!currentDate.isNullOrEmpty()) {
            holder.binding.llDate.visibility = View.VISIBLE
            holder.binding.vDivider.visibility = View.GONE
            holder.binding.callDate.text= currentDate
        } else {
            holder.binding.llDate.visibility = View.GONE
            holder.binding.vDivider.visibility = View.VISIBLE

        }

        if (call.blocked==2){
            holder.binding.ivVideoCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
            holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
            holder.binding.ivVideo.isEnabled = false
            holder.binding.ivVideo.setImageResource(R.drawable.ic_video_modern)
            
            holder.binding.ivAudioCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
            holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
            holder.binding.ivAudio.isEnabled = false
            holder.binding.ivAudio.setImageResource(R.drawable.ic_phone_modern)
            
            holder.binding.ivAudioCircle.setOnSingleClickListener {
                CallUnavailableFeedback.showBlocked(activity, holder.binding.root)
            }

            holder.binding.ivVideoCircle.setOnSingleClickListener {
                CallUnavailableFeedback.showBlocked(activity, holder.binding.root)
            }

        }
        
        // Add click listener on profile container to open profile detail
        holder.binding.profileContainer.setOnSingleClickListener {
            val intent = Intent(activity, UserProfileDetailActivity::class.java).apply {
                putExtra(DConstants.USER_ID, call.id)
                putExtra("USER_NAME", call.name)
                putExtra("USER_IMAGE", call.image)
                putExtra("USER_LANGUAGE", call.language)
                putExtra("USER_INTERESTS", call.interests)
                putExtra("USER_ABOUT", call.describe_yourself)
                putExtra("USER_AGE", 0) // Not available in CallsListResponseData
                putExtra("AUDIO_STATUS", call.audio_status)
                putExtra("VIDEO_STATUS", call.video_status)
            }
            activity.startActivity(intent)
        }

    }



    override fun getItemCount(): Int {
        return callList.size
    }



    fun addData(newData: List<CallsListResponseData>) {
        if (isFavouriteMode) {
            // Merge and de-dupe by id: fixes (1) duplicates inside one API payload, (2) overlapping responses / races
            val merged = (callList + newData).distinctBy { it.id }
            if (merged.size == callList.size) return
            callList.clear()
            callList.addAll(merged)
            notifyDataSetChanged()
            return
        }
        val start = callList.size
        callList.addAll(newData)
        notifyItemRangeInserted(start, newData.size)
    }

    fun clearData() {
        callList.clear()

        notifyDataSetChanged()
    }

    fun sortByRecent() {
        // Sort by date and started_time (most recent first)
        callList.sortWith(compareByDescending<CallsListResponseData> { 
            parseDateString(it.date)
        }.thenByDescending { 
            parseTimeString(it.started_time)
        })
        notifyDataSetChanged()
    }

    fun sortByTalkTime() {
        // Sort by duration (longest first)
        callList.sortByDescending { parseDuration(it.duration) }
        notifyDataSetChanged()
    }

    fun sortByName() {
        // Sort by name alphabetically (A-Z)
        callList.sortBy { it.name.lowercase() }
        notifyDataSetChanged()
    }

    private fun parseDateString(dateStr: String): Long {
        return try {
            if (dateStr.isNullOrEmpty()) return 0L
            
            // Try different date formats
            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()),
                SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()),
                SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()),
                SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            )
            
            for (format in formats) {
                try {
                    return format.parse(dateStr)?.time ?: 0L
                } catch (e: Exception) {
                    continue
                }
            }
            0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseTimeString(timeStr: String): Long {
        return try {
            if (timeStr.isNullOrEmpty()) return 0L
            
            // Try different time formats
            val formats = listOf(
                SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()),
                SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()),
                SimpleDateFormat("HH:mm", java.util.Locale.getDefault()),
                SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
            )
            
            for (format in formats) {
                try {
                    return format.parse(timeStr)?.time ?: 0L
                } catch (e: Exception) {
                    continue
                }
            }
            0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseDuration(durationStr: String): Int {
        return try {
            // Extract numbers from duration string
            // Handles formats like "5 min", "5min", "5", etc.
            val numericValue = durationStr.replace("[^0-9]".toRegex(), "")
            if (numericValue.isEmpty()) 0 else numericValue.toInt()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Backend sends durations as "X min Y sec" (e.g. "1265 min 29 sec"),
     * which is unreadable once we cross an hour. Normalize so 60+ min
     * rolls up into "H hr M min S sec", dropping zero parts.
     */
    private fun formatDuration(rawDuration: String): String {
        val trimmed = rawDuration.trim()
        if (trimmed.isEmpty()) return trimmed

        val pattern = Regex(
            "^(\\d+)\\s*min(?:ute)?s?(?:\\s+(\\d+)\\s*sec(?:ond)?s?)?$",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(trimmed) ?: return trimmed

        val totalMinutes = match.groupValues[1].toIntOrNull() ?: return trimmed
        val seconds = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 -> buildString {
                append(hours).append(" hr")
                if (minutes > 0) append(' ').append(minutes).append(" min")
                if (seconds > 0) append(' ').append(seconds).append(" sec")
            }
            minutes > 0 -> buildString {
                append(minutes).append(" min")
                if (seconds > 0) append(' ').append(seconds).append(" sec")
            }
            else -> "$seconds sec"
        }
    }

    private fun openChatActivity(call: CallsListResponseData) {
        val intent = Intent(activity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java)
        intent.putExtra("USER_ID", call.id)
        intent.putExtra("USER_NAME", call.name)
        intent.putExtra("USER_IMAGE", call.image)
        activity.startActivity(intent)
    }

    internal class ItemHolder(val binding: AdapterRecentCallsBinding) :
        RecyclerView.ViewHolder(binding.root)


}
