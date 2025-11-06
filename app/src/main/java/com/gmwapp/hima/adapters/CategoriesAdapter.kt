package com.gmwapp.hima.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.databinding.ItemCategoryBinding
import com.gmwapp.hima.retrofit.responses.CategoryData

class CategoriesAdapter(
    private val categories: MutableList<CategoryData>,
    private val onItemClick: (CategoryData) -> Unit
) : RecyclerView.Adapter<CategoriesAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: CategoryData) {
            binding.tvCategoryName.text = category.category
            
            // Show "Contact Support Team" text and change icon for "Other Queries"
            if (category.category.equals("Other Queries", ignoreCase = true)) {
                binding.tvContactSupport.visibility = android.view.View.VISIBLE
                // Change icon to chat bubble for "Other Queries"
                binding.ivCategoryIcon.setImageResource(com.gmwapp.hima.R.drawable.ic_chat_bubble)
            } else {
                binding.tvContactSupport.visibility = android.view.View.GONE
                // Default icon for other categories
                binding.ivCategoryIcon.setImageResource(com.gmwapp.hima.R.drawable.ic_help)
            }
            
            binding.root.setOnClickListener {
                onItemClick(category)
            }
        }
    }
}



