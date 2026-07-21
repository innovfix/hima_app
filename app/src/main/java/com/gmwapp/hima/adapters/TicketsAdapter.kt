package com.gmwapp.hima.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.databinding.ItemTicketBinding
import com.gmwapp.hima.retrofit.responses.TicketDataResponse
import com.gmwapp.hima.utils.unescapeHelpContent
import java.text.SimpleDateFormat
import java.util.Locale

class TicketsAdapter(
    private val tickets: MutableList<TicketDataResponse>,
    private val onTicketClick: (TicketDataResponse) -> Unit
) : RecyclerView.Adapter<TicketsAdapter.TicketViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val binding = ItemTicketBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TicketViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        holder.bind(tickets[position])
    }

    override fun getItemCount(): Int = tickets.size

    inner class TicketViewHolder(private val binding: ItemTicketBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ticket: TicketDataResponse) {
            // Ticket ID
            binding.tvTicketId.text = "#${ticket.id}"

            // Status badge
            if (ticket.status == 0) {
                binding.tvStatusBadge.text = "Active"
                binding.tvStatusBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#E91E63") // Pink for active
                )
            } else {
                binding.tvStatusBadge.text = "Resolved"
                binding.tvStatusBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#4CAF50") // Green for resolved
                )
            }

            // Message preview (layout caps it at 2 lines + ellipsis)
            val messageText = ticket.message ?: ""
            binding.tvMessage.text = messageText.unescapeHelpContent()

            // Created date - format from "2025-11-05 12:11:14" to "5 Nov 2025"
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                val date = inputFormat.parse(ticket.created_at)
                binding.tvCreatedDate.text = date?.let { outputFormat.format(it) } ?: ticket.created_at
            } catch (e: Exception) {
                binding.tvCreatedDate.text = ticket.created_at
            }

            // Reply-status hint — the full reply + attachments live on the detail page.
            val hasReply = !ticket.reply.isNullOrBlank()
            if (hasReply) {
                binding.tvReplyHint.text = "✓ Support replied"
                binding.tvReplyHint.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            } else {
                binding.tvReplyHint.text = "Awaiting reply"
                binding.tvReplyHint.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            }

            // Whole card opens the detail page.
            binding.root.setOnClickListener { onTicketClick(ticket) }
        }
    }
}
