package com.gmwapp.hima.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.AdapterFriendItemBinding
import com.gmwapp.hima.fragments.FriendsTabFragment
import com.gmwapp.hima.retrofit.responses.FriendData
import com.gmwapp.hima.utils.DateTimeUtils
import com.gmwapp.hima.utils.setOnSingleClickListener

class FriendsAdapter(
    private val activity: Activity,
    private val friendsList: List<FriendData>,
    private val tabType: Int,
    private val onChatClick: (FriendData) -> Unit,
    private val onAcceptClick: (FriendData) -> Unit,
    private val onRemoveClick: (FriendData) -> Unit,
    private val onProfileClick: (FriendData) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AdapterFriendItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val friend = friendsList[position]
        holder.bind(friend)
    }

    override fun getItemCount(): Int = friendsList.size

    inner class ViewHolder(private val binding: AdapterFriendItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(friend: FriendData) {
            // Load profile image
            Glide.with(activity)
                .load(friend.image)
                .placeholder(R.drawable.star)
                .error(R.drawable.star)
                .circleCrop()
                .into(binding.ivProfile)

            // Set name (extract name only, remove trailing numbers)
            binding.tvName.text = extractNameOnly(friend.name)

            // Configure UI based on tab type
            when (tabType) {
                FriendsTabFragment.TYPE_CHAT -> {
                    // Chat tab: Show all users with status, no buttons, click to chat
                    binding.tvStatus.text = if (friend.is_online) {
                        "Online now"
                    } else {
                        DateTimeUtils.formatLastSeen(friend.last_seen)
                    }
                    binding.llActions.visibility = View.GONE
                    binding.cardFriend.setOnSingleClickListener {
                        onChatClick(friend)
                    }
                }
                FriendsTabFragment.TYPE_FRIENDS -> {
                    // Friends tab: Show different text based on chat history
                    binding.tvStatus.text = if (friend.hasChatHistory) {
                        "Tap to continue chatting"
                    } else {
                        "Start your conversation now"
                    }
                    binding.llActions.visibility = View.GONE
                    binding.cardFriend.setOnSingleClickListener {
                        onChatClick(friend)
                    }
                }
                FriendsTabFragment.TYPE_MY_REQUESTS -> {
                    // My Requests: Show "Request sent", show cancel button only
                    binding.tvStatus.text = "Request sent"
                    binding.llActions.visibility = View.VISIBLE
                    binding.btnAccept.visibility = View.GONE
                    binding.btnReject.visibility = View.GONE
                    
                    binding.btnReject.setOnSingleClickListener {
                        onRemoveClick(friend)
                    }
                    
                    // Click card to view profile
                    binding.cardFriend.setOnSingleClickListener {
                        onProfileClick(friend)
                    }
                }
                FriendsTabFragment.TYPE_THEIR_REQUESTS -> {
                    // Their Requests: Show "Wants to be friends", show accept and reject buttons
                    binding.tvStatus.text = "Wants to be friends"
                    binding.llActions.visibility = View.VISIBLE
                    binding.btnAccept.visibility = View.VISIBLE
                    binding.btnReject.visibility = View.VISIBLE
                    
                    binding.btnAccept.setOnSingleClickListener {
                        onAcceptClick(friend)
                    }
                    
                    binding.btnReject.setOnSingleClickListener {
                        onRemoveClick(friend)
                    }
                    
                    // Click card to view profile
                    binding.cardFriend.setOnSingleClickListener {
                        onProfileClick(friend)
                    }
                }
            }
        }

        /**
         * Extracts the name part from username by removing trailing numbers
         * Examples: "Joy22" -> "Joy", "ZNKAK467" -> "ZNKAK"
         */
        private fun extractNameOnly(username: String): String {
            if (username.isEmpty()) return username
            return com.gmwapp.hima.utils.PeerNameUtils.sanitizePeerName(username)
        }
    }
}

