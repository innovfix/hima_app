package com.gmwapp.hima.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.databinding.ActivityCancelSubscriptionBinding
import com.gmwapp.hima.dialogs.BottomSheetCancelConfirm
import com.gmwapp.hima.utils.setOnSingleClickListener

class CancelSubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCancelSubscriptionBinding

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
    }
}
