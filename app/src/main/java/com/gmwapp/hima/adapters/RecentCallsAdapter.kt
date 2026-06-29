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
import com.gmwapp.hima.databinding.AdapterFavouriteCallsBinding
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
        // Favourite tab uses its own clean card (adapter_favourite_calls.xml). Recent is unaffected.
        if (isFavouriteMode) {
            return FavouriteHolder(
                AdapterFavouriteCallsBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }
        val itemHolder = ItemHolder(
            AdapterRecentCallsBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        return itemHolder
    }

    override fun onBindViewHolder(holderParent: RecyclerView.ViewHolder, position: Int) {
        val call: CallsListResponseData = callList[position]
        if (holderParent is FavouriteHolder) {
            bindFavourite(holderParent, call)
            return
        }
        val holder: ItemHolder = holderParent as ItemHolder
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

            // Female viewer calling back a male — audio_status / video_status
            // on the male peer is always 0 (no male UI to opt in, no seed in
            // register()), so the old gate disabled every callback button.
            // Treat male peers as always available (mirrors B080 in
            // FriendsTabFragment + the chat-screen fix). The backend still
            // rejects upstream if the male is deleted, blocked, or busy.
            if (call.call_blocked == true) {
                // FEMALE_3_REJECT_BLOCK — she auto-blocked this male (3 rejects/5min);
                // grey the callback button for the 60-min cooldown.
                holder.binding.ivAudioCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
                holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
                holder.binding.ivAudio.isEnabled = false
                holder.binding.ivAudio.setImageResource(R.drawable.ic_phone_modern)
                holder.binding.ivAudioCircle.setOnSingleClickListener {
                    CallUnavailableFeedback.show(activity, holder.binding.root, forAudio = true)
                }
            } else {
                holder.binding.ivAudioCircle.setOnSingleClickListener {
                    onAudioListener.onItemSelected(call)
                }
                holder.binding.ivAudioCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.colorAccent))
                holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.white))
                holder.binding.ivAudio.isEnabled = true
                holder.binding.ivAudio.setImageResource(R.drawable.ic_phone_modern)
            }

            holder.binding.ivVideoCircle.setOnSingleClickListener { onVideoListener.onItemSelected(call) }
            holder.binding.ivVideoCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.green))
            holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.white))
            holder.binding.ivVideo.isEnabled = true
            holder.binding.ivVideo.setImageResource(R.drawable.ic_video_modern)
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
            // Missed calls come back from the backend with an empty or 0 duration,
            // which left the clock-icon row visually empty. Show "Missed" instead.
            // For connected calls, roll 60+ minutes into hours so "1265 min 29 sec"
            // renders as "21 hr 5 min 29 sec".
            // FI_06: a declined call ("rejected") is distinct from an unanswered "missed".
            // Check end_reason first so a rejected row (also empty-duration) isn't mislabeled.
            holder.binding.tvDuration.text = when {
                call.end_reason == "rejected" -> activity.getString(R.string.rejected_call_label)
                rawDuration.isEmpty() || parseDuration(rawDuration) <= 0 ->
                    activity.getString(R.string.missed_call_label)
                else -> formatDuration(rawDuration)
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
        
        // Online / Offline labels under each call button — derived from the
        // final button availability set by all the branches above (and matched
        // to each button's visibility, so a hidden video button hides its label
        // and the divider).
        val onlineColor = ContextCompat.getColor(activity, R.color.green)
        val offlineColor = ContextCompat.getColor(activity, R.color.grey_medium)

        holder.binding.tvAudioStatus.visibility = holder.binding.ivAudioCircle.visibility
        if (holder.binding.ivAudio.isEnabled) {
            holder.binding.tvAudioStatus.text = activity.getString(R.string.call_status_online)
            holder.binding.tvAudioStatus.setTextColor(onlineColor)
        } else {
            holder.binding.tvAudioStatus.text = activity.getString(R.string.call_status_offline)
            holder.binding.tvAudioStatus.setTextColor(offlineColor)
        }

        holder.binding.tvVideoStatus.visibility = holder.binding.ivVideoCircle.visibility
        holder.binding.vBtnDivider.visibility =
            if (holder.binding.ivVideoCircle.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        if (holder.binding.ivVideo.isEnabled) {
            holder.binding.tvVideoStatus.text = activity.getString(R.string.call_status_online)
            holder.binding.tvVideoStatus.setTextColor(onlineColor)
        } else {
            holder.binding.tvVideoStatus.text = activity.getString(R.string.call_status_offline)
            holder.binding.tvVideoStatus.setTextColor(offlineColor)
        }

        // Add click listener on profile container to open profile detail
        holder.binding.profileContainer.setOnSingleClickListener {
            // TC_027: a creator who has blocked this user (blocked==2) must not be
            // viewable. Match the call-button gating above — show the blocked feedback
            // instead of opening her profile, so the blocked user can't view the card.
            if (call.blocked == 2) {
                CallUnavailableFeedback.showBlocked(activity, holder.binding.root)
                return@setOnSingleClickListener
            }
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
        // NOTE: do NOT de-dupe the recent list by `id` — here `id` is the PEER user id
        // (call_user_id/user_id), not a per-call key, and the list is one row per call
        // (backend calls_list has no GROUP BY). Deduping by id would collapse legitimate
        // repeat calls to the same person. The duplicate-on-first-open bug is fixed at the
        // source instead: RecentFragment guards its onResume reload with !isLoading so it
        // no longer races initUI's page-0 fetch.
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

    /**
     * Favourite-tab card binding. Online => tinted circle (pink audio / purple video) + coin +
     * per-min rate; Offline => grey circle + "Offline". Rates are the app-wide defaults
     * (audio 10/min, video 60/min) since CallsListResponseData carries no per-contact price.
     * Favourite is male-only (4-tab nav), so this only handles the male caller path.
     */
    private fun bindFavourite(holder: FavouriteHolder, call: CallsListResponseData) {
        val b = holder.binding
        Glide.with(activity).load(call.image)
            .apply(RequestOptions().circleCrop()).into(b.ivImage)
        b.tvName.text = call.name.replace(Regex("[0-9]"), "")

        // Chat Now -> open chat directly (no friend gating, mirrors prior favourite behavior)
        b.cardChatNow.setOnSingleClickListener { openChatActivity(call) }

        val blocked = call.blocked == 2
        configureFavouriteButton(
            online = call.audio_status == 1 && !blocked, blocked = blocked, forAudio = true,
            circle = b.flAudio, icon = b.ivAudio, rateRow = b.llAudioRate, statusLabel = b.tvAudioStatus,
            onlineCircleBg = R.drawable.circle_bg_pink_light, onlineIconTint = R.color.colorAccent,
            root = b.root, call = call
        )
        configureFavouriteButton(
            online = call.video_status == 1 && !blocked, blocked = blocked, forAudio = false,
            circle = b.flVideo, icon = b.ivVideo, rateRow = b.llVideoRate, statusLabel = b.tvVideoStatus,
            onlineCircleBg = R.drawable.circle_bg_purple_light, onlineIconTint = R.color.purple,
            root = b.root, call = call
        )

        b.profileContainer.setOnSingleClickListener {
            if (blocked) {
                CallUnavailableFeedback.showBlocked(activity, b.root)
                return@setOnSingleClickListener
            }
            val intent = Intent(activity, UserProfileDetailActivity::class.java).apply {
                putExtra(DConstants.USER_ID, call.id)
                putExtra("USER_NAME", call.name)
                putExtra("USER_IMAGE", call.image)
                putExtra("USER_LANGUAGE", call.language)
                putExtra("USER_INTERESTS", call.interests)
                putExtra("USER_ABOUT", call.describe_yourself)
                putExtra("USER_AGE", 0)
                putExtra("AUDIO_STATUS", call.audio_status)
                putExtra("VIDEO_STATUS", call.video_status)
            }
            activity.startActivity(intent)
        }
    }

    private fun configureFavouriteButton(
        online: Boolean, blocked: Boolean, forAudio: Boolean,
        circle: View, icon: android.widget.ImageView, rateRow: View, statusLabel: View,
        onlineCircleBg: Int, onlineIconTint: Int, root: View, call: CallsListResponseData
    ) {
        if (online) {
            circle.setBackgroundResource(onlineCircleBg)
            icon.setColorFilter(ContextCompat.getColor(activity, onlineIconTint))
            rateRow.visibility = View.VISIBLE
            statusLabel.visibility = View.GONE
            circle.setOnSingleClickListener {
                if (forAudio) onAudioListener.onItemSelected(call) else onVideoListener.onItemSelected(call)
            }
        } else {
            circle.setBackgroundResource(R.drawable.circle_bg_grey)
            icon.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
            rateRow.visibility = View.GONE
            statusLabel.visibility = View.VISIBLE
            circle.setOnSingleClickListener {
                if (blocked) CallUnavailableFeedback.showBlocked(activity, root)
                else CallUnavailableFeedback.show(activity, root, forAudio = forAudio)
            }
        }
    }

    internal class FavouriteHolder(val binding: AdapterFavouriteCallsBinding) :
        RecyclerView.ViewHolder(binding.root)

    internal class ItemHolder(val binding: AdapterRecentCallsBinding) :
        RecyclerView.ViewHolder(binding.root)


}
