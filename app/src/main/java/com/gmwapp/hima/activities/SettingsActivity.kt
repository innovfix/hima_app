package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.databinding.ActivitySettingsBinding
import com.gmwapp.hima.utils.SubscriptionStateCache
import com.gmwapp.hima.utils.setOnSingleClickListener

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        binding.includeProfileToolbar.tvFlowTitle.text = "Settings"
        binding.includeProfileToolbar.cvBack.setOnSingleClickListener { finish() }

        binding.cvActiveSubscription.setOnSingleClickListener {
            startActivity(Intent(this, CancelSubscriptionActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSubscriptionState()
    }

    private fun refreshSubscriptionState() {
        val active = SubscriptionStateCache.isActive(this)
        val ever = SubscriptionStateCache.everActive(this)
        val autopayLanguage = com.gmwapp.hima.utils.LanguageFeatureCache.isAutopayEnabled(this)
        // Non-autopay-language users who never subscribed see no subscription
        // section at all (header + both cards hidden). Pre-existing subscribers
        // (everActive) keep visibility so they can still cancel even if the
        // language is later flipped off.
        if (!autopayLanguage && !ever) {
            binding.llSubscriptionSectionHeader.visibility = View.GONE
            binding.cvActiveSubscription.visibility = View.GONE
            binding.cvNoSubscription.visibility = View.GONE
            return
        }
        binding.llSubscriptionSectionHeader.visibility = View.VISIBLE
        binding.cvActiveSubscription.visibility = if (active) View.VISIBLE else View.GONE
        binding.cvNoSubscription.visibility = if (active) View.GONE else View.VISIBLE
    }
}
