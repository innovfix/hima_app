package com.gmwapp.hima.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.models.AiChatMessage
import com.gmwapp.hima.models.MessageType
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponseData
import com.google.android.material.button.MaterialButton

class HimaAiChatAdapter(
    private val activity: Activity,
    private val messages: MutableList<AiChatMessage>,
    private val onPayClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var peopleData: List<FemaleUsersResponseData> = emptyList()
    private var sendingComplete = false

    companion object {
        const val TYPE_AI_RECEIVED = 0
        const val TYPE_USER_SENT = 1
        const val TYPE_TYPING = 2
        const val TYPE_PEOPLE = 3
        const val TYPE_PAY = 4
        const val TYPE_SENDING = 5
    }

    override fun getItemViewType(position: Int): Int {
        return when (messages[position].type) {
            MessageType.AI_RECEIVED -> TYPE_AI_RECEIVED
            MessageType.USER_SENT -> TYPE_USER_SENT
            MessageType.TYPING_INDICATOR -> TYPE_TYPING
            MessageType.PEOPLE_CARDS -> TYPE_PEOPLE
            MessageType.PAY_CTA -> TYPE_PAY
            MessageType.SENDING_STATUS -> TYPE_SENDING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_AI_RECEIVED -> AiReceivedViewHolder(
                inflater.inflate(R.layout.item_ai_chat_received, parent, false)
            )
            TYPE_USER_SENT -> UserSentViewHolder(
                inflater.inflate(R.layout.item_ai_chat_sent, parent, false)
            )
            TYPE_TYPING -> TypingViewHolder(
                inflater.inflate(R.layout.item_ai_chat_typing, parent, false)
            )
            TYPE_PEOPLE -> PeopleViewHolder(
                inflater.inflate(R.layout.item_ai_chat_people, parent, false)
            )
            TYPE_PAY -> PayViewHolder(
                inflater.inflate(R.layout.item_ai_chat_pay, parent, false)
            )
            TYPE_SENDING -> SendingViewHolder(
                inflater.inflate(R.layout.item_ai_chat_received, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is AiReceivedViewHolder -> holder.bind(message)
            is UserSentViewHolder -> holder.bind(message)
            is TypingViewHolder -> holder.startAnimation()
            is PeopleViewHolder -> holder.bind()
            is PayViewHolder -> holder.bind()
            is SendingViewHolder -> holder.bind(sendingComplete)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is TypingViewHolder) {
            holder.stopAnimation()
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: AiChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun removeLastTypingIndicator(): Boolean {
        val index = messages.indexOfLast { it.type == MessageType.TYPING_INDICATOR }
        if (index != -1) {
            messages.removeAt(index)
            notifyItemRemoved(index)
            return true
        }
        return false
    }

    fun setPeopleData(data: List<FemaleUsersResponseData>) {
        peopleData = data
    }

    fun markSendingComplete() {
        sendingComplete = true
        val index = messages.indexOfLast { it.type == MessageType.SENDING_STATUS }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    // -- ViewHolders --

    inner class AiReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)

        fun bind(message: AiChatMessage) {
            tvMessage.text = message.text
        }
    }

    inner class UserSentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)

        fun bind(message: AiChatMessage) {
            tvMessage.text = message.text
        }
    }

    inner class TypingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dot1: View = itemView.findViewById(R.id.dot1)
        private val dot2: View = itemView.findViewById(R.id.dot2)
        private val dot3: View = itemView.findViewById(R.id.dot3)

        fun startAnimation() {
            val anim1 = AnimationUtils.loadAnimation(itemView.context, R.anim.typing_dot_bounce)
            val anim2 = AnimationUtils.loadAnimation(itemView.context, R.anim.typing_dot_bounce)
            val anim3 = AnimationUtils.loadAnimation(itemView.context, R.anim.typing_dot_bounce)

            anim2.startOffset = 150
            anim3.startOffset = 300

            dot1.startAnimation(anim1)
            dot2.startAnimation(anim2)
            dot3.startAnimation(anim3)
        }

        fun stopAnimation() {
            dot1.clearAnimation()
            dot2.clearAnimation()
            dot3.clearAnimation()
        }
    }

    inner class PeopleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rvPeople: RecyclerView = itemView.findViewById(R.id.rv_inline_people)

        fun bind() {
            if (rvPeople.adapter == null) {
                rvPeople.layoutManager =
                    LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                rvPeople.adapter = SuggestedPeopleAdapter(activity, peopleData)
            }
        }
    }

    inner class PayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val btnPay: MaterialButton = itemView.findViewById(R.id.btn_inline_pay)

        fun bind() {
            btnPay.setOnClickListener { onPayClicked() }
        }
    }

    inner class SendingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)

        fun bind(complete: Boolean) {
            if (complete) {
                tvMessage.text = activity.getString(R.string.hima_ai_sent)
            } else {
                tvMessage.text = activity.getString(R.string.hima_ai_sending)
            }
        }
    }
}
