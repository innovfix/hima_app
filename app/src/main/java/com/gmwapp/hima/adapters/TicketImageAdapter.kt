package com.gmwapp.hima.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gmwapp.hima.databinding.ItemTicketImageBinding

class TicketImageAdapter(
    private val images: MutableList<Uri>,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<TicketImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemTicketImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position], position)
    }

    override fun getItemCount(): Int = images.size

    inner class ImageViewHolder(private val binding: ItemTicketImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(imageUri: Uri, position: Int) {
            Glide.with(binding.root.context)
                .load(imageUri)
                .into(binding.ivTicketImage)

            binding.ivRemove.setOnClickListener {
                onRemoveClick(position)
            }
        }
    }
}







