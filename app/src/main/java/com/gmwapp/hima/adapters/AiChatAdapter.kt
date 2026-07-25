package com.gmwapp.hima.adapters

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gmwapp.hima.R
import com.gmwapp.hima.retrofit.responses.BotChip
import com.gmwapp.hima.retrofit.responses.MatchedCreator
import de.hdodenhof.circleimageview.CircleImageView

data class AiChatMessage(
    val text: String,
    val isUser: Boolean,
    val isTyping: Boolean = false,
    val creator: MatchedCreator? = null,
    val userPreview: String? = null,
    val skipTyping: Boolean = false,
    /**
     * Server-driven options rendered under this message (support bot only).
     * Defaulted to null so AI onboarding is completely unaffected.
     */
    val chips: List<BotChip>? = null,
    /** Local content:// URIs of the attachment(s) the user just picked, shown as
     *  small thumbnails on the user's side. Support bot only. */
    val attachmentUris: List<android.net.Uri>? = null
)

class AiChatAdapter(
    private val messages: MutableList<AiChatMessage> = mutableListOf(),
    /** Support bot only; null for AI onboarding. */
    private val onChipClick: ((BotChip) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_AI = 0
        private const val TYPE_USER = 1
        private const val TYPE_TYPING = 2
        private const val TYPE_CREATOR_DELIVERY = 3
        private const val TYPE_CHIPS = 4
        private const val TYPE_ATTACHMENT = 5
    }

    /** Chips are only ever tappable on the LAST message — an old row is history. */
    private var chipsEnabled = true

    /**
     * Must repaint the existing rows, not just flip the flag: without the
     * notify, an already-bound chip row keeps its listener and full opacity,
     * so a stale row stays tappable and posts a key the server has already
     * moved past.
     */
    fun setChipsEnabled(enabled: Boolean) {
        if (chipsEnabled == enabled) return
        chipsEnabled = enabled
        messages.forEachIndexed { i, m -> if (m.chips != null) notifyItemChanged(i) }
    }

    fun addMessage(message: AiChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    /** Wipe every row — used to rebuild a resumed transcript from scratch so a
     *  re-poll of the same session never stacks duplicate bubbles. */
    fun clear() {
        if (messages.isEmpty()) return
        messages.clear()
        notifyDataSetChanged()
    }

    fun showTyping() {
        if (messages.lastOrNull()?.isTyping == true) return
        messages.add(AiChatMessage("", isUser = false, isTyping = true))
        notifyItemInserted(messages.size - 1)
    }

    fun hideTyping() {
        val last = messages.lastOrNull()
        if (last?.isTyping == true) {
            val pos = messages.size - 1
            messages.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }

    fun getMessages() = messages.toList()

    /** Replace the whole transcript in one shot (support-bot cache restore) so a
     *  returning screen can paint the chat instantly instead of re-adding row by
     *  row or waiting on the network. */
    fun restore(newMessages: List<AiChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val m = messages[position]
        return when {
            m.creator != null -> TYPE_CREATOR_DELIVERY
            m.isTyping -> TYPE_TYPING
            m.attachmentUris != null -> TYPE_ATTACHMENT
            m.chips != null -> TYPE_CHIPS
            m.isUser -> TYPE_USER
            else -> TYPE_AI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> ChatViewHolder(inflater.inflate(R.layout.item_ai_chat_user, parent, false))
            TYPE_TYPING -> TypingViewHolder(inflater.inflate(R.layout.item_ai_chat_typing, parent, false))
            TYPE_CREATOR_DELIVERY -> CreatorDeliveryViewHolder(
                inflater.inflate(R.layout.item_ai_chat_creator_delivery, parent, false)
            )
            TYPE_CHIPS -> ChipsViewHolder(inflater.inflate(R.layout.item_ai_chat_chips, parent, false))
            TYPE_ATTACHMENT -> AttachmentViewHolder(inflater.inflate(R.layout.item_ai_chat_attachment, parent, false))
            else -> ChatViewHolder(inflater.inflate(R.layout.item_ai_chat_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChatViewHolder -> holder.tvMessage.text = messages[position].text
            is TypingViewHolder -> holder.start()
            is CreatorDeliveryViewHolder -> holder.bind(messages[position])
            is ChipsViewHolder -> holder.bind(
                messages[position],
                // Only the newest chip row stays live; tapping a stale one
                // would post a key the server has already moved past.
                enabled = chipsEnabled && position == messages.size - 1,
                onClick = onChipClick
            )
            is AttachmentViewHolder -> holder.bind(messages[position].attachmentUris)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is CreatorDeliveryViewHolder) holder.cancelAnimations()
        if (holder is TypingViewHolder) holder.stop()
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = messages.size

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tv_chat_message)
    }

    /** The attachment(s) the user just picked, shown as small rounded thumbnails
     *  on the user's side of the chat. Non-image URIs fall back to a placeholder. */
    class AttachmentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: android.widget.LinearLayout = view.findViewById(R.id.attachment_container)

        fun bind(uris: List<android.net.Uri>?) {
            container.removeAllViews()
            val list = uris ?: return
            val ctx = container.context
            val d = ctx.resources.displayMetrics.density
            val size = (72 * d).toInt()
            val gap = (6 * d).toInt()
            val radius = (10 * d).toInt()
            list.forEach { uri ->
                val iv = android.widget.ImageView(ctx)
                val lp = android.widget.LinearLayout.LayoutParams(size, size)
                lp.marginStart = gap
                iv.layoutParams = lp
                iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                Glide.with(ctx)
                    .load(uri)
                    .transform(com.bumptech.glide.load.resource.bitmap.RoundedCorners(radius))
                    .placeholder(R.drawable.bg_chat_ai_bubble)
                    .error(R.drawable.bg_chat_ai_bubble)
                    .into(iv)
                container.addView(iv)
            }
        }
    }

    class TypingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dots = listOf<View>(
            view.findViewById(R.id.dot1),
            view.findViewById(R.id.dot2),
            view.findViewById(R.id.dot3)
        )
        private val animators = mutableListOf<Animator>()

        /** Staggered bounce + fade so it reads as "support is typing…". */
        fun start() {
            stop()
            val lift = 5f * itemView.resources.displayMetrics.density // ~5dp hop
            dots.forEachIndexed { i, dot ->
                dot.alpha = 0.4f
                dot.translationY = 0f
                val anim = ObjectAnimator.ofPropertyValuesHolder(
                    dot,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -lift, 0f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.4f, 1f, 0.4f)
                ).apply {
                    duration = 900
                    startDelay = i * 160L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
                animators.add(anim)
            }
        }

        fun stop() {
            animators.toList().forEach { it.cancel() }
            animators.clear()
            dots.forEach { it.translationY = 0f; it.alpha = 1f }
        }
    }

    class ChipsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: com.google.android.flexbox.FlexboxLayout =
            view.findViewById(R.id.chips_container)

        fun bind(m: AiChatMessage, enabled: Boolean, onClick: ((BotChip) -> Unit)?) {
            container.removeAllViews()
            val chips = m.chips ?: return
            val ctx = container.context
            val inflater = LayoutInflater.from(ctx)

            chips.forEach { chip ->
                val tv = inflater.inflate(R.layout.item_bot_chip, container, false) as TextView
                tv.text = chip.label
                tv.isEnabled = enabled
                tv.alpha = if (enabled) 1f else 0.45f
                if (chip.ghost) {
                    // Secondary, NOT disabled. text_light_grey (#979797) on a
                    // dashed border read as unavailable — the opposite of what
                    // this chip is for; the spec requires it be reachable at
                    // every step. black_light on a pale fill keeps it clearly
                    // pressable while staying quieter than the pink options.
                    tv.setBackgroundResource(R.drawable.bg_bot_chip_ghost)
                    tv.setTextColor(ctx.getColor(R.color.black_light))
                }
                if (enabled) tv.setOnClickListener { onClick?.invoke(chip) }
                container.addView(tv)
            }
        }
    }

    class CreatorDeliveryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivAvatar: CircleImageView = view.findViewById(R.id.iv_creator_avatar)
        private val tvName: TextView = view.findViewById(R.id.tv_creator_name)
        private val tvPreview: TextView = view.findViewById(R.id.tv_message_preview)
        private val dotsContainer: View = view.findViewById(R.id.typing_dots_container)
        private val dot1: View = view.findViewById(R.id.dot1)
        private val dot2: View = view.findViewById(R.id.dot2)
        private val dot3: View = view.findViewById(R.id.dot3)
        private val tvSentLabel: TextView = view.findViewById(R.id.tv_sent_label)
        private val handler = Handler(Looper.getMainLooper())
        private val pending = mutableListOf<Runnable>()
        private val animators = mutableListOf<Animator>()

        fun bind(message: AiChatMessage) {
            cancelAnimations()
            val creator = message.creator ?: return
            val preview = message.userPreview.orEmpty()

            // Always start fully visible — RecyclerView's default item animator
            // handles the reveal; custom alpha tricks stuck cards at 0.
            itemView.alpha = 1f
            itemView.translationY = 0f

            tvName.text = com.gmwapp.hima.utils.DisplayName.clean(creator.name)
            if (!creator.image.isNullOrEmpty()) {
                Glide.with(itemView).load(creator.image).into(ivAvatar)
            } else {
                ivAvatar.setImageResource(R.drawable.small_profile)
            }

            if (message.skipTyping) {
                dotsContainer.visibility = View.GONE
                tvPreview.visibility = View.VISIBLE
                tvPreview.alpha = 0f
                tvPreview.text = preview
                ObjectAnimator.ofFloat(tvPreview, View.ALPHA, 0f, 1f).apply {
                    duration = 260
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                    animators.add(this)
                }
                tvSentLabel.visibility = View.VISIBLE
                animateSentBadge()
                return
            }

            dotsContainer.visibility = View.VISIBLE
            tvPreview.visibility = View.GONE
            tvSentLabel.visibility = View.GONE
            tvSentLabel.alpha = 0f
            tvSentLabel.scaleX = 0.7f
            tvSentLabel.scaleY = 0.7f
            tvPreview.text = ""

            startDotsPulse()

            post(1700) {
                stopDotsPulse()
                dotsContainer.visibility = View.GONE
                tvPreview.visibility = View.VISIBLE
                animateTypeIn(preview)
            }
        }

        private fun startDotsPulse() {
            val dots = listOf(dot1, dot2, dot3)
            dots.forEachIndexed { i, dot ->
                dot.alpha = 0.3f
                val anim = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.3f, 1f, 0.3f).apply {
                    duration = 1000
                    startDelay = (i * 180L)
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
                animators.add(anim)
            }
        }

        private fun stopDotsPulse() {
            animators.toList().forEach { it.cancel() }
            animators.clear()
        }

        private fun animateTypeIn(fullText: String) {
            if (fullText.isEmpty()) {
                post(500) { showSent() }
                return
            }
            // Pace the typing so it reads naturally: ~75ms/char, clamped to a
            // total of 2800–3600ms so very short or very long messages both
            // feel unhurried.
            val perChar = (3200L / fullText.length).coerceIn(55L, 95L)
            var charIndex = 0
            val typer = object : Runnable {
                override fun run() {
                    charIndex++
                    if (charIndex > fullText.length) {
                        post(650) { showSent() }
                        return
                    }
                    tvPreview.text = fullText.substring(0, charIndex)
                    handler.postDelayed(this, perChar)
                }
            }
            handler.post(typer)
            pending.add(typer)
        }

        private fun showSent() {
            tvSentLabel.visibility = View.VISIBLE
            animateSentBadge()
        }

        private fun animateSentBadge() {
            tvSentLabel.alpha = 0f
            tvSentLabel.scaleX = 0.7f
            tvSentLabel.scaleY = 0.7f
            val anim = tvSentLabel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(360)
                .setInterpolator(OvershootInterpolator(2f))
            anim.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    anim.setListener(null)
                }
            })
            anim.start()
        }

        private fun post(delayMs: Long, action: () -> Unit) {
            val r = Runnable { action() }
            handler.postDelayed(r, delayMs)
            pending.add(r)
        }

        fun cancelAnimations() {
            pending.forEach { handler.removeCallbacks(it) }
            pending.clear()
            animators.toList().forEach { it.cancel() }
            animators.clear()
            tvSentLabel.animate().cancel()
            // Guard against any lingering intermediate state
            itemView.alpha = 1f
            itemView.translationY = 0f
            tvSentLabel.alpha = 1f
            tvSentLabel.scaleX = 1f
            tvSentLabel.scaleY = 1f
        }
    }
}
