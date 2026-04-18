package com.gmwapp.hima.adapters

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.R
import com.gmwapp.hima.agora.FcmUtils
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ItemChatConversationBinding
import com.gmwapp.hima.models.ChatConversation
import com.gmwapp.hima.utils.setOnSingleClickListener
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ChatListAdapter(
    private val activity: Activity,
    private val conversations: ArrayList<ChatConversation>,
    private val onItemClick: (ChatConversation) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {

    companion object {
        private const val BLINK_INTERVAL_MS = 1800L
        private const val PROMPT_TEXT = "✨ Tap to see your message for free"
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

    override fun onViewRecycled(holder: ViewHolder) {
        holder.stopBlink()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = conversations.size

    fun updateConversations(newConversations: List<ChatConversation>) {
        conversations.clear()
        conversations.addAll(newConversations)
        notifyDataSetChanged()
    }

    fun clearConversations() {
        conversations.clear()
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemChatConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val blinkHandler = Handler(Looper.getMainLooper())
        private var blinkRunnable: Runnable? = null

        fun stopBlink() {
            blinkRunnable?.let { blinkHandler.removeCallbacks(it) }
            blinkRunnable = null
        }

        fun bind(conversation: ChatConversation) {
            // Set user name (extract name only, remove trailing numbers)
            binding.tvUserName.text = extractNameOnly(conversation.userName)

            // Set user image
            Glide.with(activity)
                .load(conversation.userImage)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.small_profile)
                .error(R.drawable.small_profile)
                .into(binding.ivUserImage)

            // Last message: blink between the real message and a "tap to see" prompt.
            stopBlink()
            val realMessage = if (conversation.lastMessage.isNotEmpty())
                conversation.lastMessage
            else
                "No messages yet"
            binding.tvLastMessage.text = realMessage

            if (conversation.lastMessage.isNotEmpty()) {
                val accent = androidx.core.content.ContextCompat.getColor(activity, R.color.colorAccent)
                val normal = androidx.core.content.ContextCompat.getColor(activity, R.color.grey_medium)
                var showPrompt = false
                blinkRunnable = object : Runnable {
                    override fun run() {
                        showPrompt = !showPrompt
                        if (showPrompt) {
                            binding.tvLastMessage.text = PROMPT_TEXT
                            binding.tvLastMessage.setTextColor(accent)
                        } else {
                            binding.tvLastMessage.text = realMessage
                            binding.tvLastMessage.setTextColor(normal)
                        }
                        blinkHandler.postDelayed(this, BLINK_INTERVAL_MS)
                    }
                }
                blinkHandler.postDelayed(blinkRunnable!!, BLINK_INTERVAL_MS)
            }

            // Set time
            binding.tvTime.text = formatTime(conversation.lastMessageTime)

            // Set unread count
            if (conversation.unreadCount > 0) {
                binding.tvUnreadCount.visibility = View.VISIBLE
                binding.tvUnreadCount.text = if (conversation.unreadCount > 99) {
                    "99+"
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

            // Handle card click (open chat)
            binding.root.setOnSingleClickListener {
                onItemClick(conversation)
            }

            // Audio / Video buttons — hide if creator doesn't support that call type
            binding.btnAudioCall.visibility =
                if (conversation.audioStatus == 1) View.VISIBLE else View.GONE
            binding.btnVideoCall.visibility =
                if (conversation.videoStatus == 1) View.VISIBLE else View.GONE

            binding.btnAudioCall.setOnSingleClickListener {
                launchCall(conversation, "audio")
            }
            binding.btnVideoCall.setOnSingleClickListener {
                launchCall(conversation, "video")
            }
        }

        private fun launchCall(conv: ChatConversation, callType: String) {
            val intent = Intent(activity, MaleCallConnectingActivity::class.java).apply {
                putExtra(DConstants.CALL_TYPE, callType)
                putExtra(DConstants.RECEIVER_ID, conv.userId.toIntOrNull() ?: 0)
                putExtra(DConstants.RECEIVER_NAME, conv.userName)
                putExtra(DConstants.CALL_ID, 0)
                putExtra(DConstants.IMAGE, conv.userImage)
                putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
                putExtra(
                    DConstants.TEXT,
                    activity.getString(R.string.wait_user_hint, conv.userName)
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
                diffInMillis < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                
                // Less than 1 hour ago
                diffInMillis < TimeUnit.HOURS.toMillis(1) -> {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
                    "${minutes}m"
                }
                
                // Today
                isSameDay(messageTime, now) -> {
                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(messageTime)
                }
                
                // Yesterday
                isYesterday(messageTime, now) -> "Yesterday"
                
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
         * Extracts the name part from username by removing trailing numbers
         * Examples: "Joy22" -> "Joy", "ZNKAK467" -> "ZNKAK"
         */
        private fun extractNameOnly(username: String): String {
            if (username.isEmpty()) return username
            
            // Remove trailing digits
            return username.replace(Regex("\\d+$"), "").trim()
        }
    }
}

