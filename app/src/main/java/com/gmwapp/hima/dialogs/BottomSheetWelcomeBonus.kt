package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.databinding.BottomSheetWelcomeBonusBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BottomSheetWelcomeBonus : BottomSheetDialogFragment() {

    interface OnAddCoinsListener {
        fun onAddCoins(coins: String, id: Int)
    }

    private var _binding: BottomSheetWelcomeBonusBinding? = null
    private val binding get() = _binding!!
    private var addCoinsListener: OnAddCoinsListener? = null

    // Variables to receive from arguments
    private var coins: Int = 0
    private var originalPrice: Int = 0
    private var discountedPrice: Int = 0
    private var coinId: Int = 0
    private var totalCount: Int = 0

    companion object {
        fun newInstance(
            coins: Int,
            originalPrice: Int,
            discountedPrice: Int,
            coinId: Int,
            totalCount: Int
        ): BottomSheetWelcomeBonus {
            val fragment = BottomSheetWelcomeBonus()
            val args = Bundle().apply {
                putInt("coins", coins)
                putInt("originalPrice", originalPrice)
                putInt("discountedPrice", discountedPrice)
                putInt("coinId", coinId)
                putInt("totalCount", totalCount)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnAddCoinsListener) {
            addCoinsListener = context
        } else {
            throw RuntimeException("$context must implement OnAddCoinsListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            coins = it.getInt("coins")
            originalPrice = it.getInt("originalPrice")
            discountedPrice = it.getInt("discountedPrice")
            coinId = it.getInt("coinId")
            totalCount = it.getInt("totalCount")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetWelcomeBonusBinding.inflate(inflater, container, false)

        // Set strikethrough on original price
        binding.tvBonusOriginal.paintFlags =
            binding.tvBonusOriginal.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

        // Set UI values
        binding.tvBonusText.text = "$coins Coins"
        binding.tvBonusOriginal.text = "₹$originalPrice"
        binding.tvBonusDiscount.text = "₹$discountedPrice"
        binding.tvUsedBy.text = "Used by $totalCount people in the last 30 mins"

        val twoPercentage = discountedPrice.toDouble() * 0.02
        val roundedAmount = Math.round(twoPercentage)
        val totalAmount = (discountedPrice + roundedAmount).toString()

        binding.tvViewMorePlans.setOnSingleClickListener {
            startActivity(Intent(context, WalletActivity::class.java))
        }

        binding.btnAddCoins.setOnSingleClickListener {
            addCoinsListener?.onAddCoins(totalAmount, coinId)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }
}
