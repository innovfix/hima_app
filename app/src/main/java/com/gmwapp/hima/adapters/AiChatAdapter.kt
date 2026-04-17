package com.gmwapp.hima.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R

data class AiChatMessage(
    val text: String,
    val isUser: Boolean,
    val isTyping: Boolean = false
)

class AiChatAdapter(
    private val messages: MutableList<AiChatMessage> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_AI = 0
        private const val TYPE_USER = 1
        private const val TYPE_TYPING = 2
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
            else -> ChatViewHolder(inflater.inflate(R.layout.item_ai_chat_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ChatViewHolder) {
            holder.tvMessage.text = messages[position].text
        }
    }

    override fun getItemCount() = messages.size

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tv_chat_message)
    }

    class TypingViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
