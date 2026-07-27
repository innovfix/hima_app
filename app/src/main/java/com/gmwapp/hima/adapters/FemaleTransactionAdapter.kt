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
import com.gmwapp.hima.utils.DisplayName

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

        // F1: reset the inline duration-bonus each bind (recycled rows). Only call
        // rows with a positive bonus turn it back on below.
        holder.binding.tvBonus.visibility = android.view.View.GONE

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

        // Default to single line; only call rows use the two-line subtitle below.
        // Reset every bind so a recycled call-row holder doesn't keep maxLines=2.
        holder.binding.tvTransactionDate.maxLines = 1

        // Set transaction details based on type
        // IMPORTANT: All female transactions are CREDITS (they earn money)
        when (transactionType) {
            "audio" -> {
                // Audio session - female earns money (credit/positive)
                val formattedCoins = formatDouble(transaction.coins)
                holder.binding.tvCoins.text = "$rupeeSymbol$formattedCoins"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
                // Show only the name (strip digit suffixes like "Ram01" → "Ram"),
                // matching the home-screen name convention.
                holder.binding.tvTransactionTitle.text =
                    DisplayName.clean(transaction.title).ifBlank { "Audio Session" }
                // FI_05: date, call start time, then duration (pulled from description).
                holder.binding.tvTransactionDate.maxLines = 1
                holder.binding.tvTransactionDate.text = buildCallSubtitle(transaction)
                // Set icon and background color - same as male transactions
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_audio_expense)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                showBonusInline(holder, rupeeSymbol, transaction.bonus, ::formatDouble)
                Log.d("femaleTrasactionLog", "onBindViewHolder: Set audio icon, amount=+₹$formattedCoins")
            }
            "video" -> {
                // Video session - female earns money (credit/positive)
                val formattedCoins = formatDouble(transaction.coins)
                holder.binding.tvCoins.text = "$rupeeSymbol$formattedCoins"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.binding.tvTransactionTitle.text =
                    DisplayName.clean(transaction.title).ifBlank { "Video Session" }
                // FI_05: date, call start time, then duration (pulled from description).
                holder.binding.tvTransactionDate.maxLines = 1
                holder.binding.tvTransactionDate.text = buildCallSubtitle(transaction)
                // Set icon and background color - same as male transactions
                holder.binding.ivTransactionIcon.setImageResource(R.drawable.ic_video_expense)
                holder.binding.cvIconBackground.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                showBonusInline(holder, rupeeSymbol, transaction.bonus, ::formatDouble)
                Log.d("femaleTrasactionLog", "onBindViewHolder: Set video icon, amount=+₹$formattedCoins")
            }
            "referral" -> {
                // Referral earning - credit (positive)
                val formattedCoins = formatDouble(transaction.coins)
                holder.binding.tvCoins.text = "$rupeeSymbol$formattedCoins"
                holder.binding.tvCoins.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.binding.tvTransactionTitle.text =
                    DisplayName.clean(transaction.title).ifBlank { "Referral Earning" }
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
                holder.binding.tvTransactionTitle.text =
                    DisplayName.clean(transaction.title).ifBlank { "IPL Room Income" }
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
                // Show date + time, same style as call rows. Gift rows have no
                // started_time, so derive the time from the full datetime; the helper
                // falls back to date-only if datetime is missing/unparseable.
                holder.binding.tvTransactionDate.maxLines = 1
                holder.binding.tvTransactionDate.text =
                    DateTimeUtils.buildTxnSubtitle(transaction.date, transaction.datetime, null)
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

    // FI_05: two-line subtitle — "date, start-time" then duration — so it
    // reconciles to the real call without wrapping mid-word. Start time prefers
    // the real call started_time (time-only), falling back to the transaction
    // datetime on older rows; duration is recovered from the description string
    // ("Jan 03 · 7 sec"). See DateTimeUtils.buildTxnSubtitle.
    // F1: show the inline duration-bonus ("+₹X" gold) on a call row when it's > 0.
    private fun showBonusInline(holder: ItemHolder, rupeeSymbol: String, bonus: Double?, fmt: (Double) -> String) {
        val b = bonus ?: 0.0
        if (b > 0.0) {
            holder.binding.tvBonus.text = "+$rupeeSymbol${fmt(b)}"
            holder.binding.tvBonus.visibility = android.view.View.VISIBLE
        }
    }

    // Matches a real duration token like "7 sec", "2 min", "1 hr" (any of the plural /
    // long forms). Used to reject non-duration description text from the duration slot.
    private val durationRegex =
        Regex("\\d+\\s*(sec|min|hr|hour|second|minute)s?", RegexOption.IGNORE_CASE)

    private fun buildCallSubtitle(transaction: FemaleTransactionsResponseData): CharSequence {
        val durationPart = transaction.description?.let { desc ->
            // The duration, when present, lives after the "·" ("Jul 24 · 7 sec"); some rows
            // carry just the duration with no separator. Take that candidate…
            val candidate = if (desc.contains("·")) desc.substringAfter("·").trim() else desc.trim()
            // …but only use it if it actually READS like a duration. Short (<10s) calls whose
            // backend description carries NO duration segment (just the date, e.g. "Jul 24")
            // were rendering that date in the duration slot — the reported bug. Requiring a
            // sec/min/hr token drops any non-duration text (the date) safely; the subtitle
            // then shows just "<date>, <start-time>".
            candidate.takeIf { it.isNotEmpty() && durationRegex.containsMatchIn(it) }
        }
        return DateTimeUtils.buildTxnSubtitle(
            transaction.date,
            transaction.started_time?.takeIf { it.isNotBlank() } ?: transaction.datetime,
            durationPart
        )
    }

    fun addTransactions(newTransactions: List<FemaleTransactionsResponseData>) {
        val startPos = transactions.size
        transactions.addAll(newTransactions)
        notifyItemRangeInserted(startPos, newTransactions.size)
    }

    // BUG #14 — replace the whole list (used by pull-to-refresh / first page).
    fun setTransactions(newTransactions: List<FemaleTransactionsResponseData>) {
        transactions.clear()
        transactions.addAll(newTransactions)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return transactions.size
    }

    internal class ItemHolder(val binding: AdapterTransactionBinding) :
        RecyclerView.ViewHolder(binding.root)
}

