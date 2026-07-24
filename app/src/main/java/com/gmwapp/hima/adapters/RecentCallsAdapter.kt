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
            // Per-call earnings pill, centred under the call buttons.
            holder.binding.tvAmount.visibility = View.VISIBLE
            // F1: show the duration bonus inline in the same pill, e.g. "₹6 +₹3"
            // (base green, bonus gold). No bonus → just the base amount.
            val baseAmount = activity.getString(R.string.rupee_text, call.income)
            val callBonus = call.bonus ?: 0
            if (callBonus > 0) {
                val bonusPart = "  +₹$callBonus"
                val full = android.text.SpannableString(baseAmount + bonusPart)
                full.setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#C08A00")),
                    baseAmount.length, full.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                holder.binding.tvAmount.text = full
            } else {
                holder.binding.tvAmount.text = baseAmount
            }

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
                // Unfilled look: light-pink circle with a pink phone icon.
                holder.binding.ivAudioCircle.setCardBackgroundColor(android.graphics.Color.parseColor("#FCE3EE"))
                holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.colorAccent))
                holder.binding.ivAudio.isEnabled = true
                holder.binding.ivAudio.setImageResource(R.drawable.ic_phone_modern)
            }

            // Video button repurposed as a CHAT button on female/creator cards.
            // Tapping opens the in-house chat page with this user (no video call).
            holder.binding.ivVideoCircle.visibility = View.VISIBLE
            holder.binding.ivVideo.setImageResource(R.drawable.ic_chat_bubble)
            holder.binding.ivVideoCircle.setCardBackgroundColor(android.graphics.Color.parseColor("#FCE3EE"))
            holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.colorAccent))
            holder.binding.ivVideo.isEnabled = true
            holder.binding.tvVideoStatus.text = "Chat"
            holder.binding.ivVideoCoin.visibility = View.GONE
            holder.binding.ivVideoCircle.setOnSingleClickListener {
                val intent = android.content.Intent(activity, com.gmwapp.hima.activities.ChatActivityInHouse::class.java)
                intent.putExtra("USER_ID", call.id)
                intent.putExtra("USER_NAME", call.name)
                intent.putExtra("USER_IMAGE", call.image)
                activity.startActivity(intent)
            }

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
            // For connected calls, roll 60+ minutes into hours so "1265 min 29 sec"
            // renders as "21 hr 5 min 29 sec".
            // FI_06: a declined call ("rejected") is distinct from an unanswered "missed".
            // Check end_reason first so a rejected row (also empty-duration) isn't mislabeled.
            // Rejected rows are filtered out of All server-side; the branch stays as a guard.
            holder.binding.tvDuration.text = when {
                call.end_reason == "rejected" -> activity.getString(R.string.rejected_call_label)
                // "Missed" = never connected. NO single field can decide it:
                //   * started_time is NOT the DB column — calls_list overwrites it with a
                //     display label ("Today 01:52 PM"), so it is never empty. Testing it
                //     makes this branch dead code and every miss renders "0 sec".
                //   * duration alone: blank also for calls that DID connect but were
                //     orphaned/underbilled (ended_time '00:00:00') — QA bug #10.
                //   * end_reason alone: rows carry a real started_time together with
                //     'not_answered' (Talk Time filters them out for this reason).
                // So: no billed span AND the backend did not record a real ending.
                // A blank end_reason must count — a caller who abandons the ring leaves
                // the row unstamped, and on prod that is ~34% of all misses (56k/day);
                // requiring 'not_answered' alone would render those "0 sec". An orphaned
                // connected row is stamped 'ended', so it stays out of this branch.
                (call.end_reason.isNullOrBlank() || call.end_reason == "not_answered") &&
                    rawDuration.isEmpty() ->
                    activity.getString(R.string.missed_call_label)
                else -> formatDuration(rawDuration).ifEmpty { "0 sec" }
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

        // B_005 — grey both call buttons when EITHER party blocked the other (either
        // direction). blocked==2 is the legacy creator-blocked-me case; either_blocked
        // adds male-blocked-creator and the female-viewer cases. The profile-view gate
        // below stays on blocked==2 only, so blocking someone doesn't hide their card.
        //
        // B_003 — otherwise, grey the CALL button(s) when the peer being called is on
        // DND. The peer is the opposite party for this row: a male viewer calls a
        // female (read female_dnd), a female viewer calls a male (read male_dnd).
        // Block takes precedence over DND (stronger + more accurate message). DND mutes
        // CALLS only, so the female-side chat button (the repurposed video circle) is
        // left untouched — only the male path greys the video (a real video call).
        // Inert until calls_list returns male_dnd/female_dnd (both default false).
        val peerDnd = if (userData?.gender == DConstants.MALE) call.female_dnd else call.male_dnd
        if (call.blocked==2 || call.either_blocked){
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

        } else if (peerDnd) {
            // Grey the audio call button on both sides.
            holder.binding.ivAudioCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
            holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
            holder.binding.ivAudio.isEnabled = false
            holder.binding.ivAudio.setImageResource(R.drawable.ic_phone_modern)
            holder.binding.ivAudioCircle.setOnSingleClickListener {
                CallUnavailableFeedback.showDnd(activity, holder.binding.root)
            }
            // Male viewer: the video circle is a real video call — grey it too.
            // Female viewer: the video circle is the Chat button (calls-only DND
            // doesn't block chat), so leave it enabled.
            if (userData?.gender == DConstants.MALE) {
                holder.binding.ivVideoCircle.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.grey_extra_light))
                holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
                holder.binding.ivVideo.isEnabled = false
                holder.binding.ivVideo.setImageResource(R.drawable.ic_video_modern)
                holder.binding.ivVideoCircle.setOnSingleClickListener {
                    CallUnavailableFeedback.showDnd(activity, holder.binding.root)
                }
            }
        }
        
        // "Audio" / "Video" coin labels under each call button. Text is static
        // (set in XML); here we only keep each label row + the divider in sync
        // with its button's visibility (so a hidden video button hides its
        // label row and the divider too).
        holder.binding.llAudioLabel.visibility = holder.binding.ivAudioCircle.visibility
        holder.binding.llVideoLabel.visibility = holder.binding.ivVideoCircle.visibility
        holder.binding.vBtnDivider.visibility =
            if (holder.binding.ivAudioCircle.visibility == View.VISIBLE &&
                holder.binding.ivVideoCircle.visibility == View.VISIBLE
            ) View.VISIBLE else View.GONE

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

    /**
     * B_017 — replace the list in place with a minimal DiffUtil update instead of
     * clear()+addData(). The silent post-call refresh (RecentFragment's ~1.5s
     * deferred re-query) uses this so the list never blanks to empty and refills —
     * only the rows that actually changed rebind, eliminating the visible flicker.
     *
     * Row identity is (peer id + date + started_time): `id` alone is the PEER user
     * id, not a per-call key (the list is one row per call, so a peer can appear
     * more than once). An imperfect key at worst causes a few extra rebinds — never
     * a blank flash — so this is strictly safer than the old clear+refill.
     */
    fun setData(newData: List<CallsListResponseData>) {
        val old = ArrayList(callList)
        val diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(
            object : androidx.recyclerview.widget.DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = newData.size
                override fun areItemsTheSame(o: Int, n: Int): Boolean {
                    val a = old[o]; val b = newData[n]
                    return a.id == b.id && a.date == b.date && a.started_time == b.started_time
                }
                override fun areContentsTheSame(o: Int, n: Int): Boolean = old[o] == newData[n]
            }
        )
        callList.clear()
        callList.addAll(newData)
        diff.dispatchUpdatesTo(this)
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

        // B_005 — grey the callback buttons when EITHER party blocked the other (any
        // direction). blocked==2 is the legacy creator-blocked-me case; either_blocked
        // adds the male-blocked-creator case (the screenshot: male blocks the creator,
        // which sets either_blocked but NOT blocked==2, so the favourite card stayed lit).
        // Mirrors the Recent path. The profile-view gate below stays on blocked==2 only,
        // so blocking someone doesn't hide their card.
        val callBlocked = call.blocked == 2 || call.either_blocked
        val blocked = call.blocked == 2
        // B_003 — Favourite is male-only (4-tab nav), so the peer is always a female
        // creator; read female_dnd. On DND, treat the callback buttons as unavailable.
        val peerDnd = call.female_dnd
        configureFavouriteButton(
            online = call.audio_status == 1 && !callBlocked && !peerDnd, blocked = callBlocked, dnd = peerDnd, forAudio = true,
            circle = b.flAudio, icon = b.ivAudio, rateRow = b.llAudioRate, statusLabel = b.tvAudioStatus,
            onlineCircleBg = R.drawable.circle_bg_pink_light, onlineIconTint = R.color.colorAccent,
            root = b.root, call = call
        )
        configureFavouriteButton(
            online = call.video_status == 1 && !callBlocked && !peerDnd, blocked = callBlocked, dnd = peerDnd, forAudio = false,
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
        online: Boolean, blocked: Boolean, dnd: Boolean = false, forAudio: Boolean,
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
                when {
                    blocked -> CallUnavailableFeedback.showBlocked(activity, root)
                    dnd -> CallUnavailableFeedback.showDnd(activity, root)          // B_003
                    else -> CallUnavailableFeedback.show(activity, root, forAudio = forAudio)
                }
            }
        }
    }

    internal class FavouriteHolder(val binding: AdapterFavouriteCallsBinding) :
        RecyclerView.ViewHolder(binding.root)

    internal class ItemHolder(val binding: AdapterRecentCallsBinding) :
        RecyclerView.ViewHolder(binding.root)


}
