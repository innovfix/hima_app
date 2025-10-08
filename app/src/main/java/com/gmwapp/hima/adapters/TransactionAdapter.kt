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

        // Set coins amount and color based on transaction type
        if(transaction.payment_type == "Credit"){
            holder.binding.tvCoins.text = "+${transaction.coins}"
            holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
        } else{
            holder.binding.tvCoins.text = "-${transaction.coins}"
            holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#EF4444"))
        }

        val callType = transaction.call_type.orEmpty().replaceFirstChar { it.uppercase() }
        val callUserName = transaction.call_user_name

        // Set transaction details based on type
        when (transaction.type) {
            "add_coins" -> {
                holder.binding.tvTransactionTitle.text = "Wallet Recharge"
                holder.binding.tvTransactionDate.text = transaction.date
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_wallet_add)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
            }
            "coins_deduction" -> {
                holder.binding.tvTransactionTitle.text = "$callType session with $callUserName"
                holder.binding.tvTransactionDate.text = "${transaction.date} · ${transaction.duration}"
                
                // Set icon based on call type
                when (callType.lowercase()) {
                    "video" -> {
                        holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_video_expense)
                        holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                    }
                    "audio" -> {
                        holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_audio_expense)
                        holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                    }
                    else -> {
                        holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_call_expense)
                        holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                    }
                }
            }
            "refer_bonus" -> {
                holder.binding.tvCoins.text = "+${transaction.coins}"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.binding.tvTransactionTitle.text = "Referral Earning"
                holder.binding.tvTransactionDate.text = transaction.date
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_referral_bonus)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
            }
            "send_gift" -> {
                holder.binding.tvCoins.text = "-${transaction.coins}"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                holder.binding.tvTransactionTitle.text = "Gift Sent"
                holder.binding.tvTransactionDate.text = transaction.date
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_gift_sent)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"))
            }
            else -> {
                holder.binding.tvTransactionTitle.text = "Transaction"
                holder.binding.tvTransactionDate.text = transaction.date
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.coin_d)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#F3F4F6"))
            }
        }

        // Log for debugging
        Log.d("transaction_datetime","${transaction.datetime}")
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
