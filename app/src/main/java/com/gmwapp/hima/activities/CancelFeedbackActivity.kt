package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityCancelFeedbackBinding
import com.gmwapp.hima.utils.SubscriptionStateCache
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AutopayViewModel
import com.gmwapp.hima.BaseApplication
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CancelFeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCancelFeedbackBinding
    private val autopayViewModel: AutopayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCancelFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        binding.includeProfileToolbar.tvFlowTitle.text = "Cancel Subscription"
        binding.includeProfileToolbar.cvBack.setOnSingleClickListener { finish() }

        binding.btnCancelSubscription.setOnClickListener {
            val checkedId = binding.radioGroup.checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(this, "Please select a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val (reasonId, reasonText) = mapReason(checkedId)
            val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
            if (userId == null) {
                Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.btnCancelSubscription.isEnabled = false
            binding.btnCancelSubscription.text = "Cancelling…"
            autopayViewModel.subscriptionCancel(userId, reasonId, reasonText)
        }

        autopayViewModel.cancelLiveData.observe(this) { resp ->
            if (resp == null) return@observe
            if (resp.success) {
                SubscriptionStateCache.clear() // force re-fetch on next resume
                Toast.makeText(this, "Subscription cancelled", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
            } else {
                binding.btnCancelSubscription.isEnabled = true
                binding.btnCancelSubscription.text = "Cancel Subscription"
                Toast.makeText(this, resp.message ?: "Could not cancel.", Toast.LENGTH_SHORT).show()
            }
        }

        autopayViewModel.errorLiveData.observe(this) { msg ->
            binding.btnCancelSubscription.isEnabled = true
            binding.btnCancelSubscription.text = "Cancel Subscription"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mapReason(checkedId: Int): Pair<Int, String> = when (checkedId) {
        R.id.rb_expensive  -> 1 to "It's too expensive"
        R.id.rb_not_using  -> 2 to "I'm not using it enough"
        R.id.rb_quality    -> 3 to "Poor call quality"
        R.id.rb_better_app -> 4 to "Found a better app"
        R.id.rb_other      -> 5 to "Other reason"
        else               -> 0 to "Unspecified"
    }
}
