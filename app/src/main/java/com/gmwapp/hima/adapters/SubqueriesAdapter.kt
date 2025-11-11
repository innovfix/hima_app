package com.gmwapp.hima.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.databinding.ItemSubqueryBinding
import com.gmwapp.hima.retrofit.responses.SubqueryData

class SubqueriesAdapter(
    private val subqueries: MutableList<SubqueryData>,
    private val onItemClick: (SubqueryData) -> Unit
) : RecyclerView.Adapter<SubqueriesAdapter.SubqueryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubqueryViewHolder {
        val binding = ItemSubqueryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SubqueryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubqueryViewHolder, position: Int) {
        holder.bind(subqueries[position])
    }

    override fun getItemCount(): Int = subqueries.size

    inner class SubqueryViewHolder(private val binding: ItemSubqueryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(subquery: SubqueryData) {
            binding.tvSubqueryTitle.text = subquery.title
            binding.root.setOnClickListener {
                onItemClick(subquery)
            }
        }
    }
}






