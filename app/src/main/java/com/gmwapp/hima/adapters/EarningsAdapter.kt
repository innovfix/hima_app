package com.gmwapp.hima.adapters

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.AdapterEarningsBinding
import com.gmwapp.hima.databinding.AdapterTransactionBinding
import com.gmwapp.hima.retrofit.responses.EarningsResponseData
import com.gmwapp.hima.retrofit.responses.TransactionsResponseData


class EarningsAdapter(
    val activity: Activity,
    private val earnings: List<EarningsResponseData>,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemHolder = ItemHolder(
            AdapterEarningsBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        return itemHolder
    }

    override fun onBindViewHolder(holderParent: RecyclerView.ViewHolder, position: Int) {
        val holder: ItemHolder = holderParent as ItemHolder
        val earning: EarningsResponseData = earnings[position]

        when (earning.status) {
            0 -> {
                holder.binding.tvStatus.text = activity.getString(R.string.pending)
                holder.binding.tvStatus.setTextColor(activity.getColor(R.color.yellow_dark))
                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_badge_pending)
                holder.binding.vStatusIndicator.setBackgroundResource(R.drawable.status_indicator_pending)
            }
            1 -> {
                holder.binding.tvStatus.text = activity.getString(R.string.paid)
                holder.binding.tvStatus.setTextColor(activity.getColor(R.color.green))
                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_badge_success)
                holder.binding.vStatusIndicator.setBackgroundResource(R.drawable.status_indicator_success)
            }
            else -> {
                holder.binding.tvStatus.text = activity.getString(R.string.cancelled)
                holder.binding.tvStatus.setTextColor(activity.getColor(R.color.Red))
                holder.binding.tvStatus.setBackgroundResource(R.drawable.status_badge_error)
                holder.binding.vStatusIndicator.setBackgroundResource(R.drawable.status_indicator_error)
                if (earning.reason.isNotEmpty() && earning.reason!=null){
                    holder.binding.tvReason.visibility = View.VISIBLE
                    holder.binding.tvReason.text = "Reason : ${earning.reason}"
                }
            }
        }
        holder.binding.tvDate.text = earning.datetime
        holder.binding.tvAmount.text = "₹" + earning.amount.toString()
        holder.binding.tvId.text = activity.getString(R.string.transaction_id, earning.id.toString())

        Log.d("CancelledReason","${earning.reason}")

    }

     override fun getItemCount(): Int {
        return earnings.size
    }

    internal class ItemHolder(val binding: AdapterEarningsBinding) :
        RecyclerView.ViewHolder(binding.root) {
    }
}
