package com.gmwapp.hima.adapters

import android.app.Activity
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.AdapterCoinBinding
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.utils.setOnSingleClickListener
//import com.zego.ve.Log


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

        if (coin.popular == 1){
            holder.binding.tvPopular.visibility = View.VISIBLE
        }else{
            holder.binding.tvPopular.visibility = View.GONE
        }

        // Update the UI based on selection
        if (coin.isSelected == true) {
            holder.binding.cvCoin.strokeWidth = 4
            holder.binding.cvCoin.strokeColor = activity.resources.getColor(R.color.yellow_dark)
            holder.binding.llPrice.setBackgroundColor(activity.resources.getColor(R.color.yellow_dark))
        } else {
            holder.binding.cvCoin.strokeWidth = 0
            holder.binding.cvCoin.strokeColor = activity.resources.getColor(R.color.white)
            holder.binding.llPrice.setBackgroundColor(activity.resources.getColor(R.color.pink_extra_light))
        }

        // Handle item click
        holder.binding.main.setOnSingleClickListener {
            onItemSelectionListener.onItemSelected(coin)
            coins.onEach { it.isSelected = false }
            coin.isSelected = true
            coins[position] = coin
            notifyDataSetChanged()
        }


        // Set coin details
        holder.binding.tvCoins.text = coin.coins.toString()
        if (coin.save == null) {
            holder.binding.tvDiscountPrice.visibility = View.GONE
        } else {
            holder.binding.tvDiscountPrice.visibility = View.VISIBLE
            holder.binding.tvDiscountPrice.text = "Save ${coin.save} %"
        }
        holder.binding.tvPrice.text = activity.getString(R.string.rupee_text, coin.price)
        if (coin.save==0){
            holder.binding.tvDiscountPrice.visibility = View.INVISIBLE
        }


        if (coin.actual_price > 0){
            holder.binding.originalPrice.visibility= View.VISIBLE
            holder.binding.originalPrice.text = activity.getString(R.string.rupee_text, coin.actual_price)

            holder.binding.originalPrice.paintFlags =
                holder.binding.originalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

    }

    override fun getItemCount(): Int {
        return coins.size
    }

    internal class ItemHolder(val binding: AdapterCoinBinding) :
        RecyclerView.ViewHolder(binding.root)
}
