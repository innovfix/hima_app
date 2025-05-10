package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityPaymentBinding

class PaymentActivity : AppCompatActivity() {
    lateinit var binding: ActivityPaymentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initUI()
    }

    private fun initUI(){

        val coinSelected = intent.getStringExtra("COIN_SELECTED")
        val savePercent = intent.getStringExtra("SAVE_PERCENT")
        val amount = intent.getStringExtra("AMOUNT")

        var couponCode = intent.getStringExtra("COUPON_CODE")
        val originalPrice = intent.getStringExtra("ORIGINAL_PRICE")
        val discountedPrice = intent.getStringExtra("DISCOUNTED_PRICE")
        val save = intent.getStringExtra("SAVE")
        val coins = intent.getStringExtra("COINS")

        Log.d("couponcode","$couponCode")
        Log.d("couponcode","$originalPrice")
        Log.d("couponcode","$discountedPrice")
        Log.d("couponcode","$save")

        binding.tvCoinsText.text = coinSelected + " Coins"
        binding.tvTotalAmount.text = "₹$amount"
        binding.tvSavePercent.text = "Save $savePercent%"
        binding.tvFinalAmount.text =  "₹$amount"

        binding.tvChange.setOnClickListener {
            var intent = Intent(this, WalletActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()

        }

        binding.llAllCoupons.setOnClickListener {
            var intent = Intent(this, CouponActivity::class.java)
            startActivity(intent)
            binding.etCouponCode.text.clear()

        }

        binding.ivBack.setOnClickListener {
            var intent = Intent(this, WalletActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        val cleanedCouponCode = couponCode?.trim()
        binding.etCouponCode.clearFocus()
        binding.etCouponCode.error = null
        binding.etCouponCode.setText(cleanedCouponCode)

        if (couponCode != null && originalPrice != null && discountedPrice != null && save != null) {
            binding.etCouponCode.setText(couponCode)
            binding.tvTotalAmount.text = "$originalPrice" // Set original price
            binding.tvFinalAmount.text = "$originalPrice" // Use a different field for discounted price
            binding.tvSavePercent.text = save
            binding.tvCoinsText.text = coins
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        var intent = Intent(this, WalletActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update activity's intent reference
        handleIntent(intent) // Handle new intent data

    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val couponCode = it.getStringExtra("COUPON_CODE")
            val originalPrice = it.getStringExtra("ORIGINAL_PRICE")
            val discountedPrice = it.getStringExtra("DISCOUNTED_PRICE")
            val coins = it.getStringExtra("COINS")
            val save = intent.getStringExtra("SAVE")

            binding.etCouponCode?.setText(couponCode)
            binding.tvTotalAmount.text = "$originalPrice" // Set original price
            binding.tvFinalAmount.text = "$discountedPrice" // Use a different field for discounted price
            binding.tvSavePercent.text = save
            binding.tvCoinsText.text = coins+" Coins"

            Log.d("PaymentActivityCheck", "Coupon Code: $couponCode")
            Log.d("PaymentActivity", "Original Price: $originalPrice")
            Log.d("PaymentActivity", "Discounted Price: $discountedPrice")
            Log.d("PaymentActivity", "Coins: $coins")
        }
    }
}