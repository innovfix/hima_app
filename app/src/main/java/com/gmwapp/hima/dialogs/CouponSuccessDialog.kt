package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.gmwapp.hima.R

class CouponSuccessDialog(
    private val context: Context,
    private val couponCode: String,
    private val savingsAmount: String,
    private val originalAmount: String,
    private val finalAmount: String,
    private val onContinueClick: () -> Unit
) {

    private lateinit var dialog: Dialog

    fun show() {
        dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_coupon_success, null)
        dialog.setContentView(view)
        
        // Set dialog window size
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Set values
        view.findViewById<TextView>(R.id.tv_dialog_coupon_code).text = couponCode
        view.findViewById<TextView>(R.id.tv_dialog_savings_amount).text = "₹$savingsAmount"
        view.findViewById<TextView>(R.id.tv_dialog_original_amount).text = "₹$originalAmount"
        view.findViewById<TextView>(R.id.tv_dialog_discount).text = "- ₹$savingsAmount"
        view.findViewById<TextView>(R.id.tv_dialog_final_amount).text = "₹$finalAmount"

        // Continue button
        view.findViewById<TextView>(R.id.btn_dialog_continue).setOnClickListener {
            dialog.dismiss()
            onContinueClick()
        }

        // Show dialog first
        dialog.show()
        
        // Add bounce animation after showing
        view.postDelayed({
            val bounceAnimation = AnimationUtils.loadAnimation(context, R.anim.dialog_bounce_in)
            view.startAnimation(bounceAnimation)
        }, 50)
    }

    fun dismiss() {
        if (::dialog.isInitialized && dialog.isShowing) {
            dialog.dismiss()
        }
    }
}

