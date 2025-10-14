package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.Coupon
import com.gmwapp.hima.CouponAdapter
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityCouponBinding
import com.gmwapp.hima.viewmodels.CouponViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint

class CouponActivity : AppCompatActivity(), CouponAdapter.OnCouponClickListener  {
    lateinit var binding: ActivityCouponBinding
    private lateinit var bestCouponsAdapter: CouponAdapter
    private lateinit var moreCouponsAdapter: CouponAdapter
    private val couponViewModel: CouponViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCouponBinding.inflate(layoutInflater)
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

        val rvMoreCoupons = findViewById<RecyclerView>(R.id.rv_moreCoupons)
        val rvBestCoupons = findViewById<RecyclerView>(R.id.rv_bestCoupons)
        val ivBack = findViewById<ImageView>(R.id.iv_back)
        val coinID = BaseApplication.getInstance()?.getPrefs()?.getString("last_coin_id")
        Log.d("CoinIDSaved","$coinID")


        if (!coinID.isNullOrEmpty()) {
            couponViewModel.getCoupons(coinID)
        } else {
            Log.e("CouponActivity", "CoinID is null or empty")
        }

        couponViewModel.couponsLiveData.observe(this, Observer {
            if (it != null && it.success && it.data != null) {
                val moreCoupons = it.data.filter { coupon -> coupon.type == "more_coupons" }
                val bestCoupons = it.data.filter { coupon -> coupon.type == "best_coupons" }

                if (bestCoupons.isNotEmpty()) {
                    binding.tvBestCoupons.visibility = View.VISIBLE
                    binding.tvBestCoupons.text = bestCoupons[0].coupon_name ?: "Best Coupons"
                    Log.d("CouponName", "${bestCoupons[0].coupon_name}")
                } else {
                    binding.tvBestCoupons.visibility = View.GONE
                }

                if (moreCoupons.isNotEmpty()) {
                    binding.tvMoreCoupons.visibility = View.VISIBLE
                    binding.tvMoreCoupons.text = moreCoupons[0].coupon_name ?: "More Coupons"
                    Log.d("CouponName", "${moreCoupons[0].coupon_name}")
                } else {
                    binding.tvMoreCoupons.visibility = View.GONE
                }


                moreCouponsAdapter = CouponAdapter(moreCoupons.map { cd ->
                    Coupon(
                        cd.id.toString(),
                        cd.offer ?: "",
                        cd.coupon_code ?: "",
                        cd.save_price ?: "Save ₹0",
                        cd.valid ?: "Limited time",
                        "₹${cd.original_price ?: 0}",
                        formatDiscountPrice(cd.discount_price ?: "0"),
                        (cd.coins ?: 0).toString()
                    )
                }, this)

                bestCouponsAdapter = CouponAdapter(bestCoupons.map { cd ->
                    Coupon(
                        cd.id.toString(),
                        cd.offer ?: "",
                        cd.coupon_code ?: "",
                        cd.save_price ?: "Save ₹0",
                        cd.valid ?: "Limited time",
                        "₹${cd.original_price ?: 0}",
                        formatDiscountPrice(cd.discount_price ?: "0"),
                        (cd.coins ?: 0).toString()
                    )
                }, this)

                binding.rvMoreCoupons.layoutManager = LinearLayoutManager(this)
                binding.rvMoreCoupons.adapter = moreCouponsAdapter

                binding.rvBestCoupons.layoutManager = LinearLayoutManager(this)
                binding.rvBestCoupons.adapter = bestCouponsAdapter
            }else{
                binding.tvBestCoupons.visibility= View.GONE
                binding.tvMoreCoupons.setText("No Coupon Found")

                (binding.tvMoreCoupons.layoutParams as LinearLayout.LayoutParams).setMargins(0, (150 * resources.displayMetrics.density).toInt(), 0, 0)

                binding.tvMoreCoupons.gravity = Gravity.CENTER
            }
        })




