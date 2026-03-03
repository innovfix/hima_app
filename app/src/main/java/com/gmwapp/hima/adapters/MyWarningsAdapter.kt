package com.gmwapp.hima.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.databinding.ItemMyWarningBinding
import com.gmwapp.hima.retrofit.responses.MyWarningItem

class MyWarningsAdapter(
    private val items: List<MyWarningItem>,
    private val showDetails: Boolean = false,
) : RecyclerView.Adapter<MyWarningsAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMyWarningBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], showDetails)
    }

    override fun getItemCount(): Int = items.size

    class VH(private val binding: ItemMyWarningBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MyWarningItem, showDetails: Boolean) {
            val levelTitleRaw = item.warningLevelTitle?.trim()
            val level = item.warningLevel
            val normalizedTitle = levelTitleRaw
                ?.replace(Regex("^Level\\s*\\d+\\s*:\\s*", RegexOption.IGNORE_CASE), "")
                ?.trim()

            // Use API-provided title for the badge when possible.
            val badgeText = normalizedTitle?.takeIf { it.isNotEmpty() }
                ?.let { "Level : $it" }
                ?: level?.let { "Level : $it" }
                ?: "Warning"
            binding.tvLevel.text = badgeText

            // In the "previous warnings" bottom sheet we show details; avoid repeating the title.
            if (showDetails) {
                binding.tvTitle.visibility = View.GONE
                binding.tvTitle.text = ""
            } else {
                binding.tvTitle.visibility = View.VISIBLE
                binding.tvTitle.text = levelTitleRaw?.takeIf { it.isNotEmpty() } ?: badgeText
            }

            val details = item.warningLevelDetails?.trim()
            if (showDetails && !details.isNullOrEmpty()) {
                binding.tvDetails.visibility = View.VISIBLE
                // If backend repeats the title as the first line, drop it to avoid duplication.
                val cleaned = if (!levelTitleRaw.isNullOrBlank() && details.startsWith(levelTitleRaw)) {
                    details.removePrefix(levelTitleRaw).trimStart('\n', ' ')
                } else {
                    details
                }
                binding.tvDetails.text = cleaned
            } else {
                binding.tvDetails.visibility = View.GONE
                binding.tvDetails.text = ""
            }

            // Don't hardcode extra policy strings; show only if backend sends details text.
            binding.tvExtra.visibility = View.GONE

            // Keep header clean in history cards: show only the level badge.
            binding.cardChipBlocked.visibility = View.GONE
            binding.cardChipWithdraw.visibility = View.GONE

            binding.tvDate.text = item.date ?: ""
        }
    }
}

