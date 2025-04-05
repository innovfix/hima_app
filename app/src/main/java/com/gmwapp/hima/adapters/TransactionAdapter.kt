package com.gmwapp.hima.adapters

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import java.text.SimpleDateFormat
import java.util.Locale
import com.gmwapp.hima.databinding.AdapterTransactionBinding
import com.gmwapp.hima.retrofit.responses.TransactionsResponseData


class TransactionAdapter(
    val activity: Activity,
    private val transactions: MutableList<TransactionsResponseData>,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemHolder = ItemHolder(
            AdapterTransactionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        return itemHolder
    }

    override fun onBindViewHolder(holderParent: RecyclerView.ViewHolder, position: Int) {
        val holder: ItemHolder = holderParent as ItemHolder
        val transaction: TransactionsResponseData = transactions[position]

        if(transaction.payment_type == "Credit"){
            holder.binding.tvCoins.text = "+"+transaction.coins
            holder.binding.tvCoins.setTextColor(activity.getColor(android.R.color.holo_green_dark))
        } else{
            holder.binding.tvCoins.text = "-"+transaction.coins
            holder.binding.tvCoins.setTextColor(activity.getColor(android.R.color.holo_red_dark))
        }
        var callType = transaction.call_type.orEmpty().replaceFirstChar { it.uppercase() }
        var call_user_name = transaction.call_user_name
        if (transaction.type=="add_coins"){
            holder.binding.tvTransactionTitle.text = "Wallet Recharge"
            holder.binding.tvTransactionDate.text = "${transaction.date}"

        }else if(transaction.type=="coins_deduction"){
            holder.binding.tvTransactionTitle.text = "$callType call with $call_user_name"
            holder.binding.tvTransactionDate.text = "${transaction.date} · ${transaction.duration}"

        }

        //  holder.binding.tvTransactionDate.text = formatTime(transaction.datetime)
        Log.d("transaction_datetime","$transaction.datetime")
        holder.binding.tvTransactionHint.text = activity.getString(R.string.session_id)+transaction.id

    }


    fun addTransactions(newTransactions: List<TransactionsResponseData>) {
        val startPos = transactions.size
        transactions.addAll(newTransactions)
        notifyItemRangeInserted(startPos, newTransactions.size)
    }



     override fun getItemCount(): Int {
        return transactions.size
    }

    internal class ItemHolder(val binding: AdapterTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {
    }

    fun formatTime(datetime: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()) // 12-hour format

        val date = inputFormat.parse(datetime) // Convert string to Date
        val formattedTime = outputFormat.format(date!!) // Convert Date to formatted string

        return datetime.substring(0, 10) + " " + formattedTime // Keep original date, change time
    }
}
