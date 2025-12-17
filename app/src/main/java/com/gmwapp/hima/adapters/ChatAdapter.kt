package com.gmwapp.hima.adapters

import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.models.ChatMessage

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val enableReactions: Boolean = false,
    private val myUserId: Int = 0,
    private val onReactionChanged: ((ChatMessage, String?) -> Unit)? = null,
    private val onReactionClick: ((ChatMessage, String) -> Unit)? = null  // message, emoji clicked
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_DATE_HEADER = 3
        private val REACTIONS = listOf("👍", "❤️", "😂", "😮", "🙏")
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            messages[position].isDateHeader -> VIEW_TYPE_DATE_HEADER
            messages[position].isSentByMe -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is DateHeaderViewHolder -> holder.bind(message)
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun showReactionPopupForPosition(anchor: View, position: Int) {
        val safePosition = position.takeIf { it in messages.indices } ?: return
        val message = messages[safePosition]
        val currentUserReaction = message.reactions[myUserId] // Get current user's reaction
        
        // Create horizontal popup layout
        val popupView = LayoutInflater.from(anchor.context).inflate(R.layout.popup_reaction_horizontal, null)
        val reactionContainer = popupView.findViewById<LinearLayout>(R.id.ll_reaction_container)
            ?: popupView as LinearLayout
        
        // #region agent log
        Log.d("DEBUG_REACTION_H1", "Container created - alpha:${reactionContainer.alpha}, visibility:${reactionContainer.visibility}, hasBackground:${reactionContainer.background != null}")
        // #endregion
        
        // Add emoji buttons horizontally
        REACTIONS.forEachIndexed { index, emoji ->
            val emojiButton = TextView(anchor.context).apply {
                text = emoji
                textSize = 32f
                gravity = Gravity.CENTER
                val padding = (8 * anchor.context.resources.displayMetrics.density).toInt()
                val horizontalPadding = (12 * anchor.context.resources.displayMetrics.density).toInt()
                setPadding(horizontalPadding, padding, horizontalPadding, padding)
                includeFontPadding = false
                
                // Fix faded emoji colors - force full color rendering
                setTextColor(android.graphics.Color.BLACK)
                alpha = 1.0f
                typeface = android.graphics.Typeface.DEFAULT
                paintFlags = android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG
                
                // #region agent log
                Log.d("DEBUG_REACTION_H2H3", "TextView created - emoji:$emoji, alpha:${this.alpha}, textColor:${Integer.toHexString(this.currentTextColor)}, paintFlags:${this.paint.flags}, hasColorFilter:${this.paint.colorFilter != null}, textScaleX:${this.paint.textScaleX}")
                // #endregion
                
                setOnClickListener {
                    val selected = REACTIONS.getOrNull(index) ?: return@setOnClickListener
                    val newReaction = if (currentUserReaction == selected) null else selected
                    applyReaction(safePosition, newReaction)
                    currentPopupWindow?.dismiss()
                }
            }
            
            reactionContainer.addView(emojiButton)
        }
        
        // Measure the container to get its size
        reactionContainer.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        
        // Create PopupWindow
        val popupWindow = PopupWindow(
            reactionContainer,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(null)
            
            // #region agent log
            Log.d("DEBUG_REACTION_H5", "PopupWindow configured - elevation:${this.elevation}, isOutsideTouchable:${this.isOutsideTouchable}, isFocusable:${this.isFocusable}, background:${this.background}")
            // #endregion
        }
        
        // Store reference to dismiss it later
        currentPopupWindow = popupWindow
        
        // #region agent log
        Log.d("DEBUG_REACTION_H1", "Before showing popup - containerAlphaFinal:${reactionContainer.alpha}, containerChildCount:${reactionContainer.childCount}")
        // #endregion
        
        // Calculate position (above the anchor view, centered)
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val popupWidth = reactionContainer.measuredWidth
        val x = location[0] + (anchor.width / 2) - (popupWidth / 2)
        val y = location[1] - reactionContainer.measuredHeight - (8 * anchor.context.resources.displayMetrics.density).toInt()
        
        // Show popup
        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }
    
    private var currentPopupWindow: PopupWindow? = null
    
    private fun showReactionPopup(anchor: View, position: Int) {
        showReactionPopupForPosition(anchor, position)
    }

    private fun applyReaction(position: Int, reaction: String?) {
        if (position !in messages.indices) return
        val updated = messages[position]
        onReactionChanged?.invoke(updated, reaction)
    }

    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDateHeader: TextView = itemView.findViewById(R.id.tv_date_header)

        fun bind(message: ChatMessage) {
            tvDateHeader.text = message.dateHeaderText
        }
    }

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvReaction: TextView = itemView.findViewById(R.id.tv_reaction)

        fun bind(message: ChatMessage) {
            tvMessage.text = message.message
            tvTime.text = message.timestamp
            bindReaction(tvReaction, message)

            if (enableReactions) {
                itemView.setOnLongClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        showReactionPopup(tvMessage, pos)
                    }
                    true
                }
                itemView.isLongClickable = true
            } else {
                itemView.setOnLongClickListener(null)
            }
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvReaction: TextView = itemView.findViewById(R.id.tv_reaction)

        fun bind(message: ChatMessage) {
            tvMessage.text = message.message
            tvTime.text = message.timestamp
            bindReaction(tvReaction, message)

            if (enableReactions) {
                itemView.setOnLongClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        showReactionPopup(tvMessage, pos)
                    }
                    true
                }
                itemView.isLongClickable = true
            } else {
                itemView.setOnLongClickListener(null)
            }
        }
    }

    private fun bindReaction(reactionView: TextView, message: ChatMessage) {
        val reactions = message.reactions
        
        android.util.Log.d("ChatReactions", "bindReaction called for message ID: ${message.id}, reactions: $reactions")
        
        if (reactions.isEmpty()) {
            reactionView.visibility = View.GONE
            reactionView.text = ""
            reactionView.setOnClickListener(null)
            android.util.Log.d("ChatReactions", "Hiding reaction view for message ID: ${message.id}")
        } else {
            // Display all reactions (WhatsApp style: 👍❤️😂)
            // Group by emoji and show unique emojis
            val uniqueEmojis = reactions.values.distinct()
            val reactionText = uniqueEmojis.joinToString("")
            
            // Set circular background for the reaction emoji
            reactionView.setBackgroundResource(R.drawable.bg_reaction_circle)
            
            // Force full color emoji rendering
            reactionView.typeface = android.graphics.Typeface.DEFAULT
            
            // Set text color to black to ensure emojis render with full color
            reactionView.setTextColor(android.graphics.Color.BLACK)
            
            // Enable subpixel text rendering for crisp emoji display
            reactionView.paintFlags = android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG
            
            // Ensure full opacity - no transparency
            reactionView.alpha = 1.0f
            
            // Disable any text appearance that might affect emoji rendering
            reactionView.setTextAppearance(android.R.style.TextAppearance)
            
            // Set the emoji text (all unique reactions)
            reactionView.text = reactionText
            
            // Make visible
            reactionView.visibility = View.VISIBLE
            
            // Add click listener to show who reacted
            reactionView.setOnClickListener {
                // Get the emoji that was clicked (first emoji for now, or we can detect which one)
                // For simplicity, show all reactors grouped by emoji
                onReactionClick?.invoke(message, reactionText)
            }
            
            android.util.Log.d("ChatReactions", "Showing reactions '$reactionText' for message ID: ${message.id}, visibility: ${reactionView.visibility}")
        }
    }
}