        binding.ivBack.setOnClickListener {
//            var intent = Intent(this, PaymentActivity::class.java)
//            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
//            startActivity(intent)
            finish()
        }

//        val dummyCoupons = listOf(
//            Coupon("1", "50% Offer", "SAVE50", "Save ₹50", "Valid on orders above ₹500", "₹500", "₹250", "1400"),
//            Coupon("2", "60% Offer", "FLAT60", "Save ₹60", "Valid on orders above ₹600", "₹600", "₹240", "1500"),
//        )
//
//        val dummyCoupons2 = listOf(
//            Coupon("3", "30% Offer", "CASH30", "Save ₹30", "Valid on prepaid orders", "₹100", "₹70","500"),
//            Coupon("4", "₹100 Offer", "DIS100", "Save ₹100", "Valid for first-time users", "₹1000", "₹900", "200")
//        )
//
//        bestCouponsAdapter = CouponAdapter(dummyCoupons, this)
//        binding.rvBestCoupons.layoutManager = LinearLayoutManager(this)
//        binding.rvBestCoupons.adapter = bestCouponsAdapter
//
//        moreCouponsAdapter = CouponAdapter(dummyCoupons2,this)
//        binding.rvMoreCoupons.layoutManager = LinearLayoutManager(this)
//        binding.rvMoreCoupons.adapter = moreCouponsAdapter
    }

    private fun formatDiscountPrice(price: String): String {
        return try {
            // Handle empty or null-like strings
            if (price.isBlank() || price.equals("null", ignoreCase = true)) {
                return "₹0"
            }
            
            // Remove any existing currency symbols, whitespace, and commas
            val cleanPrice = price.replace("₹", "")
                .replace("$", "")
                .replace(",", "")
                .trim()
            
            // Validate that it's a valid number
            val numericValue = cleanPrice.toDoubleOrNull()
            if (numericValue != null) {
                // Format as integer if it's a whole number, otherwise keep decimals
                if (numericValue % 1.0 == 0.0) {
                    "₹${numericValue.toInt()}"
                } else {
                    "₹${"%.2f".format(numericValue)}"
                }
            } else {
                // If not a valid number, return as is with ₹ prefix
                "₹$cleanPrice"
            }
        } catch (e: Exception) {
            Log.e("CouponActivity", "Error formatting discount price: ${e.message}")
            "₹$price"  // Return original with ₹ prefix as fallback
        }
    }

    override fun onCouponClick(coupon: Coupon) {
        // Use exact string values from API without conversion
        val originalPrice = coupon.originalPrice.replace("₹", "").replace(",", "").trim()
        val discountedPrice = coupon.discountedPrice.replace("₹", "").replace(",", "").trim()
        
        // Calculate savings amount - preserve decimals if present
        val savingsAmount = try {
            val original = originalPrice.toDoubleOrNull() ?: 0.0
            val discounted = discountedPrice.toDoubleOrNull() ?: 0.0
            val savings = original - discounted
            
            if (savings > 0) {
                // Format to remove unnecessary decimal places
                if (savings % 1.0 == 0.0) {
                    // Whole number - show as integer
                    savings.toInt().toString()
                } else {
                    // Has decimals - preserve them
                    String.format("%.2f", savings)
                }
            } else {
                // Use the save_price from API if calculation fails
                coupon.saveAmount.replace("₹", "").replace("Save ", "").trim()
            }
        } catch (e: Exception) {
            Log.e("CouponActivity", "Error calculating savings: ${e.message}")
            coupon.saveAmount.replace("₹", "").replace("Save ", "").trim()
        }

        // Show success dialog with exact values
        val successDialog = com.gmwapp.hima.dialogs.CouponSuccessDialog(
            context = this,
            couponCode = coupon.couponCode,
            savingsAmount = savingsAmount,
            originalAmount = originalPrice,
            finalAmount = discountedPrice,
            onContinueClick = {
                // Navigate to Payment Activity after dialog
                val intent = Intent(this, PaymentActivity::class.java).apply {
                    putExtra("COUPON_CODE", coupon.couponCode)
                    putExtra("ORIGINAL_PRICE", coupon.originalPrice)
                    putExtra("DISCOUNTED_PRICE", coupon.discountedPrice)
                    putExtra("COINS", coupon.coins)
                    putExtra("SAVE", savingsAmount)
                    putExtra("OFFER", coupon.offer)  // Pass the offer text (e.g., "50% Off")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            }
        )
        successDialog.show()
    }
}
