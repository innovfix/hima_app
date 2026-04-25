package com.gmwapp.hima.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.databinding.ActivityCancelFeedbackBinding
import com.gmwapp.hima.utils.setOnSingleClickListener

class CancelFeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCancelFeedbackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCancelFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        binding.includeProfileToolbar.tvFlowTitle.text = "Cancel Subscription"
        binding.includeProfileToolbar.cvBack.setOnSingleClickListener { finish() }

        binding.btnCancelSubscription.setOnClickListener {
            if (binding.radioGroup.checkedRadioButtonId == -1) {
                Toast.makeText(this, "Please select a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            getSharedPreferences(DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(DummySubscriptionActivity.KEY_ACTIVE, false)
                .apply()
            Toast.makeText(this, "Subscription cancelled", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
    }
}
