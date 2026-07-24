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
import com.gmwapp.hima.utils.CallButtonStyler
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

    // Idempotency guard for the realtime unread bump. A single inbound message can
    // reach the list twice — once over Socket.IO and once via the OneSignal push
    // broadcast — and on reconnect the socket can replay a backlog of messages the
    // server already counts. Bumping per-delivery inflated the badge to "every
    // message ever" instead of the WhatsApp-style unseen-only count. We therefore
    // bump at most once per unique message id; the bounded set caps memory.
    private val countedIncomingIds = LinkedHashSet<Long>()
    private val maxCountedIncomingIds = 1000

    /** Records [id] as counted; returns true only the first time we see it. */
    private fun rememberCountedIncomingId(id: Long): Boolean {
        if (!countedIncomingIds.add(id)) return false
        if (countedIncomingIds.size > maxCountedIncomingIds) {
            val it = countedIncomingIds.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
        return true
    }

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
     *
     * The unread badge is bumped at most once per unique [incomingMessageId].
     * Socket.IO deliveries carry the id; the OneSignal push broadcast passes `null`
     * and therefore never bumps the count — the socket path owns counting, and the
     * host's periodic `my_chat` refresh reconciles the authoritative server value.
     * This stops the same message being counted twice (socket + push) and stops
     * reconnect backlog replays from inflating the badge.
     */
    fun applyIncomingMessage(
        peerUserId: String,
        lastMessageText: String,
        lastMessageType: String,
        lastMessageTime: com.google.firebase.Timestamp,
        suppressUnreadIncrement: Boolean = false,
        incomingMessageId: Long? = null,
        // CM_006-live: set by the push path when the socket is NOT connected — the
        // socket can't also count this message, so the badge must bump here or it
        // never updates live on the list. Deduped by id when an id is present.
        forceCountUnread: Boolean = false
    ): Boolean {
        val idx = conversations.indexOfFirst { it.userId == peerUserId }
        if (idx < 0) return false

        val current = conversations[idx]
        // Record every id-bearing (socket) delivery so a later replay of the same
        // message can't re-bump the badge. Bump on the first sighting of an id, OR
        // when forceCountUnread is set (push path, socket down — no double-count is
        // possible). The host's periodic `my_chat` refresh reconciles the server value.
        val firstSighting = incomingMessageId != null && rememberCountedIncomingId(incomingMessageId)
        val countThis = (firstSighting || forceCountUnread) && !suppressUnreadIncrement
        val nextUnread = if (countThis) current.unreadCount + 1 else current.unreadCount
        val updated = current.copy(
            lastMessage = lastMessageText,
            lastMessageType = lastMessageType,
            lastMessageTime = lastMessageTime,
            unreadCount = nextUnread
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
        // QA bug #8 — keep blocked conversations pinned to the bottom on live
        // socket re-sorts too, so a new message on a normal chat never floats it
        // above a blocked one inconsistently with the full-list sort. Final stable
        // partition: normal rows keep their order, blocked rows go to the end.
        val (blockedRows, normalRows) = (sortedPinned + sortedUnpinned)
            .partition { it.isBlocked || it.peerBlockedMe }
        updateConversations(normalRows + blockedRows)
        return true
    }

    inner class ViewHolder(private val binding: ItemChatConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: ChatConversation) {
            val pinned = PinnedChatsPrefsHelper.isPinned(activity, conversation.userId)

            // CM_001: the notify bell + audio/video call buttons are male/payer controls. The bell
            // calls set_female_notification_preference (male_user_id must be male), so on the creator
            // (female) side it always fails ("Couldn't update notification"). Hide the bell AND the
            // call buttons for female viewers; keep only the pin. Male view is unchanged.
            val isViewerFemale = BaseApplication.getInstance()?.getPrefs()
                ?.getUserData()?.gender
                ?.equals(DConstants.FEMALE, ignoreCase = true) == true
            binding.ivNotifyBell.visibility = if (isViewerFemale) View.GONE else View.VISIBLE

            // Set user name (extract name only, remove trailing numbers).
            // Pin state is shown only by iv_pin (filled/outline + tint), not a compound drawable on the name.
            binding.tvUserName.text = extractNameOnly(conversation.userName)
            // Feed the id->name cache so the in-app chat heads-up (BaseApplication) can
            // title itself "<name> sent you a message" — the socket payload has only the id.
            com.gmwapp.hima.utils.PeerNameCache.put(
                conversation.userId.toIntOrNull() ?: 0,
                extractNameOnly(conversation.userName)
            )
            binding.tvUserName.setCompoundDrawablesRelative(null, null, null, null)

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
            binding.tvLastMessage.text = when (conversation.lastMessageType.lowercase()) {
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
            // TC_022: a blocked conversation stays in the list, but the call buttons are
            // swapped for a clear "Blocked" badge so the user can still see/track it.
            // Show a badge when EITHER I blocked them (isBlocked) OR they blocked me
            // (peerBlockedMe). Either way the call buttons are swapped for the badge so
            // the row reads as blocked instead of looking normal.
            val isBlockedRow = conversation.isBlocked || conversation.peerBlockedMe
            // Product decision (2026-07): a blocked peer must still SHOW the call
            // buttons, just GREYED OUT / disabled — not hidden. So we no longer swap
            // the buttons for the "Blocked" text badge on the male side; instead the
            // buttons render in their grey (unavailable) state below, with NO sub-label.
            // The text badge is kept ONLY for female (creator) viewers, who have no
            // call buttons at all (CM_001) and so need some blocked indicator.
            binding.tvBlockedBadge.text = if (conversation.isBlocked)
                activity.getString(R.string.chat_blocked_indicator)
            else
                activity.getString(R.string.chat_peer_blocked_indicator)
            binding.tvBlockedBadge.visibility =
                if (isBlockedRow && isViewerFemale) View.VISIBLE else View.GONE
            // CM_001: call buttons hidden for female (creator) viewers (see bell gate above).
            binding.callButtonsRow.visibility =
                if (isViewerFemale) View.GONE else View.VISIBLE

            // Blocked forces the grey/disabled state regardless of the peer's actual
            // online/availability, so a blocked contact can never be called from here.
            val showAudio = conversation.audioStatus == 1 && !isBlockedRow
            val showVideo = conversation.videoStatus == 1 && !isBlockedRow
            val disabledTextColor = activity.getColor(R.color.chat_list_call_disabled_text)
            val whiteColor = activity.getColor(R.color.white)

            binding.btnAudioCall.visibility = View.VISIBLE
            binding.btnVideoCall.visibility = View.VISIBLE

            // Circular call buttons (item_chat_conversation.xml): home look —
            // white circle + coloured icon when available, grey icon + 0.5 alpha
            // + "Offline" label when not.
            val greyIconColor = CallButtonStyler.DISABLED_COLOR
            val rateTextColor = activity.getColor(R.color.black_light)

            // Audio = call (both genders can call a male/female peer when online).
            binding.btnAudioCall.setBackgroundResource(R.drawable.bg_call_circle_white)
            binding.btnAudioCall.alpha = if (showAudio) 1f else 0.5f
            binding.ivAudioIcon.setImageResource(R.drawable.ic_phone)
            binding.ivAudioIcon.setColorFilter(if (showAudio) CallButtonStyler.AUDIO_COLOR else greyIconColor)
            binding.btnAudioCall.isEnabled = showAudio
            binding.btnAudioCall.isClickable = showAudio

            // Video = call. White circle + purple camera icon when available.
            binding.btnVideoCall.setBackgroundResource(R.drawable.bg_call_circle_white)
            binding.btnVideoCall.alpha = if (showVideo) 1f else 0.5f
            binding.ivVideoIcon.setImageResource(R.drawable.ic_videocam)
            binding.ivVideoIcon.setColorFilter(if (showVideo) CallButtonStyler.VIDEO_COLOR else greyIconColor)
            binding.btnVideoCall.isEnabled = showVideo
            binding.btnVideoCall.isClickable = showVideo

            // The per-minute coin rate is the caller's cost — only males pay, so
            // coins/rate never show for the female recipient (she sees Online/Offline).
            binding.ivAudioCoin.visibility =
                if (showAudio && !isViewerFemale) View.VISIBLE else View.GONE
            binding.ivVideoCoin.visibility =
                if (showVideo && !isViewerFemale) View.VISIBLE else View.GONE

            val offlineLabel = activity.getString(R.string.call_status_offline)
            val onlineLabel = activity.getString(R.string.call_status_online)
            // Blocked: greyed buttons with NO sub-label (no "Offline"/"Blocked"/rate text).
            binding.tvAudioRate.text = when {
                isBlockedRow -> ""
                !showAudio -> offlineLabel
                isViewerFemale -> onlineLabel
                else -> "${conversation.coinPerMinAudio}/min"
            }
            binding.tvAudioRate.setTextColor(if (showAudio) rateTextColor else greyIconColor)
            binding.tvVideoRate.text = when {
                isBlockedRow -> ""
                !showVideo -> offlineLabel
                isViewerFemale -> onlineLabel
                else -> "${conversation.coinPerMinVideo}/min"
            }
            binding.tvVideoRate.setTextColor(if (showVideo) rateTextColor else greyIconColor)

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
            // Female callers are recipients, not payers — route them through the
            // female call flow so they don't hit the male coin gate and get bounced
            // to WalletActivity. Mirrors the pattern in FavouriteFragment.
            val callerGender = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
            val activityClass = if (callerGender == DConstants.FEMALE) {
                com.gmwapp.hima.agora.female.FemaleCallConnectingActivity::class.java
            } else {
                MaleCallConnectingActivity::class.java
            }
            val intent = Intent(activity, activityClass).apply {
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
            return com.gmwapp.hima.utils.PeerNameUtils.sanitizePeerName(username)
        }
    }
}

