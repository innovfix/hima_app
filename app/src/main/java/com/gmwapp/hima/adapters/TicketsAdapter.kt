package com.gmwapp.hima.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.databinding.ItemTicketBinding
import com.gmwapp.hima.retrofit.responses.TicketDataResponse
import com.gmwapp.hima.utils.unescapeHelpContent
import java.text.SimpleDateFormat
import java.util.Locale

class TicketsAdapter(
    private val tickets: MutableList<TicketDataResponse>,
    private val onAttachmentClick: (List<String>) -> Unit
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
            
            // Message - unescape common escape sequences
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
            
            // Reply section - unescape common escape sequences
            val replyText = ticket.reply ?: ""
            if (replyText.isNotEmpty()) {
                binding.llReplySection.visibility = View.VISIBLE
                binding.tvReply.text = replyText.unescapeHelpContent()
            } else {
                binding.llReplySection.visibility = View.GONE
            }
            
            // Attachments shown here are the SUPPORT team's reply attachments
            // (reply_images), NOT the user's own screenshots — the user already
            // knows what they sent; what's useful to them is what support sent
            // back. Anything support attaches while resolving shows up here.
            val attachmentUrls = ticket.reply_images ?: emptyList()

            if (attachmentUrls.isNotEmpty()) {
                binding.llScreenshots.visibility = View.VISIBLE
                val attachmentText = if (attachmentUrls.size == 1) {
                    "1 attachment"
                } else {
                    "${attachmentUrls.size} attachments"
                }
                binding.tvScreenshotsCount.text = attachmentText

                // Make clickable to view attachments
                binding.llScreenshots.setOnClickListener {
                    onAttachmentClick(attachmentUrls)
                }
                binding.llScreenshots.isClickable = true
            } else {
                binding.llScreenshots.visibility = View.GONE
            }
        }
    }
}

