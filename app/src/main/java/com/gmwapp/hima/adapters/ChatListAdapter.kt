package com.gmwapp.hima.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ItemChatConversationBinding
import com.gmwapp.hima.models.ChatConversation
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ChatListAdapter(
    private val activity: Activity,
    private val conversations: ArrayList<ChatConversation>,
    private val onItemClick: (ChatConversation) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {

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

        fun bind(conversation: ChatConversation) {
            // Set user name
            binding.tvUserName.text = conversation.userName

            // Set user image
            Glide.with(activity)
                .load(conversation.userImage)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.small_profile)
                .error(R.drawable.small_profile)
                .into(binding.ivUserImage)

            // Set last message
            binding.tvLastMessage.text = if (conversation.lastMessage.isNotEmpty()) {
                conversation.lastMessage
            } else {
                "No messages yet"
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

            // Handle click
            binding.root.setOnClickListener {
                onItemClick(conversation)
            }
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
    }
}

