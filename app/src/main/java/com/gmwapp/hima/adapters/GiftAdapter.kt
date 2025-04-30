package com.gmwapp.hima.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gmwapp.hima.R
import com.gmwapp.hima.retrofit.responses.GiftData

class GiftAdapter(private val context: Context,     private val onItemClicked: (GiftData) -> Unit) : RecyclerView.Adapter<GiftAdapter.GiftViewHolder>() {

    private var giftList: List<GiftData> = mutableListOf()


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_gifts, parent, false)
        return GiftViewHolder(view)
    }

    override fun onBindViewHolder(holder: GiftViewHolder, position: Int) {
        val gift = giftList[position]
        Glide.with(context)
            .load(gift.gift_icon)  // Loading the gift image URL
            .into(holder.ivGift)

        holder.tvCoinsAmount.text = gift.coins.toString()  // Setting the coin amount
        holder.itemView.setOnClickListener {
            onItemClicked(gift)  // <--- Here we call the click listener
        }
    }

    override fun getItemCount(): Int = giftList.size

    fun updateGiftList(newGiftList: List<GiftData>) {
        giftList = newGiftList
        Log.d("GiftAdapter", "Updated gift list: ${giftList.size} items")

        notifyDataSetChanged()  // Notify RecyclerView that the data has changed
    }

    inner class GiftViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivGift: ImageView = itemView.findViewById(R.id.iv_gift)
        val tvCoinsAmount: TextView = itemView.findViewById(R.id.tv_coinsAmount)
    }
}
