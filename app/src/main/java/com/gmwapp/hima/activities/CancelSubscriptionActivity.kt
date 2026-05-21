package com.gmwapp.hima.activities

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityCancelSubscriptionBinding
import com.gmwapp.hima.dialogs.BottomSheetCancelConfirm
import com.gmwapp.hima.utils.SubscriptionStateCache
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AutopayViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CancelSubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCancelSubscriptionBinding
    private val autopayViewModel: AutopayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCancelSubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        binding.includeProfileToolbar.tvFlowTitle.text = "Cancel Subscription"
        binding.includeProfileToolbar.cvBack.setOnSingleClickListener { finish() }

        binding.tvCancelAction.setOnSingleClickListener {
            BottomSheetCancelConfirm().show(supportFragmentManager, "cancel_confirm")
        }

        // Render whatever the cache holds (instant), then refresh from the API.
        renderStatus(SubscriptionStateCache.status() ?: "none")

        autopayViewModel.statusLiveData.observe(this) { resp ->
            val data = resp?.data ?: return@observe
            SubscriptionStateCache.update(data)
            renderStatus(data.status)
        }
    }

    override fun onResume() {
        super.onResume()
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
            autopayViewModel.subscriptionStatus(it)
        }
    }

    /**
     * Maps a subscription status to the status-pill text + colour, and
     * decides whether the "Cancel Subscription" link should be visible.
     * Cancel is only meaningful while the subscription is still active.
     */
    private fun renderStatus(status: String) {
        val (label, colorRes) = when (status) {
            "active"    -> "Status: Active"          to R.color.green
            "pending"   -> "Status: Pending"         to R.color.colorAccent
            "cancelled" -> "Status: Cancelled"       to R.color.grey_medium
            "failed"    -> "Status: Payment failed"  to R.color.colorAccent
            else        -> "Status: Not subscribed"  to R.color.grey_medium
        }
        binding.tvSubscriptionStatus.text = label
        try {
            binding.tvSubscriptionStatus.setTextColor(ContextCompat.getColor(this, colorRes))
        } catch (_: Exception) {
            binding.tvSubscriptionStatus.setTextColor(Color.GRAY)
        }
        binding.tvCancelAction.visibility = if (status == "active") View.VISIBLE else View.GONE
    }
}
