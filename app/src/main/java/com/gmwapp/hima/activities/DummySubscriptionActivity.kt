package com.gmwapp.hima.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gmwapp.hima.databinding.ActivityDummySubscriptionBinding
import com.gmwapp.hima.utils.DummyDailyCoins

class DummySubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDummySubscriptionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDummySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        binding.switchSubscription.isChecked = prefs.getBoolean(KEY_ACTIVE, false)
        binding.switchSubscription.setOnCheckedChangeListener { _, isChecked ->
            val editor = prefs.edit().putBoolean(KEY_ACTIVE, isChecked)
            if (isChecked) {
                editor.putBoolean(KEY_EVER_ACTIVE, true)
            }
            editor.apply()
            if (isChecked) {
                DummyDailyCoins.creditIfNewDay(this)
                finish()
            }
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    companion object {
        const val PREFS = "TrialOfferPrefs"
        const val KEY_ACTIVE = "isSubscriptionActive"
        const val KEY_EVER_ACTIVE = "hasEverSubscribed"
    }
}
