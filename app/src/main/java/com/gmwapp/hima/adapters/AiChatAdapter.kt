package com.gmwapp.hima.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R

data class AiChatMessage(
    val text: String,
    val isUser: Boolean
)

class AiChatAdapter(
    private val messages: MutableList<AiChatMessage> = mutableListOf()
) : RecyclerView.Adapter<AiChatAdapter.ChatViewHolder>() {

    companion object {
        private const val TYPE_AI = 0
        private const val TYPE_USER = 1
    }

    fun addMessage(message: AiChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun getMessages() = messages.toList()

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layoutId = if (viewType == TYPE_USER) {
            R.layout.item_ai_chat_user
        } else {
            R.layout.item_ai_chat_ai
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.tvMessage.text = messages[position].text
    }

    override fun getItemCount() = messages.size

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tv_chat_message)
    }
}
