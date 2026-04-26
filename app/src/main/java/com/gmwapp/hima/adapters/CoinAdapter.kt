package com.gmwapp.hima.adapters

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.AdapterCoinBinding
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.utils.setOnSingleClickListener


class CoinAdapter(
    val activity: Activity,
    private val coins: ArrayList<CoinsResponseData>,
    val onItemSelectionListener: OnItemSelectionListener<CoinsResponseData>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemHolder = ItemHolder(
            AdapterCoinBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        return itemHolder
    }

    override fun onBindViewHolder(holderParent: RecyclerView.ViewHolder, position: Int) {
        val holder: ItemHolder = holderParent as ItemHolder
        val coin: CoinsResponseData = coins[position]


        Log.d("save percent","${coin.save}")

        // Set the default selected item to position 0
        if (position == 0 && coins.none { it.isSelected == true }) {
            coin.isSelected = true
        }

        // Show label if not empty, otherwise show based on popular field
        if (!coin.label.isNullOrEmpty()) {
            holder.binding.tvPopular.visibility = View.VISIBLE
            holder.binding.tvPopular.text = coin.label
        } else if (coin.popular == 1) {
            holder.binding.tvPopular.visibility = View.VISIBLE
            holder.binding.tvPopular.text = "★ Popular"
        } else {
            holder.binding.tvPopular.visibility = View.GONE
        }

        // Update the UI based on selection (ContextCompat instead of the
        // deprecated resources.getColor(Int) overload).
        if (coin.isSelected == true) {
            holder.binding.cvCoin.strokeWidth = 3
            holder.binding.cvCoin.strokeColor = ContextCompat.getColor(activity, R.color.pink)
            holder.binding.cvCoin.cardElevation = 6f
        } else {
            holder.binding.cvCoin.strokeWidth = 0
            holder.binding.cvCoin.strokeColor =
                ContextCompat.getColor(activity, android.R.color.transparent)
            holder.binding.cvCoin.cardElevation = 2f
        }

        // Handle item click — narrow the notify to just the two affected rows
        // (old-selected and new-selected) instead of repainting the entire grid.
        holder.binding.main.setOnSingleClickListener {
            val previousSelectedIndex = coins.indexOfFirst { it.isSelected == true }
            if (previousSelectedIndex == position) return@setOnSingleClickListener

            onItemSelectionListener.onItemSelected(coin)
            coins.forEach { it.isSelected = false }
            coin.isSelected = true

            if (previousSelectedIndex >= 0 && previousSelectedIndex != position) {
                notifyItemChanged(previousSelectedIndex)
            }
            notifyItemChanged(position)
        }


        // Set coin details
        holder.binding.tvCoins.text = coin.coins.toString()
        holder.binding.tvPrice.text = activity.getString(R.string.rupee_text, coin.price)
        when {
            coin.save == null -> {
                holder.binding.tvDiscountPrice.visibility = View.GONE
            }
            coin.save == 0 -> {
                holder.binding.tvDiscountPrice.visibility = View.INVISIBLE
            }
            else -> {
                holder.binding.tvDiscountPrice.visibility = View.VISIBLE
                holder.binding.tvDiscountPrice.text = "Save ${coin.save} %"
            }
        }


//        if (coin.actual_price > 0){
//            holder.binding.originalPrice.visibility= View.VISIBLE
//            holder.binding.originalPrice.text = activity.getString(R.string.rupee_text, coin.actual_price)
//
//            holder.binding.originalPrice.paintFlags =
//                holder.binding.originalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
//
//        }

    }

    override fun getItemCount(): Int {
        return coins.size
    }

    internal class ItemHolder(val binding: AdapterCoinBinding) :
        RecyclerView.ViewHolder(binding.root)
}
