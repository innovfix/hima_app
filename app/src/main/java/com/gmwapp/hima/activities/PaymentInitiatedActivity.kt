package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityPaymentInitiatedBinding
import com.gmwapp.hima.databinding.ActivityWithdrawBinding
import com.gmwapp.hima.utils.applySystemBarInsets

class PaymentInitiatedActivity : AppCompatActivity() {
    lateinit var binding: ActivityPaymentInitiatedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentInitiatedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, R.color.white, darkStatusBarIcons = true)
        initUI()
    }

    private fun initUI() {
        binding.btnGoBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()

        }

    }
}