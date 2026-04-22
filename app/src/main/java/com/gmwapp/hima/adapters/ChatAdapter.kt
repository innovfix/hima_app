package com.gmwapp.hima.adapters

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gmwapp.hima.R
import com.gmwapp.hima.models.ChatMessage
import com.gmwapp.hima.utils.ChatAudioPlayer

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val enableReactions: Boolean = false,
    private val myUserId: Int = 0,
    private val onReactionChanged: ((ChatMessage, String?) -> Unit)? = null,
    private val onReactionClick: ((ChatMessage, String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT_TEXT = 1
        private const val VIEW_TYPE_RECEIVED_TEXT = 2
        private const val VIEW_TYPE_DATE_HEADER = 3
        private const val VIEW_TYPE_SENT_IMAGE = 4
        private const val VIEW_TYPE_RECEIVED_IMAGE = 5
        private const val VIEW_TYPE_SENT_AUDIO = 6
        private const val VIEW_TYPE_RECEIVED_AUDIO = 7
        private val REACTIONS = listOf("👍", "❤️", "😂", "😮", "🙏")
    }

    private var currentPopupWindow: PopupWindow? = null
    private lateinit var audioPlayer: ChatAudioPlayer

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        if (message.isDateHeader) return VIEW_TYPE_DATE_HEADER

        return when (message.messageType.lowercase()) {
            "image" -> if (message.isSentByMe) VIEW_TYPE_SENT_IMAGE else VIEW_TYPE_RECEIVED_IMAGE
            "audio" -> if (message.isSentByMe) VIEW_TYPE_SENT_AUDIO else VIEW_TYPE_RECEIVED_AUDIO
            else -> if (message.isSentByMe) VIEW_TYPE_SENT_TEXT else VIEW_TYPE_RECEIVED_TEXT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        ensureAudioPlayer(parent)
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE_HEADER -> DateHeaderViewHolder(
                inflater.inflate(R.layout.item_date_header, parent, false)
            )
            VIEW_TYPE_SENT_TEXT -> TextMessageViewHolder(
                inflater.inflate(R.layout.item_message_sent, parent, false),
                isSent = true
            )
            VIEW_TYPE_RECEIVED_TEXT -> TextMessageViewHolder(
                inflater.inflate(R.layout.item_message_received, parent, false),
                isSent = false
            )
            VIEW_TYPE_SENT_IMAGE -> ImageMessageViewHolder(
                inflater.inflate(R.layout.item_message_sent_image, parent, false),
                isSent = true
            )
            VIEW_TYPE_RECEIVED_IMAGE -> ImageMessageViewHolder(
                inflater.inflate(R.layout.item_message_received_image, parent, false),
                isSent = false
            )
            VIEW_TYPE_SENT_AUDIO -> AudioMessageViewHolder(
                inflater.inflate(R.layout.item_message_sent_audio, parent, false),
                isSent = true
            )
            else -> AudioMessageViewHolder(
                inflater.inflate(R.layout.item_message_received_audio, parent, false),
                isSent = false
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is DateHeaderViewHolder -> holder.bind(message)
            is TextMessageViewHolder -> holder.bind(message)
            is ImageMessageViewHolder -> holder.bind(message)
            is AudioMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (::audioPlayer.isInitialized) {
            audioPlayer.release()
        }
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun release() {
        if (::audioPlayer.isInitialized) {
            audioPlayer.release()
        }
    }

    fun showReactionPopupForPosition(anchor: View, position: Int) {
        val safePosition = position.takeIf { it in messages.indices } ?: return
        val message = messages[safePosition]
        val currentUserReaction = message.reactions[myUserId]

        val popupView = LayoutInflater.from(anchor.context)
            .inflate(R.layout.popup_reaction_horizontal, null)
        val reactionContainer = popupView.findViewById<LinearLayout>(R.id.ll_reaction_container)
            ?: popupView as LinearLayout

        REACTIONS.forEachIndexed { index, emoji ->
            val emojiButton = TextView(anchor.context).apply {
                text = emoji
                textSize = 32f
                gravity = Gravity.CENTER
                val padding = (8 * anchor.context.resources.displayMetrics.density).toInt()
                val horizontalPadding = (12 * anchor.context.resources.displayMetrics.density).toInt()
                setPadding(horizontalPadding, padding, horizontalPadding, padding)
                includeFontPadding = false
                setTextColor(Color.BLACK)
                alpha = 1.0f
                typeface = Typeface.DEFAULT
                paintFlags = android.graphics.Paint.ANTI_ALIAS_FLAG or
                    android.graphics.Paint.SUBPIXEL_TEXT_FLAG
                setOnClickListener {
                    val selected = REACTIONS.getOrNull(index) ?: return@setOnClickListener
                    val newReaction = if (currentUserReaction == selected) null else selected
                    applyReaction(safePosition, newReaction)
                    currentPopupWindow?.dismiss()
                }
            }
            reactionContainer.addView(emojiButton)
        }

        reactionContainer.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

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
        }

        currentPopupWindow = popupWindow
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val popupWidth = reactionContainer.measuredWidth
        val x = location[0] + (anchor.width / 2) - (popupWidth / 2)
        val y = location[1] - reactionContainer.measuredHeight -
            (8 * anchor.context.resources.displayMetrics.density).toInt()
        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }

    private fun applyReaction(position: Int, reaction: String?) {
        if (position !in messages.indices) return
        val updated = messages[position]
        onReactionChanged?.invoke(updated, reaction)
    }

    private fun ensureAudioPlayer(parent: ViewGroup) {
        if (::audioPlayer.isInitialized) return
        audioPlayer = ChatAudioPlayer(
            parent.context.applicationContext,
            onPlaybackStateChanged = { messageId ->
                notifyMessageChanged(messageId)
            },
            onProgressChanged = { messageId, _, _ ->
                notifyMessageChanged(messageId)
            }
        )
    }

    private fun notifyMessageChanged(messageId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index != -1) notifyItemChanged(index)
    }

    private fun bindLongClick(anchor: View, itemView: View, positionProvider: () -> Int) {
        if (enableReactions) {
            itemView.setOnLongClickListener {
                val pos = positionProvider()
                if (pos != RecyclerView.NO_POSITION && pos != -1) {
                    showReactionPopupForPosition(anchor, pos)
                }
                true
            }
            itemView.isLongClickable = true
        } else {
            itemView.setOnLongClickListener(null)
        }
    }

    private fun bindReaction(reactionView: TextView, message: ChatMessage) {
        val reactions = message.reactions
        if (reactions.isEmpty()) {
            reactionView.visibility = View.GONE
            reactionView.text = ""
            reactionView.setOnClickListener(null)
            return
        }

        val reactionText = reactions.values.distinct().joinToString("")
        reactionView.setBackgroundResource(R.drawable.bg_reaction_circle)
        reactionView.typeface = Typeface.DEFAULT
        reactionView.setTextColor(Color.BLACK)
        reactionView.paintFlags = android.graphics.Paint.ANTI_ALIAS_FLAG or
            android.graphics.Paint.SUBPIXEL_TEXT_FLAG
        reactionView.alpha = 1.0f
        reactionView.setTextAppearance(android.R.style.TextAppearance)
        reactionView.text = reactionText
        reactionView.visibility = View.VISIBLE
        reactionView.setOnClickListener {
            onReactionClick?.invoke(message, reactionText)
        }
    }

    private fun openImagePreview(source: String, anchor: View) {
        val dialog = Dialog(anchor.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = AppCompatImageView(anchor.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { dialog.dismiss() }
        }
        Glide.with(anchor).load(source).into(imageView)
        dialog.setContentView(imageView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.show()
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(minutes, seconds)
    }

    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDateHeader: TextView = itemView.findViewById(R.id.tv_date_header)

        fun bind(message: ChatMessage) {
            tvDateHeader.text = message.dateHeaderText
        }
    }

    inner class TextMessageViewHolder(
        itemView: View,
        private val isSent: Boolean
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvReaction: TextView = itemView.findViewById(R.id.tv_reaction)

        fun bind(message: ChatMessage) {
            tvMessage.text = message.message
            tvTime.text = message.timestamp
            bindReaction(tvReaction, message)
            bindLongClick(tvMessage, itemView) { bindingAdapterPosition }
            if (!isSent) {
                tvMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.chat_text_received))
            }
        }
    }

    inner class ImageMessageViewHolder(
        itemView: View,
        private val isSent: Boolean
    ) : RecyclerView.ViewHolder(itemView) {
        private val bubbleContainer: View = itemView.findViewById(R.id.bubble_container)
        private val imageView: ImageView = itemView.findViewById(R.id.iv_image)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvReaction: TextView = itemView.findViewById(R.id.tv_reaction)

        fun bind(message: ChatMessage) {
            val source = message.attachmentUrl ?: message.message
            tvTime.text = message.timestamp
            bindReaction(tvReaction, message)
            bindLongClick(bubbleContainer, itemView) { bindingAdapterPosition }

            Glide.with(itemView)
                .load(source)
                .placeholder(R.drawable.ic_photo_library)
                .error(R.drawable.ic_photo_library)
                .into(imageView)

            imageView.setOnClickListener {
                if (source.isNotBlank()) {
                    openImagePreview(source, itemView)
                }
            }
        }
    }

    inner class AudioMessageViewHolder(
        itemView: View,
        private val isSent: Boolean
    ) : RecyclerView.ViewHolder(itemView) {
        private val bubbleContainer: View = itemView.findViewById(R.id.bubble_container)
        private val ivPlayPause: ImageView = itemView.findViewById(R.id.iv_play_pause)
        private val progressAudio: ProgressBar = itemView.findViewById(R.id.progress_audio)
        private val tvDuration: TextView = itemView.findViewById(R.id.tv_duration)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvReaction: TextView = itemView.findViewById(R.id.tv_reaction)

        fun bind(message: ChatMessage) {
            val source = message.attachmentUrl
            val durationMs = audioPlayer.getDuration(message.id, message.audioDurationMs)
            val progressMs = audioPlayer.getProgress(message.id).coerceAtLeast(0)
            val isPlaying = audioPlayer.isPlaying(message.id)

            tvTime.text = message.timestamp
            if (durationMs <= 0) {
                tvDuration.text = "--:--"
                progressAudio.max = 100
                progressAudio.progress = 0
            } else {
                tvDuration.text = formatDuration(durationMs.toLong())
                progressAudio.max = durationMs.coerceAtLeast(1)
                progressAudio.progress = progressMs.coerceAtMost(progressAudio.max)
            }
            bindReaction(tvReaction, message)
            bindLongClick(bubbleContainer, itemView) { bindingAdapterPosition }

            ivPlayPause.setImageResource(if (isPlaying) R.drawable.pause else R.drawable.play)
            if (isSent) {
                ivPlayPause.setColorFilter(ContextCompat.getColor(itemView.context, R.color.white))
            } else {
                ivPlayPause.setColorFilter(ContextCompat.getColor(itemView.context, R.color.chat_list_call_disabled_text))
            }

            ivPlayPause.setOnClickListener {
                if (source.isNullOrBlank()) {
                    Toast.makeText(itemView.context, "Audio unavailable", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                audioPlayer.toggle(message.id, source) { error ->
                    Toast.makeText(itemView.context, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

