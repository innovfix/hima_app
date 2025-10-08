package com.gmwapp.hima

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CouponAdapter(private val coupons: List<Coupon>,
                    private val listener: OnCouponClickListener) :
    RecyclerView.Adapter<CouponAdapter.CouponViewHolder>() {
    interface OnCouponClickListener {
        fun onCouponClick(coupon: Coupon)
    }

    class CouponViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvOffer: TextView = view.findViewById(R.id.tvOffer)
        val tvCouponCode: TextView = view.findViewById(R.id.tvCouponCode)
        val tvSaveAmount: TextView = view.findViewById(R.id.tvSaveAmount)
        val tvValidity: TextView = view.findViewById(R.id.tvValidity)
        val tvOriginalPrice: TextView = view.findViewById(R.id.tvOriginalPrice)
        val tvDiscountedPrice: TextView = view.findViewById(R.id.tvDiscountedPrice)
        val coins: TextView = view.findViewById(R.id.tvCoins)
        val btnApply: View = view.findViewById(R.id.btnApplyCoupon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CouponViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_coupons, parent, false)
        return CouponViewHolder(view)
    }

    override fun onBindViewHolder(holder: CouponViewHolder, position: Int) {
        val coupon = coupons[position]
        holder.tvOffer.text = coupon.offer
        holder.tvCouponCode.text = coupon.couponCode
        holder.tvSaveAmount.text = coupon.saveAmount
        holder.tvValidity.text = coupon.validity
        holder.tvOriginalPrice.text = coupon.originalPrice
        holder.tvDiscountedPrice.text = coupon.discountedPrice
        holder.coins.text = coupon.coins

        // Add strikethrough to original price
        holder.tvOriginalPrice.paintFlags =
            holder.tvOriginalPrice.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG

        holder.btnApply.setOnClickListener {
            listener.onCouponClick(coupon)
        }
    }

    override fun getItemCount(): Int = coupons.size
}
