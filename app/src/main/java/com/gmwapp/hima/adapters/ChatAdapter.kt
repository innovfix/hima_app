package com.gmwapp.hima.adapters

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
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
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.gmwapp.hima.R
import com.gmwapp.hima.models.ChatMessage
import com.gmwapp.hima.models.MessageDeliveryStatus
import com.gmwapp.hima.utils.ChatAudioPlayer
import com.gmwapp.hima.utils.showAppToast

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val enableReactions: Boolean = false,
    private val myUserId: Int = 0,
    private val onReactionChanged: ((ChatMessage, String?) -> Unit)? = null,
    private val onReactionClick: ((ChatMessage, String) -> Unit)? = null,
    /** If set, long-press opens this menu instead of jumping straight to reactions. */
    private val onMessageLongPress: ((anchor: View, message: ChatMessage, position: Int) -> Unit)? = null,
    private val onReplyQuoteTap: ((ChatMessage) -> Unit)? = null,
    /**
     * CHAT-047: tap on an image bubble. When set, the adapter delegates to the
     * host instead of opening the legacy bare-dialog full-screen image — the
     * host launches FullscreenImageActivity with peer/timestamp/message-id and
     * wires the Reply/React result back into the in-thread reply/react flow.
     */
    private val onImageBubbleTap: ((ChatMessage) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    companion object {
        const val PAYLOAD_AUDIO_PROGRESS = "audio_progress"
        private const val VIEW_TYPE_SENT_TEXT = 1
        private const val VIEW_TYPE_RECEIVED_TEXT = 2
        private const val VIEW_TYPE_DATE_HEADER = 3
        private const val VIEW_TYPE_SENT_IMAGE = 4
        private const val VIEW_TYPE_RECEIVED_IMAGE = 5
        private const val VIEW_TYPE_SENT_AUDIO = 6
        private const val VIEW_TYPE_RECEIVED_AUDIO = 7
        private val REACTIONS = listOf("👍", "❤️", "😂", "😮", "🙏")

        /** CHAT-127: line cap before the Read more toggle appears. Matches the
         *  `maxLines` set on tv_message in item_message_*.xml — keep in sync. */
        private const val COLLAPSED_LINE_LIMIT = 10
    }

    private var currentPopupWindow: PopupWindow? = null
    private lateinit var audioPlayer: ChatAudioPlayer

    /**
     * CHAT-127: per-message expanded state for the Read more / Show less toggle.
     * Kept process-local — WhatsApp behavior is to collapse on app restart,
     * which falls out naturally because the set lives with the adapter.
     */
    private val expandedMessageIds = mutableSetOf<String>()

    /** Parsed breakdown of an inline reply-prefixed message; display-time only. */
    private data class InlineReply(val author: String, val snippet: String, val body: String)

    /**
     * Splits `↩ Author: snippet\n<body>` into its three parts. Returns null for regular
     * text so the caller can fall back to rendering the message verbatim. The checks
     * (leading arrow glyph, `": "` separator in the header, non-empty body on the next
     * line) are strict on purpose: they keep normal messages that happen to contain a
     * colon from being mis-rendered as quoted replies. Kept adapter-local so the
     * `ChatMessage` model doesn't need new fields.
     */
    private fun parseInlineReply(raw: String): InlineReply? {
        val nl = raw.indexOf('\n')
        if (nl <= 0) return null
        val head = raw.substring(0, nl)
        if (!head.startsWith("↩ ")) return null
        val afterArrow = head.removePrefix("↩ ")
        val colon = afterArrow.indexOf(": ")
        if (colon <= 0) return null
        val author = afterArrow.substring(0, colon).trim()
        val snippet = afterArrow.substring(colon + 2).trim()
        val body = raw.substring(nl + 1)
        if (author.isEmpty() || body.isEmpty()) return null
        return InlineReply(author, snippet, body)
    }

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

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() &&
            holder is AudioMessageViewHolder &&
            payloads.contains(PAYLOAD_AUDIO_PROGRESS)
        ) {
            holder.updateProgressOnly(messages[position])
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemId(position: Int): Long {
        val m = messages[position]
        return m.id.hashCode().toLong() xor if (m.isDateHeader) 0xD000L else 0L
    }

    override fun getItemCount(): Int = messages.size

    fun isDateHeaderPosition(position: Int): Boolean =
        messages.getOrNull(position)?.isDateHeader == true

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
        if (message.isDateHeader || message.isDeleted) return
        currentPopupWindow?.dismiss()
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
                val index = messages.indexOfFirst { it.id == messageId }
                if (index != -1) notifyItemChanged(index, PAYLOAD_AUDIO_PROGRESS)
            }
        )
    }

    private fun notifyMessageChanged(messageId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index != -1) notifyItemChanged(index)
    }

    private fun bindLongClick(anchor: View, itemView: View, positionProvider: () -> Int) {
        val listener: View.OnLongClickListener? = when {
            onMessageLongPress != null -> View.OnLongClickListener {
                val pos = positionProvider()
                if (pos == RecyclerView.NO_POSITION || pos == -1) return@OnLongClickListener false
                val msg = messages.getOrNull(pos) ?: return@OnLongClickListener false
                if (msg.isDateHeader) return@OnLongClickListener false
                onMessageLongPress.invoke(anchor, msg, pos)
                true
            }
            enableReactions -> View.OnLongClickListener {
                val pos = positionProvider()
                if (pos != RecyclerView.NO_POSITION && pos != -1) {
                    showReactionPopupForPosition(anchor, pos)
                }
                true
            }
            else -> null
        }

        if (listener == null) {
            itemView.setOnLongClickListener(null)
            itemView.isLongClickable = false
            if (anchor !== itemView) {
                anchor.setOnLongClickListener(null)
                anchor.isLongClickable = false
            }
            return
        }

        itemView.setOnLongClickListener(listener)
        itemView.isLongClickable = true
        // CHAT-098: the anchor (the text view, or the image/audio bubble
        // container) is often `isLongClickable=true` for selection or to claim
        // touches inside the bubble. Android treats long-clickable views as
        // clickable for hit testing, so the inner view captures the DOWN event
        // and the long-press dispatched there never bubbles up to the row's
        // listener — long-press on the actual message text used to do nothing,
        // only the padding around the bubble worked. Attaching the SAME
        // listener to the anchor closes that gap without needing a separate
        // per-view handler in each ViewHolder.
        if (anchor !== itemView) {
            anchor.setOnLongClickListener(listener)
            anchor.isLongClickable = true
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

        // T39: show per-emoji counts so two users tapping 👍 render as "👍 2"
        // instead of a single 👍 (which loses the per-user signal).
        // Sort by canonical REACTIONS order so the chip stays stable across refreshes.
        val counts = reactions.values.groupingBy { it }.eachCount()
        val reactionText = counts.entries
            .sortedBy { entry ->
                REACTIONS.indexOf(entry.key).let { idx -> if (idx == -1) Int.MAX_VALUE else idx }
            }
            .joinToString(" ") { entry ->
                if (entry.value > 1) "${entry.key} ${entry.value}" else entry.key
            }
        reactionView.setBackgroundResource(R.drawable.bg_reaction_circle)
        reactionView.typeface = Typeface.DEFAULT
        reactionView.setTextColor(ContextCompat.getColor(reactionView.context, R.color.chat_text_received))
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

    private fun bindDeliveryIndicator(itemView: View, message: ChatMessage) {
        val pb = itemView.findViewById<ProgressBar>(R.id.pb_send_pending)
        val iv = itemView.findViewById<ImageView>(R.id.iv_send_delivery)
        if (pb == null || iv == null) return

        if (!message.isSentByMe) {
            pb.visibility = View.GONE
            iv.visibility = View.GONE
            return
        }

        val ctx = itemView.context
        when (message.deliveryStatus) {
            MessageDeliveryStatus.SENDING -> {
                pb.visibility = View.VISIBLE
                iv.visibility = View.GONE
                iv.imageTintList = null
            }
            MessageDeliveryStatus.SENT -> {
                pb.visibility = View.GONE
                iv.visibility = View.VISIBLE
                iv.setImageDrawable(
                    ContextCompat.getDrawable(ctx, R.drawable.ic_chat_single_check)?.mutate()
                )
                iv.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.chat_tick_on_bubble)
                )
            }
            MessageDeliveryStatus.DELIVERED -> {
                pb.visibility = View.GONE
                iv.visibility = View.VISIBLE
                iv.setImageDrawable(
                    ContextCompat.getDrawable(ctx, R.drawable.ic_chat_double_check)?.mutate()
                )
                iv.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.chat_tick_on_bubble)
                )
            }
            MessageDeliveryStatus.READ -> {
                pb.visibility = View.GONE
                iv.visibility = View.VISIBLE
                iv.setImageDrawable(
                    ContextCompat.getDrawable(ctx, R.drawable.ic_chat_double_check)?.mutate()
                )
                iv.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.chat_read_receipt)
                )
            }
        }
    }

    private fun clearDeliveryIndicator(itemView: View) {
        itemView.findViewById<ProgressBar>(R.id.pb_send_pending)?.visibility = View.GONE
        itemView.findViewById<ImageView>(R.id.iv_send_delivery)?.visibility = View.GONE
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
        private val tvReadMore: TextView = itemView.findViewById(R.id.tv_read_more)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvReaction: TextView = itemView.findViewById(R.id.tv_reaction)
        private val layoutReplyQuote: View = itemView.findViewById(R.id.layout_reply_quote)
        private val tvReplyQuoteAuthor: TextView =
            itemView.findViewById(R.id.tv_reply_quote_author)
        private val tvReplyQuoteSnippet: TextView =
            itemView.findViewById(R.id.tv_reply_quote_snippet)
        private val vReplyQuoteStrip: View = itemView.findViewById(R.id.v_reply_quote_strip)

        fun bind(message: ChatMessage) {
            if (message.isDeleted) {
                // Tombstone: italic, dimmed, no reactions/long-press/delivery ticks,
                // no inline reply quote (the original body is irrelevant now).
                layoutReplyQuote.visibility = View.GONE
                layoutReplyQuote.setOnClickListener(null)
                clearDeliveryIndicator(itemView)
                tvMessage.text = itemView.context.getString(R.string.chat_message_deleted_tombstone)
                tvMessage.setTypeface(tvMessage.typeface, Typeface.ITALIC)
                tvMessage.alpha = 0.6f
                tvTime.text = message.timestamp
                tvReaction.visibility = View.GONE
                itemView.setOnLongClickListener(null)
                itemView.isLongClickable = false
                tvMessage.setOnLongClickListener(null)
                tvMessage.isLongClickable = false
                // Tombstone is always a single short line — no Read more needed.
                tvReadMore.visibility = View.GONE
                tvReadMore.setOnClickListener(null)
                return
            }

            // Reset any tombstone styling carried over by recycling.
            tvMessage.setTypeface(Typeface.create(tvMessage.typeface, Typeface.NORMAL), Typeface.NORMAL)
            tvMessage.alpha = 1f
            itemView.isLongClickable = true
            tvMessage.isLongClickable = true

            val reply = parseInlineReply(message.message)
            if (reply != null) {
                layoutReplyQuote.visibility = View.VISIBLE
                tvReplyQuoteAuthor.text = reply.author
                tvReplyQuoteSnippet.text = reply.snippet
                tvMessage.text = reply.body
                applyQuoteTint(isSent)
                layoutReplyQuote.setOnClickListener { onReplyQuoteTap?.invoke(message) }
            } else {
                layoutReplyQuote.visibility = View.GONE
                layoutReplyQuote.setOnClickListener(null)
                tvMessage.text = message.message
            }
            // CHAT-048: auto-detect URLs / phone numbers / emails after setText.
            // LinkMovementMethod lets taps open browser/dialer/mail; long-press
            // still bubbles up to the bubble's long-click handler.
            Linkify.addLinks(
                tvMessage,
                Linkify.WEB_URLS or Linkify.PHONE_NUMBERS or Linkify.EMAIL_ADDRESSES
            )
            tvMessage.movementMethod = LinkMovementMethod.getInstance()
            tvMessage.setLinkTextColor(Color.parseColor("#880E4F"))
            tvTime.text = message.timestamp
            bindReaction(tvReaction, message)
            bindLongClick(tvMessage, itemView) { bindingAdapterPosition }
            if (!isSent) {
                tvMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.chat_text_received))
            } else {
                bindDeliveryIndicator(itemView, message)
            }
            applyReadMoreState(message)
        }

        /**
         * CHAT-127: collapse messages past [COLLAPSED_LINE_LIMIT] lines and show a
         * Read more / Show less toggle. The toggle is also wired to long-press so
         * users can still bring up the reactions menu without tap-eating the
         * expand action.
         */
        private fun applyReadMoreState(message: ChatMessage) {
            val expanded = expandedMessageIds.contains(message.id)
            if (expanded) {
                tvMessage.maxLines = Integer.MAX_VALUE
                tvMessage.ellipsize = null
            } else {
                tvMessage.maxLines = COLLAPSED_LINE_LIMIT
                tvMessage.ellipsize = android.text.TextUtils.TruncateAt.END
            }

            // Decide visibility AFTER layout so we can read tv_message.layout's
            // ellipsis count. Hide eagerly to avoid a flash on short messages.
            tvReadMore.visibility = View.GONE
            tvReadMore.setOnClickListener(null)
            tvMessage.post {
                val layout = tvMessage.layout ?: return@post
                val truncated = if (expanded) {
                    // Once expanded, we still want Show less visible so the user
                    // can collapse. Cheap heuristic: line count > limit.
                    layout.lineCount > COLLAPSED_LINE_LIMIT
                } else {
                    val last = (layout.lineCount - 1).coerceAtLeast(0)
                    layout.getEllipsisCount(last) > 0
                }
                if (!truncated) {
                    tvReadMore.visibility = View.GONE
                    return@post
                }
                tvReadMore.visibility = View.VISIBLE
                tvReadMore.text = itemView.context.getString(
                    if (expanded) R.string.chat_show_less else R.string.chat_read_more
                )
                tvReadMore.setOnClickListener {
                    if (expandedMessageIds.contains(message.id)) {
                        expandedMessageIds.remove(message.id)
                    } else {
                        expandedMessageIds.add(message.id)
                    }
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        notifyItemChanged(pos)
                    }
                }
            }
        }

        /**
         * Retints the shared quote block so it reads correctly on both sender (pink) and
         * receiver (white) bubbles — different strip color, author color, and a softer
         * background overlay on each side.
         */
        private fun applyQuoteTint(isSent: Boolean) {
            val ctx = itemView.context
            val stripColorRes = if (isSent)
                R.color.chat_reply_quote_strip
            else
                R.color.chat_reply_quote_strip_received
            val authorColorRes = if (isSent)
                R.color.chat_reply_quote_author
            else
                R.color.chat_reply_quote_author_received
            val bgColorRes = if (isSent)
                R.color.chat_reply_quote_bg_sent
            else
                R.color.chat_reply_quote_bg_received

            vReplyQuoteStrip.setBackgroundColor(ContextCompat.getColor(ctx, stripColorRes))
            tvReplyQuoteAuthor.setTextColor(ContextCompat.getColor(ctx, authorColorRes))
            // Overlay color tint: leave the drawable shape (rounded corners) but override
            // its solid fill so one drawable serves both bubble sides.
            layoutReplyQuote.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, bgColorRes))
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
        private val tvTombstone: TextView? = itemView.findViewById(R.id.tv_tombstone)
        // Sent image has a chip LinearLayout around the time; received image has time as a
        // standalone TextView (= tvTime). Either way we hide it when deleted.
        private val layoutImageTimeChip: View? = itemView.findViewById(R.id.layout_image_time_chip)

        fun bind(message: ChatMessage) {
            if (message.isDeleted) {
                imageView.visibility = View.GONE
                imageView.setOnClickListener(null)
                imageView.setOnLongClickListener(null)
                layoutImageTimeChip?.visibility = View.GONE
                clearDeliveryIndicator(itemView)
                tvTime.visibility = View.GONE
                tvReaction.visibility = View.GONE
                tvTombstone?.let {
                    it.visibility = View.VISIBLE
                    it.text = itemView.context.getString(R.string.chat_message_deleted_tombstone)
                }
                itemView.setOnLongClickListener(null)
                itemView.isLongClickable = false
                bubbleContainer.setOnLongClickListener(null)
                bubbleContainer.isLongClickable = false
                return
            }

            // Reset in case this holder was previously bound to a deleted row.
            imageView.visibility = View.VISIBLE
            layoutImageTimeChip?.visibility = View.VISIBLE
            tvTime.visibility = View.VISIBLE
            tvTombstone?.visibility = View.GONE
            itemView.isLongClickable = true
            bubbleContainer.isLongClickable = true

            val source = message.attachmentUrl ?: message.message
            tvTime.text = message.timestamp
            bindReaction(tvReaction, message)
            bindLongClick(bubbleContainer, itemView) { bindingAdapterPosition }
            // CHAT-014 / CHAT-050: the imageView's setOnClickListener (below)
            // makes it clickable, which swallows long-press before it can bubble
            // up to itemView. Re-route long-press here so it reaches the same
            // popup-menu handler bindLongClick uses.
            imageView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos == -1) return@setOnLongClickListener false
                val msg = messages.getOrNull(pos) ?: return@setOnLongClickListener false
                if (msg.isDateHeader) return@setOnLongClickListener false
                onMessageLongPress?.invoke(bubbleContainer, msg, pos)
                true
            }

            Glide.with(itemView)
                .load(source)
                .placeholder(R.drawable.ic_photo_library)
                .error(R.drawable.ic_image_broken)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.w("ChatAdapter", "image load failed for message=${message.id}: ${e?.message}")
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean = false
                })
                .into(imageView)

            imageView.setOnClickListener {
                // T21: dropped the redundant Glide.into(imageView) — the bubble
                // already has the bitmap from the bind above; re-loading on tap
                // triggered a second network fetch.
                // CHAT-047: route to the host so it can open FullscreenImageActivity
                // with peer + timestamp chrome; fall back to the legacy bare
                // full-screen dialog when no host handler is provided.
                val host = onImageBubbleTap
                if (host != null) {
                    host.invoke(message)
                } else if (source.isNotBlank()) {
                    openImagePreview(source, itemView)
                }
            }
            if (isSent) {
                bindDeliveryIndicator(itemView, message)
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
        private val tvTombstone: TextView? = itemView.findViewById(R.id.tv_tombstone)
        private val layoutAudioPlayback: View? = itemView.findViewById(R.id.layout_audio_playback)
        // Sent audio wraps the time row in a LinearLayout (for progress+ticks); received
        // audio keeps time as a standalone TextView.
        private val layoutAudioTime: View? = itemView.findViewById(R.id.layout_audio_time)

        fun bind(message: ChatMessage) {
            if (message.isDeleted) {
                layoutAudioPlayback?.visibility = View.GONE
                layoutAudioTime?.visibility = View.GONE
                clearDeliveryIndicator(itemView)
                tvTime.visibility = View.GONE
                tvReaction.visibility = View.GONE
                ivPlayPause.setOnClickListener(null)
                ivPlayPause.setOnLongClickListener(null)
                tvTombstone?.let {
                    it.visibility = View.VISIBLE
                    it.text = itemView.context.getString(R.string.chat_message_deleted_tombstone)
                }
                itemView.setOnLongClickListener(null)
                itemView.isLongClickable = false
                bubbleContainer.setOnLongClickListener(null)
                bubbleContainer.isLongClickable = false
                return
            }

            // Reset view state after recycling a previously-deleted row.
            layoutAudioPlayback?.visibility = View.VISIBLE
            layoutAudioTime?.visibility = View.VISIBLE
            tvTime.visibility = View.VISIBLE
            tvTombstone?.visibility = View.GONE
            itemView.isLongClickable = true
            bubbleContainer.isLongClickable = true

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
            // CHAT-050: ivPlayPause has setOnClickListener (below) so long-press
            // on the play button is eaten before reaching itemView. Re-route it
            // to the same popup-menu handler so long-press works everywhere on
            // the audio bubble, not just on the non-button areas.
            ivPlayPause.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos == -1) return@setOnLongClickListener false
                val msg = messages.getOrNull(pos) ?: return@setOnLongClickListener false
                if (msg.isDateHeader) return@setOnLongClickListener false
                onMessageLongPress?.invoke(bubbleContainer, msg, pos)
                true
            }

            ivPlayPause.setImageResource(if (isPlaying) R.drawable.pause else R.drawable.play)
            if (isSent) {
                ivPlayPause.setColorFilter(ContextCompat.getColor(itemView.context, R.color.chat_text_sent))
            } else {
                ivPlayPause.setColorFilter(ContextCompat.getColor(itemView.context, R.color.chat_list_call_disabled_text))
            }

            ivPlayPause.setOnClickListener {
                if (source.isNullOrBlank()) {
                    itemView.context.showAppToast(
                        itemView.context.getString(R.string.chat_audio_unavailable),
                        Toast.LENGTH_SHORT
                    )
                    return@setOnClickListener
                }
                audioPlayer.toggle(message.id, source) { error ->
                    itemView.context.showAppToast(error, Toast.LENGTH_SHORT)
                }
            }
            if (isSent) {
                bindDeliveryIndicator(itemView, message)
            }
        }

        fun updateProgressOnly(message: ChatMessage) {
            val durationMs = audioPlayer.getDuration(message.id, message.audioDurationMs)
            val progressMs = audioPlayer.getProgress(message.id).coerceAtLeast(0)
            if (durationMs > 0) {
                progressAudio.max = durationMs.coerceAtLeast(1)
                progressAudio.progress = progressMs.coerceAtMost(progressAudio.max)
            } else {
                progressAudio.max = 100
                progressAudio.progress = 0
            }
            tvDuration.text = if (durationMs <= 0) "--:--" else formatDuration(durationMs.toLong())
            ivPlayPause.setImageResource(
                if (audioPlayer.isPlaying(message.id)) R.drawable.pause else R.drawable.play
            )
            if (isSent) {
                ivPlayPause.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.chat_text_sent)
                )
            } else {
                ivPlayPause.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.chat_list_call_disabled_text)
                )
            }
        }
    }
}
