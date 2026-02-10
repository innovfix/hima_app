package com.gmwapp.hima.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.databinding.ItemCreatorWarningBinding
import com.gmwapp.hima.retrofit.responses.WarningItem
import java.text.SimpleDateFormat
import java.util.*

class CreatorWarningsAdapter(
    private val warnings: List<WarningItem>
) : RecyclerView.Adapter<CreatorWarningsAdapter.WarningViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WarningViewHolder {
        val binding = ItemCreatorWarningBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WarningViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WarningViewHolder, position: Int) {
        holder.bind(warnings[position])
    }

    override fun getItemCount(): Int = warnings.size

    class WarningViewHolder(private val binding: ItemCreatorWarningBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(warning: WarningItem) {
            binding.tvReason.text = warning.reason ?: "No reason provided"
            binding.tvAdminNotes.text = warning.adminNotes ?: "No additional notes"
            binding.tvActionType.text = warning.actionType?.uppercase() ?: "WARNING"
            
            // Format date and show at bottom
            warning.actionDate?.let {
                binding.tvDateBottom.text = formatDate(it)
            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                date?.let { outputFormat.format(it) } ?: dateString
            } catch (e: Exception) {
                dateString
            }
        }
    }
}
