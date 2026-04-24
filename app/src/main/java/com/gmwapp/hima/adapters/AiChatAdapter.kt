package com.gmwapp.hima.adapters

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
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
import com.gmwapp.hima.retrofit.responses.MatchedCreator
import de.hdodenhof.circleimageview.CircleImageView

data class AiChatMessage(
    val text: String,
    val isUser: Boolean,
    val isTyping: Boolean = false,
    val creator: MatchedCreator? = null,
    val userPreview: String? = null,
    val skipTyping: Boolean = false
)

class AiChatAdapter(
    private val messages: MutableList<AiChatMessage> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_AI = 0
        private const val TYPE_USER = 1
        private const val TYPE_TYPING = 2
        private const val TYPE_CREATOR_DELIVERY = 3
    }

    fun addMessage(message: AiChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
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

    override fun getItemViewType(position: Int): Int {
        val m = messages[position]
        return when {
            m.creator != null -> TYPE_CREATOR_DELIVERY
            m.isTyping -> TYPE_TYPING
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
            else -> ChatViewHolder(inflater.inflate(R.layout.item_ai_chat_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChatViewHolder -> holder.tvMessage.text = messages[position].text
            is CreatorDeliveryViewHolder -> holder.bind(messages[position])
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is CreatorDeliveryViewHolder) holder.cancelAnimations()
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = messages.size

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tv_chat_message)
    }

    class TypingViewHolder(view: View) : RecyclerView.ViewHolder(view)

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

            tvName.text = creator.name
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
