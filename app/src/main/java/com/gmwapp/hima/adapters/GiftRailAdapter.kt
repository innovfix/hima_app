package com.gmwapp.hima.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gmwapp.hima.R
import com.gmwapp.hima.retrofit.responses.GiftData

class GiftRailAdapter(
    private val context: Context,
    private val onItemClicked: (GiftData) -> Unit
) : RecyclerView.Adapter<GiftRailAdapter.GiftRailViewHolder>() {

    private var giftList: List<GiftData> = mutableListOf()
    private var availableCoins: Int = Int.MAX_VALUE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftRailViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_gift_rail, parent, false)
        return GiftRailViewHolder(view)
    }

    override fun onBindViewHolder(holder: GiftRailViewHolder, position: Int) {
        val gift = giftList[position]

        Glide.with(context)
            .load(gift.gift_icon)
            .into(holder.ivGift)

        holder.tvCoinsAmount.text = gift.coins.toString()

        val affordable = availableCoins >= gift.coins
        holder.itemView.isEnabled = affordable
        holder.itemView.alpha = if (affordable) 1f else 0.45f

        holder.itemView.setOnClickListener {
            onItemClicked(gift)
        }
    }

    override fun getItemCount(): Int = giftList.size

    fun updateGiftList(newGiftList: List<GiftData>) {
        giftList = newGiftList
        notifyDataSetChanged()
    }

    fun setAvailableCoins(coins: Int) {
        if (availableCoins == coins) return
        availableCoins = coins
        notifyDataSetChanged()
    }

    inner class GiftRailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivGift: ImageView = itemView.findViewById(R.id.iv_gift)
        val tvCoinsAmount: TextView = itemView.findViewById(R.id.tv_coinsAmount)
    }
}
