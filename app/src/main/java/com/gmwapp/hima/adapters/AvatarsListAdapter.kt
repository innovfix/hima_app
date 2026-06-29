package com.gmwapp.hima.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.AdapterAvatarBinding
import com.gmwapp.hima.retrofit.responses.AvatarsListData


class AvatarsListAdapter(
    private val activity: Activity,
    private val avatarsListData: ArrayList<AvatarsListData?>,
) : RecyclerView.Adapter<AvatarsListAdapter.ItemHolder>() {

    private var selectedPosition: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        return ItemHolder(
            AdapterAvatarBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val item: AvatarsListData? = avatarsListData[position]

        Glide.with(activity)
            .load(item?.image)
            .centerCrop()
            .into(holder.binding.ivAvatar)

        val isSelected = position == selectedPosition
        val density = activity.resources.displayMetrics.density
        holder.binding.ivAvatarSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.binding.cardAvatar.strokeWidth =
            (if (isSelected) 3 * density else 0f).toInt()
        holder.binding.cardAvatar.strokeColor = ContextCompat.getColor(
            activity,
            if (isSelected) R.color.colorAccent else R.color.divider
        )
    }

    override fun getItemCount(): Int {
        return avatarsListData.size
    }

    fun setSelectedPosition(position: Int) {
        if (position !in 0 until itemCount || position == selectedPosition) return
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
    }

    class ItemHolder(val binding: AdapterAvatarBinding) :
        RecyclerView.ViewHolder(binding.root)
}
