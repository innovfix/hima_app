package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.databinding.BottomSheetTrialOfferBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BottomSheetTrialOffer : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetTrialOfferBinding
    private var onTryNowClick: (() -> Unit)? = null

    fun setOnTryNowClickListener(listener: () -> Unit) {
        onTryNowClick = listener
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetTrialOfferBinding.inflate(inflater, container, false)
        binding.btnTryNow.setOnSingleClickListener {
            onTryNowClick?.invoke()
            dismissAllowingStateLoss()
        }
        binding.tvPurchaseCoins.paintFlags =
            binding.tvPurchaseCoins.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvPurchaseCoins.setOnSingleClickListener {
            startActivity(Intent(requireContext(), WalletActivity::class.java))
            dismissAllowingStateLoss()
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isFitToContents = true
    }

    companion object {
        const val TAG = "BottomSheetTrialOffer"
        fun newInstance(): BottomSheetTrialOffer = BottomSheetTrialOffer()
    }
}
