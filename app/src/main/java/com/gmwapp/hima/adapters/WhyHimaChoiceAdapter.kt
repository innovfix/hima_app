package com.gmwapp.hima.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ItemWhyHimaChoiceBinding

data class WhyHimaChoice(
    val key: String,
    val emoji: String,
    val text: String,
    var isSelected: Boolean = false
)

class WhyHimaChoiceAdapter(
    private val choices: List<WhyHimaChoice>,
    private val onChoiceSelected: (WhyHimaChoice) -> Unit
) : RecyclerView.Adapter<WhyHimaChoiceAdapter.ChoiceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChoiceViewHolder {
        val binding = ItemWhyHimaChoiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChoiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChoiceViewHolder, position: Int) {
        holder.bind(choices[position])
    }

    override fun getItemCount(): Int = choices.size

    inner class ChoiceViewHolder(
        private val binding: ItemWhyHimaChoiceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(choice: WhyHimaChoice) {
            binding.tvEmoji.text = choice.emoji
            binding.tvChoiceText.text = choice.text

            val context = binding.root.context
            if (choice.isSelected) {
                binding.cardChoice.strokeColor = context.getColor(R.color.pink)
                binding.cardChoice.strokeWidth = 6
                binding.cardChoice.setCardBackgroundColor(context.getColor(R.color.light_pink_bg))
                binding.ivCheck.visibility = View.VISIBLE
                binding.flEmoji.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.pink_extra_light))
            } else {
                binding.cardChoice.strokeColor = context.getColor(R.color.light_grey)
                binding.cardChoice.strokeWidth = 4
                binding.cardChoice.setCardBackgroundColor(context.getColor(R.color.white))
                binding.ivCheck.visibility = View.GONE
                binding.flEmoji.backgroundTintList = null
            }

            binding.cardChoice.setOnClickListener {
                choices.forEach { it.isSelected = false }
                choice.isSelected = true
                notifyDataSetChanged()
                onChoiceSelected(choice)
            }
        }
    }
}
