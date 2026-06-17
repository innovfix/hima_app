package com.gmwapp.hima.adapters

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.AdapterTransactionBinding
import com.gmwapp.hima.retrofit.responses.FemaleTransactionsResponseData
import com.gmwapp.hima.utils.DateTimeUtils

class FemaleTransactionAdapter(
    val activity: Activity,
    private val transactions: MutableList<FemaleTransactionsResponseData>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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
        val transaction: FemaleTransactionsResponseData = transactions[position]

        // Log transaction type for debugging
        Log.d("femaleTrasactionLog", "onBindViewHolder: position=$position, type=${transaction.type}, title=${transaction.title}, coins=${transaction.coins}")

        // Ensure icon is visible
        holder.binding.ivTransactionIcon.visibility = android.view.View.VISIBLE
        holder.binding.cvIconBackground.visibility = android.view.View.VISIBLE

        // Handle call_income type (should be mapped to audio/video by backend, but handle as fallback)
        val transactionType = when (transaction.type.lowercase()) {
            "call_income" -> {
                // If backend didn't map it, check title to determine if audio or video
                val title = transaction.title?.lowercase() ?: ""
                if (title.contains("video")) "video" else "audio"
            }
            else -> transaction.type.lowercase()
        }
        
        // Helper function to format double value (remove unnecessary decimals)
        fun formatDouble(value: Double): String {
            return if (value % 1.0 == 0.0) {
                // If it's a whole number, show without decimals
                value.toInt().toString()
            } else {
                // If it has decimals, show up to 2 decimal places
                String.format("%.2f", value).trimEnd('0').trimEnd('.')
            }
        }

        // Hide coin icon and show rupee symbol instead for female transactions
        holder.binding.ivCoin.visibility = android.view.View.GONE
        val rupeeSymbol = activity.getString(R.string.rupee_symbol)

        // Set transaction details based on type
        // IMPORTANT: All female transactions are CREDITS (they earn money)
        when (transactionType) {
            "audio" -> {
                // Audio session - female earns money (credit/positive)
                val formattedCoins = formatDouble(transaction.coins)
                holder.binding.tvCoins.text = "$rupeeSymbol$formattedCoins"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.binding.tvTransactionTitle.text = transaction.title ?: "Audio Session"
                // FI_05: date, call start time, then duration (pulled from description).
                holder.binding.tvTransactionDate.text = buildCallSubtitle(transaction)
                // Set icon and background color - same as male transactions
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_audio_expense)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                Log.d("femaleTrasactionLog", "onBindViewHolder: Set audio icon, amount=+₹$formattedCoins")
            }
            "video" -> {
                // Video session - female earns money (credit/positive)
                val formattedCoins = formatDouble(transaction.coins)
                holder.binding.tvCoins.text = "$rupeeSymbol$formattedCoins"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.binding.tvTransactionTitle.text = transaction.title ?: "Video Session"
                // FI_05: date, call start time, then duration (pulled from description).
                holder.binding.tvTransactionDate.text = buildCallSubtitle(transaction)
                // Set icon and background color - same as male transactions
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_video_expense)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                Log.d("femaleTrasactionLog", "onBindViewHolder: Set video icon, amount=+₹$formattedCoins")
            }
            "referral" -> {
                // Referral earning - credit (positive)
                val formattedCoins = formatDouble(transaction.coins)
                holder.binding.tvCoins.text = "$rupeeSymbol$formattedCoins"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.binding.tvTransactionTitle.text = transaction.title ?: "Referral Earning"
                holder.binding.tvTransactionDate.text = transaction.date
                // Set icon and background color - same as male transactions
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_referral_bonus)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                Log.d("femaleTrasactionLog", "onBindViewHolder: Set referral icon, amount=+₹$formattedCoins")
            }
            "ipl_room" -> {
                // IPL Room income - credit (positive)
                val formattedCoins = formatDouble(transaction.coins)
                holder.binding.tvCoins.text = "$rupeeSymbol$formattedCoins"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
                // Title from backend: "IPL Room with {UserName}"
                holder.binding.tvTransactionTitle.text = transaction.title ?: "IPL Room Income"
                // Build subtitle: date · duration only (skip room name)
                val reason = transaction.description ?: ""
                val durationMatch = Regex("- (\\d+ min)").find(reason)
                val durationText = durationMatch?.groupValues?.get(1) ?: ""
                val subtitle = listOf(transaction.date, durationText).filter { it.isNotEmpty() }.joinToString(" · ")
                holder.binding.tvTransactionDate.text = subtitle.ifEmpty { transaction.date }
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_audio_expense)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                Log.d("femaleTrasactionLog", "onBindViewHolder: Set ipl_room icon, amount=+₹$formattedCoins")
            }
            "gift_received" -> {
                // Gift received - credit (positive)
                val formattedCoins = formatDouble(transaction.coins)
                holder.binding.tvCoins.text = "$rupeeSymbol$formattedCoins"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.binding.tvTransactionTitle.text = transaction.title ?: "Gift Received"
                holder.binding.tvTransactionDate.text = transaction.date
                // Set icon and background color - same as male transactions (using gift icon)
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_gift_sent)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"))
                Log.d("femaleTrasactionLog", "onBindViewHolder: Set gift icon, amount=+₹$formattedCoins")
            }
            else -> {
                // Fallback for unknown types - still show as credit (female earns)
                Log.w("femaleTrasactionLog", "onBindViewHolder: Unknown transaction type '${transaction.type}', using default")
                val formattedCoins = formatDouble(transaction.coins)
                holder.binding.tvCoins.text = "$rupeeSymbol$formattedCoins"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.binding.tvTransactionTitle.text = transaction.title ?: "Transaction"
                holder.binding.tvTransactionDate.text = transaction.date
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.coin_d)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#F3F4F6"))
            }
        }

        // Log for debugging
        Log.d("female_transaction_datetime", "${transaction.datetime}")
        holder.binding.tvTransactionHint.text = activity.getString(R.string.session_id) + transaction.id
    }

    // FI_05: build "date, start-time · duration" for a call session. Date comes from
    // transaction.date; start time from transaction.datetime; duration is recovered from
    // the description string ("Jan 03 · 7 sec") which is where the backend puts it.
    private fun buildCallSubtitle(transaction: FemaleTransactionsResponseData): String {
        // FI_05: prefer the real call start time; fall back to transaction datetime on older rows.
        val startTime = DateTimeUtils.formatCallStartTime(
            transaction.started_time?.takeIf { it.isNotBlank() } ?: transaction.datetime
        )
        val durationPart = transaction.description?.let { desc ->
            if (desc.contains("·")) desc.substringAfter("·").trim().takeIf { it.isNotEmpty() }
            else desc.trim().takeIf { it.isNotEmpty() }
        }
        return buildString {
            append(transaction.date)
            if (startTime.isNotEmpty()) append(", ").append(startTime)
            if (durationPart != null) append(" · ").append(durationPart)
        }
    }

    fun addTransactions(newTransactions: List<FemaleTransactionsResponseData>) {
        val startPos = transactions.size
        transactions.addAll(newTransactions)
        notifyItemRangeInserted(startPos, newTransactions.size)
    }

    override fun getItemCount(): Int {
        return transactions.size
    }

    internal class ItemHolder(val binding: AdapterTransactionBinding) :
        RecyclerView.ViewHolder(binding.root)
}

