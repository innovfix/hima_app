package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.models.ChatMessage
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.hdodenhof.circleimageview.CircleImageView

class BottomSheetReactionDetails : BottomSheetDialogFragment() {
    
    private var message: ChatMessage? = null
    private var myUserId: Int = 0
    private var peerUserId: Int = 0
    private var peerUserName: String = ""
    private var peerUserImage: String = ""
    private var onReactionRemove: ((ChatMessage, String) -> Unit)? = null
    private var onAddReaction: ((ChatMessage) -> Unit)? = null
    
    companion object {
        fun newInstance(
            message: ChatMessage,
            myUserId: Int,
            peerUserId: Int,
            peerUserName: String,
            peerUserImage: String,
            onReactionRemove: ((ChatMessage, String) -> Unit)?,
            onAddReaction: ((ChatMessage) -> Unit)?
        ): BottomSheetReactionDetails {
            val fragment = BottomSheetReactionDetails()
            fragment.message = message
            fragment.myUserId = myUserId
            fragment.peerUserId = peerUserId
            fragment.peerUserName = peerUserName
            fragment.peerUserImage = peerUserImage
            fragment.onReactionRemove = onReactionRemove
            fragment.onAddReaction = onAddReaction
            return fragment
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_reaction_details, container, false)
    }
    
    override fun getTheme(): Int = R.style.BottomSheetDialogTheme
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val reactions = message?.reactions ?: emptyMap()
        if (reactions.isEmpty()) {
            dismiss()
            return
        }
        
        // Set title with total reaction count
        val tvTitle = view.findViewById<TextView>(R.id.tv_reaction_title)
        val totalReactions = reactions.size
        tvTitle.text = "$totalReactions reaction${if (totalReactions > 1) "s" else ""}"
        
        // Group reactions by emoji
        val reactionsByEmoji = reactions.entries.groupBy({ it.value }, { it.key })
        val uniqueEmojis = reactionsByEmoji.keys.toList()
        
        // Setup RecyclerView first (needed for filter buttons)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_reaction_users)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Setup filter buttons
        val llFilters = view.findViewById<android.widget.LinearLayout>(R.id.ll_reaction_filters)
        val btnAddReaction = view.findViewById<MaterialButton>(R.id.btn_add_reaction)
        
        // Add reaction button click
        btnAddReaction.setOnClickListener {
            onAddReaction?.invoke(message!!)
            dismiss()
        }
        
        // Track selected filter
        var selectedEmojiFilter: String? = null
        
        // Add filter button for each emoji
        uniqueEmojis.forEachIndexed { index, emoji ->
            val count = reactionsByEmoji[emoji]?.size ?: 0
            val filterButton = MaterialButton(requireContext()).apply {
                text = "$emoji $count"
                textSize = 16f
                minWidth = 0
                setPadding(32, 16, 32, 16)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (index == 0) {
                        selectedEmojiFilter = emoji
                        0xFFE8F5E9.toInt() // Light green for selected
                    } else {
                        0xFFF3F4F6.toInt() // Light grey for unselected
                    }
                )
                setTextColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK))
                
                // Fix faded emoji colors - force full color rendering
                alpha = 1.0f
                typeface = android.graphics.Typeface.DEFAULT
                paintFlags = android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG
                
                cornerRadius = 20
                strokeWidth = 0
                elevation = 0f
                
                setOnClickListener {
                    // Update selected filter
                    selectedEmojiFilter = if (selectedEmojiFilter == emoji) null else emoji
                    
                    // Update all button colors
                    for (i in 0 until llFilters.childCount) {
                        val btn = llFilters.getChildAt(i) as? MaterialButton
                        if (btn != null && btn != btnAddReaction) {
                            val btnEmoji = uniqueEmojis.getOrNull(i - 1) // -1 because add button is first
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                                if (btnEmoji == selectedEmojiFilter) {
                                    0xFFE8F5E9.toInt()
                                } else {
                                    0xFFF3F4F6.toInt()
                                }
                            )
                        }
                    }
                    
                    // Filter reactions (update adapter)
                    val reactionsMap = message?.reactions ?: emptyMap()
                    updateReactionList(recyclerView, reactionsMap, selectedEmojiFilter)
                }
            }
            
            val params = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
            llFilters.addView(filterButton, params)
        }
        
        // Initial load (show all reactions)
        val reactionsMap = message?.reactions ?: emptyMap()
        updateReactionList(recyclerView, reactionsMap, selectedEmojiFilter)
    }
    
    private fun updateReactionList(
        recyclerView: RecyclerView,
        reactions: Map<Int, String>,
        selectedEmoji: String?
    ) {
        // Create list of reaction items (filtered by emoji if selected)
        val reactionItems = mutableListOf<ReactionUserItem>()
        reactions.forEach { (userId, emoji) ->
            // Filter by selected emoji if any
            if (selectedEmoji == null || emoji == selectedEmoji) {
                val isCurrentUser = userId == myUserId
                val userName = if (isCurrentUser) {
                    "You"
                } else if (userId == peerUserId) {
                    peerUserName
                } else {
                    "User $userId" // Fallback for other users (shouldn't happen in 1-on-1 chat)
                }
                
                val userImage = when {
                    isCurrentUser -> BaseApplication.getInstance()?.getPrefs()?.getUserData()?.image ?: ""
                    userId == peerUserId -> peerUserImage
                    else -> "" // Fallback
                }
                
                reactionItems.add(ReactionUserItem(userId, userName, userImage, emoji, isCurrentUser))
            }
        }
        
        recyclerView.adapter = ReactionUsersAdapter(reactionItems) { item ->
            if (item.isCurrentUser) {
                // Remove reaction
                onReactionRemove?.invoke(message!!, item.emoji)
                dismiss()
            }
        }
    }
    
    private data class ReactionUserItem(
        val userId: Int,
        val userName: String,
        val userImage: String,
        val emoji: String,
        val isCurrentUser: Boolean
    )
    
    private class ReactionUsersAdapter(
        private val items: List<ReactionUserItem>,
        private val onItemClick: (ReactionUserItem) -> Unit
    ) : RecyclerView.Adapter<ReactionUsersAdapter.ViewHolder>() {
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivAvatar: CircleImageView = itemView.findViewById(R.id.iv_user_avatar)
            val tvUserName: TextView = itemView.findViewById(R.id.tv_user_name)
            val tvTapToRemove: TextView = itemView.findViewById(R.id.tv_tap_to_remove)
            val tvEmoji: TextView = itemView.findViewById(R.id.tv_reaction_emoji)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reaction_user, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            holder.tvUserName.text = item.userName
            holder.tvEmoji.text = item.emoji
            
            // Fix faded emoji colors - force full color rendering
            holder.tvEmoji.setTextColor(android.graphics.Color.BLACK)
            holder.tvEmoji.alpha = 1.0f
            holder.tvEmoji.typeface = android.graphics.Typeface.DEFAULT
            holder.tvEmoji.paintFlags = android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG
            
            // Show "Tap to remove" only for current user
            if (item.isCurrentUser) {
                holder.tvTapToRemove.visibility = View.VISIBLE
            } else {
                holder.tvTapToRemove.visibility = View.GONE
            }
            
            // Load user image
            if (item.userImage.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(item.userImage)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.star)
                    .error(R.drawable.star)
                    .into(holder.ivAvatar)
            } else {
                holder.ivAvatar.setImageResource(R.drawable.star)
            }
            
            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }
        
        override fun getItemCount() = items.size
    }
}

