package com.gmwapp.hima.adapters

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ItemChatConversationBinding
import com.gmwapp.hima.models.ChatConversation
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FemaleNotificationPreferenceResponse
import com.gmwapp.hima.utils.NotifyOnlinePrefsHelper
import com.gmwapp.hima.utils.PinnedChatsPrefsHelper
import com.gmwapp.hima.utils.setOnSingleClickListener
import retrofit2.Call
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ChatListAdapter(
    private val activity: Activity,
    private val conversations: ArrayList<ChatConversation>,
    private val onItemClick: (ChatConversation) -> Unit,
    // Optional ApiManager — when provided, bell taps hit the real
    // `set_female_notification_preference` endpoint. When null (e.g. older
    // callers), the adapter falls back to local-only SharedPreferences so
    // it still works without network/DI setup.
    private val apiManager: ApiManager? = null,
    /** When pin state changes, parent re-sorts the list (pinned first, max 3). */
    private val onPinToggled: (() -> Unit)? = null,
) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {

    // SharedPreferences bucket that tracks which creator user IDs the current
    // user is subscribed to for "notify when online" alerts. Keys are the
    // creator's user id, values are Boolean (true == subscribed).
    private val notifyPrefs = activity.getSharedPreferences(NotifyOnlinePrefsHelper.PREFS_NAME, Context.MODE_PRIVATE)

    private fun isSubscribed(userId: String): Boolean =
        notifyPrefs.getBoolean("notify_$userId", false)

    private fun setSubscribed(userId: String, enabled: Boolean, displayName: String?, imageUrl: String?) {
        val id = userId.toIntOrNull() ?: return
        NotifyOnlinePrefsHelper.setSubscribedWithMeta(activity, id, enabled, displayName, imageUrl)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.bind(conversation)
    }

    override fun getItemCount(): Int = conversations.size

    /**
     * T16: replace the `notifyDataSetChanged()` rebuild with a `DiffUtil`-based
     * dispatch so every `my_chat` refresh doesn't unbind every visible row,
     * preserving scroll position and avoiding the avatar-flash on each tick.
     *
     * Kept the existing function shape (caller signature unchanged) instead of
     * full `ListAdapter` migration to minimize blast radius.
     */
    fun updateConversations(newConversations: List<ChatConversation>) {
        val oldList = conversations.toList()
        val diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(
            object : androidx.recyclerview.widget.DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldList.size
                override fun getNewListSize(): Int = newConversations.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                    oldList[oldPos].userId == newConversations[newPos].userId
                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                    oldList[oldPos] == newConversations[newPos]
            }
        )
        conversations.clear()
        conversations.addAll(newConversations)
        diff.dispatchUpdatesTo(this)
    }

    fun clearConversations() {
        val oldSize = conversations.size
        conversations.clear()
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize)
    }

    /**
     * Optimistically clears the unread badge for the row matching [userId]. Called
     * when the user taps a chat so the badge disappears instantly; the server-side
     * mark-read + next `my_chat` refresh will confirm the state on resume.
     */
    fun markConversationAsRead(userId: String) {
        val idx = conversations.indexOfFirst { it.userId == userId }
        if (idx >= 0 && conversations[idx].unreadCount != 0) {
            conversations[idx] = conversations[idx].copy(unreadCount = 0)
            notifyItemChanged(idx)
        }
    }

    /**
     * Realtime in-place update for an incoming message. Updates last-message
     * preview, type, timestamp, and bumps the unread badge — then re-orders so the
     * row appears at the top of the unpinned section (pinned rows always stay
     * first, in their stored order).
     *
     * - If the chat for [peerUserId] is currently open, the unread count stays at
     *   0 (the thread has its own mark-read flow).
     * - If [peerUserId] isn't in the list yet, returns `false` so the host can
     *   trigger a full `loadData()` to pull the new conversation in from the API.
     */
    fun applyIncomingMessage(
        peerUserId: String,
        lastMessageText: String,
        lastMessageType: String,
        lastMessageTime: com.google.firebase.Timestamp,
        suppressUnreadIncrement: Boolean = false,
        sentByMe: Boolean = false,
        lastMessageId: Long = 0L
    ): Boolean {
        val idx = conversations.indexOfFirst { it.userId == peerUserId }
        if (idx < 0) return false

        val current = conversations[idx]
        val nextUnread = if (sentByMe || suppressUnreadIncrement) 0 else current.unreadCount + 1
        val updated = current.copy(
            lastMessage = lastMessageText,
            lastMessageType = lastMessageType,
            lastMessageTime = lastMessageTime,
            unreadCount = nextUnread,
            lastMessageSentByMe = sentByMe,
            lastMessageIsRead = false,
            lastMessageId = if (lastMessageId > 0L) lastMessageId else current.lastMessageId
        )

        // Compute new order: pinned rows keep their stored order; unpinned rows
        // are sorted by lastMessageTime desc. Apply via DiffUtil so the move
        // animates and we don't blow away view state.
        val newList = conversations.toMutableList()
        newList[idx] = updated
        val (pinned, unpinned) = newList.partition { it.isPinned }
        val pinnedOrder = PinnedChatsPrefsHelper.getPinnedIds(activity)
        val sortedPinned = pinned.sortedBy { conv ->
            val i = pinnedOrder.indexOf(conv.userId)
            if (i >= 0) i else Int.MAX_VALUE
        }
        val sortedUnpinned = unpinned.sortedByDescending {
            it.lastMessageTime?.toDate()?.time ?: 0L
        }
        updateConversations(sortedPinned + sortedUnpinned)
        return true
    }

    fun applyMessagesRead(peerUserId: String, lastReadMessageId: Long?, readMessageIds: List<Long>): Boolean {
        val idx = conversations.indexOfFirst { it.userId == peerUserId }
        if (idx < 0) return false
        val current = conversations[idx]
        if (!current.lastMessageSentByMe) return false
        if (current.lastMessageIsRead) return true
        val covered = when {
            readMessageIds.isNotEmpty() && current.lastMessageId > 0L ->
                readMessageIds.contains(current.lastMessageId)
            lastReadMessageId != null && current.lastMessageId > 0L ->
                current.lastMessageId <= lastReadMessageId
            else -> true
        }
        if (!covered) return false
        val updated = current.copy(lastMessageIsRead = true)
        val newList = conversations.toMutableList()
        newList[idx] = updated
        updateConversations(newList)
        return true
    }

    inner class ViewHolder(private val binding: ItemChatConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: ChatConversation) {
            val pinned = PinnedChatsPrefsHelper.isPinned(activity, conversation.userId)
            // T-CHAT-021: surface blocked-state in the list so users don't waste
            // time composing messages that will only fail at send time.
            val isBlocked = conversation.isBlocked

            // Set user name (extract name only, remove trailing numbers).
            // Pin state is shown only by iv_pin (filled/outline + tint), not a compound drawable on the name.
            binding.tvUserName.text = extractNameOnly(conversation.userName)
            binding.tvUserName.setCompoundDrawablesRelative(null, null, null, null)

            binding.tvBlockedBadge.visibility = if (isBlocked) View.VISIBLE else View.GONE

            binding.ivPin.setImageResource(
                if (pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin_outline
            )
            binding.ivPin.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    activity,
                    if (pinned) R.color.colorAccent else R.color.chat_list_bell_inactive
                )
            )
            binding.ivPin.contentDescription = activity.getString(
                if (pinned) R.string.chat_unpin_action else R.string.chat_pin_action
            )

            binding.ivPin.setOnSingleClickListener {
                val uid = conversation.userId
                if (PinnedChatsPrefsHelper.isPinned(activity, uid)) {
                    PinnedChatsPrefsHelper.unpin(activity, uid)
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.chat_unpinned_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                    onPinToggled?.invoke()
                } else {
                    when (PinnedChatsPrefsHelper.tryPin(activity, uid)) {
                        PinnedChatsPrefsHelper.PinResult.Added -> {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.chat_pinned_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                            onPinToggled?.invoke()
                        }
                        PinnedChatsPrefsHelper.PinResult.AlreadyPinned -> Unit
                        PinnedChatsPrefsHelper.PinResult.LimitReached -> {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.chat_pin_limit_reached),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            // Set user image
            Glide.with(activity)
                .load(conversation.userImage)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.small_profile)
                .error(R.drawable.small_profile)
                .into(binding.ivUserImage)

            // Set last message — show type-aware preview so image/audio rows
            // don't appear as "No messages yet" when the text body is empty.
            // When the peer is blocked, override the preview entirely so the
            // row reads as a blocked thread at a glance.
            //
            // CHAT-108: drafts win over the last-message preview (except on
            // blocked threads where we keep the "you blocked this user" copy).
            // Rendered as a brand-pink italic "Draft:" prefix followed by the
            // draft text — same pattern WhatsApp uses.
            val peerIdInt = conversation.userId.toIntOrNull() ?: 0
            val draft = if (!isBlocked && peerIdInt > 0)
                com.gmwapp.hima.utils.ChatDraftStore.get(activity, peerIdInt)
            else ""
            if (draft.isNotBlank()) {
                val prefix = activity.getString(R.string.chat_list_draft_prefix)
                val full = "$prefix $draft"
                val span = android.text.SpannableStringBuilder(full)
                val pink = ContextCompat.getColor(activity, R.color.colorAccent)
                span.setSpan(
                    android.text.style.ForegroundColorSpan(pink),
                    0, prefix.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                span.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                    0, full.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                binding.tvLastMessage.text = span
            } else {
                binding.tvLastMessage.text = if (isBlocked) {
                    activity.getString(R.string.chat_blocked_preview)
                } else when (conversation.lastMessageType.lowercase()) {
                    "image" -> activity.getString(R.string.chat_preview_photo)
                    "audio" -> activity.getString(R.string.chat_preview_voice)
                    "video" -> activity.getString(R.string.chat_preview_video)
                    "file" -> activity.getString(R.string.chat_preview_file)
                    else -> if (conversation.lastMessage.isNotEmpty())
                        conversation.lastMessage
                    else
                        if (conversation.lastMessageType.equals("text", ignoreCase = true)) {
                            activity.getString(R.string.chat_preview_no_messages)
                        } else {
                            activity.getString(R.string.chat_preview_unsupported)
                        }
                }
            }

            // CHAT-108: hide the delivery tick when a draft is showing — the tick
            // would refer to the last sent message, which the draft preview is
            // hiding anyway, so the icon makes no sense next to "Draft: …".
            if (conversation.lastMessageSentByMe && !isBlocked && draft.isBlank()) {
                binding.ivLastMessageTick.visibility = View.VISIBLE
                if (conversation.lastMessageIsRead) {
                    binding.ivLastMessageTick.setImageResource(R.drawable.ic_chat_double_check)
                    binding.ivLastMessageTick.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(activity, R.color.chat_read_receipt)
                    )
                } else {
                    binding.ivLastMessageTick.setImageResource(R.drawable.ic_chat_single_check)
                    binding.ivLastMessageTick.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(activity, R.color.grey_medium)
                    )
                }
            } else {
                binding.ivLastMessageTick.visibility = View.GONE
            }

            // Set time
            binding.tvTime.text = formatTime(conversation.lastMessageTime)

            // Set unread count
            if (conversation.unreadCount > 0) {
                binding.tvUnreadCount.visibility = View.VISIBLE
                binding.tvUnreadCount.text = if (conversation.unreadCount > 99) {
                    activity.getString(R.string.chat_unread_overflow)
                } else {
                    conversation.unreadCount.toString()
                }
            } else {
                binding.tvUnreadCount.visibility = View.GONE
            }

            // Set online indicator
            if (conversation.isOnline) {
                binding.vOnlineIndicator.visibility = View.VISIBLE
            } else {
                binding.vOnlineIndicator.visibility = View.GONE
            }

            // Audio / Video call buttons — show only when the creator supports
            // that call type. Label includes the per-minute coin rate so users
            // know what tapping will cost them (e.g. "Audio • 10/min").
            // Audio / Video call buttons — always visible; when the creator
            // doesn't support a given call type we render the button in a
            // disabled (grey) state instead of hiding so the row stays
            // visually balanced. Label shows per-minute coin rate when
            // enabled, "Unavailable" otherwise.
            // Blocked threads can't initiate calls either — force both buttons
            // into the disabled (grey) state regardless of audio/video status.
            val showAudio = !isBlocked && conversation.audioStatus == 1
            val showVideo = !isBlocked && conversation.videoStatus == 1
            val disabledTextColor = activity.getColor(R.color.chat_list_call_disabled_text)
            val whiteColor = activity.getColor(R.color.white)

            binding.btnAudioCall.visibility = View.VISIBLE
            binding.btnVideoCall.visibility = View.VISIBLE

            binding.btnAudioCall.background = activity.resources.getDrawable(
                if (showAudio) R.drawable.button_audio_gradient else R.drawable.button_disabled_gradient,
                null
            )
            binding.btnVideoCall.background = activity.resources.getDrawable(
                if (showVideo) R.drawable.button_video_gradient else R.drawable.button_disabled_gradient,
                null
            )
            binding.btnAudioCall.isEnabled = showAudio
            binding.btnVideoCall.isEnabled = showVideo
            binding.btnAudioCall.isClickable = showAudio
            binding.btnVideoCall.isClickable = showVideo

            // Readable text & icon colors on both enabled gradient (white) and
            // disabled grey background (darker grey) — white on grey is invisible.
            binding.ivAudioIcon.setColorFilter(if (showAudio) whiteColor else disabledTextColor)
            binding.ivVideoIcon.setColorFilter(if (showVideo) whiteColor else disabledTextColor)
            binding.tvAudioRate.setTextColor(if (showAudio) whiteColor else disabledTextColor)
            binding.tvVideoRate.setTextColor(if (showVideo) whiteColor else disabledTextColor)
            binding.ivAudioCoin.visibility = if (showAudio) View.VISIBLE else View.GONE
            binding.ivVideoCoin.visibility = if (showVideo) View.VISIBLE else View.GONE

            binding.tvAudioRate.text = if (showAudio)
                "${conversation.coinPerMinAudio}/min"
            else
                activity.getString(R.string.call_unavailable)
            binding.tvVideoRate.text = if (showVideo)
                "${conversation.coinPerMinVideo}/min"
            else
                activity.getString(R.string.call_unavailable)

            binding.btnAudioCall.setOnSingleClickListener {
                if (showAudio) launchCall(conversation, "audio")
            }
            binding.btnVideoCall.setOnSingleClickListener {
                if (showVideo) launchCall(conversation, "video")
            }

            // Notify-when-online bell icon — tap to toggle subscription.
            // Persisted per creator-id in SharedPreferences for instant UI
            // feedback, and when an ApiManager is available we also hit the
            // real `set_female_notification_preference` endpoint so the
            // backend actually sends a push when the creator comes online.
            val displayName = extractNameOnly(conversation.userName)
            val subscribed = isSubscribed(conversation.userId)
            updateBellIcon(subscribed)
            binding.ivNotifyBell.contentDescription = if (subscribed)
                activity.getString(R.string.notify_bell_on_desc, displayName)
            else
                activity.getString(R.string.notify_bell_off_desc, displayName)

            binding.ivNotifyBell.setOnSingleClickListener {
                val nowSubscribed = !isSubscribed(conversation.userId)
                // Optimistic UI — update instantly, rollback on API error.
                setSubscribed(
                    conversation.userId,
                    nowSubscribed,
                    extractNameOnly(conversation.userName),
                    conversation.userImage
                )
                updateBellIcon(nowSubscribed)
                binding.ivNotifyBell.contentDescription = if (nowSubscribed)
                    activity.getString(R.string.notify_bell_on_desc, displayName)
                else
                    activity.getString(R.string.notify_bell_off_desc, displayName)
                if (nowSubscribed) {
                    binding.ivNotifyBell.startAnimation(
                        AnimationUtils.loadAnimation(activity, R.anim.bell_ring)
                    )
                }

                val enableToast = activity.getString(R.string.notify_enabled_toast, displayName)
                val disableToast = activity.getString(R.string.notify_disabled_toast, displayName)

                val maleUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
                val femaleUserId = conversation.userId.toIntOrNull() ?: 0
                val api = apiManager
                if (api != null && maleUserId != 0 && femaleUserId != 0) {
                    // Show toast optimistically — backend confirms / rolls back.
                    Toast.makeText(
                        activity,
                        if (nowSubscribed) enableToast else disableToast,
                        Toast.LENGTH_SHORT
                    ).show()

                    api.setFemaleNotificationPreference(
                        maleUserId = maleUserId,
                        femaleUserId = femaleUserId,
                        status = if (nowSubscribed) 1 else 0,
                        callback = object : NetworkCallback<FemaleNotificationPreferenceResponse> {
                            override fun onResponse(
                                call: Call<FemaleNotificationPreferenceResponse>,
                                response: Response<FemaleNotificationPreferenceResponse>
                            ) {
                                val ok = response.isSuccessful && response.body()?.success == true
                                if (!ok) rollbackBell(conversation, nowSubscribed)
                                Log.d(
                                    "NotifyPref",
                                    "setFemaleNotificationPreference success=$ok for $femaleUserId -> $nowSubscribed"
                                )
                            }

                            override fun onFailure(
                                call: Call<FemaleNotificationPreferenceResponse>,
                                t: Throwable
                            ) {
                                rollbackBell(conversation, nowSubscribed)
                                Log.e("NotifyPref", "API failed: ${t.message}")
                            }

                            override fun onNoNetwork() {
                                rollbackBell(conversation, nowSubscribed)
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.notify_no_network_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                } else {
                    // Local-only fallback (no ApiManager wired).
                    Toast.makeText(
                        activity,
                        if (nowSubscribed) enableToast else disableToast,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Handle click on the rest of the row — open chat
            binding.root.setOnClickListener {
                onItemClick(conversation)
            }
        }

        private fun rollbackBell(conversation: ChatConversation, attemptedState: Boolean) {
            val reverted = !attemptedState
            setSubscribed(
                conversation.userId,
                reverted,
                extractNameOnly(conversation.userName),
                conversation.userImage
            )
            updateBellIcon(reverted)
            Toast.makeText(
                activity,
                activity.getString(R.string.notify_error_toast),
                Toast.LENGTH_SHORT
            ).show()
        }

        private fun updateBellIcon(subscribed: Boolean) {
            if (subscribed) {
                binding.ivNotifyBell.setImageResource(R.drawable.ic_notifications_active)
                binding.ivNotifyBell.setColorFilter(
                    activity.getColor(R.color.chat_list_bell_active)
                )
            } else {
                binding.ivNotifyBell.setImageResource(R.drawable.ic_notifications_off)
                binding.ivNotifyBell.setColorFilter(
                    activity.getColor(R.color.chat_list_bell_inactive)
                )
            }
        }

        private fun launchCall(conversation: ChatConversation, callType: String) {
            val receiverId = conversation.userId.toIntOrNull() ?: return
            val intent = Intent(activity, MaleCallConnectingActivity::class.java).apply {
                putExtra(DConstants.CALL_TYPE, callType)
                putExtra(DConstants.RECEIVER_ID, receiverId)
                putExtra(DConstants.RECEIVER_NAME, conversation.userName)
                putExtra(DConstants.CALL_ID, 0)
                putExtra(DConstants.IMAGE, conversation.userImage)
                putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
                putExtra(
                    DConstants.TEXT,
                    activity.getString(R.string.wait_user_hint, conversation.userName)
                )
            }
            FcmUtils.isUserAvailable = 1
            activity.startActivity(intent)
        }

        private fun formatTime(timestamp: com.google.firebase.Timestamp?): String {
            if (timestamp == null) return ""

            val messageTime = timestamp.toDate()
            val now = Date()
            val diffInMillis = now.time - messageTime.time

            return when {
                // Less than 1 minute ago
                diffInMillis < TimeUnit.MINUTES.toMillis(1) -> activity.getString(R.string.chat_time_just_now)
                
                // Less than 1 hour ago
                diffInMillis < TimeUnit.HOURS.toMillis(1) -> {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
                    activity.getString(R.string.chat_time_minutes_short, minutes)
                }
                
                // Today
                isSameDay(messageTime, now) -> {
                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(messageTime)
                }
                
                // Yesterday
                isYesterday(messageTime, now) -> activity.getString(R.string.chat_date_yesterday)
                
                // This week
                diffInMillis < TimeUnit.DAYS.toMillis(7) -> {
                    SimpleDateFormat("EEE", Locale.getDefault()).format(messageTime)
                }
                
                // Older
                else -> {
                    SimpleDateFormat("MMM dd", Locale.getDefault()).format(messageTime)
                }
            }
        }

        private fun isSameDay(date1: Date, date2: Date): Boolean {
            val cal1 = Calendar.getInstance().apply { time = date1 }
            val cal2 = Calendar.getInstance().apply { time = date2 }
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        private fun isYesterday(date: Date, now: Date): Boolean {
            val cal1 = Calendar.getInstance().apply { time = date }
            val cal2 = Calendar.getInstance().apply { 
                time = now
                add(Calendar.DAY_OF_YEAR, -1)
            }
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        /**
         * T34: only strip 6+ trailing digits (the auto-generated suffix shape) so
         * names like `Agent007` or `Joy22` stay intact. `User123456` becomes `User`.
         */
        private fun extractNameOnly(username: String): String {
            if (username.isEmpty()) return username
            return username.replace(Regex("\\d{6,}$"), "").trim()
        }
    }
}

